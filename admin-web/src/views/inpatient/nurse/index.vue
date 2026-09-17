<template>
  <div class="page">
    <!-- 住院总览 -->
    <w-card class="page-card" title="护士站 · 住院总览" subtitle="在院患者 / 医嘱动态 / 预交金与费用 / 最新体征">
      <template #extra>
        <w-button icon="Refresh" circle @click="loadOverview" />
      </template>
      <w-table :data="overview" v-loading="overviewLoading" stripe @row-click="selectAdmission">
        <w-table-column prop="admissionNo" label="住院号" width="150" />
        <w-table-column prop="patientName" label="患者" width="100" />
        <w-table-column label="床位" width="110">
          <template #default="{ row }">{{ row.roomNo ? `${row.roomNo}房${row.bedNo}床` : '未分床' }}</template>
        </w-table-column>
        <w-table-column prop="pendingOrderCount" label="待核对" width="90" />
        <w-table-column prop="executingOrderCount" label="执行中" width="90" />
        <w-table-column label="预交金" width="100">
          <template #default="{ row }">￥{{ fmt(row.depositBalance) }}</template>
        </w-table-column>
        <w-table-column label="累计费用" width="100">
          <template #default="{ row }">￥{{ fmt(row.totalFee) }}</template>
        </w-table-column>
        <w-table-column label="最新体征" min-width="210">
          <template #default="{ row }">
            <span v-if="row.latestVital" class="muted">
              T{{ row.latestVital.temperature ?? '-' }}℃ · P{{ row.latestVital.pulse ?? '-' }} · BP{{ row.latestVital.bloodPressure ?? '-' }}
            </span>
            <span v-else class="muted">暂无</span>
          </template>
        </w-table-column>
        <w-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <w-button link type="primary" @click.stop="selectAdmission(row)">接管</w-button>
          </template>
        </w-table-column>
      </w-table>
    </w-card>

    <template v-if="selectedId">
      <!-- 医嘱执行 -->
      <w-card class="page-card" title="医嘱核对与执行" subtitle="核对（OPEN→CONFIRMED）→ 执行（临时完成 / 长期执行中）">
        <div class="action-bar">
          <w-button type="primary" @click="vitalVisible = true">录入体征</w-button>
          <w-button @click="nursingVisible = true">护理记录</w-button>
          <w-button @click="bedVisible = true">床位看板 / 分床</w-button>
          <w-button @click="feeVisible = true">费用登记</w-button>
        </div>
        <w-table :data="orders" v-loading="ordersLoading" stripe size="small">
          <w-table-column prop="orderNo" label="医嘱号" width="180" />
          <w-table-column label="类型" width="80">
            <template #default="{ row }">{{ row.orderType === 'LONG_TERM' ? '长期' : '临时' }}</template>
          </w-table-column>
          <w-table-column prop="category" label="类别" width="90" />
          <w-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
          <w-table-column prop="frequency" label="频次" width="80" />
          <w-table-column label="状态" width="100">
            <template #default="{ row }">
              <w-tag :type="orderStatusType(row.status)" effect="light">{{ orderStatusText(row.status) }}</w-tag>
            </template>
          </w-table-column>
          <w-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <w-button v-if="row.status === 'OPEN'" link type="primary" @click="confirmOrder(row)">核对</w-button>
              <w-button v-if="row.status === 'CONFIRMED' || row.status === 'EXECUTING'" link type="success" @click="openExecute(row)">执行</w-button>
              <span v-if="row.status === 'COMPLETED' || row.status === 'STOPPED'" class="muted">-</span>
            </template>
          </w-table-column>
        </w-table>
      </w-card>

      <!-- 体征与护理记录 -->
      <w-card class="page-card" title="体征 / 护理记录" subtitle="当前患者最近记录">
        <div class="record-grid">
          <div>
            <div class="section-title">生命体征</div>
            <w-table :data="vitals" v-loading="vitalsLoading" size="small" stripe>
              <w-table-column prop="recordTime" label="时间" width="170" />
              <w-table-column label="体温℃" width="80">
                <template #default="{ row }">{{ row.temperature ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="脉搏" width="70">
                <template #default="{ row }">{{ row.pulse ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="呼吸" width="70">
                <template #default="{ row }">{{ row.respiration ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="血压" width="100">
                <template #default="{ row }">{{ row.bloodPressure ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="血氧%">
                <template #default="{ row }">{{ row.bloodOxygen ?? '-' }}</template>
              </w-table-column>
            </w-table>
          </div>
          <div>
            <div class="section-title">护理病历</div>
            <w-table :data="nursings" v-loading="nursingLoading" size="small" stripe>
              <w-table-column prop="recordTime" label="时间" width="170" />
              <w-table-column label="类型" width="90">
                <template #default="{ row }">{{ row.recordType === 'IO' ? '出入量' : '护理' }}</template>
              </w-table-column>
              <w-table-column prop="content" label="内容" min-width="160" show-overflow-tooltip />
              <w-table-column label="入量(ml)" width="90">
                <template #default="{ row }">{{ row.intakeMl ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="出量(ml)">
                <template #default="{ row }">{{ row.outputMl ?? '-' }}</template>
              </w-table-column>
            </w-table>
          </div>
        </div>
      </w-card>
    </template>

    <!-- 体征录入 -->
    <w-dialog v-model="vitalVisible" title="录入生命体征" width="520px">
      <w-form :model="vitalForm" label-width="100px">
        <w-form-item label="体温 ℃">
          <w-input-number v-model="vitalForm.temperature" :min="33" :max="43" :step="0.1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="脉搏 次/分">
          <w-input-number v-model="vitalForm.pulse" :min="30" :max="220" style="width: 100%" />
        </w-form-item>
        <w-form-item label="呼吸 次/分">
          <w-input-number v-model="vitalForm.respiration" :min="8" :max="50" style="width: 100%" />
        </w-form-item>
        <w-form-item label="血压">
          <w-input v-model="vitalForm.bloodPressure" placeholder="如 120/80" />
        </w-form-item>
        <w-form-item label="血氧 %">
          <w-input-number v-model="vitalForm.bloodOxygen" :min="50" :max="100" style="width: 100%" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="vitalVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="saveVital">保存</w-button>
      </template>
    </w-dialog>

    <!-- 护理记录 -->
    <w-dialog v-model="nursingVisible" title="录入护理记录" width="520px">
      <w-form :model="nursingForm" label-width="100px">
        <w-form-item label="记录类型">
          <w-radio-group v-model="nursingForm.recordType">
            <w-radio value="ROUTINE">护理记录</w-radio>
            <w-radio value="IO">出入量</w-radio>
          </w-radio-group>
        </w-form-item>
        <w-form-item label="内容">
          <w-input v-model="nursingForm.content" type="textarea" :rows="3" placeholder="护理内容/病情观察" />
        </w-form-item>
        <template v-if="nursingForm.recordType === 'IO'">
          <w-form-item label="入量 ml">
            <w-input-number v-model="nursingForm.intakeMl" :min="0" style="width: 100%" />
          </w-form-item>
          <w-form-item label="出量 ml">
            <w-input-number v-model="nursingForm.outputMl" :min="0" style="width: 100%" />
          </w-form-item>
        </template>
      </w-form>
      <template #footer>
        <w-button @click="nursingVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="saveNursing">保存</w-button>
      </template>
    </w-dialog>

    <!-- 床位看板 -->
    <w-dialog v-model="bedVisible" title="床位看板 / 分床转床" width="760px">
      <w-select v-model="bedDeptId" placeholder="选择科室查看床位" filterable style="margin-bottom: 12px" @change="loadBeds">
        <w-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
      </w-select>
      <w-table :data="beds" v-loading="bedsLoading" size="small" stripe>
        <w-table-column prop="roomNo" label="房间" width="90" />
        <w-table-column prop="bedNo" label="床位" width="80" />
        <w-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.bedType === 'ICU' ? 'ICU' : row.bedType === 'ISOLATION' ? '隔离' : '普通' }}</template>
        </w-table-column>
        <w-table-column label="日费" width="90">
          <template #default="{ row }">￥{{ fmt(row.dailyFee) }}</template>
        </w-table-column>
        <w-table-column label="状态" width="100">
          <template #default="{ row }">
            <w-tag :type="row.status === 'AVAILABLE' ? 'success' : row.status === 'OCCUPIED' ? 'danger' : 'info'" effect="light">
              {{ row.status === 'AVAILABLE' ? '空闲' : row.status === 'OCCUPIED' ? '占用' : '维修' }}
            </w-tag>
          </template>
        </w-table-column>
        <w-table-column prop="occupantAdmissionNo" label="占用住院号" />
        <w-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <w-button v-if="row.status === 'AVAILABLE' && selectedId" link type="primary" @click="assignBed(row)">分床</w-button>
            <span v-else class="muted">-</span>
          </template>
        </w-table-column>
      </w-table>
    </w-dialog>

    <!-- 费用登记 -->
    <w-dialog v-model="feeVisible" title="费用登记" width="520px">
      <w-form :model="feeForm" label-width="100px">
        <w-form-item label="费用类型">
          <w-select v-model="feeForm.feeType">
            <w-option label="诊疗费" value="TREATMENT" />
            <w-option label="护理费" value="NURSING" />
            <w-option label="药品费" value="DRUG" />
            <w-option label="检查费" value="EXAM" />
            <w-option label="检验费" value="LAB" />
            <w-option label="其他" value="OTHER" />
          </w-select>
        </w-form-item>
        <w-form-item label="项目名称">
          <w-input v-model="feeForm.itemName" placeholder="项目名称" />
        </w-form-item>
        <w-form-item label="金额 ￥">
          <w-input-number v-model="feeForm.amount" :min="0" :precision="2" style="width: 100%" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="feeVisible = false">取消</w-button>
        <w-button type="primary" :loading="submitting" @click="saveFee">保存</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { WMessage, WMessageBox } from 'win-design-next'
