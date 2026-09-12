<template>
  <div class="max-w-7xl mx-auto py-6">
    <div v-if="pending" class="flex justify-center py-32">
      <div class="animate-spin h-8 w-8 border-2 border-primary-600 border-t-transparent rounded-full"></div>
    </div>
    <div v-else-if="!article" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-20 text-center text-gray-400">
      <span class="text-5xl mb-4 block">🔍</span>
      文章不存在或已被删除
    </div>
    <!-- 与 /article/{id} 共用同一个组件，两条路由的展示完全一致 -->
    <ArticleDetail v-else :article="article" :tags="tags" :author="author" />
  </div>
</template>

<script setup lang="ts">
/**
 * /post/{slug} —— 站内的规范文章 URL（canonical 指向这里）。
 *
 * 历史上这个页面是一份"精简实现"（只有标题+正文），而功能完整的那份在
 * /article/{id}，于是同一篇文章从首页进来和从 RAG 引用进来长得完全不一样。
 * 现在两者都渲染 ArticleDetail，展示一致；这里只保留「按 slug 取数 + SEO」。
 */
const route = useRoute()
const { get } = useApi()

const SITE_URL = 'https://codeup.asia'

const { data, pending } = await useAsyncData(`post-${route.params.slug}`, async () => {
  const res = await get<any>(`/article/post/${route.params.slug}`)
  if (res.code === 200 && res.data) {
    // 详情接口返回 { article, tags, author }
    const d = res.data
    return { article: d.article || d, tags: (d.tags || []) as any[], author: d.author || null }
  }
  throw new Error('文章不存在')
}, {
  // 客户端在 /post/a → /post/b 之间导航时自动重新拉取
  watch: [() => route.params.slug]
})

const article = computed(() => (data.value as any)?.article || null)
const tags = computed<any[]>(() => (data.value as any)?.tags || [])
const author = computed(() => (data.value as any)?.author || null)

const canonicalUrl = computed(() => `${SITE_URL}/post/${route.params.slug}`)

// 这个 URL 最规范，此前却完全没有 SEO meta：补上 title/description/og，
// 并声明 canonical（与 /article/{id} 去重，避免同一篇内容两个 URL 互相稀释权重）。
useSeoMeta({
  title: () => (article.value?.title ? `${article.value.title} - 好啵博客` : '文章 - 好啵博客'),
  description: () => article.value?.summary || '好啵博客技术文章',
  ogTitle: () => article.value?.title || '好啵博客',
  ogDescription: () => article.value?.summary || '一个程序员的个人技术博客',
  ogImage: () => article.value?.cover || `${SITE_URL}/apple-touch-icon.png`,
  ogType: 'article',
  ogUrl: () => canonicalUrl.value
})

// Article 结构化数据：搜索引擎/社交平台能拿到作者、发布时间与封面
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
