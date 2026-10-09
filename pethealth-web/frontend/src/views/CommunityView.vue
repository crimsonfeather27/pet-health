<template>
  <section id="community">
    <div class="section-header-row">
      <h2>宠物社区</h2>
      <button class="btn btn-primary" @click="openNewPost">发布新帖</button>
    </div>

    <div v-if="loading" class="empty-hint">加载中…</div>
    <div v-else-if="!posts.length" class="empty-hint">
      还没有帖子 —— 点击右上角「发布新帖」即可创建
    </div>
    <div v-else class="posts-grid">
      <div v-for="p in posts" :key="p.id" class="post-card card-glow" @click="openDetail(p.id)">
        <div class="post-header">
          <span class="post-category">{{ categoryLabel(p.category) }}</span>
          <span class="post-author">by {{ p.authorName }}</span>
        </div>
        <h3 class="post-title">{{ p.title }}</h3>
        <p class="post-content">{{ truncate(p.content || '', 100) }}</p>
        <div class="post-meta" @click.stop>
          <span class="meta-stat" title="浏览">
            <LucideEye :size="15" /> {{ p.viewCount || 0 }}
          </span>
          <button
            class="btn-tiny btn-like"
            :class="{ 'btn-liked': p.liked }"
            @click="toggleLikeList(p.id)"
          >
            <LucideThumbsUp :size="15" />
            <span class="like-count">{{ p.likeCount || 0 }}</span>
          </button>
          <span class="meta-stat" title="回复">
            <LucideMessageCircle :size="15" /> {{ p.replyCount || 0 }}
          </span>
          <button class="btn-tiny btn-primary" @click="openDetail(p.id)">查看详情 →</button>
        </div>
      </div>
    </div>

    <!-- 帖子详情 Modal -->
    <div v-if="detail" class="modal post-detail-modal" @click.self="closeDetail">
      <div class="modal-content modal-content-wide">
        <button class="modal-close" @click="closeDetail">✕</button>
        <h3>{{ detail.post.title }}</h3>
        <p class="post-author">
          作者: {{ detail.post.authorName || '-' }} · {{ detail.post.createdAt || '' }}
        </p>
        <p class="post-category">
          {{ categoryLabel(detail.post.category) }}
          <span v-if="detail.post.petSpecies"> · {{ speciesLabel(detail.post.petSpecies) }}</span>
        </p>
        <p v-if="detail.post.tags?.length" class="post-tags">
          <span v-for="t in detail.post.tags" :key="t" class="tag">{{ t }}</span>
        </p>
        <div class="post-content-box">{{ detail.post.content }}</div>
        <div class="post-stats">
          <span class="meta-stat"><LucideEye :size="15" /> {{ detail.post.viewCount || 0 }}</span>
          <button
            class="btn-tiny btn-like"
            :class="{ 'btn-liked': detail.liked }"
            @click="toggleLikeDetail"
          >
            <LucideThumbsUp :size="15" />
            <span class="like-count">{{ detail.post.likeCount || 0 }}</span>
          </button>
          <span class="meta-stat">
            <LucideMessageCircle :size="15" /> {{ detail.replies.length }}
          </span>
        </div>
        <div v-if="isDetailAuthor" class="modal-actions" style="margin: 1rem 0 0">
          <button class="btn btn-primary btn-tiny" @click="editDetail">编辑</button>
          <button class="btn btn-danger btn-tiny" @click="onDeletePost">删除帖子</button>
        </div>

        <h4 class="replies-title">
          <LucideMessageCircle :size="16" /> 回复 ({{ detail.replies.length }})
        </h4>
        <div class="replies-list">
          <p v-if="!detail.replies.length" class="empty-hint">暂无回复，快来抢沙发～</p>
          <div
            v-for="r in detail.replies"
            :key="r.id"
            class="reply-item"
            :class="{ 'reply-item-accepted': r.isAccepted }"
          >
            <div class="reply-avatar">{{ (r.authorName || 'U').charAt(0).toUpperCase() }}</div>
            <div class="reply-body">
              <p class="reply-author">
                {{ r.authorName || '匿名' }}
                <span class="reply-time">{{ r.createdAt || '' }}</span>
              </p>
              <p class="reply-content">{{ r.content }}</p>
              <div v-if="r.isAccepted || isDetailAuthor || isReplyAuthor(r)" class="reply-actions">
                <span v-if="r.isAccepted" class="pet-badge" style="background: #c6e7cd">
                  <LucideTarget :size="13" /> 已采纳
                </span>
                <div class="reply-action-buttons">
                  <button
                    v-if="isDetailAuthor && !r.isAccepted"
                    class="btn-tiny btn-primary"
                    @click="onAcceptReply(r.id)"
                  >
                    <LucideTarget :size="13" /> 采纳此回复
                  </button>
                  <button
                    v-if="isReplyAuthor(r)"
                    class="btn-tiny btn-danger"
                    @click="onDeleteReply(r.id)"
                  >
                    <LucideTrash2 :size="13" /> 删除
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="user.isLoggedIn" class="form-group" style="margin-top: 1rem">
          <textarea
            v-model="replyContent"
            rows="2"
            placeholder="说点什么吧…"
            @keyup.ctrl.enter="submitReply"
          ></textarea>
          <div class="modal-actions" style="margin-top: 0.5rem">
            <button class="btn btn-primary btn-tiny" :disabled="replying" @click="submitReply">
              <LucideMessageCircle :size="14" /> 发表回复
            </button>
          </div>
        </div>
        <p v-else class="empty-hint" style="margin-top: 1rem">请先登录后参与讨论</p>
      </div>
    </div>

    <!-- 发帖/编辑表单 -->
    <PostFormModal
      v-if="formOpen"
      :post-id="formPostId"
      @close="closeForm"
      @submitted="onFormSubmitted"
    />
  </section>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { apiGet, apiPost, apiDelete } from '@/api'
