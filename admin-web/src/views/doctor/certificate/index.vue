<template>
  <div class="page">
    <w-card class="page-card" title="医疗证明" subtitle="诊断证明 / 病假条 / 转诊单 / 医疗建议，支持 PDF 下载">
      <template #extra>
        <w-button type="primary" @click="openCreate">开具证明</w-button>
      </template>

      <div class="toolbar">
        <w-select v-model="query.certType" placeholder="全部类型" clearable style="width: 160px" @change="load">
          <w-option label="诊断证明" value="DIAGNOSIS" />
          <w-option label="病假条" value="SICK_LEAVE" />
          <w-option label="转诊单" value="REFERRAL" />
          <w-option label="医疗建议" value="MEDICAL_ADVICE" />
        </w-select>
        <w-button icon="Refresh" circle @click="load" />
      </div>

      <w-table :data="list" v-loading="loading" stripe>
        <w-table-column prop="certNo" label="证明编号" width="150" />
        <w-table-column prop="certType" label="类型" width="110">
          <template #default="{ row }">{{ typeText(row.certType) }}</template>
        </w-table-column>
        <w-table-column prop="patientId" label="患者ID" width="90" />
        <w-table-column prop="doctorName" label="开具医生" width="100" />
        <w-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
        <w-table-column prop="startDate" label="起始日期" width="110">
          <template #default="{ row }">{{ row.startDate || '-' }}</template>
        </w-table-column>
        <w-table-column prop="days" label="天数" width="80">
          <template #default="{ row }">{{ row.days ?? '-' }}</template>
        </w-table-column>
        <w-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <w-tag :type="row.status === 'ISSUED' ? 'success' : 'info'" effect="light">{{ row.status === 'ISSUED' ? '已开具' : '已作废' }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <w-button link type="primary" @click="download(row)">下载PDF</w-button>
            <w-button v-if="row.status === 'ISSUED'" link type="danger" @click="cancel(row)">作废</w-button>
            <span v-else class="muted">-</span>
          </template>
        </w-table-column>
      </w-table>
    </w-card>

    <w-dialog v-model="createVisible" title="开具医疗证明" width="600px">
      <w-form ref="createFormRef" :model="form" :rules="rules" label-width="90px">
        <w-form-item label="证明类型" prop="certType">
          <w-select v-model="form.certType" placeholder="请选择类型">
            <w-option label="诊断证明" value="DIAGNOSIS" />
            <w-option label="病假条" value="SICK_LEAVE" />
            <w-option label="转诊单" value="REFERRAL" />
            <w-option label="医疗建议" value="MEDICAL_ADVICE" />
          </w-select>
        </w-form-item>
        <w-form-item label="患者ID" prop="patientId">
          <w-input v-model="form.patientId" placeholder="请输入患者ID" />
        </w-form-item>
        <w-form-item label="病历ID">
          <w-input v-model="form.medicalRecordId" placeholder="可选" />
        </w-form-item>
        <w-form-item label="起始日期">
          <w-date-picker v-model="form.startDate" type="date" placeholder="可选" style="width: 100%" />
        </w-form-item>
        <w-form-item v-if="form.certType === 'SICK_LEAVE'" label="休假天数">
          <w-input-number v-model="form.days" :min="0" :max="180" placeholder="天数" style="width: 100%" />
        </w-form-item>
        <w-form-item label="证明内容" prop="content">
          <w-input v-model="form.content" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="例如：该患者诊断为XX，建议休息X天" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="createVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="create">开具</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox, type FormInstance, type FormRules } from 'win-design-next'
import {
  cancelCertificateApi,
  createCertificateApi,
  downloadCertificateApi,
  listCertificatesApi,
} from '@/api/clinic'
import type { MedicalCertificateVO } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const list = ref<MedicalCertificateVO[]>([])
const query = reactive({ certType: '' })
const form = reactive({
  certType: 'DIAGNOSIS',
  patientId: '' as string,
  medicalRecordId: '' as string,
  startDate: '',
  days: null as number | null,
  content: '',
})
const createFormRef = ref<FormInstance>()
const rules: FormRules = {
  certType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  patientId: [{ required: true, message: '请输入患者ID', trigger: 'blur' }],
  content: [{ required: true, message: '请填写证明内容', trigger: 'blur' }],
}

function typeText(s?: string): string {
  if (s === 'SICK_LEAVE') return '病假条'
  if (s === 'REFERRAL') return '转诊单'
  if (s === 'MEDICAL_ADVICE') return '医疗建议'
  return '诊断证明'
}

async function load() {
  loading.value = true
  try {
    list.value = await listCertificatesApi({ certType: query.certType || undefined })
  } catch (e) {
    WMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.certType = 'DIAGNOSIS'
  form.patientId = ''
  form.medicalRecordId = ''
  form.startDate = ''
  form.days = null
  form.content = ''
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
    await createCertificateApi({
      certType: form.certType,
      patientId: Number(form.patientId),
      medicalRecordId: form.medicalRecordId ? Number(form.medicalRecordId) : undefined,
      startDate: form.startDate || undefined,
      days: form.days ?? undefined,
      content: form.content,
    })
    WMessage.success('证明已开具')
    createVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '开具失败')
  } finally {
    submitting.value = false
  }
}

async function download(row: MedicalCertificateVO) {
  try {
    const res = (await downloadCertificateApi(row.id)) as Blob
    const url = URL.createObjectURL(res)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.certNo || 'certificate'}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    WMessage.error((e as Error).message || '下载失败')
  }
}

async function cancel(row: MedicalCertificateVO) {
  try {
    await WMessageBox.confirm('确认作废该证明？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelCertificateApi(row.id)
    WMessage.success('已作废')
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '作废失败')
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
