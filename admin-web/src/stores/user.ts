import { defineStore } from 'pinia'
import { loginApi, logoutApi, refreshApi } from '@/api/auth'
import { clearAuth, getToken, getUser, setToken, setUser } from '@/utils/auth'
import type { LoginVO } from '@/types'

interface UserState {
  token: string
  userInfo: Record<string, unknown> | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: getToken(),
    userInfo: getUser(),
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    realName: (state) => (state.userInfo?.realName as string) || '管理员',
    roles: (state) => (state.userInfo?.roles as string[]) || [],
    isSuperAdmin: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_SUPER_ADMIN'),
    // 管理权限 = 超级管理员 或 管理员（超管拥有管理员全部权限）
    isAdmin: (state) => {
      const roles = (state.userInfo?.roles as string[]) || []
      return roles.includes('ROLE_SUPER_ADMIN') || roles.includes('ROLE_ADMIN')
    },
    isDeptChief: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_DEPT_CHIEF'),
    isDoctor: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_DOCTOR'),
    isPharmacist: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_PHARMACIST'),
    isExamTech: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_EXAM_TECH'),
    isNurse: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_NURSE'),
    isPatient: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_PATIENT'),
    isCashier: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_CASHIER'),
    isTriageNurse: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_TRIAGE_NURSE'),
    isLabTech: (state) => ((state.userInfo?.roles as string[]) || []).includes('ROLE_LAB_TECH'),
  },
  actions: {
    async login(payload: { phone: string; password: string }) {
      const data: LoginVO = await loginApi(payload)
      this.token = data.token
      this.userInfo = data as unknown as Record<string, unknown>
      setToken(data.token)
      setUser(data as unknown as Record<string, unknown>)
      return data
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // 登出接口失败不阻断本地清理
      }
      this.token = ''
      this.userInfo = null
      clearAuth()
    },
    async refresh() {
      const data = await refreshApi()
      this.token = data.token
      this.userInfo = data as unknown as Record<string, unknown>
      setToken(data.token)
      setUser(data as unknown as Record<string, unknown>)
      return data
    },
  },
})
