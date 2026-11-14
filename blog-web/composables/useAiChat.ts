/** 一次工具调用在 UI 上的展示状态 */
export interface ChatStep {
  step: number
  name: string
  state: 'start' | 'done'
  ok?: boolean
  /** 工具返回的一句话摘要，例如「命中 3 篇：《RAG 入门》…」 */
  digest?: string
  error?: string
}

export interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
  sources?: any[]
  steps?: ChatStep[]
  /** thinking / 空 —— 用于在还没有正文时显示"正在思考" */
  phase?: string
  /** 用户主动点了停止 */
  aborted?: boolean
  /** 本轮实际使用的模型（降级后与请求开始时不同） */
  model?: string
  /** 主模型失败、用了降级模型（P4）。只用于在回答下方挂一行说明 */
  degraded?: boolean
}

/** 工具名 → 人话。后端返回的是 snake_case 工具名，直接把英文显示给用户太生硬。 */
export const TOOL_LABELS: Record<string, string> = {
  search_articles: '检索站内文章',
  get_article: '阅读文章正文',
  related_articles: '查找相关文章',
  list_taxonomy: '查看分类与标签',
  site_stats: '统计站点数据',
  search_knowledge: '检索知识库',
  web_search: '联网搜索'
}

export function toolLabel(name: string): string {
  return TOOL_LABELS[name] || name
}

/**
 * 当前进行中的请求控制器。
 *
 * 放模块级而不是 useState：AbortController 不可序列化，塞进 useState 会被 SSR payload 尝试序列化。
 * 只有浏览器侧才会给它赋值（streamRequest 里），服务端始终是 null。
 */
