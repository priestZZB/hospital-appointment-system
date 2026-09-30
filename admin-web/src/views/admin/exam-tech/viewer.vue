<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { WMessage } from 'win-design-next'
import { ArrowLeft, ArrowRight, Picture, Refresh, ZoomIn, ZoomOut } from '@win-design-next/icons-vue'
import { getCloudViewApi, getExamSeriesApi, getExamSeriesImageApi } from '@/api/lis'
import type { CloudViewVO, ImageSeries } from '@/types'

/**
 * PACS 影像浏览（迭代8）
 * - query.seriesId：直接按序列ID浏览（影像中心 / 医技工作台跳转入口）
 * - query.code：云影像模式（/medsupply/cloud/view/{code}），展示序列 + 检查报告，
 *   影像仍走 GET /admin/exam/image/series/{id}/image/{index} 的 blob 通道（带 token）
 * - 影像通过 axios（统一封装带 Authorization）以 blob 拉取后转 objectURL 展示
 */

const route = useRoute()

const isCloud = ref(false)
const cloudApplicationId = ref<number | null>(null)
const cloudReport = ref<CloudViewVO['report']>(null)

const seriesList = ref<ImageSeries[]>([])
const activeSeriesId = ref<number | null>(null)
const series = computed(() => seriesList.value.find((s) => s.id === activeSeriesId.value) || null)

const imageUrls = ref<(string | null)[]>([])
const pendingIdx = ref<Set<number>>(new Set())
const current = ref(0)
const initLoading = ref(false)
const initError = ref('')

// 缩放 + 窗宽窗位模拟（亮度 / 对比度 → CSS filter）
const scale = ref(1)
const brightness = ref(100)
const contrast = ref(100)

const imageCount = computed(() => series.value?.imageCount ?? imageUrls.value.length)
const imgStyle = computed(() => ({
  transform: `scale(${scale.value})`,
  filter: `brightness(${brightness.value}%) contrast(${contrast.value}%)`,
}))

function revokeAll() {
  imageUrls.value.forEach((u) => {
    if (u) URL.revokeObjectURL(u)
  })
  imageUrls.value = []
}

async function fetchImageObjectUrl(sid: number, index: number): Promise<string | null> {
  try {
    const blob = await getExamSeriesImageApi(sid, index)
    return URL.createObjectURL(blob)
  } catch {
    return null
  }
}

/** 确保第 index 张影像已加载（幂等，带并发去重） */
async function ensureImage(index: number): Promise<void> {
  const sid = activeSeriesId.value
  if (!sid) return
  if (index < 0 || index >= imageUrls.value.length) return
  if (imageUrls.value[index] || pendingIdx.value.has(index)) return
  pendingIdx.value.add(index)
  const url = await fetchImageObjectUrl(sid, index)
  if (url) imageUrls.value[index] = url
  const nextPending = new Set(pendingIdx.value)
  nextPending.delete(index)
  pendingIdx.value = nextPending
}

/** 缩略图后台顺序预取（也顺带填充主图缓存） */
async function prefetchThumbs(): Promise<void> {
  for (let i = 0; i < imageUrls.value.length; i += 1) {
    if (!imageUrls.value[i]) await ensureImage(i)
  }
}

async function loadSeries(sid: number): Promise<void> {
  const target = seriesList.value.find((s) => s.id === sid)
  if (!target) {
    WMessage.error('序列不存在或已失效')
    return
  }
  revokeAll()
  activeSeriesId.value = sid
  current.value = 0
  scale.value = 1
  brightness.value = 100
  contrast.value = 100
  const count = target.imageCount ?? 0
  imageUrls.value = Array.from({ length: count }, () => null)
  if (count <= 0) return
  await ensureImage(0)
  prefetchThumbs()
}

async function go(delta: number): Promise<void> {
  const next = current.value + delta
  if (next < 0 || next >= imageUrls.value.length) return
  current.value = next
  await ensureImage(next)
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'ArrowLeft') {
    go(-1)
  } else if (e.key === 'ArrowRight') {
    go(1)
  }
}

function zoomIn(): void {
  scale.value = Math.min(4, Number((scale.value + 0.2).toFixed(2)))
}

function zoomOut(): void {
  scale.value = Math.max(0.4, Number((scale.value - 0.2).toFixed(2)))
}

