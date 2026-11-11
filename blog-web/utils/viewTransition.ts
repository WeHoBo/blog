/**
 * 主题切换的圆形扩散过渡（P2-10）
 *
 * 做法：把「换主题」这个瞬间交给浏览器 View Transitions API —— 它先给旧界面拍一张快照，
 * 等回调里的 DOM 改完再拍一张新的，然后在两张快照之间做动画。我们只负责告诉它
 * 「从哪个点开始扩散、半径要多大」，动画本身由 CSS 的 `::view-transition-new(root)` 完成。
 *
 * 三个必须守住的降级：
 * 1. 浏览器不支持 `startViewTransition`（Safari 17 以下、Firefox 部分版本）→ 直接切，不报错；
 * 2. 用户开了「减少动态效果」→ 直接切，不放动画（这是无障碍要求，不是可选优化）；
 * 3. SSR / 没 `window` → 直接切。
 *
 * 注意：这里**不引入 vue**。留下纯函数是为了能在 node 里被 check 脚本直接编译执行。
 */

export interface ViewTransitionOrigin {
  x: number
  y: number
}

type StartViewTransition = (cb: () => void | Promise<void>) => { finished?: Promise<void> }

const REDUCED_MOTION = '(prefers-reduced-motion: reduce)'

/** 当前环境是否真的会播放过渡（测试与调试用） */
export function supportsViewTransition(): boolean {
  if (typeof document === 'undefined' || typeof window === 'undefined') return false
  return typeof (document as unknown as Record<string, unknown>).startViewTransition === 'function'
}

/** 用户是否要求减少动态效果 */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia(REDUCED_MOTION).matches
}

/**
 * 从元素矩形算出圆心（取正中）。
 * 用元素而不是鼠标坐标：键盘/读屏用户触发时没有可信的鼠标位置，
 * 元素矩形两种情况都拿得到。
 */
export function originFromElement(el: Element | null | undefined): ViewTransitionOrigin | null {
  if (!el || typeof el.getBoundingClientRect !== 'function') return null
  const rect = el.getBoundingClientRect()
  if (!rect || (!rect.width && !rect.height)) return null
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
}

/**
 * 扩散圆的半径：要盖住「离圆心最远的那个视口角」。
 * 直接用 150vmax 也能盖住，但那样小按钮点击时前半段几乎看不出扩散；
 * 算出来的半径让动画的视觉速度更均匀。
 */
export function revealRadius(origin: ViewTransitionOrigin, width: number, height: number): number {
  const dx = Math.max(origin.x, width - origin.x)
  const dy = Math.max(origin.y, height - origin.y)
  return Math.hypot(Math.max(dx, 0), Math.max(dy, 0))
}

/**
 * 包一层视图过渡。
 *
 * @param origin 扩散圆心（视口坐标）；传 null 时用视口正中
 * @param update 真正改状态的函数，可以返回 Promise —— 过渡会等它 resolve 再拍「新快照」
 */
export function withViewTransition(
  origin: ViewTransitionOrigin | null,
  update: () => void | Promise<void>
): Promise<void> {
  // 只允许 update 执行一次：startViewTransition 在「已有过渡进行中」时会同步抛错，
  // 那种情况下 cb 可能已经跑过一次了，再跑一次就会把主题切回去
  let done = false
  const run = () => {
    if (done) return Promise.resolve()
    done = true
    return Promise.resolve(update())
  }

  if (!supportsViewTransition() || prefersReducedMotion()) return run()

  const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0
  const center = origin || { x: viewportWidth / 2, y: viewportHeight / 2 }

  // 圆心钳在视口内：元素被滚动出屏幕时 rect 可能是负数，钳一下避免圆从看不见的地方长出来
  const cx = Math.min(Math.max(center.x, 0), viewportWidth)
  const cy = Math.min(Math.max(center.y, 0), viewportHeight)

  const root = document.documentElement
  root.style.setProperty('--vt-cx', `${Math.round(cx)}px`)
  root.style.setProperty('--vt-cy', `${Math.round(cy)}px`)
  root.style.setProperty('--vt-r', `${Math.ceil(revealRadius({ x: cx, y: cy }, viewportWidth, viewportHeight))}px`)

  const start = (document as unknown as { startViewTransition: StartViewTransition }).startViewTransition
  try {
    const transition = start.call(document, run)
    // finished 在过渡被跳过时也会 resolve；不 catch 会产生未处理的 rejection
    return Promise.resolve(transition?.finished).catch(() => undefined)
  } catch {
    // 有的实现会在「已有过渡进行中」时同步抛错，此时退回直接切；run 内部有一次保护
    return run()
  }
}
