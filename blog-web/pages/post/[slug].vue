<template>
  <div class="max-w-7xl mx-auto py-6">
    <div class="flex gap-6">
      <main class="flex-1 min-w-0">
        <div v-if="pending" class="flex justify-center py-32">
          <div class="animate-spin h-8 w-8 border-2 border-primary-600 border-t-transparent rounded-full"></div>
        </div>
        <div v-else-if="error || !article" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-20 text-center text-gray-400">
          <span class="text-5xl mb-4 block">🔍</span>文章不存在或已被删除
        </div>
        <article v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
          <div class="px-6 sm:px-10 pt-8">
            <h1 class="text-2xl sm:text-3xl font-extrabold text-gray-900 dark:text-gray-100 leading-tight mb-4 neon-glow">{{ article.title }}</h1>
            <div class="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-gray-400 mb-6 pb-6 border-b dark:border-gray-700">
              <span>{{ article.createTime?.substring(0, 10) }}</span>
              <span>👁 {{ article.viewCount }} 阅读</span>
              <span>💬 {{ article.commentCount || 0 }} 评论</span>
            </div>
          </div>
          <div class="px-6 sm:px-10 py-8">
            <MarkdownRenderer :content="article.contentMd || ''" />
          </div>
        </article>
        <div class="mt-6">
          <NuxtLink to="/" class="inline-flex items-center gap-1 text-primary-600 dark:text-primary-400 hover:underline text-sm font-medium">← 返回首页</NuxtLink>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const { get } = useApi()
const { data: articleData, pending, error } = await useAsyncData(`post-${route.params.slug}`, async () => {
  const res = await get<any>(`/article/post/${route.params.slug}`)
  if (res.code === 200 && res.data) return res.data.article || res.data
  throw new Error('文章不存在')
})
const article = computed(() => articleData.value as any)
</script>
