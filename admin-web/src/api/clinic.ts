import { request } from './request'
import type {
  AppointmentVO,
  BiOverviewVO,
  DepartmentVO,
  DoctorVO,
  MedicalRecordVO,
  PageResult,
  PrescriptionVO,
  QueuePatientVO,
  QueueSnapshotVO,
  ScheduleVO,
  SlotVO,
  StopApplicationVO,
} from '@/types'

/** BI 当日概览 + 近7日趋势 + 科室占比（依据：API接口文档.md §3.7 #53 GET /api/clinic/bi/overview） */
export function getBiOverviewApi(): Promise<BiOverviewVO> {
  return request<BiOverviewVO>({ url: '/clinic/bi/overview', method: 'get' })
}

/** 科室列表（依据：§3.1 #22 GET /api/clinic/departments） */
export function getDepartmentsApi(keyword?: string): Promise<DepartmentVO[]> {
  return request({ url: '/clinic/departments', method: 'get', params: { keyword } })
}

/** 科室详情（依据：§3.1 #23 GET /api/clinic/departments/{id}） */
export function getDepartmentApi(id: number): Promise<DepartmentVO> {
  return request({ url: `/clinic/departments/${id}`, method: 'get' })
}

/** 新增科室（依据：§3.1 #24） */
export function createDepartmentApi(data: Record<string, unknown>): Promise<DepartmentVO> {
  return request({ url: '/clinic/departments', method: 'post', data })
}

/** 编辑科室（依据：§3.1 #25） */
export function updateDepartmentApi(id: number, data: Record<string, unknown>): Promise<DepartmentVO> {
  return request({ url: `/clinic/departments/${id}`, method: 'put', data })
}

/** 启用/停用科室（依据：§3.1 #26） */
export function updateDepartmentStatusApi(id: number, status: number): Promise<unknown> {
  return request({ url: `/clinic/departments/${id}/status`, method: 'put', params: { status } })
}

/** 医生分页（依据：§9.2 S4 GET /api/clinic/doctors） */
export function getDoctorsApi(params: Record<string, unknown>): Promise<PageResult<DoctorVO>> {
  return request({ url: '/clinic/doctors', method: 'get', params })
}

/** 医生详情（依据：§9.2 S5 GET /api/clinic/doctors/{id}） */
export function getDoctorApi(id: number): Promise<DoctorVO> {
  return request({ url: `/clinic/doctors/${id}`, method: 'get' })
}

/** 新增医生（依据：§9.2 S6） */
export function createDoctorApi(data: Record<string, unknown>): Promise<DoctorVO> {
  return request({ url: '/clinic/doctors', method: 'post', data })
}

/** 编辑医生（依据：§9.2 S7） */
export function updateDoctorApi(id: number, data: Record<string, unknown>): Promise<DoctorVO> {
  return request({ url: `/clinic/doctors/${id}`, method: 'put', data })
}

/** 启用/停用医生（依据：§9.2 S8） */
export function updateDoctorStatusApi(id: number, status: number): Promise<unknown> {
  return request({ url: `/clinic/doctors/${id}/status`, method: 'put', data: { status } })
}

/** 排班日历视图（依据：§3.2 #28） */
export function getSchedulesApi(params: Record<string, unknown>): Promise<ScheduleVO[]> {
  return request({ url: '/clinic/schedules', method: 'get', params })
}

/** 排班详情（依据：§3.2 #29 GET /api/clinic/schedules/{id}） */
export function getScheduleApi(id: number): Promise<ScheduleVO> {
  return request({ url: `/clinic/schedules/${id}`, method: 'get' })
}

/** 创建排班（依据：§3.2 #27） */
export function createScheduleApi(data: Record<string, unknown>): Promise<ScheduleVO> {
  return request({ url: '/clinic/schedules', method: 'post', data })
}

/** 门诊部确认排班（生成号源） */
export function confirmScheduleApi(id: number): Promise<ScheduleVO> {
  return request({ url: `/clinic/schedules/${id}/confirm`, method: 'put' })
}

/** 门诊部驳回排班 */
export function rejectScheduleApi(id: number): Promise<ScheduleVO> {
  return request({ url: `/clinic/schedules/${id}/reject`, method: 'put' })
}

/** 待确认排班列表 */
export function getPendingSchedulesApi(params: Record<string, unknown>): Promise<ScheduleVO[]> {
  return request({ url: '/clinic/schedules/pending', method: 'get', params })
}

/** 取消排班（依据：§3.2 #30） */
export function cancelScheduleApi(id: number): Promise<unknown> {
  return request({ url: `/clinic/schedules/${id}/cancel`, method: 'put' })
}

/** 按科室+日期查号源（依据：§3.3 #31） */
export function getSlotsApi(departmentId: number, date: string): Promise<SlotVO[]> {
  return request({ url: '/clinic/slots', method: 'get', params: { departmentId, date } })
}

/** 按排班查号源（依据：§3.3 #32） */
export function getScheduleSlotsApi(scheduleId: number): Promise<SlotVO[]> {
  return request({ url: `/clinic/slots/schedule/${scheduleId}`, method: 'get' })
}

/** 挂号下单（依据：§3.3 #33 POST /api/clinic/appointments） */
export function submitAppointmentApi(data: { slotId: number; scheduleId: number }): Promise<AppointmentVO> {
  return request({ url: '/clinic/appointments', method: 'post', data })
}

/** 我的预约（依据：§3.3 #34 GET /api/clinic/appointments） */
export function getMyAppointmentsApi(params?: Record<string, unknown>): Promise<AppointmentVO[]> {
  return request({ url: '/clinic/appointments', method: 'get', params })
}

/** 预约详情（依据：§3.3 #35） */
export function getAppointmentApi(id: number): Promise<AppointmentVO> {
  return request({ url: `/clinic/appointments/${id}`, method: 'get' })
}

