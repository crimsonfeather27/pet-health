<template>
  <section id="home">
    <h2 style="display: none">首页</h2>

    <!-- Hero 品牌区 -->
    <div class="home-hero">
      <div class="hero-content">
        <div class="hero-badge">
          <span class="dot"></span>
          宠物健康管家 · 一站式平台
        </div>
        <h2 class="hero-logo">Pet<span>Health</span></h2>
        <p class="hero-tagline">— 让每一只毛孩子都被好好照顾 —</p>
        <h3 class="hero-title">
          All your pet's health,<br /><span class="highlight">in one place.</span>
        </h3>
        <p class="hero-desc">记录健康数据、按时提醒就医、AI 辅助诊断 —— 用科技让养宠更轻松、更安心。</p>
        <div class="hero-actions">
          <RouterLink class="btn btn-primary" to="/pets">开始管理宠物健康</RouterLink>
          <RouterLink class="btn btn-secondary" to="/ai-diagnosis">了解 AI 助手 →</RouterLink>
        </div>
      </div>
    </div>

    <!-- 核心功能入口 -->
    <div class="home-features">
      <div class="home-section-head">
        <p class="section-label">核心功能</p>
        <h3 class="section-title">全方位守护 <span class="serif">毛孩子</span> 的健康</h3>
        <p class="section-desc">六大模块，覆盖宠物健康管理的每一个环节</p>
      </div>
      <div class="features-grid">
        <RouterLink v-for="f in features" :key="f.to" class="feature-card" :to="f.to">
          <h4>{{ f.title }}</h4>
          <p>{{ f.desc }}</p>
        </RouterLink>
      </div>
    </div>

    <!-- 社区动态 + 到期提醒 -->
    <div class="home-story">
      <div class="home-story-inner">
        <div class="home-story-text">
          <p class="section-label">社区动态</p>
          <h3 class="section-title">热门 <span class="serif">讨论</span></h3>
          <p class="section-desc">看看其他宠主都在聊什么，分享你的养宠心得。</p>
          <RouterLink class="btn btn-secondary" to="/community">进入社区 →</RouterLink>
        </div>
        <div class="home-story-visual">
          <div class="home-panel">
            <h4>热门社区</h4>
            <div>
              <div
                v-for="p in hotPosts"
                :key="p.id"
                class="hot-post-item"
                @click="goPost(p.id)"
              >
                <span class="hot-title">{{ p.title }}</span>
                <span class="hot-meta">{{ p.replyCount || 0 }}回复</span>
              </div>
              <p v-if="!hotPosts.length" class="home-panel-empty">暂无热门帖子</p>
            </div>
          </div>
        </div>
      </div>

      <div class="home-story-inner">
        <div class="home-story-visual">
          <div class="home-panel">
            <h4>即将到期提醒</h4>
            <div>
              <div v-for="r in dueReminders" :key="r.id" class="reminder-item">
                <span class="reminder-icon"><Bell /></span>
                <span>{{ r.title }} — 还有 {{ r.daysLeft }} 天</span>
              </div>
              <p v-if="!dueReminders.length" class="home-panel-empty">暂无到期提醒</p>
            </div>
          </div>
        </div>
        <div class="home-story-text">
          <p class="section-label">健康守护</p>
          <h3 class="section-title">从不 <span class="serif">错过</span> 重要节点</h3>
          <p class="section-desc">疫苗、驱虫、体检即将到期？PetHealth 提前为您安排。</p>
          <RouterLink class="btn btn-secondary" to="/reminders">查看提醒 →</RouterLink>
        </div>
      </div>
    </div>

    <!-- CTA -->
    <div class="home-cta">
      <h3>和 <span>PetHealth</span> 一起，守护毛孩子的健康</h3>
      <p>立即加入宠主的行列，用科技让养宠更轻松、更安心。</p>
      <RouterLink class="btn btn-primary" to="/pets">开始使用</RouterLink>
    </div>

    <!-- 页脚 -->
    <div class="home-footer">
      <span class="home-footer-brand">Pet<span>Health</span> · 宠物健康管家</span>
      <span>© 2026 PetHealth · 用心守护每一只毛孩子</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from 'lucide-vue-next'
import { apiGet } from '@/api'

const router = useRouter()

const features = [
  { to: '/pets', title: '宠物档案', desc: '疫苗、驱虫、体检、就医史一站式记录' },
  { to: '/health-records', title: '健康追踪', desc: '体重、体温等健康数据趋势可视化' },
  { to: '/ai-diagnosis', title: 'AI 健康助手', desc: '描述症状，获得 AI 初步诊断建议' },
  { to: '/reminders', title: '智能提醒', desc: '疫苗、驱虫到期自动提醒' },
  { to: '/nutrition', title: '营养助手', desc: '科学公式计算每日热量与喂食量' },
  { to: '/community', title: '宠物社区', desc: '与千万宠主交流养宠经验' },
]

interface HotPost {
  id: string
  title: string
  replyCount?: number
}
interface DueReminder {
  id: string
  title: string
  daysLeft: number
}

const hotPosts = ref<HotPost[]>([])
const dueReminders = ref<DueReminder[]>([])

function goPost(postId: string) {
  // 跳转到社区并打开帖子详情（由 CommunityView 承载）
  router.push({ path: '/community', query: { postId } })
}

onMounted(async () => {
  try {
    hotPosts.value = (await apiGet<HotPost[]>('/api/posts/hot?limit=5')) || []
  } catch (e) {
    /* 后端未启动时显示占位 */
  }
  try {
    dueReminders.value = (await apiGet<DueReminder[]>('/api/reminders/due?days=7')) || []
  } catch (e) {
    /* reminders 微服务未启动时忽略 */
  }
})
</script>

<style scoped>
.home-panel-empty {
  color: var(--text-light);
  font-size: 0.85rem;
  padding: 0.5rem 0;
}
</style>
