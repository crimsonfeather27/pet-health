<template>
  <section id="health-records">
    <h2>健康记录</h2>
    <div class="card-glow hr-toolbar">
      <div class="form-group hr-toolbar-field">
        <label>选择宠物</label>
        <select v-model="petId" @change="loadTrend">
          <option value="">请选择宠物</option>
          <option v-for="p in pets.pets" :key="p.id" :value="p.id">
            {{ [p.name, p.species, p.breed].filter(Boolean).join(' · ') }}
          </option>
        </select>
      </div>
      <div class="form-group hr-toolbar-field">
        <label>统计周期</label>
        <select v-model="period" @change="loadTrend">
          <option value="weekly">本周</option>
          <option value="monthly">本月</option>
        </select>
      </div>
      <button class="btn btn-secondary" @click="loadTrend">刷新</button>
      <button class="btn btn-primary" @click="openForm(null)">添加记录</button>

      <div class="hr-stats-grid">
        <div v-if="!stats" class="hr-stat-card">统计暂不可用</div>
        <template v-else>
          <div class="hr-stat-card">
            <span class="hr-stat-label">{{ petName }} · {{ periodText }}</span>
            <b>{{ stats.recordCount || 0 }}</b>
            <span class="hr-stat-sub">条记录</span>
          </div>
          <div v-if="stats.weightAvg != null" class="hr-stat-card">
            <span class="hr-stat-label">体重均值</span>
            <b>{{ stats.weightAvg }} <small>kg</small></b>
            <span class="hr-stat-sub">
              范围 {{ stats.weightMin }}~{{ stats.weightMax }} · {{ trendText(stats.weightTrend) }}
            </span>
          </div>
          <div v-if="stats.tempAvg != null" class="hr-stat-card">
            <span class="hr-stat-label">体温均值</span>
            <b>{{ stats.tempAvg }} <small>℃</small></b>
            <span class="hr-stat-sub">范围 {{ stats.tempMin }}~{{ stats.tempMax }}</span>
          </div>
          <div v-if="stats.recordTypes?.length" class="hr-stat-card">
            <span class="hr-stat-label">记录类型</span>
            <b>{{ stats.recordTypes.join(' / ') }}</b>
            <span class="hr-stat-sub">{{ stats.period === 'empty' ? '该周期内暂无记录' : '已聚合' }}</span>
          </div>
        </template>
      </div>
    </div>

    <!-- 指标筛选标签 -->
    <div v-if="allTypes.length" class="hr-chart-filters">
      <button
        v-for="t in allTypes"
        :key="t"
        class="hr-type-tag"
        :class="{ active: !deselectedTypes.has(t) }"
        @click="toggleType(t)"
      >
        {{ t }}<template v-if="typeUnits[t]"> ({{ typeUnits[t] }})</template>
      </button>
    </div>

    <!-- 趋势图 -->
    <div v-if="!petId" class="empty-hint">请先选择宠物</div>
    <div v-else-if="!allTypes.length" class="empty-hint">
      该周期内暂无数值型健康记录（体重、体温等）
    </div>
    <div v-else-if="!activeTypes.length" class="empty-hint">请至少选择一个指标进行展示</div>
    <AppChart v-else :option="chartOption" />

    <h3 class="hr-list-title">记录明细</h3>
    <div v-if="!petId" class="empty-hint">暂无记录</div>
    <div v-else-if="!records.length" class="empty-hint">暂无健康记录</div>
    <div v-else class="hr-records-list">
      <div
        v-for="r in records"
        :key="r.id"
        class="hr-record-card card-glow"
        @click="openDetail(r.id)"
      >
        <div class="hr-record-icon"><component :is="hrIcon(r.recordType)" :size="20" /></div>
        <div class="hr-record-body">
          <h4>
            {{ r.recordType || '记录' }}
            <span class="hr-record-value">{{ formatValue(r) }}</span>
          </h4>
          <p class="hr-record-time">{{ formatTime(r.recordedAt) }}</p>
          <p v-if="r.notes" class="hr-record-notes">{{ r.notes }}</p>
        </div>
        <div class="hr-record-actions" @click.stop>
          <button class="btn btn-tiny btn-secondary" @click="openForm(r.id)">编辑</button>
          <button class="btn btn-tiny btn-danger" @click="onDelete(r.id)">删除</button>
        </div>
      </div>
    </div>

    <!-- 记录表单 Modal -->
    <div v-if="formOpen" class="modal" @click.self="formOpen = false">
      <div class="modal-content">
        <button class="modal-close" @click="formOpen = false">✕</button>
        <h3>{{ editingId ? '编辑健康记录' : '添加健康记录' }}</h3>
        <div class="form-row">
          <div class="form-group" style="flex: 1">
            <label>宠物 <span style="color: var(--danger)">*</span></label>
            <select v-model="form.petId">
              <option v-for="p in pets.pets" :key="p.id" :value="p.id">
                {{ [p.name, p.species, p.breed].filter(Boolean).join(' · ') }}
              </option>
            </select>
          </div>
          <div class="form-group" style="flex: 1">
            <label>记录类型 <span style="color: var(--danger)">*</span></label>
            <select v-model="form.recordType" @change="onTypeChange">
              <option v-for="t in Object.keys(HR_TYPE_UNITS)" :key="t" :value="t">{{ t }}</option>
            </select>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group" style="flex: 1">
            <label>数值 <span style="color: var(--danger)">*</span></label>
            <input
              v-model="form.value"
              type="number"
              :step="currentTypeConf.step"
              :placeholder="currentTypeConf.placeholder"
            />
          </div>
          <div class="form-group" style="flex: 1">
            <label>单位</label>
            <input v-model="form.unit" type="text" maxlength="10" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group" style="flex: 1">
            <label>记录时间 <span style="color: var(--danger)">*</span></label>
            <input v-model="form.recordedAt" type="datetime-local" />
          </div>
        </div>
        <div class="form-group">
          <label>备注</label>
          <textarea
            v-model="form.notes"
            rows="2"
            maxlength="200"
            placeholder="可选，例如：饭后两小时测量（最多200字）"
          ></textarea>
        </div>
        <div class="modal-actions">
          <button class="btn" @click="formOpen = false">取消</button>
          <button class="btn btn-primary" :disabled="submitting" @click="submit">
            {{ editingId ? '保存修改' : '保存记录' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 记录详情 Modal -->
    <div v-if="detail" class="modal" @click.self="detail = null">
      <div class="modal-content">
        <button class="modal-close" @click="detail = null">✕</button>
        <h3>{{ detail.recordType || '记录' }}</h3>
        <p class="pet-meta">记录时间：{{ formatTime(detail.recordedAt) }}</p>
        <p class="pet-meta">数值：<b>{{ formatValue(detail) }}</b></p>
        <p v-if="detail.notes" class="pet-desc">{{ detail.notes }}</p>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="detail = null">关闭</button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { usePetsStore } from '@/stores/pets'
import { apiGet, apiPost, apiPut, apiDelete } from '@/api'
import { showToast } from '@/composables/useToast'
import AppChart from '@/components/AppChart.vue'
import {
  Weight as LucideWeight,
  Thermometer as LucideThermometer,
  Heart as LucideHeart,
  Utensils as LucideUtensils,
  Dumbbell as LucideDumbbell,
  Syringe as LucideSyringe,
  Worm as LucideWorm,
  Hospital as LucideHospital,
  HeartPulse as LucideHeartPulse,
  Activity as LucideActivity,
} from 'lucide-vue-next'

interface HealthRecord {
  id: string
  petId?: string
  recordType?: string
  value?: { value?: number; unit?: string }
  recordedAt?: string
  notes?: string
}
interface HealthStats {
  recordCount?: number
  weightAvg?: number | null
  weightMin?: number | null
  weightMax?: number | null
  weightTrend?: string
  tempAvg?: number | null
  tempMin?: number | null
  tempMax?: number | null
  recordTypes?: string[]
  period?: string
}

const HR_TYPE_UNITS: Record<string, { unit: string; step: string; placeholder: string }> = {
  体重: { unit: 'kg', step: '0.1', placeholder: '例如：4.5' },
  体温: { unit: '℃', step: '0.1', placeholder: '例如：38.5' },
  心率: { unit: '次/分', step: '1', placeholder: '例如：120' },
  饮食: { unit: 'g', step: '1', placeholder: '例如：150' },
  排便: { unit: '次', step: '1', placeholder: '例如：2' },
  运动: { unit: '分钟', step: '1', placeholder: '例如：30' },
  其他: { unit: '', step: '0.01', placeholder: '自定义数值' },
}

const user = useUserStore()
const pets = usePetsStore()

const petId = ref('')
const period = ref<'weekly' | 'monthly'>('weekly')
const stats = ref<HealthStats | null>(null)
const records = ref<HealthRecord[]>([])
const deselectedTypes = ref<Set<string>>(new Set())

const petName = computed(() => {
  const p = pets.pets.find((x) => x.id === petId.value)
  return p?.name || '宠物'
})
const periodText = computed(() => (period.value === 'monthly' ? '本月' : '本周'))

function trendText(t?: string) {
  return t === 'up' ? '↑ 上升' : t === 'down' ? '↓ 下降' : t === 'stable' ? '→ 平稳' : '-'
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}
function toLocalInput(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatTime(t?: string) {
  return t ? new Date(t).toLocaleString('zh-CN') : '-'
}
function formatValue(r: HealthRecord) {
  const v = r.value?.value
  const val = v != null ? v : r.value ? JSON.stringify(r.value) : '-'
  const unit = r.value?.unit ? ` ${r.value.unit}` : ''
  return `${val}${unit}`
}

function hrIcon(type?: string) {
  const t = (type || '').toString()
  if (t.includes('体重')) return LucideWeight
  if (t.includes('体温')) return LucideThermometer
  if (t.includes('心率') || t.includes('脉搏')) return LucideHeart
  if (t.includes('饮食') || t.includes('食欲')) return LucideUtensils
  if (t.includes('排便') || t.includes('排泄')) return LucideActivity
  if (t.includes('运动') || t.includes('活动')) return LucideDumbbell
  if (t.includes('疫苗')) return LucideSyringe
  if (t.includes('驱虫')) return LucideWorm
  if (t.includes('就诊')) return LucideHospital
  if (t.includes('体检')) return LucideHeartPulse
  return LucideActivity
}

// ---- 趋势图 ----
// 按周期过滤记录并按类型分组，提取 [ts, value]
const grouped = computed(() => {
  const rangeDays = period.value === 'monthly' ? 30 : 7
  const startTs = Date.now() - rangeDays * 24 * 3600 * 1000
  const map: Record<string, { unit: string; points: [number, number][] }> = {}
  records.value.forEach((r) => {
    const v = r.value?.value
    if (v == null || isNaN(Number(v)) || !r.recordedAt) return
    const ts = new Date(r.recordedAt).getTime()
    if (ts < startTs) return
    const type = r.recordType || '其他'
    const unit = r.value?.unit || ''
    ;(map[type] = map[type] || { unit, points: [] }).points.push([ts, Number(v)])
  })
  return map
})

const allTypes = computed(() => Object.keys(grouped.value))
const typeUnits = computed(() => {
  const u: Record<string, string> = {}
  Object.entries(grouped.value).forEach(([t, g]) => (u[t] = g.unit))
  return u
})
const activeTypes = computed(() =>
  allTypes.value.filter((t) => !deselectedTypes.value.has(t))
)

const chartOption = computed(() => {
  const COLORS = ['#3873B6', '#E6A23C', '#67C23A', '#8B5CF6', '#F56C6C', '#14B8A6']
  const types = activeTypes.value
  const yAxes: any[] = []
  const series: any[] = []
  types.forEach((type, i) => {
    const g = grouped.value[type]
    const points = [...g.points].sort((a, b) => a[0] - b[0])
    yAxes.push({
      type: 'value',
      scale: true,
      name: g.unit,
      nameTextStyle: { color: '#6DA1D8' },
      position: i === 0 ? 'left' : 'right',
      offset: i === 0 ? 0 : (i - 1) * 52,
      axisLine: { show: false },
      splitLine: { show: i === 0 },
    })
    series.push({
      name: g.unit ? `${type} (${g.unit})` : type,
      type: 'line',
      yAxisIndex: i,
      data: points,
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      itemStyle: { color: COLORS[i % COLORS.length] },
      lineStyle: { width: 2.5 },
      ...(types.length === 1 ? { areaStyle: { color: 'rgba(56,115,182,0.08)' } } : {}),
    })
  })
  return {
    title: { text: `${petName.value} · 健康指标趋势`, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: series.map((s) => s.name), top: 25 },
    grid: { left: 60, right: 45 + Math.max(0, types.length - 1) * 52, top: 70, bottom: 40 },
    xAxis: { type: 'time', axisLabel: { hideOverlap: true } },
    yAxis: yAxes,
    series,
  }
})

function toggleType(t: string) {
  const s = new Set(deselectedTypes.value)
  if (s.has(t)) s.delete(t)
  else s.add(t)
  deselectedTypes.value = s
}

// ---- 加载趋势 + 列表 ----
async function loadTrend() {
  if (!petId.value) {
    stats.value = null
    records.value = []
    deselectedTypes.value = new Set()
    return
  }
  try {
    stats.value = await apiGet<HealthStats>(
      `/api/health-records/pet/${petId.value}/trends?period=${period.value}`
    )
  } catch { stats.value = null }
  try {
    records.value = (await apiGet<HealthRecord[]>(`/api/health-records/pet/${petId.value}`)) || []
  } catch {
    records.value = []
  }
  // 清理已不存在的类型取消标记
  const valid = new Set(allTypes.value)
  const s = new Set<string>()
  deselectedTypes.value.forEach((t) => {
    if (valid.has(t)) s.add(t)
  })
  deselectedTypes.value = s
}

// ---- 表单 ----
const formOpen = ref(false)
const editingId = ref('')
const submitting = ref(false)
const form = reactive({
  petId: '',
  recordType: '体重',
  value: '',
  unit: 'kg',
  recordedAt: '',
  notes: '',
})

const currentTypeConf = computed(() => HR_TYPE_UNITS[form.recordType] || HR_TYPE_UNITS['其他'])

function onTypeChange() {
  const conf = HR_TYPE_UNITS[form.recordType]
  if (conf) {
    form.unit = conf.unit
  }
}

async function openForm(recordId: string | null) {
  if (!user.isLoggedIn) {
    showToast('请先登录后添加记录', 'error')
    return
  }
  await pets.loadPets()
  if (!pets.pets.length) {
    showToast('请先在宠物档案页添加宠物', 'error')
    return
  }

  let record: HealthRecord | null = null
  if (recordId) {
    try {
      record = await apiGet<HealthRecord>(`/api/health-records/${recordId}`)
    } catch (e: any) {
      showToast('加载记录失败：' + e.message, 'error')
      return
    }
    if (!record) {
      showToast('健康记录不存在', 'error')
      return
    }
  }

  editingId.value = record?.id || ''
  form.petId = record?.petId || petId.value || pets.pets[0]?.id || ''
  form.recordType = record?.recordType || '体重'
  form.value =
    record?.value?.value != null ? String(record.value.value) : ''
  form.unit =
    record?.value?.unit ?? (HR_TYPE_UNITS[form.recordType]?.unit ?? 'kg')
  form.notes = record?.notes || ''
  form.recordedAt = record?.recordedAt
    ? toLocalInput(new Date(record.recordedAt))
    : toLocalInput(new Date())

  formOpen.value = true
}

async function submit() {
  if (!form.petId) {
    showToast('请选择宠物', 'error')
    return
  }
  if (!form.recordType) {
    showToast('请选择记录类型', 'error')
    return
  }
  if (form.value === '' || isNaN(Number(form.value))) {
    showToast('请填写有效数值', 'error')
    return
  }
  if (!form.recordedAt) {
    showToast('请选择记录时间', 'error')
    return
  }

  const payload: any = {
    petId: form.petId,
    ownerId: user.currentUser?.id,
    recordType: form.recordType,
    value: { value: Number(form.value), ...(form.unit ? { unit: form.unit } : {}) },
    recordedAt: form.recordedAt,
    notes: form.notes.trim() || null,
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await apiPut(`/api/health-records/${editingId.value}`, payload)
      showToast('健康记录已更新')
    } else {
      await apiPost('/api/health-records', payload)
      showToast('健康记录已添加')
    }
    formOpen.value = false
    petId.value = form.petId
    await loadTrend()
  } catch (e: any) {
    showToast((editingId.value ? '更新失败：' : '添加失败：') + (e.message || '未知错误'), 'error')
  } finally {
    submitting.value = false
  }
}

async function onDelete(recordId: string) {
  if (!recordId) return
  if (!confirm('确认删除这条健康记录？删除后不可恢复。')) return
  try {
    await apiDelete(`/api/health-records/${recordId}`)
    showToast('健康记录已删除')
    detail.value = null
    await loadTrend()
  } catch (e: any) {
    showToast('删除失败：' + (e.message || '未知错误'), 'error')
  }
}

// ---- 详情 ----
const detail = ref<HealthRecord | null>(null)

async function openDetail(recordId: string) {
  try {
    detail.value = await apiGet<HealthRecord>(`/api/health-records/${recordId}`)
  } catch (e: any) {
    showToast('加载记录失败：' + e.message, 'error')
  }
}

onMounted(async () => {
  await pets.loadPets()
  if (pets.pets.length) {
    petId.value = pets.pets[0].id
    await loadTrend()
  }
})
</script>

<style scoped>
.hr-toolbar {
  margin-bottom: 1rem;
  display: flex;
  gap: 0.5rem;
  align-items: flex-end;
  flex-wrap: wrap;
}
.hr-toolbar-field {
  margin: 0;
}
.hr-stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.6rem;
  width: 100%;
  margin-top: 0.85rem;
}
.hr-stat-card {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.6rem 0.85rem;
  background: var(--bg-alt, rgba(0, 0, 0, 0.02));
  border-radius: 10px;
  border: 1px solid var(--border);
}
.hr-stat-label {
  font-size: 0.75rem;
  color: var(--text-light);
}
.hr-stat-card b {
  font-size: 1.1rem;
}
.hr-stat-sub {
  font-size: 0.72rem;
  color: var(--text-light);
}
.hr-chart-filters {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin-bottom: 0.6rem;
}
.hr-type-tag {
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-light);
  padding: 0.25rem 0.6rem;
  border-radius: 14px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s;
}
.hr-type-tag.active {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(56, 115, 182, 0.1);
}
.hr-list-title {
  margin-top: 1.5rem;
}
.hr-records-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.hr-record-card {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.75rem 1rem;
  cursor: pointer;
}
.hr-record-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}
.hr-record-body {
  flex: 1;
  min-width: 0;
}
.hr-record-body h4 {
  margin: 0 0 0.2rem;
  display: flex;
  gap: 0.5rem;
  align-items: baseline;
}
.hr-record-value {
  color: var(--accent);
  font-weight: 600;
}
.hr-record-time {
  color: var(--text-light);
  font-size: 0.8rem;
  margin: 0;
}
.hr-record-notes {
  color: var(--text-light);
  font-size: 0.82rem;
  margin: 0.2rem 0 0;
}
.hr-record-actions {
  display: flex;
  gap: 0.4rem;
  flex-shrink: 0;
}
.empty-hint {
  color: var(--text-light);
  padding: 1rem 0;
}
.btn-tiny {
  font-size: 0.78rem;
  padding: 0.25rem 0.55rem;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: transparent;
  cursor: pointer;
}
.btn-tiny.btn-primary {
  border-color: var(--accent);
  color: var(--accent);
}
.btn-tiny.btn-danger {
  border-color: var(--danger);
  color: var(--danger);
}
</style>
