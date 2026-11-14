/**
 * AI 助手「推荐问题」的生成逻辑（纯函数，不依赖 vue / nuxt，便于 Node 侧直接验证）。
 *
 * 为什么不再写死：原来那 4 个问题是按当时的文章挑的（RAG / Agent / Transformer / Spring Boot），
 * 文章下线后它们就指向了站内并不存在的内容 —— 用户点了只会得到「站内资料里没有相关内容」，
 * 比不给建议更糟。改成从**当前真实存在的文章标题**生成，点下去一定能检索到东西。
 *
 * 取法：
 *   1. 最多 3 个来自文章 ——「《标题》讲了什么？」，排序（按阅读量）由调用方决定，
 *      这里只按传入顺序取，不猜业务口径；
 *   2. 剩下的用分类补 ——「站内有哪些「分类」的文章？」；
 *   3. 还凑不齐（接口挂了 / 站里没几篇）就退回 3 条通用问题，任何情况下都不会空着。
 */

/** 单条标题在 chip 里的可读上限，超过截断加省略号 */
const MAX_TITLE = 20

/** 兜底问题：不依赖站内数据，接口异常时用它顶上 */
const FALLBACK_QUESTIONS = [
  '站内最近更新了哪些文章？',
  '站内有哪些分类？',
  '帮我推荐几篇文章',
  'AI 能帮我做什么？'
]

export interface SuggestArticle {
  title?: string | null
}

export interface SuggestCategory {
  name?: string | null
}

/**
 * 把长标题压到适合放进按钮的长度。
 *
 * 真实标题里有不少这种：「学校课程《计算机操作系统》第三部分：处理机调度与死锁（PPT总结）」，
 * 直接塞进 chip 会撑成三行。所以：先合并空白 → 去掉结尾的括注 → 在主标题的冒号处截断
 * （冒号前太短就保留原样，否则会截出「笔记」这种没信息量的碎片）→ 最后按上限截断。
 */
export function tidyTitle(raw: string | null | undefined): string {
  const original = String(raw || '').replace(/\s+/g, ' ').trim()
  if (!original) return ''

  let t = original.replace(/[（(][^（()）]{1,14}[)）]\s*$/g, '').trim()
  const colonAt = t.search(/[：:｜|]/)
  if (colonAt >= 6) t = t.slice(0, colonAt).trim()
  if (!t) t = original

  return t.length > MAX_TITLE ? t.slice(0, MAX_TITLE) + '…' : t
}

/**
 * 生成推荐问题列表。
 *
 * @param articles   候选文章（调用方已按热度/时间排好序，这里按顺序取）
 * @param categories 候选分类
 * @param limit      要几条，默认 4（跟面板的排版对齐）
 * @returns 去重后的字符串数组，长度不超过 limit；任何入参为空都有兜底
 */
export function buildQuickQuestions(
  articles: SuggestArticle[] = [],
  categories: SuggestCategory[] = [],
  limit = 4
): string[] {
  const out: string[] = []
  const wanted = Math.max(0, Math.floor(limit))

  const push = (q: string) => {
    const s = String(q || '').trim()
    if (s && out.length < wanted && !out.includes(s)) out.push(s)
  }

  // 1) 文章问题：最多 3 条，留一条位置给分类，让「单篇」和「一类」两种问法都能被看到
  const maxArticle = Math.min(3, wanted)
  const titles = new Set<string>()
  for (const a of articles) {
    if (out.length >= maxArticle) break
    const t = tidyTitle(a?.title)
    if (!t || titles.has(t)) continue
    titles.add(t)
    push(`「${t}」讲了什么？`)
  }

  // 2) 分类问题补位
  for (const c of categories) {
    if (out.length >= wanted) break
    const name = String(c?.name || '').trim()
    if (!name) continue
    push(`站内有哪些「${name}」的文章？`)
  }

  // 3) 通用兜底
  for (const q of FALLBACK_QUESTIONS) push(q)

  return out
}

/* ================================================================
   问题池 + 「换一批」
   ================================================================ */

export interface QuestionItem {
  /** 稳定标识，直接就用文本本身（池内已去重），存 localStorage 时只记 id */
  id: string
  text: string
}

/**
 * 后台存的是 JSON 数组字符串。任何脏数据（null / 不是数组 / 元素不是字符串）
 * 都降级成空池，让前台回落到「按文章动态生成」，而不是抛错把面板搞白。
 */
export function parseQuestionPool(raw: string | null | undefined): QuestionItem[] {
  if (!raw) return []
  let arr: unknown
  try {
    arr = JSON.parse(String(raw))
  } catch {
    return []
  }
  if (!Array.isArray(arr)) return []
  const seen = new Set<string>()
  const out: QuestionItem[] = []
  for (const v of arr) {
    const text = String(v ?? '').replace(/\s+/g, ' ').trim()
    if (!text || seen.has(text)) continue
    seen.add(text)
    out.push({ id: text, text })
  }
  return out
}

/** Fisher–Yates；rand 可注入，便于测试时固定随机种子 */
export function shuffle<T>(arr: T[], rand: () => number = Math.random): T[] {
  const a = arr.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/**
 * 从问题池里抽一批要显示的问题。
 *
 * 规则（按需求）：
 *   1. 优先抽「不在上一批」的；
 *   2. 排不满 count 条时，用上一批的问题补足（先补上一批，再补池子里其它没用过的）；
 *   3. 返回新的 ids，调用方存起来当作下一轮的「上一批」；
 *   4. 池子 ≤ count 时降级为「允许重复、只尽量改变顺序」——
 *      否则池子里只有 2 条却要显示 4 个格子，永远填不满。
 *
 * 额外保证：池子比 count 大时，新一批不会和上一批完全一样
 * （极端随机下可能抽到同一组，重抽一次就够，不做无限循环）。
 */
export function pickQuestions(
  pool: QuestionItem[],
  lastIds: string[] = [],
  count = 4,
  rand: () => number = Math.random
): { items: QuestionItem[]; ids: string[] } {
  const wanted = Math.max(0, Math.floor(count))
  const items = pool.filter((p) => p && p.id && p.text)
  if (!wanted || items.length === 0) return { items: [], ids: [] }

  // 池子装不满一屏 → 允许重复，只打乱顺序
  if (items.length <= wanted) {
    const order = shuffle(items, rand)
    const out: QuestionItem[] = []
    for (let i = 0; i < wanted; i++) out.push(order[i % order.length])
    return { items: out, ids: out.map((p) => p.id) }
  }

  const lastSet = new Set(lastIds)
  const fresh = shuffle(items.filter((p) => !lastSet.has(p.id)), rand)
  const picked = fresh.slice(0, wanted)

  if (picked.length < wanted) {
    const chosen = new Set(picked.map((p) => p.id))
    const rest = items.filter((p) => !chosen.has(p.id))
    // 先补「上一批里的」，再补其它——这样用户看到的始终是「换掉了几条」而不是整屏刷新
    const prevFirst = [...rest.filter((p) => lastSet.has(p.id)), ...rest.filter((p) => !lastSet.has(p.id))]
    for (const p of prevFirst) {
      if (picked.length >= wanted) break
      picked.push(p)
    }
  }

  // 池子明明够大却抽到跟上一批一模一样，换一次（一次就够，不必循环）
  if (lastSet.size === wanted && picked.every((p) => lastSet.has(p.id))) {
    const again = shuffle(items.filter((p) => !lastSet.has(p.id)), rand).slice(0, wanted)
    if (again.length === wanted) return { items: again, ids: again.map((p) => p.id) }
  }

  return { items: picked, ids: picked.map((p) => p.id) }
}
