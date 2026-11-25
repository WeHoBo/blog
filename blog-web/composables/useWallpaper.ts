export type WallpaperMode = 'banner' | 'full' | 'cover' | 'none'
export type WallpaperPreference = 'site' | 'skin'

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
 * 站点配置的 bgImage 优先，留空时使用当前皮肤的背景。
 * 设置面板、背景层和横幅占位共用这里的判断。
 *
 * `wave` 是横幅底部的水波纹开关（Banner Options → Waves Animation），
 * 只有 banner 模式且确实有图时才看得到，所以和模式放在一起维护。
 */
export const useWallpaper = () => {
  const mode = useState<WallpaperMode>('blog-wallpaper', () => 'banner')
  const image = useState<string>('blog-wallpaper-image', () => '')
  const wave = useState<boolean>('blog-banner-wave', () => true)
  const preferenceCookie = useCookie<WallpaperPreference>('blog-wallpaper-source', { path: '/', sameSite: 'lax', maxAge: 31536000 })
  const preference = useState<WallpaperPreference>('blog-wallpaper-source', () => preferenceCookie.value === 'skin' ? 'skin' : 'site')
  const previousMode = useState<Exclude<WallpaperMode, 'none'>>('blog-wallpaper-last-visible', () => 'banner')
  const failedImage = useState<string>('blog-wallpaper-failed-image', () => '')
  const { current: skin } = useSkin()
  const siteImage = computed(() => preference.value === 'site' && image.value !== failedImage.value ? image.value : '')
  const source = computed(() => {
    if (siteImage.value) return 'site'
    if (skin.value.wallpaper) return 'skin'
    return 'none'
  })
  const visible = computed(() => source.value !== 'none' && mode.value !== 'none')
  const bannerVisible = computed(() => visible.value && mode.value === 'banner')

  // 只在第一次挂载时读 localStorage：不能写在 composable 顶层，
  // 否则 SSR 渲染「横幅」而客户端读回「隐藏」会造成 hydration 不一致
  const hydrated = useState<boolean>('blog-wallpaper-hydrated', () => false)

  function setMode(next: WallpaperMode) {
    mode.value = next
    if (next !== 'none') previousMode.value = next
    if (process.client) {
      // 同步到 <html>：CSS 的壁纸几何与正文上边距都读这个属性
      // （首次绘制前由 nuxt.config 里的内联脚本写入，这里是用户手动切换后保持一致）
      document.documentElement.setAttribute('data-wallpaper', next)
      try {
        localStorage.setItem(WALLPAPER_KEY, next)
        localStorage.setItem('blog-wallpaper-last-mode', previousMode.value)
      } catch { /* 仅应用于当前页面 */ }
    }
  }

  function setWave(next: boolean) {
    wave.value = next
    if (process.client) {
      try { localStorage.setItem(WAVE_KEY, next ? '1' : '0') } catch { /* 隐私模式忽略 */ }
    }
  }

  function setImage(url: string) {
    const next = (url || '').trim()
    if (image.value !== next) failedImage.value = ''
    image.value = next
  }

  function setSource(next: WallpaperPreference | 'none') {
    if (next === 'none') {
      setMode('none')
      return
    }
    preference.value = next
    preferenceCookie.value = next
    if (next === 'site') failedImage.value = ''
    if (mode.value === 'none') setMode(previousMode.value)
  }

  function markImageFailed(url: string) {
    if (url === image.value) failedImage.value = url
  }

  function hydrate() {
    if (!process.client || hydrated.value) return
    hydrated.value = true
    try {
      mode.value = parseWallpaperMode(localStorage.getItem(WALLPAPER_KEY))
      const last = parseWallpaperMode(localStorage.getItem('blog-wallpaper-last-mode'))
      previousMode.value = mode.value !== 'none' ? mode.value : last === 'none' ? 'banner' : last
      wave.value = parseWaveFlag(localStorage.getItem(WAVE_KEY))
    } catch { /* 存储受限时沿用当前模式 */ }
    document.documentElement.setAttribute('data-wallpaper', mode.value)
  }

  return { mode, image, siteImage, preference, failedImage, wave, source, visible, bannerVisible, setMode, setSource, setWave, setImage, markImageFailed, hydrate }
}
