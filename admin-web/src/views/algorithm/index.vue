<script setup lang="ts">
/**
 * 智能算法中心页（迭代15，/algorithm）。
 * Tab1 遗传排班（B1-1 生成/应用建议）；Tab2 停诊重调度（B1-3）；Tab3 爽约预测（B1-4）。
 */
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Search } from '@win-design-next/icons-vue'
import {
  applyScheduleBatchApi,
  generateScheduleApi,
  getNoShowListApi,
  getRescheduleBatchesApi,
  getScheduleListApi,
  predictNoShowApi,
  stopRescheduleApi,
} from '@/api/algorithm'
import type { ScheduleSuggestionRow } from '@/api/algorithm'

const activeTab = ref<'ga' | 'resched' | 'noshow'>('ga')
function fmt(v?: string | null): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

/* ================= Tab1 遗传排班 ================= */
const gaLoading = ref(false)
const gaRunning = ref(false)
const gaList = ref<ScheduleSuggestionRow[]>([])
const gaTotal = ref(0)
const gaPageNo = ref(1)
const gaQuery = reactive<{ status: string }>({ status: '' })
const gaResult = ref<Record<string, unknown> | null>(null)

function gaMeta(v?: string): { label: string; tag: 'info' | 'success' | 'danger' } {
  const k = (v || '').toUpperCase()
  if (k === 'APPLIED') return { label: '已应用', tag: 'success' }
  if (k === 'REJECTED') return { label: '已驳回', tag: 'danger' }
  return { label: '草稿', tag: 'info' }
}
async function fetchGa() {
  gaLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: gaPageNo.value, pageSize: 10 }
    if (gaQuery.status) params.status = gaQuery.status
    const page = await getScheduleListApi(params)
    gaList.value = Array.isArray(page?.records) ? page.records : []
    gaTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '建议加载失败')
  } finally {
    gaLoading.value = false
  }
}
async function runGa() {
  gaRunning.value = true
  try {
    gaResult.value = await generateScheduleApi({})
    WMessage.success(`遗传算法完成：批次 ${gaResult.value?.batchNo}，最优适应度 ${gaResult.value?.bestFitness}`)
    fetchGa()
  } catch (e) {
    WMessage.error((e as Error).message || '生成失败')
  } finally {
    gaRunning.value = false
  }
}
async function applyBatch(batchNo: string) {
  try {
    await applyScheduleBatchApi(batchNo)
    WMessage.success('批次已应用')
    fetchGa()
  } catch (e) {
    WMessage.error((e as Error).message || '应用失败')
  }
}

/* ================= Tab2 停诊重调度 ================= */
const reschedLoading = ref(false)
const reschedList = ref<Array<Record<string, unknown>>>([])
const reschedTotal = ref(0)
const reschedPageNo = ref(1)
const reschedDialog = ref(false)
const reschedSaving = ref(false)
const reschedForm = reactive<{ doctorId: number | null; stopDate: string }>({ doctorId: null, stopDate: '' })
const reschedResult = ref<Record<string, unknown> | null>(null)

async function fetchResched() {
  reschedLoading.value = true
  try {
    const page = await getRescheduleBatchesApi({ pageNo: reschedPageNo.value, pageSize: 10 })
    reschedList.value = Array.isArray(page?.records) ? page.records : []
    reschedTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '批次加载失败')
  } finally {
    reschedLoading.value = false
  }
}
async function submitResched() {
  if (!reschedForm.doctorId || !reschedForm.stopDate) {
    WMessage.warning('请填写医生 ID 与停诊日期')
    return
  }
  reschedSaving.value = true
  try {
    reschedResult.value = await stopRescheduleApi({ doctorId: reschedForm.doctorId, stopDate: reschedForm.stopDate })
    WMessage.success(`重调度完成：受影响 ${reschedResult.value?.affectedCount} 个预约`)
    reschedDialog.value = false
    fetchResched()
  } catch (e) {
    WMessage.error((e as Error).message || '生成失败')
  } finally {
    reschedSaving.value = false
  }
}

/* ================= Tab3 爽约预测 ================= */
const noshowLoading = ref(false)
const noshowList = ref<Array<Record<string, unknown>>>([])
const noshowTotal = ref(0)
const noshowPageNo = ref(1)
const noshowForm = reactive<{ patientId: number | null; appointmentId: number | null; historyTotal: number | null; historyNoShow: number | null; advanceDays: number | null; hourOfDay: number | null }>({
  patientId: null,
  appointmentId: null,
  historyTotal: 6,
  historyNoShow: 2,
  advanceDays: 9,
  hourOfDay: 9,
})
const noshowResult = ref<Record<string, unknown> | null>(null)

