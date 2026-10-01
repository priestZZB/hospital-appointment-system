<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { WMessageBox } from 'win-design-next'
import {
  BarChart,
  Barcode,
  CaretBottom,
  Computer,
  Cut,
  Date as DateIcon,
  File,
  Flow,
  Fold,
  Guide,
  Hospital,
  List,
  Picture,
  Qrcode,
  Scan,
  Send,
  Server,
  Setting,
  Stamp,
  Stop,
  Tool,
  TurnOff,
  Unfold,
  User,
  UserGroup,
  Verify,
  ViewGridCard,
} from '@win-design-next/icons-vue'

interface MenuItem {
  path: string
  label: string
  icon?: unknown
  roles?: string[]
  /** 权限码（迭代5 阶段4）：命中任一即显示；无则恒显示 */
  permissions?: string[]
}

interface MenuGroup {
  title: string
  items: MenuItem[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const collapsed = ref(false)

/** 菜单权限过滤（v-permission 同源逻辑） */
function filterByPerm(items: MenuItem[]): MenuItem[] {
  return items.filter((item) => {
    if (!item.permissions || item.permissions.length === 0) return true
    if (permissionStore.superAdmin) return true
    return item.permissions.some((p) => permissionStore.permissions.includes(p))
  })
}

const activeMenu = computed(() => route.path)

/** 管理员菜单按权限过滤后的组 */
const visibleAdminGroups = computed<MenuGroup[]>(() =>
  adminMenuGroups
    .map((g) => ({ ...g, items: filterByPerm(g.items) }))
    .filter((g) => g.items.length > 0),
)

/** 医生菜单按权限过滤后 */
const visibleDoctorTabs = computed<MenuItem[]>(() => filterByPerm(doctorTabs))

/* ================= 管理员：深蓝侧栏菜单 ================= */
const adminMenuGroups: MenuGroup[] = [
  {
    title: '工作台',
    items: [
      { path: '/dashboard', label: '首页概览', icon: ViewGridCard, permissions: ['menu:admin:dashboard'] },
      { path: '/bigscreen', label: 'BI 统计大屏', icon: BarChart, permissions: ['menu:admin:bi'] },
      { path: '/call-board', label: '签到叫号', icon: Computer },
      { path: '/data-scope-apply', label: '数据范围申请', icon: Guide, permissions: ['api:auth:data-scope:apply'] },
      { path: '/profile', label: '个人中心', icon: Setting },
    ],
  },
  {
    title: '基础数据',
    items: [
      { path: '/departments', label: '科室管理', icon: Server, permissions: ['menu:admin:department'] },
      { path: '/doctors', label: '医生管理', icon: Verify, permissions: ['menu:admin:doctor'] },
      { path: '/schedules', label: '排班管理', icon: DateIcon, permissions: ['menu:admin:schedule'] },
      // 迭代9 A8：排班周视图日历
      { path: '/schedule-calendar', label: '排班日历', icon: DateIcon, permissions: ['menu:admin:schedule'] },
      { path: '/slots', label: '号源查询', icon: Qrcode, permissions: ['menu:admin:slot'] },
      // 迭代9 J3：ICD-10 字典（门诊诊断选择器数据源）
      { path: '/icd-dict', label: 'ICD 字典', icon: File, permissions: ['menu:admin:department'] },
    ],
  },
  {
    title: '业务管理',
    items: [
      { path: '/appointments', label: '预约管理', icon: List, permissions: ['menu:admin:appointment'] },
      { path: '/clinic', label: '门诊诊疗', icon: Flow },
      { path: '/drugs', label: '药品管理', icon: Tool, permissions: ['menu:admin:drug'] },
      { path: '/exam', label: '检查检验', icon: Scan, permissions: ['menu:admin:exam'] },
      { path: '/med-tech-center', label: '医技工作台', icon: Picture },
      { path: '/exam-tech', label: '检查技师工作台', icon: Scan, permissions: ['menu:exam-tech:workbench'] },
      { path: '/lab-tech', label: '检验技师工作台', icon: Scan, permissions: ['menu:lab-tech:workbench'] },
      { path: '/infusion-nurse', label: '护士站输液', icon: Tool, permissions: ['menu:nurse:workbench'] },
      { path: '/cashier', label: '收费工作台', icon: Send, permissions: ['menu:cashier:workbench'] },
      { path: '/triage', label: '分诊台', icon: Computer, permissions: ['menu:triage:workbench'] },
      // 迭代9 A1/A6：分诊台工作台（优先级/回诊）
      { path: '/triage-desk', label: '分诊台工作台', icon: UserGroup, permissions: ['menu:triage:workbench'] },
      { path: '/stop', label: '停诊审批', icon: Stop, permissions: ['menu:admin:stop'] },
      { path: '/consult-request', label: '会诊管理', icon: Flow, permissions: ['menu:doctor:consult-request'] },
      { path: '/referral', label: '转诊管理', icon: Flow, permissions: ['menu:doctor:referral'] },
      { path: '/follow-up', label: '随访管理', icon: DateIcon, permissions: ['menu:doctor:follow-up'] },
      { path: '/certificate', label: '医疗证明', icon: File, permissions: ['menu:doctor:certificate'] },
      { path: '/critical', label: '危急值', icon: Send, permissions: ['api:medsupply:critical:query'] },
      { path: '/prescription-review', label: '处方点评', icon: Tool, permissions: ['api:medsupply:prescription-review:query'] },
      { path: '/inpatient-doctor', label: '住院医生站', icon: Hospital, permissions: ['menu:inpatient:doctor'] },
      { path: '/inpatient-nurse', label: '住院护士站', icon: Tool, permissions: ['menu:inpatient:nurse'] },
      // 迭代10：手术/麻醉中心（挂住院组旁，管理员/医生/护士可见）
      { path: '/surgery-center', label: '手术中心', icon: Cut },
    ],
  },
  {
    // 迭代11：财务收费（医保与财务，管理员 + 收费员可见）
    title: '财务收费',
    items: [
      { path: '/insurance-settle', label: '医保结算', icon: Barcode },
      { path: '/charge-items', label: '收费项目字典', icon: List },
      { path: '/insurance-catalog', label: '医保目录管理', icon: Stamp },
    ],
  },
  {
    // 迭代12：统计分析（病案与统计，管理员 + 医生可见）
    title: '统计分析',
    items: [
      { path: '/report-center', label: '统计报表中心', icon: BarChart },
      { path: '/report-form', label: '上报登记', icon: Send },
      { path: '/medical-record', label: '病案与临床路径', icon: File },
    ],
  },
  {
    title: '系统管理',
    items: [
      { path: '/users', label: '用户管理', icon: UserGroup, permissions: ['menu:admin:user'] },
      { path: '/audit-logs', label: '审计日志', icon: File, permissions: ['menu:admin:audit'] },
    ],
  },
]

/* ================= 医生：顶部 tab 导航 ================= */
const doctorTabs: MenuItem[] = [
  { path: '/workbench', label: '工作台', permissions: ['menu:doctor:workbench'] },
  { path: '/clinic', label: '门诊诊疗', permissions: ['menu:doctor:workbench'] },
  { path: '/call-board', label: '签到叫号' },
  { path: '/schedules', label: '我的排班', permissions: ['menu:admin:schedule'] },
  { path: '/slots', label: '号源查询', permissions: ['menu:admin:slot'] },
  { path: '/stop', label: '停诊申请', permissions: ['menu:admin:stop'] },
  { path: '/consult-request', label: '会诊', icon: Flow, permissions: ['menu:doctor:consult-request'] },
  { path: '/referral', label: '转诊', icon: Flow, permissions: ['menu:doctor:referral'] },
  { path: '/follow-up', label: '随访', icon: DateIcon, permissions: ['menu:doctor:follow-up'] },
  { path: '/certificate', label: '证明', icon: File, permissions: ['menu:doctor:certificate'] },
  { path: '/inpatient-doctor', label: '住院医生站', permissions: ['menu:inpatient:doctor'] },
  // 迭代10：手术/麻醉中心
  { path: '/surgery-center', label: '手术中心' },
]

/* ================= 患者：C 端顶部 ================= */
const patientTabs: MenuItem[] = [
  { path: '/patient', label: '患者中心' },
  { path: '/profile', label: '个人中心' },
]

/* ================= 医技/护理：顶部 tab 导航 ================= */
const staffTabs = computed<MenuItem[]>(() => {
  const tabs: MenuItem[] = []
  if (userStore.isCashier) tabs.push({ path: '/cashier', label: '收费工作台', permissions: ['menu:cashier:workbench'] })
  // 迭代11：医保结算（收银员可见；管理员走侧栏「财务收费」组）
  if (userStore.isCashier) tabs.push({ path: '/insurance-settle', label: '医保结算' })
  if (userStore.isTriageNurse) tabs.push({ path: '/triage', label: '分诊台', permissions: ['menu:triage:workbench'] })
  // 迭代9：分诊台工作台（优先级调整 / 回诊标记）
  if (userStore.isTriageNurse) tabs.push({ path: '/triage-desk', label: '分诊工作台', permissions: ['menu:triage:workbench'] })
  if (userStore.isLabTech) tabs.push({ path: '/lab-tech', label: '检验工作台', permissions: ['menu:lab-tech:workbench'] })
  if (userStore.isNurse) tabs.push({ path: '/infusion-nurse', label: '护士站输液', permissions: ['menu:nurse:workbench'] })
  if (userStore.isNurse) tabs.push({ path: '/inpatient-nurse', label: '住院护士站', permissions: ['menu:inpatient:nurse'] })
  // 迭代10：手术/麻醉中心（护士可见）
  if (userStore.isNurse) tabs.push({ path: '/surgery-center', label: '手术中心' })
  if (userStore.isExamTech) tabs.push({ path: '/exam-tech', label: '检查执行', permissions: ['menu:exam-tech:workbench'] })
  // 迭代8：检验 LIS + 影像中心（页面内按角色门控，无权限显示空态）
  if (userStore.isLabTech || userStore.isExamTech) tabs.push({ path: '/med-tech-center', label: '医技中心' })
  if (userStore.isPharmacist) tabs.push({ path: '/drugs', label: '药房', permissions: ['menu:admin:drug'] })
  tabs.push({ path: '/profile', label: '个人中心' })
  return filterByPerm(tabs)
})

function greeting(): string {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 18) return '下午好'
  return '晚上好'
}

function roleText(): string {
  if (userStore.isSuperAdmin) return '超级管理员'
  if (userStore.isAdmin) return '管理员'
  if (userStore.isDeptChief) return '科主任'
  if (userStore.isPharmacist) return '药师'
  if (userStore.isCashier) return '收费员'
  if (userStore.isTriageNurse) return '分诊护士'
  if (userStore.isLabTech) return '检验技师'
  if (userStore.isExamTech) return '检查技师'
  if (userStore.isNurse) return '护士'
  if (userStore.isDoctor) return '医生'
  return '患者'
}

function todayText(): string {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${String(d.getMonth() + 1).padStart(2, '0')}月${String(d.getDate()).padStart(2, '0')}日 周${week}`
}

async function handleCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    await WMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await userStore.logout()
    router.replace('/login')
  }
}
</script>

<template>
  <!-- ============ 管理员壳：深蓝侧栏 ============ -->
  <w-container v-if="userStore.isAdmin" direction="vertical" class="app-shell">
    <w-header height="56px" class="topbar">
      <div class="topbar-left">
        <w-button class="collapse-btn" text :icon="collapsed ? Unfold : Fold" @click="collapsed = !collapsed" />
        <div class="brand">
          <span class="brand-mark"><Hospital /></span>
          <div class="brand-text">
            <span class="brand-name">医院门诊预约挂号系统</span>
            <span class="brand-sub">Hospital Appointment System</span>
          </div>
        </div>
      </div>
      <div class="topbar-right">
        <span class="hello">{{ greeting() }}，{{ userStore.realName }}</span>
        <w-tag type="primary" size="small" effect="light" class="role-tag">管理员</w-tag>
        <w-dropdown trigger="click" @command="handleCommand">
          <span class="user-trigger">
            <w-avatar size="small" class="user-avatar">{{ userStore.realName.charAt(0) }}</w-avatar>
            <span class="user-name">{{ userStore.realName }}</span>
            <CaretBottom class="caret" />
          </span>
          <template #dropdown>
            <w-dropdown-menu>
              <w-dropdown-item command="profile"><Setting class="dd-icon" /> 个人中心</w-dropdown-item>
              <w-dropdown-item command="logout" divided><TurnOff class="dd-icon" /> 退出登录</w-dropdown-item>
            </w-dropdown-menu>
          </template>
        </w-dropdown>
      </div>
    </w-header>

    <w-container class="body">
      <w-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
        <w-menu
          :default-active="activeMenu"
          mode="vertical"
          :collapse="collapsed"
          class="side-menu"
          :default-openeds="visibleAdminGroups.map((g) => g.title)"
        >
          <template v-for="group in visibleAdminGroups" :key="group.title">
            <w-sub-menu :index="group.title">
              <template #title><span class="group-title">{{ group.title }}</span></template>
              <w-menu-item v-for="item in group.items" :key="item.path" :index="item.path" @click="router.push(item.path)">
                <component :is="item.icon" class="menu-icon" />
                <span>{{ item.label }}</span>
              </w-menu-item>
            </w-sub-menu>
          </template>
        </w-menu>
      </w-aside>
      <w-main class="main"><router-view /></w-main>
    </w-container>
  </w-container>

  <!-- ============ 医生壳：顶部 tab 导航 ============ -->
  <w-container v-else-if="userStore.isDoctor" direction="vertical" class="app-shell">
    <w-header height="64px" class="doctor-topbar">
      <div class="doctor-topbar-left">
        <div class="brand">
          <span class="brand-mark"><Hospital /></span>
          <div class="brand-text">
            <span class="brand-name">医生工作站</span>
            <span class="brand-sub">{{ userStore.realName }} · 内科 · 主任医师</span>
          </div>
        </div>
        <div class="doctor-tabs">
          <router-link
            v-for="tab in visibleDoctorTabs"
            :key="tab.path"
            :to="tab.path"
            class="doctor-tab"
            :class="{ active: route.path === tab.path }"
          >{{ tab.label }}</router-link>
        </div>
      </div>
      <div class="doctor-topbar-right">
        <span class="doctor-date">{{ todayText() }}</span>
        <w-dropdown trigger="click" @command="handleCommand">
          <w-avatar size="small" class="user-avatar doctor-avatar">{{ userStore.realName.charAt(0) }}</w-avatar>
          <template #dropdown>
            <w-dropdown-menu>
              <w-dropdown-item command="profile"><Setting class="dd-icon" /> 个人中心</w-dropdown-item>
              <w-dropdown-item command="logout" divided><TurnOff class="dd-icon" /> 退出登录</w-dropdown-item>
            </w-dropdown-menu>
          </template>
        </w-dropdown>
      </div>
    </w-header>
    <w-main class="main"><router-view /></w-main>
  </w-container>

  <!-- ============ 医技/护理/收费壳：顶部 tab 导航 ============ -->
  <w-container
    v-else-if="
      userStore.isNurse ||
      userStore.isExamTech ||
      userStore.isCashier ||
      userStore.isTriageNurse ||
      userStore.isLabTech ||
      userStore.isPharmacist
    "
    direction="vertical"
    class="app-shell"
  >
    <w-header height="64px" class="doctor-topbar">
      <div class="doctor-topbar-left">
        <div class="brand">
          <span class="brand-mark"><Hospital /></span>
          <div class="brand-text">
            <span class="brand-name">{{ roleText() }}工作站</span>
            <span class="brand-sub">{{ userStore.realName }}</span>
          </div>
        </div>
        <div class="doctor-tabs">
          <router-link
            v-for="tab in staffTabs"
            :key="tab.path"
            :to="tab.path"
            class="doctor-tab"
            :class="{ active: route.path === tab.path }"
          >{{ tab.label }}</router-link>
        </div>
      </div>
      <div class="doctor-topbar-right">
        <span class="doctor-date">{{ todayText() }}</span>
        <w-dropdown trigger="click" @command="handleCommand">
          <w-avatar size="small" class="user-avatar doctor-avatar">{{ userStore.realName.charAt(0) }}</w-avatar>
          <template #dropdown>
            <w-dropdown-menu>
              <w-dropdown-item command="profile"><Setting class="dd-icon" /> 个人中心</w-dropdown-item>
              <w-dropdown-item command="logout" divided><TurnOff class="dd-icon" /> 退出登录</w-dropdown-item>
            </w-dropdown-menu>
          </template>
        </w-dropdown>
      </div>
    </w-header>
    <w-main class="main"><router-view /></w-main>
  </w-container>

  <!-- ============ 患者壳：C 端渐变顶部 ============ -->
  <w-container v-else direction="vertical" class="app-shell">
    <div class="patient-top">
      <div class="patient-top-inner">
        <div class="patient-user">
          <w-avatar size="large" class="patient-avatar">{{ userStore.realName.charAt(0) }}</w-avatar>
          <div class="patient-hello">
            <span class="patient-name">你好，{{ userStore.realName }}</span>
            <span class="patient-sub">已实名认证 · 健康档案完整度 85%</span>
          </div>
        </div>
        <div class="patient-actions">
          <router-link to="/patient" class="patient-nav" :class="{ active: route.path === '/patient' }">患者中心</router-link>
          <router-link to="/profile" class="patient-nav" :class="{ active: route.path === '/profile' }">个人中心</router-link>
          <w-dropdown trigger="click" @command="handleCommand">
            <div class="patient-gear"><Setting /></div>
            <template #dropdown>
              <w-dropdown-menu>
                <w-dropdown-item command="logout"><TurnOff class="dd-icon" /> 退出登录</w-dropdown-item>
              </w-dropdown-menu>
            </template>
          </w-dropdown>
        </div>
      </div>
    </div>
    <w-main class="patient-main"><router-view /></w-main>
  </w-container>
</template>

<style scoped>
.app-shell {
  height: 100vh;
}

/* ===== 通用品牌 ===== */
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--w3-color-primary, #2d5afa) 0%, var(--w3-color-primary-press, #1d39c4) 100%);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 4px 12px rgba(45, 90, 250, 0.35);
  flex-shrink: 0;
}
.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--hospital-text-main);
  letter-spacing: 0.3px;
}
.brand-sub {
  font-size: 10.5px;
  color: var(--hospital-text-third);
  letter-spacing: 0.5px;
}
.user-avatar {
  background: linear-gradient(135deg, var(--w3-color-primary, #2d5afa), var(--w3-color-primary-press, #1d39c4));
  color: #fff;
  font-weight: 600;
}
.dd-icon {
  margin-right: 6px;
  vertical-align: -2px;
}
.main {
  padding: 20px;
  background: var(--hospital-bg-page);
  overflow-y: auto;
}

/* ===== 管理员顶栏 ===== */
.topbar {
  background: #fff;
  border-bottom: 1px solid var(--hospital-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 8px;
  box-shadow: 0 1px 4px rgba(16, 24, 40, 0.04);
  z-index: 10;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.collapse-btn {
  color: var(--hospital-text-second);
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.hello {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.role-tag {
  letter-spacing: 1px;
}
.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.caret {
  font-size: 12px;
  color: var(--hospital-text-third);
}

/* ===== 管理员侧栏 ===== */
.body {
  height: calc(100vh - 56px);
}
.sidebar {
  background: linear-gradient(180deg, #0d1730 0%, #101b33 100%);
  overflow: hidden;
  transition: width 0.25s ease;
  display: flex;
  flex-direction: column;
}
.side-menu {
  border-right: none;
  background: transparent;
  padding: 8px 6px;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.group-title {
  color: rgba(255, 255, 255, 0.82);
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 1px;
}
.menu-icon {
  margin-right: 6px;
  vertical-align: -2px;
  font-size: 15px;
}
.side-menu :deep(.w3-sub-menu__title),
.side-menu :deep(.w3-menu-item) {
  color: rgba(255, 255, 255, 0.72);
  border-radius: var(--hospital-radius-sm);
  margin-bottom: 2px;
  height: 42px;
  line-height: 42px;
}
.side-menu :deep(.w3-sub-menu__title:hover),
.side-menu :deep(.w3-menu-item:hover) {
  color: #fff;
  background: rgba(255, 255, 255, 0.07);
}
.side-menu :deep(.w3-menu-item.is-active) {
  color: #fff;
  background: linear-gradient(90deg, var(--w3-color-primary, #2d5afa), var(--w3-color-primary-hover, #5175f4));
  box-shadow: 0 4px 12px rgba(45, 90, 250, 0.4);
}
.side-menu :deep(.w3-sub-menu .w3-menu) {
  background: transparent;
}
.side-menu :deep(.w3-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.4);
}
.side-menu :deep(.w3-menu--collapse .w3-sub-menu__title span) {
  display: none;
}

/* ===== 医生顶栏 ===== */
.doctor-topbar {
  background: #fff;
  border-bottom: 1px solid var(--hospital-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.doctor-topbar-left {
  display: flex;
  align-items: center;
  gap: 28px;
}
.doctor-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
}
.doctor-tab {
  padding: 6px 16px;
  border-radius: 8px;
  color: var(--hospital-text-second);
  font-size: 14px;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.2s;
}
.doctor-tab:hover {
  background: var(--hospital-bg-page);
  color: var(--hospital-text-main);
}
.doctor-tab.active {
  background: var(--w3-color-primary-plain, #eaeefe);
  color: var(--w3-color-primary, #2d5afa);
  font-weight: 600;
}
.doctor-topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.doctor-date {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.doctor-avatar {
  cursor: pointer;
}

/* ===== 患者壳 ===== */
.patient-top {
  background: linear-gradient(90deg, #2d5afa 0%, #5b8cff 55%, #8fb6ff 100%);
  padding: 20px 32px;
}
.patient-top-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
}
.patient-user {
  display: flex;
  align-items: center;
  gap: 14px;
}
.patient-avatar {
  background: rgba(255, 255, 255, 0.22);
  border: 2px solid rgba(255, 255, 255, 0.5);
  color: #fff;
  font-size: 20px;
}
.patient-hello {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.patient-name {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}
.patient-sub {
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
}
.patient-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}
.patient-nav {
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  text-decoration: none;
  padding: 6px 14px;
  border-radius: 8px;
  cursor: pointer;
}
.patient-nav:hover {
  color: #fff;
}
.patient-nav.active {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-weight: 600;
}
.patient-gear {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.4);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 16px;
}
.patient-main {
  background: #f4f7fe;
  padding: 0;
  overflow-y: auto;
}
</style>
