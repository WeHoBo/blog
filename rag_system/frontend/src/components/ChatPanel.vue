<script setup>
/**
 * ChatPanel.vue - RAG 聊天问答界面
 * 功能：
 *  1. 选择问答范围（全部文档 / 指定文档）
 *  2. 输入问题 -> 流式展示大模型回答（打字机效果）
 *  3. 「查看历史」加载 SQLite 中的历史问答记录
 *  4. 「新对话」清空当前会话
 */
import { ref, nextTick, onMounted } from 'vue'
import { streamChat, getHistory } from '../api.js'

const props = defineProps({
  documents: { type: Array, default: () => [] }, // 用于问答范围选择
})

// ---------------- 状态 ----------------
const messages = ref([])          // 消息列表 [{ role: 'user'|'assistant', content }]
const question = ref('')          // 输入框内容
const sending = ref(false)        // 是否正在等待回答
const scopeDocId = ref('')        // 问答范围：'' = 全部文档
const chatBody = ref(null)        // 消息区容器（用于自动滚动到底部）
const historyLoading = ref(false)
const hint = ref('')

/** 回车发送，Shift+Enter 换行 */
function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

/** 滚动消息区到底部 */
async function scrollToBottom() {
  await nextTick()
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
}

/** 发送问题：加入消息列表 -> 调用流式接口 -> 逐块渲染回答 */
async function send() {
  const text = question.value.trim()
  if (!text || sending.value) return

  messages.value.push({ role: 'user', content: text })
  question.value = ''
  // 预置一条空的 assistant 消息，流式内容直接往里追加
  const reply = { role: 'assistant', content: '', streaming: true }
  messages.value.push(reply)
  sending.value = true
  scrollToBottom()

  try {
    await streamChat(
      text,
      { docId: scopeDocId.value || null, topK: 5 },
      (delta) => {
        reply.content += delta
        scrollToBottom()
      }
    )
    reply.streaming = false
  } catch (e) {
    reply.content = '出错了：' + e.message
    reply.streaming = false
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

/** 查看历史：加载最近 50 条问答记录渲染为消息列表 */
async function loadHistory() {
  historyLoading.value = true
  try {
    const history = await getHistory(50)
    // 后端按时间倒序返回，这里转成正序展示
    const list = []
    for (const item of history.reverse()) {
      list.push({ role: 'user', content: item.question })
      list.push({ role: 'assistant', content: item.answer })
    }
    messages.value = list
    hint.value = `已加载 ${history.length} 条历史记录`
    scrollToBottom()
  } catch (e) {
    hint.value = e.message
  } finally {
    historyLoading.value = false
  }
}

/** 新对话：清空当前消息列表（历史仍保存在数据库中） */
function newChat() {
  messages.value = []
  hint.value = ''
}

onMounted(() => {
  hint.value = '欢迎使用 RAG 问答，请先在上传文档后开始提问'
})
</script>

<template>
  <!-- 标题栏：范围选择 + 操作按钮 -->
  <div class="panel-header">
    <span>智能问答</span>
    <div class="toolbar">
      <select v-model="scopeDocId" class="scope-select" title="选择问答范围">
        <option value="">全部文档</option>
        <option v-for="doc in props.documents" :key="doc.id" :value="doc.id">
          {{ doc.filename }}
        </option>
      </select>
      <button class="btn" :disabled="historyLoading" @click="loadHistory">
        {{ historyLoading ? '加载中…' : '查看历史' }}
      </button>
      <button class="btn" @click="newChat">新对话</button>
    </div>
  </div>

  <!-- 消息区 -->
  <div ref="chatBody" class="chat-body">
    <div v-for="(msg, i) in messages" :key="i" class="msg-row" :class="msg.role">
      <div class="avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
      <div class="bubble">
        {{ msg.content }}
        <span v-if="msg.streaming" class="cursor">▍</span>
      </div>
    </div>
    <div v-if="!messages.length" class="chat-empty">
      <div class="empty-icon">💬</div>
      <p>上传 PDF 后即可向企业知识库提问</p>
      <p class="empty-sub">回答基于检索到的文档内容生成，不会凭空编造</p>
    </div>
  </div>

  <!-- 底部提示 + 输入区 -->
  <div class="chat-footer">
    <p v-if="hint" class="hint">{{ hint }}</p>
    <div class="input-row">
      <textarea
        v-model="question"
        placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
        rows="1"
        :disabled="sending"
        @keydown="onKeydown"
      ></textarea>
      <button class="btn btn-primary send-btn" :disabled="sending || !question.trim()" @click="send">
        {{ sending ? '回答中…' : '发送' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 工具栏 */
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.scope-select {
  border: 1px solid #d9dce1;
  border-radius: 8px;
  padding: 6px 8px;
  font-size: 13px;
  background: #fff;
  max-width: 160px;
}

/* 消息区 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.msg-row.user {
  flex-direction: row-reverse; /* 用户消息靠右 */
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #fff;
}

.msg-row.assistant .avatar {
  background: linear-gradient(135deg, #4f7cff, #7b5cff);
}

.msg-row.user .avatar {
  background: #00b578;
}

.bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;      /* 保留回答中的换行 */
  word-break: break-word;
}

.msg-row.assistant .bubble {
  background: #f4f5f7;
  border-top-left-radius: 4px;
}

.msg-row.user .bubble {
  background: #e8f4ff;
  border-top-right-radius: 4px;
}

/* 流式输出光标 */
.cursor {
  color: #4f7cff;
  animation: blink 0.8s infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* 空状态 */
.chat-empty {
  margin: auto;
  text-align: center;
  color: #8a919f;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 10px;
}

.empty-sub {
  font-size: 12px;
  margin-top: 6px;
  color: #b0b4bb;
}

/* 底部输入区 */
.chat-footer {
  border-top: 1px solid #f0f1f3;
  padding: 12px 16px;
  flex-shrink: 0;
}

.hint {
  font-size: 12px;
  color: #8a919f;
  margin-bottom: 8px;
}

.input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

textarea {
  flex: 1;
  resize: none;
  border: 1px solid #d9dce1;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  min-height: 42px;
  max-height: 120px;
}

textarea:focus {
  border-color: #4f7cff;
}

.send-btn {
  padding: 9px 22px;
  font-size: 14px;
}
</style>
