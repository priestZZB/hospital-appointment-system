<script setup lang="ts">
/**
 * 急诊中心页（迭代14，/emergency）。
 * Tab1 预检分级（G1 四级登记/流转/今日统计）；Tab2 抢救记录（G2 开始/结束）；Tab3 考勤打卡（L4）。
 */
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Plus, Search } from '@win-design-next/icons-vue'
import {
  attendanceCheckinApi,
  attendanceCheckoutApi,
  createRescueApi,
  createTriageApi,
  finishRescueApi,
  getAttendanceListApi,
  getAttendanceSummaryApi,
  getAttendanceTodayApi,
  getRescueListApi,
  getTriageListApi,
  getTriageTodayStatsApi,
  triageStatusApi,
} from '@/api/emergency'
import type { AttendanceRow, RescueRow, TriageRow } from '@/api/emergency'

const activeTab = ref<'triage' | 'rescue' | 'attendance'>('triage')
function fmt(v?: string | null): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

/* ================= Tab1 预检分级 ================= */
const triageLoading = ref(false)
const triageList = ref<TriageRow[]>([])
const triageTotal = ref(0)
const triagePageNo = ref(1)
const triageQuery = reactive<{ triageLevel: string; status: string; keyword: string }>({ triageLevel: '', status: '', keyword: '' })
const triageTodayStats = ref<Array<Record<string, unknown>>>([])
const triageDialog = ref(false)
const triageSaving = ref(false)
const triageForm = reactive<{
  patientId: number | null
  patientName: string
  triageLevel: string
  chiefComplaint: string
  temperature: number | null
  pulse: number | null
  bloodPressure: string
}>({ patientId: null, patientName: '', triageLevel: 'YELLOW', chiefComplaint: '', temperature: null, pulse: null, bloodPressure: '' })

function levelMeta(v?: string): { label: string; tag: 'danger' | 'warning' | 'primary' | 'success' } {
  const k = (v || '').toUpperCase()
  if (k === 'RED') return { label: '红-危重', tag: 'danger' }
  if (k === 'ORANGE') return { label: '橙-急重', tag: 'warning' }
  if (k === 'YELLOW') return { label: '黄-急症', tag: 'primary' }
  return { label: '绿-轻症', tag: 'success' }
}
function statusMeta(v?: string): { label: string; tag: 'info' | 'warning' | 'success' } {
  const k = (v || '').toUpperCase()
  if (k === 'TREATING') return { label: '就诊中', tag: 'warning' }
  if (k === 'DONE') return { label: '已完成', tag: 'success' }
  return { label: '待就诊', tag: 'info' }
}
async function fetchTriage() {
  triageLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: triagePageNo.value, pageSize: 10 }
    if (triageQuery.triageLevel) params.triageLevel = triageQuery.triageLevel
    if (triageQuery.status) params.status = triageQuery.status
    const kw = triageQuery.keyword.trim()
    if (kw) params.keyword = kw
    const page = await getTriageListApi(params)
    triageList.value = Array.isArray(page?.records) ? page.records : []
    triageTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '分诊加载失败')
  } finally {
    triageLoading.value = false
  }
}
async function fetchTriageStats() {
  try {
    triageTodayStats.value = (await getTriageTodayStatsApi()) || []
  } catch {
    triageTodayStats.value = []
  }
}
function statText(level: string): string {
  const hit = triageTodayStats.value.find((s) => String(s.level || '').toUpperCase() === level)
  return String(hit?.cnt ?? 0)
}
function openTriage() {
  triageForm.patientId = null
  triageForm.patientName = ''
  triageForm.triageLevel = 'YELLOW'
  triageForm.chiefComplaint = ''
  triageForm.temperature = null
  triageForm.pulse = null
  triageForm.bloodPressure = ''
  triageDialog.value = true
}
async function submitTriage() {
  if (!triageForm.patientId || !triageForm.patientName.trim()) {
    WMessage.warning('请填写患者 ID 与姓名')
    return
  }
  triageSaving.value = true
  try {
    await createTriageApi({
      patientId: triageForm.patientId,
      patientName: triageForm.patientName.trim(),
      triageLevel: triageForm.triageLevel,
      chiefComplaint: triageForm.chiefComplaint.trim() || undefined,
      temperature: triageForm.temperature ?? undefined,
      pulse: triageForm.pulse ?? undefined,
      bloodPressure: triageForm.bloodPressure.trim() || undefined,
    })
    WMessage.success('分诊登记完成')
    triageDialog.value = false
    fetchTriage()
    fetchTriageStats()
  } catch (e) {
    WMessage.error((e as Error).message || '登记失败')
  } finally {
    triageSaving.value = false
  }
}
async function triageFlow(row: TriageRow, status: 'TREATING' | 'DONE') {
  if (!row.id) return
  try {
    await triageStatusApi(row.id, status)
    WMessage.success(status === 'TREATING' ? '已开始接诊' : '就诊已完成')
    fetchTriage()
    fetchTriageStats()
  } catch (e) {
    WMessage.error((e as Error).message || '流转失败')
  }
}