import {
  addNursingRecordApi,
  assignBedApi,
  confirmOrderApi,
  executeOrderApi,
  getBedsApi,
  getNursingRecordsApi,
  getOrdersApi,
  getOverviewApi,
  getVitalsApi,
  postFeeApi,
  recordVitalApi,
} from '@/api/inpatient'
import { getDepartmentsApi } from '@/api/clinic'
import type {
  BedVO,
  DepartmentVO,
  InpatientFeeVO,
  InpatientOverviewVO,
  MedicalOrderVO,
  NursingRecordVO,
  VitalSignVO,
} from '@/types'

const overviewLoading = ref(false)
const ordersLoading = ref(false)
const vitalsLoading = ref(false)
const nursingLoading = ref(false)
const bedsLoading = ref(false)
const submitting = ref(false)

const overview = ref<InpatientOverviewVO[]>([])
const departments = ref<DepartmentVO[]>([])
const selectedId = ref<number | null>(null)
const orders = ref<MedicalOrderVO[]>([])
const vitals = ref<VitalSignVO[]>([])
const nursings = ref<NursingRecordVO[]>([])
const beds = ref<BedVO[]>([])
const bedDeptId = ref<number | null>(null)

const vitalVisible = ref(false)
const nursingVisible = ref(false)
const bedVisible = ref(false)
const feeVisible = ref(false)

