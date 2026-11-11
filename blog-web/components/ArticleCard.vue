<template>
  <NuxtLink :to="articlePath(article)"
    class="block bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600 transition-colors duration-200 overflow-hidden group"
    :class="layout === 'grid' ? 'flex flex-col h-full' : ''">
    <!-- 网格模式：顶部插图 + 下方信息 -->
    <template v-if="layout === 'grid'">
      <div class="aspect-video overflow-hidden bg-gray-100 dark:bg-gray-700 flex-shrink-0">
        <img v-if="article.cover" :src="ossImage(article.cover, { w: IMG_W.card })" :alt="article.title" loading="lazy" decoding="async"
          class="w-full h-full object-cover group-hover:scale-[1.03] transition-transform duration-500" />
        <div v-else class="w-full h-full flex items-center justify-center theme-illustration">
          <svg class="w-10 h-10 opacity-60" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 6a2 2 0 012-2h4l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2z"/></svg>
        </div>
      </div>
      <div class="p-4 flex-1 flex flex-col">
        <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100 line-clamp-2 title-hover transition-colors leading-snug">
          <span v-if="article.isTop" class="inline-block text-[10px] bg-primary-600 text-white px-1.5 py-0.5 rounded mr-1.5 align-middle font-medium">置顶</span><span v-html="hlTitle"></span>
        </h2>
        <div class="mt-3 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400 mt-auto pt-2">
          <span>{{ article.createTime?.substring(0, 10) }}</span>
          <span>{{ article.viewCount }} 阅读</span>
          <span>{{ article.commentCount || 0 }} 评论</span>
        </div>
      </div>
    </template>

    <!-- 列表模式：左侧信息 + 右侧插图 -->
    <template v-else>
      <div class="flex items-stretch">
        <div class="flex-1 min-w-0 p-5 flex flex-col justify-center">
          <div class="flex items-baseline gap-2.5 mb-2.5">
            <span class="w-[3px] h-4 theme-bar rounded-full flex-shrink-0 translate-y-0.5"></span>
            <h2 class="title-hover text-base sm:text-lg font-bold text-gray-900 dark:text-gray-100 line-clamp-2 transition-colors leading-snug">
              <span v-html="hlTitle"></span>
            </h2>
          </div>

          <div class="flex flex-wrap items-center gap-x-2 gap-y-1 mb-2 text-xs text-gray-500 dark:text-gray-400">
            <span>{{ article.createTime?.substring(0, 10) }}</span>
            <span class="opacity-40">·</span>
            <span>{{ article.wordCount || 0 }} 字</span>
            <span class="opacity-40">·</span>
            <span class="theme-text">{{ article.categoryName || '未建档文章' }}</span>
          </div>

          <p v-if="article.summary" class="text-gray-500 dark:text-gray-400 text-sm line-clamp-2 leading-relaxed mb-3" v-html="hlSummary"></p>
          <p v-else class="text-gray-300 dark:text-gray-600 text-sm line-clamp-2 mb-3">暂无摘要</p>

          <div class="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
            <span>{{ article.viewCount }} 阅读</span>
            <span>{{ article.commentCount || 0 }} 评论</span>
          </div>
        </div>

        <div class="hidden sm:block w-36 lg:w-44 flex-shrink-0 border-l border-gray-200 dark:border-gray-700">
          <div class="h-full min-h-[10rem] theme-illustration flex items-center justify-center">
            <img v-if="article.cover" :src="ossImage(article.cover, { w: IMG_W.card })" :alt="article.title" loading="lazy" decoding="async" class="w-full h-full object-cover" />
            <svg v-else class="w-10 h-10 opacity-60" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 6a2 2 0 012-2h4l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2z"/></svg>
          </div>
        </div>
      </div>
    </template>
  </NuxtLink>
</template>

<script setup lang="ts">
// 文章链接统一走 articlePath()：避免各处手写模板串导致 /post/{slug} 与 /article/{id} 混用
import { articlePath } from '~/utils/articlePath'
// 封面按显示尺寸取图：卡片宽 144–300px，用 640 宽（2 倍屏够用），实测单张 341KB → 8KB
import { ossImage, IMG_W } from '~/utils/image'
// 高亮返回的是已转义的 HTML 片段（见 utils/highlight.ts），只有它才能进 v-html
import { highlightKeyword } from '~/utils/highlight'

const props = withDefaults(defineProps<{
  article: { id: number; title: string; slug?: string; summary: string; cover: string; isTop: number; viewCount: number; commentCount: number; createTime: string; wordCount?: number; categoryName?: string; tags?: { id: number; name: string }[]; author?: { nickname: string; avatar: string } }
  layout?: 'list' | 'grid'
  /** 搜索结果页传当前关键词进来；为空时等同于原样输出（仍然会转义） */
  keyword?: string
}>(), { layout: 'list', keyword: '' })

/** 标题走的是 v-html，所以必须无条件过一遍 escape —— 高亮与否都安全 */
const hlTitle = computed(() => highlightKeyword(props.article.title, props.keyword))
const hlSummary = computed(() => highlightKeyword(props.article.summary, props.keyword))

</script>
