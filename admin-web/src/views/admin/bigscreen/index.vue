<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import type { Component } from 'vue'
import * as echarts from 'echarts'
import { WMessage } from 'win-design-next'
import { CircleCheck, Hospital, LineChart, Rank, Refresh } from '@win-design-next/icons-vue'
import { getBiOverviewApi } from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { BiOverviewVO } from '@/types'

interface KpiStat {
  label: string
  value: string
  prefix: string
  suffix: string
  icon: Component
  tone: 'primary' | 'success' | 'warning'
  desc: string
}

interface DeptRankItem {
  name: string
  value: number
  percent: number
}

const userStore = useUserStore()
const loading = ref(false)
const overview = ref<BiOverviewVO | null>(null)
const now = ref('')
const trendRef = ref<HTMLDivElement>()
const trendChart = shallowRef<echarts.ECharts>()

const roleText = computed(() => {
  if (userStore.isAdmin) return '管理员'
  if (userStore.isDoctor) return '医生'
  return '患者'
})

function formatNumber(value: number): string {
  return value.toLocaleString('zh-CN')
}

const stats = computed<KpiStat[]>(() => [
  {
    label: '今日挂号量',
    value: formatNumber(overview.value?.todayAppointments ?? 0),
    prefix: '',
    suffix: '人次',
    icon: Hospital,
    tone: 'primary',
    desc: '今日新增预约挂号',
  },
  {
    label: '今日就诊量',
    value: formatNumber(overview.value?.todayConsultations ?? 0),
    prefix: '',
    suffix: '人次',
    icon: CircleCheck,
    tone: 'success',
    desc: '今日完成接诊',
  },
  {
    label: '今日收入',
    value: formatNumber(overview.value?.todayRevenue ?? 0),
    prefix: '¥',
    suffix: '',
    icon: Rank,
    tone: 'warning',
    desc: '今日挂号收入合计',
  },
])

const trendDates = computed(() => {
  const data = overview.value
  const list = data && Array.isArray(data.weeklyTrend) ? data.weeklyTrend : []
  return list.map((item) => (Object.keys(item)[0] || '').slice(5))
})

const trendCounts = computed(() => {
  const data = overview.value
  const list = data && Array.isArray(data.weeklyTrend) ? data.weeklyTrend : []
  return list.map((item) => Number(Object.values(item)[0] || 0))
})

const deptRank = computed<DeptRankItem[]>(() => {
  const data = overview.value
  const dist = data && Array.isArray(data.deptDistribution) ? data.deptDistribution : []
  const items = dist
    .map((item) => ({ name: Object.keys(item)[0] || '未命名科室', value: Number(Object.values(item)[0] || 0) }))
    .sort((a, b) => b.value - a.value)
  const max = items.reduce((m, item) => Math.max(m, item.value), 0) || 1
  return items.map((item) => ({ ...item, percent: Math.round((item.value / max) * 100) }))
})

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function hexToRgba(hex: string, alpha: number): string {
  const raw = hex.replace('#', '')
  const full = raw.length === 3 ? raw.split('').map((c) => c + c).join('') : raw
  const num = Number.parseInt(full, 16)
  const r = (num >> 16) & 0xff
  const g = (num >> 8) & 0xff
  const b = num & 0xff
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function renderTrend() {
  if (!trendRef.value) return
  const primary = cssVar('--w3-color-primary', '#2d5afa')
  const axisColor = 'rgba(255, 255, 255, 0.45)'
  const splitColor = 'rgba(255, 255, 255, 0.08)'
  trendChart.value?.dispose()
  trendChart.value = echarts.init(trendRef.value)
  trendChart.value.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(16, 24, 40, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff' },
    },
    grid: { top: 24, right: 20, bottom: 28, left: 48 },
    xAxis: {
      type: 'category',
      data: trendDates.value,
      boundaryGap: false,
      axisLine: { lineStyle: { color: axisColor } },
      axisTick: { show: false },
      axisLabel: { color: axisColor },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: splitColor } },
      axisLabel: { color: axisColor },
    },
    series: [
      {
        name: '挂号量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: trendCounts.value,
        lineStyle: { color: primary, width: 3 },
        itemStyle: { color: primary, borderColor: 'rgba(10, 20, 40, 1)', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: hexToRgba(primary, 0.4) },
            { offset: 1, color: hexToRgba(primary, 0) },
          ]),
        },
      },
    ],
  })
}

async function fetchData() {
  loading.value = true
  try {
    const data = await getBiOverviewApi()
    overview.value = data
    renderTrend()
  } catch (e) {
    WMessage.error((e as Error).message || '数据加载失败')
  } finally {
    loading.value = false
  }
}

