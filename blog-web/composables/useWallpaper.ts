export type WallpaperMode = 'banner' | 'full' | 'cover' | 'none'

export const WALLPAPER_KEY = 'blog-wallpaper-mode'
export const WAVE_KEY = 'blog-banner-wave'

export function parseWallpaperMode(value: string | null): WallpaperMode {
  if (value === 'full' || value === 'cover' || value === 'none') return value
  return 'banner'
}

/** 波浪开关：只有明确存了 '0' 才算关闭（默认开，和「横幅模式」的观感一致） */
export function parseWaveFlag(value: string | null): boolean {
  return value !== '0'
}

/**
 * 壁纸模式（替代原来的「主题色相」滑块）。
 *
 * - banner：只在顶部横幅区域显示壁纸，内容卡片压在图片下沿
 * - full  ：壁纸固定铺满视口，页面滚动时不动
 * - cover ：壁纸铺满整个页面高度，随页面一起滚动
 * - none  ：不显示壁纸，回到纯纸面
 *
 * 图片本身来自站点配置（`/site-config/public` 的 bgImage），这里只存模式与图片地址，
 * 由 `WallpaperLayer` 统一渲染，设置面板与页面共用同一份状态。
 *
 * `wave` 是横幅底部的水波纹开关（Banner Options → Waves Animation），
 * 只有 banner 模式且确实有图时才看得到，所以和模式放在一起维护。
 */
export const useWallpaper = () => {
  const mode = useState<WallpaperMode>('blog-wallpaper', () => 'banner')
  const image = useState<string>('blog-wallpaper-image', () => '')
  const wave = useState<boolean>('blog-banner-wave', () => true)
  // 只在第一次挂载时读 localStorage：不能写在 composable 顶层，
  // 否则 SSR 渲染「横幅」而客户端读回「隐藏」会造成 hydration 不一致
  const hydrated = useState<boolean>('blog-wallpaper-hydrated', () => false)

  function setMode(next: WallpaperMode) {
    mode.value = next
    if (process.client) {
      localStorage.setItem(WALLPAPER_KEY, next)
      // 同步到 <html>：CSS 的壁纸几何与正文上边距都读这个属性
      // （首次绘制前由 nuxt.config 里的内联脚本写入，这里是用户手动切换后保持一致）
      document.documentElement.setAttribute('data-wallpaper', next)
    }
  }

  function setWave(next: boolean) {
    wave.value = next
    if (process.client) {
      try { localStorage.setItem(WAVE_KEY, next ? '1' : '0') } catch { /* 隐私模式忽略 */ }
    }
  }

  function setImage(url: string) {
    image.value = url || ''
  }

  function hydrate() {
    if (!process.client || hydrated.value) return
    hydrated.value = true
    const saved = parseWallpaperMode(localStorage.getItem(WALLPAPER_KEY))
    mode.value = saved
    // 内联脚本正常工作时这里只是重复写入一次；脚本被拦（隐私插件等）时靠它兜底
    document.documentElement.setAttribute('data-wallpaper', saved)
    wave.value = parseWaveFlag(localStorage.getItem(WAVE_KEY))
  }

  return { mode, image, wave, setMode, setWave, setImage, hydrate }
}
