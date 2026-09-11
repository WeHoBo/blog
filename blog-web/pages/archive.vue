<template>
  <div class="max-w-3xl mx-auto">
    <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-8">文章归档</h1>

    <div v-if="loading" class="text-center py-10 text-gray-400">加载中...</div>
    <div v-else v-for="yearItem in archives" :key="yearItem.year" class="mb-8">
      <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-1">{{ yearItem.year }}</h2>
      <div v-for="monthItem in yearItem.months" :key="monthItem.month" class="mb-4 ml-2">
        <h3 class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">
          {{ yearItem.year }}年{{ monthItem.month }}月 ({{ monthItem.count }}篇)
        </h3>
        <div class="space-y-1">
          <NuxtLink v-for="a in monthItem.articles" :key="a.id" :to="a.slug ? `/post/${a.slug}` : `/article/${a.id}`" class="flex items-center gap-3 py-1.5 px-3 rounded-lg hover:bg-white dark:hover:bg-gray-800 transition text-sm group">
            <span class="text-xs text-gray-400 w-20 flex-shrink-0">{{ a.createTime?.substring(5) }}</span>
            <span class="text-gray-700 dark:text-gray-300 group-hover:text-primary-600 dark:group-hover:text-primary-400 line-clamp-1">{{ a.title }}</span>
          </NuxtLink>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const { get } = useApi()
const archives = ref<any[]>([])
const loading = ref(true)

const { data } = await useAsyncData('archive', async () => {
  try {
    const res = await get<any>('/article/archive')
    if (res.code === 200 && res.data) {
      archives.value = res.data.archives || []
    }
  } catch { /* 容错 */ }
  loading.value = false
})

useSeoMeta({
  title: '文章归档 - 好啵博客',
  description: '好啵博客的文章归档：按年月浏览全部已发布的技术文章。',
  ogTitle: '文章归档 - 好啵博客',
  ogDescription: '按年月浏览好啵博客的全部已发布文章。',
  ogImage: 'https://codeup.asia/apple-touch-icon.png'
})
useHead({ link: [{ rel: 'canonical', href: 'https://codeup.asia/archive' }] })
</script>
