<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { WMessage } from 'win-design-next'
import {
  CircleCheck,
  Date,
  Hospital,
  List,
  Refresh,
  Right,
  Send,
  Stamp,
  Stop,
  UserGroup,
} from '@win-design-next/icons-vue'
import { getTodayConsultationsApi } from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { QueuePatientVO } from '@/types'

type StatTone = 'primary' | 'success' | 'warning' | 'danger' | 'info'
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface StatItem {
  label: string
  value: number
  suffix: string
  icon: Component
  tone: StatTone
  desc: string
}

interface QuickEntry {
  title: string
  desc: string
  path: string
  icon: Component
}

interface StatusMeta {
  label: string
  type: TagType
}

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const activeTab = ref('overview')
const todayList = ref<QueuePatientVO[]>([])

const waitingCount = computed(() => todayList.value.filter((x) => x.queueStatus === 'WAITING').length)
const completedCount = computed(() => todayList.value.filter((x) => x.visitStatus === 'COMPLETED').length)
const sourceCount = computed(() => todayList.value.length)
const calledCount = computed(() => todayList.value.filter((x) => x.queueStatus === 'CALLED').length)

const greeting = computed(() => (userStore.isDoctor ? `${userStore.realName} 医生` : userStore.realName))

const stats = computed<StatItem[]>(() => [
  { label: '待接诊', value: waitingCount.value, suffix: '人', icon: UserGroup, tone: 'primary', desc: '等待叫号的患者' },
  { label: '已完成', value: completedCount.value, suffix: '人', icon: CircleCheck, tone: 'success', desc: '今日完成接诊' },
  { label: '号源', value: sourceCount.value, suffix: '个', icon: List, tone: 'info', desc: '今日预约号源' },
  { label: '叫号', value: calledCount.value, suffix: '人', icon: Send, tone: 'warning', desc: '当前已叫号' },
])

const quickEntries: QuickEntry[] = [
  { title: '门诊诊疗', desc: '接诊 → 病历 → 处方 → 检查申请', path: '/clinic', icon: Hospital },
  { title: '签到叫号', desc: '排队快照与叫号大屏', path: '/call-board', icon: Stamp },
  { title: '我的排班', desc: '查看排班与号源', path: '/schedules', icon: Date },
  { title: '停诊申请', desc: '提交停诊与查看记录', path: '/stop', icon: Stop },
]

function statusMeta(row: QueuePatientVO): StatusMeta {
  if (row.visitStatus === 'COMPLETED') return { label: '已完成', type: 'success' }
  if (row.queueStatus === 'MISSED') return { label: '已过号', type: 'danger' }
  if (row.queueStatus === 'CALLED') return { label: '已叫号', type: 'warning' }
  if (row.queueStatus === 'WAITING') return { label: '待叫号', type: 'primary' }
  return { label: row.queueStatus || row.visitStatus || '待接诊', type: 'info' }
}

async function fetchToday() {
  loading.value = true
  try {
    todayList.value = await getTodayConsultationsApi({})
  } catch (e) {
    WMessage.error((e as Error).message || '今日接诊加载失败')
  } finally {
    loading.value = false
  }
}

function go(path: string) {
  router.push(path)
}

