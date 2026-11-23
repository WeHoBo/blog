<template>
  <!-- 横幅中间的**置顶**堆叠：折叠时两张叠压，点开铺成完整列表。
       ⚠️ 与下面中栏的「精品」是两回事，数据也不共享。
       参考 HarmonyOS 的「卡片夹」，但**只做叠压与展开/收起** ——
       手势拖拽、滑动切换在 320px 宽的栏里既难用也没必要。 -->
  <div class="h-full flex flex-col">
    <div class="flex items-center justify-between mb-2">
      <div class="text-[11px] font-bold text-gray-400 tracking-wide">
        置顶<span v-if="articles.length" class="ml-1 font-normal opacity-70">· {{ articles.length }} 篇</span>
      </div>
    </div>

    <!-- 空态：超管给一个直达入口，访客只看到一句说明 -->
    <div v-if="!articles.length" class="flex-1 flex flex-col items-center justify-center text-center py-3">
      <div class="w-8 h-8 rounded-lg border border-dashed border-gray-300 dark:border-gray-600 flex items-center justify-center text-gray-300 dark:text-gray-600 mb-2">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.6" d="M4 7h16M4 12h16M4 17h10"/></svg>
      </div>
      <p class="text-[11px] text-gray-400 leading-relaxed">还没有置顶文章</p>
      <NuxtLink v-if="isSuper" to="/admin/articles"
        class="mt-1.5 text-[11px] text-primary-600 dark:text-primary-400 hover:underline">去挑几篇 →</NuxtLink>
    </div>

    <template v-else>
      <!-- 折叠态：两张叠压。高度写死，否则第二张的偏移量算不准、
           展开切换时整块横幅会跟着抖。
           ⚠️ 74 = ArticleRow 的 68px + 6px 偏移，改其中一边必须同步另一边。 -->
      <div v-if="!open" class="relative" style="height: 74px">
        <!-- 后一张先渲染 → 天然压在下面 -->
        <div v-if="second" class="absolute inset-x-0 top-0">
          <ArticleRow :article="second" with-top-offset />
        </div>
        <div class="absolute inset-x-0 top-0">
          <ArticleRow :article="first" />
        </div>
      </div>

      <!-- 展开态：限高滚动，不把顶部横幅撑破 -->
      <div v-else class="space-y-1.5 overflow-y-auto pr-0.5" style="max-height: 240px">
        <ArticleRow v-for="a in articles" :key="a.id" :article="a" />
      </div>

      <button v-if="articles.length > 1" @click="open = !open"
        class="mt-2 inline-flex items-center gap-1 text-[11px] text-primary-600 dark:text-primary-400 hover:underline self-start">
        {{ open ? '收起' : `展开全部 ${articles.length} 篇` }}
        <svg class="w-3 h-3 transition-transform duration-200" :class="open ? 'rotate-180' : ''"
          fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 9l6 6 6-6"/></svg>
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
// 置顶文章来自首页的 SSR payload（`/article/list?top=1`），这里不做任何请求
const props = withDefaults(defineProps<{
  articles: any[]
}>(), { articles: () => [] })

/** 只能超管看到「去挑几篇」入口；mounted 后置 true，避免 SSR 水合不一致 */
const isSuper = useIsSuper()

const open = ref(false)

const first = computed(() => props.articles[0] || null)
const second = computed(() => props.articles[1] || null)

// 数量减少到 1 篇时，展开态没有意义，自动收起（否则按钮消失但列表还开着）
watch(() => props.articles.length, (n) => { if (n <= 1) open.value = false })
</script>
