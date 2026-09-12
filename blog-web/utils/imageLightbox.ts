/**
 * 正文图片灯箱（零依赖手写实现，风格与组件内已有的 mermaid 放大遮罩保持一致）。
 *
 * 为什么不自建依赖 medium-zoom / PhotoSwipe：
 *   - medium-zoom 只能"放大"，没有多图切换与键盘导航；PhotoSwipe 功能全但会额外引入
 *     一个 JS + 一个 CSS 文件（还要覆盖它自带的暗色主题）；
 *   - 本站的诉求就是「点开看大图、多图左右翻、滚轮/双指缩放」，约 200 行可覆盖，
 *     且能直接复用 main.css 的暗色变量，不必再维护第三方主题皮肤。
 *
 * 用法：bindImageLightbox(root) —— 传入正文容器，内部给其中所有 <img> 挂上点击放大。
 * 幂等：重复调用不会给同一张图重复绑定（靠 dataset.zoomBound 标记）。
 */

const SCALE_MIN = 1
const SCALE_MAX = 5
const SWIPE_THRESHOLD = 50
/**
 * 原始尺寸同时小于这个值的图片视为图标/徽章，不劫持点击。
 * 用 naturalWidth/naturalHeight（资源本身尺寸）而不是 clientWidth（当前布局尺寸）：
 * 懒加载或尚未加载完时布局尺寸是 0，会把正常大图误判成小图标。
 */
const MIN_ZOOMABLE_SIZE = 48

/** 当前打开的灯箱清理函数；同一时刻只允许存在一个灯箱 */
let activeCleanup: (() => void) | null = null

/** 是不是图标/徽章这类不值得放大的小图 */
function isTinyIcon(img: HTMLImageElement): boolean {
  const w = img.naturalWidth
  const h = img.naturalHeight
  if (!w || !h) return false
  return w < MIN_ZOOMABLE_SIZE && h < MIN_ZOOMABLE_SIZE
}

interface ZoomEntry {
  img: HTMLImageElement
  src: string
  alt: string
}

function touchDistance(touches: TouchList): number {
  const dx = touches[0].clientX - touches[1].clientX
  const dy = touches[0].clientY - touches[1].clientY
  return Math.hypot(dx, dy)
}

/**
 * 决定这张图是否挂灯箱，并给出「该放大哪一张」。
 *
 * 这里要处理正文里真实存在的三种写法：
 *   1. ![截图](a.png)                  → 直接放大 a.png
 *   2. [![截图](thumb.png)](a.png)     → 链接指向大图，应该放大 a.png（而不是缩略图）
 *   3. [![截图](a.png)](https://项目主页) → 链接指向别处，**必须让链接正常工作**，
 *      否则读者点了图却发现跳不到项目主页，是很隐蔽的 bug
 * 所以：只有「没有链接」或「链接指向图片本身」时才挂灯箱；其余情况返回 null 放弃绑定。
 */
function resolveZoomSrc(img: HTMLImageElement): string | null {
  const src = (img.getAttribute('src') || '').trim()
  const href = (img.closest('a')?.getAttribute('href') || '').trim()
  if (!href || href.startsWith('#')) {
    return src || null
  }
  const imageLike = /\.(png|jpe?g|gif|webp|avif|svg|bmp)(\?.*)?$/i.test(href)
  if (imageLike || href === src) {
    return href
  }
  return null
}

/** 关闭当前灯箱（如果开着） */
export function closeLightbox() {
  if (activeCleanup) {
    const fn = activeCleanup
    activeCleanup = null
    fn()
  }
}

/** 给容器内所有正文图片挂上点击放大 */
export function bindImageLightbox(root: HTMLElement | null) {
  if (!root) return
  root.querySelectorAll<HTMLImageElement>('img').forEach((img) => {
    if (img.dataset.zoomBound === '1') return
    const src = resolveZoomSrc(img)
    // 没有真实 src（懒加载占位）或被链接包着且链接指向别处 → 不挂
    if (!src) return

    img.dataset.zoomBound = '1'
    img.dataset.zoomSrc = src
    img.classList.add('article-img-zoomable')
    img.addEventListener('error', () => img.classList.add('article-img-error'))
    // 关键：zoomBound 只表示「已处理过」以防重复绑定，不表示「参与灯箱」。
    // 参与与否看 zoomSrc 有没有值 —— 图片加载完成后若发现是小图标（徽章/emoji），
    // 就把 zoomSrc 清掉：它既不显示放大光标，也不会被算进多图导航
    // （否则点「下一张」会翻到一张 20x20 的徽章，很难发现问题）。
    const dropIfTiny = () => {
      if (!isTinyIcon(img)) return
      img.classList.remove('article-img-zoomable')
      delete img.dataset.zoomSrc
    }
    if (img.complete) dropIfTiny()
    else img.addEventListener('load', dropIfTiny, { once: true })

    img.addEventListener('click', (e) => {
      // 小图标 / 已被移除的都放行，不劫持点击
      if (isTinyIcon(img) || !img.dataset.zoomSrc) return
      e.preventDefault()
      openLightbox(root, img)
    })
  })
}

