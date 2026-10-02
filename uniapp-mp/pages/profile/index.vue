<template>
  <view class="page">
    <view class="card profile">
      <view class="avatar">医</view>
      <view class="profile-info">
        <text class="profile-name">{{ phone || '未登录' }}</text>
        <text class="profile-sub">智慧医院患者端</text>
      </view>
    </view>
    <view class="card">
      <view class="form-item">
        <text class="form-label">手机号</text>
        <input v-model="phone" class="form-input" placeholder="请输入手机号" />
      </view>
      <view class="form-item">
        <text class="form-label">密码</text>
        <input v-model="password" class="form-input" password placeholder="请输入密码" />
      </view>
      <button class="btn-primary" :loading="loading" @tap="doLogin">登录</button>
      <button class="btn-logout" @tap="doLogout">退出登录</button>
    </view>
  </view>
</template>

<script setup>
/** 个人中心（迭代17 K5）：登录 / 退出 */
import { ref } from 'vue'
import { clearToken, getToken, login, setToken } from '../../api/request'

const phone = ref(uni.getStorageSync('hospital_phone') || '')
const password = ref('')
const loading = ref(false)

const doLogin = async () => {
  if (!phone.value || !password.value) {
    uni.showToast({ title: '请填写手机号与密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const data = await login(phone.value, password.value)
    setToken(data?.token || '')
    uni.setStorageSync('hospital_phone', phone.value)
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const doLogout = () => {
  clearToken()
  uni.showToast({ title: '已退出', icon: 'none' })
}

if (getToken()) {
  uni.showToast({ title: '已登录', icon: 'none' })
}
</script>

<style scoped>
.profile {
  display: flex;
  align-items: center;
  gap: 12px;
}
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #3b82f6;
  color: #fff;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.profile-name {
  font-weight: 600;
  font-size: 16px;
}
.profile-sub {
  font-size: 12px;
  color: #98a3b3;
  display: block;
}
.form-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.form-label {
  width: 60px;
  color: #5d6b82;
}
.form-input {
  flex: 1;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 8px 10px;
}
.btn-logout {
  margin-top: 12px;
  background: #fde8e8;
  color: #c0392b;
  border-radius: 8px;
}
</style>