import { showToast } from '@/composables/useToast'
import PostFormModal from '@/components/PostFormModal.vue'
import {
  Eye as LucideEye,
  ThumbsUp as LucideThumbsUp,
  MessageCircle as LucideMessageCircle,
  Target as LucideTarget,
  Trash2 as LucideTrash2,
} from 'lucide-vue-next'

interface Post {
  id: string
  title: string
  content?: string
  category?: string
  petSpecies?: string
  tags?: string[]
  authorId?: string
  authorName?: string
  likeCount?: number
  replyCount?: number
  viewCount?: number
  createdAt?: string
  liked?: boolean
}
interface Reply {
  id: string
  content?: string
  authorId?: string
  authorName?: string
  createdAt?: string
  isAccepted?: boolean
}

const user = useUserStore()
const route = useRoute()
const router = useRouter()

const CATEGORY_LABELS: Record<string, string> = {
  GENERAL: '综合讨论',
  HEALTH: '健康医疗',
  NUTRITION: '喂养营养',
  TRAINING: '训练行为',
  SHOW: '萌宠展示',
  QUESTION: '求助问答',
}
const SPECIES_LABELS: Record<string, string> = {
  DOG: '狗狗',
  CAT: '猫咪',
  RABBIT: '兔子',
  BIRD: '鸟类',
  OTHER: '其他',
}
function categoryLabel(c?: string) {
  return CATEGORY_LABELS[String(c || 'GENERAL').toUpperCase()] || c || 'GENERAL'
}
function speciesLabel(s?: string) {
  return SPECIES_LABELS[String(s || '').toUpperCase()] || s || ''
}
function truncate(str: string, n: number) {
  return str && str.length > n ? str.slice(0, n) + '...' : str
}

// ---- 列表 ----
const posts = ref<Post[]>([])
const loading = ref(true)

async function loadPosts() {
  loading.value = true
  try {
    const page = await apiGet<any>('/api/posts?page=0&size=20')
    const list: Post[] = page.content || page || []
    const uid = user.currentUser?.id
    if (uid && list.length) {
      // 批量查询当前用户对帖子的点赞状态
      const results = await Promise.all(
        list.map((p) =>
          apiGet(`/api/likes/check?targetType=POST&targetId=${p.id}`)
            .then((liked) => ({ id: p.id, liked: !!liked }))
            .catch(() => ({ id: p.id, liked: false }))
        )
      )
      const map = new Map(results.map((r) => [r.id, r.liked]))
      list.forEach((p) => (p.liked = map.get(p.id) || false))
    }
    posts.value = list
  } catch (e: any) {
    showToast('帖子加载失败：' + e.message, 'error')
  } finally {
    loading.value = false
  }
}

// ---- 详情 ----
const detail = ref<{ post: Post; replies: Reply[]; liked: boolean } | null>(null)
const replyContent = ref('')
const replying = ref(false)

const isDetailAuthor = computed(() => {
  const p = detail.value?.post
  const u = user.currentUser
  return !!(u && p && (u.id === p.authorId || u.username === p.authorName))
})

