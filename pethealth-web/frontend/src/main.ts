import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { registerUnauthorizedHandler } from './api'
import { useUserStore } from './stores/user'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 注册 401 处理：清登录态（对应原 script.js 中 res.status === 401 的逻辑）
registerUnauthorizedHandler(() => {
  const userStore = useUserStore()
  userStore.clear()
})

app.mount('#app')
