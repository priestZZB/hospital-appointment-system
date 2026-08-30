<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Check, Hospital, Rank, Refresh, Talk, UserGroup } from '@win-design-next/icons-vue'
import {
  callNextApi,
  checkinApi,
  getDepartmentsApi,
  getQueueSnapshotApi,
  missedApi,
  recallApi,
} from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO, QueuePatientVO, QueueSnapshotVO } from '@/types'

const userStore = useUserStore()
const canOperate = computed(() => userStore.isTriageNurse || userStore.isAdmin)

const departments = ref<DepartmentVO[]>([])
const deptId = ref<number>()
const consultRoom = ref('1')
const snapshot = ref<QueueSnapshotVO | null>(null)
const loading = ref(false)
const calling = ref(false)
const checkinIdText = ref('')
const checkinLoading = ref(false)

let timer: number | undefined

const currentCall = computed(() => snapshot.value?.currentCall ?? null)
const waitingList = computed(() => snapshot.value?.waitingList ?? [])

function formatTime(v?: string): string {
  if (!v) return '-'
  return v.length > 16 ? v.slice(11, 16) : v
}

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

async function fetchSnapshot() {
  if (!deptId.value) return
  loading.value = true
  try {
    snapshot.value = await getQueueSnapshotApi(deptId.value)
  } catch (e) {
    WMessage.error((e as Error).message || '排队快照加载失败')
  } finally {
    loading.value = false
  }
}

function watchDept() {
  fetchSnapshot()
}

async function handleCheckin() {
  const raw = checkinIdText.value.trim()
  const appointmentId = Number(raw)
  if (!raw || Number.isNaN(appointmentId)) {
    WMessage.warning('请输入正确的预约 ID')
    return
  }
  checkinLoading.value = true
  try {
    await checkinApi(appointmentId)
    WMessage.success('签到登记成功')
    checkinIdText.value = ''
    await fetchSnapshot()
  } catch (e) {
    WMessage.error((e as Error).message || '签到登记失败')
  } finally {
    checkinLoading.value = false
  }
}

async function handleCallNext() {
  if (!deptId.value) return
  calling.value = true
  try {
    await callNextApi({ departmentId: deptId.value, consultRoom: consultRoom.value })
    WMessage.success('叫号成功')
    await fetchSnapshot()
  } catch (e) {
    WMessage.error((e as Error).message || '叫号失败')
  } finally {
    calling.value = false
  }
}

async function handleRecall(row: QueuePatientVO) {
  try {
    await recallApi(row.checkinId, consultRoom.value)
    WMessage.success('已重呼')
    await fetchSnapshot()
  } catch (e) {
    WMessage.error((e as Error).message || '重呼失败')
  }
}

