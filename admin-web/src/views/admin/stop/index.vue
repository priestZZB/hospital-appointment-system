<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import { Check, Close, Plus, Refresh, Search, Stop, UserGroup } from '@win-design-next/icons-vue'
import { applyStopApi, approveStopApi, chiefReviewStopApi, getDoctorStopApi, getSchedulesApi, getStopListApi } from '@/api/clinic'
import { useUserStore } from '@/stores/user'
import type { ScheduleVO, StopApplicationVO } from '@/types'

type StatusTone = 'success' | 'warning' | 'danger' | 'info'
interface StatusMeta {
  text: string
  tone: StatusTone
}

const STATUS_META: Record<string, StatusMeta> = {
  PENDING: { text: '待科主任初审', tone: 'warning' },
  PENDING_ADMIN: { text: '待门诊部终审', tone: 'warning' },
  APPROVED: { text: '已通过', tone: 'success' },
  REJECTED: { text: '已驳回', tone: 'danger' },
}

const userStore = useUserStore()

const loading = ref(false)
const list = ref<StopApplicationVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const query = reactive({ status: '' })

const applyVisible = ref(false)
const applyLoading = ref(false)
const schedules = ref<ScheduleVO[]>([])
const applyForm = reactive({ scheduleId: undefined as number | undefined, applyReason: '' })

const doctorStopVisible = ref(false)
const doctorStopLoading = ref(false)
const doctorStopList = ref<StopApplicationVO[]>([])
const doctorId = ref<number | undefined>(undefined)

function statusMeta(status?: string): StatusMeta {
  return STATUS_META[status ?? ''] ?? { text: status || '未知', tone: 'info' }
}

