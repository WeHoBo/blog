<template>
  <NuxtLink :to="article.slug ? `/post/${article.slug}` : `/article/${article.id}`"
    class="block bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 hover:border-gray-200 dark:hover:border-gray-600 transition-all duration-200 overflow-hidden group hover:-translate-y-0.5 hover:shadow-md"
    :class="layout === 'grid' ? 'flex flex-col h-full' : ''">
    <!-- 网格模式：顶部插图 + 下方信息 -->
    <template v-if="layout === 'grid'">
      <div class="aspect-video overflow-hidden bg-gray-100 dark:bg-gray-700 flex-shrink-0">
        <img v-if="article.cover" :src="article.cover" :alt="article.title" loading="lazy"
          class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
        <div v-else class="w-full h-full flex items-center justify-center">
          <svg class="w-10 h-10 text-gray-300 dark:text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 6a2 2 0 012-2h4l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2z"/></svg>
        </div>
      </div>
      <div class="p-4 flex-1 flex flex-col">
        <h2 class="text-2xl font-extrabold text-gray-900 dark:text-gray-100 line-clamp-2 title-hover transition-colors">
          <span v-if="article.isTop" class="inline-block text-xs bg-red-500 text-white px-1.5 py-0.5 rounded mr-1.5 align-middle">置顶</span>
          {{ article.title }}
        </h2>
        <div class="mt-3 flex items-center gap-3 text-xs text-gray-400 mt-auto pt-2">
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
          <div class="flex items-center gap-2 mb-3">
            <span class="w-1 h-5 theme-bar rounded-full flex-shrink-0"></span>
            <h2 class="title-hover text-2xl font-extrabold text-gray-900 dark:text-gray-100 line-clamp-2 transition-colors">
              {{ article.title }}
            </h2>
            <span class="theme-text flex-shrink-0 transition-transform group-hover:translate-x-1">→</span>
          </div>

          <div class="flex flex-wrap items-center gap-x-2 gap-y-1 mb-3 text-xs text-gray-400 dark:text-gray-500">
            <span>{{ article.createTime?.substring(0, 10) }}</span>
            <span class="opacity-40">·</span>
            <span>{{ article.wordCount || 0 }} 字</span>
            <span class="opacity-40">·</span>
            <span class="theme-text">{{ article.categoryName || '未分类' }}</span>
          </div>

          <p v-if="article.summary" class="text-gray-400 dark:text-gray-500 text-sm line-clamp-2 leading-relaxed mb-3">{{ article.summary }}</p>
          <p v-else class="text-gray-300 dark:text-gray-600 text-sm line-clamp-2 mb-3">暂无摘要</p>

          <div class="flex items-center gap-4 text-xs text-gray-400">
            <span>{{ article.viewCount }} 阅读</span>
            <span>{{ article.commentCount || 0 }} 评论</span>
          </div>
        </div>

        <div class="hidden sm:block w-36 lg:w-44 flex-shrink-0 border-l border-gray-100 dark:border-gray-700">
          <div class="h-full min-h-[10rem] theme-illustration flex items-center justify-center">
            <img v-if="article.cover" :src="article.cover" :alt="article.title" loading="lazy" class="w-full h-full object-cover" />
            <svg v-else class="w-10 h-10 opacity-40" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 6a2 2 0 012-2h4l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2z"/></svg>
          </div>
        </div>
      </div>
    </template>
  </NuxtLink>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  article: { id: number; title: string; summary: string; cover: string; isTop: number; viewCount: number; commentCount: number; createTime: string; wordCount?: number; categoryName?: string; tags?: { id: number; name: string }[]; author?: { nickname: string; avatar: string } }
  layout?: 'list' | 'grid'
}>(), { layout: 'list' })
</script>
