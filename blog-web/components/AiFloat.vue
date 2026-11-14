<template>
  <div>
    <!-- 首次提示气泡 -->
    <Transition name="ai-pop">
      <div v-if="showHint && !open" class="fixed bottom-24 right-6 z-[65] bg-white dark:bg-gray-800 rounded-2xl shadow-xl border border-gray-100 dark:border-gray-700 px-4 py-3 text-sm max-w-[200px] relative">
        <button @click="dismissHint" class="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-gray-200 dark:bg-gray-600 text-gray-500 dark:text-gray-300 flex items-center justify-center text-xs hover:bg-gray-300 dark:hover:bg-gray-500 transition">✕</button>
        <div class="font-medium text-gray-800 dark:text-gray-200 mb-0.5">👋 你好！</div>
        <p class="text-gray-500 dark:text-gray-400 text-xs leading-relaxed">有问题可以问我，我能根据本站技术文章帮你解答 🤖</p>
      </div>
    </Transition>

    <!-- 悬浮按钮 -->
    <button v-if="!open" @click="toggle"
      class="fixed bottom-6 right-6 z-[65] w-14 h-14 rounded-2xl bg-gradient-to-br from-primary-500 to-primary-700 text-white shadow-lg shadow-primary-600/25 hover:shadow-xl hover:shadow-primary-600/30 hover:-translate-y-0.5 active:scale-95 transition-all duration-200 flex items-center justify-center group">
      <svg v-if="!streaming" class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="4" y="8" width="16" height="12" rx="3" stroke-width="1.8"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M12 8V4m0 0l-2 2m2-2l2 2"/><circle cx="9" cy="13" r="1" fill="currentColor" stroke="none"/><circle cx="15" cy="13" r="1" fill="currentColor" stroke="none"/><path stroke-linecap="round" stroke-width="1.5" d="M9.5 16.5h5"/></svg>
      <span v-else class="flex gap-1.5">
        <span class="w-1.5 h-1.5 bg-white/90 rounded-full animate-bounce"></span>
        <span class="w-1.5 h-1.5 bg-white/60 rounded-full animate-bounce" style="animation-delay:.15s"></span>
        <span class="w-1.5 h-1.5 bg-white/40 rounded-full animate-bounce" style="animation-delay:.3s"></span>
      </span>
      <span class="absolute right-full mr-3 px-2.5 py-1 rounded-lg bg-gray-900 dark:bg-gray-700 text-white text-xs whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">{{ aiName }}</span>
    </button>

    <!-- 聊天窗口 -->
    <Teleport to="body">
      <Transition name="ai-window">
        <div v-if="open"
          class="ai-panel fixed z-[80] bottom-4 right-4 sm:bottom-6 sm:right-6 max-h-[85vh] bg-white dark:bg-gray-900 rounded-2xl shadow-2xl border border-gray-100 dark:border-gray-700 flex flex-col overflow-hidden"
          :style="panelStyle">
          <!-- 左上角拖拽把手：面板贴右下角，所以「变大」是往左上拉。
               只在桌面端出现 —— 手机上窗口是全宽底部抽屉，改了宽度反而更难用。 -->
          <div class="hidden sm:flex absolute top-0 left-0 z-10 w-5 h-5 items-start justify-start cursor-nwse-resize group"
            title="拖动调整窗口大小" @pointerdown="startResize">
            <svg class="w-3 h-3 mt-1 ml-1 text-gray-300 dark:text-gray-600 group-hover:text-primary-500 transition" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" viewBox="0 0 12 12" aria-hidden="true">
              <path d="M1 5V1h4M1 9V8"/>
            </svg>
          </div>
          <!-- Header -->
          <div class="px-4 py-3 border-b dark:border-gray-700 flex items-center justify-between bg-gradient-to-r from-primary-50/80 to-transparent dark:from-primary-900/20">
            <div class="flex items-center gap-2.5">
              <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center text-white flex-shrink-0">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="4" y="8" width="16" height="12" rx="3" stroke-width="1.8"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M12 8V4m0 0l-2 2m2-2l2 2"/></svg>
              </div>
              <div>
                <div class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ aiName }}</div>
                <div class="text-[10px] text-gray-400">{{ quotaText }}</div>
              </div>
            </div>
            <div class="flex items-center gap-1">
              <button v-if="messages.length" @click="clear" title="清空对话" class="w-7 h-7 rounded-lg flex items-center justify-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.87 12.14A2 2 0 0116.14 21H7.86a2 2 0 01-1.99-1.86L5 7m5 4v6m4-6v6M9 7V4h6v3m-9 0h12"/></svg>
              </button>
              <button @click="close" class="w-7 h-7 rounded-lg flex items-center justify-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition">✕</button>
            </div>
          </div>

          <!-- Body -->
          <div ref="bodyEl" class="ai-chat-body flex-1 overflow-y-auto px-4 py-4 space-y-4">
            <!-- 欢迎语 + 问题池（文案全部来自后台「站点设置 → AI 助手」） -->
            <div v-if="visibleMessages.length === 0 && !streaming" class="text-center pt-6 pb-2">
              <div class="text-4xl mb-3">🤖</div>
              <p class="text-gray-800 dark:text-gray-100 font-medium text-sm">👋 你好，我是{{ aiName }}</p>
              <p class="text-gray-400 text-xs mt-2 leading-relaxed px-2">{{ aiGreeting }}</p>
              <div v-if="quickQuestions.length" class="mt-5 px-2">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-[11px] text-gray-400">试试这样问</span>
                  <button @click="refreshQuestions" :disabled="streaming"
                    class="inline-flex items-center gap-1 text-[11px] text-gray-400 hover:text-primary-600 dark:hover:text-primary-400 disabled:opacity-40 transition"
                    title="从问题池里换一批">
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M21 12a9 9 0 1 1-3-6.7"/><path d="M21 3v5h-5"/></svg>
                    换一批
                  </button>
                </div>
                <div class="flex flex-col gap-2">
                  <button v-for="q in quickQuestions" :key="q.id" @click="ask(q.text)" :disabled="streaming"
                    class="text-left text-sm px-3.5 py-2.5 rounded-xl border border-gray-100 dark:border-gray-700 bg-gray-50/60 dark:bg-gray-800/60 text-gray-600 dark:text-gray-300 hover:border-primary-300 dark:hover:border-primary-700 hover:text-primary-600 dark:hover:text-primary-400 disabled:opacity-50 transition">
                    {{ q.text }}
                  </button>
                </div>
              </div>
            </div>

            <!-- 消息 -->
            <template v-for="(msg, i) in visibleMessages" :key="i">
              <div v-if="msg.role === 'user'" class="flex justify-end">
                <div class="max-w-[85%] px-3.5 py-2 rounded-2xl rounded-br-sm bg-primary-600 text-white text-sm whitespace-pre-wrap break-words">{{ msg.content }}</div>
              </div>
              <div v-else class="flex justify-start">
                <div class="max-w-[92%] min-w-0">
                  <div v-if="msg.content || msg.phase || (msg.steps && msg.steps.length) || (streaming && i === visibleMessages.length - 1)"
                    class="rounded-2xl rounded-bl-sm border border-gray-100 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3.5 py-2.5 text-sm text-gray-700 dark:text-gray-300 overflow-hidden ai-msg">
                    <!-- 工具调用轨迹：让用户看到"它正在查什么"，而不是干等一片空白 -->
                    <div v-if="msg.steps && msg.steps.length" class="mb-2 space-y-1">
                      <div v-for="(s, si) in msg.steps" :key="si"
                        class="flex items-start gap-1.5 text-[11px] leading-snug"
                        :class="s.state === 'done' && s.ok === false ? 'text-amber-600 dark:text-amber-400' : 'text-gray-400 dark:text-gray-500'">
                        <svg v-if="s.state === 'start'" class="w-3 h-3 mt-[3px] shrink-0 animate-spin" fill="none" viewBox="0 0 24 24">
                          <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2.5" class="opacity-25"/>
                          <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/>
                        </svg>
                        <svg v-else-if="s.ok === false" class="w-3 h-3 mt-[3px] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>
                        </svg>
                        <svg v-else class="w-3 h-3 mt-[3px] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="m4 12.5 5 5L20 6.5"/>
                        </svg>
                        <span class="min-w-0 break-words">
                          {{ toolLabel(s.name) }}
                          <template v-if="s.state === 'start'">…</template>
                          <template v-else-if="s.digest"> · {{ s.digest }}</template>
                          <template v-else-if="s.error"> · {{ s.error }}</template>
                        </span>
                      </div>
                    </div>
                    <div v-if="msg.content">
                      <MarkdownRenderer :content="msg.content" />
                    </div>
                    <span v-else-if="msg.phase === 'thinking' || (streaming && i === visibleMessages.length - 1)"
                      class="text-gray-400 dark:text-gray-500 text-xs">正在思考…</span>
                    <span v-if="streaming && i === visibleMessages.length - 1 && msg.content"
                      class="inline-block w-2 h-4 bg-primary-500 animate-pulse align-middle"></span>
                  </div>
                  <!-- 降级说明（P4）：只在真的换了备用模型时出现。
                       不显示的话，用户只会觉得"这次回答怎么变差了"，而不知道原因 -->
                  <p v-if="msg.degraded"
                    class="mt-1.5 inline-flex items-center gap-1 text-[10px] text-gray-400 dark:text-gray-500">
                    <svg class="w-3 h-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.6"
                        d="M12 9v4m0 4h.01M10.3 3.9L2.6 17a1.5 1.5 0 001.3 2.2h16.2a1.5 1.5 0 001.3-2.2L13.7 3.9a1.5 1.5 0 00-2.6 0z"/>
                    </svg>
                    主模型暂时不可用，本条回答由备用模型生成
                  </p>
                  <!-- 来源卡片三档视觉：站内=主题色可跳转 / PDF=灰不可点 / 站外=灰可点、带域名 -->
                  <div v-if="msg.sources && msg.sources.length" class="mt-2">
                    <p class="text-[10px] text-gray-400 mb-1 font-medium">📚 参考来源</p>
                    <div class="flex flex-wrap gap-1.5">
                      <template v-for="(s, si) in msg.sources" :key="s.url || s.title || si">
                        <!-- 站外网页：中性灰 + ↗，视觉上明确比站内低一档
                             （不能加下划线/主题色，否则读者分不清哪个是自己写的） -->
                        <a v-if="webUrl(s)" :href="webUrl(s)!" target="_blank"
                          rel="noopener noreferrer nofollow"
                          class="inline-flex items-start gap-1 max-w-[230px] px-2.5 py-1 rounded-lg text-[11px] bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 border border-gray-200 dark:border-gray-700 hover:bg-gray-200 dark:hover:bg-gray-700/60 transition"
                          :title="s.url">
                          <svg class="w-3 h-3 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M7 17L17 7M17 7H9M17 7v8"/>
                          </svg>
                          <span class="min-w-0">
                            <span class="block truncate">{{ truncateTitle(s.title, 24) }}</span>
                            <span class="block text-[10px] text-gray-400 truncate">{{ s.domain || domainOf(s.url) || '外部链接' }}</span>
                          </span>
                        </a>
                        <!-- 站内文章：走相对路径白名单，避免非法 URL 从上一档掉进来被当路由 -->
                        <NuxtLink v-else-if="internalPath(s.url)" :to="internalPath(s.url)!"
                          class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300 border border-primary-100 dark:border-primary-800/40 hover:bg-primary-100 dark:hover:bg-primary-900/40 transition">
                          <span>📄</span>{{ sourceLabel(s) }} →
                        </NuxtLink>
                        <span v-else
                          class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 border border-gray-200 dark:border-gray-700"
                          :title="s.page ? `第 ${s.page} 页` : ''">
                          <span>📄</span>{{ sourceLabel(s) }}<template v-if="s.page"> · 第{{ s.page }}页</template>
                        </span>
                      </template>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <!-- Input -->
          <div class="border-t dark:border-gray-700 p-3">
            <div class="flex items-end gap-2">
              <!-- maxlength 与 RAG ChatRequest 的上限是两层保险：这里 2000 是产品约束
                   （手打提问不该这么长），后端 8000 是兜底（给"带内容"的程序化提问留余量）。
                   没有 maxlength 时，粘一大段进来直接 422，用户看不到任何字数提示。 -->
              <textarea v-model="input" @keydown.enter.exact.prevent="sendInput" rows="1"
                maxlength="2000"
                placeholder="输入问题，Enter 发送 / Shift+Enter 换行"
                class="flex-1 resize-none px-3.5 py-2.5 rounded-xl border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 dark:text-gray-100 text-sm focus:border-primary-400 focus:outline-none transition max-h-32 min-h-[42px]"
                :disabled="streaming"></textarea>
              <button v-if="streaming" @click="stop" title="停止生成"
                class="w-10 h-10 rounded-xl border border-gray-200 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 flex items-center justify-center transition flex-shrink-0">
                <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
              </button>
              <button v-else @click="sendInput" :disabled="!input.trim()"
                class="w-10 h-10 rounded-xl bg-primary-600 text-white flex items-center justify-center hover:bg-primary-700 disabled:opacity-40 transition flex-shrink-0">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h14m0 0l-6-6m6 6l-6 6"/></svg>
              </button>
            </div>
            <p class="text-[10px] text-gray-300 dark:text-gray-600 mt-1.5 text-center">AI 基于本站文章回答，联网时含第三方网页内容，可能有误，请核对原文</p>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { toolLabel } from '~/composables/useAiChat'