function resetView(): void {
  scale.value = 1
  brightness.value = 100
  contrast.value = 100
}

async function init(): Promise<void> {
  initLoading.value = true
  initError.value = ''
  try {
    const sidParam = route.query.seriesId ? Number(route.query.seriesId) : NaN
    const code = typeof route.query.code === 'string' ? route.query.code : ''
    if (code) {
      // 云影像模式
      isCloud.value = true
      const view = await getCloudViewApi(code)
      cloudApplicationId.value = view.applicationId
      cloudReport.value = view.report ?? null
      seriesList.value = view.series || []
      if (!seriesList.value.length) {
        initError.value = '该云影像链接下暂无影像序列'
        return
      }
      const wanted = seriesList.value.find((s) => s.id === sidParam)
      await loadSeries((wanted || seriesList.value[0]).id)
    } else if (!Number.isNaN(sidParam)) {
      // 直连序列模式
      const s = await getExamSeriesApi(sidParam)
      seriesList.value = [s]
      await loadSeries(s.id)
    } else {
      initError.value = '缺少 seriesId 或 code 参数'
    }
  } catch (e) {
    initError.value = (e as Error).message || '影像加载失败'
  } finally {
    initLoading.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  init()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  revokeAll()
})
</script>

<template>
  <div class="viewer-page">
    <!-- 页头 -->
    <div class="page-head">
      <h2 class="page-title">PACS 影像浏览</h2>
      <p class="page-subtitle">
        <template v-if="isCloud">云影像模式 · 访问码 {{ route.query.code }} · 申请 {{ cloudApplicationId ?? '-' }}</template>
        <template v-else>序列模式 · {{ series?.seriesNo || '-' }}</template>
      </p>
    </div>

    <!-- 加载 / 空态 -->
    <w-card v-if="initLoading" shadow="never" class="hospital-card">
      <div v-loading="true" class="viewer-empty" />
    </w-card>
    <w-card v-else-if="initError" shadow="never" class="hospital-card">
      <w-empty :description="initError">
        <w-button type="primary" :icon="Refresh" @click="init">重试</w-button>
      </w-empty>
    </w-card>

    <template v-else>
      <div class="viewer-layout">
        <!-- 主视图 -->
        <w-card shadow="never" class="hospital-card viewer-main">
          <!-- 工具栏 -->
          <div class="viewer-toolbar">
            <div class="viewer-toolbar__left">
              <w-select
                v-if="seriesList.length > 1"
                :model-value="activeSeriesId"
                placeholder="切换序列"
                style="width: 220px"
                @change="(v: number | string) => loadSeries(Number(v))"
              >
                <w-option v-for="s in seriesList" :key="s.id" :label="`#${s.id} ${s.seriesNo || ''}（${s.modality || '-'} · ${s.imageCount ?? 0} 张）`" :value="s.id" />
              </w-select>
              <w-tag v-if="series?.modality" type="primary" effect="light" size="small">{{ series.modality }}</w-tag>
              <w-tag v-if="isCloud" type="success" effect="light" size="small">云影像</w-tag>
              <span v-if="imageCount > 0" class="viewer-index">{{ current + 1 }} / {{ imageCount }}</span>
            </div>
            <div class="viewer-toolbar__right">
              <w-button size="small" :icon="ArrowLeft" :disabled="current <= 0" @click="go(-1)">上一张</w-button>
              <w-button size="small" :icon="ArrowRight" :disabled="current >= imageCount - 1" @click="go(1)">下一张</w-button>
              <w-divider direction="vertical" />
              <w-button size="small" :icon="ZoomOut" :disabled="scale <= 0.4" @click="zoomOut">缩小</w-button>
              <w-button size="small" :icon="ZoomIn" :disabled="scale >= 4" @click="zoomIn">放大</w-button>
              <w-button size="small" link type="primary" @click="resetView">重置视图</w-button>
              <w-divider direction="vertical" />
              <span class="viewer-slider-label">亮度</span>
              <w-slider v-model="brightness" :min="30" :max="200" style="width: 110px" />
              <span class="viewer-slider-label">对比度</span>
              <w-slider v-model="contrast" :min="30" :max="200" style="width: 110px" />
            </div>
          </div>

          <!-- 影像画布 -->
          <div class="viewer-canvas" tabindex="0">
            <div v-if="imageCount <= 0" class="viewer-empty">
              <w-empty description="该序列暂无影像" />
            </div>
            <img
              v-else-if="imageUrls[current]"
              :src="imageUrls[current] || undefined"
              class="viewer-image"
              :style="imgStyle"
              alt="DICOM 影像帧"
            />
            <div v-else v-loading="true" class="viewer-empty" />
            <span v-if="imageCount > 0" class="viewer-hint">← / → 键翻页 · 滚轮缩放请用工具栏按钮</span>
          </div>

          <!-- 缩略图条 -->
          <div v-if="imageCount > 0" class="viewer-thumbs">
            <button
              v-for="(url, i) in imageUrls"
              :key="i"
              type="button"
              class="viewer-thumb"
              :class="{ 'viewer-thumb--active': i === current }"
              @click="current = i; ensureImage(i)"
            >
              <img v-if="url" :src="url" alt="" />
              <span v-else class="viewer-thumb__loading">{{ i + 1 }}</span>
            </button>
          </div>
        </w-card>

        <!-- 云影像报告侧栏 -->
        <w-card v-if="isCloud" shadow="never" class="hospital-card viewer-report">
          <div class="report-head">
            <Picture class="report-head__icon" />
            <span class="report-head__title">检查报告</span>
          </div>
          <template v-if="cloudReport">
            <div class="report-block">
              <div class="report-block__label">所见（findings）</div>
              <p class="report-block__content">{{ cloudReport.findings || '-' }}</p>
            </div>
            <div class="report-block">
              <div class="report-block__label">印象（conclusion）</div>
              <p class="report-block__content">{{ cloudReport.conclusion || '-' }}</p>
            </div>
          </template>
          <w-empty v-else description="该检查暂未发布报告" />
        </w-card>
      </div>
    </template>
  </div>
