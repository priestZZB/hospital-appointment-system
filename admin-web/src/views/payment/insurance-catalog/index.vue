<script setup lang="ts">
/**
 * 医保目录管理页（迭代11 H1，base=/api/admin/insurance-catalog）。
 * 目录映射列表（项目类型/引用ID/项目名/甲乙类 tag/报销比例/状态）+ 新增/编辑弹窗 + 类别/类型筛选。
 * itemType=CHARGED_ITEM 时提示填收费项目 id；乙类 reimburseRatio 为先行自付比例（如 15）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Edit, Plus, RefreshLeft, Search } from '@win-design-next/icons-vue'
import {
  createInsuranceCatalogApi,
  getInsuranceCatalogListApi,
  updateInsuranceCatalogApi,
} from '@/api/insurance'
import type { InsuranceCatalog } from '@/types'

/* ================= 选项常量 ================= */
const ITEM_TYPE_OPTIONS = [
  { value: 'REGISTER', label: '挂号' },
  { value: 'DRUG', label: '药品' },
  { value: 'EXAM', label: '检查' },
  { value: 'LAB', label: '检验' },
  { value: 'TREATMENT', label: '诊疗' },
  { value: 'MATERIAL', label: '卫材' },
  { value: 'CHARGED_ITEM', label: '收费项目' },
]
const CATALOG_CLASS_OPTIONS = [
  { value: 'A', label: '甲类' },
  { value: 'B', label: '乙类' },
  { value: 'C', label: '自费' },
]

function itemTypeText(v?: string): string {
  if (!v) return '-'
  return ITEM_TYPE_OPTIONS.find((o) => o.value === v.toUpperCase())?.label || v
}

/** 甲乙类 tag：A-绿（甲类）/ B-橙（乙类）/ C-灰（自费） */
function catalogClassMeta(v?: string): { label: string; tag: 'success' | 'warning' | 'info' } {
  const key = (v || '').toUpperCase()
  if (key === 'A') return { label: '甲类', tag: 'success' }
  if (key === 'B') return { label: '乙类', tag: 'warning' }
  if (key === 'C') return { label: '自费', tag: 'info' }
  return { label: v || '-', tag: 'info' }
}

/** 状态防御式展示：1/ACTIVE-启用，0/DEPRECATED-停用，其余 '-' */
function statusText(v?: number | string): string {
  if (v == null || v === '') return '-'
  if (typeof v === 'number') return v === 0 ? '停用' : '启用'
  const key = v.toUpperCase()
  if (key === 'ACTIVE') return '启用'
  if (key === 'DEPRECATED') return '停用'
  return v
}
function statusTagType(v?: number | string): 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  const text = statusText(v)
  return text === '启用' ? 'success' : 'info'
}

function fmtRatio(row: InsuranceCatalog): string {
  const v = row.reimburseRatio ?? row.reimburse_ratio
  if (v == null) return '-'
  return `${v}%`
}

/* ================= 查询 ================= */
const loading = ref(false)
const list = ref<InsuranceCatalog[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const query = reactive<{ itemType: string; catalogClass: string }>({ itemType: '', catalogClass: '' })

function buildParams(): Record<string, unknown> {
  const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value }
  if (query.itemType) params.itemType = query.itemType
  if (query.catalogClass) params.catalogClass = query.catalogClass
  return params
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getInsuranceCatalogListApi(buildParams())
    list.value = Array.isArray(page?.records) ? page.records : []
    total.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '医保目录加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchList()
}
function handleReset() {
  query.itemType = ''
  query.catalogClass = ''
  pageNo.value = 1
  fetchList()
}
function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

/* ================= 新增 / 编辑 ================= */
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<{
  itemType: string
  itemRefId: number | null
  itemName: string
  catalogClass: string
  reimburseRatio: number | null
}>({
  itemType: '',
  itemRefId: null,
  itemName: '',
  catalogClass: 'A',
  reimburseRatio: null,
})

/** CHARGED_ITEM 提示填收费项目 id；乙类提示先行自付比例 */
const refIdHint = computed(() =>
  form.itemType === 'CHARGED_ITEM' ? '项目类型为「收费项目」，引用 ID 请填收费项目字典的记录 ID（charge-item.id）' : '',
)
const ratioHint = computed(() => {
  if (form.catalogClass === 'B') return '乙类：先行自付比例，如填 15 表示先行自付 15%'
  if (form.catalogClass === 'A') return '甲类：全额纳入统筹，比例可留空或填 0'
  if (form.catalogClass === 'C') return '自费：全额自付，比例可留空或填 0'
  return ''
})

function openCreate() {
  editingId.value = null
  Object.assign(form, { itemType: '', itemRefId: null, itemName: '', catalogClass: 'A', reimburseRatio: null })
  dialogVisible.value = true
}