const vitalForm = reactive({
  temperature: 36.5 as number | null,
  pulse: 80 as number | null,
  respiration: 18 as number | null,
  bloodPressure: '',
  bloodOxygen: 98 as number | null,
})
const nursingForm = reactive({ recordType: 'ROUTINE', content: '', intakeMl: null as number | null, outputMl: null as number | null })
const feeForm = reactive({ feeType: 'TREATMENT', itemName: '', amount: 0 as number | null })

function fmt(v?: number | null): string {
  return (v ?? 0).toFixed(2)
}

function orderStatusText(s?: string): string {
  const map: Record<string, string> = {
    OPEN: '待核对', CONFIRMED: '已核对', EXECUTING: '执行中', COMPLETED: '已完成', STOPPED: '已停止',
  }
  return map[s ?? ''] ?? s ?? '-'
}
function orderStatusType(s?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    OPEN: 'warning', CONFIRMED: 'primary', EXECUTING: 'primary', COMPLETED: 'success', STOPPED: 'info',
  }
  return map[s ?? ''] ?? 'info'
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await getOverviewApi({})
  } catch (e) {
    WMessage.error((e as Error).message || '加载总览失败')
  } finally {
    overviewLoading.value = false
  }
}

async function selectAdmission(row: InpatientOverviewVO) {
  selectedId.value = row.admissionId
  loadOrders()
  loadVitals()
  loadNursings()
}

async function loadOrders() {
  if (!selectedId.value) return
  ordersLoading.value = true
  try {
    orders.value = await getOrdersApi({ admissionId: selectedId.value })
  } catch (e) {
    WMessage.error((e as Error).message || '加载医嘱失败')
  } finally {
    ordersLoading.value = false
  }
}

