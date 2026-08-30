<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import { CircleCheck, File, List, Refresh, Scan, Search, Send, Time } from '@win-design-next/icons-vue'
import { getUnpaidPrescriptionsApi } from '@/api/clinic'
import { getUnpaidExamApplicationsApi, getUnpaidInfusionOrdersApi } from '@/api/medsupply'
import {
  cashierCreateOrderApi,
  cashierPayApi,
  cashierRefundApi,
  createSettleApi,
  getSettleTodayApi,
} from '@/api/payment'
import type { ExamApplicationVO, InfusionOrder, PrescriptionVO, SettleSummaryVO } from '@/types'

const ORDER_TYPE_LABELS: Record<string, string> = {
  DRUG: '处方',
  EXAM: '检查',
  INFUSION: '输液',
  REGISTRATION: '挂号',
}

const patientIdText = ref('')
const queryLoading = ref(false)
const payLoading = ref(false)
const refundLoading = ref(false)
const settleLoading = ref(false)

const prescriptions = ref<PrescriptionVO[]>([])
const exams = ref<ExamApplicationVO[]>([])
const infusions = ref<InfusionOrder[]>([])

const selectedRx = ref<PrescriptionVO[]>([])
const selectedExams = ref<ExamApplicationVO[]>([])
const selectedInfusions = ref<InfusionOrder[]>([])

const settleSummary = ref<SettleSummaryVO | null>(null)

const refundForm = reactive({ orderId: '', reason: '' })

function formatMoney(value?: number): string {
  if (value == null) return '-'
  return `¥${Number(value).toFixed(2)}`
}

function rxAmount(p: PrescriptionVO): number {
  return p.totalAmount ?? 0
}

function examAmount(e: ExamApplicationVO): number {
  return e.totalAmount ?? 0
}

function infusionAmount(o: InfusionOrder): number {
  if (o.totalAmount != null && Number(o.totalAmount) > 0) return Number(o.totalAmount)
  return (o.unitPrice ?? 0) * (o.days ?? 1)
}

function isPriced(value: number): boolean {
  return value > 0
}

function canSelectRx(row: PrescriptionVO): boolean {
  return isPriced(rxAmount(row))
}
function canSelectExam(row: ExamApplicationVO): boolean {
  return isPriced(examAmount(row))
}
function canSelectInfusion(row: InfusionOrder): boolean {
  return isPriced(infusionAmount(row))
}

function rxItemsText(p: PrescriptionVO): string {
  if (!p.items || !p.items.length) return '-'
  return p.items.map((i) => `${i.drugName}×${i.quantity ?? 1}`).join('、')
}

const selectedCount = computed(
  () => selectedRx.value.length + selectedExams.value.length + selectedInfusions.value.length,
)
const selectedAmount = computed(() => {
  let sum = 0
  selectedRx.value.forEach((p) => (sum += rxAmount(p)))
  selectedExams.value.forEach((e) => (sum += examAmount(e)))
  selectedInfusions.value.forEach((o) => (sum += infusionAmount(o)))
  return sum
})

const settleTypeRows = computed(() => {
  const detail = settleSummary.value?.detail || []
  return detail.map((d) => {
    const type = d.order_type ?? ''
    return {
      type,
      label: ORDER_TYPE_LABELS[type] ?? type,
      amount: Number(d.total_amount ?? 0),
      count: Number(d.order_count ?? 0),
    }
  })
})

function onRxSelectionChange(val: PrescriptionVO[]) {
  selectedRx.value = val
}
function onExamSelectionChange(val: ExamApplicationVO[]) {
  selectedExams.value = val
}
function onInfusionSelectionChange(val: InfusionOrder[]) {
  selectedInfusions.value = val
}

function clearSelection() {
  selectedRx.value = []
  selectedExams.value = []
  selectedInfusions.value = []
}

async function fetchUnpaid() {
  const raw = patientIdText.value.trim()
  const pid = Number(raw)
  if (!raw || Number.isNaN(pid)) {
    WMessage.warning('请输入正确的患者 ID')
    return
  }
  queryLoading.value = true
  clearSelection()
  try {
    const [rx, ex, inf] = await Promise.all([
      getUnpaidPrescriptionsApi(pid),
      getUnpaidExamApplicationsApi(pid),
      getUnpaidInfusionOrdersApi(pid),
    ])
    prescriptions.value = Array.isArray(rx) ? rx : []
    exams.value = Array.isArray(ex) ? ex : []
    infusions.value = Array.isArray(inf) ? inf : []
    const total = prescriptions.value.length + exams.value.length + infusions.value.length
    WMessage.success(`查询到 ${total} 条待缴费项目`)
  } catch (e) {
    WMessage.error((e as Error).message || '待缴费查询失败')
  } finally {
    queryLoading.value = false
  }
}

