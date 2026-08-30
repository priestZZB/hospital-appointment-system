<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { Component } from 'vue'
import { WMessage } from 'win-design-next'
import type { UploadFile, UploadInstance } from 'win-design-next'
import { CircleCheck, File, ListSolid, Plus, Refresh, Search, Upload } from '@win-design-next/icons-vue'
import { createExamItemApi, createExamReportApi, getExamItemsApi, getMyExamReportsApi } from '@/api/medsupply'
import { useUserStore } from '@/stores/user'
import type { ExamItem, ExamReport } from '@/types'

type TabName = 'items' | 'entry' | 'reports'
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const ITEM_TYPES: { label: string; value: string }[] = [
  { value: 'LAB', label: '检验' },
  { value: 'RADIOLOGY', label: '放射' },
  { value: 'ULTRASOUND', label: '超声' },
  { value: 'ENDOSCOPY', label: '内镜' },
  { value: 'ECG', label: '心电图' },
]

const ITEM_TYPE_LABELS: Record<string, string> = {
  LAB: '检验',
  RADIOLOGY: '放射',
  ULTRASOUND: '超声',
  ENDOSCOPY: '内镜',
  ECG: '心电图',
}

const ITEM_TYPE_TONES: Record<string, TagType> = {
  LAB: 'primary',
  RADIOLOGY: 'warning',
  ULTRASOUND: 'info',
  ENDOSCOPY: 'success',
  ECG: 'danger',
}

const userStore = useUserStore()
const canManageItems = computed(() => userStore.isAdmin)
const canEntryReport = computed(() => userStore.isAdmin || userStore.isDoctor)
const activeTab = ref<TabName>(canManageItems.value ? 'items' : canEntryReport.value ? 'entry' : 'reports')

// 检查项目
const items = ref<ExamItem[]>([])
const itemLoading = ref(false)
const itemDialogVisible = ref(false)
const itemSubmitting = ref(false)
const itemForm = reactive({
  itemCode: '',
  itemName: '',
  itemType: 'LAB',
  referencePrice: 0,
  execDept: '',
  precautions: '',
})

// 报告录入
const entryForm = reactive({
  applicationId: undefined as number | undefined,
  reportDesc: '',
  reportResult: '',
  status: 'PUBLISHED' as 'DRAFT' | 'PUBLISHED',
})
const entryFile = ref<File | null>(null)
const entrySubmitting = ref(false)
const uploadRef = ref<UploadInstance>()

// 报告查询
const reports = ref<ExamReport[]>([])
const reportLoading = ref(false)
const reportTotal = ref(0)
const reportPage = ref(1)
const reportSize = ref(10)
const reportQuery = reactive({ patientId: undefined as number | undefined })

interface StatItem {
  key: string
  label: string
  value: number
  unit: string
  icon: Component
  tone: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  desc: string
}

const stats = computed<StatItem[]>(() => {
  const list: StatItem[] = []
  if (canManageItems.value) {
    list.push({
      key: 'items',
      label: '检查项目',
      value: items.value.length,
      unit: '项',
      icon: ListSolid,
      tone: 'primary',
      desc: '已维护的检查检验项目',
    })
  }
  list.push({
    key: 'reports',
    label: '检查报告',
    value: reportTotal.value,
    unit: '份',
    icon: File,
    tone: 'info',
    desc: '报告查询结果总数',
  })
  return list
})

function itemTypeLabel(type: string): string {
  return ITEM_TYPE_LABELS[type] || type
}

function itemTypeTone(type: string): TagType {
  return ITEM_TYPE_TONES[type] || 'info'
}

function formatPrice(value?: number): string {
  return value != null ? `¥${Number(value).toFixed(2)}` : '-'
}

function formatTime(value?: string): string {
  return value || '-'
}

function reportStatusLabel(status?: string): string {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'DRAFT') return '草稿'
  return status || '-'
}

function reportStatusTone(status?: string): TagType {
  return status === 'PUBLISHED' ? 'success' : 'info'
}

async function fetchItems() {
  itemLoading.value = true
  try {
    items.value = await getExamItemsApi({})
  } catch (e) {
    WMessage.error((e as Error).message || '检查项目加载失败')
  } finally {
    itemLoading.value = false
  }
}

function openAddItem() {
  Object.assign(itemForm, {
    itemCode: '',
    itemName: '',
    itemType: 'LAB',
    referencePrice: 0,
    execDept: '',
    precautions: '',
  })
  itemDialogVisible.value = true
}

async function submitItem() {
  if (!itemForm.itemCode.trim() || !itemForm.itemName.trim()) {
    WMessage.warning('项目编码与名称必填')
    return
  }
  itemSubmitting.value = true
  try {
    await createExamItemApi({ ...itemForm })
    WMessage.success('检查项目创建成功')
    itemDialogVisible.value = false
    fetchItems()
  } catch (e) {
    WMessage.error((e as Error).message || '检查项目创建失败')
  } finally {
    itemSubmitting.value = false
  }
}

function onFileChange(uploadFile: UploadFile) {
  entryFile.value = uploadFile.raw || null
}