let abortCtrl: AbortController | null = null

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
  // 今日额度：面板打开时拉一次，让用户知道还能问几次，而不是聊到一半被突然拦住
  const quota = useState<any>('ai-chat-quota', () => null)

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
    if (open.value) loadQuota()
  }

  function openPanel() {
    if (!authStore.isLoggedIn) {
      window.location.href = '/login'
      return
    }
    open.value = true
    loadQuota()
  }

  /** 拉取今日剩余额度。失败静默忽略——额度提示是辅助信息，不该影响主流程。 */
  async function loadQuota() {
    if (!process.client) return
    try {
      const resp = await fetch(`${config.public.apiBase}/ai/quota`, {
        headers: { Authorization: `Bearer ${token.value}` }
      })
      if (!resp.ok) return
      const json = await resp.json()
      if (json?.code === 200) quota.value = json.data
    } catch { /* 忽略 */ }
  }

  function close() { open.value = false }

  function clear() {
    stop()
    messages.value = []
    pendingSources.value = []
  }

  /** 停止当前回答。后端在下一行输出时会发现连接已断并取消任务，token 也随之停止计费。 */
  function stop() {
    if (abortCtrl) {
      abortCtrl.abort()
      abortCtrl = null
    }
    streaming.value = false
  }

  function scrollToBottom() {
    nextTick(() => {
      const el = document.querySelector('.ai-chat-body')
      if (el) el.scrollTop = el.scrollHeight
    })
  }

  /** 解析一个 SSE 事件块（可能包含 event: 与多行 data:） */
  function parseFrame(raw: string): { event: string; data: any } | null {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of raw.split('\n')) {
      if (!line || line.startsWith(':')) continue   // 空行 / 心跳注释
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    }
    if (!dataLines.length) return null
    try {
      return { event, data: JSON.parse(dataLines.join('\n')) }
    } catch {
      return null
    }
  }

  /**
   * 发起一次 SSE 请求，把事件分发到最后一条 assistant 消息上。
   *
   * ⚠️ 必须用缓冲区按 `\n\n` 切事件：一次 read() 可能只拿到半个事件（TCP 分片），
   * 也可能一次拿到三个事件。直接对每个 chunk 做 JSON.parse 是 SSE 客户端最典型的 bug。
   */
  async function streamRequest(url: string, body: any): Promise<void> {
    const target = (): ChatMsg | null => {
      const last = messages.value[messages.value.length - 1]
      return last && last.role === 'assistant' ? last : null
    }

    const dispatch = (event: string, data: any) => {
      const msg = target()
      if (!msg) return
      switch (event) {
        case 'status':
          msg.phase = data?.phase || ''
          break
        case 'token':
          msg.phase = ''
          msg.content += String(data?.text ?? '')
          break
        case 'tool': {
          msg.phase = ''
          if (!msg.steps) msg.steps = []
          if (data?.state === 'start') {
            msg.steps.push({ step: data.step, name: data.name, state: 'start' })
          } else {
            // 从后往前找同一步同名、还在 start 状态的条目补上结果
            for (let i = msg.steps.length - 1; i >= 0; i--) {
              const s = msg.steps[i]
              if (s.state === 'start' && s.name === data?.name) {
                msg.steps[i] = {
                  ...s,
                  state: 'done',
                  ok: data.ok,
                  digest: data.digest,
                  error: data.error
                }
                break
              }
            }
          }
          break
        }
        case 'sources':
          msg.sources = Array.isArray(data) ? data : []
          pendingSources.value = msg.sources
          break
        case 'error':
          // 限流文案要原样显示，不能统一成"请求失败"
          msg.content += (msg.content ? '\n\n' : '') + '> ⚠️ ' + (data?.message || '请求失败')
          break
        case 'done':
          msg.phase = ''
          // 降级信息随 done 一起到（P4）：不告诉用户的话，他只会觉得"这次回答怎么变差了"
          if (data && typeof data === 'object') {
            msg.degraded = Boolean((data as any).degraded)
            if ((data as any).model) msg.model = String((data as any).model)
          }
          break
      }
    }

    abortCtrl = new AbortController()
    const resp = await fetch(`${config.public.apiBase}${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token.value}`,
        Accept: 'text/event-stream'
      },
      body: JSON.stringify(body),
      signal: abortCtrl.signal
    })

    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      // 422 在这两个接口上几乎只有一个原因：提交内容超过 pydantic 的 max_length。
      // 原样把 FastAPI 的 detail JSON 甩到气泡里，用户只会看到一坨英文，
      // 完全不知道"是我选的内容太长"——所以这里必须翻译成人话。
      if (resp.status === 422 || resp.status === 413) {
        throw new Error('内容太长了，超出单次请求上限，请缩短后重试（长文可以分段提问）')
      }
      throw new Error(text?.slice(0, 200) || `请求失败(${resp.status})`)
    }
    if (!resp.body) throw new Error('浏览器不支持流式响应')

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')
      let idx = buffer.indexOf('\n\n')
      while (idx !== -1) {
        const raw = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const frame = parseFrame(raw)
        if (frame) dispatch(frame.event, frame.data)
        idx = buffer.indexOf('\n\n')
      }
      scrollToBottom()
    }
  }

  // 追加用户消息并开始回答（assistant 消息置顶流式填充）
  function pushUser(q: string) {
    messages.value.push({ role: 'user', content: q })
    messages.value.push({ role: 'assistant', content: '', steps: [], phase: 'thinking' })
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

  function failMessage(e: any): string {
    if (e?.name === 'AbortError') return ''
    return e?.message || '请求失败'
  }

  /** Agent 问答（可检索站内文章 / 知识库） */
  async function ask(question: string, articleId?: string | number) {
    const q = question.trim()
    if (!q || streaming.value) return
    pushUser(q)
    try {
      const body: any = { question: q, top_k: 5 }
      if (articleId) body.article_id = String(articleId)
      mode.value = 'rag'
      await streamRequest('/ai/chat', body)
    } catch (e: any) {
      const last = lastAssistant()
      if (last) {
        if (e?.name === 'AbortError') {
          last.aborted = true
          if (!last.content) last.content = '（已停止）'
        } else {
          last.content = '⚠️ ' + failMessage(e)
        }
      }
    } finally {
      abortCtrl = null
      streaming.value = false
      const last = lastAssistant()
      if (last) last.phase = ''
      if (last && !last.content) last.content = '（无回答）'
      scrollToBottom()
      loadQuota()
    }
  }

  /** 自由问答（代码解释 / 全文总结，不检索） */
  async function freeAsk(question: string, explainMode = true) {
    const q = question.trim()
    if (!q || streaming.value) return
    pushUser(q.length > 200 ? q.slice(0, 200) + '…' : q)
    try {
      mode.value = 'free'
      await streamRequest('/ai/free', { question: q, mode: explainMode ? 'explain' : 'chat' })
    } catch (e: any) {
      const last = lastAssistant()
      if (last) {
        if (e?.name === 'AbortError') {
          last.aborted = true
          if (!last.content) last.content = '（已停止）'
        } else {
          last.content = '⚠️ ' + failMessage(e)
        }
      }
    } finally {
      abortCtrl = null
      streaming.value = false
      const last = lastAssistant()
      if (last) last.phase = ''
      if (last && !last.content) last.content = '（无回答）'
      scrollToBottom()
      loadQuota()
    }
  }

  return {
    open, toggle, openPanel, close,
    messages, input, streaming, pendingSources, mode, quota,
    clear, stop, ask, freeAsk, loadQuota
  }
}
