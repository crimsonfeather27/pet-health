<template>
  <div class="modal" @click.self="$emit('close')">
    <div class="modal-content">
      <button class="modal-close" @click="$emit('close')">✕</button>
      <h3>个人中心</h3>
      <div class="profile-avatar-row">
        <div class="profile-avatar-wrap">
          <img
            v-if="previewAvatar || avatarUrlComputed"
            :src="previewAvatar || avatarUrlComputed"
            alt="头像"
          />
          <div v-else class="profile-avatar-fallback">
            {{ (user.currentUser?.username || 'U').charAt(0).toUpperCase() }}
          </div>
        </div>
        <div class="profile-avatar-actions">
          <button
            class="btn btn-primary btn-tiny"
            :disabled="uploading"
            @click="fileInput?.click()"
          >
            {{ uploading ? '上传中…' : '上传头像' }}
          </button>
          <p class="profile-hint">支持 png/jpg/gif/webp，≤ 5MB</p>
        </div>
        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          style="display: none"
          @change="onAvatarSelect"
        />
      </div>
      <div class="form-group">
        <label>用户名</label>
        <input type="text" :value="user.currentUser?.username" disabled />
      </div>
      <div class="form-row">
        <div class="form-group" style="flex: 1">
          <label>邮箱</label>
          <input
            v-model="email"
            type="email"
            placeholder="name@example.com"
          />
        </div>
        <div class="form-group" style="flex: 1">
          <label>手机号</label>
          <input v-model="phone" type="text" placeholder="选填" />
        </div>
      </div>
      <div class="modal-actions">
        <button class="btn" @click="$emit('close')">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">
          保存资料
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/stores/user'
import { uploadFormData } from '@/api'
import { showToast } from '@/composables/useToast'

const emit = defineEmits<{ (e: 'close'): void }>()

const user = useUserStore()

const email = ref('')
const phone = ref('')
const previewAvatar = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const saving = ref(false)

const avatarUrlComputed = computed(() => {
  const a = user.currentUser?.avatar
  return a && a.startsWith('/') ? a : ''
})

async function onAvatarSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files && input.files[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showToast('请选择图片文件', 'error')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    showToast('图片大小不能超过 5MB', 'error')
    return
  }

  // 本地预览
  previewAvatar.value = URL.createObjectURL(file)

  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const updated = await uploadFormData('/api/users/avatar', fd)
    // 更新 store（手动赋值 currentUser）
    if (user.currentUser) {
      user.currentUser.avatar = updated.avatar
    }
    showToast('头像上传成功')
  } catch (e: any) {
    showToast('头像上传失败：' + e.message, 'error')
    previewAvatar.value = ''
  } finally {
    uploading.value = false
    // 清空 input 以便重复选同一文件
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function save() {
  const mail = email.value.trim()
  const ph = phone.value.trim()
  if (mail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) {
    showToast('邮箱格式不正确', 'error')
    return
  }
  saving.value = true
  try {
    await user.updateProfile({ email: mail, phone: ph })
    showToast('资料已保存')
    emit('close')
  } catch (e: any) {
    showToast('保存失败：' + e.message, 'error')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  email.value = user.currentUser?.email || ''
  phone.value = user.currentUser?.phone || ''
})

onBeforeUnmount(() => {
  if (previewAvatar.value) URL.revokeObjectURL(previewAvatar.value)
})
</script>

<style scoped>
.profile-avatar-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}
.profile-avatar-wrap {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--bg-alt, rgba(0, 0, 0, 0.06));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.profile-avatar-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.profile-avatar-fallback {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--accent);
}
.profile-avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.profile-hint {
  font-size: 0.75rem;
  color: var(--text-light);
  margin: 0;
}
input:disabled {
  background: rgba(0, 0, 0, 0.05);
  cursor: not-allowed;
}
</style>
