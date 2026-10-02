import { request } from './request'
import type { PageResult } from '@/types'

/** ==================== 急诊与后台运营（迭代14） ====================
 *  G1 预检分级 base=/api/clinic/emergency-triages
 *  G2 抢救记录 base=/api/clinic/rescues
 *  L4 考勤打卡 base=/api/clinic/attendances
 *  L1 耗材     base=/api/medsupply/consumables
 *  L2 设备     base=/api/medsupply/equipments
 */

/* ---------------- G1 急诊分诊 ---------------- */
export interface TriageRow {
  id?: number
  visitNo?: string
  patientId?: number
  patientName?: string
  triageLevel?: string
  chiefComplaint?: string
  temperature?: number
  pulse?: number
  bloodPressure?: string
  deptName?: string
  doctorName?: string
  status?: string
  createTime?: string
}
export function createTriageApi(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/clinic/emergency-triages', method: 'post', data })
}
export function getTriageListApi(params: Record<string, unknown>): Promise<PageResult<TriageRow>> {
  return request<PageResult<TriageRow>>({ url: '/clinic/emergency-triages/list', method: 'get', params })
}
export function getTriageTodayStatsApi(): Promise<Array<Record<string, unknown>>> {
  return request<Array<Record<string, unknown>>>({ url: '/clinic/emergency-triages/today-stats', method: 'get' })
}
export function triageStatusApi(id: number, status: string): Promise<string> {
  return request<string>({ url: `/clinic/emergency-triages/${id}/status`, method: 'put', data: { status } })
}

/* ---------------- G2 抢救记录 ---------------- */
export interface RescueRow {
  id?: number
  rescueNo?: string
  patientName?: string
  measures?: string
  participants?: string
  outcome?: string
  startTime?: string
  endTime?: string
}
export function createRescueApi(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/clinic/rescues', method: 'post', data })
}
export function getRescueListApi(params: Record<string, unknown>): Promise<PageResult<RescueRow>> {
  return request<PageResult<RescueRow>>({ url: '/clinic/rescues/list', method: 'get', params })
}
export function finishRescueApi(id: number, outcome: string): Promise<string> {
  return request<string>({ url: `/clinic/rescues/${id}/finish`, method: 'put', data: { outcome } })
}

/* ---------------- L4 考勤 ---------------- */
export interface AttendanceRow {
  id?: number
  userId?: number
  userName?: string
  workDate?: string
  checkinTime?: string
  checkoutTime?: string
  status?: string
}
export function attendanceCheckinApi(): Promise<string> {
  return request<string>({ url: '/clinic/attendances/checkin', method: 'post' })
}
export function attendanceCheckoutApi(): Promise<string> {
  return request<string>({ url: '/clinic/attendances/checkout', method: 'post' })
}
export function getAttendanceTodayApi(): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/clinic/attendances/today', method: 'get' })
}
export function getAttendanceListApi(params: Record<string, unknown>): Promise<PageResult<AttendanceRow>> {
  return request<PageResult<AttendanceRow>>({ url: '/clinic/attendances/list', method: 'get', params })
}
export function getAttendanceSummaryApi(): Promise<Array<Record<string, unknown>>> {
  return request<Array<Record<string, unknown>>>({ url: '/clinic/attendances/summary', method: 'get' })
}

/* ---------------- L1 耗材 ---------------- */
export interface ConsumableRow {
  id?: number
  code?: string
  name?: string
  specification?: string
  unit?: string
  price?: number
  stock?: number
  safetyStock?: number
  status?: string
}
export function createConsumableApi(data: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({ url: '/medsupply/consumables', method: 'post', data })
}
export function getConsumableListApi(params: Record<string, unknown>): Promise<PageResult<ConsumableRow>> {
  return request<PageResult<ConsumableRow>>({ url: '/medsupply/consumables/list', method: 'get', params })
}
export function consumableStockApi(id: number, type: 'IN' | 'OUT', quantity: number, remark?: string): Promise<string> {
  return request<string>({ url: `/medsupply/consumables/${id}/stock`, method: 'post', data: { type, quantity, remark } })
}
export function getConsumableRecordsApi(params: Record<string, unknown>): Promise<PageResult<Record<string, unknown>>> {
  return request<PageResult<Record<string, unknown>>>({ url: '/medsupply/consumables/records', method: 'get', params })
}
export function getLowStockApi(): Promise<ConsumableRow[]> {
  return request<ConsumableRow[]>({ url: '/medsupply/consumables/low-stock', method: 'get' })
}

/* ---------------- L2 设备 ---------------- */
export interface EquipmentRow {
  id?: number
  code?: string
  name?: string
  model?: string
  location?: string
  status?: string
  buyDate?: string
  lastMaintainDate?: string
  remark?: string
}
export function createEquipmentApi(data: Record<string, unknown>): Promise<string> {
  return request<string>({ url: '/medsupply/equipments', method: 'post', data })
}
export function getEquipmentListApi(params: Record<string, unknown>): Promise<PageResult<EquipmentRow>> {
  return request<PageResult<EquipmentRow>>({ url: '/medsupply/equipments/list', method: 'get', params })
}
export function equipmentStatusApi(id: number, status: string): Promise<string> {
  return request<string>({ url: `/medsupply/equipments/${id}/status`, method: 'put', data: { status } })
}
export function equipmentMaintainApi(id: number): Promise<string> {
  return request<string>({ url: `/medsupply/equipments/${id}/maintain`, method: 'put', data: {} })
}
