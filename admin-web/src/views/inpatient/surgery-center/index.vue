<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import { Plus, Refresh } from '@win-design-next/icons-vue'
import {
  createOutpatientSurgeryApi,
  getSurgeryBoardApi,
  getSurgeryDetailApi,
  getSurgeryListApi,
  postopFollowupApi,
  scheduleSurgerySlotApi,
  signSurgeryConsentApi,
  startSurgeryApi,
  submitAnesthesiaRecordApi,
  submitPreopApi,
  submitSurgeryRecordApi,
} from '@/api/surgery'
import type {
  ConsentVO,
  PostopFollowupVO,
  SurgeryBoardItemVO,
  SurgeryDetailVO,
  SurgeryVO,
} from '@/types'

/* ================= 状态与文案 ================= */

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const STATUS_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'APPLIED', label: '待排台' },
  { value: 'SCHEDULED', label: '已排台' },
  { value: 'PREOP_PASSED', label: '术前通过' },
  { value: 'IN_OPERATION', label: '手术中' },
  { value: 'OPERATED', label: '手术完成' },
  { value: 'CANCELLED', label: '已取消' },
]
const STATUS_TEXT: Record<string, string> = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label]))
/** 状态 tag 颜色：APPLIED info / SCHEDULED primary / PREOP_PASSED warning / IN_OPERATION danger / OPERATED success / CANCELLED info */
const STATUS_TAG: Record<string, TagType> = {
  APPLIED: 'info',
  SCHEDULED: 'primary',
  PREOP_PASSED: 'warning',
  IN_OPERATION: 'danger',
  OPERATED: 'success',
  CANCELLED: 'info',
}
function statusText(s?: string): string {
  return STATUS_TEXT[s ?? ''] ?? s ?? '-'
}
function statusTag(s?: string): TagType {
  return STATUS_TAG[s ?? ''] ?? 'info'
}

const CONCLUSION_TEXT: Record<string, string> = {
  PASSED: '通过',
  CONDITIONAL: '有条件通过',
  REJECTED: '未通过',
}
function conclusionText(c?: string): string {
  return CONCLUSION_TEXT[c ?? ''] ?? c ?? '未评估'
}
function conclusionTag(c?: string): TagType {
  if (c === 'PASSED') return 'success'
  if (c === 'CONDITIONAL') return 'warning'
  if (c === 'REJECTED') return 'danger'
  return 'info'
}
/** 评估结论字段名防御式兼容：preopConclusion / assessmentConclusion 两种命名都可能 */
function rowConclusion(row: { preopConclusion?: string; assessmentConclusion?: string }): string {
  return row.preopConclusion || row.assessmentConclusion || ''
}

const SOURCE_TEXT: Record<string, string> = {
  INPATIENT: '住院转入',
  OUTPATIENT: '门诊登记',
}
function sourceText(s?: string): string {
  return SOURCE_TEXT[s ?? ''] ?? s ?? '-'
}

const ANESTHESIA_METHODS = ['全麻', '局麻', '椎管内麻醉', '神经阻滞', '静脉麻醉', '复合麻醉']
const SURGERY_TYPES = ['门诊小手术', '门诊中等手术', '择期手术', '急诊手术']
const ASA_OPTIONS = [
  { value: 1, label: 'ASA Ⅰ（正常健康）' },
  { value: 2, label: 'ASA Ⅱ（轻度系统性疾病）' },
  { value: 3, label: 'ASA Ⅲ（重度系统性疾病）' },
  { value: 4, label: 'ASA Ⅳ（重度疾病常威胁生命）' },
  { value: 5, label: 'ASA Ⅴ（濒危患者）' },
]

function todayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 后端 LocalDateTime 序列化为 ISO（含 T），展示统一替换为空格；空值为 '-' */
function fmtDateTime(v?: string): string {
  return v ? v.replace('T', ' ') : '-'
}

/** 排台时间只展示时段部分（HH:mm），兼容 'T'/空格两种分隔符 */
function timePart(t?: string): string {
  if (!t) return '-'
  const m = t.match(/[ T](\d{2}:\d{2})/)
  return m ? m[1] : t
}

/** 提交用：选择器产出 "YYYY-MM-DD HH:mm:ss"，后端 LocalDateTime 需 ISO（T 分隔） */
function toIsoDateTime(v: string): string {
  return v.replace(' ', 'T')
}

function specimenText(v?: boolean | number | string): string {
  if (v === true || v === 1 || v === '1' || v === 'true' || v === '是' || v === 'YES') return '已送标本'
  return '未送标本'
}

/* ================= 当前行（弹窗操作目标） ================= */
const target = ref<SurgeryVO | null>(null)

/* ================= 排台看板 ================= */
const activeTab = ref('board')
const boardLoading = ref(false)
const boardDate = ref(todayStr())
/** 顶部状态筛选：/board 接口无 status 参数，按前端过滤实现 */
const boardStatus = ref('')
const board = ref<SurgeryBoardItemVO[]>([])
const boardView = computed<SurgeryBoardItemVO[]>(() =>
  boardStatus.value ? board.value.filter((r) => r.status === boardStatus.value) : board.value,
)

async function fetchBoard() {
  boardLoading.value = true
  try {
    const res = await getSurgeryBoardApi({ date: boardDate.value || undefined })
    board.value = Array.isArray(res) ? res : []
  } catch (e) {
    WMessage.error((e as Error).message || '排台看板加载失败')
  } finally {
    boardLoading.value = false
  }
}

/* ================= 手术分页列表 ================= */
const listLoading = ref(false)
const list = ref<SurgeryVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const listStatus = ref('')
const listSource = ref('')

function buildListParams(): Record<string, unknown> {
  const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value }
  if (listStatus.value) params.status = listStatus.value
  if (listSource.value) params.source = listSource.value
  return params
}

