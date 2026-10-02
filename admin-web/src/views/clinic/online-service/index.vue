<script setup lang="ts">
/**
 * 互联网医院服务页（迭代13，/online-service）。
 * Tab1 满意度评价（K3 提交+医生均分+分页）；Tab2 检查改约（K4）；Tab3 购药配送（K2）；Tab4 图文复诊（K1）。
 */
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Plus, Search } from '@win-design-next/icons-vue'
import {
  acceptConsultApi,
  closeConsultApi,
  createConsultApi,
  createDeliveryApi,
  deliverDeliveryApi,
  dispatchDeliveryApi,
  getConsultDetailApi,
  getConsultListApi,
  getDeliveryListApi,
  getDoctorEvaluationApi,
  getEvaluationListApi,
  getReservationListApi,
  prescribeConsultApi,
  rescheduleReservationApi,
  sendConsultMessageApi,
  submitEvaluationApi,
} from '@/api/online'
import type { ConsultMessage, ConsultRow, DeliveryRow, EvaluationRow } from '@/api/online'

const activeTab = ref<'evaluation' | 'reschedule' | 'delivery' | 'consult'>('evaluation')
function fmt(v?: string | null): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

/* ================= Tab1 满意度评价 ================= */
const evalLoading = ref(false)
const evalList = ref<EvaluationRow[]>([])
const evalTotal = ref(0)
const evalPageNo = ref(1)
const evalQuery = reactive<{ doctorId: number | null; minScore: number | null }>({ doctorId: null, minScore: null })
const evalDialog = ref(false)
const evalSaving = ref(false)
const evalForm = reactive<{ appointmentId: number | null; doctorId: number | null; score: number; content: string }>({
  appointmentId: null,
  doctorId: 1,
  score: 5,
  content: '',
})
const doctorSummary = ref<Record<string, unknown> | null>(null)

async function fetchEvalList() {
  evalLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: evalPageNo.value, pageSize: 10 }
    if (evalQuery.doctorId) params.doctorId = evalQuery.doctorId
    if (evalQuery.minScore) params.minScore = evalQuery.minScore
    const page = await getEvaluationListApi(params)
    evalList.value = Array.isArray(page?.records) ? page.records : []
    evalTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '评价加载失败')
  } finally {
    evalLoading.value = false
  }
}
function openEval() {
  evalForm.appointmentId = null
  evalForm.doctorId = 1
  evalForm.score = 5
  evalForm.content = ''
  evalDialog.value = true
}
async function submitEval() {
  if (!evalForm.appointmentId || !evalForm.doctorId) {
    WMessage.warning('请填写就诊单与医生 ID')
    return
  }
  evalSaving.value = true
  try {
    await submitEvaluationApi({
      appointmentId: evalForm.appointmentId,
      doctorId: evalForm.doctorId,
      score: evalForm.score,
      content: evalForm.content.trim() || undefined,
    })
    WMessage.success('评价已提交')
    evalDialog.value = false
    fetchEvalList()
    if (evalQuery.doctorId) loadDoctorSummary()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    evalSaving.value = false
  }
}
async function loadDoctorSummary() {
  if (!evalQuery.doctorId) return
  try {
    doctorSummary.value = await getDoctorEvaluationApi(evalQuery.doctorId)
  } catch {
    doctorSummary.value = null
  }
}

/* ================= Tab2 检查改约 ================= */
const resvLoading = ref(false)
const resvList = ref<Array<Record<string, unknown>>>([])
const resvStatus = ref('BOOKED')
const rescheduleDialog = ref(false)
const rescheduleSaving = ref(false)
const rescheduleTarget = ref<Record<string, unknown> | null>(null)
const rescheduleForm = reactive<{ date: string; timeSlot: string }>({ date: '', timeSlot: '08:00-09:00' })

