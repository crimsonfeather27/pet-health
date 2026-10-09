<template>
  <section id="nutrition">
    <div class="section-header-row">
      <h2>营养助手</h2>
    </div>

    <div class="card-glow" style="margin-bottom: 1rem">
      <div class="form-group" style="margin-bottom: 0">
        <label>选择宠物</label>
        <select v-model="selectedPetId" @change="loadReport">
          <option v-if="!pets.pets.length" value="">（请先在宠物档案页添加宠物）</option>
          <option v-else value="">请选择宠物</option>
          <option v-for="p in pets.pets" :key="p.id" :value="p.id">
            {{ [p.name, p.species, p.breed].filter(Boolean).join(' · ') }}
          </option>
        </select>
      </div>
    </div>

    <div v-if="!selectedPetId" class="empty-hint">
      请选择宠物，系统将根据档案与体重记录自动计算每日营养需求
    </div>
    <div v-else-if="loading" class="empty-hint">正在计算能量需求…</div>
    <div v-else-if="error" class="empty-hint">营养报告加载失败：{{ error }}</div>
    <div v-else-if="report" class="card-glow nutrition-report">
      <!-- 无计算结果 -->
      <template v-if="!report.calculation">
        <h3>{{ report.pet?.name || '宠物' }}</h3>
        <p class="nutrition-meta">
          {{ report.pet?.species || '' }}{{ report.pet?.breed ? ' · ' + report.pet.breed : '' }} ·
          {{ ageText }} · {{ neuteredText }}
        </p>
        <p v-if="report.weight" class="nutrition-meta">
          最新体重 <b>{{ report.weight.value }} {{ report.weight.unit || 'kg' }}</b>
          <span v-if="report.weight.recordedAt">
            （{{ new Date(report.weight.recordedAt).toLocaleDateString('zh-CN') }}）
          </span>
        </p>
        <p class="empty-hint" style="margin-top: 0.75rem">
          {{ report.notice || '暂无法生成营养计算' }}
        </p>
      </template>

      <!-- 有计算结果 -->
      <template v-else>
        <div class="nutrition-head">
          <h3>{{ report.pet?.name || '宠物' }} · 每日营养需求</h3>
          <p class="nutrition-meta">
            {{ report.pet?.species || '' }}{{ report.pet?.breed ? ' · ' + report.pet.breed : '' }} ·
            {{ ageText }} · {{ neuteredText }}
          </p>
          <p v-if="report.weight" class="nutrition-meta">
            最新体重 <b>{{ report.weight.value }} {{ report.weight.unit || 'kg' }}</b>
            <span v-if="report.weight.recordedAt">
              （{{ new Date(report.weight.recordedAt).toLocaleDateString('zh-CN') }}）
            </span>
          </p>
        </div>

        <div class="nutrition-stats">
          <div class="nutrition-stat">
            <span>静息能量 RER</span>
            <b>{{ Math.round(calc.rer) }} <small>kcal/天</small></b>
          </div>
          <div class="nutrition-stat">
            <span>阶段系数</span>
            <b>{{ calc.merFactor }}<small>{{ calc.stageLabel }}</small></b>
          </div>
          <div class="nutrition-stat">
            <span>每日能量 MER</span>
            <b>{{ Math.round(calc.mer) }} <small>kcal/天</small></b>
          </div>
        </div>

        <div class="form-group">
          <label>主粮热量密度</label>
          <select v-model.number="kcalDensity" @change="updateFeedingGrams">
            <option :value="340">340 kcal/100g</option>
            <option :value="360">360 kcal/100g</option>
            <option :value="380">380 kcal/100g（常见干粮）</option>
            <option :value="400">400 kcal/100g</option>
            <option :value="420">420 kcal/100g</option>
          </select>
        </div>

        <div class="nutrition-result-row">
          <span>每日建议喂食量</span>
          <b class="nutrition-grams">{{ feedingGrams.toFixed(1) }} g/天</b>
        </div>

        <div class="form-group">
          <label>体况评分 BCS（1-9）</label>
          <select v-model="bcs" @change="loadReport">
            <option value="">未选择（不调整）</option>
            <option v-for="n in 9" :key="n" :value="n">
              {{ n }}{{ n >= 7 ? '（超重倾向）' : n <= 4 ? '（偏瘦）' : '（正常）' }}
            </option>
          </select>
          <p v-if="calc.bcsTargetKcal != null" class="nutrition-tip">
            体重管理目标热量：约 <b>{{ Math.round(calc.bcsTargetKcal) }} kcal/天</b>
            {{
              (calc.bcs ?? 0) >= 7
                ? '（当前偏重，建议渐进减量并咨询兽医）'
                : '（当前偏瘦，建议适量增量）'
            }}
          </p>
        </div>

        <div class="nutrition-chart">
          <template v-if="report.trend && report.trend.length">
            <AppChart :option="trendOption" />
          </template>
          <p v-else class="nutrition-tip">暂无体重历史记录，记录后即可展示体重趋势</p>
        </div>

        <p v-if="report.notice" class="nutrition-tip">{{ report.notice }}</p>
        <p class="nutrition-disclaimer">
          参考 NRC 2006 / WSAVA 通用估算标准，实际需求因个体代谢与活动量而异，请以兽医建议为准。
        </p>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { usePetsStore } from '@/stores/pets'
