<template>
  <section id="ai-diagnosis">
    <h2>AI 健康助手</h2>
    <div class="card-glow">
      <div class="form-group">
        <label>选择宠物</label>
        <select v-model="petId">
          <option value="">加载中...</option>
          <option v-for="p in pets.pets" :key="p.id" :value="p.id">
            {{ [p.name, p.species, p.breed].filter(Boolean).join(' · ') }}
          </option>
        </select>
      </div>
      <div class="form-group">
        <label>描述症状</label>
        <textarea
          v-model="symptoms"
          rows="4"
          placeholder="例如：今天没精神，不爱动，也不太吃东西，还偶尔打喷嚏"
        ></textarea>
      </div>
      <div class="form-group">
        <label>持续时间</label>
        <select v-model="duration">
          <option>半天</option>
          <option>1天</option>
          <option>2-3天</option>
          <option>一周以上</option>
        </select>
      </div>
      <button class="btn btn-primary" :disabled="loading" @click="submitDiagnosis(false)">
        开始 AI 诊断
      </button>
      <button class="btn btn-secondary" style="margin-left: 0.5rem" @click="openReportModal">
        生成健康报告
      </button>
      <div class="llm-key-row">
        DeepSeek Key：<span>{{ maskedKey }}</span>
        <button class="btn btn-secondary btn-tiny" style="margin-left: 0.5rem" @click="openKeyModal('manage')">
          设置 API Key
        </button>
      </div>
      <div ref="resultBox" class="ai-result">
        <p v-if="loading" class="loading">
          AI 正在分析中，模型生成约需 5~30 秒，请勿重复点击或关闭页面...
        </p>
        <template v-else-if="result">
          <div class="ai-result-card">
            <template v-if="result.isRule">
              <h4>可能原因：</h4>
              <ul>
                <li v-for="(c, i) in result.causes" :key="i">
                  <b>{{ c.name }}</b>（概率：{{ c.probability ?? '-' }}）— {{ c.description || '' }}
                </li>
              </ul>
              <h4>建议：</h4>
              <ol>
                <li v-for="(s, i) in result.suggestions" :key="i">{{ s }}</li>
              </ol>
            </template>
            <template v-else>
              <h4>AI 分析结果：</h4>
              <div class="ai-text-block">{{ result.causesText }}</div>
              <p v-if="result.suggestionsText" class="ai-text-sub">{{ result.suggestionsText }}</p>
            </template>
            <template v-if="result.redFlags.length">
              <h4>危险信号：</h4>
              <ul>
                <li v-for="(r, i) in result.redFlags" :key="i" class="red-flag">{{ r }}</li>
              </ul>
            </template>
            <p class="disclaimer">{{ result.disclaimer || '本建议仅供参考，不能替代兽医诊断' }}</p>
          </div>
        </template>
      </div>
    </div>

    <!-- API Key 设置 Modal -->
    <div v-if="keyModalOpen" class="modal" @click.self="keyModalOpen = false">
      <div class="modal-content">
        <button class="modal-close" @click="keyModalOpen = false">✕</button>
        <h3>设置 DeepSeek API Key</h3>
        <p class="modal-hint key-hint">
          Key 仅保存在当前浏览器（localStorage），<b>不会写入服务器或代码仓库</b>；
          诊断时经本机后端转发调用 DeepSeek，服务端不记录、不存储。留空则使用内置规则引擎。
        </p>
        <div class="form-group">
          <label>API Key</label>
          <input v-model="keyInput" type="password" placeholder="sk-..." />
        </div>
        <button class="btn btn-primary full-width" @click="saveLlmKey">
          {{ keyModalMode === 'diagnose' ? '保存并开始诊断' : '保存' }}
        </button>
        <div class="modal-actions key-actions">
          <button class="btn btn-secondary btn-tiny" @click="clearLlmKey">清除已保存的 Key</button>
          <button
            v-if="keyModalMode === 'diagnose'"
            class="btn btn-secondary btn-tiny"
            @click="skipLlmKey"
          >
            跳过，用内置规则
          </button>
        </div>
        <p class="modal-hint">申请地址：platform.deepseek.com → API Keys</p>
      </div>
    </div>

    <!-- 健康报告 Modal -->
    <div v-if="reportOpen" class="modal" @click.self="reportOpen = false">
      <div class="modal-content">
        <button class="modal-close" @click="reportOpen = false">✕</button>
        <h3>生成健康报告</h3>
        <div class="form-row">
          <div class="form-group" style="flex: 1">
            <label>选择宠物 <span style="color: var(--danger)">*</span></label>
            <select v-model="reportPetId">
              <option value="">请选择</option>
              <option v-for="p in pets.pets" :key="p.id" :value="p.id">
                {{ [p.name, p.species, p.breed].filter(Boolean).join(' · ') }}
              </option>
            </select>
          </div>
          <div class="form-group" style="flex: 1">
            <label>统计周期</label>
            <select v-model="reportPeriod">
              <option value="weekly">本周</option>
              <option value="monthly">本月</option>
            </select>
          </div>
        </div>
        <div class="modal-actions" style="margin-bottom: 1rem">
          <button class="btn btn-primary" :disabled="reportLoading" @click="generateReport">
            生成报告
          </button>
        </div>
        <div>
          <p v-if="reportLoading" class="loading">报告生成中...</p>
          <div v-else-if="reportText" class="report-card">
            <pre class="report-text">{{ reportText }}</pre>
            <div class="modal-actions">
              <button class="btn btn-tiny btn-secondary" @click="copyReport">复制报告</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { usePetsStore } from '@/stores/pets'
