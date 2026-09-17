import { request } from './request'
import type {
  CriticalValueVO,
  Drug,
  DrugDispense,
  DrugInventory,
  ExamApplicationVO,
  ExamItem,
  ExamReport,
  InfusionOrder,
  InfusionRecord,
  PageResult,
  PrescriptionReviewVO,
} from '@/types'

/** 药品目录分页（依据：API接口文档.md §5 #60） */
export function getDrugPageApi(params: Record<string, unknown>): Promise<PageResult<Drug>> {
  return request({ url: '/admin/drug/page', method: 'get', params })
}

/** 药品详情（依据：§5 #61 GET /api/admin/drug/{id}） */
export function getDrugApi(id: number): Promise<Drug> {
  return request({ url: `/admin/drug/${id}`, method: 'get' })
}

/** 新增药品（依据：§5 #62） */
export function createDrugApi(data: Record<string, unknown>): Promise<Drug> {
  return request({ url: '/admin/drug', method: 'post', data })
}

/** 编辑药品（依据：§5 #63） */
export function updateDrugApi(id: number, data: Record<string, unknown>): Promise<Drug> {
  return request({ url: `/admin/drug/${id}`, method: 'put', data })
}

/** 库存列表（依据：§5 #64） */
export function getInventoryApi(params: Record<string, unknown>): Promise<PageResult<DrugInventory>> {
  return request({ url: '/admin/drug/inventory/list', method: 'get', params })
}

/** 入库（依据：§5 #65） */
export function inventoryInboundApi(data: { drugId: number; quantity: number; remark?: string }): Promise<unknown> {
  return request({ url: '/admin/drug/inventory/inbound', method: 'post', data })
}

/** 出库（依据：§5 #66） */
export function inventoryOutboundApi(data: { drugId: number; quantity: number; remark?: string }): Promise<unknown> {
  return request({ url: '/admin/drug/inventory/outbound', method: 'post', data })
}

/** 盘点调整（依据：§5 #67） */
export function inventoryAdjustApi(data: { drugId: number; quantity: number; remark?: string }): Promise<unknown> {
  return request({ url: '/admin/drug/inventory/adjust', method: 'post', data })
}

/** 检查项目列表（依据：§5 #68） */
export function getExamItemsApi(params: Record<string, unknown>): Promise<ExamItem[]> {
  return request({ url: '/admin/exam/item', method: 'get', params })
}

/** 创建检查项目（依据：§5 #69） */
export function createExamItemApi(data: Record<string, unknown>): Promise<ExamItem> {
  return request({ url: '/admin/exam/item', method: 'post', data })
}

