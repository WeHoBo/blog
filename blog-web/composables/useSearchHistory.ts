/**
 * 搜索历史（本地存储）。
 *
 * 只存在浏览器 localStorage 里，不上报、不参与 SSR —— 服务端渲染时 items 恒为空数组，
 * 保证首屏 HTML 与客户端首帧一致，不会触发 hydration mismatch。
 * 挂载后再由 load() 补上真实历史。
 */
export const SEARCH_HISTORY_KEY = 'blog-search-history'
export const SEARCH_HISTORY_MAX = 8

/** 去重 + 去空白 + 截断：同一关键词重复搜索只会把它提到最前面 */
export function normalizeHistory(list: unknown): string[] {
  if (!Array.isArray(list)) return []
  const seen = new Set<string>()
  const out: string[] = []
  for (const raw of list) {
    if (typeof raw !== 'string') continue
    const q = raw.trim()
    if (!q || seen.has(q.toLowerCase())) continue
    seen.add(q.toLowerCase())
    out.push(q)
    if (out.length >= SEARCH_HISTORY_MAX) break
  }
  return out
}

export const useSearchHistory = () => {
  const items = useState<string[]>('blog-search-history', () => [])

  /** 只在浏览器侧调用（onMounted / 交互回调里），避免 SSR 访问 localStorage */
  function load() {
    if (!process.client) return
    try {
      items.value = normalizeHistory(JSON.parse(localStorage.getItem(SEARCH_HISTORY_KEY) || '[]'))
    } catch {
      items.value = []
    }
  }

  function persist() {
    if (!process.client) return
    try {
      localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(items.value))
    } catch { /* 隐私模式 / 配额满：功能降级，不影响搜索本身 */ }
  }

  function add(keyword: string) {
    const q = (keyword || '').trim()
    if (!q) return
    items.value = normalizeHistory([q, ...items.value])
    persist()
  }

  function remove(keyword: string) {
    items.value = items.value.filter(x => x !== keyword)
    persist()
  }

  function clear() {
    items.value = []
    persist()
  }

  return { items, load, add, remove, clear }
}
