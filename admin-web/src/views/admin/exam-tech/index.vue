<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { WMessage, WMessageBox } from 'win-design-next'
import type { UploadFile, UploadFiles, UploadInstance } from 'win-design-next'
import { Copy, Delete, Link, Picture, Plus, Refresh, Search, Upload } from '@win-design-next/icons-vue'
// 别名导入：避免与本地命名冲突（包内真实存在 Download 图标）
import { Download as DownloadIcon } from '@win-design-next/icons-vue'
import {
  applyReportTemplateApi,
  checkinExamReservationApi,
  collectSpecimenApi,
  createCloudLinkApi,
  createExamReservationApi,
  createReportTemplateApi,
  deleteReportTemplateApi,
  downloadLabReportPdfApi,
  getExamReservationListApi,
  getExamSeriesListApi,
  getLabResultApi,
  getReportTemplateListApi,
  getSpecimenListApi,
  receiveSpecimenApi,
  saveLabResultApi,
  testingSpecimenApi,
  updateExamReservationStatusApi,
  updateReportTemplateApi,
  uploadExamImagesApi,
} from '@/api/lis'
import { getLabApplicationsApi } from '@/api/medsupply'
import { useUserStore } from '@/stores/user'
import type {
  CloudLink,
  ExamApplicationVO,
  ExamReservation,
  ImageSeries,
  PageResult,
  ReportTemplate,
  ResultItem,
  Specimen,
} from '@/types'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const router = useRouter()
const userStore = useUserStore()

/** 医技工作台入口权限：检验技师 / 检查技师 / 管理员（无权限页面加载后展示空态） */
const canAccess = computed(() => userStore.isLabTech || userStore.isExamTech || userStore.isAdmin)

const tab = ref('specimen')
/** Tab 懒加载：首次激活再拉数据，避免首屏请求过多 */
const loadedTabs = new Set<string>(['specimen'])

function onTabChange(name: string | number) {
  const key = String(name)
  if (loadedTabs.has(key)) return
  loadedTabs.add(key)
  if (key === 'reservation') {
    fetchReservations()
  } else if (key === 'template') {
    fetchTemplates()
  }
  // 结果录入 / 影像序列 需先输入报告ID / 申请ID，不自动查询
}

// ==================== 通用工具 ====================

/** 后端分页兼容：分页对象（records/total 或 list/total）与纯数组两种返回 */
function normalizePage<T>(page: PageResult<T> | T[]): { records: T[]; total: number } {
  if (Array.isArray(page)) return { records: page, total: page.length }
  const records = page.records ?? (page as unknown as { list?: T[] }).list ?? []
  return { records, total: page.total ?? records.length }
}

function todayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// ==================== Tab1 标本采集 ====================

const SPECIMEN_TYPE_OPTIONS = [
  { value: 'BLOOD', label: '血液' },
  { value: 'URINE', label: '尿液' },
  { value: 'STOOL', label: '粪便' },
  { value: 'SPUTUM', label: '痰液' },
  { value: 'OTHER', label: '其他' },
] as const

function specimenTypeText(type?: string): string {
  return SPECIMEN_TYPE_OPTIONS.find((o) => o.value === type)?.label || type || '-'
}

const SPECIMEN_STATUS_META: Record<string, { text: string; type: TagType }> = {
  COLLECTED: { text: '已采集', type: 'primary' },
  RECEIVED: { text: '已核收', type: 'success' },
  TESTING: { text: '检测中', type: 'warning' },
  REJECTED: { text: '已拒收', type: 'danger' },
}

function specimenStatusMeta(status?: string): { text: string; type: TagType } {
  return SPECIMEN_STATUS_META[status ?? ''] || { text: status || '-', type: 'info' as TagType }
}

// 待采检验申请（GET /lab/application/list?status=PENDING）
const labAppLoading = ref(false)
const labApps = ref<ExamApplicationVO[]>([])

async function fetchLabApplications() {
  labAppLoading.value = true
  try {
    const page = await getLabApplicationsApi({ status: 'PENDING', offset: 0, limit: 10 })
    labApps.value = normalizePage<ExamApplicationVO>(page).records
  } catch (e) {
    WMessage.error((e as Error).message || '检验申请列表加载失败')
  } finally {
    labAppLoading.value = false
  }
}

// 标本列表（GET /admin/lab/specimen/list）
const spLoading = ref(false)
const specimens = ref<Specimen[]>([])
const spTotal = ref(0)
const spPage = ref(1)
const spSize = ref(10)
const spQuery = reactive({ status: '', specimenType: '' })

async function fetchSpecimens() {
  spLoading.value = true
  try {
    const page = await getSpecimenListApi({
      pageNo: spPage.value,
      pageSize: spSize.value,
      status: spQuery.status || undefined,
      specimenType: spQuery.specimenType || undefined,
    })
    const { records, total } = normalizePage<Specimen>(page)
    specimens.value = records
    spTotal.value = total
  } catch (e) {
    WMessage.error((e as Error).message || '标本列表加载失败')
  } finally {
    spLoading.value = false
  }
}

// 按申请ID采集对话框
const collectDialogVisible = ref(false)
const collectSubmitting = ref(false)
const collectForm = reactive({
  applicationId: undefined as number | undefined,
  specimenType: 'BLOOD',
  container: '',
  collectSite: '',
})

function openCollectDialog(row?: ExamApplicationVO) {
  collectForm.applicationId = row?.id
  collectForm.specimenType = 'BLOOD'
  collectForm.container = ''
  collectForm.collectSite = ''
  collectDialogVisible.value = true
}

