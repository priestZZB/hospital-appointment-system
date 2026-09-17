<template>
  <div class="page">
    <w-card class="page-card" title="会诊管理" subtitle="发起会诊、处理会诊请求、查看会诊结论">
      <template #extra>
        <w-button type="primary" @click="openCreate">发起会诊</w-button>
      </template>

      <div class="toolbar">
        <w-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px" @change="load">
          <w-option label="待会诊" value="PENDING" />
          <w-option label="已接受" value="ACCEPTED" />
          <w-option label="已完成" value="COMPLETED" />
          <w-option label="已拒绝" value="REJECTED" />
        </w-select>
        <w-button icon="Refresh" circle @click="load" />
      </div>

      <w-table :data="list" v-loading="loading" stripe>
        <w-table-column prop="requestNo" label="申请编号" width="140" />
        <w-table-column prop="applyDeptName" label="申请科室" width="120" />
        <w-table-column prop="applyDoctorName" label="申请医生" width="100" />
        <w-table-column prop="targetDeptName" label="会诊科室" width="120" />
        <w-table-column prop="targetDoctorName" label="会诊医生" width="100">
          <template #default="{ row }">{{ row.targetDoctorName || '科室指定' }}</template>
        </w-table-column>
        <w-table-column prop="reason" label="会诊原因" min-width="180" show-overflow-tooltip />
        <w-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <w-tag :type="statusType(row.status)" effect="light">{{ statusText(row.status) }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column prop="consultOpinion" label="会诊意见" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.consultOpinion || '-' }}</template>
        </w-table-column>
        <w-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <w-button link type="primary" @click="handle(row, 'ACCEPT')">接受</w-button>
              <w-button link type="danger" @click="handle(row, 'REJECT')">拒绝</w-button>
            </template>
            <template v-if="row.status === 'ACCEPTED'">
              <w-button link type="success" @click="openOpinion(row)">填结论</w-button>
            </template>
            <span v-if="row.status === 'COMPLETED' || row.status === 'REJECTED'" class="muted">-</span>
          </template>
        </w-table-column>
      </w-table>
    </w-card>

    <!-- 发起会诊 -->
    <w-dialog v-model="createVisible" title="发起会诊" width="560px">
      <w-form ref="createFormRef" :model="form" :rules="rules" label-width="90px">
        <w-form-item label="病历ID" prop="medicalRecordId">
          <w-input v-model="form.medicalRecordId" placeholder="请输入病历ID" />
        </w-form-item>
        <w-form-item label="会诊科室" prop="targetDeptId">
          <w-select v-model="form.targetDeptId" placeholder="请选择科室" filterable>
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="会诊医生">
          <w-select v-model="form.targetDoctorId" placeholder="科室指定（可空）" filterable clearable>
            <w-option v-for="d in doctors" :key="d.id" :label="d.name" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="会诊原因" prop="reason">
          <w-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="病情摘要 / 会诊目的" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="createVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="create">提交</w-button>
      </template>
    </w-dialog>

    <!-- 填写会诊结论 -->
    <w-dialog v-model="opinionVisible" title="填写会诊结论" width="520px">
      <w-input v-model="opinion" type="textarea" :rows="4" placeholder="会诊意见 / 结论" />
      <template #footer>
        <w-button @click="opinionVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="completeOpinion">保存并完成</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, type FormInstance, type FormRules } from 'win-design-next'
import {
  createConsultRequestApi,
  handleConsultRequestApi,
  listConsultRequestsApi,
} from '@/api/clinic'
import { getDepartmentsApi, getDoctorsApi } from '@/api/clinic'
import type { ConsultationRequestVO, DepartmentVO } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const opinionVisible = ref(false)
const list = ref<ConsultationRequestVO[]>([])
const departments = ref<DepartmentVO[]>([])
const doctors = ref<{ id: number; name: string }[]>([])
const query = reactive({ status: '' })
const form = reactive({
  medicalRecordId: '' as string,
  targetDeptId: null as number | null,
  targetDoctorId: null as number | null,
  reason: '',
})
const opinion = ref('')
const currentId = ref<number | null>(null)
const createFormRef = ref<FormInstance>()
const rules: FormRules = {
  medicalRecordId: [{ required: true, message: '请输入病历ID', trigger: 'blur' }],
  targetDeptId: [{ required: true, message: '请选择会诊科室', trigger: 'change' }],
  reason: [{ required: true, message: '请填写会诊原因', trigger: 'blur' }],
}

function statusText(s?: string): string {
  if (s === 'ACCEPTED') return '已接受'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'REJECTED') return '已拒绝'
  return '待会诊'
}
function statusType(s?: string): 'primary' | 'success' | 'warning' | 'danger' {
  if (s === 'ACCEPTED') return 'primary'
  if (s === 'COMPLETED') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'warning'
}

async function load() {
  loading.value = true
  try {
    list.value = await listConsultRequestsApi({ status: query.status || undefined })
  } catch (e) {
    WMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    departments.value = await getDepartmentsApi()
  } catch { /* ignore */ }
}

async function loadDoctors() {
  try {
    const res = await getDoctorsApi({ pageNo: 1, pageSize: 100 })
    doctors.value = (res.records || []).map((d) => ({ id: d.id, name: d.name }))
  } catch { /* ignore */ }
}

function openCreate() {
  form.medicalRecordId = ''
  form.targetDeptId = null
  form.targetDoctorId = null
  form.reason = ''
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
    await createConsultRequestApi({
      medicalRecordId: Number(form.medicalRecordId),
      targetDeptId: form.targetDeptId,
      targetDoctorId: form.targetDoctorId || undefined,
      reason: form.reason,
    })
    WMessage.success('会诊申请已提交')
    createVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function handle(row: ConsultationRequestVO, action: string) {
  try {
    await handleConsultRequestApi(row.id, action)
    WMessage.success(action === 'ACCEPT' ? '已接受会诊' : '已拒绝会诊')
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

function openOpinion(row: ConsultationRequestVO) {
  currentId.value = row.id
  opinion.value = row.consultOpinion || ''
  opinionVisible.value = true
}

async function completeOpinion() {
  if (!currentId.value) return
  submitting.value = true
  try {
    await handleConsultRequestApi(currentId.value, 'COMPLETE', opinion.value)
    WMessage.success('会诊已完成')
    opinionVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  loadDepartments()
  loadDoctors()
})
</script>

<style scoped>
.page { padding: 4px; }
.page-card { max-width: 1200px; margin: 0 auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 12px; }
.muted { color: #aaa; }
</style>
