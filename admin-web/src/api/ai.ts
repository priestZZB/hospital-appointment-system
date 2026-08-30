import { request } from './request'
import type { TriageResultVO } from '@/types'

/** AI 智能分诊（依据：API接口文档.md §6 #71 POST /api/ai/triage） */
export function triageApi(symptom: string): Promise<TriageResultVO> {
  return request({ url: '/ai/triage', method: 'post', data: { symptom } })
}
