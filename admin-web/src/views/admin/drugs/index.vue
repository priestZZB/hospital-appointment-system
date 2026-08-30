<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import { Plus, Refresh, Search } from '@win-design-next/icons-vue'
import {
  createDrugApi,
  dispenseApi,
  getDispenseListApi,
  getDrugApi,
  getDrugPageApi,
  getInventoryApi,
  inventoryAdjustApi,
  inventoryInboundApi,
  inventoryOutboundApi,
  reviewDispenseApi,
  searchDrugsApi,
  updateDrugApi,
} from '@/api/medsupply'
import { useUserStore } from '@/stores/user'
import type { Drug, DrugDispense, DrugInventory } from '@/types'

const userStore = useUserStore()

const tab = ref('drugs')

interface DrugForm {
  drugCode: string
  drugName: string
  genericName: string
  specification: string
  dosageForm: string
  manufacturer: string
  referencePrice: number
  unit: string
  description: string
  status: number
}

interface DrugOption {
  value: number
  label: string
}

// ---------------- 药品目录 ----------------
const drugLoading = ref(false)
const drugs = ref<Drug[]>([])
const drugTotal = ref(0)
const drugPage = ref(1)
const drugSize = ref(10)
const drugQuery = reactive({ keyword: '' })
const drugDialog = ref(false)
const drugEditing = ref<Drug | null>(null)
const drugForm = reactive<DrugForm>({
  drugCode: '',
  drugName: '',
  genericName: '',
  specification: '',
  dosageForm: '',
  manufacturer: '',
  referencePrice: 0,
  unit: '盒',
  description: '',
  status: 1,
})

// ---------------- 库存管理 ----------------
const invLoading = ref(false)
const inventory = ref<DrugInventory[]>([])
const invTotal = ref(0)
const invPage = ref(1)
const invSize = ref(10)
const invQuery = reactive({ keyword: '', lowStock: false })
const invDialogVisible = ref(false)
const invDialogType = ref<'inbound' | 'outbound' | 'adjust'>('inbound')
const invForm = reactive({ drugId: undefined as number | undefined, quantity: 1, remark: '' })
const drugOptions = ref<DrugOption[]>([])
const drugSearchLoading = ref(false)

// ---------------- 发药审核 ----------------
const dispLoading = ref(false)
const dispenseList = ref<DrugDispense[]>([])
const dispTotal = ref(0)
const dispPage = ref(1)
const dispSize = ref(10)
const dispStatus = ref('')

// 四查十对审核弹窗
const FOUR_CHECK_TEN_PAIRS = [
  { key: 'name', label: '对姓名（患者身份核对）' },
  { key: 'drugName', label: '对药名' },
  { key: 'specification', label: '对规格' },
  { key: 'dosage', label: '对剂量' },
  { key: 'usage', label: '对用法（给药途径）' },
  { key: 'frequency', label: '对频次/用量' },
  { key: 'diagnosis', label: '对临床诊断' },
  { key: 'incompatibility', label: '配伍禁忌核查' },
  { key: 'rationality', label: '用药合理性核查' },
  { key: 'allergy', label: '过敏史核查' },
] as const
const reviewDialogVisible = ref(false)
const reviewingRow = ref<DrugDispense | null>(null)
const reviewAction = ref<'APPROVE' | 'REJECT'>('APPROVE')
const reviewComment = ref('')
const reviewChecks = ref<Record<string, boolean>>({})
const reviewSubmitting = ref(false)

const invDialogTitle = computed(() => {
  if (invDialogType.value === 'inbound') return '药品入库'
  if (invDialogType.value === 'outbound') return '药品出库'
  return '盘点调整'
})

const dispStatusMap: Record<
  string,
  { text: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }
> = {
  PENDING_REVIEW: { text: '待审核', type: 'warning' },
  REVIEW_PASSED: { text: '审核通过', type: 'primary' },
  REVIEW_REJECTED: { text: '审核驳回', type: 'danger' },
  DISPENSED: { text: '已发药', type: 'success' },
}

function dispenseStatus(row: DrugDispense) {
  return dispStatusMap[row.status || ''] || { text: row.status || '-', type: 'info' as const }
}

async function fetchDrugs() {
  drugLoading.value = true
  try {
    const page = await getDrugPageApi({
      keyword: drugQuery.keyword,
      pageNo: drugPage.value,
      pageSize: drugSize.value,
    })
    drugs.value = page.records || []
    drugTotal.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '药品列表加载失败')
  } finally {
    drugLoading.value = false
  }
}

