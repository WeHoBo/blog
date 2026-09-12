/**
 * 文章链接的唯一生成入口。
 *
 * 背景（这是个真实踩过的坑）：站内曾经存在两种写法 —— 有的地方写 `/post/{slug}`，
 * 有的地方写 `/article/{id}`，还有的地方两个混用（同一个页面里「上一篇」跳
 * `/article/{id}`、「下一篇」跳 `/post/{slug}`）。而这两条路由此前对应**两个不同的
 * 页面实现**（一个功能完整、一个只有正文），于是读者点着点着功能就"消失"了。
 *
 * 现在两条路由共用同一个详情组件、内容完全一致，但仍然统一用 slug 优先：
 *   - `/post/{slug}` 是 canonical（SEO 友好、可读）；
 *   - 没有 slug 的老数据才回退到 `/article/{id}`。
 * 所有文章链接都必须走这里，不要再手写模板字符串。
 */

export interface ArticleLike {
  id?: number | string | null
  slug?: string | null
}

/** 文章详情页路径：有 slug 用 /post/{slug}，否则退回 /article/{id} */
export function articlePath(a: ArticleLike | null | undefined): string {
  if (!a) return '/'
  const slug = typeof a.slug === 'string' ? a.slug.trim() : ''
  if (slug) return `/post/${encodeURIComponent(slug)}`
  return a.id != null ? `/article/${a.id}` : '/'
}
