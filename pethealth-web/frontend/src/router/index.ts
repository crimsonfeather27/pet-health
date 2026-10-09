import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

// 与原 HASH_SECTIONS 对齐；使用 hash 模式以兼容 Spring Boot 静态托管
const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'home', component: () => import('@/views/HomeView.vue') },
  { path: '/pets', name: 'pets', component: () => import('@/views/PetsView.vue') },
  { path: '/health-records', name: 'health-records', component: () => import('@/views/HealthRecordsView.vue') },
  { path: '/ai-diagnosis', name: 'ai-diagnosis', component: () => import('@/views/AiDiagnosisView.vue') },
  { path: '/community', name: 'community', component: () => import('@/views/CommunityView.vue') },
  { path: '/nutrition', name: 'nutrition', component: () => import('@/views/NutritionView.vue') },
  { path: '/reminders', name: 'reminders', component: () => import('@/views/RemindersView.vue') },
  { path: '/notifications', name: 'notifications', component: () => import('@/views/NotificationsView.vue') },
  { path: '/my-posts', name: 'my-posts', component: () => import('@/views/MyPostsView.vue') },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router