import { safeExternalUrl, domainOf, isExternalSource, internalPath, truncateTitle } from '~/utils/externalLink'
import { buildQuickQuestions, parseQuestionPool, pickQuestions, shuffle, type QuestionItem } from '~/utils/aiSuggest'

// 解构出顶层 ref —— 模板中 ref 自动解包，v-model 才能正确读写字符串
const { open, toggle, close, messages, input, streaming, clear, stop, ask, quota } = useAiChat()
const { get } = useApi()

/**
 * 站外来源的可用 URL。
 *
 * 服务端已经过滤过协议，这里在渲染前再校验一次：`<a :href>` 一旦漏掉校验
 * 就是可点击的 XSS 入口，而多写一行 `new URL()` 的成本几乎为零（纵深防御）。
 * 返回 null 时模板会退到"不可点击"分支。
 */
function webUrl(s: any): string | null {
  return isExternalSource(s) ? safeExternalUrl(s.url) : null
}

/** 来源标题截断，避免一条超长标题把卡片撑破 */
function sourceLabel(s: any): string {
  const text = String(s?.title || s?.filename || '未命名来源')
  return text.length > 18 ? text.slice(0, 18) + '…' : text
}

/** 副标题：优先显示剩余额度，拿不到就退回原来的品牌文案 */
const quotaText = computed(() => {
  const q = quota.value
  if (!q) return '博客知识库助手'
  const used = typeof q.requestCount === 'number' ? q.requestCount : 0
  if (q.unlimited) return `管理员 · 今日已用 ${used} 次`
  if (typeof q.remaining === 'number') {
    return q.remaining > 0 ? `今日剩余 ${q.remaining} 次` : '今日额度已用完'
  }
  return '博客知识库助手'
})

