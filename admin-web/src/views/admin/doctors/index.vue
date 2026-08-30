<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import type { FormInstance, FormRules } from 'win-design-next'
import { Edit, Plus, Refresh, Search } from '@win-design-next/icons-vue'
import {
  createDoctorApi,
  getDepartmentsApi,
  getDoctorApi,
  getDoctorsApi,
  updateDoctorApi,
  updateDoctorStatusApi,
} from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO, DoctorVO } from '@/types'

const userStore = useUserStore()

/** 职称选项与展示映射 */
const titleOptions = [
  { label: '主任医师', value: 'CHIEF' },
  { label: '副主任医师', value: 'VICE_CHIEF' },
  { label: '主治医师', value: 'ATTENDING' },
  { label: '住院医师', value: 'RESIDENT' },
]
const titleMap: Record<string, string> = {
  CHIEF: '主任医师',
  VICE_CHIEF: '副主任医师',
  ATTENDING: '主治医师',
  RESIDENT: '住院医师',
}

function titleText(value?: string): string {
  return (value && titleMap[value]) || value || '—'
}

function genderText(value?: number): string {
  return value === 2 ? '女' : '男'
}

const loading = ref(false)
const list = ref<DoctorVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const query = reactive({
  departmentId: undefined as number | undefined,
  keyword: '',
  title: '',
})
const departments = ref<DepartmentVO[]>([])

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<DoctorVO | null>(null)

interface DoctorForm {
  userId?: number
  name: string
  gender: number
  phone: string
  departmentId?: number
  title: string
  specialty: string
  introduction: string
}

const form = reactive<DoctorForm>({
  userId: undefined,
  name: '',
  gender: 1,
  phone: '',
  departmentId: undefined,
  title: 'ATTENDING',
  specialty: '',
  introduction: '',
})

const formRef = ref<FormInstance>()
const rules: FormRules = {
  name: [{ required: true, message: '请输入医生姓名', trigger: 'blur', type: 'string' }],
  departmentId: [{ required: true, message: '请选择所属科室', trigger: 'change', type: 'number' }],
  title: [{ required: true, message: '请选择职称', trigger: 'change', type: 'string' }],
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getDoctorsApi({
      departmentId: query.departmentId,
      keyword: query.keyword || undefined,
      title: query.title || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    list.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '医生列表加载失败')
  } finally {
    loading.value = false
  }
}

