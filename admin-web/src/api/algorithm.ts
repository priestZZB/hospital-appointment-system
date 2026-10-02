/**
 * 算法层接口（迭代15 B1）：遗传排班 / 停诊重调度 / 爽约预测 / 排床优化 / 优先级叫号
 */
import { request } from './request'
import type { PageResult } from '@/types'

/** ---------- B1-1 遗传排班 ---------- */
export interface ScheduleSuggestionRow {
  id: number
  batchNo: string
  weekStart: string
  departmentId: number | null
  doctorId: number
  doctorName: string
  planDays: string
  fitness: number
  status: string
}

export function generateScheduleApi(params?: { departmentId?: number; weekStart?: string }) {
  return request<Record<string, unknown>>({ url: '/clinic/schedule-algo/generate', method: 'post', params })
}
export function getScheduleBatchApi(batchNo: string) {
  return request<{ batchNo: string; suggestions: ScheduleSuggestionRow[] }>({
    url: `/clinic/schedule-algo/batch/${batchNo}`,
    method: 'get',
  })
}
export function getScheduleListApi(params: Record<string, unknown>) {
  return request<PageResult<ScheduleSuggestionRow>>({
    url: '/clinic/schedule-algo/list',
    method: 'get',
    params,
  })
}
export function applyScheduleBatchApi(batchNo: string) {
  return request<Record<string, unknown>>({
    url: `/clinic/schedule-algo/batch/${batchNo}/apply`,
    method: 'post',
  })
}

/** ---------- B1-3 停诊重调度 ---------- */
export function stopRescheduleApi(data: { doctorId: number; stopDate: string }) {
  return request<Record<string, unknown>>({ url: '/clinic/schedule-stop/reschedule', method: 'post', data })
}
export function getRescheduleBatchesApi(params: Record<string, unknown>) {
  return request<PageResult<Record<string, unknown>>>({
    url: '/clinic/schedule-stop/batches',
    method: 'get',
    params,
  })
}

/** ---------- B1-4 爽约预测 ---------- */
export function predictNoShowApi(data: {
  patientId: number
  appointmentId: number
  historyTotal?: number
  historyNoShow?: number
  advanceDays?: number
  hourOfDay?: number
}) {
  return request<Record<string, unknown>>({ url: '/ai/no-show/predict', method: 'post', data })
}
export function getNoShowListApi(params: Record<string, unknown>) {
  return request<PageResult<Record<string, unknown>>>({ url: '/ai/no-show/list', method: 'get', params })
}

/** ---------- B1-2 优先级叫号 ---------- */
export function getQueuePriorityApi() {
  return request<Array<Record<string, unknown>>>({ url: '/clinic/queue-priority', method: 'get' })
}

/** ---------- B1-5 排床优化 ---------- */
export function generateBedPlanApi(data: {
  departmentId?: number
  patients: Array<{ patientId: number; patientName?: string; priority: number; queuedAt?: string }>
}) {
  return request<Record<string, unknown>>({ url: '/inpatient/bed-plan/generate', method: 'post', data })
}
export function getBedPlanListApi(params: Record<string, unknown>) {
  return request<PageResult<Record<string, unknown>>>({
    url: '/inpatient/bed-plan/list',
    method: 'get',
    params,
  })
}
