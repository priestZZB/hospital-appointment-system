<script setup lang="ts">
/**
 * AI 智能助手页（迭代16，/ai-assistant）。
 * Tab1 多轮问诊（B2-1 对话式追问+建议）；Tab2 智能预测（B2-2 候诊时长/B2-3 门诊量）；Tab3 AI 医助（B2-4 用药/B2-5 摘要）。
 */
import { reactive, ref } from 'vue'
import { WMessage } from 'win-design-next'
import {
  drugRecommendApi,
  predictVisitVolumeApi,
  predictWaitTimeApi,
  reportSummaryApi,
  replyConsultApi,
  startConsultApi,
} from '@/api/ai2'
import type { ConsultMessage } from '@/api/ai2'

const activeTab = ref<'consult' | 'predict' | 'assist'>('consult')

/* ================= Tab1 多轮问诊 ================= */
const chatLoading = ref(false)
const chatInput = ref('')
const chatMessages = ref<ConsultMessage[]>([])
const sessionNo = ref<string | null>(null)
const chatForm = reactive<{ patientId: number | null; symptom: string }>({ patientId: null, symptom: '' })

function roleLabel(role: string): string {
  return role === 'AI' ? 'AI 助手' : '患者'
}
async function startChat() {
  if (!chatForm.patientId || !chatForm.symptom.trim()) {
    WMessage.warning('请填写患者 ID 与主诉症状')
    return
  }
  chatLoading.value = true
  try {
    const res = await startConsultApi({ patientId: chatForm.patientId, symptom: chatForm.symptom.trim() })
    sessionNo.value = String(res?.sessionNo || '')
    chatMessages.value = [{ role: 'PATIENT', content: `主诉：${chatForm.symptom.trim()}` }, { role: 'AI', content: String(res?.question || '') }]
    WMessage.success('问诊已开始')
  } catch (e) {
    WMessage.error((e as Error).message || '开始失败')
  } finally {
    chatLoading.value = false
  }
}
async function sendReply() {
  const content = chatInput.value.trim()
  if (!content) return
  if (!sessionNo.value) {
    WMessage.warning('请先开始问诊')
    return
  }
  chatLoading.value = true
  chatMessages.value.push({ role: 'PATIENT', content })
  chatInput.value = ''
  try {
    const res = await replyConsultApi(sessionNo.value, { content })
    if (res?.finished) {
      chatMessages.value.push({ role: 'AI', content: `【初步建议】${res.suggestion}` })
      sessionNo.value = null
    } else {
      chatMessages.value.push({ role: 'AI', content: String(res?.question || '') })
    }
  } catch (e) {
    chatMessages.value.push({ role: 'AI', content: `（会话已结束或出错：${(e as Error).message}）` })
    sessionNo.value = null
  } finally {
    chatLoading.value = false
  }
}

/* ================= Tab2 智能预测 ================= */
const predictLoading = ref(false)
const waitForm = reactive<{ queueLength: number | null; avgMinutes: number | null; windows: number | null }>({ queueLength: 12, avgMinutes: 8, windows: 2 })
const waitResult = ref<Record<string, unknown> | null>(null)
const volumeInput = ref('120,135,128,140,150,145,160')
const volumeResult = ref<Record<string, unknown> | null>(null)

async function runWaitPredict() {
  if (!waitForm.queueLength || !waitForm.avgMinutes) {
    WMessage.warning('请填写队列长度与平均就诊分钟')
    return
  }
  predictLoading.value = true
  try {
    waitResult.value = await predictWaitTimeApi({
      queueLength: waitForm.queueLength,
      avgMinutes: waitForm.avgMinutes,
      windows: waitForm.windows ?? 1,
    })
  } catch (e) {
    WMessage.error((e as Error).message || '预测失败')
  } finally {
    predictLoading.value = false
  }
}
async function runVolumePredict() {
  const arr = volumeInput.value.split(/[,，\s]+/).map(Number).filter((v) => !Number.isNaN(v))
  if (arr.length < 3) {
    WMessage.warning('至少输入 3 天门诊量数据')
    return
  }
  predictLoading.value = true
  try {
    volumeResult.value = await predictVisitVolumeApi({ recentDaily: arr })
  } catch (e) {
    WMessage.error((e as Error).message || '预测失败')
  } finally {
    predictLoading.value = false
  }
}

