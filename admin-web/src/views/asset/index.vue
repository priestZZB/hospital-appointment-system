<script setup lang="ts">
/**
 * 后台资产管理页（迭代14，/asset）。
 * Tab1 耗材管理（L1 字典/出入库/低库存预警）；Tab2 设备台账（L2 状态/维保）。
 */
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Plus, Search } from '@win-design-next/icons-vue'
import {
  consumableStockApi,
  createConsumableApi,
  createEquipmentApi,
  equipmentMaintainApi,
  equipmentStatusApi,
  getConsumableListApi,
  getConsumableRecordsApi,
  getEquipmentListApi,
  getLowStockApi,
} from '@/api/emergency'
import type { ConsumableRow, EquipmentRow } from '@/api/emergency'

const activeTab = ref<'consumable' | 'equipment'>('consumable')
function fmt(v?: string | null): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

/* ================= Tab1 耗材 ================= */
const consLoading = ref(false)
const consList = ref<ConsumableRow[]>([])
const consTotal = ref(0)
const consPageNo = ref(1)
const consQuery = reactive<{ status: string; keyword: string }>({ status: '', keyword: '' })
const lowStock = ref<ConsumableRow[]>([])
const consDialog = ref(false)
const consSaving = ref(false)
const consForm = reactive<{ code: string; name: string; specification: string; unit: string; price: number | null; stock: number | null; safetyStock: number | null }>({
  code: '',
  name: '',
  specification: '',
  unit: '支',
  price: null,
  stock: 0,
  safetyStock: 0,
})
const stockDialog = ref(false)
const stockSaving = ref(false)
const stockTarget = ref<ConsumableRow | null>(null)
const stockForm = reactive<{ type: 'IN' | 'OUT'; quantity: number | null; remark: string }>({ type: 'IN', quantity: null, remark: '' })
const recordLoading = ref(false)
const recordList = ref<Array<Record<string, unknown>>>([])
const recordTotal = ref(0)
const recordPageNo = ref(1)

async function fetchCons() {
  consLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: consPageNo.value, pageSize: 10 }
    if (consQuery.status) params.status = consQuery.status
    const kw = consQuery.keyword.trim()
    if (kw) params.keyword = kw
    const page = await getConsumableListApi(params)
    consList.value = Array.isArray(page?.records) ? page.records : []
    consTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '耗材加载失败')
  } finally {
    consLoading.value = false
  }
}
async function fetchLowStock() {
  try {
    lowStock.value = (await getLowStockApi()) || []
  } catch {
    lowStock.value = []
  }
}
async function fetchRecords() {
  recordLoading.value = true
  try {
    const page = await getConsumableRecordsApi({ pageNo: recordPageNo.value, pageSize: 8 })
    recordList.value = Array.isArray(page?.records) ? page.records : []
    recordTotal.value = page?.total ?? 0
  } catch {
    recordList.value = []
    recordTotal.value = 0
  } finally {
    recordLoading.value = false
  }
}
function openCons() {
  consForm.code = ''
  consForm.name = ''
  consForm.specification = ''
  consForm.unit = '支'
  consForm.price = null
  consForm.stock = 0
  consForm.safetyStock = 0
  consDialog.value = true
}
async function submitCons() {
  if (!consForm.code.trim() || !consForm.name.trim()) {
    WMessage.warning('编码与名称不能为空')
    return
  }
  consSaving.value = true
  try {
    await createConsumableApi({
      code: consForm.code.trim(),
      name: consForm.name.trim(),
      specification: consForm.specification.trim() || undefined,
      unit: consForm.unit.trim() || undefined,
      price: consForm.price ?? undefined,
      stock: consForm.stock ?? 0,
      safetyStock: consForm.safetyStock ?? 0,
    })
    WMessage.success('耗材已登记')
    consDialog.value = false
    fetchCons()
    fetchLowStock()
  } catch (e) {
    WMessage.error((e as Error).message || '登记失败')
  } finally {
    consSaving.value = false
  }
}
function openStock(row: ConsumableRow, type: 'IN' | 'OUT') {
  stockTarget.value = row
  stockForm.type = type
  stockForm.quantity = null
  stockForm.remark = ''
  stockDialog.value = true
}
async function submitStock() {
  if (!stockTarget.value?.id || !stockForm.quantity || stockForm.quantity <= 0) {
    WMessage.warning('请填写正整数数量')
    return
  }
  stockSaving.value = true
  try {
    await consumableStockApi(stockTarget.value.id, stockForm.type, stockForm.quantity, stockForm.remark.trim() || undefined)
    WMessage.success(stockForm.type === 'IN' ? '入库成功' : '出库成功')
    stockDialog.value = false
    fetchCons()
    fetchLowStock()
    fetchRecords()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  } finally {
    stockSaving.value = false
  }
}

