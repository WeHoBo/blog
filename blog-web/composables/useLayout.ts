export const LAYOUT_KEY = 'blog-layout'
export const SORT_KEY = 'blog-sort'

export function parseLayout(value: string | null): 'list' | 'grid' {
  return value === 'grid' ? 'grid' : 'list'
}

export function parseSort(value: string | null): 'time' | 'title' {
  return value === 'title' ? 'title' : 'time'
}

export const useLayout = () => {
  const viewMode = useState<'list' | 'grid'>('blog-layout', () => 'list')
  const sortMode = useState<'time' | 'title'>('blog-sort', () => 'time')

  function setViewMode(mode: 'list' | 'grid') {
    viewMode.value = mode
    if (process.client) localStorage.setItem(LAYOUT_KEY, mode)
  }

  function setSortMode(mode: 'time' | 'title') {
    sortMode.value = mode
    if (process.client) localStorage.setItem(SORT_KEY, mode)
  }

  if (process.client) {
    viewMode.value = parseLayout(localStorage.getItem(LAYOUT_KEY))
    sortMode.value = parseSort(localStorage.getItem(SORT_KEY))
  }

  return { viewMode, setViewMode, sortMode, setSortMode }
}
