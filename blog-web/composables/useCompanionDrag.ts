/**
 * 桌宠拖拽（P2）
 *
 * 抽成独立 composable 的原因：CompanionEntry 已经背着「定位 / 选画笔 / 状态灌注 /
 * 首次招呼 / 面板开关」五件事了，再塞进指针事件和边界计算，那个文件就没法读了。
 *
 * 设计上的四个关键决定：
 *
 * 1. **拖拽只改最外层容器的 left/top，不碰任何内层 transform。**
 *    立绘内部三条通道已经被占满：`.cs-sway` 用 rotate 做摇摆、`.cs-img` 用 translate
 *    做呼吸、内联 style 被视线跟随用掉。拖拽走外层坐标，四者互不干扰。
 *
 * 2. **位置用 `null` 表示「没拖过，走默认右下角」。**
 *    服务端不知道视口宽高，如果一开始就 inline 一个算出来的 left/top，
 *    SSR 和客户端第一次渲染对不上，会报 hydration mismatch。
 *    所以：没拖过 → 完全交给 CSS；拖过 → 才切成 absolute 坐标。
 *
 * 3. **松手磁吸 + 出界夹取，两道都用。**
 *    只 clamp 不磁吸，角色会停在离边缘 3px 那种"卡住一半"的尴尬位置；
 *    只磁吸不 clamp，甩得太快会直接飞到视口外，用户再也找不回来。
 *
 * 4. **手机完全不启用。**
 *    移动端面板是 100vw × 78vh 的底部抽屉，跟着立绘跑没有意义；
 *    而且 `touch-action` 一旦设成 none，从角色位置起手就滚不动页面了。
 */

const STORE_KEY = 'companion-dock-pos'
/** 离视口边缘至少留这么多，别贴着边看不见影子 */
const MARGIN = 8
/** 松手时离边缘多近才吸附 */
const SNAP = 40
/** 超过这个位移才算拖拽，否则算点击 */
const DRAG_THRESHOLD = 5
/** 桌宠宽度断点：和组件里的 @media 保持一致 */
const DESKTOP_MIN = 640

export interface DockPos {
  x: number
  y: number
}

