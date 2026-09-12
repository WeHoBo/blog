<template>
  <div class="max-w-5xl mx-auto py-8">
    <nav class="text-xs text-gray-400 mb-3 flex items-center gap-1.5">
      <NuxtLink to="/" class="hover:text-primary-600 transition">首页</NuxtLink>
      <span class="opacity-50">/</span>
      <span class="text-gray-500 dark:text-gray-400">标签云</span>
    </nav>

    <header class="mb-6">
      <h1 class="text-2xl font-extrabold text-gray-900 dark:text-gray-100 flex items-center gap-2">
        <span class="w-1 h-6 bg-primary-600 rounded-full"></span>
        标签云
      </h1>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-2">
        共 {{ tags.length }} 个标签 · 字号越大表示该标签下的文章越多
      </p>
    </header>

    <div v-if="pending" class="text-center py-16 text-gray-400">加载中...</div>

    <div v-else-if="!tags.length" class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 p-16 text-center text-gray-400">
      <span class="text-4xl block mb-3">🏷️</span>
      <p class="text-sm">还没有任何标签</p>
    </div>

    <template v-else>
      <!-- 标签云：字号随文章数变化 -->
      <div class="bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 shadow-sm p-6 sm:p-8 flex flex-wrap items-baseline gap-x-5 gap-y-4">
        <NuxtLink
          v-for="t in sortedTags"
          :key="t.id"
          :to="taxonomyPath('/tag', t)"
          class="leading-none text-gray-600 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 transition"
          :style="{ fontSize: cloudSize(t) + 'px' }"
          :title="`${t.name}（${t.articleCount || 0} 篇）`">
          {{ t.name }}<sup class="text-[10px] opacity-50 ml-0.5">{{ t.articleCount || 0 }}</sup>
        </NuxtLink>
      </div>

      <!-- 全量列表：云图不好点的时候用这个 -->
      <section class="mt-8">
        <h2 class="text-sm font-bold text-gray-900 dark:text-gray-100 mb-3">全部标签</h2>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <NuxtLink
            v-for="t in sortedTags"
            :key="t.id"
            :to="taxonomyPath('/tag', t)"
            class="flex items-center justify-between gap-3 px-4 py-3 rounded-xl bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 hover:border-primary-300 dark:hover:border-primary-700 hover:shadow-sm transition">
            <span class="text-sm font-medium text-gray-700 dark:text-gray-200 truncate">
              <span class="text-primary-500 mr-1">#</span>{{ t.name }}
            </span>
            <span class="text-xs text-gray-400 flex-shrink-0">{{ t.articleCount || 0 }} 篇</span>
          </NuxtLink>
        </div>
      </section>
    </template>

    <div class="mt-10 flex flex-wrap items-center gap-4 text-sm">
      <NuxtLink to="/categories" class="text-primary-600 dark:text-primary-400 hover:underline">按分类浏览 →</NuxtLink>
      <NuxtLink to="/archive" class="text-gray-500 dark:text-gray-400 hover:text-primary-600 transition">按时间归档 →</NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { taxonomyPath, SITE_URL, type TaxonomyItem } from '~/utils/taxonomy'

const { get } = useApi()

const { data, pending } = await useAsyncData('tags-index', async () => {
  const res = await get<any>('/article/tags').catch(() => null)
  const list: TaxonomyItem[] = res?.code === 200 ? (res.data || []) : []
  // 只展示真正有文章的标签：空标签点进去是空列表，对读者和搜索引擎都是噪音
  return (list || []).filter((t) => (t.articleCount || 0) > 0)
})

const tags = computed<TaxonomyItem[]>(() => data.value || [])

/** 文章数多的排前面，云图阅读顺序更符合直觉 */
const sortedTags = computed(() =>
  [...tags.value].sort((a, b) => (b.articleCount || 0) - (a.articleCount || 0))
)

const maxCount = computed(() => Math.max(1, ...tags.value.map((t) => t.articleCount || 0)))

/** 13px（最少）~ 27px（最多）：用平方根压一下差距，避免一两个大标签把其余压成小字 */
function cloudSize(t: TaxonomyItem) {
  const ratio = Math.sqrt((t.articleCount || 0) / maxCount.value)
  return Math.round(13 + ratio * 14)
}

useSeoMeta({
  title: '标签云 - 好啵博客',
  description: '好啵博客的全部技术标签：按标签快速找到 Java、SpringBoot、Vue、Nuxt、Python、Linux、Docker 等主题下的文章。',
  ogTitle: '标签云 - 好啵博客',
  ogDescription: '按标签浏览好啵博客的全部技术文章。',
  ogUrl: `${SITE_URL}/tags`
})
useHead({ link: [{ rel: 'canonical', href: `${SITE_URL}/tags` }] })
</script>
