<script setup lang="ts">
/**
 * 上报登记页（迭代12 I2，/report-form，base=/api/inpatient/report-form）。
 * 传染病（INFECTIOUS）/不良事件（ADVERSE_EVENT）上报 + 审核（SUBMITTED→REVIEWED）。
 */
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Plus, RefreshLeft, Search } from '@win-design-next/icons-vue'
import { createReportFormApi, getReportFormListApi, reviewReportFormApi } from '@/api/record'
import type { ReportForm } from '@/types'

function typeText(v?: string): string {
  return v === 'INFECTIOUS' ? '传染病' : v === 'ADVERSE_EVENT' ? '不良事件' : v || '-'
}
function typeTag(v?: string): 'danger' | 'warning' {
  return v === 'INFECTIOUS' ? 'danger' : 'warning'
}
function statusMeta(v?: string): { label: string; tag: 'primary' | 'success' | 'info' } {
  const k = (v || '').toUpperCase()
  if (k === 'REVIEWED') return { label: '已审核', tag: 'success' }
  if (k === 'DRAFT') return { label: '草稿', tag: 'info' }
  return { label: '待审核', tag: 'primary' }
}

/* ================= 查询 ================= */
const loading = ref(false)
const list = ref<ReportForm[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const query = reactive<{ reportType: string; status: string }>({ reportType: '', status: '' })

function buildParams(): Record<string, unknown> {
  const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value }
  if (query.reportType) params.reportType = query.reportType
  if (query.status) params.status = query.status
  return params
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getReportFormListApi(buildParams())
    list.value = Array.isArray(page?.records) ? page.records : []
    total.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '上报记录加载失败')
  } finally {
    loading.value = false
  }
}
function handleSearch() {
  pageNo.value = 1
  fetchList()
}
function handleReset() {
  query.reportType = ''
  query.status = ''
  pageNo.value = 1
  fetchList()
}

/* ================= 新建 ================= */
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<{
  reportType: string
  patientId: number | null
  patientName: string
  eventName: string
  eventTime: string
  occurDepartment: string
  content: string
  reporterId: number | null
  reporterName: string
}>({
  reportType: 'INFECTIOUS',
  patientId: null,
  patientName: '',
  eventName: '',
  eventTime: '',
  occurDepartment: '',
  content: '',
  reporterId: null,
  reporterName: '',
})

function eventNamePlaceholder(): string {
  return form.reportType === 'INFECTIOUS' ? '病名，如：肺结核' : '事件简述，如：用药错误'
}

function openCreate() {
  form.reportType = 'INFECTIOUS'
  form.patientId = null
  form.patientName = ''
  form.eventName = ''
  form.eventTime = ''
  form.occurDepartment = ''
  form.content = ''
  form.reporterId = null
  form.reporterName = ''
  dialogVisible.value = true
}

async function submitCreate() {
  if (!form.patientId) {
    WMessage.warning('请填写患者 ID')
    return
  }
  if (!form.eventName.trim()) {
    WMessage.warning('请填写事件名称')
    return
  }
  saving.value = true
  try {
    await createReportFormApi({
      reportType: form.reportType,
      patientId: form.patientId,
      patientName: form.patientName.trim() || undefined,
      eventName: form.eventName.trim(),
      eventTime: form.eventTime || undefined,
      occurDepartment: form.occurDepartment.trim() || undefined,
      content: form.content.trim() || undefined,
      reporterId: form.reporterId ?? undefined,
      reporterName: form.reporterName.trim() || undefined,
    })
    WMessage.success('上报已提交')
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    saving.value = false
  }
}

/* ================= 审核 ================= */
const reviewVisible = ref(false)
const reviewId = ref<number | null>(null)
const reviewNote = ref('')
const reviewing = ref(false)

