import type { Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/stores/permission'

/**
 * 按钮/元素级权限指令（迭代5 阶段4）
 * 用法：
 *   v-permission="'api:auth:user:create'"          → 单一权限码
 *   v-permission="['api:a', 'api:b']"              → 任一命中即显示
 * 超管（*:*:*）默认全量显示。
 */
function check(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const store = usePermissionStore()
  if (!binding.value) return
  const codes = Array.isArray(binding.value) ? binding.value : [binding.value]
  const ok = store.superAdmin || codes.some((c) => store.permissions.includes(c))
  if (!ok) {
    el.style.display = 'none'
    el.setAttribute('data-v-permission-hidden', 'true')
  } else {
    el.style.display = ''
    el.removeAttribute('data-v-permission-hidden')
  }
}

export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted: check,
  updated: check,
}