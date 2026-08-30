import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { WMessage } from 'win-design-next'
import { clearAuth, getToken } from '@/utils/auth'
import router from '@/router'
import type { Result } from '@/types'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const res = response.data as Result
    if (res.code === 0) {
      return res.data as unknown as AxiosResponse
    }
    if (res.code === 1004 || res.code === 1007) {
      clearAuth()
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    if (error.response?.status === 401) {
      clearAuth()
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    } else {
      WMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

/** 统一请求封装：resolve 出 Result.data */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as Promise<T>
}

export default service