/** 检查报告录入（依据：§9.3 S15 POST /api/admin/exam/report） */
export function createExamReportApi(form: FormData): Promise<ExamReport> {
  return request({ url: '/admin/exam/report', method: 'post', data: form, headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 检查报告查询（依据：§5 #70 GET /api/exam/report/my） */
export function getMyExamReportsApi(params: Record<string, unknown>): Promise<PageResult<ExamReport>> {
  return request({ url: '/exam/report/my', method: 'get', params })
}

/** 医生选药（依据：§9.3 S14 GET /api/medsupply/drugs） */
export function searchDrugsApi(params: Record<string, unknown>): Promise<PageResult<Drug>> {
  return request({ url: '/medsupply/drugs', method: 'get', params })
}

/** 发药记录分页（依据：§9.3 S18） */
export function getDispenseListApi(params: Record<string, unknown>): Promise<PageResult<DrugDispense>> {
  return request({ url: '/admin/drug/dispense/list', method: 'get', params })
}

/** 处方审核（依据：§9.3 S16 / 迭代4 新增 reviewCheck 四查十对核查结果） */
export function reviewDispenseApi(
  prescriptionId: number,
  data: { action: string; reviewComment?: string; reviewCheck?: string },
): Promise<DrugDispense> {
  return request({ url: `/admin/drug/dispense/${prescriptionId}/review`, method: 'put', data })
}

/** 发药确认（依据：§9.3 S17） */
export function dispenseApi(prescriptionId: number): Promise<DrugDispense> {
  return request({ url: `/admin/drug/dispense/${prescriptionId}`, method: 'post' })
}

/** 医生开输液单（依据：门诊业务扩展 POST /api/infusion/orders） */
export function createInfusionOrderApi(data: {
  medicalRecordId: number
  patientId: number
  doctorId: number
  drugId: number
  drugName: string
  dosage?: string
  usageMethod?: string
  frequency?: string
  days?: number
  skinTestRequired?: number
  unitPrice?: number
}): Promise<InfusionOrder> {
  return request({ url: '/infusion/orders', method: 'post', data })
}

/** 患者查询本人输液单（依据：门诊业务扩展 GET /api/infusion/orders/my） */
export function getMyInfusionOrdersApi(params: Record<string, unknown>): Promise<PageResult<InfusionOrder>> {
  return request({ url: '/infusion/orders/my', method: 'get', params })
}

/** 输液单详情（依据：门诊业务扩展 GET /api/infusion/orders/{id}） */
export function getInfusionOrderApi(id: number): Promise<InfusionOrder> {
  return request({ url: `/infusion/orders/${id}`, method: 'get' })
}

/** 护士站待执行输液列表（依据：门诊业务扩展 GET /api/infusion/nurse/pending） */
export function getNursePendingInfusionsApi(params: Record<string, unknown>): Promise<PageResult<InfusionOrder>> {
  return request({ url: '/infusion/nurse/pending', method: 'get', params })
}

/** 护士执行记录（依据：门诊业务扩展 POST /api/infusion/orders/{id}/records） */
export function createInfusionRecordApi(
  id: number,
  data: { recordType: string; recordContent?: string; skinTestResult?: string; dropRate?: string; operatorName?: string },
): Promise<InfusionRecord> {
  return request({ url: `/infusion/orders/${id}/records`, method: 'post', data })
}

/** 检查技师执行登记（依据：门诊业务扩展 PUT /api/exam/application/{id}/execute） */
export function executeExamApplicationApi(id: number, data?: Record<string, unknown>): Promise<unknown> {
  return request({ url: `/exam/application/${id}/execute`, method: 'put', data })
}

/** 检查科申请列表（依据：门诊业务扩展 GET /api/exam/application/list） */
export function getExamApplicationListApi(params: Record<string, unknown>): Promise<PageResult<ExamApplicationVO> | ExamApplicationVO[]> {
  return request({ url: '/exam/application/list', method: 'get', params })
}

/** 检验科申请列表（依据：迭代4 GET /api/lab/application/list?status=，item_type=LAB） */
export function getLabApplicationsApi(params: Record<string, unknown>): Promise<PageResult<ExamApplicationVO> | ExamApplicationVO[]> {
  return request({ url: '/lab/application/list', method: 'get', params })
}

/** 检验执行登记（依据：迭代4 PUT /api/lab/application/{id}/execute） */
export function executeLabApi(id: number, data?: Record<string, unknown>): Promise<unknown> {
  return request({ url: `/lab/application/${id}/execute`, method: 'put', data })
}

/** 未缴费检查申请列表（依据：迭代4 GET /api/exam/application/unpaid?patientId=） */
export function getUnpaidExamApplicationsApi(patientId: number): Promise<ExamApplicationVO[]> {
  return request({ url: '/exam/application/unpaid', method: 'get', params: { patientId } })
}

/** 未缴费输液单列表（依据：迭代4 GET /api/infusion/orders/unpaid?patientId=） */
export function getUnpaidInfusionOrdersApi(patientId: number): Promise<InfusionOrder[]> {
  return request({ url: '/infusion/orders/unpaid', method: 'get', params: { patientId } })
}

/** 报告审核（依据：迭代4 PUT /api/admin/exam/report/{id}/audit?status=&auditComment=） */
export function auditExamReportApi(id: number, params: { status: string; auditComment?: string }): Promise<unknown> {
  return request({ url: `/admin/exam/report/${id}/audit`, method: 'put', params })
}

// ==================== 迭代6 功能补全：危急值 + 处方点评 ====================

/** 上报危急值 */
export function reportCriticalApi(data: Record<string, unknown>): Promise<CriticalValueVO> {
  return request({ url: '/medsupply/critical', method: 'post', data })
}

/** 危急值列表 */
export function listCriticalApi(params: Record<string, unknown>): Promise<CriticalValueVO[]> {
  return request({ url: '/medsupply/critical', method: 'get', params })
}

/** 复核危急值：CONFIRMED / RESOLVED */
export function confirmCriticalApi(id: number, action: string, comment?: string): Promise<CriticalValueVO> {
  return request({ url: `/medsupply/critical/${id}/confirm`, method: 'put', params: { action, comment } })
}

/** 创建处方点评 */
export function createPrescriptionReviewApi(data: Record<string, unknown>): Promise<PrescriptionReviewVO> {
  return request({ url: '/medsupply/prescription-review', method: 'post', data })
}

/** 处方点评列表 */
export function listPrescriptionReviewsApi(params: Record<string, unknown>): Promise<PrescriptionReviewVO[]> {
  return request({ url: '/medsupply/prescription-review', method: 'get', params })
}
