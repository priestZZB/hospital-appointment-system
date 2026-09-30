<script setup lang="ts">
/**
 * 医保结算页（迭代11，base=/api/payment/insurance）。
 * Tab1 结算模拟：患者/医保号/业务类型 + 可增删明细行 → 提交结算 → 结果卡片（三色金额块 + 甲乙自费小计 + 逐项拆分）。
 * Tab2 结算单列表：分页查询 + 详情抽屉（复用结果卡片）/ 票据 PDF（新窗口）/ 冲正。
 * 管理员 + 收费员可见；字段驼峰/下划线双命名防御式兼容。
 */
import { computed, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import { Delete, Pdf, Plus, RefreshLeft, Search, Send } from '@win-design-next/icons-vue'
import {
  fetchVoucherPdfBlob,
  getInsuranceSettleDetailApi,
  getInsuranceSettleListApi,
  getVoucherPdfUrl,
  insuranceSettleApi,
  reverseInsuranceSettleApi,
} from '@/api/insurance'
import type { InsuranceSettle } from '@/types'
import { getUser } from '@/utils/auth'
import SettleResultCard from './result-card.vue'

/* ================= 选项常量 ================= */
const BIZ_TYPE_OPTIONS = [
  { value: 'REGISTER', label: '挂号结算' },
  { value: 'OUTPATIENT', label: '门诊结算' },
  { value: 'INPATIENT', label: '住院结算' },
]
const ITEM_TYPE_OPTIONS = [
  { value: 'REGISTER', label: '挂号' },
  { value: 'DRUG', label: '药品' },
  { value: 'EXAM', label: '检查' },
  { value: 'LAB', label: '检验' },
  { value: 'TREATMENT', label: '诊疗' },
  { value: 'MATERIAL', label: '卫材' },
  { value: 'CHARGED_ITEM', label: '收费项目' },
]

const BIZ_TYPE_TEXT: Record<string, string> = {
  REGISTER: '挂号结算',
  OUTPATIENT: '门诊结算',
  INPATIENT: '住院结算',
}
function bizTypeText(v?: string): string {
  if (!v) return '-'
  return BIZ_TYPE_TEXT[v.toUpperCase()] || v
}

const SETTLE_STATUS_META: Record<string, { label: string; tag: 'success' | 'danger' | 'info' }> = {
  SETTLED: { label: '已结算', tag: 'success' },
  REVERSED: { label: '已冲正', tag: 'danger' },
}
function statusMeta(status?: string): { label: string; tag: 'success' | 'danger' | 'info' } {
  const key = (status || '').toUpperCase()
  return SETTLE_STATUS_META[key] || { label: status || '-', tag: 'info' }
}

function fmtMoney(v?: number | null): string {
  if (v == null) return '-'
  return `¥${Number(v).toFixed(2)}`
}

/* ================= Tab1：结算模拟 ================= */
const storedUser = getUser()
const form = reactive({
  patientId: null as number | null,
  insuranceNo: '',
  bizType: 'OUTPATIENT',
  bizRefId: '',
  operatorId: (storedUser && storedUser.userId != null ? Number(storedUser.userId) : null) as number | null,
})

interface SettleDraftRow {
  key: number
  itemType: string
  refId: string
  itemName: string
  amount: number | null
}
let rowKeySeed = 0
function emptyRow(): SettleDraftRow {
  rowKeySeed += 1
  return { key: rowKeySeed, itemType: '', refId: '', itemName: '', amount: null }
}
const draftRows = ref<SettleDraftRow[]>([emptyRow()])

const draftTotal = computed(() =>
  draftRows.value.reduce((sum, r) => sum + (typeof r.amount === 'number' && !Number.isNaN(r.amount) ? r.amount : 0), 0),
)

function addRow() {
  draftRows.value.push(emptyRow())
}
function removeRow(key: number) {
  if (draftRows.value.length <= 1) {
    WMessage.warning('至少保留一条结算明细')
    return
  }
  draftRows.value = draftRows.value.filter((r) => r.key !== key)
}

const submitting = ref(false)
const settleResult = ref<InsuranceSettle | null>(null)

async function handleSubmit() {
  const pid = form.patientId
  if (pid == null || !Number.isFinite(pid) || pid <= 0) {
    WMessage.warning('请输入正确的患者 ID')
    return
  }
  if (!form.bizType) {
    WMessage.warning('请选择业务类型')
    return
  }
  if (!draftRows.value.length) {
    WMessage.warning('请至少填写一条结算明细')
    return
  }
  for (let i = 0; i < draftRows.value.length; i += 1) {
    const r = draftRows.value[i]
    if (!r.itemType) {
      WMessage.warning(`第 ${i + 1} 行请选择项目类型`)
      return
    }
    if (!r.itemName.trim()) {
      WMessage.warning(`第 ${i + 1} 行请填写项目名称`)
      return
    }
    if (r.amount == null || Number.isNaN(r.amount) || r.amount <= 0) {
      WMessage.warning(`第 ${i + 1} 行请填写大于 0 的金额`)
      return
    }
  }
  try {
    await WMessageBox.confirm(
      `确认提交医保结算？共 ${draftRows.value.length} 项，合计 ${fmtMoney(draftTotal.value)}`,
      '结算确认',
      { type: 'warning', confirmButtonText: '提交结算', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    settleResult.value = await insuranceSettleApi({
      patientId: pid,
      insuranceNo: form.insuranceNo.trim() || undefined,
      bizType: form.bizType,
      bizRefId: form.bizRefId.trim() || undefined,
      operatorId: form.operatorId ?? undefined,
      items: draftRows.value.map((r) => ({
        itemType: r.itemType,
        refId: r.refId.trim() || undefined,
        itemName: r.itemName.trim(),
        amount: Number(r.amount),
      })),
    })
    WMessage.success('医保结算成功')
  } catch (e) {
    WMessage.error((e as Error).message || '医保结算失败')
  } finally {
    submitting.value = false
  }
}

/* ================= Tab2：结算单列表 ================= */
const activeTab = ref('settle')
const recordsLoaded = ref(false)
const listLoading = ref(false)
const records = ref<InsuranceSettle[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const query = reactive({ patientId: null as number | null, bizType: '' })

function buildListParams(): { patientId?: number; bizType?: string; pageNo: number; pageSize: number } {
  const params: { patientId?: number; bizType?: string; pageNo: number; pageSize: number } = {
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  }
  if (query.patientId != null && Number.isFinite(query.patientId) && query.patientId > 0) params.patientId = query.patientId
  if (query.bizType) params.bizType = query.bizType
  return params
}

async function fetchRecords() {
  listLoading.value = true
  try {
    const page = await getInsuranceSettleListApi(buildListParams())
    records.value = Array.isArray(page?.records) ? page.records : []
    total.value = page?.total ?? 0
    recordsLoaded.value = true
  } catch (e) {
    WMessage.error((e as Error).message || '结算单列表加载失败')
  } finally {
    listLoading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchRecords()
}
function handleReset() {
  query.patientId = null
  query.bizType = ''
  pageNo.value = 1
  fetchRecords()
}
function handleSizeChange() {
  pageNo.value = 1
  fetchRecords()
}
function onTabChange(name: string | number) {
  if (name === 'records' && !recordsLoaded.value) fetchRecords()
}

/* ---------- 详情抽屉 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<InsuranceSettle | null>(null)

async function openDetail(row: InsuranceSettle) {
  if (row.id == null) {
    WMessage.warning('结算单 ID 缺失，无法查看详情')
    return
  }
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    detailData.value = await getInsuranceSettleDetailApi(row.id)
  } catch (e) {
    WMessage.error((e as Error).message || '结算单详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

/* ---------- 票据 PDF（blob 优先，直链兜底，均新窗口打开） ---------- */
function openVoucher(row: InsuranceSettle) {
  if (row.id == null) {
    WMessage.warning('结算单 ID 缺失，无法预览票据')
    return
  }
  const id = row.id
  // 先同步开窗保留用户手势，避免 await 后 window.open 被弹窗拦截
  const win = window.open('', '_blank')
  if (!win) {
    WMessage.warning('浏览器拦截了新窗口，请允许弹窗后重试')
    return
  }
  fetchVoucherPdfBlob(id)
    .then((blob) => {
      const url = URL.createObjectURL(blob)
      win.location.href = url
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
    })
    .catch(() => {
      // blob 拉取失败（如直链鉴权差异）：降级为 baseURL 直链新窗口（与既有凭证 PDF getReceiptUrl 同款做法）
      win.location.href = getVoucherPdfUrl(id)
    })
}

/* ---------- 冲正 ---------- */
async function handleReverse(row: InsuranceSettle) {
  if (row.id == null) {
    WMessage.warning('结算单 ID 缺失，无法冲正')
    return
  }
  const id = row.id
  const no = row.settleNo || row.settle_no || `#${id}`
  try {
    await WMessageBox.confirm(`确认冲正结算单 ${no} 吗？冲正后该单状态将变为「已冲正」。`, '冲正确认', {
      type: 'warning',
      confirmButtonText: '确认冲正',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await reverseInsuranceSettleApi(id)
    WMessage.success('冲正成功')
    await fetchRecords()
    if (detailData.value && detailData.value.id === id) detailData.value.status = 'REVERSED'
  } catch (e) {
    WMessage.error((e as Error).message || '冲正失败')
  }
}
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">医保结算</h2>
      <p class="page-subtitle">医保费用模拟结算 · 结算单查询 / 详情 / 票据 / 冲正（H2/H3）</p>
    </div>

    <w-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ============ Tab1 结算模拟 ============ -->
      <w-tab-pane label="结算模拟" name="settle">
        <div class="tab-inner">
          <w-card shadow="never" class="hospital-card">
            <template #header>
              <span class="card-head__title">结算信息</span>
            </template>
            <w-form inline class="settle-form">
              <w-form-item label="患者 ID" required>
                <w-input-number
                  v-model="form.patientId"
                  :min="1"
                  :controls="false"
                  placeholder="患者档案 ID"
                  style="width: 150px"
                />
              </w-form-item>
              <w-form-item label="医保号">
                <w-input v-model="form.insuranceNo" placeholder="选填，如 3301..." clearable style="width: 190px" />
              </w-form-item>
              <w-form-item label="业务类型" required>
                <w-select v-model="form.bizType" style="width: 150px">
                  <w-option v-for="o in BIZ_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </w-select>
              </w-form-item>
              <w-form-item label="业务引用ID">
                <w-input v-model="form.bizRefId" placeholder="选填，如处方/申请单 ID" clearable style="width: 170px" />
              </w-form-item>
              <w-form-item label="操作员 ID">
                <w-input-number
                  v-model="form.operatorId"
                  :min="1"
                  :controls="false"
                  placeholder="选填"
                  style="width: 130px"
                />
              </w-form-item>
            </w-form>

            <div class="draft-head">
              <span class="card-head__title card-head__title--sm">结算明细（至少 1 行，可增删）</span>
              <w-button size="small" type="primary" plain :icon="Plus" @click="addRow">添加明细</w-button>
            </div>
            <w-table :data="draftRows" border row-key="key" size="small" empty-text="请添加结算明细">
              <w-table-column type="index" label="#" width="52" align="center" />
              <w-table-column label="项目类型" width="170">
                <template #default="{ row }">
                  <w-select v-model="row.itemType" size="small" placeholder="选择类型">
                    <w-option v-for="o in ITEM_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                  </w-select>
                </template>
              </w-table-column>
              <w-table-column label="引用 ID" width="150">
                <template #default="{ row }">
                  <w-input v-model="row.refId" size="small" placeholder="选填" clearable />
                </template>
              </w-table-column>
              <w-table-column label="项目名称" min-width="200">
                <template #default="{ row }">
                  <w-input v-model="row.itemName" size="small" placeholder="如 门诊诊察费" clearable />
                </template>
              </w-table-column>
              <w-table-column label="金额（元）" width="180">
                <template #default="{ row }">
                  <w-input-number
                    v-model="row.amount"
                    size="small"
                    :min="0"
                    :precision="2"
                    :controls="false"
                    placeholder="0.00"
                    style="width: 100%"
                  />
                </template>
              </w-table-column>
              <w-table-column label="操作" width="80" align="center">
                <template #default="{ $index }">
                  <w-button
                    size="small"
                    text
                    type="danger"
                    :icon="Delete"
                    :disabled="draftRows.length <= 1"
                    @click="removeRow(draftRows[$index]?.key ?? -1)"
                  >
                    删除
                  </w-button>
                </template>
              </w-table-column>
            </w-table>

            <div class="draft-footer">
              <div class="draft-footer__total">
                <span>明细 {{ draftRows.length }} 项 · 合计</span>
                <span class="draft-footer__amount">{{ fmtMoney(draftTotal) }}</span>
              </div>
              <w-button type="primary" :icon="Send" :loading="submitting" @click="handleSubmit">提交结算</w-button>
            </div>
          </w-card>

          <!-- 结果卡片 -->
          <w-card v-if="settleResult" shadow="never" class="hospital-card">
            <template #header>
              <span class="card-head__title">结算结果</span>
            </template>
            <SettleResultCard :settle="settleResult" />
          </w-card>
        </div>
      </w-tab-pane>

      <!-- ============ Tab2 结算单列表 ============ -->
      <w-tab-pane label="结算单列表" name="records">
        <div class="tab-inner">
          <w-card shadow="never" class="hospital-card">
            <w-form inline class="query-form">
              <w-form-item label="患者 ID">
                <w-input-number
                  v-model="query.patientId"
                  :min="1"
                  :controls="false"
                  placeholder="患者 ID"
                  style="width: 160px"
                  @keyup.enter="handleSearch"
                />
              </w-form-item>
              <w-form-item label="业务类型">
                <w-select v-model="query.bizType" placeholder="全部" clearable style="width: 160px">
                  <w-option v-for="o in BIZ_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </w-select>
              </w-form-item>
              <w-form-item>
                <w-button type="primary" :icon="Search" :loading="listLoading" @click="handleSearch">查询</w-button>
                <w-button :icon="RefreshLeft" @click="handleReset">重置</w-button>
              </w-form-item>
            </w-form>
          </w-card>

          <w-card shadow="never" class="hospital-card">
            <div class="table-toolbar list-toolbar">
              <div class="table-toolbar__left">
                <span class="table-total">共 {{ total }} 笔结算单</span>
              </div>
            </div>

            <w-table
              :data="records"
              v-loading="listLoading"
              border
              stripe
              row-key="id"
              empty-text="暂无医保结算单"
              size="default"
            >
              <w-table-column label="结算号" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.settleNo || row.settle_no || '-' }}</template>
              </w-table-column>
              <w-table-column label="患者ID" width="90" align="center">
                <template #default="{ row }">{{ row.patientId ?? row.patient_id ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="医保号" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.insuranceNo || row.insurance_no || '-' }}</template>
              </w-table-column>
              <w-table-column label="业务类型" width="104" align="center">
                <template #default="{ row }">{{ bizTypeText(row.bizType || row.biz_type) }}</template>
              </w-table-column>
              <w-table-column label="总额" width="104" align="right">
                <template #default="{ row }">
                  <span class="money">{{ fmtMoney(row.totalAmount ?? row.total_amount) }}</span>
                </template>
              </w-table-column>
              <w-table-column label="统筹" width="104" align="right">
                <template #default="{ row }">{{ fmtMoney(row.insurancePay ?? row.insurance_pay) }}</template>
              </w-table-column>
              <w-table-column label="个账" width="104" align="right">
                <template #default="{ row }">{{ fmtMoney(row.personalAccountPay ?? row.personal_account_pay) }}</template>
              </w-table-column>
              <w-table-column label="现金" width="104" align="right">
                <template #default="{ row }">{{ fmtMoney(row.cashAmount ?? row.cash_amount) }}</template>
              </w-table-column>
              <w-table-column label="状态" width="92" align="center">
                <template #default="{ row }">
                  <w-tag :type="statusMeta(row.status).tag" effect="light" size="small">
                    {{ statusMeta(row.status).label }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column label="结算时间" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.settleTime || row.settle_time || row.createTime || row.create_time || '-' }}
                </template>
              </w-table-column>
              <w-table-column label="操作" width="216" align="center" fixed="right">
                <template #default="{ row }">
                  <w-button size="small" text type="primary" @click="openDetail(row)">详情</w-button>
                  <w-button size="small" text type="primary" :icon="Pdf" @click="openVoucher(row)">票据</w-button>
                  <w-button
                    size="small"
                    text
                    type="danger"
                    :disabled="(row.status || '').toUpperCase() === 'REVERSED'"
                    @click="handleReverse(row)"
                  >
                    冲正
                  </w-button>
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
                @current-change="fetchRecords"
                @size-change="handleSizeChange"
              />
            </div>
          </w-card>
        </div>
      </w-tab-pane>
    </w-tabs>

    <!-- 详情抽屉（复用结果卡片数据结构） -->
    <w-drawer v-model="detailVisible" title="医保结算单详情" size="660px">
      <SettleResultCard :settle="detailData" :loading="detailLoading" />
    </w-drawer>
  </div>
</template>

<style scoped>
.tab-inner {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.card-head__title--sm {
  font-size: 13.5px;
}

.settle-form {
  flex-wrap: wrap;
}

.draft-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6px 0 10px;
}

.draft-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding: 12px 16px;
  border-radius: var(--hospital-radius-sm);
  background: var(--w3-color-primary-plain, #eaeefe);
}
.draft-footer__total {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  color: var(--hospital-text-second);
}
.draft-footer__amount {
  font-size: 20px;
  font-weight: 700;
  color: var(--w3-color-danger);
  font-variant-numeric: tabular-nums;
}

.query-form {
  flex-wrap: wrap;
}
.list-toolbar {
  margin-bottom: 14px;
}
.table-total {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
