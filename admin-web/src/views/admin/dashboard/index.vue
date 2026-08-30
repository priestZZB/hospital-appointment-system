<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import * as echarts from 'echarts'
import { WMessage } from 'win-design-next'
import { BarChart, CircleCheck, Hospital, LineChart, Rank, Refresh } from '@win-design-next/icons-vue'
import { getBiOverviewApi } from '@/api/clinic'
import type { BiOverviewVO } from '@/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const overview = ref<BiOverviewVO | null>(null)
const trendRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
const trendChart = shallowRef<echarts.ECharts>()
const pieChart = shallowRef<echarts.ECharts>()

const greeting = computed(() => `你好，${userStore.realName}`)
const roleLabel = computed(() => {
  if (userStore.isAdmin) return '管理员'
  if (userStore.isDoctor) return '医生'
  if (userStore.isPatient) return '患者'
  return ''
})

/** 统计卡元数据（图标为组件引用，保持非响应式） */
const STAT_META = [
  {
    key: 'appointments',
    label: '今日挂号量',
    icon: Hospital,
    tone: 'primary',
    prefix: '',
    suffix: '人次',
    desc: '今日新增预约挂号',
  },
  {
    key: 'consultations',
    label: '今日就诊量',
    icon: CircleCheck,
    tone: 'success',
    prefix: '',
    suffix: '人次',
    desc: '今日完成接诊',
  },
  {
    key: 'revenue',
    label: '今日收入',
    icon: Rank,
    tone: 'warning',
    prefix: '¥',
    suffix: '',
    desc: '今日挂号收入合计',
  },
] as const

type StatKey = (typeof STAT_META)[number]['key']

const statValues = computed(() => {
  const v = overview.value
  const map: Record<StatKey, number> = {
    appointments: v?.todayAppointments ?? 0,
    consultations: v?.todayConsultations ?? 0,
    revenue: v?.todayRevenue ?? 0,
  }
  return map
})

function formatNumber(n: number): string {
  return n.toLocaleString('zh-CN')
}

/** 设计 token 兜底值（与 styles/index.css 定义一致，仅作防御） */
const TOKEN_FALLBACK: Record<string, string> = {
  '--hospital-primary': '#2d5afa',
  '--hospital-primary-hover': '#5175f4',
  '--hospital-success': '#00ab44',
  '--hospital-warning': '#ff8c00',
  '--hospital-danger': '#ec0000',
  '--hospital-info': '#999999',
  '--hospital-text-main': '#101828',
  '--hospital-text-second': '#667085',
  '--hospital-text-third': '#98a2b3',
  '--hospital-border': '#e9e9e9',
  '--hospital-border-lighter': '#f0f0f0',
  '--hospital-bg-card': '#ffffff',
  '--hospital-bg-sidebar': '#101b33',
  '--w3-color-success-hover': '#08c955',
  '--w3-color-warning-hover': '#ffac48',
}

function readToken(name: string): string {
  if (typeof window === 'undefined') return TOKEN_FALLBACK[name] ?? ''
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || TOKEN_FALLBACK[name] || ''
}

function hexToRgba(hex: string, alpha: number): string {
  const raw = hex.replace('#', '').trim()
  if (!/^[0-9a-fA-F]{3}$|^[0-9a-fA-F]{6}$/.test(raw)) return `rgba(45, 90, 250, ${alpha})`
  const full = raw.length === 3 ? raw.split('').map((c) => c + c).join('') : raw
  const r = parseInt(full.slice(0, 2), 16)
  const g = parseInt(full.slice(2, 4), 16)
  const b = parseInt(full.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

const trendDates = computed(() => {
  const data = Array.isArray(overview.value?.weeklyTrend) ? overview.value!.weeklyTrend : []
  return data.map((item) => Object.keys(item)[0] || '')
})
const trendCounts = computed(() => {
  const data = Array.isArray(overview.value?.weeklyTrend) ? overview.value!.weeklyTrend : []
  return data.map((item) => Number(Object.values(item)[0] || 0))
})
const pieData = computed(() => {
  const data = Array.isArray(overview.value?.deptDistribution) ? overview.value!.deptDistribution : []
  return data.map((item) => ({ name: Object.keys(item)[0] || '', value: Number(Object.values(item)[0] || 0) }))
})

function renderCharts() {
  const primary = readToken('--hospital-primary')
  const success = readToken('--hospital-success')
  const warning = readToken('--hospital-warning')
  const danger = readToken('--hospital-danger')
  const info = readToken('--hospital-info')
  const textMain = readToken('--hospital-text-main')
  const textSecond = readToken('--hospital-text-second')
  const textThird = readToken('--hospital-text-third')
  const border = readToken('--hospital-border')
  const borderLighter = readToken('--hospital-border-lighter')
  const bgCard = readToken('--hospital-bg-card')
  const bgSidebar = readToken('--hospital-bg-sidebar')

  if (trendRef.value) {
    trendChart.value?.dispose()
    trendChart.value = echarts.init(trendRef.value)
    trendChart.value.setOption({
      tooltip: { trigger: 'axis', backgroundColor: bgSidebar, borderWidth: 0, textStyle: { color: bgCard } },
      grid: { top: 30, right: 24, bottom: 28, left: 44 },
      xAxis: {
        type: 'category',
        data: trendDates.value,
        boundaryGap: false,
        axisLine: { lineStyle: { color: border } },
        axisLabel: { color: textSecond },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: borderLighter } },
        axisLabel: { color: textThird },
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
          itemStyle: { color: primary, borderColor: bgCard, borderWidth: 2 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: hexToRgba(primary, 0.28) },
              { offset: 1, color: hexToRgba(primary, 0.02) },
            ]),
          },
        },
      ],
    })
  }

  if (pieRef.value) {
    pieChart.value?.dispose()
    pieChart.value = echarts.init(pieRef.value)
    pieChart.value.setOption({
      tooltip: { trigger: 'item', backgroundColor: bgSidebar, borderWidth: 0, textStyle: { color: bgCard } },
      legend: { bottom: 0, textStyle: { color: textSecond }, icon: 'circle', itemWidth: 8, itemHeight: 8 },
      color: [
        primary,
        success,
        warning,
        danger,
        readToken('--hospital-primary-hover'),
        readToken('--w3-color-success-hover'),
        readToken('--w3-color-warning-hover'),
        info,
      ],
      series: [
        {
          name: '科室占比',
          type: 'pie',
          radius: ['46%', '70%'],
          center: ['50%', '44%'],
          data: pieData.value,
          label: { formatter: '{b}\n{d}%', color: textMain, fontSize: 12, lineHeight: 18 },
          labelLine: { length: 12, length2: 8 },
          itemStyle: { borderRadius: 6, borderColor: bgCard, borderWidth: 2 },
        },
      ],
    })
  }
}