function riskMeta(v?: string): { label: string; tag: 'success' | 'warning' | 'danger' } {
  const k = (v || '').toUpperCase()
  if (k === 'HIGH') return { label: '高风险', tag: 'danger' }
  if (k === 'MEDIUM') return { label: '中风险', tag: 'warning' }
  return { label: '低风险', tag: 'success' }
}
async function fetchNoshow() {
  noshowLoading.value = true
  try {
    const page = await getNoShowListApi({ pageNo: noshowPageNo.value, pageSize: 10 })
    noshowList.value = Array.isArray(page?.records) ? page.records : []
    noshowTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '记录加载失败')
  } finally {
    noshowLoading.value = false
  }
}
async function submitNoshow() {
  if (!noshowForm.patientId || !noshowForm.appointmentId) {
    WMessage.warning('请填写患者 ID 与预约 ID')
    return
  }
  noshowLoading.value = true
  try {
    noshowResult.value = await predictNoShowApi({
      patientId: noshowForm.patientId,
      appointmentId: noshowForm.appointmentId,
      historyTotal: noshowForm.historyTotal ?? 0,
      historyNoShow: noshowForm.historyNoShow ?? 0,
      advanceDays: noshowForm.advanceDays ?? 1,
      hourOfDay: noshowForm.hourOfDay ?? 9,
    })
    WMessage.success('预测完成')
    fetchNoshow()
  } catch (e) {
    WMessage.error((e as Error).message || '预测失败')
  } finally {
    noshowLoading.value = false
  }
}

