<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { WMessage, WMessageBox } from 'win-design-next'

const router = useRouter()
const userStore = useUserStore()

const user = userStore.userInfo as Record<string, unknown> | null

async function handleLogout() {
  await WMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  await userStore.logout()
  router.replace('/login')
}

async function handleRefresh() {
  try {
    await userStore.refresh()
    WMessage.success('令牌已刷新')
  } catch (e) {
    WMessage.error((e as Error).message || '刷新失败')
  }
}
</script>

<template>
  <div class="profile">
    <div class="page-head">
      <h2>个人中心</h2>
      <p>当前登录账号信息（登录信息来自 POST /api/auth/login 返回，依据：API接口文档 §1 #2）</p>
    </div>
    <w-card shadow="hover" class="profile-card">
      <template #header><span class="card-title">账号信息</span></template>
      <w-descriptions :column="2" border>
        <w-descriptions-item label="手机号">{{ user?.phone || '-' }}</w-descriptions-item>
        <w-descriptions-item label="姓名">{{ user?.realName || '-' }}</w-descriptions-item>
        <w-descriptions-item label="用户ID">{{ user?.userId ?? '-' }}</w-descriptions-item>
        <w-descriptions-item label="角色">
          <w-tag v-for="role in userStore.roles" :key="role" class="role-tag">{{ role }}</w-tag>
          <span v-if="!userStore.roles.length">-</span>
        </w-descriptions-item>
      </w-descriptions>
      <div class="actions">
        <w-button type="primary" plain @click="handleRefresh">刷新令牌</w-button>
        <w-button type="danger" plain @click="handleLogout">退出登录</w-button>
      </div>
    </w-card>
  </div>
</template>

<style scoped>
.profile {
  max-width: 860px;
}
.page-head h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
}
.page-head p {
  margin: 0 0 16px;
  font-size: 12.5px;
  color: var(--w3-font-color-third, #999);
}
.card-title {
  font-size: 15px;
  font-weight: 600;
}
.role-tag {
  margin-right: 6px;
}
.actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
