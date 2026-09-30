<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import {
  Check,
  CircleCheck,
  Close,
  Edit,
  File,
  Hospital,
  ListTimeline,
  Plus,
  Refresh,
  Search,
  Send,
  Stamp,
  Stop,
  Time,
  User,
  UserGroup,
} from '@win-design-next/icons-vue'
import {
  applyExamApi,
  createPrescriptionApi,
  finishConsultationApi,
  getDepartmentsApi,
  getMedicalRecordApi,
  getPatientRecordsApi,
  getTodayConsultationsApi,
  saveMedicalRecordApi,
  startConsultationApi,
} from '@/api/clinic'
import { getExamItemsApi, searchDrugsApi } from '@/api/medsupply'
import { triageApi } from '@/api/ai'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO, Drug, ExamItem, MedicalRecordVO, QueuePatientVO } from '@/types'

const userStore = useUserStore()
const canOperate = computed(() => userStore.isDoctor || userStore.isAdmin)

const departments = ref<DepartmentVO[]>([])
const deptId = ref<number>()
const list = ref<QueuePatientVO[]>([])
const loading = ref(false)

const record = ref<MedicalRecordVO | null>(null)
const recordLoading = ref(false)
const active = ref<QueuePatientVO | null>(null)

const recordForm = reactive({
  chiefComplaint: '',
  presentIllness: '',
  pastHistory: '',
  temperature: undefined as number | undefined,
  pulse: undefined as number | undefined,
  respiration: undefined as number | undefined,
  bloodPressure: '',
  diagnosisCode: '',
  diagnosisDesc: '',
  treatmentOpinion: '',
  action: 'DRAFT' as 'DRAFT' | 'SUBMIT',
})

type PrescriptionDraft = {
  drugId?: number
  drugName: string
  specification: string
  dosage: string
  usageMethod: string
  frequency: string
  days: number
  quantity: number
  price: number
  unit: string
  remark: string
  /** 中药煎法：先煎/后下/包煎/烊化等（中药笺使用，V9） */
  decoctionMethod?: string
  /** 中药脚注：特殊处理说明（中药笺使用，V9） */
  footnote?: string
}

const prescriptionItems = ref<PrescriptionDraft[]>([])
const drugOptions = ref<Drug[]>([])
const drugKeyword = ref('')

/** 处方笺类型：WESTERN-西药笺（默认） / HERBAL-中药饮片笺（V9） */
const prescriptionType = ref<'WESTERN' | 'HERBAL'>('WESTERN')
/** 中药剂数（中药笺提交必填） */
const herbalDoses = ref<number | undefined>(undefined)
/** 煎服法 */
const herbalUsage = ref('')

function rxLineTotal(item: PrescriptionDraft): number {
  return (item.price ?? 0) * (item.quantity ?? 1)
}
const prescriptionTotal = computed(() =>
  prescriptionItems.value.reduce((sum, item) => sum + rxLineTotal(item), 0),
)

const examItems = ref<ExamItem[]>([])
const examForm = reactive({ examItemId: undefined as number | undefined, applyRemark: '' })

const triageText = ref('')
const triageResult = ref('')
const triageLoading = ref(false)

const recordsVisible = ref(false)
const records = ref<MedicalRecordVO[]>([])
const recordsLoading = ref(false)

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
interface StatusMeta {
  type: TagType
  text: string
}

function queueStatusMeta(status?: string): StatusMeta {
  switch (status) {
    case 'CALLED':
      return { type: 'warning', text: '已叫号' }
    case 'WAITING':
      return { type: 'info', text: '待接诊' }
    case 'IN_CONSULTATION':
    case 'CONSULTING':
      return { type: 'primary', text: '就诊中' }
    case 'MISSED':
      return { type: 'danger', text: '已过号' }
    case 'COMPLETED':
      return { type: 'success', text: '已完成' }
    default:
      return { type: 'info', text: status || '未知' }
  }
}

