<template>
  <div class="max-w-3xl mx-auto">
    <h1 class="text-3xl font-extrabold text-gray-900 dark:text-gray-100 mb-8">关于</h1>
    
    <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm p-8 mb-8">
      <div class="prose dark:prose-invert max-w-none">
        <p class="text-gray-700 dark:text-gray-300 leading-relaxed">
          你好，欢迎来到好啵博客。这里记录了我的技术思考、编程心得和学习笔记。
        </p>
        <p class="text-gray-700 dark:text-gray-300 leading-relaxed mt-4">
          技术栈：Java / SpringBoot / Vue / Nuxt / MySQL / Redis
        </p>
      </div>
    </div>

    <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-4">友链</h2>
    <div v-if="links.length === 0" class="text-gray-400 text-sm">暂无友链</div>
    <div v-else class="grid gap-3 sm:grid-cols-2">
      <a v-for="link in links" :key="link.id" :href="link.url" target="_blank" rel="noopener" class="flex items-center gap-3 bg-white dark:bg-gray-800 rounded-xl shadow-sm p-4 hover:shadow-md transition group">
        <img v-if="link.avatar" :src="link.avatar" class="w-10 h-10 rounded-full object-cover" />
        <div v-else class="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-sm">{{ link.name[0] }}</div>
        <div>
          <div class="font-medium text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition text-sm">{{ link.name }}</div>
          <div v-if="link.description" class="text-xs text-gray-400 mt-0.5">{{ link.description }}</div>
        </div>
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
const { get } = useApi()
const links = ref<any[]>([])

async function fetchLinks() {
  const res = await get<any>('/friend-link/list')
  if (res.code === 200) links.value = res.data || []
}

fetchLinks()

useSeoMeta({ title: '关于 - 好啵博客' })
</script>
