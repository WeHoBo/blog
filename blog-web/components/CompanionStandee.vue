<template>
  <!--
    AI 助手的「静态立绘」画笔（P1-final）

    为什么最终选了它：Live2D 那套能跑通（实测渲出了完整的半身立绘），
    但模型的使用须知明确禁止「把完整模型文件重新分发」——
    而网页 Live2D 的技术前提就是把 moc3 发给每个访客，等于分发。
    所以改成：只发渲染出来的图，站上不放模型文件。

    2026-11-17 换图：之前是从模型渲的等身立绘（174×240 竖构图），
    缩到这个尺寸后五官糊成一团，观感差。现在改成 Q 版（180×167 近方形）：
    Q 版头大身小，在小尺寸下轮廓和表情都还认得出来，也更适合当"入口"而不是"海报"。

    ⚠️ 素材来源：这张 Q 版原图由站主提供（`_poc/companion/q/src.png` 留档），
    可复现的处理脚本在同目录 `clean.py`（抠白底 → 切掉右上角对话气泡 →
    清动感碎线 → 收缩 1px 去白边 → 裁边 → 导出三档 WebP）。
    公开部署前请确认画师许可 —— 这一点和上一版（模型渲染图）是不同的授权前提。

    代价是没有真眨眼和头发飘 —— 用 CSS 补：呼吸、轻微摆动、视线跟手。
    ⚠️ 这一层只负责「画」，状态从 useCompanionState 读，交互往上 emit。
  -->
  <button
    ref="root"
    type="button"
    class="cs"
    :class="[`s-${state}`, { 'is-paused': pageHidden, 'is-frozen': frozen, 'is-sleeping': state === 'sleep' }]"
    :aria-label="ariaText"
    :title="ariaText"
    @click="emit('activate')"
  >
    <span class="cs-glow" aria-hidden="true" />
    <!--
      多包一层专门用来做「重心摇摆」。
      为什么不能直接给 .cs-img 再加一个 animation：
        内联 style 的 transform 已经被视线跟随占用；
        而各状态（hint/typing/done/error）用 `animation:` 简写整体覆盖了 .cs-img 的动画列表，
        写在同一个元素上会被状态动画顺手抹掉。
      套一层之后，摇摆独立存在，状态怎么切都不影响它。
    -->
    <span class="cs-sway">
      <img
        class="cs-img"
        :src="src"
        :srcset="srcset"
        width="180"
        height="167"
        alt=""
        decoding="async"
        fetchpriority="low"
        draggable="false"
        :style="tiltStyle"
        @error="emit('fail')"
      />
    </span>
    <!-- 思考三点 / 打盹的 Z：和 Live2D 版保持一致的位置，都在头顶左上 -->
    <span class="cs-dots" aria-hidden="true"><i /><i /><i /></span>
    <span class="cs-zzz" aria-hidden="true"><i>z</i><i>z</i><i>z</i></span>
  </button>
</template>

<script setup lang="ts">
import { useCompanionState } from '~/composables/useCompanionState'

const emit = defineEmits<{ (e: 'activate'): void; (e: 'fail'): void }>()

/**
 * frozen：拖拽期间冻结。
 *   两个原因：① 视线跟随是全局 pointermove 驱动的，拖拽时手一动她就往反方向偏，看起来像在挣扎；
 *            ② 呼吸/摇摆还在跑，图像一直在动，鼠标抓不准那个"已经被抓住"的手指反馈。
 */
const props = defineProps<{ frozen?: boolean }>()

const { state, pageHidden } = useCompanionState()

/* 三档尺寸：宽高比按图本身来（690:641 ≈ 1.076），别硬塞成方形会把脸压扁 */
const BASE = '/companion/standee.webp'
const src = BASE
const srcset = `${BASE} 1x, /companion/standee@2x.webp 2x, /companion/standee@3x.webp 3x`

const root = ref<HTMLElement | null>(null)

/**
 * 视线跟随：整张图做 1~3px 的倾斜位移。
 * 静态图不能真的转眼睛，但「整个人微微朝你偏一点」足够表达"注意到你了"。
 * 只在 idle/hint 生效 —— 思考和出字时她应该在"忙"，不该跟着鼠标乱晃。
 */
const tilt = ref({ x: 0, y: 0, r: 0 })
const tiltStyle = computed(() => ({
  transform: `translate3d(${tilt.value.x}px, ${tilt.value.y}px, 0) rotate(${tilt.value.r}deg)`,
}))

let raf = 0
function onMove(e: PointerEvent) {
  if (props.frozen) return
  if (raf) return
  raf = requestAnimationFrame(() => {
    raf = 0
    const el = root.value
    if (!el) return
    if (state.value !== 'idle' && state.value !== 'hint') return
    const r = el.getBoundingClientRect()
    const dx = e.clientX - (r.left + r.width / 2)
    // 支点取脸的位置。Q 版头占了画面上半部分，脸中心约在 32%（等身立绘只要 28%），
    // 用错数值会出现"眼睛看着别处、整个人却在朝你偏"的错位感。
    const dy = e.clientY - (r.top + r.height * 0.32)
    const dist = Math.hypot(dx, dy)
    if (dist < 1) return
    const k = Math.min(dist, 320) / 320
    tilt.value = {
      x: +((dx / dist) * 3 * k).toFixed(2),
      y: +((dy / dist) * 2 * k).toFixed(2),
      r: +((dx / dist) * 0.8 * k).toFixed(2),
    }
  })
}

