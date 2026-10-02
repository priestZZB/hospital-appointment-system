<script setup lang="ts">
/**
 * 院内公告页（迭代13 L3，/notices，base=/api/clinic/notices）。
 * 公告发布（管理员）+ 下线 + 分页浏览（类型筛选）。
 */
import { onMounted, reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import { Plus, RefreshLeft, Search } from '@win-design-next/icons-vue'
import { getNoticeListApi, offlineNoticeApi, publishNoticeApi } from '@/api/online'
import type { Notice } from '@/api/online'

function typeText(v?: string): string {
  return v === 'POLICY' ? '政策' : v === 'ACTIVITY' ? '活动' : '通知'
}
function statusMeta(v?: string): { label: string; tag: 'success' | 'info' } {
  return (v || '').toUpperCase() === 'OFFLINE' ? { label: '已下线', tag: 'info' } : { label: '已发布', tag: 'success' }
}

const loading = ref(false)
const list = ref<Notice[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const query = reactive<{ noticeType: string; keyword: string }>({ noticeType: '', keyword: '' })

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (query.noticeType) params.noticeType = query.noticeType
    const kw = query.keyword.trim()
    if (kw) params.keyword = kw
    const page = await getNoticeListApi(params)
    list.value = Array.isArray(page?.records) ? page.records : []
    total.value = page?.total ?? 0
  } catch (e) {
    WMessage.error((e as Error).message || '公告加载失败')
  } finally {
    loading.value = false
  }
}
function handleReset() {
  query.noticeType = ''
  query.keyword = ''
  pageNo.value = 1
  fetchList()
}

/* 发布弹窗 */
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive<{ title: string; content: string; noticeType: string; publisherName: string }>({
  title: '',
  content: '',
  noticeType: 'NOTICE',
  publisherName: '院办',
})
function openCreate() {
  form.title = ''
  form.content = ''
  form.noticeType = 'NOTICE'
  form.publisherName = '院办'
  dialogVisible.value = true
}
async function submitCreate() {
  if (!form.title.trim()) {
    WMessage.warning('请填写公告标题')
    return
  }
  saving.value = true
  try {
    await publishNoticeApi({
      title: form.title.trim(),
      content: form.content.trim() || undefined,
      noticeType: form.noticeType,
      publisherName: form.publisherName.trim() || undefined,
    })
    WMessage.success('公告已发布')
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '发布失败')
  } finally {
    saving.value = false
  }
}

async function handleOffline(row: Notice) {
  if (!row.id) return
  try {
    await offlineNoticeApi(row.id)
    WMessage.success('已下线')
    fetchList()
  } catch (e) {
    WMessage.error((e as Error).message || '下线失败')
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">院内公告</h2>
      <p class="page-subtitle">公告发布 / 下线 / 全员浏览（L3）</p>
    </div>

    <w-card shadow="never" class="hospital-card">
      <w-form inline class="query-form">
        <w-form-item label="类型">
          <w-select v-model="query.noticeType" placeholder="全部类型" clearable style="width: 150px">
            <w-option label="通知" value="NOTICE" />
            <w-option label="政策" value="POLICY" />
            <w-option label="活动" value="ACTIVITY" />
          </w-select>
        </w-form-item>
        <w-form-item label="标题">
          <w-input v-model="query.keyword" placeholder="标题关键字" clearable style="width: 200px" @keyup.enter="pageNo = 1; fetchList()" />
        </w-form-item>
        <w-form-item>
          <w-button type="primary" :icon="Search" :loading="loading" @click="pageNo = 1; fetchList()">查询</w-button>
          <w-button :icon="RefreshLeft" @click="handleReset">重置</w-button>
        </w-form-item>
      </w-form>
    </w-card>

    <w-card shadow="never" class="hospital-card">
      <div class="table-toolbar list-toolbar">
        <div class="table-toolbar__left"><span class="table-total">共 {{ total }} 条公告</span></div>
        <div class="table-toolbar__right">
          <w-button type="primary" :icon="Plus" @click="openCreate">发布公告</w-button>
        </div>
      </div>

      <w-table :data="list" row-key="id" border stripe :loading="loading" empty-text="暂无公告" size="default">
        <w-table-column label="标题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || '-' }}</template>
        </w-table-column>
        <w-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <w-tag effect="light">{{ typeText(row.noticeType) }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column label="发布人" width="110">
          <template #default="{ row }">{{ row.publisherName || row.publisher_name || '-' }}</template>
        </w-table-column>
        <w-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ row.publishTimeText || row.publish_time_text || '-' }}</template>
        </w-table-column>
        <w-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <w-tag :type="statusMeta(row.status).tag" effect="light">{{ statusMeta(row.status).label }}</w-tag>
          </template>
        </w-table-column>
        <w-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <w-button v-if="(row.status || '').toUpperCase() === 'PUBLISHED'" type="warning" link @click="handleOffline(row)">
              下线
            </w-button>
            <span v-else>-</span>
          </template>
        </w-table-column>
      </w-table>

      <w-pagination
        class="pager"
        :current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, sizes"
        @current-change="(p: number) => { pageNo = p; fetchList() }"
        @size-change="(s: number) => { pageSize = s; pageNo = 1; fetchList() }"
      />
    </w-card>

    <w-dialog v-model="dialogVisible" title="发布公告" width="560px" destroy-on-close>
      <w-form label-width="88px">
        <w-form-item label="公告标题" required>
          <w-input v-model="form.title" placeholder="公告标题" />
        </w-form-item>
        <w-form-item label="类型">
          <w-radio-group v-model="form.noticeType">
            <w-radio value="NOTICE">通知</w-radio>
            <w-radio value="POLICY">政策</w-radio>
            <w-radio value="ACTIVITY">活动</w-radio>
          </w-radio-group>
        </w-form-item>
        <w-form-item label="正文">
          <w-input v-model="form.content" type="textarea" :rows="4" placeholder="公告正文" />
        </w-form-item>
        <w-form-item label="发布人">
          <w-input v-model="form.publisherName" />
        </w-form-item>
      </w-form>
      <template #footer>
        <w-button @click="dialogVisible = false">取消</w-button>
        <w-button type="primary" :loading="saving" @click="submitCreate">发布</w-button>
      </template>
    </w-dialog>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