/* ================= Tab2 设备 ================= */
const equipLoading = ref(false)
const equipList = ref<EquipmentRow[]>([])
const equipTotal = ref(0)
const equipPageNo = ref(1)
const equipQuery = reactive<{ status: string; keyword: string }>({ status: '', keyword: '' })
const equipDialog = ref(false)
const equipSaving = ref(false)
const equipForm = reactive<{ code: string; name: string; model: string; location: string; remark: string }>({
  code: '',
  name: '',
  model: '',
  location: '',
  remark: '',
})

function equipMeta(v?: string): { label: string; tag: 'success' | 'info' | 'warning' | 'danger' } {
  const k = (v || '').toUpperCase()
  if (k === 'USING') return { label: '使用中', tag: 'success' }
  if (k === 'REPAIR') return { label: '维修中', tag: 'warning' }
  if (k === 'SCRAP') return { label: '已报废', tag: 'danger' }
  return { label: '闲置', tag: 'info' }
}
async function fetchEquip() {
  equipLoading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: equipPageNo.value, pageSize: 10 }
    if (equipQuery.status) params.status = equipQuery.status
    const kw = equipQuery.keyword.trim()
    if (kw) params.keyword = kw
    const page = await getEquipmentListApi(params)
    equipList.value = Array.isArray(page?.records) ? page.records : []
    equipTotal.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '设备加载失败')
  } finally {
    equipLoading.value = false
  }
}
function openEquip() {
  equipForm.code = ''
  equipForm.name = ''
  equipForm.model = ''
  equipForm.location = ''
  equipForm.remark = ''
  equipDialog.value = true
}
async function submitEquip() {
  if (!equipForm.code.trim() || !equipForm.name.trim()) {
    WMessage.warning('编码与名称不能为空')
    return
  }
  equipSaving.value = true
  try {
    await createEquipmentApi({
      code: equipForm.code.trim(),
      name: equipForm.name.trim(),
      model: equipForm.model.trim() || undefined,
      location: equipForm.location.trim() || undefined,
      remark: equipForm.remark.trim() || undefined,
    })
    WMessage.success('设备已登记')
    equipDialog.value = false
    fetchEquip()
  } catch (e) {
    WMessage.error((e as Error).message || '登记失败')
  } finally {
    equipSaving.value = false
  }
}
async function equipStatus(row: EquipmentRow, status: string) {
  if (!row.id) return
  try {
    await equipmentStatusApi(row.id, status)
    WMessage.success('状态已更新')
    fetchEquip()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}
async function equipMaintain(row: EquipmentRow) {
  if (!row.id) return
  try {
    await equipmentMaintainApi(row.id)
    WMessage.success('维保已登记')
    fetchEquip()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

onMounted(() => {
  fetchCons()
  fetchLowStock()
  fetchRecords()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">后台资产管理</h2>
      <p class="page-subtitle">耗材出入库 · 设备台账维保（L1/L2）</p>
    </div>

    <w-tabs v-model="activeTab" class="hospital-card">
      <w-tab-pane label="耗材管理" name="consumable" />
      <w-tab-pane label="设备台账" name="equipment" />
    </w-tabs>

    <!-- Tab1 耗材 -->
    <template v-if="activeTab === 'consumable'">
      <w-card v-if="lowStock.length" shadow="never" class="hospital-card low-card">
        <span class="low-title">低库存预警：</span>
        <w-tag v-for="c in lowStock" :key="c.id" type="danger" effect="light" class="low-tag">
          {{ c.name }}（{{ c.stock }}/{{ c.safetyStock }}）
        </w-tag>
      </w-card>
      <w-card shadow="never" class="hospital-card">
        <w-form inline class="query-form">
          <w-form-item label="状态">
            <w-select v-model="consQuery.status" placeholder="全部" clearable style="width: 130px">
              <w-option label="启用" value="ACTIVE" />
              <w-option label="停用" value="DISABLED" />
            </w-select>
          </w-form-item>
          <w-form-item label="关键字">
            <w-input v-model="consQuery.keyword" placeholder="编码/名称" clearable style="width: 170px" @keyup.enter="consPageNo = 1; fetchCons()" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :icon="Search" :loading="consLoading" @click="consPageNo = 1; fetchCons()">查询</w-button>
          </w-form-item>
        </w-form>
        <div class="table-toolbar list-toolbar">
          <div class="table-toolbar__left"><span class="table-total">共 {{ consTotal }} 项耗材</span></div>
          <div class="table-toolbar__right">
            <w-button type="primary" :icon="Plus" @click="openCons">新增耗材</w-button>
          </div>
        </div>
        <w-table :data="consList" row-key="id" border stripe :loading="consLoading" empty-text="暂无耗材" size="default">
          <w-table-column label="编码" width="130">
            <template #default="{ row }">{{ row.code || '-' }}</template>
          </w-table-column>
          <w-table-column label="名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.name || '-' }}</template>
          </w-table-column>
          <w-table-column label="规格" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.specification || '-' }}</template>
          </w-table-column>
          <w-table-column label="单价" width="90" align="right">
            <template #default="{ row }">¥{{ row.price ?? '-' }}</template>
          </w-table-column>
          <w-table-column label="库存" width="90" align="center">
            <template #default="{ row }">
              <span :class="{ 'low-stock': row.stock != null && row.safetyStock != null && row.stock <= row.safetyStock }">
                {{ row.stock ?? 0 }}
              </span>
            </template>
          </w-table-column>
          <w-table-column label="安全库存" width="95" align="center">
            <template #default="{ row }">{{ row.safetyStock ?? 0 }}</template>
          </w-table-column>
          <w-table-column label="操作" width="170" fixed="right" align="center">
            <template #default="{ row }">
              <w-button type="success" link @click="openStock(row, 'IN')">入库</w-button>
              <w-button type="warning" link @click="openStock(row, 'OUT')">出库</w-button>
            </template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="consPageNo"
          :page-size="10"
          :total="consTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { consPageNo = p; fetchCons() }"
        />
        <h4 class="sub-title">最近出入库流水</h4>
        <w-table :data="recordList" row-key="id" border size="small" :loading="recordLoading" empty-text="暂无流水" max-height="240">
          <w-table-column label="耗材" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.consumableName || row.consumable_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="类型" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="(row.recordType || row.record_type) === 'IN' ? 'success' : 'warning'" effect="light">
                {{ (row.recordType || row.record_type) === 'IN' ? '入库' : '出库' }}
              </w-tag>
            </template>
          </w-table-column>
          <w-table-column label="数量" width="90" align="center">
            <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
          </w-table-column>
          <w-table-column label="操作人" width="120">
            <template #default="{ row }">{{ row.operatorName || row.operator_name || '-' }}</template>
          </w-table-column>
          <w-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmt(row.createTime || row.create_time) }}</template>
          </w-table-column>
          <w-table-column label="备注" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </w-table-column>
        </w-table>
        <w-pagination
          class="pager"
          :current-page="recordPageNo"
          :page-size="8"
          :total="recordTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { recordPageNo = p; fetchRecords() }"
        />
      </w-card>
    </template>

    <!-- Tab2 设备 -->
    <w-card v-if="activeTab === 'equipment'" shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="状态">
          <w-select v-model="equipQuery.status" placeholder="全部" clearable style="width: 130px" @change="equipPageNo = 1; fetchEquip()">
            <w-option label="使用中" value="USING" />
            <w-option label="闲置" value="IDLE" />
            <w-option label="维修中" value="REPAIR" />
            <w-option label="已报废" value="SCRAP" />
          </w-select>
        </w-form-item>
        <w-form-item label="关键字">
          <w-input v-model="equipQuery.keyword" placeholder="编码/名称/位置" clearable style="width: 180px" @keyup.enter="equipPageNo = 1; fetchEquip()" />
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :icon="Search" :loading="equipLoading" @click="equipPageNo = 1; fetchEquip()">查询</w-button>
        </w-form-item>
      </w-form>
      <div class="table-toolbar list-toolbar">
        <div class="table-toolbar__left"><span class="table-total">共 {{ equipTotal }} 台设备</span></div>
        <div class="table-toolbar__right">
          <w-button type="primary" :icon="Plus" @click="openEquip">新增设备</w-button>
        </div>
      </div>
      <w-table :data="equipList" row-key="id" border stripe :loading="equipLoading" empty-text="暂无设备" size="default">
        <w-table-column label="编码" width="130">
          <template #default="{ row }">{{ row.code || '-' }}</template>
        </w-table-column>
        <w-table-column label="名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </w-table-column>
        <w-table-column label="型号" width="110">
          <template #default="{ row }">{{ row.model || '-' }}</template>
        </w-table-column>
        <w-table-column label="位置" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.location || '-' }}</template>
        </w-table-column>
        <w-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <w-tag :type="equipMeta(row.status).tag" effect="light">{{ equipMeta(row.status).label }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column label="购入日期" width="110">
          <template #default="{ row }">{{ row.buyDate || row.buy_date || '-' }}</template>
        </w-table-column>
        <w-table-column label="最近维保" width="110">
          <template #default="{ row }">{{ row.lastMaintainDate || row.last_maintain_date || '-' }}</template>
        </w-table-column>
        <w-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <w-button v-if="(row.status || '') === 'IDLE'" type="success" link @click="equipStatus(row, 'USING')">启用</w-button>
            <w-button v-if="(row.status || '') === 'USING'" type="warning" link @click="equipStatus(row, 'IDLE')">停用</w-button>
            <w-button v-if="['USING', 'IDLE'].includes(row.status || '')" type="warning" link @click="equipStatus(row, 'REPAIR')">报修</w-button>
            <w-button v-if="(row.status || '') === 'REPAIR'" type="primary" link @click="equipMaintain(row)">维保完成</w-button>
            <w-button v-if="['IDLE', 'REPAIR'].includes(row.status || '')" type="danger" link @click="equipStatus(row, 'SCRAP')">报废</w-button>
          </template>
        </w-table-column>
      </w-table>
      <w-pagination
        class="pager"
        :current-page="equipPageNo"
        :page-size="10"
        :total="equipTotal"
        layout="total, prev, pager, next"
        @current-change="(p: number) => { equipPageNo = p; fetchEquip() }"
      />
    </w-card>

    <!-- 新增耗材弹窗 -->
    <w-dialog v-model="consDialog" title="新增耗材" width="520px" destroy-on-close>
      <w-form label-width="96px">
        <w-form-item label="编码" required>
          <w-input v-model="consForm.code" placeholder="耗材编码（唯一）" />
        </w-form-item>
        <w-form-item label="名称" required>
          <w-input v-model="consForm.name" />
        </w-form-item>
        <w-form-item label="规格">
          <w-input v-model="consForm.specification" />
        </w-form-item>
        <w-form-item label="单位">
          <w-input v-model="consForm.unit" style="width: 160px" />
        </w-form-item>
        <w-form-item label="单价">
          <w-input-number v-model="consForm.price" :min="0" :precision="2" style="width: 160px" />
        </w-form-item>
        <w-form-item label="初始库存">
          <w-input-number v-model="consForm.stock" :min="0" style="width: 160px" />
        </w-form-item>
        <w-form-item label="安全库存">
          <w-input-number v-model="consForm.safetyStock" :min="0" style="width: 160px" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="consDialog = false">取消</w-button>
        <w-button type="primary" :loading="consSaving" @click="submitCons">登记</w-button>
      </template>
    </w-dialog>

    <!-- 出入库弹窗 -->
    <w-dialog v-model="stockDialog" :title="`耗材${stockForm.type === 'IN' ? '入库' : '出库'}：${stockTarget?.name || ''}`" width="440px" destroy-on-close>
      <w-form label-width="88px">
        <w-form-item label="类型">
          <w-radio-group v-model="stockForm.type">
            <w-radio value="IN">入库</w-radio>
            <w-radio value="OUT">出库</w-radio>
          </w-radio-group>
        </w-form-item>
        <w-form-item label="数量" required>
          <w-input-number v-model="stockForm.quantity" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="备注">
          <w-input v-model="stockForm.remark" placeholder="如 采购入库 / 门诊领用" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="stockDialog = false">取消</w-button>
        <w-button type="primary" :loading="stockSaving" @click="submitStock">确认</w-button>
      </template>
    </w-dialog>

    <!-- 新增设备弹窗 -->
    <w-dialog v-model="equipDialog" title="新增设备" width="520px" destroy-on-close>
      <w-form label-width="88px">
        <w-form-item label="编码" required>
          <w-input v-model="equipForm.code" placeholder="设备编码（唯一）" />
        </w-form-item>
        <w-form-item label="名称" required>
          <w-input v-model="equipForm.name" />
        </w-form-item>
        <w-form-item label="型号">
          <w-input v-model="equipForm.model" />
        </w-form-item>
        <w-form-item label="位置">
          <w-input v-model="equipForm.location" placeholder="如 急诊抢救室" />
        </w-form-item>
        <w-form-item label="备注">
          <w-input v-model="equipForm.remark" type="textarea" :rows="2" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="equipDialog = false">取消</w-button>
        <w-button type="primary" :loading="equipSaving" @click="submitEquip">登记</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.sub-title {
  margin: 16px 0 8px;
}
.low-card {
  margin-bottom: 12px;
}
.low-title {
  font-size: 13px;
  color: #c0392b;
}
.low-tag {
  margin-right: 8px;
}
.low-stock {
  color: #c0392b;
  font-weight: 600;
}
</style>
