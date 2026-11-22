<template>
  <!--
    AI 助手入口（P2）—— 唯一入口

    2026-11-17 起，右下角那个 56×56 的渐变圆按钮被删掉了，换成这个「角色」：
    静态按钮只能表达「这里有东西可以点」，而聊天其实有 6 种进行中的状态
    （在思考、在查资料、在出字、答完了、失败了、睡着了）。
    这些信息以前只能打开面板才看得到，现在站在页面上就能看出来。

    P2（本次）在这个基础上做了三件事：
      1. **可拖拽**。位置存 localStorage，松手靠边会吸附。
      2. **点一下不再直接开面板**，而是「戳」它一下（分四级吐槽）+ 冒出「点我问问题」按钮。
         ⚠️ 双击仍然直接开面板 —— 老用户的手感不能断。
      3. **面板锚定在立绘左上**（`.cc-panel-slot`），拖这个容器时面板零计算自动跟随。

    为什么面板要放进这个容器，而不是继续 fixed 在右下角：
       原来面板和立绘占**完全相同的坐标**，所以面板一开必须把立绘藏起来。
       一旦要"一起移动"，那个设计就不成立了。改成锚定之后两者共存，
       而且气泡那句「好啦，看窗口 →」终于有了实际指向 —— 角色真的站在窗口下方。

    这一层只做四件事，不画任何图形：
      1. 定位 + 拖拽
      2. 选画笔（立绘 / CSS 小鱼，见 app.config.ts 的 companion.engine）
      3. 把「面板开关 / 页面可见性 / 首次招呼」灌给状态机
      4. 管住"戳一下"和"点我问问题"这两个交互
    画由 CompanionStandee 或 CompanionCss 负责，两者读同一个状态机。

    ⚠️ 为什么是「立绘」而不是真 Live2D：
       大肥鱼模型（DeepSeek-chan）的使用须知禁止重新分发完整模型文件，
       而网页 Live2D 必须把 moc3 发给每个访客 —— 技术上无法规避。
       所以这里放的是自己渲染出来的一张 PNG，属于条款允许的二次创作。
       形象署名：原画/拆分 神宫凉子 · Live2D 建模 霓虹猫_NeonNeko。
  -->
  <div
    v-if="enabled"
    ref="dockEl"
    class="cc-dock"
    :class="{ 'is-dragging': dragging }"
    :style="dockStyleFull"
    @pointerdown="onPointerDown"
    @dblclick="onDblClick"
  >
    <!-- 面板槽：位置完全由 CSS 决定（右下角对齐立绘右上角），拖拽容器时自动跟随 -->
    <div class="cc-panel-slot">
      <slot name="panel" />
    </div>

    <CompanionBubble :text="bubbleText" :side="bubbleSide" :offset-y="renderer === 'standee' ? '26%' : '50%'" />

    <!-- 戳一下才出现的邀请按钮。面板一开就收起来，不然会被面板压住 -->
    <Transition name="cc-ask-pop">
      <button v-if="showAsk && !open" class="cc-ask" type="button" @click.stop="openPanel">
        点我问问题
      </button>
    </Transition>

    <!-- 立绘加载失败（图挂了 / 断网）就退回 CSS 小鱼，入口不会凭空消失 -->
    <CompanionStandee
      v-if="renderer === 'standee'"
      :frozen="dragging"
      @activate="onPoke"
      @fail="onImgFail"
    />
    <CompanionCss v-else @activate="onPoke" />
  </div>
</template>

<script setup lang="ts">
import { useCompanionState } from '~/composables/useCompanionState'
import { useCompanionDrag } from '~/composables/useCompanionDrag'

const props = defineProps<{ open: boolean; panelW?: number; panelH?: number }>()
const emit = defineEmits<{ (e: 'activate'): void }>()

const { bubbleText, setHint, setIdle, setPanelOpen, setPageHidden, wake, clearTimers, poke } =
  useCompanionState()

/**
 * 用哪支画笔。
 * 默认跟着 app.config.ts 走；图挂了会自动降级到 css，且这一次访问不再重试
 * （重试只会让用户看到一个反复闪烁的破图）。
 */
const appConfig = useAppConfig() as any
const renderer = ref<'standee' | 'css'>(appConfig?.companion?.engine === 'css' ? 'css' : 'standee')

function onImgFail() {
  if (renderer.value === 'standee') renderer.value = 'css'
}

/**
 * 配置读取。
 * 用 useAppConfig 而不是硬编码：app.config.ts 是**运行时**配置，
 * 以后要临时关掉角色、或切换画笔，改这一处就行，不用重新 build 前端。
 * 没读到就按默认值来（开、立绘），保证任何情况下页面都不会因为配置缺失白屏。
 */
