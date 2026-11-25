/**
 * 站点风格（皮肤）的唯一状态源。
 *
 * 为什么用 cookie 而不是 localStorage：
 *   皮肤决定首页渲染哪套布局。localStorage 在 SSR 阶段读不到，
 *   服务端会先按默认皮肤吐一版 HTML，水合后再切成用户选的那套 ——
 *   整页闪一下、结构还会跳。cookie 会随请求一起到服务端，SSR 直接按它渲染，
 *   首屏就是正确的那套。
 *
 * 首页、导航和外观面板共享布局标识，同一张站点背景也可以搭配不同排版。
 */

export type ReferenceLayout = 'platform' | 'journal' | 'alpine' | 'album'
export type SkinId = 'default' | 'paper' | 'sidebar' | 'mint' | 'sky' | ReferenceLayout

/** 首页布局族：皮肤只负责挑一个，具体样式在各自的组件里 */
export type SkinLayout = 'classic' | 'sidebar' | 'mint' | ReferenceLayout

export function isReferenceLayout(layout: SkinLayout): layout is ReferenceLayout {
  return layout === 'platform' || layout === 'journal' || layout === 'alpine' || layout === 'album'
}

export interface SkinDef {
  id: SkinId
  name: string
  /** 设置面板里那行小字，说清这套长什么样 */
  desc: string
  layout: SkinLayout
  /**
   * 这套皮肤的主题色相（0–360）。
   * 只给色相：饱和度与亮度是站点的硬约束（低饱和、低明度），皮肤不许动，
   * 否则「薄荷」那套能调出荧光绿，和整站的纸墨气质就断了。
   */
  hue: number
  /** 缩略图的底色（渐变），只是给人一眼区分，不参与真实渲染 */
  swatch: string
  /** 缩略图上画的小示意块颜色，同样只是示意 */
  accent: string
  /** 内置渐变也用于缺图时的首屏，暖纸只保留页面底色。 */
  wallpaper?: { gradient: string; darkGradient: string; image?: string }
}

export const SKINS: SkinDef[] = [
  {
    id: 'default',
    name: '青野',
    // 2026-11-24 起「默认」就是这个样子：蓝天草地底 + 毛玻璃面板，三栏结构不变
    desc: '默认：天空草地底、毛玻璃面板，三栏（左分类 / 中精品 / 右最新）',
    layout: 'classic',
    // 色相仍是陶土 22：设计稿里背景是蓝天，但强调色（「精选」前那道竖线、
    // 日期、阅读全文）都是暖褐 —— 蓝底配陶土是这张稿子本身的搭配，别跟着背景改成蓝。
    hue: 22,
    swatch: 'linear-gradient(180deg,#C6E0F6 0%,#E4EFF9 55%,#DCEBCB 100%)',
    accent: '#8A6350',
    wallpaper: {
      gradient: 'radial-gradient(ellipse at 20% 8%, #F3F8F9 0%, transparent 42%), linear-gradient(170deg, #9EC6E4, #D9EBEE 55%, #B7D0A2)',
      darkGradient: 'linear-gradient(166deg, #203143, #263532 65%, #2E3D2D)',
    },
  },
  {
    id: 'paper',
    name: '暖纸',
    // 旧的默认外观，原样保留：纯纸面、没有背景图。想安静读书时用这套。
    desc: '纯暖纸底、无背景图，最安静的一套',
    layout: 'classic',
    hue: 22,
    swatch: 'linear-gradient(135deg,#F7F2E8,#EFE7D8)',
    accent: '#8A6350',
  },
  {
    id: 'sidebar',
    name: '侧栏',
    desc: '导航收进左竖栏，首屏给一句欢迎和四张统计卡',
    layout: 'sidebar',
    hue: 202,
    swatch: 'linear-gradient(180deg,#CFE4F7,#E8F0DA)',
    accent: '#3E6E9B',
    wallpaper: {
      gradient: 'linear-gradient(118deg, #B4CBC5, #E3E8D9 49%, #C0CDD7)',
      darkGradient: 'linear-gradient(118deg, #273C3A, #30372D 49%, #25313C)',
    },
  },
  {
    id: 'mint',
    name: '薄荷',
    desc: '浅绿底、大圆角卡片、左侧竖栏 + 日历',
    layout: 'mint',
    hue: 152,
    swatch: 'linear-gradient(135deg,#EAF6EF,#D8EFE2)',
    accent: '#3F8F63',
    wallpaper: {
      gradient: 'radial-gradient(ellipse at 22% 22%, #E2F4D9, transparent 52%), linear-gradient(145deg, #A8D8BF, #D7EDE2 55%, #AED5C6)',
      darkGradient: 'linear-gradient(145deg, #203D30, #263C36 55%, #1D3031)',
    },
  },
  {
    id: 'sky',
    name: '晴空',
    desc: '与薄荷共用日历版式，搭配晴蓝渐变与蓝色强调',
    layout: 'mint',
    hue: 206,
    swatch: 'linear-gradient(180deg,#CDE6FA,#EAF3FB)',
    accent: '#4A7FB5',
    wallpaper: {
      gradient: 'radial-gradient(ellipse at 78% 32%, #F1F5FF, transparent 45%), linear-gradient(162deg, #8CBCE4, #C4DEF0 52%, #DADEF7)',
      darkGradient: 'linear-gradient(162deg, #203953, #29394B 52%, #323345)',
    },
  },
  {
    id: 'platform', name: '晴日站台', layout: 'platform', hue: 28,
    desc: '左侧导航、车站横幅与四格统计，下方并排展示近期文章和分类。',
    swatch: 'linear-gradient(155deg,#b7d8ed 15%,#f1ebd9 78%,#ced6b4)', accent: '#935522',
    wallpaper: { gradient: 'linear-gradient(160deg,#a8d0e9,#f6ead4)', darkGradient: 'linear-gradient(160deg,#273845,#342e26)', image: '/images/skins/meadow.webp' },
  },
  {
    id: 'journal', name: '拾光手记', layout: 'journal', hue: 24,
    desc: '顶部导航与拍立得主视觉，下方是作者栏、右图文章卡和近期文章。',
    swatch: 'linear-gradient(145deg,#f6efde,#ddd2b8 78%,#d3dee2)', accent: '#815d43',
    wallpaper: { gradient: 'linear-gradient(155deg,#c6deeb,#efe8d7 70%,#c8cdae)', darkGradient: 'linear-gradient(155deg,#263743,#322d29 78%,#2e352b)', image: '/images/skins/meadow.webp' },
  },
  {
    id: 'alpine', name: '山岚书架', layout: 'alpine', hue: 260,
    desc: '淡紫分栏刊头整合简介、置顶和标签；正文并列分类日历、精选文章和最新目录。',
    swatch: 'linear-gradient(150deg,#d9e9fc,#ddd9f2 62%,#b9c3e4)', accent: '#68549e',
    wallpaper: { gradient: 'linear-gradient(155deg,#b4cde7,#e2e3f3 58%,#c5cce4)', darkGradient: 'linear-gradient(155deg,#263342,#302d42 58%,#293340)' },
  },
  {
    id: 'album', name: '原野相册', layout: 'album', hue: 77,
    desc: '左侧导航、叠放明信片和月历。进入文章列表后可在双列宽封面与列表间切换。',
    swatch: 'linear-gradient(150deg,#c0d7e8,#f4efdf 60%,#ccd5ac)', accent: '#747c4a',
    wallpaper: { gradient: 'linear-gradient(160deg,#accfe3,#f1e9d6 65%,#c5ceac)', darkGradient: 'linear-gradient(160deg,#283a45,#323329 70%,#2c382d)', image: '/images/skins/meadow.webp' },
  },
]