async function submitReport() {
  if (!entryForm.applicationId) {
    WMessage.warning('请填写检查申请ID')
    return
  }
  const form = new FormData()
  form.append('applicationId', String(entryForm.applicationId))
  form.append('reportDesc', entryForm.reportDesc)
  form.append('reportResult', entryForm.reportResult)
  form.append('status', entryForm.status)
  if (entryFile.value) form.append('file', entryFile.value)
  entrySubmitting.value = true
  try {
    await createExamReportApi(form)
    WMessage.success('报告录入成功')
    Object.assign(entryForm, { applicationId: undefined, reportDesc: '', reportResult: '', status: 'PUBLISHED' })
    entryFile.value = null
    uploadRef.value?.clearFiles()
    fetchReports()
  } catch (e) {
    WMessage.error((e as Error).message || '报告录入失败')
  } finally {
    entrySubmitting.value = false
  }
}

async function fetchReports() {
  reportLoading.value = true
  try {
    const page = await getMyExamReportsApi({
      patientId: reportQuery.patientId ?? undefined,
      pageNo: reportPage.value,
      pageSize: reportSize.value,
    })
    reports.value = page.records || []
    reportTotal.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '报告查询失败')
  } finally {
    reportLoading.value = false
  }
}

function handleSearch() {
  reportPage.value = 1
  fetchReports()
}

function handleReset() {
  reportQuery.patientId = undefined
  reportPage.value = 1
  fetchReports()
}

function handleSizeChange() {
  reportPage.value = 1
  fetchReports()
}

onMounted(() => {
  if (canManageItems.value) fetchItems()
  fetchReports()
})
</script>