// MarkdownRenderer 含 mermaid 体积大（~2MB），异步加载避免拖慢全局页面
const MarkdownRenderer = defineAsyncComponent(() => import('./MarkdownRenderer.vue'))

const bodyEl = ref<HTMLElement | null>(null)

// 过滤非法消息元素，防御共享 state 被污染导致渲染崩溃
const visibleMessages = computed(() =>
  (messages.value || []).filter((m: any) => m && (m.role === 'user' || m.role === 'assistant'))
)

/* ---------------------------------------------------------------
   名字 / 欢迎语 / 问题池：全部来自后台「站点设置 → AI 助手」（site_config）。
   之前这三样是写死在模板里的，改一个字要改代码重新构建。
   --------------------------------------------------------------- */
const { data: siteCfg } = useSiteConfig()

const aiName = computed(() => (siteCfg.value?.aiName || '').trim() || '博观 AI')
const aiGreeting = computed(() => (siteCfg.value?.aiGreeting || '').trim()
  || '我可以根据本站的技术文章，帮你学习 Java、AI、Linux、Docker 以及计算机基础知识。你想了解什么？')
/** 后台维护的问题池；为空就退回「按真实文章动态生成」 */
const questionPool = computed<QuestionItem[]>(() => parseQuestionPool(siteCfg.value?.aiQuestions))

