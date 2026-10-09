<template>
  <header>
    <div class="header-container">
      <div class="logo">
        <h1>PetHealth</h1>
      </div>
      <nav>
        <ul>
          <li v-for="item in navItems" :key="item.to">
            <RouterLink :to="item.to" active-class="active" class="nav-link">
              {{ item.label }}
            </RouterLink>
          </li>
        </ul>
      </nav>
      <div id="user-section">
        <!-- 未登录 -->
        <template v-if="!user.isLoggedIn">
          <button class="btn btn-secondary" @click="openLogin">登录</button>
          <button class="btn btn-primary" @click="openRegister">注册</button>
        </template>
        <!-- 已登录：用户下拉 -->
        <div v-else class="user-dropdown" @click.stop>
          <div class="user-profile-entry" title="个人中心" @click="toggleDropdown">
            <img
              v-if="avatarUrl"
              class="user-avatar"
              :src="avatarUrl"
              alt="头像"
              @error="onAvatarError"
            />
            <span v-if="!avatarUrl || avatarBroken" class="user-avatar user-avatar-fallback">
              {{ (user.currentUser?.username || 'U').charAt(0).toUpperCase() }}
            </span>
            <span class="user-welcome">{{ user.currentUser?.username }}</span>
            <span class="user-caret">▾</span>
          </div>
          <div v-show="dropdownOpen" class="user-dropdown-menu">
            <div class="user-dropdown-item" @click="goProfile">我的信息</div>
            <div class="user-dropdown-item" @click="goMyPosts">我的帖子</div>
            <div class="user-dropdown-item" @click="goNotifications">
              消息
              <span v-if="unreadCount > 0" class="notif-badge">{{ unreadCount }}</span>
            </div>
            <div class="user-dropdown-item user-dropdown-item-danger" @click="onLogout">退出</div>
          </div>
        </div>
      </div>
    </div>
  </header>

  <main>
    <RouterView v-slot="{ Component, route }">
      <Transition name="section-fade" mode="out-in">
        <component :is="Component" :key="route.path" />
      </Transition>
    </RouterView>
  </main>

  <!-- 登录模态框 -->
  <div v-if="modal === 'login'" class="modal" @click.self="closeModal">
    <div class="modal-content">
      <button class="modal-close" @click="closeModal">✕</button>
      <h3>登录 PetHealth</h3>
      <div class="form-group">
        <label>用户名</label>
        <input v-model="loginForm.username" type="text" placeholder="demo" />
      </div>
      <div class="form-group">
        <label>密码</label>
        <input v-model="loginForm.password" type="password" placeholder="123456" @keyup.enter="doLogin" />
      </div>
      <button class="btn btn-primary full-width" @click="doLogin">登录</button>
    </div>
  </div>

  <!-- 注册模态框 -->
  <div v-if="modal === 'register'" class="modal" @click.self="closeModal">
    <div class="modal-content">
      <button class="modal-close" @click="closeModal">✕</button>
      <h3>注册 PetHealth</h3>
      <div class="form-group">
        <label>用户名</label>
        <input v-model="regForm.username" type="text" placeholder="你的昵称" />
      </div>
      <div class="form-group">
        <label>邮箱</label>
        <input v-model="regForm.email" type="email" placeholder="you@example.com" />
      </div>
      <div class="form-group">
        <label>密码</label>
        <input v-model="regForm.password" type="password" placeholder="至少 6 位" @keyup.enter="doRegister" />
      </div>
      <button class="btn btn-primary full-width" @click="doRegister">注册</button>
    </div>
  </div>

  <AppToast />

  <!-- 个人中心模态框 -->
  <ProfileModal v-if="profileOpen" @close="profileOpen = false" />
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { showToast } from '@/composables/useToast'
import { useUnreadBadge, refreshUnreadBadge } from '@/composables/useUnreadBadge'
import AppToast from '@/components/AppToast.vue'
import ProfileModal from '@/components/ProfileModal.vue'

const user = useUserStore()
const router = useRouter()

const navItems = [
  { to: '/home', label: '首页' },
  { to: '/pets', label: '宠物档案' },
  { to: '/health-records', label: '健康记录' },
  { to: '/ai-diagnosis', label: 'AI 健康助手' },
  { to: '/community', label: '宠物社区' },
  { to: '/nutrition', label: '营养助手' },
  { to: '/reminders', label: '提醒中心' },
]

// ---- 登录/注册模态框 ----
type ModalKind = 'login' | 'register' | null
const modal = ref<ModalKind>(null)
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', email: '', password: '' })

function openLogin() {
  modal.value = 'login'
}
function openRegister() {
  modal.value = 'register'
}
function closeModal() {
  modal.value = null
}

async function doLogin() {
  try {
    const u = await user.login(loginForm.username, loginForm.password)
    closeModal()
    showToast(`欢迎回来，${u.username}！`)
    refreshUnreadBadge()
  } catch (e: any) {
    showToast(e.message, 'error')
  }
}

async function doRegister() {
  try {
    await user.register(regForm.username, regForm.email, regForm.password)
    closeModal()
    showToast('注册成功！请登录')
    openLogin()
  } catch (e: any) {
    showToast(e.message, 'error')
  }
}

// ---- 用户下拉 ----
const dropdownOpen = ref(false)
const avatarBroken = ref(false)
const profileOpen = ref(false)
const { unreadCount } = useUnreadBadge()

function toggleDropdown() {
  dropdownOpen.value = !dropdownOpen.value
}
function closeDropdown() {
  dropdownOpen.value = false
}
function goProfile() {
  dropdownOpen.value = false
  if (!user.isLoggedIn) {
    showToast('请先登录', 'error')
    return
  }
  profileOpen.value = true
}
function goMyPosts() {
  dropdownOpen.value = false
  router.push('/my-posts')
}
function goNotifications() {
  dropdownOpen.value = false
  router.push('/notifications')
}
async function onLogout() {
  dropdownOpen.value = false
  await user.logout()
  showToast('已退出登录')
  router.push('/home')
}

function onAvatarError() {
  avatarBroken.value = true
}

const avatarUrl = computed(() => {
  const a = user.currentUser?.avatar
  if (a && a.startsWith('/')) return a
  return ''
})

// 点击空白收起下拉
function onDocClick() {
  closeDropdown()
}

onMounted(async () => {
  document.addEventListener('click', onDocClick)
  await user.fetchMe()
  refreshUnreadBadge()
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
})

// 暴露给子组件刷新徽标（通过路由 afterEach 也可）
router.afterEach(() => refreshUnreadBadge())
</script>

<style scoped>
/* 用户下拉菜单样式从 global.css 继承；此处仅补充 Vue 控制相关 */
.user-dropdown-menu .notif-badge {
  margin-left: 0.4rem;
}
</style>

<style>
/* 路由切换动画，对应原 main > section.active 的 fadeSlideUp */
.section-fade-enter-active {
  animation: fadeSlideUp 0.55s cubic-bezier(0.16, 1, 0.3, 1);
}
.section-fade-leave-active {
  animation: fadeIn 0.15s reverse;
}
</style>
