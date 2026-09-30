import { request } from './request'
import type {
  AppointmentVO,
  BiOverviewVO,
  ConsultationRequestVO,
  DepartmentVO,
  DoctorVO,
  FollowUpPlanVO,
  MedicalCertificateVO,
  MedicalRecordVO,
  PageResult,
  PrescriptionVO,
  QueuePatientVO,
  QueueSnapshotVO,
  ReferralOrderVO,
  ScheduleCalendarVO,
  ScheduleVO,
  SlotVO,
  StopApplicationVO,
  TriageQueueItemVO,
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

/** 处方明细提交体（V9：HERBAL 明细携带煎法/脚注） */
export interface PrescriptionItemPayload {
  drugId?: number
  drugName: string
  specification?: string
  dosage?: string
  usageMethod?: string
  frequency?: string
  days?: number
  quantity?: number
  price?: number
  unit?: string
  remark?: string
  /** 中药煎法：先煎/后下/包煎/烊化等（HERBAL 明细使用） */
  decoctionMethod?: string
  /** 中药脚注：特殊处理说明（HERBAL 明细使用） */
  footnote?: string
}

/** 处方开具提交体（V9：prescriptionType=HERBAL 时 herbalDoses 必填，并携带煎服法） */
export interface PrescriptionCreatePayload {
  medicalRecordId: number
  /** 处方类型：WESTERN-西药笺（默认） / HERBAL-中药饮片笺 */
  prescriptionType?: string
  /** 中药剂数（HERBAL 处方必填，如 7 剂） */
  herbalDoses?: number
  /** 煎服法（如：每日一剂，水煎400ml，分早晚两次温服） */
  herbalUsage?: string
  items: PrescriptionItemPayload[]
}

/** 开具处方（依据：§3.5 #44 POST /api/clinic/prescription） */
export function createPrescriptionApi(data: PrescriptionCreatePayload): Promise<PrescriptionVO> {
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

// ==================== 迭代6 功能补全：会诊 / 转诊 / 随访 / 证明 ====================

/** 创建会诊请求 */
export function createConsultRequestApi(data: Record<string, unknown>): Promise<ConsultationRequestVO> {
  return request({ url: '/clinic/consult-request', method: 'post', data })
}

/** 查询会诊（医生/患者） */
export function getConsultRequestApi(id: number): Promise<ConsultationRequestVO> {
  return request({ url: `/clinic/consult-request/${id}`, method: 'get' })
}

/** 会诊列表 */
export function listConsultRequestsApi(params: Record<string, unknown>): Promise<ConsultationRequestVO[]> {
  return request({ url: '/clinic/consult-request', method: 'get', params })
}

/** 处理会诊：ACCEPT / COMPLETE / REJECT */
export function handleConsultRequestApi(id: number, action: string, opinion?: string): Promise<unknown> {
  return request({ url: `/clinic/consult-request/${id}/handle`, method: 'put', params: { action, opinion } })
}

/** 创建转诊单 */
export function createReferralApi(data: Record<string, unknown>): Promise<ReferralOrderVO> {
  return request({ url: '/clinic/referral', method: 'post', data })
}

/** 转诊列表 */
export function listReferralsApi(params: Record<string, unknown>): Promise<ReferralOrderVO[]> {
  return request({ url: '/clinic/referral', method: 'get', params })
}

/** 处理转诊：ACCEPT / COMPLETE / REJECT */
export function handleReferralApi(id: number, action: string): Promise<unknown> {
  return request({ url: `/clinic/referral/${id}/handle`, method: 'put', params: { action } })
}

/** 创建随访计划 */
export function createFollowUpPlanApi(data: Record<string, unknown>): Promise<FollowUpPlanVO> {
  return request({ url: '/clinic/follow-up', method: 'post', data })
}

/** 随访计划列表 */
export function listFollowUpPlansApi(params: Record<string, unknown>): Promise<FollowUpPlanVO[]> {
  return request({ url: '/clinic/follow-up', method: 'get', params })
}

/** 填写随访记录 */
export function addFollowUpRecordApi(id: number, content?: string, nextFollowDate?: string): Promise<unknown> {
  return request({ url: `/clinic/follow-up/${id}/record`, method: 'post', params: { content, nextFollowDate } })
}

/** 取消随访计划 */
export function cancelFollowUpPlanApi(id: number): Promise<unknown> {
  return request({ url: `/clinic/follow-up/${id}/cancel`, method: 'put' })
}

/** 创建医疗证明 */
export function createCertificateApi(data: Record<string, unknown>): Promise<MedicalCertificateVO> {
  return request({ url: '/clinic/certificate', method: 'post', data })
}

/** 医疗证明列表 */
export function listCertificatesApi(params: Record<string, unknown>): Promise<MedicalCertificateVO[]> {
  return request({ url: '/clinic/certificate', method: 'get', params })
}

/** 下载证明 PDF */
export function downloadCertificateApi(id: number): Promise<unknown> {
  return request({ url: `/clinic/certificate/${id}/pdf`, method: 'get', responseType: 'blob' })
}

/** 作废证明 */
export function cancelCertificateApi(id: number): Promise<unknown> {
  return request({ url: `/clinic/certificate/${id}/cancel`, method: 'put' })
}

// ==================== 迭代9：门诊流程补强（分诊优先级 / 回诊 / 加号 / 改期 / 绿色通道 / 排班日历） ====================

/** 分诊队列（A1 GET /api/clinic/triage/queue?departmentId=） */
export function getTriageQueueApi(departmentId?: number): Promise<TriageQueueItemVO[]> {
  return request({ url: '/clinic/triage/queue', method: 'get', params: { departmentId } })
}

/** 设置分诊优先级（A1 POST /api/clinic/triage/set-priority，priority：0-急诊 1-优先 2-普通） */
export function setTriagePriorityApi(data: {
  checkinId: number
  priority: number
  returnFlag?: boolean
}): Promise<unknown> {
  return request({ url: '/clinic/triage/set-priority', method: 'post', data })
}

/** 回诊（A6 POST /api/clinic/checkin/{checkinId}/rejoin） */
export function rejoinQueueApi(checkinId: number): Promise<unknown> {
  return request({ url: `/clinic/checkin/${checkinId}/rejoin`, method: 'post' })
}

/** 加号（A2 POST /api/clinic/appointments/overbook，请求体与挂号一致） */
export function overbookAppointmentApi(data: {
  slotId: number
  scheduleId: number
  patientId?: number
}): Promise<AppointmentVO> {
  return request({ url: '/clinic/appointments/overbook', method: 'post', data })
}

/** 预约改期（A3 POST /api/clinic/appointments/{id}/reschedule） */
export function rescheduleAppointmentApi(
  id: number,
  data: { newSlotId: number; newScheduleId: number },
): Promise<AppointmentVO> {
  return request({ url: `/clinic/appointments/${id}/reschedule`, method: 'post', data })
}

/** 号源设为绿色通道（A4 管理端 POST /api/admin/schedule/{id}/green-slots，前 N 个号设绿色） */
export function createGreenSlotsApi(scheduleId: number, count: number): Promise<unknown> {
  return request({ url: `/admin/schedule/${scheduleId}/green-slots`, method: 'post', params: { channelType: 'GREEN', count } })
}

/** 排班日历（A8 GET /api/clinic/schedules/calendar?departmentId=&startDate=&days=7，扁平列表） */
export function getScheduleCalendarApi(params: {
  departmentId: number
  startDate: string
  days?: number
}): Promise<ScheduleCalendarVO[]> {
  return request({ url: '/clinic/schedules/calendar', method: 'get', params })
}

/** 手动生成排班（A8 管理端 POST /api/admin/schedule/generate?date=YYYY-MM-DD） */
export function generateScheduleApi(date: string): Promise<unknown> {
  return request({ url: '/admin/schedule/generate', method: 'post', params: { date } })
}
