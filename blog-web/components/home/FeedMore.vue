<template>
  <div v-if="error || more || pending" class="py-3 text-center text-xs">
    <span v-if="error" role="status" class="mr-2 text-gray-600 dark:text-gray-300">文章加载失败</span>
    <button type="button" :disabled="pending" @click="$emit('load')"
      class="min-h-9 px-4 rounded-full border border-gray-300 dark:border-gray-600 hover:border-primary-500 disabled:opacity-60">
      {{ pending ? '正在加载…' : error ? '重试' : '加载更多' }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ error: boolean; more: boolean; pending: boolean }>()
defineEmits<{ load: [] }>()
</script>
