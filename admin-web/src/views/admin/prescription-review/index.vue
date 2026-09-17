<template>
  <div class="page">
    <w-card class="page-card" title="处方点评" subtitle="药师对处方进行点评（合理/不合理、问题分类）">
      <template #extra>
        <w-button type="primary" @click="openCreate">新增点评</w-button>
      </template>

      <div class="toolbar">
        <w-input v-model="query.prescriptionId" placeholder="按处方ID筛选" clearable style="width: 180px" @change="load" />
        <w-button icon="Refresh" circle @click="load" />
      </div>

      <w-table :data="list" v-loading="loading" stripe>
        <w-table-column prop="prescriptionId" label="处方ID" width="100" />
        <w-table-column prop="patientId" label="患者ID" width="100" />
        <w-table-column prop="rating" label="评价" width="100">
          <template #default="{ row }">
            <w-tag :type="row.rating === 'REASONABLE' ? 'success' : 'danger'" effect="light">{{ row.rating === 'REASONABLE' ? '合理' : '不合理' }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column prop="problemType" label="问题分类" width="140">
          <template #default="{ row }">{{ row.problemType || '-' }}</template>
        </w-table-column>
        <w-table-column prop="comment" label="点评意见" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.comment || '-' }}</template>
        </w-table-column>
        <w-table-column prop="createTime" label="点评时间" width="160" />
      </w-table>
    </w-card>

    <w-dialog v-model="createVisible" title="新增处方点评" width="560px">
      <w-form ref="createFormRef" :model="form" :rules="rules" label-width="90px">
        <w-form-item label="处方ID" prop="prescriptionId">
          <w-input v-model="form.prescriptionId" placeholder="请输入处方ID" />
        </w-form-item>
        <w-form-item label="患者ID" prop="patientId">
          <w-input v-model="form.patientId" placeholder="请输入患者ID" />
        </w-form-item>
        <w-form-item label="评价" prop="rating">
          <w-radio-group v-model="form.rating">
            <w-radio label="REASONABLE">合理</w-radio>
            <w-radio label="UNREASONABLE">不合理</w-radio>
          </w-radio-group>
        </w-form-item>
        <w-form-item label="问题分类">
          <w-select v-model="form.problemType" placeholder="选择分类（可不选）" clearable>
            <w-option label="剂量不当" value="DOSAGE" />
            <w-option label="配伍禁忌" value="INTERACTION" />
            <w-option label="禁忌症" value="CONTRAINDICATION" />
            <w-option label="重复用药" value="DUPLICATE" />
            <w-option label="其他" value="OTHER" />
          </w-select>
        </w-form-item>
        <w-form-item label="点评意见">
          <w-input v-model="form.comment" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="点评意见" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="createVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="submit">提交</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, type FormInstance, type FormRules } from 'win-design-next'
import { createPrescriptionReviewApi, listPrescriptionReviewsApi } from '@/api/medsupply'
import type { PrescriptionReviewVO } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const list = ref<PrescriptionReviewVO[]>([])
const query = reactive({ prescriptionId: '' })
const form = reactive({
  prescriptionId: '' as string,
  patientId: '' as string,
  rating: 'REASONABLE',
  problemType: '',
  comment: '',
})
const createFormRef = ref<FormInstance>()
const rules: FormRules = {
  prescriptionId: [{ required: true, message: '请输入处方ID', trigger: 'blur' }],
  patientId: [{ required: true, message: '请输入患者ID', trigger: 'blur' }],
  rating: [{ required: true, message: '请选择评价', trigger: 'change' }],
}

async function load() {
  loading.value = true
  try {
    list.value = await listPrescriptionReviewsApi({
      prescriptionId: query.prescriptionId ? Number(query.prescriptionId) : undefined,
    })
  } catch (e) {
    WMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.prescriptionId = ''
  form.patientId = ''
  form.rating = 'REASONABLE'
  form.problemType = ''
  form.comment = ''
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
    await createPrescriptionReviewApi({
      prescriptionId: Number(form.prescriptionId),
      patientId: Number(form.patientId),
      rating: form.rating,
      problemType: form.problemType || undefined,
      comment: form.comment || undefined,
    })
    WMessage.success('点评已提交')
    createVisible.value = false
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
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
</style>