function recordStatusMeta(status?: string): StatusMeta {
  switch (status) {
    case 'DRAFT':
      return { type: 'info', text: '草稿' }
    case 'SUBMITTED':
      return { type: 'warning', text: '已提交' }
    case 'COMPLETED':
      return { type: 'success', text: '已完成' }
    default:
      return { type: 'info', text: status || '未知' }
  }
}

function queueRowClass({ row }: { row: QueuePatientVO }): string {
  return active.value?.appointmentId === row.appointmentId ? 'queue-row--active' : ''
}

async function loadBase() {
  try {
    departments.value = await getDepartmentsApi()
    examItems.value = await getExamItemsApi({})
  } catch (e) {
    WMessage.error((e as Error).message || '基础数据加载失败')
  }
}

async function fetchToday() {
  loading.value = true
  try {
    list.value = await getTodayConsultationsApi({ departmentId: deptId.value })
  } catch (e) {
    WMessage.error((e as Error).message || '今日接诊列表加载失败')
  } finally {
    loading.value = false
  }
}

async function handleStart(row: QueuePatientVO) {
  active.value = row
  record.value = null
  recordLoading.value = true
  resetForm()
  try {
    const r = await startConsultationApi(row.appointmentId)
    record.value = r
    if (r.id) {
      const full = await getMedicalRecordApi(r.id)
      record.value = full
      fillForm(full)
    }
  } catch (e) {
    record.value = null
    WMessage.error((e as Error).message || '接诊失败')
  } finally {
    recordLoading.value = false
  }
}

function resetForm() {
  Object.assign(recordForm, {
    chiefComplaint: '',
    presentIllness: '',
    pastHistory: '',
    temperature: undefined,
    pulse: undefined,
    respiration: undefined,
    bloodPressure: '',
    diagnosisCode: '',
    diagnosisDesc: '',
    treatmentOpinion: '',
    action: 'DRAFT',
  })
  prescriptionItems.value = []
  prescriptionType.value = 'WESTERN'
  herbalDoses.value = undefined
  herbalUsage.value = ''
  examForm.examItemId = undefined
  examForm.applyRemark = ''
  triageText.value = ''
  triageResult.value = ''
}

function fillForm(r: MedicalRecordVO) {
  recordForm.chiefComplaint = r.chiefComplaint || ''
  recordForm.presentIllness = r.presentIllness || ''
  recordForm.pastHistory = r.pastHistory || ''
  recordForm.temperature = r.temperature
  recordForm.pulse = r.pulse
  recordForm.respiration = r.respiration
  recordForm.bloodPressure = r.bloodPressure || ''
  recordForm.diagnosisCode = r.diagnosisCode || ''
  recordForm.diagnosisDesc = r.diagnosisDesc || ''
  recordForm.treatmentOpinion = r.treatmentOpinion || ''
}

async function handleSave(action: 'DRAFT' | 'SUBMIT') {
  if (!record.value?.id) return
  if (action === 'SUBMIT' && !recordForm.diagnosisDesc.trim()) {
    WMessage.warning('提交病历前请填写诊断描述')
    return
  }
  recordForm.action = action
  try {
    const r = await saveMedicalRecordApi(record.value.id, { ...recordForm })
    record.value = r
    WMessage.success(action === 'SUBMIT' ? '病历已提交' : '草稿已保存')
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  }
}

async function searchDrugs() {
  try {
    const page = await searchDrugsApi({ keyword: drugKeyword.value, pageNo: 1, pageSize: 20 })
    drugOptions.value = page.records || []
  } catch (e) {
    WMessage.error((e as Error).message || '药品查询失败')
  }
}

function onDrugSelectVisible(visible: boolean) {
  if (visible) searchDrugs()
}

function onDrugSelect(item: PrescriptionDraft, drugId: number) {
  const drug = drugOptions.value.find((d) => d.id === drugId)
  item.drugName = drug?.drugName ?? ''
  item.specification = drug?.specification ?? ''
  item.unit = drug?.unit ?? '盒'
  item.price = drug?.referencePrice ?? 0
}

function addPrescriptionItem() {
  prescriptionItems.value.push({
    drugId: undefined,
    drugName: '',
    specification: '',
    dosage: '',
    usageMethod: '',
    frequency: '',
    days: 1,
    quantity: 1,
    price: 0,
    unit: '盒',
    remark: '',
  })
}

