/**
 * AI 智能层接口（迭代16 B2）：多轮问诊 / 候诊时长 / 门诊量 / AI 用药 / 报告摘要
 */
import { request } from './request'
import type { PageResult } from '@/types'

/** ---------- B2-1 多轮问诊 ---------- */
export interface ConsultMessage {
  role: string
  content: string
}

export function startConsultApi(data: { patientId: number; symptom: string }) {
  return request<Record<string, unknown>>({ url: '/ai/consult/start', method: 'post', data })
}
export function replyConsultApi(sessionNo: string, data: { content: string }) {
  return request<Record<string, unknown>>({ url: `/ai/consult/${sessionNo}/reply`, method: 'post', data })
}
export function getConsultDetailApi(sessionNo: string) {
  return request<Record<string, unknown>>({ url: `/ai/consult/${sessionNo}`, method: 'get' })
}

/** ---------- B2-2/B2-3 智能预测 ---------- */
export function predictWaitTimeApi(data: { queueLength: number; avgMinutes: number; windows?: number }) {
  return request<Record<string, unknown>>({ url: '/ai/wait-time/predict', method: 'post', data })
}
export function predictVisitVolumeApi(data: { recentDaily: number[] }) {
  return request<Record<string, unknown>>({ url: '/ai/visit-volume/predict', method: 'post', data })
}
export function getPredictionListApi(params: Record<string, unknown>) {
  return request<PageResult<Record<string, unknown>>>({ url: '/ai/predictions', method: 'get', params })
}

/** ---------- B2-4/B2-5 AI 医助 ---------- */
export function drugRecommendApi(data: { diagnosis: string; allergy?: string }) {
  return request<Record<string, unknown>>({ url: '/ai/drug-recommend', method: 'post', data })
}
export function reportSummaryApi(data: { title?: string; chiefComplaint: string; diagnosis: string; advice?: string }) {
  return request<Record<string, unknown>>({ url: '/ai/report-summary', method: 'post', data })
}