/** 一次显示几条（与后台的说明文案保持一致） */
const SHOW_COUNT = 4
/** 「上一批」只存 id：体积小、跨会话可用，池子改了也不会错位 */
const LAST_KEY = 'ai_questions_last'

const quickQuestions = ref<QuestionItem[]>([])
/** 池子为空时用的文章兜底列表（顺序固定，靠 shuffle 制造「换一批」） */
let articleFallback: string[] = []
let suggestLoaded = false

function readLastIds(): string[] {
  if (!process.client) return []
  try {
    const arr = JSON.parse(localStorage.getItem(LAST_KEY) || '[]')
    return Array.isArray(arr) ? arr.filter((x): x is string => typeof x === 'string') : []
  } catch {
    return []
  }
}

function writeLastIds(ids: string[]) {
  if (!process.client) return
  try { localStorage.setItem(LAST_KEY, JSON.stringify(ids)) } catch { /* 隐私模式忽略 */ }
}

/** 从问题池抽一批；返回 false 表示池子为空，需要走文章兜底 */
function pickFromPool(): boolean {
  if (!questionPool.value.length) return false
  const { items, ids } = pickQuestions(questionPool.value, readLastIds(), SHOW_COUNT)
  if (!items.length) return false
  quickQuestions.value = items
  writeLastIds(ids)
  return true
}

