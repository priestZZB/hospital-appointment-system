<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { CircleCheck, Refresh, Search } from '@win-design-next/icons-vue'
import { createInfusionRecordApi, getNursePendingInfusionsApi } from '@/api/medsupply'
import { useUserStore } from '@/stores/user'
import type { InfusionOrder } from '@/types'

type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'primary'
interface StatusMeta {
  text: string
  tone: StatusTone
}

const RECORD_TYPES = [
  { value: 'SKIN_TEST', label: '皮试' },
  { value: 'PREPARE', label: '配液' },
  { value: 'START', label: '开始输液' },
  { value: 'END', label: '结束输液' },
  { value: 'OBSERVE', label: '观察' },
]

const RECORD_TYPE_LABELS: Record<string, string> = {
  SKIN_TEST: '皮试',
  PREPARE: '配液',
  START: '开始输液',
  END: '结束输液',
  OBSERVE: '观察',
}

const SKIN_RESULTS = [
  { value: 'NEGATIVE', label: '阴性' },
  { value: 'POSITIVE', label: '阳性' },
]

const ORDER_STATUS_META: Record<string, StatusMeta> = {
  PENDING: { text: '待执行', tone: 'warning' },
  SKIN_TEST: { text: '待皮试', tone: 'warning' },
  IN_PROGRESS: { text: '执行中', tone: 'primary' },
  STARTED: { text: '执行中', tone: 'primary' },
  COMPLETED: { text: '已完成', tone: 'success' },
  FINISHED: { text: '已完成', tone: 'success' },
  CANCELLED: { text: '已取消', tone: 'info' },
}

const userStore = useUserStore()

