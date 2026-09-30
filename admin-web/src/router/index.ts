import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken, getUser } from '@/utils/auth'
import { homeForRoles } from '@/utils/permission'

function defaultHome(): string {
  const user = getUser()
  return homeForRoles((user?.roles as string[]) || [])
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/403/index.vue'),
    meta: { title: '无权限', public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: () => defaultHome(),
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/admin/dashboard/index.vue'),
        meta: { title: '首页概览', permissions: ['menu:admin:dashboard'] },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心' },
      },
      {
        path: 'workbench',
        name: 'Workbench',
        component: () => import('@/views/doctor/workbench/index.vue'),
        meta: { title: '医生工作台', permissions: ['menu:doctor:workbench'] },
      },
      {
        path: 'patient',
        name: 'Patient',
        component: () => import('@/views/patient/index.vue'),
        meta: { title: '患者中心', permissions: ['menu:patient:center'] },
      },
      {
        path: 'call-board',
        name: 'CallBoard',
        component: () => import('@/views/admin/call-board/index.vue'),
        meta: { title: '签到叫号' },
      },
      {
        path: 'clinic',
        name: 'Clinic',
        component: () => import('@/views/doctor/clinic/index.vue'),
        meta: { title: '门诊诊疗', permissions: ['menu:doctor:workbench'] },
      },
      {
        path: 'departments',
        name: 'Departments',
        component: () => import('@/views/admin/departments/index.vue'),
        meta: { title: '科室管理', permissions: ['menu:admin:department'] },
      },
      {
        path: 'doctors',
        name: 'Doctors',
        component: () => import('@/views/admin/doctors/index.vue'),
        meta: { title: '医生管理', permissions: ['menu:admin:doctor'] },
      },
      {
        path: 'schedules',
        name: 'Schedules',
        component: () => import('@/views/admin/schedules/index.vue'),
        meta: { title: '排班管理', permissions: ['menu:admin:schedule'] },
      },
      {
        // 迭代9 A8：排班周视图日历（医生 × 日期），复用排班管理权限码
        path: 'schedule-calendar',
        name: 'ScheduleCalendar',
        component: () => import('@/views/admin/schedule-calendar/index.vue'),
        meta: { title: '排班日历', permissions: ['menu:admin:schedule'] },
      },
      {
        path: 'slots',
        name: 'Slots',
        component: () => import('@/views/admin/slots/index.vue'),
        meta: { title: '号源查询', permissions: ['menu:admin:slot'] },
      },
      {
        // 迭代9 J3：ICD-10 字典管理（门诊诊断选择器数据源），复用基础数据权限码
        path: 'icd-dict',
        name: 'IcdDict',
        component: () => import('@/views/admin/icd/index.vue'),
        meta: { title: 'ICD 字典管理', permissions: ['menu:admin:department'] },
      },
      {
        path: 'appointments',
        name: 'Appointments',
        component: () => import('@/views/admin/appointments/index.vue'),
        meta: { title: '预约管理', permissions: ['menu:admin:appointment'] },
      },
      {
        path: 'drugs',
        name: 'Drugs',
        component: () => import('@/views/admin/drugs/index.vue'),
        meta: { title: '药品管理', permissions: ['menu:admin:drug'] },
      },
      {
        path: 'exam',
        name: 'Exam',
        component: () => import('@/views/admin/exam/index.vue'),
        meta: { title: '检查检验', permissions: ['menu:admin:exam'] },
      },
      {
        // 迭代8 医技工作台（检验 LIS + 影像中心）。/exam-tech 已被既有「检查技师工作台」占用，
        // 故以 /med-tech-center 平级挂载；页面内按 isLabTech/isExamTech/isAdmin 角色门控（无权限显示空态）
        path: 'med-tech-center',
        name: 'MedTechCenter',
        component: () => import('@/views/admin/exam-tech/index.vue'),
        meta: { title: '医技工作台' },
      },
      {
        // PACS 影像浏览（query: seriesId 或 code=云影像访问码）
        path: 'pacs-viewer',
        name: 'PacsViewer',
        component: () => import('@/views/admin/exam-tech/viewer.vue'),
        meta: { title: 'PACS 影像浏览' },
      },
      {
        path: 'stop',
        name: 'Stop',
        component: () => import('@/views/admin/stop/index.vue'),
        meta: { title: '停诊审批', permissions: ['menu:admin:stop'] },
      },
      {
        path: 'infusion-nurse',
        name: 'InfusionNurse',
        component: () => import('@/views/infusion-nurse/index.vue'),
        meta: { title: '护士站输液', permissions: ['menu:nurse:workbench'] },
      },
      {
        path: 'exam-tech',
        name: 'ExamTech',
        component: () => import('@/views/exam-tech/index.vue'),
        meta: { title: '检查技师工作台', permissions: ['menu:exam-tech:workbench'] },
      },
      {
        path: 'cashier',
        name: 'Cashier',
        component: () => import('@/views/cashier/index.vue'),
        meta: { title: '收费工作台', permissions: ['menu:cashier:workbench'] },
      },
      {
        path: 'triage',
        name: 'Triage',
        component: () => import('@/views/triage/index.vue'),
        meta: { title: '分诊台', permissions: ['menu:triage:workbench'] },
      },
      {
        // 迭代9 A1/A6：分诊台工作台（优先级调整 + 回诊标记），复用分诊台权限码
        path: 'triage-desk',
        name: 'TriageDesk',
        component: () => import('@/views/clinic/triage/index.vue'),
        meta: { title: '分诊台工作台', permissions: ['menu:triage:workbench'] },
      },
      {
        path: 'lab-tech',
        name: 'LabTech',
        component: () => import('@/views/lab-tech/index.vue'),
        meta: { title: '检验技师工作台', permissions: ['menu:lab-tech:workbench'] },
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/admin/users/index.vue'),
        meta: { title: '用户管理', permissions: ['menu:admin:user'] },
      },
      {
        path: 'audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/admin/audit-logs/index.vue'),
        meta: { title: '审计日志', permissions: ['menu:admin:audit'] },
      },
      {
        path: 'bigscreen',
        name: 'BigScreen',
        component: () => import('@/views/admin/bigscreen/index.vue'),
        meta: { title: 'BI 统计大屏', permissions: ['menu:admin:bi'] },
      },
      {
        path: 'consult-request',
        name: 'ConsultRequest',
        component: () => import('@/views/doctor/consult-request/index.vue'),
        meta: { title: '会诊管理', permissions: ['menu:doctor:consult-request'] },
      },
      {
        path: 'referral',
        name: 'Referral',
        component: () => import('@/views/doctor/referral/index.vue'),
        meta: { title: '转诊管理', permissions: ['menu:doctor:referral'] },
      },
      {
        path: 'follow-up',
        name: 'FollowUp',
        component: () => import('@/views/doctor/follow-up/index.vue'),
        meta: { title: '随访管理', permissions: ['menu:doctor:follow-up'] },
      },
      {
        path: 'certificate',
        name: 'Certificate',
        component: () => import('@/views/doctor/certificate/index.vue'),
        meta: { title: '医疗证明', permissions: ['menu:doctor:certificate'] },
      },
      {
        path: 'critical',
        name: 'Critical',
        component: () => import('@/views/admin/critical/index.vue'),
        meta: { title: '危急值管理', permissions: ['api:medsupply:critical:query'] },
      },
      {
        path: 'prescription-review',
        name: 'PrescriptionReview',
        component: () => import('@/views/admin/prescription-review/index.vue'),
        meta: { title: '处方点评', permissions: ['api:medsupply:prescription-review:query'] },
      },
      {
        path: 'data-scope-apply',
        name: 'DataScopeApply',
        component: () => import('@/views/admin/data-scope-apply/index.vue'),
        meta: { title: '数据范围申请', permissions: ['api:auth:data-scope:apply'] },
      },
      {
        path: 'inpatient-doctor',
        name: 'InpatientDoctor',
        component: () => import('@/views/inpatient/doctor/index.vue'),
        meta: { title: '住院医生站', permissions: ['menu:inpatient:doctor'] },
      },
      {
        path: 'inpatient-nurse',
        name: 'InpatientNurse',
        component: () => import('@/views/inpatient/nurse/index.vue'),
        meta: { title: '住院护士站', permissions: ['menu:inpatient:nurse'] },
      },
      {
        // 迭代10：手术/麻醉中心（门诊建单 + 排台看板 + 全流程状态机操作）。
        // 与 /inpatient-doctor、/inpatient-nurse 同级挂在住院组旁；管理员/医生/护士可见
        path: 'surgery-center',
        name: 'SurgeryCenter',
        component: () => import('@/views/inpatient/surgery-center/index.vue'),
        meta: { title: '手术中心' },
      },
      {
        // 迭代11：医保结算（H2/H3 模拟结算 + 结算单列表/详情/票据/冲正）。
        // 跨角色页面（管理员 + 收费员），菜单层控制可见性，与 surgery-center 同款不加 permissions
        path: 'insurance-settle',
        name: 'InsuranceSettle',
        component: () => import('@/views/payment/insurance-settle/index.vue'),
        meta: { title: '医保结算' },
      },
      {
        // 迭代11：收费项目字典（H4 CRUD + 调价 + 启停）
        path: 'charge-items',
        name: 'ChargeItems',
        component: () => import('@/views/payment/charge-items/index.vue'),
        meta: { title: '收费项目字典' },
      },
      {
        // 迭代11：医保目录管理（H1 目录映射 + 甲乙类/比例维护）
        path: 'insurance-catalog',
        name: 'InsuranceCatalog',
        component: () => import('@/views/payment/insurance-catalog/index.vue'),
        meta: { title: '医保目录管理' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

interface RouteMetaPermission {
  permissions?: string[]
}

router.beforeEach(async (to) => {
  document.title = to.meta.title ? `${to.meta.title as string} · 医院门诊预约挂号系统` : '医院门诊预约挂号系统'
  if (to.meta.public) {
    if (getToken() && to.path === '/login') {
      return { path: '/dashboard' }
    }
    return true
  }
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 权限守卫（迭代5 阶段4）：路由声明 meta.permissions 时按权限码放行
  const required = (to.meta as RouteMetaPermission).permissions
  if (required && required.length > 0) {
    const { usePermissionStore } = await import('@/stores/permission')
    const permStore = usePermissionStore()
    if (!permStore.loaded) {
      try {
        await permStore.load()
      } catch {
        // 权限拉取失败不阻断访问（fail-open），避免循环跳转
      }
    }
    if (!permStore.canAccess(required)) {
      return { path: '/403' }
    }
  }
  return true
})

export default router
