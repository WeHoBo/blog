<template>
  <div class="max-w-7xl mx-auto py-6">
    <div class="flex gap-6">
      <!-- Left TOC sidebar -->
      <aside class="hidden lg:block w-56 flex-shrink-0">
        <div class="sticky top-20 max-h-[calc(100vh-6rem)] overflow-y-auto">
          <TableOfContents :content="currentArticle?.contentMd || ''" />
        </div>
      </aside>
      <!-- Main content -->
      <main class="flex-1 min-w-0">
        <div v-if="pending" class="flex justify-center py-32">
          <div class="animate-spin h-8 w-8 border-2 border-primary-600 border-t-transparent rounded-full"></div>
        </div>
        <div v-else-if="error" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-20 text-center text-gray-400">
          <span class="text-5xl mb-4 block">🔍</span>
          文章不存在或已被删除
        </div>
        <article v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
          <!-- Meta header -->
          <div class="px-6 sm:px-10 pt-8">
            <h1 class="text-2xl sm:text-3xl font-extrabold text-gray-900 dark:text-gray-100 leading-tight mb-4 neon-glow">
              {{ currentArticle.title }}
            </h1>
            <div class="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-gray-400 mb-6 pb-6 border-b dark:border-gray-700">
              <span>{{ currentArticle.createTime?.substring(0, 10) }}</span>
              <span v-if="author" class="flex items-center gap-1">
                <img v-if="author.avatar" :src="author.avatar" class="w-5 h-5 rounded-full object-cover" />
                <span>{{ author.nickname || author.username }}</span>
              </span>
              <span>👁 {{ currentArticle.viewCount }} 阅读</span>
              <ReadingTime :content="currentArticle?.contentMd || ''" />
              <span>📝 {{ wordCount }} 字</span>
              <span>💬 {{ currentArticle.commentCount || 0 }} 评论</span>
              <button v-if="authStore.isLoggedIn" @click="aiSummarize"
                class="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs bg-primary-600 text-white hover:bg-primary-700 transition"
                :disabled="aiSummaryLoading">
                <svg v-if="aiSummaryLoading" class="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path></svg>
                <svg v-else class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="4" y="8" width="16" height="12" rx="3" stroke-width="2"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8V4m0 0l-2 2m2-2l2 2"/></svg>
                {{ aiSummaryLoading ? '总结中...' : 'AI 总结' }}
              </button>
            </div>
          </div>

          <!-- Cover -->
          <img v-if="currentArticle.cover" :src="currentArticle.cover" :alt="currentArticle.title" loading="lazy" class="w-full max-h-96 object-cover" />

          <!-- Content -->
          <div class="px-6 sm:px-10 py-8">
            <MarkdownRenderer :content="currentArticle.contentMd || ''" />
          </div>

          <!-- Tags -->
          <div class="px-6 sm:px-10 pb-6" v-if="articleTags.length > 0">
            <div class="flex flex-wrap gap-2">
              <NuxtLink v-for="t in articleTags" :key="t.id" :to="`/?tagId=${t.id}`" class="px-3 py-1 rounded-full text-xs bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400 hover:bg-primary-100 dark:hover:bg-primary-800/30 transition">
                {{ t.name }}
              </NuxtLink>
            </div>
          </div>
        </article>

      <!-- 文章正文之外的所有区块：仅在文章成功加载后渲染（避免 404/加载中时出现空评论区等诡异内容） -->
      <template v-if="currentArticle">
      <!-- Prev/Next -->
      <div class="mt-10 flex justify-between gap-4 max-w-3xl mx-auto">
        <NuxtLink v-if="prevArticle" :to="`/article/${prevArticle.id}`" class="flex-1 bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 hover:shadow-md transition group">
          <span class="text-xs text-gray-400">← 上一篇</span>
          <p class="text-sm font-medium text-gray-700 dark:text-gray-300 group-hover:text-primary-600 dark:group-hover:text-primary-400 line-clamp-1 mt-1">{{ prevArticle.title }}</p>
        </NuxtLink>
        <div v-else class="flex-1"></div>
        <NuxtLink v-if="nextArticle" :to="nextArticle.slug ? `/post/${nextArticle.slug}` : `/article/${nextArticle.id}`" class="flex-1 bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 hover:shadow-md transition group text-right">
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
            <div class="mt-1">{{ currentArticle?.createTime?.substring(0, 10) }}</div>
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
                <textarea v-model="commentText" rows="3" placeholder="写下你的评论..." class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary-500"></textarea>
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

        <!-- Related -->
        <div v-if="related.length > 0" class="mt-6">
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
            <div class="px-6 py-4 border-b dark:border-gray-700">
              <h3 class="font-bold text-gray-900 dark:text-gray-100">相关推荐</h3>
            </div>
            <div class="p-4 grid gap-3 sm:grid-cols-2">
              <NuxtLink v-for="r in related" :key="r.id" :to="`/article/${r.id}`" class="flex items-start gap-3 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition">
                <div class="flex-1 min-w-0">
                  <h4 class="font-medium text-sm text-gray-900 dark:text-gray-100 line-clamp-2 hover:text-primary-600 dark:hover:text-primary-400 transition">{{ r.title }}</h4>
                  <div class="text-xs text-gray-400 mt-1">{{ r.createTime?.substring(0, 10) }}</div>
                </div>
              </NuxtLink>
            </div>
          </div>
        </div>
      </template>
      </main>
    </div>

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
const route = useRoute()
const { get } = useApi()
const authStore = useAuthStore()
const aiChat = useAiChat()
const aiSummaryLoading = ref(false)

