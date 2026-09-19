<template>
  <div class="flex gap-6">
    <!-- Left TOC sidebar -->
    <aside class="hidden lg:block w-56 flex-shrink-0">
      <div class="sticky top-20 max-h-[calc(100vh-6rem)] overflow-y-auto">
        <TableOfContents :content="article.contentMd || ''" />
      </div>
    </aside>

    <!-- Main content -->
    <main class="flex-1 min-w-0">
      <article class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
        <!--
          头部版式：左侧「标题 + 元信息」，右侧「约方形封面缩略图」。
          以前封面是正文上方的通栏大图（w-full max-h-96）：又长又扁，把标题和正文硬生生隔开，
          和左侧密集的文字信息在视觉上也不协调。现在收成方形缩略图贴到标题右侧，左右两栏对齐。
        -->
        <div class="px-6 sm:px-10 pt-8 pb-6 border-b dark:border-gray-700">
          <div class="flex items-start gap-4 sm:gap-6">
            <!-- 左：标题 + 标题下方的全部元信息 -->
            <div class="flex-1 min-w-0">
              <h1 class="text-2xl sm:text-3xl font-extrabold text-gray-900 dark:text-gray-100 leading-tight mb-4 neon-glow">
                {{ article.title }}
              </h1>
              <div class="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-gray-400">
                <span>{{ article.createTime?.substring(0, 10) }}</span>
                <span v-if="author" class="flex items-center gap-1">
                  <img v-if="author.avatar" :src="author.avatar" class="w-5 h-5 rounded-full object-cover" />
                  <span>{{ author.nickname || author.username }}</span>
                </span>
                <span>👁 {{ article.viewCount }} 阅读</span>
                <span>📝 {{ wordCount }} 字</span>
                <ReadingTime :content="article.contentMd || ''" />
                <span>💬 {{ commentTotal || article.commentCount || 0 }} 评论</span>
                <button v-if="authStore.isLoggedIn" @click="aiSummarize"
                  class="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs bg-primary-600 text-white hover:bg-primary-700 transition"
                  :disabled="aiSummaryLoading">
                  <svg v-if="aiSummaryLoading" class="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path></svg>
                  <svg v-else class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="4" y="8" width="16" height="12" rx="3" stroke-width="2"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8V4m0 0l-2 2m2-2l2 2"/></svg>
                  {{ aiSummaryLoading ? '总结中...' : 'AI 总结' }}
                </button>
              </div>
            </div>

            <!-- 右：约方形封面缩略图（不再撑满整行，和左侧信息同高起步） -->
            <div v-if="article.cover"
              class="flex-shrink-0 w-20 h-20 sm:w-28 sm:h-28 lg:w-32 lg:h-32 rounded-2xl overflow-hidden bg-gray-100 dark:bg-gray-700 ring-1 ring-black/5 dark:ring-white/10 shadow-sm">
              <img :src="article.cover" :alt="article.title" loading="lazy" class="w-full h-full object-cover" />
            </div>
          </div>
        </div>

        <!-- Content -->
        <div class="px-6 sm:px-10 py-8">
          <MarkdownRenderer :content="article.contentMd || ''" />
        </div>

        <!-- Tags -->
        <div v-if="tags.length > 0" class="px-6 sm:px-10 pb-6">
          <div class="flex flex-wrap gap-2">
            <NuxtLink v-for="t in tags" :key="t.id" :to="taxonomyPath('/tag', t)"
              class="px-3 py-1 rounded-full text-xs bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400 hover:bg-primary-100 dark:hover:bg-primary-800/30 transition">
              # {{ t.name }}
            </NuxtLink>
          </div>
        </div>
      </article>

      <!-- Prev/Next：两边都走 articlePath()，否则会出现"点下一篇就掉进另一个页面"的问题 -->
      <div class="mt-10 flex justify-between gap-4 max-w-3xl mx-auto">
        <NuxtLink v-if="prevArticle" :to="articlePath(prevArticle)" class="flex-1 bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 hover:shadow-md transition group">
          <span class="text-xs text-gray-400">← 上一篇</span>
          <p class="text-sm font-medium text-gray-700 dark:text-gray-300 group-hover:text-primary-600 dark:group-hover:text-primary-400 line-clamp-1 mt-1">{{ prevArticle.title }}</p>
        </NuxtLink>
        <div v-else class="flex-1"></div>
        <NuxtLink v-if="nextArticle" :to="articlePath(nextArticle)" class="flex-1 bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 hover:shadow-md transition group text-right">
          <span class="text-xs text-gray-400">下一篇 →</span>
          <p class="text-sm font-medium text-gray-700 dark:text-gray-300 group-hover:text-primary-600 dark:group-hover:text-primary-400 line-clamp-1 mt-1">{{ nextArticle.title }}</p>
        </NuxtLink>
        <div v-else class="flex-1"></div>
      </div>

      <!-- Back -->
      <div class="mt-6">
        <NuxtLink to="/" class="inline-flex items-center gap-1 text-primary-600 dark:text-primary-400 hover:underline text-sm font-medium">
          ← 返回首页
        </NuxtLink>
      </div>

      <!-- Author card -->
      <div v-if="author" class="mt-10 bg-white dark:bg-gray-800 rounded-xl shadow-sm p-6 flex items-center gap-4">
        <div v-if="author.avatar" class="w-14 h-14 rounded-full bg-primary-100 overflow-hidden flex-shrink-0">
          <img :src="author.avatar" class="w-full h-full object-cover" />
        </div>
        <div v-else class="w-14 h-14 rounded-full bg-primary-600 flex items-center justify-center text-white font-bold text-xl flex-shrink-0">{{ (author.nickname || author.username)?.[0] || '?' }}</div>
        <div class="flex-1">
          <div class="font-bold text-gray-900 dark:text-gray-100">{{ author.nickname || author.username }}</div>
          <div class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">技术博客作者</div>
        </div>
        <div class="text-xs text-gray-400 text-right">
          <div>{{ wordCount }} 字 · {{ commentTotal }} 评论</div>
          <div class="mt-1">{{ article.createTime?.substring(0, 10) }}</div>
        </div>
        <button @click="copyShareLink" class="ml-4 px-3 py-1.5 text-xs border rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition">{{ shareCopied ? '✓ 已复制' : '📋 复制链接' }}</button>
      </div>

      <!-- Comments -->
      <div class="mt-8">
        <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
          <div class="px-6 py-4 border-b dark:border-gray-700">
            <h3 class="font-bold text-gray-900 dark:text-gray-100">评论 ({{ commentTotal }})</h3>
          </div>
          <div class="px-6 py-4">
            <div v-if="comments.length === 0" class="text-center py-8 text-gray-400 text-sm">暂无评论，来写第一条吧</div>
            <div v-else class="space-y-4 mb-5">
              <div v-for="c in comments" :key="c.id" class="border-b dark:border-gray-700 last:border-0 pb-4 last:pb-0">
                <div class="flex items-center gap-2 text-xs text-gray-400 mb-2">
                  <span class="font-medium text-gray-600 dark:text-gray-300">{{ c.nickname || c.username || '匿名' }}</span>
                  <span>{{ c.createTime?.substring(0, 16) }}</span>
                </div>
                <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">{{ c.content }}</p>
              </div>
            </div>

            <div v-if="authStore.isLoggedIn" class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-4">
              <textarea v-model="commentText" rows="3" maxlength="500" placeholder="写下你的评论..." class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary-500"></textarea>
              <div class="flex justify-between items-center mt-3">
                <span class="text-xs text-gray-400">{{ commentText.length }}/500</span>
                <button @click="submitComment" :disabled="!commentText.trim() || commentSubmitting" class="px-5 py-2 bg-primary-600 text-white rounded-full text-sm font-medium hover:bg-primary-700 disabled:opacity-50 transition">
                  {{ commentSubmitting ? '提交中...' : '发表评论' }}
                </button>
              </div>
              <p v-if="commentError" class="text-red-500 text-xs mt-2">{{ commentError }}</p>
            </div>
            <p v-else class="text-center text-sm text-gray-400 py-4">
              <NuxtLink to="/login" class="text-primary-600 hover:underline">登录</NuxtLink>后发表评论
            </p>
          </div>
        </div>
      </div>

      <!--
        相关推荐。
        以前这块有两个"不完整"的地方：
          1. 只要 related 为空整块就消失 → 读者在这里断掉，没有任何下一步入口；
          2. 卡片只有标题+日期，接口其实已经返回了 cover / summary，白白浪费。
        现在：后端保证「同分类不足时用最新文章补齐」，前端补上封面缩略图、摘要、
        阅读数与「更多文章」出口。
      -->
      <div v-if="related.length > 0" class="mt-6">
        <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
          <div class="px-6 py-4 border-b dark:border-gray-700 flex items-center justify-between">
            <h3 class="font-bold text-gray-900 dark:text-gray-100">相关推荐</h3>
            <NuxtLink to="/archive" class="text-xs text-primary-600 dark:text-primary-400 hover:underline">更多文章 →</NuxtLink>
          </div>
          <div class="p-4 grid gap-3 sm:grid-cols-2">
            <NuxtLink v-for="r in related" :key="r.id" :to="articlePath(r)"
              class="flex items-start gap-3 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition group">
              <div class="w-16 h-16 flex-shrink-0 rounded-lg overflow-hidden bg-gray-100 dark:bg-gray-700">
                <img v-if="r.cover" :src="r.cover" :alt="r.title" loading="lazy" class="w-full h-full object-cover" />
                <div v-else class="w-full h-full flex items-center justify-center">
                  <svg class="w-6 h-6 text-gray-300 dark:text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 6a2 2 0 012-2h4l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2z"/></svg>
                </div>
              </div>
              <div class="flex-1 min-w-0">
                <h4 class="font-medium text-sm text-gray-900 dark:text-gray-100 line-clamp-2 group-hover:text-primary-600 dark:group-hover:text-primary-400 transition">{{ r.title }}</h4>
                <p v-if="r.summary" class="text-xs text-gray-400 mt-1 line-clamp-1">{{ r.summary }}</p>
                <div class="flex items-center gap-3 text-xs text-gray-400 mt-1.5">
                  <span>{{ r.createTime?.substring(0, 10) }}</span>
                  <span v-if="r.viewCount != null">👁 {{ r.viewCount }}</span>
                </div>
              </div>
            </NuxtLink>
          </div>
        </div>
      </div>
    </main>

    <!-- 选中文本：AI 解释工具条（Teleport 放在根节点内，保证页面单根，避免过渡卡死） -->
    <Teleport to="body">
      <Transition name="sel-pop">
        <button v-if="selBox && authStore.isLoggedIn" @click="explainSelection" :disabled="explainLoading"
          class="fixed z-[75] px-3 py-1.5 rounded-full bg-gray-900 dark:bg-gray-700 text-white text-xs shadow-lg hover:bg-gray-800 dark:hover:bg-gray-600 transition flex items-center gap-1.5 -translate-x-1/2 -translate-y-full -mt-2"
          :style="{ left: selPos.x + 'px', top: selPos.y + 'px' }">
          <svg v-if="explainLoading" class="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path></svg>
          <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="4" y="8" width="16" height="12" rx="3" stroke-width="2"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8V4m0 0l-2 2m2-2l2 2"/></svg>
          {{ explainLoading ? '分析中...' : 'AI 解释这段' }}
        </button>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * 文章详情的唯一实现。
 *
 * /post/{slug} 与 /article/{id} 都渲染这个组件 —— 之前它们是两套独立页面，
 * 导致同一篇文章从不同入口打开时功能不一样（详见 docs 里的排查说明）。
 *
 * 只接收「已加载好的文章」，loading / 404 由调用方处理，
 * 这样组件内部的取数（评论 / 上下篇 / 相关推荐）在 setup 时就能拿到 article.id，
 * 不必再处理 id 为空的分支。
 */
