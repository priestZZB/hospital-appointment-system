<template>
  <div class="page">
    <!-- 住院总览看板 -->
    <w-card class="page-card" title="住院总览" subtitle="在院患者 / 医嘱动态 / 预交金余额">
      <template #extra>
        <w-button icon="Refresh" circle @click="loadOverview" />
      </template>
      <w-table :data="overview" v-loading="overviewLoading" stripe @row-click="pickFromOverview">
        <w-table-column prop="admissionNo" label="住院号" width="150" />
        <w-table-column prop="patientName" label="患者" width="100" />
        <w-table-column label="床位" width="110">
          <template #default="{ row }">{{ row.roomNo ? `${row.roomNo}房${row.bedNo}床` : '未分床' }}</template>
        </w-table-column>
        <w-table-column prop="pendingOrderCount" label="待核对医嘱" width="110" />
        <w-table-column prop="executingOrderCount" label="执行中医嘱" width="110" />
        <w-table-column label="预交金余额" width="110">
          <template #default="{ row }">￥{{ fmt(row.depositBalance) }}</template>
        </w-table-column>
        <w-table-column label="累计费用" width="110">
          <template #default="{ row }">￥{{ fmt(row.totalFee) }}</template>
        </w-table-column>
        <w-table-column label="最新体征" min-width="220">
          <template #default="{ row }">
            <span v-if="row.latestVital" class="muted">
              T{{ row.latestVital.temperature ?? '-' }}℃ · P{{ row.latestVital.pulse ?? '-' }} · BP{{ row.latestVital.bloodPressure ?? '-' }} · SpO2{{ row.latestVital.bloodOxygen ?? '-' }}%
            </span>
            <span v-else class="muted">暂无</span>
          </template>
        </w-table-column>
        <w-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <w-button link type="primary" @click.stop="selectAdmission(row.admissionId)">接诊管理</w-button>
          </template>
        </w-table-column>
      </w-table>
    </w-card>

    <!-- 住院患者与操作 -->
    <w-card class="page-card" title="住院患者管理" subtitle="选择住院记录后开医嘱 / 办出院 / 手术申请 / 会诊 / 转科">
      <template #extra>
        <w-select v-model="queryStatus" placeholder="全部状态" clearable style="width: 140px" @change="loadAdmissions">
          <w-option label="在院" value="ADMITTED" />
          <w-option label="已出院" value="DISCHARGED" />
        </w-select>
      </template>

      <w-table :data="admissions" v-loading="listLoading" stripe highlight-current-row>
        <w-table-column prop="admissionNo" label="住院号" width="150" />
        <w-table-column prop="patientName" label="患者" width="100" />
        <w-table-column prop="admissionDiag" label="入院诊断" min-width="180" show-overflow-tooltip />
        <w-table-column label="床位" width="110">
          <template #default="{ row }">{{ row.currentRoomNo ? `${row.currentRoomNo}房${row.currentBedNo}床` : '未分床' }}</template>
        </w-table-column>
        <w-table-column prop="admissionTime" label="入院时间" width="170" />
        <w-table-column label="状态" width="90">
          <template #default="{ row }">
            <w-tag :type="row.status === 'ADMITTED' ? 'success' : 'info'" effect="light">
              {{ row.status === 'ADMITTED' ? '在院' : '已出院' }}
            </w-tag>
          </template>
        </w-table-column>
        <w-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <w-button link type="primary" @click="selectAdmission(row.id)">选择</w-button>
          </template>
        </w-table-column>
      </w-table>

      <template v-if="selected">
        <div class="selected-bar">
          当前患者：<b>{{ selected.patientName || selected.patientId }}</b>
          （{{ selected.admissionNo }} · {{ selected.admissionDiag }})
        </div>
        <div class="action-bar">
          <w-button type="primary" :disabled="selected.status !== 'ADMITTED'" @click="orderVisible = true">开医嘱</w-button>
          <w-button type="warning" :disabled="selected.status !== 'ADMITTED'" @click="dischargeVisible = true">办理出院</w-button>
          <w-button :disabled="selected.status !== 'ADMITTED'" @click="surgeryVisible = true">手术申请</w-button>
          <w-button :disabled="selected.status !== 'ADMITTED'" @click="consultVisible = true">发起会诊</w-button>
          <w-button :disabled="selected.status !== 'ADMITTED'" @click="transferVisible = true">转科</w-button>
          <w-button text type="primary" @click="openSummary">出院小结 / 病案首页</w-button>
        </div>

        <div class="section-title">医嘱记录</div>
        <w-table :data="orders" v-loading="ordersLoading" stripe size="small">
          <w-table-column prop="orderNo" label="医嘱号" width="180" />
          <w-table-column label="类型" width="90">
            <template #default="{ row }">{{ row.orderType === 'LONG_TERM' ? '长期' : '临时' }}</template>
          </w-table-column>
          <w-table-column prop="category" label="类别" width="90" />
          <w-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
          <w-table-column prop="frequency" label="频次" width="80" />
          <w-table-column label="状态" width="100">
            <template #default="{ row }">
              <w-tag :type="orderStatusType(row.status)" effect="light">{{ orderStatusText(row.status) }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column prop="openTime" label="开立时间" width="170" />
          <w-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <w-button v-if="row.status === 'OPEN' || row.status === 'CONFIRMED' || row.status === 'EXECUTING'"
                        link type="danger" @click="stopOrder(row)">停止</w-button>
              <span v-else class="muted">-</span>
            </template>
          </w-table-column>
        </w-table>

        <!-- 迭代10：手术申请记录（补显「已排台手术单」状态；后端回写 surgeryId 时可跳转手术中心） -->
        <div class="section-title">手术申请记录</div>
        <w-table :data="surgeries" stripe size="small" empty-text="该患者暂无手术申请记录">
          <w-table-column prop="surgeryName" label="手术名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.surgeryName || '-' }}</template>
          </w-table-column>
          <w-table-column label="麻醉方式" width="110">
            <template #default="{ row }">{{ row.anesthesiaType || '-' }}</template>
          </w-table-column>
          <w-table-column label="排台时间" width="160">
            <template #default="{ row }">{{ row.scheduledTime || '未排台' }}</template>
          </w-table-column>
          <w-table-column label="手术单状态" width="150">
            <template #default="{ row }">
              <w-tag v-if="applyScheduled(row.status)" type="primary" effect="light">已排台手术单</w-tag>
              <w-tag v-else :type="row.status === 'CANCELLED' ? 'info' : 'warning'" effect="light">
                {{ applyStatusText(row.status) }}
              </w-tag>
            </template>
          </w-table-column>
          <w-table-column label="操作" width="130">
            <template #default="{ row }">
              <router-link v-if="row.surgeryId" to="/surgery-center" class="surgery-link">前往手术中心</router-link>
              <span v-else class="muted">-</span>
            </template>
          </w-table-column>
        </w-table>
      </template>
    </w-card>

    <!-- 开医嘱 -->
    <w-dialog v-model="orderVisible" title="开立住院医嘱" width="560px">
      <w-form :model="orderForm" label-width="90px">
        <w-form-item label="医嘱类型">
          <w-radio-group v-model="orderForm.orderType">
            <w-radio value="LONG_TERM">长期医嘱</w-radio>
            <w-radio value="TEMPORARY">临时医嘱</w-radio>
          </w-radio-group>
        </w-form-item>
        <w-form-item label="类别">
          <w-select v-model="orderForm.category">
            <w-option label="药品" value="DRUG" />
            <w-option label="检查" value="EXAM" />
            <w-option label="检验" value="LAB" />
            <w-option label="输液" value="INFUSION" />
            <w-option label="护理" value="NURSING" />
            <w-option label="饮食" value="DIET" />
            <w-option label="其他" value="OTHER" />
          </w-select>
        </w-form-item>
        <w-form-item label="内容">
          <w-input v-model="orderForm.content" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="医嘱内容（药名/剂量/说明）" />
        </w-form-item>
        <w-form-item label="频次">
          <w-select v-model="orderForm.frequency" clearable placeholder="长期医嘱建议选择">
            <w-option label="每日一次 QD" value="QD" />
            <w-option label="每日两次 BID" value="BID" />
            <w-option label="每日三次 TID" value="TID" />
            <w-option label="每晚一次 QN" value="QN" />
            <w-option label="必要时 PRN" value="PRN" />
            <w-option label="立即 STAT" value="STAT" />
          </w-select>
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="orderVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="createOrder">提交</w-button>
      </template>
    </w-dialog>

    <!-- 办理出院 -->
    <w-dialog v-model="dischargeVisible" title="办理出院（出院小结 + 结算）" width="620px">
      <w-form :model="dischargeForm" label-width="90px">
        <w-form-item label="出院诊断">
          <w-input v-model="dischargeForm.dischargeDiag" placeholder="出院诊断" />
        </w-form-item>
        <w-form-item label="诊疗过程">
          <w-input v-model="dischargeForm.treatmentProcess" type="textarea" :rows="3" placeholder="住院期间主要诊疗过程" />
        </w-form-item>
        <w-form-item label="出院情况">
          <w-input v-model="dischargeForm.dischargeCondition" type="textarea" :rows="2" placeholder="出院时情况" />
        </w-form-item>
        <w-form-item label="出院医嘱">
          <w-input v-model="dischargeForm.dischargeAdvice" type="textarea" :rows="2" placeholder="出院医嘱/建议" />
        </w-form-item>
        <w-alert type="info" :closable="false" title="出院后将自动生成病案首页并衔接出院随访（3 天后电话随访）" />
      </w-form>
      <template #footer>
        <w-button @click="dischargeVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="doDischarge">确认出院</w-button>
      </template>
    </w-dialog>

    <!-- 手术申请 -->
    <w-dialog v-model="surgeryVisible" title="开手术申请单" width="540px">
      <w-form :model="surgeryForm" label-width="90px">
        <w-form-item label="手术名称">
          <w-input v-model="surgeryForm.surgeryName" placeholder="手术名称" />
        </w-form-item>
        <w-form-item label="麻醉方式">
          <w-select v-model="surgeryForm.anesthesiaType" clearable>
            <w-option label="全麻" value="全麻" />
            <w-option label="局麻" value="局麻" />
            <w-option label="椎管内麻醉" value="椎管内" />
            <w-option label="神经阻滞" value="神经阻滞" />
          </w-select>
        </w-form-item>
        <w-form-item label="备注">
          <w-input v-model="surgeryForm.remark" type="textarea" :rows="2" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="surgeryVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="applySurgery">提交</w-button>
      </template>
    </w-dialog>

    <!-- 发起会诊 -->
    <w-dialog v-model="consultVisible" title="发起院内会诊" width="540px">
      <w-form :model="consultForm" label-width="90px">
        <w-form-item label="受邀科室">
          <w-select v-model="consultForm.targetDeptId" filterable>
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="会诊目的">
          <w-input v-model="consultForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="病情摘要 / 会诊目的" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="consultVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="createConsult">提交</w-button>
      </template>
    </w-dialog>

    <!-- 转科 -->
    <w-dialog v-model="transferVisible" title="转科" width="480px">
      <w-form :model="transferForm" label-width="90px">
        <w-form-item label="转入科室">
          <w-select v-model="transferForm.targetDeptId" filterable>
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="转科原因">
          <w-input v-model="transferForm.reason" type="textarea" :rows="2" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="transferVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="doTransfer">确认转科</w-button>
      </template>
    </w-dialog>

    <!-- 出院小结 / 病案首页 -->
    <w-dialog v-model="summaryVisible" title="出院小结 / 病案首页" width="680px">
      <template v-if="summary">
        <div class="section-title">出院小结</div>
        <w-descriptions :column="2" border size="small">
          <w-descriptions-item label="出院诊断">{{ summary.dischargeDiag }}</w-descriptions-item>
          <w-descriptions-item label="出院时间">{{ summary.dischargeTime }}</w-descriptions-item>
          <w-descriptions-item label="诊疗过程" :span="2">{{ summary.treatmentProcess || '—' }}</w-descriptions-item>
          <w-descriptions-item label="出院情况" :span="2">{{ summary.dischargeCondition || '—' }}</w-descriptions-item>
          <w-descriptions-item label="出院医嘱" :span="2">{{ summary.dischargeAdvice || '—' }}</w-descriptions-item>
          <w-descriptions-item label="累计费用">￥{{ fmt(summary.totalFee) }}</w-descriptions-item>
          <w-descriptions-item label="预交金余额">￥{{ fmt(summary.depositBalance) }}</w-descriptions-item>
          <w-descriptions-item label="结算金额（正补/负退）" :span="2">￥{{ fmt(summary.settlementAmount) }}</w-descriptions-item>
        </w-descriptions>
        <template v-if="home">
          <div class="section-title">病案首页</div>
          <w-descriptions :column="3" border size="small">
            <w-descriptions-item label="住院天数">{{ home.hospitalDays }} 天</w-descriptions-item>
            <w-descriptions-item label="出院诊断">{{ home.dischargeDiag }}</w-descriptions-item>
            <w-descriptions-item label="主要手术">{{ home.mainOperation || '—' }}</w-descriptions-item>
            <w-descriptions-item label="床位费">￥{{ fmt(home.feeBed) }}</w-descriptions-item>
            <w-descriptions-item label="药品费">￥{{ fmt(home.feeDrug) }}</w-descriptions-item>
            <w-descriptions-item label="检查费">￥{{ fmt(home.feeExam) }}</w-descriptions-item>
            <w-descriptions-item label="检验费">￥{{ fmt(home.feeLab) }}</w-descriptions-item>
            <w-descriptions-item label="其他">￥{{ fmt(home.feeOther) }}</w-descriptions-item>
            <w-descriptions-item label="合计">￥{{ fmt(home.feeTotal) }}</w-descriptions-item>
          </w-descriptions>
        </template>
      </template>
      <w-empty v-else description="暂无出院记录" />
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import {
  applySurgeryApi,
  createInpatientConsultApi,
  createOrderApi,
  dischargeApi,
  getAdmissionsApi,
  getDischargeSummaryApi,
  getInpatientConsultsApi,
  getOrdersApi,
  getOverviewApi,
  getRecordHomeApi,
  getSurgeryAppliesApi,
  stopOrderApi,
  transferDeptApi,
} from '@/api/inpatient'
import { getDepartmentsApi } from '@/api/clinic'
import type {
  AdmissionVO,
  ConsultVO,
  DepartmentVO,
  DischargeSummaryVO,
  InpatientOverviewVO,
  MedicalOrderVO,
  MedicalRecordHomeVO,
  SurgeryApplyVO,
} from '@/types'

const overviewLoading = ref(false)
const listLoading = ref(false)
const ordersLoading = ref(false)
const submitting = ref(false)
const overview = ref<InpatientOverviewVO[]>([])
const admissions = ref<AdmissionVO[]>([])
const departments = ref<DepartmentVO[]>([])
const orders = ref<MedicalOrderVO[]>([])
const selected = ref<AdmissionVO | null>(null)
const queryStatus = ref('ADMITTED')

const orderVisible = ref(false)
const dischargeVisible = ref(false)
const surgeryVisible = ref(false)
const consultVisible = ref(false)
const transferVisible = ref(false)
const summaryVisible = ref(false)

const orderForm = reactive({ orderType: 'TEMPORARY', category: 'DRUG', content: '', frequency: '' })
const dischargeForm = reactive({ dischargeDiag: '', treatmentProcess: '', dischargeCondition: '', dischargeAdvice: '' })
const surgeryForm = reactive({ surgeryName: '', anesthesiaType: '', remark: '' })
const consultForm = reactive({ targetDeptId: null as number | null, reason: '' })
const transferForm = reactive({ targetDeptId: null as number | null, reason: '' })

const summary = ref<DischargeSummaryVO | null>(null)
const home = ref<MedicalRecordHomeVO | null>(null)
/** 会诊/手术单缓存（选中患者切换时刷新） */
const consults = ref<ConsultVO[]>([])
const surgeries = ref<SurgeryApplyVO[]>([])

function fmt(v?: number | null): string {
  return (v ?? 0).toFixed(2)
}

function orderStatusText(s?: string): string {
  const map: Record<string, string> = {
    OPEN: '待核对', CONFIRMED: '已核对', EXECUTING: '执行中', COMPLETED: '已完成', STOPPED: '已停止',
  }
  return map[s ?? ''] ?? s ?? '-'
}
function orderStatusType(s?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    OPEN: 'warning', CONFIRMED: 'primary', EXECUTING: 'primary', COMPLETED: 'success', STOPPED: 'info',
  }
  return map[s ?? ''] ?? 'info'
}

