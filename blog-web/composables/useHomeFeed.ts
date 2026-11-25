import { computed, reactive, ref } from 'vue'

export interface HomeArticlePage {
  records: any[]
  total: number
}

/** 首页的精品、置顶和时间流独立翻页。切换筛选时作废同一栏的旧请求。 */
export function useHomeFeed(fetchPage: (page: number) => Promise<HomeArticlePage>, pageSize: number) {
  const records = ref<any[]>([])
  const total = ref(0)
  const page = ref(0)
  const pending = ref(false)
  const error = ref(false)
  const hasMore = computed(() => page.value > 0 && page.value * pageSize < total.value)
  let requestId = 0

  function reset(initial: HomeArticlePage | null) {
    requestId++
    records.value = initial?.records ?? []
    total.value = initial?.total ?? 0
    page.value = initial ? 1 : 0
    error.value = !initial
    pending.value = false
  }

  async function load(next = false) {
    if (next && (pending.value || !hasMore.value)) return
    if (!next) reset(null)
    const request = ++requestId
    const target = next ? page.value + 1 : 1
    pending.value = true
    error.value = false
    try {
      const result = await fetchPage(target)
      if (request !== requestId) return
      if (next) {
        const existing = new Set(records.value.map(article => article.id))
        records.value.push(...result.records.filter(article => !existing.has(article.id)))
      } else {
        records.value = result.records
      }
      total.value = result.total
      page.value = target
    } catch {
      if (request === requestId) error.value = true
    } finally {
      if (request === requestId) pending.value = false
    }
  }
  return reactive({ records, total, pending, error, hasMore, reset, load,
    next: () => load(page.value > 0) })
}