async function fetchResvList() {
  resvLoading.value = true
  try {
    const page = await getReservationListApi({ status: resvStatus.value, pageNo: 1, pageSize: 20 })
    resvList.value = Array.isArray(page?.records) ? page.records : []
  } catch (e) {
    WMessage.error((e as Error).message || '预约加载失败')
  } finally {
    resvLoading.value = false
  }
}
function openReschedule(row: Record<string, unknown>) {
  rescheduleTarget.value = row
  rescheduleForm.date = ''
  rescheduleForm.timeSlot = String(row.timeSlot || '08:00-09:00')
  rescheduleDialog.value = true
}
async function submitReschedule() {
  if (!rescheduleTarget.value || !rescheduleForm.date) {
    WMessage.warning('请选择新的预约日期')
    return
  }
  rescheduleSaving.value = true
  try {
    await rescheduleReservationApi(Number(rescheduleTarget.value.id), rescheduleForm.date, rescheduleForm.timeSlot)
    WMessage.success('改约成功')
    rescheduleDialog.value = false
    fetchResvList()
  } catch (e) {
    WMessage.error((e as Error).message || '改约失败')
  } finally {
    rescheduleSaving.value = false
  }
}

/* ================= Tab3 购药配送 ================= */
const deliveryLoading = ref(false)
const deliveryList = ref<DeliveryRow[]>([])
const deliveryTotal = ref(0)
const deliveryPageNo = ref(1)
const deliveryStatus = ref('')
const deliveryDialog = ref(false)
const deliverySaving = ref(false)
const deliveryForm = reactive<{
  prescriptionId: number | null
  patientId: number | null
  patientName: string
  drugSummary: string
  receiverName: string
  receiverPhone: string
  address: string
}>({ prescriptionId: null, patientId: null, patientName: '', drugSummary: '', receiverName: '', receiverPhone: '', address: '' })