/* ================= Tab3 AI 医助 ================= */
const assistLoading = ref(false)
const drugForm = reactive<{ diagnosis: string; allergy: string }>({ diagnosis: '', allergy: '' })
const drugResult = ref<Record<string, unknown> | null>(null)
const summaryForm = reactive<{ title: string; chiefComplaint: string; diagnosis: string; advice: string }>({
  title: '',
  chiefComplaint: '',
  diagnosis: '',
  advice: '',
})
const summaryResult = ref<Record<string, unknown> | null>(null)

async function runDrug() {
  if (!drugForm.diagnosis.trim()) {
    WMessage.warning('请填写诊断')
    return
  }
  assistLoading.value = true
  try {
    drugResult.value = await drugRecommendApi({ diagnosis: drugForm.diagnosis.trim(), allergy: drugForm.allergy.trim() || undefined })
  } catch (e) {
    WMessage.error((e as Error).message || '推荐失败')
  } finally {
    assistLoading.value = false
  }
}
async function runSummary() {
  if (!summaryForm.chiefComplaint.trim() || !summaryForm.diagnosis.trim()) {
    WMessage.warning('主诉与诊断不能为空')
    return
  }
  assistLoading.value = true
  try {
    summaryResult.value = await reportSummaryApi({
      title: summaryForm.title.trim() || undefined,
      chiefComplaint: summaryForm.chiefComplaint.trim(),
      diagnosis: summaryForm.diagnosis.trim(),
      advice: summaryForm.advice.trim() || undefined,
    })
  } catch (e) {
    WMessage.error((e as Error).message || '摘要失败')
  } finally {
    assistLoading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">AI 智能助手</h2>
      <p class="page-subtitle">多轮问诊 · 智能预测 · AI 医助（B2）</p>
    </div>

    <w-tabs v-model="activeTab" class="hospital-card">
      <w-tab-pane label="多轮问诊" name="consult" />
      <w-tab-pane label="智能预测" name="predict" />
      <w-tab-pane label="AI 医助" name="assist" />
    </w-tabs>

    <!-- Tab1 多轮问诊 -->
    <template v-if="activeTab === 'consult'">
      <w-card shadow="never" class="hospital-card">
        <w-form inline class="query-form">
          <w-form-item label="患者 ID">
            <w-input-number v-model="chatForm.patientId" :min="1" style="width: 140px" />
          </w-form-item>
          <w-form-item label="主诉">
            <w-input v-model="chatForm.symptom" placeholder="如 发热伴咳嗽" style="width: 240px" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :loading="chatLoading" @click="startChat">开始问诊</w-button>
          </w-form-item>
        </w-form>
        <div class="chat-box">
          <div v-for="(m, i) in chatMessages" :key="i" class="chat-row" :class="m.role === 'AI' ? 'ai' : 'me'">
            <div class="chat-bubble">
              <div class="chat-role">{{ roleLabel(m.role) }}</div>
              <div class="chat-text">{{ m.content }}</div>
            </div>
          </div>
          <div v-if="!chatMessages.length" class="chat-empty">填写患者 ID 与主诉后点击「开始问诊」，AI 将逐步追问并给出初步建议。</div>
        </div>
        <div class="chat-input-row">
          <w-input
            v-model="chatInput"
            placeholder="输入回答后回车发送"
            :disabled="!sessionNo"
            @keyup.enter="sendReply"
          />
          <w-button type="primary" :loading="chatLoading" :disabled="!sessionNo" @click="sendReply">发送</w-button>
        </div>
      </w-card>
    </template>

    <!-- Tab2 智能预测 -->
    <template v-if="activeTab === 'predict'">
      <w-card shadow="never" class="hospital-card">
        <h4 class="sub-title">B2-2 候诊时长预测</h4>
        <w-form inline class="query-form">
          <w-form-item label="排队人数">
            <w-input-number v-model="waitForm.queueLength" :min="1" style="width: 120px" />
          </w-form-item>
          <w-form-item label="均时(分)">
            <w-input-number v-model="waitForm.avgMinutes" :min="1" style="width: 120px" />
          </w-form-item>
          <w-form-item label="诊室数">
            <w-input-number v-model="waitForm.windows" :min="1" style="width: 110px" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :loading="predictLoading" @click="runWaitPredict">预测</w-button>
          </w-form-item>
        </w-form>
        <w-alert v-if="waitResult" type="success" :closable="false" class="result-alert">
          预计候诊 <b>{{ waitResult.estimateMinutes }}</b> 分钟（区间 {{ waitResult.rangeLow }} ~ {{ waitResult.rangeHigh }} 分钟）
        </w-alert>

        <h4 class="sub-title">B2-3 未来 3 天门诊量预测</h4>
        <w-form inline class="query-form">
          <w-form-item label="近 7 天门诊量">
            <w-input v-model="volumeInput" placeholder="逗号分隔，如 120,135,128,…" style="width: 320px" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :loading="predictLoading" @click="runVolumePredict">预测</w-button>
          </w-form-item>
        </w-form>
        <w-alert v-if="volumeResult" type="success" :closable="false" class="result-alert">
          移动均值 <b>{{ volumeResult.movingAverage }}</b>，日趋势 <b>{{ volumeResult.dailyTrend }}</b>，
          未来 3 天预测：<b>{{ (Array.isArray(volumeResult.forecastNext3Days) ? volumeResult.forecastNext3Days : []).join(' / ') }}</b>
        </w-alert>
      </w-card>
    </template>

    <!-- Tab3 AI 医助 -->
    <template v-if="activeTab === 'assist'">
      <w-card shadow="never" class="hospital-card">
        <h4 class="sub-title">B2-4 AI 用药推荐</h4>
        <w-form inline class="query-form">
          <w-form-item label="诊断">
            <w-input v-model="drugForm.diagnosis" placeholder="如 高血压2级 / 急性上呼吸道感染" style="width: 260px" />
          </w-form-item>
          <w-form-item label="过敏史">
            <w-input v-model="drugForm.allergy" placeholder="如 青霉素" style="width: 160px" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :loading="assistLoading" @click="runDrug">推荐</w-button>
          </w-form-item>
        </w-form>
        <w-alert v-if="drugResult" type="warning" :closable="false" class="result-alert">
          <div v-if="drugResult && Array.isArray(drugResult.recommended) && drugResult.recommended.length">
            命中关键词「{{ drugResult.matchedKeyword }}」，推荐：
            <ul class="drug-list">
              <li v-for="(d, i) in drugResult.recommended" :key="i">{{ d }}</li>
            </ul>
          </div>
          <div v-else>未命中规则，请由医生人工评估</div>
          <div class="disclaimer">{{ drugResult.disclaimer }}</div>
        </w-alert>

        <h4 class="sub-title">B2-5 报告摘要生成</h4>
        <w-form inline class="query-form">
          <w-form-item label="标题">
            <w-input v-model="summaryForm.title" placeholder="如 门诊病历" style="width: 150px" />
          </w-form-item>
          <w-form-item label="主诉">
            <w-input v-model="summaryForm.chiefComplaint" placeholder="如 反复咳嗽咳痰一周" style="width: 240px" />
          </w-form-item>
          <w-form-item label="诊断">
            <w-input v-model="summaryForm.diagnosis" placeholder="如 急性上呼吸道感染" style="width: 220px" />
          </w-form-item>
          <w-form-item>
            <w-button type="primary" :loading="assistLoading" @click="runSummary">生成摘要</w-button>
          </w-form-item>
        </w-form>
        <w-alert v-if="summaryResult" type="success" :closable="false" class="result-alert">
          {{ summaryResult.summary }}
          <div v-if="summaryResult && Array.isArray(summaryResult.keywords) && summaryResult.keywords.length" class="kw-row">
            关键词：<w-tag v-for="(k, i) in summaryResult.keywords" :key="i" size="small" effect="plain" class="kw-tag">{{ k }}</w-tag>
          </div>
        </w-alert>
      </w-card>
    </template>
  </div>
</template>

<style scoped>
.sub-title {
  margin: 12px 0 8px;
}
.result-alert {
  margin-bottom: 10px;
}
.chat-box {
  height: 320px;
  overflow-y: auto;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 14px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.chat-row {
  display: flex;
}
.chat-row.me {
  justify-content: flex-end;
}
.chat-bubble {
  max-width: 76%;
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 8px 12px;
}
.chat-row.ai .chat-bubble {
  background: #ecf5ff;
  border-color: #d4e7fb;
}
.chat-role {
  font-size: 12px;
  color: #98a3b3;
  margin-bottom: 2px;
}
.chat-text {
  font-size: 13px;
  line-height: 1.5;
}
.chat-empty {
  margin: auto;
  color: #98a3b3;
  font-size: 13px;
}
.chat-input-row {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.drug-list {
  margin: 6px 0 4px 18px;
}
.disclaimer {
  font-size: 12px;
  color: #98a3b3;
}
.kw-row {
  margin-top: 6px;
}
.kw-tag {
  margin-right: 6px;
}
</style>
