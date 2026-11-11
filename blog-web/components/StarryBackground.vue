<template>
  <!-- 纸面底色：单一暖色渐变，不再叠加多色光斑与斜线。
       目标是「一张米色纸」，任何额外色块都会让版面变吵。 -->
  <div class="fixed inset-0 pointer-events-none" style="z-index: 0">
    <div
      class="absolute inset-0 dark:hidden"
      style="background: radial-gradient(120% 80% at 50% -10%, hsl(40 42% 97%) 0%, hsl(38 30% 93%) 55%, hsl(36 24% 90%) 100%)"
    ></div>
    <div
      class="absolute inset-0 hidden dark:block"
      style="background: radial-gradient(120% 80% at 50% -10%, hsl(30 10% 14%) 0%, hsl(28 9% 9%) 60%, hsl(26 9% 7%) 100%)"
    ></div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped></style>
