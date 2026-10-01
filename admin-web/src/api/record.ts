import { request } from './request'
import type { PageResult, RecordBorrow, ReportForm } from '@/types'

/** ==================== 病案与统计（迭代12 I2/I3/J1） ====================
 *  I2 上报登记   base=/api/inpatient/report-form
 *  I3 病案借阅   base=/api/inpatient/medical-record
 *  J1 临床路径   base=/api/inpatient/path-template 与 /api/inpatient/patient-path
 *  统一走 src/utils/request 封装（Result 信封 code=0 成功，拦截器已解包 data）。
 */

/* ---------------- I2 上报登记（report-form） ---------------- */
export function createReportFormApi(data: Partial<ReportForm>): Promise<ReportForm> {
  return request<ReportForm>({ url: '/inpatient/report-form', method: 'post', data })
}
export function getReportFormApi(id: number): Promise<ReportForm> {
  return request<ReportForm>({ url: `/inpatient/report-form/${id}`, method: 'get' })
}
export function getReportFormListApi(params: Record<string, unknown>): Promise<PageResult<ReportForm>> {
  return request<PageResult<ReportForm>>({ url: '/inpatient/report-form/list', method: 'get', params })
}
export function reviewReportFormApi(id: number, note: string): Promise<string> {
  return request<string>({ url: `/inpatient/report-form/${id}/review`, method: 'put', params: { note } })
}

/* ---------------- I3 病案（medical-record） ---------------- */
export function createMedicalRecordApi(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/inpatient/medical-record', method: 'post', data })
}
export function getMedicalRecordApi(id: number): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: `/inpatient/medical-record/${id}`, method: 'get' })
}
export function getMedicalRecordListApi(params: Record<string, unknown>): Promise<PageResult<Record<string, unknown>>> {
  return request<PageResult<Record<string, unknown>>>({ url: '/inpatient/medical-record/list', method: 'get', params })
}
export function archiveMedicalRecordApi(id: number): Promise<string> {
  return request<string>({ url: `/inpatient/medical-record/${id}/archive`, method: 'put' })
}
export function borrowMedicalRecordApi(id: number, data: Record<string, unknown>): Promise<RecordBorrow> {
  return request<RecordBorrow>({ url: `/inpatient/medical-record/${id}/borrow`, method: 'post', data })
}
export function returnMedicalRecordApi(recordId: number, borrowId: number): Promise<string> {
  return request<string>({ url: `/inpatient/medical-record/${recordId}/return/${borrowId}`, method: 'post' })
}
export function getBorrowListApi(params: Record<string, unknown>): Promise<PageResult<RecordBorrow>> {
  return request<PageResult<RecordBorrow>>({ url: '/inpatient/medical-record/borrow/list', method: 'get', params })
}

/* ---------------- J1 临床路径（path-template / patient-path） ---------------- */
export function createPathTemplateApi(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/inpatient/path-template', method: 'post', data })
}
export function getPathTemplateListApi(params: Record<string, unknown>): Promise<PageResult<Record<string, unknown>>> {
  return request<PageResult<Record<string, unknown>>>({ url: '/inpatient/path-template/list', method: 'get', params })
}
export function enterPatientPathApi(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/inpatient/patient-path/enter', method: 'post', data })
}
export function getPatientPathListApi(params: Record<string, unknown>): Promise<PageResult<Record<string, unknown>>> {
  return request<PageResult<Record<string, unknown>>>({ url: '/inpatient/patient-path/list', method: 'get', params })
}
export function getPatientPathApi(id: number): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: `/inpatient/patient-path/${id}`, method: 'get' })
}
export function advancePatientPathApi(id: number): Promise<string> {
  return request<string>({ url: `/inpatient/patient-path/${id}/advance`, method: 'post' })
}
export function varyPatientPathApi(id: number, reason: string): Promise<string> {
  return request<string>({ url: `/inpatient/patient-path/${id}/variation`, method: 'post', data: { reason } })
}
export function exitPatientPathApi(id: number, reason: string): Promise<string> {
  return request<string>({ url: `/inpatient/patient-path/${id}/exit`, method: 'post', data: { reason } })
}
export function completePatientPathApi(id: number): Promise<string> {
  return request<string>({ url: `/inpatient/patient-path/${id}/complete`, method: 'post' })
}
