<template>
  <div class="max-w-5xl mx-auto py-8">
    <nav class="text-xs text-gray-400 mb-3 flex items-center gap-1.5">
      <NuxtLink to="/" class="hover:text-primary-600 transition">首页</NuxtLink>
      <span class="opacity-50">/</span>
      <span class="text-gray-500 dark:text-gray-400">分类</span>
    </nav>

    <header class="mb-6">
      <h1 class="text-2xl font-extrabold text-gray-900 dark:text-gray-100 flex items-center gap-2">
        <span class="w-1 h-6 bg-primary-600 rounded-full"></span>
        文章分类
      </h1>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-2">
        共 {{ categories.length }} 个分类 · 括号内为已发布文章数（含子分类）
      </p>
    </header>

    <div v-if="pending" class="text-center py-16 text-gray-400">加载中...</div>

    <div v-else-if="!categories.length" class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 p-16 text-center text-gray-400">
      <span class="text-4xl block mb-3">📂</span>
      <p class="text-sm">还没有任何分类</p>
    </div>

    <!-- 一级分类：卡片；二级分类作为卡片内的子项 -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <section
        v-for="cat in tree"
        :key="cat.id"
        class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 shadow-sm overflow-hidden flex flex-col">
        <NuxtLink
          :to="taxonomyPath('/category', cat)"
          class="flex items-center justify-between gap-3 px-5 py-4 hover:bg-gray-50 dark:hover:bg-gray-700/40 transition group">
          <span class="text-base font-bold text-gray-900 dark:text-gray-100 group-hover:text-primary-600 dark:group-hover:text-primary-400 transition truncate">
            {{ cat.name }}
          </span>
          <span class="text-xs text-gray-400 flex-shrink-0">{{ cat.articleCount || 0 }} 篇</span>
        </NuxtLink>

        <div v-if="cat.children && cat.children.length" class="px-5 pb-4 pt-1 flex flex-wrap gap-2 border-t border-gray-50 dark:border-gray-700/60">
          <NuxtLink
            v-for="child in cat.children"
            :key="child.id"
            :to="taxonomyPath('/category', child)"
            class="inline-flex items-baseline gap-1 px-2.5 py-1 rounded-full text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-primary-100 dark:hover:bg-primary-900/30 hover:text-primary-600 dark:hover:text-primary-400 transition">
            {{ child.name }}
            <span class="text-[10px] opacity-50">{{ child.articleCount || 0 }}</span>
          </NuxtLink>
        </div>
      </section>
    </div>

    <div class="mt-10 flex flex-wrap items-center gap-4 text-sm">
      <NuxtLink to="/tags" class="text-primary-600 dark:text-primary-400 hover:underline">按标签浏览 →</NuxtLink>
      <NuxtLink to="/archive" class="text-gray-500 dark:text-gray-400 hover:text-primary-600 transition">按时间归档 →</NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { buildCategoryTree, taxonomyPath, SITE_URL, type TaxonomyItem } from '~/utils/taxonomy'

const { get } = useApi()

const { data, pending } = await useAsyncData('categories-index', async () => {
  const res = await get<any>('/category/list').catch(() => null)
  const list: TaxonomyItem[] = res?.code === 200 ? (res.data || []) : []
  return list
})

/** 只保留有文章的分类（含子分类计数），空分类点进去是空页，属于噪音 */
const categories = computed<TaxonomyItem[]>(() =>
  (data.value || []).filter((c) => (c.articleCount || 0) > 0)
)

const tree = computed(() => buildCategoryTree(categories.value))

useSeoMeta({
  title: '文章分类 - 好啵博客',
  description: '好啵博客的文章分类导航：按 Java、AI、操作系统、数据库、网络等分类浏览全部技术文章。',
  ogTitle: '文章分类 - 好啵博客',
  ogDescription: '按分类浏览好啵博客的全部技术文章。',
  ogUrl: `${SITE_URL}/categories`
})
useHead({ link: [{ rel: 'canonical', href: `${SITE_URL}/categories` }] })
</script>
