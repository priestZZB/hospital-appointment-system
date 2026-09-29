<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { WMessage } from 'win-design-next'
import { Hospital, Key, Lock, Telephone, User } from '@win-design-next/icons-vue'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { homeForRoles } from '@/utils/permission'
import type { FormInstance, FormRules } from 'win-design-next'
import bgImage from '@/assets/images/login-bg.jpg'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  phone: '13800000000',
  password: '123456',
  rememberMe: true,
})

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6~32 位', trigger: 'blur' },
  ],
}

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const features = [
  { icon: Hospital, title: '智能分诊', desc: 'AI 症状识别，精准推荐科室' },
  { icon: Key, title: '在线挂号', desc: '号源实时同步，秒级锁定' },
  { icon: Lock, title: '安全支付', desc: '本地消息表保障，超时自动退款' },
]

async function handleLogin() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const data = await userStore.login({ phone: form.phone, password: form.password })
    // 登录后立即拉取权限码（迭代5 阶段4：路由守卫/菜单/按钮均依赖）
    try {
      await permissionStore.load()
    } catch {
      // 权限拉取失败不阻断登录（fail-open）
    }
    WMessage.success('登录成功')
    const roles = data.roles || []
    const defaultHome = homeForRoles(roles)
    const redirect = (route.query.redirect as string) || defaultHome
    router.replace(redirect)
  } catch (e) {
    WMessage.error((e as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌展示区 -->
    <div class="login-brand">
      <div class="brand-bg">
        <img :src="bgImage" alt="医院场景" />
      </div>
      <div class="brand-overlay"></div>
      <div class="brand-content">
        <div class="brand-logo">
          <span class="brand-mark"><Hospital /></span>
          <span class="brand-title">医院门诊预约挂号系统</span>
        </div>
        <h1 class="brand-slogan">让每一次就诊<br />更简单、更高效</h1>
        <p class="brand-desc">基于微服务架构的智慧医疗平台，覆盖挂号、支付、签到、诊疗全流程</p>
        <div class="brand-features">
          <div v-for="f in features" :key="f.title" class="feature-item">
            <span class="feature-icon"><component :is="f.icon" /></span>
            <div class="feature-text">
              <span class="feature-title">{{ f.title }}</span>
              <span class="feature-desc">{{ f.desc }}</span>
            </div>
          </div>
        </div>
        <div class="brand-footer">© 2026 Hospital Appointment System · All Rights Reserved</div>
      </div>
    </div>

    <!-- 右侧登录卡片 -->
    <div class="login-panel">
      <div class="login-card">
        <h2 class="login-title">{{ greeting }}，欢迎登录</h2>
        <p class="login-subtitle">请输入您的账号信息进入系统</p>

        <w-form ref="formRef" :model="form" :rules="rules" class="login-form" size="large">
          <w-form-item prop="phone">
            <w-input v-model="form.phone" placeholder="请输入手机号" clearable :prefix-icon="Telephone" />
          </w-form-item>
          <w-form-item prop="password">
            <w-input v-model="form.password" type="password" placeholder="请输入密码" show-password :prefix-icon="Key" />
          </w-form-item>
          <div class="login-row">
            <w-checkbox v-model="form.rememberMe">记住账号</w-checkbox>
            <span class="help">忘记密码？<a>联系管理员</a></span>
          </div>
          <w-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </w-button>
        </w-form>

        <div class="login-tip">
          <span>演示账号</span>
          <code>13800000000</code>
          <span>/</span>
          <code>123456</code>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: #fff;
}

/* ===== 左侧品牌区 ===== */
.login-brand {
  position: relative;
  flex: 1.15;
  overflow: hidden;
  display: flex;
}
.brand-bg {
  position: absolute;
  inset: 0;
}
.brand-bg img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.brand-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(13, 23, 48, 0.88) 0%, rgba(29, 57, 196, 0.78) 55%, rgba(45, 90, 250, 0.62) 100%);
}
.brand-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 56px 64px;
  color: #fff;
  max-width: 640px;
}
.brand-logo {
  display: flex;
  align-items: center;
  gap: 14px;
}
.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.brand-title {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}
.brand-slogan {
  margin: 64px 0 0;
  font-size: 38px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 1px;
  text-shadow: 0 4px 24px rgba(0, 0, 0, 0.25);
}
.brand-desc {
  margin: 16px 0 0;
  font-size: 14.5px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.78);
  max-width: 420px;
}
.brand-features {
  margin-top: 44px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.feature-item {
  display: flex;
  align-items: center;
  gap: 14px;
}
.feature-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  flex-shrink: 0;
}
.feature-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.feature-title {
  font-size: 15px;
  font-weight: 600;
}
.feature-desc {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.65);
}
.brand-footer {
  margin-top: 48px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}

/* ===== 右侧登录区 ===== */
.login-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: linear-gradient(180deg, #f8faff 0%, #eef2fb 100%);
}
.login-card {
  width: 420px;
  background: #fff;
  border-radius: var(--hospital-radius-lg, 12px);
  padding: 48px 44px 40px;
  box-shadow: 0 12px 48px rgba(16, 24, 40, 0.1), 0 2px 8px rgba(16, 24, 40, 0.05);
  animation: card-in 0.55s cubic-bezier(0.22, 0.8, 0.36, 1) both;
}
@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.login-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--hospital-text-main);
}
.login-subtitle {
  margin: 10px 0 0;
  font-size: 13.5px;
  color: var(--hospital-text-second);
}
.login-form {
  margin-top: 32px;
}
.login-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 22px;
}
.help {
  font-size: 13px;
  color: var(--hospital-text-second);
}
.help a {
  color: var(--w3-color-primary, #2d5afa);
  cursor: pointer;
  text-decoration: none;
}
.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 8px;
  border-radius: 8px;
}
.login-tip {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--hospital-text-third);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.login-tip code {
  background: var(--w3-color-primary-plain, #eaeefe);
  color: var(--w3-color-primary-press, #1d39c4);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

@media (max-width: 1024px) {
  .login-brand {
    display: none;
  }
  .login-panel {
    flex: 1;
  }
}
</style>