async function fetchDepartments() {
  try {
    departments.value = await getDepartmentsApi()
  } catch (e) {
    WMessage.error((e as Error).message || '科室列表加载失败')
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchList()
}

function handleReset() {
  query.departmentId = undefined
  query.keyword = ''
  query.title = ''
  pageNo.value = 1
  fetchList()
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

function resetForm() {
  Object.assign(form, {
    userId: undefined,
    name: '',
    gender: 1,
    phone: '',
    departmentId: query.departmentId,
    title: 'ATTENDING',
    specialty: '',
    introduction: '',
  })
  formRef.value?.clearValidate()
}

function openCreate() {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: DoctorVO) {
  editing.value = row
  resetForm()
  try {
    const full = await getDoctorApi(row.id)
    Object.assign(form, {
      userId: full.userId,
      name: full.name,
      gender: full.gender ?? 1,
      phone: full.phone ?? '',
      departmentId: full.departmentId,
      title: full.title,
      specialty: full.specialty || '',
      introduction: full.introduction || '',
    })
  } catch {
    Object.assign(form, {
      userId: row.userId,
      name: row.name,
      gender: row.gender ?? 1,
      phone: row.phone ?? '',
      departmentId: row.departmentId,
      title: row.title,
      specialty: row.specialty || '',
      introduction: row.introduction || '',
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const payload: Record<string, unknown> = {
    userId: form.userId,
    name: form.name,
    gender: form.gender,
    phone: form.phone || undefined,
    departmentId: form.departmentId,
    title: form.title,
    specialty: form.specialty || undefined,
    introduction: form.introduction || undefined,
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateDoctorApi(editing.value.id, payload)
    } else {
      await createDoctorApi(payload)
    }
    WMessage.success('保存成功')
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleStatus(row: DoctorVO) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateDoctorStatusApi(row.id, next)
    WMessage.success(next === 1 ? '已启用' : '已停用')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

onMounted(() => {
  fetchList()
  fetchDepartments()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="doctors-head">
      <div class="page-head">
        <h2 class="page-title">医生管理</h2>
        <p class="page-subtitle">医生档案维护与启停管理</p>
      </div>
      <w-button v-if="userStore.isAdmin" type="primary" :icon="Plus" @click="openCreate">
        新增医生
      </w-button>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form :model="query" inline class="query-form">
        <w-form-item label="医生姓名">
          <w-input
            v-model="query.keyword"
            placeholder="请输入医生姓名"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </w-form-item>
        <w-form-item label="所属科室">
          <w-select
            v-model="query.departmentId"
            placeholder="全部科室"
            clearable
            style="width: 180px"
            @change="handleSearch"
          >
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="职称">
          <w-select
            v-model="query.title"
            placeholder="全部职称"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <w-option v-for="t in titleOptions" :key="t.value" :label="t.label" :value="t.value" />
          </w-select>
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :icon="Search" @click="handleSearch">查询</w-button>
          <w-button :icon="Refresh" @click="handleReset">重置</w-button>
        </w-form-item>
      </w-form>
    </w-card>

    <!-- 列表区 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar doctor-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-count">共 {{ total }} 位医生</span>
        </div>
        <div class="table-toolbar__right">
          <w-button :icon="Refresh" @click="fetchList">刷新</w-button>
        </div>
      </div>

      <div v-loading="loading">
        <w-table :data="list" row-key="id" border size="default">
          <w-table-column prop="name" label="姓名" min-width="110" show-overflow-tooltip />
          <w-table-column label="性别" width="70" align="center">
            <template #default="{ row }">{{ genderText(row.gender) }}</template>
          </w-table-column>
          <w-table-column prop="departmentName" label="科室" min-width="130" show-overflow-tooltip />
          <w-table-column label="职称" min-width="110">
            <template #default="{ row }">{{ titleText(row.title) }}</template>
          </w-table-column>
          <w-table-column prop="specialty" label="擅长" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.specialty || '—' }}</template>
          </w-table-column>
          <w-table-column label="联系电话" min-width="130">
            <template #default="{ row }">{{ row.phone || '—' }}</template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </w-tag>
            </template>
          </w-table-column>
          <w-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <template v-if="userStore.isAdmin">
                <w-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</w-button>
                <w-popconfirm
                  :title="row.status === 1 ? `确定停用医生「${row.name}」吗？` : `确定启用医生「${row.name}」吗？`"
                  :confirm-button-text="row.status === 1 ? '停用' : '启用'"
                  :confirm-button-type="row.status === 1 ? 'danger' : 'primary'"
                  @confirm="handleStatus(row)"
                >
                  <template #reference>
                    <w-button link :type="row.status === 1 ? 'danger' : 'success'">
                      {{ row.status === 1 ? '停用' : '启用' }}
                    </w-button>
                  </template>
                </w-popconfirm>
              </template>
            </template>
          </w-table-column>
        </w-table>
      </div>

      <div class="pager">
        <w-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="handleSizeChange"
        />
      </div>
    </w-card>

    <!-- 新增/编辑弹窗 -->
    <w-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑医生' : '新增医生'"
      width="640px"
      :close-on-click-modal="false"
    >
      <w-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <w-row :gutter="16">
          <w-col :span="12">
            <w-form-item label="医生姓名" prop="name">
              <w-input v-model="form.name" placeholder="请输入医生姓名" maxlength="50" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="性别" prop="gender">
              <w-radio-group v-model="form.gender">
                <w-radio :value="1">男</w-radio>
                <w-radio :value="2">女</w-radio>
              </w-radio-group>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="所属科室" prop="departmentId">
              <w-select v-model="form.departmentId" placeholder="请选择科室" filterable>
                <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
              </w-select>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="职称" prop="title">
              <w-select v-model="form.title" placeholder="请选择职称">
                <w-option v-for="t in titleOptions" :key="t.value" :label="t.label" :value="t.value" />
              </w-select>
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="联系电话" prop="phone">
              <w-input v-model="form.phone" placeholder="请输入联系电话" maxlength="20" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="擅长领域" prop="specialty">
              <w-input v-model="form.specialty" placeholder="请输入擅长领域" maxlength="255" />
            </w-form-item>
          </w-col>
          <w-col :span="24">
            <w-form-item label="医生简介" prop="introduction">
              <w-input v-model="form.introduction" type="textarea" :rows="3" placeholder="请输入医生简介" />
            </w-form-item>
          </w-col>
        </w-row>
      </w-form>
      <template #footer>
        <w-button @click="dialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="saving" @click="handleSave">保存</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.doctors-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.query-form {
  margin-bottom: 0;
}

.query-form :deep(.w3-form-item) {
  margin-right: 16px;
  margin-bottom: 12px;
}

.doctor-toolbar {
  margin-bottom: 12px;
}

.toolbar-count {
  font-size: 13px;
  color: var(--hospital-text-second);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
