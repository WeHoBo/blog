<template>
  <div class="max-w-3xl mx-auto" :class="{ 'edition-archive': reference }">
    <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-8">文章归档</h1>

    <div v-if="status === 'pending'" role="status" class="text-center py-10 text-gray-400">加载中…</div>
    <div v-else-if="error" role="status" class="text-center py-10 text-gray-500">
      归档加载失败。<button type="button" class="text-primary-600 underline" @click="refresh()">重试</button>
    </div>
    <p v-else-if="!archives.length" class="py-10 text-center text-gray-500">还没有已发布的文章。</p>
    <div v-else-if="reference" class="archive-volumes">
      <details v-for="(yearItem, yi) in archives" :key="yearItem.year" :open="yi === 0" class="archive-volume">
        <summary><strong>{{ yearItem.year }} 年</strong><span>{{ yearCount(yearItem) }} 篇</span></summary>
        <details v-for="(monthItem, mi) in yearItem.months" :key="monthItem.month" :open="mi === 0" class="archive-month">
          <summary><strong>{{ monthItem.month }} 月</strong><span>{{ monthItem.count }} 篇</span></summary>
          <ul>
            <li v-for="a in monthItem.articles" :key="a.id">
              <NuxtLink :to="articlePath(a)"><time :datetime="a.publishAt || a.createTime">{{ (a.publishAt || a.createTime)?.substring(5, 10) }}</time><span>{{ a.title }}</span></NuxtLink>
            </li>
          </ul>
        </details>
      </details>
    </div>
    <div v-else v-for="yearItem in archives" :key="yearItem.year" class="mb-8">
      <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-1">{{ yearItem.year }}</h2>
      <div v-for="monthItem in yearItem.months" :key="monthItem.month" class="mb-4 ml-2">
        <h3 class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">
          {{ yearItem.year }}年{{ monthItem.month }}月 ({{ monthItem.count }}篇)
        </h3>
        <div class="space-y-0.5 sm:space-y-1">
          <!-- 手机端两处调整（2026-11-24）：
               1. 日期只取 MM-DD（原来 substring(5) 会把 "11-24T10:22:50" 整段渲染出来，
                  列宽被撑开后标题只剩很窄一条）；宽度也收到 w-11，把省下的位置还给标题。
               2. 标题允许两行：手机上一行只有十来个汉字，开头相似的文章根本分不出来。 -->
          <NuxtLink v-for="a in monthItem.articles" :key="a.id" :to="articlePath(a)" class="flex items-start gap-2.5 sm:gap-3 py-1.5 px-3 rounded-lg hover:bg-white dark:hover:bg-gray-800 transition text-sm group">
            <span class="text-[11px] sm:text-xs text-gray-400 w-11 sm:w-12 flex-shrink-0 tabular-nums pt-[3px]">{{ (a.publishAt || a.createTime)?.substring(5, 10) }}</span>
            <span class="flex-1 min-w-0 text-gray-700 dark:text-gray-300 group-hover:text-primary-600 dark:group-hover:text-primary-400 line-clamp-2 leading-relaxed">{{ a.title }}</span>
          </NuxtLink>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 文章链接统一走 articlePath()
import { articlePath } from '~/utils/articlePath'

const { get } = useApi()
const { reference } = useReferenceShell()
const yearCount = (year: { months: { count: number }[] }) => year.months.reduce((total, month) => total + month.count, 0)
const { data: archives, status, error, refresh } = await useAsyncData('archive', async () => {
  const res = await get<any>('/article/archive')
  if (res?.code !== 200 || !res.data) throw new Error('归档加载失败')
  return res.data.archives as any[] || []
}, { default: () => [] as any[] })

useSeoMeta({
  title: '文章归档 - 博观',
  description: '博观的文章归档：按年月浏览全部已发布的技术文章。',
  ogTitle: '文章归档 - 博观',
  ogDescription: '按年月浏览博观的全部已发布文章。',
  ogImage: 'https://codeup.asia/apple-touch-icon.png'
})
useHead({ link: [{ rel: 'canonical', href: 'https://codeup.asia/archive' }] })
</script>
