<script setup lang="ts">
/**
 * 病案与临床路径页（迭代12 I3/J1，/medical-record）。
 * Tab1 病案管理（登记/归档/借出/归还+详情抽屉含借阅历史）；Tab2 借阅台账；Tab3 临床路径（模板/入径/推进/变异/退出/完成）。
 */
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Plus, RefreshLeft, Search } from '@win-design-next/icons-vue'
import {
  advancePatientPathApi,
  archiveMedicalRecordApi,
  borrowMedicalRecordApi,
  completePatientPathApi,
  createMedicalRecordApi,
  createPathTemplateApi,
  enterPatientPathApi,
  exitPatientPathApi,
  getBorrowListApi,
  getMedicalRecordApi,
  getMedicalRecordListApi,
  getPatientPathApi,
  getPatientPathListApi,
  getPathTemplateListApi,
  returnMedicalRecordApi,
  varyPatientPathApi,
} from '@/api/record'
import type { RecordBorrow } from '@/types'

/* ================= 通用 ================= */
function archiveMeta(v?: string): { label: string; tag: 'success' | 'info' } {
  return (v || '').toUpperCase() === 'ARCHIVED' ? { label: '已归档', tag: 'success' } : { label: '病区未归', tag: 'info' }
}
function pathStatusMeta(v?: string): { label: string; tag: 'success' | 'warning' | 'danger' | 'primary' } {
  const k = (v || '').toUpperCase()
  if (k === 'COMPLETED') return { label: '已完成', tag: 'primary' }
  if (k === 'VARIATION') return { label: '变异', tag: 'warning' }
  if (k === 'EXITED') return { label: '已退出', tag: 'danger' }
  return { label: '在径', tag: 'success' }
}
function borrowMeta(v?: string): { label: string; tag: 'warning' | 'success' } {
  return (v || '').toUpperCase() === 'RETURNED' ? { label: '已归还', tag: 'success' } : { label: '借出中', tag: 'warning' }
}
function fmt(v?: unknown): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}
function money(v?: number | string | null): string {
  if (v == null) return '-'
  return `¥${Number(v).toFixed(2)}`
}

/* ================= Tab1 病案管理 ================= */
const activeTab = ref<'record' | 'borrow' | 'path'>('record')
const loading = ref(false)
const list = ref<Record<string, unknown>[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const query = reactive<{ archiveStatus: string; keyword: string }>({ archiveStatus: '', keyword: '' })

function buildRecordParams(): Record<string, unknown> {
  const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value }
  if (query.archiveStatus) params.archiveStatus = query.archiveStatus
  const kw = query.keyword.trim()
  if (kw) params.keyword = kw
  return params
}
async function fetchRecords() {
  loading.value = true
  try {
    const page = await getMedicalRecordListApi(buildRecordParams())
    list.value = Array.isArray(page?.records) ? page.records : []
    total.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '病案加载失败')
  } finally {
    loading.value = false
  }
}

function rowId(row: Record<string, unknown>): number {
  return Number(row.id || 0)
}

/* 登记弹窗 */
const createVisible = ref(false)
const saving = ref(false)
const createForm = reactive<{ patientId: number | null; patientName: string; admissionId: number | null; diagnosis: string }>({
  patientId: null,
  patientName: '',
  admissionId: null,
  diagnosis: '',
})
function openCreate() {
  createForm.patientId = null
  createForm.patientName = ''
  createForm.admissionId = null
  createForm.diagnosis = ''
  createVisible.value = true
}
async function submitCreate() {
  if (!createForm.patientId) {
    WMessage.warning('请填写患者 ID')
    return
  }
  saving.value = true
  try {
    await createMedicalRecordApi({
      patientId: createForm.patientId,
      patientName: createForm.patientName.trim() || undefined,
      admissionId: createForm.admissionId ?? undefined,
      diagnosis: createForm.diagnosis.trim() || undefined,
    })
    WMessage.success('病案已登记')
    createVisible.value = false
    fetchRecords()
  } catch (e) {
    WMessage.error((e as Error).message || '登记失败')
  } finally {
    saving.value = false
  }
}

