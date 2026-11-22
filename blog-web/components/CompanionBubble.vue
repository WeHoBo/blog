<template>
  <!--
    AI 助手的状态气泡（P1）

    为什么自己写而不是用现成 toast：
      1. toast 是「一次性事件」的语义，这里是「持续状态」—— 思考多久就显示多久，
         状态走了气泡自己消失，不应该有"3 秒自动关闭"。
      2. 要贴着小鱼定位，要一个指向右边的小三角，要跟主题色/暗色一起变。
      3. 面板打开时必须立刻消失（不然和面板里的"正在思考…"重复两遍）。

    内容由 useCompanionState.bubbleText 统一给出，空字符串 = 不显示。
  -->
  <Transition name="ccb">
    <div v-if="text" class="ccb" :class="`side-${side}`" role="status" aria-live="polite" :style="{ top: offsetY }">
      <span class="ccb-text">{{ text }}</span>
      <i class="ccb-arrow" aria-hidden="true" />
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { useCompanionState } from '~/composables/useCompanionState'

/**
 * offsetY：气泡相对入口的垂直位置。
 *   CSS 小鱼是 84px 的圆，气泡对着圆心（50%）最自然；
 *   静态立绘是 240px 的全身像，气泡应该对着头（约 22%），对着腰会显得错位。
 *
 * side：气泡挂在角色的哪一侧。
 *   默认左边（角色在右下角时，左边是正文区，有空间）。
 *   但角色被拖到屏幕左侧后，左边没地方了 —— 气泡会整个跑出视口，
 *   所以支持翻到右侧。
 */
withDefaults(defineProps<{ text: string; offsetY?: string; side?: 'left' | 'right' }>(), {
  offsetY: '50%',
  side: 'left',
})
</script>

<style scoped>
.ccb {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  /* ⚠️ max-content 必须显式写。
     气泡是 absolute 且只设了 right（或 left），宽度走 shrink-to-fit，
     可用空间算出来是「容器宽 - 偏移」，这里就是 180 - 192 = 负数 → 收缩到"一个字"宽，
     于是「别戳我！」竖着排成四条。之前白色 nowrap 时不会暴露，
     因为 nowrap 会让首选最小宽度等于整句话，撑住了盒子。
     一旦允许换行（poke 文案会变长），就必须自己声明宽度。 */
  width: max-content;
  max-width: 190px;
  padding: 7px 11px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid hsla(var(--ph), var(--ps), var(--pl), 0.24);
  box-shadow: 0 6px 20px hsl(26 20% 20% / 0.1);
  /* 气泡是"说话"，不是标题 —— 用无衬线、常规字重，别抢正文 */
  font-size: 12px;
  line-height: 1.45;
  color: hsl(26 12% 26%);
  white-space: normal;
  word-break: break-word;
  pointer-events: none;
  z-index: 1;
}
/* 挂在角色左边（默认）：左边缘对齐角色左侧再往左退 12px */
.side-left {
  right: calc(100% + 12px);
}
/* 挂在角色右边：角色被拖到屏幕左侧时用这个，不然气泡跑出视口 */
.side-right {
  left: calc(100% + 12px);
}

.dark .ccb {
  background: hsl(28 10% 14%);
  border-color: hsla(var(--ph), var(--ps), var(--pl), 0.4);
  box-shadow: 0 6px 20px hsl(0 0% 0% / 0.45);
  color: hsl(36 14% 84%);
}

.ccb-text {
  display: block;
  /* 超长工具名要截断，不能把气泡撑到屏幕外 */
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 指向右边的小三角：用旋转的方块做出边框，比 border 三角形多一条边线 */
.ccb-arrow {
  position: absolute;
  top: 50%;
  right: -5px;
  width: 8px;
  height: 8px;
  margin-top: -4px;
  background: inherit;
  border-right: 1px solid hsla(var(--ph), var(--ps), var(--pl), 0.24);
  border-top: 1px solid hsla(var(--ph), var(--ps), var(--pl), 0.24);
  transform: rotate(45deg);
  border-radius: 0 2px 0 0;
}
/* 气泡翻到右侧时，三角跟着翻到左边。
   继续用 border-right + border-top，靠 225° 把它们转到左下角 ——
   比"换 border 方向 + 保持 45°"少一处要同步维护的地方。 */
.side-right .ccb-arrow {
  right: auto;
  left: -5px;
  border-radius: 0 0 0 2px;
  transform: rotate(225deg);
}
.dark .ccb-arrow {
  border-color: hsla(var(--ph), var(--ps), var(--pl), 0.4);
}

/* 进出场：从角色那一侧「长出来」，方向指向角色，视觉上才有归属感 */
.ccb-enter-active,
.ccb-leave-active {
  transition: opacity 0.18s ease, transform 0.22s cubic-bezier(0.34, 1.4, 0.64, 1);
}
.ccb-enter-from,
.ccb-leave-to {
  opacity: 0;
  transform: translateY(-50%) translateX(6px) scale(0.94);
}
/* 右侧气泡的"长出来"方向要反过来，否则会朝着远离角色的方向窜 */
.ccb.side-right.ccb-enter-from,
.ccb.side-right.ccb-leave-to {
  transform: translateY(-50%) translateX(-6px) scale(0.94);
}

@media (prefers-reduced-motion: reduce) {
  .ccb-enter-active,
  .ccb-leave-active {
    transition: opacity 0.12s linear;
  }
  .ccb-enter-from,
  .ccb-leave-to {
    transform: translateY(-50%);
  }
}
</style>