const loading = ref(false)
const list = ref<InfusionOrder[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

const recordVisible = ref(false)
const recordLoading = ref(false)
const recordOrder = ref<InfusionOrder | null>(null)
const recordForm = reactive({
  recordType: 'START',
  recordContent: '',
  skinTestResult: 'NEGATIVE',
  dropRate: '',
  operatorName: userStore.realName,
})

const pendingCount = computed(() => total.value)

function statusMeta(status?: string): StatusMeta {
  return ORDER_STATUS_META[status ?? ''] ?? { text: status || '未知', tone: 'info' }
}

function recordTypeLabel(type?: string): string {
  return RECORD_TYPE_LABELS[type ?? ''] ?? type ?? '-'
}

function skinRequiredLabel(value?: number): string {
  return value === 1 ? '需皮试' : '免皮试'
}

function skinRequiredTone(value?: number): StatusTone {
  return value === 1 ? 'warning' : 'info'
}

function formatPrice(value?: number): string {
  return value != null ? `¥${Number(value).toFixed(2)}` : '-'
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getNursePendingInfusionsApi({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    list.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '待执行输液列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

function openRecord(row: InfusionOrder) {
  recordOrder.value = row
  Object.assign(recordForm, {
    recordType: 'START',
    recordContent: '',
    skinTestResult: 'NEGATIVE',
    dropRate: '',
    operatorName: userStore.realName,
  })
  recordVisible.value = true
}

async function submitRecord() {
  if (!recordOrder.value) return
  if (!recordForm.recordContent.trim()) {
    WMessage.warning('请填写执行记录内容')
    return
  }
  recordLoading.value = true
  try {
    await createInfusionRecordApi(recordOrder.value.id, {
      recordType: recordForm.recordType,
      recordContent: recordForm.recordContent.trim(),
      skinTestResult: recordForm.recordType === 'SKIN_TEST' ? recordForm.skinTestResult : undefined,
      dropRate: recordForm.recordType === 'START' ? recordForm.dropRate || undefined : undefined,
      operatorName: recordForm.operatorName.trim() || userStore.realName,
    })
    WMessage.success('执行记录已登记')
    recordVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '执行记录登记失败')
  } finally {
    recordLoading.value = false
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
      <h2 class="page-title">护士站输液</h2>
      <p class="page-subtitle">待执行输液单列表 · 皮试 / 配液 / 开始 / 结束 / 观察执行登记</p>
    </div>

    <!-- 统计条 -->
    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-card__icon stat-card__icon--warning"><CircleCheck /></span>
        <div class="stat-card__body">
          <span class="stat-card__label">待执行输液单</span>
          <span class="stat-card__value">{{ pendingCount.toLocaleString() }}</span>
          <span class="stat-card__extra">当前护士站待处理</span>
        </div>
      </div>
    </div>

    <!-- 列表区 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-title"><CircleCheck class="toolbar-title__icon" />待执行输液单</span>
          <w-tag size="small" effect="light" type="primary">共 {{ total }} 条</w-tag>
        </div>
        <div class="table-toolbar__right">
          <w-button :icon="Refresh" @click="fetchList">刷新</w-button>
        </div>
      </div>

      <div v-loading="loading" class="table-wrap">
        <w-table :data="list" border stripe row-key="id" size="default">
          <w-table-column type="index" label="#" width="60" align="center" />
          <w-table-column prop="patientName" label="患者" min-width="100" show-overflow-tooltip />
          <w-table-column prop="drugName" label="药品" min-width="140" show-overflow-tooltip />
          <w-table-column prop="dosage" label="剂量" width="100" align="center" />
          <w-table-column prop="usageMethod" label="用法" width="110" show-overflow-tooltip />
          <w-table-column prop="frequency" label="频次" width="100" align="center" />
          <w-table-column label="天数" width="80" align="center">
            <template #default="{ row }">{{ row.days ?? '-' }}</template>
          </w-table-column>
          <w-table-column label="皮试" width="90" align="center">
            <template #default="{ row }">
              <w-tag size="small" effect="light" :type="skinRequiredTone(row.skinTestRequired)">
                {{ skinRequiredLabel(row.skinTestRequired) }}
              </w-tag>
            </template>
          </w-table-column>
          <w-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <w-tag size="small" effect="light" :type="statusMeta(row.status).tone">{{ statusMeta(row.status).text }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="单次费用" width="100" align="right">
            <template #default="{ row }">{{ formatPrice(row.unitPrice) }}</template>
          </w-table-column>
          <w-table-column prop="createTime" label="开单时间" min-width="160" show-overflow-tooltip />
          <w-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <w-button link type="primary" :icon="CircleCheck" @click="openRecord(row)">执行记录</w-button>
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
          layout="total, sizes, prev, pager, next"
          @current-change="fetchList"
          @size-change="handleSizeChange"
        />
      </div>
    </w-card>

    <!-- 执行记录弹窗 -->
    <w-dialog v-model="recordVisible" title="执行记录登记" width="560px" :close-on-click-modal="false" destroy-on-close>
      <template v-if="recordOrder">
        <div class="order-summary">
          <span class="order-summary__label">输液单 #{{ recordOrder.id }}</span>
          <span class="order-summary__drug">{{ recordOrder.drugName || '-' }}</span>
          <span class="order-summary__patient">{{ recordOrder.patientName || '-' }}</span>
        </div>
        <w-form :model="recordForm" label-width="88px">
          <w-form-item label="记录类型" required>
            <w-select v-model="recordForm.recordType" placeholder="请选择记录类型" class="field-full">
              <w-option v-for="t in RECORD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </w-select>
          </w-form-item>
          <w-form-item v-if="recordForm.recordType === 'SKIN_TEST'" label="皮试结果">
            <w-select v-model="recordForm.skinTestResult" placeholder="请选择皮试结果" class="field-full">
              <w-option v-for="s in SKIN_RESULTS" :key="s.value" :label="s.label" :value="s.value" />
            </w-select>
          </w-form-item>
          <w-form-item v-if="recordForm.recordType === 'START'" label="滴速">
            <w-input v-model="recordForm.dropRate" placeholder="如：30滴/分" maxlength="32" clearable />
          </w-form-item>
          <w-form-item label="记录内容" required>
            <w-input
              v-model="recordForm.recordContent"
              type="textarea"
              :rows="3"
              maxlength="300"
              show-word-limit
              placeholder="请填写本次执行记录内容"
            />
          </w-form-item>
          <w-form-item label="执行护士">
            <w-input v-model="recordForm.operatorName" placeholder="执行护士姓名" maxlength="32" clearable />
          </w-form-item>
        </w-form>
      </template>
      <template #footer>
        <w-button @click="recordVisible = false">取消</w-button>
        <w-button type="primary" :loading="recordLoading" :icon="Search" @click="submitRecord">确认登记</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.toolbar-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.toolbar-title__icon {
  color: var(--w3-color-primary);
}
.table-wrap {
  min-height: 200px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.field-full {
  width: 100%;
}
.order-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: var(--hospital-radius-md);
  background: var(--w3-color-primary-plain);
}
.order-summary__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--w3-color-primary);
}
.order-summary__drug {
  font-size: 13px;
  color: var(--hospital-text-main);
}
.order-summary__patient {
  font-size: 12px;
  color: var(--hospital-text-second);
}
</style>