function tick() {
  const d = new Date()
  now.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

function resizeChart() {
  trendChart.value?.resize()
}

let timer: number | undefined
let clock: number | undefined

onMounted(() => {
  fetchData()
  tick()
  clock = window.setInterval(tick, 1000)
  timer = window.setInterval(fetchData, 30000)
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  if (clock) window.clearInterval(clock)
  window.removeEventListener('resize', resizeChart)
  trendChart.value?.dispose()
})
</script>

<template>
  <div class="bigscreen-page bigscreen">
    <!-- 顶栏 -->
    <header class="bs-header">
      <div class="bs-header__title">
        <span class="bs-header__name">BI 统计大屏</span>
        <span class="bs-header__en">HOSPITAL OPERATIONS BI CENTER</span>
      </div>
      <div class="bs-header__right">
        <w-tag type="primary" size="small" effect="plain" round>30s 自动刷新</w-tag>
        <w-tag type="success" size="small" effect="plain" round>数据实时</w-tag>
        <span class="bs-header__operator">{{ roleText }} · {{ userStore.realName }}</span>
        <span class="bs-header__clock">{{ now }}</span>
        <w-button type="primary" text :icon="Refresh" :loading="loading" @click="fetchData">刷新</w-button>
      </div>
    </header>

    <!-- KPI 指标卡 -->
    <section class="bs-kpi">
      <div v-for="s in stats" :key="s.label" class="bs-kpi__card" :class="`bs-kpi__card--${s.tone}`">
        <span class="bs-kpi__icon"><component :is="s.icon" /></span>
        <div class="bs-kpi__body">
          <span class="bs-kpi__label">{{ s.label }}</span>
          <span class="bs-kpi__value">{{ s.prefix }}{{ s.value }}{{ s.suffix }}</span>
          <span class="bs-kpi__desc">{{ s.desc }}</span>
        </div>
      </div>
    </section>

    <!-- 折线趋势 + 科室排名 -->
    <section class="bs-body">
      <div class="bs-panel">
        <div class="bs-panel__head">
          <span class="bs-panel__title"><LineChart class="bs-panel__icon" />近 7 日就诊趋势</span>
          <w-tag type="primary" size="small" effect="plain">挂号量</w-tag>
        </div>
        <div ref="trendRef" class="bs-chart"></div>
      </div>

      <div class="bs-panel">
        <div class="bs-panel__head">
          <span class="bs-panel__title"><Rank class="bs-panel__icon" />科室挂号排名</span>
          <w-tag type="primary" size="small" effect="plain">TOP {{ deptRank.length }}</w-tag>
        </div>
        <ul v-if="deptRank.length" class="bs-rank">
          <li v-for="(item, index) in deptRank" :key="item.name" class="bs-rank__item">
            <span class="bs-rank__index" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
            <span class="bs-rank__name">{{ item.name }}</span>
            <div class="bs-rank__bar"><span :style="{ width: `${item.percent}%` }" /></div>
            <span class="bs-rank__value">{{ item.value }}</span>
          </li>
        </ul>
        <div v-else class="bs-empty">暂无科室数据</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.bigscreen {
  height: calc(100vh - 96px);
  min-height: 520px;
  border-radius: var(--hospital-radius-lg);
}

/* ---- 顶栏 ---- */
.bs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.bs-header__title {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.bs-header__name {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 3px;
  color: #fff;
  text-shadow: 0 0 24px color-mix(in srgb, var(--w3-color-primary) 55%, transparent);
}
.bs-header__en {
  font-size: 11px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.45);
  text-transform: uppercase;
}
.bs-header__right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bs-header__operator {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}
.bs-header__clock {
  font-size: 15px;
  color: #fff;
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
}

/* ---- KPI 指标卡 ---- */
.bs-kpi {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.bs-kpi__card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--hospital-radius-md);
  overflow: hidden;
}
.bs-kpi__card--primary {
  --accent: var(--w3-color-primary);
}
.bs-kpi__card--success {
  --accent: var(--w3-color-success);
}
.bs-kpi__card--warning {
  --accent: var(--w3-color-warning);
}
.bs-kpi__card::after {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  background: var(--accent);
}
.bs-kpi__icon {
  width: 44px;
  height: 44px;
  border-radius: var(--hospital-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: var(--accent);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}
.bs-kpi__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.bs-kpi__label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}
.bs-kpi__value {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--accent);
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 20px color-mix(in srgb, var(--accent) 45%, transparent);
}
.bs-kpi__desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

/* ---- 主体：图表 + 排名 ---- */
.bs-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}
.bs-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--hospital-radius-md);
}
.bs-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.bs-panel__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}
.bs-panel__icon {
  color: var(--w3-color-primary);
}
.bs-chart {
  flex: 1;
  min-height: 220px;
}

/* ---- 科室排名 ---- */
.bs-rank {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.bs-rank__item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bs-rank__index {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}
.bs-rank__index.is-top {
  color: #fff;
  background: linear-gradient(135deg, var(--w3-color-primary), var(--w3-color-primary-hover));
}
.bs-rank__name {
  width: 88px;
  flex-shrink: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.82);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bs-rank__bar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}
.bs-rank__bar > span {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--w3-color-primary), var(--w3-color-primary-hover));
  transition: width 0.4s ease;
}
.bs-rank__value {
  width: 48px;
  flex-shrink: 0;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  font-variant-numeric: tabular-nums;
}
.bs-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
}

/* ---- 响应式 ---- */
@media (max-width: 1200px) {
  .bs-kpi {
    grid-template-columns: 1fr;
  }
  .bs-body {
    grid-template-columns: 1fr;
  }
  .bigscreen {
    height: auto;
    overflow-y: auto;
  }
}
</style>
