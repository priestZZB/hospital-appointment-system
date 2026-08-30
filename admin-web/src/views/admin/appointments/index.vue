<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Refresh, RefreshLeft, Search } from '@win-design-next/icons-vue'
import {
  cancelAppointmentApi,
  getAppointmentApi,
  getAppointmentPageApi,
  getDepartmentsApi,
  getDoctorsApi,
} from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { AppointmentVO, DepartmentVO, DoctorVO } from '@/types'

const userStore = useUserStore()

const loading = ref(false)
const list = ref<AppointmentVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

const departments = ref<DepartmentVO[]>([])
const doctors = ref<DoctorVO[]>([])

const query = reactive({
  appointmentNo: '',
  patientId: '',
  departmentId: undefined as number | undefined,
  doctorId: undefined as number | undefined,
  appointmentDate: '',
  orderStatus: '',
})

const detailVisible = ref(false)
const detail = ref<AppointmentVO | null>(null)

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const ORDER_STATUS_META: Record<string, { text: string; type: TagType }> = {
  PAID: { text: '已支付', type: 'success' },
  PENDING_PAY: { text: '待支付', type: 'warning' },
  CANCELLED: { text: '已取消', type: 'info' },
  TIMEOUT: { text: '已超时', type: 'danger' },
  REFUNDED: { text: '已退款', type: 'primary' },
}

function orderStatusMeta(status?: string): { text: string; type: TagType } {
  return ORDER_STATUS_META[status || ''] || { text: status || '-', type: 'info' }
}

function visitStatusMeta(status?: string): { text: string; type: TagType } {
  if (!status) return { text: '-', type: 'info' }
  const textMap: Record<string, string> = { IN_PROGRESS: '就诊中' }
  return { text: textMap[status] || status, type: status === 'IN_PROGRESS' ? 'primary' : 'info' }
}

function buildParams(): Record<string, unknown> {
  const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value }
  const appointmentNo = query.appointmentNo.trim()
  if (appointmentNo) params.appointmentNo = appointmentNo
  const patientIdText = query.patientId.trim()
  if (patientIdText) {
    const patientId = Number(patientIdText)
    if (Number.isFinite(patientId)) params.patientId = patientId
  }
  if (query.departmentId !== undefined) params.departmentId = query.departmentId
  if (query.doctorId !== undefined) params.doctorId = query.doctorId
  if (query.appointmentDate) params.appointmentDate = query.appointmentDate
  if (query.orderStatus) params.orderStatus = query.orderStatus
  return params
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getAppointmentPageApi(buildParams())
    list.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '预约列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchList()
}

function handleReset() {
  query.appointmentNo = ''
  query.patientId = ''
  query.departmentId = undefined
  query.doctorId = undefined
  query.appointmentDate = ''
  query.orderStatus = ''
  pageNo.value = 1
  fetchList()
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

async function loadDepartments() {
  try {
    departments.value = await getDepartmentsApi()
  } catch (e) {
    WMessage.error((e as Error).message || '科室列表加载失败')
  }
}

async function loadDoctors() {
  try {
    const page = await getDoctorsApi({ pageNo: 1, pageSize: 500 })
    doctors.value = page.records || []
  } catch {
    doctors.value = []
  }
}

async function openDetail(row: AppointmentVO) {
  detailVisible.value = true
  detail.value = row
  try {
    detail.value = await getAppointmentApi(row.id)
  } catch (e) {
    WMessage.error((e as Error).message || '预约详情加载失败')
  }
}

async function handleCancel(row: AppointmentVO) {
  try {
    await cancelAppointmentApi(row.id, '管理端取消')
    WMessage.success('预约已取消')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '取消失败')
  }
}

function slotText(row: AppointmentVO): string {
  if (!row.appointmentDate) return '-'
  if (row.slotStart && row.slotEnd) return `${row.appointmentDate} ${row.slotStart}–${row.slotEnd}`
  return row.appointmentDate
}