/** 迭代10：手术单是否已排台（SCHEDULED 及之后的状态） */
function applyScheduled(s?: string): boolean {
  return s === 'SCHEDULED' || s === 'PREOP_PASSED' || s === 'IN_OPERATION' || s === 'OPERATED'
}

function applyStatusText(s?: string): string {
  const map: Record<string, string> = {
    APPLIED: '待排台',
    SCHEDULED: '已排台',
    PREOP_PASSED: '术前评估通过',
    IN_OPERATION: '手术中',
    OPERATED: '已完成',
    CANCELLED: '已取消',
  }
  return map[s ?? ''] ?? s ?? '-'
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await getOverviewApi({})
  } catch (e) {
    WMessage.error((e as Error).message || '加载总览失败')
  } finally {
    overviewLoading.value = false
  }
}

async function loadAdmissions() {
  listLoading.value = true
  try {
    admissions.value = await getAdmissionsApi({ status: queryStatus.value || undefined })
  } catch (e) {
    WMessage.error((e as Error).message || '加载住院列表失败')
  } finally {
    listLoading.value = false
  }
}

function pickFromOverview(row: InpatientOverviewVO) {
  selectAdmission(row.admissionId)
}

async function selectAdmission(admissionId: number) {
  const found = admissions.value.find((a) => a.id === admissionId)
  selected.value = found ?? ({ id: admissionId } as AdmissionVO)
  if (!found) {
    listLoading.value = true
    try {
      const all = await getAdmissionsApi({})
      selected.value = all.find((a) => a.id === admissionId) ?? selected.value
      admissions.value = all
    } catch { /* ignore */ } finally { listLoading.value = false }
  }
  loadOrders()
  loadSelectedDocs()
}

