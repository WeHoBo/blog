<template>
  <div class="max-w-5xl mx-auto px-4 py-6 lg:px-0">
    <ArticleEditor mode="author" />
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'author', layout: 'default' })
useHead({ title: '写文章', meta: [{ name: 'robots', content: 'noindex' }] })
</script>
