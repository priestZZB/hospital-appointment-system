<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { WMessage, WMessageBox } from 'win-design-next'
import { CircleCheck, Date as DateIcon, Guide, Hospital, Refresh, Send, Talk, Time } from '@win-design-next/icons-vue'
import { cancelAppointmentApi, getMyAppointmentsApi, getUnpaidPrescriptionsApi } from '@/api/clinic'
import { createTreatmentOrderApi, getNotificationsApi, payTreatmentOrderApi, readNotificationApi } from '@/api/payment'
import { getMyInfusionOrdersApi, getUnpaidExamApplicationsApi } from '@/api/medsupply'
import { getProfileApi } from '@/api/patient'
import { triageApi } from '@/api/ai'
import { useUserStore } from '@/stores/user'
import type {
  AppointmentVO,
  ExamApplicationVO,
  InfusionOrder,
  NotificationVO,
  PrescriptionVO,
  TriageResultVO,
} from '@/types'

const router = useRouter()
const userStore = useUserStore()

const roleLabel = computed(() => {
  if (userStore.isAdmin) return '管理员'
  if (userStore.isDoctor) return '医生'
  if (userStore.isPatient) return '患者'
  return '访客'
})

const greetName = computed(() => userStore.realName || '用户')

const todayText = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})

interface QuickEntry {
  key: string
  label: string
  desc: string
  icon: Component
  action: 'anchor' | 'route'
  anchor?: string
  route?: string
}

const quickEntries: QuickEntry[] = [
  { key: 'triage', label: '智能分诊', desc: 'AI 推荐就诊科室', icon: Guide, action: 'anchor', anchor: 'triage' },
  { key: 'book', label: '预约挂号', desc: '按科室查询号源', icon: Hospital, action: 'route', route: '/slots' },
  { key: 'appointments', label: '我的预约', desc: '预约与就诊记录', icon: DateIcon, action: 'anchor', anchor: 'appointments' },
  { key: 'infusions', label: '我的输液', desc: '查询本人输液单', icon: Time, action: 'anchor', anchor: 'infusions' },
  { key: 'payment', label: '待缴费', desc: '诊疗费支付', icon: Send, action: 'anchor', anchor: 'payment' },
  { key: 'notifications', label: '站内信', desc: '通知与消息提醒', icon: Talk, action: 'anchor', anchor: 'notifications' },
]