async function loadOrders() {
  if (!selected.value) return
  ordersLoading.value = true
  try {
    orders.value = await getOrdersApi({ admissionId: selected.value.id })
  } catch (e) {
    WMessage.error((e as Error).message || '加载医嘱失败')
  } finally {
    ordersLoading.value = false
  }
}

async function loadSelectedDocs() {
  if (!selected.value) return
  try {
    consults.value = await getInpatientConsultsApi({ admissionId: selected.value.id })
    surgeries.value = await getSurgeryAppliesApi({ admissionId: selected.value.id })
  } catch { /* ignore */ }
}

async function createOrder() {
  if (!selected.value) return
  if (!orderForm.content.trim()) {
    WMessage.warning('请填写医嘱内容')
    return
  }
  submitting.value = true
  try {
    await createOrderApi({
      admissionId: selected.value.id,
      orderType: orderForm.orderType,
      category: orderForm.category,
      content: orderForm.content,
      frequency: orderForm.frequency || undefined,
    })
    WMessage.success('医嘱已开立，待护士核对')
    orderVisible.value = false
    orderForm.content = ''
    loadOrders()
    loadOverview()
  } catch (e) {
    WMessage.error((e as Error).message || '开立失败')
  } finally {
    submitting.value = false
  }
}

async function stopOrder(row: MedicalOrderVO) {
  try {
    await WMessageBox.confirm('确认停止该医嘱？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await stopOrderApi(row.id)
    WMessage.success('医嘱已停止')
    loadOrders()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

async function doDischarge() {
  if (!selected.value) return
  if (!dischargeForm.dischargeDiag.trim()) {
    WMessage.warning('请填写出院诊断')
    return
  }
  submitting.value = true
  try {
    const vo = await dischargeApi({
      admissionId: selected.value.id,
      dischargeDiag: dischargeForm.dischargeDiag,
      treatmentProcess: dischargeForm.treatmentProcess || undefined,
      dischargeCondition: dischargeForm.dischargeCondition || undefined,
      dischargeAdvice: dischargeForm.dischargeAdvice || undefined,
    })
    WMessage.success(`出院办理完成，结算金额 ￥${fmt(vo.settlementAmount)}（正数补缴 / 负数退还）`)
    dischargeVisible.value = false
    loadAdmissions()
    loadOverview()
  } catch (e) {
    WMessage.error((e as Error).message || '出院办理失败')
  } finally {
    submitting.value = false
  }
}

async function applySurgery() {
  if (!selected.value) return
  if (!surgeryForm.surgeryName.trim()) {
    WMessage.warning('请填写手术名称')
    return
  }
  submitting.value = true
  try {
    await applySurgeryApi({
      admissionId: selected.value.id,
      surgeryName: surgeryForm.surgeryName,
      anesthesiaType: surgeryForm.anesthesiaType || undefined,
      remark: surgeryForm.remark || undefined,
    })
    WMessage.success('手术申请已提交')
    surgeryVisible.value = false
    loadSelectedDocs()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function createConsult() {
  if (!selected.value) return
  if (!consultForm.targetDeptId) {
    WMessage.warning('请选择受邀科室')
    return
  }
  submitting.value = true
  try {
    await createInpatientConsultApi({
      admissionId: selected.value.id,
      targetDeptId: consultForm.targetDeptId,
      reason: consultForm.reason,
    })
    WMessage.success('会诊申请已发出')
    consultVisible.value = false
    loadSelectedDocs()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function doTransfer() {
  if (!selected.value) return
  if (!transferForm.targetDeptId) {
    WMessage.warning('请选择转入科室')
    return
  }
  submitting.value = true
  try {
    await transferDeptApi({
      admissionId: selected.value.id,
      targetDeptId: transferForm.targetDeptId,
      reason: transferForm.reason || undefined,
    })
    WMessage.success('转科完成，原床位已释放，请通知新科室护士站分床')
    transferVisible.value = false
    loadAdmissions()
    loadOverview()
  } catch (e) {
    WMessage.error((e as Error).message || '转科失败')
  } finally {
    submitting.value = false
  }
}

async function openSummary() {
  if (!selected.value) return
  summaryVisible.value = true
  summary.value = null
  home.value = null
  try {
    summary.value = await getDischargeSummaryApi(selected.value.id)
    home.value = await getRecordHomeApi(selected.value.id)
  } catch { /* ignore */ }
}

onMounted(() => {
  loadOverview()
  loadAdmissions()
  loadDepartments()
})

async function loadDepartments() {
  try {
    departments.value = await getDepartmentsApi()
  } catch { /* ignore */ }
}
</script>

<style scoped>
.page { padding: 4px; display: flex; flex-direction: column; gap: 12px; }
.page-card { max-width: 1280px; margin: 0 auto; width: 100%; }
.muted { color: #aaa; }
.selected-bar { margin: 12px 0 8px; padding: 8px 12px; background: var(--w-fill-color-light, #f5f7fa); border-radius: 6px; font-size: 13px; }
.action-bar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.section-title { font-weight: 600; margin: 8px 0 8px; }
.surgery-link { color: var(--w3-color-primary, #2d5afa); text-decoration: none; font-size: 13px; }
.surgery-link:hover { text-decoration: underline; }
</style>