onMounted(fetchToday)
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__main">
        <div class="page-head__title-row">
          <h2 class="page-title">医生工作台</h2>
          <w-tag v-if="userStore.isDoctor" type="primary" effect="light" size="small">医生端</w-tag>
        </div>
        <p class="page-subtitle">您好，{{ greeting }} · 今日待接诊队列与快捷入口</p>
      </div>
      <w-button type="primary" plain :loading="loading" @click="fetchToday">
        <template #icon><Refresh /></template>
        刷新
      </w-button>
    </div>

    <!-- 顶部 tab 导航 -->
    <w-tabs v-model="activeTab" class="workbench-tabs">
      <!-- 概览：统计卡 + 快捷入口 -->
      <w-tab-pane label="工作台概览" name="overview">
        <div class="stat-grid">
          <div v-for="s in stats" :key="s.label" class="stat-card">
            <span class="stat-card__icon" :class="`stat-card__icon--${s.tone}`">
              <component :is="s.icon" />
            </span>
            <div class="stat-card__body">
              <span class="stat-card__label">{{ s.label }}</span>
              <span class="stat-card__value">
                {{ s.value }}<i class="stat-card__suffix">{{ s.suffix }}</i>
              </span>
              <span class="stat-card__extra">{{ s.desc }}</span>
            </div>
          </div>
        </div>

        <div class="quick-grid">
          <div v-for="q in quickEntries" :key="q.path" class="quick-entry" @click="go(q.path)">
            <span class="quick-entry__icon">
              <component :is="q.icon" />
            </span>
            <div class="quick-entry__body">
              <span class="quick-entry__title">{{ q.title }}</span>
              <span class="quick-entry__desc">{{ q.desc }}</span>
            </div>
            <Right class="quick-entry__arrow" />
          </div>
        </div>
      </w-tab-pane>

      <!-- 今日接诊队列 -->
      <w-tab-pane label="今日接诊队列" name="queue" lazy>
        <w-card shadow="never" class="hospital-card queue-card">
          <template #header>
            <div class="queue-head">
              <span class="queue-head__title">今日接诊队列</span>
              <span class="queue-head__count">共 {{ todayList.length }} 人</span>
            </div>
          </template>
          <w-table :data="todayList" border stripe :loading="loading" row-key="checkinId" size="default">
            <template #empty>
              <w-empty type="patient" description="今日暂无待接诊患者" />
            </template>
            <w-table-column type="index" label="序号" width="64" align="center" />
            <w-table-column label="患者" min-width="100">
              <template #default="{ row }">{{ row.patientName || row.patientId }}</template>
            </w-table-column>
            <w-table-column prop="departmentName" label="科室" min-width="110" show-overflow-tooltip />
            <w-table-column prop="doctorName" label="医生" min-width="90" show-overflow-tooltip />
            <w-table-column label="时段" min-width="150">
              <template #default="{ row }">{{ row.appointmentDate }} {{ row.slotStart }}–{{ row.slotEnd }}</template>
            </w-table-column>
            <w-table-column label="诊室" min-width="90">
              <template #default="{ row }">{{ row.consultRoom || '-' }}</template>
            </w-table-column>
            <w-table-column label="状态" width="110">
              <template #default="{ row }">
                <w-tag :type="statusMeta(row).type" effect="light" size="small">{{ statusMeta(row).label }}</w-tag>
              </template>
            </w-table-column>
            <w-table-column label="操作" width="110" fixed="right">
              <template #default>
                <w-button size="small" type="primary" plain @click="go('/clinic')">去接诊</w-button>
              </template>
            </w-table-column>
          </w-table>
        </w-card>
      </w-tab-pane>
    </w-tabs>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 4px 0;
}

.page-head__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-head__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 统计卡栅格 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card__value {
  display: inline-flex;
  align-items: baseline;
}

.stat-card__suffix {
  font-style: normal;
  margin-left: 6px;
  font-size: 13px;
  font-weight: 400;
  color: var(--hospital-text-second);
}

/* 快捷入口 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.quick-entry {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--hospital-bg-card);
  border-radius: var(--hospital-radius-md);
  border: 1px solid var(--hospital-border-lighter);
  box-shadow: var(--hospital-shadow-card);
  cursor: pointer;
  transition: transform 0.2s ease-out, box-shadow 0.25s ease-out;
}

.quick-entry:hover {
  transform: translateY(-3px);
  box-shadow: var(--hospital-shadow-hover);
}

.quick-entry__icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--hospital-radius-md);
  background: var(--hospital-primary-plain);
  color: var(--hospital-primary);
  font-size: 20px;
}

.quick-entry__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.quick-entry__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}

.quick-entry__desc {
  font-size: 12.5px;
  color: var(--hospital-text-third);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quick-entry__arrow {
  flex-shrink: 0;
  color: var(--hospital-text-third);
  font-size: 16px;
}

/* 队列卡片 */
.queue-card {
  margin-top: 4px;
}

.queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.queue-head__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}

.queue-head__count {
  font-size: 12.5px;
  color: var(--hospital-text-third);
}

/* 响应式 */
@media (max-width: 1200px) {
  .stat-grid,
  .quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stat-grid,
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