function removePrescriptionItem(index: number) {
  prescriptionItems.value.splice(index, 1)
}

async function handlePrescription() {
  if (!record.value?.id) return
  if (!prescriptionItems.value.length) {
    WMessage.warning('请先添加处方明细')
    return
  }
  if (prescriptionType.value === 'HERBAL' && (!herbalDoses.value || herbalDoses.value <= 0)) {
    WMessage.warning('中药笺请填写剂数')
    return
  }
  const isHerbal = prescriptionType.value === 'HERBAL'
  try {
    await createPrescriptionApi({
      medicalRecordId: record.value.id,
      prescriptionType: prescriptionType.value,
      herbalDoses: isHerbal ? herbalDoses.value : undefined,
      herbalUsage: isHerbal ? herbalUsage.value.trim() || undefined : undefined,
      items: prescriptionItems.value,
    })
    WMessage.success('处方已开具')
    prescriptionItems.value = []
    prescriptionType.value = 'WESTERN'
    herbalDoses.value = undefined
    herbalUsage.value = ''
  } catch (e) {
    // 后端 CDSS 拦截 / 抗菌药物授权不足等业务错误 message 原样展示
    WMessage.error((e as Error).message || '处方开具失败')
  }
}

async function handleExamApply() {
  if (!record.value?.id || !examForm.examItemId) {
    WMessage.warning('请选择检查项目')
    return
  }
  const item = examItems.value.find((i) => i.id === examForm.examItemId)
  try {
    await applyExamApi({
      medicalRecordId: record.value.id,
      examItemId: examForm.examItemId,
      examItemName: item?.itemName,
      itemType: item?.itemType,
      applyRemark: examForm.applyRemark,
    })
    WMessage.success('检查申请已提交')
    examForm.examItemId = undefined
    examForm.applyRemark = ''
  } catch (e) {
    WMessage.error((e as Error).message || '检查申请失败')
  }
}

async function handleTriage() {
  if (!triageText.value.trim()) {
    WMessage.warning('请输入症状描述')
    return
  }
  triageLoading.value = true
  try {
    const r = await triageApi(triageText.value.trim())
    triageResult.value = `推荐科室：${r.deptName}${r.confidence != null ? `（置信度 ${r.confidence}）` : ''}${r.isDegraded ? '，已降级为关键词匹配' : ''}`
  } catch (e) {
    WMessage.error((e as Error).message || '分诊失败')
  } finally {
    triageLoading.value = false
  }
}

