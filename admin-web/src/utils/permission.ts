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

/** 空实现占位：真实实现见 stores/permission.ts */
export function noop(): void {}