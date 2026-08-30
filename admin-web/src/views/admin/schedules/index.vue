<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Component } from 'vue'
import { WMessage } from 'win-design-next'
import {
  CircleCheck,
  CircleClose,
  Date as DateIcon,
  Hospital,
  List,
  Plus,
  Refresh,
  Search,
  Stamp,
  Time,
  User,
} from '@win-design-next/icons-vue'
import {
  cancelScheduleApi,
  confirmScheduleApi,
  createScheduleApi,
  rejectScheduleApi,
  getDepartmentsApi,
  getDoctorsApi,
  getSchedulesApi,
  getScheduleSlotsApi,
} from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO, DoctorVO, ScheduleVO, SlotVO } from '@/types'

const userStore = useUserStore()

/** 顶部 tab：管理员默认管理视图，医生默认「我的排班」 */
const activeTab = ref<string>(userStore.isAdmin ? 'manage' : 'mine')

/* ---------------- 排班管理（查询 + 表格） ---------------- */
const loading = ref(false)
const list = ref<ScheduleVO[]>([])
const departments = ref<DepartmentVO[]>([])
const doctors = ref<DoctorVO[]>([])

const query = reactive<{
  departmentId: number | undefined
  startDate: string
  endDate: string
}>({ departmentId: undefined, startDate: '', endDate: '' })

const pageNo = ref(1)
const pageSize = ref(10)

