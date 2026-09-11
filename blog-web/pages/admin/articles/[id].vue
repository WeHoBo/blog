<template>
  <ArticleEditor :article-id="String($route.params.id)" />
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })
</script>
