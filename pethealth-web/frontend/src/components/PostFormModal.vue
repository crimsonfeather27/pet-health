<template>
  <div class="modal" @click.self="close">
    <div class="modal-content modal-content-wide">
      <button class="modal-close" @click="close">✕</button>
      <h3>{{ editing ? '编辑帖子' : '发布新帖' }}</h3>
      <div class="form-row">
        <div class="form-group" style="flex: 2">
          <label>标题 <span style="color: var(--danger)">*</span></label>
          <input
            v-model="form.title"
            type="text"
            placeholder="2-100字"
            maxlength="100"
          />
        </div>
        <div class="form-group">
          <label>分类</label>
          <select v-model="form.category">
            <option v-for="c in categoryOptions" :key="c.value" :value="c.value">
              {{ c.label }}
            </option>
          </select>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>标签（逗号分隔，最多5个）</label>
          <input
            v-model="form.tagsStr"
            type="text"
            placeholder="例如: 疫苗,驱虫,英短"
          />
        </div>
        <div class="form-group">
          <label>关联宠物品类</label>
          <select v-model="form.petSpecies">
            <option v-for="s in speciesOptions" :key="s.value" :value="s.value">
              {{ s.label }}
            </option>
          </select>
        </div>
      </div>
      <div class="form-group">
        <label>
          正文内容 <span style="color: var(--danger)">*</span>
          <span>{{ form.content.length }} / 5000</span>
        </label>
        <textarea
          v-model="form.content"
          rows="8"
          placeholder="分享你的故事或问题吧（5-5000字）"
          maxlength="5000"
        ></textarea>
      </div>
      <div class="modal-actions">
        <button class="btn" @click="close">取消</button>
        <button class="btn btn-primary" :disabled="submitting" @click="submit">
          {{ editing ? '保存修改' : '发布' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiGet, apiPost, apiPut } from '@/api'
import { showToast } from '@/composables/useToast'

const props = defineProps<{ postId?: string | null }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'submitted', post: any): void
}>()

const user = useUserStore()
const editing = ref(false)
const submitting = ref(false)

const categoryOptions = [
  { value: 'GENERAL', label: '综合讨论' },
  { value: 'HEALTH', label: '健康医疗' },
  { value: 'NUTRITION', label: '喂养营养' },
  { value: 'TRAINING', label: '训练行为' },
  { value: 'SHOW', label: '萌宠展示' },
  { value: 'QUESTION', label: '求助问答' },
]
const speciesOptions = [
  { value: '', label: '不指定' },
  { value: 'DOG', label: '狗狗' },
  { value: 'CAT', label: '猫咪' },
  { value: 'RABBIT', label: '兔子' },
  { value: 'BIRD', label: '鸟类' },
  { value: 'OTHER', label: '其他' },
]

const form = reactive({
  title: '',
  content: '',
  category: 'GENERAL',
  petSpecies: '',
  tagsStr: '',
})

function close() {
  emit('close')
}

async function loadForEdit() {
  if (!props.postId) return
  try {
    const post = await apiGet<any>(`/api/posts/${props.postId}`)
    editing.value = true
    form.title = post.title || ''
    form.content = post.content || ''
    form.category = post.category || 'GENERAL'
    form.petSpecies = post.petSpecies || ''
    form.tagsStr = Array.isArray(post.tags) ? post.tags.join(',') : ''
  } catch (e: any) {
    showToast('加载帖子失败：' + e.message, 'error')
    emit('close')
  }
}

async function submit() {
  const title = form.title.trim()
  const content = form.content.trim()
  if (title.length < 2 || title.length > 100) {
    showToast('标题长度需在 2-100 字', 'error')
    return
  }
  if (content.length < 5 || content.length > 5000) {
    showToast('内容长度需在 5-5000 字', 'error')
    return
  }
  const tags = form.tagsStr.trim()
    ? form.tagsStr
        .split(/[,，]/)
        .map((t) => t.trim())
        .filter(Boolean)
        .slice(0, 5)
    : []

  submitting.value = true
  try {
    const payload: any = {
      title,
      content,
      category: form.category || 'GENERAL',
      petSpecies: form.petSpecies || '',
      tags,
      authorId: user.currentUser?.id,
      authorName: user.currentUser?.username,
    }
    if (editing.value && props.postId) {
      const updated = await apiPut<any>(`/api/posts/${props.postId}`, payload)
      showToast('保存修改成功')
      emit('submitted', updated)
    } else {
      const created = await apiPost<any>('/api/posts', payload)
      showToast('发布成功')
      emit('submitted', created)
    }
  } catch (e: any) {
    showToast((editing.value ? '保存失败：' : '发布失败：') + e.message, 'error')
  } finally {
    submitting.value = false
  }
}

onMounted(loadForEdit)
</script>

<style scoped>
.modal-actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}
</style>
