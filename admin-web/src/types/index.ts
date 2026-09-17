/** 后端统一响应体 Result<T>（依据：API接口文档.md §0.1） */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
  timestamp?: number
}

/** 分页对象（依据：API接口文档.md §0.3） */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

/** 登录/刷新响应 LoginVO（依据：API接口文档.md §1 #2） */
export interface LoginVO {
  token: string
  userId: number
  phone: string
  realName: string
  roles: string[]
}

/** 我的权限 MyPermissionsVO（迭代5：GET /api/auth/my-permissions） */
export interface MyPermissionsVO {
  userId: number
  superAdmin: boolean
  permissions: string[]
  apiPermissions: string[]
  menuPermissions: string[]
  btnPermissions: string[]
}

/** 岗位 PositionVO（迭代5阶段2：GET /api/auth/positions） */
export interface PositionVO {
  id: number
  positionCode?: string
  positionName?: string
  departmentId?: number
  departmentName?: string
  title?: string
  description?: string
  status?: number
  sortOrder?: number
  userCount?: number
  createTime?: string
}

/** 用户信息 UserVO（依据：API接口文档.md §1 #11 / S1） */
export interface UserVO {
  id: number
  phone: string
  realName: string
  gender?: number
  userType: 'PATIENT' | 'DOCTOR' | 'ADMIN'
  positionId?: number
  positionName?: string
  positionTitle?: string
  status?: number
  lastLoginTime?: string
  createTime?: string
  roles?: string[]
}

/** BI 概览 BiOverviewVO（依据：API接口文档.md §3.7 #53） */
export interface BiOverviewVO {
  todayAppointments: number
  todayConsultations: number
  todayRevenue: number
  weeklyTrend: Record<string, number>[]
  deptDistribution: Record<string, number>[]
}

/** 科室 DepartmentVO（依据：API接口文档.md §3.1） */
export interface DepartmentVO {
  id: number
  deptName: string
  deptCode: string
  description?: string
  location?: string
  phone?: string
  status: number
  sortOrder?: number
  createTime?: string
}

/** 医生 DoctorVO（依据：API接口文档.md §9.2 S4-S8） */
export interface DoctorVO {
  id: number
  userId?: number
  name: string
  gender?: number
  phone?: string
  departmentId: number
  departmentName?: string
  title: string
  specialty?: string
  introduction?: string
  status?: number
  createTime?: string
}

/** 排班 ScheduleVO（依据：API接口文档.md §3.2） */
export interface ScheduleVO {
  id: number
  doctorId: number
  doctorName?: string
  doctorTitle?: string
  departmentId: number
  departmentName?: string
  scheduleDate: string
  period: string
  periodStart: string
  periodEnd: string
  totalSlots: number
  availableSlots?: number
  slotDuration: number
  registerFee?: number
  status?: string
  auditStatus?: string
  createTime?: string
}

/** 号源 SlotVO（依据：API接口文档.md §3.3） */
export interface SlotVO {
  id: number
  scheduleId: number
  doctorId: number
  doctorName?: string
  doctorTitle?: string
  departmentId: number
  departmentName?: string
  scheduleDate: string
  period: string
  slotSeq: number
  slotStart: string
  slotEnd: string
  status: string
  registerFee?: number
}

/** 预约 AppointmentVO（依据：API接口文档.md §3.3） */
export interface AppointmentVO {
  id: number
  appointmentNo?: string
  patientId: number
  slotId?: number
  scheduleId?: number
  doctorId?: number
  doctorName?: string
  doctorTitle?: string
  departmentId?: number
  departmentName?: string
  appointmentDate?: string
  period?: string
  slotSeq?: number
  slotStart?: string
  slotEnd?: string
  registerFee?: number
  orderStatus?: string
  visitStatus?: string
  paymentOrderId?: number
  paymentOrderNo?: string
  createTime?: string
  updateTime?: string
}

/** 排队患者 QueuePatientVO（依据：API接口文档.md §9.2 S10） */
export interface QueuePatientVO {
  checkinId: number
  appointmentId: number
  patientId: number
  patientName?: string
  departmentId?: number
  departmentName?: string
  doctorId?: number
  doctorName?: string
  appointmentDate?: string
  period?: string
  slotSeq?: number
  slotStart?: string
  slotEnd?: string
  queueStatus?: string
  callCount?: number
  consultRoom?: string
  visitStatus?: string
  checkinTime?: string
  callTime?: string
}

