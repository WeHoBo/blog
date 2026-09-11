<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center px-4 relative overflow-hidden">
    <div class="absolute inset-0 opacity-20 dark:opacity-10">
      <div class="absolute bottom-0 right-0 w-96 h-96 rounded-full" style="background: radial-gradient(circle, hsl(var(--ph) var(--ps) 60%) 0%, transparent 70%)"></div>
      <div class="absolute top-20 left-10 w-72 h-72 rounded-full" style="background: radial-gradient(circle, hsl(calc(var(--ph) + 40) var(--ps) 65%) 0%, transparent 70%)"></div>
    </div>
    <div class="relative text-center max-w-md">
      <div class="text-7xl mb-5 font-extrabold gradient-text" style="line-height:1">{{ error?.statusCode === 404 ? '404' : '500' }}</div>
      <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-2">
        {{ error?.statusCode === 404 ? '这个页面好像走丢了...' : '出了点小问题' }}
      </h1>
      <p class="text-sm text-gray-500 dark:text-gray-400 mb-8">
        {{ error?.statusCode === 404 ? '你访问的页面不存在或已被移动。' : '请稍后重试，或返回首页看看。' }}
      </p>
      <!-- 调试信息：真实错误（帮助定位） -->
      <div v-if="error?.message && error?.statusCode !== 404" class="text-xs text-gray-400 dark:text-gray-500 mb-6 bg-gray-100 dark:bg-gray-800 rounded-lg px-3 py-2 text-left max-h-40 overflow-y-auto break-all">
        <p>{{ error.message }}</p>
        <p v-if="(error as any)?.cause?.stack" class="mt-1 opacity-70 whitespace-pre-wrap">{{ (error as any).cause.stack }}</p>
      </div>
      <div class="flex items-center justify-center gap-3">
        <button @click="handleError" class="px-5 py-2 rounded-full bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 transition">
          {{ error?.statusCode === 404 ? '返回首页' : '重新加载' }}
        </button>
        <NuxtLink to="/" class="px-5 py-2 rounded-full border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 transition">
          关于我
        </NuxtLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { NuxtError } from '#app'

const props = defineProps<{ error: NuxtError | null }>()

function handleError() {
  if (props.error?.statusCode === 404) {
    return navigateTo('/')
  }
  window.location.reload()
}
</script>
