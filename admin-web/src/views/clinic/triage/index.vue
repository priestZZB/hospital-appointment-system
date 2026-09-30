<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Hospital, Rank, Refresh, Talk, User, UserGroup } from '@win-design-next/icons-vue'
import {
  getQueueSnapshotApi,
  getTriageQueueApi,
  rejoinQueueApi,
  setTriagePriorityApi,
} from '@/api/clinic'
import { getDepartmentsApi } from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO, QueuePatientVO, TriageQueueItemVO } from '@/types'

const userStore = useUserStore()
/** 操作权限：分诊护士 / 管理员（与既有分诊台保持一致） */
const canOperate = computed(() => userStore.isTriageNurse || userStore.isAdmin)

const departments = ref<DepartmentVO[]>([])
const deptId = ref<number>()
const loading = ref(false)

/** 分诊队列（A1） */
const queue = ref<TriageQueueItemVO[]>([])
/** 优先级筛选：''-全部 / 0-急诊 / 1-优先 / 2-普通 */
const priorityFilter = ref<number | ''>('')

/** 当前叫号（复用既有排队快照接口做顶部展示） */
const snapshot = ref<{ currentCall?: QueuePatientVO | null; waitingList?: QueuePatientVO[] } | null>(null)
const currentCall = computed(() => snapshot.value?.currentCall ?? null)

let timer: number | undefined

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 优先级元信息：0 红-急诊 / 1 橙-优先 / 2 蓝-普通（未设置按普通展示） */
function priorityMeta(p?: number): { text: string; type: TagType } {
  if (p === 0) return { text: '急诊', type: 'danger' }
  if (p === 1) return { text: '优先', type: 'warning' }
  return { text: '普通', type: 'primary' }
}

function queueStatusMeta(status?: string): { text: string; type: TagType } {
  switch (status) {
    case 'CALLED':
      return { text: '已叫号', type: 'warning' }
    case 'IN_CONSULTATION':
    case 'CONSULTING':
      return { text: '就诊中', type: 'primary' }
    case 'MISSED':
      return { text: '已过号', type: 'danger' }
    case 'COMPLETED':
      return { text: '已完成', type: 'success' }
    case 'WAITING':
      return { text: '待接诊', type: 'info' }
    default:
      return { text: status || '未知', type: 'info' }
  }
}

function formatTime(v?: string): string {
  if (!v) return '-'
  return v.length > 16 ? v.slice(11, 16) : v
}

const filteredQueue = computed(() => {
  if (priorityFilter.value === '') return queue.value
  return queue.value.filter((row) => (row.priority ?? 2) === priorityFilter.value)
})

const stat = computed(() => ({
  total: queue.value.length,
  emergency: queue.value.filter((r) => r.priority === 0).length,
  returning: queue.value.filter((r) => r.returnFlag).length,
}))

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