onMounted(() => {
  if (import.meta.server || window.matchMedia('(hover: none)').matches) return
  window.addEventListener('pointermove', onMove, { passive: true })
})

onBeforeUnmount(() => {
  if (import.meta.server) return
  window.removeEventListener('pointermove', onMove)
  if (raf) cancelAnimationFrame(raf)
})

const ariaText = computed(() => {
  switch (state.value) {
    case 'thinking': return 'AI 助手正在思考，点一下打开对话'
    case 'tools': return 'AI 助手正在检索站内文章，点一下打开对话'
    case 'typing': return 'AI 助手正在回答，点一下查看'
    case 'done': return 'AI 助手已回答，点一下查看'
    case 'error': return 'AI 助手这次没能回答，点一下重试'
    case 'sleep': return 'AI 助手在打盹，点一下唤醒'
    default: return '打开 AI 助手'
  }
})
</script>

<style scoped>
.cs {
  position: relative;
  display: block;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  /* 立绘是透明的，hover 抬升交给图片本身，点上去才不违和 */
  transition: filter 0.3s ease;
}
.cs:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 4px;
  border-radius: 12px;
}

.cs-img {
  display: block;
  width: 180px;
  height: auto;
  /* 静态图要"活"，靠这层极轻的色调统一 —— 免得她像贴上去的贴纸 */
  filter: saturate(0.94) drop-shadow(0 10px 24px hsl(26 24% 24% / 0.18));
  transition: filter 0.35s ease, opacity 0.35s ease;
  /* 视线跟随的位移要跟手，不能有延迟感；呼吸动画走单独一层 */
  will-change: transform;
}

/* 脚下的淡淡光晕：把角色和页面背景"接"起来，不然悬空感很重。
   Q 版是"贴纸感"画风、裙摆直接贴底，所以这层要压得比等身立绘更淡。 */
.cs-glow {
  position: absolute;
  left: 50%;
  bottom: -2px;
  width: 94px;
  height: 14px;
  transform: translateX(-50%);
  border-radius: 999px;
  background: radial-gradient(ellipse, hsla(var(--ph), var(--ps), var(--pl), 0.16), transparent 70%);
  filter: blur(3px);
  pointer-events: none;
  /* 光晕跟着呼吸一起收放。
     人物往上浮时脚底接触面应该变小（离地了），所以稍微收窄 + 变淡。
     周期必须和 cs-breathe 完全一致，差一帧都会看出"影子不跟人走"。 */
  animation: cs-glow-breathe 4.4s ease-in-out infinite;
}
@keyframes cs-glow-breathe {
  0%, 100% { transform: translateX(-50%) scaleX(1); opacity: 1; }
  50% { transform: translateX(-50%) scaleX(0.9); opacity: 0.78; }
}

/* 呼吸：整张图极小幅上下浮动。幅度必须小，大了就像"飘着的鬼影"。
   用 translate 而不是 transform —— transform 已经被内联的视线跟随占用了，
   两个属性是分开的，能叠在一起互不打架。 */
.cs-img {
  animation: cs-breathe 4.4s ease-in-out infinite;
  transform-origin: 50% 92%;
}
@keyframes cs-breathe {
  0%, 100% { translate: 0 0; }
  50% { translate: 0 -3px; }
}

/* ── 重心摇摆 ──
   只做上下浮动的话，看久了像"悬浮的贴纸"（因为位移是纯平移，脚底也跟着离开地面）。
   加一层绕脚底的极慢左右倾倒，才有"站在那儿、重心在挪"的感觉。

   幅度只有 ±0.45deg：180px 高的图，头部横移约 1.2px，肉眼刚好能察觉"在动"而不觉得晃。
   周期 7.3s 和呼吸的 4.4s 刻意取不成整数比，两个波形很久才对上一次，看不出循环。 */
.cs-sway {
  display: block;
  transform-origin: 50% 94%;
  animation: cs-sway 7.3s ease-in-out infinite;
  will-change: rotate;
}
@keyframes cs-sway {
  0%, 100% { rotate: -0.45deg; }
  50% { rotate: 0.45deg; }
}

/* 页面不可见时全停（摇摆和光晕也要算进来，不然切回来时相位已经跑偏） */
.is-paused .cs-img,
.is-paused .cs-sway,
.is-paused .cs-glow,
.is-paused .cs-dots i,
.is-paused .cs-zzz i {
  animation-play-state: paused;
}