async function handleMissed(row: QueuePatientVO) {
  try {
    await missedApi(row.checkinId)
    WMessage.success('已标记过号')
    await fetchSnapshot()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

onMounted(async () => {
  await loadDepartments()
  await fetchSnapshot()
  timer = window.setInterval(() => void fetchSnapshot(), 30000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">分诊台</h2>
      <p class="page-subtitle">签到登记 · 科室排队快照 · 叫号辅助</p>
    </div>

    <!-- 控制条 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <w-select v-model="deptId" placeholder="选择科室" clearable style="width: 200px" @change="watchDept">
            <template #prefix>
              <Hospital class="toolbar-icon" />
            </template>
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
          <w-input v-if="canOperate" v-model="consultRoom" placeholder="诊室号" clearable style="width: 130px" />
          <w-button type="primary" plain :loading="loading" :icon="Refresh" @click="fetchSnapshot">刷新</w-button>
        </div>
        <div class="table-toolbar__right">
          <w-tag type="success" effect="light" size="small">每 30s 自动刷新</w-tag>
        </div>
      </div>
    </w-card>

    <!-- 签到登记 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-title"><Check class="toolbar-title__icon" />签到登记</span>
        </div>
      </div>
      <div class="checkin-box">
        <w-input
          v-model="checkinIdText"
          placeholder="输入预约 ID 进行签到登记"
          clearable
          style="width: 260px"
          @keyup.enter="handleCheckin"
        />
        <w-button type="primary" :icon="Check" :loading="checkinLoading" :disabled="!canOperate" @click="handleCheckin">
          签到
        </w-button>
      </div>
    </w-card>

    <!-- 当前叫号 + 等待队列 -->
    <div class="board-grid">
      <section class="call-screen">
        <div class="call-screen__head">
          <span class="call-screen__label"><Rank class="call-screen__label-icon" />当前叫号</span>
          <span class="call-screen__room">{{ currentCall?.consultRoom ? `${currentCall.consultRoom} 诊室` : '—' }}</span>
        </div>
        <div class="call-screen__body">
          <template v-if="currentCall">
            <div class="call-screen__number">{{ currentCall.slotSeq ?? '-' }}</div>
            <div class="call-screen__name">{{ currentCall.patientName || `患者#${currentCall.patientId}` }}</div>
            <div class="call-screen__meta">
              {{ currentCall.doctorName ? `${currentCall.doctorName} · ` : '' }}{{ currentCall.departmentName || '' }}
            </div>
          </template>
          <div v-else class="call-screen__empty">
            <Rank class="call-screen__empty-icon" />
            <span>等待叫号</span>
          </div>
        </div>
        <div v-if="canOperate" class="call-screen__ops">
          <w-button type="primary" :loading="calling" :icon="Talk" @click="handleCallNext">叫下一位</w-button>
          <template v-if="currentCall">
            <w-button type="warning" plain @click="handleRecall(currentCall)">重呼</w-button>
            <w-popconfirm
              title="确认将该患者标记为过号？"
              confirm-button-text="过号"
              cancel-button-text="取消"
              confirm-button-type="danger"
              @confirm="handleMissed(currentCall)"
            >
              <template #reference>
                <w-button type="danger" plain>过号</w-button>
              </template>
            </w-popconfirm>
          </template>
        </div>
      </section>

      <w-card shadow="never" class="hospital-card queue-card">
        <template #header>
          <div class="card-head">
            <span class="card-head__title"><UserGroup class="card-head__icon" />等待队列</span>
            <w-tag type="primary" effect="light" size="small">{{ waitingList.length }} 人</w-tag>
          </div>
        </template>
        <w-table
          :data="waitingList"
          :loading="loading"
          stripe
          size="small"
          row-key="checkinId"
          max-height="420"
          empty-text="暂无等待患者"
        >
          <w-table-column type="index" label="序" width="56" align="center" />
          <w-table-column label="号码" width="88">
            <template #default="{ row }">
              <span class="queue-no">{{ row.slotSeq ?? '-' }}</span>
            </template>
          </w-table-column>
          <w-table-column label="患者" min-width="120">
            <template #default="{ row }">
              <span class="queue-name">{{ row.patientName || `患者#${row.patientId}` }}</span>
            </template>
          </w-table-column>
          <w-table-column label="签到时间" width="96">
            <template #default="{ row }">
              <span class="muted">{{ formatTime(row.checkinTime) }}</span>
            </template>
          </w-table-column>
          <w-table-column v-if="canOperate" label="操作" width="120" align="center">
            <template #default="{ row }">
              <w-button size="small" text type="primary" @click="handleRecall(row)">重呼</w-button>
              <w-popconfirm
                title="确认标记过号？"
                confirm-button-text="过号"
                cancel-button-text="取消"
                confirm-button-type="danger"
                width="180"
                @confirm="handleMissed(row)"
              >
                <template #reference>
                  <w-button size="small" text type="danger">过号</w-button>
                </template>
              </w-popconfirm>
            </template>
          </w-table-column>
        </w-table>
      </w-card>
    </div>
  </div>
</template>

<style scoped>
.toolbar-icon {
  color: var(--w3-color-primary);
  font-size: 16px;
}
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

.checkin-box {
  display: flex;
  align-items: center;
  gap: 12px;
}

.board-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(420px, 1.6fr);
  gap: 16px;
  align-items: stretch;
}
@media (max-width: 1100px) {
  .board-grid {
    grid-template-columns: 1fr;
  }
}

.call-screen {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 340px;
  padding: 22px 24px;
  border-radius: var(--hospital-radius-md);
  color: #fff;
  background: linear-gradient(
    160deg,
    var(--hospital-bg-sidebar) 0%,
    var(--hospital-bg-sidebar) 42%,
    var(--w3-color-primary) 140%
  );
  box-shadow: var(--hospital-shadow-card);
}
.call-screen::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(circle at 24% 16%, var(--w3-color-primary-plain, #eaeefe) 0%, transparent 46%);
  opacity: 0.18;
}
.call-screen__head {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.call-screen__label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  opacity: 0.9;
}
.call-screen__label-icon {
  font-size: 18px;
}
.call-screen__room {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.92);
}
.call-screen__body {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 12px 0;
}
.call-screen__number {
  font-size: 84px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  letter-spacing: 2px;
  text-shadow: 0 8px 32px rgba(0, 0, 0, 0.35);
}
.call-screen__name {
  margin-top: 14px;
  font-size: 24px;
  font-weight: 600;
}
.call-screen__meta {
  margin-top: 6px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}
.call-screen__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.55);
}
.call-screen__empty-icon {
  font-size: 32px;
  opacity: 0.5;
}
.call-screen__ops {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding-top: 8px;
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
}
.card-head__icon {
  color: var(--w3-color-primary);
  font-size: 17px;
}

.queue-no {
  font-weight: 600;
  color: var(--w3-color-primary);
  font-variant-numeric: tabular-nums;
}
.queue-name {
  font-weight: 500;
}
.muted {
  color: var(--hospital-text-third);
  font-variant-numeric: tabular-nums;
}
</style>
