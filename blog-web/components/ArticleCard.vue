<template>
  <NuxtLink :to="`/article/${article.id}`" class="block bg-white dark:bg-gray-800 rounded-xl shadow-sm hover:shadow-md transition-all hover:-translate-y-0.5 overflow-hidden group">
    <div v-if="article.cover" class="h-44 overflow-hidden">
      <img :src="article.cover" :alt="article.title" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
    </div>
    <div class="p-5">
      <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition line-clamp-2">
        <span v-if="article.isTop" class="inline-block text-xs bg-red-500 text-white px-1.5 py-0.5 rounded mr-1.5 align-middle">置顶</span>
        {{ article.title }}
      </h2>
      <p v-if="article.summary" class="text-gray-500 dark:text-gray-400 text-sm mt-2 line-clamp-2 leading-relaxed">
        {{ article.summary }}
      </p>
      <div class="flex items-center gap-4 mt-3 pt-3 border-t dark:border-gray-700 text-xs text-gray-400">
        <span>{{ article.createTime?.substring(0, 10) }}</span>
        <span>👁 {{ article.viewCount }}</span>
      </div>
    </div>
  </NuxtLink>
</template>

<script setup lang="ts">
defineProps<{
  article: {
    id: number
    title: string
    summary: string
    cover: string
    isTop: number
    viewCount: number
    commentCount: number
    createTime: string
  }
}>()
</script>