/** 文章兜底：随便挑 count 条并打乱顺序（同一批内不重复） */
function pickFromArticles() {
  const list = articleFallback.length ? articleFallback : buildQuickQuestions([], [], SHOW_COUNT)
  quickQuestions.value = shuffle(list).slice(0, SHOW_COUNT).map((t) => ({ id: t, text: t }))
}

/**
 * 首次打开面板时准备问题。池子有内容就用池子（零请求）；
 * 只有池子为空时才去拉文章列表 —— 不填问题池的站点也能有正常体验。
 */
async function loadSuggestions() {
  if (suggestLoaded) return
  suggestLoaded = true
  if (pickFromPool()) return
  try {
    const [artRes, catRes] = await Promise.all([
      get<any>('/article/list?pageNum=1&pageSize=8').catch(() => null),
      get<any>('/category/list').catch(() => null)
    ])
    const records: any[] = artRes?.code === 200 ? (artRes.data?.records || []) : []
    // 按阅读量排序：热门文章更可能被点，也更能代表站内内容
    const hot = [...records].sort((a: any, b: any) => (b.viewCount || 0) - (a.viewCount || 0))
    const cats: any[] = catRes?.code === 200 ? (catRes.data || []) : []
    articleFallback = buildQuickQuestions(hot, cats, SHOW_COUNT)
  } catch {
    articleFallback = buildQuickQuestions()
  }
  pickFromArticles()
}

/** 「换一批」：优先抽没出现过的，不够就用上一批补足（算法在 utils/aiSuggest.pickQuestions） */
function refreshQuestions() {
  if (!pickFromPool()) pickFromArticles()
}

/* ---------------------------------------------------------------
   面板尺寸：可拖拽调整 + 记住上次的大小
   --------------------------------------------------------------- */
const SIZE_KEY = 'ai_panel_size'
const MIN_W = 320
const MIN_H = 380
const size = ref({ w: 400, h: 580 })

/** 交给 CSS 变量，宽度/高度仍由 Tailwind 的响应式类决定（手机上不吃这个值） */
const panelStyle = computed(() => ({
  '--ai-w': `${size.value.w}px`,
  '--ai-h': `${size.value.h}px`
}))

function clampSize(w: number, h: number) {
  const vw = process.client ? window.innerWidth : 1280
  const vh = process.client ? window.innerHeight : 800
  return {
    w: Math.round(Math.min(Math.max(w, MIN_W), Math.max(MIN_W, Math.min(760, vw - 32)))),
    h: Math.round(Math.min(Math.max(h, MIN_H), Math.max(MIN_H, Math.min(900, vh - 48))))
  }
}

