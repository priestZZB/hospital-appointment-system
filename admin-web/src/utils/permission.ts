/**
 * 权限判断工具（迭代5 阶段4）
 * - 超管拥有全部权限码（后端已做通配，前端同样放行）
 * - 权限码统一来自后端 GET /api/auth/my-permissions
 */
export function hasPermission(perms: string[], code?: string | string[]): boolean {
  if (!code) return true
  const codes = Array.isArray(code) ? code : [code]
  if (perms.includes('*') || perms.includes('*:*:*')) return true
  return codes.some((c) => perms.includes(c))
}

/**
 * 按角色解析登录后首页（与路由守卫同源，登录页与 '/' redirect 共用）
 * 顺序：管理端优先 → 细分医技/护理/收费/药师 → 医生最后（ROLE_DEPT_CHIEF 通常也带 ROLE_DOCTOR）
 */
export function homeForRoles(roles: string[]): string {
  const r = roles || []
  if (r.includes('ROLE_SUPER_ADMIN') || r.includes('ROLE_ADMIN')) return '/dashboard'
  if (r.includes('ROLE_CASHIER')) return '/cashier'
  if (r.includes('ROLE_TRIAGE_NURSE')) return '/triage'
  if (r.includes('ROLE_LAB_TECH')) return '/lab-tech'
  if (r.includes('ROLE_EXAM_TECH')) return '/exam-tech'
  if (r.includes('ROLE_NURSE')) return '/infusion-nurse'
  if (r.includes('ROLE_PHARMACIST')) return '/drugs'
  if (r.includes('ROLE_DOCTOR')) return '/workbench'
  return '/patient'
}

/** 空实现占位：真实实现见 stores/permission.ts */
export function noop(): void {}