import { request } from './request'
import type {
  AdmissionVO,
  BedVO,
  ConsultVO,
  DailyBillVO,
  DepositVO,
  DischargeSummaryVO,
  InpatientFeeVO,
  InpatientOverviewVO,
  MedicalOrderVO,
  MedicalRecordHomeVO,
  NursingRecordVO,
  SurgeryApplyVO,
  VitalSignVO,
} from '@/types'

/** ==================== 入院与床位 ==================== */

/** 入院登记（迭代6 住院模块） */
export function admitApi(data: Record<string, unknown>): Promise<AdmissionVO> {
  return request({ url: '/inpatient/admission', method: 'post', data })
}

/** 住院列表 */
export function getAdmissionsApi(params: Record<string, unknown>): Promise<AdmissionVO[]> {
  return request({ url: '/inpatient/admission', method: 'get', params })
}

/** 住院详情 */
export function getAdmissionApi(id: number): Promise<AdmissionVO> {
  return request({ url: `/inpatient/admission/${id}`, method: 'get' })
}

/** 分床/转床 */
export function assignBedApi(data: { admissionId: number; bedId: number }): Promise<AdmissionVO> {
  return request({ url: '/inpatient/admission/assign-bed', method: 'post', data })
}

/** 转科（E1） */
export function transferDeptApi(data: { admissionId: number; targetDeptId: number; reason?: string }): Promise<AdmissionVO> {
  return request({ url: '/inpatient/admission/transfer-dept', method: 'post', data })
}

/** 科室床位看板 */
export function getBedsApi(params: { departmentId?: number }): Promise<BedVO[]> {
  return request({ url: '/inpatient/admission/beds', method: 'get', params })
}

/** 住院总览看板（护士站/医生站） */
export function getOverviewApi(params: { departmentId?: number }): Promise<InpatientOverviewVO[]> {
  return request({ url: '/inpatient/admission/overview', method: 'get', params })
}

/** ==================== 住院医嘱 ==================== */

/** 开立医嘱 */
export function createOrderApi(data: Record<string, unknown>): Promise<MedicalOrderVO> {
  return request({ url: '/inpatient/order', method: 'post', data })
}

/** 护士核对 */
export function confirmOrderApi(id: number): Promise<MedicalOrderVO> {
  return request({ url: `/inpatient/order/${id}/confirm`, method: 'post' })
}

/** 护士执行 */
export function executeOrderApi(id: number, result?: string): Promise<MedicalOrderVO> {
  return request({ url: `/inpatient/order/${id}/execute`, method: 'post', params: { result } })
}

/** 停止医嘱 */
export function stopOrderApi(id: number): Promise<void> {
  return request({ url: `/inpatient/order/${id}/stop`, method: 'post' })
}

/** 医嘱列表 */
export function getOrdersApi(params: { admissionId: number; status?: string }): Promise<MedicalOrderVO[]> {
  return request({ url: '/inpatient/order', method: 'get', params })
}

/** ==================== 生命体征 ==================== */

/** 录入体征 */
export function recordVitalApi(data: Record<string, unknown>): Promise<VitalSignVO> {
  return request({ url: '/inpatient/vital', method: 'post', data })
}

/** 体征列表 */
export function getVitalsApi(params: { admissionId: number; limit?: number }): Promise<VitalSignVO[]> {
  return request({ url: '/inpatient/vital', method: 'get', params })
}

/** ==================== 预交金与费用 ==================== */

/** 预交金缴纳 */
export function payDepositApi(data: { admissionId: number; amount: number; payMethod?: string }): Promise<DepositVO> {
  return request({ url: '/inpatient/deposit/pay', method: 'post', data })
}

/** 预交金流水 */
export function getDepositsApi(admissionId: number): Promise<DepositVO[]> {
  return request({ url: '/inpatient/deposit/list', method: 'get', params: { admissionId } })
}

