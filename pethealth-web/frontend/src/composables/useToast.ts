// Toast 提示 —— 对应原 script.js 的 showToast
import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'info'

interface ToastItem {
  id: number
  msg: string
  type: ToastType
}

const toasts = ref<ToastItem[]>([])
let seq = 0

export function showToast(msg: string, type: ToastType = 'success') {
  const id = ++seq
  toasts.value.push({ id, msg, type })
  setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }, 2500)
}

export function useToast() {
  return { toasts, showToast }
}
