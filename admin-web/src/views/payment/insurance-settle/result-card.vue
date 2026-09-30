<script setup lang="ts">
/**
 * 医保结算结果卡片（迭代11）。
 * 「结算模拟」提交结果与「结算单列表 → 详情抽屉」共用同一数据结构（InsuranceSettle）。
 * 字段做驼峰/下划线双命名防御式兼容；缺省显示 '-'。
 */
import { computed } from 'vue'
import type { InsuranceSettle } from '@/types'

const props = defineProps<{
  settle: InsuranceSettle | null
  loading?: boolean
}>()

/* ================= 双命名兼容读取 ================= */
function settleNoOf(s: InsuranceSettle): string {
  return s.settleNo || s.settle_no || '-'
}
function patientIdOf(s: InsuranceSettle): number | string {
  return s.patientId ?? s.patient_id ?? '-'
}
function insuranceNoOf(s: InsuranceSettle): string {
  return s.insuranceNo || s.insurance_no || '-'
}
function num(camel?: number, snake?: number): number {
  const v = camel ?? snake
  return typeof v === 'number' && !Number.isNaN(v) ? v : 0
}
function textOrDash(v?: string | number | null): string {
  if (v == null || v === '') return '-'
  return String(v)
}

const insurancePay = computed(() => (props.settle ? num(props.settle.insurancePay, props.settle.insurance_pay) : 0))
const personalAccountPay = computed(() =>
  props.settle ? num(props.settle.personalAccountPay, props.settle.personal_account_pay) : 0,
)
const cashAmount = computed(() => (props.settle ? num(props.settle.cashAmount, props.settle.cash_amount) : 0))
const totalAmount = computed(() => (props.settle ? num(props.settle.totalAmount, props.settle.total_amount) : 0))
const catalogAAmount = computed(() => (props.settle ? num(props.settle.catalogAAmount, props.settle.catalog_a_amount) : 0))
const catalogBAmount = computed(() => (props.settle ? num(props.settle.catalogBAmount, props.settle.catalog_b_amount) : 0))
const selfAmount = computed(() => (props.settle ? num(props.settle.selfAmount, props.settle.self_amount) : 0))

function fmtMoney(v?: number | null): string {
  if (v == null) return '-'
  return `¥${Number(v).toFixed(2)}`
}

/* ================= 状态 / 类别文案 ================= */
const SETTLE_STATUS_META: Record<string, { label: string; tag: 'success' | 'danger' | 'info' | 'warning' | 'primary' }> = {
  SETTLED: { label: '已结算', tag: 'success' },
  REVERSED: { label: '已冲正', tag: 'danger' },
}
function statusMeta(status?: string) {
  const key = (status || '').toUpperCase()
  return SETTLE_STATUS_META[key] || { label: status || '-', tag: 'info' as const }
}

const BIZ_TYPE_TEXT: Record<string, string> = {
  REGISTER: '挂号结算',
  OUTPATIENT: '门诊结算',
  INPATIENT: '住院结算',
}
function bizTypeText(v?: string): string {
  if (!v) return '-'
  return BIZ_TYPE_TEXT[v.toUpperCase()] || v
}

const ITEM_TYPE_TEXT: Record<string, string> = {
  REGISTER: '挂号',
  DRUG: '药品',
  EXAM: '检查',
  LAB: '检验',
  TREATMENT: '诊疗',
  MATERIAL: '卫材',
  CHARGED_ITEM: '收费项目',
}
function itemTypeText(v?: string): string {
  if (!v) return '-'
  return ITEM_TYPE_TEXT[v.toUpperCase()] || v
}

/** 甲乙类 tag：A-绿（甲类）/ B-橙（乙类）/ C-灰（自费） */
function catalogClassMeta(v?: string): { label: string; tag: 'success' | 'warning' | 'info' } {
  const key = (v || '').toUpperCase()
  if (key === 'A') return { label: '甲类', tag: 'success' }
  if (key === 'B') return { label: '乙类', tag: 'warning' }
  if (key === 'C') return { label: '自费', tag: 'info' }
  return { label: v || '-', tag: 'info' }
}

/* ================= 明细行归一化 ================= */
interface DetailRow {
  key: string
  itemName: string
  itemType: string
  catalogClass: string
  amount: number | null
  insurancePay: number | null
  personalPay: number | null
}

function firstNum(keys: (number | undefined)[]): number | null {
  for (const v of keys) {
    if (typeof v === 'number' && !Number.isNaN(v)) return v
  }
  return null
}

const detailRows = computed<DetailRow[]>(() => {
  const raw = props.settle?.detail
  if (!Array.isArray(raw)) return []
  return raw.map((item, idx) => ({
    key: `${idx}`,
    itemName: item.itemName || item.item_name || '-',
    itemType: item.itemType || item.item_type || '',
    catalogClass: item.catalogClass || item.catalog_class || '',
    amount: firstNum([item.amount]),
    insurancePay: firstNum([item.insurancePay, item.insurance_pay]),
    personalPay: firstNum([item.personalPay, item.personal_pay, item.personalAccountPay, item.personal_account_pay]),
  }))
})

const settleTimeText = computed(() => {
  const s = props.settle
  if (!s) return '-'
  return s.settleTime || s.settle_time || s.createTime || s.create_time || '-'
})
</script>

