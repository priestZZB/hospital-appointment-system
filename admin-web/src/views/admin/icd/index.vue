<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Edit, Plus, RefreshLeft, Search } from '@win-design-next/icons-vue'
import { createIcdApi, deleteIcdApi, getIcdPageApi, updateIcdApi } from '@/api/icd'
import type { IcdVO } from '@/types'

/* ================= 常用 ICD 分类（选择器可手输扩展） ================= */
const ICD_CATEGORIES = [
  '传染病和寄生虫病',
  '肿瘤',
  '内分泌营养代谢疾病',
  '血液及造血器官疾病',
  '精神行为障碍',
  '神经系统疾病',
  '眼和附器疾病',
  '耳和乳突疾病',
  '循环系统疾病',
  '呼吸系统疾病',
  '消化系统疾病',
  '皮肤疾病',
  '肌肉骨骼系统疾病',
  '泌尿生殖系统疾病',
  '妊娠分娩产褥期',
  '起源于围生期的某些情况',
  '先天畸形变形和染色体异常',
  '症状体征和临床所见',
  '损伤中毒和外因的某些其他后果',
  '影响健康状态的因素',
]

/* ================= 查询 ================= */
const loading = ref(false)
const list = ref<IcdVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const query = reactive<{ keyword: string; category: string }>({ keyword: '', category: '' })

function buildParams(): Record<string, unknown> {
  const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value }
  const kw = query.keyword.trim()
  if (kw) params.keyword = kw
  if (query.category) params.category = query.category
  return params
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getIcdPageApi(buildParams())
    list.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || 'ICD 字典加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchList()
}

function handleReset() {
  query.keyword = ''
  query.category = ''
  pageNo.value = 1
  fetchList()
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

/* ================= 展示辅助 ================= */
function commonText(v?: number): string {
  return v === 1 ? '常用' : '一般'
}

function statusText(v?: number): string {
  return v === 0 ? '停用' : '启用'
}

/* ================= 新增 / 编辑 ================= */
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<{
  icdCode: string
  icdName: string
  category: string
  isCommon: number
  status: number
}>({
  icdCode: '',
  icdName: '',
  category: '',
  isCommon: 0,
  status: 1,
})

function openCreate() {
  editingId.value = null
  Object.assign(form, { icdCode: '', icdName: '', category: '', isCommon: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row: IcdVO) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    icdCode: row.icdCode || '',
    icdName: row.icdName || '',
    category: row.category || '',
    isCommon: row.isCommon === 1 ? 1 : 0,
    status: row.status === 0 ? 0 : 1,
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.icdCode.trim() || !form.icdName.trim()) {
    WMessage.warning('请填写 ICD 编码与名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      icdCode: form.icdCode.trim(),
      icdName: form.icdName.trim(),
      category: form.category.trim() || undefined,
      isCommon: form.isCommon,
      status: form.status,
    }
    if (editingId.value != null) {
      await updateIcdApi({ id: editingId.value, ...payload })
      WMessage.success('ICD 条目已更新')
    } else {
      await createIcdApi(payload)
      WMessage.success('ICD 条目已新增')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ================= 删除 ================= */
async function handleDelete(row: IcdVO) {
  if (row.id == null) return
  try {
    await deleteIcdApi(row.id)
    WMessage.success('已删除')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '删除失败')
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">ICD 字典管理</h2>
      <p class="page-subtitle">ICD-10 诊断字典维护：编码 / 名称 / 分类 / 常用标记，供门诊诊断选择器远程搜索</p>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="关键字">
          <w-input
            v-model="query.keyword"
            placeholder="编码或名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </w-form-item>
        <w-form-item label="分类">
          <w-select
            v-model="query.category"
            placeholder="全部分类"
            clearable
            filterable
            allow-create
            style="width: 200px"
          >
            <w-option v-for="c in ICD_CATEGORIES" :key="c" :label="c" :value="c" />
          </w-select>
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</w-button>
          <w-button :icon="RefreshLeft" @click="handleReset">重置</w-button>
        </w-form-item>
      </w-form>
    </w-card>

    <!-- 列表区 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar list-toolbar">
        <div class="table-toolbar__left">
          <span class="table-total">共 {{ total }} 条 ICD 条目</span>
        </div>
        <div class="table-toolbar__right">
          <w-button type="primary" :icon="Plus" @click="openCreate">新增条目</w-button>
        </div>
      </div>

      <w-table :data="list" row-key="id" border stripe :loading="loading" empty-text="暂无 ICD 条目" size="default">
        <w-table-column prop="icdCode" label="ICD 编码" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.icdCode || '-' }}</template>
        </w-table-column>
        <w-table-column prop="icdName" label="诊断名称" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.icdName || '-' }}</template>
        </w-table-column>
        <w-table-column prop="category" label="分类" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </w-table-column>
        <w-table-column label="常用" width="90" align="center">
          <template #default="{ row }">
            <w-tag v-if="row.isCommon === 1" type="warning" effect="light" size="small">常用</w-tag>
            <span v-else class="text-muted">{{ commonText(row.isCommon) }}</span>
          </template>
        </w-table-column>
        <w-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <w-tag :type="row.status === 0 ? 'info' : 'success'" effect="light" size="small">
              {{ statusText(row.status) }}
            </w-tag>
          </template>
        </w-table-column>
        <w-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <w-button size="small" text type="primary" :icon="Edit" @click="openEdit(row)">编辑</w-button>
            <w-popconfirm
              title="确定删除该 ICD 条目？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              confirm-button-type="danger"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <w-button size="small" text type="danger">删除</w-button>
              </template>
            </w-popconfirm>
          </template>
        </w-table-column>
      </w-table>

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

    <!-- 新增 / 编辑弹窗 -->
    <w-dialog v-model="dialogVisible" :title="editingId != null ? '编辑 ICD 条目' : '新增 ICD 条目'" width="520px">
      <w-form :model="form" label-width="90px">
        <w-form-item label="ICD 编码" required>
          <w-input v-model="form.icdCode" placeholder="如 J00 / I10.x00" clearable />
        </w-form-item>
        <w-form-item label="诊断名称" required>
          <w-input v-model="form.icdName" placeholder="如 急性鼻咽炎［普通感冒］" clearable />
        </w-form-item>
        <w-form-item label="分类">
          <w-select v-model="form.category" placeholder="选择或输入分类" clearable filterable allow-create>
            <w-option v-for="c in ICD_CATEGORIES" :key="c" :label="c" :value="c" />
          </w-select>
        </w-form-item>
        <w-form-item label="常用标记">
          <w-switch v-model="form.isCommon" :active-value="1" :inactive-value="0" active-text="常用" inactive-text="一般" />
        </w-form-item>
        <w-form-item label="状态">
          <w-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
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
.query-form {
  flex-wrap: wrap;
}
.list-toolbar {
  margin-bottom: 14px;
}
.table-total {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.text-muted {
  color: var(--hospital-text-third);
  font-size: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
