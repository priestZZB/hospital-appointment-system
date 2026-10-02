<template>
  <view class="home">
    <view class="banner">
      <text class="banner-title">智慧医院 · 挂号不排队</text>
      <text class="banner-sub">预约挂号 / 报告查询 / 院内导航</text>
    </view>
    <view class="grid card">
      <view class="grid-item" @tap="go('/pages/appointment/index')">
        <text class="grid-icon">📅</text>
        <text class="grid-label">预约挂号</text>
      </view>
      <view class="grid-item" @tap="go('/pages/orders/index')">
        <text class="grid-icon">📋</text>
        <text class="grid-label">我的预约</text>
      </view>
      <view class="grid-item" @tap="loadNotices">
        <text class="grid-icon">📢</text>
        <text class="grid-label">院内公告</text>
      </view>
      <view class="grid-item" @tap="go('/pages/profile/index')">
        <text class="grid-icon">👤</text>
        <text class="grid-label">个人中心</text>
      </view>
    </view>
    <view v-if="notices.length" class="card">
      <view class="section-title">院内公告</view>
      <view v-for="(n, i) in notices" :key="i" class="notice-item">
        <text class="notice-title">{{ n.title }}</text>
        <text class="notice-time">{{ (n.createTime || '').slice(0, 10) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
/** 患者端首页（迭代17 K5）：快捷入口 + 院内公告 */
import { ref } from 'vue'
import { listNotices } from '../../api/request'

const notices = ref([])

const go = (url) => uni.navigateTo({ url })

const loadNotices = async () => {
  try {
    const page = await listNotices()
    notices.value = Array.isArray(page?.records) ? page.records : []
    if (!notices.value.length) {
      uni.showToast({ title: '暂无公告', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  }
}

loadNotices()
</script>

<style scoped>
.banner {
  margin: 12px;
  padding: 22px 16px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border-radius: 12px;
  color: #fff;
  display: flex;
  flex-direction: column;
}
.banner-title {
  font-size: 20px;
  font-weight: 600;
}
.banner-sub {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.85;
}
.grid {
  display: flex;
  justify-content: space-between;
}
.grid-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.grid-icon {
  font-size: 26px;
}
.grid-label {
  font-size: 12px;
  color: #5d6b82;
}
.section-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.notice-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f3f7;
}
.notice-title {
  font-size: 13px;
}
.notice-time {
  font-size: 12px;
  color: #98a3b3;
}
</style>
