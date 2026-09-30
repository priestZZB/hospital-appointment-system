import { request } from './request'
import type {
  CloudLink,
  CloudViewVO,
  ExamReservation,
  ImageSeries,
  PageResult,
  ReportTemplate,
  ResultItem,
  Specimen,
  TemplateApplyVO,
} from '@/types'

// ==================== 标本采集（/api/admin/lab/specimen） ====================

/** 标本采集登记（依据：迭代8 POST /api/admin/lab/specimen/collect，返回 SP 前缀标本号，状态 COLLECTED） */
export function collectSpecimenApi(data: {
  applicationId: number
  /** BLOOD / URINE / STOOL / SPUTUM / OTHER */
  specimenType: string
  container?: string
  collectSite?: string
}): Promise<Specimen> {
  return request({ url: '/admin/lab/specimen/collect', method: 'post', data })
}

/** 标本核收 / 拒收（依据：PUT /api/admin/lab/specimen/{id}/receive；accept=true 核收 RECEIVED，false 拒收 REJECTED） */
export function receiveSpecimenApi(id: number, data: { accept: boolean; remark?: string }): Promise<Specimen> {
  return request({ url: `/admin/lab/specimen/${id}/receive`, method: 'put', data })
}

/** 标本进入检测（依据：PUT /api/admin/lab/specimen/{id}/testing，状态置 TESTING） */
export function testingSpecimenApi(id: number): Promise<Specimen> {
  return request({ url: `/admin/lab/specimen/${id}/testing`, method: 'put' })
}

/** 标本详情（依据：GET /api/admin/lab/specimen/{id}） */
export function getSpecimenApi(id: number): Promise<Specimen> {
  return request({ url: `/admin/lab/specimen/${id}`, method: 'get' })
}

/** 标本列表（依据：GET /api/admin/lab/specimen/list?pageNo&pageSize&status&specimenType；后端兼容分页对象 / 数组两种返回） */
export function getSpecimenListApi(params: Record<string, unknown>): Promise<PageResult<Specimen> | Specimen[]> {
  return request({ url: '/admin/lab/specimen/list', method: 'get', params })
}

// ==================== 检验结果（/api/admin/lab/result） ====================

/** 检验结果项定义（保存请求体） */
export interface LabResultItemPayload {
  itemCode: string
  itemName: string
  resultValue?: string
  unit?: string
  refRange?: string
  sortOrder?: number
}

/** 保存检验结果明细（依据：迭代8 POST /api/admin/lab/result/report/{reportId}；覆盖式全量替换，abnormalFlag 后端按参考范围自动判定 ↑↓） */
export function saveLabResultApi(reportId: number, data: { items: LabResultItemPayload[] }): Promise<unknown> {
  return request({ url: `/admin/lab/result/report/${reportId}`, method: 'post', data })
}

/** 查询检验结果明细（依据：GET /api/admin/lab/result/report/{reportId}） */
export function getLabResultApi(reportId: number): Promise<ResultItem[]> {
  return request({ url: `/admin/lab/result/report/${reportId}`, method: 'get' })
}

// ==================== 化验单 PDF（/api/admin/lab） ====================

/** 下载化验单 PDF（依据：GET /api/admin/lab/report/{reportId}/pdf，二进制流，参考 guidance PDF 封装 responseType: 'blob'） */
export function downloadLabReportPdfApi(reportId: number): Promise<Blob> {
  return request({ url: `/admin/lab/report/${reportId}/pdf`, method: 'get', responseType: 'blob' })
}

// ==================== 检查预约（/api/admin/exam/reservation） ====================

/** 新增检查预约（依据：迭代8 POST /api/admin/exam/reservation；reserveDate=yyyy-MM-dd，timeSlot 如 08:00-08:30） */
export function createExamReservationApi(data: {
  applicationId: number
  reserveDate: string
  timeSlot: string
  room?: string
}): Promise<ExamReservation> {
  return request({ url: '/admin/exam/reservation', method: 'post', data })
}

/** 检查报到（依据：PUT /api/admin/exam/reservation/{id}/checkin，状态 BOOKED → CHECKED_IN） */
export function checkinExamReservationApi(id: number): Promise<ExamReservation> {
  return request({ url: `/admin/exam/reservation/${id}/checkin`, method: 'put' })
}

/** 预约状态流转（依据：PUT /api/admin/exam/reservation/{id}/status?action=DONE|CANCELLED） */
export function updateExamReservationStatusApi(id: number, action: 'DONE' | 'CANCELLED'): Promise<ExamReservation> {
  return request({ url: `/admin/exam/reservation/${id}/status`, method: 'put', params: { action } })
}

