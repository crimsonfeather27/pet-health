<template>
  <section id="reminders">
    <div class="section-header-row">
      <h2>提醒中心</h2>
      <button class="btn btn-primary" @click="openForm(null)">添加新提醒</button>
    </div>

    <div v-if="loading" class="empty-hint">加载中…</div>
    <div v-else-if="!reminders.length" class="empty-hint">
      暂无提醒 — 点击右上角「添加新提醒」创建
    </div>
    <template v-else>
      <template v-if="activeList.length">
        <div class="reminder-group-title">进行中</div>
        <ReminderItem
          v-for="r in activeList"
          :key="r.id"
          :reminder="r"
          :flash-id="flashId"
          @edit="openForm"
          @acknowledge="onAcknowledge"
          @cancel="onCancel"
          @delete="onDelete"
        />
      </template>
      <template v-if="finishedList.length">
        <div class="reminder-group-title">已完成</div>
        <ReminderItem
          v-for="r in finishedList"
          :key="r.id"
          :reminder="r"
          :flash-id="flashId"
          @edit="openForm"
          @acknowledge="onAcknowledge"
          @cancel="onCancel"
          @delete="onDelete"
        />
      </template>
    </template>

    <!-- 提醒表单 Modal -->
    <div v-if="formOpen" class="modal" @click.self="formOpen = false">
      <div class="modal-content">
        <button class="modal-close" @click="formOpen = false">✕</button>
        <h3>{{ editing ? '编辑提醒' : '添加新提醒' }}</h3>
        <div class="form-row">
          <div class="form-group" style="flex: 1">
            <label>提醒类型</label>
            <select v-model="form.type">
              <option value="VACCINE">疫苗接种</option>
              <option value="DEWORMING">驱虫</option>
              <option value="CHECKUP">体检复查</option>
              <option value="MEDICINE">服药</option>
              <option value="FOOD">粮食补给</option>
              <option value="GROOMING">美容洗澡</option>
              <option value="OTHER">其他</option>
            </select>
          </div>
          <div class="form-group" style="flex: 2">
            <label>提醒标题 <span style="color: var(--danger)">*</span></label>
            <input v-model="form.title" type="text" maxlength="100" placeholder="例如：下周六带豆豆打狂犬疫苗" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group" style="flex: 1">
            <label>关联宠物</label>
            <select v-model="form.petId" @change="onPetChange">
              <option value="">不绑定宠物（通用提醒）</option>
              <option v-for="p in pets.pets" :key="p.id" :value="p.id">
                {{ [p.name, p.species, p.breed].filter(Boolean).join(' · ') }}
              </option>
            </select>
          </div>
          <div class="form-group" style="flex: 1">
            <label>提醒时间 <span style="color: var(--danger)">*</span></label>
            <input v-model="form.remindAt" type="datetime-local" />
          </div>
        </div>
        <div class="form-group">
          <label>提前预警（天）</label>
          <input v-model.number="form.advanceDays" type="number" min="0" max="30" placeholder="提前几天开始提醒，例如：1" />
        </div>
        <div class="form-group">
          <label>补充说明</label>
          <textarea v-model="form.description" rows="3" maxlength="500" placeholder="可选，例如：宠物医院地址、注意事项等（最多500字）"></textarea>
        </div>
        <div class="modal-actions">
          <button class="btn" @click="formOpen = false">取消</button>
          <button class="btn btn-primary" @click="submit">保存提醒</button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { usePetsStore } from '@/stores/pets'
import { apiGet, apiPost, apiPut, apiDelete } from '@/api'
import { showToast } from '@/composables/useToast'
import { refreshUnreadBadge } from '@/composables/useUnreadBadge'
import ReminderItem from '@/components/ReminderItem.vue'

interface Reminder {
  id: string
  title?: string
  type?: string
  description?: string
  remindAt?: string
  status?: string
  petId?: string
  petName?: string
  advanceDays?: number | null
}

const user = useUserStore()
const pets = usePetsStore()

const reminders = ref<Reminder[]>([])
const loading = ref(true)
const flashId = ref('')

const activeList = computed(() =>
  reminders.value.filter((r) => r.status === 'PENDING' || r.status === 'SENT')
)
const finishedList = computed(() =>
  reminders.value.filter((r) => r.status !== 'PENDING' && r.status !== 'SENT')
)

