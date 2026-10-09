// ===================== 通用 API 封装 =====================
// 由原 script.js 的 api/apiGet/apiPost/apiPut/apiDelete 迁移而来
// 行为保持一致：
//  - credentials: same-origin → axios withCredentials: true
//  - 响应结构 { code, message, data }；code !== 200 抛错
//  - 401 触发登录态清理（由 user store 处理）
import axios, { type AxiosInstance } from 'axios'

const instance: AxiosInstance = axios.create({
  baseURL: '',
  withCredentials: true, // 携带 HttpOnly Cookie，对齐原 credentials: 'same-origin'
  headers: { 'Content-Type': 'application/json' },
})

// 401 处理：延迟引用 user store，避免循环依赖
let onUnauthorized: (() => void) | null = null
export function registerUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

instance.interceptors.response.use(
  (response) => {
    const json = response.data
    // 兼容非标准响应（如文件下载）
    if (json && typeof json === 'object' && 'code' in json) {
      if (json.code !== 200) {
        return Promise.reject(new Error(json.message || '请求失败'))
      }
      return json.data
    }
    return json
  },
  (error) => {
    if (error.response?.status === 401) {
      onUnauthorized?.()
      return Promise.reject(new Error('登录已失效，请重新登录'))
    }
    const status = error.response?.status
    const msg = error.response?.data?.message || `HTTP ${status || 'unknown'}`
    return Promise.reject(new Error(msg))
  }
)

// 对齐原 apiGet/apiPost/apiPut/apiDelete 的签名
export const apiGet = <T = any>(url: string, headers?: Record<string, string>): Promise<T> =>
  instance.get(url, headers ? { headers } : undefined) as unknown as Promise<T>

export const apiPost = <T = any>(url: string, body?: any, headers?: Record<string, string>): Promise<T> =>
  instance.post(url, body ?? {}, headers ? { headers } : undefined) as unknown as Promise<T>

export const apiPut = <T = any>(url: string, body?: any, headers?: Record<string, string>): Promise<T> =>
  instance.put(url, body ?? {}, headers ? { headers } : undefined) as unknown as Promise<T>

export const apiDelete = <T = any>(url: string, headers?: Record<string, string>): Promise<T> =>
  instance.delete(url, headers ? { headers } : undefined) as unknown as Promise<T>

// 文件上传专用（保持原 uploadAvatarFile 行为）
export async function uploadFormData(url: string, formData: FormData): Promise<any> {
  const res = await instance.post(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res
}

export default instance
