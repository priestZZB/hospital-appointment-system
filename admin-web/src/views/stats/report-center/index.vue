<script setup lang="ts">
/**
 * 统计报表中心（迭代12 I1，/report-center）。
 * Tab1 门诊报表：日报（挂号/接诊/处方/收入/科室TOP5）+ 月报（指标+逐日序列）+ CSV 导出。
 * Tab2 住院报表：日报（入院/出院/在院/手术/押金/收入）+ 月报 + CSV 导出。
 */
import { onMounted, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Download } from '@win-design-next/icons-vue'
import {
  downloadBlob,
  exportClinicDailyBlob,
  exportClinicMonthlyBlob,
  exportInpatientDailyBlob,
  exportInpatientMonthlyBlob,
  getClinicDailyApi,
  getClinicMonthlyApi,
  getInpatientDailyApi,
  getInpatientMonthlyApi,
} from '@/api/stats'
import type { StatsDailyRow, StatsMonthlyRow } from '@/api/stats'

const activeTab = ref<'clinic' | 'inpatient'>('clinic')
const dailyDate = ref(new Date().toISOString().slice(0, 10))
const monthlyMonth = ref(new Date().toISOString().slice(0, 7))

const dailyLoading = ref(false)
const daily = ref<StatsDailyRow | null>(null)
const monthlyLoading = ref(false)
const monthly = ref<StatsMonthlyRow | null>(null)
const exporting = ref(false)

function n(v?: number | null): string {
  if (v == null) return '-'
  return String(v)
}
function money(v?: number | null): string {
  if (v == null) return '-'
  return `¥${Number(v).toFixed(2)}`
}

async function fetchDaily() {
  dailyLoading.value = true
  try {
    daily.value =
      activeTab.value === 'clinic' ? await getClinicDailyApi(dailyDate.value) : await getInpatientDailyApi(dailyDate.value)
  } catch (e) {
    WMessage.error((e as Error).message || '日报加载失败')
  } finally {
    dailyLoading.value = false
  }
}

async function fetchMonthly() {
  monthlyLoading.value = true
  try {
    monthly.value =
      activeTab.value === 'clinic'
        ? await getClinicMonthlyApi(monthlyMonth.value)
        : await getInpatientMonthlyApi(monthlyMonth.value)
  } catch (e) {
    WMessage.error((e as Error).message || '月报加载失败')
  } finally {
    monthlyLoading.value = false
  }
}

function switchTab() {
  daily.value = null
  monthly.value = null
  fetchDaily()
  fetchMonthly()
}

