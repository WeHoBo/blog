/**
 * 搜索关键词高亮。
 *
 * 两条硬约束：
 * 1. 结果来自文章标题/摘要（用户可控文本），必须**先转义再拼 HTML**，
 *    否则标题里写 `<img onerror=...>` 就能在列表页执行脚本。
 * 2. 只对真正命中的片段加 `<mark>`，不做「先转义整段再正则替换」——
 *    那样 `&` 会变成 `&amp;`，含 `&` 的关键词就再也匹配不上了。
 */

const ESCAPE_MAP: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
}

export function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, ch => ESCAPE_MAP[ch] || ch)
}

/** 逃生正则特殊字符，避免用户输入 `a.b` `(` `*` 之类把正则写坏 */
function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 返回可直接 v-html 的安全片段。
 * keyword 为空时等价于 escapeHtml(text)，因此调用方可以无条件走同一个渲染路径。
 */
export function highlightKeyword(
  text: string | null | undefined,
  keyword: string | null | undefined
): string {
  const src = text || ''
  if (!src) return ''
  const kw = (keyword || '').trim()
  if (!kw) return escapeHtml(src)

  const re = new RegExp(escapeRegExp(kw), 'gi')
  const parts: string[] = []
  let last = 0
  for (const m of src.matchAll(re)) {
    const idx = m.index ?? 0
    parts.push(escapeHtml(src.slice(last, idx)))
    parts.push(`<mark class="search-hit">${escapeHtml(m[0])}</mark>`)
    last = idx + m[0].length
  }
  if (last === 0) return escapeHtml(src)
  parts.push(escapeHtml(src.slice(last)))
  return parts.join('')
}
