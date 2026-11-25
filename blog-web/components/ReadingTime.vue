<template>
  <!-- 时钟用内联线条 SVG，不用 ⏱ 字符：各系统 emoji 字形粗细不一，
       夹在一行灰色小字里会显得比文字还重。 -->
  <span class="inline-flex items-center gap-1">
    <svg class="w-3.5 h-3.5 flex-shrink-0" fill="none" stroke="currentColor" stroke-width="1.6"
      stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.5V12l3 1.8" />
    </svg>
    <span>{{ minutes }} 分钟阅读</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 两种入参：
 * - 详情页有全文，传 content，按正文字数算（去掉 markdown 标记与空白后更准）
 * - 列表卡拿不到正文，只有后端算好的 wordCount，直接传数字
 */
const props = defineProps<{ content?: string; wordCount?: number }>()

/** 中文技术阅读大约 500 字/分钟；不足一分钟也显示 1 分钟，不显示 0 */
const minutes = computed(() => {
  if (props.wordCount && props.wordCount > 0) {
    return Math.max(1, Math.ceil(props.wordCount / 500))
  }
  if (!props.content) return 1
  const text = props.content.replace(/[#*`>\-\s]/g, '')
  return Math.max(1, Math.ceil(text.length / 500))
})
</script>
