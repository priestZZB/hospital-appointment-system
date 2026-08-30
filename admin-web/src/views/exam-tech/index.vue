<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { WMessage, WMessageBox } from 'win-design-next'
import { Check, File, Refresh, Scan, Search } from '@win-design-next/icons-vue'
import { executeExamApplicationApi, getExamApplicationListApi } from '@/api/medsupply'
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

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待执行' },
  { value: 'EXECUTING', label: '执行中' },
  { value: 'COMPLETED', label: '已完成' },
]

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const list = ref<ExamApplicationVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const query = reactive({ status: '' })
const executingId = ref<number | null>(null)

const pendingCount = computed(() => list.value.filter((a) => a.status === 'PENDING').length)

function statusMeta(status?: string): StatusMeta {
  return APPLICATION_STATUS_META[status ?? ''] ?? { text: status || '未知', tone: 'info' }
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getExamApplicationListApi({
      status: query.status || undefined,
      offset: (pageNo.value - 1) * pageSize.value,
      limit: pageSize.value,
    })
    if (Array.isArray(page)) {
      list.value = page
      total.value = page.length
    } else {
      const res = page as unknown as { records?: ExamApplicationVO[]; list?: ExamApplicationVO[]; total?: number }
      const records = res.records ?? res.list ?? []
      list.value = records
      total.value = res.total ?? records.length
    }
  } catch (e) {
    WMessage.error((e as Error).message || '检查申请列表加载失败')
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
    await WMessageBox.confirm(`确认执行登记检查申请 #${row.id}「${row.examItemName || '-'}」吗？`, '执行登记', {
      type: 'warning',
      confirmButtonText: '确认执行',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  executingId.value = row.id
  try {
    await executeExamApplicationApi(row.id, { operatorName: userStore.realName })
    WMessage.success('执行登记成功')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '执行登记失败')
  } finally {
    executingId.value = null
  }
}

function goReportEntry() {
  router.push('/exam')
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">检查技师工作台</h2>
      <p class="page-subtitle">检查申请列表 · 执行登记 · 报告录入入口</p>
    </div>

    <!-- 统计条 -->
    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-card__icon stat-card__icon--primary"><Scan /></span>
        <div class="stat-card__body">
          <span class="stat-card__label">检查申请</span>
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
          <span class="toolbar-title"><Scan class="toolbar-title__icon" />检查申请列表</span>
          <w-tag size="small" effect="light" type="primary">共 {{ total }} 条</w-tag>
        </div>
        <div class="table-toolbar__right">
          <w-button :icon="File" @click="goReportEntry">报告录入</w-button>
          <w-button :icon="Refresh" @click="fetchList">刷新</w-button>
        </div>
      </div>

      <div v-loading="loading" class="table-wrap">
        <w-table :data="list" border stripe row-key="id" size="default">
          <w-table-column type="index" label="#" width="60" align="center" />
          <w-table-column prop="id" label="申请ID" width="90" align="center" />
          <w-table-column prop="patientName" label="患者" min-width="100" show-overflow-tooltip />
          <w-table-column prop="examItemName" label="检查项目" min-width="150" show-overflow-tooltip />
          <w-table-column prop="execDept" label="执行科室" min-width="110" show-overflow-tooltip />
          <w-table-column prop="applyDoctorName" label="申请医生" min-width="100" show-overflow-tooltip />
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
              <w-button link type="primary" @click="goReportEntry">报告</w-button>
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
</style>