function openEdit(row: InsuranceCatalog) {
  editingId.value = row.id ?? null
  const refId = row.itemRefId ?? row.item_ref_id
  Object.assign(form, {
    itemType: row.itemType || row.item_type || '',
    itemRefId: typeof refId === 'number' ? refId : (refId != null && refId !== '' ? Number(refId) : null),
    itemName: row.itemName || row.item_name || '',
    catalogClass: (row.catalogClass || row.catalog_class || 'A').toUpperCase(),
    reimburseRatio: row.reimburseRatio ?? row.reimburse_ratio ?? null,
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.itemType) {
    WMessage.warning('请选择项目类型')
    return
  }
  if (form.itemRefId == null || !Number.isFinite(form.itemRefId) || form.itemRefId <= 0) {
    WMessage.warning('请填写正确的引用 ID')
    return
  }
  if (!form.itemName.trim()) {
    WMessage.warning('请填写项目名称')
    return
  }
  if (!form.catalogClass) {
    WMessage.warning('请选择甲乙类')
    return
  }
  saving.value = true
  try {
    const payload = {
      itemType: form.itemType,
      itemRefId: form.itemRefId,
      itemName: form.itemName.trim(),
      catalogClass: form.catalogClass,
      reimburseRatio:
        form.reimburseRatio != null && Number.isFinite(form.reimburseRatio) ? Number(form.reimburseRatio) : undefined,
    }
    if (editingId.value != null) {
      await updateInsuranceCatalogApi(editingId.value, payload)
      WMessage.success('医保目录映射已更新')
    } else {
      await createInsuranceCatalogApi(payload)
      WMessage.success('医保目录映射已新增')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
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
      <h2 class="page-title">医保目录管理</h2>
      <p class="page-subtitle">院内项目 ↔ 医保目录映射：甲/乙/自费归类与先行自付比例维护（H1）</p>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="项目类型">
          <w-select v-model="query.itemType" placeholder="全部类型" clearable style="width: 170px">
            <w-option v-for="o in ITEM_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="甲乙类">
          <w-select v-model="query.catalogClass" placeholder="全部类别" clearable style="width: 150px">
            <w-option v-for="o in CATALOG_CLASS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
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
          <span class="table-total">共 {{ total }} 条目录映射</span>
        </div>
        <div class="table-toolbar__right">
          <w-button type="primary" :icon="Plus" @click="openCreate">新增映射</w-button>
        </div>
      </div>

      <w-table :data="list" row-key="id" border stripe :loading="loading" empty-text="暂无医保目录映射" size="default">
        <w-table-column label="项目类型" width="120" align="center">
          <template #default="{ row }">{{ itemTypeText(row.itemType || row.item_type) }}</template>
        </w-table-column>
        <w-table-column label="引用 ID" width="100" align="center">
          <template #default="{ row }">{{ row.itemRefId ?? row.item_ref_id ?? '-' }}</template>
        </w-table-column>
        <w-table-column label="项目名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemName || row.item_name || '-' }}</template>
        </w-table-column>
        <w-table-column label="甲乙类" width="92" align="center">
          <template #default="{ row }">
            <w-tag :type="catalogClassMeta(row.catalogClass || row.catalog_class).tag" effect="light" size="small">
              {{ catalogClassMeta(row.catalogClass || row.catalog_class).label }}
            </w-tag>
          </template>
        </w-table-column>
        <w-table-column label="报销/先行自付比例" width="160" align="center">
          <template #default="{ row }">{{ fmtRatio(row) }}</template>
        </w-table-column>
        <w-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <w-tag
              v-if="row.status != null && row.status !== ''"
              :type="statusTagType(row.status)"
              effect="light"
              size="small"
            >
              {{ statusText(row.status) }}
            </w-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </w-table-column>
        <w-table-column label="创建时间" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createTime || row.create_time || '-' }}</template>
        </w-table-column>
        <w-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <w-button size="small" text type="primary" :icon="Edit" @click="openEdit(row)">编辑</w-button>
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
    <w-dialog
      v-model="dialogVisible"
      :title="editingId != null ? '编辑医保目录映射' : '新增医保目录映射'"
      width="540px"
    >
      <w-form :model="form" label-width="100px">
        <w-form-item label="项目类型" required>
          <w-select v-model="form.itemType" placeholder="请选择项目类型">
            <w-option v-for="o in ITEM_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="引用 ID" required>
          <w-input-number
            v-model="form.itemRefId"
            :min="1"
            :controls="false"
            placeholder="对应业务记录 ID"
            style="width: 100%"
          />
          <div v-if="refIdHint" class="field-hint">{{ refIdHint }}</div>
        </w-form-item>
        <w-form-item label="项目名称" required>
          <w-input v-model="form.itemName" placeholder="如 阿莫西林胶囊" clearable />
        </w-form-item>
        <w-form-item label="甲乙类" required>
          <w-select v-model="form.catalogClass" placeholder="请选择类别">
            <w-option v-for="o in CATALOG_CLASS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="比例(%)">
          <w-input-number v-model="form.reimburseRatio" :min="0" :max="100" :controls="false" style="width: 100%" />
          <div v-if="ratioHint" class="field-hint">{{ ratioHint }}</div>
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
.field-hint {
  width: 100%;
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--w3-color-warning, #ff8c00);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
