<template>
  <div class="max-w-7xl mx-auto py-6">
    <div class="flex gap-6">
      <!-- Main content -->
      <main class="flex-1 min-w-0">
        <div v-if="pending" class="flex justify-center py-32">
          <div class="animate-spin h-8 w-8 border-2 border-blue-600 border-t-transparent rounded-full"></div>
        </div>
        <div v-else-if="error || !article" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-20 text-center text-gray-400">
          <span class="text-5xl mb-4 block">🔍</span>
          文章不存在或已被删除
        </div>
        <article v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
          <!-- Meta header -->
          <div class="px-6 sm:px-10 pt-8">
            <h1 class="text-2xl sm:text-3xl font-extrabold text-gray-900 dark:text-gray-100 leading-tight mb-4">
              {{ article.title }}
            </h1>
            <div class="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-gray-400 mb-6 pb-6 border-b dark:border-gray-700">
              <span>{{ article.createTime?.substring(0, 10) }}</span>
              <span>👁 {{ article.viewCount }} 阅读</span>
              <ReadingTime :content="article?.contentMd || ''" />
              <span>💬 {{ article.commentCount || 0 }} 评论</span>
            </div>
          </div>

          <!-- Cover -->
          <img v-if="article.cover" :src="article.cover" class="w-full max-h-96 object-cover" />

          <!-- Content -->
          <div class="px-6 sm:px-10 py-8">
            <MarkdownRenderer :content="article.contentMd || ''" />
          </div>

          <!-- Tags -->
          <div class="px-6 sm:px-10 pb-6">
            <div class="flex flex-wrap gap-2">
              <span v-for="t in (article.tags || [])" :key="t" class="px-3 py-1 rounded-full text-xs bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400">{{ t }}</span>
            </div>
          </div>
        </article>

        <!-- Back -->
        <div class="mt-6">
          <NuxtLink to="/" class="inline-flex items-center gap-1 text-blue-600 dark:text-blue-400 hover:underline text-sm font-medium">
            ← 返回首页
          </NuxtLink>
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
                    <span class="font-medium text-gray-600 dark:text-gray-300">{{ c.userId }}</span>
                    <span>{{ c.createTime?.substring(0, 16) }}</span>
                  </div>
                  <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">{{ c.content }}</p>
                </div>
              </div>

              <div v-if="authStore.isLoggedIn" class="bg-gray-50 dark:bg-gray-750 rounded-lg p-4">
                <textarea v-model="commentText" rows="3" placeholder="写下你的评论..." class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-500"></textarea>
                <div class="flex justify-between items-center mt-3">
                  <span class="text-xs text-gray-400">{{ commentText.length }}/500</span>
                  <button @click="submitComment" :disabled="!commentText.trim() || commentSubmitting" class="px-5 py-2 bg-blue-600 text-white rounded-full text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition">
                    {{ commentSubmitting ? '提交中...' : '发表评论' }}
                  </button>
                </div>
                <p v-if="commentError" class="text-red-500 text-xs mt-2">{{ commentError }}</p>
              </div>
              <p v-else class="text-center text-sm text-gray-400 py-4">
                <NuxtLink to="/login" class="text-blue-600 hover:underline">登录</NuxtLink>后发表评论
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
                  <h4 class="font-medium text-sm text-gray-900 dark:text-gray-100 line-clamp-2 hover:text-blue-600 dark:hover:text-blue-400 transition">{{ r.title }}</h4>
                  <div class="text-xs text-gray-400 mt-1">{{ r.createTime?.substring(0, 10) }}</div>
                </div>
              </NuxtLink>
            </div>
          </div>
        </div>
      </main>

      <!-- Right sidebar -->
      <aside class="hidden lg:block w-64 flex-shrink-0">
        <div class="sticky top-20 space-y-4">
          <TableOfContents :content="article?.contentMd || ''" />
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const { get } = useApi()
const authStore = useAuthStore()

const { data: article, pending, error } = await useAsyncData(`article-${route.params.id}`, async () => {
  const res = await get<any>(`/article/${route.params.id}`)
  if (res.code === 200) return res.data
  throw new Error(res.message || '文章不存在')
})

const title = (article.value as any)?.title
const summary = (article.value as any)?.summary

const commentText = ref('')
const commentSubmitting = ref(false)
const commentError = ref('')
const comments = ref<any[]>([])
const commentTotal = ref(0)

async function fetchComments() {
  try {
    const res = await get<any>(`/comment/list?articleId=${route.params.id}&pageSize=50`)
    if (res.code === 200 && res.data) {
      comments.value = res.data.records || []
      commentTotal.value = res.data.total || 0
    }
  } catch {}
}

async function submitComment() {
  if (!commentText.value.trim()) return
  commentSubmitting.value = true
  commentError.value = ''
  try {
    const { post } = useApi()
    await post('/comment', { articleId: Number(route.params.id), content: commentText.value })
    commentText.value = ''
    fetchComments()
  } catch (e: any) {
    commentError.value = e.message || '评论失败'
  } finally {
    commentSubmitting.value = false
  }
}

const related = ref<any[]>([])
async function fetchRelated() {
  const res = await get<any>(`/article/${route.params.id}/related`)
  if (res.code === 200) related.value = res.data || []
}

fetchComments()
fetchRelated()

useSeoMeta({
  title: title || '文章详情',
  description: summary || '',
  ogTitle: title || '',
  ogDescription: summary || ''
})
</script>
