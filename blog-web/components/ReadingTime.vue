<template>
  <span class="flex items-center gap-1 text-gray-400">
    <span class="text-xs">⏱</span>
    <span>{{ minutes }} 分钟阅读</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ content: string }>()

const minutes = computed(() => {
  if (!props.content) return 1
  const text = props.content.replace(/[#*`>\-\s]/g, '')
  const count = text.length
  return Math.max(1, Math.ceil(count / 500))
})
</script>