function loadPanelSize() {
  if (!process.client) return
  try {
    const raw = JSON.parse(localStorage.getItem(SIZE_KEY) || 'null')
    if (raw && Number(raw.w) > 0 && Number(raw.h) > 0) {
      size.value = clampSize(Number(raw.w), Number(raw.h))
    }
  } catch { /* 脏数据就用默认值 */ }
}

function persistSize() {
  if (!process.client) return
  try { localStorage.setItem(SIZE_KEY, JSON.stringify(size.value)) } catch { /* 忽略 */ }
}

/** 面板贴右下角，所以「拉大」= 鼠标往左上移：宽高用 (起点 - 当前) 计算 */
function startResize(e: PointerEvent) {
  if (!process.client || window.innerWidth < 640) return
  e.preventDefault()
  const sx = e.clientX
  const sy = e.clientY
  const base = { ...size.value }
  const move = (ev: PointerEvent) => {
    size.value = clampSize(base.w + (sx - ev.clientX), base.h + (sy - ev.clientY))
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    persistSize()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

const showHint = ref(false)
function dismissHint() {
  showHint.value = false
  if (process.client) localStorage.setItem('ai-hint-dismissed', '1')
}

onMounted(() => {
  loadPanelSize()
  if (process.client && !localStorage.getItem('ai-hint-dismissed')) {
    setTimeout(() => { showHint.value = true }, 2500)
  }
})

// 外部触发（如文章页）打开时自动滚底
watch(open, (v) => {
  if (v) {
    loadSuggestions()
    setTimeout(() => {
      if (bodyEl.value) bodyEl.value.scrollTop = bodyEl.value.scrollHeight
    }, 50)
    dismissHint()
  }
})

function sendInput() {
  const q = (input.value || '').trim()
  if (!q || streaming.value) return
  input.value = ''
  ask(q)
}
</script>

<style scoped>
/* 面板尺寸走普通 CSS + 自定义属性，不依赖 Tailwind 的任意值语法：
   `sm:w-[var(--ai-w,400px)]` 这类含逗号的 arbitrary value 在不同 Tailwind 版本下
   可能被 JIT 扫不到（生成不出来就是桌面端全宽），CSS 变量写法没有这个不确定性。 */
.ai-panel {
  width: var(--ai-w, 400px);
  height: var(--ai-h, 580px);
}
@media (max-width: 639px) {
  .ai-panel {
    width: calc(100vw - 2rem);
    height: 78vh;
  }
}

/* 文章内容 markdown 字号适配小窗口 */
.ai-msg :deep(.article-content p) { @apply my-1.5 text-[13px] leading-relaxed; }
.ai-msg :deep(.article-content h1) { @apply text-base mt-2 mb-1.5 pb-1; }
.ai-msg :deep(.article-content h2) { @apply text-[15px] mt-2 mb-1.5 pb-1; }
.ai-msg :deep(.article-content h3) { @apply text-sm mt-2 mb-1; }
.ai-msg :deep(.article-content h4) { @apply text-[13px] mt-1.5 mb-1; }
.ai-msg :deep(.article-content ul), .ai-msg :deep(.article-content ol) { @apply my-1 pl-5 space-y-0.5 text-[13px]; }
.ai-msg :deep(.article-content li) { @apply text-[13px]; }
.ai-msg :deep(.article-content blockquote) { @apply my-2 pl-3 py-0.5 text-[13px]; }
.ai-msg :deep(.article-content pre.hljs) { @apply my-2 p-3 text-xs rounded-lg; }
.ai-msg :deep(.article-content table) { @apply my-2 text-xs; }
.ai-msg :deep(.article-content a) { @apply break-all; }
.ai-msg :deep(.article-content img) { @apply my-2; }
.ai-msg :deep(.article-content code) { font-size: 0.75rem; }

.ai-window-enter-active { transition: opacity .2s ease, transform .2s ease; }
.ai-window-leave-active { transition: opacity .15s ease, transform .15s ease; }
.ai-window-enter-from, .ai-window-leave-to { opacity: 0; transform: translateY(12px) scale(.97); }
.ai-pop-enter-active, .ai-pop-leave-active { transition: opacity .3s ease, transform .3s ease; }
.ai-pop-enter-from, .ai-pop-leave-to { opacity: 0; transform: translateY(8px); }
</style>