async function fetchOverview() {
  loading.value = true
  try {
    const data = await getBiOverviewApi()
    overview.value = data
    renderCharts()
  } catch (e) {
    WMessage.error((e as Error).message || '概览数据加载失败')
  } finally {
    loading.value = false
  }
}

function resizeCharts() {
  trendChart.value?.resize()
  pieChart.value?.resize()
}

onMounted(() => {
  fetchOverview()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart.value?.dispose()
  pieChart.value?.dispose()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头：标题 + 角色问候 + 刷新 -->
    <div class="page-head page-head--row">
      <div>
        <h2 class="page-title">首页概览</h2>
        <p class="page-subtitle">医院门诊运营关键指标与趋势分析</p>
      </div>
      <div class="page-head__aside">
        <span class="greeting">{{ greeting }}</span>
        <w-tag v-if="roleLabel" size="small" effect="light" type="primary">{{ roleLabel }}</w-tag>
        <w-button type="primary" :icon="Refresh" :loading="loading" @click="fetchOverview">刷新数据</w-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div v-for="item in STAT_META" :key="item.key" class="stat-card">
        <span class="stat-card__icon" :class="`stat-card__icon--${item.tone}`">
          <component :is="item.icon" />
        </span>
        <div class="stat-card__body">
          <span class="stat-card__label">{{ item.label }}</span>
          <span class="stat-card__value">
            <span v-if="item.prefix" class="stat-card__prefix">{{ item.prefix }}</span>{{ formatNumber(statValues[item.key]) }}<span v-if="item.suffix" class="stat-card__unit">{{ item.suffix }}</span>
          </span>
          <span class="stat-card__extra">{{ item.desc }}</span>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-grid">
      <w-card shadow="never" class="hospital-card">
        <template #header>
          <div class="chart-card__head">
            <span class="chart-card__title"><LineChart class="chart-card__head-icon" />近 7 日就诊趋势</span>
            <w-tag size="small" effect="light" type="primary">动态更新</w-tag>
          </div>
        </template>
        <div v-loading="loading" ref="trendRef" class="chart"></div>
      </w-card>

      <w-card shadow="never" class="hospital-card">
        <template #header>
          <div class="chart-card__head">
            <span class="chart-card__title"><BarChart class="chart-card__head-icon" />科室挂号占比</span>
          </div>
        </template>
        <div v-loading="loading" ref="pieRef" class="chart"></div>
      </w-card>
    </div>
  </div>
</template>

<style scoped>
.page-head--row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-head__aside {
  display: flex;
  align-items: center;
  gap: 12px;
}

.greeting {
  font-size: 13px;
  color: var(--hospital-text-second);
}

/* 统计卡栅格 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}

.stat-card__prefix {
  color: var(--hospital-text-second);
  font-weight: 600;
  margin-right: 2px;
}
.stat-card__unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--hospital-text-second);
  margin-left: 6px;
}

/* 图表区 */
.chart-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 16px;
}
@media (max-width: 1200px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
.chart-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chart-card__title {
  font-size: 15px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.chart-card__head-icon {
  color: var(--hospital-primary);
}
.chart {
  height: 340px;
}
</style>
