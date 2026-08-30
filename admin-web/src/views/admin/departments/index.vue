<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox, type FormInstance, type FormRules } from 'win-design-next'
import { Edit, Location, Plus, Refresh, Search, Telephone } from '@win-design-next/icons-vue'
import {
  createDepartmentApi,
  getDepartmentApi,
  getDepartmentsApi,
  updateDepartmentApi,
  updateDepartmentStatusApi,
} from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO } from '@/types'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const loading = ref(false)
const saving = ref(false)
const query = reactive({ keyword: '' })

const list = ref<DepartmentVO[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = computed(() => list.value.length)
const pagedList = computed(() =>
  list.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  deptName: '',
  deptCode: '',
  location: '',
  phone: '',
  description: '',
  sortOrder: 0,
})

const rules: FormRules = {
  deptName: [{ required: true, message: '请输入科室名称', trigger: 'blur' }],
  deptCode: [{ required: true, message: '请输入科室编码', trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const keyword = query.keyword.trim() || undefined
    list.value = await getDepartmentsApi(keyword)
    page.value = 1
  } catch (e) {
    WMessage.error((e as Error).message || '科室列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.keyword = ''
  fetchList()
}

function handleSizeChange() {
  page.value = 1
}

function resetForm() {
  Object.assign(form, {
    deptName: '',
    deptCode: '',
    location: '',
    phone: '',
    description: '',
    sortOrder: 0,
  })
  formRef.value?.clearValidate()
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: DepartmentVO) {
  editingId.value = row.id
  resetForm()
  try {
    const full = await getDepartmentApi(row.id)
    Object.assign(form, {
      deptName: full.deptName,
      deptCode: full.deptCode,
      location: full.location || '',
      phone: full.phone || '',
      description: full.description || '',
      sortOrder: full.sortOrder ?? 0,
    })
  } catch {
    Object.assign(form, {
      deptName: row.deptName,
      deptCode: row.deptCode,
      location: row.location || '',
      phone: row.phone || '',
      description: row.description || '',
      sortOrder: row.sortOrder ?? 0,
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
  saving.value = true
  try {
    if (editingId.value != null) {
      await updateDepartmentApi(editingId.value, { ...form })
    } else {
      await createDepartmentApi({ ...form })
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

async function handleStatus(row: DepartmentVO) {
  const next = row.status === 1 ? 0 : 1
  try {
    await WMessageBox.confirm(`确定${next === 1 ? '启用' : '停用'}科室「${row.deptName}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: next === 1 ? '启用' : '停用',
    })
  } catch {
    return
  }
  try {
    await updateDepartmentStatusApi(row.id, next)
    WMessage.success('操作成功')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container departments">
    <!-- 页头 -->
    <div class="dept-head">
      <div class="dept-head__text">
        <h2 class="page-title">科室管理</h2>
        <p class="page-subtitle">维护医院科室基础档案，支撑挂号、排班与叫号业务</p>
      </div>
      <w-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">新增科室</w-button>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form inline :model="query" label-width="auto">
        <w-form-item label="关键词">
          <w-input
            v-model="query.keyword"
            placeholder="科室名称 / 编码"
            clearable
            style="width: 260px"
            @keyup.enter="fetchList"
          />
        </w-form-item>
        <w-form-item>
          <div class="query-actions">
            <w-button type="primary" :icon="Search" @click="fetchList">查询</w-button>
            <w-button :icon="Refresh" @click="handleReset">重置</w-button>
          </div>
        </w-form-item>
      </w-form>
    </w-card>

    <!-- 表格区 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-title">科室列表</span>
          <w-tag size="small" effect="light" type="primary">共 {{ total }} 个科室</w-tag>
        </div>
        <div class="table-toolbar__right">
          <w-button text :icon="Refresh" @click="fetchList">刷新</w-button>
        </div>
      </div>

      <div v-loading="loading">
        <w-table :data="pagedList" row-key="id" border stripe empty-text="暂无科室数据" size="default">
          <w-table-column type="index" label="#" width="60" align="center" />
          <w-table-column prop="deptCode" label="科室编码" min-width="130" show-overflow-tooltip />
          <w-table-column prop="deptName" label="科室名称" min-width="150" show-overflow-tooltip />
          <w-table-column label="位置" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cell-meta"><Location class="cell-meta__icon" />{{ row.location || '—' }}</span>
            </template>
          </w-table-column>
          <w-table-column label="联系电话" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cell-meta"><Telephone class="cell-meta__icon" />{{ row.phone || '—' }}</span>
            </template>
          </w-table-column>
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag size="small" effect="light" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </w-tag>
            </template>
          </w-table-column>
          <w-table-column prop="sortOrder" label="排序" width="80" align="center" />
          <w-table-column v-if="isAdmin" label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <w-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</w-button>
              <w-button link :type="row.status === 1 ? 'danger' : 'success'" @click="handleStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </w-button>
            </template>
          </w-table-column>
        </w-table>
      </div>

      <div class="pagination-wrap">
        <w-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
        />
      </div>
    </w-card>

    <!-- 新增/编辑弹窗 -->
    <w-dialog
      v-model="dialogVisible"
      :title="editingId != null ? '编辑科室' : '新增科室'"
      width="560px"
      :close-on-click-modal="false"
    >
      <w-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <w-form-item label="科室名称" prop="deptName" required>
          <w-input v-model="form.deptName" placeholder="请输入科室名称" maxlength="50" clearable />
        </w-form-item>
        <w-form-item label="科室编码" prop="deptCode" required>
          <w-input v-model="form.deptCode" placeholder="请输入科室编码" maxlength="50" clearable />
        </w-form-item>
        <w-form-item label="所在位置" prop="location">
          <w-input v-model="form.location" placeholder="如：门诊 2F" maxlength="100" clearable />
        </w-form-item>
        <w-form-item label="联系电话" prop="phone">
          <w-input v-model="form.phone" placeholder="请输入联系电话" maxlength="30" clearable />
        </w-form-item>
        <w-form-item label="科室简介" prop="description">
          <w-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请输入科室简介"
          />
        </w-form-item>
        <w-form-item label="排序" prop="sortOrder">
          <w-input-number v-model="form.sortOrder" :min="0" :max="9999" :step="1" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="dialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="saving" @click="handleSave">保存</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.departments {
  min-height: 100%;
}

.dept-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 0;
}

.dept-head__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.query-actions {
  display: inline-flex;
  gap: 8px;
}

.table-toolbar {
  margin-bottom: 14px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}

.cell-meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--hospital-text-second);
}

.cell-meta__icon {
  flex-shrink: 0;
  color: var(--hospital-text-third);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
</style>