async function saveCollect() {
  if (!collectForm.applicationId) {
    WMessage.warning('检验申请ID必填')
    return
  }
  if (!collectForm.specimenType) {
    WMessage.warning('请选择标本类型')
    return
  }
  collectSubmitting.value = true
  try {
    await collectSpecimenApi({
      applicationId: collectForm.applicationId,
      specimenType: collectForm.specimenType,
      container: collectForm.container.trim() || undefined,
      collectSite: collectForm.collectSite.trim() || undefined,
    })
    WMessage.success('标本采集登记成功')
    collectDialogVisible.value = false
    fetchSpecimens()
  } catch (e) {
    WMessage.error((e as Error).message || '标本采集失败')
  } finally {
    collectSubmitting.value = false
  }
}

// 核收 / 拒收对话框（PUT /{id}/receive）
const receiveDialogVisible = ref(false)
const receiveSubmitting = ref(false)
const receiveRow = ref<Specimen | null>(null)
const receiveAccept = ref(true)
const receiveRemark = ref('')

function openReceiveDialog(row: Specimen, accept: boolean) {
  receiveRow.value = row
  receiveAccept.value = accept
  receiveRemark.value = accept ? '核对无误' : ''
  receiveDialogVisible.value = true
}

async function confirmReceive() {
  const row = receiveRow.value
  if (!row?.id) return
  if (!receiveAccept.value && !receiveRemark.value.trim()) {
    WMessage.warning('拒收必须填写拒收原因')
    return
  }
  receiveSubmitting.value = true
  try {
    await receiveSpecimenApi(row.id, { accept: receiveAccept.value, remark: receiveRemark.value.trim() || undefined })
    WMessage.success(receiveAccept.value ? '标本核收成功' : '标本已拒收')
    receiveDialogVisible.value = false
    fetchSpecimens()
  } catch (e) {
    WMessage.error((e as Error).message || '标本核收操作失败')
  } finally {
    receiveSubmitting.value = false
  }
}

