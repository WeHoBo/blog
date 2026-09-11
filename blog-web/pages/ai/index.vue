<script setup lang="ts">
// 旧版 /ai 页面已迁移到右下角悬浮助手，访问旧链接跳回首页
const router = useRouter()
if (process.client) {
  router.replace('/')
}
useSeoMeta({ title: '好啵博客' })
</script>

<template>
  <div class="py-40 text-center text-gray-400 text-sm">AI 助手已移至右下角悬浮按钮 🤖</div>
</template>
