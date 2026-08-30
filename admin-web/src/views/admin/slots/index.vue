<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import {
  CircleCheck,
  Close,
  Date as DateIcon,
  Hospital,
  List,
  Refresh,
  Search,
  User,
  UserGroup,
} from '@win-design-next/icons-vue'
import { getDepartmentsApi, getScheduleSlotsApi, getSlotsApi } from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO, SlotVO } from '@/types'

const userStore = useUserStore()
const departments = ref<DepartmentVO[]>([])
const loading = ref(false)
const queried = ref(false)
const list = ref<SlotVO[]>([])

const query = reactive({
  departmentId: undefined as number | undefined,
  date: '',
  scheduleId: undefined as number | undefined,
})

/** 排班 ID 精确查询为后台/医生侧能力，患者角色按科室 + 日期查询 */
const canQueryBySchedule = computed(() => userStore.isAdmin || userStore.isDoctor)

const summary = computed(() => {
  const total = list.value.length
  const available = list.value.filter((s) => s.status === 'AVAILABLE').length
  const booked = list.value.filter((s) => s.status === 'BOOKED').length
  const cancelled = list.value.filter((s) => s.status === 'CANCELLED').length
  return { total, available, booked, cancelled }
})

function today(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function statusText(status: string): string {
  return (
    {
      AVAILABLE: '可预约',
      BOOKED: '已预约',
      CANCELLED: '已取消',
    }[status] || status
  )
}

function statusType(status: string): 'success' | 'warning' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'info'> = {
    AVAILABLE: 'success',
    BOOKED: 'warning',
    CANCELLED: 'info',
  }
  return map[status] || 'info'
}

/** 单个号源是否仍有剩余可约名额（号源粒度 = 1 个名额） */
function remaining(slot: SlotVO): number {
  return slot.status === 'AVAILABLE' ? 1 : 0
}

