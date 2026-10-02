<template>
  <view class="page">
    <view class="card">
      <view class="section-title">1. 选择科室</view>
      <picker :range="deptNames" @change="onDeptChange">
        <view class="picker">{{ deptNames[deptIndex] || '请选择科室' }}</view>
      </picker>
    </view>
    <view v-if="doctors.length" class="card">
      <view class="section-title">2. 选择医生</view>
      <view
        v-for="d in doctors"
        :key="d.id"
        class="doctor-item"
        :class="{ active: doctorId === d.id }"
        @tap="selectDoctor(d)"
      >
        <text class="doctor-name">{{ d.name }}</text>
        <text class="doctor-meta">{{ d.title }} · 号余 {{ d.slotCnt ?? '-' }}</text>
      </view>
    </view>
    <view v-if="doctorId" class="card">
      <view class="section-title">3. 选择就诊日期</view>
      <picker :range="dates" @change="onDateChange">
        <view class="picker">{{ dates[dateIndex] || '请选择日期' }}</view>
      </picker>
      <button class="btn-primary submit" :loading="submitting" @tap="submit">确认挂号</button>
    </view>
  </view>
</template>

<script setup>
/** 预约挂号页（迭代17 K5）：科室 → 医生 → 日期 → 下单 */
import { computed, ref } from 'vue'
import { createAppointment, listDepartments, listDoctors, listSchedules } from '../../api/request'

const departments = ref([])
const deptIndex = ref(0)
const deptNames = computed(() => departments.value.map((d) => d.deptName || d.name))
const doctors = ref([])
const doctorId = ref(null)
const dates = ref([])
const dateIndex = ref(0)
const submitting = ref(false)

const loadDepts = async () => {
  try {
    const list = await listDepartments()
    departments.value = Array.isArray(list) ? list : []
  } catch (e) {
    uni.showToast({ title: e.message || '科室加载失败', icon: 'none' })
  }
}

const onDeptChange = async (e) => {
  deptIndex.value = Number(e.detail.value)
  doctorId.value = null
  try {
    const dept = departments.value[deptIndex.value]
    const list = await listDoctors(dept.id)
    doctors.value = Array.isArray(list) ? list : []
  } catch (err) {
    uni.showToast({ title: err.message || '医生加载失败', icon: 'none' })
  }
}

const selectDoctor = async (d) => {
  doctorId.value = d.id
  const days = []
  for (let i = 0; i < 7; i++) {
    const dt = new Date(Date.now() + i * 86400000)
    days.push(dt.toISOString().slice(0, 10))
  }
  dates.value = days
  dateIndex.value = 0
  try {
    await listSchedules(d.id, days[0])
  } catch (e) {
    uni.showToast({ title: e.message || '排班加载失败', icon: 'none' })
  }
}

const onDateChange = (e) => {
  dateIndex.value = Number(e.detail.value)
}

const submit = async () => {
  submitting.value = true
  try {
    await createAppointment({
      doctorId: doctorId.value,
      scheduleDate: dates.value[dateIndex.value],
      period: 'AM',
    })
    uni.showToast({ title: '挂号成功', icon: 'success' })
    setTimeout(() => uni.navigateTo({ url: '/pages/orders/index' }), 800)
  } catch (e) {
    uni.showToast({ title: e.message || '挂号失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

loadDepts()
</script>

<style scoped>
.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}
.picker {
  padding: 10px;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  color: #2c3e50;
}
.doctor-item {
  padding: 10px;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
}
.doctor-item.active {
  border-color: #3b82f6;
  background: #ecf5ff;
}
.doctor-name {
  font-weight: 600;
}
.doctor-meta {
  font-size: 12px;
  color: #98a3b3;
}
.submit {
  margin-top: 14px;
}
</style>