export function useCompanionDrag(opts: {
  dockEl: Ref<HTMLElement | null>
  width: number
  height: number
}) {
  const pos = ref<DockPos | null>(null)
  /** 是否正在拖（给子组件做冻结用） */
  const dragging = ref(false)
  /**
   * 刚拖完的那一小段时间。
   * 浏览器在 pointerup 之后**一定**还会补一个 click，
   * 不挡住的话「拖一下」会顺手触发一次「戳一下」，气泡无缘无故冒出来。
   */
  const justDragged = ref(false)
  const isDesktop = ref(true)

  let suppressTimer: ReturnType<typeof setTimeout> | null = null

  function viewport() {
    return {
      vw: typeof window === 'undefined' ? 1280 : window.innerWidth,
      vh: typeof window === 'undefined' ? 800 : window.innerHeight,
    }
  }

  function clamp(p: DockPos): DockPos {
    const { vw, vh } = viewport()
    return {
      x: Math.min(Math.max(p.x, MARGIN), Math.max(MARGIN, vw - opts.width - MARGIN)),
      y: Math.min(Math.max(p.y, MARGIN), Math.max(MARGIN, vh - opts.height - MARGIN)),
    }
  }

  /** 松手时靠近左右边缘就往边上吸。上下不吸 —— 底部是用户的视线落点，别硬拉 */
  function snap(p: DockPos): DockPos {
    const { vw } = viewport()
    let x = p.x
    if (x < SNAP) x = MARGIN
    else if (x + opts.width > vw - SNAP) x = vw - opts.width - MARGIN
    return clamp({ x, y: p.y })
  }

  function persist() {
    if (!pos.value) return
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(pos.value))
    } catch {
      /* 隐私模式 / 存储满：位置记不住不影响用，静默跳过 */
    }
  }

  function restore() {
    try {
      const raw = localStorage.getItem(STORE_KEY)
      if (!raw) return
      const v = JSON.parse(raw)
      if (Number.isFinite(v?.x) && Number.isFinite(v?.y)) {
        // 上次存的坐标可能是在更大的窗口里留下的，这里必须重新 clamp
        pos.value = clamp({ x: Number(v.x), y: Number(v.y) })
      }
    } catch {
      /* 脏数据就当没存过，回默认位置 */
    }
  }

  /**
   * 把当前位置「固化」成绝对坐标。
   * 第一次拖拽时 pos 还是 null（元素由 CSS right/bottom 定位），
   * 得先量一次它的实际位置，否则第一帧会瞬移到左上角。
   */
  function materialize(): DockPos | null {
    if (pos.value) return pos.value
    const el = opts.dockEl.value
    if (!el) return null
    const r = el.getBoundingClientRect()
    pos.value = clamp({ x: r.left, y: r.top })
    return pos.value
  }

  /** 由外部在 pointerdown 里调用（外部先判断点的是不是角色本体） */
  function startDrag(e: PointerEvent) {
    if (!isDesktop.value) return
    if (e.button !== 0 && e.pointerType === 'mouse') return
    const origin = materialize()
    if (!origin) return

    const startX = e.clientX
    const startY = e.clientY
    let moved = false

    const move = (ev: PointerEvent) => {
      const dx = ev.clientX - startX
      const dy = ev.clientY - startY
      if (!moved) {
        // 手指/鼠标轻微抖动不该被当成拖拽，否则每次点击都会挪一两个像素
        if (Math.hypot(dx, dy) < DRAG_THRESHOLD) return
        moved = true
        dragging.value = true
        // 拖的时候整页别选中文字（不然会拖出一片蓝色选区）
        document.body.style.userSelect = 'none'
      }
      pos.value = clamp({ x: origin.x + dx, y: origin.y + dy })
    }

    const end = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', end)
      window.removeEventListener('pointercancel', end)
      document.body.style.userSelect = ''
      if (!moved) return

      dragging.value = false
      justDragged.value = true
      if (suppressTimer) clearTimeout(suppressTimer)
      // 300ms 足够盖住 pointerup 之后那个补发的 click，又不至于让下一次点击被吞
      suppressTimer = setTimeout(() => { justDragged.value = false }, 300)

      if (pos.value) {
        pos.value = snap(pos.value)
        persist()
      }
    }

    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', end)
    window.addEventListener('pointercancel', end)
  }

  /** 面板打开时用：保证「面板在立绘左上」真的放得下，放不下就把整体往里推 */
  function ensureRoomFor(panelW: number, panelH: number) {
    if (!isDesktop.value || !pos.value) return
    const { vw, vh } = viewport()
    const p = { ...pos.value }
    // 面板右下角对齐立绘右上角，所以立绘左侧至少要留出面板宽度
    const needX = panelW - opts.width + MARGIN
    const needY = panelH + 12 + MARGIN
    if (p.x < needX) p.x = Math.min(needX, Math.max(MARGIN, vw - opts.width - MARGIN))
    if (p.y < needY) p.y = Math.min(needY, Math.max(MARGIN, vh - opts.height - MARGIN))
    const next = clamp(p)
    // 视口实在太小（比如 800×600 的窗口放 400×580 的面板）就算了，
    // 硬推只会把她推到屏幕外，不如让面板自己 overflow
    if (next.x !== pos.value.x || next.y !== pos.value.y) pos.value = next
  }

  const dockStyle = computed(() => {
    if (!pos.value || !isDesktop.value) return {} as Record<string, string>
    return {
      left: `${pos.value.x}px`,
      top: `${pos.value.y}px`,
      // 必须显式清掉，否则 right/bottom 和 left/top 同时生效，
      // 元素会被两侧拉扯（宽度固定时表现为"不动"或"拉伸"，很难排查）
      right: 'auto',
      bottom: 'auto',
    }
  })

  function onResize() {
    isDesktop.value = window.innerWidth >= DESKTOP_MIN
    if (!isDesktop.value) return
    if (pos.value) pos.value = clamp(pos.value)
  }

  onMounted(() => {
    if (import.meta.server) return
    isDesktop.value = window.innerWidth >= DESKTOP_MIN
    if (!isDesktop.value) return
    restore()
    window.addEventListener('resize', onResize)
  })

  onBeforeUnmount(() => {
    if (import.meta.server) return
    window.removeEventListener('resize', onResize)
    if (suppressTimer) clearTimeout(suppressTimer)
  })

  return { pos, dragging, justDragged, isDesktop, dockStyle, startDrag, ensureRoomFor }
}