/** 取消预约（依据：§3.3 #36） */
export function cancelAppointmentApi(id: number, reason?: string): Promise<unknown> {
  return request({ url: `/clinic/appointments/${id}/cancel`, method: 'put', params: { reason } })
}

/** 预约管理分页（依据：§9.2 S9 GET /api/clinic/appointments/page） */
export function getAppointmentPageApi(params: Record<string, unknown>): Promise<PageResult<AppointmentVO>> {
  return request({ url: '/clinic/appointments/page', method: 'get', params })
}

/** 患者签到（依据：§3.4 #37 POST /api/clinic/checkin） */
export function checkinApi(appointmentId: number): Promise<unknown> {
  return request({ url: '/clinic/checkin', method: 'post', data: { appointmentId } })
}

/** 排队状态（依据：§3.4 #38） */
export function getQueueStatusApi(checkinId: number): Promise<Record<string, unknown>> {
  return request({ url: `/clinic/checkin/${checkinId}/queue-status`, method: 'get' })
}

/** 排队快照（依据：§9.2 S10 GET /api/clinic/checkin/queue） */
export function getQueueSnapshotApi(departmentId: number): Promise<QueueSnapshotVO> {
  return request({ url: '/clinic/checkin/queue', method: 'get', params: { departmentId } })
}

/** 叫号（依据：§3.4 #39 POST /api/clinic/call/next） */
export function callNextApi(data: { departmentId: number; consultRoom: string }): Promise<unknown> {
  return request({ url: '/clinic/call/next', method: 'post', data })
}

/** 重呼（依据：§3.4 #40） */
export function recallApi(checkinId: number, consultRoom?: string): Promise<unknown> {
  return request({ url: `/clinic/call/${checkinId}/recall`, method: 'post', params: { consultRoom } })
}

/** 过号（依据：§3.4 #41） */
export function missedApi(checkinId: number): Promise<unknown> {
  return request({ url: `/clinic/call/${checkinId}/missed`, method: 'put' })
}

/** 今日待接诊列表（依据：§9.2 S11 GET /api/clinic/consultation/today） */
export function getTodayConsultationsApi(params: Record<string, unknown>): Promise<QueuePatientVO[]> {
  return request({ url: '/clinic/consultation/today', method: 'get', params })
}

/** 开始接诊（依据：§3.5 #42） */
export function startConsultationApi(appointmentId: number): Promise<MedicalRecordVO> {
  return request({ url: '/clinic/consultation/start', method: 'post', params: { appointmentId } })
}

/** 保存病历（依据：§3.5 #43 PUT /api/clinic/consultation/{recordId}） */
export function saveMedicalRecordApi(recordId: number, data: Record<string, unknown>): Promise<MedicalRecordVO> {
  return request({ url: `/clinic/consultation/${recordId}`, method: 'put', data })
}

/** 开具处方（依据：§3.5 #44 POST /api/clinic/prescription） */
export function createPrescriptionApi(data: { medicalRecordId: number; items: Record<string, unknown>[] }): Promise<PrescriptionVO> {
  return request({ url: '/clinic/prescription', method: 'post', data })
}

/** 未缴费处方列表（依据：迭代4 GET /api/clinic/prescription/unpaid?patientId=） */
export function getUnpaidPrescriptionsApi(patientId: number): Promise<PrescriptionVO[]> {
  return request({ url: '/clinic/prescription/unpaid', method: 'get', params: { patientId } })
}

/** 检查申请（依据：§3.5 #45 POST /api/clinic/consultation/exam） */
export function applyExamApi(data: Record<string, unknown>): Promise<unknown> {
  return request({ url: '/clinic/consultation/exam', method: 'post', data })
}

/** 结束就诊（依据：§3.5 #46） */
export function finishConsultationApi(recordId: number): Promise<unknown> {
  return request({ url: `/clinic/consultation/${recordId}/finish`, method: 'put' })
}

/** 病历详情（依据：§3.5 #47） */
export function getMedicalRecordApi(recordId: number): Promise<MedicalRecordVO> {
  return request({ url: `/clinic/consultation/${recordId}`, method: 'get' })
}

/** 患者病历列表（依据：§3.5 #48） */
export function getPatientRecordsApi(patientId: number, params: Record<string, unknown>): Promise<PageResult<MedicalRecordVO>> {
  return request({ url: `/clinic/consultation/patient/${patientId}`, method: 'get', params })
}

/** 停诊申请（依据：§3.6 #49 POST /api/clinic/stop/apply） */
export function applyStopApi(data: { scheduleId: number; applyReason: string }): Promise<unknown> {
  return request({ url: '/clinic/stop/apply', method: 'post', data })
}

/** 停诊审批（依据：§3.6 #50） */
export function approveStopApi(id: number, data: { action: string; approveComment?: string }): Promise<unknown> {
  return request({ url: `/clinic/stop/${id}/approve`, method: 'put', data })
}

/** 停诊科主任初审 */
export function chiefReviewStopApi(id: number, data: { action: string; approveComment?: string }): Promise<unknown> {
  return request({ url: `/clinic/stop/${id}/chief-review`, method: 'put', data })
}

/** 停诊申请列表（依据：§3.6 #51） */
export function getStopListApi(params: Record<string, unknown>): Promise<PageResult<StopApplicationVO>> {
  return request({ url: '/clinic/stop/list', method: 'get', params })
}

/** 某医生停诊记录（依据：§3.6 #52） */
export function getDoctorStopApi(doctorId: number, params: Record<string, unknown>): Promise<PageResult<StopApplicationVO>> {
  return request({ url: `/clinic/stop/doctor/${doctorId}`, method: 'get', params })
}
