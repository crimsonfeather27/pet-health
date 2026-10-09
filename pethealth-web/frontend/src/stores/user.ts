// 用户登录态 store —— 对应原 AppState.currentUser + updateUserSection + doLogin/doLogout/restoreLoginState
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiGet, apiPost, apiPut } from '@/api'

export interface UserInfo {
  id: string
  username: string
  email?: string
  phone?: string
  avatar?: string
}

export const useUserStore = defineStore('user', () => {
  const currentUser = ref<UserInfo | null>(null)
  const isLoggedIn = computed(() => !!currentUser.value)

  /** 拉取当前登录用户（对应原 /api/users/me） */
  async function fetchMe(): Promise<UserInfo | null> {
    try {
      const user = await apiGet<UserInfo>('/api/users/me')
      currentUser.value = user
      return user
    } catch (e) {
      currentUser.value = null
      return null
    }
  }

  /** 登录 */
  async function login(username: string, password: string): Promise<UserInfo> {
    const user = await apiPost<UserInfo>('/api/users/login', { username, password })
    currentUser.value = user
    return user
  }

  /** 注册 */
  async function register(username: string, email: string, password: string): Promise<UserInfo> {
    await apiPost('/api/users/register', { username, email, password })
    // 注册成功后直接登录
    return login(username, password)
  }

  /** 登出 */
  async function logout(): Promise<void> {
    try {
      await apiPost('/api/users/logout', {})
    } finally {
      currentUser.value = null
    }
  }

  /** 更新个人资料 */
  async function updateProfile(payload: { email?: string; phone?: string }): Promise<UserInfo> {
    const u = currentUser.value
    if (!u) throw new Error('未登录')
    const user = await apiPut<UserInfo>(`/api/users/${u.id}`, payload)
    currentUser.value = user
    return user
  }

  /** 清空登录态（401 触发） */
  function clear() {
    currentUser.value = null
  }

  return {
    currentUser,
    isLoggedIn,
    fetchMe,
    login,
    register,
    logout,
    updateProfile,
    clear,
  }
})
