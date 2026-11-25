<template>
  <section :aria-busy="loading">
    <header class="mb-4 flex flex-wrap items-baseline justify-between gap-3">
      <h2 class="flex flex-wrap items-baseline gap-2 text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ title }}
        <span v-if="!loading" class="text-sm font-normal text-gray-500 dark:text-gray-400">{{ total }} 篇</span>
      </h2>
      <button type="button" @click="$emit('clear')" class="text-sm text-primary-700 hover:underline dark:text-primary-300">
        清除筛选
      </button>
    </header>
    <p v-if="loading" role="status" class="glass-solid rounded-xl border p-6 text-sm text-gray-500 dark:text-gray-400">正在加载…</p>
    <div v-else-if="!articles.length && !error" class="glass-solid rounded-xl border p-8 text-center">
      <p class="text-gray-600 dark:text-gray-300">{{ emptyText }}</p>
      <p v-if="keyword" class="mt-1 text-xs text-gray-500 dark:text-gray-400">换个关键词试试？可搜索标题、摘要、标签与分类。</p>
    </div>
    <ScrollPane v-else :max-height="620" :count="articles.length" :complete="!more && !error">
      <div :class="layout === 'grid' ? 'grid grid-cols-1 gap-4 sm:grid-cols-2' : 'space-y-3'">
        <ArticleCard v-for="article in articles" :key="article.id" :article="article" :layout="layout" :keyword="keyword" />
      </div>
      <HomeFeedMore :more="more" :pending="pending" :error="error" @load="$emit('more')" />
    </ScrollPane>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  keyword: string
  articles: any[]
  total: number
  loading: boolean
  more: boolean
  pending: boolean
  error: boolean
  emptyText: string
  layout: 'grid' | 'list'
}>()
defineEmits<{ clear: []; more: [] }>()
</script>
