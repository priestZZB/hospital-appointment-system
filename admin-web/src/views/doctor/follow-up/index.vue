<template>
  <div class="page">
    <w-card class="page-card" title="随访管理" subtitle="创建随访计划、填写回访记录、跟踪随访闭环">
      <template #extra>
        <w-button type="primary" @click="openCreate">创建随访计划</w-button>
      </template>

      <div class="toolbar">
        <w-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px" @change="load">
          <w-option label="待随访" value="PENDING" />
          <w-option label="已完成" value="DONE" />
          <w-option label="已取消" value="CANCELLED" />
        </w-select>
        <w-button icon="Refresh" circle @click="load" />
      </div>

      <w-table :data="list" v-loading="loading" stripe>
        <w-table-column prop="patientId" label="患者ID" width="90" />
        <w-table-column prop="patientName" label="患者" width="100">
          <template #default="{ row }">{{ row.patientName || '-' }}</template>
        </w-table-column>
        <w-table-column prop="followDate" label="随访日期" width="120" />
        <w-table-column prop="followMethod" label="方式" width="100">
          <template #default="{ row }">{{ methodText(row.followMethod) }}</template>
        </w-table-column>
        <w-table-column prop="template" label="随访内容" min-width="200" show-overflow-tooltip />
        <w-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <w-tag :type="statusType(row.status)" effect="light">{{ statusText(row.status) }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column prop="records" label="记录数" width="90">
          <template #default="{ row }">{{ (row.records || []).length }}</template>
        </w-table-column>
        <w-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <w-button link type="primary" @click="openRecord(row)">回访</w-button>
              <w-button link type="danger" @click="cancel(row)">取消</w-button>
            </template>
            <span v-else class="muted">-</span>
          </template>
        </w-table-column>
      </w-table>
    </w-card>

    <w-dialog v-model="createVisible" title="创建随访计划" width="560px">
      <w-form ref="createFormRef" :model="form" :rules="rules" label-width="90px">
        <w-form-item label="患者ID" prop="patientId">
          <w-input v-model="form.patientId" placeholder="请输入患者ID" />
        </w-form-item>
        <w-form-item label="病历ID">
          <w-input v-model="form.medicalRecordId" placeholder="可选" />
        </w-form-item>
        <w-form-item label="随访日期" prop="followDate">
          <w-date-picker v-model="form.followDate" type="date" placeholder="选择日期" style="width: 100%" />
        </w-form-item>
        <w-form-item label="随访方式" prop="followMethod">
          <w-select v-model="form.followMethod" placeholder="请选择方式">
            <w-option label="电话" value="PHONE" />
            <w-option label="门诊复诊" value="VISIT" />
            <w-option label="微信" value="WECHAT" />
            <w-option label="其他" value="OTHER" />
          </w-select>
        </w-form-item>
        <w-form-item label="随访模板">
          <w-input v-model="form.template" type="textarea" :rows="3" placeholder="随访内容 / 模板" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="createVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="create">提交</w-button>
      </template>
    </w-dialog>

    <w-dialog v-model="recordVisible" title="填写回访记录" width="560px">
      <w-form label-width="90px">
        <w-form-item label="回访内容">
          <w-input v-model="recordForm.content" type="textarea" :rows="4" placeholder="患者反馈 / 恢复情况" />
        </w-form-item>
        <w-form-item label="下次随访">
          <w-date-picker v-model="recordForm.nextFollowDate" type="date" placeholder="可选，自动创建下次计划" style="width: 100%" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="recordVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="submitRecord">保存</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox, type FormInstance, type FormRules } from 'win-design-next'
import {
  addFollowUpRecordApi,
  cancelFollowUpPlanApi,
  createFollowUpPlanApi,
  listFollowUpPlansApi,
} from '@/api/clinic'
import type { FollowUpPlanVO } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const recordVisible = ref(false)
const list = ref<FollowUpPlanVO[]>([])
const query = reactive({ status: '' })
const form = reactive({
  patientId: '' as string,
  medicalRecordId: '' as string,
  followDate: '',
  followMethod: 'PHONE',
  template: '',
})
const recordForm = reactive({ content: '', nextFollowDate: '' })
const currentId = ref<number | null>(null)
const createFormRef = ref<FormInstance>()
const rules: FormRules = {
  patientId: [{ required: true, message: '请输入患者ID', trigger: 'blur' }],
  followDate: [{ required: true, message: '请选择随访日期', trigger: 'change' }],
  followMethod: [{ required: true, message: '请选择随访方式', trigger: 'change' }],
}

function statusText(s?: string): string {
  if (s === 'DONE') return '已完成'
  if (s === 'CANCELLED') return '已取消'
  return '待随访'
}
function statusType(s?: string): 'primary' | 'success' | 'danger' {
  if (s === 'DONE') return 'success'
  if (s === 'CANCELLED') return 'danger'
  return 'primary'
}
function methodText(s?: string): string {
  if (s === 'PHONE') return '电话'
  if (s === 'VISIT') return '门诊复诊'
  if (s === 'WECHAT') return '微信'
  return '其他'
}

async function load() {
  loading.value = true
  try {
    list.value = await listFollowUpPlansApi({ status: query.status || undefined })
  } catch (e) {
    WMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.patientId = ''
  form.medicalRecordId = ''
  form.followDate = ''
  form.followMethod = 'PHONE'
  form.template = ''
  createFormRef.value?.clearValidate()
  createVisible.value = true
}

async function create() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await createFollowUpPlanApi({
      patientId: Number(form.patientId),
      medicalRecordId: form.medicalRecordId ? Number(form.medicalRecordId) : undefined,
      followDate: form.followDate,
      followMethod: form.followMethod,
      template: form.template,
    })
    WMessage.success('随访计划已创建')
    createVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function openRecord(row: FollowUpPlanVO) {
  currentId.value = row.id
  recordForm.content = ''
  recordForm.nextFollowDate = ''
  recordVisible.value = true
}

async function submitRecord() {
  if (!currentId.value) return
  submitting.value = true
  try {
    await addFollowUpRecordApi(currentId.value, recordForm.content, recordForm.nextFollowDate || undefined)
    WMessage.success('回访记录已保存')
    recordVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function cancel(row: FollowUpPlanVO) {
  try {
    await WMessageBox.confirm('确认取消该随访计划？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelFollowUpPlanApi(row.id)
    WMessage.success('已取消')
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '取消失败')
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