/** 排队快照 QueueSnapshotVO（依据：API接口文档.md §9.2 S10） */
export interface QueueSnapshotVO {
  departmentId: number
  departmentName?: string
  currentCall: QueuePatientVO | null
  waitingList: QueuePatientVO[]
}

/** 叫号推送 CallMessageVO（依据：API接口文档.md §7 / §9.2） */
export interface CallMessageVO {
  type: 'CALL_NUMBER' | 'RECALL' | 'MISSED'
  deptId: number
  deptName?: string
  doctorName?: string
  consultRoom?: string
  patientName?: string
  queueNumber?: number
  timestamp?: string
  checkinId?: number
  appointmentId?: number
  patientId?: number
}

/** 病历 MedicalRecordVO（依据：API接口文档.md §3.5） */
export interface MedicalRecordVO {
  id: number
  appointmentId?: number
  patientId: number
  doctorId?: number
  departmentId?: number
  chiefComplaint?: string
  presentIllness?: string
  pastHistory?: string
  temperature?: number
  pulse?: number
  respiration?: number
  bloodPressure?: string
  diagnosisCode?: string
  diagnosisDesc?: string
  treatmentOpinion?: string
  referralDeptId?: number
  referralReason?: string
  status?: string
  isReturnVisit?: number
  prescriptions?: PrescriptionVO[]
  createTime?: string
  updateTime?: string
}

/** 处方 PrescriptionVO（依据：API接口文档.md §3.5） */
export interface PrescriptionVO {
  id: number
  prescriptionNo?: string
  medicalRecordId?: number
  patientId?: number
  doctorId?: number
  status?: string
  reviewComment?: string
  /** 应缴金额（缴费回写时写入，未划价为 0/空） */
  totalAmount?: number
  /** 缴费状态：UNPAID / PAID / REFUNDED */
  payStatus?: string
  items?: PrescriptionItemVO[]
  createTime?: string
}

export interface PrescriptionItemVO {
  id: number
  prescriptionId?: number
  drugId: number
  drugName: string
  specification?: string
  dosage?: string
  usageMethod?: string
  frequency?: string
  days?: number
  quantity?: number
  unit?: string
  remark?: string
}

/** 停诊申请 StopApplicationVO（依据：API接口文档.md §3.6） */
export interface StopApplicationVO {
  id: number
  scheduleId: number
  doctorId?: number
  doctorName?: string
  departmentName?: string
  scheduleDate?: string
  period?: string
  applyReason?: string
  status?: string
  chiefReviewStatus?: string
  chiefReviewedBy?: string
  chiefReviewComment?: string
  chiefReviewTime?: string
  approveComment?: string
  approvedBy?: string
  approveTime?: string
  affectedCount?: number
  refundTotal?: number
  createTime?: string
  updateTime?: string
}

/** 角色 RoleVO（依据：API接口文档.md §1） */
export interface RoleVO {
  id: number
  roleCode: string
  roleName: string
  description?: string
  status?: number
  createTime?: string
}

/** 患者档案 PatientVO（依据：API接口文档.md §2） */
export interface PatientVO {
  id: number
  userId: number
  name?: string
  gender?: number
  birthDate?: string
  idCard?: string
  phone?: string
  emergencyContact?: string
  emergencyPhone?: string
  verifyStatus?: number
  idCardFrontUrl?: string
  idCardBackUrl?: string
  verifyComment?: string
  avatarUrl?: string
  createTime?: string
}

/** 就诊卡 VisitCardVO（依据：API接口文档.md §2） */
export interface VisitCardVO {
  id: number
  cardNo: string
  status?: number
  issueDate?: string
}

/** 过敏史 AllergyVO（依据：API接口文档.md §2） */
export interface AllergyVO {
  id: number
  allergen: string
  reactionType?: string
  severity?: string
  source?: string
  createTime?: string
}

