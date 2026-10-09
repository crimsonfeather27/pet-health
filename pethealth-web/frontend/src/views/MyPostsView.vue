<template>
  <section id="my-posts">
    <div class="section-header-row">
      <h2>我的帖子</h2>
      <button class="btn btn-primary" @click="openNewPost">发布新帖</button>
    </div>

    <div v-if="!user.isLoggedIn" class="empty-hint">请先登录后查看我的帖子</div>
    <div v-else-if="loading" class="empty-hint">加载中…</div>
    <div v-else-if="!myPosts.length" class="empty-hint">
      还没有发布过帖子 — 点击右上角「发布新帖」创建
    </div>
    <div v-else class="my-posts-list">
      <div v-for="p in myPosts" :key="p.id" class="my-post-item">
        <div class="my-post-info">
          <span class="post-category">
            {{ categoryLabel(p.category) }}
          </span>
          <span class="my-post-title">{{ p.title }}</span>
        </div>
        <div class="my-post-meta">
          <span class="meta-stat" title="点赞">
            <LucideThumbsUp :size="14" /> {{ p.likeCount || 0 }}
          </span>
          <span class="meta-stat" title="回复">
            <LucideMessageCircle :size="14" /> {{ p.replyCount || 0 }}
          </span>
          <span class="meta-time">{{ formatTime(p.createdAt) }}</span>
          <button class="btn btn-tiny btn-secondary" @click="goToPost(p.id)">
            查看
          </button>
        </div>
      </div>
    </div>

    <PostFormModal
      v-if="formOpen"
      :post-id="formPostId"
      @close="closeForm"
      @submitted="onSubmitted"
    />
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { apiGet } from '@/api'
import { showToast } from '@/composables/useToast'
import PostFormModal from '@/components/PostFormModal.vue'
import { ThumbsUp as LucideThumbsUp, MessageCircle as LucideMessageCircle } from 'lucide-vue-next'

interface Post {
  id: string
  title: string
  category?: string
  likeCount?: number
  replyCount?: number
  viewCount?: number
  createdAt?: string
  authorId?: string
}

const user = useUserStore()
const router = useRouter()

const myPosts = ref<Post[]>([])
const loading = ref(true)

const CATEGORY_LABELS: Record<string, string> = {
  GENERAL: '综合讨论',
  HEALTH: '健康医疗',
  NUTRITION: '喂养营养',
  TRAINING: '训练行为',
  SHOW: '萌宠展示',
  QUESTION: '求助问答',
}

function categoryLabel(c?: string) {
  return CATEGORY_LABELS[String(c || 'GENERAL').toUpperCase()] || c || 'GENERAL'
}

function formatTime(t?: string) {
  if (!t) return ''
  return new Date(t).toLocaleDateString('zh-CN')
}

async function load() {
  if (!user.isLoggedIn) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const page = await apiGet<any>('/api/posts?page=0&size=50')
    const posts: Post[] = page.content || page || []
    const uid = user.currentUser?.id
    myPosts.value = posts.filter((p) => p.authorId === uid)
  } catch (e: any) {
    showToast('帖子加载失败：' + e.message, 'error')
  } finally {
    loading.value = false
  }
}

// ---- 帖子表单 ----
const formOpen = ref(false)
const formPostId = ref<string | null>(null)

function openNewPost() {
  if (!user.isLoggedIn) {
    showToast('请先登录后发帖', 'error')
    return
  }
  formPostId.value = null
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  formPostId.value = null
}

function onSubmitted() {
  closeForm()
  load()
}

// 跳转到社区并打开帖子详情
function goToPost(postId: string) {
  router.push({ path: '/community', query: { openPost: postId } })
}

onMounted(load)
</script>

<style scoped>
.empty-hint {
  color: var(--text-light);
  padding: 1rem 0;
}
.my-posts-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.my-post-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.85rem 1rem;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.my-post-item:hover {
  border-color: var(--accent);
  box-shadow: 0 4px 14px rgba(56, 115, 182, 0.1);
}
.my-post-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
  flex: 1;
}
.my-post-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.my-post-meta {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  color: var(--text-light);
  font-size: 0.85rem;
  white-space: nowrap;
}
.meta-stat {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}
.post-category {
  background: var(--tag-bg, rgba(56, 115, 182, 0.12));
  color: var(--accent);
  padding: 0.15rem 0.5rem;
  border-radius: 6px;
  font-size: 0.75rem;
  white-space: nowrap;
}
</style>
