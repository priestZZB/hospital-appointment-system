<script setup lang="ts">
/**
 * 收费项目字典页（迭代11 H4，base=/api/admin/charge-item）。
 * CRUD 表格 + 类别/关键字搜索 + 调价弹窗（新价+原因）+ 启停开关。
 * 价格状态 tag：ACTIVE-绿（正常）/ ADJUSTED-橙（已调价）/ DEPRECATED-灰（已停用）。
 */
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Edit, Plus, RefreshLeft, Search } from '@win-design-next/icons-vue'
import {
  adjustChargeItemPriceApi,
  createChargeItemApi,
  getChargeItemListApi,
  setChargeItemStatusApi,
  updateChargeItemApi,
} from '@/api/insurance'
import type { ChargeItem } from '@/types'

/* ================= 类别选项（与医保目录 itemType 同族） ================= */
const CATEGORY_OPTIONS = [
  { value: 'REGISTER', label: '挂号费' },
  { value: 'DRUG', label: '药品费' },
  { value: 'EXAM', label: '检查费' },
  { value: 'LAB', label: '检验费' },
  { value: 'TREATMENT', label: '诊疗费' },
  { value: 'MATERIAL', label: '卫材费' },
]
function categoryText(v?: string): string {
  if (!v) return '-'
  return CATEGORY_OPTIONS.find((o) => o.value === v.toUpperCase())?.label || v
}

/** 价格状态 tag：ACTIVE 绿 / ADJUSTED 橙 / DEPRECATED 灰（字段驼峰/下划线/复用 status 防御式兼容） */
function priceStatusMeta(row: ChargeItem): { label: string; tag: 'success' | 'warning' | 'info' } {
  const key = (row.priceStatus || row.price_status || row.status || 'ACTIVE').toUpperCase()
  if (key === 'ADJUSTED') return { label: '已调价', tag: 'warning' }
  if (key === 'DEPRECATED') return { label: '已停用', tag: 'info' }
  return { label: '正常', tag: 'success' }
}

/** 开关当前值：DEPRECATED 视为停用，其余视为启用 */
function statusOf(row: ChargeItem): 'ACTIVE' | 'DEPRECATED' {
  const key = (row.status || row.priceStatus || row.price_status || 'ACTIVE').toUpperCase()
  return key === 'DEPRECATED' ? 'DEPRECATED' : 'ACTIVE'
}

function fmtMoney(v?: number | null): string {
  if (v == null) return '-'
  return `¥${Number(v).toFixed(2)}`
}

/* ================= 查询 ================= */
const loading = ref(false)
const list = ref<ChargeItem[]>([])
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
    const page = await getChargeItemListApi(buildParams())
    list.value = Array.isArray(page?.records) ? page.records : []
    total.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '收费项目加载失败')
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

/* ================= 新增 / 编辑 ================= */
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<{
  itemCode: string
  itemName: string
  category: string
  unit: string
  unitPrice: number | null
}>({
  itemCode: '',
  itemName: '',
  category: '',
  unit: '',
  unitPrice: null,
})

function openCreate() {
  editingId.value = null
  Object.assign(form, { itemCode: '', itemName: '', category: '', unit: '', unitPrice: null })
  dialogVisible.value = true
}