/** 预约详情（依据：GET /api/admin/exam/reservation/{id}） */
export function getExamReservationApi(id: number): Promise<ExamReservation> {
  return request({ url: `/admin/exam/reservation/${id}`, method: 'get' })
}

/** 预约列表（依据：GET /api/admin/exam/reservation/list?pageNo&pageSize&date&status；后端兼容分页对象 / 数组两种返回） */
export function getExamReservationListApi(
  params: Record<string, unknown>,
): Promise<PageResult<ExamReservation> | ExamReservation[]> {
  return request({ url: '/admin/exam/reservation/list', method: 'get', params })
}

// ==================== 影像序列（/api/admin/exam/image） ====================

/** 上传影像序列（依据：迭代8 POST /api/admin/exam/image/upload/{applicationId}?modality=CT&description=xxx；
 *  multipart 字段名 images，可多文件 jpg，返回 IM 前缀序列号） */
export function uploadExamImagesApi(
  applicationId: number,
  files: File[],
  modality: string,
  description?: string,
): Promise<ImageSeries> {
  const form = new FormData()
  files.forEach((f) => form.append('images', f))
  return request({
    url: `/admin/exam/image/upload/${applicationId}`,
    method: 'post',
    params: { modality, description },
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 影像序列详情（依据：GET /api/admin/exam/image/series/{id}） */
export function getExamSeriesApi(id: number): Promise<ImageSeries> {
  return request({ url: `/admin/exam/image/series/${id}`, method: 'get' })
}

/** 序列内单张影像（依据：GET /api/admin/exam/image/series/{id}/image/{index}，image/jpeg 二进制流，需携带 token，走 blob 通道） */
export function getExamSeriesImageApi(id: number, index: number): Promise<Blob> {
  return request({ url: `/admin/exam/image/series/${id}/image/${index}`, method: 'get', responseType: 'blob' })
}

/** 按申请ID查询影像序列列表（依据：GET /api/admin/exam/image/list?applicationId=） */
export function getExamSeriesListApi(applicationId: number): Promise<ImageSeries[]> {
  return request({ url: '/admin/exam/image/list', method: 'get', params: { applicationId } })
}

// ==================== 报告模板（/api/admin/exam/template） ====================

/** 新增报告模板（依据：迭代8 POST /api/admin/exam/template；templateType: FINDING-所见 / CONCLUSION-印象） */
export function createReportTemplateApi(data: {
  modality: string
  bodyPart?: string
  templateType: string
  content: string
  status?: number
}): Promise<ReportTemplate> {
  return request({ url: '/admin/exam/template', method: 'post', data })
}

/** 编辑报告模板（依据：PUT /api/admin/exam/template/{id}） */
export function updateReportTemplateApi(id: number, data: Record<string, unknown>): Promise<ReportTemplate> {
  return request({ url: `/admin/exam/template/${id}`, method: 'put', data })
}

/** 删除报告模板（依据：DELETE /api/admin/exam/template/{id}） */
export function deleteReportTemplateApi(id: number): Promise<unknown> {
  return request({ url: `/admin/exam/template/${id}`, method: 'delete' })
}

/** 模板列表（依据：GET /api/admin/exam/template/list?modality=） */
export function getReportTemplateListApi(params: Record<string, unknown>): Promise<ReportTemplate[]> {
  return request({ url: '/admin/exam/template/list', method: 'get', params })
}

/** 套用模板（依据：GET /api/admin/exam/template/apply?modality=&bodyPart=，回传 {findings, conclusion}） */
export function applyReportTemplateApi(params: { modality: string; bodyPart?: string }): Promise<TemplateApplyVO> {
  return request({ url: '/admin/exam/template/apply', method: 'get', params })
}

// ==================== 云影像（/api/medsupply/cloud） ====================

/** 生成云影像分享链接（依据：迭代8 POST /api/medsupply/cloud/{applicationId}/link，返回 {code, url}） */
export function createCloudLinkApi(applicationId: number): Promise<CloudLink> {
  return request({ url: `/medsupply/cloud/${applicationId}/link`, method: 'post' })
}

/** 云影像浏览数据（依据：GET /api/medsupply/cloud/view/{code}，返回序列列表 + 检查报告（可空）） */
export function getCloudViewApi(code: string): Promise<CloudViewVO> {
  return request({ url: `/medsupply/cloud/view/${code}`, method: 'get' })
}
