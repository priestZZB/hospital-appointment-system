import { request } from './request'
import type { IcdOptionVO, IcdVO, PageResult } from '@/types'

/** ==================== ICD-10 字典（迭代9 J3） ====================
 * 后端 IcdDictController base=/api/clinic/icd：
 *  GET  /page     分页（keyword/category/pageNo/pageSize，常用优先）→ icd:query
 *  GET  /categories  章节分类
 *  POST           新增 → icd:manage
 *  PUT  /{id}     编辑 → icd:manage
 *  DELETE /{id}   删除 → icd:manage
 * 选择器搜索复用 /page（pageSize=20，keyword 前缀/名称模糊）。
 */

/** ICD 字典分页（J3 GET /api/clinic/icd/page） */
export function getIcdPageApi(params: Record<string, unknown>): Promise<PageResult<IcdVO>> {
  return request({ url: '/clinic/icd/page', method: 'get', params })
}

/** 新增 ICD 条目（J3 POST /api/clinic/icd） */
export function createIcdApi(data: Record<string, unknown>): Promise<IcdVO> {
  return request({ url: '/clinic/icd', method: 'post', data })
}

/** 编辑 ICD 条目（J3 PUT /api/clinic/icd/{id}） */
export function updateIcdApi(data: Record<string, unknown>): Promise<IcdVO> {
  const id = (data as { id?: number }).id
  return request({ url: `/clinic/icd/${id}`, method: 'put', data })
}

/** 删除 ICD 条目（J3 DELETE /api/clinic/icd/{id}） */
export function deleteIcdApi(id: number): Promise<unknown> {
  return request({ url: `/clinic/icd/${id}`, method: 'delete' })
}

/** ICD 选择器搜索（J3 复用 /page，供病历诊断选择器远程搜索，返回 code+name 选项） */
export function getIcdOptionsApi(keyword?: string): Promise<IcdOptionVO[]> {
  return request({ url: '/clinic/icd/page', method: 'get', params: { keyword, pageNo: 1, pageSize: 20 } })
    .then((res: unknown) => {
      const page = res as { records?: Array<{ icdCode?: string; icdName?: string; icd_code?: string; icd_name?: string }> }
      const rows = page?.records ?? []
      return rows.map((r) => ({
        icdCode: r.icdCode ?? r.icd_code ?? '',
        icdName: r.icdName ?? r.icd_name ?? '',
      })) as IcdOptionVO[]
    })
}
