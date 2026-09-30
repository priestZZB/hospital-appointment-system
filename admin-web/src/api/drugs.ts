import { request } from './request'
import type {
  DecoctionOrder,
  DoctorAntibioticAuth,
  DrugBatch,
  DrugReturn,
  DrugRule,
  DrugTransfer,
  NarcoticRegister,
  PageResult,
} from '@/types'

// ==================== 迭代7 药事扩展：批次 / 效期 / 报损 ====================

/** 批次采购入库（插批次 + 联动总库存 + 麻精登记，依据：DrugBatchController POST /api/admin/drug/batch/inbound） */
export function batchInboundApi(data: {
  drugId: number
  batchNo: string
  supplier: string
  quantity: number
  productionDate?: string
  expiryDate?: string
}): Promise<DrugBatch> {
  return request({ url: '/admin/drug/batch/inbound', method: 'post', data })
}

/** 效期预警（30 天内到期批次，依据：GET /api/admin/drug/batch/expiring） */
export function listExpiringBatchesApi(): Promise<DrugBatch[]> {
  return request({ url: '/admin/drug/batch/expiring', method: 'get' })
}

/** 批次报损（清零 + 库存扣减 + 麻精 SCRAP 登记，依据：POST /api/admin/drug/batch/{id}/scrap） */
export function scrapBatchApi(id: number, reason?: string): Promise<DrugBatch> {
  return request({ url: `/admin/drug/batch/${id}/scrap`, method: 'post', data: { reason } })
}