async function fetchList() {
  listLoading.value = true
  try {
    const res = await getSurgeryListApi(buildListParams())
    list.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '手术列表加载失败')
  } finally {
    listLoading.value = false
  }
}

function searchList() {
  pageNo.value = 1
  fetchList()
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

function reloadAll() {
  fetchBoard()
  fetchList()
}

/* ================= 门诊手术登记 ================= */
const outpatientVisible = ref(false)
const outpatientSubmitting = ref(false)
const outpatientForm = reactive({
  patientId: null as number | null,
  surgeryName: '',
  surgeryType: '',
  notes: '',
})

function openOutpatient() {
  Object.assign(outpatientForm, { patientId: null, surgeryName: '', surgeryType: '', notes: '' })
  outpatientVisible.value = true
}

async function submitOutpatient() {
  if (!outpatientForm.patientId) {
    WMessage.warning('请填写患者 ID')
    return
  }
  if (!outpatientForm.surgeryName.trim()) {
    WMessage.warning('请填写手术名称')
    return
  }
  outpatientSubmitting.value = true
  try {
    const vo = await createOutpatientSurgeryApi({
      patientId: outpatientForm.patientId,
      surgeryName: outpatientForm.surgeryName.trim(),
      surgeryType: outpatientForm.surgeryType || undefined,
      notes: outpatientForm.notes.trim() || undefined,
    })
    WMessage.success(`门诊手术已建单${vo?.surgeryNo ? `（${vo.surgeryNo}）` : ''}，请及时排台`)
    outpatientVisible.value = false
    reloadAll()
  } catch (e) {
    WMessage.error((e as Error).message || '门诊手术建单失败')
  } finally {
    outpatientSubmitting.value = false
  }
}

/* ================= 排台（APPLIED） ================= */
const scheduleVisible = ref(false)
const scheduleSubmitting = ref(false)
const scheduleForm = reactive({
  scheduledTime: '',
  operatingRoom: '',
  surgeonId: null as number | null,
  anesthesiaMethod: '',
  anesthesiologistId: null as number | null,
})

function openSchedule(row: SurgeryVO) {
  target.value = row
  Object.assign(scheduleForm, {
    scheduledTime: row.scheduledTime || '',
    operatingRoom: row.operatingRoom || '',
    surgeonId: row.surgeonId ?? null,
    anesthesiaMethod: row.anesthesiaMethod || '',
    anesthesiologistId: row.anesthesiologistId ?? null,
  })
  scheduleVisible.value = true
}

async function submitSchedule() {
  const row = target.value
  if (!row) return
  if (!scheduleForm.scheduledTime) {
    WMessage.warning('请选择排台时间')
    return
  }
  if (!scheduleForm.operatingRoom.trim()) {
    WMessage.warning('请填写手术室/诊室')
    return
  }
  if (!scheduleForm.surgeonId) {
    WMessage.warning('请填写主刀医生 ID')
    return
  }
  if (!scheduleForm.anesthesiaMethod) {
    WMessage.warning('请选择麻醉方式')
    return
  }
  scheduleSubmitting.value = true
  try {
    await scheduleSurgerySlotApi(row.id, {
      scheduledTime: toIsoDateTime(scheduleForm.scheduledTime),
      operatingRoom: scheduleForm.operatingRoom.trim(),
      surgeonId: scheduleForm.surgeonId,
      anesthesiaMethod: scheduleForm.anesthesiaMethod,
      anesthesiologistId: scheduleForm.anesthesiologistId ?? undefined,
    })
    WMessage.success('排台完成，可继续术前评估')
    scheduleVisible.value = false
    reloadAll()
  } catch (e) {
    WMessage.error((e as Error).message || '排台失败')
  } finally {
    scheduleSubmitting.value = false
  }
}

/* ================= 术前评估（SCHEDULED） ================= */
const preopVisible = ref(false)
const preopSubmitting = ref(false)
const preopForm = reactive({
  asaGrade: null as number | null,
  riskFactors: '',
  assessmentText: '',
  conclusion: 'PASSED' as 'PASSED' | 'CONDITIONAL' | 'REJECTED',
})

function openPreop(row: SurgeryVO) {
  target.value = row
  Object.assign(preopForm, { asaGrade: null, riskFactors: '', assessmentText: '', conclusion: 'PASSED' })
  preopVisible.value = true
}

async function submitPreop() {
  const row = target.value
  if (!row) return
  if (!preopForm.asaGrade) {
    WMessage.warning('请选择 ASA 分级')
    return
  }
  preopSubmitting.value = true
  try {
    await submitPreopApi(row.id, {
      // 后端 PreopAssessmentDTO.asaGrade 为数字字符 1~5（@Pattern）
      asaGrade: String(preopForm.asaGrade),
      riskFactors: preopForm.riskFactors.trim() || undefined,
      assessmentText: preopForm.assessmentText.trim() || undefined,
      conclusion: preopForm.conclusion,
    })
    WMessage.success(preopForm.conclusion === 'REJECTED' ? '评估已提交：未通过，请重新安排评估' : '术前评估已提交')
    preopVisible.value = false
    reloadAll()
  } catch (e) {
    WMessage.error((e as Error).message || '术前评估提交失败')
  } finally {
    preopSubmitting.value = false
  }
}

/* ================= 知情同意（SCHEDULED / PREOP_PASSED，两类分别签署） ================= */
const consentVisible = ref(false)
const consentLoading = ref(false)
const consentSubmitting = ref('')
const consentDetail = ref<SurgeryDetailVO | null>(null)
const surgeryConsentForm = reactive({ patientSign: '', witness: '' })
const anesthesiaConsentForm = reactive({ patientSign: '', witness: '' })

function signedConsent(type: string): ConsentVO | undefined {
  return consentDetail.value?.consents?.find((c) => c.consentType === type)
}

async function openConsent(row: SurgeryVO) {
  target.value = row
  Object.assign(surgeryConsentForm, { patientSign: '', witness: '' })
  Object.assign(anesthesiaConsentForm, { patientSign: '', witness: '' })
  consentVisible.value = true
  consentDetail.value = null
  consentLoading.value = true
  try {
    consentDetail.value = await getSurgeryDetailApi(row.id)
  } catch (e) {
    WMessage.error((e as Error).message || '知情同意记录加载失败')
  } finally {
    consentLoading.value = false
  }
}

async function submitConsent(type: 'SURGERY' | 'ANESTHESIA') {
  const row = target.value
  if (!row) return
  const form = type === 'SURGERY' ? surgeryConsentForm : anesthesiaConsentForm
  if (!form.patientSign.trim()) {
    WMessage.warning('请填写患者签名')
    return
  }
  consentSubmitting.value = type
  try {
    await signSurgeryConsentApi(row.id, {
      consentType: type,
      patientSign: form.patientSign.trim(),
      witness: form.witness.trim() || undefined,
    })
    WMessage.success(type === 'SURGERY' ? '手术知情同意书已签署' : '麻醉知情同意书已签署')
    form.patientSign = ''
    form.witness = ''
    consentDetail.value = await getSurgeryDetailApi(row.id).catch(() => null)
  } catch (e) {
    WMessage.error((e as Error).message || '签署失败')
  } finally {
    consentSubmitting.value = ''
  }
}

/* ================= 开始手术（PREOP_PASSED） ================= */
async function doStart(row: SurgeryVO) {
  try {
    await WMessageBox.confirm('确认开始手术？开始后可录入手术记录与麻醉记录。', '开始手术', { type: 'warning' })
  } catch {
    return
  }
  try {
    await startSurgeryApi(row.id)
    WMessage.success('手术已开始')
    reloadAll()
  } catch (e) {
    WMessage.error((e as Error).message || '开始手术失败')
  }
}

/* ================= 手术记录（IN_OPERATION） ================= */
const recordVisible = ref(false)
const recordSubmitting = ref(false)
const recordForm = reactive({
  incision: '',
  procedureText: '',
  findings: '',
  specimenFlag: false,
  bloodLossMl: null as number | null,
  durationMin: null as number | null,
})

function openRecord(row: SurgeryVO) {
  target.value = row
  Object.assign(recordForm, {
    incision: '',
    procedureText: '',
    findings: '',
    specimenFlag: false,
    bloodLossMl: null,
    durationMin: null,
  })
  recordVisible.value = true
}

async function submitRecord() {
  const row = target.value
  if (!row) return
  if (!recordForm.incision.trim() || !recordForm.procedureText.trim()) {
    WMessage.warning('请填写切口与手术经过')
    return
  }
  recordSubmitting.value = true
  try {
    await submitSurgeryRecordApi(row.id, {
      incision: recordForm.incision.trim(),
      procedureText: recordForm.procedureText.trim(),
      findings: recordForm.findings.trim() || undefined,
      // 后端 SurgeryRecordDTO.specimenFlag 为 Integer：0-否 1-是
      specimenFlag: recordForm.specimenFlag ? 1 : 0,
      bloodLossMl: recordForm.bloodLossMl ?? undefined,
      durationMin: recordForm.durationMin ?? undefined,
    })
    WMessage.success('手术记录已提交')
    recordVisible.value = false
    reloadAll()
  } catch (e) {
    WMessage.error((e as Error).message || '手术记录提交失败')
  } finally {
    recordSubmitting.value = false
  }
}

/* ================= 麻醉记录（IN_OPERATION，vitals 三阶段数字输入 → JSON） ================= */
interface VitalsGroup {
  bp: number | null
  hr: number | null
  spo2: number | null
}

const anesthesiaVisible = ref(false)
const anesthesiaSubmitting = ref(false)
const anesthesiaForm = reactive({
  method: '',
  asaGrade: null as number | null,
  inductionTime: '',
  reversalTime: '',
  anesthesiologistId: null as number | null,
})
const vitalsForm = reactive<{ preop: VitalsGroup; intraop: VitalsGroup; postop: VitalsGroup }>({
  preop: { bp: null, hr: null, spo2: null },
  intraop: { bp: null, hr: null, spo2: null },
  postop: { bp: null, hr: null, spo2: null },
})
const VITAL_KEYS = ['preop', 'intraop', 'postop'] as const
type VitalKey = (typeof VITAL_KEYS)[number]
const VITAL_LABELS: Record<VitalKey, string> = { preop: '术前', intraop: '术中', postop: '术后' }

function openAnesthesia(row: SurgeryVO) {
  target.value = row
  Object.assign(anesthesiaForm, {
    method: row.anesthesiaMethod || '',
    asaGrade: null,
    inductionTime: '',
    reversalTime: '',
    anesthesiologistId: row.anesthesiologistId ?? null,
  })
  vitalsForm.preop = { bp: null, hr: null, spo2: null }
  vitalsForm.intraop = { bp: null, hr: null, spo2: null }
  vitalsForm.postop = { bp: null, hr: null, spo2: null }
  anesthesiaVisible.value = true
}

function buildVitalsJson(): string {
  const out: Record<string, Record<string, number>> = {}
  for (const k of VITAL_KEYS) {
    const g = vitalsForm[k]
    const item: Record<string, number> = {}
    if (g.bp != null) item.bp = g.bp
    if (g.hr != null) item.hr = g.hr
    if (g.spo2 != null) item.spo2 = g.spo2
    if (Object.keys(item).length > 0) out[k] = item
  }
  return JSON.stringify(out)
}

async function submitAnesthesia() {
  const row = target.value
  if (!row) return
  if (!anesthesiaForm.method) {
    WMessage.warning('请选择麻醉方式')
    return
  }
  anesthesiaSubmitting.value = true
  try {
    await submitAnesthesiaRecordApi(row.id, {
      method: anesthesiaForm.method,
      // 后端 AnesthesiaRecordDTO.asaGrade 为数字字符 1~5（@Pattern）
      asaGrade: anesthesiaForm.asaGrade ? String(anesthesiaForm.asaGrade) : undefined,
      inductionTime: anesthesiaForm.inductionTime ? toIsoDateTime(anesthesiaForm.inductionTime) : undefined,
      reversalTime: anesthesiaForm.reversalTime ? toIsoDateTime(anesthesiaForm.reversalTime) : undefined,
      vitals: buildVitalsJson(),
      anesthesiologistId: anesthesiaForm.anesthesiologistId ?? undefined,
    })
    WMessage.success('麻醉记录已提交')
    anesthesiaVisible.value = false
    reloadAll()
  } catch (e) {
    WMessage.error((e as Error).message || '麻醉记录提交失败')
  } finally {
    anesthesiaSubmitting.value = false
  }
}

/* ================= 术后镇痛随访（OPERATED） ================= */
async function doFollowup(row: SurgeryVO) {
  try {
    const res: PostopFollowupVO = await postopFollowupApi(row.id)
    // 任务约定返回 created；后端实际返回 followUpCreated，两者防御式兼容
    const created = res?.created ?? res?.followUpCreated
    if (created) {
      WMessage.success(res?.message || '术后镇痛随访计划已创建')
    } else {
      WMessage.warning(res?.message || '术后镇痛随访未创建，请稍后手工创建随访计划')
    }
  } catch (e) {
    WMessage.error((e as Error).message || '术后镇痛随访创建失败')
  }
}

/* ================= 详情聚合（抽屉） ================= */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<SurgeryDetailVO | null>(null)

async function openDetail(row: SurgeryVO) {
  target.value = row
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getSurgeryDetailApi(row.id)
  } catch (e) {
    WMessage.error((e as Error).message || '详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function parseVitals(v?: string): Record<string, Record<string, number>> | null {
  if (!v) return null
  try {
    const obj = JSON.parse(v) as unknown
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
      return obj as Record<string, Record<string, number>>
    }
    return null
  } catch {
    return null
  }
}

interface VitalsRow {
  label: string
  bp: number | string
  hr: number | string
  spo2: number | string
}

function vitalsRows(v?: string): VitalsRow[] {
  const parsed = parseVitals(v)
  if (!parsed) return []
  const rows: VitalsRow[] = []
  for (const k of VITAL_KEYS) {
    const g = parsed[k]
    if (g && typeof g === 'object') {
      rows.push({
        label: VITAL_LABELS[k],
        bp: typeof g.bp === 'number' ? g.bp : '-',
        hr: typeof g.hr === 'number' ? g.hr : '-',
        spo2: typeof g.spo2 === 'number' ? g.spo2 : '-',
      })
    }
  }
  return rows
}

function consentTypeText(t?: string): string {
  return t === 'ANESTHESIA' ? '麻醉知情同意书' : '手术知情同意书'
}

onMounted(() => {
  fetchBoard()
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">手术中心</h2>
      <p class="page-subtitle">
        手术/麻醉全流程：登记 → 排台 → 术前评估 → 知情同意 → 开始手术 → 手术/麻醉记录 → 术后镇痛随访
      </p>
    </div>

    <w-card shadow="never" class="hospital-card">
      <!-- 工具栏：日期 + 状态筛选 + 门诊手术登记 + 刷新 -->
      <div class="toolbar">
        <w-date-picker-pro
          v-model="boardDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          placeholder="看板日期"
          style="width: 150px"
          @change="fetchBoard"
        />
        <w-select v-model="boardStatus" placeholder="全部状态" clearable style="width: 150px">
          <w-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </w-select>
        <w-button type="primary" :icon="Plus" @click="openOutpatient">门诊手术登记</w-button>
        <w-button :icon="Refresh" @click="reloadAll">刷新</w-button>
        <span class="toolbar-tip">状态筛选作用于当日排台看板</span>
      </div>

      <w-tabs v-model="activeTab">
        <!-- ============ 排台看板 ============ -->
        <w-tab-pane label="排台看板" name="board">
          <w-table :data="boardView" v-loading="boardLoading" border stripe empty-text="当日暂无手术安排">
            <w-table-column label="时段" width="80">
              <template #default="{ row }">{{ timePart(row.scheduledTime) }}</template>
            </w-table-column>
            <w-table-column label="手术号" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.surgeryNo || '-' }}</template>
            </w-table-column>
            <w-table-column label="术式" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ row.surgeryName || '-' }}</template>
            </w-table-column>
            <w-table-column label="患者ID" width="90">
              <template #default="{ row }">{{ row.patientId ?? '-' }}</template>
            </w-table-column>
            <w-table-column label="主刀ID" width="90">
              <template #default="{ row }">{{ row.surgeonId ?? '-' }}</template>
            </w-table-column>
            <w-table-column label="麻醉" width="110">
              <template #default="{ row }">{{ row.anesthesiaMethod || '-' }}</template>
            </w-table-column>
            <w-table-column label="诊室" width="110">
              <template #default="{ row }">{{ row.operatingRoom || '-' }}</template>
            </w-table-column>
            <w-table-column label="评估结论" width="110" align="center">
              <template #default="{ row }">
                <w-tag :type="conclusionTag(rowConclusion(row))" effect="light" size="small">
                  {{ conclusionText(rowConclusion(row)) }}
                </w-tag>
              </template>
            </w-table-column>
            <w-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <w-tag :type="statusTag(row.status)" effect="light">{{ statusText(row.status) }}</w-tag>
              </template>
            </w-table-column>
            <w-table-column label="操作" width="140" fixed="right" align="center">
              <template #default="{ row }">
                <w-button v-if="row.status === 'APPLIED'" link type="primary" @click="openSchedule(row)">去排台</w-button>
                <w-button link type="primary" @click="openDetail(row)">详情</w-button>
              </template>
            </w-table-column>
          </w-table>
        </w-tab-pane>

        <!-- ============ 手术列表（分页） ============ -->
        <w-tab-pane label="手术列表" name="list">
          <w-form inline class="list-filter">
            <w-form-item label="状态">
              <w-select v-model="listStatus" placeholder="全部状态" clearable style="width: 150px" @change="searchList">
                <w-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </w-select>
            </w-form-item>
            <w-form-item label="来源">
              <w-select v-model="listSource" placeholder="全部来源" clearable style="width: 140px" @change="searchList">
                <w-option label="住院转入" value="INPATIENT" />
                <w-option label="门诊登记" value="OUTPATIENT" />
              </w-select>
            </w-form-item>
            <w-form-item>
              <w-button type="primary" @click="searchList">查询</w-button>
            </w-form-item>
          </w-form>

          <w-table :data="list" v-loading="listLoading" border stripe row-key="id" empty-text="暂无手术单">
            <w-table-column label="手术号" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.surgeryNo || '-' }}</template>
            </w-table-column>
            <w-table-column label="手术名称" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ row.surgeryName || '-' }}</template>
            </w-table-column>
            <w-table-column label="患者ID" width="90">
              <template #default="{ row }">{{ row.patientId ?? '-' }}</template>
            </w-table-column>
            <w-table-column label="来源" width="100">
              <template #default="{ row }">{{ sourceText(row.source) }}</template>
            </w-table-column>
            <w-table-column label="手术类型" width="110">
              <template #default="{ row }">{{ row.surgeryType || '-' }}</template>
            </w-table-column>
            <w-table-column label="排台时间" width="160">
              <template #default="{ row }">{{ row.scheduledTime ? fmtDateTime(row.scheduledTime) : '未排台' }}</template>
            </w-table-column>
            <w-table-column label="诊室" width="100">
              <template #default="{ row }">{{ row.operatingRoom || '-' }}</template>
            </w-table-column>
            <w-table-column label="麻醉方式" width="110">
              <template #default="{ row }">{{ row.anesthesiaMethod || '-' }}</template>
            </w-table-column>
            <w-table-column label="评估结论" width="110" align="center">
              <template #default="{ row }">
                <w-tag :type="conclusionTag(rowConclusion(row))" effect="light" size="small">
                  {{ conclusionText(rowConclusion(row)) }}
                </w-tag>
              </template>
            </w-table-column>
            <w-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <w-tag :type="statusTag(row.status)" effect="light">{{ statusText(row.status) }}</w-tag>
              </template>
            </w-table-column>
            <w-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <w-button v-if="row.status === 'APPLIED'" link type="primary" @click="openSchedule(row)">排台</w-button>
                <w-button v-if="row.status === 'SCHEDULED'" link type="primary" @click="openPreop(row)">术前评估</w-button>
                <w-button
                  v-if="row.status === 'SCHEDULED' || row.status === 'PREOP_PASSED'"
                  link
                  type="warning"
                  @click="openConsent(row)"
                >知情同意</w-button>
                <w-button v-if="row.status === 'PREOP_PASSED'" link type="danger" @click="doStart(row)">开始手术</w-button>
                <w-button v-if="row.status === 'IN_OPERATION'" link type="primary" @click="openRecord(row)">手术记录</w-button>
                <w-button v-if="row.status === 'IN_OPERATION'" link type="primary" @click="openAnesthesia(row)">麻醉记录</w-button>
                <w-button v-if="row.status === 'OPERATED'" link type="success" @click="doFollowup(row)">术后镇痛随访</w-button>
                <w-button link type="info" @click="openDetail(row)">详情</w-button>
              </template>
            </w-table-column>
          </w-table>

          <div class="pager">
            <w-pagination
              v-model:current-page="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchList"
              @size-change="handleSizeChange"
            />
          </div>
        </w-tab-pane>
      </w-tabs>
    </w-card>

    <!-- 门诊手术登记 -->
    <w-dialog v-model="outpatientVisible" title="门诊手术登记" width="520px">
      <w-form :model="outpatientForm" label-width="90px">
        <w-form-item label="患者ID" required>
          <w-input-number v-model="outpatientForm.patientId" :min="1" :controls="false" placeholder="患者 ID" style="width: 100%" />
        </w-form-item>
        <w-form-item label="手术名称" required>
          <w-input v-model="outpatientForm.surgeryName" maxlength="100" placeholder="如：体表肿物切除术" />
        </w-form-item>
        <w-form-item label="手术类型">
          <w-select v-model="outpatientForm.surgeryType" placeholder="选择手术类型" clearable filterable allow-create>
            <w-option v-for="t in SURGERY_TYPES" :key="t" :label="t" :value="t" />
          </w-select>
        </w-form-item>
        <w-form-item label="备注">
          <w-input v-model="outpatientForm.notes" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="备注（选填）" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="outpatientVisible = false">取消</w-button>
        <w-button type="primary" :loading="outpatientSubmitting" @click="submitOutpatient">建单</w-button>
      </template>
    </w-dialog>

    <!-- 排台 -->
    <w-dialog v-model="scheduleVisible" title="手术排台" width="560px">
      <w-form :model="scheduleForm" label-width="100px">
        <w-form-item label="排台时间" required>
          <w-date-picker-pro
            v-model="scheduleForm.scheduledTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择手术时间"
            style="width: 100%"
          />
        </w-form-item>
        <w-form-item label="手术室/诊室" required>
          <w-input v-model="scheduleForm.operatingRoom" maxlength="50" placeholder="如：手术室 3 / 门诊手术室 1" />
        </w-form-item>
        <w-form-item label="主刀医生ID" required>
          <w-input-number v-model="scheduleForm.surgeonId" :min="1" :controls="false" placeholder="主刀医生 ID" style="width: 100%" />
        </w-form-item>
        <w-form-item label="麻醉方式" required>
          <w-select v-model="scheduleForm.anesthesiaMethod" placeholder="选择麻醉方式" clearable>
            <w-option v-for="m in ANESTHESIA_METHODS" :key="m" :label="m" :value="m" />
          </w-select>
        </w-form-item>
        <w-form-item label="麻醉医师ID">
          <w-input-number v-model="scheduleForm.anesthesiologistId" :min="1" :controls="false" placeholder="选填" style="width: 100%" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="scheduleVisible = false">取消</w-button>
        <w-button type="primary" :loading="scheduleSubmitting" @click="submitSchedule">确认排台</w-button>
      </template>
    </w-dialog>

    <!-- 术前评估 -->
    <w-dialog v-model="preopVisible" title="术前评估" width="560px">
      <w-form :model="preopForm" label-width="100px">
        <w-form-item label="ASA 分级" required>
          <w-select v-model="preopForm.asaGrade" placeholder="ASA 1~5 级">
            <w-option v-for="a in ASA_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="危险因素">
          <w-input v-model="preopForm.riskFactors" type="textarea" :rows="2" maxlength="300" placeholder="高血压 / 糖尿病 / 冠心病等（选填）" />
        </w-form-item>
        <w-form-item label="评估意见">
          <w-input v-model="preopForm.assessmentText" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="评估意见（选填）" />
        </w-form-item>
        <w-form-item label="评估结论" required>
          <w-radio-group v-model="preopForm.conclusion">
            <w-radio value="PASSED">通过</w-radio>
            <w-radio value="CONDITIONAL">有条件通过</w-radio>
            <w-radio value="REJECTED">未通过</w-radio>
          </w-radio-group>
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="preopVisible = false">取消</w-button>
        <w-button type="primary" :loading="preopSubmitting" @click="submitPreop">提交评估</w-button>
      </template>
    </w-dialog>

    <!-- 知情同意签署（两类分别签署） -->
    <w-dialog v-model="consentVisible" title="知情同意书签署" width="640px">
      <div v-loading="consentLoading" class="consent-wrap">
        <w-alert
          type="info"
          :closable="false"
          title="手术与麻醉两类同意书需分别签署，已签署的不可重复签署"
          class="consent-tip"
        />
        <!-- 手术知情同意书 -->
        <div class="consent-block">
          <div class="consent-title">
            手术知情同意书
            <w-tag v-if="signedConsent('SURGERY')" type="success" effect="light" size="small">已签署</w-tag>
            <w-tag v-else type="info" effect="light" size="small">未签署</w-tag>
          </div>
          <w-descriptions v-if="signedConsent('SURGERY')" :column="2" border size="small">
            <w-descriptions-item label="患者签名">{{ signedConsent('SURGERY')?.patientSign || '-' }}</w-descriptions-item>
            <w-descriptions-item label="见证人">{{ signedConsent('SURGERY')?.witness || '-' }}</w-descriptions-item>
            <w-descriptions-item label="签署时间" :span="2">
              {{ signedConsent('SURGERY')?.signTime || signedConsent('SURGERY')?.createTime || '-' }}
            </w-descriptions-item>
          </w-descriptions>
          <template v-else>
            <w-form label-width="80px">
              <w-form-item label="患者签名" required>
                <w-input v-model="surgeryConsentForm.patientSign" maxlength="50" placeholder="患者本人签名（文本）" />
              </w-form-item>
              <w-form-item label="见证人">
                <w-input v-model="surgeryConsentForm.witness" maxlength="50" placeholder="见证人（选填）" />
              </w-form-item>
            </w-form>
            <div class="consent-actions">
              <w-button
                type="primary"
                size="small"
                :loading="consentSubmitting === 'SURGERY'"
                @click="submitConsent('SURGERY')"
              >签署手术同意书</w-button>
            </div>
          </template>
        </div>
        <!-- 麻醉知情同意书 -->
        <div class="consent-block">
          <div class="consent-title">
            麻醉知情同意书
            <w-tag v-if="signedConsent('ANESTHESIA')" type="success" effect="light" size="small">已签署</w-tag>
            <w-tag v-else type="info" effect="light" size="small">未签署</w-tag>
          </div>
          <w-descriptions v-if="signedConsent('ANESTHESIA')" :column="2" border size="small">
            <w-descriptions-item label="患者签名">{{ signedConsent('ANESTHESIA')?.patientSign || '-' }}</w-descriptions-item>
            <w-descriptions-item label="见证人">{{ signedConsent('ANESTHESIA')?.witness || '-' }}</w-descriptions-item>
            <w-descriptions-item label="签署时间" :span="2">
              {{ signedConsent('ANESTHESIA')?.signTime || signedConsent('ANESTHESIA')?.createTime || '-' }}
            </w-descriptions-item>
          </w-descriptions>
          <template v-else>
            <w-form label-width="80px">
              <w-form-item label="患者签名" required>
                <w-input v-model="anesthesiaConsentForm.patientSign" maxlength="50" placeholder="患者本人签名（文本）" />
              </w-form-item>
              <w-form-item label="见证人">
                <w-input v-model="anesthesiaConsentForm.witness" maxlength="50" placeholder="见证人（选填）" />
              </w-form-item>
            </w-form>
            <div class="consent-actions">
              <w-button
                type="primary"
                size="small"
                :loading="consentSubmitting === 'ANESTHESIA'"
                @click="submitConsent('ANESTHESIA')"
              >签署麻醉同意书</w-button>
            </div>
          </template>
        </div>
      </div>
      <template #footer>
        <w-button @click="consentVisible = false">关闭</w-button>
      </template>
    </w-dialog>

    <!-- 手术记录 -->
    <w-dialog v-model="recordVisible" title="录入手术记录" width="600px">
      <w-form :model="recordForm" label-width="100px">
        <w-form-item label="切口" required>
          <w-input v-model="recordForm.incision" maxlength="100" placeholder="如：右下腹麦氏切口，长约 5cm" />
        </w-form-item>
        <w-form-item label="手术经过" required>
          <w-input v-model="recordForm.procedureText" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="手术步骤与经过" />
        </w-form-item>
        <w-form-item label="术中所见">
          <w-input v-model="recordForm.findings" type="textarea" :rows="2" maxlength="1000" placeholder="术中所见（选填）" />
        </w-form-item>
        <w-form-item label="送病理标本">
          <w-switch v-model="recordForm.specimenFlag" active-text="是" inactive-text="否" />
        </w-form-item>
        <w-form-item label="出血量(ml)">
          <w-input-number v-model="recordForm.bloodLossMl" :min="0" :controls="false" placeholder="选填" style="width: 100%" />
        </w-form-item>
        <w-form-item label="时长(分钟)">
          <w-input-number v-model="recordForm.durationMin" :min="1" :controls="false" placeholder="选填" style="width: 100%" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="recordVisible = false">取消</w-button>
        <w-button type="primary" :loading="recordSubmitting" @click="submitRecord">提交手术记录</w-button>
      </template>
    </w-dialog>

    <!-- 麻醉记录 -->
    <w-dialog v-model="anesthesiaVisible" title="录入麻醉记录" width="700px">
      <w-form :model="anesthesiaForm" label-width="110px">
        <w-row>
          <w-col :span="12">
            <w-form-item label="麻醉方式" required>
              <w-select v-model="anesthesiaForm.method" placeholder="选择麻醉方式" clearable>
                <w-option v-for="m in ANESTHESIA_METHODS" :key="m" :label="m" :value="m" />
              </w-select>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="ASA 分级">
              <w-select v-model="anesthesiaForm.asaGrade" placeholder="选填" clearable>
                <w-option v-for="a in ASA_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
              </w-select>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="诱导时间">
              <w-date-picker-pro
                v-model="anesthesiaForm.inductionTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选填"
                style="width: 100%"
              />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="苏醒/拮抗">
              <w-date-picker-pro
                v-model="anesthesiaForm.reversalTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选填"
                style="width: 100%"
              />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="麻醉医师ID">
              <w-input-number v-model="anesthesiaForm.anesthesiologistId" :min="1" :controls="false" placeholder="选填" style="width: 100%" />
            </w-form-item>
          </w-col>
        </w-row>
      </w-form>

      <div class="vitals-tip">生命体征（选填，数字留空则不写入该字段）：BP mmHg / HR 次/分 / SpO2 %</div>
      <div class="vitals-grid">
        <div v-for="k in VITAL_KEYS" :key="k" class="vitals-group">
          <div class="vitals-group-title">{{ VITAL_LABELS[k] }}</div>
          <div class="vitals-field">
            <span class="vitals-label">BP</span>
            <w-input-number v-model="vitalsForm[k].bp" :min="40" :max="260" :controls="false" placeholder="mmHg" style="width: 100%" />
          </div>
          <div class="vitals-field">
            <span class="vitals-label">HR</span>
            <w-input-number v-model="vitalsForm[k].hr" :min="20" :max="250" :controls="false" placeholder="次/分" style="width: 100%" />
          </div>
          <div class="vitals-field">
            <span class="vitals-label">SpO2</span>
            <w-input-number v-model="vitalsForm[k].spo2" :min="50" :max="100" :controls="false" placeholder="%" style="width: 100%" />
          </div>
        </div>
      </div>

      <template #footer>
        <w-button @click="anesthesiaVisible = false">取消</w-button>
        <w-button type="primary" :loading="anesthesiaSubmitting" @click="submitAnesthesia">提交麻醉记录</w-button>
      </template>
    </w-dialog>

    <!-- 详情聚合抽屉 -->
    <w-drawer v-model="detailVisible" title="手术详情" size="680px">
      <div v-loading="detailLoading" class="detail-wrap">
        <template v-if="detail">
          <div class="section-title">手术单</div>
          <w-descriptions v-if="detail.surgery" :column="2" border size="small">
            <w-descriptions-item label="手术号">{{ detail.surgery.surgeryNo || '-' }}</w-descriptions-item>
            <w-descriptions-item label="状态">
              <w-tag :type="statusTag(detail.surgery.status)" effect="light">{{ statusText(detail.surgery.status) }}</w-tag>
            </w-descriptions-item>
            <w-descriptions-item label="手术名称">{{ detail.surgery.surgeryName || '-' }}</w-descriptions-item>
            <w-descriptions-item label="手术类型">{{ detail.surgery.surgeryType || '-' }}</w-descriptions-item>
            <w-descriptions-item label="患者ID">{{ detail.surgery.patientId ?? '-' }}</w-descriptions-item>
            <w-descriptions-item label="来源">{{ sourceText(detail.surgery.source) }}</w-descriptions-item>
            <w-descriptions-item label="排台时间">{{ detail.surgery.scheduledTime ? fmtDateTime(detail.surgery.scheduledTime) : '未排台' }}</w-descriptions-item>
            <w-descriptions-item label="手术室/诊室">{{ detail.surgery.operatingRoom || '-' }}</w-descriptions-item>
            <w-descriptions-item label="主刀医生ID">{{ detail.surgery.surgeonId ?? '-' }}</w-descriptions-item>
            <w-descriptions-item label="麻醉方式">{{ detail.surgery.anesthesiaMethod || '-' }}</w-descriptions-item>
            <w-descriptions-item label="麻醉医师ID">{{ detail.surgery.anesthesiologistId ?? '-' }}</w-descriptions-item>
            <w-descriptions-item label="创建时间">{{ fmtDateTime(detail.surgery.createTime) }}</w-descriptions-item>
            <w-descriptions-item label="备注" :span="2">{{ detail.surgery.notes || '—' }}</w-descriptions-item>
          </w-descriptions>
          <w-empty v-else description="未返回手术单信息" :image-size="60" />

          <div class="section-title">术前评估</div>
          <w-descriptions v-if="detail.preop" :column="2" border size="small">
            <w-descriptions-item label="ASA 分级">{{ detail.preop.asaGrade ? `ASA ${detail.preop.asaGrade}` : '-' }}</w-descriptions-item>
            <w-descriptions-item label="结论">
              <w-tag :type="conclusionTag(detail.preop.conclusion)" effect="light">{{ conclusionText(detail.preop.conclusion) }}</w-tag>
            </w-descriptions-item>
            <w-descriptions-item label="危险因素" :span="2">{{ detail.preop.riskFactors || '—' }}</w-descriptions-item>
            <w-descriptions-item label="评估意见" :span="2">{{ detail.preop.assessmentText || '—' }}</w-descriptions-item>
          </w-descriptions>
          <w-empty v-else description="尚未进行术前评估" :image-size="60" />

          <div class="section-title">知情同意</div>
          <w-table v-if="detail.consents && detail.consents.length" :data="detail.consents" size="small" border>
            <w-table-column label="类型" min-width="130">
              <template #default="{ row }">{{ consentTypeText(row.consentType) }}</template>
            </w-table-column>
            <w-table-column label="患者签名" min-width="90">
              <template #default="{ row }">{{ row.patientSign || '-' }}</template>
            </w-table-column>
            <w-table-column label="见证人" min-width="80">
              <template #default="{ row }">{{ row.witness || '-' }}</template>
            </w-table-column>
            <w-table-column label="签署时间" min-width="150">
              <template #default="{ row }">{{ fmtDateTime(row.signTime || row.createTime) }}</template>
            </w-table-column>
          </w-table>
          <w-empty v-else description="尚未签署知情同意书" :image-size="60" />

          <div class="section-title">手术记录</div>
          <w-descriptions v-if="detail.record" :column="2" border size="small">
            <w-descriptions-item label="切口">{{ detail.record.incision || '-' }}</w-descriptions-item>
            <w-descriptions-item label="标本">{{ specimenText(detail.record.specimenFlag) }}</w-descriptions-item>
            <w-descriptions-item label="出血量">{{ detail.record.bloodLossMl != null ? `${detail.record.bloodLossMl} ml` : '-' }}</w-descriptions-item>
            <w-descriptions-item label="时长">{{ detail.record.durationMin != null ? `${detail.record.durationMin} 分钟` : '-' }}</w-descriptions-item>
            <w-descriptions-item label="手术经过" :span="2">{{ detail.record.procedureText || '—' }}</w-descriptions-item>
            <w-descriptions-item label="术中所见" :span="2">{{ detail.record.findings || '—' }}</w-descriptions-item>
          </w-descriptions>
          <w-empty v-else description="尚未录入手术记录" :image-size="60" />

          <div class="section-title">麻醉记录</div>
          <template v-if="detail.anesthesia">
            <w-descriptions :column="2" border size="small">
              <w-descriptions-item label="麻醉方式">{{ detail.anesthesia.method || '-' }}</w-descriptions-item>
              <w-descriptions-item label="ASA 分级">{{ detail.anesthesia.asaGrade ? `ASA ${detail.anesthesia.asaGrade}` : '-' }}</w-descriptions-item>
              <w-descriptions-item label="诱导时间">{{ fmtDateTime(detail.anesthesia.inductionTime) }}</w-descriptions-item>
              <w-descriptions-item label="苏醒/拮抗时间">{{ fmtDateTime(detail.anesthesia.reversalTime) }}</w-descriptions-item>
              <w-descriptions-item label="麻醉医师ID" :span="2">{{ detail.anesthesia.anesthesiologistId ?? '-' }}</w-descriptions-item>
            </w-descriptions>
            <template v-if="vitalsRows(detail.anesthesia.vitals).length">
              <div class="vitals-view-title">生命体征（BP mmHg / HR 次/分 / SpO2 %）</div>
              <w-table :data="vitalsRows(detail.anesthesia.vitals)" size="small" border>
                <w-table-column prop="label" label="阶段" width="80" />
                <w-table-column prop="bp" label="BP" />
                <w-table-column prop="hr" label="HR" />
                <w-table-column prop="spo2" label="SpO2" />
              </w-table>
            </template>
          </template>
          <w-empty v-else description="尚未录入麻醉记录" :image-size="60" />
        </template>
        <w-empty v-else-if="!detailLoading" description="未获取到手术详情" />
      </div>
    </w-drawer>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.toolbar-tip {
  font-size: 12px;
  color: var(--hospital-text-third, #999);
}
.list-filter {
  margin-bottom: 4px;
}
.list-filter :deep(.w3-form-item) {
  margin-bottom: 10px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 知情同意 */
.consent-wrap {
  min-height: 120px;
}
.consent-tip {
  margin-bottom: 14px;
}
.consent-block {
  border: 1px solid var(--hospital-border, #e4e7ed);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.consent-title {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 14px;
}
.consent-actions {
  display: flex;
  justify-content: flex-end;
}

/* 麻醉记录 vitals 三阶段 */
.vitals-tip {
  font-size: 12px;
  color: var(--hospital-text-second, #666);
  margin: 4px 0 10px;
}
.vitals-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.vitals-group {
  border: 1px solid var(--hospital-border, #e4e7ed);
  border-radius: 8px;
  padding: 10px 12px;
}
.vitals-group-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
}
.vitals-field {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.vitals-field:last-child {
  margin-bottom: 0;
}
.vitals-label {
  width: 38px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--hospital-text-second, #666);
}

/* 详情抽屉 */
.detail-wrap {
  min-height: 160px;
}
.section-title {
  font-weight: 600;
  font-size: 14px;
  margin: 18px 0 8px;
}
.section-title:first-child {
  margin-top: 0;
}
.vitals-view-title {
  font-size: 12.5px;
  color: var(--hospital-text-second, #666);
  margin: 10px 0 6px;
}
</style>