function openEdit(row: ChargeItem) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    itemCode: row.itemCode || row.item_code || '',
    itemName: row.itemName || row.item_name || '',
    category: row.category || '',
    unit: row.unit || '',
    unitPrice: typeof row.unitPrice === 'number' ? row.unitPrice : (row.unit_price ?? null),
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.itemCode.trim() || !form.itemName.trim()) {
    WMessage.warning('请填写项目编码与名称')
    return
  }
  if (!form.category) {
    WMessage.warning('请选择项目类别')
    return
  }
  if (form.unitPrice == null || Number.isNaN(form.unitPrice) || form.unitPrice < 0) {
    WMessage.warning('请填写正确的单价')
    return
  }
  saving.value = true
  try {
    const payload = {
      itemCode: form.itemCode.trim(),
      itemName: form.itemName.trim(),
      category: form.category,
      unit: form.unit.trim() || undefined,
      unitPrice: Number(form.unitPrice),
    }
    if (editingId.value != null) {
      await updateChargeItemApi(editingId.value, payload)
      WMessage.success('收费项目已更新')
    } else {
      await createChargeItemApi(payload)
      WMessage.success('收费项目已新增')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ================= 启停开关 ================= */
const statusPendingId = ref<number | null>(null)

async function handleStatusChange(row: ChargeItem, next: 'ACTIVE' | 'DEPRECATED') {
  if (row.id == null) {
    WMessage.warning('记录 ID 缺失')
    fetchList()
    return
  }
  statusPendingId.value = row.id
  try {
    await setChargeItemStatusApi(row.id, next)
    WMessage.success(next === 'ACTIVE' ? '已启用' : '已停用')
  } catch (e) {
    WMessage.error((e as Error).message || '状态更新失败')
  } finally {
    statusPendingId.value = null
    fetchList()
  }
}

/* ================= 调价 ================= */
const adjustVisible = ref(false)
const adjustSaving = ref(false)
const adjustTarget = ref<ChargeItem | null>(null)
const adjustForm = reactive<{ newPrice: number | null; reason: string }>({ newPrice: null, reason: '' })

function openAdjust(row: ChargeItem) {
  adjustTarget.value = row
  adjustForm.newPrice = typeof row.unitPrice === 'number' ? row.unitPrice : (row.unit_price ?? null)
  adjustForm.reason = ''
  adjustVisible.value = true
}

async function handleAdjustSubmit() {
  const target = adjustTarget.value
  if (!target || target.id == null) {
    WMessage.warning('记录 ID 缺失')
    return
  }
  if (adjustForm.newPrice == null || Number.isNaN(adjustForm.newPrice) || adjustForm.newPrice < 0) {
    WMessage.warning('请填写正确的新价格')
    return
  }
  if (!adjustForm.reason.trim()) {
    WMessage.warning('请填写调价原因')
    return
  }
  adjustSaving.value = true
  try {
    await adjustChargeItemPriceApi(target.id, adjustForm.newPrice, adjustForm.reason.trim())
    WMessage.success('调价成功')
    adjustVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '调价失败')
  } finally {
    adjustSaving.value = false
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
      <h2 class="page-title">收费项目字典</h2>
      <p class="page-subtitle">收费项目维护：编码 / 类别 / 单价 · 调价留痕 · 启停控制（H4）</p>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="类别">
          <w-select v-model="query.category" placeholder="全部类别" clearable style="width: 170px">
            <w-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="关键字">
          <w-input
            v-model="query.keyword"
            placeholder="编码或名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
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
          <span class="table-total">共 {{ total }} 条收费项目</span>
        </div>
        <div class="table-toolbar__right">
          <w-button type="primary" :icon="Plus" @click="openCreate">新增项目</w-button>
        </div>
      </div>

      <w-table :data="list" row-key="id" border stripe :loading="loading" empty-text="暂无收费项目" size="default">
        <w-table-column label="项目编码" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemCode || row.item_code || '-' }}</template>
        </w-table-column>
        <w-table-column label="项目名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemName || row.item_name || '-' }}</template>
        </w-table-column>
        <w-table-column label="类别" width="110" align="center">
          <template #default="{ row }">{{ categoryText(row.category) }}</template>
        </w-table-column>
        <w-table-column label="单位" width="80" align="center">
          <template #default="{ row }">{{ row.unit || '-' }}</template>
        </w-table-column>
        <w-table-column label="现行单价" width="116" align="right">
          <template #default="{ row }">
            <span class="price">{{ fmtMoney(row.unitPrice ?? row.unit_price) }}</span>
          </template>
        </w-table-column>
        <w-table-column label="价格状态" width="100" align="center">
          <template #default="{ row }">
            <w-tag :type="priceStatusMeta(row).tag" effect="light" size="small">
              {{ priceStatusMeta(row).label }}
            </w-tag>
          </template>
        </w-table-column>
        <w-table-column label="启停" width="92" align="center">
          <template #default="{ row }">
            <w-switch
              :model-value="statusOf(row)"
              active-value="ACTIVE"
              inactive-value="DEPRECATED"
              :disabled="statusPendingId === row.id || row.id == null"
              @change="(v: 'ACTIVE' | 'DEPRECATED') => handleStatusChange(row, v)"
            />
          </template>
        </w-table-column>
        <w-table-column label="创建时间" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createTime || row.create_time || '-' }}</template>
        </w-table-column>
        <w-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <w-button size="small" text type="primary" :icon="Edit" @click="openEdit(row)">编辑</w-button>
            <w-button size="small" text type="warning" @click="openAdjust(row)">调价</w-button>
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
    <w-dialog v-model="dialogVisible" :title="editingId != null ? '编辑收费项目' : '新增收费项目'" width="520px">
      <w-form :model="form" label-width="90px">
        <w-form-item label="项目编码" required>
          <w-input v-model="form.itemCode" placeholder="如 REG-01 / EXAM-CT" clearable />
        </w-form-item>
        <w-form-item label="项目名称" required>
          <w-input v-model="form.itemName" placeholder="如 普通门诊诊察费" clearable />
        </w-form-item>
        <w-form-item label="类别" required>
          <w-select v-model="form.category" placeholder="请选择类别">
            <w-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="单位">
          <w-input v-model="form.unit" placeholder="如 次 / 盒 / 支" clearable />
        </w-form-item>
        <w-form-item label="单价（元）" required>
          <w-input-number v-model="form.unitPrice" :min="0" :precision="2" style="width: 100%" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="dialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="saving" @click="handleSave">保存</w-button>
      </template>
    </w-dialog>

    <!-- 调价弹窗（新价 + 调价原因） -->
    <w-dialog v-model="adjustVisible" title="价格调整" width="460px">
      <w-form :model="adjustForm" label-width="90px">
        <w-form-item label="项目">
          <span class="adjust-item-name">
            {{ adjustTarget?.itemName || adjustTarget?.item_name || adjustTarget?.itemCode || '-' }}
          </span>
        </w-form-item>
        <w-form-item label="现行单价">
          <span class="price">{{ fmtMoney(adjustTarget?.unitPrice ?? adjustTarget?.unit_price) }}</span>
        </w-form-item>
        <w-form-item label="新价格" required>
          <w-input-number v-model="adjustForm.newPrice" :min="0" :precision="2" style="width: 100%" />
        </w-form-item>
        <w-form-item label="调价原因" required>
          <w-input
            v-model="adjustForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写调价原因（如物价文件号）"
          />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="adjustVisible = false">取消</w-button>
        <w-button type="primary" :loading="adjustSaving" @click="handleAdjustSubmit">确认调价</w-button>
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
.price {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--w3-color-primary);
}
.adjust-item-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
