import service, { request } from './request'
import type { ChargeItem, InsuranceCatalog, InsuranceSettle, InsuranceSettleParam, PageResult } from '@/types'

/** ==================== 医保与财务（迭代11） ====================
 *  H4 收费项目字典  base=/api/admin/charge-item
 *  H1 医保目录映射  base=/api/admin/insurance-catalog
 *  H2/H3 医保结算  base=/api/payment/insurance
 *  统一走 src/utils/request 封装（Result 信封 code=0 成功，拦截器已解包 data）。
 */

/* ---------------- H4 收费项目字典（charge-item） ---------------- */

/** 新增收费项目（H4 POST /api/admin/charge-item） */
export function createChargeItemApi(data: {
  itemCode: string
  itemName: string
  category: string
  unit?: string
  unitPrice: number
}): Promise<ChargeItem> {
  return request({ url: '/admin/charge-item', method: 'post', data })
}

/** 编辑收费项目（H4 PUT /api/admin/charge-item/{id}） */
export function updateChargeItemApi(
  id: number,
  data: {
    itemCode?: string
    itemName?: string
    category?: string
    unit?: string
    unitPrice?: number
  },
): Promise<ChargeItem> {
  return request({ url: `/admin/charge-item/${id}`, method: 'put', data })
}

/** 调价（H4 PUT /api/admin/charge-item/{id}/adjust-price?newPrice=&reason=） */
export function adjustChargeItemPriceApi(id: number, newPrice: number, reason: string): Promise<unknown> {
  return request({ url: `/admin/charge-item/${id}/adjust-price`, method: 'put', params: { newPrice, reason } })
}

/** 启停收费项目（H4 PUT /api/admin/charge-item/{id}/status?status=ACTIVE|DEPRECATED） */
export function setChargeItemStatusApi(id: number, status: 'ACTIVE' | 'DEPRECATED'): Promise<unknown> {
  return request({ url: `/admin/charge-item/${id}/status`, method: 'put', params: { status } })
}

/** 收费项目分页（H4 GET /api/admin/charge-item/list?category=&keyword=&pageNo=&pageSize=） */
export function getChargeItemListApi(params: {
  category?: string
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<ChargeItem>> {
  return request({ url: '/admin/charge-item/list', method: 'get', params })
}

/* ---------------- H1 医保目录映射（insurance-catalog） ---------------- */

/** 录入医保目录映射（H1 POST /api/admin/insurance-catalog） */
export function createInsuranceCatalogApi(data: {
  itemType: string
  itemRefId?: number | string
  itemName: string
  catalogClass: string
  reimburseRatio?: number
}): Promise<InsuranceCatalog> {
  return request({ url: '/admin/insurance-catalog', method: 'post', data })
}

/** 编辑医保目录映射（H1 PUT /api/admin/insurance-catalog/{id}） */
export function updateInsuranceCatalogApi(
  id: number,
  data: {
    itemType?: string
    itemRefId?: number | string
    itemName?: string
    catalogClass?: string
    reimburseRatio?: number
  },
): Promise<InsuranceCatalog> {
  return request({ url: `/admin/insurance-catalog/${id}`, method: 'put', data })
}

/** 医保目录分页（H1 GET /api/admin/insurance-catalog/list?itemType=&catalogClass=&pageNo=&pageSize=） */
export function getInsuranceCatalogListApi(params: {
  itemType?: string
  catalogClass?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<InsuranceCatalog>> {
  return request({ url: '/admin/insurance-catalog/list', method: 'get', params })
}

/* ---------------- H2/H3 医保结算（payment/insurance） ---------------- */

/** 提交医保结算（H2 POST /api/payment/insurance/settle） */
export function insuranceSettleApi(data: InsuranceSettleParam): Promise<InsuranceSettle> {
  return request({ url: '/payment/insurance/settle', method: 'post', data })
}

/** 结算单详情（H3 GET /api/payment/insurance/settle/{id}） */
export function getInsuranceSettleDetailApi(id: number): Promise<InsuranceSettle> {
  return request({ url: `/payment/insurance/settle/${id}`, method: 'get' })
}

/** 结算单分页（H3 GET /api/payment/insurance/settle/list?patientId=&bizType=&pageNo=&pageSize=） */
export function getInsuranceSettleListApi(params: {
  patientId?: number
  bizType?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<InsuranceSettle>> {
  return request({ url: '/payment/insurance/settle/list', method: 'get', params })
}

/** 冲正（H3 POST /api/payment/insurance/settle/{id}/reverse，状态 → REVERSED） */
export function reverseInsuranceSettleApi(id: number): Promise<unknown> {
  return request({ url: `/payment/insurance/settle/${id}/reverse`, method: 'post' })
}

/** 结算凭证 PDF 直链（基于 baseURL 拼绝对地址；鉴权走 Authorization 头时直链仅作降级兜底） */
export function getVoucherPdfUrl(id: number): string {
  const base = (service.defaults.baseURL as string | undefined) || '/api'
  return `${base.replace(/\/+$/, '')}/payment/insurance/${id}/voucher-pdf`
}

/**
 * 拉取结算凭证 PDF（H3 GET /api/payment/insurance/{id}/voucher-pdf）。
 * 与 request.ts 约定一致：responseType=blob 时拦截器直接返回二进制（携带 Bearer 头）。
 * 预览方（window.open(blobUrl)）在拉取失败时降级用 getVoucherPdfUrl 直链新开窗口。
 */
export async function fetchVoucherPdfBlob(id: number): Promise<Blob> {
  const data = await request<Blob>({
    url: `/payment/insurance/${id}/voucher-pdf`,
    method: 'get',
    responseType: 'blob',
  })
  if (!(data instanceof Blob)) {
    throw new Error('票据响应格式异常')
  }
  // 后端异常时可能返回 application/json 的错误信封，此处转为人读错误
  if (data.type && data.type.includes('application/json')) {
    let message = '票据生成失败'
    try {
      const parsed = JSON.parse(await data.text()) as { message?: string }
      message = parsed.message || message
    } catch {
      // 非 JSON 文本则保留默认错误
    }
    throw new Error(message)
  }
  return data
}