async function exportCsv(kind: 'daily' | 'monthly') {
  exporting.value = true
  try {
    const blob =
      activeTab.value === 'clinic'
        ? kind === 'daily'
          ? await exportClinicDailyBlob(dailyDate.value)
          : await exportClinicMonthlyBlob(monthlyMonth.value)
        : kind === 'daily'
          ? await exportInpatientDailyBlob(dailyDate.value)
          : await exportInpatientMonthlyBlob(monthlyMonth.value)
    downloadBlob(blob, `${activeTab.value}-${kind}-${kind === 'daily' ? dailyDate.value : monthlyMonth.value}.csv`)
    WMessage.success('CSV 已导出')
  } catch (e) {
    WMessage.error((e as Error).message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function seriesIncome(row: { income?: number }): string {
  return money(row?.income)
}

onMounted(() => {
  fetchDaily()
  fetchMonthly()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">统计报表中心</h2>
      <p class="page-subtitle">门诊/住院日报与月报 · 指标速览 · CSV 导出（I1）</p>
    </div>

    <w-tabs v-model="activeTab" class="hospital-card" @tab-change="switchTab">
      <w-tab-pane label="门诊报表" name="clinic" />
      <w-tab-pane label="住院报表" name="inpatient" />
    </w-tabs>

    <!-- 日报 -->
    <w-card shadow="never" class="hospital-card">
      <template #header>
        <div class="card-head-row">
          <span>日报速览</span>
          <div class="head-actions">
            <w-date-picker v-model="dailyDate" value-format="YYYY-MM-DD" style="width: 150px" @change="fetchDaily" />
            <w-button type="primary" :icon="Download" :loading="exporting" @click="exportCsv('daily')">导出 CSV</w-button>
          </div>
        </div>
      </template>
      <div v-loading="dailyLoading" class="metric-grid">
        <template v-if="activeTab === 'clinic'">
          <div class="metric-box"><div class="metric-label">挂号数</div><div class="metric-value">{{ n(daily?.registerCount) }}</div></div>
          <div class="metric-box"><div class="metric-label">接诊数（实际报到）</div><div class="metric-value">{{ n(daily?.consultCount) }}</div></div>
          <div class="metric-box"><div class="metric-label">处方数</div><div class="metric-value">{{ n(daily?.prescriptionCount) }}</div></div>
          <div class="metric-box"><div class="metric-label">门诊总收入</div><div class="metric-value metric-money">{{ money(daily?.totalIncome) }}</div></div>
        </template>
        <template v-else>
          <div class="metric-box"><div class="metric-label">入院人数</div><div class="metric-value">{{ n(daily?.admitCount) }}</div></div>
          <div class="metric-box"><div class="metric-label">出院人数</div><div class="metric-value">{{ n(daily?.dischargeCount) }}</div></div>
          <div class="metric-box"><div class="metric-label">当前在院</div><div class="metric-value">{{ n(daily?.inWardCount) }}</div></div>
          <div class="metric-box"><div class="metric-label">当日手术</div><div class="metric-value">{{ n(daily?.surgeryCount) }}</div></div>
          <div class="metric-box"><div class="metric-label">当日押金</div><div class="metric-value metric-money">{{ money(daily?.depositTotal) }}</div></div>
          <div class="metric-box"><div class="metric-label">当日费用收入</div><div class="metric-value metric-money">{{ money(daily?.incomeTotal) }}</div></div>
        </template>
      </div>
      <!-- 科室 TOP5（仅门诊） -->
      <template v-if="activeTab === 'clinic'">
        <h4 class="sub-title">科室挂号 TOP5</h4>
        <div v-if="daily?.topDepartments?.length" class="top-list">
          <div v-for="(d, i) in daily.topDepartments" :key="i" class="top-row">
            <span class="top-name">{{ d.deptName || '-' }}</span>
            <div class="top-bar-wrap">
              <div
                class="top-bar"
                :style="{ width: Math.max(8, (Number(d.count) / Math.max(1, Number(daily.topDepartments?.[0]?.count || 1))) * 100) + '%' }"
              />
            </div>
            <span class="top-count">{{ n(d.count) }}</span>
          </div>
        </div>
        <w-empty v-else description="当日暂无挂号数据" />
      </template>
    </w-card>

    <!-- 月报 -->
    <w-card shadow="never" class="hospital-card">
      <template #header>
        <div class="card-head-row">
          <span>月报趋势</span>
          <div class="head-actions">
            <w-date-picker v-model="monthlyMonth" type="month" value-format="YYYY-MM" style="width: 150px" @change="fetchMonthly" />
            <w-button type="primary" :icon="Download" :loading="exporting" @click="exportCsv('monthly')">导出 CSV</w-button>
          </div>
        </div>
      </template>
      <div v-loading="monthlyLoading">
        <div class="metric-grid">
          <template v-if="activeTab === 'clinic'">
            <div class="metric-box"><div class="metric-label">本月挂号</div><div class="metric-value">{{ n(monthly?.registerCount) }}</div></div>
            <div class="metric-box"><div class="metric-label">本月收入</div><div class="metric-value metric-money">{{ money(monthly?.totalIncome) }}</div></div>
          </template>
          <template v-else>
            <div class="metric-box"><div class="metric-label">本月入院</div><div class="metric-value">{{ n(monthly?.admitCount) }}</div></div>
            <div class="metric-box"><div class="metric-label">当前在院</div><div class="metric-value">{{ n(monthly?.inWardCount) }}</div></div>
            <div class="metric-box"><div class="metric-label">本月费用收入</div><div class="metric-value metric-money">{{ money(monthly?.incomeTotal) }}</div></div>
          </template>
        </div>
        <w-table
          :data="monthly?.dailySeries || []"
          row-key="day"
          border
          stripe
          max-height="420"
          empty-text="本月暂无数据"
          size="small"
          class="series-table"
        >
          <w-table-column label="日期" width="140">
            <template #default="{ row }">{{ row.day || '-' }}</template>
          </w-table-column>
          <w-table-column :label="activeTab === 'clinic' ? '挂号数' : '入院人数'" width="140" align="center">
            <template #default="{ row }">{{ n(row.count) }}</template>
          </w-table-column>
          <w-table-column label="收入" min-width="160" align="right">
            <template #default="{ row }">{{ seriesIncome(row) }}</template>
          </w-table-column>
        </w-table>
      </div>
    </w-card>
  </div>
</template>

<style scoped>
.card-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 8px;
}
.metric-box {
  background: var(--w-color-fill-lighter, #f7f9fc);
  border-radius: 8px;
  padding: 14px 16px;
}
.metric-label {
  font-size: 13px;
  color: #7a8699;
}
.metric-value {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
}
.metric-money {
  color: #d4751f;
}
.sub-title {
  margin: 18px 0 10px;
}
.top-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.top-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.top-name {
  width: 140px;
  font-size: 13px;
}
.top-bar-wrap {
  flex: 1;
  background: #eef1f6;
  border-radius: 5px;
  height: 16px;
  overflow: hidden;
}
.top-bar {
  height: 100%;
  background: linear-gradient(90deg, #3a7afe, #6fb1ff);
  border-radius: 5px;
}
.top-count {
  width: 60px;
  text-align: right;
  font-weight: 600;
}
.series-table {
  margin-top: 12px;
}
</style>
