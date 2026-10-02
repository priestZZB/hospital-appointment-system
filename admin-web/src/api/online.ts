import { request } from './request'
import type { PageResult } from '@/types'

/** ==================== 互联网医院与患者服务（迭代13） ====================
 *  L3 公告     base=/api/clinic/notices
 *  K3 满意度   base=/api/clinic/evaluations
 *  K4 检查改约 base=/api/admin/exam/reservation
 *  K2 购药配送 base=/api/medsupply/deliveries
 *  K1 图文复诊 base=/api/clinic/online-consults
 *  统一走 src/utils/request 封装（Result 信封 code=0 成功，拦截器已解包 data）。
 */

/* ---------------- L3 院内公告 ---------------- */
export interface Notice {
  id?: number
  title?: string
  content?: string
  noticeType?: string
  status?: string
  publisherId?: number
  publisherName?: string
  publish_time_text?: string
  publishTimeText?: string
  createTimeText?: string
}
export function publishNoticeApi(data: Partial<Notice>): Promise<string> {
  return request<string>({ url: '/clinic/notices', method: 'post', data })
}
export function getNoticeListApi(params: Record<string, unknown>): Promise<PageResult<Notice>> {
  return request<PageResult<Notice>>({ url: '/clinic/notices/list', method: 'get', params })
}
export function offlineNoticeApi(id: number): Promise<string> {
  return request<string>({ url: `/clinic/notices/${id}/offline`, method: 'put' })
}

/* ---------------- K3 满意度评价 ---------------- */
export interface EvaluationRow {
  id?: number
  appointmentId?: number
  patientId?: number
  doctorId?: number
  doctorName?: string
  deptName?: string
  score?: number
  content?: string
  createTime?: string
}
export function submitEvaluationApi(data: Record<string, unknown>): Promise<string> {
  return request<string>({ url: '/clinic/evaluations', method: 'post', data })
}
export function getDoctorEvaluationApi(doctorId: number): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: `/clinic/evaluations/doctor/${doctorId}`, method: 'get' })
}
export function getEvaluationListApi(params: Record<string, unknown>): Promise<PageResult<EvaluationRow>> {
  return request<PageResult<EvaluationRow>>({ url: '/clinic/evaluations/list', method: 'get', params })
}

/* ---------------- K4 检查预约改约 ---------------- */
export function getReservationListApi(params: Record<string, unknown>): Promise<PageResult<Record<string, unknown>>> {
  return request<PageResult<Record<string, unknown>>>({ url: '/admin/exam/reservation/list', method: 'get', params })
}
export function rescheduleReservationApi(id: number, date: string, timeSlot: string): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: `/admin/exam/reservation/${id}/reschedule`, method: 'put', data: { date, timeSlot } })
}

/* ---------------- K2 购药配送 ---------------- */
export interface DeliveryRow {
  id?: number
  deliveryNo?: string
  prescriptionId?: number
  patientId?: number
  patientName?: string
  drugSummary?: string
  receiverName?: string
  receiverPhone?: string
  address?: string
  status?: string
  createTime?: string
  dispatchTime?: string
  deliverTime?: string
}
export function createDeliveryApi(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/medsupply/deliveries', method: 'post', data })
}
export function getDeliveryListApi(params: Record<string, unknown>): Promise<PageResult<DeliveryRow>> {
  return request<PageResult<DeliveryRow>>({ url: '/medsupply/deliveries/list', method: 'get', params })
}
export function dispatchDeliveryApi(id: number): Promise<string> {
  return request<string>({ url: `/medsupply/deliveries/${id}/dispatch`, method: 'put' })
}
export function deliverDeliveryApi(id: number): Promise<string> {
  return request<string>({ url: `/medsupply/deliveries/${id}/deliver`, method: 'put' })
}

/* ---------------- K1 图文复诊 ---------------- */
export interface ConsultRow {
  id?: number
  consultNo?: string
  patientId?: number
  patientName?: string
  doctorId?: number
  doctorName?: string
  chiefComplaint?: string
  status?: string
  prescriptionNo?: string
  createTime?: string
  acceptTime?: string
  closeTime?: string
}
export interface ConsultMessage {
  id?: number
  senderType?: string
  senderName?: string
  content?: string
  createTime?: string
}
export function createConsultApi(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/clinic/online-consults', method: 'post', data })
}
export function getConsultListApi(params: Record<string, unknown>): Promise<PageResult<ConsultRow>> {
  return request<PageResult<ConsultRow>>({ url: '/clinic/online-consults/list', method: 'get', params })
}
export function getConsultDetailApi(id: number): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: `/clinic/online-consults/${id}`, method: 'get' })
}
export function sendConsultMessageApi(id: number, data: Record<string, unknown>): Promise<string> {
  return request<string>({ url: `/clinic/online-consults/${id}/messages`, method: 'post', data })
}
export function acceptConsultApi(id: number): Promise<string> {
  return request<string>({ url: `/clinic/online-consults/${id}/accept`, method: 'post' })
}
export function prescribeConsultApi(id: number, items: Array<Record<string, unknown>>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: `/clinic/online-consults/${id}/prescribe`, method: 'post', data: { items } })
}
export function closeConsultApi(id: number): Promise<string> {
  return request<string>({ url: `/clinic/online-consults/${id}/close`, method: 'post' })
}