function resetDrugForm() {
  Object.assign(drugForm, {
    drugCode: '',
    drugName: '',
    genericName: '',
    specification: '',
    dosageForm: '',
    manufacturer: '',
    referencePrice: 0,
    unit: '盒',
    description: '',
    status: 1,
  })
}

function applyDrugForm(data: Drug) {
  drugForm.drugCode = data.drugCode || ''
  drugForm.drugName = data.drugName || ''
  drugForm.genericName = data.genericName || ''
  drugForm.specification = data.specification || ''
  drugForm.dosageForm = data.dosageForm || ''
  drugForm.manufacturer = data.manufacturer || ''
  drugForm.referencePrice = data.referencePrice ?? 0
  drugForm.unit = data.unit || '盒'
  drugForm.description = data.description || ''
  drugForm.status = data.status ?? 1
}

async function openDrugDialog(row?: Drug) {
  drugEditing.value = row ?? null
  if (row) {
    try {
      const full = await getDrugApi(row.id as number)
      applyDrugForm(full)
    } catch {
      applyDrugForm(row)
    }
  } else {
    resetDrugForm()
  }
  drugDialog.value = true
}

async function saveDrug() {
  if (!drugForm.drugCode.trim() || !drugForm.drugName.trim()) {
    WMessage.warning('药品编码与名称必填')
    return
  }
  const editing = drugEditing.value
  try {
    if (editing?.id) {
      await updateDrugApi(editing.id, { ...drugForm })
    } else {
      await createDrugApi({ ...drugForm })
    }
    WMessage.success('保存成功')
    drugDialog.value = false
    fetchDrugs()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  }
}

async function fetchInventory() {
  invLoading.value = true
  try {
    const page = await getInventoryApi({
      keyword: invQuery.keyword,
      lowStock: invQuery.lowStock || undefined,
      pageNo: invPage.value,
      pageSize: invSize.value,
    })
    inventory.value = page.records || []
    invTotal.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '库存列表加载失败')
  } finally {
    invLoading.value = false
  }
}

async function searchDrugOptions(keyword: string) {
  drugSearchLoading.value = true
  try {
    const page = await searchDrugsApi({ keyword, pageNo: 1, pageSize: 50 })
    drugOptions.value = (page.records || []).map((d) => ({
      value: d.id as number,
      label: d.drugName ? `${d.drugName}（${d.drugCode || d.id}）` : String(d.id),
    }))
  } catch {
    drugOptions.value = []
  } finally {
    drugSearchLoading.value = false
  }
}

function openInvDialog(type: 'inbound' | 'outbound' | 'adjust', row?: DrugInventory) {
  invDialogType.value = type
  invForm.drugId = row?.drugId
  invForm.quantity = 1
  invForm.remark = ''
  if (row?.drugId) {
    drugOptions.value = [{ value: row.drugId, label: row.drugName || String(row.drugId) }]
  } else {
    drugOptions.value = []
    searchDrugOptions('')
  }
  invDialogVisible.value = true
}

