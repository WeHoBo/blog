<template>
  <!--
    AI 助手的 CSS 画笔（P1）

    为什么要有这一版：Live2D 只能在桌面、要 8MB 模型、要 WebGL。
    手机、低端机、模型加载失败、用户关了动效 —— 这些情况都得有个东西站在右下角。
    这就是那个「东西」：一张内联 SVG，不到 4KB，零请求、零依赖、不碰 WebGL。

    ⚠️ 这一层只负责「画」，状态从 useCompanionState 读，交互往上 emit。
       定位、气泡、显隐判断都在 CompanionEntry 里 —— 换引擎时只换这个组件。
  -->
  <button
    ref="root"
    type="button"
    class="cc"
    :class="[`s-${state}`, { 'is-paused': pageHidden }]"
    :aria-label="ariaText"
    :title="ariaText"
    @click="emit('activate')"
  >
    <svg class="cc-fish" viewBox="0 0 68 46" aria-hidden="true">
      <g class="cc-breathe">
        <!-- 尾鳍：燕尾。鱼这个形状最容易被认出来的就是它，比身体轮廓更重要 -->
        <path class="cc-tail" d="M52 23 66 11l-4 12 4 12Z" fill="var(--primary)" opacity=".9" />
        <!-- 背鳍 -->
        <path d="M25 13.5C29 4.5 41 3 45 12Z" fill="var(--primary)" opacity=".55" />
        <!-- 胸鳍：打盹时会垂下来 -->
        <path class="cc-fin" d="M28.5 30.5C32 36.5 38 37.5 41.5 33.5Z" fill="var(--primary)" opacity=".5" />
        <!-- 身体：梭形（头钝尾细），别用正椭圆 —— 椭圆看起来像豆子不像鱼 -->
        <path
          d="M5.5 23c3-11 17-16.5 29-13.5C45 12 51.5 16.5 53.5 23 51.5 29.5 45 34 34 35.5 22.5 37 8.5 34 5.5 23Z"
          fill="var(--primary)"
        />
        <!-- 腹部高光：给扁平的色块一点体积 -->
        <path d="M12 27c8 5 22 5.5 33 0-4 5.5-12 8.5-19 8.5-8 0-12-4.5-14-8.5Z" fill="#fff" opacity=".18" />
        <!-- 嘴：一条短线，有它整条鱼才"朝左"得明确 -->
        <path d="M6.8 25.4q2.6 1.9 5.2.2" fill="none" stroke="#2b2723" stroke-width="1.1" stroke-linecap="round" opacity=".4" />

        <!-- 眼睛三套：睁着 / 笑成弯 / 出错打叉，靠状态切 opacity -->
        <g class="cc-eye-normal">
          <g class="cc-blink">
            <circle cx="15.5" cy="20" r="4.7" fill="#fff" />
            <g class="cc-pupil" :style="pupilStyle">
              <circle cx="16.2" cy="20" r="2.4" fill="#2b2723" />
              <circle cx="15.1" cy="19.1" r=".9" fill="#fff" opacity=".95" />
            </g>
          </g>
        </g>
        <path class="cc-eye-happy" d="M11 20.6q4.4-5.2 8.8 0" fill="none" stroke="#2b2723" stroke-width="1.9" stroke-linecap="round" />
        <g class="cc-eye-x" stroke="#2b2723" stroke-width="1.9" stroke-linecap="round">
          <path d="M11.6 16.6l7.6 7.6M19.2 16.6l-7.6 7.6" />
        </g>
      </g>
    </svg>

    <!-- 思考三点：跟气泡分工 —— 气泡说「在干什么」，这里只是「在动脑」的体感 -->
    <span class="cc-dots" aria-hidden="true"><i /><i /><i /></span>

    <!-- 打盹的 Z：90 秒没人理才出现 -->
    <span class="cc-zzz" aria-hidden="true"><i>z</i><i>z</i><i>z</i></span>
  </button>
</template>

<script setup lang="ts">
import { useCompanionState } from '~/composables/useCompanionState'

const emit = defineEmits<{ (e: 'activate'): void }>()

const { state, pageHidden } = useCompanionState()

const root = ref<HTMLElement | null>(null)

/**
 * 视线跟随：瞳孔朝鼠标方向偏移，最多 2.2px。
 * 只在 idle / hint 生效 —— 思考时眼珠应该盯着"答案"而不是跟着鼠标乱转。
 */
const pupils = ref({ x: 0, y: 0 })
const pupilStyle = computed(() => ({
  transform: `translate(${pupils.value.x}px, ${pupils.value.y}px)`,
}))

