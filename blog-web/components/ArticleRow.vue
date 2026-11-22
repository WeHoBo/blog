<template>
  <!-- 紧凑行：62×32 缩略图 + 单行标题 + 日期。
       首页右栏「全部文章」与横幅置顶堆叠都用它 —— 两处尺寸一致，
       不各写一套卡片，改一次两边都变。 -->
  <NuxtLink :to="articlePath(article)"
    class="flex items-center gap-2.5 px-2 h-[54px] rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:border-primary-400 transition-colors group"
    :class="withTopOffset ? 'translate-y-[6px] scale-[0.97] opacity-60 pointer-events-none origin-top' : ''">
    <span class="w-16 h-8 flex-shrink-0 rounded overflow-hidden bg-gray-100 dark:bg-gray-700 flex items-center justify-center">
      <img v-if="article.cover" :src="ossImage(article.cover, { w: 200 })" :alt="article.title"
        loading="lazy" decoding="async"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
    </span>
    <span class="min-w-0 flex-1">
      <span class="block text-xs font-medium text-gray-900 dark:text-gray-100 truncate">{{ article.title }}</span>
      <span class="block text-[10px] text-gray-400 mt-0.5">{{ shownDate }}</span>
    </span>
  </NuxtLink>
</template>

<script setup lang="ts">
import { articlePath } from '~/utils/articlePath'
import { ossImage } from '~/utils/image'

const props = withDefaults(defineProps<{
  article: any
  /** 横幅叠压态的「后一张」：下移 6px + 缩到 0.97，露出边缘 */
  withTopOffset?: boolean
}>(), { withTopOffset: false })

/** 与文章卡片一致的日期口径：优先发布时间，回落到创建时间 */
const shownDate = computed(() => String(props.article?.publishAt || props.article?.createTime || '').substring(0, 10))
</script>
