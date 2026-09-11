<script setup>
/**
 * App.vue - 根组件：顶栏 + 左右两栏布局
 * 左栏：PDF 上传与文档列表（UploadPanel）
 * 右栏：RAG 聊天对话（ChatPanel）
 */
import { ref } from 'vue'
import UploadPanel from './components/UploadPanel.vue'
import ChatPanel from './components/ChatPanel.vue'

// 文档列表由左侧管理，右侧选择问答范围时共享
const documents = ref([])

/** 文档列表更新回调（上传成功后由左侧触发） */
function handleDocumentsUpdated(list) {
  documents.value = list
}
</script>

<template>
  <!-- 顶栏 -->
  <header class="topbar">
    <div class="logo">R</div>
    <h1>企业级RAG智能文档问答系统</h1>
    <span class="sub">DeepSeek · ChromaDB · FastAPI · Vue3</span>
  </header>

  <!-- 主体两栏 -->
  <div class="main">
    <section class="panel panel-left">
      <UploadPanel :documents="documents" @documents-updated="handleDocumentsUpdated" />
    </section>
    <section class="panel panel-right">
      <ChatPanel :documents="documents" />
    </section>
  </div>
</template>