async function handleFinish() {
  if (!record.value?.id) return
  try {
    await WMessageBox.confirm('结束就诊前请确认病历已提交，是否继续？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await finishConsultationApi(record.value.id)
    WMessage.success('就诊已结束')
    active.value = null
    record.value = null
    fetchToday()
  } catch (e) {
    WMessage.error((e as Error).message || '结束就诊失败')
  }
}

async function openRecords() {
  if (!active.value) return
  recordsVisible.value = true
  recordsLoading.value = true
  try {
    const page = await getPatientRecordsApi(active.value.patientId, { pageNo: 1, pageSize: 20 })
    records.value = page.records || []
  } catch (e) {
    WMessage.error((e as Error).message || '病历列表加载失败')
  } finally {
    recordsLoading.value = false
  }
}

onMounted(async () => {
  await loadBase()
  fetchToday()
})
</script>

<template>
  <div class="clinic">
    <!-- 页头 -->
    <div class="clinic-head">
      <div>
        <h2 class="page-title">门诊诊疗</h2>
        <p class="page-subtitle">今日待接诊队列 → 接诊 → 病历 → 处方 → 检查申请 → 结束就诊</p>
      </div>
      <div class="clinic-head__actions">
        <w-select v-model="deptId" placeholder="全部科室" clearable class="clinic-head__select" @change="fetchToday">
          <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
        </w-select>
        <w-button type="primary" :icon="Refresh" :loading="loading" @click="fetchToday">刷新</w-button>
      </div>
    </div>

    <!-- 三栏工作台 -->
    <div class="clinic-grid">
      <!-- 左：待接诊队列 -->
      <w-card shadow="never" class="hospital-card clinic-col">
        <template #header>
          <div class="col-head">
            <span class="col-head__icon"><Hospital /></span>
            <span>待接诊队列</span>
            <w-tag v-if="list.length" size="small" effect="light" type="primary" class="col-head__count">{{ list.length }}</w-tag>
          </div>
        </template>
        <w-table
          :data="list"
          size="small"
          :loading="loading"
          :max-height="480"
          :row-class-name="queueRowClass"
          empty-text="暂无待接诊患者"
        >
          <w-table-column label="患者" min-width="130">
            <template #default="{ row }">
              <div class="queue-patient">
                <span class="queue-patient__name">{{ row.patientName || `患者 ${row.patientId}` }}</span>
                <span class="queue-patient__meta">{{ row.slotStart }}–{{ row.slotEnd }}</span>
              </div>
            </template>
          </w-table-column>
          <w-table-column label="状态" width="76" align="center">
            <template #default="{ row }">
              <w-tag :type="queueStatusMeta(row.queueStatus).type" effect="light" size="small">{{ queueStatusMeta(row.queueStatus).text }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="操作" width="64" align="center" fixed="right">
            <template #default="{ row }">
              <w-button size="small" text type="primary" :disabled="!canOperate" @click="handleStart(row)">接诊</w-button>
            </template>
          </w-table-column>
        </w-table>
      </w-card>

      <!-- 中：病历编辑器 -->
      <w-card shadow="never" class="hospital-card clinic-col">
        <template #header>
          <div class="col-head">
            <span class="col-head__icon"><Edit /></span>
            <span>病历编辑器</span>
          </div>
        </template>

        <div v-loading="recordLoading" class="editor">
          <div v-if="!active" class="editor-empty">
            <span class="editor-empty__icon"><Hospital /></span>
            <p>请从左侧「待接诊队列」选择患者开始接诊</p>
          </div>

          <template v-else>
            <!-- 患者信息条 -->
            <div class="patient-banner">
              <span class="patient-banner__avatar"><User /></span>
              <div class="patient-banner__info">
                <div class="patient-banner__name">{{ active.patientName || `患者 ${active.patientId}` }}</div>
                <div class="patient-banner__meta">{{ active.departmentName || '未分配科室' }} · {{ active.doctorName || '待分配医生' }}</div>
                <div class="patient-banner__meta"><Time class="meta-icon" />{{ active.appointmentDate }} {{ active.slotStart }}–{{ active.slotEnd }}</div>
              </div>
              <w-tag class="patient-banner__tag" effect="light" type="primary">接诊中</w-tag>
            </div>

            <w-divider content-position="left">病历信息</w-divider>
            <w-form :model="recordForm" label-position="top" class="editor-form">
              <w-row :gutter="12">
                <w-col :span="24"><w-form-item label="主诉"><w-input v-model="recordForm.chiefComplaint" type="textarea" :rows="2" placeholder="请输入主诉" /></w-form-item></w-col>
                <w-col :span="24"><w-form-item label="现病史"><w-input v-model="recordForm.presentIllness" type="textarea" :rows="2" placeholder="请输入现病史" /></w-form-item></w-col>
                <w-col :span="24"><w-form-item label="既往史"><w-input v-model="recordForm.pastHistory" type="textarea" :rows="2" placeholder="请输入既往史" /></w-form-item></w-col>
                <w-col :span="12"><w-form-item label="诊断（ICD-10）"><w-input v-model="recordForm.diagnosisCode" placeholder="如 J00" /></w-form-item></w-col>
                <w-col :span="12"><w-form-item label="诊断描述"><w-input v-model="recordForm.diagnosisDesc" placeholder="提交时必填" /></w-form-item></w-col>
                <w-col :span="24"><w-form-item label="处理意见"><w-input v-model="recordForm.treatmentOpinion" type="textarea" :rows="2" placeholder="请输入处理意见" /></w-form-item></w-col>
              </w-row>
            </w-form>

            <w-divider content-position="left">生命体征</w-divider>
            <w-form :model="recordForm" label-position="top" class="editor-form">
              <w-row :gutter="12">
                <w-col :span="12"><w-form-item label="体温（℃）"><w-input-number v-model="recordForm.temperature" :precision="1" :step="0.1" :min="0" /></w-form-item></w-col>
                <w-col :span="12"><w-form-item label="脉搏（次/分）"><w-input-number v-model="recordForm.pulse" :min="0" /></w-form-item></w-col>
                <w-col :span="12"><w-form-item label="呼吸（次/分）"><w-input-number v-model="recordForm.respiration" :min="0" /></w-form-item></w-col>
                <w-col :span="12"><w-form-item label="血压（mmHg）"><w-input v-model="recordForm.bloodPressure" placeholder="如 120/80" /></w-form-item></w-col>
              </w-row>
            </w-form>

            <w-divider content-position="left">处方明细</w-divider>
            <div class="rx-toolbar">
              <w-radio-group v-model="prescriptionType" class="rx-toolbar__type">
                <w-radio value="WESTERN">西药笺</w-radio>
                <w-radio value="HERBAL">中药笺</w-radio>
              </w-radio-group>
              <w-input v-model="drugKeyword" placeholder="搜索药品名称" class="rx-toolbar__search" @keyup.enter="searchDrugs" />
              <w-button :icon="Search" @click="searchDrugs">搜索</w-button>
              <w-button type="primary" :icon="Plus" @click="addPrescriptionItem">添加明细</w-button>
            </div>
            <!-- 中药笺头部：剂数（必填） + 煎服法 -->
            <div v-if="prescriptionType === 'HERBAL'" class="rx-herbal-head">
              <div class="rx-herbal-head__item">
                <span class="rx-herbal-head__label">剂数<em class="rx-herbal-head__required">*</em></span>
                <w-input-number
                  v-model="herbalDoses"
                  :min="1"
                  :max="99"
                  controls-position="right"
                  placeholder="剂"
                  class="rx-herbal-head__doses"
                />
              </div>
              <div class="rx-herbal-head__item rx-herbal-head__item--grow">
                <span class="rx-herbal-head__label">煎服法</span>
                <w-input v-model="herbalUsage" placeholder="如：每日一剂，水煎400ml，分早晚两次温服" />
              </div>
            </div>
            <div v-if="!prescriptionItems.length" class="rx-empty">暂无处方明细，点击「添加明细」开始</div>
            <div v-for="(item, idx) in prescriptionItems" :key="idx" class="rx-row">
              <div class="rx-row__head">
                <span class="rx-row__index">{{ idx + 1 }}</span>
                <w-select
                  v-model="item.drugId"
                  filterable
                  placeholder="选择药品"
                  class="rx-row__drug"
                  @change="(v: number) => onDrugSelect(item, v)"
                  @visible-change="onDrugSelectVisible"
                >
                  <w-option v-for="d in drugOptions" :key="d.id" :label="`${d.drugName}（${d.specification || d.unit || '—'}）`" :value="d.id" />
                </w-select>
                <span class="rx-row__price">¥{{ (item.price ?? 0).toFixed(2) }} × {{ item.quantity ?? 1 }}</span>
                <w-button size="small" text type="danger" :icon="Close" @click="removePrescriptionItem(idx)">移除</w-button>
              </div>
              <div class="rx-row__fields">
                <w-input v-model="item.dosage" placeholder="剂量" class="rx-field" />
                <w-input v-model="item.usageMethod" placeholder="用法" class="rx-field" />
                <w-input v-model="item.frequency" placeholder="频次" class="rx-field" />
                <w-input-number v-model="item.days" :min="1" placeholder="天数" controls-position="right" class="rx-field" />
                <w-input-number v-model="item.quantity" :min="1" placeholder="数量" controls-position="right" class="rx-field" />
                <template v-if="prescriptionType === 'HERBAL'">
                  <w-input v-model="item.decoctionMethod" placeholder="煎法（先煎/后下/包煎）" class="rx-field" />
                  <w-input v-model="item.footnote" placeholder="脚注（特殊处理说明）" class="rx-field" />
                </template>
              </div>
            </div>
            <div v-if="prescriptionItems.length" class="rx-total">
              <span>处方合计</span>
              <span class="rx-total__amount">¥{{ prescriptionTotal.toFixed(2) }}</span>
            </div>

            <w-divider content-position="left">检查申请</w-divider>
            <w-form :model="examForm" label-position="top" class="editor-form">
              <w-row :gutter="12">
                <w-col :span="24">
                  <w-form-item label="检查项目">
                    <w-select v-model="examForm.examItemId" placeholder="选择检查项目">
                      <w-option v-for="i in examItems" :key="i.id" :label="`${i.itemName}（${i.itemType}）`" :value="i.id" />
                    </w-select>
                  </w-form-item>
                </w-col>
                <w-col :span="24"><w-form-item label="申请备注"><w-input v-model="examForm.applyRemark" placeholder="申请备注（选填）" /></w-form-item></w-col>
              </w-row>
            </w-form>
          </template>
        </div>
      </w-card>

      <!-- 右：操作按钮组 -->
      <w-card shadow="never" class="hospital-card clinic-col">
        <template #header>
          <div class="col-head">
            <span class="col-head__icon"><ListTimeline /></span>
            <span>快捷操作</span>
          </div>
        </template>

        <div class="action-panel">
          <div class="action-block">
            <div class="action-block__title"><Send class="block-icon" />AI 智能分诊</div>
            <w-input v-model="triageText" type="textarea" :rows="3" placeholder="输入患者症状描述（2~500 字）" />
            <w-button type="primary" plain :icon="Send" :loading="triageLoading" :disabled="!canOperate" @click="handleTriage">AI 分诊</w-button>
            <w-alert v-if="triageResult" type="success" :closable="false" :title="triageResult" show-icon class="action-block__result" />
          </div>

          <w-divider />

          <div class="action-list">
            <w-button type="primary" :icon="Stamp" :disabled="!record?.id || !canOperate" @click="handlePrescription">开处方</w-button>
            <w-button type="primary" plain :icon="File" :disabled="!record?.id || !canOperate" @click="handleExamApply">开检查</w-button>
            <w-button :icon="Check" :disabled="!record?.id || !canOperate" @click="handleSave('DRAFT')">保存草稿</w-button>
            <w-button type="success" :icon="CircleCheck" :disabled="!record?.id || !canOperate" @click="handleSave('SUBMIT')">提交病历</w-button>
            <w-button type="danger" plain :icon="Stop" :disabled="!record?.id || !canOperate" @click="handleFinish">结束就诊</w-button>
            <w-button :icon="UserGroup" :disabled="!active" @click="openRecords">患者病历列表</w-button>
          </div>
        </div>
      </w-card>
    </div>

    <!-- 患者病历列表弹窗 -->
    <w-dialog v-model="recordsVisible" title="患者病历列表" width="760px">
      <w-table :data="records" border stripe size="small" :loading="recordsLoading" empty-text="暂无历史病历">
        <w-table-column prop="id" label="病历ID" width="80" />
        <w-table-column prop="chiefComplaint" label="主诉" min-width="160" show-overflow-tooltip />
        <w-table-column prop="diagnosisCode" label="ICD-10" width="90" />
        <w-table-column prop="diagnosisDesc" label="诊断" min-width="140" show-overflow-tooltip />
        <w-table-column label="状态" width="90">
          <template #default="{ row }">
            <w-tag :type="recordStatusMeta(row.status).type" effect="light" size="small">{{ recordStatusMeta(row.status).text }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column prop="createTime" label="时间" min-width="150" />
      </w-table>
    </w-dialog>
  </div>
</template>

<style scoped>
.clinic {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 页头 */
.clinic-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 4px 0;
}
.clinic-head__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.clinic-head__select {
  width: 180px;
}

/* 三栏栅格 */
.clinic-grid {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: start;
}
@media (max-width: 1280px) {
  .clinic-grid {
    grid-template-columns: 1fr;
  }
}

.clinic-col {
  border-radius: var(--hospital-radius-md);
  border: 1px solid var(--hospital-border-lighter);
  box-shadow: var(--hospital-shadow-card);
}

.col-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.col-head__icon {
  display: inline-flex;
  align-items: center;
  font-size: 16px;
  color: var(--hospital-primary);
}
.col-head__count {
  margin-left: auto;
}

/* 待接诊队列 */
.clinic-grid :deep(.queue-row--active) > td {
  background: var(--w3-color-primary-plain, #eaeefe) !important;
}
.queue-patient {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.queue-patient__name {
  font-weight: 500;
  color: var(--hospital-text-main);
}
.queue-patient__meta {
  font-size: 12px;
  color: var(--hospital-text-third);
}

/* 病历编辑器 */
.editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 420px;
}
.editor-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 360px;
  color: var(--hospital-text-third);
  font-size: 13px;
}
.editor-empty p {
  margin: 0;
}
.editor-empty__icon {
  font-size: 32px;
  color: var(--w3-border-color-light, #e9e9e9);
}

.patient-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--w3-color-primary-plain, #eaeefe);
  border-radius: var(--hospital-radius-md);
}
.patient-banner__avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--w3-color-primary, #2d5afa);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.patient-banner__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.patient-banner__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.patient-banner__meta {
  font-size: 12px;
  color: var(--hospital-text-second);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.meta-icon {
  font-size: 12px;
  color: var(--hospital-text-third);
}
.patient-banner__tag {
  margin-left: auto;
  flex-shrink: 0;
}

.editor-form :deep(.w3-form-item) {
  margin-bottom: 12px;
}

/* 处方明细 */
.rx-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.rx-toolbar__search {
  flex: 1;
  min-width: 140px;
}
.rx-toolbar__type {
  margin-right: 4px;
}

/* 中药笺头部：剂数 + 煎服法 */
.rx-herbal-head {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  flex-wrap: wrap;
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px solid var(--w3-color-primary-plain, #eaeefe);
  border-radius: var(--hospital-radius-md);
  background: var(--w3-color-primary-plain, #eaeefe);
}
.rx-herbal-head__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 130px;
}
.rx-herbal-head__item--grow {
  flex: 1;
  min-width: 200px;
  width: auto;
}
.rx-herbal-head__label {
  font-size: 12px;
  color: var(--hospital-text-second);
}
.rx-herbal-head__required {
  color: var(--w3-color-danger, #f53f3f);
  font-style: normal;
  margin-left: 2px;
}
.rx-herbal-head__doses {
  width: 100%;
}
.rx-empty {
  padding: 16px;
  text-align: center;
  color: var(--hospital-text-third);
  font-size: 13px;
  background: var(--w3-fill-color-lighter, #fafafa);
  border: 1px dashed var(--hospital-border);
  border-radius: var(--hospital-radius-sm);
}
.rx-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
  background: var(--w3-fill-color-blank, #fff);
}
.rx-row + .rx-row {
  margin-top: 10px;
}
.rx-row__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rx-row__index {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--w3-color-primary-plain, #eaeefe);
  color: var(--w3-color-primary, #2d5afa);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rx-row__drug {
  flex: 1;
  min-width: 0;
}
.rx-row__price {
  font-size: 13px;
  font-weight: 600;
  color: var(--w3-color-primary, #2d5afa);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.rx-row__fields {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.rx-field {
  min-width: 0;
}
.rx-total {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--hospital-border-lighter);
  font-size: 13px;
  color: var(--hospital-text-second);
}
.rx-total__amount {
  font-size: 18px;
  font-weight: 700;
  color: var(--w3-color-danger, #f53f3f);
  font-variant-numeric: tabular-nums;
}

/* 快捷操作 */
.action-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.action-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.action-block__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.block-icon {
  color: var(--w3-color-primary, #2d5afa);
}
.action-block__result {
  margin-top: 2px;
}
.action-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.action-panel :deep(.w3-button) {
  width: 100%;
  margin-left: 0;
}
</style>
