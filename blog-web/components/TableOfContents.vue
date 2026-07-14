<template>
  <nav v-if="items.length > 0" class="toc text-sm">
    <h4 class="font-semibold text-gray-900 dark:text-gray-100 mb-2">目录</h4>
    <ul class="space-y-1">
      <li v-for="item in items" :key="item.id" :class="'pl-' + (item.level - 1) * 3">
        <a :href="'#' + item.id" class="text-gray-500 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition block py-0.5 leading-snug" :class="{ 'font-medium text-gray-700 dark:text-gray-300': item.level <= 2 }">
          {{ item.text }}
        </a>
      </li>
    </ul>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ content: string }>()

const items = computed(() => {
  if (!props.content) return []
  const headings: { id: string; text: string; level: number }[] = []
  const lines = props.content.split('\n')
  for (const line of lines) {
    const match = line.match(/^(#{1,4})\s+(.+)/)
    if (match) {
      const level = match[1].length
      const text = match[2].trim()
      const id = text.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fff\-]/g, '')
      headings.push({ id, text, level })
    }
  }
  return headings
})
</script>