import { apiGet, apiPost } from '@/api'
import { showToast } from '@/composables/useToast'

const LLM_KEY_STORAGE = 'pethealth:llm-api-key'
const LLM_KEY_HEADER = 'X-LLM-Api-Key'

const user = useUserStore()
const pets = usePetsStore()

const petId = ref('')
const symptoms = ref('')
const duration = ref('1天')
const loading = ref(false)
const result = ref<any>(null)

// ---- LLM Key 管理 ----
const keyModalOpen = ref(false)
const keyModalMode = ref<'manage' | 'diagnose'>('manage')
const keyInput = ref('')

function getLlmKey() {
  return (localStorage.getItem(LLM_KEY_STORAGE) || '').trim()
}
function setLlmKey(k: string) {
  const v = (k || '').trim()
  if (v) localStorage.setItem(LLM_KEY_STORAGE, v)
  else localStorage.removeItem(LLM_KEY_STORAGE)
}
const maskedKey = computed(() => {
  const k = getLlmKey()
  if (!k) return '未配置'
  if (k.length < 8) return '****'
  return (k.startsWith('sk-') ? 'sk-' : '') + '****' + k.slice(-4)
})

function openKeyModal(mode: 'manage' | 'diagnose') {
  keyModalMode.value = mode
  keyInput.value = getLlmKey()
  keyModalOpen.value = true
}

function saveLlmKey() {
  setLlmKey(keyInput.value)
  const mode = keyModalMode.value
  keyModalOpen.value = false
  showToast(keyInput.value ? 'API Key 已保存到本浏览器' : '已清空 API Key')
  if (mode === 'diagnose') submitDiagnosis(true)
}

function skipLlmKey() {
  keyModalOpen.value = false
  if (keyModalMode.value === 'diagnose') submitDiagnosis(true)
}

function clearLlmKey() {
  setLlmKey('')
  keyInput.value = ''
  showToast('已清除保存的 Key')
}

