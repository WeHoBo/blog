<template>
  <footer class="border-t dark:border-gray-800 bg-pink-50/50 dark:bg-gray-900 mt-16">
    <div class="max-w-7xl mx-auto px-4 py-6 text-center">
      <p class="text-xs text-gray-400">&copy; {{ year }} 好啵博客 · Built with ❤️ &amp; SpringBoot + Nuxt3</p>
    </div>
  </footer>
</template>

<script setup lang="ts">
const year = new Date().getFullYear()
</script>
