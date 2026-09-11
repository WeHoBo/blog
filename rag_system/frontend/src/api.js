/**
 * api.js - 后端接口封装
 * 开发环境下通过 Vite 代理：/api/xxx -> http://127.0.0.1:8000/xxx
 */

const BASE = '/api'

/** 获取已上传文档列表 */
export async function getDocuments() {
  const resp = await fetch(`${BASE}/documents`)
  if (!resp.ok) throw new Error('获取文档列表失败')
  return (await resp.json()).documents
}

/**
 * 上传 PDF（使用 XMLHttpRequest 以获得上传进度事件）
 * @param {File} file 选择的 PDF 文件
 * @param {(percent: number) => void} onProgress 上传进度回调（0-100）
 */
export function uploadPdf(file, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${BASE}/upload`)
    // 上传进度事件
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100))
    }
    xhr.onload = () => {
      let data = {}
      try {
        data = JSON.parse(xhr.responseText)
      } catch (_) { /* 忽略解析失败 */ }
      if (xhr.status === 200) resolve(data)
      else reject(new Error(data.detail || '上传失败'))
    }
    xhr.onerror = () => reject(new Error('网络错误，上传失败'))
    const formData = new FormData()
    formData.append('file', file)
    xhr.send(formData)
  })
}

/**
 * RAG 问答（流式）：边接收大模型输出边通过 onDelta 回调追加渲染
 * @param {string} question 用户问题
 * @param {{docId?: string, topK?: number}} options 检索选项
 * @param {(delta: string) => void} onDelta 增量文本回调
 */
export async function streamChat(question, { docId = null, topK = 5 } = {}, onDelta) {
  const resp = await fetch(`${BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, doc_id: docId, top_k: topK }),
  })
  if (!resp.ok) throw new Error('问答请求失败')
  // 通过 ReadableStream 逐块读取流式响应
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    onDelta(decoder.decode(value, { stream: true }))
  }
}

/** 获取聊天历史 */
export async function getHistory(limit = 50) {
  const resp = await fetch(`${BASE}/history?limit=${limit}`)
  if (!resp.ok) throw new Error('获取历史记录失败')
  return (await resp.json()).history
}
