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
import {
  batchInboundApi,
  createDrugReturnApi,
  createDrugRuleApi,
  createDrugTransferApi,
  deleteDrugRuleApi,
  getBatchListApi,
  getDrugReturnListApi,
  getDrugRuleListApi,
  getNarcoticRegisterListApi,
  listExpiringBatchesApi,
  scrapBatchApi,
  updateDrugTypeApi,
} from '@/api/drugs'
import { useUserStore } from '@/stores/user'
import type {
  Drug,
  DrugBatch,
  DrugDispense,
  DrugInventory,
  DrugReturn,
  DrugRule,
  NarcoticRegister,
} from '@/types'

const userStore = useUserStore()

const tab = ref('drugs')
/** 药事操作权限：批次/调拨/退药/CDSS 规则/麻精登记均为药师或管理员 */
const canPharmacy = computed(() => userStore.isPharmacist || userStore.isAdmin)

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

// ---------------- 分类 / 管控 / 抗菌分级元数据（V8 B1/B8/B10） ----------------
const DRUG_TYPE_OPTIONS = [
  { value: 'WESTERN', label: '西药' },
  { value: 'CHINESE_PATENT', label: '中成药' },
  { value: 'HERBAL', label: '中药饮片' },
] as const

function drugTypeMeta(drugType?: string) {
  return DRUG_TYPE_OPTIONS.find((o) => o.value === drugType)
}

function drugTypeTagType(drugType?: string): TagType {
  if (drugType === 'HERBAL') return 'warning'
  if (drugType === 'CHINESE_PATENT') return 'success'
  return 'primary'
}

/** 管控级别：麻醉 / 精一 / 精二 红色 tag（NORMAL 不展示） */
const CONTROL_LEVEL_META: Record<string, { label: string; type: TagType }> = {
  NARCOTIC: { label: '麻醉', type: 'danger' },
  PSYCHIATRIC_1: { label: '精一', type: 'danger' },
  PSYCHIATRIC_2: { label: '精二', type: 'danger' },
}

function controlLevelMeta(level?: string) {
  return level ? CONTROL_LEVEL_META[level] : undefined
}

const ANTIBIOTIC_LEVEL_OPTIONS = [
  { value: 'NON_RESTRICTED', label: '非限制使用级' },
  { value: 'RESTRICTED', label: '限制使用级' },
  { value: 'SPECIAL', label: '特殊使用级' },
] as const

function antibioticLevelText(level?: string) {
  return ANTIBIOTIC_LEVEL_OPTIONS.find((o) => o.value === level)?.label
}

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
const drugQuery = reactive({ keyword: '', drugType: '' })
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

/** /admin/drug/page 后端暂无 drugType 查询参数，分类筛选对当前页结果做前端过滤 */
const displayedDrugs = computed(() =>
  drugQuery.drugType ? drugs.value.filter((d) => d.drugType === drugQuery.drugType) : drugs.value,
)