function formatMoney(value?: number): string {
  return (value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getStopListApi({
      status: query.status || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    list.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '停诊申请列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchList()
}

function handleReset() {
  query.status = ''
  pageNo.value = 1
  fetchList()
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

async function openApply() {
  applyForm.scheduleId = undefined
  applyForm.applyReason = ''
  applyVisible.value = true
  try {
    schedules.value = await getSchedulesApi({})
  } catch {
    schedules.value = []
  }
}

async function handleApply() {
  if (!applyForm.scheduleId || !applyForm.applyReason.trim()) {
    WMessage.warning('排班与申请原因必填')
    return
  }
  applyLoading.value = true
  try {
    await applyStopApi({ scheduleId: applyForm.scheduleId, applyReason: applyForm.applyReason.trim() })
    WMessage.success('停诊申请已提交')
    applyVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '提交失败')
  } finally {
    applyLoading.value = false
  }
}

async function handleApprove(row: StopApplicationVO, action: 'APPROVE' | 'REJECT') {
  const isApprove = action === 'APPROVE'
  try {
    await WMessageBox.confirm(
      isApprove ? '通过该停诊申请后，将自动取消关联预约并退款，是否继续？' : '确定驳回该停诊申请吗？',
      isApprove ? '通过审批' : '驳回审批',
      { type: 'warning', confirmButtonText: isApprove ? '通过' : '驳回', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await approveStopApi(row.id, { action, approveComment: isApprove ? '审批通过' : '审批驳回' })
    WMessage.success('审批完成')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '审批失败')
  }
}

/** 科主任初审（通过 → 待门诊部终审；驳回 → 驳回） */
async function handleChiefReview(row: StopApplicationVO, action: 'APPROVE' | 'REJECT') {
  const isApprove = action === 'APPROVE'
  try {
    await WMessageBox.confirm(
      isApprove ? '初审通过后流转至门诊部终审，是否继续？' : '确定初审驳回该停诊申请吗？',
      isApprove ? '初审通过' : '初审驳回',
      { type: 'warning', confirmButtonText: isApprove ? '通过' : '驳回', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await chiefReviewStopApi(row.id, { action, approveComment: isApprove ? '初审通过' : '初审驳回' })
    WMessage.success('初审完成')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '初审失败')
  }
}

function openDoctorStop() {
  doctorId.value = undefined
  doctorStopList.value = []
  doctorStopVisible.value = true
}

async function handleDoctorStop() {
  if (!doctorId.value) {
    WMessage.warning('请输入医生ID')
    return
  }
  doctorStopLoading.value = true
  try {
    const page = await getDoctorStopApi(doctorId.value, { pageNo: 1, pageSize: 50 })
    doctorStopList.value = page.records || []
  } catch (e) {
    WMessage.error((e as Error).message || '查询失败')
  } finally {
    doctorStopLoading.value = false
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
      <h2 class="page-title">停诊审批</h2>
      <p class="page-subtitle">医生提交停诊申请，管理员审批；通过后自动取消关联预约并退款</p>
    </div>

    <!-- 查询区 -->
    <w-card shadow="never" class="hospital-card">
      <w-form :model="query" inline>
        <w-form-item label="申请状态">
          <w-select v-model="query.status" clearable placeholder="全部状态" style="width: 180px" @change="handleSearch">
            <w-option label="待审批" value="PENDING" />
            <w-option label="已通过" value="APPROVED" />
            <w-option label="已驳回" value="REJECTED" />
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
      <div class="table-toolbar">
        <div class="table-toolbar__left">
          <span class="toolbar-title"><Stop class="toolbar-title__icon" />停诊申请列表</span>
          <w-tag size="small" effect="light" type="primary">共 {{ total }} 条</w-tag>
        </div>
        <div class="table-toolbar__right">
          <w-button v-if="userStore.isAdmin" :icon="UserGroup" @click="openDoctorStop">医生停诊记录</w-button>
          <w-button v-if="userStore.isDoctor" type="primary" :icon="Plus" @click="openApply">提交停诊申请</w-button>
        </div>
      </div>

      <div v-loading="loading" class="table-wrap">
        <w-table :data="list" border stripe row-key="id" size="default">
          <w-table-column type="index" label="#" width="60" align="center" />
          <w-table-column prop="doctorName" label="医生" min-width="100" show-overflow-tooltip />
          <w-table-column prop="departmentName" label="科室" min-width="110" show-overflow-tooltip />
          <w-table-column label="排班时间" min-width="150">
            <template #default="{ row }">{{ row.scheduleDate }} {{ row.period }}</template>
          </w-table-column>
          <w-table-column prop="applyReason" label="申请原因" min-width="180" show-overflow-tooltip />
          <w-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <w-tag :type="statusMeta(row.status).tone" effect="light">{{ statusMeta(row.status).text }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="影响预约" width="90" align="center">
            <template #default="{ row }">
              <span>{{ row.affectedCount ?? '-' }}</span>
            </template>
          </w-table-column>
          <w-table-column label="退款总额" width="110" align="right">
            <template #default="{ row }">
              <span>{{ row.status === 'APPROVED' ? `¥${formatMoney(row.refundTotal)}` : '-' }}</span>
            </template>
          </w-table-column>
          <w-table-column prop="createTime" label="申请时间" min-width="160" show-overflow-tooltip />
          <w-table-column label="操作" width="180" fixed="right" align="center">
            <template #default="{ row }">
              <!-- 科主任初审：待科主任初审的申请 -->
              <template v-if="(userStore.isDeptChief || userStore.isAdmin) && row.status === 'PENDING'">
                <w-button link type="success" :icon="Check" @click="handleChiefReview(row, 'APPROVE')">初审通过</w-button>
                <w-button link type="danger" :icon="Close" @click="handleChiefReview(row, 'REJECT')">初审驳回</w-button>
              </template>
              <!-- 门诊部终审：科主任初审通过后的申请 -->
              <template v-else-if="userStore.isAdmin && row.status === 'PENDING_ADMIN'">
                <w-button link type="success" :icon="Check" @click="handleApprove(row, 'APPROVE')">通过</w-button>
                <w-button link type="danger" :icon="Close" @click="handleApprove(row, 'REJECT')">驳回</w-button>
              </template>
              <span v-else class="muted">-</span>
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

    <!-- 停诊申请弹窗 -->
    <w-dialog v-model="applyVisible" title="提交停诊申请" width="520px" :close-on-click-modal="false">
      <w-form :model="applyForm" label-width="90px">
        <w-form-item label="排班" required>
          <w-select v-model="applyForm.scheduleId" filterable placeholder="请选择需要停诊的排班">
            <w-option
              v-for="s in schedules"
              :key="s.id"
              :label="`#${s.id} ${s.doctorName ?? ''} ${s.scheduleDate} ${s.period}`"
              :value="s.id"
            />
          </w-select>
        </w-form-item>
        <w-form-item label="申请原因" required>
          <w-input
            v-model="applyForm.applyReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写停诊原因"
          />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="applyVisible = false">取消</w-button>
        <w-button type="primary" :loading="applyLoading" @click="handleApply">提交申请</w-button>
      </template>
    </w-dialog>

    <!-- 医生停诊记录弹窗 -->
    <w-dialog v-model="doctorStopVisible" title="医生停诊记录" width="760px" :close-on-click-modal="false">
      <div class="doctor-stop-query">
        <w-input-number v-model="doctorId" :min="1" placeholder="请输入医生 ID" style="width: 200px" />
        <w-button type="primary" :icon="Search" :loading="doctorStopLoading" @click="handleDoctorStop">查询</w-button>
      </div>
      <div v-loading="doctorStopLoading" class="table-wrap">
        <w-table :data="doctorStopList" border stripe size="small" row-key="id">
          <w-table-column prop="id" label="申请ID" width="80" />
          <w-table-column prop="doctorName" label="医生" min-width="100" show-overflow-tooltip />
          <w-table-column prop="departmentName" label="科室" min-width="110" show-overflow-tooltip />
          <w-table-column label="排班时间" min-width="150">
            <template #default="{ row }">{{ row.scheduleDate }} {{ row.period }}</template>
          </w-table-column>
          <w-table-column prop="applyReason" label="申请原因" min-width="140" show-overflow-tooltip />
          <w-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <w-tag :type="statusMeta(row.status).tone" effect="light">{{ statusMeta(row.status).text }}</w-tag>
            </template>
          </w-table-column>
        </w-table>
      </div>
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
.doctor-stop-query {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.muted {
  color: var(--hospital-text-third);
}
</style>
