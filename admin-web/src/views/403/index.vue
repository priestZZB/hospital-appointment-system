<template>
  <div class="forbidden-page">
    <div class="code">403</div>
    <div class="msg">抱歉，您没有权限访问该页面</div>
    <w-button type="primary" @click="router.push(home)">返回首页</w-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { getUser } from '@/utils/auth'

const router = useRouter()
const roles = (getUser()?.roles as string[]) || []
const home = roles.includes('ROLE_DOCTOR') ? '/workbench' : '/dashboard'
</script>

<style scoped>
.forbidden-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: var(--hospital-bg-page, #f4f7fe);
}
.code {
  font-size: 72px;
  font-weight: 800;
  color: var(--w3-color-primary, #2d5afa);
  letter-spacing: 2px;
}
.msg {
  font-size: 15px;
  color: var(--hospital-text-second, #5a6272);
}
</style>