/** 药品 Drug（依据：API接口文档.md §5） */
export interface Drug {
  id?: number
  drugCode: string
  drugName: string
  genericName?: string
  specification?: string
  dosageForm?: string
  manufacturer?: string
  referencePrice?: number
  unit?: string
  description?: string
  status?: number
  createTime?: string
  updateTime?: string
}

/** 库存 DrugInventory（依据：API接口文档.md §5） */
export interface DrugInventory {
  id: number
  drugId: number
  drugName?: string
  currentStock: number
  minThreshold?: number
  version?: number
  lastStockinTime?: string
  lastStockoutTime?: string
  createTime?: string
}

/** 检查项目 ExamItem（依据：API接口文档.md §5） */
export interface ExamItem {
  id?: number
  itemCode: string
  itemName: string
  itemType: string
  referencePrice?: number
  execDept?: string
  precautions?: string
  status?: number
  createTime?: string
}

/** 检查报告 ExamReport（依据：API接口文档.md §5 / S15） */
export interface ExamReport {
  id: number
  applicationId: number
  patientId?: number
  reportDesc?: string
  reportResult?: string
  attachmentUrl?: string
  attachmentName?: string
  operatorId?: number
  status?: string
  completeTime?: string
  createTime?: string
}

/** 发药记录 DrugDispense（依据：API接口文档.md §9.3） */
export interface DrugDispense {
  id: number
  prescriptionId: number
  patientId?: number
  status?: string
  reviewOperatorId?: number
  reviewComment?: string
  reviewTime?: string
  dispenseOperatorId?: number
  dispenseTime?: string
  createTime?: string
}

/** 支付订单 PaymentOrderVO（依据：API接口文档.md §4） */
export interface PaymentOrderVO {
  id: number
  orderNo: string
  appointmentId?: number
  patientId?: number
  amount?: number
  orderType?: string
  status?: string
  payTime?: string
  payMethod?: string
  expireTime?: string
  createTime?: string
}

/** 站内信 NotificationVO（依据：API接口文档.md §4） */
export interface NotificationVO {
  id: number
  patientId?: number
  title: string
  content?: string
  notifyType?: string
  relatedId?: number
  isRead?: number
  readTime?: string
  createTime?: string
}

/** 审计日志 AuditLog（依据：API接口文档.md §9.1 S2） */
export interface AuditLog {
  id: number
  userId?: number
  username?: string
  operation?: string
  httpMethod?: string
  requestUri?: string
  requestIp?: string
  requestParams?: string
  responseResult?: string
  executionTime?: number
  status?: number
  errorMessage?: string
  createTime?: string
}

/** AI 分诊 TriageResultVO（依据：API接口文档.md §6 #71） */
export interface TriageResultVO {
  deptId?: number
  deptName: string
  confidence?: number
  isDegraded?: boolean
  degradedReason?: string
  executionTimeMs?: number
}

/** 输液单 InfusionOrder（依据：门诊业务扩展 · 输液接口） */
export interface InfusionOrder {
  id: number
  medicalRecordId?: number
  patientId?: number
  patientName?: string
  doctorId?: number
  doctorName?: string
  drugId?: number
  drugName?: string
  dosage?: string
  usageMethod?: string
  frequency?: string
  days?: number
  skinTestRequired?: number
  skinTestResult?: string
  unitPrice?: number
  status?: string
  /** 应缴金额（缴费回写时写入） */
  totalAmount?: number
  /** 缴费状态：UNPAID / PAID / REFUNDED */
  payStatus?: string
  createTime?: string
  updateTime?: string
}

/** 输液执行记录 InfusionRecord（依据：门诊业务扩展 · 输液执行接口） */
export interface InfusionRecord {
  id?: number
  orderId?: number
  recordType?: string
  recordContent?: string
  skinTestResult?: string
  dropRate?: string
  operatorName?: string
  createTime?: string
}

/** 检查申请 ExamApplicationVO（依据：门诊业务扩展 · 检查执行接口） */
export interface ExamApplicationVO {
  id: number
  patientId?: number
  patientName?: string
  medicalRecordId?: number
  examItemId?: number
  examItemName?: string
  itemType?: string
  status?: string
  execDept?: string
  applyDoctorName?: string
  /** 应缴金额（缴费回写时写入，未划价为 0/空） */
  totalAmount?: number
  /** 缴费状态：UNPAID / PAID / REFUNDED */
  payStatus?: string
  createTime?: string
}

