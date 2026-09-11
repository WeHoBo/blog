<script setup>
/**
 * UploadPanel.vue - PDF 上传与文档列表
 * 功能：拖拽/点击选择 PDF -> 上传并显示进度条 -> 成功后刷新文档列表
 * 注意：文档列表数据由 App.vue 统一持有，上传完成后通过事件通知父组件更新
 */
import { ref, onMounted } from 'vue'
import { getDocuments, uploadPdf } from '../api.js'

const props = defineProps({
  documents: { type: Array, default: () => [] }, // 父组件传入的文档列表
})
const emit = defineEmits(['documents-updated'])

const uploading = ref(false)        // 是否正在上传
const progress = ref(0)             // 上传进度 0-100
const dragOver = ref(false)         // 是否拖拽悬停在上传区
const fileInput = ref(null)         // 隐藏的文件选择框
const message = ref('')             // 操作提示信息

/** 页面加载时拉取一次文档列表 */
onMounted(refreshDocuments)

/** 刷新文档列表 */
async function refreshDocuments() {
  try {
    emit('documents-updated', await getDocuments())
  } catch (e) {
    message.value = e.message
  }
}

/** 选择文件后触发上传 */
function handleFileSelected(event) {
  const file = event.target.files[0]
  event.target.value = '' // 允许重复选择同一文件
  if (file) doUpload(file)
}

/** 拖拽放置文件 */
function handleDrop(event) {
  dragOver.value = false
  const file = event.dataTransfer.files[0]
  if (file) doUpload(file)
}

/** 执行上传：带进度条，完成后刷新文档列表 */
async function doUpload(file) {
  // 前端先校验文件类型
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    message.value = '仅支持上传 PDF 文件'
    return
  }
  uploading.value = true
  progress.value = 0
  message.value = ''
  try {
    const result = await uploadPdf(file, (p) => (progress.value = p))
    message.value = `《${result.document.filename}》上传成功，共切分 ${result.document.chunk_count} 个文本块`
    await refreshDocuments() // 上传成功后刷新文档列表
  } catch (e) {
    message.value = e.message
  } finally {
    uploading.value = false
  }
}

/** 格式化上传时间显示 */
function formatTime(iso) {
  return iso ? iso.replace('T', ' ').slice(0, 19) : ''
}
</script>

<template>
  <!-- 标题栏 -->
  <div class="panel-header">知识库文档管理</div>

  <!-- 上传区域：支持点击选择与拖拽 -->
  <div class="upload-area">
    <div
      class="drop-zone"
      :class="{ 'drag-over': dragOver }"
      @click="fileInput && fileInput.click()"
      @dragover.prevent="dragOver = true"
      @dragleave="dragOver = false"
      @drop.prevent="handleDrop"
    >
      <div class="drop-icon">PDF</div>
      <div class="drop-text">点击选择或拖拽 PDF 文件到此处</div>
      <div class="drop-sub">支持 PyMuPDF 解析提取文本，自动切块向量化</div>
      <input
        ref="fileInput"
        type="file"
        accept=".pdf"
        style="display: none"
        @change="handleFileSelected"
      />
    </div>

    <!-- 上传进度条 -->
    <div v-if="uploading" class="progress-wrap">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progress + '%' }"></div>
      </div>
      <span class="progress-text">上传中 {{ progress }}%</span>
    </div>

    <!-- 提示信息 -->
    <p v-if="message" class="message" :class="{ error: message.includes('失败') || message.includes('仅支持') }">
      {{ message }}
    </p>

    <!-- 文档列表 -->
    <div class="doc-list">
      <div v-for="doc in props.documents" :key="doc.id" class="doc-item">
        <div class="doc-icon">📄</div>
        <div class="doc-info">
          <div class="doc-name" :title="doc.filename">{{ doc.filename }}</div>
          <div class="doc-meta">{{ doc.chunk_count }} 个文本块 · {{ formatTime(doc.created_at) }}</div>
        </div>
      </div>
      <div v-if="!props.documents.length" class="doc-empty">暂无文档，请先上传 PDF</div>
    </div>
  </div>
</template>

<style scoped>
/* 上传区域样式 */
.upload-area {
  padding: 16px;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.drop-zone {
  border: 2px dashed #c9cdd4;
  border-radius: 12px;
  padding: 26px 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafbfc;
}

.drop-zone:hover,
.drag-over {
  border-color: #4f7cff;
  background: #f3f6ff;
}

.drop-icon {
  width: 52px;
  height: 52px;
  margin: 0 auto 10px;
  border-radius: 12px;
  background: #eef1ff;
  color: #4f7cff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbe2ff;
}

.drop-text {
  font-size: 14px;
  color: #333;
}

.drop-sub {
  font-size: 12px;
  color: #8a919f;
  margin-top: 6px;
}

/* 进度条 */
.progress-wrap {
  margin-top: 14px;
}

.progress-bar {
  height: 8px;
  background: #eef0f3;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4f7cff, #7b5cff);
  border-radius: 4px;
  transition: width 0.2s;
}

.progress-text {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
  display: block;
}

/* 提示信息 */
.message {
  margin-top: 12px;
  font-size: 13px;
  color: #16a34a;
  word-break: break-all;
}

.message.error {
  color: #e5484d;
}

/* 文档列表 */
.doc-list {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #eef0f3;
  border-radius: 10px;
  background: #fafbfc;
}

.doc-icon {
  font-size: 20px;
}

.doc-info {
  min-width: 0;
}

.doc-name {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-meta {
  font-size: 12px;
  color: #8a919f;
  margin-top: 2px;
}

.doc-empty {
  text-align: center;
  color: #b0b4bb;
  font-size: 13px;
  padding: 18px 0;
}
</style>