/* ================= Tab2 抢救记录 ================= */
const rescueLoading = ref(false)
const rescueList = ref<RescueRow[]>([])
const rescueTotal = ref(0)
const rescuePageNo = ref(1)
const rescueQuery = reactive<{ outcome: string; keyword: string }>({ outcome: '', keyword: '' })
const rescueDialog = ref(false)
const rescueSaving = ref(false)
const rescueForm = reactive<{ patientId: number | null; patientName: string; measures: string; participants: string }>({
  patientId: null,
  patientName: '',
  measures: '',
  participants: '',
})

function outcomeMeta(v?: string): { label: string; tag: 'warning' | 'success' | 'danger' } {
  const k = (v || '').toUpperCase()
  if (k === 'SUCCESS') return { label: '抢救成功', tag: 'success' }
  if (k === 'DEATH') return { label: '抢救无效', tag: 'danger' }
  return { label: '进行中', tag: 'warning' }
}
async function fetchRescue() {
  rescueLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: rescuePageNo.value, pageSize: 10 }
    if (rescueQuery.outcome) params.outcome = rescueQuery.outcome
    const kw = rescueQuery.keyword.trim()
    if (kw) params.keyword = kw
    const page = await getRescueListApi(params)
    rescueList.value = Array.isArray(page?.records) ? page.records : []
    rescueTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '抢救记录加载失败')
  } finally {
    rescueLoading.value = false
  }
}
function openRescue() {
  rescueForm.patientId = null
  rescueForm.patientName = ''
  rescueForm.measures = ''
  rescueForm.participants = ''
  rescueDialog.value = true
}
async function submitRescue() {
  if (!rescueForm.patientId || !rescueForm.patientName.trim()) {
    WMessage.warning('请填写患者 ID 与姓名')
    return
  }
  rescueSaving.value = true
  try {
    await createRescueApi({
      patientId: rescueForm.patientId,
      patientName: rescueForm.patientName.trim(),
      measures: rescueForm.measures.trim() || undefined,
      participants: rescueForm.participants.trim() || undefined,
    })
    WMessage.success('抢救记录已开始')
    rescueDialog.value = false
    fetchRescue()
  } catch (e) {
    WMessage.error((e as Error).message || '开始失败')
  } finally {
    rescueSaving.value = false
  }
}
async function rescueFinish(row: RescueRow, outcome: 'SUCCESS' | 'DEATH') {
  if (!row.id) return
  try {
    await finishRescueApi(row.id, outcome)
    WMessage.success(outcome === 'SUCCESS' ? '已记录抢救成功' : '已记录抢救无效')
    fetchRescue()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

/* ================= Tab3 考勤打卡 ================= */
const attLoading = ref(false)
const attList = ref<AttendanceRow[]>([])
const attTotal = ref(0)
const attPageNo = ref(1)
const attSummary = ref<Array<Record<string, unknown>>>([])
const attToday = ref<Record<string, unknown> | null>(null)
const attBusy = ref(false)

function attMeta(v?: string): { label: string; tag: 'warning' | 'success' } {
  return (v || '').toUpperCase() === 'OFF_DUTY' ? { label: '已签退', tag: 'success' } : { label: '在岗', tag: 'warning' }
}
async function fetchAttendance() {
  attLoading.value = true
  try {
    const page = await getAttendanceListApi({ pageNo: attPageNo.value, pageSize: 10 })
    attList.value = Array.isArray(page?.records) ? page.records : []
    attTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '考勤加载失败')
  } finally {
    attLoading.value = false
  }
}
async function fetchAttMeta() {
  try {
    attToday.value = await getAttendanceTodayApi()
  } catch {
    attToday.value = null
  }
  try {
    attSummary.value = (await getAttendanceSummaryApi()) || []
  } catch {
    attSummary.value = []
  }
}
async function doCheckin() {
  attBusy.value = true
  try {
    await attendanceCheckinApi()
    WMessage.success('上班打卡成功')
    fetchAttMeta()
    fetchAttendance()
  } catch (e) {
    WMessage.error((e as Error).message || '打卡失败')
  } finally {
    attBusy.value = false
  }
}
async function doCheckout() {
  attBusy.value = true
  try {
    await attendanceCheckoutApi()
    WMessage.success('下班签退成功')
    fetchAttMeta()
    fetchAttendance()
  } catch (e) {
    WMessage.error((e as Error).message || '签退失败')
  } finally {
    attBusy.value = false
  }
}

onMounted(() => {
  fetchTriage()
  fetchTriageStats()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">急诊中心</h2>
      <p class="page-subtitle">预检分级 · 抢救记录 · 考勤打卡（G1/G2/L4）</p>
    </div>

    <w-tabs v-model="activeTab" class="hospital-card">
      <w-tab-pane label="预检分级" name="triage" />
      <w-tab-pane label="抢救记录" name="rescue" />
      <w-tab-pane label="考勤打卡" name="attendance" />
    </w-tabs>

    <!-- Tab1 预检分级 -->
    <template v-if="activeTab === 'triage'">
      <w-card shadow="never" class="hospital-card">
        <div class="stat-row">
          <span class="stat-item stat-red">红-危重：{{ statText('RED') }}</span>
          <span class="stat-item stat-orange">橙-急重：{{ statText('ORANGE') }}</span>
          <span class="stat-item stat-yellow">黄-急症：{{ statText('YELLOW') }}</span>
          <span class="stat-item stat-green">绿-轻症：{{ statText('GREEN') }}</span>
          <span class="stat-tip">（今日）</span>
        </div>
        <w-form inline class="query-form">
          <w-form-item label="分级">
            <w-select v-model="triageQuery.triageLevel" placeholder="全部" clearable style="width: 140px">
              <w-option label="红-危重" value="RED" />
              <w-option label="橙-急重" value="ORANGE" />
              <w-option label="黄-急症" value="YELLOW" />
              <w-option label="绿-轻症" value="GREEN" />
            </w-select>
          </w-form-item>
          <w-form-item label="状态">
            <w-select v-model="triageQuery.status" placeholder="全部" clearable style="width: 120px">
              <w-option label="待就诊" value="WAITING" />
              <w-option label="就诊中" value="TREATING" />
              <w-option label="已完成" value="DONE" />
            </w-select>
          </w-form-item>
          <w-form-item label="关键字">
            <w-input v-model="triageQuery.keyword" placeholder="患者/就诊号" clearable style="width: 160px" @keyup.enter="triagePageNo = 1; fetchTriage()" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :icon="Search" :loading="triageLoading" @click="triagePageNo = 1; fetchTriage()">查询</w-button>
          </w-form-item>
        </w-form>
        <w-table :data="triageList" row-key="id" border stripe :loading="triageLoading" empty-text="暂无分诊记录" size="default">
          <w-table-column label="就诊号" width="185" show-overflow-tooltip>
            <template #default="{ row }">{{ row.visitNo || row.visit_no || '-' }}</template>
          </w-table-column>
          <w-table-column label="患者" width="110">
            <template #default="{ row }">{{ row.patientName || row.patient_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="分级" width="100" align="center">
            <template #default="{ row }">
              <w-tag :type="levelMeta(row.triageLevel).tag" effect="light">{{ levelMeta(row.triageLevel).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="主诉" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.chiefComplaint || row.chief_complaint || '-' }}</template>
          </w-table-column>
          <w-table-column label="体征" width="190" show-overflow-tooltip>
            <template #default="{ row }">
              {{ [row.temperature != null ? `${row.temperature}℃` : '', row.pulse != null ? `脉${row.pulse}` : '', row.bloodPressure || row.blood_pressure].filter(Boolean).join(' / ') || '-' }}
            </template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="statusMeta(row.status).tag" effect="light">{{ statusMeta(row.status).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="登记时间" width="150">
            <template #default="{ row }">{{ fmt(row.createTime || row.create_time) }}</template>
          </w-table-column>
          <w-table-column label="操作" width="140" fixed="right" align="center">
            <template #default="{ row }">
              <w-button v-if="(row.status || '') === 'WAITING'" type="primary" link @click="triageFlow(row, 'TREATING')">接诊</w-button>
              <w-button v-if="(row.status || '') === 'TREATING'" type="success" link @click="triageFlow(row, 'DONE')">完成</w-button>
              <span v-if="(row.status || '') === 'DONE'">-</span>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="triagePageNo"
          :page-size="10"
          :total="triageTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { triagePageNo = p; fetchTriage() }"
        />
      </w-card>
      <div class="float-bar">
        <w-button type="primary" :icon="Plus" @click="openTriage">分诊登记</w-button>
      </div>
    </template>

    <!-- Tab2 抢救记录 -->
    <template v-if="activeTab === 'rescue'">
      <w-card shadow="never" class="hospital-card">
        <div class="table-toolbar list-toolbar">
          <div class="table-toolbar__left">
            <w-select v-model="rescueQuery.outcome" placeholder="全部结局" clearable style="width: 140px" @change="rescuePageNo = 1; fetchRescue()">
              <w-option label="进行中" value="ONGOING" />
              <w-option label="抢救成功" value="SUCCESS" />
              <w-option label="抢救无效" value="DEATH" />
            </w-select>
          </div>
          <div class="table-toolbar__right">
            <w-button type="primary" :icon="Plus" @click="openRescue">开始抢救</w-button>
          </div>
        </div>
        <w-table :data="rescueList" row-key="id" border stripe :loading="rescueLoading" empty-text="暂无抢救记录" size="default">
          <w-table-column label="抢救单号" width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.rescueNo || row.rescue_no || '-' }}</template>
          </w-table-column>
          <w-table-column label="患者" width="110">
            <template #default="{ row }">{{ row.patientName || row.patient_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="抢救措施" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.measures || '-' }}</template>
          </w-table-column>
          <w-table-column label="参与人员" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.participants || row.participants || '-' }}</template>
          </w-table-column>
          <w-table-column label="结局" width="100" align="center">
            <template #default="{ row }">
              <w-tag :type="outcomeMeta(row.outcome).tag" effect="light">{{ outcomeMeta(row.outcome).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="开始时间" width="150">
            <template #default="{ row }">{{ fmt(row.startTime || row.start_time) }}</template>
          </w-table-column>
          <w-table-column label="结束时间" width="150">
            <template #default="{ row }">{{ fmt(row.endTime || row.end_time) }}</template>
          </w-table-column>
          <w-table-column label="操作" width="160" fixed="right" align="center">
            <template #default="{ row }">
              <template v-if="(row.outcome || '') === 'ONGOING'">
                <w-button type="success" link @click="rescueFinish(row, 'SUCCESS')">成功</w-button>
                <w-button type="danger" link @click="rescueFinish(row, 'DEATH')">无效</w-button>
              </template>
              <span v-else>-</span>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="rescuePageNo"
          :page-size="10"
          :total="rescueTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { rescuePageNo = p; fetchRescue() }"
        />
      </w-card>
    </template>

    <!-- Tab3 考勤打卡 -->
    <template v-if="activeTab === 'attendance'">
      <w-card shadow="never" class="hospital-card">
        <div class="att-bar">
          <div class="att-status">
            今日状态：
            <w-tag v-if="attToday && attToday.id" :type="attMeta(String(attToday.status)).tag" effect="light">
              {{ attMeta(String(attToday.status)).label }}（打卡 {{ fmt(String(attToday.checkinTime || attToday.checkinTime || '')) }}）
            </w-tag>
            <w-tag v-else type="info" effect="light">未打卡</w-tag>
          </div>
          <div class="att-actions">
            <w-button type="primary" :loading="attBusy" :disabled="!!(attToday && attToday.id)" @click="doCheckin">上班打卡</w-button>
            <w-button type="success" :loading="attBusy" :disabled="!(attToday && attToday.id && attToday.status === 'ON_DUTY')" @click="doCheckout">下班签退</w-button>
          </div>
        </div>
        <h4 class="sub-title">近 30 天出勤统计</h4>
        <w-table :data="attSummary" row-key="userId" border size="small" empty-text="暂无统计数据" max-height="240">
          <w-table-column label="人员" min-width="140">
            <template #default="{ row }">{{ row.userName || row.user_name || `#${row.userId || row.user_id}` }}</template>
          </w-table-column>
          <w-table-column label="出勤天数" width="110" align="center">
            <template #default="{ row }">{{ row.days ?? '-' }}</template>
          </w-table-column>
          <w-table-column label="完整出勤（含签退）" width="170" align="center">
            <template #default="{ row }">{{ row.fullDays ?? row.fulldays ?? '-' }}</template>
          </w-table-column>
        </w-table>
        <h4 class="sub-title">考勤明细</h4>
        <w-table :data="attList" row-key="id" border stripe :loading="attLoading" empty-text="暂无考勤记录" size="default">
          <w-table-column label="人员" min-width="130">
            <template #default="{ row }">{{ row.userName || row.user_name || `#${row.userId || row.user_id}` }}</template>
          </w-table-column>
          <w-table-column label="日期" width="120">
            <template #default="{ row }">{{ fmt(row.workDate || row.work_date).slice(0, 10) }}</template>
          </w-table-column>
          <w-table-column label="上班打卡" width="150">
            <template #default="{ row }">{{ fmt(row.checkinTime || row.checkin_time) }}</template>
          </w-table-column>
          <w-table-column label="下班签退" width="150">
            <template #default="{ row }">{{ fmt(row.checkoutTime || row.checkout_time) }}</template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="attMeta(row.status).tag" effect="light">{{ attMeta(row.status).label }}</w-tag>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="attPageNo"
          :page-size="10"
          :total="attTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { attPageNo = p; fetchAttendance() }"
        />
      </w-card>
    </template>

    <!-- 分诊登记弹窗 -->
    <w-dialog v-model="triageDialog" title="急诊分诊登记" width="560px" destroy-on-close>
      <w-form label-width="96px">
        <w-form-item label="患者 ID" required>
          <w-input-number v-model="triageForm.patientId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="患者姓名" required>
          <w-input v-model="triageForm.patientName" />
        </w-form-item>
        <w-form-item label="预检分级" required>
          <w-radio-group v-model="triageForm.triageLevel">
            <w-radio value="RED">红-危重</w-radio>
            <w-radio value="ORANGE">橙-急重</w-radio>
            <w-radio value="YELLOW">黄-急症</w-radio>
            <w-radio value="GREEN">绿-轻症</w-radio>
          </w-radio-group>
        </w-form-item>
        <w-form-item label="主诉">
          <w-input v-model="triageForm.chiefComplaint" type="textarea" :rows="2" />
        </w-form-item>
        <w-form-item label="体温(℃)">
          <w-input-number v-model="triageForm.temperature" :precision="1" :min="30" :max="45" style="width: 160px" />
        </w-form-item>
        <w-form-item label="脉搏">
          <w-input-number v-model="triageForm.pulse" :min="0" :max="300" style="width: 160px" />
        </w-form-item>
        <w-form-item label="血压">
          <w-input v-model="triageForm.bloodPressure" placeholder="如 90/60" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="triageDialog = false">取消</w-button>
        <w-button type="primary" :loading="triageSaving" @click="submitTriage">登记</w-button>
      </template>
    </w-dialog>

    <!-- 开始抢救弹窗 -->
    <w-dialog v-model="rescueDialog" title="开始抢救登记" width="560px" destroy-on-close>
      <w-form label-width="96px">
        <w-form-item label="患者 ID" required>
          <w-input-number v-model="rescueForm.patientId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="患者姓名" required>
          <w-input v-model="rescueForm.patientName" />
        </w-form-item>
        <w-form-item label="抢救措施">
          <w-input v-model="rescueForm.measures" type="textarea" :rows="2" placeholder="如 心肺复苏+电除颤" />
        </w-form-item>
        <w-form-item label="参与人员">
          <w-input v-model="rescueForm.participants" placeholder="如 急诊一组" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="rescueDialog = false">取消</w-button>
        <w-button type="primary" :loading="rescueSaving" @click="submitRescue">开始抢救</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.float-bar {
  margin-top: 12px;
}
.stat-row {
  display: flex;
  gap: 18px;
  margin-bottom: 12px;
  font-size: 13px;
}
.stat-item {
  padding: 3px 10px;
  border-radius: 12px;
}
.stat-red { background: #fde8e8; color: #c0392b; }
.stat-orange { background: #fdebd0; color: #b9770e; }
.stat-yellow { background: #fcf3cf; color: #9a7d0a; }
.stat-green { background: #e8f8f0; color: #1e8449; }
.stat-tip { color: #98a3b3; align-self: center; }
.att-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.att-actions {
  display: flex;
  gap: 10px;
}
.sub-title {
  margin: 14px 0 8px;
}
</style>
