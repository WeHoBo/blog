/**
 * 站点配置（`/api/site-config/public`）的唯一读取入口。
 *
 * 为什么改成 `useAsyncData`（原来是手写 `useState` + `fetch()`，而且没人调用）：
 * 这份数据同时被「布局」和「首页横幅」需要 ——
 *   - 布局：壁纸图 `bgImage`，属于首屏视觉，晚到会闪一下白；
 *   - 首页横幅：博主名 / 简介 / 头像（这四个字段原来写死在 `pages/index.vue` 里）。
 * `useState` + 手动 fetch 的问题是**两处各自调用就会发两次请求**；而 `useAsyncData`
 * 按 key 去重、且请求状态是**按请求隔离**的（模块级 Promise 做去重会在 SSR 并发时串用户，
 * 这点很容易踩）。所以：固定一个 key，先 await 的那次真正请求，后一次直接拿缓存。
 *
 * ⚠️ key 不要随便改：布局与首页都靠它去重，改名等于两处各发一次请求。
 */
export const SITE_CONFIG_KEY = 'site-config-public'

export interface SiteConfig {
  siteName?: string
  siteDesc?: string
  aboutContent?: string
  /** 壁纸图（横幅模式取它当底图） */
  bgImage?: string
  /** 博主展示名：首页横幅「你好，我是 ___」 */
  bloggerName?: string
  /** 博主简介，可多行；首页只取第一行当副标题 */
  bloggerBio?: string
  /** 博主头像 URL */
  bloggerAvatar?: string
  /** 横幅上的「关注方向」，英文逗号分隔 */
  bloggerFocus?: string
  /** AI 助手的展示名（悬浮球 tooltip、面板标题、欢迎语里的自称） */
  aiName?: string
  /** AI 面板的欢迎语 */
  aiGreeting?: string
  /** 问题池，JSON 数组字符串；为空时前台按最新文章自动生成 */
  aiQuestions?: string
}

export const useSiteConfig = () => {
  const { get } = useApi()
  return useAsyncData<SiteConfig>(SITE_CONFIG_KEY, async () => {
    try {
      const res = await get<SiteConfig>('/site-config/public')
      // 后端约定：HTTP 200 只是传输成功，业务结果要看 body.code
      return res?.code === 200 ? (res.data || {}) : {}
    } catch {
      // 配置接口挂了不该让整页 500：横幅文案与壁纸都回落到默认值
      return {}
    }
  })
}