/** AI 总结本文：取全文（截断过长），走自由问答 */
async function aiSummarize() {
  const md = currentArticle.value?.contentMd || ''
  if (!md.trim() || aiSummaryLoading.value) return
  aiSummaryLoading.value = true
  try {
    aiChat.openPanel()
    const content = md.length > 18000 ? md.slice(0, 18000) : md
    await aiChat.freeAsk(
      `请用 Markdown 总结下面这篇博客文章，输出：\n## 核心内容\n## 主要知识点\n## 适合人群\n## 学习重点\n\n文章标题：${currentArticle.value?.title || ''}\n\n文章内容：\n${content}`
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

const { data: articleRaw, pending, error } = await useAsyncData(
  `article-${route.params.id}`,
  async () => {
    const res = await get<any>(`/article/${route.params.id}`)
    if (res.code === 200) return res.data
    throw new Error(res.message || '文章不存在')
  },
  {
    // 客户端路由导航（上一篇/下一篇）时自动重新拉取
    watch: [() => route.params.id]
  }
)

const article = computed(() => (articleRaw.value as any)?.article)
const articleTags = computed(() => (articleRaw.value as any)?.tags || [])
const author = computed(() => (articleRaw.value as any)?.author || null)

const wordCount = computed(() => currentArticle.value?.wordCount || 0)

const currentArticle = computed(() => article.value)

const commentText = ref('')
const commentSubmitting = ref(false)
const commentError = ref('')

const shareCopied = ref(false)
function copyShareLink() {
  const url = window.location.href
  if (navigator.clipboard) {
    navigator.clipboard.writeText(url).then(() => {
      shareCopied.value = true; setTimeout(() => shareCopied.value = false, 2000)
    })
  } else {
    const el = document.createElement('textarea')
    el.value = url; document.body.appendChild(el); el.select()
    document.execCommand('copy'); document.body.removeChild(el)
    shareCopied.value = true; setTimeout(() => shareCopied.value = false, 2000)
  }
}

const SITE_URL = 'https://codeup.asia'

/** 评论列表：用 useAsyncData 让服务端只拉一次、客户端 hydration 复用 payload，避免重复请求（第 36 条） */
const { data: commentsData, refresh: refreshComments } = await useAsyncData(
  `article-comments-${route.params.id}`,
  async () => {
    try {
      const res = await get<any>(`/comment/list?articleId=${route.params.id}&pageSize=50`)
      if (res.code === 200 && res.data) return { records: res.data.records || [], total: res.data.total || 0 }
    } catch {}
    return { records: [], total: 0 }
  },
  { watch: [() => route.params.id], default: () => ({ records: [], total: 0 }) }
)
const comments = computed(() => commentsData.value?.records || [])
const commentTotal = computed(() => commentsData.value?.total || 0)

async function submitComment() {
  if (!commentText.value.trim()) return
  commentSubmitting.value = true
  commentError.value = ''
  try {
    const { post } = useApi()
    await post('/comment', { articleId: Number(route.params.id), content: commentText.value })
    commentText.value = ''
    await refreshComments()
  } catch (e: any) {
    commentError.value = e.message || '评论失败'
  } finally {
    commentSubmitting.value = false
  }
}

/** 上下篇：走后端轻量接口，不再为了算相邻文章而拉取 100 篇全文（第 35 条） */
const { data: neighborsData } = await useAsyncData(
  `article-neighbors-${route.params.id}`,
  async () => {
    try {
      const res = await get<any>(`/article/${route.params.id}/neighbors`)
      if (res.code === 200 && res.data) return res.data
    } catch {}
    return {}
  },
  { watch: [() => route.params.id], default: () => ({}) }
)
const prevArticle = computed(() => neighborsData.value?.prev || null)
const nextArticle = computed(() => neighborsData.value?.next || null)

/** 相关推荐 */
const { data: relatedData } = await useAsyncData(
  `article-related-${route.params.id}`,
  async () => {
    try {
      const res = await get<any>(`/article/${route.params.id}/related`)
      if (res.code === 200) return res.data || []
    } catch {}
    return []
  },
  { watch: [() => route.params.id], default: () => [] }
)
const related = computed(() => relatedData.value || [])

// canonical 统一指向 /post/{slug}（有 slug 时），避免 /post/{slug} 与 /article/{id} 互相稀释权重（第 37 条）
const canonicalUrl = computed(() => {
  const a = article.value
  if (!a) return `${SITE_URL}/article/${route.params.id}`
  return a.slug ? `${SITE_URL}/post/${a.slug}` : `${SITE_URL}/article/${a.id}`
})

useSeoMeta({
  title: () => (article.value?.title ? `${article.value.title} - 好啵博客` : '文章详情 - 好啵博客'),
  description: () => article.value?.summary || '好啵博客技术文章',
  ogTitle: () => article.value?.title || '好啵博客',
  ogDescription: () => article.value?.summary || '一个程序员的个人技术博客',
  ogImage: () => article.value?.cover || `${SITE_URL}/apple-touch-icon.png`,
  ogType: 'article',
  ogUrl: () => canonicalUrl.value
})

// Article 结构化数据（第 38 条）
useHead(() => ({
  link: [{ rel: 'canonical', href: canonicalUrl.value }],
  script: article.value
    ? [{
        type: 'application/ld+json',
        innerHTML: JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'Article',
          headline: article.value.title,
          description: article.value.summary || '',
          image: article.value.cover ? [article.value.cover] : undefined,
          datePublished: article.value.createTime || undefined,
          dateModified: article.value.updateTime || article.value.createTime || undefined,
          author: { '@type': 'Person', name: '好啵' },
          mainEntityOfPage: canonicalUrl.value
        })
      }]
    : []
}))
</script>
