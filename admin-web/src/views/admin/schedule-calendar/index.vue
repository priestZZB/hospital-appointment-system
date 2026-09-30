<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import { ArrowLeft, ArrowRight, Date as DateIcon, Hospital, Plus, Refresh, Stamp } from '@win-design-next/icons-vue'
import {
  generateScheduleApi,
  getDepartmentsApi,
  getScheduleCalendarApi,
} from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO, ScheduleCalendarVO } from '@/types'

const userStore = useUserStore()

const departments = ref<DepartmentVO[]>([])
const deptId = ref<number>()
const loading = ref(false)
const generating = ref(false)

/** 日历扁平数据（A8） */
const calendar = ref<ScheduleCalendarVO[]>([])

/* ================= 周区间计算 ================= */

function fmt(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/** 取某日所在周的周一 */
function mondayOf(d: Date): string {
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  const m = new Date(d)
  m.setDate(d.getDate() + diff)
  return fmt(m)
}

function addDays(dateStr: string, days: number): string {
  const d = new Date(`${dateStr}T00:00:00`)
  d.setDate(d.getDate() + days)
  return fmt(d)
}

const todayStr = fmt(new Date())
const weekStart = ref(mondayOf(new Date()))
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart.value, i)))
const weekEnd = computed(() => weekDays.value[6])
const WEEK_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function dayLabel(d: string, idx: number): string {
  const mmdd = d.slice(5)
  return d === todayStr ? `今天 ${mmdd}` : `${WEEK_LABELS[idx]} ${mmdd}`
}

function shiftWeek(days: number) {
  weekStart.value = addDays(weekStart.value, days)
  fetchCalendar()
}

function backToThisWeek() {
  weekStart.value = mondayOf(new Date())
  fetchCalendar()
}

/* ================= 数据组装：医生行 × 日期列 ================= */

interface CalendarRow {
  doctorId: number
  doctorName: string
  cells: Record<string, ScheduleCalendarVO[]>
}

const calendarRows = computed<CalendarRow[]>(() => {
  const map = new Map<number, CalendarRow>()
  for (const item of calendar.value) {
    const did = item.doctorId ?? 0
    const date = item.date || ''
    let row = map.get(did)
    if (!row) {
      row = { doctorId: did, doctorName: item.doctorName || `医生#${did}`, cells: {} }
      map.set(did, row)
    }
    if (!date) continue
    if (!row.cells[date]) row.cells[date] = []
    row.cells[date].push(item)
  }
  return Array.from(map.values()).sort((a, b) => a.doctorName.localeCompare(b.doctorName, 'zh'))
})

function cellOf(row: CalendarRow, date: string): ScheduleCalendarVO[] {
  return row.cells[date] || []
}

const weekStat = computed(() => ({
  schedules: calendar.value.length,
  available: calendar.value.reduce((sum, e) => sum + (e.slotAvailable ?? 0), 0),
  green: calendar.value.filter((e) => isGreen(e)).length,
}))

/* ================= 展示辅助（防御式） ================= */

function periodText(period?: string): string {
  if (period === 'PM') return '下午'
  if (period === 'AM') return '上午'
  return period || ''
}

function isGreen(entry: ScheduleCalendarVO): boolean {
  return (entry.channelType || '').toUpperCase() === 'GREEN'
}

function isExpert(entry: ScheduleCalendarVO): boolean {
  return (entry.feeType || '').toUpperCase() === 'EXPERT'
}

function cellKey(entry: ScheduleCalendarVO): string {
  return `${entry.scheduleId ?? 'x'}-${entry.period ?? ''}-${entry.periodStart ?? ''}`
}

/* ================= 数据加载 ================= */

async function loadDepartments() {
  try {
    departments.value = await getDepartmentsApi()
    if (!deptId.value && departments.value.length) {
      deptId.value = departments.value[0].id
    }
  } catch (e) {
    WMessage.error((e as Error).message || '科室列表加载失败')
  }
}

async function fetchCalendar() {
  if (!deptId.value) {
    calendar.value = []
    return
  }
  loading.value = true
  try {
    const res = await getScheduleCalendarApi({
      departmentId: deptId.value,
      startDate: weekStart.value,
      days: 7,
    })
    calendar.value = Array.isArray(res) ? res : []
  } catch (e) {
    calendar.value = []
    WMessage.error((e as Error).message || '排班日历加载失败')
  } finally {
    loading.value = false
  }
}

function onDeptChange() {
  fetchCalendar()
}

