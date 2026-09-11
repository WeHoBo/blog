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
      <span class="absolute right-full mr-3 px-2.5 py-1 rounded-lg bg-gray-900 dark:bg-gray-700 text-white text-xs whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">好啵 AI 助手</span>
    </button>

    <!-- 聊天窗口 -->
    <Teleport to="body">
      <Transition name="ai-window">
        <div v-if="open" class="fixed z-[80] bottom-4 right-4 sm:bottom-6 sm:right-6 w-[calc(100vw-2rem)] sm:w-[400px] h-[78vh] sm:h-[580px] max-h-[85vh] bg-white dark:bg-gray-900 rounded-2xl shadow-2xl border border-gray-100 dark:border-gray-700 flex flex-col overflow-hidden">
          <!-- Header -->
          <div class="px-4 py-3 border-b dark:border-gray-700 flex items-center justify-between bg-gradient-to-r from-primary-50/80 to-transparent dark:from-primary-900/20">
            <div class="flex items-center gap-2.5">
              <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center text-white flex-shrink-0">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="4" y="8" width="16" height="12" rx="3" stroke-width="1.8"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M12 8V4m0 0l-2 2m2-2l2 2"/></svg>
              </div>
              <div>
                <div class="text-sm font-bold text-gray-900 dark:text-gray-100">好啵 AI</div>
                <div class="text-[10px] text-gray-400">博客知识库助手</div>
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
            <!-- 欢迎语 -->
            <div v-if="visibleMessages.length === 0 && !streaming" class="text-center pt-6 pb-2">
              <div class="text-4xl mb-3">🤖</div>
              <p class="text-gray-800 dark:text-gray-100 font-medium text-sm">👋 你好，我是好啵 AI</p>
              <p class="text-gray-400 text-xs mt-2 leading-relaxed px-2">我可以根据本站的技术文章，帮你学习 Java、AI、Linux、Docker 以及计算机基础知识。你想了解什么？</p>
              <div class="flex flex-col gap-2 mt-5 px-2">
                <button v-for="q in quickQuestions" :key="q" @click="ask(q)" :disabled="streaming"
                  class="text-left text-sm px-3.5 py-2.5 rounded-xl border border-gray-100 dark:border-gray-700 bg-gray-50/60 dark:bg-gray-800/60 text-gray-600 dark:text-gray-300 hover:border-primary-300 dark:hover:border-primary-700 hover:text-primary-600 dark:hover:text-primary-400 disabled:opacity-50 transition">
                  {{ q }}
                </button>
              </div>
            </div>

            <!-- 消息 -->
            <template v-for="(msg, i) in visibleMessages" :key="i">
              <div v-if="msg.role === 'user'" class="flex justify-end">
                <div class="max-w-[85%] px-3.5 py-2 rounded-2xl rounded-br-sm bg-primary-600 text-white text-sm whitespace-pre-wrap break-words">{{ msg.content }}</div>
              </div>
              <div v-else class="flex justify-start">
                <div class="max-w-[92%] min-w-0">
                  <div v-if="msg.content || (streaming && i === visibleMessages.length - 1)"
                    class="rounded-2xl rounded-bl-sm border border-gray-100 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3.5 py-2.5 text-sm text-gray-700 dark:text-gray-300 overflow-hidden ai-msg">
                    <MarkdownRenderer v-if="msg.content" :content="msg.content" />
                    <span v-if="streaming && i === visibleMessages.length - 1" class="inline-block w-2 h-4 bg-primary-500 animate-pulse align-middle"></span>
                  </div>
                  <!-- 来源 -->
                  <div v-if="msg.sources && msg.sources.length" class="mt-2">
                    <p class="text-[10px] text-gray-400 mb-1 font-medium">📚 参考本站文章</p>
                    <div class="flex flex-wrap gap-1.5">
                      <NuxtLink v-for="s in msg.sources" :key="s.article_id || s.filename" :to="s.url || '#'"
                        class="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300 border border-primary-100 dark:border-primary-800/40 hover:bg-primary-100 dark:hover:bg-primary-900/40 transition">
                        <span>📄</span>{{ (s.title || s.filename).slice(0, 18) }}{{ (s.title || s.filename).length > 18 ? '…' : '' }} →
                      </NuxtLink>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <!-- Input -->
          <div class="border-t dark:border-gray-700 p-3">
            <div class="flex items-end gap-2">
              <textarea v-model="input" @keydown.enter.exact.prevent="sendInput" rows="1"
                placeholder="输入问题，Enter 发送 / Shift+Enter 换行"
                class="flex-1 resize-none px-3.5 py-2.5 rounded-xl border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 dark:text-gray-100 text-sm focus:border-primary-400 focus:outline-none transition max-h-32 min-h-[42px]"
                :disabled="streaming"></textarea>
              <button @click="sendInput" :disabled="!input.trim() || streaming"
                class="w-10 h-10 rounded-xl bg-primary-600 text-white flex items-center justify-center hover:bg-primary-700 disabled:opacity-40 transition flex-shrink-0">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h14m0 0l-6-6m6 6l-6 6"/></svg>
              </button>
            </div>
            <p class="text-[10px] text-gray-300 dark:text-gray-600 mt-1.5 text-center">AI 基于本站文章回答，可能有误，请核对原文</p>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
// 解构出顶层 ref —— 模板中 ref 自动解包，v-model 才能正确读写字符串
const { open, toggle, close, messages, input, streaming, clear, ask } = useAiChat()

// MarkdownRenderer 含 mermaid 体积大（~2MB），异步加载避免拖慢全局页面
const MarkdownRenderer = defineAsyncComponent(() => import('./MarkdownRenderer.vue'))

const bodyEl = ref<HTMLElement | null>(null)

// 过滤非法消息元素，防御共享 state 被污染导致渲染崩溃
const visibleMessages = computed(() =>
  (messages.value || []).filter((m: any) => m && (m.role === 'user' || m.role === 'assistant'))
)

const quickQuestions = [
  '什么是 RAG？',
  '什么是 Agent？',
  '什么是 Transformer？',
  'Spring Boot 怎么学？'
]

const showHint = ref(false)
function dismissHint() {
  showHint.value = false
  if (process.client) localStorage.setItem('ai-hint-dismissed', '1')
}

onMounted(() => {
  if (process.client && !localStorage.getItem('ai-hint-dismissed')) {
    setTimeout(() => { showHint.value = true }, 2500)
  }
})

// 外部触发（如文章页）打开时自动滚底
watch(open, (v) => {
  if (v) {
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