const enabled = computed(() => appConfig?.companion?.enabled !== false)

/* ── 拖拽 ── */
const dockEl = ref<HTMLElement | null>(null)
/* 尺寸和 CompanionStandee 里的 .cs-img 一致（180×167）。
   移动端会缩到 132px，但移动端根本不启用拖拽，所以不用跟着变。 */
const { pos, dragging, justDragged, isDesktop, dockStyle, startDrag, ensureRoomFor } = useCompanionDrag({
  dockEl,
  width: 180,
  height: 167,
})

/**
 * 气泡翻边：气泡最大 190px + 12px 间距 + 8px 安全边距 ≈ 210px。
 * 角色左边留不出这么多空间时，气泡就得翻到右边去，否则会整个跑出视口。
 */
const bubbleSide = computed<'left' | 'right'>(() => {
  if (!pos.value || !isDesktop.value) return 'left'
  return pos.value.x < 214 ? 'right' : 'left'
})

/**
 * 把容器纵向坐标透出成 CSS 变量，给面板算「我上面还剩多少空间」用。
 *
 * 为什么需要：面板 580 高、立绘 167 高，加起来 759。
 * 视口要是只有 700（笔记本很常见），面板一定从顶部冲出去，用户只看到半个窗口。
 * 实测在 860 高的视口里，立绘一旦离开最底部，面板顶就已经到 y=-94 了。
 *
 * ⚠️ 「没拖过」的情况不能写死一个 900px 了事：
 *    没拖过时容器走 CSS 的 right/bottom 定位，它的实际 top 是
 *    `视口高 - 立绘高 - 24`，视口一矮照样溢出。
 *    所以这里用等价的 CSS 表达式兜底，由浏览器自己算，SSR 也不用知道视口。
 */
const dockStyleFull = computed(() => ({
  ...dockStyle.value,
  '--dock-y':
    pos.value && isDesktop.value
      ? `${pos.value.y}px`
      : 'calc(100vh - 191px)' /* 167（立绘高）+ 24（右下角边距） */,
}))

/**
 * 只在点到角色本体时才开始拖。
 * 不能绑在整个容器上：面板槽也是容器的子元素，那样在输入框里按下鼠标拖动就会把整个桌宠拖走。
 * `.cs`（立绘）/ `.cc`（CSS 小鱼）是两支画笔各自的根元素 class。
 */
function onPointerDown(e: PointerEvent) {
  const t = e.target as HTMLElement | null
  if (!t || !t.closest('.cs, .cc')) return
  startDrag(e)
}

/* ── 戳一下 ── */
const showAsk = ref(false)
let askTimer: ReturnType<typeof setTimeout> | null = null

function armAskTimer() {
  if (askTimer) clearTimeout(askTimer)
  askTimer = setTimeout(() => { showAsk.value = false; askTimer = null }, 6000)
}

function onPoke() {
  // 拖完浏览器一定会补发一个 click，这里必须挡住，否则拖一下就冒气泡
  if (justDragged.value) return
  wake()
  poke()
  showAsk.value = true
  armAskTimer()
}

/** 双击 = 老手感：直接开面板。单击的两下 poke 会先闪一下，可以接受（比延迟 250ms 等判定好） */
function onDblClick(e: MouseEvent) {
  const t = e.target as HTMLElement | null
  if (!t || !t.closest('.cs, .cc')) return
  if (justDragged.value) return
  openPanel()
}

function openPanel() {
  showAsk.value = false
  if (askTimer) { clearTimeout(askTimer); askTimer = null }
  wake()
  emit('activate')
}

/* ── 面板开关 ── */
watch(
  () => props.open,
  (v) => {
    setPanelOpen(v)
    if (v) {
      setIdle()
      showAsk.value = false
      /* 面板从立绘左上展开，需要立绘左边至少空出一个面板宽度、上面空出一个面板高度。
         放不下就把整体往里推一点 —— 不推的话面板会直接怼到视口外，
         用户只看到半个窗口，还以为打不开。 */
      ensureRoomFor(props.panelW || 400, props.panelH || 580)
    }
  },
  { immediate: true },
)

/* ── 页面可见性：切走时动画全停（省电），切回来重新计时 ── */
function onVisibility() {
  setPageHidden(document.hidden)
}
onMounted(() => {
  if (import.meta.server) return
  onVisibility()
  document.addEventListener('visibilitychange', onVisibility)
})
onBeforeUnmount(() => {
  if (import.meta.server) return
  document.removeEventListener('visibilitychange', onVisibility)
  if (askTimer) clearTimeout(askTimer)
  clearTimers()
})

