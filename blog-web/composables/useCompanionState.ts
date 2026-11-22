/**
 * AI 助手入口的**唯一状态源**（P1）
 *
 * 为什么单独抽一层：
 *   - 桌面用 Live2D 画、手机用 CSS 小鱼画，以后可能还有第三种渲染器 ——
 *     如果状态散落在各个组件里，换渲染器就得重写一遍逻辑；
 *   - 入口要跟着聊天状态动（思考/查资料/打字/出错），这些状态本来就存在于
 *     `useAiChat`（streaming / msg.phase / msg.steps / msg.error），这里只做**映射**，
 *     绝不自己去猜，更不重复发请求。
 *
 * 9 个状态（`hidden` 不是视觉状态，是"暂停"标记，单独用 ref 表示）：
 *   idle     待机：呼吸 + 眨眼 + 视线跟随
 *   hint     首次到访打招呼（一天一次）
 *   thinking 已发出问题、还没吐字
 *   tools    正在调用工具（联网/检索站内文章）
 *   typing   正在流式吐字
 *   done     这一轮答完了（1.8s 后自己回 idle）
 *   error    这一轮失败了（3s 后自己回 idle）
 *   sleep    90s 无人理，打盹；点一下就醒
 */

export type CompanionState =
  | 'idle'
  | 'hint'
  | 'thinking'
  | 'tools'
  | 'typing'
  | 'done'
  | 'error'
  | 'sleep'

/** 气泡文案：只讲"在干什么"，不承载回答正文（正文在聊天面板里） */
const BUBBLES: Record<CompanionState, string> = {
  idle: '',
  hint: '点我聊聊～',
  thinking: '正在想…',
  tools: '正在查站内文章…',
  typing: '整理答案中…',
  done: '好啦，看窗口 →',
  error: '这次答不上来，换个问法？',
  sleep: '',
}

/** 自动回 idle 的时长：done 快一点，error 多留一会儿让人看见 */
const AUTO_IDLE_MS: Partial<Record<CompanionState, number>> = {
  done: 1800,
  error: 3000,
  hint: 6000,
}

/* ── 被戳（poke）─────────────────────────────────────
   和状态机是两套东西：状态机描述「AI 在干什么」，被戳描述「用户手贱」。
   如果硬塞进 CompanionState，就会和 thinking/typing 抢同一个 ref，
   打字打到一半被戳一下，气泡就变成"别戳我"了 —— 那是错的。
   所以单独一个 pokeText，在 bubbleText 里只是**优先级更高**的一层。 */
const POKE_LINES = [
  '别戳我！',
  '别再戳我啦，晕啦~',
  '再戳我要生气了哦',
  '（晕过去了）',
]
/** 两次点击间隔小于这个值才算"连着戳"。太长了随便点两下就升级，太长…… 就是没有 */
const POKE_COMBO_MS = 900
/** 一句话停留多久 */
const POKE_HOLD_MS = 2200

let pokeHoldTimer: ReturnType<typeof setTimeout> | null = null
let pokeLastAt = 0
let pokeCount = 0

/* 计时器放模块级：只在客户端产生，SSR 阶段不会创建。
   注意不要在这里放"用户相关"的状态（那是 useState 的事），否则 SSR 会串用户。 */
let idleTimer: ReturnType<typeof setTimeout> | null = null
let sleepTimer: ReturnType<typeof setTimeout> | null = null