function deliveryMeta(v?: string): { label: string; tag: 'info' | 'warning' | 'success' } {
  const k = (v || '').toUpperCase()
  if (k === 'DISPATCHED') return { label: '已发货', tag: 'warning' }
  if (k === 'DELIVERED') return { label: '已送达', tag: 'success' }
  return { label: '已创建', tag: 'info' }
}
async function fetchDeliveryList() {
  deliveryLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: deliveryPageNo.value, pageSize: 10 }
    if (deliveryStatus.value) params.status = deliveryStatus.value
    const page = await getDeliveryListApi(params)
    deliveryList.value = Array.isArray(page?.records) ? page.records : []
    deliveryTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '配送单加载失败')
  } finally {
    deliveryLoading.value = false
  }
}
function openDelivery() {
  deliveryForm.prescriptionId = null
  deliveryForm.patientId = null
  deliveryForm.patientName = ''
  deliveryForm.drugSummary = ''
  deliveryForm.receiverName = ''
  deliveryForm.receiverPhone = ''
  deliveryForm.address = ''
  deliveryDialog.value = true
}
async function submitDelivery() {
  if (!deliveryForm.prescriptionId || !deliveryForm.patientId) {
    WMessage.warning('请填写处方与患者 ID')
    return
  }
  if (!deliveryForm.receiverName.trim() || !deliveryForm.receiverPhone.trim() || !deliveryForm.address.trim()) {
    WMessage.warning('收货人/电话/地址不能为空')
    return
  }
  deliverySaving.value = true
  try {
    await createDeliveryApi({
      prescriptionId: deliveryForm.prescriptionId,
      patientId: deliveryForm.patientId,
      patientName: deliveryForm.patientName.trim() || undefined,
      drugSummary: deliveryForm.drugSummary.trim() || undefined,
      receiverName: deliveryForm.receiverName.trim(),
      receiverPhone: deliveryForm.receiverPhone.trim(),
      address: deliveryForm.address.trim(),
    })
    WMessage.success('配送单已创建')
    deliveryDialog.value = false
    fetchDeliveryList()
  } catch (e) {
    WMessage.error((e as Error).message || '创建失败')
  } finally {
    deliverySaving.value = false
  }
}
async function deliveryAction(row: DeliveryRow, action: 'dispatch' | 'deliver') {
  if (!row.id) return
  try {
    if (action === 'dispatch') {
      await dispatchDeliveryApi(row.id)
      WMessage.success('已发货')
    } else {
      await deliverDeliveryApi(row.id)
      WMessage.success('已送达')
    }
    fetchDeliveryList()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

/* ================= Tab4 图文复诊 ================= */
const consultLoading = ref(false)
const consultList = ref<ConsultRow[]>([])
const consultTotal = ref(0)
const consultPageNo = ref(1)
const consultQuery = reactive<{ status: string; doctorId: number | null }>({ status: '', doctorId: null })
const consultDialog = ref(false)
const consultSaving = ref(false)
const consultForm = reactive<{ doctorId: number | null; chiefComplaint: string }>({ doctorId: 1, chiefComplaint: '' })
const chatVisible = ref(false)
const chatConsult = ref<ConsultRow | null>(null)
const chatMessages = ref<ConsultMessage[]>([])
const chatInput = ref('')
const prescribeItems = ref<Array<Record<string, unknown>>>([])

function consultMeta(v?: string): { label: string; tag: 'info' | 'warning' | 'success' } {
  const k = (v || '').toUpperCase()
  if (k === 'IN_PROGRESS') return { label: '问诊中', tag: 'warning' }
  if (k === 'CLOSED') return { label: '已结束', tag: 'success' }
  return { label: '待接诊', tag: 'info' }
}
async function fetchConsultList() {
  consultLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: consultPageNo.value, pageSize: 10 }
    if (consultQuery.status) params.status = consultQuery.status
    if (consultQuery.doctorId) params.doctorId = consultQuery.doctorId
    const page = await getConsultListApi(params)
    consultList.value = Array.isArray(page?.records) ? page.records : []
    consultTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '复诊单加载失败')
  } finally {
    consultLoading.value = false
  }
}
function openConsult() {
  consultForm.doctorId = 1
  consultForm.chiefComplaint = ''
  consultDialog.value = true
}
async function submitConsult() {
  if (!consultForm.doctorId || !consultForm.chiefComplaint.trim()) {
    WMessage.warning('请填写接诊医生与主诉')
    return
  }
  consultSaving.value = true
  try {
    await createConsultApi({ doctorId: consultForm.doctorId, chiefComplaint: consultForm.chiefComplaint.trim() })
    WMessage.success('复诊单已提交，等待医生接诊')
    consultDialog.value = false
    fetchConsultList()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    consultSaving.value = false
  }
}
async function openChat(row: ConsultRow) {
  if (!row.id) return
  try {
    const detail = await getConsultDetailApi(row.id)
    chatConsult.value = detail as unknown as ConsultRow
    chatMessages.value = Array.isArray(detail?.messages) ? (detail.messages as ConsultMessage[]) : []
    prescribeItems.value = []
    chatVisible.value = true
  } catch (e) {
    WMessage.error((e as Error).message || '详情加载失败')
  }
}
async function sendMessage(senderType: 'PATIENT' | 'DOCTOR') {
  if (!chatConsult.value?.id || !chatInput.value.trim()) return
  try {
    await sendConsultMessageApi(chatConsult.value.id, { senderType, content: chatInput.value.trim() })
    chatInput.value = ''
    const detail = await getConsultDetailApi(chatConsult.value.id)
    chatMessages.value = Array.isArray(detail?.messages) ? (detail.messages as ConsultMessage[]) : []
  } catch (e) {
    WMessage.error((e as Error).message || '发送失败')
  }
}
async function consultAction(row: ConsultRow, action: 'accept' | 'close') {
  if (!row.id) return
  try {
    if (action === 'accept') {
      await acceptConsultApi(row.id)
      WMessage.success('已接诊')
    } else {
      await closeConsultApi(row.id)
      WMessage.success('复诊已结束')
    }
    fetchConsultList()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}
function addPrescribeItem() {
  prescribeItems.value.push({ drugId: null, drugName: '', dosage: '', usageMethod: '口服', frequency: '每日三次', days: 3, quantity: 1, unit: '盒' })
}
async function submitPrescribe() {
  if (!chatConsult.value?.id) return
  if (!prescribeItems.value.length) {
    WMessage.warning('请先添加续方药品')
    return
  }
  try {
    await prescribeConsultApi(chatConsult.value.id, prescribeItems.value)
    WMessage.success('续方成功')
    chatVisible.value = false
    fetchConsultList()
  } catch (e) {
    WMessage.error((e as Error).message || '续方失败')
  }
}

onMounted(() => {
  fetchEvalList()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">互联网医院服务</h2>
      <p class="page-subtitle">满意度评价 · 检查改约 · 购药配送 · 图文复诊（K1~K4）</p>
    </div>

    <w-tabs v-model="activeTab" class="hospital-card">
      <w-tab-pane label="满意度评价" name="evaluation" />
      <w-tab-pane label="检查改约" name="reschedule" />
      <w-tab-pane label="购药配送" name="delivery" />
      <w-tab-pane label="图文复诊" name="consult" />
    </w-tabs>

    <!-- Tab1 满意度评价 -->
    <template v-if="activeTab === 'evaluation'">
      <w-card shadow="never" class="hospital-card">
        <w-form inline class="query-form">
          <w-form-item label="医生ID">
            <w-input-number v-model="evalQuery.doctorId" :min="1" style="width: 130px" />
          </w-form-item>
          <w-form-item label="最低星级">
            <w-select v-model="evalQuery.minScore" placeholder="不限" clearable style="width: 120px">
              <w-option v-for="s in [1, 2, 3, 4, 5]" :key="s" :label="`${s} 星及以上`" :value="s" />
            </w-select>
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :loading="evalLoading" @click="evalPageNo = 1; fetchEvalList(); loadDoctorSummary()">查询</w-button>
          </w-form-item>
        </w-form>
        <div v-if="doctorSummary" class="doctor-summary">
          医生 #{{ evalQuery.doctorId }} 均分：<b>{{ doctorSummary.avgScore ?? doctorSummary.avgscore ?? '-' }}</b> 星（共 {{ doctorSummary.count ?? '-' }} 条评价）
        </div>
        <w-table :data="evalList" row-key="id" border stripe :loading="evalLoading" empty-text="暂无评价" size="default">
          <w-table-column label="评价ID" width="90" align="center">
            <template #default="{ row }">#{{ row.id || '-' }}</template>
          </w-table-column>
          <w-table-column label="医生" width="120">
            <template #default="{ row }">{{ row.doctorName || row.doctor_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="科室" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.deptName || row.dept_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="星级" width="120" align="center">
            <template #default="{ row }">{{ '★'.repeat(Number(row.score || 0)) }}{{ '☆'.repeat(5 - Number(row.score || 0)) }}</template>
          </w-table-column>
          <w-table-column label="评价内容" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.content || '-' }}</template>
          </w-table-column>
          <w-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmt(row.createTime || row.create_time) }}</template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="evalPageNo"
          :page-size="10"
          :total="evalTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { evalPageNo = p; fetchEvalList() }"
        />
      </w-card>
      <div class="float-bar">
        <w-button type="primary" :icon="Plus" @click="openEval">提交评价</w-button>
      </div>
    </template>

    <!-- Tab2 检查改约 -->
    <w-card v-if="activeTab === 'reschedule'" shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="状态">
          <w-select v-model="resvStatus" placeholder="全部" clearable style="width: 150px" @change="fetchResvList">
            <w-option label="已预约" value="BOOKED" />
            <w-option label="已报到" value="CHECKED_IN" />
            <w-option label="已完成" value="DONE" />
            <w-option label="已取消" value="CANCELLED" />
          </w-select>
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :icon="Search" :loading="resvLoading" @click="fetchResvList">查询</w-button>
        </w-form-item>
      </w-form>
      <w-table :data="resvList" row-key="id" border stripe :loading="resvLoading" empty-text="暂无检查预约" size="default">
        <w-table-column label="预约ID" width="90" align="center">
          <template #default="{ row }">#{{ row.id || '-' }}</template>
        </w-table-column>
        <w-table-column label="预约日期" width="130">
          <template #default="{ row }">{{ row.reserveDate || row.reserve_date || '-' }}</template>
        </w-table-column>
        <w-table-column label="时段" width="130">
          <template #default="{ row }">{{ row.timeSlot || row.time_slot || '-' }}</template>
        </w-table-column>
        <w-table-column label="检查室" width="130">
          <template #default="{ row }">{{ row.room || '-' }}</template>
        </w-table-column>
        <w-table-column label="状态" width="100" align="center">
          <template #default="{ row }">{{ row.status || '-' }}</template>
        </w-table-column>
        <w-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <w-button v-if="(row.status || '') === 'BOOKED'" type="primary" link @click="openReschedule(row)">改约</w-button>
            <span v-else>-</span>
          </template>
        </w-table-column>
      </w-table>
    </w-card>

    <!-- Tab3 购药配送 -->
    <template v-if="activeTab === 'delivery'">
      <w-card shadow="never" class="hospital-card">
        <div class="table-toolbar list-toolbar">
          <div class="table-toolbar__left">
            <w-select v-model="deliveryStatus" placeholder="全部状态" clearable style="width: 140px" @change="deliveryPageNo = 1; fetchDeliveryList()">
              <w-option label="已创建" value="CREATED" />
              <w-option label="已发货" value="DISPATCHED" />
              <w-option label="已送达" value="DELIVERED" />
            </w-select>
          </div>
          <div class="table-toolbar__right">
            <w-button type="primary" :icon="Plus" @click="openDelivery">创建配送单</w-button>
          </div>
        </div>
        <w-table :data="deliveryList" row-key="id" border stripe :loading="deliveryLoading" empty-text="暂无配送单" size="default">
          <w-table-column label="配送单号" width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.deliveryNo || row.deliveryNo || '-' }}</template>
          </w-table-column>
          <w-table-column label="患者" width="110">
            <template #default="{ row }">{{ row.patientName || row.patient_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="药品摘要" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.drugSummary || row.drug_summary || '-' }}</template>
          </w-table-column>
          <w-table-column label="收货人" width="100">
            <template #default="{ row }">{{ row.receiverName || row.receiver_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="deliveryMeta(row.status).tag" effect="light">{{ deliveryMeta(row.status).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ fmt(row.createTime || row.create_time) }}</template>
          </w-table-column>
          <w-table-column label="操作" width="130" fixed="right" align="center">
            <template #default="{ row }">
              <w-button v-if="(row.status || '') === 'CREATED'" type="warning" link @click="deliveryAction(row, 'dispatch')">发货</w-button>
              <w-button v-if="(row.status || '') === 'DISPATCHED'" type="success" link @click="deliveryAction(row, 'deliver')">送达</w-button>
              <span v-if="(row.status || '') === 'DELIVERED'">-</span>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="deliveryPageNo"
          :page-size="10"
          :total="deliveryTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { deliveryPageNo = p; fetchDeliveryList() }"
        />
      </w-card>
    </template>

    <!-- Tab4 图文复诊 -->
    <template v-if="activeTab === 'consult'">
      <w-card shadow="never" class="hospital-card">
        <div class="table-toolbar list-toolbar">
          <div class="table-toolbar__left">
            <w-select v-model="consultQuery.status" placeholder="全部状态" clearable style="width: 140px" @change="consultPageNo = 1; fetchConsultList()">
              <w-option label="待接诊" value="WAITING" />
              <w-option label="问诊中" value="IN_PROGRESS" />
              <w-option label="已结束" value="CLOSED" />
            </w-select>
          </div>
          <div class="table-toolbar__right">
            <w-button type="primary" :icon="Plus" @click="openConsult">发起复诊</w-button>
          </div>
        </div>
        <w-table :data="consultList" row-key="id" border stripe :loading="consultLoading" empty-text="暂无复诊单" size="default">
          <w-table-column label="复诊单号" width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.consultNo || row.consult_no || '-' }}</template>
          </w-table-column>
          <w-table-column label="患者" width="110">
            <template #default="{ row }">{{ row.patientName || row.patient_name || (row.patientId ? `#${row.patientId}` : '-') }}</template>
          </w-table-column>
          <w-table-column label="医生" width="110">
            <template #default="{ row }">{{ row.doctorName || row.doctor_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="主诉" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.chiefComplaint || row.chief_complaint || '-' }}</template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="consultMeta(row.status).tag" effect="light">{{ consultMeta(row.status).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="续方" width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.prescriptionNo || row.prescription_no || '-' }}</template>
          </w-table-column>
          <w-table-column label="操作" width="170" fixed="right" align="center">
            <template #default="{ row }">
              <w-button type="primary" link @click="openChat(row)">会话</w-button>
              <w-button v-if="(row.status || '') === 'WAITING'" type="warning" link @click="consultAction(row, 'accept')">接诊</w-button>
              <w-button v-if="(row.status || '') !== 'CLOSED'" type="danger" link @click="consultAction(row, 'close')">关闭</w-button>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="consultPageNo"
          :page-size="10"
          :total="consultTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { consultPageNo = p; fetchConsultList() }"
        />
      </w-card>
    </template>

    <!-- 提交评价弹窗 -->
    <w-dialog v-model="evalDialog" title="提交就诊评价" width="480px" destroy-on-close>
      <w-form label-width="88px">
        <w-form-item label="就诊单ID" required>
          <w-input-number v-model="evalForm.appointmentId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="医生ID" required>
          <w-input-number v-model="evalForm.doctorId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="星级" required>
          <w-rate v-model="evalForm.score" />
        </w-form-item>
        <w-form-item label="评价内容">
          <w-input v-model="evalForm.content" type="textarea" :rows="3" placeholder="就医体验（选填）" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="evalDialog = false">取消</w-button>
        <w-button type="primary" :loading="evalSaving" @click="submitEval">提交</w-button>
      </template>
    </w-dialog>

    <!-- 改约弹窗 -->
    <w-dialog v-model="rescheduleDialog" title="检查预约改约" width="440px" destroy-on-close>
      <w-form label-width="88px">
        <w-form-item label="新日期" required>
          <w-date-picker v-model="rescheduleForm.date" value-format="YYYY-MM-DD" style="width: 100%" />
        </w-form-item>
        <w-form-item label="时段" required>
          <w-input v-model="rescheduleForm.timeSlot" placeholder="如 08:00-09:00" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="rescheduleDialog = false">取消</w-button>
        <w-button type="primary" :loading="rescheduleSaving" @click="submitReschedule">确认改约</w-button>
      </template>
    </w-dialog>

    <!-- 创建配送单弹窗 -->
    <w-dialog v-model="deliveryDialog" title="创建购药配送单" width="560px" destroy-on-close>
      <w-form label-width="96px">
        <w-form-item label="处方 ID" required>
          <w-input-number v-model="deliveryForm.prescriptionId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="患者 ID" required>
          <w-input-number v-model="deliveryForm.patientId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="患者姓名">
          <w-input v-model="deliveryForm.patientName" />
        </w-form-item>
        <w-form-item label="药品摘要">
          <w-input v-model="deliveryForm.drugSummary" placeholder="如 阿莫西林胶囊x2盒" />
        </w-form-item>
        <w-form-item label="收货人" required>
          <w-input v-model="deliveryForm.receiverName" />
        </w-form-item>
        <w-form-item label="联系电话" required>
          <w-input v-model="deliveryForm.receiverPhone" />
        </w-form-item>
        <w-form-item label="收货地址" required>
          <w-input v-model="deliveryForm.address" type="textarea" :rows="2" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="deliveryDialog = false">取消</w-button>
        <w-button type="primary" :loading="deliverySaving" @click="submitDelivery">创建</w-button>
      </template>
    </w-dialog>

    <!-- 发起复诊弹窗 -->
    <w-dialog v-model="consultDialog" title="发起图文复诊" width="480px" destroy-on-close>
      <w-form label-width="88px">
        <w-form-item label="接诊医生" required>
          <w-input-number v-model="consultForm.doctorId" :min="1" style="width: 100%" placeholder="医生 ID" />
        </w-form-item>
        <w-form-item label="主诉" required>
          <w-input v-model="consultForm.chiefComplaint" type="textarea" :rows="3" placeholder="简要描述本次复诊问题" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="consultDialog = false">取消</w-button>
        <w-button type="primary" :loading="consultSaving" @click="submitConsult">提交</w-button>
      </template>
    </w-dialog>

    <!-- 复诊会话抽屉 -->
    <w-drawer v-model="chatVisible" title="图文复诊会话" size="560px" destroy-on-close>
      <template v-if="chatConsult">
        <div class="chat-desc">主诉：{{ chatConsult.chiefComplaint || '-' }} · 状态：{{ consultMeta(chatConsult.status).label }}</div>
        <div class="chat-list">
          <div
            v-for="m in chatMessages"
            :key="m.id"
            class="chat-item"
            :class="(m.senderType) === 'DOCTOR' ? 'chat-doctor' : 'chat-patient'"
          >
            <div class="chat-meta">{{ m.senderName || '-' }} · {{ fmt(m.createTime) }}</div>
            <div class="chat-bubble">{{ m.content || '-' }}</div>
          </div>
        </div>
        <div class="chat-input">
          <w-input v-model="chatInput" placeholder="输入消息" @keyup.enter="sendMessage('DOCTOR')" />
          <w-button type="primary" @click="sendMessage('DOCTOR')">发送</w-button>
        </div>
        <h4 class="sub-title">续方</h4>
        <div v-for="(it, i) in prescribeItems" :key="i" class="rx-row">
          <w-input-number v-model="it.drugId" :min="1" placeholder="药品ID" style="width: 100px" />
          <w-input v-model="it.drugName" placeholder="药名" style="width: 150px" />
          <w-input v-model="it.dosage" placeholder="剂量" style="width: 80px" />
          <w-input-number v-model="it.days" :min="1" style="width: 80px" />
          <w-button link type="danger" @click="prescribeItems.splice(i, 1)">删</w-button>
        </div>
        <div class="rx-actions">
          <w-button size="small" @click="addPrescribeItem">添加药品</w-button>
          <w-button size="small" type="primary" @click="submitPrescribe">提交续方</w-button>
        </div>
      </template>
    </w-drawer>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.float-bar {
  margin-top: 12px;
}
.doctor-summary {
  margin-bottom: 12px;
  font-size: 13px;
  color: #47586e;
}
.chat-desc {
  font-size: 13px;
  color: #47586e;
  margin-bottom: 10px;
}
.chat-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 320px;
  overflow-y: auto;
  margin-bottom: 12px;
}
.chat-item .chat-meta {
  font-size: 12px;
  color: #98a3b3;
  margin-bottom: 2px;
}
.chat-doctor .chat-bubble {
  background: #eef4ff;
  border-radius: 8px;
  padding: 8px 10px;
}
.chat-patient .chat-bubble {
  background: #f4f6f9;
  border-radius: 8px;
  padding: 8px 10px;
}
.chat-input {
  display: flex;
  gap: 8px;
}
.sub-title {
  margin: 16px 0 8px;
}
.rx-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}
.rx-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
</style>
