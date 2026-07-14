<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex flex-col relative">
    <StarryBackground />
    <header class="relative z-10 bg-white/80 dark:bg-gray-800/80 backdrop-blur-md shadow-sm border-b dark:border-gray-700">
      <div class="container mx-auto px-4 max-w-5xl flex items-center justify-between h-14">
        <div class="flex items-center gap-6">
          <NuxtLink to="/" class="font-bold text-primary-600 dark:text-primary-400">好啵博客</NuxtLink>
          <nav class="flex gap-4 text-sm">
            <NuxtLink to="/admin/articles" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition">文章</NuxtLink>
            <NuxtLink to="/admin/categories" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition">分类</NuxtLink>
            <NuxtLink to="/admin/tags" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition">标签</NuxtLink>
            <NuxtLink to="/admin/links" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition">友链</NuxtLink>
          </nav>
        </div>
        <div class="flex items-center gap-3 text-sm">
          <span class="text-gray-500">{{ authStore.user?.nickname }}</span>
          <button @click="authStore.logout" class="text-gray-400 hover:text-red-500 transition">退出</button>
        </div>
      </div>
    </header>
    <main class="relative z-10 flex-1 container mx-auto px-4 py-6 max-w-5xl">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin' })
const authStore = useAuthStore()
</script>