/** 手动生成排班（A8 POST /api/admin/schedule/generate?date=） */
async function handleGenerate() {
  if (!deptId.value) {
    WMessage.warning('请先选择科室')
    return
  }
  try {
    await WMessageBox.confirm(
      `将按排班规则生成 ${weekStart.value} ~ ${weekEnd.value} 的排班与号源，是否继续？`,
      '生成本周排班',
      { type: 'warning' },
    )
  } catch {
    return
  }
  generating.value = true
  try {
    await generateScheduleApi(weekStart.value)
    WMessage.success('本周排班已生成')
    await fetchCalendar()
  } catch (e) {
    WMessage.error((e as Error).message || '排班生成失败')
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  await loadDepartments()
  await fetchCalendar()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">排班日历</h2>
      <p class="page-subtitle">周视图 · 医生 × 日期排班总览 · 绿色通道 / 专家号标识 · 一键生成本周排班</p>
    </div>

    <!-- 控制条 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <w-select v-model="deptId" placeholder="选择科室" clearable style="width: 190px" @change="onDeptChange">
            <template #prefix>
              <Hospital class="toolbar-icon" />
            </template>
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
          <w-button :icon="ArrowLeft" @click="shiftWeek(-7)" />
          <w-button type="primary" plain :icon="DateIcon" @click="backToThisWeek">本周</w-button>
          <w-button @click="shiftWeek(7)">
            下周
            <template #icon><ArrowRight /></template>
          </w-button>
          <span class="week-range">{{ weekStart }} ~ {{ weekEnd }}</span>
        </div>
        <div class="table-toolbar__right">
          <w-button v-if="userStore.isAdmin" type="primary" :icon="Plus" :loading="generating" @click="handleGenerate">
            生成本周排班
          </w-button>
          <w-button :icon="Refresh" :loading="loading" @click="fetchCalendar">刷新</w-button>
        </div>
      </div>
      <!-- 图例 + 周统计 -->
      <div class="legend-row">
        <span class="legend"><w-tag type="success" effect="light" size="small">绿色通道</w-tag>含绿色号源</span>
        <span class="legend"><w-tag type="warning" effect="light" size="small">专家</w-tag>专家号（EXPERT）</span>
        <span class="legend"><w-tag type="info" effect="light" size="small">未确认</w-tag>排班待门诊部确认</span>
        <span class="legend__spacer" />
        <span class="legend-stat">本周排班 <b>{{ weekStat.schedules }}</b> 条</span>
        <span class="legend-stat">剩余号源 <b>{{ weekStat.available }}</b> 个</span>
        <span class="legend-stat is-green">绿色通道 <b>{{ weekStat.green }}</b> 条</span>
      </div>
    </w-card>

    <!-- 周视图表格：医生行 × 7 日期列 -->
    <w-card shadow="never" class="hospital-card">
      <template #header>
        <div class="card-head">
          <span class="card-head__title"><Stamp class="card-head__icon" />排班总览（{{ weekStart }} 起 7 天）</span>
          <span class="card-head__sub">{{ calendarRows.length }} 位医生</span>
        </div>
      </template>
      <w-table
        v-if="deptId"
        :data="calendarRows"
        :loading="loading"
        border
        size="small"
        row-key="doctorId"
        max-height="560"
        empty-text="本周暂无排班"
      >
        <w-table-column label="医生" width="130" fixed="left">
          <template #default="{ row }">
            <span class="doctor-name">{{ row.doctorName }}</span>
          </template>
        </w-table-column>
        <w-table-column v-for="(d, idx) in weekDays" :key="d" :label="dayLabel(d, idx)" min-width="150" align="center">
          <template #default="{ row }">
            <div v-if="cellOf(row, d).length" class="cell-list">
              <div v-for="e in cellOf(row, d)" :key="cellKey(e)" class="cal-cell" :class="{ 'is-today': d === todayStr }">
                <div class="cal-cell__time">
                  {{ periodText(e.period) }} {{ e.periodStart || '' }}<template v-if="e.periodEnd">–{{ e.periodEnd }}</template>
                </div>
                <div class="cal-cell__slots">
                  <span :class="{ 'is-empty': (e.slotAvailable ?? 0) === 0 }">{{ e.slotAvailable ?? 0 }}</span>
                  / {{ e.slotTotal ?? 0 }} 号
                </div>
                <div v-if="isGreen(e) || isExpert(e) || e.confirmed === false" class="cal-cell__tags">
                  <w-tag v-if="isExpert(e)" type="warning" effect="light" size="small">专家</w-tag>
                  <w-tag v-if="isGreen(e)" type="success" effect="light" size="small">绿色通道</w-tag>
                  <w-tag v-if="e.confirmed === false" type="info" effect="light" size="small">未确认</w-tag>
                </div>
              </div>
            </div>
            <span v-else class="cell-empty">—</span>
          </template>
        </w-table-column>
      </w-table>
      <w-empty v-else type="data" description="请先选择科室查看排班日历" />
    </w-card>
  </div>
</template>

<style scoped>
.toolbar-icon {
  color: var(--w3-color-primary);
  font-size: 16px;
}
.week-range {
  font-size: 13px;
  font-weight: 600;
  color: var(--hospital-text-main);
  font-variant-numeric: tabular-nums;
}

/* 图例行 */
.legend-row {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--hospital-text-second);
}
.legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend__spacer {
  flex: 1;
}
.legend-stat {
  font-variant-numeric: tabular-nums;
}
.legend-stat b {
  color: var(--hospital-text-main);
}
.legend-stat.is-green b {
  color: var(--hospital-success, #2ba471);
}

/* 卡片头 */
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
}
.card-head__icon {
  color: var(--w3-color-primary);
  font-size: 17px;
}
.card-head__sub {
  font-size: 12.5px;
  color: var(--hospital-text-third);
}

/* 单元格 */
.doctor-name {
  font-weight: 600;
  color: var(--hospital-text-main);
}
.cell-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cal-cell {
  padding: 6px 8px;
  border: 1px solid var(--hospital-border-lighter);
  border-left: 3px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-sm, 6px);
  background: var(--hospital-bg-card, #fff);
  text-align: left;
}
.cal-cell.is-today {
  border-left-color: var(--w3-color-primary);
  background: var(--w3-color-primary-plain, #eaeefe);
}
.cal-cell__time {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--hospital-text-main);
  white-space: nowrap;
}
.cal-cell__slots {
  font-size: 12px;
  color: var(--hospital-text-second);
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
}
.cal-cell__slots span {
  font-weight: 700;
  color: var(--hospital-primary, #2d5afa);
}
.cal-cell__slots span.is-empty {
  color: var(--hospital-text-third);
}
.cal-cell__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.cell-empty {
  color: var(--hospital-text-third);
}
</style>
