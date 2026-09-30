import { request } from './request'
import type {
  AnesthesiaVO,
  ConsentVO,
  PageResult,
  PostopFollowupVO,
  PreopVO,
  SurgeryBoardItemVO,
  SurgeryDetailVO,
  SurgeryRecordVO,
  SurgeryVO,
} from '@/types'

/** ==================== 手术/麻醉中心（迭代10） ====================
 * 后端 SurgeryController base=/api/inpatient/surgery（request 封装 baseURL 已含 /api）：
 *  POST /outpatient           门诊手术建单
 *  PUT  /{id}/schedule        排台
 *  POST /{id}/preop           术前评估
 *  POST /{id}/consent         知情同意签署
 *  POST /{id}/start           开始手术
 *  POST /{id}/record          手术记录
 *  POST /{id}/anesthesia      麻醉记录
 *  POST /{id}/postop-followup 术后镇痛随访
 *  GET  /board?date=          排台看板（当日列表）
 *  GET  /{id}                 详情聚合（surgery/preop/consents[]/record/anesthesia）
 *  GET  /list                 分页（status/source/pageNo/pageSize）
 * 状态机：APPLIED→SCHEDULED→PREOP_PASSED→IN_OPERATION→OPERATED（可 CANCELLED）。
 * 迭代6 的住院手术申请（applySurgeryApi 等）仍在 api/inpatient.ts，两套接口并存。
 */

/** 门诊手术建单（POST /inpatient/surgery/outpatient） */
export function createOutpatientSurgeryApi(data: Record<string, unknown>): Promise<SurgeryVO> {
  return request({ url: '/inpatient/surgery/outpatient', method: 'post', data })
}

/** 手术排台（PUT /inpatient/surgery/{id}/schedule） */
export function scheduleSurgerySlotApi(id: number, data: Record<string, unknown>): Promise<SurgeryVO> {
  return request({ url: `/inpatient/surgery/${id}/schedule`, method: 'put', data })
}

/** 术前评估（POST /inpatient/surgery/{id}/preop） */
export function submitPreopApi(id: number, data: Record<string, unknown>): Promise<PreopVO> {
  return request({ url: `/inpatient/surgery/${id}/preop`, method: 'post', data })
}

/** 知情同意签署（POST /inpatient/surgery/{id}/consent） */
export function signSurgeryConsentApi(id: number, data: Record<string, unknown>): Promise<ConsentVO> {
  return request({ url: `/inpatient/surgery/${id}/consent`, method: 'post', data })
}

/** 开始手术（POST /inpatient/surgery/{id}/start） */
export function startSurgeryApi(id: number): Promise<SurgeryVO> {
  return request({ url: `/inpatient/surgery/${id}/start`, method: 'post' })
}

/** 手术记录（POST /inpatient/surgery/{id}/record） */
export function submitSurgeryRecordApi(id: number, data: Record<string, unknown>): Promise<SurgeryRecordVO> {
  return request({ url: `/inpatient/surgery/${id}/record`, method: 'post', data })
}

/** 麻醉记录（POST /inpatient/surgery/{id}/anesthesia） */
export function submitAnesthesiaRecordApi(id: number, data: Record<string, unknown>): Promise<AnesthesiaVO> {
  return request({ url: `/inpatient/surgery/${id}/anesthesia`, method: 'post', data })
}

/** 术后镇痛随访（POST /inpatient/surgery/{id}/postop-followup） */
export function postopFollowupApi(id: number): Promise<PostopFollowupVO> {
  return request({ url: `/inpatient/surgery/${id}/postop-followup`, method: 'post' })
}

/** 排台看板（GET /inpatient/surgery/board?date=YYYY-MM-DD，返回当日列表） */
export function getSurgeryBoardApi(params: { date?: string }): Promise<SurgeryBoardItemVO[]> {
  return request({ url: '/inpatient/surgery/board', method: 'get', params })
}

/** 手术详情聚合（GET /inpatient/surgery/{id}） */
export function getSurgeryDetailApi(id: number): Promise<SurgeryDetailVO> {
  return request({ url: `/inpatient/surgery/${id}`, method: 'get' })
}

/** 手术分页列表（GET /inpatient/surgery/list?status=&source=&pageNo=&pageSize=） */
export function getSurgeryListApi(params: Record<string, unknown>): Promise<PageResult<SurgeryVO>> {
  return request({ url: '/inpatient/surgery/list', method: 'get', params })
}