// ---------------- 分类设置（PUT /admin/drug/{id}/type） ----------------
const typeDialogVisible = ref(false)
const typeEditing = ref<Drug | null>(null)
const typeSubmitting = ref(false)
const typeForm = reactive({
  drugType: 'WESTERN',
  controlLevel: '',
  antibioticLevel: '',
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

// ---------------- 批次效期（药库批次） ----------------
const EXPIRY_WARN_DAYS = 30

const batchLoading = ref(false)
const batches = ref<DrugBatch[]>([])
const batchTotal = ref(0)
const batchPage = ref(1)
const batchSize = ref(10)
const batchQuery = reactive({ drugId: undefined as number | undefined })

const batchInDialogVisible = ref(false)
const batchInSubmitting = ref(false)
const batchInForm = reactive({
  drugId: undefined as number | undefined,
  batchNo: '',
  supplier: '',
  quantity: 1,
  productionDate: '',
  expiryDate: '',
})

const expiringLoading = ref(false)
const expiringBatches = ref<DrugBatch[]>([])

const scrapDialogVisible = ref(false)
const scrapRow = ref<DrugBatch | null>(null)
const scrapReason = ref('')
const scrapSubmitting = ref(false)

// ---------------- 调拨（药库 → 药房/科室） ----------------
const transferDialogVisible = ref(false)
const transferSubmitting = ref(false)
const transferForm = reactive({
  drugId: undefined as number | undefined,
  quantity: 1,
  fromLocation: 'WAREHOUSE',
  toLocation: 'PHARMACY',
  batchNo: '',
})

const BATCH_STATUS_TEXT: Record<string, string> = { ACTIVE: '在库', EXHAUSTED: '耗尽', SCRAPPED: '已报损' }
const BATCH_INBOUND_TYPE_TEXT: Record<string, string> = { PURCHASE: '采购入库', RETURN: '退药回冲', TRANSFER: '调拨入' }
const LOCATION_OPTIONS = [
  { value: 'WAREHOUSE', label: '药库' },
  { value: 'PHARMACY', label: '药房' },
  { value: 'DEPT', label: '临床科室' },
] as const

function locationText(value?: string) {
  return LOCATION_OPTIONS.find((o) => o.value === value)?.label || value || '-'
}

/** 距失效日期剩余天数（按自然日） */
function daysToExpiry(expiryDate?: string): number | null {
  if (!expiryDate) return null
  const end = new Date(`${expiryDate}T00:00:00`).getTime()
  if (Number.isNaN(end)) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.ceil((end - today.getTime()) / 86400000)
}

/** 是否为 30 天内到期批次（效期预警行高亮依据） */
function isExpiring(row: DrugBatch): boolean {
  const days = daysToExpiry(row.expiryDate)
  return days !== null && days <= EXPIRY_WARN_DAYS
}

/** 剩余天数（向下取 0） */
function remainingDays(row: DrugBatch): number {
  const days = daysToExpiry(row.expiryDate)
  return days === null ? 0 : Math.max(days, 0)
}

/** 30 天内到期批次行高亮 */
function batchRowClass({ row }: { row: DrugBatch }): string {
  return isExpiring(row) ? 'batch-row--expiring' : ''
}

// ---------------- 退药登记（发药后退药冲账） ----------------
const returnLoading = ref(false)
const returnList = ref<DrugReturn[]>([])
const returnTotal = ref(0)
const returnPage = ref(1)
const returnSize = ref(10)

const returnDialogVisible = ref(false)
const returnSubmitting = ref(false)
const returnForm = reactive({
  prescriptionId: undefined as number | undefined,
  patientId: undefined as number | undefined,
  drugId: undefined as number | undefined,
  quantity: 1,
  refundAmount: 0,
  reason: '',
})

// ---------------- CDSS 合理用药规则 ----------------
const RULE_TYPE_OPTIONS = [
  { value: 'MAX_DOSE', label: '剂量上限（MAX_DOSE）' },
  { value: 'DRUG_DUPLICATE', label: '重复用药（DRUG_DUPLICATE）' },
  { value: 'DRUG_CONFLICT', label: '配伍禁忌（DRUG_CONFLICT）' },
  { value: 'PREGNANCY', label: '妊娠禁忌（PREGNANCY）' },
] as const

const SEVERITY_OPTIONS = [
  { value: 'BLOCK', label: '拦截（BLOCK）' },
  { value: 'WARN', label: '警告（WARN）' },
] as const

function ruleTypeText(ruleType?: string) {
  return RULE_TYPE_OPTIONS.find((o) => o.value === ruleType)?.label || ruleType || '-'
}

function severityMeta(severity?: string): { label: string; type: TagType } {
  if (severity === 'BLOCK') return { label: '拦截', type: 'danger' }
  if (severity === 'WARN') return { label: '警告', type: 'warning' }
  return { label: severity || '-', type: 'info' }
}

const ruleLoading = ref(false)
const rules = ref<DrugRule[]>([])
const ruleQuery = reactive({ ruleType: '' })

const ruleDialogVisible = ref(false)
const ruleSubmitting = ref(false)
const ruleForm = reactive({
  ruleType: 'MAX_DOSE',
  drugId: undefined as number | undefined,
  pairedDrugId: undefined as number | undefined,
  maxSingleDose: undefined as number | undefined,
  severity: 'WARN',
  description: '',
})

// ---------------- 管控（麻精五专登记查询） ----------------
const NARCOTIC_ACTION_META: Record<string, { label: string; type: TagType }> = {
  INBOUND: { label: '入库', type: 'success' },
  OUTBOUND: { label: '发药', type: 'primary' },
  RETURN: { label: '退药', type: 'warning' },
  SCRAP: { label: '报损', type: 'danger' },
}

function narcoticActionMeta(action?: string) {
  return NARCOTIC_ACTION_META[action || ''] || { label: action || '-', type: 'info' as TagType }
}

const narcoticLoading = ref(false)
const narcoticList = ref<NarcoticRegister[]>([])
const narcoticDrugId = ref<number | undefined>()
const narcoticQueried = ref(false)

const invDialogTitle = computed(() => {
  if (invDialogType.value === 'inbound') return '药品入库'
  if (invDialogType.value === 'outbound') return '药品出库'
  return '盘点调整'
})

const dispStatusMap: Record<string, { text: string; type: TagType }> = {
  PENDING_REVIEW: { text: '待审核', type: 'warning' },
  REVIEW_PASSED: { text: '审核通过', type: 'primary' },
  REVIEW_REJECTED: { text: '审核驳回', type: 'danger' },
  DISPENSED: { text: '已发药', type: 'success' },
}

function dispenseStatus(row: DrugDispense) {
  return dispStatusMap[row.status || ''] || { text: row.status || '-', type: 'info' as const }
}

/** 新增 Tab 首次激活时懒加载，避免首屏请求过多 */
const loadedTabs = new Set<string>(['drugs', 'inventory', 'dispense'])

function onTabChange(name: string | number) {
  const key = String(name)
  if (loadedTabs.has(key)) return
  loadedTabs.add(key)
  if (key === 'batch') {
    fetchBatchList()
    fetchExpiringBatches()
  } else if (key === 'return') {
    fetchReturnList()
  } else if (key === 'rule') {
    fetchRules()
  }
  // 管控 Tab 需先选择药品，不自动查询
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

// ---------------- 分类设置 ----------------
function openTypeDialog(row: Drug) {
  typeEditing.value = row
  typeForm.drugType = row.drugType || 'WESTERN'
  typeForm.controlLevel = row.controlLevel && row.controlLevel !== 'NORMAL' ? row.controlLevel : ''
  typeForm.antibioticLevel = row.antibioticLevel || ''
  typeDialogVisible.value = true
}

async function saveDrugType() {
  const row = typeEditing.value
  if (!row?.id) return
  if (!typeForm.drugType) {
    WMessage.warning('药品分类必选')
    return
  }
  typeSubmitting.value = true
  try {
    await updateDrugTypeApi(row.id, {
      drugType: typeForm.drugType,
      controlLevel: typeForm.controlLevel || undefined,
      antibioticLevel: typeForm.antibioticLevel || undefined,
    })
    WMessage.success('分类设置已保存')
    typeDialogVisible.value = false
    fetchDrugs()
  } catch (e) {
    WMessage.error((e as Error).message || '分类设置失败')
  } finally {
    typeSubmitting.value = false
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

// ---------------- 批次效期 ----------------
async function fetchBatchList() {
  batchLoading.value = true
  try {
    const page = await getBatchListApi({
      drugId: batchQuery.drugId,
      pageNo: batchPage.value,
      pageSize: batchSize.value,
    })
    batches.value = page.records || []
    batchTotal.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '批次列表加载失败')
  } finally {
    batchLoading.value = false
  }
}

async function fetchExpiringBatches() {
  expiringLoading.value = true
  try {
    expiringBatches.value = await listExpiringBatchesApi()
  } catch (e) {
    WMessage.error((e as Error).message || '效期预警加载失败')
  } finally {
    expiringLoading.value = false
  }
}

function openBatchInboundDialog() {
  batchInForm.drugId = undefined
  batchInForm.batchNo = ''
  batchInForm.supplier = ''
  batchInForm.quantity = 1
  batchInForm.productionDate = ''
  batchInForm.expiryDate = ''
  drugOptions.value = []
  searchDrugOptions('')
  batchInDialogVisible.value = true
}

async function saveBatchInbound() {
  if (!batchInForm.drugId) {
    WMessage.warning('请选择药品')
    return
  }
  if (!batchInForm.batchNo.trim()) {
    WMessage.warning('批次号必填')
    return
  }
  if (!batchInForm.quantity || batchInForm.quantity <= 0) {
    WMessage.warning('入库数量必须大于 0')
    return
  }
  if (!batchInForm.expiryDate) {
    WMessage.warning('失效日期必填')
    return
  }
  const expiryDays = daysToExpiry(batchInForm.expiryDate)
  if (expiryDays !== null && expiryDays < 0) {
    WMessage.warning('批次已过期，禁止入库')
    return
  }
  batchInSubmitting.value = true
  try {
    await batchInboundApi({
      drugId: batchInForm.drugId,
      batchNo: batchInForm.batchNo.trim(),
      supplier: batchInForm.supplier.trim(),
      quantity: batchInForm.quantity,
      productionDate: batchInForm.productionDate || undefined,
      expiryDate: batchInForm.expiryDate,
    })
    WMessage.success('采购入库成功')
    batchInDialogVisible.value = false
    fetchBatchList()
    fetchExpiringBatches()
  } catch (e) {
    WMessage.error((e as Error).message || '采购入库失败')
  } finally {
    batchInSubmitting.value = false
  }
}

function openScrapDialog(row: DrugBatch) {
  scrapRow.value = row
  scrapReason.value = ''
  scrapDialogVisible.value = true
}

async function confirmScrap() {
  const row = scrapRow.value
  if (!row?.id) return
  scrapSubmitting.value = true
  try {
    await scrapBatchApi(row.id, scrapReason.value.trim() || undefined)
    WMessage.success('报损完成')
    scrapDialogVisible.value = false
    fetchBatchList()
    fetchExpiringBatches()
  } catch (e) {
    WMessage.error((e as Error).message || '报损失败')
  } finally {
    scrapSubmitting.value = false
  }
}

// ---------------- 调拨 ----------------
function openTransferDialog() {
  transferForm.drugId = undefined
  transferForm.quantity = 1
  transferForm.fromLocation = 'WAREHOUSE'
  transferForm.toLocation = 'PHARMACY'
  transferForm.batchNo = ''
  drugOptions.value = []
  searchDrugOptions('')
  transferDialogVisible.value = true
}

async function saveTransfer() {
  if (!transferForm.drugId) {
    WMessage.warning('请选择药品')
    return
  }
  if (!transferForm.quantity || transferForm.quantity <= 0) {
    WMessage.warning('调拨数量必须大于 0')
    return
  }
  if (!transferForm.fromLocation || !transferForm.toLocation) {
    WMessage.warning('请选择调出/调入位置')
    return
  }
  if (transferForm.fromLocation === transferForm.toLocation) {
    WMessage.warning('调出与调入位置不能相同')
    return
  }
  transferSubmitting.value = true
  try {
    await createDrugTransferApi({
      drugId: transferForm.drugId,
      quantity: transferForm.quantity,
      fromLocation: transferForm.fromLocation,
      toLocation: transferForm.toLocation,
      batchNo: transferForm.batchNo.trim() || undefined,
    })
    WMessage.success('调拨单已创建')
    transferDialogVisible.value = false
    fetchBatchList()
  } catch (e) {
    WMessage.error((e as Error).message || '调拨失败')
  } finally {
    transferSubmitting.value = false
  }
}

// ---------------- 退药登记 ----------------
async function fetchReturnList() {
  returnLoading.value = true
  try {
    const page = await getDrugReturnListApi({
      pageNo: returnPage.value,
      pageSize: returnSize.value,
    })
    returnList.value = page.records || []
    returnTotal.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '退药记录加载失败')
  } finally {
    returnLoading.value = false
  }
}

function openReturnDialog() {
  returnForm.prescriptionId = undefined
  returnForm.patientId = undefined
  returnForm.drugId = undefined
  returnForm.quantity = 1
  returnForm.refundAmount = 0
  returnForm.reason = ''
  drugOptions.value = []
  searchDrugOptions('')
  returnDialogVisible.value = true
}

async function saveReturn() {
  if (!returnForm.prescriptionId) {
    WMessage.warning('处方ID必填')
    return
  }
  if (!returnForm.patientId) {
    WMessage.warning('患者ID必填')
    return
  }
  if (!returnForm.drugId) {
    WMessage.warning('请选择退药药品')
    return
  }
  if (!returnForm.quantity || returnForm.quantity <= 0) {
    WMessage.warning('退药数量必须大于 0')
    return
  }
  returnSubmitting.value = true
  try {
    await createDrugReturnApi({
      prescriptionId: returnForm.prescriptionId,
      patientId: returnForm.patientId,
      drugId: returnForm.drugId,
      quantity: returnForm.quantity,
      refundAmount: returnForm.refundAmount || 0,
      reason: returnForm.reason.trim() || undefined,
    })
    WMessage.success('退药冲账完成')
    returnDialogVisible.value = false
    fetchReturnList()
  } catch (e) {
    WMessage.error((e as Error).message || '退药失败')
  } finally {
    returnSubmitting.value = false
  }
}

// ---------------- CDSS 规则 ----------------
async function fetchRules() {
  ruleLoading.value = true
  try {
    rules.value = await getDrugRuleListApi({ ruleType: ruleQuery.ruleType || undefined })
  } catch (e) {
    WMessage.error((e as Error).message || '规则列表加载失败')
  } finally {
    ruleLoading.value = false
  }
}

function openRuleDialog() {
  ruleForm.ruleType = 'MAX_DOSE'
  ruleForm.drugId = undefined
  ruleForm.pairedDrugId = undefined
  ruleForm.maxSingleDose = undefined
  ruleForm.severity = 'WARN'
  ruleForm.description = ''
  ruleDialogVisible.value = true
}

async function saveRule() {
  if (!ruleForm.ruleType) {
    WMessage.warning('请选择规则类型')
    return
  }
  if (!ruleForm.drugId) {
    WMessage.warning('药品ID必填')
    return
  }
  if ((ruleForm.ruleType === 'DRUG_DUPLICATE' || ruleForm.ruleType === 'DRUG_CONFLICT') && !ruleForm.pairedDrugId) {
    WMessage.warning('重复用药/配伍禁忌规则需填写对照药品ID')
    return
  }
  ruleSubmitting.value = true
  try {
    await createDrugRuleApi({
      ruleType: ruleForm.ruleType,
      drugId: ruleForm.drugId,
      pairedDrugId: ruleForm.pairedDrugId,
      maxSingleDose: ruleForm.maxSingleDose,
      severity: ruleForm.severity,
      description: ruleForm.description.trim() || undefined,
      status: 1,
    })
    WMessage.success('规则已新增')
    ruleDialogVisible.value = false
    fetchRules()
  } catch (e) {
    WMessage.error((e as Error).message || '规则保存失败')
  } finally {
    ruleSubmitting.value = false
  }
}

async function deleteRule(row: DrugRule) {
  if (!row.id) return
  try {
    await WMessageBox.confirm(`确定删除该条规则吗？删除后规则停用（软删）`, '删除规则', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteDrugRuleApi(row.id)
    WMessage.success('规则已删除')
    fetchRules()
  } catch (e) {
    WMessage.error((e as Error).message || '规则删除失败')
  }
}

// ---------------- 麻精五专登记查询 ----------------
async function fetchNarcotic() {
  if (!narcoticDrugId.value) {
    WMessage.warning('请先选择药品')
    return
  }
  narcoticLoading.value = true
  try {
    narcoticList.value = await getNarcoticRegisterListApi(narcoticDrugId.value)
    narcoticQueried.value = true
  } catch (e) {
    WMessage.error((e as Error).message || '麻精登记查询失败')
  } finally {
    narcoticLoading.value = false
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
      <p class="page-subtitle">药品目录、库存动态、批次效期、退药冲账与处方发药审核的集中管理</p>
    </div>

    <!-- 主体 -->
    <w-card shadow="never" class="hospital-card">
      <w-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 药品目录 -->
        <w-tab-pane label="药品目录" name="drugs">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-input
                  v-model="drugQuery.keyword"
                  placeholder="药品名称 / 编码 / 通用名"
                  clearable
                  style="width: 220px"
                  @keyup.enter="drugPage = 1; fetchDrugs()"
                  @clear="drugPage = 1; fetchDrugs()"
                />
                <w-select
                  v-model="drugQuery.drugType"
                  placeholder="全部分类"
                  clearable
                  style="width: 150px"
                >
                  <w-option v-for="o in DRUG_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </w-select>
                <w-button type="primary" :icon="Search" @click="drugPage = 1; fetchDrugs()">查询</w-button>
                <w-button v-if="userStore.isAdmin" type="primary" plain :icon="Plus" @click="openDrugDialog()">新增药品</w-button>
              </div>
            </div>

            <w-table :data="displayedDrugs" row-key="id" border stripe :loading="drugLoading" empty-text="暂无数据" size="default">
              <w-table-column prop="drugCode" label="编码" min-width="110" show-overflow-tooltip />
              <w-table-column prop="drugName" label="名称" min-width="120" show-overflow-tooltip />
              <w-table-column prop="genericName" label="通用名" min-width="110" show-overflow-tooltip />
              <w-table-column prop="specification" label="规格" min-width="90" show-overflow-tooltip />
              <w-table-column prop="dosageForm" label="剂型" width="80" show-overflow-tooltip />
              <w-table-column label="分类" width="96" align="center">
                <template #default="{ row }">
                  <w-tag v-if="drugTypeMeta(row.drugType)" :type="drugTypeTagType(row.drugType)" effect="light" size="small">
                    {{ drugTypeMeta(row.drugType)!.label }}
                  </w-tag>
                  <span v-else class="muted">-</span>
                </template>
              </w-table-column>
              <w-table-column label="管控级别" width="96" align="center">
                <template #default="{ row }">
                  <w-tag v-if="controlLevelMeta(row.controlLevel)" :type="controlLevelMeta(row.controlLevel)!.type" effect="dark" size="small">
                    {{ controlLevelMeta(row.controlLevel)!.label }}
                  </w-tag>
                  <span v-else class="muted">-</span>
                </template>
              </w-table-column>
              <w-table-column prop="manufacturer" label="生产厂家" min-width="140" show-overflow-tooltip />
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
              <w-table-column v-if="userStore.isAdmin" label="操作" width="120" fixed="right" align="center">
                <template #default="{ row }">
                  <w-button size="small" link type="primary" @click="openDrugDialog(row)">编辑</w-button>
                  <w-button size="small" link type="warning" @click="openTypeDialog(row)">分类</w-button>
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

        <!-- 批次效期（药师/管理员） -->
        <w-tab-pane v-if="canPharmacy" label="批次效期" name="batch">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-select
                  v-model="batchQuery.drugId"
                  filterable
                  remote
                  clearable
                  :remote-method="searchDrugOptions"
                  :loading="drugSearchLoading"
                  placeholder="按药品筛选（可搜索）"
                  style="width: 240px"
                  @change="batchPage = 1; fetchBatchList()"
                >
                  <w-option v-for="opt in drugOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </w-select>
                <w-button type="primary" :icon="Search" @click="batchPage = 1; fetchBatchList()">查询</w-button>
                <w-button type="primary" plain :icon="Plus" @click="openBatchInboundDialog()">采购入库</w-button>
                <w-button type="warning" plain @click="openTransferDialog()">调拨</w-button>
                <w-button :icon="Refresh" @click="fetchBatchList(); fetchExpiringBatches()">刷新</w-button>
              </div>
            </div>

            <w-table
              :data="batches"
              row-key="id"
              border
              stripe
              size="default"
              :loading="batchLoading"
              empty-text="暂无批次"
              :row-class-name="batchRowClass"
            >
              <w-table-column prop="id" label="批次ID" width="80" align="center" />
              <w-table-column prop="drugId" label="药品ID" width="90" align="center" />
              <w-table-column prop="batchNo" label="批号" min-width="130" show-overflow-tooltip />
              <w-table-column prop="supplier" label="供货商" min-width="140" show-overflow-tooltip />
              <w-table-column prop="quantity" label="数量" width="80" align="center" />
              <w-table-column prop="productionDate" label="生产日期" width="110" align="center" />
              <w-table-column label="失效日期" width="150" align="center">
                <template #default="{ row }">
                  <span>{{ row.expiryDate || '-' }}</span>
                  <w-tag v-if="isExpiring(row)" type="danger" effect="light" size="small" class="expiry-days-tag">
                    剩 {{ remainingDays(row) }} 天
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column label="入库类型" width="96" align="center">
                <template #default="{ row }">
                  {{ BATCH_INBOUND_TYPE_TEXT[row.inboundType || ''] || row.inboundType || '-' }}
                </template>
              </w-table-column>
              <w-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <w-tag
                    :type="row.status === 'ACTIVE' ? 'success' : row.status === 'SCRAPPED' ? 'danger' : 'info'"
                    effect="light"
                    size="small"
                  >
                    {{ BATCH_STATUS_TEXT[row.status || ''] || row.status || '-' }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column label="操作" width="90" fixed="right" align="center">
                <template #default="{ row }">
                  <w-button v-if="row.status === 'ACTIVE'" size="small" link type="danger" @click="openScrapDialog(row)">报损</w-button>
                  <span v-else class="muted">-</span>
                </template>
              </w-table-column>
            </w-table>

            <div class="pager">
              <w-pagination
                v-model:current-page="batchPage"
                v-model:page-size="batchSize"
                :total="batchTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchBatchList"
                @size-change="batchPage = 1; fetchBatchList()"
              />
            </div>

            <!-- 效期预警：30 天内到期批次 -->
            <div class="expiring-block">
              <div class="expiring-block__head">
                <span class="expiring-block__title">效期预警</span>
                <span class="expiring-block__tip">以下为 {{ EXPIRY_WARN_DAYS }} 天内到期的在库批次，到期行高亮</span>
                <w-tag v-if="expiringBatches.length" type="danger" effect="light" size="small">{{ expiringBatches.length }} 批</w-tag>
              </div>
              <w-table
                :data="expiringBatches"
                row-key="id"
                border
                size="small"
                :loading="expiringLoading"
                empty-text="暂无 30 天内到期批次"
                :row-class-name="batchRowClass"
              >
                <w-table-column prop="id" label="批次ID" width="80" align="center" />
                <w-table-column prop="drugId" label="药品ID" width="90" align="center" />
                <w-table-column prop="batchNo" label="批号" min-width="120" show-overflow-tooltip />
                <w-table-column prop="quantity" label="在库数量" width="90" align="center" />
                <w-table-column prop="expiryDate" label="失效日期" width="110" align="center" />
                <w-table-column label="剩余天数" width="100" align="center">
                  <template #default="{ row }">
                    <w-tag :type="remainingDays(row) <= 7 ? 'danger' : 'warning'" effect="light" size="small">
                      {{ remainingDays(row) }} 天
                    </w-tag>
                  </template>
                </w-table-column>
              </w-table>
            </div>
          </div>
        </w-tab-pane>

        <!-- 退药登记（药师/管理员） -->
        <w-tab-pane v-if="canPharmacy" label="退药登记" name="return">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-button type="primary" plain :icon="Plus" @click="openReturnDialog()">退药登记</w-button>
                <w-button :icon="Refresh" @click="returnPage = 1; fetchReturnList()">刷新</w-button>
                <span class="table-toolbar__tip">退药冲账：库存回冲 + 批次回冲（RETURN）+ 麻精 RETURN 登记；退款金额按药品参考价 × 数量自动计算</span>
              </div>
            </div>

            <w-table :data="returnList" row-key="id" border stripe :loading="returnLoading" empty-text="暂无退药记录" size="default">
              <w-table-column prop="returnNo" label="退药单号" min-width="150" show-overflow-tooltip />
              <w-table-column prop="prescriptionId" label="处方ID" width="90" align="center" />
              <w-table-column prop="patientId" label="患者ID" width="90" align="center" />
              <w-table-column prop="drugId" label="药品ID" width="90" align="center" />
              <w-table-column prop="quantity" label="数量" width="80" align="center" />
              <w-table-column label="退款金额" width="110" align="right">
                <template #default="{ row }">
                  <span class="price">¥{{ Number(row.refundAmount ?? 0).toFixed(2) }}</span>
                </template>
              </w-table-column>
              <w-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
              <w-table-column prop="createTime" label="时间" min-width="150" show-overflow-tooltip />
            </w-table>

            <div class="pager">
              <w-pagination
                v-model:current-page="returnPage"
                v-model:page-size="returnSize"
                :total="returnTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchReturnList"
                @size-change="returnPage = 1; fetchReturnList()"
              />
            </div>
          </div>
        </w-tab-pane>

        <!-- CDSS 规则（药师/管理员） -->
        <w-tab-pane v-if="canPharmacy" label="CDSS 规则" name="rule">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-select v-model="ruleQuery.ruleType" placeholder="全部类型" clearable style="width: 200px" @change="fetchRules">
                  <w-option v-for="o in RULE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </w-select>
                <w-button type="primary" :icon="Search" @click="fetchRules">查询</w-button>
                <w-button type="primary" plain :icon="Plus" @click="openRuleDialog()">新增规则</w-button>
                <w-button :icon="Refresh" @click="fetchRules">刷新</w-button>
              </div>
            </div>

            <w-table :data="rules" row-key="id" border stripe :loading="ruleLoading" empty-text="暂无规则" size="default">
              <w-table-column prop="id" label="ID" width="70" align="center" />
              <w-table-column label="类型" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ ruleTypeText(row.ruleType) }}</template>
              </w-table-column>
              <w-table-column prop="drugId" label="药品ID" width="90" align="center" />
              <w-table-column label="对照药品ID" width="100" align="center">
                <template #default="{ row }">{{ row.pairedDrugId ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="单次上限（mg）" width="120" align="center">
                <template #default="{ row }">{{ row.maxSingleDose ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="严重级别" width="90" align="center">
                <template #default="{ row }">
                  <w-tag :type="severityMeta(row.severity).type" effect="light" size="small">
                    {{ severityMeta(row.severity).label }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
              <w-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <w-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" size="small">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column label="操作" width="80" fixed="right" align="center">
                <template #default="{ row }">
                  <w-button size="small" link type="danger" @click="deleteRule(row)">删除</w-button>
                </template>
              </w-table-column>
            </w-table>
          </div>
        </w-tab-pane>

        <!-- 管控：麻精五专登记查询（药师/管理员） -->
        <w-tab-pane v-if="canPharmacy" label="管控" name="control">
          <div class="tab-body">
            <div class="table-toolbar">
              <div class="table-toolbar__left">
                <w-select
                  v-model="narcoticDrugId"
                  filterable
                  remote
                  clearable
                  :remote-method="searchDrugOptions"
                  :loading="drugSearchLoading"
                  placeholder="选择药品查五专登记流水"
                  style="width: 260px"
                >
                  <w-option v-for="opt in drugOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </w-select>
                <w-button type="primary" :icon="Search" @click="fetchNarcotic">查询</w-button>
              </div>
            </div>

            <w-table
              :data="narcoticList"
              row-key="id"
              border
              stripe
              :loading="narcoticLoading"
              :empty-text="narcoticQueried ? '该药品暂无麻精登记流水' : '请先选择药品并查询'"
              size="default"
            >
              <w-table-column prop="createTime" label="时间" min-width="160" show-overflow-tooltip />
              <w-table-column label="动作" width="90" align="center">
                <template #default="{ row }">
                  <w-tag :type="narcoticActionMeta(row.action).type" effect="light" size="small">
                    {{ narcoticActionMeta(row.action).label }}
                  </w-tag>
                </template>
              </w-table-column>
              <w-table-column prop="quantity" label="数量" width="80" align="center" />
              <w-table-column prop="balance" label="结存" width="80" align="center" />
              <w-table-column label="处方ID" width="90" align="center">
                <template #default="{ row }">{{ row.prescriptionId ?? '-' }}</template>
              </w-table-column>
              <w-table-column label="患者ID" width="90" align="center">
                <template #default="{ row }">{{ row.patientId ?? '-' }}</template>
              </w-table-column>
              <w-table-column prop="operatorId" label="操作人ID" width="90" align="center" />
              <w-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
            </w-table>
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

    <!-- 分类设置弹窗（PUT /admin/drug/{id}/type） -->
    <w-dialog v-model="typeDialogVisible" title="分类设置" width="480px" :close-on-click-modal="false">
      <w-form :model="typeForm" label-width="90px">
        <w-form-item label="药品" required>
          <span class="type-drug-name">{{ typeEditing?.drugName || typeEditing?.drugCode || typeEditing?.id }}</span>
        </w-form-item>
        <w-form-item label="药品分类" required>
          <w-select v-model="typeForm.drugType" placeholder="请选择分类">
            <w-option v-for="o in DRUG_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="管控级别">
          <w-select v-model="typeForm.controlLevel" placeholder="普通药品留空" clearable>
            <w-option label="麻醉药品" value="NARCOTIC" />
            <w-option label="第一类精神（精一）" value="PSYCHIATRIC_1" />
            <w-option label="第二类精神（精二）" value="PSYCHIATRIC_2" />
          </w-select>
        </w-form-item>
        <w-form-item label="抗菌分级">
          <w-select v-model="typeForm.antibioticLevel" placeholder="非抗菌药品留空" clearable>
            <w-option v-for="o in ANTIBIOTIC_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="typeDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="typeSubmitting" @click="saveDrugType">保存</w-button>
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

    <!-- 采购入库弹窗（批次入库） -->
    <w-dialog v-model="batchInDialogVisible" title="采购入库（批次）" width="520px" :close-on-click-modal="false">
      <w-form :model="batchInForm" label-width="90px">
        <w-form-item label="药品" required>
          <w-select
            v-model="batchInForm.drugId"
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
        <w-form-item label="批次号" required>
          <w-input v-model="batchInForm.batchNo" placeholder="请输入生产批号" />
        </w-form-item>
        <w-form-item label="供货商">
          <w-input v-model="batchInForm.supplier" placeholder="供货商（选填）" />
        </w-form-item>
        <w-form-item label="数量" required>
          <w-input-number v-model="batchInForm.quantity" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="生产日期">
          <w-date-picker-pro
            v-model="batchInForm.productionDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="生产日期（选填）"
            style="width: 100%"
          />
        </w-form-item>
        <w-form-item label="失效日期" required>
          <w-date-picker-pro
            v-model="batchInForm.expiryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="失效日期"
            style="width: 100%"
          />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="batchInDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="batchInSubmitting" @click="saveBatchInbound">确认入库</w-button>
      </template>
    </w-dialog>

    <!-- 批次报损弹窗 -->
    <w-dialog v-model="scrapDialogVisible" title="批次报损" width="480px" :close-on-click-modal="false">
      <div class="scrap-body">
        <p class="scrap-body__tip">
          批次 {{ scrapRow?.batchNo || scrapRow?.id }}（药品ID {{ scrapRow?.drugId }}，在库
          {{ scrapRow?.quantity ?? 0 }}）清零并扣减总库存；管控药品将写入 SCRAP 五专登记。
        </p>
        <w-form label-width="90px">
          <w-form-item label="报损原因">
            <w-input v-model="scrapReason" type="textarea" :rows="3" maxlength="200" placeholder="请输入报损原因（选填）" />
          </w-form-item>
        </w-form>
      </div>
      <template #footer>
        <w-button @click="scrapDialogVisible = false">取消</w-button>
        <w-button type="danger" :loading="scrapSubmitting" @click="confirmScrap">确认报损</w-button>
      </template>
    </w-dialog>

    <!-- 调拨弹窗（药库 → 药房/科室） -->
    <w-dialog v-model="transferDialogVisible" title="药品调拨" width="500px" :close-on-click-modal="false">
      <w-form :model="transferForm" label-width="90px">
        <w-form-item label="药品" required>
          <w-select
            v-model="transferForm.drugId"
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
        <w-form-item label="调拨数量" required>
          <w-input-number v-model="transferForm.quantity" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="调出位置" required>
          <w-select v-model="transferForm.fromLocation" placeholder="调出位置">
            <w-option v-for="o in LOCATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="调入位置" required>
          <w-select v-model="transferForm.toLocation" placeholder="调入位置">
            <w-option v-for="o in LOCATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="批号">
          <w-input v-model="transferForm.batchNo" placeholder="关联批号（选填）" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="transferDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="transferSubmitting" @click="saveTransfer">确认调拨</w-button>
      </template>
    </w-dialog>

    <!-- 退药登记弹窗 -->
    <w-dialog v-model="returnDialogVisible" title="退药登记" width="520px" :close-on-click-modal="false">
      <w-form :model="returnForm" label-width="90px">
        <w-form-item label="处方ID" required>
          <w-input-number v-model="returnForm.prescriptionId" :min="1" :controls="false" placeholder="已发药的处方ID" style="width: 100%" />
        </w-form-item>
        <w-form-item label="患者ID" required>
          <w-input-number v-model="returnForm.patientId" :min="1" :controls="false" placeholder="患者ID" style="width: 100%" />
        </w-form-item>
        <w-form-item label="退药药品" required>
          <w-select
            v-model="returnForm.drugId"
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
        <w-form-item label="退药数量" required>
          <w-input-number v-model="returnForm.quantity" :min="1" style="width: 100%" />
        </w-form-item>
        <w-form-item label="退款金额">
          <w-input-number v-model="returnForm.refundAmount" :min="0" :precision="2" style="width: 100%" />
          <div class="form-tip">实际冲账金额由后端按药品参考价 × 数量自动计算</div>
        </w-form-item>
        <w-form-item label="退药原因">
          <w-input v-model="returnForm.reason" type="textarea" :rows="2" maxlength="200" placeholder="请输入退药原因" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="returnDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="returnSubmitting" @click="saveReturn">确认退药</w-button>
      </template>
    </w-dialog>

    <!-- 新增 CDSS 规则弹窗 -->
    <w-dialog v-model="ruleDialogVisible" title="新增 CDSS 规则" width="520px" :close-on-click-modal="false">
      <w-form :model="ruleForm" label-width="100px">
        <w-form-item label="规则类型" required>
          <w-select v-model="ruleForm.ruleType" placeholder="请选择规则类型">
            <w-option v-for="o in RULE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="药品ID" required>
          <w-input-number v-model="ruleForm.drugId" :min="1" :controls="false" placeholder="关联 drug.id" style="width: 100%" />
        </w-form-item>
        <w-form-item label="对照药品ID">
          <w-input-number
            v-model="ruleForm.pairedDrugId"
            :min="1"
            :controls="false"
            :placeholder="ruleForm.ruleType === 'DRUG_DUPLICATE' || ruleForm.ruleType === 'DRUG_CONFLICT' ? '必填' : '可空'"
            style="width: 100%"
          />
        </w-form-item>
        <w-form-item label="单次上限">
          <w-input-number v-model="ruleForm.maxSingleDose" :min="0" :precision="2" :controls="false" placeholder="mg（选填）" style="width: 100%" />
        </w-form-item>
        <w-form-item label="严重级别" required>
          <w-select v-model="ruleForm.severity" placeholder="请选择严重级别">
            <w-option v-for="o in SEVERITY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </w-select>
        </w-form-item>
        <w-form-item label="描述">
          <w-input v-model="ruleForm.description" type="textarea" :rows="3" maxlength="300" placeholder="规则说明（开方拦截时提示文案）" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="ruleDialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="ruleSubmitting" @click="saveRule">保存规则</w-button>
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

.table-toolbar__tip {
  font-size: 12px;
  color: var(--hospital-text-third);
}

.expiry-days-tag {
  margin-left: 4px;
}

/* 30 天内到期批次行高亮 */
.tab-body :deep(.batch-row--expiring) > td {
  background: var(--w3-color-danger-plain, #fef0f0) !important;
}

.expiring-block {
  margin-top: 24px;
  padding: 14px;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
  background: var(--w3-fill-color-lighter, #fafafa);
}
.expiring-block__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.expiring-block__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.expiring-block__tip {
  font-size: 12px;
  color: var(--hospital-text-third);
}

.type-drug-name {
  font-weight: 500;
  color: var(--hospital-text-main);
}

.form-tip {
  font-size: 12px;
  line-height: 1.5;
  color: var(--hospital-text-third);
}

.scrap-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.scrap-body__tip {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--hospital-text-second);
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
