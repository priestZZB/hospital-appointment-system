import { request } from './request'
import type { AuditLog, LoginVO, MyPermissionsVO, PageResult, PositionVO, RoleVO, UserVO } from '@/types'

/** 登录（依据：API接口文档.md §1 #2 POST /api/auth/login） */
export function loginApi(data: { phone: string; password: string }): Promise<LoginVO> {
  return request<LoginVO>({ url: '/auth/login', method: 'post', data })
}

/** 登出（依据：API接口文档.md §1 #3 POST /api/auth/logout） */
export function logoutApi(): Promise<null> {
  return request<null>({ url: '/auth/logout', method: 'post' })
}

/** 刷新令牌（依据：API接口文档.md §1 #4 POST /api/auth/refresh） */
export function refreshApi(): Promise<LoginVO> {
  return request<LoginVO>({ url: '/auth/refresh', method: 'post' })
}

/** 用户分页（依据：API接口文档.md §1 #11 GET /api/auth/users） */
export function getUsersApi(params: Record<string, unknown>): Promise<{ records: UserVO[]; total: number; pageNo: number; pageSize: number }> {
  return request({ url: '/auth/users', method: 'get', params })
}

/** 创建用户（依据：§9.1 S1 POST /api/auth/users） */
export function createUserApi(data: { phone: string; password: string; realName: string; gender?: number; userType: string; roleIds?: number[] }): Promise<UserVO> {
  return request({ url: '/auth/users', method: 'post', data })
}

/** 启用/禁用用户（依据：§1 #12 PUT /api/auth/users/{id}/status） */
export function updateUserStatusApi(id: number, status: number): Promise<unknown> {
  return request({ url: `/auth/users/${id}/status`, method: 'put', data: { status } })
}

/** 角色列表（依据：§1 #5 GET /api/auth/roles） */
export function getRolesApi(): Promise<RoleVO[]> {
  return request({ url: '/auth/roles', method: 'get' })
}

/** 角色详情（依据：§1 #6 GET /api/auth/roles/{id}） */
export function getRoleApi(id: number): Promise<RoleVO> {
  return request({ url: `/auth/roles/${id}`, method: 'get' })
}

/** 创建角色（依据：§1 #7 POST /api/auth/roles） */
export function createRoleApi(data: { roleCode: string; roleName: string; description?: string; status?: number }): Promise<RoleVO> {
  return request({ url: '/auth/roles', method: 'post', data })
}

/** 编辑角色（依据：§1 #8 PUT /api/auth/roles/{id}） */
export function updateRoleApi(id: number, data: { roleName: string; description?: string; status?: number }): Promise<RoleVO> {
  return request({ url: `/auth/roles/${id}`, method: 'put', data })
}

/** 删除角色（依据：§1 #9 DELETE /api/auth/roles/{id}） */
export function deleteRoleApi(id: number): Promise<unknown> {
  return request({ url: `/auth/roles/${id}`, method: 'delete' })
}

/** 分配角色（依据：§1 #10 POST /api/auth/roles/assign） */
export function assignRolesApi(userId: number, roleIds: number[]): Promise<unknown> {
  return request({ url: '/auth/roles/assign', method: 'post', data: { userId, roleIds } })
}

/** 审计日志分页（依据：§9.1 S2 GET /api/auth/audit-logs） */
export function getAuditLogsApi(params: Record<string, unknown>): Promise<PageResult<AuditLog>> {
  return request({ url: '/auth/audit-logs', method: 'get', params })
}

/** 我的权限（迭代5：GET /api/auth/my-permissions） */
export function getMyPermissionsApi(): Promise<MyPermissionsVO> {
  return request<MyPermissionsVO>({ url: '/auth/my-permissions', method: 'get' })
}

/** 我的信息（迭代5阶段2：GET /api/auth/profile） */
export function getProfileApi(): Promise<UserVO> {
  return request<UserVO>({ url: '/auth/profile', method: 'get' })
}

/** 提交跨科室数据范围申请（迭代5阶段3：POST /api/auth/data-scope/apply） */
export function applyDataScopeApi(data: { targetDepartmentId: number; targetDepartmentName?: string; reason: string }): Promise<unknown> {
  return request({ url: '/auth/data-scope/apply', method: 'post', data })
}

/** 审批跨科室数据范围申请（迭代5阶段3：PUT /api/auth/data-scope/{id}/approve） */
export function approveDataScopeApi(id: number, data: { action: 'APPROVE' | 'REJECT'; approveComment?: string; expireTime?: string }): Promise<unknown> {
  return request({ url: `/auth/data-scope/${id}/approve`, method: 'put', data })
}

/** 我的跨科室申请列表（迭代5阶段3：GET /api/auth/data-scope/my） */
export function getMyDataScopesApi(params: Record<string, unknown>): Promise<PageResult<unknown>> {
  return request({ url: '/auth/data-scope/my', method: 'get', params })
}

/** 待审批列表（迭代5阶段3：GET /api/auth/data-scope/pending） */
export function getPendingDataScopesApi(params: Record<string, unknown>): Promise<PageResult<unknown>> {
  return request({ url: '/auth/data-scope/pending', method: 'get', params })
}

/** 岗位分页（迭代5阶段2：GET /api/auth/positions） */
export function getPositionsApi(params: Record<string, unknown>): Promise<PageResult<PositionVO>> {
  return request({ url: '/auth/positions', method: 'get', params })
}