function isReplyAuthor(r: Reply) {
  const u = user.currentUser
  return !!(u && (u.id === r.authorId || u.username === r.authorName))
}

async function openDetail(postId: string) {
  try {
    const [post, replies] = await Promise.all([
      apiGet<Post>(`/api/posts/${postId}`),
      apiGet<Reply[]>(`/api/replies/post/${postId}`),
    ])
    let liked = false
    if (user.currentUser?.id) {
      try {
        liked = !!await apiGet(`/api/likes/check?targetType=POST&targetId=${postId}`)
      } catch { /* ignore */ }
    }
    detail.value = { post, replies: replies || [], liked }
    replyContent.value = ''
  } catch (e: any) {
    showToast('加载帖子失败：' + e.message, 'error')
  }
}

function closeDetail() {
  detail.value = null
}

async function refreshReplies() {
  const d = detail.value
  if (!d) return
  try {
    const [replies, post] = await Promise.all([
      apiGet<Reply[]>(`/api/replies/post/${d.post.id}`),
      apiGet<Post>(`/api/posts/${d.post.id}`),
    ])
    d.replies = replies || []
    if (post) {
      d.post.likeCount = post.likeCount
      d.post.replyCount = post.replyCount
      d.post.viewCount = post.viewCount
    }
  } catch (e: any) {
    showToast('刷新回复失败：' + e.message, 'error')
  }
}

async function submitReply() {
  const d = detail.value
  if (!d) return
  const content = replyContent.value.trim()
  if (!content) {
    showToast('回复内容不能为空', 'error')
    return
  }
  replying.value = true
  try {
    await apiPost('/api/replies', {
      postId: d.post.id,
      content,
      authorId: user.currentUser?.id || 'demo',
      authorName: user.currentUser?.username || 'demo',
    })
    showToast('回复成功')
    replyContent.value = ''
    await refreshReplies()
  } catch (e: any) {
    showToast('回复失败：' + e.message, 'error')
  } finally {
    replying.value = false
  }
}

async function onDeleteReply(replyId: string) {
  if (!detail.value) return
  if (!confirm('确定删除这条回复吗？删除后无法恢复。')) return
  try {
    await apiDelete(`/api/replies/${replyId}`)
    showToast('已删除回复')
    await refreshReplies()
  } catch (e: any) {
    showToast('删除失败：' + e.message, 'error')
  }
}

async function onAcceptReply(replyId: string) {
  if (!detail.value) return
  try {
    await apiPost(`/api/replies/${replyId}/accept`, {})
    showToast('已采纳为最佳回答')
    await refreshReplies()
  } catch (e: any) {
    showToast('采纳失败：' + e.message, 'error')
  }
}

// ---- 点赞 ----
async function toggleLikeList(postId: string) {
  if (!user.isLoggedIn) {
    showToast('请先登录后点赞', 'error')
    return
  }
  const target = posts.value.find((p) => p.id === postId)
  if (!target) return
  const liked = !!target.liked
  try {
    if (liked) {
      await apiDelete(`/api/likes/POST/${postId}`)
      target.liked = false
      target.likeCount = Math.max(0, (target.likeCount || 0) - 1)
    } else {
      await apiPost('/api/likes', { targetType: 'POST', targetId: postId })
      target.liked = true
      target.likeCount = (target.likeCount || 0) + 1
    }
    // 同步详情（若打开的是同一帖子）
    const d = detail.value
    if (d?.post.id === postId) {
      d.liked = target.liked
      d.post.likeCount = target.likeCount
    }
  } catch (e: any) {
    showToast(e.message || '操作失败', 'error')
  }
}

async function toggleLikeDetail() {
  const d = detail.value
  if (!d) return
  const p = d.post
  if (!user.isLoggedIn) {
    showToast('请先登录后点赞', 'error')
    return
  }
  const liked = d.liked
  try {
    if (liked) {
      await apiDelete(`/api/likes/POST/${p.id}`)
      d.liked = false
      p.likeCount = Math.max(0, (p.likeCount || 0) - 1)
    } else {
      await apiPost('/api/likes', { targetType: 'POST', targetId: p.id })
      d.liked = true
      p.likeCount = (p.likeCount || 0) + 1
    }
    // 同步列表
    const listPost = posts.value.find((x) => x.id === p.id)
    if (listPost) {
      listPost.liked = d.liked
      listPost.likeCount = p.likeCount
    }
  } catch (e: any) {
    showToast(e.message || '操作失败', 'error')
  }
}