async function fetchQueue() {
  if (!deptId.value) return
  loading.value = true
  try {
    const res = await getTriageQueueApi(deptId.value)
    queue.value = Array.isArray(res) ? res : []
  } catch (e) {
    WMessage.error((e as Error).message || '分诊队列加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchSnapshot() {
  if (!deptId.value) return
  try {
    snapshot.value = await getQueueSnapshotApi(deptId.value)
  } catch {
    // 快照失败不阻断队列展示（顶部叫号条退化为空态）
  }
}

function onDeptChange() {
  fetchQueue()
  fetchSnapshot()
}

function refreshAll() {
  fetchQueue()
  fetchSnapshot()
}

/** 设优先级（下拉 0/1/2 直接调 set-priority） */
async function handleSetPriority(row: TriageQueueItemVO, priority: number) {
  if (!canOperate.value) return
  try {
    await setTriagePriorityApi({ checkinId: row.checkinId, priority, returnFlag: !!row.returnFlag })
    row.priority = priority
    WMessage.success(`已设为${priorityMeta(priority).text}优先级`)
  } catch (e) {
    WMessage.error((e as Error).message || '设置优先级失败')
  }
}

/** 标记回诊（A6 rejoin） */
async function handleRejoin(row: TriageQueueItemVO) {
  try {
    await rejoinQueueApi(row.checkinId)
    WMessage.success('已标记回诊，患者重新进入队列')
    await fetchQueue()
  } catch (e) {
    WMessage.error((e as Error).message || '回诊操作失败')
  }
}

onMounted(async () => {
  await loadDepartments()
  refreshAll()
  timer = window.setInterval(() => void refreshAll(), 30000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">分诊台工作台</h2>
      <p class="page-subtitle">分诊队列 · 优先级调整（急诊/优先/普通）· 回诊标记 · 每 30s 自动刷新</p>
    </div>

    <!-- 当前叫号（简化展示） -->
    <w-card shadow="never" class="hospital-card call-strip">
      <div class="call-strip__body">
        <span class="call-strip__label"><Rank class="call-strip__icon" />当前呼叫</span>
        <template v-if="currentCall">
          <span class="call-strip__no">{{ currentCall.slotSeq ?? '-' }}</span>
          <span class="call-strip__name">{{ currentCall.patientName || `患者#${currentCall.patientId}` }}</span>
          <w-tag size="small" effect="plain" type="primary">
            {{ currentCall.consultRoom ? `${currentCall.consultRoom} 诊室` : '叫号中' }}
          </w-tag>
        </template>
        <span v-else class="call-strip__empty">暂无叫号</span>
        <span class="call-strip__spacer" />
        <span class="call-strip__stat"><UserGroup class="call-strip__mini" />等待 {{ snapshot?.waitingList?.length ?? 0 }}</span>
        <span class="call-strip__stat is-danger"><User class="call-strip__mini" />急诊 {{ stat.emergency }}</span>
        <span class="call-strip__stat is-warning"><Talk class="call-strip__mini" />回诊 {{ stat.returning }}</span>
      </div>
    </w-card>

    <!-- 控制条 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <w-select v-model="deptId" placeholder="选择科室" clearable style="width: 200px" @change="onDeptChange">
            <template #prefix>
              <Hospital class="toolbar-icon" />
            </template>
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
          <w-select v-model="priorityFilter" placeholder="全部优先级" style="width: 140px">
            <w-option label="全部优先级" value="" />
            <w-option label="急诊" :value="0" />
            <w-option label="优先" :value="1" />
            <w-option label="普通" :value="2" />
          </w-select>
          <w-button type="primary" plain :icon="Refresh" :loading="loading" @click="refreshAll">刷新</w-button>
        </div>
        <div class="table-toolbar__right">
          <w-tag type="success" effect="light" size="small">每 30s 自动刷新</w-tag>
        </div>
      </div>
    </w-card>

    <!-- 分诊队列 -->
    <w-card shadow="never" class="hospital-card">
      <template #header>
        <div class="card-head">
          <span class="card-head__title"><UserGroup class="card-head__icon" />分诊队列</span>
          <w-tag type="primary" effect="light" size="small">共 {{ filteredQueue.length }} 人</w-tag>
        </div>
      </template>
      <w-table
        :data="filteredQueue"
        :loading="loading"
        stripe
        size="small"
        row-key="checkinId"
        max-height="520"
        empty-text="暂无分诊患者"
      >
        <w-table-column type="index" label="序" width="56" align="center" />
        <w-table-column label="患者" min-width="130">
          <template #default="{ row }">
            <span class="patient-name">{{ row.patientName || `患者#${row.patientId}` }}</span>
          </template>
        </w-table-column>
        <w-table-column label="签到时间" width="96">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.checkinTime) }}</span>
          </template>
        </w-table-column>
        <w-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <w-tag :type="queueStatusMeta(row.queueStatus).type" effect="light" size="small">
              {{ queueStatusMeta(row.queueStatus).text }}
            </w-tag>
          </template>
        </w-table-column>
        <w-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <w-tag :type="priorityMeta(row.priority).type" effect="light" size="small">
              {{ priorityMeta(row.priority).text }}
            </w-tag>
          </template>
        </w-table-column>
        <w-table-column label="回诊" width="84" align="center">
          <template #default="{ row }">
            <w-tag v-if="row.returnFlag" type="warning" effect="light" size="small">回诊</w-tag>
            <span v-else class="muted">-</span>
          </template>
        </w-table-column>
        <w-table-column label="呼叫次数" width="90" align="center">
          <template #default="{ row }">
            <span class="call-count">{{ row.callCount ?? 0 }}</span>
          </template>
        </w-table-column>
        <w-table-column v-if="canOperate" label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <w-select
              :model-value="row.priority ?? 2"
              size="small"
              class="prio-select"
              :disabled="!canOperate"
              @change="(v: number) => handleSetPriority(row, v)"
            >
              <w-option label="急诊" :value="0" />
              <w-option label="优先" :value="1" />
              <w-option label="普通" :value="2" />
            </w-select>
            <w-button size="small" text type="warning" @click="handleRejoin(row)">回诊</w-button>
          </template>
        </w-table-column>
      </w-table>
    </w-card>
  </div>
</template>

<style scoped>
.toolbar-icon {
  color: var(--w3-color-primary);
  font-size: 16px;
}

/* 当前叫号条 */
.call-strip__body {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.call-strip__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.call-strip__icon {
  color: var(--w3-color-primary);
  font-size: 17px;
}
.call-strip__no {
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  color: var(--w3-color-primary);
  font-variant-numeric: tabular-nums;
}
.call-strip__name {
  font-size: 15px;
  font-weight: 500;
  color: var(--hospital-text-main);
}
.call-strip__empty {
  font-size: 13px;
  color: var(--hospital-text-third);
}
.call-strip__spacer {
  flex: 1;
}
.call-strip__stat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--hospital-text-second);
}
.call-strip__stat.is-danger {
  color: var(--hospital-danger, #e34d59);
}
.call-strip__stat.is-warning {
  color: var(--hospital-warning, #ed7b2f);
}
.call-strip__mini {
  font-size: 14px;
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

/* 表格 */
.patient-name {
  font-weight: 500;
}
.muted {
  color: var(--hospital-text-third);
}
.call-count {
  font-variant-numeric: tabular-nums;
  color: var(--hospital-text-second);
}
.prio-select {
  width: 92px;
  margin-right: 8px;
}
</style>