interface CollectItem {
  type: 'DRUG' | 'EXAM' | 'INFUSION'
  relatedId: number
  itemName: string
  price: number
}

function collectItems(): CollectItem[] {
  const list: CollectItem[] = []
  selectedRx.value.forEach((p) =>
    list.push({ type: 'DRUG', relatedId: p.id, itemName: `处方费 #${p.id}`, price: rxAmount(p) }),
  )
  selectedExams.value.forEach((e) =>
    list.push({ type: 'EXAM', relatedId: e.id, itemName: `${e.examItemName || '检查费'} #${e.id}`, price: examAmount(e) }),
  )
  selectedInfusions.value.forEach((o) =>
    list.push({ type: 'INFUSION', relatedId: o.id, itemName: `${o.drugName || '输液费'} #${o.id}`, price: infusionAmount(o) }),
  )
  return list
}

async function handleCollect() {
  const items = collectItems()
  if (!items.length) {
    WMessage.warning('请先勾选待缴费项目')
    return
  }
  try {
    await WMessageBox.confirm(
      `确认收取 ${items.length} 项，合计 ${formatMoney(selectedAmount.value)}？`,
      '收款确认',
      { type: 'warning', confirmButtonText: '确认收款', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  payLoading.value = true
  try {
    const pid = Number(patientIdText.value.trim())
    let success = 0
    for (const it of items) {
      const order = await cashierCreateOrderApi({
        patientId: pid,
        orderType: it.type,
        relatedId: it.relatedId,
        items: [{ itemName: it.itemName, qty: 1, price: it.price }],
      })
      await cashierPayApi(order.id)
      success += 1
    }
    WMessage.success(`收款成功 ${success} 笔`)
    clearSelection()
    await fetchUnpaid()
    await loadSettleToday()
  } catch (e) {
    WMessage.error((e as Error).message || '收款失败')
  } finally {
    payLoading.value = false
  }
}

async function loadSettleToday() {
  try {
    settleSummary.value = await getSettleTodayApi()
  } catch {
    settleSummary.value = null
  }
}

async function handleSettle() {
  try {
    await WMessageBox.confirm('确认生成今日日结单吗？', '日结确认', {
      type: 'warning',
      confirmButtonText: '生成日结单',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  settleLoading.value = true
  try {
    const record = await createSettleApi()
    WMessage.success(`日结单已生成${record?.settleNo ? `：${record.settleNo}` : ''}`)
    await loadSettleToday()
  } catch (e) {
    WMessage.error((e as Error).message || '生成日结单失败')
  } finally {
    settleLoading.value = false
  }
}

async function handleRefund() {
  const raw = refundForm.orderId.trim()
  const orderId = Number(raw)
  if (!raw || Number.isNaN(orderId)) {
    WMessage.warning('请输入正确的订单 ID')
    return
  }
  if (!refundForm.reason.trim()) {
    WMessage.warning('请填写退费原因')
    return
  }
  try {
    await WMessageBox.confirm(`确认对订单 #${orderId} 退费吗？`, '退费确认', {
      type: 'warning',
      confirmButtonText: '确认退费',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  refundLoading.value = true
  try {
    await cashierRefundApi({ orderId, refundReason: refundForm.reason.trim() })
    WMessage.success('退费成功')
    refundForm.orderId = ''
    refundForm.reason = ''
    await loadSettleToday()
  } catch (e) {
    WMessage.error((e as Error).message || '退费失败')
  } finally {
    refundLoading.value = false
  }
}

onMounted(() => {
  loadSettleToday()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">收费工作台</h2>
      <p class="page-subtitle">查询患者待缴费项目 · 勾选计价收款 · 日结对账 · 退费</p>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <w-input
            v-model="patientIdText"
            placeholder="输入患者 ID（档案 ID）"
            clearable
            style="width: 260px"
            @keyup.enter="fetchUnpaid"
          />
          <w-button type="primary" :icon="Search" :loading="queryLoading" @click="fetchUnpaid">查询待缴费</w-button>
        </div>
        <div class="table-toolbar__right">
          <w-tag size="small" effect="light" type="primary">
            处方 {{ prescriptions.length }} · 检查 {{ exams.length }} · 输液 {{ infusions.length }}
          </w-tag>
        </div>
      </div>
    </w-card>

    <!-- 收款汇总条 -->
    <w-card shadow="never" class="hospital-card collect-bar">
      <div class="collect-bar__left">
        <span class="collect-bar__label">已勾选</span>
        <span class="collect-bar__count">{{ selectedCount }}</span>
        <span class="collect-bar__unit">项</span>
        <span class="collect-bar__divider" />
        <span class="collect-bar__label">应收合计</span>
        <span class="collect-bar__amount">{{ formatMoney(selectedAmount) }}</span>
      </div>
      <div class="collect-bar__right">
        <w-button type="primary" :icon="Send" :loading="payLoading" :disabled="!selectedCount" @click="handleCollect">
          收款
        </w-button>
      </div>
    </w-card>

    <!-- 待缴费处方 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-title"><File class="toolbar-title__icon" />待缴费处方</span>
          <w-tag size="small" effect="light" type="primary">共 {{ prescriptions.length }} 条</w-tag>
        </div>
      </div>
      <div v-loading="queryLoading" class="table-wrap">
        <w-table :data="prescriptions" border stripe row-key="id" size="default" empty-text="暂无待缴费处方">
          <w-table-column type="selection" width="50" :selectable="canSelectRx" />
          <w-table-column type="index" label="#" width="60" align="center" />
          <w-table-column prop="id" label="处方ID" width="90" align="center" />
          <w-table-column label="明细项（名称×数量）" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ rxItemsText(row) }}</template>
          </w-table-column>
          <w-table-column label="应缴金额" width="120" align="right">
            <template #default="{ row }">
              <span v-if="isPriced(rxAmount(row))" class="price">{{ formatMoney(rxAmount(row)) }}</span>
              <w-tag v-else size="small" effect="light" type="warning">待划价</w-tag>
            </template>
          </w-table-column>
          <w-table-column prop="createTime" label="开单时间" min-width="160" show-overflow-tooltip />
        </w-table>
      </div>
    </w-card>

    <!-- 待缴费检查 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-title"><Scan class="toolbar-title__icon" />待缴费检查</span>
          <w-tag size="small" effect="light" type="primary">共 {{ exams.length }} 条</w-tag>
        </div>
      </div>
      <div v-loading="queryLoading" class="table-wrap">
        <w-table :data="exams" border stripe row-key="id" size="default" empty-text="暂无待缴费检查">
          <w-table-column type="selection" width="50" :selectable="canSelectExam" />
          <w-table-column type="index" label="#" width="60" align="center" />
          <w-table-column prop="id" label="申请ID" width="90" align="center" />
          <w-table-column prop="examItemName" label="检查项目" min-width="160" show-overflow-tooltip />
          <w-table-column prop="execDept" label="执行科室" min-width="120" show-overflow-tooltip />
          <w-table-column label="应缴金额" width="120" align="right">
            <template #default="{ row }">
              <span v-if="isPriced(examAmount(row))" class="price">{{ formatMoney(examAmount(row)) }}</span>
              <w-tag v-else size="small" effect="light" type="warning">待划价</w-tag>
            </template>
          </w-table-column>
          <w-table-column prop="createTime" label="申请时间" min-width="160" show-overflow-tooltip />
        </w-table>
      </div>
    </w-card>

    <!-- 待缴费输液 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-title"><Time class="toolbar-title__icon" />待缴费输液</span>
          <w-tag size="small" effect="light" type="primary">共 {{ infusions.length }} 条</w-tag>
        </div>
      </div>
      <div v-loading="queryLoading" class="table-wrap">
        <w-table :data="infusions" border stripe row-key="id" size="default" empty-text="暂无待缴费输液单">
          <w-table-column type="selection" width="50" :selectable="canSelectInfusion" />
          <w-table-column type="index" label="#" width="60" align="center" />
          <w-table-column prop="id" label="输液单ID" width="100" align="center" />
          <w-table-column prop="drugName" label="药品" min-width="140" show-overflow-tooltip />
          <w-table-column prop="dosage" label="剂量" width="100" align="center" />
          <w-table-column prop="usageMethod" label="用法" width="120" show-overflow-tooltip />
          <w-table-column label="应缴金额" width="120" align="right">
            <template #default="{ row }">
              <span v-if="isPriced(infusionAmount(row))" class="price">{{ formatMoney(infusionAmount(row)) }}</span>
              <w-tag v-else size="small" effect="light" type="warning">待划价</w-tag>
            </template>
          </w-table-column>
          <w-table-column prop="createTime" label="开单时间" min-width="160" show-overflow-tooltip />
        </w-table>
      </div>
    </w-card>

    <!-- 日结 + 退费 -->
    <div class="settle-grid">
      <w-card shadow="never" class="hospital-card">
        <template #header>
          <div class="card-head">
            <span class="card-head__title"><List class="card-head__icon" />今日日结汇总</span>
            <w-button size="small" text :icon="Refresh" @click="loadSettleToday">刷新</w-button>
          </div>
        </template>
        <div class="settle-summary">
          <div class="settle-summary__row">
            <span class="settle-summary__label">收款总额</span>
            <span class="settle-summary__value">{{ formatMoney(settleSummary?.totalAmount) }}</span>
          </div>
          <div class="settle-summary__row">
            <span class="settle-summary__label">收款笔数</span>
            <span class="settle-summary__value">{{ settleSummary?.orderCount ?? 0 }}</span>
          </div>
          <div v-if="settleTypeRows.length" class="settle-summary__types">
            <div v-for="t in settleTypeRows" :key="t.type" class="settle-summary__type">
              <w-tag size="small" effect="light" type="primary">{{ t.label }}</w-tag>
              <span class="settle-summary__type-meta">{{ formatMoney(t.amount) }} · {{ t.count }} 笔</span>
            </div>
          </div>
          <div v-else class="muted">暂无今日收款记录</div>
        </div>
        <div class="settle-actions">
          <w-button type="primary" plain :icon="List" :loading="settleLoading" @click="handleSettle">生成日结单</w-button>
        </div>
      </w-card>

      <w-card shadow="never" class="hospital-card">
        <template #header>
          <div class="card-head">
            <span class="card-head__title"><CircleCheck class="card-head__icon" />退费</span>
          </div>
        </template>
        <div class="refund-form">
          <w-input
            v-model="refundForm.orderId"
            placeholder="订单 ID / 订单号"
            clearable
            style="width: 100%"
          />
          <w-input
            v-model="refundForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="退费原因"
          />
          <w-button type="danger" plain :icon="Send" :loading="refundLoading" @click="handleRefund">确认退费</w-button>
        </div>
      </w-card>
    </div>
  </div>
</template>

<style scoped>
.toolbar-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.toolbar-title__icon {
  color: var(--w3-color-primary);
}
.table-wrap {
  min-height: 120px;
}
.price {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--w3-color-primary);
}

.collect-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  background: var(--w3-color-primary-plain);
}
.collect-bar__left {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.collect-bar__label {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.collect-bar__count {
  font-size: 22px;
  font-weight: 700;
  color: var(--w3-color-primary);
  font-variant-numeric: tabular-nums;
}
.collect-bar__unit {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.collect-bar__divider {
  width: 1px;
  height: 16px;
  background: var(--hospital-border);
  margin: 0 6px;
}
.collect-bar__amount {
  font-size: 22px;
  font-weight: 700;
  color: var(--w3-color-danger);
  font-variant-numeric: tabular-nums;
}

.settle-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}
@media (max-width: 1000px) {
  .settle-grid {
    grid-template-columns: 1fr;
  }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-head__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.card-head__icon {
  color: var(--w3-color-primary);
  font-size: 17px;
}

.settle-summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.settle-summary__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--hospital-border-lighter);
}
.settle-summary__label {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.settle-summary__value {
  font-size: 18px;
  font-weight: 700;
  color: var(--hospital-text-main);
  font-variant-numeric: tabular-nums;
}
.settle-summary__types {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.settle-summary__type {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.settle-summary__type-meta {
  font-size: 12.5px;
  color: var(--hospital-text-second);
  font-variant-numeric: tabular-nums;
}
.settle-actions {
  margin-top: 14px;
}

.refund-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.muted {
  color: var(--hospital-text-third);
  font-size: 13px;
}
</style>
