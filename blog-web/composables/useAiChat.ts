export interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
  sources?: any[]
}

export const useAiChat = () => {
  const config = useRuntimeConfig()
  const token = useCookie('token')
  const authStore = useAuthStore()

  const open = useState<boolean>('ai-chat-open', () => false)
  const messages = useState<ChatMsg[]>('ai-chat-messages', () => [])
  const input = useState<string>('ai-chat-input', () => '')
  const streaming = useState<boolean>('ai-chat-streaming', () => false)
  const pendingSources = useState<any[]>('ai-chat-sources', () => [])
  const mode = useState<'rag' | 'free'>('ai-chat-mode', () => 'rag')

  // 防御：清理历史污染数据（如 SSR payload 或旧版本留下的非法元素）
  if (process.client && messages.value.length) {
    messages.value = messages.value.filter((m: any) => m && (m.role === 'user' || m.role === 'assistant'))
  }

  function toggle() {
    if (!authStore.isLoggedIn) {
      window.location.href = '/login'
      return
    }
    open.value = !open.value
  }

  function openPanel() {
    if (!authStore.isLoggedIn) {
      window.location.href = '/login'
      return
    }
    open.value = true
  }

  function close() { open.value = false }

  function clear() {
    messages.value = []
    pendingSources.value = []
  }

  function scrollToBottom() {
    nextTick(() => {
      const el = document.querySelector('.ai-chat-body')
      if (el) el.scrollTop = el.scrollHeight
    })
  }

  // 原生 fetch 逐块读取，实现真流式
  async function streamRequest(url: string, body: any): Promise<string> {
    const resp = await fetch(`${config.public.apiBase}${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token.value}`,
        Accept: 'text/plain'
      },
      body: JSON.stringify(body)
    })
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text?.slice(0, 200) || `请求失败(${resp.status})`)
    }
    const reader = resp.body!.getReader()
    const decoder = new TextDecoder('utf-8')
    let full = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      full += decoder.decode(value, { stream: true })
      const last = messages.value[messages.value.length - 1]
      if (last) last.content = full.replace(/\n@@SOURCES@@\n.*$/s, '')
    }
    return full
  }

  // 追加用户消息并开始回答（assistant 消息置顶流式填充）
  function pushUser(q: string) {
    messages.value.push({ role: 'user', content: q })
    messages.value.push({ role: 'assistant', content: '' })
    pendingSources.value = []
    streaming.value = true
    scrollToBottom()
  }

  // 获取最后一条 assistant 消息（可能已被清空，返回 null 则忽略写入）
  function lastAssistant(): ChatMsg | null {
    if (!messages.value.length) return null
    const last = messages.value[messages.value.length - 1]
    return last && last.role === 'assistant' ? last : null
  }

  /** RAG 问答（检索博客文章） */
  async function ask(question: string, articleId?: string | number) {
    const q = question.trim()
    if (!q || streaming.value) return
    pushUser(q)
    try {
      const body: any = { question: q, top_k: 5 }
      if (articleId) body.article_id = String(articleId)
      mode.value = 'rag'
      const full = await streamRequest('/ai/chat', body)
      const idx = full.indexOf('\n@@SOURCES@@\n')
      const last = lastAssistant()
      if (last) {
        last.content = (idx === -1 ? full : full.slice(0, idx)) || '（无回答）'
        if (idx !== -1) {
          try {
            pendingSources.value = JSON.parse(full.slice(idx + '\n@@SOURCES@@\n'.length))
            last.sources = pendingSources.value
          } catch { /* ignore */ }
        }
      }
    } catch (e: any) {
      const last = lastAssistant()
      if (last) last.content = '⚠️ ' + (e.message || '请求失败')
    } finally {
      streaming.value = false
      scrollToBottom()
    }
  }

  /** 自由问答（代码解释 / 全文总结，不检索） */
  async function freeAsk(question: string, explainMode = true) {
    const q = question.trim()
    if (!q || streaming.value) return
    pushUser(q.length > 200 ? q.slice(0, 200) + '…' : q)
    try {
      mode.value = 'free'
      const full = await streamRequest('/ai/free', { question: q, mode: explainMode ? 'explain' : 'chat' })
      const last = lastAssistant()
      if (last) last.content = full || '（无回答）'
    } catch (e: any) {
      const last = lastAssistant()
      if (last) last.content = '⚠️ ' + (e.message || '请求失败')
    } finally {
      streaming.value = false
      scrollToBottom()
    }
  }

  return {
    open, toggle, openPanel, close,
    messages, input, streaming, pendingSources, mode,
    clear, ask, freeAsk
  }
}