import { apiGet } from '@/api'
import AppChart from '@/components/AppChart.vue'

const pets = usePetsStore()

interface NutritionReport {
  pet?: any
  weight?: { value: number; unit?: string; recordedAt?: string }
  calculation?: {
    rer: number
    mer: number
    merFactor: number
    stageLabel: string
    bcs?: number
    bcsTargetKcal?: number | null
  }
  trend?: { date: string; value: number }[]
  notice?: string
}

const selectedPetId = ref('')
const bcs = ref<string | number>('')
const report = ref<NutritionReport | null>(null)
const loading = ref(false)
const error = ref('')
const kcalDensity = ref(380)

const calc = computed(() => report.value?.calculation!)

const ageText = computed(() => {
  const m = report.value?.pet?.ageMonths
  if (m == null) return '年龄未知'
  if (m >= 12) {
    const y = Math.floor(m / 12)
    const mo = m % 12
    return mo ? `${y} 岁 ${mo} 个月` : `${y} 岁`
  }
  return `${m} 个月`
})

const neuteredText = computed(() => {
  const n = report.value?.pet?.neutered
  return n == null ? '未设置绝育' : n ? '已绝育' : '未绝育'
})

const feedingGrams = ref(0)

function updateFeedingGrams() {
  const mer = calc.value?.mer || 0
  const kcal = kcalDensity.value
  feedingGrams.value = kcal > 0 && mer > 0 ? Math.round((mer / (kcal / 100)) * 10) / 10 : 0
}

const trendOption = computed(() => {
  const trend = report.value?.trend || []
  const petName = report.value?.pet?.name || ''
  return {
    title: { text: `${petName} · 体重趋势`, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 45, bottom: 35 },
    xAxis: { type: 'category', data: trend.map((t) => t.date), axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', name: 'kg', scale: true },
    series: [
      {
        name: '体重',
        type: 'line',
        smooth: true,
        data: trend.map((t) => t.value),
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color: '#3873B6' },
        lineStyle: { width: 2.5 },
        areaStyle: { color: 'rgba(56,115,182,0.08)' },
      },
    ],
  }
})

async function loadReport() {
  if (!selectedPetId.value) {
    report.value = null
    return
  }
  loading.value = true
  error.value = ''
  const url = bcs.value
    ? `/api/nutrition/${selectedPetId.value}?bcs=${encodeURIComponent(String(bcs.value))}`
    : `/api/nutrition/${selectedPetId.value}`
  try {
    report.value = await apiGet<NutritionReport>(url)
    // 初始喂食量
    const mer = report.value?.calculation?.mer || 0
    feedingGrams.value = Math.round((mer / (380 / 100)) * 10) / 10
    kcalDensity.value = 380
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await pets.loadPets()
})
</script>

<style scoped>
.empty-hint {
  color: var(--text-light);
  padding: 1rem 0;
}
</style>