function onQuick(entry: QuickEntry) {
  if (entry.action === 'route' && entry.route) {
    router.push(entry.route)
    return
  }
  if (entry.anchor) {
    document.getElementById(entry.anchor)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

// 智能分诊
const triageText = ref('')
const triageLoading = ref(false)
const triageResult = ref<TriageResultVO | null>(null)

async function handleTriage() {
  const symptom = triageText.value.trim()
  if (!symptom) {
    WMessage.warning('请先描述您的症状')
    return
  }
  if (symptom.length < 2) {
    WMessage.warning('症状描述至少 2 个字')
    return
  }
  triageLoading.value = true
  triageResult.value = null
  try {
    triageResult.value = await triageApi(symptom)
  } catch (e) {
    WMessage.error((e as Error).message || '智能分诊失败，请稍后重试')
  } finally {
    triageLoading.value = false
  }
}

// 我的预约
const appointments = ref<AppointmentVO[]>([])
const apptLoading = ref(false)

type StatusTone = 'success' | 'warning' | 'info' | 'danger'

function orderStatusMeta(status?: string): { text: string; tone: StatusTone } {
  switch (status) {
    case 'PAID':
      return { text: '已支付', tone: 'success' }
    case 'PENDING_PAY':
      return { text: '待支付', tone: 'warning' }
    case 'CANCELLED':
      return { text: '已取消', tone: 'info' }
    case 'REFUNDED':
      return { text: '已退款', tone: 'info' }
    case 'COMPLETED':
      return { text: '已完成', tone: 'success' }
    default:
      return { text: status || '—', tone: 'info' }
  }
}

function canCancel(appt: AppointmentVO): boolean {
  return appt.orderStatus !== 'CANCELLED' && !appt.visitStatus
}

function apptTime(appt: AppointmentVO): string {
  if (appt.slotStart && appt.slotEnd) return `${appt.slotStart}-${appt.slotEnd}`
  if (appt.slotStart) return appt.slotStart
  return '时间待定'
}

async function loadMyAppointments() {
  apptLoading.value = true
  try {
    appointments.value = await getMyAppointmentsApi()
  } catch (e) {
    WMessage.error((e as Error).message || '预约列表加载失败')
  } finally {
    apptLoading.value = false
  }
}

async function handleCancel(appt: AppointmentVO) {
  try {
    await cancelAppointmentApi(appt.id)
    WMessage.success('预约已取消')
    loadMyAppointments()
  } catch (e) {
    WMessage.error((e as Error).message || '取消预约失败')
  }
}

// 站内信
const notifications = ref<NotificationVO[]>([])
const notifLoading = ref(false)
const unreadCount = computed(() => notifications.value.filter((n) => !n.isRead).length)

async function loadNotifications() {
  notifLoading.value = true
  try {
    notifications.value = await getNotificationsApi()
  } catch (e) {
    WMessage.error((e as Error).message || '站内信加载失败')
  } finally {
    notifLoading.value = false
  }
}

async function markRead(id: number) {
  try {
    await readNotificationApi(id)
    const target = notifications.value.find((n) => n.id === id)
    if (target) target.isRead = 1
  } catch (e) {
    WMessage.error((e as Error).message || '标记已读失败')
  }
}

// 我的输液
const infusions = ref<InfusionOrder[]>([])
const infusionLoading = ref(false)
const payLoading = ref(false)

function infusionStatusMeta(status?: string): { text: string; tone: StatusTone } {
  switch (status) {
    case 'PENDING':
      return { text: '待执行', tone: 'warning' }
    case 'IN_PROGRESS':
    case 'STARTED':
      return { text: '执行中', tone: 'success' }
    case 'COMPLETED':
    case 'FINISHED':
      return { text: '已完成', tone: 'success' }
    case 'CANCELLED':
      return { text: '已取消', tone: 'info' }
    default:
      return { text: status || '—', tone: 'info' }
  }
}

function formatMoney(value?: number): string {
  return value != null ? `¥${Number(value).toFixed(2)}` : '-'
}

function infusionAmount(order: InfusionOrder): number {
  if (order.totalAmount != null && Number(order.totalAmount) > 0) return Number(order.totalAmount)
  return (order.unitPrice ?? 0) * (order.days ?? 1)
}

function payStatusMeta(status?: string): { text: string; tone: StatusTone } {
  switch (status) {
    case 'PAID':
      return { text: '已缴费', tone: 'success' }
    case 'UNPAID':
      return { text: '待缴费', tone: 'info' }
    case 'REFUNDED':
      return { text: '已退款', tone: 'danger' }
    default:
      return { text: status || '—', tone: 'info' }
  }
}

// 待缴费：处方 / 检查（患者本人）
const unpaidRx = ref<PrescriptionVO[]>([])
const unpaidExams = ref<ExamApplicationVO[]>([])
const unpaidLoading = ref(false)
const myPatientId = ref<number>()

async function loadUnpaidAll() {
  unpaidLoading.value = true
  try {
    if (!myPatientId.value) {
      const profile = await getProfileApi()
      myPatientId.value = profile.id
    }
    const [rx, ex] = await Promise.all([
      getUnpaidPrescriptionsApi(myPatientId.value),
      getUnpaidExamApplicationsApi(myPatientId.value),
    ])
    unpaidRx.value = Array.isArray(rx) ? rx : []
    unpaidExams.value = Array.isArray(ex) ? ex : []
  } catch (e) {
    WMessage.error((e as Error).message || '待缴费项目加载失败')
  } finally {
    unpaidLoading.value = false
  }
}

async function payUnpaidItem(
  orderType: 'DRUG' | 'EXAM' | 'INFUSION',
  relatedId: number,
  itemName: string,
  price: number,
) {
  try {
    await WMessageBox.confirm(`确认支付「${itemName}」诊疗费吗？`, '诊疗费缴费', {
      type: 'warning',
      confirmButtonText: '支付',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  payLoading.value = true
  try {
    const created = await createTreatmentOrderApi({
      orderType,
      items: [{ itemName, qty: 1, price }],
      relatedId,
    })
    await payTreatmentOrderApi(created.id)
    WMessage.success('缴费成功')
    await loadUnpaidAll()
    await loadMyInfusions()
  } catch (e) {
    WMessage.error((e as Error).message || '缴费失败')
  } finally {
    payLoading.value = false
  }
}

async function loadMyInfusions() {
  infusionLoading.value = true
  try {
    const page = await getMyInfusionOrdersApi({ pageNo: 1, pageSize: 50 })
    infusions.value = page.records || []
  } catch (e) {
    WMessage.error((e as Error).message || '输液单加载失败')
  } finally {
    infusionLoading.value = false
  }
}

async function handlePayInfusion(order: InfusionOrder) {
  try {
    await WMessageBox.confirm(`确认支付「${order.drugName || '输液'}」诊疗费吗？`, '诊疗费缴费', {
      type: 'warning',
      confirmButtonText: '支付',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  payLoading.value = true
  try {
    const created = await createTreatmentOrderApi({
      orderType: 'INFUSION',
      items: [{ itemName: order.drugName || '输液费', qty: order.days || 1, price: order.unitPrice ?? 0 }],
      relatedId: order.id,
    })
    await payTreatmentOrderApi(created.id)
    WMessage.success('缴费成功')
    loadMyInfusions()
  } catch (e) {
    WMessage.error((e as Error).message || '缴费失败')
  } finally {
    payLoading.value = false
  }
}

onMounted(() => {
  loadMyAppointments()
  loadMyInfusions()
  loadNotifications()
  loadUnpaidAll()
})
</script>

<template>
  <div class="page-container patient-center">
    <!-- 渐变顶 -->
    <div class="hero">
      <div class="hero__main">
        <div class="hero__greet">
          <span class="hero__hi">{{ todayText }} · 您好，{{ greetName }}</span>
          <span class="hero__role">{{ roleLabel }}</span>
        </div>
        <h2 class="hero__title">患者中心</h2>
        <p class="hero__subtitle">智能分诊 · 预约挂号 · 消息通知，一站式就医服务</p>
      </div>
      <div class="hero__stats">
        <div class="hero__stat">
          <span class="hero__stat-value">{{ appointments.length }}</span>
          <span class="hero__stat-label">我的预约</span>
        </div>
        <span class="hero__stat-divider" />
        <div class="hero__stat">
          <span class="hero__stat-value">{{ unreadCount }}</span>
          <span class="hero__stat-label">未读消息</span>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="quick-grid">
      <div v-for="entry in quickEntries" :key="entry.key" class="quick-card hospital-card" @click="onQuick(entry)">
        <w-badge
          :value="unreadCount"
          :max="99"
          :hidden="entry.key !== 'notifications' || unreadCount === 0"
          class="quick-card__badge"
        >
          <span class="quick-card__icon"><component :is="entry.icon" /></span>
        </w-badge>
        <div class="quick-card__body">
          <span class="quick-card__label">{{ entry.label }}</span>
          <span class="quick-card__desc">{{ entry.desc }}</span>
        </div>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="content-grid">
      <div class="content-grid__main">
        <!-- AI 智能分诊 -->
        <w-card id="triage" shadow="never" class="hospital-card section-card">
          <template #header>
            <div class="section-head">
              <span class="section-head__title"><Guide class="section-head__icon" />AI 智能分诊</span>
              <span class="section-head__hint">描述症状，AI 为您推荐科室</span>
            </div>
          </template>
          <div class="triage-box">
            <w-input
              v-model="triageText"
              type="textarea"
              :rows="4"
              :maxlength="500"
              show-word-limit
              placeholder="请描述您的症状，例如：发热、咳嗽三天，伴有咽痛……"
            />
            <div class="triage-actions">
              <w-button type="primary" :icon="Send" :loading="triageLoading" @click="handleTriage">开始分诊</w-button>
              <w-button text @click="triageText = ''">清空</w-button>
            </div>
          </div>
          <div v-if="triageResult" class="triage-result">
            <span class="triage-result__icon"><CircleCheck /></span>
            <div class="triage-result__body">
              <span class="triage-result__label">推荐科室</span>
              <span class="triage-result__dept">{{ triageResult.deptName }}</span>
              <div class="triage-result__meta">
                <span v-if="triageResult.confidence != null">置信度 {{ triageResult.confidence }}</span>
                <span v-if="triageResult.isDegraded">已降级为关键词匹配</span>
              </div>
            </div>
          </div>
        </w-card>

        <!-- 我的预约 -->
        <w-card id="appointments" shadow="never" class="hospital-card section-card">
          <template #header>
            <div class="section-head">
              <span class="section-head__title"><DateIcon class="section-head__icon" />我的预约</span>
              <div class="section-head__right">
                <span class="section-head__hint">共 {{ appointments.length }} 条</span>
                <w-button size="small" text :icon="Refresh" @click="loadMyAppointments">刷新</w-button>
              </div>
            </div>
          </template>
          <div v-loading="apptLoading" class="appt-list">
            <div v-for="appt in appointments" :key="appt.id" class="appt-card">
              <div class="appt-card__time">
                <span class="appt-card__date">{{ appt.appointmentDate || '—' }}</span>
                <span class="appt-card__period">{{ apptTime(appt) }}</span>
              </div>
              <div class="appt-card__info">
                <div class="appt-card__doctor">
                  <span>{{ appt.doctorName || '待定医生' }}</span>
                  <w-tag v-if="appt.doctorTitle" size="small" effect="plain" type="primary">{{ appt.doctorTitle }}</w-tag>
                </div>
                <div class="appt-card__dept">
                  <Time class="appt-card__meta-icon" />
                  <span>{{ appt.departmentName || '—' }}<template v-if="appt.period"> · {{ appt.period }}</template></span>
                </div>
              </div>
              <div class="appt-card__meta">
                <w-tag size="small" effect="light" :type="orderStatusMeta(appt.orderStatus).tone">
                  {{ orderStatusMeta(appt.orderStatus).text }}
                </w-tag>
                <w-tag v-if="appt.visitStatus" size="small" effect="light" type="success">已就诊</w-tag>
              </div>
              <div class="appt-card__actions">
                <w-popconfirm
                  v-if="canCancel(appt)"
                  title="确定取消该预约吗？"
                  confirm-button-text="取消预约"
                  confirm-button-type="danger"
                  @confirm="handleCancel(appt)"
                >
                  <template #reference>
                    <w-button size="small" type="danger" text>取消</w-button>
                  </template>
                </w-popconfirm>
              </div>
            </div>
            <w-empty v-if="!apptLoading && !appointments.length" type="patient" description="暂无预约记录" :image-size="96">
              <w-button type="primary" plain @click="router.push('/slots')">去预约挂号</w-button>
            </w-empty>
          </div>
        </w-card>

        <!-- 我的输液 -->
        <w-card id="infusions" shadow="never" class="hospital-card section-card">
          <template #header>
            <div class="section-head">
              <span class="section-head__title"><Time class="section-head__icon" />我的输液</span>
              <div class="section-head__right">
                <span class="section-head__hint">共 {{ infusions.length }} 条</span>
                <w-button size="small" text :icon="Refresh" @click="loadMyInfusions">刷新</w-button>
              </div>
            </div>
          </template>
          <div v-loading="infusionLoading" class="infusion-list">
            <div v-for="o in infusions" :key="o.id" class="infusion-item">
              <div class="infusion-item__main">
                <span class="infusion-item__drug">{{ o.drugName || '—' }}</span>
                <span class="infusion-item__meta">
                  <template v-if="o.dosage">{{ o.dosage }}</template>
                  <template v-if="o.usageMethod"> · {{ o.usageMethod }}</template>
                  <template v-if="o.frequency"> · {{ o.frequency }}</template>
                </span>
              </div>
              <div class="infusion-item__tags">
                <w-tag size="small" effect="light" :type="infusionStatusMeta(o.status).tone">{{ infusionStatusMeta(o.status).text }}</w-tag>
                <w-tag v-if="o.payStatus" size="small" effect="light" :type="payStatusMeta(o.payStatus).tone">
                  {{ payStatusMeta(o.payStatus).text }}
                </w-tag>
                <w-tag v-if="o.skinTestRequired === 1" size="small" effect="light" type="warning">需皮试</w-tag>
              </div>
              <div class="infusion-item__amount">{{ formatMoney(infusionAmount(o)) }}</div>
            </div>
            <w-empty v-if="!infusionLoading && !infusions.length" type="patient" description="暂无输液记录" :image-size="96" />
          </div>
        </w-card>
      </div>

      <!-- 站内信 -->
      <div class="content-grid__side">
        <w-card id="notifications" shadow="never" class="hospital-card section-card">
          <template #header>
            <div class="section-head">
              <span class="section-head__title"><Talk class="section-head__icon" />站内信</span>
              <span class="section-head__hint">未读 {{ unreadCount }} 条</span>
            </div>
          </template>
          <div v-loading="notifLoading" class="notif-list">
            <div v-for="n in notifications" :key="n.id" class="notif-item" :class="{ 'notif-item--unread': !n.isRead }">
              <div class="notif-item__head">
                <span class="notif-item__title">{{ n.title }}</span>
                <span v-if="!n.isRead" class="notif-item__dot" />
              </div>
              <div class="notif-item__content">{{ n.content }}</div>
              <div class="notif-item__foot">
                <span class="notif-item__time">{{ n.createTime || '—' }}</span>
                <w-button v-if="!n.isRead" size="small" text type="primary" @click="markRead(n.id)">标记已读</w-button>
              </div>
            </div>
            <w-empty v-if="!notifLoading && !notifications.length" type="patient" description="暂无站内信" :image-size="96" />
          </div>
        </w-card>

        <!-- 待缴费 -->
        <w-card id="payment" shadow="never" class="hospital-card section-card">
          <template #header>
            <div class="section-head">
              <span class="section-head__title"><Send class="section-head__icon" />待缴费</span>
              <span class="section-head__hint">诊疗费支付</span>
            </div>
          </template>
          <div v-loading="infusionLoading || unpaidLoading" class="pay-list">
            <!-- 未缴费处方 -->
            <div v-for="p in unpaidRx" :key="`rx-${p.id}`" class="pay-item">
              <div class="pay-item__info">
                <span class="pay-item__name">处方 #{{ p.id }}</span>
                <span class="pay-item__amount">
                  <template v-if="p.totalAmount">合计 {{ formatMoney(p.totalAmount) }}</template>
                  <template v-else>待划价</template>
                </span>
              </div>
              <w-button
                size="small"
                type="primary"
                plain
                :loading="payLoading"
                :disabled="!p.totalAmount"
                @click="payUnpaidItem('DRUG', p.id, `处方费 #${p.id}`, p.totalAmount ?? 0)"
              >缴费</w-button>
            </div>
            <!-- 未缴费检查 -->
            <div v-for="e in unpaidExams" :key="`ex-${e.id}`" class="pay-item">
              <div class="pay-item__info">
                <span class="pay-item__name">{{ e.examItemName || '检查项目' }}</span>
                <span class="pay-item__amount">
                  <template v-if="e.totalAmount">合计 {{ formatMoney(e.totalAmount) }}</template>
                  <template v-else>待划价</template>
                </span>
              </div>
              <w-button
                size="small"
                type="primary"
                plain
                :loading="payLoading"
                :disabled="!e.totalAmount"
                @click="payUnpaidItem('EXAM', e.id, `${e.examItemName || '检查费'} #${e.id}`, e.totalAmount ?? 0)"
              >缴费</w-button>
            </div>
            <!-- 输液 -->
            <div v-for="o in infusions" :key="o.id" class="pay-item">
              <div class="pay-item__info">
                <span class="pay-item__name">{{ o.drugName || '—' }}</span>
                <span class="pay-item__amount">{{ formatMoney(infusionAmount(o)) }}</span>
              </div>
              <w-button size="small" type="primary" plain :loading="payLoading" @click="handlePayInfusion(o)">缴费</w-button>
            </div>
            <w-empty
              v-if="!infusionLoading && !unpaidLoading && !infusions.length && !unpaidRx.length && !unpaidExams.length"
              type="patient"
              description="暂无待缴费项目"
              :image-size="96"
            />
          </div>
        </w-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.patient-center {
  gap: 16px;
}

/* 渐变顶 */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  border-radius: var(--hospital-radius-lg);
  background: linear-gradient(135deg, var(--w3-color-primary) 0%, var(--w3-color-primary-hover) 100%);
  box-shadow: var(--hospital-shadow-hover);
  color: var(--w3-bg-color);
}
.hero__main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hero__greet {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hero__hi {
  font-size: 13px;
  opacity: 0.9;
}
.hero__role {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.2);
}
.hero__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.hero__subtitle {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
}
.hero__stats {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-shrink: 0;
}
.hero__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 64px;
}
.hero__stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.hero__stat-label {
  font-size: 12px;
  opacity: 0.85;
}
.hero__stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(255, 255, 255, 0.3);
}

/* 快捷入口 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.quick-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  cursor: pointer;
}
.quick-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--hospital-radius-md);
  font-size: 20px;
  color: var(--w3-color-primary);
  background: var(--w3-color-primary-plain);
  flex-shrink: 0;
}
.quick-card__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.quick-card__label {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.quick-card__desc {
  font-size: 12px;
  color: var(--hospital-text-third);
}

/* 内容栅格 */
.content-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  align-items: start;
}
.content-grid__main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.content-grid__side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 区块卡片 */
.section-card {
  scroll-margin-top: 16px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-head__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.section-head__icon {
  font-size: 18px;
  color: var(--w3-color-primary);
}
.section-head__hint {
  font-size: 12px;
  color: var(--hospital-text-third);
}
.section-head__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* AI 分诊 */
.triage-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.triage-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.triage-result {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-top: 14px;
  padding: 16px;
  border-radius: var(--hospital-radius-md);
  background: var(--w3-color-primary-plain);
}
.triage-result__icon {
  display: flex;
  font-size: 20px;
  color: var(--w3-color-primary);
}
.triage-result__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.triage-result__label {
  font-size: 12px;
  color: var(--hospital-text-third);
}
.triage-result__dept {
  font-size: 18px;
  font-weight: 700;
  color: var(--w3-color-primary);
}
.triage-result__meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--hospital-text-second);
}

/* 我的预约卡片 */
.appt-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.appt-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
  background: var(--w3-fill-color-blank);
  transition: border-color 0.2s ease-out, box-shadow 0.2s ease-out;
}
.appt-card:hover {
  border-color: var(--w3-color-primary-active-bg);
  box-shadow: var(--hospital-shadow-card);
}
.appt-card__time {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 96px;
  padding: 10px 8px;
  border-radius: var(--hospital-radius-md);
  background: var(--w3-color-primary-plain);
  flex-shrink: 0;
}
.appt-card__date {
  font-size: 13px;
  font-weight: 600;
  color: var(--w3-color-primary);
  text-align: center;
}
.appt-card__period {
  font-size: 12px;
  color: var(--hospital-text-second);
}
.appt-card__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.appt-card__doctor {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.appt-card__dept {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--hospital-text-second);
}
.appt-card__meta-icon {
  font-size: 14px;
}
.appt-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.appt-card__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* 站内信 */
.notif-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.notif-item {
  padding: 12px 14px;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
}
.notif-item--unread {
  background: var(--w3-color-primary-plain);
  border-color: var(--w3-color-primary-active-bg);
}
.notif-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.notif-item__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.notif-item__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--w3-color-danger);
  flex-shrink: 0;
}
.notif-item__content {
  margin: 4px 0;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--hospital-text-second);
}
.notif-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.notif-item__time {
  font-size: 12px;
  color: var(--hospital-text-third);
}

/* 我的输液 */
.infusion-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.infusion-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
}
.infusion-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.infusion-item__drug {
  font-size: 14px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.infusion-item__meta {
  font-size: 12px;
  color: var(--hospital-text-third);
}
.infusion-item__tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.infusion-item__amount {
  font-size: 14px;
  font-weight: 600;
  color: var(--w3-color-primary);
  flex-shrink: 0;
}

/* 待缴费 */
.pay-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.pay-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
}
.pay-item__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.pay-item__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.pay-item__amount {
  font-size: 12px;
  color: var(--hospital-text-second);
}

/* 响应式 */
@media (max-width: 1080px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
  .quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