// ---- 删帖 ----
async function onDeletePost() {
  const p = detail.value?.post
  if (!p) return
  if (!confirm('确定删除该帖子？此操作不可恢复。')) return
  try {
    await apiDelete(`/api/posts/${p.id}`)
    showToast('删除成功')
    closeDetail()
    await loadPosts()
  } catch (e: any) {
    showToast('删除失败：' + e.message, 'error')
  }
}

// ---- 发帖/编辑表单 ----
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

function editDetail() {
  if (!detail.value) return
  formPostId.value = detail.value.post.id
  formOpen.value = true
}

async function onFormSubmitted(updated: any) {
  closeForm()
  // 若是编辑态，重新打开详情；否则仅刷新列表
  if (updated?.id && detail.value?.post.id === updated.id) {
    await openDetail(updated.id)
  } else if (updated?.id && formPostId.value) {
    await openDetail(updated.id)
  }
  await loadPosts()
}

// ---- 跨视图：从我的帖子跳转过来打开详情 ----
function consumeOpenPostQuery() {
  const openPost = route.query.openPost
  if (typeof openPost === 'string' && openPost) {
    // 清理 query 避免刷新重复打开
    router.replace({ path: '/community' })
    openDetail(openPost)
  }
}

onMounted(async () => {
  await loadPosts()
  consumeOpenPostQuery()
})
</script>

<style scoped>
.empty-hint {
  color: var(--text-light);
  padding: 1rem 0;
}
.posts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1rem;
}
.post-card {
  cursor: pointer;
}
.post-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}
.post-category {
  background: rgba(56, 115, 182, 0.12);
  color: var(--accent);
  padding: 0.15rem 0.5rem;
  border-radius: 6px;
  font-size: 0.75rem;
}
.post-author {
  color: var(--text-light);
  font-size: 0.8rem;
}
.post-title {
  font-size: 1.05rem;
  margin: 0 0 0.4rem;
}
.post-content {
  color: var(--text-light);
  font-size: 0.9rem;
  margin: 0 0 0.75rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.post-meta {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  color: var(--text-light);
  font-size: 0.85rem;
}
.meta-stat {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}
.btn-like {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-light);
  cursor: pointer;
  padding: 0.2rem 0.5rem;
  border-radius: 6px;
  transition: all 0.2s;
}
.btn-like:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.btn-like.btn-liked {
  background: rgba(56, 115, 182, 0.15);
  border-color: var(--accent);
  color: var(--accent);
}

/* 详情 Modal */
.post-detail-modal .post-content-box {
  white-space: pre-wrap;
  background: var(--bg-alt, rgba(0, 0, 0, 0.02));
  border-radius: 8px;
  padding: 0.85rem 1rem;
  margin: 0.5rem 0;
  line-height: 1.6;
}
.post-tags {
  margin: 0.4rem 0;
}
.tag {
  display: inline-block;
  background: rgba(56, 115, 182, 0.1);
  color: var(--accent);
  padding: 0.1rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  margin-right: 0.3rem;
}
.post-stats {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin: 0.5rem 0;
  color: var(--text-light);
}
.replies-title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 1rem 0 0.5rem;
}
.replies-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-height: 40vh;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(56, 115, 182, 0.25) transparent;
  scrollbar-gutter: stable;
}
.reply-item {
  display: flex;
  gap: 0.6rem;
  padding: 0.6rem;
  border-radius: 8px;
  background: var(--bg-alt, rgba(0, 0, 0, 0.02));
}
.reply-item-accepted {
  background: rgba(198, 231, 205, 0.35);
  border: 1px solid rgba(56, 142, 89, 0.3);
}
.reply-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}
.reply-body {
  flex: 1;
  min-width: 0;
}
.reply-author {
  font-weight: 600;
  margin: 0 0 0.2rem;
  font-size: 0.9rem;
}
.reply-time {
  color: var(--text-light);
  font-weight: 400;
  font-size: 0.75rem;
  margin-left: 0.4rem;
}
.reply-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.reply-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.4rem;
  flex-wrap: wrap;
}
.reply-action-buttons {
  display: flex;
  gap: 0.4rem;
}
.pet-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  font-size: 0.72rem;
  color: #2a6b3c;
}
.btn-tiny {
  font-size: 0.78rem;
  padding: 0.25rem 0.55rem;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
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