/** 诊疗费订单项 TreatmentOrderItem（依据：门诊业务扩展 · 诊疗缴费接口） */
export interface TreatmentOrderItem {
  itemName: string
  qty: number
  price: number
}

/** 诊疗费订单 TreatmentOrderVO（依据：门诊业务扩展 · 诊疗缴费接口） */
export interface TreatmentOrderVO {
  id: number
  orderNo?: string
  orderType?: string
  patientId?: number
  amount?: number
  status?: string
  relatedId?: number
  items?: TreatmentOrderItem[]
  createTime?: string
  payTime?: string
}

/** 日结分类明细行（依据：迭代4 settle_record / settle summary 的 SQL 返回，snake_case 列名） */
export interface SettleDetailRow {
  order_type?: string
  pay_method?: string
  order_count?: number
  total_amount?: number
}

/** 日结汇总 SettleSummaryVO（依据：迭代4 GET /api/payment/cashier/settle/today） */
export interface SettleSummaryVO {
  date?: string
  totalAmount?: number
  orderCount?: number
  /** 按 order_type + pay_method 分组的明细列表 */
  detail?: SettleDetailRow[]
  [key: string]: unknown
}

/** 日结单记录 SettleRecordVO（依据：迭代4 settle_record 表） */
export interface SettleRecordVO {
  id?: number
  settleNo?: string
  cashierId?: number
  settleDate?: string
  totalAmount?: number
  orderCount?: number
  detail?: string
  createTime?: string
}
/** 会诊请求 VO（迭代6） */
export interface ConsultationRequestVO {
  id: number
  requestNo?: string
  medicalRecordId?: number
  patientId?: number
  applyDeptId?: number
  applyDeptName?: string
  applyDoctorId?: number
  applyDoctorName?: string
  targetDeptId?: number
  targetDeptName?: string
  targetDoctorId?: number
  targetDoctorName?: string
  reason?: string
  status?: string
  consultOpinion?: string
  consultDoctorId?: number
  consultDoctorName?: string
  consultTime?: string
  createTime?: string
}

/** 转诊单 VO（迭代6） */
export interface ReferralOrderVO {
  id: number
  referralNo?: string
  medicalRecordId?: number
  patientId?: number
  fromDeptId?: number
  fromDeptName?: string
  fromDoctorId?: number
  fromDoctorName?: string
  toDeptId?: number
  toDeptName?: string
  reason?: string
  status?: string
  acceptTime?: string
  completeTime?: string
  createTime?: string
}

/** 随访记录 VO（迭代6） */
export interface FollowUpRecordVO {
  id: number
  planId?: number
  patientId?: number
  doctorId?: number
  doctorName?: string
  content?: string
  nextFollowDate?: string
  createTime?: string
}

/** 随访计划 VO（迭代6） */
export interface FollowUpPlanVO {
  id: number
  patientId?: number
  patientName?: string
  medicalRecordId?: number
  doctorId?: number
  doctorName?: string
  followDate?: string
  followMethod?: string
  template?: string
  status?: string
  createTime?: string
  records?: FollowUpRecordVO[]
}

/** 医疗证明 VO（迭代6） */
export interface MedicalCertificateVO {
  id: number
  certNo?: string
  certType?: string
  patientId?: number
  patientName?: string
  doctorId?: number
  doctorName?: string
  medicalRecordId?: number
  content?: string
  days?: number
  startDate?: string
  status?: string
  createTime?: string
}

/** 危急值 VO（迭代6） */
export interface CriticalValueVO {
  id: number
  reportId?: number
  applicationId?: number
  patientId?: number
  itemName?: string
  resultValue?: string
  referenceRange?: string
  criticalLevel?: string
  status?: string
  reporterId?: number
  reporterName?: string
  confirmDoctorId?: number
  confirmDoctorName?: string
  confirmComment?: string
  confirmTime?: string
  createTime?: string
}

/** 处方点评 VO（迭代6） */
export interface PrescriptionReviewVO {
  id: number
  prescriptionId?: number
  patientId?: number
  pharmacistId?: number
  pharmacistName?: string
  rating?: string
  problemType?: string
  comment?: string
  createTime?: string
}

