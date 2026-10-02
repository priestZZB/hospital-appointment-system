import { request } from './request'

/** ==================== 统计报表中心（迭代12 I1） ====================
 *  门诊统计 base=/api/clinic/stats
 *  住院统计 base=/api/inpatient/stats
 *  统一走 src/utils/request 封装（Result 信封 code=0 成功，拦截器已解包 data）。
 */

export interface StatsDailyRow {
  date?: string
  registerCount?: number
  consultCount?: number
  prescriptionCount?: number
  totalIncome?: number
  topDepartments?: Array<{ deptName?: string; count?: number }>
  admitCount?: number
  dischargeCount?: number
  inWardCount?: number
  surgeryCount?: number
  depositTotal?: number
  incomeTotal?: number
  /* 下划线兜底 */
  register_count?: number
  consult_count?: number
  prescription_count?: number
  total_income?: number
  admit_count?: number
  discharge_count?: number
  in_ward_count?: number
  surgery_count?: number
  deposit_total?: number
  income_total?: number
}

export interface StatsMonthlyRow {
  month?: string
  registerCount?: number
  totalIncome?: number
  admitCount?: number
  incomeTotal?: number
  inWardCount?: number
  dailySeries?: Array<{ day?: string; count?: number; income?: number }>
}

/* ---------------- 门诊统计 ---------------- */
export function getClinicDailyApi(date: string): Promise<StatsDailyRow> {
  return request<StatsDailyRow>({ url: '/clinic/stats/daily', method: 'get', params: { date } })
}
export function getClinicMonthlyApi(month: string): Promise<StatsMonthlyRow> {
  return request<StatsMonthlyRow>({ url: '/clinic/stats/monthly', method: 'get', params: { month } })
}
export function exportClinicDailyBlob(date: string): Promise<Blob> {
  return request<Blob>({ url: '/clinic/stats/daily/export', method: 'get', params: { date }, responseType: 'blob' })
}
export function exportClinicMonthlyBlob(month: string): Promise<Blob> {
  return request<Blob>({ url: '/clinic/stats/monthly/export', method: 'get', params: { month }, responseType: 'blob' })
}

/* ---------------- 住院统计 ---------------- */
export function getInpatientDailyApi(date: string): Promise<StatsDailyRow> {
  return request<StatsDailyRow>({ url: '/inpatient/stats/daily', method: 'get', params: { date } })
}
export function getInpatientMonthlyApi(month: string): Promise<StatsMonthlyRow> {
  return request<StatsMonthlyRow>({ url: '/inpatient/stats/monthly', method: 'get', params: { month } })
}
export function exportInpatientDailyBlob(date: string): Promise<Blob> {
  return request<Blob>({ url: '/inpatient/stats/daily/export', method: 'get', params: { date }, responseType: 'blob' })
}
export function exportInpatientMonthlyBlob(month: string): Promise<Blob> {
  return request<Blob>({ url: '/inpatient/stats/monthly/export', method: 'get', params: { month }, responseType: 'blob' })
}

/** 触发浏览器下载 blob 为 CSV 文件 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}

/** ==================== 迭代12补全 J4/J2 ==================== */
/** J4 医生工作量行 */
export interface DoctorWorkloadRow {
  doctorId?: number
  doctorName?: string
  deptName?: string
  registerCount?: number
  consultCount?: number
  prescriptionCount?: number
  prescriptionAmount?: number
}
/** J4 医生工作量（按医生聚合当日挂号/接诊/处方/金额） */
export function getDoctorWorkloadApi(date: string): Promise<DoctorWorkloadRow[]> {
  return request<DoctorWorkloadRow[]>({ url: '/clinic/stats/doctor-workload', method: 'get', params: { date } })
}
/** J2 质控指标（危急值闭环/报告完成率/标本） */
export interface QualityIndicators {
  critical?: { total?: number; closed?: number; closeRate?: number | null }
  report?: { total?: number; done?: number; doneRate?: number | null; avgHours?: number | null }
  specimen?: { total?: number; collected?: number }
}
export function getQualityIndicatorsApi(date?: string): Promise<QualityIndicators> {
  return request<QualityIndicators>({ url: '/medsupply/quality/indicators', method: 'get', params: date ? { date } : {} })
}