async function load() {
  loading.value = true
  const ownerId = user.currentUser?.id || 'demo'
  try {
    const list = await apiGet<Reminder[]>(`/api/reminders?ownerId=${ownerId}`)
    reminders.value = list || []
  } catch (e: any) {
    showToast('提醒列表加载失败：' + e.message, 'error')
  } finally {
    loading.value = false
  }
}

// ---- 表单 ----
const formOpen = ref(false)
const editing = ref<Reminder | null>(null)
const form = reactive({
  id: '',
  type: 'OTHER',
  title: '',
  petId: '',
  petName: '',
  remindAt: '',
  advanceDays: 1 as number | null,
  description: '',
})

function pad(n: number) {
  return String(n).padStart(2, '0')
}

async function openForm(reminderId: string | null) {
  if (!user.isLoggedIn) {
    showToast('请先登录后添加提醒', 'error')
    return
  }
  // 确保宠物缓存就绪
  await pets.loadPets()

  editing.value = reminderId ? reminders.value.find((r) => r.id === reminderId) || null : null
  if (reminderId && !editing.value) {
    showToast('提醒不存在或已删除', 'error')
    return
  }

  const e = editing.value
  form.id = e?.id || ''
  form.type = e?.type || 'OTHER'
  form.title = e?.title || ''
  form.petId = e?.petId || ''
  form.petName = e?.petName || ''
  form.description = e?.description || ''
  form.advanceDays = e?.advanceDays != null ? e.advanceDays : 1

  if (e?.remindAt) {
    form.remindAt = String(e.remindAt).slice(0, 16)
  } else {
    const d = new Date()
    d.setDate(d.getDate() + 1)
    d.setHours(9, 0, 0, 0)
    form.remindAt = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  formOpen.value = true
}

function onPetChange() {
  if (form.petId) {
    const p = pets.pets.find((x) => x.id === form.petId)
    form.petName = p?.name || ''
  } else {
    form.petName = ''
  }
}

async function submit() {
  const title = form.title.trim()
  if (!title || title.length < 2 || title.length > 100) {
    showToast('标题长度需在 2-100 字', 'error')
    return
  }
  if (!form.remindAt) {
    showToast('请选择提醒时间', 'error')
    return
  }
  if (new Date(form.remindAt).getTime() <= Date.now()) {
    showToast('提醒时间必须晚于当前时间', 'error')
    return
  }

  const payload: any = {
    title,
    type: form.type || 'OTHER',
    description: form.description.trim() || null,
    remindAt: `${form.remindAt}:00`,
    advanceDays: form.advanceDays,
    notifyMethod: ['INAPP'],
    ownerId: user.currentUser!.id,
    status: 'PENDING',
  }
  if (form.petId) payload.petId = form.petId
  if (form.petName) payload.petName = form.petName
  if (user.currentUser?.email) payload.email = user.currentUser.email

  try {
    if (form.id) {
      await apiPut(`/api/reminders/${form.id}`, payload)
      showToast('提醒已更新')
    } else {
      await apiPost('/api/reminders', payload)
      showToast('提醒创建成功')
    }
    formOpen.value = false
    await load()
  } catch (e: any) {
    showToast(form.id ? '更新失败：' + e.message : '创建失败：' + e.message, 'error')
  }
}

// ---- 操作 ----
async function onAcknowledge(id: string) {
  if (!id) return
  try {
    await apiPut(`/api/reminders/${id}/acknowledge`, {})
    showToast('提醒已完成，为你点赞')
    flashId.value = id
    setTimeout(() => (flashId.value = ''), 2000)
    await load()
  } catch (e: any) {
    showToast('确认失败：' + e.message, 'error')
  }
}

async function onCancel(id: string) {
  if (!id) return
  if (!confirm('确定取消该提醒吗？取消后将不会发送通知，且不可恢复。')) return
  try {
    await apiPut(`/api/reminders/${id}/cancel`, {})
    showToast('已取消提醒')
    await load()
  } catch (e: any) {
    showToast('取消失败：' + e.message, 'error')
  }
}

async function onDelete(id: string) {
  if (!id) return
  if (!confirm('确定删除该提醒吗？删除后不可恢复。')) return
  try {
    await apiDelete(`/api/reminders/${id}`)
    showToast('提醒已删除')
    await load()
  } catch (e: any) {
    showToast('删除失败：' + e.message, 'error')
  }
}

onMounted(load)

// 暴露 load 供外部刷新
defineExpose({ load, refreshUnreadBadge })
</script>

<style scoped>
.empty-hint {
  color: var(--text-light);
  padding: 1rem 0;
}
</style>