/** ==================== 住院模块（迭代6） ==================== */

/** 入院登记 VO */
export interface AdmissionVO {
  id: number
  admissionNo?: string
  patientId?: number
  patientName?: string
  departmentId?: number
  attendingDoctorId?: number
  attendingDoctorName?: string
  admissionDiag?: string
  expectedDays?: number
  admissionTime?: string
  status?: string
  currentBedId?: number
  currentRoomNo?: string
  currentBedNo?: string
  depositBalance?: number
  totalFee?: number
  createTime?: string
}

/** 床位 VO */
export interface BedVO {
  id: number
  departmentId?: number
  roomNo?: string
  bedNo?: string
  bedType?: string
  dailyFee?: number
  status?: string
  occupantAdmissionNo?: string
  occupantPatientId?: number
}

/** 住院医嘱 VO */
export interface MedicalOrderVO {
  id: number
  orderNo?: string
  admissionId?: number
  doctorId?: number
  orderType?: string
  category?: string
  content?: string
  frequency?: string
  status?: string
  openTime?: string
  confirmTime?: string
  stopTime?: string
  createTime?: string
}

/** 生命体征 VO */
export interface VitalSignVO {
  id: number
  admissionId?: number
  temperature?: number
  pulse?: number
  respiration?: number
  bloodPressure?: string
  bloodOxygen?: number
  recordTime?: string
}

/** 住院总览 VO（护士站/医生站看板） */
export interface InpatientOverviewVO {
  admissionId: number
  admissionNo?: string
  patientName?: string
  roomNo?: string
  bedNo?: string
  status?: string
  pendingOrderCount?: number
  executingOrderCount?: number
  depositBalance?: number
  totalFee?: number
  latestVital?: VitalSignVO | null
}

/** 预交金流水 VO */
export interface DepositVO {
  id: number
  admissionId?: number
  amount?: number
  payMethod?: string
  balanceAfter?: number
  operatorId?: number
  createTime?: string
}

/** 住院费用 VO */
export interface InpatientFeeVO {
  id: number
  admissionId?: number
  feeType?: string
  itemName?: string
  amount?: number
  billDate?: string
  createTime?: string
}

/** 每日费用清单 VO */
export interface DailyBillVO {
  billDate?: string
  items?: InpatientFeeVO[]
  total?: number
}

/** 出院小结 VO */
export interface DischargeSummaryVO {
  id: number
  admissionId?: number
  admissionNo?: string
  patientId?: number
  admissionDiag?: string
  dischargeDiag?: string
  treatmentProcess?: string
  dischargeCondition?: string
  dischargeAdvice?: string
  doctorId?: number
  settlementAmount?: number
  depositBalance?: number
  totalFee?: number
  dischargeTime?: string
  createTime?: string
}

/** 护理病历 VO */
export interface NursingRecordVO {
  id: number
  admissionId?: number
  recordType?: string
  content?: string
  intakeMl?: number
  outputMl?: number
  nurseId?: number
  recordTime?: string
}

/** 住院会诊 VO */
export interface ConsultVO {
  id: number
  admissionId?: number
  admissionNo?: string
  patientId?: number
  requestDeptId?: number
  requestDoctorId?: number
  targetDeptId?: number
  targetDoctorId?: number
  reason?: string
  opinion?: string
  status?: string
  handleDoctorId?: number
  handleTime?: string
  createTime?: string
}

/** 手术申请 VO */
export interface SurgeryApplyVO {
  id: number
  admissionId?: number
  admissionNo?: string
  patientId?: number
  surgeryName?: string
  anesthesiaType?: string
  applyDoctorId?: number
  scheduledTime?: string
  operatingRoom?: string
  status?: string
  remark?: string
  createTime?: string
}

/** 病案首页 VO */
export interface MedicalRecordHomeVO {
  id: number
  admissionId?: number
  admissionNo?: string
  patientId?: number
  departmentId?: number
  doctorId?: number
  admissionTime?: string
  dischargeTime?: string
  hospitalDays?: number
  dischargeDiag?: string
  mainOperation?: string
  feeBed?: number
  feeDrug?: number
  feeExam?: number
  feeLab?: number
  feeOther?: number
  feeTotal?: number
  settlementAmount?: number
}
