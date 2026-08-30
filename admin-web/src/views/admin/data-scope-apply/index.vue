<template>
  <div class="data-scope-page">
    <w-card class="page-card" title="跨科室数据范围申请" subtitle="查看本科室/本人范围之外的数据前，需提交申请并经科主任/管理员审批">
      <template #extra>
        <div class="card-extra">
          <w-tag type="info" effect="light">默认范围：本科室/本人</w-tag>
          <w-button type="primary" @click="dialogVisible = true">提交申请</w-button>
        </div>
      </template>

      <!-- 我的申请列表 -->
      <w-table :data="records" v-loading="loading" stripe>
        <w-table-column prop="targetDepartmentName" label="目标科室" min-width="120" />
        <w-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip />
        <w-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <w-tag :type="statusType(row.status)" effect="light">{{ statusText(row.status) }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column prop="approverName" label="审批人" width="100">
          <template #default="{ row }">{{ row.approverName || '-' }}</template>
        </w-table-column>
        <w-table-column prop="approveComment" label="审批意见" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.approveComment || '-' }}</template>
        </w-table-column>
        <w-table-column prop="expireTime" label="有效至" width="150">
          <template #default="{ row }">{{ row.expireTime || '长期' }}</template>
        </w-table-column>
        <w-table-column prop="applyTime" label="申请时间" width="160" />
      </w-table>

      <div class="pager">
        <w-pagination
          :current-page="query.pageNo"
          :page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { query.pageNo = p; load() }"
        />
      </div>

      <w-alert type="info" show-icon :closable="false" class="tip">
        提示：影像/检验技师、护士等岗位默认仅本科室数据；如需跨科室协作（如会诊、代班），在此提交申请。
      </w-alert>
    </w-card>

    <!-- 提交申请弹窗 -->
    <w-dialog v-model="dialogVisible" title="提交跨科室申请" width="520px">
      <w-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <w-form-item label="目标科室" prop="targetDepartmentId">
          <w-select v-model="form.targetDepartmentId" placeholder="请选择科室" filterable>
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="申请理由" prop="reason">
          <w-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="例如：本周在急诊科代班，需查看急诊病历数据" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="dialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="submit">提交</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import type { FormInstance, FormRules } from 'win-design-next'
import { applyDataScopeApi, getMyDataScopesApi } from '@/api/auth'
import type { DepartmentVO } from '@/types'

interface DataScopeRow {
  id: number
  targetDepartmentId?: number
  targetDepartmentName?: string
  reason?: string
  status?: string
  approverName?: string
  approveComment?: string
  expireTime?: string
  applyTime?: string
}

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const records = ref<DataScopeRow[]>([])
const total = ref(0)
const departments = ref<DepartmentVO[]>([])
const formRef = ref<FormInstance>()

const query = reactive({ pageNo: 1, pageSize: 10 })
const form = reactive<{ targetDepartmentId: number | null; reason: string }>({ targetDepartmentId: null, reason: '' })

const rules: FormRules = {
  targetDepartmentId: [{ required: true, message: '请选择目标科室', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写申请理由', trigger: 'blur' },
    { min: 5, message: '理由至少 5 个字', trigger: 'blur' },
  ],
}

function statusText(s?: string): string {
  if (s === 'APPROVED') return '已通过'
  if (s === 'REJECTED') return '已驳回'
  return '待审批'
}
function statusType(s?: string): 'success' | 'danger' | 'warning' {
  if (s === 'APPROVED') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'warning'
}

async function load() {
  loading.value = true
  try {
    const res = await getMyDataScopesApi({ pageNo: query.pageNo, pageSize: query.pageSize })
    records.value = (res.records || []) as unknown as DataScopeRow[]
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const { getDepartmentsApi } = await import('@/api/clinic')
    departments.value = await getDepartmentsApi()
  } catch {
    // 科室列表拉取失败不阻塞页面
  }
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const dept = departments.value.find((d) => d.id === form.targetDepartmentId)
    await applyDataScopeApi({
      targetDepartmentId: form.targetDepartmentId as number,
      targetDepartmentName: dept?.deptName,
      reason: form.reason,
    })
    WMessage.success('申请已提交，等待审批')
    dialogVisible.value = false
    form.targetDepartmentId = null
    form.reason = ''
    query.pageNo = 1
    load()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  loadDepartments()
})
</script>

<style scoped>
.data-scope-page {
  padding: 4px;
}
.page-card {
  max-width: 1100px;
  margin: 0 auto;
}
.card-extra {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.tip {
  margin-top: 16px;
}
</style>