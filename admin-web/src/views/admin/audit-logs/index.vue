<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Filter, Refresh, Search, User } from '@win-design-next/icons-vue'
import { getAuditLogsApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { AuditLog } from '@/types'

const userStore = useUserStore()

const loading = ref(false)
const list = ref<AuditLog[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

const query = reactive({
  userId: '',
  operationType: '',
  startTime: '',
  endTime: '',
})

function buildParams(): Record<string, unknown> {
  const params: Record<string, unknown> = {
    pageNo: pageNo.value,
    pageSize: pageSize.value,
    operationType: query.operationType.trim() || undefined,
    startTime: query.startTime || undefined,
    endTime: query.endTime || undefined,
  }
  const userId = query.userId.trim()
  const userIdNum = Number(userId)
  if (userId !== '' && Number.isFinite(userIdNum)) {
    params.userId = userIdNum
  }
  return params
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getAuditLogsApi(buildParams())
    list.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '审计日志加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchList()
}

function handleReset() {
  query.userId = ''
  query.operationType = ''
  query.startTime = ''
  query.endTime = ''
  handleSearch()
}

function handlePageChange() {
  fetchList()
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ')
}

function operatorName(row: AuditLog) {
  if (row.username) return row.username
  return row.userId != null ? `用户#${row.userId}` : '—'
}

function formatCost(ms?: number) {
  return ms == null ? '—' : `${ms} ms`
}

function costTone(ms?: number) {
  if (ms == null) return 'default'
  if (ms < 300) return 'success'
  if (ms < 1000) return 'warning'
  return 'danger'
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">审计日志</h2>
      <p class="page-subtitle">系统操作审计记录查询，仅管理员可见</p>
    </div>

    <template v-if="userStore.isAdmin">
      <!-- 筛选区 -->
      <w-card shadow="never" class="hospital-card">
        <template #header>
          <div class="card-head">
            <Filter class="card-head__icon" />
            <span class="card-head__title">筛选条件</span>
          </div>
        </template>
        <w-form :model="query" inline label-position="right">
          <w-form-item label="操作人">
            <w-input v-model="query.userId" :prefix-icon="User" placeholder="操作人 ID" clearable style="width: 160px" />
          </w-form-item>
          <w-form-item label="操作类型">
            <w-input v-model="query.operationType" placeholder="类型（模糊匹配）" clearable style="width: 190px" />
          </w-form-item>
          <w-form-item label="时间范围">
            <div class="range-group">
              <w-date-picker-pro v-model="query.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="开始时间" style="width: 190px" />
              <span class="range-sep">至</span>
              <w-date-picker-pro v-model="query.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="结束时间" style="width: 190px" />
            </div>
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :icon="Search" @click="handleSearch">查询</w-button>
            <w-button :icon="Refresh" @click="handleReset">重置</w-button>
          </w-form-item>
        </w-form>
      </w-card>

      <!-- 表格区 -->
      <w-card shadow="never" class="hospital-card">
        <div class="table-toolbar">
          <div class="table-toolbar__left">
            <span class="toolbar-title">日志记录</span>
            <w-tag size="small" effect="light" type="info">共 {{ total }} 条</w-tag>
          </div>
          <div class="table-toolbar__right">
            <w-button :icon="Refresh" :loading="loading" @click="handlePageChange">刷新</w-button>
          </div>
        </div>
        <w-table :data="list" row-key="id" border stripe :loading="loading" empty-text="暂无审计日志" size="default">
          <w-table-column label="时间" width="175">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </w-table-column>
          <w-table-column label="操作人" min-width="110">
            <template #default="{ row }">{{ operatorName(row) }}</template>
          </w-table-column>
          <w-table-column label="类型" min-width="160">
            <template #default="{ row }">
              <div class="op-cell">
                <w-tag v-if="row.httpMethod" size="small" effect="plain" type="info">{{ row.httpMethod }}</w-tag>
                <span class="op-text">{{ row.operation || '—' }}</span>
              </div>
            </template>
          </w-table-column>
          <w-table-column prop="requestUri" label="路径" min-width="220" show-overflow-tooltip />
          <w-table-column prop="requestIp" label="IP" width="130" />
          <w-table-column label="耗时" width="110" align="right">
            <template #default="{ row }">
              <span class="cost" :class="`cost--${costTone(row.executionTime)}`">{{ formatCost(row.executionTime) }}</span>
            </template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tooltip v-if="row.status !== 1 && row.errorMessage" :content="row.errorMessage" placement="top">
                <w-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</w-tag>
              </w-tooltip>
              <w-tag v-else size="small" :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</w-tag>
            </template>
          </w-table-column>
        </w-table>
        <div class="pager">
          <w-pagination
            v-model:current-page="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </w-card>
    </template>

    <w-result v-else icon="info" title="无访问权限" sub-title="审计日志仅对管理员开放" />
  </div>
</template>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-head__icon {
  color: var(--w3-color-primary);
  font-size: 16px;
}
.card-head__title {
  font-size: 15px;
  font-weight: 600;
}
.range-group {
  display: inline-flex;
  align-items: center;
}
.range-sep {
  margin: 0 8px;
  color: var(--w3-font-color-third);
}
.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--w3-font-color-normal);
}
.table-toolbar {
  margin-bottom: 14px;
}
.op-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.op-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cost {
  font-variant-numeric: tabular-nums;
}
.cost--success {
  color: var(--w3-color-success);
}
.cost--warning {
  color: var(--w3-color-warning);
}
.cost--danger {
  color: var(--w3-color-danger);
}
.cost--default {
  color: var(--w3-font-color-second);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
