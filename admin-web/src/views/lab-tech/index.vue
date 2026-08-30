<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import { Check, File, Refresh, Scan, Search } from '@win-design-next/icons-vue'
import { createExamReportApi, executeLabApi, getLabApplicationsApi } from '@/api/medsupply'
import { useUserStore } from '@/stores/user'
import type { ExamApplicationVO } from '@/types'

type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'primary'
interface StatusMeta {
  text: string
  tone: StatusTone
}

const APPLICATION_STATUS_META: Record<string, StatusMeta> = {
  PENDING: { text: '待执行', tone: 'warning' },
  EXECUTING: { text: '执行中', tone: 'primary' },
  IN_PROGRESS: { text: '执行中', tone: 'primary' },
  COMPLETED: { text: '已完成', tone: 'success' },
  FINISHED: { text: '已完成', tone: 'success' },
  CANCELLED: { text: '已取消', tone: 'info' },
}

const PAY_STATUS_META: Record<string, StatusMeta> = {
  UNPAID: { text: '待缴费', tone: 'info' },
  PAID: { text: '已缴费', tone: 'success' },
  REFUNDED: { text: '已退款', tone: 'danger' },
}

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待执行' },
  { value: 'EXECUTING', label: '执行中' },
  { value: 'COMPLETED', label: '已完成' },
]

const userStore = useUserStore()

const loading = ref(false)
const list = ref<ExamApplicationVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const query = reactive({ status: '' })
const executingId = ref<number | null>(null)

// 结果录入
const reportVisible = ref(false)
const reportLoading = ref(false)
const reportOrder = ref<ExamApplicationVO | null>(null)
const reportForm = reactive({
  reportDesc: '',
  reportResult: '',
  status: 'PUBLISHED' as 'DRAFT' | 'PUBLISHED',
})

const pendingCount = computed(() => list.value.filter((a) => a.status === 'PENDING').length)

function statusMeta(status?: string): StatusMeta {
  return APPLICATION_STATUS_META[status ?? ''] ?? { text: status || '未知', tone: 'info' }
}

function payStatusMeta(status?: string): StatusMeta {
  return PAY_STATUS_META[status ?? ''] ?? { text: status || '-', tone: 'info' }
}