export const DEFAULT_SKIN: SkinId = 'default'

const VALID = new Set<string>(SKINS.map((s) => s.id))

export function isSkinId(v: unknown): v is SkinId {
  return typeof v === 'string' && VALID.has(v)
}

/** 皮肤 id → 定义（未知值一律当默认，避免老 cookie 让页面崩） */
export function skinOf(id: string | undefined | null): SkinDef {
  return SKINS.find((s) => s.id === id) || SKINS[0]
}

/**
 * 当前皮肤。cookie 一年有效，跨设备不同步是刻意的（每台设备屏幕不一样）。
 *
 * 为什么要多套一层 `useState`：
 * `useCookie` 每次调用返回的是**各自独立的 ref**，不是同一个对象。
 * 布局里那份和设置面板里那份互不相通 —— 点一下风格，cookie 变了，
 * 但布局的 ref 不会跟着变，页面就不会重绘。用 `useState` 做唯一真身，
 * cookie 只负责「跨请求持久化」。
 *
 * ⚠️ cookie 用 `sameSite: 'lax'`：站点内部跳转会带上，外链过来也会带上，
 * 不会因为第三方跳转被拦掉。
 */
const SKIN_STATE_KEY = 'site-skin'

export const useSkin = () => {
  const cookie = useCookie<SkinId>(SKIN_STATE_KEY, {
    default: () => DEFAULT_SKIN,
    maxAge: 60 * 60 * 24 * 365,
    sameSite: 'lax',
    path: '/',
  })

  // 初始值取 cookie：SSR 阶段读得到，所以服务端吐出的第一帧就是用户选的那套
  const state = useState<SkinId>(SKIN_STATE_KEY, () =>
    (isSkinId(cookie.value) ? cookie.value : DEFAULT_SKIN)
  )

  const skin = computed<SkinId>(() => (isSkinId(state.value) ? state.value : DEFAULT_SKIN))

  const current = computed<SkinDef>(() => skinOf(skin.value))

  function setSkin(id: SkinId, { persist = true }: { persist?: boolean } = {}) {
    const next = isSkinId(id) ? id : DEFAULT_SKIN
    state.value = next
    if (persist) cookie.value = next
  }

  return { skin, current, setSkin }
}