let raf = 0
function onMove(e: PointerEvent) {
  if (raf) return
  raf = requestAnimationFrame(() => {
    raf = 0
    const el = root.value
    if (!el) return
    if (state.value !== 'idle' && state.value !== 'hint') return
    const r = el.getBoundingClientRect()
    const dx = e.clientX - (r.left + r.width / 2)
    const dy = e.clientY - (r.top + r.height / 2)
    const dist = Math.hypot(dx, dy)
    if (dist < 1) return
    // 越近偏移越大，超过 280px 封顶 —— 远处鼠标不该让它抖
    const k = Math.min(dist, 280) / 280
    pupils.value = {
      x: +((dx / dist) * 2.2 * k).toFixed(2),
      y: +((dy / dist) * 1.6 * k).toFixed(2),
    }
  })
}

onMounted(() => {
  // 触屏没有 hover 语义，pointermove 也几乎不触发，装了白装
  if (import.meta.server || window.matchMedia('(hover: none)').matches) return
  window.addEventListener('pointermove', onMove, { passive: true })
})

onBeforeUnmount(() => {
  if (import.meta.server) return
  window.removeEventListener('pointermove', onMove)
  if (raf) cancelAnimationFrame(raf)
})

/** 无障碍：读屏用户看不到小鱼，得靠这句话知道这是干什么的 */
const ariaText = computed(() => {
  switch (state.value) {
    case 'thinking': return 'AI 助手正在思考，点一下打开对话'
    case 'tools': return 'AI 助手正在检索站内文章，点一下打开对话'
    case 'typing': return 'AI 助手正在回答，点一下打开对话'
    case 'done': return 'AI 助手已回答，点一下查看'
    case 'error': return 'AI 助手这次没能回答，点一下重试'
    case 'sleep': return 'AI 助手在打盹，点一下唤醒'
    default: return '打开 AI 助手'
  }
})
</script>

<style scoped>
.cc {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 纸感托座：不装白底，用主题色极淡的一层，和整站暖纸调子一致 */
  background: var(--primary-bg);
  border: 1px solid hsla(var(--ph), var(--ps), var(--pl), 0.26);
  box-shadow: 0 6px 22px hsla(var(--ph), var(--ps), var(--pl), 0.16);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}
.cc:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px hsla(var(--ph), var(--ps), var(--pl), 0.22);
}
.cc:active { transform: scale(0.95); }
.cc:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}

.cc-fish {
  width: 66px;
  height: 45px;
  overflow: visible;
}

/* ── 通用动画 ───────────────────────────────────────── */
.cc-breathe,
.cc-tail,
.cc-blink,
.cc-fin,
.cc-pupil {
  transform-box: fill-box;
  transform-origin: center;
}

.cc-breathe {
  animation: cc-breathe 3.6s ease-in-out infinite;
}
.cc-tail {
  /* 绕尾巴根部转，绕自身中心转会变成"原地抖" */
  transform-origin: 6% 50%;
  animation: cc-tail 2.4s ease-in-out infinite;
}
.cc-blink {
  animation: cc-blink 5.6s ease-in-out infinite;
}
/* 瞳孔要跟 JS 的 translate 共存，只能过渡不能动画 */
.cc-pupil {
  transition: transform 0.28s ease-out;
}

@keyframes cc-breathe {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.035, 0.975); }
}
@keyframes cc-tail {
  0%, 100% { transform: rotate(0deg) scaleX(1); }
  50% { transform: rotate(-7deg) scaleX(0.9); }
}
@keyframes cc-blink {
  /* 大部分时间睁着，只在每轮末尾快速眨两下 */
  0%, 90%, 100% { transform: scaleY(1); }
  92%, 95% { transform: scaleY(0.06); }
}

/* 页面不可见时全停 —— 切到别的标签页还在跑动画纯属白烧电 */
.is-paused .cc-breathe,
.is-paused .cc-tail,
.is-paused .cc-blink,
.is-paused .cc-dots i,
.is-paused .cc-zzz i {
  animation-play-state: paused;
}

/* ── 眼睛三套的默认可见性 ────────────────────────────── */
.cc-eye-happy,
.cc-eye-x {
  opacity: 0;
  transition: opacity 0.18s ease;
}

/* ── 思考三点 ──────────────────────────────────────── */
.cc-dots {
  position: absolute;
  top: 9px;
  right: 13px;
  display: none;
  gap: 3px;
}
.cc-dots i {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: var(--primary);
  animation: cc-dot 1.1s ease-in-out infinite;
}
.cc-dots i:nth-child(2) { animation-delay: 0.15s; }
.cc-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes cc-dot {
  0%, 100% { transform: translateY(0); opacity: 0.35; }
  40% { transform: translateY(-4px); opacity: 1; }
}