import { articlePath } from '~/utils/articlePath'
import { taxonomyPath } from '~/utils/taxonomy'

const props = defineProps<{
  article: any
  tags?: any[]
  author?: any
}>()

const { get } = useApi()
const authStore = useAuthStore()
const aiChat = useAiChat()

const articleId = computed(() => props.article?.id)
const tags = computed<any[]>(() => props.tags || [])
const author = computed<any>(() => props.author || null)
const wordCount = computed(() => props.article?.wordCount || 0)

/** AI 总结本文：取全文（截断过长），走自由问答 */
const aiSummaryLoading = ref(false)
async function aiSummarize() {
  const md = props.article?.contentMd || ''
  if (!md.trim() || aiSummaryLoading.value) return
  aiSummaryLoading.value = true
  try {
    aiChat.openPanel()
    const content = md.length > 18000 ? md.slice(0, 18000) : md
    await aiChat.freeAsk(
      `请用 Markdown 总结下面这篇博客文章，输出：\n## 核心内容\n## 主要知识点\n## 适合人群\n## 学习重点\n\n文章标题：${props.article?.title || ''}\n\n文章内容：\n${content}`
    )
  } finally {
    aiSummaryLoading.value = false
  }
}

/* ---- 选中代码「解释这段」工具条 ---- */
const selBox = ref(false)
const selText = ref('')
const selPos = reactive({ x: 0, y: 0 })
const explainLoading = ref(false)

