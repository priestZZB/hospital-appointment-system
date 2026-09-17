<template>
  <div class="page">
    <w-card class="page-card" title="危急值管理" subtitle="危急值上报 → 医生复核 → 处置闭环">
      <template #extra>
        <w-button type="primary" @click="openCreate">上报危急值</w-button>
      </template>

      <div class="toolbar">
        <w-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px" @change="load">
          <w-option label="待复核" value="PENDING" />
          <w-option label="已复核" value="CONFIRMED" />
          <w-option label="已处置" value="RESOLVED" />
        </w-select>
        <w-button icon="Refresh" circle @click="load" />
      </div>

      <w-table :data="list" v-loading="loading" stripe>
        <w-table-column prop="itemName" label="项目" min-width="140" />
        <w-table-column prop="resultValue" label="结果值" width="120" />
        <w-table-column prop="referenceRange" label="参考范围" width="140">
          <template #default="{ row }">{{ row.referenceRange || '-' }}</template>
        </w-table-column>
        <w-table-column prop="criticalLevel" label="级别" width="80">
          <template #default="{ row }">
            <w-tag :type="row.criticalLevel === 'HIGH' ? 'danger' : 'warning'" effect="light">{{ row.criticalLevel === 'HIGH' ? '高' : '低' }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column prop="patientId" label="患者ID" width="90" />
        <w-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <w-tag :type="statusType(row.status)" effect="light">{{ statusText(row.status) }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column prop="confirmComment" label="复核意见" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.confirmComment || '-' }}</template>
        </w-table-column>
        <w-table-column prop="createTime" label="上报时间" width="160" />
        <w-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <w-button link type="primary" @click="confirm(row, 'CONFIRMED')">复核</w-button>
              <w-button link type="warning" @click="confirm(row, 'RESOLVED')">处置</w-button>
            </template>
            <span v-else class="muted">-</span>
          </template>
        </w-table-column>
      </w-table>
    </w-card>

    <w-dialog v-model="createVisible" title="上报危急值" width="560px">
      <w-form ref="createFormRef" :model="form" :rules="rules" label-width="90px">
        <w-form-item label="报告ID" prop="reportId">
          <w-input v-model="form.reportId" placeholder="请输入检查报告ID" />
        </w-form-item>
        <w-form-item label="项目名称" prop="itemName">
          <w-input v-model="form.itemName" placeholder="如：血糖 / 白细胞计数" />
        </w-form-item>
        <w-form-item label="结果值" prop="resultValue">
          <w-input v-model="form.resultValue" placeholder="如：28.5 mmol/L" />
        </w-form-item>
        <w-form-item label="参考范围">
          <w-input v-model="form.referenceRange" placeholder="如：3.9-6.1 mmol/L" />
        </w-form-item>
        <w-form-item label="级别" prop="criticalLevel">
          <w-select v-model="form.criticalLevel">
            <w-option label="高（HIGH）" value="HIGH" />
            <w-option label="低（LOW）" value="LOW" />
          </w-select>
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="createVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="submit">上报</w-button>
      </template>
    </w-dialog>

    <w-dialog v-model="commentVisible" title="复核意见" width="520px">
      <w-input v-model="comment" type="textarea" :rows="3" placeholder="复核/处置意见（可选）" />
      <template #footer>
        <w-button @click="commentVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="submitConfirm">确认</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, type FormInstance, type FormRules } from 'win-design-next'
import { confirmCriticalApi, listCriticalApi, reportCriticalApi } from '@/api/medsupply'
import type { CriticalValueVO } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const commentVisible = ref(false)
const list = ref<CriticalValueVO[]>([])
const query = reactive({ status: '' })
const form = reactive({
  reportId: '' as string,
  itemName: '',
  resultValue: '',
  referenceRange: '',
  criticalLevel: 'HIGH',
})
const comment = ref('')
const currentId = ref<number | null>(null)
const currentAction = ref('CONFIRMED')
const createFormRef = ref<FormInstance>()
const rules: FormRules = {
  reportId: [{ required: true, message: '请输入报告ID', trigger: 'blur' }],
  itemName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  resultValue: [{ required: true, message: '请输入结果值', trigger: 'blur' }],
  criticalLevel: [{ required: true, message: '请选择级别', trigger: 'change' }],
}

function statusText(s?: string): string {
  if (s === 'CONFIRMED') return '已复核'
  if (s === 'RESOLVED') return '已处置'
  return '待复核'
}
function statusType(s?: string): 'danger' | 'warning' | 'success' {
  if (s === 'PENDING') return 'danger'
  if (s === 'CONFIRMED') return 'warning'
  return 'success'
}

async function load() {
  loading.value = true
  try {
    list.value = await listCriticalApi({ status: query.status || undefined })
  } catch (e) {
    WMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.reportId = ''
  form.itemName = ''
  form.resultValue = ''
  form.referenceRange = ''
  form.criticalLevel = 'HIGH'
  createFormRef.value?.clearValidate()
  createVisible.value = true
}

async function submit() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await reportCriticalApi({
      reportId: Number(form.reportId),
      itemName: form.itemName,
      resultValue: form.resultValue,
      referenceRange: form.referenceRange || undefined,
      criticalLevel: form.criticalLevel,
    })
    WMessage.success('危急值已上报')
    createVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '上报失败')
  } finally {
    submitting.value = false
  }
}

function confirm(row: CriticalValueVO, action: string) {
  currentId.value = row.id
  currentAction.value = action
  comment.value = ''
  commentVisible.value = true
}

async function submitConfirm() {
  if (!currentId.value) return
  submitting.value = true
  try {
    await confirmCriticalApi(currentId.value, currentAction.value, comment.value || undefined)
    WMessage.success(currentAction.value === 'CONFIRMED' ? '已复核' : '已处置')
    commentVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { padding: 4px; }
.page-card { max-width: 1200px; margin: 0 auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 12px; }
.muted { color: #aaa; }
</style>