async function loadVitals() {
  if (!selectedId.value) return
  vitalsLoading.value = true
  try {
    vitals.value = await getVitalsApi({ admissionId: selectedId.value, limit: 20 })
  } catch { /* ignore */ } finally {
    vitalsLoading.value = false
  }
}

async function loadNursings() {
  if (!selectedId.value) return
  nursingLoading.value = true
  try {
    nursings.value = await getNursingRecordsApi({ admissionId: selectedId.value, limit: 20 })
  } catch { /* ignore */ } finally {
    nursingLoading.value = false
  }
}

async function confirmOrder(row: MedicalOrderVO) {
  try {
    await confirmOrderApi(row.id)
    WMessage.success('医嘱已核对')
    loadOrders()
    loadOverview()
  } catch (e) {
    WMessage.error((e as Error).message || '核对失败')
  }
}

async function openExecute(row: MedicalOrderVO) {
  let result = ''
  try {
    const input = await WMessageBox.prompt('请输入执行结果/备注', `执行医嘱 ${row.orderNo}`, {
      confirmButtonText: '确认执行',
      cancelButtonText: '取消',
      inputValue: '',
    })
    result = (input?.value as string) || ''
  } catch { return }
  try {
    await executeOrderApi(row.id, result || undefined)
    WMessage.success('执行记录已保存')
    loadOrders()
    loadOverview()
  } catch (e) {
    WMessage.error((e as Error).message || '执行失败')
  }
}

async function saveVital() {
  if (!selectedId.value) return
  submitting.value = true
  try {
    await recordVitalApi({
      admissionId: selectedId.value,
      temperature: vitalForm.temperature ?? undefined,
      pulse: vitalForm.pulse ?? undefined,
      respiration: vitalForm.respiration ?? undefined,
      bloodPressure: vitalForm.bloodPressure || undefined,
      bloodOxygen: vitalForm.bloodOxygen ?? undefined,
    })
    WMessage.success('体征已录入')
    vitalVisible.value = false
    loadVitals()
    loadOverview()
  } catch (e) {
    WMessage.error((e as Error).message || '录入失败')
  } finally {
    submitting.value = false
  }
}

async function saveNursing() {
  if (!selectedId.value) return
  submitting.value = true
  try {
    await addNursingRecordApi({
      admissionId: selectedId.value,
      recordType: nursingForm.recordType,
      content: nursingForm.content || undefined,
      intakeMl: nursingForm.intakeMl ?? undefined,
      outputMl: nursingForm.outputMl ?? undefined,
    })
    WMessage.success('护理记录已保存')
    nursingVisible.value = false
    nursingForm.content = ''
    loadNursings()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function loadBeds() {
  bedsLoading.value = true
  try {
    beds.value = await getBedsApi(bedDeptId.value ? { departmentId: bedDeptId.value } : {})
  } catch (e) {
    WMessage.error((e as Error).message || '加载床位失败')
  } finally {
    bedsLoading.value = false
  }
}

async function assignBed(row: BedVO) {
  if (!selectedId.value) return
  try {
    await assignBedApi({ admissionId: selectedId.value, bedId: row.id })
    WMessage.success('分床成功')
    loadBeds()
    loadOverview()
  } catch (e) {
    WMessage.error((e as Error).message || '分床失败')
  }
}

async function saveFee() {
  if (!selectedId.value) return
  if (!feeForm.itemName.trim()) {
    WMessage.warning('请填写项目名称')
    return
  }
  submitting.value = true
  try {
    const fee = await postFeeApi({
      admissionId: selectedId.value,
      feeType: feeForm.feeType,
      itemName: feeForm.itemName,
      amount: feeForm.amount,
    })
    WMessage.success(`费用已登记：￥${fmt((fee as InpatientFeeVO).amount)}`)
    feeVisible.value = false
    feeForm.itemName = ''
    feeForm.amount = 0
    loadOverview()
  } catch (e) {
    WMessage.error((e as Error).message || '登记失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadOverview()
  loadDepartments()
})

async function loadDepartments() {
  try {
    departments.value = await getDepartmentsApi()
  } catch { /* ignore */ }
}
</script>

<style scoped>
.page { padding: 4px; display: flex; flex-direction: column; gap: 12px; }
.page-card { max-width: 1280px; margin: 0 auto; width: 100%; }
.muted { color: #aaa; }
.action-bar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.section-title { font-weight: 600; margin: 4px 0 8px; }
.record-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
</style>