async function saveInventory() {
  if (!invForm.drugId || !invForm.quantity) {
    WMessage.warning('药品与数量必填')
    return
  }
  const payload = { drugId: invForm.drugId, quantity: invForm.quantity, remark: invForm.remark }
  try {
    if (invDialogType.value === 'inbound') {
      await inventoryInboundApi(payload)
    } else if (invDialogType.value === 'outbound') {
      await inventoryOutboundApi(payload)
    } else {
      await inventoryAdjustApi(payload)
    }
    WMessage.success('操作成功')
    invDialogVisible.value = false
    fetchInventory()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

async function fetchDispense() {
  dispLoading.value = true
  try {
    const page = await getDispenseListApi({
      status: dispStatus.value || undefined,
      pageNo: dispPage.value,
      pageSize: dispSize.value,
    })
    dispenseList.value = page.records || []
    dispTotal.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '发药记录加载失败')
  } finally {
    dispLoading.value = false
  }
}

function handleReview(row: DrugDispense, action: 'APPROVE' | 'REJECT') {
  reviewingRow.value = row
  reviewAction.value = action
  reviewComment.value = action === 'APPROVE' ? '审核通过' : '审核驳回'
  reviewChecks.value = {}
  reviewDialogVisible.value = true
}

async function confirmReview() {
  const row = reviewingRow.value
  if (!row) return
  // 通过时必须完成四查十对核查（至少勾选药品/剂量/用法/诊断四项核心项）
  if (reviewAction.value === 'APPROVE') {
    const required = ['drugName', 'dosage', 'usage', 'diagnosis']
    const missing = required.filter((k) => !reviewChecks.value[k])
    if (missing.length) {
      WMessage.warning('审核通过前请完成「对药名 / 对剂量 / 对用法 / 对诊断」四项核心核对')
      return
    }
  }
  const reviewCheck = JSON.stringify(
    FOUR_CHECK_TEN_PAIRS.map((c) => ({ item: c.label, result: reviewChecks.value[c.key] ? 'PASS' : 'SKIP' })),
  )
  reviewSubmitting.value = true
  try {
    await reviewDispenseApi(row.prescriptionId, {
      action: reviewAction.value,
      reviewComment: reviewComment.value,
      reviewCheck,
    })
    WMessage.success('审核完成')
    reviewDialogVisible.value = false
    fetchDispense()
  } catch (e) {
    WMessage.error((e as Error).message || '审核失败')
  } finally {
    reviewSubmitting.value = false
  }
}

async function handleDispense(row: DrugDispense) {
  try {
    await WMessageBox.confirm('确定发药确认吗？将乐观锁扣减库存', '发药确认', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await dispenseApi(row.prescriptionId)
    WMessage.success('发药完成')
    fetchDispense()
  } catch (e) {
    WMessage.error((e as Error).message || '发药失败')
  }
}

onMounted(() => {
  fetchDrugs()
  fetchInventory()
  fetchDispense()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">药品管理</h2>
      <p class="page-subtitle">药品目录、库存动态与处方发药审核的集中管理</p>
    </div>

    <!-- 主体：三 Tab -->
    <w-card shadow="never" class="hospital-card">
      <w-tabs v-model="tab">
        <!-- 药品目录 -->
        <w-tab-pane label="药品目录" name="drugs">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-input
                  v-model="drugQuery.keyword"
                  placeholder="药品名称 / 编码 / 通用名"
                  clearable
                  style="width: 240px"
                  @keyup.enter="drugPage = 1; fetchDrugs()"
                  @clear="drugPage = 1; fetchDrugs()"
                />
                <w-button type="primary" :icon="Search" @click="drugPage = 1; fetchDrugs()">查询</w-button>
                <w-button v-if="userStore.isAdmin" type="primary" plain :icon="Plus" @click="openDrugDialog()">新增药品</w-button>
              </div>
            </div>

            <w-table :data="drugs" row-key="id" border stripe :loading="drugLoading" empty-text="暂无数据" size="default">
              <w-table-column prop="drugCode" label="编码" min-width="110" show-overflow-tooltip />
              <w-table-column prop="drugName" label="名称" min-width="120" show-overflow-tooltip />
              <w-table-column prop="genericName" label="通用名" min-width="110" show-overflow-tooltip />
              <w-table-column prop="specification" label="规格" min-width="90" show-overflow-tooltip />
              <w-table-column prop="dosageForm" label="剂型" width="80" show-overflow-tooltip />
              <w-table-column prop="manufacturer" label="生产厂家" min-width="150" show-overflow-tooltip />
              <w-table-column label="参考价" width="100" align="right">
                <template #default="{ row }">
                  <span class="price">¥{{ (row.referencePrice ?? 0).toFixed(2) }}</span>
                </template>
              </w-table-column>
              <w-table-column prop="unit" label="单位" width="70" align="center" />
              <w-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <w-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" size="small">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column v-if="userStore.isAdmin" label="操作" width="80" fixed="right" align="center">
                <template #default="{ row }">
                  <w-button size="small" link type="primary" @click="openDrugDialog(row)">编辑</w-button>
                </template>
              </w-table-column>
            </w-table>

            <div class="pager">
              <w-pagination
                v-model:current-page="drugPage"
                v-model:page-size="drugSize"
                :total="drugTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchDrugs"
                @size-change="drugPage = 1; fetchDrugs()"
              />
            </div>
          </div>
        </w-tab-pane>

        <!-- 库存管理 -->
        <w-tab-pane label="库存管理" name="inventory">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-input
                  v-model="invQuery.keyword"
                  placeholder="药品关键字"
                  clearable
                  style="width: 200px"
                  @keyup.enter="invPage = 1; fetchInventory()"
                  @clear="invPage = 1; fetchInventory()"
                />
                <w-checkbox v-model="invQuery.lowStock">仅看低库存</w-checkbox>
                <w-button type="primary" :icon="Search" @click="invPage = 1; fetchInventory()">查询</w-button>
              </div>
            </div>

            <w-table :data="inventory" row-key="id" border stripe :loading="invLoading" empty-text="暂无数据" size="default">
              <w-table-column prop="drugId" label="药品ID" width="90" align="center" />
              <w-table-column prop="drugName" label="药品" min-width="140" show-overflow-tooltip />
              <w-table-column label="当前库存" width="110" align="center">
                <template #default="{ row }">
                  <w-tag
                    :type="row.currentStock <= (row.minThreshold || 0) ? 'danger' : 'success'"
                    effect="light"
                    size="small"
                  >
                    {{ row.currentStock }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="minThreshold" label="最低阈值" width="100" align="center" />
              <w-table-column prop="lastStockinTime" label="最近入库" min-width="150" show-overflow-tooltip />
              <w-table-column prop="lastStockoutTime" label="最近出库" min-width="150" show-overflow-tooltip />
              <w-table-column v-if="userStore.isAdmin" label="操作" width="200" fixed="right" align="center">
                <template #default="{ row }">
                  <w-button size="small" link type="success" @click="openInvDialog('inbound', row)">入库</w-button>
                  <w-button size="small" link type="warning" @click="openInvDialog('outbound', row)">出库</w-button>
                  <w-button size="small" link @click="openInvDialog('adjust', row)">盘点</w-button>
                </template>
              </w-table-column>
            </w-table>

            <div class="pager">
              <w-pagination
                v-model:current-page="invPage"
                v-model:page-size="invSize"
                :total="invTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchInventory"
                @size-change="invPage = 1; fetchInventory()"
              />
            </div>
          </div>
        </w-tab-pane>

        <!-- 发药审核 -->
        <w-tab-pane label="发药审核" name="dispense">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-select
                  v-model="dispStatus"
                  placeholder="全部状态"
                  clearable
                  style="width: 180px"
                  @change="dispPage = 1; fetchDispense()"
                >
                  <w-option label="待审核" value="PENDING_REVIEW" />
                  <w-option label="审核通过" value="REVIEW_PASSED" />
                  <w-option label="审核驳回" value="REVIEW_REJECTED" />
                  <w-option label="已发药" value="DISPENSED" />
                </w-select>
                <w-button :icon="Refresh" @click="dispPage = 1; fetchDispense()">刷新</w-button>
              </div>
            </div>

            <w-table :data="dispenseList" row-key="id" border size="default" stripe :loading="dispLoading" empty-text="暂无数据">
              <w-table-column prop="prescriptionId" label="处方ID" width="100" align="center" />
              <w-table-column prop="patientId" label="患者ID" width="90" align="center" />
              <w-table-column label="状态" width="110" align="center">
                <template #default="{ row }">
                  <w-tag :type="dispenseStatus(row).type" effect="light" size="small">
                    {{ dispenseStatus(row).text }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="reviewComment" label="审核意见" min-width="140" show-overflow-tooltip />
              <w-table-column prop="reviewTime" label="审核时间" min-width="150" show-overflow-tooltip />
              <w-table-column prop="dispenseTime" label="发药时间" min-width="150" show-overflow-tooltip />
              <w-table-column v-if="userStore.isAdmin || userStore.isPharmacist" label="操作" width="180" fixed="right" align="center">
                <template #default="{ row }">
                  <template v-if="row.status === 'PENDING_REVIEW'">
                    <w-button size="small" link type="success" @click="handleReview(row, 'APPROVE')">通过</w-button>
                    <w-button size="small" link type="danger" @click="handleReview(row, 'REJECT')">驳回</w-button>
                  </template>
                  <w-button
                    v-else-if="row.status === 'REVIEW_PASSED'"
                    size="small"
                    link
                    type="primary"
                    @click="handleDispense(row)"
                  >
                    发药确认
                  </w-button>
                  <span v-else class="muted">-</span>
                </template>
              </w-table-column>
            </w-table>

            <div class="pager">
              <w-pagination
                v-model:current-page="dispPage"
                v-model:page-size="dispSize"
                :total="dispTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchDispense"
                @size-change="dispPage = 1; fetchDispense()"
              />
            </div>
          </div>
        </w-tab-pane>
      </w-tabs>
    </w-card>

    <!-- 药品新增 / 编辑弹窗 -->
    <w-dialog
      v-model="drugDialog"
      :title="drugEditing ? '编辑药品' : '新增药品'"
      width="640px"
      :close-on-click-modal="false"
    >
      <w-form :model="drugForm" label-width="90px">
        <w-row :gutter="16">
          <w-col :span="12">
            <w-form-item label="药品编码" required>
              <w-input v-model="drugForm.drugCode" placeholder="请输入编码" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="药品名称" required>
              <w-input v-model="drugForm.drugName" placeholder="请输入名称" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="通用名">
              <w-input v-model="drugForm.genericName" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="规格">
              <w-input v-model="drugForm.specification" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="剂型">
              <w-input v-model="drugForm.dosageForm" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="生产厂家">
              <w-input v-model="drugForm.manufacturer" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="参考价">
              <w-input-number v-model="drugForm.referencePrice" :min="0" :precision="2" style="width: 100%" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="单位">
              <w-input v-model="drugForm.unit" />
            </w-form-item>
          </w-col>
          <w-col :span="12">
            <w-form-item label="状态">
              <w-switch
                v-model="drugForm.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
            </w-form-item>
          </w-col>
          <w-col :span="24">
            <w-form-item label="描述">
              <w-input v-model="drugForm.description" type="textarea" :rows="3" placeholder="药品描述" />
            </w-form-item>
          </w-col>
        </w-row>
      </w-form>
      <template #footer>
        <w-button @click="drugDialog = false">取消</w-button>
        <w-button type="primary" @click="saveDrug">保存</w-button>
      </template>
    </w-dialog>

    <!-- 库存入库 / 出库 / 盘点弹窗 -->
    <w-dialog v-model="invDialogVisible" :title="invDialogTitle" width="460px" :close-on-click-modal="false">
      <w-form :model="invForm" label-width="80px">
        <w-form-item label="药品" required>
          <w-select
            v-model="invForm.drugId"
            filterable
            remote
            :remote-method="searchDrugOptions"
            :loading="drugSearchLoading"
            placeholder="搜索并选择药品"
            style="width: 100%"
          >
            <w-option v-for="opt in drugOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="数量" required>
          <w-input-number v-model="invForm.quantity" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="备注">
          <w-input v-model="invForm.remark" placeholder="备注（选填）" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="invDialogVisible = false">取消</w-button>
        <w-button type="primary" @click="saveInventory">确认</w-button>
      </template>
    </w-dialog>

    <!-- 四查十对审核弹窗 -->
    <w-dialog
      v-model="reviewDialogVisible"
      :title="reviewAction === 'APPROVE' ? '处方审核 · 四查十对' : '处方审核 · 驳回'"
      width="560px"
      :close-on-click-modal="false"
    >
      <div class="review-body">
        <p class="review-body__tip">
          请逐项核对处方，通过时至少完成「对药名 / 对剂量 / 对用法 / 对诊断」四项核心核查。
        </p>
        <div class="review-checks">
          <w-checkbox
            v-for="c in FOUR_CHECK_TEN_PAIRS"
            :key="c.key"
            v-model="reviewChecks[c.key]"
            class="review-checks__item"
          >
            {{ c.label }}
          </w-checkbox>
        </div>
        <w-input
          v-model="reviewComment"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="审核意见"
        />
      </div>
      <template #footer>
        <w-button @click="reviewDialogVisible = false">取消</w-button>
        <w-button
          :type="reviewAction === 'APPROVE' ? 'success' : 'danger'"
          :loading="reviewSubmitting"
          @click="confirmReview"
        >
          {{ reviewAction === 'APPROVE' ? '审核通过' : '确认驳回' }}
        </w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.tab-body {
  padding-top: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.price {
  font-variant-numeric: tabular-nums;
  color: var(--hospital-text-main);
}

.muted {
  color: var(--hospital-text-third);
}

.review-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.review-body__tip {
  margin: 0;
  font-size: 13px;
  color: var(--hospital-text-second);
  line-height: 1.6;
}
.review-checks {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 12px;
  padding: 12px;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
  background: var(--w3-fill-color-blank, #fff);
}
.review-checks__item {
  margin: 0;
}
</style>