onMounted(() => {
  document.addEventListener('mouseup', handleSelect)
  document.addEventListener('scroll', hideSelBox, true)
})
onUnmounted(() => {
  document.removeEventListener('mouseup', handleSelect)
  document.removeEventListener('scroll', hideSelBox, true)
})

function handleSelect() {
  const s = window.getSelection()
  const text = s ? s.toString().trim() : ''
  // 内容过短或跨出正文区域不显示
  if (!text || text.length < 10 || text.length > 4000) { selBox.value = false; return }
  const range = s.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  if (!rect || (rect.width === 0 && rect.height === 0)) { selBox.value = false; return }
  selText.value = text
  selPos.x = rect.left + rect.width / 2
  selPos.y = rect.top
  selBox.value = true
}

function hideSelBox() { selBox.value = false }

async function explainSelection() {
  const text = selText.value
  selBox.value = false
  if (!text || explainLoading.value) return
  explainLoading.value = true
  try {
    aiChat.openPanel()
    await aiChat.freeAsk(`请解释下面这段代码（或文本）的作用、执行流程、关键方法与易错点，用 Markdown 输出：\n\n${text}`)
  } finally {
    explainLoading.value = false
  }
}

/* ---- 分享 ---- */
const shareCopied = ref(false)
function copyShareLink() {
  const copy = () => { shareCopied.value = true; setTimeout(() => shareCopied.value = false, 2000) }
  const url = window.location.href
  if (navigator.clipboard) {
    navigator.clipboard.writeText(url).then(copy)
  } else {
    const el = document.createElement('textarea')
    el.value = url; document.body.appendChild(el); el.select()
    document.execCommand('copy'); document.body.removeChild(el)
    copy()
  }
}