/** 批次分页（依据：GET /api/admin/drug/batch/list?pageNo&pageSize&drugId&status） */
export function getBatchListApi(params: {
  drugId?: number
  status?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<DrugBatch>> {
  return request({ url: '/admin/drug/batch/list', method: 'get', params })
}

// ==================== 退药冲账 ====================

/** 退药冲账（库存回冲 + 批次回冲 + 金额冲账，依据：DrugReturnController POST /api/admin/drug/return） */
export function createDrugReturnApi(data: {
  prescriptionId: number
  patientId: number
  drugId: number
  quantity: number
  refundAmount: number
  reason?: string
}): Promise<DrugReturn> {
  return request({ url: '/admin/drug/return', method: 'post', data })
}

/** 退药单分页（依据：GET /api/admin/drug/return/list?patientId=&status=&pageNo=&pageSize=） */
export function getDrugReturnListApi(params: {
  patientId?: number
  status?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<DrugReturn>> {
  return request({ url: '/admin/drug/return/list', method: 'get', params })
}

// ==================== 药品调拨（药库→药房→科室） ====================

/** 创建调拨单（简化模式：总库存不变、批次不拆分，依据：DrugTransferController POST /api/admin/drug/transfer） */
export function createDrugTransferApi(data: {
  drugId: number
  quantity: number
  fromLocation: string
  toLocation: string
  batchNo?: string
}): Promise<DrugTransfer> {
  return request({ url: '/admin/drug/transfer', method: 'post', data })
}

/** 调拨单分页（依据：GET /api/admin/drug/transfer/list?drugId=&status=&pageNo=&pageSize=） */
export function getDrugTransferListApi(params: {
  drugId?: number
  status?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<DrugTransfer>> {
  return request({ url: '/admin/drug/transfer/list', method: 'get', params })
}

// ==================== 麻精药品五专登记 ====================

/** 五专登记册查询（按药品，最新流水在前，依据：NarcoticRegisterController GET /api/admin/drug/narcotic/list?drugId=） */
export function getNarcoticRegisterListApi(drugId: number): Promise<NarcoticRegister[]> {
  return request({ url: '/admin/drug/narcotic/list', method: 'get', params: { drugId } })
}

// ==================== CDSS 合理用药规则 ====================

/** 新增规则（依据：DrugRuleController POST /api/admin/drug/rule） */
export function createDrugRuleApi(data: Partial<DrugRule>): Promise<DrugRule> {
  return request({ url: '/admin/drug/rule', method: 'post', data })
}

/** 更新规则（依据：PUT /api/admin/drug/rule） */
export function updateDrugRuleApi(data: Partial<DrugRule>): Promise<DrugRule> {
  return request({ url: '/admin/drug/rule', method: 'put', data })
}

/** 删除规则（软删 status=0，依据：DELETE /api/admin/drug/rule/{id}） */
export function deleteDrugRuleApi(id: number): Promise<void> {
  return request({ url: `/admin/drug/rule/${id}`, method: 'delete' })
}

/** 启用规则列表（可按类型/药品筛选，依据：GET /api/admin/drug/rule/list?ruleType=&drugId=） */
export function getDrugRuleListApi(params: { ruleType?: string; drugId?: number }): Promise<DrugRule[]> {
  return request({ url: '/admin/drug/rule/list', method: 'get', params })
}

// ==================== 抗菌药物分级授权（仅管理员） ====================

/** 授权（doctor_id 唯一，重复授权即更新，依据：POST /api/admin/drug/antibiotic-auth/grant） */
export function grantAntibioticAuthApi(data: { doctorId: number; maxLevel: string }): Promise<DoctorAntibioticAuth> {
  return request({ url: '/admin/drug/antibiotic-auth/grant', method: 'post', data })
}

/** 撤销授权（依据：DELETE /api/admin/drug/antibiotic-auth/{doctorId}） */
export function revokeAntibioticAuthApi(doctorId: number): Promise<void> {
  return request({ url: `/admin/drug/antibiotic-auth/${doctorId}`, method: 'delete' })
}

/** 授权列表（依据：GET /api/admin/drug/antibiotic-auth/list） */
export function getAntibioticAuthListApi(): Promise<DoctorAntibioticAuth[]> {
  return request({ url: '/admin/drug/antibiotic-auth/list', method: 'get' })
}

// ==================== 中药代煎 ====================

/** 代煎下单（body 未带 patientId 时后端解析本人档案，依据：DecoctionController POST /api/medsupply/decoction） */
export function createDecoctionApi(data: {
  prescriptionId: number
  patientId?: number
  doses: number
  decoctionType: string
  remark?: string
}): Promise<DecoctionOrder> {
  return request({ url: '/medsupply/decoction', method: 'post', data })
}

/** 代煎状态流转（药师，依据：PUT /api/medsupply/decoction/{id}/handle） */
export function handleDecoctionApi(id: number, action: string): Promise<DecoctionOrder> {
  return request({ url: `/medsupply/decoction/${id}/handle`, method: 'put', data: { action } })
}

/** 患者本人代煎订单分页（依据：GET /api/medsupply/decoction/my） */
export function getMyDecoctionListApi(params: { pageNo?: number; pageSize?: number }): Promise<PageResult<DecoctionOrder>> {
  return request({ url: '/medsupply/decoction/my', method: 'get', params })
}

/** 药师代煎订单分页（按状态筛选，依据：GET /api/medsupply/decoction/list） */
export function getDecoctionListApi(params: {
  status?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<DecoctionOrder>> {
  return request({ url: '/medsupply/decoction/list', method: 'get', params })
}

// ==================== 用药指导单 ====================

/** 按处方生成并下载用药指导单 PDF（二进制流，依据：DrugGuidanceController GET /api/medsupply/guidance/{prescriptionId}/pdf） */
export function downloadDrugGuidanceApi(prescriptionId: number): Promise<Blob> {
  return request({ url: `/medsupply/guidance/${prescriptionId}/pdf`, method: 'get', responseType: 'blob' })
}

// ==================== 药品分类 / 管控级别 ====================

/** 药品三分类/管控级别/抗菌分级管理（依据：DrugController PUT /api/admin/drug/{id}/type） */
export function updateDrugTypeApi(
  id: number,
  data: { drugType?: string; controlLevel?: string; antibioticLevel?: string },
): Promise<void> {
  return request({ url: `/admin/drug/${id}/type`, method: 'put', data })
}