/* 拖拽期间冻结：图不再自己动，鼠标抓上去才有"抓住了"的实感 */
.is-frozen .cs-img,
.is-frozen .cs-sway,
.is-frozen .cs-glow {
  animation-play-state: paused;
}
/* 拖拽时给一点"被拎起来"的反馈，松手自然恢复 */
.is-frozen .cs-img {
  filter: saturate(0.94) drop-shadow(0 16px 30px hsl(26 24% 24% / 0.26));
  transition: filter 0.18s ease;
}

/* ── 思考三点 ── */
.cs-dots {
  position: absolute;
  top: 2px;
  left: -6px;
  display: none;
  gap: 3px;
}
.cs-dots i {
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: var(--primary);
  box-shadow: 0 0 0 2px hsl(38 30% 96% / 0.7);
  animation: cs-dot 1.1s ease-in-out infinite;
}
.dark .cs-dots i { box-shadow: 0 0 0 2px hsl(28 10% 10% / 0.7); }
.cs-dots i:nth-child(2) { animation-delay: 0.15s; }
.cs-dots i:nth-child(3) { animation-delay: 0.3s; }
@keyframes cs-dot {
  0%, 100% { transform: translateY(0); opacity: 0.3; }
  40% { transform: translateY(-5px); opacity: 1; }
}

/* ── 打盹的 Z ── */
.cs-zzz {
  position: absolute;
  top: -4px;
  right: 4px;
  display: none;
  width: 16px;
  height: 16px;
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}
.cs-zzz i {
  position: absolute;
  right: 0;
  bottom: 0;
  font-style: normal;
  opacity: 0;
  animation: cs-zzz 2.8s ease-out infinite;
}
.cs-zzz i:nth-child(2) { animation-delay: 0.9s; font-size: 10px; }
.cs-zzz i:nth-child(3) { animation-delay: 1.8s; font-size: 8px; }
@keyframes cs-zzz {
  0% { opacity: 0; transform: translate(0, 0) scale(0.8); }
  25% { opacity: 0.85; }
  100% { opacity: 0; transform: translate(7px, -18px) scale(1.05); }
}

/* ── 各状态 ─────────────────────────────────────────── */

/* hint：第一次见，招呼两下 */
.s-hint .cs-img {
  animation: cs-hop 1.5s ease-in-out 2, cs-breathe 4.4s ease-in-out infinite;
}

@keyframes cs-hop {
  0%, 100% { translate: 0 0; }
  30% { translate: 0 -8px; }
  62% { translate: 0 0; }
}

/* thinking / tools：慢下来、略微倾斜、三点在跳 —— "在动脑"的体感 */
.s-thinking .cs-img,
.s-tools .cs-img {
  animation-duration: 5.6s;
  filter: saturate(0.94) drop-shadow(0 10px 24px hsl(26 24% 24% / 0.18)) brightness(0.99);
}
.s-thinking .cs-dots,
.s-tools .cs-dots { display: flex; }
/* typing：比思考快得多的小幅抖动，一眼看出"在出字" */
.s-typing .cs-img {
  animation: cs-typing 0.62s ease-in-out infinite;
}
@keyframes cs-typing {
  0%, 100% { translate: 0 0; }
  50% { translate: 0 -3px; }
}

/* done：蹦一下 + 提亮一点点，像"说完了" */
.s-done .cs-img {
  animation: cs-celebrate 0.62s cubic-bezier(0.34, 1.56, 0.64, 1);
  filter: saturate(1.02) drop-shadow(0 12px 26px hsl(26 24% 24% / 0.22)) brightness(1.03);
}
@keyframes cs-celebrate {
  0% { translate: 0 0; }
  42% { translate: 0 -9px; }
  100% { translate: 0 0; }
}

/* error：往下沉一下，不出红色 —— 只是没答上来，不是报错 */
.s-error .cs-img {
  animation: cs-sink 0.65s ease-out;
  filter: saturate(0.72) drop-shadow(0 8px 20px hsl(26 24% 24% / 0.16)) brightness(0.96);
}
@keyframes cs-sink {
  0% { translate: 0 0; }
  38% { translate: 0 4px; }
  100% { translate: 0 0; }
}

/* sleep：呼吸更慢、整体压暗、Z 往上飘 */
.is-sleeping .cs-img {
  animation-duration: 7s;
  filter: saturate(0.55) drop-shadow(0 8px 20px hsl(26 24% 24% / 0.14)) brightness(0.9);
}
.is-sleeping .cs-zzz { display: block; }
.is-sleeping .cs-glow { opacity: 0.5; }

/* 尊重系统「减少动效」 */
@media (prefers-reduced-motion: reduce) {
  .cs-img,
  .cs-sway,
  .cs-glow,
  .cs-dots i,
  .cs-zzz i {
    animation: none;
    transition: none;
  }
}

/* 手机上缩一档：Q 版是近方形，180px 在 375 宽的屏幕上会压住正文 */
@media (max-width: 640px) {
  .cs-img {
    width: 132px;
  }
  .cs-dots { left: -2px; }
}
</style>