onMounted(() => {
  loadDepartments()
  loadDoctors()
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">预约管理</h2>
      <p class="page-subtitle">全院预约记录的多条件筛选与分页管理，支持查看详情与取消预约</p>
    </div>

    <!-- 筛选区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form :model="query" inline label-position="right" label-width="auto">
        <w-form-item label="预约号">
          <w-input
            v-model="query.appointmentNo"
            placeholder="请输入预约号"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </w-form-item>
        <w-form-item label="患者ID">
          <w-input
            v-model="query.patientId"
            placeholder="请输入患者ID"
            clearable
            style="width: 130px"
            @keyup.enter="handleSearch"
          />
        </w-form-item>
        <w-form-item label="科室">
          <w-select v-model="query.departmentId" placeholder="全部科室" clearable style="width: 170px">
            <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="医生">
          <w-select v-model="query.doctorId" placeholder="全部医生" clearable filterable style="width: 170px">
            <w-option v-for="d in doctors" :key="d.id" :label="d.name" :value="d.id" />
          </w-select>
        </w-form-item>
        <w-form-item label="预约日期">
          <w-date-picker-pro
            v-model="query.appointmentDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 160px"
          />
        </w-form-item>
        <w-form-item label="订单状态">
          <w-select v-model="query.orderStatus" placeholder="全部状态" clearable style="width: 130px">
            <w-option label="待支付" value="PENDING_PAY" />
            <w-option label="已支付" value="PAID" />
            <w-option label="已取消" value="CANCELLED" />
            <w-option label="已超时" value="TIMEOUT" />
            <w-option label="已退款" value="REFUNDED" />
          </w-select>
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :icon="Search" @click="handleSearch">查询</w-button>
          <w-button :icon="RefreshLeft" @click="handleReset">重置</w-button>
        </w-form-item>
      </w-form>
    </w-card>

    <!-- 列表区 -->
    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar list-toolbar">
        <div class="table-toolbar__left">
          <span class="table-total">共 {{ total }} 条预约记录</span>
        </div>
        <div class="table-toolbar__right">
          <w-button :icon="Refresh" @click="fetchList">刷新</w-button>
        </div>
      </div>

      <w-table :data="list" row-key="id" border :loading="loading" empty-text="暂无预约记录" size="default">
        <w-table-column prop="appointmentNo" label="预约号" min-width="160" show-overflow-tooltip />
        <w-table-column prop="patientId" label="患者ID" width="90" align="center" />
        <w-table-column prop="departmentName" label="科室" min-width="110" show-overflow-tooltip />
        <w-table-column prop="doctorName" label="医生" min-width="100" show-overflow-tooltip />
        <w-table-column label="时段" min-width="170">
          <template #default="{ row }">{{ slotText(row) }}</template>
        </w-table-column>
        <w-table-column label="订单状态" width="110" align="center">
          <template #default="{ row }">
            <w-tag :type="orderStatusMeta(row.orderStatus).type">{{ orderStatusMeta(row.orderStatus).text }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column label="就诊状态" width="110" align="center">
          <template #default="{ row }">
            <w-tag v-if="row.visitStatus" :type="visitStatusMeta(row.visitStatus).type">
              {{ visitStatusMeta(row.visitStatus).text }}
            </w-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </w-table-column>
        <w-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <w-button size="small" text type="primary" @click="openDetail(row)">详情</w-button>
            <w-popconfirm
              v-if="userStore.isAdmin"
              title="确定取消该预约吗？取消后号源将释放。"
              confirm-button-text="确定"
              cancel-button-text="再想想"
              confirm-button-type="danger"
              @confirm="handleCancel(row)"
            >
              <template #reference>
                <w-button size="small" text type="danger">取消</w-button>
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
          layout="total, sizes, prev, pager, next"
          @current-change="fetchList"
          @size-change="handleSizeChange"
        />
      </div>
    </w-card>

    <!-- 详情弹窗 -->
    <w-dialog v-model="detailVisible" title="预约详情" width="560px">
      <w-descriptions v-if="detail" :column="2" border>
        <w-descriptions-item label="预约号">{{ detail.appointmentNo || '-' }}</w-descriptions-item>
        <w-descriptions-item label="患者ID">{{ detail.patientId }}</w-descriptions-item>
        <w-descriptions-item label="科室">{{ detail.departmentName || '-' }}</w-descriptions-item>
        <w-descriptions-item label="医生">
          {{ detail.doctorName || '-' }}{{ detail.doctorTitle ? `（${detail.doctorTitle}）` : '' }}
        </w-descriptions-item>
        <w-descriptions-item label="就诊日期">{{ detail.appointmentDate || '-' }}</w-descriptions-item>
        <w-descriptions-item label="时段">
          {{ detail.slotStart && detail.slotEnd ? `${detail.slotStart}–${detail.slotEnd}` : '-' }}
        </w-descriptions-item>
        <w-descriptions-item label="挂号费">
          {{ detail.registerFee != null ? `¥${detail.registerFee}` : '-' }}
        </w-descriptions-item>
        <w-descriptions-item label="订单状态">
          <w-tag :type="orderStatusMeta(detail.orderStatus).type">{{ orderStatusMeta(detail.orderStatus).text }}</w-tag>
        </w-descriptions-item>
        <w-descriptions-item label="就诊状态">
          <w-tag v-if="detail.visitStatus" :type="visitStatusMeta(detail.visitStatus).type">
            {{ visitStatusMeta(detail.visitStatus).text }}
          </w-tag>
          <span v-else class="text-muted">-</span>
        </w-descriptions-item>
        <w-descriptions-item label="支付单号">{{ detail.paymentOrderNo || '-' }}</w-descriptions-item>
        <w-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</w-descriptions-item>
        <w-descriptions-item label="更新时间">{{ detail.updateTime || '-' }}</w-descriptions-item>
      </w-descriptions>
      <template #footer>
        <w-button @click="detailVisible = false">关闭</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.list-toolbar {
  margin-bottom: 14px;
}
.table-total {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.text-muted {
  color: var(--hospital-text-third);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