async function handleArchive(row: Record<string, unknown>) {
  try {
    await archiveMedicalRecordApi(rowId(row))
    WMessage.success('归档完成')
    fetchRecords()
  } catch (e) {
    WMessage.error((e as Error).message || '归档失败')
  }
}

/* 借阅弹窗 */
const borrowVisible = ref(false)
const borrowTarget = ref<Record<string, unknown> | null>(null)
const borrowing = ref(false)
const borrowForm = reactive<{ borrowerId: number | null; borrowerName: string; purpose: string; expectReturnTime: string }>({
  borrowerId: null,
  borrowerName: '',
  purpose: '',
  expectReturnTime: '',
})
function openBorrow(row: Record<string, unknown>) {
  borrowTarget.value = row
  borrowForm.borrowerId = null
  borrowForm.borrowerName = ''
  borrowForm.purpose = ''
  borrowForm.expectReturnTime = ''
  borrowVisible.value = true
}
async function submitBorrow() {
  if (!borrowTarget.value) return
  if (!borrowForm.borrowerName.trim()) {
    WMessage.warning('请填写借阅人姓名')
    return
  }
  borrowing.value = true
  try {
    await borrowMedicalRecordApi(rowId(borrowTarget.value), {
      borrowerId: borrowForm.borrowerId ?? undefined,
      borrowerName: borrowForm.borrowerName.trim(),
      purpose: borrowForm.purpose.trim() || undefined,
      expectReturnTime: borrowForm.expectReturnTime || undefined,
    })
    WMessage.success('借出成功')
    borrowVisible.value = false
    fetchRecords()
  } catch (e) {
    WMessage.error((e as Error).message || '借出失败')
  } finally {
    borrowing.value = false
  }
}

/* 详情抽屉 */
const detailVisible = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const detailBorrows = ref<RecordBorrow[]>([])
async function openDetail(row: Record<string, unknown>) {
  try {
    const data = await getMedicalRecordApi(rowId(row))
    detail.value = (data?.record as Record<string, unknown>) || row
    detailBorrows.value = Array.isArray(data?.borrows) ? data.borrows : []
    detailVisible.value = true
  } catch (e) {
    WMessage.error((e as Error).message || '详情加载失败')
  }
}
async function handleReturn(b: RecordBorrow) {
  if (!b.id || !detail.value) return
  try {
    await returnMedicalRecordApi(rowId(detail.value), b.id)
    WMessage.success('归还完成')
    detailVisible.value = false
    fetchRecords()
  } catch (e) {
    WMessage.error((e as Error).message || '归还失败')
  }
}

/* ================= Tab2 借阅台账 ================= */
const borrowLoading = ref(false)
const borrowList = ref<RecordBorrow[]>([])
const borrowTotal = ref(0)
const borrowPageNo = ref(1)
const borrowStatus = ref('')
async function fetchBorrows() {
  borrowLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: borrowPageNo.value, pageSize: 20 }
    if (borrowStatus.value) params.status = borrowStatus.value
    const page = await getBorrowListApi(params)
    borrowList.value = Array.isArray(page?.records) ? page.records : []
    borrowTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '借阅台账加载失败')
  } finally {
    borrowLoading.value = false
  }
}

/* ================= Tab3 临床路径 ================= */
const templates = ref<Record<string, unknown>[]>([])
const templateLoading = ref(false)
const paths = ref<Record<string, unknown>[]>([])
const pathLoading = ref(false)
const pathTotal = ref(0)
const pathPageNo = ref(1)
const pathQuery = reactive<{ admissionId: number | null; status: string }>({ admissionId: null, status: '' })