export function useCompanionState() {
  const state = useState<CompanionState>('companion-state', () => 'idle')
  /** 当前工具名，`tools` 状态下拼进气泡：「正在查：搜索站内文章」 */
  const toolName = useState<string>('companion-tool-name', () => '')
  /** 聊天面板是否打开：打开时不再弹气泡（否则和面板里的"正在思考"重复） */
  const panelOpen = useState<boolean>('companion-panel-open', () => false)
  /** 页面是否不可见（切标签页）：不可见时暂停动画 */
  const pageHidden = useState<boolean>('companion-page-hidden', () => false)

  /** 是否正在"干活"：干活期间不进入 sleep */
  const busy = computed(
    () => state.value === 'thinking' || state.value === 'tools' || state.value === 'typing',
  )

  /** 被戳时要说的话；空字符串 = 没说 */
  const pokeText = useState<string>('companion-poke-text', () => '')

  /** 气泡文本；空字符串表示不显示气泡 */
  const bubbleText = computed(() => {
    if (panelOpen.value) return ''
    // 被戳的优先级最高：用户刚动手，得先回应他，而不是继续报"正在查资料"
    if (pokeText.value) return pokeText.value
    if (state.value === 'tools' && toolName.value) return `正在查：${toolName.value}`
    return BUBBLES[state.value] || ''
  })

  /**
   * 被戳一下。
   *
   * 连击判定用「距上次点击的间隔」而不是定时器归零：
   * 定时器归零需要额外一个 timer，而且用户停手后那段空窗里
   * 计数已经清零了但气泡还挂着，容易出现"下一次点又是从第一句开始"的跳变。
   */
  function poke() {
    if (import.meta.server) return
    const now = Date.now()
    pokeCount = now - pokeLastAt < POKE_COMBO_MS ? pokeCount + 1 : 1
    pokeLastAt = now
    pokeText.value = POKE_LINES[Math.min(pokeCount, POKE_LINES.length) - 1]

    if (pokeHoldTimer) clearTimeout(pokeHoldTimer)
    pokeHoldTimer = setTimeout(() => {
      pokeText.value = ''
      pokeHoldTimer = null
    }, POKE_HOLD_MS)
    armSleep()
  }

  function clearTimers() {
    if (idleTimer) { clearTimeout(idleTimer); idleTimer = null }
    if (sleepTimer) { clearTimeout(sleepTimer); sleepTimer = null }
    if (pokeHoldTimer) { clearTimeout(pokeHoldTimer); pokeHoldTimer = null }
  }

  /**
   * 重置"打盹"计时。任何一次状态变化都算"有人理它"，
   * 所以统一在这里重置，而不是散落在每个 setter 里。
   *
   * ⚠️ 必须守住服务端：`setPanelOpen` / `wake` 会在 setup 阶段被调用，
   *    没有这道守卫，SSR 每渲染一次页面就在 Node 里留一个 90 秒的定时器。
   */
  function armSleep(ms = 90000) {
    if (import.meta.server) return
    if (sleepTimer) clearTimeout(sleepTimer)
    sleepTimer = setTimeout(() => {
      // 正在回答、面板开着、页面不可见，都不该睡
      if (busy.value || panelOpen.value || pageHidden.value) { armSleep(ms); return }
      state.value = 'sleep'
    }, ms)
  }

  function set(next: CompanionState, opts: { autoIdle?: boolean } = {}) {
    // SSR 阶段完全不碰计时器
    if (import.meta.server) { state.value = next; return }
    state.value = next
    if (idleTimer) { clearTimeout(idleTimer); idleTimer = null }
    const ms = opts.autoIdle === false ? 0 : AUTO_IDLE_MS[next] || 0
    if (ms) {
      idleTimer = setTimeout(() => { state.value = 'idle' }, ms)
    }
    armSleep()
  }

  const setIdle = () => set('idle', { autoIdle: false })
  const setHint = () => set('hint')
  const setThinking = () => set('thinking', { autoIdle: false })
  const setTyping = () => set('typing', { autoIdle: false })
  const setDone = () => set('done')
  const setError = () => set('error')

  function setTools(name = '') {
    toolName.value = name || ''
    set('tools', { autoIdle: false })
  }

  /** 把 `useAiChat` 的状态映射进来（AiFloat 里只调这一个函数） */
  type Derived = 'idle' | 'thinking' | 'tools' | 'typing' | 'done' | 'error'
  function applyDerived(next: Derived, tool = '') {
    // 同是 tools、但换了个工具名（先检索再联网），气泡文案要跟着换，
    // 所以不能一看到"状态没变"就直接 return。
    if (next === state.value) {
      if (next === 'tools' && tool !== toolName.value) toolName.value = tool || ''
      return
    }
    switch (next) {
      case 'thinking': setThinking(); break
      case 'tools': setTools(tool); break
      case 'typing': setTyping(); break
      case 'done': setDone(); break
      case 'error': setError(); break
      default: setIdle()
    }
  }

  function setPanelOpen(v: boolean) {
    panelOpen.value = v
    if (import.meta.server) return
    if (v) {
      // 面板一开就醒（用户显然在理它）
      if (state.value === 'sleep') setIdle()
      armSleep()
      return
    }
    /* 关闭面板时，如果停在「一次性状态」上（done / error / hint），
       要重新起一次自动回落计时。
       为什么需要：入口组件会随面板卸载，卸载时 clearTimers 把计时一起清了；
       重新挂载时状态还停在 done，小鱼就会一直笑着眼、气泡一直挂着不消失。 */
    const cur = state.value
    if (cur === 'done' || cur === 'error' || cur === 'hint') set(cur)
    else armSleep()
  }

  function setPageHidden(v: boolean) {
    pageHidden.value = v
    if (import.meta.server) return
    if (!v && state.value === 'sleep') armSleep()
  }

  function wake() {
    if (state.value === 'sleep') setIdle()
    armSleep()
  }

  function sleep() {
    if (busy.value) return
    if (sleepTimer) { clearTimeout(sleepTimer); sleepTimer = null }
    state.value = 'sleep'
  }

  return {
    state,
    toolName,
    panelOpen,
    pageHidden,
    busy,
    bubbleText,
    pokeText,
    poke,
    setIdle,
    setHint,
    setThinking,
    setTools,
    setTyping,
    setDone,
    setError,
    applyDerived,
    setPanelOpen,
    setPageHidden,
    wake,
    sleep,
    clearTimers,
  }
}
