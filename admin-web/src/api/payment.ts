import { request } from './request'
import type {
  NotificationVO,
  PaymentOrderVO,
  SettleRecordVO,
  SettleSummaryVO,
  TreatmentOrderItem,
  TreatmentOrderVO,
} from '@/types'

/** 模拟支付（依据：API接口文档.md §4 #54 POST /api/payment/pay） */
export function payApi(orderId: number): Promise<unknown> {
  return request({ url: '/payment/pay', method: 'post', params: { orderId } })
}

/** 订单状态（依据：§4 #55 GET /api/payment/status/{orderId}） */
export function getPaymentStatusApi(orderId: number): Promise<PaymentOrderVO> {
  return request({ url: `/payment/status/${orderId}`, method: 'get' })
}

/** 挂号凭证 PDF 下载（依据：§4 #56 GET /api/payment/receipt/{orderId}） */
export function getReceiptUrl(orderId: number): string {
  return `/api/payment/receipt/${orderId}`
}

/** 手动触发扫表关单（依据：§4 #57 POST /api/payment/scan-timeout） */
export function scanTimeoutApi(): Promise<unknown> {
  return request({ url: '/payment/scan-timeout', method: 'post' })
}

/** 站内信列表（依据：§4 #58 GET /api/payment/notifications） */
export function getNotificationsApi(): Promise<NotificationVO[]> {
  return request({ url: '/payment/notifications', method: 'get' })
}

/** 标记已读（依据：§4 #59 PUT /api/payment/notifications/{id}/read） */
export function readNotificationApi(id: number): Promise<unknown> {
  return request({ url: `/payment/notifications/${id}/read`, method: 'put' })
}

/** 创建诊疗费订单（依据：门诊业务扩展 POST /api/payment/treatment/order） */
export function createTreatmentOrderApi(data: {
  orderType: 'DRUG' | 'EXAM' | 'INFUSION' | 'TREATMENT'
  items: TreatmentOrderItem[]
  relatedId?: number
}): Promise<TreatmentOrderVO> {
  return request({ url: '/payment/treatment/order', method: 'post', data })
}

/** 支付诊疗费（依据：门诊业务扩展 POST /api/payment/treatment/pay/{orderId}） */
export function payTreatmentOrderApi(orderId: number): Promise<unknown> {
  return request({ url: `/payment/treatment/pay/${orderId}`, method: 'post' })
}

/** 收费员建单（依据：迭代4 POST /api/payment/cashier/order） */
export function cashierCreateOrderApi(data: {
  patientId: number
  orderType: 'DRUG' | 'EXAM' | 'INFUSION'
  items: TreatmentOrderItem[]
  relatedId?: number
}): Promise<TreatmentOrderVO> {
  return request({ url: '/payment/cashier/order', method: 'post', data })
}

/** 收费员收费（依据：迭代4 POST /api/payment/cashier/pay/{orderId}） */
export function cashierPayApi(orderId: number): Promise<unknown> {
  return request({ url: `/payment/cashier/pay/${orderId}`, method: 'post' })
}

/** 收费员退费（依据：迭代4 POST /api/payment/cashier/refund） */
export function cashierRefundApi(data: { orderId: number; refundReason?: string }): Promise<unknown> {
  return request({ url: '/payment/cashier/refund', method: 'post', data })
}

/** 日结汇总（依据：迭代4 GET /api/payment/cashier/settle/today） */
export function getSettleTodayApi(): Promise<SettleSummaryVO> {
  return request({ url: '/payment/cashier/settle/today', method: 'get' })
}

/** 生成日结单（依据：迭代4 POST /api/payment/cashier/settle） */
export function createSettleApi(): Promise<SettleRecordVO> {
  return request({ url: '/payment/cashier/settle', method: 'post' })
}
