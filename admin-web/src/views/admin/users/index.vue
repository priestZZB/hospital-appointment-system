<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { CirclePlus, Plus, Refresh, Search, UserGroup } from '@win-design-next/icons-vue'
import {
  assignRolesApi,
  createRoleApi,
  createUserApi,
  deleteRoleApi,
  getRoleApi,
  getRolesApi,
  getUsersApi,
  updateRoleApi,
  updateUserStatusApi,
} from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { RoleVO, UserVO } from '@/types'

/** 用户类型展示元数据（语义色标签） */
const USER_TYPE_META: Record<string, { label: string; type: 'success' | 'warning' | 'primary' }> = {
  PATIENT: { label: '患者', type: 'success' },
  DOCTOR: { label: '医生', type: 'warning' },
  ADMIN: { label: '管理员', type: 'primary' },
}

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const activeTab = ref('users')

/* ── 用户列表 ── */
const loading = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const query = reactive({ phone: '', userType: '', status: undefined as number | undefined })

/* ── 创建用户 ── */
const createVisible = ref(false)
const createForm = reactive({
  phone: '',
  password: '',
  realName: '',
  gender: 1,
  userType: 'DOCTOR',
  roleIds: [] as number[],
})

/* ── 角色 ── */
const roles = ref<RoleVO[]>([])
const roleLoading = ref(false)

/* ── 分配角色 ── */
const assignVisible = ref(false)
const assignUser = ref<UserVO | null>(null)
const assignRoleIds = ref<number[]>([])

/* ── 角色弹窗 ── */
const roleDialog = ref(false)
const roleEditing = ref<RoleVO | null>(null)
const roleForm = reactive({ roleCode: '', roleName: '', description: '', status: 1 })

function userTypeLabel(type: string): string {
  return USER_TYPE_META[type]?.label || type
}

function userTypeTag(type: string): 'success' | 'warning' | 'primary' | 'info' {
  return USER_TYPE_META[type]?.type || 'info'
}

function roleName(code: string): string {
  return roles.value.find((r) => r.roleCode === code)?.roleName || code
}

async function fetchList() {
  loading.value = true
  try {
    const page = await getUsersApi({ ...query, pageNo: pageNo.value, pageSize: pageSize.value })
    list.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    WMessage.error((e as Error).message || '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  roleLoading.value = true
  try {
    roles.value = await getRolesApi()
  } catch (e) {
    WMessage.error((e as Error).message || '角色列表加载失败')
  } finally {
    roleLoading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchList()
}

function handleReset() {
  query.phone = ''
  query.userType = ''
  query.status = undefined
  pageNo.value = 1
  fetchList()
}

function handleSizeChange() {
  pageNo.value = 1
  fetchList()
}

function openCreate() {
  Object.assign(createForm, { phone: '', password: '', realName: '', gender: 1, userType: 'DOCTOR', roleIds: [] })
  createVisible.value = true
}

async function handleCreate() {
  if (!createForm.phone || !createForm.password || !createForm.realName || !createForm.userType) {
    WMessage.warning('手机号、密码、姓名、用户类型必填')
    return
  }
  try {
    await createUserApi({ ...createForm })
    WMessage.success('用户创建成功')
    createVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '创建失败')
  }
}

async function handleStatus(row: UserVO) {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateUserStatusApi(row.id, next)
    WMessage.success(next === 1 ? '已启用' : '已禁用')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '操作失败')
  }
}

function openAssign(row: UserVO) {
  assignUser.value = row
  assignRoleIds.value = (row.roles || [])
    .map((code) => roles.value.find((role) => role.roleCode === code)?.id || 0)
    .filter(Boolean)
  assignVisible.value = true
}

async function handleAssign() {
  if (!assignUser.value) return
  try {
    await assignRolesApi(assignUser.value.id, assignRoleIds.value)
    WMessage.success('角色分配成功')
    assignVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '分配失败')
  }
}

async function openRoleDialog(row?: RoleVO) {
  roleEditing.value = row || null
  if (row) {
    try {
      const full = await getRoleApi(row.id)
      Object.assign(roleForm, {
        roleCode: full.roleCode,
        roleName: full.roleName,
        description: full.description || '',
        status: full.status ?? 1,
      })
    } catch {
      Object.assign(roleForm, {
        roleCode: row.roleCode,
        roleName: row.roleName,
        description: row.description || '',
        status: row.status ?? 1,
      })
    }
  } else {
    Object.assign(roleForm, { roleCode: '', roleName: '', description: '', status: 1 })
  }
  roleDialog.value = true
}

