import { request } from './request'
import type { AllergyVO, PatientVO, VisitCardVO } from '@/types'

/** 本人档案（依据：API接口文档.md §2 #13 GET /api/patient/profile） */
export function getProfileApi(): Promise<PatientVO> {
  return request({ url: '/patient/profile', method: 'get' })
}

/** 编辑本人档案（依据：§2 #14 PUT /api/patient/profile） */
export function updateProfileApi(data: Record<string, unknown>): Promise<PatientVO> {
  return request({ url: '/patient/profile', method: 'put', data })
}

/** 就诊卡列表（依据：§2 #15 GET /api/patient/visit-cards） */
export function getVisitCardsApi(): Promise<VisitCardVO[]> {
  return request({ url: '/patient/visit-cards', method: 'get' })
}

/** 提交实名认证（依据：§2 #16 POST /api/patient/realname） */
export function submitRealnameApi(data: { name: string; idCard: string; idCardFrontUrl?: string; idCardBackUrl?: string }): Promise<unknown> {
  return request({ url: '/patient/realname', method: 'post', data })
}

/** 实名认证审核（依据：§2 #17 PUT /api/patient/realname/{patientId}/review） */
export function reviewRealnameApi(patientId: number, data: { verifyStatus: number; verifyComment: string }): Promise<unknown> {
  return request({ url: `/patient/realname/${patientId}/review`, method: 'put', data })
}

/** 过敏史列表（依据：§2 #18 GET /api/patient/allergies） */
export function getAllergiesApi(): Promise<AllergyVO[]> {
  return request({ url: '/patient/allergies', method: 'get' })
}

/** 新增过敏史（依据：§2 #19 POST /api/patient/allergies） */
export function addAllergyApi(data: { allergen: string; reactionType?: string; severity?: string }): Promise<AllergyVO> {
  return request({ url: '/patient/allergies', method: 'post', data })
}

/** 删除过敏史（依据：§2 #20 DELETE /api/patient/allergies/{id}） */
export function deleteAllergyApi(id: number): Promise<unknown> {
  return request({ url: `/patient/allergies/${id}`, method: 'delete' })
}

/** 文件上传（依据：§2 #21 POST /api/patient/upload） */
export function uploadApi(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return request({ url: '/patient/upload', method: 'post', data: form, headers: { 'Content-Type': 'multipart/form-data' } })
}
