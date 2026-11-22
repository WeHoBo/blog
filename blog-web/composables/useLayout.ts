export const LAYOUT_KEY = 'blog-layout'
export const SORT_KEY = 'blog-sort'

/** 首页「中间/右侧视图互换」的持久化 key */
export const HOME_SWAP_KEY = 'blog-home-swap'

export function parseLayout(value: string | null): 'list' | 'grid' {
  return value === 'grid' ? 'grid' : 'list'
}

export function parseSort(value: string | null): 'time' | 'title' {
  return value === 'title' ? 'title' : 'time'
}

/** 互换状态的口径：只认 '1'，其余（含 null / '0' / 脏值）都当 false */
export function parseSwap(value: string | null): boolean {
  return value === '1'
}

export const useLayout = () => {
  const viewMode = useState<'list' | 'grid'>('blog-layout', () => 'list')
  const sortMode = useState<'time' | 'title'>('blog-sort', () => 'time')
  /**
   * 首页中间那栏的视图形态（第二轮返工后**只切中间**，右侧「全部文章」固定列表）。
   * false（默认）= 中间列表；true = 中间网格。
   *
   * ⚠️ 这个值**不在 setup 期间读 localStorage**：SSR 读不到，客户端能读到，
   * 直接同步会造成「服务端吐 A、水合后变 B」。浏览器里的值由页面在
   * onMounted 里调 restoreHomeSwap() 补上，代价是切换过形态的用户会看到
   * 一次极短的重排（几百毫秒内），换来的是不报 hydration mismatch。
   */
  const homeSwapped = useState<boolean>('blog-home-swap', () => false)

  function setViewMode(mode: 'list' | 'grid') {
    viewMode.value = mode
    if (process.client) localStorage.setItem(LAYOUT_KEY, mode)
  }

  function setSortMode(mode: 'time' | 'title') {
    sortMode.value = mode
    if (process.client) localStorage.setItem(SORT_KEY, mode)
  }

  function setHomeSwap(flag: boolean) {
    homeSwapped.value = flag
    if (process.client) localStorage.setItem(HOME_SWAP_KEY, flag ? '1' : '0')
  }

  function toggleHomeSwap() {
    setHomeSwap(!homeSwapped.value)
  }

  /** 只在客户端 onMounted 里调用，别在 setup 里调 */
  function restoreHomeSwap() {
    if (!process.client) return
    homeSwapped.value = parseSwap(localStorage.getItem(HOME_SWAP_KEY))
  }

  if (process.client) {
    viewMode.value = parseLayout(localStorage.getItem(LAYOUT_KEY))
    sortMode.value = parseSort(localStorage.getItem(SORT_KEY))
  }

  return { viewMode, setViewMode, sortMode, setSortMode, homeSwapped, setHomeSwap, toggleHomeSwap, restoreHomeSwap }
}