/**
 * 首次到访打招呼：一天一次。
 *
 * 为什么不用原来的 `ai-hint-dismissed`（永久只弹一次）：
 * 老 key 会让「升级前访问过的人」永远看不到新入口的招呼 —— 而入口本身换了个样子，
 * 不介绍一下反而更迷惑。改成按天存，成本只是每天一句 6 秒的气泡。
 */
const GREET_KEY = 'ai-companion-greeted'
/* 计时器存组件级变量，在顶层 onBeforeUnmount 里清 ——
   在 onMounted 回调里注册 onBeforeUnmount 是无效的（那时已没有活跃组件实例）。 */
let greetTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  if (import.meta.server) return
  try {
    const today = new Date().toISOString().slice(0, 10)
    if (localStorage.getItem(GREET_KEY) === today) return
    // 稍等一下再冒头，别和首屏内容抢注意力
    greetTimer = setTimeout(() => {
      setHint()
      localStorage.setItem(GREET_KEY, today)
    }, 2200)
  } catch {
    /* 隐私模式下 localStorage 会抛，静默跳过即可，不值得打断用户 */
  }
})

onBeforeUnmount(() => {
  if (greetTimer) clearTimeout(greetTimer)
})
</script>

<style scoped>
.cc-dock {
  position: fixed;
  right: 1.5rem;
  bottom: 1.5rem;
  /* 原来是 65（只有立绘），现在容器里还装着面板，得抬到和面板原来一样的层 */
  z-index: 80;
  /* 气泡用绝对定位挂在左边，所以这里必须是定位上下文 */
  display: block;
  /* 拖拽时整体跟手，不加过渡；只在"被推回视口"那种程序化移动时才需要动画 */
  touch-action: manipulation;
}

/* 面板槽：右下角对齐立绘右上角 = 面板整体落在立绘左上方。
   用 absolute 而不是 fixed —— fixed 不受父级坐标系影响，就不会跟着拖拽走了。 */
.cc-panel-slot {
  position: absolute;
  right: 0;
  bottom: calc(100% + 12px);
  z-index: 5;
}
/* 空槽（面板没开）不占任何位置，也不能吃掉指针事件 */
.cc-panel-slot:empty {
  display: none;
}

/* 恢复记忆位置、以及面板打开时"被推回视口"都是程序化移动，
   有过渡才不会让桌宠瞬移。拖拽时必须关掉 —— 否则手指和图像之间隔一层缓动，非常黏。 */
.cc-dock:not(.is-dragging) {
  transition: left 0.26s cubic-bezier(0.32, 0.72, 0.28, 1), top 0.26s cubic-bezier(0.32, 0.72, 0.28, 1);
}
@media (prefers-reduced-motion: reduce) {
  .cc-dock:not(.is-dragging) {
    transition: none;
  }
}

/* 指针提示：只有桌面端才暗示"可以拖" */
@media (min-width: 640px) {
  .cc-dock :deep(.cs),
  .cc-dock :deep(.cc) {
    cursor: grab;
  }
  .is-dragging :deep(.cs),
  .is-dragging :deep(.cc) {
    cursor: grabbing;
  }
}

/* ── 点我问问题 ── */
.cc-ask {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 10px);
  transform: translateX(-50%);
  z-index: 6;
  padding: 6px 13px;
  border: 0;
  border-radius: 999px;
  white-space: nowrap;
  background: hsl(var(--ph), var(--ps), var(--pl));
  color: #fff;
  font-size: 12px;
  line-height: 1.25;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 6px 18px hsla(var(--ph), var(--ps), var(--pl), 0.32);
  transition: background-color 0.18s ease, transform 0.18s ease;
}
.cc-ask:hover {
  background: var(--primary-dark, hsl(var(--ph), var(--ps), 32%));
  transform: translateX(-50%) translateY(-1px);
}
.cc-ask:active {
  transform: translateX(-50%) translateY(0);
}
.cc-ask:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}

.cc-ask-pop-enter-active {
  transition: opacity 0.18s ease, transform 0.24s cubic-bezier(0.34, 1.45, 0.64, 1);
}
.cc-ask-pop-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}
.cc-ask-pop-enter-from,
.cc-ask-pop-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(6px) scale(0.9);
}

/* 手机端贴边收紧一点，和原来的按钮保持一致的内边距 */
@media (max-width: 640px) {
  .cc-dock {
    right: 1rem;
    bottom: 1rem;
  }
  /* 手机上面板是接近全屏的底部抽屉，从立绘左上展开会直接溢出视口，
     所以这里改回"固定铺底"，和改造前的手感一致。 */
  .cc-panel-slot {
    position: fixed;
    left: 1rem;
    right: auto;
    bottom: 1rem;
  }
  .cc-ask {
    bottom: calc(100% + 6px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cc-ask-pop-enter-active,
  .cc-ask-pop-leave-active,
  .cc-ask {
    transition: none;
  }
}
</style>