/** 收集当前正文里所有「已挂灯箱」的图片，用于左右切换 */
function collectEntries(root: HTMLElement): ZoomEntry[] {
  return Array.from(root.querySelectorAll<HTMLImageElement>('img'))
    .filter((i) => i.dataset.zoomBound === '1' && (i.dataset.zoomSrc || '').trim() !== '')
    .map((i) => ({
      img: i,
      src: i.dataset.zoomSrc as string,
      alt: i.getAttribute('alt') || i.getAttribute('title') || ''
    }))
}

function openLightbox(root: HTMLElement, startImg: HTMLImageElement) {
  closeLightbox()

  const entries = collectEntries(root)
  if (entries.length === 0) return
  const startIndex = entries.findIndex((e) => e.img === startImg)
  let index = startIndex >= 0 ? startIndex : 0

  const overlay = document.createElement('div')
  overlay.className = 'img-lightbox'
  overlay.setAttribute('role', 'dialog')
  overlay.setAttribute('aria-modal', 'true')
  overlay.setAttribute('aria-label', '图片预览')
  overlay.innerHTML =
    '<button class="img-lightbox-btn img-lightbox-close" aria-label="关闭" title="关闭 (Esc)">&times;</button>' +
    (entries.length > 1
      ? '<button class="img-lightbox-btn img-lightbox-prev" aria-label="上一张" title="上一张 (←)">&#8249;</button>' +
        '<button class="img-lightbox-btn img-lightbox-next" aria-label="下一张" title="下一张 (→)">&#8250;</button>'
      : '') +
    '<img class="img-lightbox-img" alt="" draggable="false" />' +
    '<div class="img-lightbox-counter"></div>' +
    '<div class="img-lightbox-caption"></div>'

  const imgEl = overlay.querySelector('.img-lightbox-img') as HTMLImageElement
  const counterEl = overlay.querySelector('.img-lightbox-counter') as HTMLElement
  const captionEl = overlay.querySelector('.img-lightbox-caption') as HTMLElement

  let scale = 1
  let offsetX = 0
  let offsetY = 0

  function applyTransform() {
    imgEl.style.transform = `translate3d(${offsetX}px, ${offsetY}px, 0) scale(${scale})`
    imgEl.style.cursor = scale > 1 ? 'grab' : 'zoom-in'
  }

  /** 放大后平移不能把图拖出可视区：按「溢出的一半」夹取偏移 */
  function clampOffsets() {
    const w = imgEl.clientWidth || 1
    const h = imgEl.clientHeight || 1
    const maxX = Math.max(0, (w * scale - w) / 2)
    const maxY = Math.max(0, (h * scale - h) / 2)
    offsetX = Math.min(maxX, Math.max(-maxX, offsetX))
    offsetY = Math.min(maxY, Math.max(-maxY, offsetY))
  }

  function resetView() {
    scale = 1
    offsetX = 0
    offsetY = 0
    applyTransform()
  }

  function setScale(next: number) {
    scale = Math.min(SCALE_MAX, Math.max(SCALE_MIN, next))
    if (scale === 1) {
      offsetX = 0
      offsetY = 0
    } else {
      clampOffsets()
    }
    applyTransform()
  }

  function show(i: number) {
    index = ((i % entries.length) + entries.length) % entries.length
    const cur = entries[index]
    imgEl.classList.remove('img-lightbox-img-error')
    imgEl.src = cur.src
    imgEl.alt = cur.alt
    captionEl.textContent = cur.alt
    captionEl.style.display = cur.alt ? '' : 'none'
    counterEl.textContent = entries.length > 1 ? `${index + 1} / ${entries.length}` : ''
    resetView()
  }

  // ---------------------------------------------------------------
  // 交互
  // ---------------------------------------------------------------
  let dragging = false
  let dragStart = { x: 0, y: 0, ox: 0, oy: 0 }
  let moved = false
  /** 最近一次触摸结束的时间：用来吃掉触屏上的合成 click，见下面 imgEl 的 click 处理 */
  let lastTouchAt = 0

  function startDrag(x: number, y: number) {
    if (scale <= 1) return
    dragging = true
    moved = false
    dragStart = { x, y, ox: offsetX, oy: offsetY }
    imgEl.style.cursor = 'grabbing'
  }

  function moveDrag(x: number, y: number) {
    if (!dragging) return
    const dx = x - dragStart.x
    const dy = y - dragStart.y
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) moved = true
    offsetX = dragStart.ox + dx
    offsetY = dragStart.oy + dy
    clampOffsets()
    applyTransform()
  }

  function endDrag() {
    if (!dragging) return
    dragging = false
    imgEl.style.cursor = scale > 1 ? 'grab' : 'zoom-in'
  }

  function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      e.preventDefault()
      closeLightbox()
    } else if (e.key === 'ArrowLeft' && entries.length > 1) {
      e.preventDefault()
      show(index - 1)
    } else if (e.key === 'ArrowRight' && entries.length > 1) {
      e.preventDefault()
      show(index + 1)
    } else if (e.key === '+' || e.key === '=') {
      setScale(scale * 1.2)
    } else if (e.key === '-' || e.key === '_') {
      setScale(scale / 1.2)
    }
  }

  let pinchStartDist = 0
  let pinchStartScale = 1
  let touchStartX = 0
  let touchStartY = 0
  let touchMoved = false

  function onTouchStart(e: TouchEvent) {
    if (e.touches.length === 2) {
      pinchStartDist = touchDistance(e.touches)
      pinchStartScale = scale
    } else if (e.touches.length === 1) {
      touchStartX = e.touches[0].clientX
      touchStartY = e.touches[0].clientY
      touchMoved = false
      startDrag(touchStartX, touchStartY)
    }
  }

  function onTouchMove(e: TouchEvent) {
    if (e.touches.length === 2 && pinchStartDist > 0) {
      e.preventDefault()
      setScale(pinchStartScale * (touchDistance(e.touches) / pinchStartDist))
      return
    }
    if (e.touches.length === 1) {
      const x = e.touches[0].clientX
      const y = e.touches[0].clientY
      if (Math.abs(x - touchStartX) > 8 || Math.abs(y - touchStartY) > 8) touchMoved = true
      if (dragging) {
        e.preventDefault()
        moveDrag(x, y)
      }
    }
  }

  function onTouchEnd(e: TouchEvent) {
    if (e.touches.length < 2) pinchStartDist = 0
    if (e.touches.length > 0) return
    lastTouchAt = Date.now()
    endDrag()
    // 未放大时单指横向滑动 = 切换图片（与手机相册的直觉一致）
    if (touchMoved && scale === 1 && entries.length > 1) {
      const dx = (e.changedTouches[0]?.clientX ?? 0) - touchStartX
      if (Math.abs(dx) > SWIPE_THRESHOLD) show(index + (dx < 0 ? 1 : -1))
    } else if (!touchMoved && scale === 1) {
      // 轻点图片 → 放大；再轻点还原
      setScale(2)
    }
    touchMoved = false
  }

  function onWheel(e: WheelEvent) {
    e.preventDefault()
    setScale(scale * (e.deltaY < 0 ? 1.15 : 1 / 1.15))
  }

  // 鼠标拖拽平移 / 单击切换缩放
  overlay.addEventListener('mousedown', (e) => {
    if (e.target !== imgEl) return
    // 每次新的按下都重置：否则上一次拖拽留下的 moved=true 会让这一次的单击被误判成拖拽
    moved = false
    startDrag(e.clientX, e.clientY)
  })
  overlay.addEventListener('mousemove', (e) => moveDrag(e.clientX, e.clientY))
  overlay.addEventListener('mouseup', () => endDrag())
  imgEl.addEventListener('click', () => {
    // 触屏上 touchend 已经处理过缩放，紧接着浏览器还会补一个合成 click，
    // 不挡掉的话会被"放大→立刻还原"
    if (Date.now() - lastTouchAt < 500) return
    if (moved) return
    setScale(scale > 1 ? 1 : 2)
  })
  imgEl.addEventListener('error', () => {
    imgEl.classList.add('img-lightbox-img-error')
    captionEl.style.display = ''
    captionEl.textContent = '图片加载失败'
  })

  overlay.addEventListener('wheel', onWheel, { passive: false })
  overlay.addEventListener('touchstart', onTouchStart, { passive: true })
  overlay.addEventListener('touchmove', onTouchMove, { passive: false })
  overlay.addEventListener('touchend', onTouchEnd)
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) closeLightbox()
  })
  overlay.querySelector('.img-lightbox-close')?.addEventListener('click', () => closeLightbox())
  overlay.querySelector('.img-lightbox-prev')?.addEventListener('click', (e) => {
    e.stopPropagation()
    show(index - 1)
  })
  overlay.querySelector('.img-lightbox-next')?.addEventListener('click', (e) => {
    e.stopPropagation()
    show(index + 1)
  })

  // 锁滚动：记下原值，关闭时还原（不要直接写死成 ''）
  const prevOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('keydown', onKeydown)

  activeCleanup = () => {
    window.removeEventListener('keydown', onKeydown)
    document.body.style.overflow = prevOverflow
    overlay.remove()
  }

  document.body.appendChild(overlay)
  show(index)
  applyTransform()
}