async function saveRole() {
  if (!roleForm.roleCode || !roleForm.roleName) {
    WMessage.warning('角色编码与名称必填')
    return
  }
  try {
    if (roleEditing.value) {
      await updateRoleApi(roleEditing.value.id, {
        roleName: roleForm.roleName,
        description: roleForm.description,
        status: roleForm.status,
      })
    } else {
      await createRoleApi({ ...roleForm })
    }
    WMessage.success('保存成功')
    roleDialog.value = false
    loadRoles()
  } catch (e) {
    WMessage.error((e as Error).message || '保存失败')
  }
}

async function removeRole(row: RoleVO) {
  try {
    await deleteRoleApi(row.id)
    WMessage.success('已删除')
    loadRoles()
  } catch (e) {
    WMessage.error((e as Error).message || '删除失败')
  }
}

onMounted(() => {
  fetchList()
  loadRoles()
})
</script>

<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title"><UserGroup class="title-icon" />用户管理</h2>
      <p class="page-subtitle">账号与角色权限统一管理，支持创建用户、分配角色与启停控制</p>
    </div>

    <!-- 主体卡片 -->
    <w-card shadow="never" class="hospital-card">
      <w-tabs v-model="activeTab">
        <!-- 用户列表 -->
        <w-tab-pane label="用户列表" name="users">
          <!-- 查询区 -->
          <w-form :model="query" inline label-position="right" class="query-form">
            <w-form-item label="手机号">
              <w-input v-model="query.phone" placeholder="请输入手机号" clearable style="width: 170px" />
            </w-form-item>
            <w-form-item label="用户类型">
              <w-select v-model="query.userType" placeholder="全部" clearable style="width: 140px">
                <w-option label="患者" value="PATIENT" />
                <w-option label="医生" value="DOCTOR" />
                <w-option label="管理员" value="ADMIN" />
              </w-select>
            </w-form-item>
            <w-form-item label="状态">
              <w-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
                <w-option label="启用" :value="1" />
                <w-option label="禁用" :value="0" />
              </w-select>
            </w-form-item>
            <w-form-item>
              <w-button type="primary" :icon="Search" @click="handleSearch">查询</w-button>
              <w-button :icon="Refresh" @click="handleReset">重置</w-button>
            </w-form-item>
          </w-form>

          <!-- 工具栏 -->
          <div class="table-toolbar">
            <div class="table-toolbar__left">
              <span class="toolbar-tip">共 {{ total }} 条记录</span>
            </div>
            <div class="table-toolbar__right">
              <w-button v-if="isAdmin" type="primary" :icon="CirclePlus" @click="openCreate">创建用户</w-button>
            </div>
          </div>

          <!-- 表格 -->
          <w-table :data="list" row-key="id" border stripe :loading="loading" size="default">
            <w-table-column prop="id" label="ID" width="70" />
            <w-table-column prop="phone" label="手机号" min-width="130" />
            <w-table-column prop="realName" label="姓名" min-width="110" />
            <w-table-column label="类型" width="100" align="center">
              <template #default="{ row }">
                <w-tag size="small" :type="userTypeTag(row.userType)">{{ userTypeLabel(row.userType) }}</w-tag>
              </template>
            </w-table-column>
            <w-table-column label="角色" min-width="180">
              <template #default="{ row }">
                <div class="role-cell">
                  <w-tag v-for="r in row.roles || []" :key="r" size="small" type="info" effect="plain">{{ roleName(r) }}</w-tag>
                  <span v-if="!row.roles || !row.roles.length" class="cell-empty">—</span>
                </div>
              </template>
            </w-table-column>
            <w-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <w-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</w-tag>
              </template>
            </w-table-column>
            <w-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ row.createTime || '—' }}</template>
            </w-table-column>
            <w-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <div class="op-cell">
                  <w-button v-if="isAdmin" size="small" text type="primary" @click="openAssign(row)">分配角色</w-button>
                  <w-popconfirm
                    v-if="isAdmin"
                    :title="row.status === 1 ? '确定禁用该用户吗？' : '确定启用该用户吗？'"
                    @confirm="handleStatus(row)"
                  >
                    <template #reference>
                      <w-button size="small" text :type="row.status === 1 ? 'danger' : 'success'">
                        {{ row.status === 1 ? '禁用' : '启用' }}
                      </w-button>
                    </template>
                  </w-popconfirm>
                </div>
              </template>
            </w-table-column>
          </w-table>

          <!-- 分页 -->
          <div class="pager">
            <w-pagination
              v-model:current-page="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              layout="total, sizes, prev, pager, next"
              @current-change="fetchList"
              @size-change="handleSizeChange"
            />
          </div>
        </w-tab-pane>

        <!-- 角色管理 -->
        <w-tab-pane label="角色管理" name="roles">
          <div class="table-toolbar">
            <div class="table-toolbar__left">
              <span class="toolbar-tip">共 {{ roles.length }} 个角色</span>
            </div>
            <div class="table-toolbar__right">
              <w-button v-if="isAdmin" type="primary" :icon="Plus" @click="openRoleDialog()">新增角色</w-button>
            </div>
          </div>

          <w-table :data="roles" row-key="id" border stripe :loading="roleLoading" size="default">
            <w-table-column prop="id" label="ID" width="80" />
            <w-table-column prop="roleCode" label="角色编码" min-width="150" />
            <w-table-column prop="roleName" label="角色名称" min-width="150" />
            <w-table-column label="描述" min-width="200">
              <template #default="{ row }">{{ row.description || '—' }}</template>
            </w-table-column>
            <w-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <w-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</w-tag>
              </template>
            </w-table-column>
            <w-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ row.createTime || '—' }}</template>
            </w-table-column>
            <w-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <div class="op-cell">
                  <w-button v-if="isAdmin" size="small" text type="primary" @click="openRoleDialog(row)">编辑</w-button>
                  <w-popconfirm v-if="isAdmin" title="确定删除该角色吗？" @confirm="removeRole(row)">
                    <template #reference>
                      <w-button size="small" text type="danger">删除</w-button>
                    </template>
                  </w-popconfirm>
                </div>
              </template>
            </w-table-column>
          </w-table>
        </w-tab-pane>
      </w-tabs>
    </w-card>

    <!-- 创建用户 -->
    <w-dialog v-model="createVisible" title="创建用户" width="520px">
      <w-form :model="createForm" label-width="88px">
        <w-form-item label="手机号" required>
          <w-input v-model="createForm.phone" placeholder="请输入 11 位手机号" clearable />
        </w-form-item>
        <w-form-item label="密码" required>
          <w-input v-model="createForm.password" type="password" placeholder="请输入 6~32 位密码" show-password />
        </w-form-item>
        <w-form-item label="姓名" required>
          <w-input v-model="createForm.realName" placeholder="请输入姓名" clearable />
        </w-form-item>
        <w-form-item label="用户类型">
          <w-select v-model="createForm.userType" style="width: 100%">
            <w-option label="患者" value="PATIENT" />
            <w-option label="医生" value="DOCTOR" />
            <w-option label="管理员" value="ADMIN" />
          </w-select>
        </w-form-item>
        <w-form-item label="角色">
          <w-select v-model="createForm.roleIds" multiple placeholder="可选，默认按类型分配" style="width: 100%">
            <w-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </w-select>
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="createVisible = false">取消</w-button>
        <w-button type="primary" @click="handleCreate">创建</w-button>
      </template>
    </w-dialog>

    <!-- 分配角色 -->
    <w-dialog v-model="assignVisible" :title="`分配角色：${assignUser?.realName || ''}`" width="460px">
      <w-checkbox-group v-model="assignRoleIds" class="assign-group">
        <w-checkbox v-for="r in roles" :key="r.id" :value="r.id">{{ r.roleName }}</w-checkbox>
      </w-checkbox-group>
      <template #footer>
        <w-button @click="assignVisible = false">取消</w-button>
        <w-button type="primary" @click="handleAssign">保存</w-button>
      </template>
    </w-dialog>

    <!-- 角色弹窗 -->
    <w-dialog v-model="roleDialog" :title="roleEditing ? '编辑角色' : '新增角色'" width="500px">
      <w-form :model="roleForm" label-width="80px">
        <w-form-item label="编码" required>
          <w-input v-model="roleForm.roleCode" :disabled="!!roleEditing" placeholder="如 ROLE_NURSE" />
        </w-form-item>
        <w-form-item label="名称" required>
          <w-input v-model="roleForm.roleName" placeholder="请输入角色名称" clearable />
        </w-form-item>
        <w-form-item label="描述">
          <w-input v-model="roleForm.description" type="textarea" :rows="3" placeholder="请输入角色描述" />
        </w-form-item>
        <w-form-item label="状态">
          <w-select v-model="roleForm.status" style="width: 100%">
            <w-option label="启用" :value="1" />
            <w-option label="禁用" :value="0" />
          </w-select>
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="roleDialog = false">取消</w-button>
        <w-button type="primary" @click="saveRole">保存</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.title-icon {
  color: var(--w3-color-primary);
}

.page-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.query-form {
  margin-bottom: 4px;
}

.toolbar-tip {
  font-size: 13px;
  color: var(--hospital-text-second);
}

.role-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.cell-empty {
  color: var(--hospital-text-third);
}

.op-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.assign-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}
</style>
