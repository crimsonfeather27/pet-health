<template>
  <section id="pets">
    <div class="section-header-row">
      <h2>宠物档案</h2>
      <button class="btn btn-primary" @click="openPetForm(null)">添加新宠物</button>
    </div>
    <div class="pets-grid">
      <div v-for="p in pets.pets" :key="p.id" class="card-glow pet-card">
        <div class="pet-card-main" @click="openDetail(p.id)">
          <div class="pet-avatar"><component :is="petIcon(p.species)" /></div>
          <h3>{{ p.name }}</h3>
          <p class="pet-meta">
            <span v-if="p.species">{{ p.species }}</span>
            <span v-if="p.breed"> · {{ p.breed }}</span>
          </p>
          <p class="pet-meta" v-if="p.ageMonths != null">{{ p.ageMonths }} 个月</p>
        </div>
      </div>
      <p v-if="!pets.pets.length" class="home-panel-empty">暂无宠物，点击右上角添加</p>
    </div>

    <!-- 宠物表单 Modal -->
    <div v-if="petFormOpen" class="modal" @click.self="petFormOpen = false">
      <div class="modal-content modal-content-wide">
        <button class="modal-close" @click="petFormOpen = false">✕</button>
        <h3>{{ petEditingId ? '编辑宠物信息' : '添加新宠物' }}</h3>
        <div class="form-row">
          <div class="form-group">
            <label>宠物名 *</label>
            <input v-model="petForm.name" type="text" placeholder="如：豆豆" />
          </div>
          <div class="form-group">
            <label>物种 *</label>
            <select v-model="petForm.species">
              <option value="cat">猫 cat</option>
              <option value="dog">狗 dog</option>
              <option value="rabbit">兔 rabbit</option>
              <option value="bird">鸟 bird</option>
              <option value="other">其他 other</option>
            </select>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>品种</label>
            <input v-model="petForm.breed" type="text" placeholder="如：柯基 / 英短" />
          </div>
          <div class="form-group">
            <label>性别</label>
            <select v-model="petForm.gender">
              <option value="公">公</option>
              <option value="母">母</option>
              <option value="未知">未知</option>
            </select>
          </div>
          <div class="form-group">
            <label>是否绝育</label>
            <select v-model="petForm.neutered">
              <option :value="''">未设置</option>
              <option :value="'true'">是（已绝育）</option>
              <option :value="'false'">否（未绝育）</option>
            </select>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>出生日期</label>
            <input v-model="petForm.birthday" type="date" />
          </div>
          <div class="form-group">
            <label>头像 URL（可选）</label>
            <input v-model="petForm.avatar" type="text" placeholder="留空使用默认图标" />
          </div>
        </div>
        <div class="form-group">
          <label>简介</label>
          <textarea v-model="petForm.description" rows="2" placeholder="如：活泼好动的小短腿"></textarea>
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="petFormOpen = false">取消</button>
          <button class="btn btn-primary" :disabled="petSubmitting" @click="submitPet">
            {{ petEditingId ? '保存修改' : '创建宠物' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 宠物详情 Modal -->
    <div v-if="detailPet" class="modal pet-detail-modal" @click.self="detailPet = null">
      <div class="modal-content modal-content-wide">
        <button class="modal-close" @click="detailPet = null">✕</button>
        <div class="pet-detail-header">
          <div class="pet-detail-avatar"><component :is="petIcon(detailPet.species)" /></div>
          <div class="pet-detail-info">
            <h3>{{ detailPet.name }}</h3>
            <p class="pet-meta">
              {{ detailPet.species || '-' }} · {{ detailPet.breed || '-' }} ·
              {{ detailPet.gender || '-' }}
            </p>
            <p class="pet-meta">
              生日: {{ detailPet.birthday || '-' }} ({{ ageText(detailPet.birthday) }})
            </p>
            <p v-if="detailPet.description" class="pet-desc">{{ detailPet.description }}</p>
          </div>
          <div class="pet-detail-actions">
            <button
              class="btn-tiny btn-secondary"
              title="编辑基本信息"
              @click="openPetForm(detailPet.id)"
            >
              编辑
            </button>
            <button
              class="btn-tiny btn-danger"
              title="删除宠物"
              @click="onDeletePet(detailPet.id)"
            >
              删除
            </button>
          </div>
        </div>

        <div class="tab-headers">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            class="tab-btn"
            :class="{ active: activeTab === tab.id }"
            @click="activeTab = tab.id"
          >
            <component :is="tab.icon" :size="16" style="vertical-align: -2px; margin-right: 4px;" />{{ tab.label }} ({{ recordsList(tab.id).length }})
          </button>
        </div>

        <div class="tab-content active">
          <div class="tab-toolbar">
            <button class="btn btn-primary btn-tiny" @click="openRecordForm(activeTabMeta.type)">
              添加{{ activeTabMeta.label }}
            </button>
          </div>
          <p v-if="!recordsList(activeTabMeta.id).length" class="empty-hint">暂无{{ activeTabMeta.label }}记录</p>
          <div v-else class="timeline">
            <div
              v-for="(r, i) in recordsList(activeTabMeta.id)"
              :key="i"
              class="timeline-item"
              :class="{ overdue: isOverdue(r.nextDueAt) }"
            >
              <div class="timeline-dot"><component :is="activeTabMeta.icon" :size="18" /></div>
              <div class="timeline-content">
                <h4>{{ recordTitle(activeTabMeta.type, r) }}</h4>
                <p class="timeline-date">{{ recordDate(activeTabMeta.type, r) }}</p>
                <p v-if="r.nextDueAt" class="timeline-due">下次: {{ r.nextDueAt }}</p>
                <template v-for="meta in recordMeta(activeTabMeta.type, r)" :key="meta.label">
                  <p class="timeline-meta">{{ meta.label }}: {{ meta.value }}</p>
                </template>
                <div class="timeline-actions">
                  <button
                    class="btn-tiny btn-secondary"
                    @click="openRecordForm(activeTabMeta.type, i)"
                  >
                    编辑
                  </button>
                  <button
                    class="btn-tiny btn-danger"
                    @click="onDeleteRecord(activeTabMeta.type, i)"
                  >
                    删除
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 记录表单 Modal（疫苗/驱虫/体检/就诊） -->
    <div v-if="recordFormOpen" class="modal" @click.self="recordFormOpen = false">
      <div class="modal-content">
        <button class="modal-close" @click="recordFormOpen = false">✕</button>
        <h3>{{ recordEditingIdx >= 0 ? '编辑' : '添加' }}{{ recordMetaTitle }}记录</h3>
        <p class="pet-meta">宠物：{{ detailPet?.name }}</p>

        <!-- 疫苗 -->
        <template v-if="recordFormType === 'vaccine'">
          <div class="form-group">
            <label>疫苗名称 *</label>
            <input v-model="rfVaccine.name" type="text" placeholder="如：猫三联" />
          </div>
          <div class="form-row">
            <div class="form-group"><label>接种日期</label><input v-model="rfVaccine.vaccinatedAt" type="date" /></div>
            <div class="form-group"><label>下次到期</label><input v-model="rfVaccine.nextDueAt" type="date" /></div>
          </div>
          <div class="form-group"><label>接种医院</label><input v-model="rfVaccine.vetClinic" type="text" /></div>
          <div class="form-group"><label>备注</label><input v-model="rfVaccine.notes" type="text" /></div>
        </template>

        <!-- 驱虫 -->
        <template v-else-if="recordFormType === 'deworming'">
          <div class="form-row">
            <div class="form-group">
              <label>驱虫类型</label>
              <select v-model="rfDeworming.type">
                <option value="体内驱虫">体内驱虫</option>
                <option value="体外驱虫">体外驱虫</option>
                <option value="体内外驱虫">体内外驱虫</option>
              </select>
            </div>
            <div class="form-group"><label>药品名</label><input v-model="rfDeworming.medicine" type="text" placeholder="如：拜宠清" /></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>驱虫日期</label><input v-model="rfDeworming.dewormedAt" type="date" /></div>
            <div class="form-group"><label>下次到期</label><input v-model="rfDeworming.nextDueAt" type="date" /></div>
          </div>
          <div class="form-group"><label>备注</label><input v-model="rfDeworming.notes" type="text" /></div>
        </template>

        <!-- 体检 -->
        <template v-else-if="recordFormType === 'checkup'">
          <div class="form-row">
            <div class="form-group"><label>体检日期</label><input v-model="rfCheckup.checkedAt" type="date" /></div>
            <div class="form-group"><label>医院</label><input v-model="rfCheckup.clinic" type="text" /></div>
          </div>
          <div class="form-group"><label>兽医姓名</label><input v-model="rfCheckup.vetName" type="text" /></div>
          <div class="form-group"><label>检查结果</label><textarea v-model="rfCheckup.result" rows="2" placeholder="如：一切正常，体重 5.8kg"></textarea></div>
          <div class="form-group"><label>异常项（逗号分隔）</label><input v-model="rfCheckup.abnormalItemsStr" type="text" placeholder="如：牙齿结石, 皮肤红点" /></div>
        </template>

        <!-- 就诊 -->
        <template v-else-if="recordFormType === 'medicalVisit'">
          <div class="form-group"><label>就诊日期</label><input v-model="rfVisit.visitedAt" type="date" /></div>
          <div class="form-group"><label>就诊原因 *</label><input v-model="rfVisit.reason" type="text" placeholder="如：食欲下降" /></div>
          <div class="form-group"><label>诊断结果</label><textarea v-model="rfVisit.diagnosis" rows="2"></textarea></div>
          <div class="form-group"><label>治疗方案</label><textarea v-model="rfVisit.treatment" rows="2"></textarea></div>
        </template>

        <div class="modal-actions">
          <button class="btn btn-secondary" @click="recordFormOpen = false">取消</button>
          <button class="btn btn-primary" :disabled="recordSubmitting" @click="submitRecord">
            {{ recordEditingIdx >= 0 ? '保存修改' : '添加记录' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { usePetsStore } from '@/stores/pets'
import { apiPost, apiPut, apiDelete } from '@/api'
import { showToast } from '@/composables/useToast'
import type { Pet } from '@/stores/pets'
import {
  Cat as LucideCat,
  Dog as LucideDog,
  Rabbit as LucideRabbit,
  Bird as LucideBird,
  Fish as LucideFish,
  PawPrint as LucidePawPrint,
  Syringe as LucideSyringe,
  Worm as LucideWorm,
  HeartPulse as LucideHeartPulse,
  Hospital as LucideHospital,
} from 'lucide-vue-next'

const user = useUserStore()
const pets = usePetsStore()

function petIcon(species?: string) {
  const s = (species || '').toLowerCase()
  if (s.includes('cat') || s.includes('猫')) return LucideCat
  if (s.includes('dog') || s.includes('狗')) return LucideDog
  if (s.includes('rabbit') || s.includes('兔')) return LucideRabbit
  if (s.includes('bird') || s.includes('鸟')) return LucideBird
  if (s.includes('fish') || s.includes('鱼')) return LucideFish
  return LucidePawPrint
}

function calcAge(birthday?: string) {
  if (!birthday) return '未知'
  const b = new Date(birthday)
  const now = new Date()
  const years = now.getFullYear() - b.getFullYear()
  const months = now.getMonth() - b.getMonth()
  const totalMonths = years * 12 + months
  if (totalMonths < 12) return `${Math.max(0, totalMonths)} 个月`
  return `${years} 岁${months > 0 ? ' ' + months + ' 个月' : ''}`
}
function ageText(birthday?: string) {
  return calcAge(birthday)
}

// ---- 详情 ----
const detailPet = ref<Pet | null>(null)
const activeTab = ref<string>('vaccines')

const tabs = [
  { id: 'vaccines', label: '疫苗', type: 'vaccine' as const, icon: LucideSyringe },
  { id: 'dewormings', label: '驱虫', type: 'deworming' as const, icon: LucideWorm },
  { id: 'checkups', label: '体检', type: 'checkup' as const, icon: LucideHeartPulse },
  { id: 'medicalVisits', label: '就诊', type: 'medicalVisit' as const, icon: LucideHospital },
]

const activeTabMeta = computed(
  () => tabs.find((t) => t.id === activeTab.value) || tabs[0]
)

const RECORD_FIELD_MAP: Record<string, { listKey: string; title: string }> = {
  vaccine: { listKey: 'vaccines', title: '疫苗' },
  deworming: { listKey: 'dewormings', title: '驱虫' },
  checkup: { listKey: 'checkups', title: '体检' },
  medicalVisit: { listKey: 'medicalVisits', title: '就诊' },
}

function recordsList(tabId: string): any[] {
  if (!detailPet.value) return []
  const type = tabs.find((t) => t.id === tabId)?.type
  if (!type) return []
  const listKey = RECORD_FIELD_MAP[type].listKey
  return detailPet.value[listKey] || []
}

function isOverdue(nextDueAt?: string) {
  return !!(nextDueAt && new Date(nextDueAt) <= new Date())
}

function recordTitle(type: string, r: any) {
  if (type === 'vaccine') return r.name || '未命名疫苗'
  if (type === 'deworming') return `${r.type || '驱虫'} · ${r.medicine || '-'}`
  if (type === 'checkup') return r.clinic || '体检'
  if (type === 'medicalVisit') return r.reason || '就诊'
  return '记录'
}
function recordDate(type: string, r: any) {
  if (type === 'vaccine') return `接种: ${r.vaccinatedAt || '-'}`
  if (type === 'deworming') return `驱虫: ${r.dewormedAt || '-'}`
  if (type === 'checkup') return `日期: ${r.checkedAt || '-'}`
  if (type === 'medicalVisit') return `日期: ${r.visitedAt || '-'}`
  return '-'
}
function recordMeta(type: string, r: any) {
  const out: { label: string; value: string }[] = []
  if (type === 'vaccine') {
    if (r.vetClinic) out.push({ label: '医院', value: r.vetClinic })
    if (r.notes) out.push({ label: '备注', value: r.notes })
  } else if (type === 'deworming') {
    if (r.notes) out.push({ label: '备注', value: r.notes })
  } else if (type === 'checkup') {
    if (r.vetName) out.push({ label: '兽医', value: r.vetName })
    if (r.result) out.push({ label: '结果', value: r.result })
    if (r.abnormalItems?.length) out.push({ label: '异常', value: r.abnormalItems.join(', ') })
  } else if (type === 'medicalVisit') {
    if (r.diagnosis) out.push({ label: '诊断', value: r.diagnosis })
    if (r.treatment) out.push({ label: '治疗', value: r.treatment })
  }
  return out
}

function openDetail(petId: string) {
  const p = pets.pets.find((x) => x.id === petId)
  if (!p) {
    showToast('未找到宠物信息', 'error')
    return
  }
  detailPet.value = p
  activeTab.value = 'vaccines'
}

// ---- 宠物表单 ----
const petFormOpen = ref(false)
const petEditingId = ref('')
const petSubmitting = ref(false)
const petForm = reactive({
  name: '',
  species: 'cat',
  breed: '',
  gender: '未知',
  neutered: '' as string,
  birthday: '',
  avatar: '',
  description: '',
})

async function openPetForm(petId: string | null) {
  if (!user.isLoggedIn) {
    showToast('请先登录后再添加宠物', 'error')
    return
  }
  petEditingId.value = petId || ''
  const p = petId ? pets.pets.find((x) => x.id === petId) : null
  petForm.name = p?.name || ''
  petForm.species = p?.species || 'cat'
  petForm.breed = p?.breed || ''
  petForm.gender = p?.gender || '未知'
  petForm.neutered = p?.neutered == null ? '' : String(p.neutered)
  petForm.birthday = p?.birthday || ''
  petForm.avatar = p?.avatar || ''
  petForm.description = p?.description || ''
  petFormOpen.value = true
}

async function submitPet() {
  if (!petForm.name.trim()) {
    showToast('宠物名不能为空', 'error')
    return
  }
  const neuteredVal =
    petForm.neutered === '' ? null : petForm.neutered === 'true'
  const body: any = {
    name: petForm.name.trim(),
    species: petForm.species,
    breed: petForm.breed.trim() || null,
    gender: petForm.gender,
    neutered: neuteredVal,
    birthday: petForm.birthday || null,
    avatar: petForm.avatar.trim() || null,
    description: petForm.description.trim() || null,
    ownerId: user.currentUser?.id || 'demo',
    ownerName: user.currentUser?.username || 'demo',
  }
  petSubmitting.value = true
  try {
    if (petEditingId.value) {
      await apiPut(`/api/pets/${petEditingId.value}`, body)
      showToast('宠物信息已更新')
    } else {
      await apiPost('/api/pets', body)
      showToast('宠物创建成功')
    }
    petFormOpen.value = false
    detailPet.value = null
    await pets.loadPets(true)
  } catch (e: any) {
    showToast('保存失败：' + e.message, 'error')
  } finally {
    petSubmitting.value = false
  }
}

async function onDeletePet(petId: string) {
  const p = pets.pets.find((x) => x.id === petId)
  if (!confirm(`确认删除「${p?.name || '此宠物'}」？\n该操作不可恢复，相关疫苗/驱虫/体检/就诊记录也将一并删除。`)) return
  try {
    await apiDelete(`/api/pets/${petId}`)
    showToast('已删除宠物')
    detailPet.value = null
    await pets.loadPets(true)
  } catch (e: any) {
    showToast('删除失败：' + e.message, 'error')
  }
}

// ---- 记录表单（疫苗/驱虫/体检/就诊） ----
const recordFormOpen = ref(false)
const recordFormType = ref<'vaccine' | 'deworming' | 'checkup' | 'medicalVisit'>('vaccine')
const recordEditingIdx = ref(-1)
const recordSubmitting = ref(false)

const rfVaccine = reactive({ name: '', vaccinatedAt: '', nextDueAt: '', vetClinic: '', notes: '' })
const rfDeworming = reactive({ type: '体内驱虫', medicine: '', dewormedAt: '', nextDueAt: '', notes: '' })
const rfCheckup = reactive({ checkedAt: '', clinic: '', vetName: '', result: '', abnormalItemsStr: '' })
const rfVisit = reactive({ visitedAt: '', reason: '', diagnosis: '', treatment: '' })

const recordMetaTitle = computed(() => RECORD_FIELD_MAP[recordFormType.value]?.title || '记录')

function openRecordForm(type: 'vaccine' | 'deworming' | 'checkup' | 'medicalVisit', idx?: number) {
  if (!detailPet.value) return
  recordFormType.value = type
  recordEditingIdx.value = idx != null ? idx : -1

  const listKey = RECORD_FIELD_MAP[type].listKey
  const list: any[] = detailPet.value[listKey] || []
  const rec = idx != null && idx >= 0 && idx < list.length ? list[idx] : null

  rfVaccine.name = rec?.name || ''
  rfVaccine.vaccinatedAt = rec?.vaccinatedAt || ''
  rfVaccine.nextDueAt = rec?.nextDueAt || ''
  rfVaccine.vetClinic = rec?.vetClinic || ''
  rfVaccine.notes = rec?.notes || ''

  rfDeworming.type = rec?.type || '体内驱虫'
  rfDeworming.medicine = rec?.medicine || ''
  rfDeworming.dewormedAt = rec?.dewormedAt || ''
  rfDeworming.nextDueAt = rec?.nextDueAt || ''
  rfDeworming.notes = rec?.notes || ''

  rfCheckup.checkedAt = rec?.checkedAt || ''
  rfCheckup.clinic = rec?.clinic || ''
  rfCheckup.vetName = rec?.vetName || ''
  rfCheckup.result = rec?.result || ''
  rfCheckup.abnormalItemsStr = rec?.abnormalItems ? rec.abnormalItems.join(', ') : ''

  rfVisit.visitedAt = rec?.visitedAt || ''
  rfVisit.reason = rec?.reason || ''
  rfVisit.diagnosis = rec?.diagnosis || ''
  rfVisit.treatment = rec?.treatment || ''

  recordFormOpen.value = true
}

async function submitRecord() {
  const dp = detailPet.value
  if (!dp) return
  const petId = dp.id
  const meta = RECORD_FIELD_MAP[recordFormType.value]
  let rec: any = {}

  if (recordFormType.value === 'vaccine') {
    if (!rfVaccine.name.trim()) {
      showToast('疫苗名称不能为空', 'error')
      return
    }
    rec = {
      name: rfVaccine.name.trim(),
      vaccinatedAt: rfVaccine.vaccinatedAt,
      nextDueAt: rfVaccine.nextDueAt,
      vetClinic: rfVaccine.vetClinic,
      notes: rfVaccine.notes,
    }
  } else if (recordFormType.value === 'deworming') {
    rec = {
      type: rfDeworming.type,
      medicine: rfDeworming.medicine,
      dewormedAt: rfDeworming.dewormedAt,
      nextDueAt: rfDeworming.nextDueAt,
      notes: rfDeworming.notes,
    }
  } else if (recordFormType.value === 'checkup') {
    const abnormal = rfCheckup.abnormalItemsStr
      .split(/[,，]/)
      .map((s) => s.trim())
      .filter(Boolean)
    rec = {
      checkedAt: rfCheckup.checkedAt,
      vetName: rfCheckup.vetName,
      clinic: rfCheckup.clinic,
      result: rfCheckup.result,
      abnormalItems: abnormal,
    }
  } else if (recordFormType.value === 'medicalVisit') {
    if (!rfVisit.reason.trim()) {
      showToast('就诊原因不能为空', 'error')
      return
    }
    rec = {
      visitedAt: rfVisit.visitedAt,
      reason: rfVisit.reason,
      diagnosis: rfVisit.diagnosis,
      treatment: rfVisit.treatment,
    }
  }

  const listKey = meta.listKey
  const list = [...(dp[listKey] || [])]
  if (recordEditingIdx.value >= 0) {
    list[recordEditingIdx.value] = rec
  } else {
    list.push(rec)
  }

  recordSubmitting.value = true
  try {
    await apiPut(`/api/pets/${petId}`, { [listKey]: list })
    showToast(`${meta.title}记录已${recordEditingIdx.value >= 0 ? '更新' : '添加'}`)
    recordFormOpen.value = false
    await refreshDetail(petId)
  } catch (e: any) {
    showToast('保存失败：' + e.message, 'error')
  } finally {
    recordSubmitting.value = false
  }
}

async function onDeleteRecord(type: 'vaccine' | 'deworming' | 'checkup' | 'medicalVisit', idx: number) {
  const dp = detailPet.value
  if (!dp) return
  const petId = dp.id
  const meta = RECORD_FIELD_MAP[type]
  if (!confirm(`确认删除这条${meta.title}记录？`)) return
  const list = (dp[meta.listKey] || []).filter((_: any, i: number) => i !== idx)
  try {
    await apiPut(`/api/pets/${petId}`, { [meta.listKey]: list })
    showToast(`${meta.title}记录已删除`)
    await refreshDetail(petId)
  } catch (e: any) {
    showToast('删除失败：' + e.message, 'error')
  }
}

async function refreshDetail(petId: string) {
  try {
    const updated = await pets.refreshPet(petId)
    if (updated) detailPet.value = updated
  } catch (e) {
    /* ignore */
  }
}

onMounted(() => {
  pets.loadPets()
})
</script>

<style scoped>
.home-panel-empty {
  color: var(--text-light);
}
.pets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}
.pet-card-main {
  text-align: center;
  cursor: pointer;
}
.pet-avatar {
  font-size: 2.5rem;
}
.pet-card-main h3 {
  margin: 0.3rem 0 0.2rem;
}
.pet-meta {
  color: var(--text-light);
  font-size: 0.85rem;
  margin: 0;
}

/* 详情 Modal */
.pet-detail-header {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-bottom: 1rem;
}
.pet-detail-avatar {
  font-size: 3rem;
  flex-shrink: 0;
}
.pet-detail-info {
  flex: 1;
  min-width: 0;
}
.pet-detail-info h3 {
  margin: 0 0 0.3rem;
}
.pet-desc {
  color: var(--text-light);
  font-size: 0.85rem;
  margin: 0.3rem 0 0;
}
.pet-detail-actions {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  flex-shrink: 0;
}
.tab-headers {
  display: flex;
  gap: 0.4rem;
  border-bottom: 1px solid var(--border);
  margin-bottom: 0.75rem;
  flex-wrap: wrap;
}
.tab-btn {
  border: none;
  background: transparent;
  color: var(--text-light);
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  font-size: 0.9rem;
}
.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}
.tab-content.active {
  display: block;
}
.tab-toolbar {
  margin-bottom: 0.6rem;
}
.timeline {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-height: 38vh;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(56, 115, 182, 0.25) transparent;
  scrollbar-gutter: stable;
}
.timeline-item {
  display: flex;
  gap: 0.75rem;
  padding: 0.6rem;
  border-radius: 8px;
  background: var(--bg-alt, rgba(0, 0, 0, 0.02));
  border-left: 3px solid var(--accent);
}
.timeline-item.overdue {
  border-left-color: var(--danger);
  background: rgba(245, 108, 108, 0.08);
}
.timeline-dot {
  font-size: 1.2rem;
  flex-shrink: 0;
}
.timeline-content {
  flex: 1;
  min-width: 0;
}
.timeline-content h4 {
  margin: 0 0 0.2rem;
}
.timeline-date,
.timeline-due,
.timeline-meta {
  color: var(--text-light);
  font-size: 0.8rem;
  margin: 0.1rem 0;
}
.timeline-due {
  color: var(--accent);
}
.timeline-actions {
  display: flex;
  gap: 0.4rem;
  margin-top: 0.4rem;
}
.empty-hint {
  color: var(--text-light);
  padding: 0.5rem 0;
}
.btn-tiny {
  font-size: 0.78rem;
  padding: 0.25rem 0.55rem;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: transparent;
  cursor: pointer;
}
.btn-tiny.btn-primary {
  border-color: var(--accent);
  color: var(--accent);
}
.btn-tiny.btn-danger {
  border-color: var(--danger);
  color: var(--danger);
}
</style>