function openReview(row: ReportForm) {
  if (!row.id) return
  reviewId.value = row.id
  reviewNote.value = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (reviewId.value == null) return
  reviewing.value = true
  try {
    await reviewReportFormApi(reviewId.value, reviewNote.value.trim())
    WMessage.success('审核完成')
    reviewVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '审核失败')
  } finally {
    reviewing.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">上报登记</h2>
      <p class="page-subtitle">传染病 / 不良事件上报登记与审核（I2）</p>
    </div>

    <w-card shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="类型">
          <w-select v-model="query.reportType" placeholder="全部类型" clearable style="width: 150px">
            <w-option label="传染病" value="INFECTIOUS" />
            <w-option label="不良事件" value="ADVERSE_EVENT" />
          </w-select>
        </w-form-item>
        <w-form-item label="状态">
          <w-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
            <w-option label="待审核" value="SUBMITTED" />
            <w-option label="已审核" value="REVIEWED" />
            <w-option label="草稿" value="DRAFT" />
          </w-select>
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</w-button>
          <w-button :icon="RefreshLeft" @click="handleReset">重置</w-button>
        </w-form-item>
      </w-form>
    </w-card>

    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar list-toolbar">
        <div class="table-toolbar__left"><span class="table-total">共 {{ total }} 条上报</span></div>
        <div class="table-toolbar__right">
          <w-button type="primary" :icon="Plus" @click="openCreate">新建上报</w-button>
        </div>
      </div>

      <w-table :data="list" row-key="id" border stripe :loading="loading" empty-text="暂无上报记录" size="default">
        <w-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <w-tag :type="typeTag(row.reportType)" effect="light">{{ typeText(row.reportType) }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column label="事件名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.eventName || '-' }}</template>
        </w-table-column>
        <w-table-column label="患者" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.patientName || (row.patientId ? `#${row.patientId}` : '-') }}</template>
        </w-table-column>
        <w-table-column label="发生科室" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.occurDepartment || '-' }}</template>
        </w-table-column>
        <w-table-column label="上报人" width="110">
          <template #default="{ row }">{{ row.reporterName || '-' }}</template>
        </w-table-column>
        <w-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <w-tag :type="statusMeta(row.status).tag" effect="light">{{ statusMeta(row.status).label }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column label="审核备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewNote || '-' }}</template>
        </w-table-column>
        <w-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <w-button
              v-if="(row.status || '').toUpperCase() === 'SUBMITTED'"
              type="primary"
              link
              @click="openReview(row)"
            >
              审核
            </w-button>
            <span v-else>-</span>
          </template>
        </w-table-column>
      </w-table>

      <w-pagination
        class="pager"
        :current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, sizes"
        @current-change="(p: number) => { pageNo = p; fetchList() }"
        @size-change="(s: number) => { pageSize = s; pageNo = 1; fetchList() }"
      />
    </w-card>

    <!-- 新建上报弹窗 -->
    <w-dialog v-model="dialogVisible" title="新建上报" width="560px" destroy-on-close>
      <w-form label-width="92px">
        <w-form-item label="上报类型">
          <w-radio-group v-model="form.reportType">
            <w-radio value="INFECTIOUS">传染病</w-radio>
            <w-radio value="ADVERSE_EVENT">不良事件</w-radio>
          </w-radio-group>
        </w-form-item>
        <w-form-item label="患者 ID" required>
          <w-input-number v-model="form.patientId" :min="1" style="width: 100%" placeholder="患者主键" />
        </w-form-item>
        <w-form-item label="患者姓名">
          <w-input v-model="form.patientName" placeholder="选填" />
        </w-form-item>
        <w-form-item label="事件名称" required>
          <w-input v-model="form.eventName" :placeholder="eventNamePlaceholder()" />
        </w-form-item>
        <w-form-item label="发生时间">
          <w-date-picker v-model="form.eventTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </w-form-item>
        <w-form-item label="发生科室">
          <w-input v-model="form.occurDepartment" placeholder="如：呼吸内科" />
        </w-form-item>
        <w-form-item label="详情">
          <w-input v-model="form.content" type="textarea" :rows="3" placeholder="事件经过/初步处置" />
        </w-form-item>
        <w-form-item label="上报人">
          <div class="reporter-row">
            <w-input-number v-model="form.reporterId" :min="1" placeholder="ID" style="width: 130px" />
            <w-input v-model="form.reporterName" placeholder="姓名" style="flex: 1" />
          </div>
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="dialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="saving" @click="submitCreate">提交上报</w-button>
      </template>
    </w-dialog>

    <!-- 审核弹窗 -->
    <w-dialog v-model="reviewVisible" title="上报审核" width="460px" destroy-on-close>
      <w-form label-width="80px">
        <w-form-item label="审核备注">
          <w-input v-model="reviewNote" type="textarea" :rows="3" placeholder="复核意见（选填）" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="reviewVisible = false">取消</w-button>
        <w-button type="primary" :loading="reviewing" @click="submitReview">确认审核</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.reporter-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
