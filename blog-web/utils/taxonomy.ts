/**
 * 标签 / 分类（taxonomy）页面的公共工具。
 *
 * 为什么需要「容忍 slug / name / id 三种形态」：
 *   - Tag / Category 都有 slug 字段，但历史数据里 slug 可能为空；
 *   - TagController、CategoryController 在新建时只是把空 slug 兜底成 name，
 *     所以线上真实存在「slug 是中文别名」与「slug 为空」两类数据；
 *   - 只要有一类没兼容到，读者点到该标签就会 404。
 * 因此路由参数按 slug → name → id 依次匹配，保证老链接也能打开。
 */

export interface TaxonomyItem {
  id: number
  name: string
  slug?: string | null
  articleCount?: number
  parentId?: number | null
  children?: TaxonomyItem[]
}

export type TaxonomyPrefix = '/tag' | '/category'

/** 站点规范域名：与各页 canonical、SitemapController 保持一致 */
export const SITE_URL = 'https://codeup.asia'

/** 从路由参数里解出唯一的一项：先按 slug，再按 name，最后按 id */
export function matchTaxonomy(
  list: TaxonomyItem[] | null | undefined,
  param: string | string[] | undefined
): TaxonomyItem | null {
  const raw = Array.isArray(param) ? param[0] : param
  if (!raw) return null
  let key = String(raw)
  try {
    key = decodeURIComponent(key)
  } catch {
    // 参数本身已是解码后的文本，忽略
  }
  const items = list || []
  return (
    items.find((x) => x.slug && String(x.slug) === key)
    || items.find((x) => String(x.name) === key)
    || items.find((x) => String(x.id) === key)
    || null
  )
}

/** 生成规范 URL：有 slug 用 slug（SEO 友好），没有就退回 id */
export function taxonomyPath(prefix: TaxonomyPrefix, item: TaxonomyItem | null | undefined): string {
  if (!item) return prefix === '/tag' ? '/tags' : '/categories'
  const raw = item.slug && String(item.slug).trim() ? String(item.slug).trim() : String(item.id)
  return `${prefix}/${encodeURIComponent(raw)}`
}

/** 该分类法页面的 canonical 绝对地址 */
export function taxonomyCanonical(
  prefix: TaxonomyPrefix,
  item: TaxonomyItem | null | undefined
): string {
  return SITE_URL + taxonomyPath(prefix, item)
}

/** 扁平分类列表 → 树（父分类不存在的子分类提升为一级，保证不丢失） */
export function buildCategoryTree(list: TaxonomyItem[] | null | undefined): TaxonomyItem[] {
  const map = new Map<number, TaxonomyItem>()
  ;(list || []).forEach((c) => map.set(c.id, { ...c, children: [] }))
  const roots: TaxonomyItem[] = []
  ;(list || []).forEach((c) => {
    const node = map.get(c.id) as TaxonomyItem
    const parent = c.parentId ? map.get(c.parentId) : null
    if (parent) (parent.children as TaxonomyItem[]).push(node)
    else roots.push(node)
  })
  return roots
}