/* ---- 评论 ---- */
const { data: commentsData, refresh: refreshComments } = await useAsyncData(
  `article-comments-${articleId.value}`,
  async () => {
    try {
      const res = await get<any>(`/comment/list?articleId=${articleId.value}&pageSize=50`)
      if (res.code === 200 && res.data) return { records: res.data.records || [], total: res.data.total || 0 }
    } catch {}
    return { records: [], total: 0 }
  },
  { watch: [articleId], default: () => ({ records: [], total: 0 }) }
)
const comments = computed(() => commentsData.value?.records || [])
const commentTotal = computed(() => commentsData.value?.total || 0)

const commentText = ref('')
const commentSubmitting = ref(false)
const commentError = ref('')

async function submitComment() {
  if (!commentText.value.trim()) return
  commentSubmitting.value = true
  commentError.value = ''
  try {
    const { post } = useApi()
    await post('/comment', { articleId: Number(articleId.value), content: commentText.value })
    commentText.value = ''
    await refreshComments()
  } catch (e: any) {
    commentError.value = e.message || '评论失败'
  } finally {
    commentSubmitting.value = false
  }
}

/* ---- 上下篇：走后端轻量接口，不为了算相邻文章而拉取整页全文 ---- */
const { data: neighborsData } = await useAsyncData(
  `article-neighbors-${articleId.value}`,
  async () => {
    try {
      const res = await get<any>(`/article/${articleId.value}/neighbors`)
      if (res.code === 200 && res.data) return res.data
    } catch {}
    return {}
  },
  { watch: [articleId], default: () => ({}) }
)
const prevArticle = computed(() => neighborsData.value?.prev || null)
const nextArticle = computed(() => neighborsData.value?.next || null)

/* ---- 相关推荐 ---- */
const { data: relatedData } = await useAsyncData(
  `article-related-${articleId.value}`,
  async () => {
    try {
      const res = await get<any>(`/article/${articleId.value}/related`)
      if (res.code === 200) return res.data || []
    } catch {}
    return []
  },
  { watch: [articleId], default: () => [] }
)
const related = computed<any[]>(() => relatedData.value || [])
</script>