function today(): string {
  const d = new Date()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

async function fetchList() {
  if (!query.departmentId) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = await getSchedulesApi({
      departmentId: query.departmentId,
      startDate: query.startDate || today(),
      endDate: query.endDate || '',
    })
    pageNo.value = 1
  } catch (e) {
    WMessage.error((e as Error).message || '排班列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  fetchList()
}

function handleReset() {
  query.departmentId = undefined
  query.startDate = ''
  query.endDate = ''
  fetchList()
}

/* ---------------- 我的排班（日期选择 + 卡片列表） ---------------- */
const mineDate = ref(today())
const mineLoading = ref(false)
const mineList = ref<ScheduleVO[]>([])

async function fetchMine() {
  if (!query.departmentId) return
  mineLoading.value = true
  try {
    mineList.value = await getSchedulesApi({
      departmentId: query.departmentId,
      startDate: mineDate.value,
      endDate: mineDate.value,
    })
  } catch (e) {
    WMessage.error((e as Error).message || '排班日历加载失败')
  } finally {
    mineLoading.value = false
  }
}

function setToday() {
  mineDate.value = today()
  fetchMine()
}

/* ---------------- 客户端分页 ---------------- */
const pagedList = computed<ScheduleVO[]>(() =>
  list.value.slice((pageNo.value - 1) * pageSize.value, pageNo.value * pageSize.value),
)

/* ---------------- 统计卡 ---------------- */
interface StatItem {
  label: string
  value: number
  unit: string
  icon: Component
  tone: 'primary' | 'success' | 'danger' | 'warning'
  extra: string
}

const stats = computed<StatItem[]>(() => {
  const total = list.value.length
  const available = list.value.filter((s) => s.status !== 'CANCELLED').length
  const cancelled = list.value.filter((s) => s.status === 'CANCELLED').length
  const remainSlots = list.value.reduce((sum, s) => sum + (s.availableSlots ?? 0), 0)
  return [
    { label: '排班总数', value: total, unit: '条', icon: List, tone: 'primary', extra: '当前筛选结果' },
    { label: '可预约排班', value: available, unit: '条', icon: CircleCheck, tone: 'success', extra: '状态正常' },
    { label: '已取消排班', value: cancelled, unit: '条', icon: CircleClose, tone: 'danger', extra: '已级联处理' },
    { label: '剩余号源', value: remainSlots, unit: '个', icon: Stamp, tone: 'warning', extra: '可预约号源合计' },
  ]
})

/* ---------------- 创建排班 ---------------- */
interface ScheduleForm {
  doctorId: number | undefined
  departmentId: number | undefined
  scheduleDate: string
  period: string
  periodStart: string
  periodEnd: string
  slotDuration: number
  registerFee: number
}

const dialogVisible = ref(false)
const saving = ref(false)

const form = reactive<ScheduleForm>({
  doctorId: undefined,
  departmentId: undefined,
  scheduleDate: today(),
  period: 'AM',
  periodStart: '08:00',
  periodEnd: '12:00',
  slotDuration: 10,
  registerFee: 20,
})

async function loadDoctors() {
  if (!form.departmentId) {
    doctors.value = []
    return
  }
  try {
    const page = await getDoctorsApi({ departmentId: form.departmentId, pageNo: 1, pageSize: 100 })
    doctors.value = page.records || []
  } catch (e) {
    doctors.value = []
    WMessage.error((e as Error).message || '医生列表加载失败')
  }
}

function openCreate() {
  Object.assign(form, {
    doctorId: undefined,
    departmentId: query.departmentId,
    scheduleDate: today(),
    period: 'AM',
    periodStart: '08:00',
    periodEnd: '12:00',
    slotDuration: 10,
    registerFee: 20,
  })
  doctors.value = []
  dialogVisible.value = true
  if (form.departmentId) loadDoctors()
}

function onDepartmentChange() {
  form.doctorId = undefined
  loadDoctors()
}

async function handleSave() {
  if (!form.doctorId || !form.departmentId || !form.scheduleDate || !form.periodStart || !form.periodEnd) {
    WMessage.warning('请完整填写医生、科室、日期与时段')
    return
  }
  saving.value = true
  try {
    await createScheduleApi({ ...form })
    WMessage.success('排班已提交，待门诊部确认后生成号源')
    dialogVisible.value = false
    fetchList()
    if (mineDate.value === form.scheduleDate) fetchMine()
  } catch (e) {
    WMessage.error((e as Error).message || '创建失败')
  } finally {
    saving.value = false
  }
}

/* ---------------- 取消排班（w-popconfirm 内联确认） ---------------- */
async function handleCancel(row: ScheduleVO) {
  try {
    await cancelScheduleApi(row.id)
    WMessage.success('排班已取消')
    fetchList()
    fetchMine()
  } catch (e) {
    WMessage.error((e as Error).message || '取消失败')
  }
}

/* ---------------- 详情（含号源明细） ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ScheduleVO | null>(null)
const slots = ref<SlotVO[]>([])

async function openDetail(row: ScheduleVO) {
  detail.value = row
  slots.value = []
  detailVisible.value = true
  detailLoading.value = true
  try {
    slots.value = await getScheduleSlotsApi(row.id)
  } catch (e) {
    WMessage.error((e as Error).message || '号源明细加载失败')
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 展示辅助 ---------------- */
function periodText(period: string): string {
  return period === 'PM' ? '下午' : '上午'
}

function scheduleStatusText(row: ScheduleVO): string {
  if (row.status === 'CANCELLED') return '已取消'
  if (row.auditStatus === 'PENDING') return '待确认'
  if (row.auditStatus === 'REJECTED') return '已驳回'
  return '可预约'
}

function scheduleTagType(row: ScheduleVO): 'success' | 'warning' | 'danger' | 'info' {
  if (row.status === 'CANCELLED') return 'danger'
  if (row.auditStatus === 'PENDING') return 'warning'
  if (row.auditStatus === 'REJECTED') return 'info'
  return 'success'
}

/* ---------------- 门诊部确认/驳回排班 ---------------- */
async function handleConfirm(row: ScheduleVO) {
  try {
    await confirmScheduleApi(row.id)
    WMessage.success('排班已确认，号源已生成')
    fetchList()
    fetchMine()
  } catch (e) {
    WMessage.error((e as Error).message || '确认失败')
  }
}

async function handleReject(row: ScheduleVO) {
  try {
    await rejectScheduleApi(row.id)
    WMessage.success('排班已驳回')
    fetchList()
    fetchMine()
  } catch (e) {
    WMessage.error((e as Error).message || '驳回失败')
  }
}

function slotStatusText(status: string): string {
  if (status === 'BOOKED') return '已预约'
  if (status === 'CANCELLED') return '已取消'
  return '可预约'
}

function slotTagType(status: string): 'success' | 'primary' | 'danger' {
  if (status === 'BOOKED') return 'primary'
  if (status === 'CANCELLED') return 'danger'
  return 'success'
}

function slotPercent(row: ScheduleVO): number {
  const total = row.totalSlots || 0
  if (!total) return 0
  return Math.round(((row.availableSlots ?? 0) / total) * 100)
}

onMounted(async () => {
  try {
    departments.value = await getDepartmentsApi()
    if (!query.departmentId && departments.value.length) {
      query.departmentId = departments.value[0].id
    }
  } catch (e) {
    WMessage.error((e as Error).message || '科室列表加载失败')
  }
  fetchList()
  fetchMine()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">排班管理</h2>
      <p class="page-subtitle">创建排班自动生成号源；取消排班将级联取消预约并退款</p>
    </div>

    <!-- 顶部 tab 导航 -->
    <w-tabs v-model="activeTab">
      <w-tab-pane v-if="userStore.isAdmin" name="manage">
        <template #label>
          <span class="tab-label"><List class="tab-icon" />排班管理</span>
        </template>

        <div class="manage-tab">
          <!-- 统计卡 -->
          <div class="stat-grid">
            <div v-for="s in stats" :key="s.label" class="stat-card">
              <span class="stat-card__icon" :class="`stat-card__icon--${s.tone}`">
                <component :is="s.icon" />
              </span>
              <div class="stat-card__body">
                <span class="stat-card__label">{{ s.label }}</span>
                <span class="stat-card__value">
                  {{ s.value.toLocaleString() }}<span class="stat-value__unit">{{ s.unit }}</span>
                </span>
                <span class="stat-card__extra">{{ s.extra }}</span>
              </div>
            </div>
          </div>

          <!-- 查询区 -->
          <w-card shadow="never" class="hospital-card query-card">
            <div class="filter-bar">
              <w-select v-model="query.departmentId" placeholder="全部科室" clearable style="width: 200px">
                <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
              </w-select>
              <w-date-picker-pro
                v-model="query.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="开始日期"
                style="width: 160px"
              />
              <w-date-picker-pro
                v-model="query.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="结束日期"
                style="width: 160px"
              />
              <w-button type="primary" :icon="Search" @click="handleQuery">查询</w-button>
              <w-button :icon="Refresh" @click="handleReset">重置</w-button>
            </div>
          </w-card>

          <!-- 表格区 -->
          <w-card shadow="never" class="hospital-card table-card">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <span class="table-card__title">排班列表</span>
                <span class="table-card__count">共 {{ list.length }} 条</span>
              </div>
              <div class="table-toolbar__right">
                <w-button type="primary" :icon="Plus" @click="openCreate">创建排班</w-button>
              </div>
            </div>

            <w-table :data="pagedList" border stripe :loading="loading" row-key="id" empty-text="暂无排班数据" size="default">
              <w-table-column type="index" label="#" width="56" align="center" />
              <w-table-column prop="doctorName" label="医生" min-width="110" show-overflow-tooltip />
              <w-table-column prop="departmentName" label="科室" min-width="110" show-overflow-tooltip />
              <w-table-column prop="scheduleDate" label="日期" width="110" />
              <w-table-column label="时段" width="70" align="center">
                <template #default="{ row }">{{ periodText(row.period) }}</template>
              </w-table-column>
              <w-table-column label="出诊时间" min-width="130">
                <template #default="{ row }">{{ row.periodStart }} – {{ row.periodEnd }}</template>
              </w-table-column>
              <w-table-column prop="totalSlots" label="总号源" width="80" align="center" />
              <w-table-column label="剩余号源" width="90" align="center">
                <template #default="{ row }">{{ row.availableSlots ?? 0 }}</template>
              </w-table-column>
              <w-table-column label="挂号费" width="90" align="right">
                <template #default="{ row }">¥{{ (row.registerFee ?? 0).toFixed(2) }}</template>
              </w-table-column>
              <w-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <w-tag :type="scheduleTagType(row)" effect="light" size="small">
                    {{ scheduleStatusText(row) }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column label="操作" width="220" fixed="right" align="center">
                <template #default="{ row }">
                  <w-button size="small" text type="primary" @click="openDetail(row)">详情</w-button>
                  <template v-if="row.auditStatus === 'PENDING' && userStore.isAdmin">
                    <w-button size="small" text type="success" @click="handleConfirm(row)">确认</w-button>
                    <w-button size="small" text type="warning" @click="handleReject(row)">驳回</w-button>
                  </template>
                  <w-popconfirm
                    v-if="userStore.isAdmin && row.status !== 'CANCELLED' && row.auditStatus !== 'PENDING'"
                    title="取消该排班将级联取消预约并退款，确定继续？"
                    confirm-button-text="取消排班"
                    cancel-button-text="再想想"
                    confirm-button-type="danger"
                    width="220"
                    @confirm="handleCancel(row)"
                  >
                    <template #reference>
                      <w-button size="small" text type="danger">取消排班</w-button>
                    </template>
                  </w-popconfirm>
                  <span v-else-if="userStore.isAdmin && row.status === 'CANCELLED'" class="op-disabled">-</span>
                </template>
              </w-table-column>
            </w-table>

            <div class="pagination-wrap">
              <w-pagination
                v-model:current-page="pageNo"
                v-model:page-size="pageSize"
                :total="list.length"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
              />
            </div>
          </w-card>
        </div>
      </w-tab-pane>

      <w-tab-pane name="mine">
        <template #label>
          <span class="tab-label"><Hospital class="tab-icon" />我的排班</span>
        </template>

        <div class="mine-tab">
          <div class="filter-bar mine-toolbar">
            <w-date-picker-pro
              v-model="mineDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 180px"
              @change="fetchMine"
            />
            <w-button :icon="DateIcon" @click="setToday">今天</w-button>
            <span class="mine-toolbar__hint">{{ mineDate }} · 共 {{ mineList.length }} 条排班</span>
          </div>

          <div v-loading="mineLoading" class="schedule-grid">
            <div v-for="s in mineList" :key="s.id" class="schedule-card hospital-card">
              <div class="schedule-card__head">
                <div class="schedule-card__doctor">
                  <span class="doctor-avatar"><User /></span>
                  <div class="doctor-info">
                    <div class="doctor-name">{{ s.doctorName }}</div>
                    <div class="doctor-title">{{ s.doctorTitle || '医生' }}</div>
                  </div>
                </div>
                <w-tag :type="scheduleTagType(s)" effect="light" size="small">
                  {{ scheduleStatusText(s) }}
                </w-tag>
              </div>

              <div class="schedule-card__meta">
                <span class="meta-item"><Hospital class="meta-icon" />{{ s.departmentName }}</span>
                <span class="meta-item">
                  <Time class="meta-icon" />{{ periodText(s.period) }} {{ s.periodStart }} – {{ s.periodEnd }}
                </span>
              </div>

              <div class="schedule-card__slots">
                <div class="slots-line">
                  <span class="slots-text">
                    剩余 <b>{{ s.availableSlots ?? 0 }}</b> / {{ s.totalSlots }} 号源
                  </span>
                  <span class="slots-fee">挂号费 ¥{{ (s.registerFee ?? 0).toFixed(2) }}</span>
                </div>
                <div class="slots-bar">
                  <div class="slots-bar__fill" :style="{ width: `${slotPercent(s)}%` }"></div>
                </div>
              </div>

              <div class="schedule-card__actions">
                <w-button size="small" text type="primary" @click="openDetail(s)">详情</w-button>
                <w-popconfirm
                  v-if="userStore.isAdmin && s.status !== 'CANCELLED'"
                  title="取消该排班将级联取消预约并退款，确定继续？"
                  confirm-button-text="取消排班"
                  cancel-button-text="再想想"
                  confirm-button-type="danger"
                  width="220"
                  @confirm="handleCancel(s)"
                >
                  <template #reference>
                    <w-button size="small" text type="danger">取消排班</w-button>
                  </template>
                </w-popconfirm>
              </div>
            </div>
          </div>

          <w-empty v-if="!mineLoading && mineList.length === 0" type="data" description="当日暂无排班" />
        </div>
      </w-tab-pane>
    </w-tabs>

    <!-- 排班详情 -->
    <w-dialog v-model="detailVisible" title="排班详情" width="640px">
      <w-descriptions v-if="detail" :column="2" border label-width="100px" class="detail-desc">
        <w-descriptions-item label="医生">{{ detail.doctorName }}（{{ detail.doctorTitle || '医生' }}）</w-descriptions-item>
        <w-descriptions-item label="科室">{{ detail.departmentName }}</w-descriptions-item>
        <w-descriptions-item label="日期">{{ detail.scheduleDate }}</w-descriptions-item>
        <w-descriptions-item label="时段">{{ periodText(detail.period) }}</w-descriptions-item>
        <w-descriptions-item label="出诊时间">{{ detail.periodStart }} – {{ detail.periodEnd }}</w-descriptions-item>
        <w-descriptions-item label="号源">{{ detail.availableSlots ?? 0 }} / {{ detail.totalSlots }}</w-descriptions-item>
        <w-descriptions-item label="挂号费">¥{{ (detail.registerFee ?? 0).toFixed(2) }}</w-descriptions-item>
        <w-descriptions-item label="状态">
          <w-tag :type="scheduleTagType(detail)" effect="light" size="small">
            {{ scheduleStatusText(detail) }}
          </w-tag>
        </w-descriptions-item>
      </w-descriptions>

      <div class="slot-block">
        <div class="slot-block__title"><Stamp class="meta-icon" />号源明细</div>
        <w-table :data="slots" size="small" border :loading="detailLoading" max-height="280" row-key="id">
          <w-table-column prop="slotSeq" label="序号" width="70" align="center" />
          <w-table-column label="时间段" min-width="140">
            <template #default="{ row }">{{ row.slotStart }} – {{ row.slotEnd }}</template>
          </w-table-column>
          <w-table-column label="挂号费" width="100" align="right">
            <template #default="{ row }">¥{{ (row.registerFee ?? 0).toFixed(2) }}</template>
          </w-table-column>
          <w-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <w-tag :type="slotTagType(row.status)" effect="light" size="small">{{ slotStatusText(row.status) }}</w-tag>
            </template>
          </w-table-column>
        </w-table>
      </div>

      <template #footer>
        <w-button @click="detailVisible = false">关闭</w-button>
      </template>
    </w-dialog>

    <!-- 创建排班 -->
    <w-dialog v-model="dialogVisible" title="创建排班" width="640px">
      <w-form :model="form" label-width="90px">
        <w-row :gutter="16">
          <w-col :span="12">
            <w-form-item label="科室" required>
              <w-select v-model="form.departmentId" placeholder="请选择科室" clearable @change="onDepartmentChange">
                <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
              </w-select>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="医生" required>
              <w-select v-model="form.doctorId" placeholder="请选择医生" clearable :disabled="!form.departmentId">
                <w-option v-for="doc in doctors" :key="doc.id" :label="doc.name" :value="doc.id" />
              </w-select>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="日期" required>
              <w-date-picker-pro
                v-model="form.scheduleDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择日期"
                style="width: 100%"
              />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="上/下午" required>
              <w-radio-group v-model="form.period">
                <w-radio value="AM">上午</w-radio>
                <w-radio value="PM">下午</w-radio>
              </w-radio-group>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="开始时间" required>
              <w-time-picker-pro v-model="form.periodStart" value-format="HH:mm" placeholder="开始时间" style="width: 100%" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="结束时间" required>
              <w-time-picker-pro v-model="form.periodEnd" value-format="HH:mm" placeholder="结束时间" style="width: 100%" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="号源间隔">
              <w-input-number v-model="form.slotDuration" :min="5" :max="60" :step="5" style="width: 100%" />
              <span class="form-unit">分钟</span>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="挂号费">
              <w-input-number v-model="form.registerFee" :min="0" :precision="2" :step="5" style="width: 100%" />
              <span class="form-unit">元</span>
            </w-form-item>
          </w-col>
        </w-row>
      </w-form>

      <template #footer>
        <w-button @click="dialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="saving" @click="handleSave">创建</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
/* tab 标签 */
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tab-icon {
  font-size: 16px;
}

/* 管理 tab */
.manage-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 640px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}

.stat-value__unit {
  font-size: 13px;
  font-weight: 500;
  color: var(--hospital-text-third);
  margin-left: 4px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.query-card {
  padding: 4px;
}

.table-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.table-card__count {
  font-size: 13px;
  color: var(--hospital-text-third);
}
.op-disabled {
  color: var(--hospital-text-third);
  font-size: 13px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

/* 我的排班 tab */
.mine-toolbar {
  margin-bottom: 4px;
}
.mine-toolbar__hint {
  margin-left: auto;
  font-size: 13px;
  color: var(--hospital-text-third);
}

.schedule-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
@media (max-width: 1400px) {
  .schedule-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 900px) {
  .schedule-grid {
    grid-template-columns: 1fr;
  }
}

.schedule-card {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.schedule-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.schedule-card__doctor {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.doctor-avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--hospital-radius-md);
  background: var(--hospital-primary-plain);
  color: var(--hospital-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.doctor-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.doctor-title {
  font-size: 12px;
  color: var(--hospital-text-third);
  margin-top: 2px;
}

.schedule-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--hospital-text-second);
}
.meta-icon {
  color: var(--hospital-primary);
  font-size: 14px;
  flex-shrink: 0;
}

.schedule-card__slots {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.slots-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: var(--hospital-text-second);
}
.slots-text b {
  font-size: 15px;
  color: var(--hospital-primary);
}
.slots-fee {
  color: var(--hospital-text-third);
}
.slots-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--hospital-border-lighter);
  overflow: hidden;
}
.slots-bar__fill {
  height: 100%;
  border-radius: 3px;
  background: var(--hospital-primary);
  transition: width 0.3s ease-out;
}

.schedule-card__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  border-top: 1px solid var(--hospital-border-lighter);
  padding-top: 12px;
}

/* 详情弹窗 */
.detail-desc {
  margin-bottom: 16px;
}
.slot-block__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--hospital-text-main);
  margin-bottom: 10px;
}

/* 表单单位 */
.form-unit {
  margin-left: 8px;
  font-size: 13px;
  color: var(--hospital-text-third);
  white-space: nowrap;
}
</style>