<template>
  <div v-loading="loading" class="settle-result">
    <template v-if="settle">
      <!-- 头部：结算号 + 状态 + 基本信息 -->
      <div class="result-head">
        <div class="result-no">
          <span class="result-no__label">结算单号</span>
          <span class="result-no__value">{{ settleNoOf(settle) }}</span>
          <w-tag :type="statusMeta(settle.status).tag" effect="light" size="small">
            {{ statusMeta(settle.status).label }}
          </w-tag>
        </div>
        <div class="result-meta">
          <span>患者ID：{{ textOrDash(patientIdOf(settle)) }}</span>
          <span>医保号：{{ insuranceNoOf(settle) }}</span>
          <span>业务类型：{{ bizTypeText(settle.bizType || settle.biz_type) }}</span>
          <span>结算时间：{{ settleTimeText }}</span>
        </div>
      </div>

      <!-- 三色金额块：统筹支付绿 / 个人账户橙 / 现金灰 -->
      <div class="amount-blocks">
        <div class="amount-block amount-block--success">
          <span class="amount-block__label">统筹支付</span>
          <span class="amount-block__value">{{ fmtMoney(insurancePay) }}</span>
        </div>
        <div class="amount-block amount-block--warning">
          <span class="amount-block__label">个人账户支付</span>
          <span class="amount-block__value">{{ fmtMoney(personalAccountPay) }}</span>
        </div>
        <div class="amount-block amount-block--info">
          <span class="amount-block__label">现金支付</span>
          <span class="amount-block__value">{{ fmtMoney(cashAmount) }}</span>
        </div>
      </div>

      <!-- 甲乙自费三类小计 -->
      <div class="subtotal-bar">
        <span class="subtotal-bar__item">费用总额：<b>{{ fmtMoney(totalAmount) }}</b></span>
        <span class="subtotal-bar__divider" />
        <span class="subtotal-bar__item">甲类小计：<b class="c-success">{{ fmtMoney(catalogAAmount) }}</b></span>
        <span class="subtotal-bar__item">乙类小计：<b class="c-warning">{{ fmtMoney(catalogBAmount) }}</b></span>
        <span class="subtotal-bar__item">自费小计：<b class="c-info">{{ fmtMoney(selfAmount) }}</b></span>
      </div>

      <!-- 逐项拆分明细 -->
      <w-table :data="detailRows" border stripe size="small" empty-text="无结算明细" row-key="key">
        <w-table-column type="index" label="#" width="52" align="center" />
        <w-table-column label="项目名称" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemName }}</template>
        </w-table-column>
        <w-table-column label="项目类型" width="100" align="center">
          <template #default="{ row }">{{ itemTypeText(row.itemType) }}</template>
        </w-table-column>
        <w-table-column label="类别" width="86" align="center">
          <template #default="{ row }">
            <w-tag :type="catalogClassMeta(row.catalogClass).tag" effect="light" size="small">
              {{ catalogClassMeta(row.catalogClass).label }}
            </w-tag>
          </template>
        </w-table-column>
        <w-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ fmtMoney(row.amount) }}</span>
          </template>
        </w-table-column>
        <w-table-column label="统筹支付" width="110" align="right">
          <template #default="{ row }">{{ fmtMoney(row.insurancePay) }}</template>
        </w-table-column>
        <w-table-column label="个人支付" width="110" align="right">
          <template #default="{ row }">{{ fmtMoney(row.personalPay) }}</template>
        </w-table-column>
      </w-table>
    </template>
    <w-empty v-else description="暂无结算数据" />
  </div>
</template>

<style scoped>
.settle-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 120px;
}

.result-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.result-no {
  display: flex;
  align-items: center;
  gap: 10px;
}
.result-no__label {
  font-size: 12.5px;
  color: var(--hospital-text-third);
}
.result-no__value {
  font-size: 17px;
  font-weight: 700;
  color: var(--hospital-text-main);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.4px;
}
.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  font-size: 12.5px;
  color: var(--hospital-text-second);
}

.amount-blocks {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.amount-block {
  border-radius: var(--hospital-radius-md);
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid transparent;
}
.amount-block--success {
  background: var(--w3-color-success-plain, #e8f6ee);
  border-color: rgba(0, 171, 68, 0.18);
}
.amount-block--success .amount-block__value {
  color: var(--w3-color-success, #00ab44);
}
.amount-block--warning {
  background: var(--w3-color-warning-plain, #fff0df);
  border-color: rgba(255, 140, 0, 0.18);
}
.amount-block--warning .amount-block__value {
  color: var(--w3-color-warning, #ff8c00);
}
.amount-block--info {
  background: #f2f4f7;
  border-color: rgba(71, 84, 103, 0.14);
}
.amount-block--info .amount-block__value {
  color: #475467;
}
.amount-block__label {
  font-size: 12.5px;
  color: var(--hospital-text-second);
}
.amount-block__value {
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.subtotal-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 14px;
  padding: 10px 14px;
  border-radius: var(--hospital-radius-sm);
  background: var(--w3-color-primary-plain, #eaeefe);
  font-size: 13px;
  color: var(--hospital-text-second);
}
.subtotal-bar__item b {
  font-variant-numeric: tabular-nums;
  color: var(--hospital-text-main);
}
.subtotal-bar__divider {
  width: 1px;
  height: 14px;
  background: var(--hospital-border);
}
.c-success {
  color: var(--w3-color-success, #00ab44) !important;
}
.c-warning {
  color: var(--w3-color-warning, #ff8c00) !important;
}
.c-info {
  color: #475467 !important;
}

.money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
</style>