onMounted(() => {
  fetchGa()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">智能算法中心</h2>
      <p class="page-subtitle">遗传排班 · 停诊重调度 · 爽约预测（B1）</p>
    </div>

    <w-tabs v-model="activeTab" class="hospital-card">
      <w-tab-pane label="遗传排班" name="ga" />
      <w-tab-pane label="停诊重调度" name="resched" />
      <w-tab-pane label="爽约预测" name="noshow" />
    </w-tabs>

    <!-- Tab1 遗传排班 -->
    <template v-if="activeTab === 'ga'">
      <w-card shadow="never" class="hospital-card">
        <div class="ga-bar">
          <div class="ga-info">
            <template v-if="gaResult">
              批次 <b>{{ gaResult.batchNo }}</b> · 最优适应度 <b>{{ gaResult.bestFitness }}</b>
              · 医生 {{ gaResult.doctorCount }} 人 · 种群 {{ gaResult.population }} × {{ gaResult.generations }} 代
            </template>
            <template v-else>点击「运行遗传算法」生成下一周排班建议（含已有人工排班硬约束保留）</template>
          </div>
          <w-button type="primary" :loading="gaRunning" @click="runGa">运行遗传算法</w-button>
        </div>
        <w-form inline class="query-form">
          <w-form-item label="状态">
            <w-select v-model="gaQuery.status" placeholder="全部" clearable style="width: 120px" @change="gaPageNo = 1; fetchGa()">
              <w-option label="草稿" value="DRAFT" />
              <w-option label="已应用" value="APPLIED" />
            </w-select>
          </w-form-item>
          <w-form-item>
            <w-button :icon="Search" :loading="gaLoading" @click="gaPageNo = 1; fetchGa()">查询</w-button>
          </w-form-item>
        </w-form>
        <w-table :data="gaList" row-key="id" border stripe :loading="gaLoading" empty-text="暂无排班建议" size="default">
          <w-table-column label="批次" width="185" show-overflow-tooltip>
            <template #default="{ row }">{{ row.batchNo || '-' }}</template>
          </w-table-column>
          <w-table-column label="周起始" width="110">
            <template #default="{ row }">{{ row.weekStart || '-' }}</template>
          </w-table-column>
          <w-table-column label="医生" width="110">
            <template #default="{ row }">{{ row.doctorName || `#${row.doctorId}` }}</template>
          </w-table-column>
          <w-table-column label="周计划（一~日）" width="130" align="center">
            <template #default="{ row }">
              <span class="plan-days">
                <i v-for="(d, k) in String(row.planDays || '').split('')" :key="k" :class="{ on: d === '1' }"></i>
              </span>
            </template>
          </w-table-column>
          <w-table-column label="出诊天" width="80" align="center">
            <template #default="{ row }">{{ String(row.planDays || '').split('').filter((c) => c === '1').length }}</template>
          </w-table-column>
          <w-table-column label="适应度" width="95" align="center">
            <template #default="{ row }">{{ row.fitness ?? '-' }}</template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="gaMeta(row.status).tag" effect="light">{{ gaMeta(row.status).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="操作" width="110" fixed="right" align="center">
            <template #default="{ row }">
              <w-button v-if="row.status === 'DRAFT'" type="success" link @click="applyBatch(row.batchNo)">应用批次</w-button>
              <span v-else>-</span>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="gaPageNo"
          :page-size="10"
          :total="gaTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { gaPageNo = p; fetchGa() }"
        />
      </w-card>
    </template>

    <!-- Tab2 停诊重调度 -->
    <template v-if="activeTab === 'resched'">
      <w-card shadow="never" class="hospital-card">
        <div class="table-toolbar list-toolbar">
          <div class="table-toolbar__left"><span class="table-total">共 {{ reschedTotal }} 个批次</span></div>
          <div class="table-toolbar__right">
            <w-button type="primary" @click="reschedForm.doctorId = null; reschedForm.stopDate = ''; reschedResult = null; reschedDialog = true">
              新建停诊重调度
            </w-button>
          </div>
        </div>
        <w-table :data="reschedList" row-key="id" border stripe :loading="reschedLoading" empty-text="暂无重调度批次" size="default">
          <w-table-column label="批次" width="185" show-overflow-tooltip>
            <template #default="{ row }">{{ row.batchNo || '-' }}</template>
          </w-table-column>
          <w-table-column label="医生 ID" width="90" align="center">
            <template #default="{ row }">{{ row.doctorId || '-' }}</template>
          </w-table-column>
          <w-table-column label="停诊日期" width="110">
            <template #default="{ row }">{{ row.stopDate || '-' }}</template>
          </w-table-column>
          <w-table-column label="受影响预约" width="105" align="center">
            <template #default="{ row }">{{ row.affectedCnt ?? 0 }}</template>
          </w-table-column>
          <w-table-column label="状态" width="95" align="center">
            <template #default="{ row }">
              <w-tag :type="row.status === 'NOTIFIED' ? 'success' : 'warning'" effect="light">
                {{ row.status === 'NOTIFIED' ? '已通知' : '已生成' }}
              </w-tag>
            </template>
          </w-table-column>
          <w-table-column label="生成时间" width="150">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="reschedPageNo"
          :page-size="10"
          :total="reschedTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { reschedPageNo = p; fetchResched() }"
        />
      </w-card>
    </template>

    <!-- Tab3 爽约预测 -->
    <template v-if="activeTab === 'noshow'">
      <w-card shadow="never" class="hospital-card">
        <w-form inline class="query-form">
          <w-form-item label="患者 ID">
            <w-input-number v-model="noshowForm.patientId" :min="1" style="width: 130px" />
          </w-form-item>
          <w-form-item label="预约 ID">
            <w-input-number v-model="noshowForm.appointmentId" :min="1" style="width: 130px" />
          </w-form-item>
          <w-form-item label="历史就诊">
            <w-input-number v-model="noshowForm.historyTotal" :min="0" style="width: 110px" />
          </w-form-item>
          <w-form-item label="历史爽约">
            <w-input-number v-model="noshowForm.historyNoShow" :min="0" style="width: 110px" />
          </w-form-item>
          <w-form-item label="提前天数">
            <w-input-number v-model="noshowForm.advanceDays" :min="0" style="width: 110px" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :loading="noshowLoading" @click="submitNoshow">预测</w-button>
          </w-form-item>
        </w-form>
        <w-alert v-if="noshowResult" :type="riskMeta(String(noshowResult.riskLevel)).tag === 'danger' ? 'error' : 'success'" :closable="false" class="predict-alert">
          预测结果：风险评分 <b>{{ noshowResult.score }}</b>
          <w-tag :type="riskMeta(String(noshowResult.riskLevel)).tag" effect="light" class="risk-tag">
            {{ riskMeta(String(noshowResult.riskLevel)).label }}
          </w-tag>
          {{ noshowResult.suggestion }}
        </w-alert>
        <w-table :data="noshowList" row-key="id" border stripe :loading="noshowLoading" empty-text="暂无预测记录" size="default">
          <w-table-column label="患者 ID" width="100" align="center">
            <template #default="{ row }">{{ row.patientId || '-' }}</template>
          </w-table-column>
          <w-table-column label="预约 ID" width="100" align="center">
            <template #default="{ row }">{{ row.appointmentId || '-' }}</template>
          </w-table-column>
          <w-table-column label="评分" width="90" align="center">
            <template #default="{ row }">{{ row.score ?? '-' }}</template>
          </w-table-column>
          <w-table-column label="风险" width="95" align="center">
            <template #default="{ row }">
              <w-tag :type="riskMeta(row.riskLevel).tag" effect="light">{{ riskMeta(row.riskLevel).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="特征" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ row.factors || '-' }}</template>
          </w-table-column>
          <w-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="noshowPageNo"
          :page-size="10"
          :total="noshowTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { noshowPageNo = p; fetchNoshow() }"
        />
      </w-card>
    </template>

    <!-- 停诊重调度弹窗 -->
    <w-dialog v-model="reschedDialog" title="停诊重调度" width="460px" destroy-on-close>
      <w-form label-width="96px">
        <w-form-item label="医生 ID" required>
          <w-input-number v-model="reschedForm.doctorId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="停诊日期" required>
          <w-input v-model="reschedForm.stopDate" placeholder="yyyy-MM-dd" />
        </w-form-item>
      </w-form>
      <w-alert v-if="reschedResult" type="success" :closable="false">
        批次 {{ reschedResult.batchNo }}：受影响 {{ reschedResult.affectedCount }} 个预约已生成替诊建议
      </w-alert>
      <template #footer>
        <w-button @click="reschedDialog = false">取消</w-button>
        <w-button type="primary" :loading="reschedSaving" @click="submitResched">生成</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.ga-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}
.ga-info {
  font-size: 13px;
  color: #5d6b82;
}
.plan-days {
  display: inline-flex;
  gap: 4px;
}
.plan-days i {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  background: #e4e9f0;
  display: inline-block;
}
.plan-days i.on {
  background: #3b82f6;
}
.predict-alert {
  margin-bottom: 14px;
}
.risk-tag {
  margin: 0 6px;
}
</style>
