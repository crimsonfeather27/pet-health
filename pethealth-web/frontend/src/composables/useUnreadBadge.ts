// 未读消息徽标 —— 供 App.vue 导航栏与 NotificationsView 共享
import { ref } from 'vue'
import { apiGet } from '@/api'
import { useUserStore } from '@/stores/user'

const unreadCount = ref(0)

export async function refreshUnreadBadge() {
  const userStore = useUserStore()
  if (!userStore.isLoggedIn) {
    unreadCount.value = 0
    return
  }
  const uid = userStore.currentUser?.id
  if (!uid) return
  try {
    const count = await apiGet<number>(`/api/notifications/unread-count?ownerId=${uid}`)
    unreadCount.value = count || 0
  } catch (e) {
    unreadCount.value = 0
  }
}

export function useUnreadBadge() {
  return { unreadCount, refreshUnreadBadge }
}