// 进入检测（PUT /{id}/testing）
async function handleTesting(row: Specimen) {
  if (!row.id) return
  try {
    await WMessageBox.confirm(`确认将标本 ${row.specimenNo || row.id} 置为「检测中」吗？`, '进入检测', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await testingSpecimenApi(row.id)
    WMessage.success('标本已进入检测')
    fetchSpecimens()
  } catch (e) {
    WMessage.error((e as Error).message || '进入检测失败')
  }
}

// ==================== Tab2 结果录入 ====================

const reportId = ref<number | undefined>()
const resultLoading = ref(false)
const resultRows = ref<ResultItem[]>([])
const resultLoaded = ref(false)

async function loadResult() {
  if (!reportId.value) {
    WMessage.warning('请输入检验报告ID')
    return
  }
  resultLoading.value = true
  try {
    const items = await getLabResultApi(reportId.value)
    resultRows.value = (items || []).map((it) => ({ ...it }))
    resultLoaded.value = true
    if (!resultRows.value.length) WMessage.info('该报告暂无结果明细，可直接添加行录入')
  } catch (e) {
    WMessage.error((e as Error).message || '检验结果加载失败')
  } finally {
    resultLoading.value = false
  }
}

function addResultRow() {
  resultRows.value.push({
    itemCode: '',
    itemName: '',
    resultValue: '',
    unit: '',
    refRange: '',
    sortOrder: resultRows.value.length + 1,
  })
}

function removeResultRow(index: number) {
  resultRows.value.splice(index, 1)
}

async function saveResult() {
  if (!reportId.value) {
    WMessage.warning('请输入检验报告ID')
    return
  }
  const items = resultRows.value.filter((r) => r.itemCode.trim() || r.itemName.trim())
  if (!items.length) {
    WMessage.warning('请至少填写一行结果（项目编码 / 项目名称）')
    return
  }
  const missing = items.find((r) => !r.itemCode.trim() || !r.itemName.trim())
  if (missing) {
    WMessage.warning('存在未填写完整「项目编码 / 项目名称」的明细行')
    return
  }
  resultLoading.value = true
  try {
    await saveLabResultApi(reportId.value, {
      items: items.map((r, idx) => ({
        itemCode: r.itemCode.trim(),
        itemName: r.itemName.trim(),
        resultValue: r.resultValue ?? '',
        unit: r.unit ?? '',
        refRange: r.refRange ?? '',
        sortOrder: r.sortOrder ?? idx + 1,
      })),
    })
    WMessage.success('检验结果保存成功（覆盖式）')
    const items2 = await getLabResultApi(reportId.value)
    resultRows.value = (items2 || []).map((it) => ({ ...it }))
  } catch (e) {
    WMessage.error((e as Error).message || '检验结果保存失败')
  } finally {
    resultLoading.value = false
  }
}

/** 异常标识 tag（后端自动判定 ↑↓） */
function abnormalMeta(flag?: string): { text: string; type: TagType } | undefined {
  if (flag === '↑' || flag === 'H') return { text: '↑ 偏高', type: 'danger' }
  if (flag === '↓' || flag === 'L') return { text: '↓ 偏低', type: 'warning' }
  return undefined
}

async function downloadLabPdf() {
  if (!reportId.value) {
    WMessage.warning('请输入检验报告ID')
    return
  }
  try {
    const blob = await downloadLabReportPdfApi(reportId.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `lab-report-${reportId.value}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    WMessage.error((e as Error).message || '化验单下载失败')
  }
}

// ==================== Tab3 检查预约 ====================

const RESERVATION_STATUS_META: Record<string, { text: string; type: TagType }> = {
  BOOKED: { text: '已预约', type: 'primary' },
  CHECKED_IN: { text: '已报到', type: 'warning' },
  DONE: { text: '已完成', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' },
}

function reservationStatusMeta(status?: string): { text: string; type: TagType } {
  return RESERVATION_STATUS_META[status ?? ''] || { text: status || '-', type: 'info' as TagType }
}

/** 预约时段：08:00-08:30 起 30 分钟步进至 18:00 */
const TIME_SLOTS: string[] = (() => {
  const slots: string[] = []
  for (let m = 8 * 60; m < 18 * 60; m += 30) {
    const s = `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
    const e = `${String(Math.floor((m + 30) / 60)).padStart(2, '0')}:${String((m + 30) % 60).padStart(2, '0')}`
    slots.push(`${s}-${e}`)
  }
  return slots
})()

const resLoading = ref(false)
const reservations = ref<ExamReservation[]>([])
const resTotal = ref(0)
const resPage = ref(1)
const resSize = ref(10)
const resQuery = reactive({ date: todayStr(), status: '' })

async function fetchReservations() {
  resLoading.value = true
  try {
    const page = await getExamReservationListApi({
      pageNo: resPage.value,
      pageSize: resSize.value,
      date: resQuery.date || undefined,
      status: resQuery.status || undefined,
    })
    const { records, total } = normalizePage<ExamReservation>(page)
    reservations.value = records
    resTotal.value = total
  } catch (e) {
    WMessage.error((e as Error).message || '预约列表加载失败')
  } finally {
    resLoading.value = false
  }
}

const resDialogVisible = ref(false)
const resSubmitting = ref(false)
const resForm = reactive({
  applicationId: undefined as number | undefined,
  reserveDate: todayStr(),
  timeSlot: TIME_SLOTS[0],
  room: '',
})

function openReservationDialog() {
  resForm.applicationId = undefined
  resForm.reserveDate = todayStr()
  resForm.timeSlot = TIME_SLOTS[0]
  resForm.room = ''
  resDialogVisible.value = true
}

async function saveReservation() {
  if (!resForm.applicationId) {
    WMessage.warning('检查申请ID必填')
    return
  }
  if (!resForm.reserveDate) {
    WMessage.warning('请选择预约日期')
    return
  }
  if (!resForm.timeSlot) {
    WMessage.warning('请选择预约时段')
    return
  }
  resSubmitting.value = true
  try {
    await createExamReservationApi({
      applicationId: resForm.applicationId,
      reserveDate: resForm.reserveDate,
      timeSlot: resForm.timeSlot,
      room: resForm.room.trim() || undefined,
    })
    WMessage.success('检查预约创建成功')
    resDialogVisible.value = false
    fetchReservations()
  } catch (e) {
    WMessage.error((e as Error).message || '检查预约创建失败')
  } finally {
    resSubmitting.value = false
  }
}

async function handleCheckin(row: ExamReservation) {
  if (!row.id) return
  try {
    await WMessageBox.confirm(`确认预约 #${row.id}（时段 ${row.timeSlot || '-'}）患者报到吗？`, '检查报到', {
      type: 'warning',
      confirmButtonText: '确认报到',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await checkinExamReservationApi(row.id)
    WMessage.success('报到成功')
    fetchReservations()
  } catch (e) {
    WMessage.error((e as Error).message || '报到失败')
  }
}

async function handleReservationStatus(row: ExamReservation, action: 'DONE' | 'CANCELLED') {
  if (!row.id) return
  const text = action === 'DONE' ? '完成' : '取消'
  try {
    await WMessageBox.confirm(`确认${text}预约 #${row.id} 吗？`, `预约${text}`, {
      type: action === 'DONE' ? 'warning' : 'warning',
      confirmButtonText: `确认${text}`,
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await updateExamReservationStatusApi(row.id, action)
    WMessage.success(`预约已${text}`)
    fetchReservations()
  } catch (e) {
    WMessage.error((e as Error).message || `预约${text}失败`)
  }
}

// ==================== Tab4 影像序列 ====================

const MODALITY_OPTIONS = ['CT', 'MR', 'DR', 'CR', 'US', 'OTHER'] as const

const imgQuery = reactive({ applicationId: undefined as number | undefined })
const imgLoading = ref(false)
const seriesList = ref<ImageSeries[]>([])

async function fetchSeries() {
  if (!imgQuery.applicationId) {
    WMessage.warning('请输入检查申请ID')
    return
  }
  imgLoading.value = true
  try {
    seriesList.value = (await getExamSeriesListApi(imgQuery.applicationId)) || []
  } catch (e) {
    WMessage.error((e as Error).message || '影像序列加载失败')
  } finally {
    imgLoading.value = false
  }
}

function openPacsViewer(row: ImageSeries) {
  router.push({ path: '/pacs-viewer', query: { seriesId: String(row.id) } })
}

// 上传影像对话框（multipart 字段 images，可多文件 jpg）
const uploadVisible = ref(false)
const uploadSubmitting = ref(false)
const uploadForm = reactive({
  applicationId: undefined as number | undefined,
  modality: 'CT',
  description: '',
})
const uploadFiles = ref<File[]>([])
const uploadRef = ref<UploadInstance>()

function openUploadDialog() {
  uploadForm.applicationId = imgQuery.applicationId
  uploadForm.modality = 'CT'
  uploadForm.description = ''
  uploadFiles.value = []
  uploadVisible.value = true
}

function onUploadChange(_file: UploadFile, files: UploadFiles) {
  const raws: File[] = []
  files.forEach((f) => {
    if (f.raw) raws.push(f.raw)
  })
  uploadFiles.value = raws
}

async function saveUpload() {
  if (!uploadForm.applicationId) {
    WMessage.warning('检查申请ID必填')
    return
  }
  if (!uploadForm.modality) {
    WMessage.warning('请选择检查模态')
    return
  }
  if (!uploadFiles.value.length) {
    WMessage.warning('请选择至少一张 jpg 影像')
    return
  }
  uploadSubmitting.value = true
  try {
    await uploadExamImagesApi(
      uploadForm.applicationId,
      uploadFiles.value,
      uploadForm.modality,
      uploadForm.description.trim() || undefined,
    )
    WMessage.success('影像上传成功')
    uploadVisible.value = false
    uploadRef.value?.clearFiles()
    if (imgQuery.applicationId === uploadForm.applicationId) fetchSeries()
  } catch (e) {
    WMessage.error((e as Error).message || '影像上传失败')
  } finally {
    uploadSubmitting.value = false
  }
}

// 生成云影像链接（POST /medsupply/cloud/{applicationId}/link）
const cloudDialogVisible = ref(false)
const cloudLink = ref<CloudLink | null>(null)
const cloudSource = ref<ImageSeries | null>(null)

const cloudFullUrl = computed(() =>
  cloudLink.value?.url ? `${window.location.origin}${cloudLink.value.url}` : '',
)

async function genCloudLink(row: ImageSeries) {
  try {
    cloudLink.value = await createCloudLinkApi(row.applicationId)
    cloudSource.value = row
    cloudDialogVisible.value = true
  } catch (e) {
    WMessage.error((e as Error).message || '云影像链接生成失败')
  }
}

async function copyCloudLink() {
  if (!cloudFullUrl.value) return
  try {
    await navigator.clipboard.writeText(cloudFullUrl.value)
    WMessage.success('链接已复制')
  } catch {
    // clipboard API 不可用时降级为临时文本域复制
    const textarea = document.createElement('textarea')
    textarea.value = cloudFullUrl.value
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      WMessage.success('链接已复制')
    } catch {
      WMessage.warning('复制失败，请手动复制')
    }
    document.body.removeChild(textarea)
  }
}

// ==================== Tab5 报告模板 ====================

const TEMPLATE_TYPE_OPTIONS = [
  { value: 'FINDING', label: '所见（FINDING）' },
  { value: 'CONCLUSION', label: '印象（CONCLUSION）' },
] as const

function templateTypeText(type?: string): string {
  if (type === 'FINDING') return '所见'
  if (type === 'CONCLUSION') return '印象'
  return type || '-'
}

function templateTypeTag(type?: string): TagType {
  return type === 'CONCLUSION' ? 'success' : 'primary'
}

const tplLoading = ref(false)
const templates = ref<ReportTemplate[]>([])
const tplQuery = reactive({ modality: '' })

async function fetchTemplates() {
  tplLoading.value = true
  try {
    templates.value = (await getReportTemplateListApi({ modality: tplQuery.modality || undefined })) || []
  } catch (e) {
    WMessage.error((e as Error).message || '报告模板加载失败')
  } finally {
    tplLoading.value = false
  }
}

const tplDialogVisible = ref(false)
const tplSubmitting = ref(false)
const tplEditing = ref<ReportTemplate | null>(null)
const tplForm = reactive({
  modality: 'CT',
  bodyPart: '',
  templateType: 'FINDING',
  content: '',
  status: 1,
})

function openTemplateDialog(row?: ReportTemplate) {
  tplEditing.value = row ?? null
  tplForm.modality = row?.modality || 'CT'
  tplForm.bodyPart = row?.bodyPart || ''
  tplForm.templateType = row?.templateType || 'FINDING'
  tplForm.content = row?.content || ''
  tplForm.status = row?.status ?? 1
  tplDialogVisible.value = true
}

async function saveTemplate() {
  if (!tplForm.modality) {
    WMessage.warning('请选择模板分类（检查模态）')
    return
  }
  if (!tplForm.templateType) {
    WMessage.warning('请选择模板类型')
    return
  }
  if (!tplForm.content.trim()) {
    WMessage.warning('模板内容必填')
    return
  }
  tplSubmitting.value = true
  try {
    const payload = {
      modality: tplForm.modality,
      bodyPart: tplForm.bodyPart.trim() || undefined,
      templateType: tplForm.templateType,
      content: tplForm.content,
      status: tplForm.status,
    }
    if (tplEditing.value?.id) {
      await updateReportTemplateApi(tplEditing.value.id, { ...payload })
    } else {
      await createReportTemplateApi({ ...payload })
    }
    WMessage.success('模板保存成功')
    tplDialogVisible.value = false
    fetchTemplates()
  } catch (e) {
    WMessage.error((e as Error).message || '模板保存失败')
  } finally {
    tplSubmitting.value = false
  }
}

async function deleteTemplate(row: ReportTemplate) {
  if (!row.id) return
  try {
    await WMessageBox.confirm(`确定删除该「${templateTypeText(row.templateType)}」模板吗？`, '删除模板', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteReportTemplateApi(row.id)
    WMessage.success('模板已删除')
    fetchTemplates()
  } catch (e) {
    WMessage.error((e as Error).message || '模板删除失败')
  }
}

onMounted(() => {
  if (!canAccess.value) return
  fetchLabApplications()
  fetchSpecimens()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">医技工作台</h2>
      <p class="page-subtitle">检验 LIS（标本采集 · 结果录入 · 化验单）与影像中心（检查预约 · 影像序列 · 报告模板 · 云影像）</p>
    </div>

    <!-- 无权限空态 -->
    <w-card v-if="!canAccess" shadow="never" class="hospital-card">
      <w-empty description="暂无权限访问医技工作台（限检验技师 / 检查技师 / 管理员）" />
    </w-card>

    <!-- 主体 -->
    <w-card v-else shadow="never" class="hospital-card">
      <w-tabs v-model="tab" @tab-change="onTabChange">
        <!-- Tab1 标本采集 -->
        <w-tab-pane label="标本采集" name="specimen">
          <div class="tab-body">
            <!-- 待采检验申请 -->
            <div class="sub-block">
              <div class="sub-block__head">
                <span class="sub-block__title">待采检验申请</span>
                <span class="sub-block__tip">取最新 10 条 PENDING 状态检验申请，点击「采集」预填申请ID</span>
                <w-button size="small" :icon="Refresh" :loading="labAppLoading" @click="fetchLabApplications">刷新</w-button>
              </div>
              <w-table :data="labApps" row-key="id" border size="small" :loading="labAppLoading" empty-text="暂无待采检验申请">
                <w-table-column prop="id" label="申请ID" width="90" align="center" />
                <w-table-column prop="patientName" label="患者" min-width="100" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.patientName || row.patientId || '-' }}</template>
                </w-table-column>
                <w-table-column prop="examItemName" label="检验项目" min-width="150" show-overflow-tooltip />
                <w-table-column prop="createTime" label="申请时间" min-width="150" show-overflow-tooltip />
                <w-table-column label="操作" width="90" fixed="right" align="center">
                  <template #default="{ row }">
                    <w-button size="small" link type="primary" @click="openCollectDialog(row)">采集</w-button>
                  </template>
                </w-table-column>
              </w-table>
            </div>

            <!-- 标本列表 -->
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-select v-model="spQuery.status" placeholder="全部状态" clearable style="width: 150px" @change="spPage = 1; fetchSpecimens()">
                  <w-option label="已采集" value="COLLECTED" />
                  <w-option label="已核收" value="RECEIVED" />
                  <w-option label="检测中" value="TESTING" />
                  <w-option label="已拒收" value="REJECTED" />
                </w-select>
                <w-select v-model="spQuery.specimenType" placeholder="全部类型" clearable style="width: 130px" @change="spPage = 1; fetchSpecimens()">
                  <w-option v-for="o in SPECIMEN_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </w-select>
                <w-button type="primary" :icon="Search" @click="spPage = 1; fetchSpecimens()">查询</w-button>
                <w-button type="primary" plain :icon="Plus" @click="openCollectDialog()">按申请ID采集</w-button>
                <w-button :icon="Refresh" @click="spPage = 1; fetchSpecimens()">刷新</w-button>
              </div>
            </div>

            <w-table :data="specimens" row-key="id" border stripe :loading="spLoading" empty-text="暂无标本" size="default">
              <w-table-column prop="id" label="ID" width="70" align="center" />
              <w-table-column prop="specimenNo" label="标本号" min-width="150" show-overflow-tooltip />
              <w-table-column prop="applicationId" label="申请ID" width="90" align="center" />
              <w-table-column prop="patientId" label="患者ID" width="90" align="center" />
              <w-table-column label="类型" width="90" align="center">
                <template #default="{ row }">{{ specimenTypeText(row.specimenType) }}</template>
              </w-table-column>
              <w-table-column prop="container" label="容器" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.container || '-' }}</template>
              </w-table-column>
              <w-table-column prop="collectSite" label="采集部位" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.collectSite || '-' }}</template>
              </w-table-column>
              <w-table-column label="状态" width="96" align="center">
                <template #default="{ row }">
                  <w-tag :type="specimenStatusMeta(row.status).type" effect="light" size="small">
                    {{ specimenStatusMeta(row.status).text }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="collectTime" label="采集时间" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.collectTime || '-' }}</template>
              </w-table-column>
              <w-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remark || '-' }}</template>
              </w-table-column>
              <w-table-column label="操作" width="180" fixed="right" align="center">
                <template #default="{ row }">
                  <template v-if="row.status === 'COLLECTED'">
                    <w-button size="small" link type="success" @click="openReceiveDialog(row, true)">核收</w-button>
                    <w-button size="small" link type="danger" @click="openReceiveDialog(row, false)">拒收</w-button>
                  </template>
                  <w-button
                    v-if="row.status === 'COLLECTED' || row.status === 'RECEIVED'"
                    size="small"
                    link
                    type="warning"
                    @click="handleTesting(row)"
                  >进入检测</w-button>
                  <span
                    v-if="row.status !== 'COLLECTED' && row.status !== 'RECEIVED'"
                    class="muted"
                  >-</span>
                </template>
              </w-table-column>
            </w-table>

            <div class="pager">
              <w-pagination
                v-model:current-page="spPage"
                v-model:page-size="spSize"
                :total="spTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchSpecimens"
                @size-change="spPage = 1; fetchSpecimens()"
              />
            </div>
          </div>
        </w-tab-pane>

        <!-- Tab2 结果录入 -->
        <w-tab-pane label="结果录入" name="result">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-input-number
                  v-model="reportId"
                  :min="1"
                  :step="1"
                  :controls="false"
                  placeholder="检验报告ID"
                  style="width: 160px"
                  @keyup.enter="loadResult"
                />
                <w-button type="primary" :icon="Search" :loading="resultLoading" @click="loadResult">回读明细</w-button>
                <w-button type="primary" plain :icon="Plus" @click="addResultRow">添加行</w-button>
                <w-button type="success" :loading="resultLoading" @click="saveResult">保存（覆盖式）</w-button>
                <w-button :icon="DownloadIcon" @click="downloadLabPdf">化验单 PDF</w-button>
                <span class="table-toolbar__tip">异常标识（↑↓）由后端按参考范围自动判定，保存后回读生效</span>
              </div>
            </div>

            <w-table
              :data="resultRows"
              border
              size="default"
              :loading="resultLoading"
              :empty-text="resultLoaded ? '该报告暂无结果明细，点击「添加行」录入' : '请输入报告ID并回读明细'"
            >
              <w-table-column label="项目编码" min-width="130">
                <template #default="{ row }">
                  <w-input v-model="row.itemCode" size="small" placeholder="如 WBC" />
                </template>
              </w-table-column>
              <w-table-column label="项目名称" min-width="130">
                <template #default="{ row }">
                  <w-input v-model="row.itemName" size="small" placeholder="如 白细胞计数" />
                </template>
              </w-table-column>
              <w-table-column label="结果" min-width="120">
                <template #default="{ row }">
                  <w-input v-model="row.resultValue" size="small" placeholder="结果值" />
                </template>
              </w-table-column>
              <w-table-column label="单位" width="100">
                <template #default="{ row }">
                  <w-input v-model="row.unit" size="small" placeholder="单位" />
                </template>
              </w-table-column>
              <w-table-column label="参考范围" min-width="120">
                <template #default="{ row }">
                  <w-input v-model="row.refRange" size="small" placeholder="如 4-10" />
                </template>
              </w-table-column>
              <w-table-column label="异常" width="96" align="center">
                <template #default="{ row }">
                  <w-tag v-if="abnormalMeta(row.abnormalFlag)" :type="abnormalMeta(row.abnormalFlag)!.type" effect="light" size="small">
                    {{ abnormalMeta(row.abnormalFlag)!.text }}
                  </w-tag>
                  <span v-else class="muted">-</span>
                </template>
              </w-table-column>
              <w-table-column label="排序" width="110" align="center">
                <template #default="{ row, $index }">
                  <div class="row-order">
                    <w-input-number v-model="row.sortOrder" :min="1" :controls="false" size="small" style="width: 70px" />
                    <w-button size="small" link type="danger" :icon="Delete" @click="removeResultRow($index)" />
                  </div>
                </template>
              </w-table-column>
            </w-table>
          </div>
        </w-tab-pane>

        <!-- Tab3 检查预约 -->
        <w-tab-pane label="检查预约" name="reservation">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-date-picker-pro
                  v-model="resQuery.date"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="预约日期"
                  clearable
                  style="width: 160px"
                />
                <w-select v-model="resQuery.status" placeholder="全部状态" clearable style="width: 140px" @change="resPage = 1; fetchReservations()">
                  <w-option label="已预约" value="BOOKED" />
                  <w-option label="已报到" value="CHECKED_IN" />
                  <w-option label="已完成" value="DONE" />
                  <w-option label="已取消" value="CANCELLED" />
                </w-select>
                <w-button type="primary" :icon="Search" @click="resPage = 1; fetchReservations()">查询</w-button>
                <w-button type="primary" plain :icon="Plus" @click="openReservationDialog">新增预约</w-button>
                <w-button :icon="Refresh" @click="resPage = 1; fetchReservations()">刷新</w-button>
              </div>
            </div>

            <w-table :data="reservations" row-key="id" border stripe :loading="resLoading" empty-text="暂无预约记录" size="default">
              <w-table-column prop="id" label="预约ID" width="80" align="center" />
              <w-table-column prop="applicationId" label="申请ID" width="90" align="center" />
              <w-table-column prop="patientId" label="患者ID" width="90" align="center" />
              <w-table-column prop="reserveDate" label="预约日期" width="110" align="center" />
              <w-table-column prop="timeSlot" label="时段" width="110" align="center" />
              <w-table-column prop="room" label="检查室" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.room || '-' }}</template>
              </w-table-column>
              <w-table-column label="状态" width="96" align="center">
                <template #default="{ row }">
                  <w-tag :type="reservationStatusMeta(row.status).type" effect="light" size="small">
                    {{ reservationStatusMeta(row.status).text }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="checkinTime" label="报到时间" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.checkinTime || '-' }}</template>
              </w-table-column>
              <w-table-column label="操作" width="180" fixed="right" align="center">
                <template #default="{ row }">
                  <template v-if="row.status === 'BOOKED'">
                    <w-button size="small" link type="primary" @click="handleCheckin(row)">报到</w-button>
                    <w-button size="small" link type="danger" @click="handleReservationStatus(row, 'CANCELLED')">取消</w-button>
                  </template>
                  <template v-else-if="row.status === 'CHECKED_IN'">
                    <w-button size="small" link type="success" @click="handleReservationStatus(row, 'DONE')">完成</w-button>
                    <w-button size="small" link type="danger" @click="handleReservationStatus(row, 'CANCELLED')">取消</w-button>
                  </template>
                  <span v-else class="muted">-</span>
                </template>
              </w-table-column>
            </w-table>

            <div class="pager">
              <w-pagination
                v-model:current-page="resPage"
                v-model:page-size="resSize"
                :total="resTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchReservations"
                @size-change="resPage = 1; fetchReservations()"
              />
            </div>
          </div>
        </w-tab-pane>

        <!-- Tab4 影像序列 -->
        <w-tab-pane label="影像序列" name="images">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-input-number
                  v-model="imgQuery.applicationId"
                  :min="1"
                  :step="1"
                  :controls="false"
                  placeholder="检查申请ID"
                  style="width: 160px"
                  @keyup.enter="fetchSeries"
                />
                <w-button type="primary" :icon="Search" :loading="imgLoading" @click="fetchSeries">查询</w-button>
                <w-button type="primary" plain :icon="Upload" @click="openUploadDialog">上传影像</w-button>
                <w-button :icon="Refresh" @click="imgQuery.applicationId ? fetchSeries() : undefined">刷新</w-button>
              </div>
            </div>

            <w-table
              :data="seriesList"
              row-key="id"
              border
              stripe
              :loading="imgLoading"
              empty-text="请输入检查申请ID查询影像序列"
              size="default"
            >
              <w-table-column prop="id" label="序列ID" width="80" align="center" />
              <w-table-column prop="seriesNo" label="序列号" min-width="150" show-overflow-tooltip />
              <w-table-column prop="applicationId" label="申请ID" width="90" align="center" />
              <w-table-column prop="patientId" label="患者ID" width="90" align="center" />
              <w-table-column label="模态" width="90" align="center">
                <template #default="{ row }">
                  <w-tag type="primary" effect="light" size="small">{{ row.modality || '-' }}</w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.description || '-' }}</template>
              </w-table-column>
              <w-table-column prop="imageCount" label="影像数" width="90" align="center" />
              <w-table-column prop="createTime" label="创建时间" min-width="150" show-overflow-tooltip />
              <w-table-column label="操作" width="210" fixed="right" align="center">
                <template #default="{ row }">
                  <w-button size="small" link type="primary" :icon="Picture" @click="openPacsViewer(row)">PACS 浏览</w-button>
                  <w-button size="small" link type="success" :icon="Link" @click="genCloudLink(row)">云影像链接</w-button>
                </template>
              </w-table-column>
            </w-table>
          </div>
        </w-tab-pane>

        <!-- Tab5 报告模板 -->
        <w-tab-pane label="报告模板" name="template">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-select v-model="tplQuery.modality" placeholder="全部分类" clearable style="width: 140px" @change="fetchTemplates">
                  <w-option v-for="m in MODALITY_OPTIONS" :key="m" :label="m" :value="m" />
                </w-select>
                <w-button type="primary" :icon="Search" @click="fetchTemplates">查询</w-button>
                <w-button type="primary" plain :icon="Plus" @click="openTemplateDialog()">新增模板</w-button>
                <w-button :icon="Refresh" @click="fetchTemplates">刷新</w-button>
                <span class="table-toolbar__tip">医生报告录入页可按模态 + 部位套用模板回填「所见 / 印象」</span>
              </div>
            </div>

            <w-table :data="templates" row-key="id" border stripe :loading="tplLoading" empty-text="暂无模板" size="default">
              <w-table-column prop="id" label="ID" width="70" align="center" />
              <w-table-column label="分类" width="90" align="center">
                <template #default="{ row }">
                  <w-tag type="primary" effect="light" size="small">{{ row.modality || '-' }}</w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="bodyPart" label="部位" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.bodyPart || '通用' }}</template>
              </w-table-column>
              <w-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <w-tag :type="templateTypeTag(row.templateType)" effect="light" size="small">
                    {{ templateTypeText(row.templateType) }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="content" label="模板内容" min-width="280" show-overflow-tooltip />
              <w-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <w-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" size="small">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="createTime" label="创建时间" min-width="150" show-overflow-tooltip />
              <w-table-column label="操作" width="120" fixed="right" align="center">
                <template #default="{ row }">
                  <w-button size="small" link type="primary" @click="openTemplateDialog(row)">编辑</w-button>
                  <w-button size="small" link type="danger" @click="deleteTemplate(row)">删除</w-button>
                </template>
              </w-table-column>
            </w-table>
          </div>
        </w-tab-pane>
      </w-tabs>
    </w-card>

    <!-- 标本采集弹窗 -->
    <w-dialog v-model="collectDialogVisible" title="标本采集登记" width="480px" :close-on-click-modal="false">
      <w-form :model="collectForm" label-width="90px">
        <w-form-item label="申请ID" required>
          <w-input-number v-model="collectForm.applicationId" :min="1" :step="1" :controls="false" placeholder="检验申请ID" style="width: 100%" />
        </w-form-item>
        <w-form-item label="标本类型" required>
          <w-select v-model="collectForm.specimenType" placeholder="请选择标本类型">
            <w-option v-for="o in SPECIMEN_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="采集容器">
          <w-input v-model="collectForm.container" placeholder="如 EDTA 抗凝管（选填）" />
        </w-form-item>
        <w-form-item label="采集部位">
          <w-input v-model="collectForm.collectSite" placeholder="如 左肘正中静脉（选填）" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="collectDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="collectSubmitting" @click="saveCollect">确认采集</w-button>
      </template>
    </w-dialog>

    <!-- 标本核收 / 拒收弹窗 -->
    <w-dialog
      v-model="receiveDialogVisible"
      :title="receiveAccept ? '标本核收' : '标本拒收'"
      width="440px"
      :close-on-click-modal="false"
    >
      <div class="receive-body">
        <p class="receive-body__tip">
          标本 {{ receiveRow?.specimenNo || receiveRow?.id }}（{{ specimenTypeText(receiveRow?.specimenType) }}）→
          {{ receiveAccept ? '核收（RECEIVED）' : '拒收（REJECTED）' }}
        </p>
        <w-form label-width="90px">
          <w-form-item :label="receiveAccept ? '核收备注' : '拒收原因'" :required="!receiveAccept">
            <w-input
              v-model="receiveRemark"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              :placeholder="receiveAccept ? '备注（选填）' : '请输入拒收原因'"
            />
          </w-form-item>
        </w-form>
      </div>
      <template #footer>
        <w-button @click="receiveDialogVisible = false">取消</w-button>
        <w-button :type="receiveAccept ? 'success' : 'danger'" :loading="receiveSubmitting" @click="confirmReceive">
          {{ receiveAccept ? '确认核收' : '确认拒收' }}
        </w-button>
      </template>
    </w-dialog>

    <!-- 检查预约弹窗 -->
    <w-dialog v-model="resDialogVisible" title="新增检查预约" width="480px" :close-on-click-modal="false">
      <w-form :model="resForm" label-width="90px">
        <w-form-item label="申请ID" required>
          <w-input-number v-model="resForm.applicationId" :min="1" :step="1" :controls="false" placeholder="检查申请ID" style="width: 100%" />
        </w-form-item>
        <w-form-item label="预约日期" required>
          <w-date-picker-pro v-model="resForm.reserveDate" type="date" value-format="YYYY-MM-DD" placeholder="预约日期" style="width: 100%" />
        </w-form-item>
        <w-form-item label="预约时段" required>
          <w-select v-model="resForm.timeSlot" placeholder="30 分钟步进时段">
            <w-option v-for="s in TIME_SLOTS" :key="s" :label="s" :value="s" />
          </w-select>
        </w-form-item>
        <w-form-item label="检查室">
          <w-input v-model="resForm.room" placeholder="如 CT-1 室（选填）" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="resDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="resSubmitting" @click="saveReservation">确认预约</w-button>
      </template>
    </w-dialog>

    <!-- 影像上传弹窗 -->
    <w-dialog v-model="uploadVisible" title="上传影像序列" width="520px" :close-on-click-modal="false">
      <w-form :model="uploadForm" label-width="90px">
        <w-form-item label="申请ID" required>
          <w-input-number v-model="uploadForm.applicationId" :min="1" :step="1" :controls="false" placeholder="检查申请ID" style="width: 100%" />
        </w-form-item>
        <w-form-item label="检查模态" required>
          <w-select v-model="uploadForm.modality" placeholder="请选择模态">
            <w-option v-for="m in MODALITY_OPTIONS" :key="m" :label="m" :value="m" />
          </w-select>
        </w-form-item>
        <w-form-item label="描述">
          <w-input v-model="uploadForm.description" placeholder="序列描述（选填）" />
        </w-form-item>
        <w-form-item label="影像文件" required>
          <w-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            multiple
            :limit="50"
            accept=".jpg,.jpeg"
            @change="onUploadChange"
            @remove="onUploadChange"
          >
            <w-button plain :icon="Upload">选择 jpg 影像（可多选）</w-button>
          </w-upload>
          <div class="form-tip">已选择 {{ uploadFiles.length }} 个文件，multipart 字段名 images</div>
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="uploadVisible = false">取消</w-button>
        <w-button type="primary" :loading="uploadSubmitting" @click="saveUpload">确认上传</w-button>
      </template>
    </w-dialog>

    <!-- 云影像链接弹窗 -->
    <w-dialog v-model="cloudDialogVisible" title="云影像分享链接" width="520px" :close-on-click-modal="false">
      <div class="cloud-body">
        <p class="cloud-body__tip">
          申请 {{ cloudSource?.applicationId }} 的云影像访问码与分享地址（患者可凭链接免登录浏览影像与报告）
        </p>
        <w-form label-width="90px">
          <w-form-item label="访问码">
            <w-input :model-value="cloudLink?.code" readonly />
          </w-form-item>
          <w-form-item label="分享地址">
            <w-input :model-value="cloudFullUrl" readonly />
          </w-form-item>
        </w-form>
        <div class="cloud-body__actions">
          <w-button type="primary" plain :icon="Copy" @click="copyCloudLink">复制链接</w-button>
        </div>
      </div>
      <template #footer>
        <w-button type="primary" @click="cloudDialogVisible = false">关闭</w-button>
      </template>
    </w-dialog>

    <!-- 报告模板新增 / 编辑弹窗 -->
    <w-dialog v-model="tplDialogVisible" :title="tplEditing ? '编辑报告模板' : '新增报告模板'" width="560px" :close-on-click-modal="false">
      <w-form :model="tplForm" label-width="90px">
        <w-row :gutter="16">
          <w-col :span="12">
            <w-form-item label="模板分类" required>
              <w-select v-model="tplForm.modality" placeholder="检查模态">
                <w-option v-for="m in MODALITY_OPTIONS" :key="m" :label="m" :value="m" />
              </w-select>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="适用部位">
              <w-input v-model="tplForm.bodyPart" placeholder="如 头部 / 胸部（通用可留空）" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="模板类型" required>
              <w-select v-model="tplForm.templateType" placeholder="请选择类型">
                <w-option v-for="o in TEMPLATE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </w-select>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="状态">
              <w-switch v-model="tplForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </w-form-item>
          </w-col>
          <w-col :span="24">
            <w-form-item label="模板内容" required>
              <w-input v-model="tplForm.content" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="报告模板正文" />
            </w-form-item>
          </w-col>
        </w-row>
      </w-form>
      <template #footer>
        <w-button @click="tplDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="tplSubmitting" @click="saveTemplate">保存</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.tab-body {
  padding-top: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.muted {
  color: var(--hospital-text-third);
}

.table-toolbar__tip {
  font-size: 12px;
  color: var(--hospital-text-third);
}

.sub-block {
  margin-bottom: 20px;
  padding: 14px;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
  background: var(--w3-fill-color-lighter, #fafafa);
}
.sub-block__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.sub-block__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.sub-block__tip {
  flex: 1;
  font-size: 12px;
  color: var(--hospital-text-third);
}

.row-order {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.form-tip {
  font-size: 12px;
  line-height: 1.5;
  color: var(--hospital-text-third);
}

.receive-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.receive-body__tip {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--hospital-text-second);
}

.cloud-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cloud-body__tip {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--hospital-text-second);
}
.cloud-body__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