async function fetchList() {
  if (!query.scheduleId && !(query.departmentId && query.date)) {
    WMessage.warning('请选择科室和日期，或填写排班 ID')
    return
  }
  loading.value = true
  try {
    if (query.scheduleId) {
      list.value = await getScheduleSlotsApi(query.scheduleId)
    } else if (query.departmentId && query.date) {
      list.value = await getSlotsApi(query.departmentId, query.date)
    } else {
      list.value = []
    }
    queried.value = true
  } catch (e) {
    WMessage.error((e as Error).message || '号源查询失败')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.departmentId = undefined
  query.date = today()
  query.scheduleId = undefined
  list.value = []
  queried.value = false
}

onMounted(async () => {
  query.date = today()
  try {
    departments.value = await getDepartmentsApi()
  } catch (e) {
    WMessage.error((e as Error).message || '科室列表加载失败')
  }
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">号源查询</h2>
      <p class="page-subtitle">按科室 + 日期查询可预约号源，或按排班 ID 精确查询</p>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="科室">
          <w-select
            v-model="query.departmentId"
            class="query-select"
            placeholder="请选择科室"
            clearable
            filterable
          >
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="日期">
          <w-date-picker-pro
            v-model="query.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择日期"
            class="query-date"
          />
        </w-form-item>
        <w-form-item v-if="canQueryBySchedule" label="排班 ID">
          <w-input-number
            v-model="query.scheduleId"
            :min="1"
            :controls="false"
            placeholder="按排班查询"
            class="query-schedule"
          />
        </w-form-item>
        <w-form-item>
          <div class="query-actions">
            <w-button type="primary" :icon="Search" :loading="loading" @click="fetchList">查询</w-button>
            <w-button :icon="Refresh" @click="resetQuery">重置</w-button>
          </div>
        </w-form-item>
      </w-form>
    </w-card>

    <!-- 结果区 -->
    <w-card shadow="never" class="hospital-card">
      <template #header>
        <div class="result-card__head">
          <span class="result-card__title">号源列表</span>
          <span class="result-card__count">共 {{ list.length }} 个号源</span>
        </div>
      </template>

      <div v-loading="loading" class="slot-body">
        <template v-if="queried">
          <!-- 汇总 -->
          <div class="slot-summary">
            <div class="slot-summary__item">
              <span class="slot-summary__icon slot-summary__icon--primary"><List /></span>
              <div class="slot-summary__body">
                <span class="slot-summary__label">号源总数</span>
                <span class="slot-summary__value">{{ summary.total }}</span>
              </div>
            </div>
            <div class="slot-summary__item">
              <span class="slot-summary__icon slot-summary__icon--success"><CircleCheck /></span>
              <div class="slot-summary__body">
                <span class="slot-summary__label">剩余可预约</span>
                <span class="slot-summary__value">{{ summary.available }}</span>
              </div>
            </div>
            <div class="slot-summary__item">
              <span class="slot-summary__icon slot-summary__icon--warning"><UserGroup /></span>
              <div class="slot-summary__body">
                <span class="slot-summary__label">已预约</span>
                <span class="slot-summary__value">{{ summary.booked }}</span>
              </div>
            </div>
            <div class="slot-summary__item">
              <span class="slot-summary__icon slot-summary__icon--info"><Close /></span>
              <div class="slot-summary__body">
                <span class="slot-summary__label">已取消</span>
                <span class="slot-summary__value">{{ summary.cancelled }}</span>
              </div>
            </div>
          </div>

          <!-- 号源卡片网格 -->
          <div v-if="list.length" class="slot-grid">
            <div
              v-for="row in list"
              :key="row.id"
              class="slot-card"
              :class="`slot-card--${row.status.toLowerCase()}`"
            >
              <div class="slot-card__head">
                <span class="slot-card__range">{{ row.slotStart }} – {{ row.slotEnd }}</span>
                <w-tag :type="statusType(row.status)" size="small" effect="light">
                  {{ statusText(row.status) }}
                </w-tag>
              </div>

              <div class="slot-card__doctor">
                <User class="slot-card__ic" />
                <span class="slot-card__name">{{ row.doctorName || '未分配医生' }}</span>
                <span v-if="row.doctorTitle" class="slot-card__title">{{ row.doctorTitle }}</span>
              </div>

              <div class="slot-card__meta">
                <span class="slot-card__meta-item">
                  <Hospital class="slot-card__ic" />
                  {{ row.departmentName || '—' }}
                </span>
                <span class="slot-card__meta-item">
                  <DateIcon class="slot-card__ic" />
                  {{ row.scheduleDate }} · {{ row.period }}
                </span>
              </div>

              <div class="slot-card__divider"></div>

              <div class="slot-card__foot">
                <div class="slot-card__remain">
                  <span class="slot-card__remain-label">剩余号源</span>
                  <span
                    class="slot-card__remain-value"
                    :class="{ 'is-empty': remaining(row) === 0 }"
                  >
                    {{ remaining(row) }}
                  </span>
                  <span class="slot-card__remain-unit">号</span>
                </div>
                <div class="slot-card__fee">
                  挂号费 <span class="slot-card__fee-value">¥{{ row.registerFee ?? '—' }}</span>
                </div>
              </div>
            </div>
          </div>

          <w-empty
            v-else
            type="search"
            description="暂无符合条件的号源"
            class="slot-empty"
          />
        </template>

        <w-empty
          v-else
          type="data"
          description="请选择科室和日期后查询号源"
          class="slot-empty"
        />
      </div>
    </w-card>
  </div>
</template>

<style scoped>
/* 查询区 */
.query-form {
  flex-wrap: wrap;
}
.query-select {
  width: 220px;
}
.query-date {
  width: 180px;
}
.query-schedule {
  width: 170px;
}
.query-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 结果区头部 */
.result-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.result-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.result-card__count {
  font-size: 12.5px;
  color: var(--hospital-text-third);
}

/* 号源汇总 */
.slot-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.slot-summary__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--hospital-bg-card);
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
}
.slot-summary__icon {
  width: 36px;
  height: 36px;
  border-radius: var(--hospital-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.slot-summary__icon--primary {
  background: var(--hospital-primary-plain);
  color: var(--hospital-primary);
}
.slot-summary__icon--success {
  background: var(--w3-color-success-plain, #e8f6ee);
  color: var(--hospital-success);
}
.slot-summary__icon--warning {
  background: var(--w3-color-warning-plain, #fff0df);
  color: var(--hospital-warning);
}
.slot-summary__icon--info {
  background: var(--w3-fill-color-lighter, #fafafa);
  color: var(--hospital-info);
}
.slot-summary__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
  min-width: 0;
}
.slot-summary__label {
  font-size: 12px;
  color: var(--hospital-text-second);
}
.slot-summary__value {
  font-size: 20px;
  font-weight: 700;
  color: var(--hospital-text-main);
  font-variant-numeric: tabular-nums;
}

/* 号源卡片网格 */
.slot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.slot-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--hospital-bg-card);
  border: 1px solid var(--hospital-border-lighter);
  border-left: 3px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
  box-shadow: var(--hospital-shadow-card);
  transition: box-shadow 0.2s ease-out, transform 0.2s ease-out;
}
.slot-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--hospital-shadow-hover);
}
.slot-card--available {
  border-left-color: var(--hospital-success);
}
.slot-card--booked {
  border-left-color: var(--hospital-warning);
}
.slot-card--cancelled {
  border-left-color: var(--hospital-info);
}
.slot-card__ic {
  width: 16px;
  height: 16px;
  color: var(--hospital-text-third);
  flex-shrink: 0;
}
.slot-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.slot-card__range {
  font-size: 16px;
  font-weight: 700;
  color: var(--hospital-text-main);
  font-variant-numeric: tabular-nums;
}
.slot-card__doctor {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.slot-card__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--hospital-text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.slot-card__title {
  font-size: 12px;
  color: var(--hospital-text-third);
  flex-shrink: 0;
}
.slot-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.slot-card__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--hospital-text-second);
}
.slot-card__divider {
  height: 1px;
  background: var(--hospital-border-lighter);
}
.slot-card__foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
}
.slot-card__remain {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.slot-card__remain-label {
  font-size: 12px;
  color: var(--hospital-text-third);
}
.slot-card__remain-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
  color: var(--hospital-primary);
  font-variant-numeric: tabular-nums;
}
.slot-card__remain-value.is-empty {
  color: var(--hospital-text-third);
}
.slot-card__remain-unit {
  font-size: 12px;
  color: var(--hospital-text-third);
}
.slot-card__fee {
  font-size: 12.5px;
  color: var(--hospital-text-second);
}
.slot-card__fee-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}

.slot-empty {
  padding: 32px 0;
}

@media (max-width: 1200px) {
  .slot-summary {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 640px) {
  .slot-summary {
    grid-template-columns: 1fr;
  }
}
</style>