/** 费用登记 */
export function postFeeApi(data: Record<string, unknown>): Promise<InpatientFeeVO> {
  return request({ url: '/inpatient/fee/post', method: 'post', data })
}

/** 费用流水 */
export function getFeesApi(admissionId: number): Promise<InpatientFeeVO[]> {
  return request({ url: '/inpatient/fee/list', method: 'get', params: { admissionId } })
}

/** 每日费用清单 */
export function getDailyBillApi(admissionId: number): Promise<DailyBillVO[]> {
  return request({ url: '/inpatient/fee/daily-bill', method: 'get', params: { admissionId } })
}

/** 床位费日结（管理员） */
export function generateBedFeesApi(date?: string): Promise<number> {
  return request({ url: '/inpatient/fee/generate-bed-fees', method: 'post', params: date ? { date } : {} })
}

/** ==================== 出院 ==================== */

/** 办理出院 */
export function dischargeApi(data: Record<string, unknown>): Promise<DischargeSummaryVO> {
  return request({ url: '/inpatient/discharge', method: 'post', data })
}

/** 重新结算 */
export function resettleApi(admissionId: number): Promise<DischargeSummaryVO> {
  return request({ url: '/inpatient/discharge/resettle', method: 'post', params: { admissionId } })
}

/** 出院小结查询 */
export function getDischargeSummaryApi(admissionId: number): Promise<DischargeSummaryVO | null> {
  return request({ url: '/inpatient/discharge/summary', method: 'get', params: { admissionId } })
}

/** 病案首页查询（E3） */
export function getRecordHomeApi(admissionId: number): Promise<MedicalRecordHomeVO | null> {
  return request({ url: '/inpatient/discharge/home', method: 'get', params: { admissionId } })
}

/** ==================== 护理病历（E2） ==================== */

/** 录入护理记录 */
export function addNursingRecordApi(data: Record<string, unknown>): Promise<NursingRecordVO> {
  return request({ url: '/inpatient/nursing', method: 'post', data })
}

/** 护理记录列表 */
export function getNursingRecordsApi(params: { admissionId: number; recordType?: string; limit?: number }): Promise<NursingRecordVO[]> {
  return request({ url: '/inpatient/nursing', method: 'get', params })
}

/** ==================== 住院会诊（E1） ==================== */

/** 发起会诊 */
export function createInpatientConsultApi(data: Record<string, unknown>): Promise<ConsultVO> {
  return request({ url: '/inpatient/consult', method: 'post', data })
}

/** 处理会诊 */
export function handleInpatientConsultApi(data: { consultId: number; action: string; opinion?: string }): Promise<ConsultVO> {
  return request({ url: '/inpatient/consult/handle', method: 'post', data })
}

/** 会诊列表 */
export function getInpatientConsultsApi(params: Record<string, unknown>): Promise<ConsultVO[]> {
  return request({ url: '/inpatient/consult', method: 'get', params })
}

/** ==================== 手术申请（E6） ==================== */

/** 开手术申请单 */
export function applySurgeryApi(data: Record<string, unknown>): Promise<SurgeryApplyVO> {
  return request({ url: '/inpatient/surgery', method: 'post', data })
}

/** 手术排台 */
export function scheduleSurgeryApi(id: number, scheduledTime: string, operatingRoom?: string): Promise<SurgeryApplyVO> {
  return request({ url: `/inpatient/surgery/${id}/schedule`, method: 'post', params: { scheduledTime, operatingRoom } })
}

/** 取消手术申请 */
export function cancelSurgeryApi(id: number): Promise<SurgeryApplyVO> {
  return request({ url: `/inpatient/surgery/${id}/cancel`, method: 'post' })
}

/** 手术申请列表 */
export function getSurgeryAppliesApi(params: Record<string, unknown>): Promise<SurgeryApplyVO[]> {
  return request({ url: '/inpatient/surgery', method: 'get', params })
}
