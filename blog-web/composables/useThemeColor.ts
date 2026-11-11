/**
 * 主题色相（Theme Color）
 *
 * 我们的主题色是一个 HSL 三元组：`--ph`（色相）/ `--ps`（饱和 44%）/ `--pl`（亮度 46%），
 * 定义在 main.css 的 `html` 上。这里**只让用户动色相**：饱和度锁死是为了守住
 * 「暖纸 + 墨色 + 单一强调色」的观感 —— 饱和度和亮度一放开，很容易调出刺眼的
 * 高饱和亮色，全站立刻变成另一个东西。
 *
 * 为什么单独开一个文件而不是塞进 Header：
 * 色相会同时影响 Header、按钮、链接、标签、目录高亮…… 状态必须放在 `useState` 里
 * 全局共享，且**首屏由内联脚本先写一次**（见 nuxt.config.ts 的 `app.head.script`），
 * 否则刷新时先按默认 22 画一帧再跳到用户色，会闪。
 */

export const HUE_KEY = 'blog-hue'
/** 22 ≈ 陶土/赤陶，与 main.css 里 `--ph: 22` 必须一致 */
export const DEFAULT_HUE = 22
export const MIN_HUE = 0
export const MAX_HUE = 360
/** 滑块步进：5° 一档，够细又不会有 360 个档位那么难对准 */
export const HUE_STEP = 5

/** 解析 localStorage 里存的色相；非法值（空、NaN、越界）一律返回 null，让调用方走默认 */
export function parseHue(value: string | null | undefined): number | null {
  if (value == null || value === '') return null
  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed) || parsed < MIN_HUE || parsed > MAX_HUE) return null
  return parsed
}

export function clampHue(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_HUE
  return Math.min(Math.max(Math.round(value), MIN_HUE), MAX_HUE)
}

export const useThemeColor = () => {
  const hue = useState<number>('blog-hue', () => DEFAULT_HUE)
  // 只在第一次挂载时读 localStorage：写在顶层会让 SSR 渲染默认色、
  // 客户端读回用户色，造成 hydration 不一致（壁纸那边踩过同样的坑）
  const hydrated = useState<boolean>('blog-hue-hydrated', () => false)

  /** 只改 CSS 变量，不落盘（内联脚本与 hydrate 复用它） */
  function applyHue(next: number) {
    const value = clampHue(next)
    hue.value = value
    if (process.client) {
      document.documentElement.style.setProperty('--ph', String(value))
    }
  }

  function setHue(next: number) {
    const value = clampHue(next)
    applyHue(value)
    if (process.client) {
      try { localStorage.setItem(HUE_KEY, String(value)) } catch { /* 隐私模式写不进去就只在本页生效 */ }
    }
  }

  function resetHue() {
    setHue(DEFAULT_HUE)
  }

  function hydrate() {
    if (!process.client || hydrated.value) return
    hydrated.value = true
    const saved = parseHue(localStorage.getItem(HUE_KEY))
    // 内联脚本正常工作时这一步只是把 ref 同步过来（颜色早就写在 CSS 变量上了）；
    // 脚本被隐私插件拦掉时，靠它兜底把颜色补上
    if (saved != null) applyHue(saved)
  }

  const isDefaultHue = computed(() => hue.value === DEFAULT_HUE)

  return { hue, isDefaultHue, setHue, resetHue, applyHue, hydrate }
}