<template>
  <div class="page-container exam-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">检查检验</h2>
      <p class="page-subtitle">检查项目维护 · 检查报告录入与查询</p>
    </div>

    <!-- 统计概览 -->
    <div class="stat-grid">
      <div v-for="s in stats" :key="s.key" class="stat-card">
        <span class="stat-card__icon" :class="`stat-card__icon--${s.tone}`">
          <component :is="s.icon" />
        </span>
        <div class="stat-card__body">
          <span class="stat-card__label">{{ s.label }}</span>
          <span class="stat-card__value">{{ s.value.toLocaleString() }}<i class="stat-card__unit">{{ s.unit }}</i></span>
          <span class="stat-card__extra">{{ s.desc }}</span>
        </div>
      </div>
    </div>

    <!-- 主体 -->
    <w-card shadow="never" class="hospital-card tab-card">
      <w-tabs v-model="activeTab">
        <!-- 检查项目 -->
        <w-tab-pane v-if="canManageItems" label="检查项目" name="items">
          <div class="pane-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <span class="pane-desc">维护检查检验项目字典，供开单与报告录入使用</span>
              </div>
              <div class="table-toolbar__right">
                <w-button type="primary" :icon="Plus" @click="openAddItem">新增检查项目</w-button>
              </div>
            </div>
            <w-table :data="items" row-key="id" border :loading="itemLoading" size="default">
              <w-table-column prop="itemCode" label="项目编码" min-width="110" show-overflow-tooltip />
              <w-table-column prop="itemName" label="项目名称" min-width="150" show-overflow-tooltip />
              <w-table-column label="项目类型" width="110" align="center">
                <template #default="{ row }">
                  <w-tag size="small" effect="light" :type="itemTypeTone(row.itemType)">{{ itemTypeLabel(row.itemType) }}</w-tag>
                </template>
              </w-table-column>
              <w-table-column label="参考价" width="100" align="right">
                <template #default="{ row }">{{ formatPrice(row.referencePrice) }}</template>
              </w-table-column>
              <w-table-column prop="execDept" label="执行科室" min-width="110" show-overflow-tooltip />
              <w-table-column prop="precautions" label="注意事项" min-width="180" show-overflow-tooltip />
              <w-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <w-tag size="small" effect="light" :type="row.status === 1 ? 'success' : 'info'">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
              </w-table-column>
            </w-table>
          </div>
        </w-tab-pane>

        <!-- 报告录入 -->
        <w-tab-pane v-if="canEntryReport" label="报告录入" name="entry">
          <div class="pane-body entry-pane">
            <w-form :model="entryForm" label-width="96px" class="entry-form">
              <w-form-item label="检查申请ID" required>
                <w-input-number v-model="entryForm.applicationId" :min="1" :step="1" placeholder="请输入检查申请ID" class="field-260" />
              </w-form-item>
              <w-form-item label="报告描述">
                <w-input v-model="entryForm.reportDesc" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入报告描述" />
              </w-form-item>
              <w-form-item label="报告结果">
                <w-input v-model="entryForm.reportResult" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入报告结果" />
              </w-form-item>
              <w-form-item label="报告状态">
                <w-radio-group v-model="entryForm.status">
                  <w-radio value="DRAFT">草稿</w-radio>
                  <w-radio value="PUBLISHED">发布</w-radio>
                </w-radio-group>
              </w-form-item>
              <w-form-item label="报告附件">
                <w-upload
                  ref="uploadRef"
                  action="#"
                  :auto-upload="false"
                  :show-file-list="false"
                  :limit="1"
                  accept=".jpg,.jpeg,.png,.pdf"
                  @change="onFileChange"
                >
                  <w-button plain :icon="Upload">选择附件</w-button>
                </w-upload>
                <span v-if="entryFile" class="file-name">{{ entryFile.name }}</span>
              </w-form-item>
              <w-form-item>
                <w-button type="primary" :loading="entrySubmitting" :icon="CircleCheck" @click="submitReport">提交报告</w-button>
              </w-form-item>
            </w-form>
          </div>
        </w-tab-pane>

        <!-- 报告查询 -->
        <w-tab-pane label="报告查询" name="reports">
          <div class="pane-body">
            <w-form :model="reportQuery" inline label-width="72px" class="query-form">
              <w-form-item label="患者ID">
                <w-input-number v-model="reportQuery.patientId" :min="1" :step="1" placeholder="请输入患者ID" class="field-200" />
              </w-form-item>
              <w-form-item>
                <w-button type="primary" :icon="Search" @click="handleSearch">查询</w-button>
                <w-button :icon="Refresh" @click="handleReset">重置</w-button>
              </w-form-item>
            </w-form>
            <w-table :data="reports" row-key="id" border size="default" :loading="reportLoading">
              <w-table-column prop="id" label="报告ID" width="80" align="center" />
              <w-table-column prop="applicationId" label="申请ID" width="90" align="center" />
              <w-table-column prop="patientId" label="患者ID" width="90" align="center" />
              <w-table-column prop="reportDesc" label="报告描述" min-width="160" show-overflow-tooltip />
              <w-table-column prop="reportResult" label="报告结果" min-width="180" show-overflow-tooltip />
              <w-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <w-tag size="small" effect="light" :type="reportStatusTone(row.status)">{{ reportStatusLabel(row.status) }}</w-tag>
                </template>
              </w-table-column>
              <w-table-column label="完成时间" width="170">
                <template #default="{ row }">{{ formatTime(row.completeTime) }}</template>
              </w-table-column>
              <w-table-column label="附件" width="90" align="center">
                <template #default="{ row }">
                  <w-link v-if="row.attachmentUrl" type="primary" :underline="false" :href="row.attachmentUrl" target="_blank">查看</w-link>
                  <span v-else class="text-third">-</span>
                </template>
              </w-table-column>
            </w-table>
            <div class="pager">
              <w-pagination
                v-model:current-page="reportPage"
                v-model:page-size="reportSize"
                :total="reportTotal"
                :page-sizes="[10, 20, 30, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchReports"
                @size-change="handleSizeChange"
              />
            </div>
          </div>
        </w-tab-pane>
      </w-tabs>
    </w-card>

    <!-- 新增检查项目弹窗 -->
    <w-dialog v-model="itemDialogVisible" title="新增检查项目" width="520px" :close-on-click-modal="false" destroy-on-close>
      <w-form :model="itemForm" label-width="88px">
        <w-form-item label="项目编码" required>
          <w-input v-model="itemForm.itemCode" placeholder="请输入项目编码" maxlength="32" clearable />
        </w-form-item>
        <w-form-item label="项目名称" required>
          <w-input v-model="itemForm.itemName" placeholder="请输入项目名称" maxlength="64" clearable />
        </w-form-item>
        <w-form-item label="项目类型">
          <w-select v-model="itemForm.itemType" placeholder="请选择项目类型" class="field-full">
            <w-option v-for="t in ITEM_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="参考价">
          <w-input-number v-model="itemForm.referencePrice" :min="0" :precision="2" :step="1" placeholder="请输入参考价" class="field-full" />
        </w-form-item>
        <w-form-item label="执行科室">
          <w-input v-model="itemForm.execDept" placeholder="请输入执行科室" maxlength="64" clearable />
        </w-form-item>
        <w-form-item label="注意事项">
          <w-input v-model="itemForm.precautions" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请输入注意事项" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="itemDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="itemSubmitting" @click="submitItem">确定</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.exam-page {
  width: 100%;
}

/* 统计概览 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.stat-card__unit {
  margin-left: 4px;
  font-size: 14px;
  font-weight: 500;
  color: var(--hospital-text-third);
  font-style: normal;
}

/* 卡片内 Tabs */
.tab-card {
  padding: 4px 20px 20px;
}
.tab-card :deep(.w3-card__body) {
  padding: 0;
}

.pane-body {
  padding: 16px 0 4px;
}

.pane-desc {
  font-size: 13px;
  color: var(--hospital-text-third);
}

/* 录入表单 */
.entry-pane {
  max-width: 680px;
}

.entry-form {
  width: 100%;
}

.query-form {
  margin-bottom: 14px;
}
.query-form :deep(.w3-form-item) {
  margin-right: 16px;
}

/* 分页 */
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* 附件名 */
.file-name {
  margin-left: 10px;
  font-size: 13px;
  color: var(--hospital-text-second);
}

.text-third {
  color: var(--hospital-text-third);
}

/* 表单控件宽度 */
.field-full {
  width: 100%;
}
.field-200 {
  width: 200px;
}
.field-260 {
  width: 260px;
}
</style>
