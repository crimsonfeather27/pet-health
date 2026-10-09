<template>
  <section id="notifications">
    <div class="section-header-row">
      <h2><Bell /> 消息通知</h2>
      <button class="btn btn-secondary" @click="markAllRead">全部已读</button>
    </div>

    <div v-if="loading" class="empty-hint">加载中…</div>
    <div v-else-if="!list.length" class="empty-hint">暂无消息通知</div>
    <div v-else>
      <div
        v-for="n in list"
        :key="n.id"
        :class="['notif-item', n.isRead ? 'notif-read' : 'notif-unread']"
        @click="!n.isRead && markRead(n.id)"
      >
        <span class="notif-icon">
          <component :is="iconMap[n.type || ''] || Megaphone" />
        </span>
        <div class="notif-body">
          <p class="notif-title">
            {{ n.title || '' }}
            <span v-if="!n.isRead" class="notif-dot">●</span>
          </p>
          <p class="notif-content">{{ n.content || '' }}</p>
          <p class="notif-time">{{ timeText(n.createdAt) }}</p>
        </div>
        <button class="notif-delete" title="删除" @click.stop="onDelete(n.id)">
          <X />
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  Bell,
  MessageCircle,
  Target,
  Clock,
  Megaphone,
  X,
} from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { apiGet, apiPut, apiDelete } from '@/api'
import { showToast } from '@/composables/useToast'
import { refreshUnreadBadge } from '@/composables/useUnreadBadge'

interface NotificationItem {
  id: string
  type?: string
  title?: string
  content?: string
  isRead?: boolean
  createdAt?: string
}

const user = useUserStore()
const list = ref<NotificationItem[]>([])
const loading = ref(true)

const iconMap: Record<string, any> = {
  REPLY: MessageCircle,
  ACCEPT: Target,
  REMINDER: Clock,
  SYSTEM: Megaphone,
}

function timeText(t?: string) {
  return t ? new Date(t).toLocaleString('zh-CN') : ''
}

async function load() {
  loading.value = true
  const ownerId = user.currentUser?.id || 'demo'
  try {
    const data = await apiGet<NotificationItem[]>(`/api/notifications?ownerId=${ownerId}`)
    list.value = data || []
  } catch (e: any) {
    showToast('消息加载失败：' + e.message, 'error')
  } finally {
    loading.value = false
  }
  refreshUnreadBadge()
}

async function markRead(id: string) {
  try {
    await apiPut(`/api/notifications/${id}/read`, {})
    const item = list.value.find((n) => n.id === id)
    if (item) item.isRead = true
    refreshUnreadBadge()
  } catch (e: any) {
    showToast('操作失败：' + e.message, 'error')
  }
}

async function onDelete(id: string) {
  if (!confirm('确认删除这条通知？')) return
  try {
    await apiDelete(`/api/notifications/${id}`)
    showToast('通知已删除')
    await load()
  } catch (e: any) {
    showToast('删除失败：' + e.message, 'error')
  }
}

async function markAllRead() {
  const ownerId = user.currentUser?.id
  if (!ownerId) return
  try {
    await apiPut(`/api/notifications/read-all?ownerId=${ownerId}`, {})
    showToast('已全部标记为已读')
    await load()
  } catch (e: any) {
    showToast('操作失败：' + e.message, 'error')
  }
}

onMounted(load)
</script>

<style scoped>
.empty-hint {
  color: var(--text-light);
  padding: 1rem 0;
}
</style>
