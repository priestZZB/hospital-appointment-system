import { defineStore } from 'pinia'
import type { MyPermissionsVO } from '@/types'

interface PermissionState {
  loaded: boolean
  superAdmin: boolean
  permissions: string[]
}

/**
 * 权限码 store（迭代5 阶段4）
 * - 登录成功后 / 刷新页面时调用 load() 拉取后端权限码
 * - 超管：superAdmin=true，全量放行
 */
export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    loaded: false,
    superAdmin: false,
    permissions: [],
  }),
  getters: {
    has: (state) => (code?: string | string[]): boolean => {
      if (!code) return true
      if (state.superAdmin) return true
      const codes = Array.isArray(code) ? code : [code]
      return codes.some((c) => state.permissions.includes(c))
    },
    /** vue-router 守卫用：路由 meta.permissions 命中任一即通过 */
    canAccess: (state) => (required?: string[]): boolean => {
      if (!required || required.length === 0) return true
      if (state.superAdmin) return true
      return required.some((c) => state.permissions.includes(c))
    },
  },
  actions: {
    async load(data?: MyPermissionsVO) {
      if (data) {
        this.superAdmin = !!data.superAdmin
        this.permissions = data.apiPermissions ?? data.permissions ?? []
        this.loaded = true
        return
      }
      // 由路由守卫懒加载（避免循环依赖 request → router → store）
      const { getMyPermissionsApi } = await import('@/api/auth')
      const res = await getMyPermissionsApi()
      this.superAdmin = !!res.superAdmin
      this.permissions = res.apiPermissions ?? res.permissions ?? []
      this.loaded = true
    },
    reset() {
      this.loaded = false
      this.superAdmin = false
      this.permissions = []
    },
  },
})