/* ── 打盹的 Z ──────────────────────────────────────── */
.cc-zzz {
  position: absolute;
  top: 6px;
  right: 9px;
  /* i 是绝对定位，撑不开父级 —— 不给尺寸三个 Z 会全部叠在一个点上 */
  width: 14px;
  height: 14px;
  display: none;
  color: var(--primary);
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}
.cc-zzz i {
  position: absolute;
  right: 0;
  bottom: 0;
  font-style: normal;
  opacity: 0;
  animation: cc-zzz 2.8s ease-out infinite;
}
.cc-zzz i:nth-child(2) { animation-delay: 0.9s; font-size: 9px; }
.cc-zzz i:nth-child(3) { animation-delay: 1.8s; font-size: 8px; }

@keyframes cc-zzz {
  0% { opacity: 0; transform: translate(0, 0) scale(0.8); }
  25% { opacity: 0.9; }
  100% { opacity: 0; transform: translate(6px, -16px) scale(1.1); }
}

/* ── 各状态 ─────────────────────────────────────────── */

/* hint：第一次见，招呼一下 —— 轻轻跳两下就够，别像弹窗那样抢注意力 */
.s-hint .cc-fish {
  animation: cc-hop 1.6s ease-in-out 2;
}
.s-hint {
  background: hsla(var(--ph), var(--ps), var(--pl), 0.16);
  border-color: hsla(var(--ph), var(--ps), var(--pl), 0.42);
}
@keyframes cc-hop {
  0%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
  60% { transform: translateY(0); }
}

/* thinking / tools：头微微歪，三点在跳 */
.s-thinking .cc-breathe,
.s-tools .cc-breathe {
  animation-duration: 2.2s;
}
.s-thinking .cc-dots,
.s-tools .cc-dots { display: flex; }
.s-thinking .cc-fish,
.s-tools .cc-fish {
  transform: rotate(-5deg);
  transition: transform 0.35s ease;
}

/* typing：比思考快得多的小幅抖动 —— 一眼能看出「在出字」而不是「在等」 */
.s-typing .cc-fish {
  animation: cc-typing 0.5s ease-in-out infinite;
}
@keyframes cc-typing {
  0%, 100% { transform: translateY(0) rotate(-2deg); }
  50% { transform: translateY(-2.5px) rotate(-2deg); }
}

/* done：笑眼 + 蹦一下，1.8 秒后自己回 idle */
.s-done .cc-eye-normal { opacity: 0; }
.s-done .cc-eye-happy { opacity: 1; }
.s-done .cc-fish {
  animation: cc-celebrate 0.55s cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes cc-celebrate {
  0% { transform: translateY(0) scale(1); }
  40% { transform: translateY(-7px) scale(1.04); }
  100% { transform: translateY(0) scale(1); }
}

/* error：× 眼 + 往下沉一下，但不要变成「报错弹窗」那种红 —— 只是没答上来 */
.s-error .cc-eye-normal { opacity: 0; }
.s-error .cc-eye-x { opacity: 1; }
.s-error .cc-fish {
  animation: cc-sink 0.6s ease-out;
}
.s-error {
  border-color: hsla(var(--ph), var(--ps), var(--pl), 0.5);
}
@keyframes cc-sink {
  0% { transform: translateY(0); }
  35% { transform: translateY(3.5px); }
  100% { transform: translateY(0); }
}

/* sleep：眼睛闭上（用眨眼动画定格在闭着的帧），胸鳍垂下，Z 往上飘 */
.s-sleep .cc-blink {
  animation: none;
  transform: scaleY(0.06);
}
.s-sleep .cc-breathe {
  animation-duration: 5.6s;
}
.s-sleep .cc-tail {
  animation-duration: 4.4s;
}
.s-sleep .cc-fin {
  transform: rotate(18deg);
  transition: transform 0.5s ease;
}
.s-sleep .cc-zzz { display: block; }
.s-sleep {
  background: hsla(var(--ph), var(--ps), var(--pl), 0.06);
  box-shadow: 0 4px 14px hsla(var(--ph), var(--ps), var(--pl), 0.1);
}

/* 尊重系统「减少动效」：不转场、不飘，但状态照样看得见（眼睛/气泡还在变） */
@media (prefers-reduced-motion: reduce) {
  .cc,
  .cc-fish,
  .cc-breathe,
  .cc-tail,
  .cc-blink,
  .cc-dots i,
  .cc-zzz i {
    animation: none;
    transition: none;
  }
  .s-sleep .cc-blink { transform: scaleY(0.06); }
}

/* 手机上再缩一档：右下角是拇指区，84px 容易误触 */
@media (max-width: 640px) {
  .cc {
    width: 68px;
    height: 68px;
  }
  .cc-fish {
    width: 54px;
    height: 37px;
  }
}
</style>