</template>

<style scoped>
.viewer-page {
  width: 100%;
}

.viewer-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
}
.viewer-main {
  flex: 1;
  min-width: 0;
}
.viewer-report {
  width: 340px;
  flex-shrink: 0;
}

.viewer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.viewer-toolbar__left,
.viewer-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.viewer-index {
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.viewer-slider-label {
  font-size: 12px;
  color: var(--hospital-text-third);
}

.viewer-canvas {
  position: relative;
  height: 560px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: var(--hospital-radius-md);
  background:
    linear-gradient(45deg, #14161c 25%, transparent 25%) -16px 0,
    linear-gradient(-45deg, #14161c 25%, transparent 25%) -16px 0,
    linear-gradient(45deg, transparent 75%, #14161c 75%),
    linear-gradient(-45deg, transparent 75%, #14161c 75%);
  background-size: 32px 32px;
  background-color: #0e1015;
  outline: none;
}
.viewer-image {
  max-width: 96%;
  max-height: 96%;
  object-fit: contain;
  transition: transform 0.15s ease, filter 0.15s ease;
}
.viewer-hint {
  position: absolute;
  right: 12px;
  bottom: 10px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  user-select: none;
}
.viewer-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 320px;
}

.viewer-thumbs {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding: 8px;
  overflow-x: auto;
  border: 1px solid var(--hospital-border-lighter);
  border-radius: var(--hospital-radius-md);
  background: var(--w3-fill-color-lighter, #fafafa);
}
.viewer-thumb {
  position: relative;
  width: 88px;
  height: 88px;
  padding: 0;
  flex-shrink: 0;
  border: 2px solid transparent;
  border-radius: var(--hospital-radius-sm);
  background: #0e1015;
  cursor: pointer;
  overflow: hidden;
}
.viewer-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.viewer-thumb--active {
  border-color: var(--w3-color-primary, #2d5afa);
}
.viewer-thumb__loading {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.report-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.report-head__icon {
  color: var(--w3-color-primary);
  font-size: 16px;
}
.report-head__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--hospital-text-main);
}
.report-block {
  margin-bottom: 14px;
}
.report-block__label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--hospital-text-third);
}
.report-block__content {
  margin: 0;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  color: var(--hospital-text-main);
  background: var(--w3-fill-color-lighter, #fafafa);
  border-radius: var(--hospital-radius-md);
  border: 1px solid var(--hospital-border-lighter);
}
</style>