// ---- 剥离 Markdown（LLM 输出安全网） ----
function stripMarkdown(text: string): string {
  if (!text) return ''
  return text
    .replace(/```[^\n]*\n?/g, '')
    .replace(/^\s{0,3}#{1,6}\s*/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s{0,3}>\s?/gm, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/~~([^~]+)~~/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1$2')
    .replace(/[ \t]+$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

// ---- 诊断 ----
async function submitDiagnosis(skipKeyCheck: boolean) {
  if (!symptoms.value.trim()) {
    showToast('请描述症状哦', 'error')
    return
  }
  // 首次使用且未配置 Key：弹窗引导
  if (!skipKeyCheck && !getLlmKey()) {
    openKeyModal('diagnose')
    return
  }

  loading.value = true
  result.value = null

  const pet = pets.pets.find((p) => p.id === petId.value) || ({} as any)
  const species = pet.species || 'CAT'
  const breed = pet.breed || '未知'
  const ageMonths = pet.ageMonths != null ? pet.ageMonths : 12

  const headers: Record<string, string> = {}
  const llmKey = getLlmKey()
  if (llmKey) headers[LLM_KEY_HEADER] = llmKey

  try {
    const data = await apiPost('/api/ai-diagnosis', {
      petId: petId.value,
      species,
      breed,
      ageMonths,
      symptoms: symptoms.value,
      duration: duration.value,
    }, headers)
    const parsed = typeof data === 'string' ? JSON.parse(data) : data
    const causes = parsed.possibleCauses
    const redFlags = parsed.redFlags || parsed.dangerSignals
    const redArr = Array.isArray(redFlags) ? redFlags : redFlags ? [redFlags] : []
    const suggArr = Array.isArray(parsed.suggestions)
      ? parsed.suggestions
      : parsed.suggestions
        ? [parsed.suggestions]
        : []
    const isRule = Array.isArray(causes)

    result.value = {
      isRule,
      causes: isRule ? causes : [],
      causesText: isRule ? '' : stripMarkdown(causes || 'AI 未返回内容'),
      suggestions: isRule ? suggArr : [],
      suggestionsText: isRule ? '' : (suggArr.length ? stripMarkdown(suggArr.join('；')) : ''),
      redFlags: redArr.map((s: string) => stripMarkdown(s)),
      disclaimer: parsed.disclaimer,
    }
  } catch (e: any) {
    showToast('AI 调用失败：' + e.message, 'error')
  } finally {
    loading.value = false
  }
}

// ---- 健康报告 ----
const reportOpen = ref(false)
const reportPetId = ref('')
const reportPeriod = ref('weekly')
const reportLoading = ref(false)
const reportText = ref('')

async function openReportModal() {
  if (!user.isLoggedIn) {
    showToast('请先登录后生成报告', 'error')
    return
  }
  await pets.loadPets()
  if (!pets.pets.length) {
    showToast('请先在宠物档案页添加宠物', 'error')
    return
  }
  reportPetId.value = petId.value || pets.pets[0]?.id || ''
  reportPeriod.value = 'weekly'
  reportText.value = ''
  reportOpen.value = true
}

async function generateReport() {
  if (!reportPetId.value) {
    showToast('请选择宠物', 'error')
    return
  }
  reportLoading.value = true
  reportText.value = ''
  try {
    const report = await apiGet(
      `/api/ai-diagnosis/report?ownerId=${user.currentUser?.id}&petId=${reportPetId.value}&period=${reportPeriod.value}`
    )
    reportText.value = (report || '').trim() || '该周期内暂无健康记录，无法生成报告。'
  } catch (e: any) {
    showToast('报告生成失败：' + e.message, 'error')
  } finally {
    reportLoading.value = false
  }
}

async function copyReport() {
  if (!reportText.value) return
  try {
    await navigator.clipboard.writeText(reportText.value)
    showToast('报告已复制到剪贴板')
  } catch {
    showToast('复制失败，请手动选择文本复制', 'error')
  }
}

onMounted(() => {
  pets.loadPets()
})
</script>

<style scoped>
.llm-key-row {
  margin-top: 0.6rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}
.ai-result {
  margin-top: 0.75rem;
}
.loading {
  color: var(--text-light);
  padding: 0.75rem 0;
}
.ai-result-card {
  background: var(--bg-alt, rgba(0, 0, 0, 0.02));
  border-radius: 10px;
  padding: 0.85rem 1rem;
}
.ai-result-card h4 {
  margin: 0.6rem 0 0.4rem;
}
.ai-result-card ul,
.ai-result-card ol {
  padding-left: 1.2rem;
  margin: 0.25rem 0;
}
.ai-text-block {
  white-space: pre-wrap;
  line-height: 1.7;
  word-break: break-word;
}
.ai-text-sub {
  color: var(--text-light);
  font-size: 0.9rem;
  margin-top: 0.5rem;
}
.red-flag {
  color: var(--danger);
}
.disclaimer {
  color: var(--text-light);
  font-size: 0.78rem;
  margin-top: 0.6rem;
  font-style: italic;
}
.key-hint {
  text-align: left;
  margin-bottom: 1rem;
}
.key-actions {
  justify-content: space-between;
  margin-top: 0.75rem;
}
.report-card {
  margin-top: 0.5rem;
}
.report-text {
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--bg-alt, rgba(0, 0, 0, 0.02));
  padding: 0.85rem 1rem;
  border-radius: 8px;
  max-height: 50vh;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(56, 115, 182, 0.25) transparent;
  scrollbar-gutter: stable;
  line-height: 1.6;
}
</style>