function normalizePage(page: unknown): { records: ExamApplicationVO[]; total: number } {
  if (Array.isArray(page)) {
    return { records: page as ExamApplicationVO[], total: (page as ExamApplicationVO[]).length }
  }
  const res = page as { records?: ExamApplicationVO[]; list?: ExamApplicationVO[]; total?: number }
  const records = res.records ?? res.list ?? []
  return { records, total: res.total ?? records.length }
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getLabApplicationsApi({
      status: query.status || undefined,
      offset: (pageNo.value - 1) * pageSize.value,
      limit: pageSize.value,
    })
    const { records, total: t } = normalizePage(page)
    list.value = records
    total.value = t
  } catch (e) {
    WMessage.error((e as Error).message || '检验申请列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchList()
}

function handleReset() {
  query.status = ''
  pageNo.value = 1
  fetchList()
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

async function handleExecute(row: ExamApplicationVO) {
  try {
    await WMessageBox.confirm(`确认执行登记检验申请 #${row.id}「${row.examItemName || '-'}」吗？`, '执行登记', {
      type: 'warning',
      confirmButtonText: '确认执行',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  executingId.value = row.id
  try {
    await executeLabApi(row.id, { operatorName: userStore.realName })
    WMessage.success('执行登记成功')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '执行登记失败')
  } finally {
    executingId.value = null
  }
}

function openReport(row: ExamApplicationVO) {
  reportOrder.value = row
  Object.assign(reportForm, { reportDesc: '', reportResult: '', status: 'PUBLISHED' })
  reportVisible.value = true
}

async function submitReport() {
  if (!reportOrder.value) return
  if (!reportForm.reportResult.trim()) {
    WMessage.warning('请填写检验结果')
    return
  }
  const form = new FormData()
  form.append('applicationId', String(reportOrder.value.id))
  form.append('reportDesc', reportForm.reportDesc)
  form.append('reportResult', reportForm.reportResult)
  form.append('status', reportForm.status)
  reportLoading.value = true
  try {
    await createExamReportApi(form)
    WMessage.success('检验结果录入成功')
    reportVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '检验结果录入失败')
  } finally {
    reportLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">检验技师工作台</h2>
      <p class="page-subtitle">检验申请列表（LAB）· 执行登记 · 结果录入</p>
    </div>

    <!-- 统计条 -->
    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-card__icon stat-card__icon--primary"><Scan /></span>
        <div class="stat-card__body">
          <span class="stat-card__label">检验申请</span>
          <span class="stat-card__value">{{ total.toLocaleString() }}</span>
          <span class="stat-card__extra">当前筛选结果总数</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="stat-card__icon stat-card__icon--warning"><File /></span>
        <div class="stat-card__body">
          <span class="stat-card__label">待执行</span>
          <span class="stat-card__value">{{ pendingCount.toLocaleString() }}</span>
          <span class="stat-card__extra">本页待执行申请</span>
        </div>
      </div>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form :model="query" inline>
        <w-form-item label="申请状态">
          <w-select v-model="query.status" clearable placeholder="全部状态" style="width: 180px" @change="handleSearch">
            <w-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </w-select>
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :icon="Search" @click="handleSearch">查询</w-button>
          <w-button :icon="Refresh" @click="handleReset">重置</w-button>
        </w-form-item>
      </w-form>
    </w-card>

    <!-- 列表区 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-title"><Scan class="toolbar-title__icon" />检验申请列表</span>
          <w-tag size="small" effect="light" type="primary">共 {{ total }} 条</w-tag>
        </div>
        <div class="table-toolbar__right">
          <w-button :icon="Refresh" @click="fetchList">刷新</w-button>
        </div>
      </div>

      <div v-loading="loading" class="table-wrap">
        <w-table :data="list" border stripe row-key="id" size="default" empty-text="暂无检验申请">
          <w-table-column type="index" label="#" width="60" align="center" />
          <w-table-column prop="id" label="申请ID" width="90" align="center" />
          <w-table-column prop="patientName" label="患者" min-width="100" show-overflow-tooltip />
          <w-table-column prop="examItemName" label="检验项目" min-width="150" show-overflow-tooltip />
          <w-table-column prop="execDept" label="执行科室" min-width="110" show-overflow-tooltip />
          <w-table-column prop="applyDoctorName" label="申请医生" min-width="100" show-overflow-tooltip />
          <w-table-column label="缴费状态" width="100" align="center">
            <template #default="{ row }">
              <w-tag size="small" effect="light" :type="payStatusMeta(row.payStatus).tone">
                {{ payStatusMeta(row.payStatus).text }}
              </w-tag>
            </template>
          </w-table-column>
          <w-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <w-tag size="small" effect="light" :type="statusMeta(row.status).tone">{{ statusMeta(row.status).text }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column prop="createTime" label="申请时间" min-width="160" show-overflow-tooltip />
          <w-table-column label="操作" width="180" fixed="right" align="center">
            <template #default="{ row }">
              <w-button
                link
                type="primary"
                :icon="Check"
                :loading="executingId === row.id"
                :disabled="row.status === 'COMPLETED' || row.status === 'FINISHED'"
                @click="handleExecute(row)"
              >执行登记</w-button>
              <w-button link type="primary" :icon="File" @click="openReport(row)">录入结果</w-button>
            </template>
          </w-table-column>
        </w-table>
      </div>

      <div class="pager">
        <w-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchList"
          @size-change="handleSizeChange"
        />
      </div>
    </w-card>

    <!-- 结果录入弹窗 -->
    <w-dialog v-model="reportVisible" title="检验结果录入" width="560px" :close-on-click-modal="false" destroy-on-close>
      <template v-if="reportOrder">
        <div class="order-summary">
          <span class="order-summary__label">申请 #{{ reportOrder.id }}</span>
          <span class="order-summary__drug">{{ reportOrder.examItemName || '-' }}</span>
          <span class="order-summary__patient">{{ reportOrder.patientName || '-' }}</span>
        </div>
        <w-form :model="reportForm" label-width="88px">
          <w-form-item label="报告描述">
            <w-input v-model="reportForm.reportDesc" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入报告描述" />
          </w-form-item>
          <w-form-item label="检验结果" required>
            <w-input v-model="reportForm.reportResult" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入检验结果" />
          </w-form-item>
          <w-form-item label="报告状态">
            <w-radio-group v-model="reportForm.status">
              <w-radio value="DRAFT">草稿</w-radio>
              <w-radio value="PUBLISHED">发布</w-radio>
            </w-radio-group>
          </w-form-item>
        </w-form>
      </template>
      <template #footer>
        <w-button @click="reportVisible = false">取消</w-button>
        <w-button type="primary" :loading="reportLoading" :icon="Search" @click="submitReport">提交结果</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
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
.table-wrap {
  min-height: 200px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.order-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: var(--hospital-radius-md);
  background: var(--w3-color-primary-plain);
}
.order-summary__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--w3-color-primary);
}
.order-summary__drug {
  font-size: 13px;
  color: var(--hospital-text-main);
}
.order-summary__patient {
  font-size: 12px;
  color: var(--hospital-text-second);
}
</style>