const templateVisible = ref(false)
const templateSaving = ref(false)
const templateForm = reactive<{ pathCode: string; pathName: string; diseaseName: string; standardDays: number | null; totalEstimate: number | null; itemJson: string }>({
  pathCode: '',
  pathName: '',
  diseaseName: '',
  standardDays: 3,
  totalEstimate: null,
  itemJson: '',
})
const enterVisible = ref(false)
const enterForm = reactive<{ templateId: number | null; admissionId: number | null; patientId: number | null }>({
  templateId: null,
  admissionId: null,
  patientId: null,
})

async function fetchTemplates() {
  templateLoading.value = true
  try {
    const page = await getPathTemplateListApi({ pageNo: 1, pageSize: 50 })
    templates.value = Array.isArray(page?.records) ? page.records : []
  } catch (e) {
    WMessage.error((e as Error).message || '路径模板加载失败')
  } finally {
    templateLoading.value = false
  }
}
async function fetchPaths() {
  pathLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: pathPageNo.value, pageSize: 20 }
    if (pathQuery.admissionId) params.admissionId = pathQuery.admissionId
    if (pathQuery.status) params.status = pathQuery.status
    const page = await getPatientPathListApi(params)
    paths.value = Array.isArray(page?.records) ? page.records : []
    pathTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '患者路径加载失败')
  } finally {
    pathLoading.value = false
  }
}
function openTemplate() {
  templateForm.pathCode = ''
  templateForm.pathName = ''
  templateForm.diseaseName = ''
  templateForm.standardDays = 3
  templateForm.totalEstimate = null
  templateForm.itemJson = ''
  templateVisible.value = true
}
async function submitTemplate() {
  if (!templateForm.pathCode.trim() || !templateForm.pathName.trim()) {
    WMessage.warning('请填写路径编码与名称')
    return
  }
  if (templateForm.itemJson.trim()) {
    try {
      JSON.parse(templateForm.itemJson)
    } catch {
      WMessage.warning('阶段医嘱项不是合法 JSON')
      return
    }
  }
  templateSaving.value = true
  try {
    await createPathTemplateApi({
      pathCode: templateForm.pathCode.trim(),
      pathName: templateForm.pathName.trim(),
      diseaseName: templateForm.diseaseName.trim() || undefined,
      standardDays: templateForm.standardDays ?? undefined,
      totalEstimate: templateForm.totalEstimate ?? undefined,
      itemJson: templateForm.itemJson.trim() || undefined,
    })
    WMessage.success('模板已创建')
    templateVisible.value = false
    fetchTemplates()
  } catch (e) {
    WMessage.error((e as Error).message || '创建失败')
  } finally {
    templateSaving.value = false
  }
}
function openEnter() {
  enterForm.templateId = null
  enterForm.admissionId = null
  enterForm.patientId = null
  enterVisible.value = true
}
async function submitEnter() {
  if (!enterForm.templateId || !enterForm.admissionId) {
    WMessage.warning('请选择模板并填写住院记录 ID')
    return
  }
  try {
    await enterPatientPathApi({
      templateId: enterForm.templateId,
      admissionId: enterForm.admissionId,
      patientId: enterForm.patientId ?? undefined,
    })
    WMessage.success('入径成功')
    enterVisible.value = false
    fetchPaths()
  } catch (e) {
    WMessage.error((e as Error).message || '入径失败')
  }
}
async function pathAction(row: Record<string, unknown>, action: 'advance' | 'complete') {
  try {
    if (action === 'advance') {
      await advancePatientPathApi(rowId(row))
      WMessage.success('已推进一天')
    } else {
      await completePatientPathApi(rowId(row))
      WMessage.success('路径已完成')
    }
    fetchPaths()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}
/* 变异/退出：轻量弹窗收集原因 */
const reasonVisible = ref(false)
const reasonActionType = ref<'variation' | 'exit'>('variation')
const reasonTarget = ref<Record<string, unknown> | null>(null)
const reasonText = ref('')
const reasonSaving = ref(false)
function reasonAction(row: Record<string, unknown>, action: 'variation' | 'exit') {
  reasonTarget.value = row
  reasonActionType.value = action
  reasonText.value = ''
  reasonVisible.value = true
}
async function submitReason() {
  if (!reasonTarget.value) return
  if (!reasonText.value.trim()) {
    WMessage.warning(reasonActionType.value === 'variation' ? '请填写变异原因' : '请填写退出原因')
    return
  }
  reasonSaving.value = true
  try {
    if (reasonActionType.value === 'variation') {
      await varyPatientPathApi(rowId(reasonTarget.value), reasonText.value.trim())
      WMessage.success('已登记变异')
    } else {
      await exitPatientPathApi(rowId(reasonTarget.value), reasonText.value.trim())
      WMessage.success('已退出路径')
    }
    reasonVisible.value = false
    fetchPaths()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  } finally {
    reasonSaving.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">病案与临床路径</h2>
      <p class="page-subtitle">病案登记/归档/借阅 · 临床路径入径/变异/完成（I3 / J1）</p>
    </div>

    <w-tabs v-model="activeTab" class="hospital-card">
      <w-tab-pane label="病案管理" name="record" />
      <w-tab-pane label="借阅台账" name="borrow" />
      <w-tab-pane label="临床路径" name="path" />
    </w-tabs>

    <!-- Tab1 病案管理 -->
    <template v-if="activeTab === 'record'">
      <w-card shadow="never" class="hospital-card">
        <w-form inline class="query-form">
          <w-form-item label="归档状态">
            <w-select v-model="query.archiveStatus" placeholder="全部" clearable style="width: 140px">
              <w-option label="病区未归" value="IN_WARD" />
              <w-option label="已归档" value="ARCHIVED" />
            </w-select>
          </w-form-item>
          <w-form-item label="关键字">
            <w-input v-model="query.keyword" placeholder="病案号/患者姓名" clearable style="width: 190px" @keyup.enter="fetchRecords" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :icon="Search" :loading="loading" @click="pageNo = 1; fetchRecords()">查询</w-button>
            <w-button :icon="RefreshLeft" @click="query.archiveStatus = ''; query.keyword = ''; pageNo = 1; fetchRecords()">重置</w-button>
          </w-form-item>
        </w-form>
      </w-card>
      <w-card shadow="never" class="hospital-card">
        <div class="table-toolbar list-toolbar">
          <div class="table-toolbar__left"><span class="table-total">共 {{ total }} 份病案</span></div>
          <div class="table-toolbar__right">
            <w-button type="primary" :icon="Plus" @click="openCreate">病案登记</w-button>
          </div>
        </div>
        <w-table :data="list" row-key="id" border stripe :loading="loading" empty-text="暂无病案" size="default">
          <w-table-column label="病案号" width="150">
            <template #default="{ row }">{{ row.recordNo || row.record_no || '-' }}</template>
          </w-table-column>
          <w-table-column label="患者" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.patientName || row.patient_name || (row.patientId ? `#${row.patientId}` : '-') }}</template>
          </w-table-column>
          <w-table-column label="诊断" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.diagnosis || '-' }}</template>
          </w-table-column>
          <w-table-column label="归档状态" width="100" align="center">
            <template #default="{ row }">
              <w-tag :type="archiveMeta(row.archiveStatus || row.archive_status).tag" effect="light">
                {{ archiveMeta(row.archiveStatus || row.archive_status).label }}
              </w-tag>
            </template>
          </w-table-column>
          <w-table-column label="归档时间" width="150">
            <template #default="{ row }">{{ fmt(row.archiveTime || row.archive_time) }}</template>
          </w-table-column>
          <w-table-column label="操作" width="190" fixed="right" align="center">
            <template #default="{ row }">
              <w-button type="primary" link @click="openDetail(row)">详情</w-button>
              <w-button v-if="(row.archiveStatus || row.archive_status) === 'IN_WARD'" type="warning" link @click="handleArchive(row)">归档</w-button>
              <w-button v-if="(row.archiveStatus || row.archive_status) === 'ARCHIVED'" type="success" link @click="openBorrow(row)">借阅</w-button>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="pageNo"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next, sizes"
          @current-change="(p: number) => { pageNo = p; fetchRecords() }"
          @size-change="(s: number) => { pageSize = s; pageNo = 1; fetchRecords() }"
        />
      </w-card>
    </template>

    <!-- Tab2 借阅台账 -->
    <w-card v-if="activeTab === 'borrow'" shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="状态">
          <w-select v-model="borrowStatus" placeholder="全部" clearable style="width: 140px">
            <w-option label="借出中" value="BORROWED" />
            <w-option label="已归还" value="RETURNED" />
          </w-select>
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :loading="borrowLoading" @click="borrowPageNo = 1; fetchBorrows()">查询</w-button>
        </w-form-item>
      </w-form>
      <w-table :data="borrowList" row-key="id" border stripe :loading="borrowLoading" empty-text="暂无借阅记录" size="default">
        <w-table-column label="借阅单" width="90" align="center">
          <template #default="{ row }">#{{ row.id || '-' }}</template>
        </w-table-column>
        <w-table-column label="病案" width="100" align="center">
          <template #default="{ row }">{{ row.recordId || row.record_id || '-' }}</template>
        </w-table-column>
        <w-table-column label="借阅人" width="120">
          <template #default="{ row }">{{ row.borrowerName || row.borrower_name || '-' }}</template>
        </w-table-column>
        <w-table-column label="用途" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.purpose || '-' }}</template>
        </w-table-column>
        <w-table-column label="借出时间" width="150">
          <template #default="{ row }">{{ fmt(row.borrowTime || row.borrow_time) }}</template>
        </w-table-column>
        <w-table-column label="归还时间" width="150">
          <template #default="{ row }">{{ fmt(row.returnTime || row.return_time) }}</template>
        </w-table-column>
        <w-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <w-tag :type="borrowMeta(row.status).tag" effect="light">{{ borrowMeta(row.status).label }}</w-tag>
          </template>
        </w-table-column>
      </w-table>
      <w-pagination
        class="pager"
        :current-page="borrowPageNo"
        :page-size="20"
        :total="borrowTotal"
        layout="total, prev, pager, next"
        @current-change="(p: number) => { borrowPageNo = p; fetchBorrows() }"
      />
    </w-card>

    <!-- Tab3 临床路径 -->
    <template v-if="activeTab === 'path'">
      <w-card shadow="never" class="hospital-card">
        <template #header>
          <div class="card-head-row">
            <span>路径模板</span>
            <div class="head-actions">
              <w-button size="small" @click="openEnter">患者入径</w-button>
              <w-button size="small" type="primary" :icon="Plus" @click="openTemplate">新增模板</w-button>
            </div>
          </div>
        </template>
        <w-table :data="templates" row-key="id" border stripe :loading="templateLoading" empty-text="暂无路径模板" size="small">
          <w-table-column label="编码" width="120">
            <template #default="{ row }">{{ row.pathCode || row.path_code || '-' }}</template>
          </w-table-column>
          <w-table-column label="路径名称" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.pathName || row.path_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="病种" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.diseaseName || row.disease_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="标准住院日" width="100" align="center">
            <template #default="{ row }">{{ row.standardDays || row.standard_days || '-' }}</template>
          </w-table-column>
          <w-table-column label="费用目标" width="120" align="right">
            <template #default="{ row }">{{ money(row.totalEstimate ?? row.total_estimate) }}</template>
          </w-table-column>
        </w-table>
      </w-card>

      <w-card shadow="never" class="hospital-card">
        <template #header>
          <div class="card-head-row">
            <span>患者路径</span>
            <div class="head-actions">
              <w-input-number v-model="pathQuery.admissionId" :min="1" placeholder="住院记录ID" style="width: 150px" />
              <w-button size="small" type="primary" @click="pathPageNo = 1; fetchPaths()">查询</w-button>
            </div>
          </div>
        </template>
        <w-table :data="paths" row-key="id" border stripe :loading="pathLoading" empty-text="暂无患者路径" size="small">
          <w-table-column label="路径" width="90" align="center">
            <template #default="{ row }">#{{ row.id || '-' }}</template>
          </w-table-column>
          <w-table-column label="模板" width="90" align="center">
            <template #default="{ row }">{{ row.templateId || row.template_id || '-' }}</template>
          </w-table-column>
          <w-table-column label="住院记录" width="100" align="center">
            <template #default="{ row }">{{ row.admissionId || row.admission_id || '-' }}</template>
          </w-table-column>
          <w-table-column label="当前天数" width="90" align="center">
            <template #default="{ row }">{{ row.currentDay ?? row.current_day ?? '-' }}</template>
          </w-table-column>
          <w-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <w-tag :type="pathStatusMeta(row.status).tag" effect="light">{{ pathStatusMeta(row.status).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="入径时间" width="150">
            <template #default="{ row }">{{ fmt(row.enterTime || row.enter_time) }}</template>
          </w-table-column>
          <w-table-column label="变异/退出原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.variationReason || row.variation_reason || '-' }}</template>
          </w-table-column>
          <w-table-column label="操作" width="210" fixed="right" align="center">
            <template #default="{ row }">
              <template v-if="(row.status || '').toUpperCase() === 'IN_PATH'">
                <w-button type="primary" link @click="pathAction(row, 'advance')">推进</w-button>
                <w-button type="warning" link @click="reasonAction(row, 'variation')">变异</w-button>
                <w-button type="danger" link @click="reasonAction(row, 'exit')">退出</w-button>
                <w-button type="success" link @click="pathAction(row, 'complete')">完成</w-button>
              </template>
              <span v-else>-</span>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="pathPageNo"
          :page-size="20"
          :total="pathTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { pathPageNo = p; fetchPaths() }"
        />
      </w-card>
    </template>

    <!-- 病案登记弹窗 -->
    <w-dialog v-model="createVisible" title="病案登记" width="480px" destroy-on-close>
      <w-form label-width="90px">
        <w-form-item label="患者 ID" required>
          <w-input-number v-model="createForm.patientId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="患者姓名">
          <w-input v-model="createForm.patientName" placeholder="选填" />
        </w-form-item>
        <w-form-item label="住院记录 ID">
          <w-input-number v-model="createForm.admissionId" :min="1" style="width: 100%" placeholder="门诊病案可留空" />
        </w-form-item>
        <w-form-item label="诊断">
          <w-input v-model="createForm.diagnosis" type="textarea" :rows="2" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="createVisible = false">取消</w-button>
        <w-button type="primary" :loading="saving" @click="submitCreate">登记</w-button>
      </template>
    </w-dialog>

    <!-- 借阅弹窗 -->
    <w-dialog v-model="borrowVisible" title="病案借阅" width="480px" destroy-on-close>
      <w-form label-width="90px">
        <w-form-item label="借阅人 ID">
          <w-input-number v-model="borrowForm.borrowerId" :min="1" style="width: 100%" placeholder="选填" />
        </w-form-item>
        <w-form-item label="借阅人" required>
          <w-input v-model="borrowForm.borrowerName" placeholder="姓名" />
        </w-form-item>
        <w-form-item label="借阅用途">
          <w-input v-model="borrowForm.purpose" type="textarea" :rows="2" />
        </w-form-item>
        <w-form-item label="预计归还">
          <w-date-picker v-model="borrowForm.expectReturnTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="borrowVisible = false">取消</w-button>
        <w-button type="primary" :loading="borrowing" @click="submitBorrow">借出</w-button>
      </template>
    </w-dialog>

    <!-- 病案详情抽屉 -->
    <w-drawer v-model="detailVisible" title="病案详情" size="520px" destroy-on-close>
      <template v-if="detail">
        <w-descriptions :column="1" border size="small">
          <w-descriptions-item label="病案号">{{ detail.recordNo || detail.record_no || '-' }}</w-descriptions-item>
          <w-descriptions-item label="患者">{{ detail.patientName || detail.patient_name || '-' }}</w-descriptions-item>
          <w-descriptions-item label="诊断">{{ detail.diagnosis || '-' }}</w-descriptions-item>
          <w-descriptions-item label="归档状态">
            {{ archiveMeta(String(detail.archiveStatus ?? detail.archive_status ?? '')).label }}
          </w-descriptions-item>
          <w-descriptions-item label="登记时间">{{ fmt(detail.createTime || detail.create_time) }}</w-descriptions-item>
        </w-descriptions>
        <h4 class="sub-title">借阅历史</h4>
        <w-table :data="detailBorrows" row-key="id" border size="small" empty-text="暂无借阅记录">
          <w-table-column label="借阅人" width="100">
            <template #default="{ row }">{{ row.borrowerName || row.borrower_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="借出时间" width="140">
            <template #default="{ row }">{{ fmt(row.borrowTime || row.borrow_time) }}</template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="borrowMeta(row.status).tag" effect="light">{{ borrowMeta(row.status).label }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <w-button v-if="(row.status || '').toUpperCase() === 'BORROWED'" type="primary" link @click="handleReturn(row)">归还</w-button>
              <span v-else>-</span>
            </template>
          </w-table-column>
        </w-table>
      </template>
    </w-drawer>

    <!-- 新增模板弹窗 -->
    <w-dialog v-model="templateVisible" title="新增路径模板" width="560px" destroy-on-close>
      <w-form label-width="92px">
        <w-form-item label="路径编码" required>
          <w-input v-model="templateForm.pathCode" placeholder="如 CP12A001" />
        </w-form-item>
        <w-form-item label="路径名称" required>
          <w-input v-model="templateForm.pathName" />
        </w-form-item>
        <w-form-item label="适用病种">
          <w-input v-model="templateForm.diseaseName" />
        </w-form-item>
        <w-form-item label="标准住院日">
          <w-input-number v-model="templateForm.standardDays" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="费用目标">
          <w-input-number v-model="templateForm.totalEstimate" :min="0" :precision="2" style="width: 100%" />
        </w-form-item>
        <w-form-item label="阶段医嘱项">
          <w-input
            v-model="templateForm.itemJson"
            type="textarea"
            :rows="4"
            placeholder='[{"day":1,"items":["血常规","腹部B超"]}]'
          />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="templateVisible = false">取消</w-button>
        <w-button type="primary" :loading="templateSaving" @click="submitTemplate">创建</w-button>
      </template>
    </w-dialog>

    <!-- 入径弹窗 -->
    <w-dialog v-model="enterVisible" title="患者入径" width="460px" destroy-on-close>
      <w-form label-width="100px">
        <w-form-item label="路径模板" required>
          <w-select v-model="enterForm.templateId" placeholder="选择模板" style="width: 100%">
            <w-option
              v-for="t in templates"
              :key="Number(t.id)"
              :label="String(t.pathName || t.path_name || t.id)"
              :value="Number(t.id)"
            />
          </w-select>
        </w-form-item>
        <w-form-item label="住院记录 ID" required>
          <w-input-number v-model="enterForm.admissionId" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="患者 ID">
          <w-input-number v-model="enterForm.patientId" :min="1" style="width: 100%" placeholder="选填" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="enterVisible = false">取消</w-button>
        <w-button type="primary" @click="submitEnter">确认入径</w-button>
      </template>
    </w-dialog>
    <!-- 变异/退出原因弹窗 -->
    <w-dialog
      v-model="reasonVisible"
      :title="reasonActionType === 'variation' ? '变异登记' : '路径退出'"
      width="440px"
      destroy-on-close
    >
      <w-form label-width="80px">
        <w-form-item :label="reasonActionType === 'variation' ? '变异原因' : '退出原因'" required>
          <w-input v-model="reasonText" type="textarea" :rows="3" placeholder="原因说明" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="reasonVisible = false">取消</w-button>
        <w-button type="primary" :loading="reasonSaving" @click="submitReason">确认</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.card-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.sub-title {
  margin: 14px 0 8px;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
