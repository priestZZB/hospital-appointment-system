<template>
  <view class="page">
    <view v-if="!orders.length" class="card empty">暂无预约记录</view>
    <view v-for="o in orders" :key="o.id" class="card order">
      <view class="order-row">
        <text class="order-no">预约号 {{ o.appointmentNo || o.id }}</text>
        <text class="order-status" :class="statusClass(o.orderStatus)">{{ statusText(o.orderStatus) }}</text>
      </view>
      <view class="order-row">
        <text class="order-info">就诊日期：{{ (o.appointmentDate || '').slice(0, 10) }}（{{ o.period === 'AM' ? '上午' : '下午' }}）</text>
      </view>
      <view class="order-row">
        <text class="order-info">号序：{{ o.slotSeq }} · 挂号费 ¥{{ o.registerFee }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
/** 我的预约页（迭代17 K5） */
import { ref } from 'vue'
import { myAppointments } from '../../api/request'

const orders = ref([])

const statusText = (s) =>
  ({ PENDING_PAY: '待支付', PAID: '已支付', CANCELLED: '已取消', REFUNDED: '已退费', USED: '已就诊' }[s] || s || '-')

const statusClass = (s) =>
  ({ PENDING_PAY: 'warn', PAID: 'ok', CANCELLED: 'mute', REFUNDED: 'mute', USED: 'ok' }[s] || 'mute')

const load = async () => {
  try {
    const page = await myAppointments(uni.getStorageSync('hospital_patient_id') || 1)
    orders.value = Array.isArray(page?.records) ? page.records : []
  } catch (e) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  }
}

load()
</script>

<style scoped>
.empty {
  text-align: center;
  color: #98a3b3;
}
.order {
  padding: 12px 14px;
}
.order-row {
  display: flex;
  justify-content: space-between;
  padding: 3px 0;
}
.order-no {
  font-weight: 600;
}
.order-status.ok {
  color: #1e8449;
}
.order-status.warn {
  color: #b9770e;
}
.order-status.mute {
  color: #98a3b3;
}
.order-info {
  font-size: 12px;
  color: #5d6b82;
}
</style>
