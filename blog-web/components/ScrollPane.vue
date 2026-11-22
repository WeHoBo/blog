<template>
  <!-- 滚动屏：内容在固定高度里自己滚，顶部用渐变遮罩制造「上面那张被盖住」的效果
       （像 iOS 选时间的滚轮，但**不是循环**：滚到底就停，最后一行是「我是有底线的」）。
       中间栏和右栏各用一个实例，天然是两条独立滚动条，不会同步。

       ⚠️ 内滚动**只在 ≥1280px 开启**（与右栏出现的断点一致）。手机/平板上关掉：
       页面本身就能滚，再套一层 `overflow-y:auto` 会让手指在窄屏里「滚不动」。 -->
  <div :class="scrollable ? 'scroll-pane' : ''" :style="scrollable ? { '--pane-max': maxHeight + 'px' } : undefined">
    <slot />
    <!-- 底线：必须放在滚动内容里，要滚到底才看得到 -->
    <div v-if="count > 0" class="flex items-center gap-3 pt-4 pb-1 select-none">
      <span class="flex-1 h-px bg-gray-200 dark:bg-gray-700"></span>
      <span class="text-[11px] text-gray-300 dark:text-gray-600 tracking-wider">我是有底线的</span>
      <span class="flex-1 h-px bg-gray-200 dark:bg-gray-700"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
// 直接让模板读 props，不额外包 computed（同名变量会和 prop 撞名，反而难读）
withDefaults(defineProps<{
  /** 可视高度上限（px）。内容不足时不会撑出空白 */
  maxHeight?: number
  /** 条目数，用于决定要不要画底线；为 0 时不画 */
  count?: number
  /**
   * 是否启用内部滚动。默认 true，但**实际只在 ≥1280px 生效**（见 <style> 里
   * 的媒体查询）。显式传 false 时连类名都不加 —— 给「窄屏兜底区块」用，
   * 它本来就只出现在 <1280px，不需要任何内滚动相关样式。
   */
  scrollable?: boolean
}>(), { maxHeight: 620, count: 0, scrollable: true })
</script>

<style scoped>
/* 窄屏：完全交给页面滚动，这里什么都不做 */
.scroll-pane {
  overscroll-behavior: contain;
}

@media (min-width: 1280px) {
  .scroll-pane {
    max-height: var(--pane-max, 620px);
    overflow-y: auto;
    /* 顶部 24px 渐隐：滚上去的内容不是硬切，而是被"盖住"。
       只在顶部做，底部留白由底线负责。 */
    -webkit-mask-image: linear-gradient(to bottom, transparent 0, #000 24px);
    mask-image: linear-gradient(to bottom, transparent 0, #000 24px);
    /* 滚动条收细，别把 400px 的栏再吃掉 8px */
    scrollbar-width: thin;
    padding-right: 2px;
  }
  .scroll-pane::-webkit-scrollbar {
    width: 6px;
  }
  .scroll-pane::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.15);
    border-radius: 3px;
  }
}
</style>
