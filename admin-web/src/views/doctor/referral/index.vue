<template>
  <div class="page">
    <w-card class="page-card" title="转诊管理" subtitle="创建转诊单、接收/完成/退回">
      <template #extra>
        <w-button type="primary" @click="openCreate">创建转诊单</w-button>
      </template>

      <div class="toolbar">
        <w-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px" @change="load">
          <w-option label="待接收" value="PENDING" />
          <w-option label="已接收" value="ACCEPTED" />
          <w-option label="已完成" value="COMPLETED" />
          <w-option label="已退回" value="REJECTED" />
        </w-select>
        <w-button icon="Refresh" circle @click="load" />
      </div>

      <w-table :data="list" v-loading="loading" stripe>
        <w-table-column prop="referralNo" label="转诊单号" width="140" />
        <w-table-column prop="fromDeptName" label="转出科室" width="120" />
        <w-table-column prop="fromDoctorName" label="转出医生" width="100" />
        <w-table-column prop="toDeptName" label="转入科室" width="120" />
        <w-table-column prop="reason" label="转诊原因" min-width="180" show-overflow-tooltip />
        <w-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <w-tag :type="statusType(row.status)" effect="light">{{ statusText(row.status) }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <w-button link type="primary" @click="handle(row, 'ACCEPT')">接收</w-button>
              <w-button link type="danger" @click="handle(row, 'REJECT')">退回</w-button>
            </template>
            <template v-if="row.status === 'ACCEPTED'">
              <w-button link type="success" @click="handle(row, 'COMPLETE')">完成</w-button>
            </template>
            <span v-if="row.status === 'COMPLETED' || row.status === 'REJECTED'" class="muted">-</span>
          </template>
        </w-table-column>
      </w-table>
    </w-card>

    <w-dialog v-model="createVisible" title="创建转诊单" width="560px">
      <w-form ref="createFormRef" :model="form" :rules="rules" label-width="90px">
        <w-form-item label="病历ID" prop="medicalRecordId">
          <w-input v-model="form.medicalRecordId" placeholder="请输入病历ID" />
        </w-form-item>
        <w-form-item label="转入科室" prop="toDeptId">
          <w-select v-model="form.toDeptId" placeholder="请选择转入科室" filterable>
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="转诊原因" prop="reason">
          <w-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="转诊原因 / 病情摘要" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="createVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="create">提交</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, type FormInstance, type FormRules } from 'win-design-next'
import { createReferralApi, handleReferralApi, listReferralsApi } from '@/api/clinic'
import { getDepartmentsApi } from '@/api/clinic'
import type { DepartmentVO, ReferralOrderVO } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const list = ref<ReferralOrderVO[]>([])
const departments = ref<DepartmentVO[]>([])
const query = reactive({ status: '' })
const form = reactive({
  medicalRecordId: '' as string,
  toDeptId: null as number | null,
  reason: '',
})
const createFormRef = ref<FormInstance>()
const rules: FormRules = {
  medicalRecordId: [{ required: true, message: '请输入病历ID', trigger: 'blur' }],
  toDeptId: [{ required: true, message: '请选择转入科室', trigger: 'change' }],
  reason: [{ required: true, message: '请填写转诊原因', trigger: 'blur' }],
}

function statusText(s?: string): string {
  if (s === 'ACCEPTED') return '已接收'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'REJECTED') return '已退回'
  return '待接收'
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
    list.value = await listReferralsApi({ status: query.status || undefined })
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

function openCreate() {
  form.medicalRecordId = ''
  form.toDeptId = null
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
    await createReferralApi({
      medicalRecordId: Number(form.medicalRecordId),
      toDeptId: form.toDeptId,
      reason: form.reason,
    })
    WMessage.success('转诊单已创建')
    createVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function handle(row: ReferralOrderVO, action: string) {
  try {
    await handleReferralApi(row.id, action)
    const msg = action === 'COMPLETE' ? '转诊已完成' : action === 'ACCEPT' ? '已接收转诊' : '已退回转诊'
    WMessage.success(msg)
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

onMounted(() => {
  load()
  loadDepartments()
})
</script>

<style scoped>
.page { padding: 4px; }
.page-card { max-width: 1200px; margin: 0 auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 12px; }
.muted { color: #aaa; }
</style>
