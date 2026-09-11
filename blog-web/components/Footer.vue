<template>
  <footer class="border-t dark:border-gray-800 bg-white/50 dark:bg-gray-900 mt-16">
    <div class="max-w-7xl mx-auto px-4 py-6 text-center">
      <p class="text-xs text-gray-400">&copy; {{ year }} 好啵博客 · Built with ❤️ &amp; SpringBoot + Nuxt3</p>
      <p class="text-xs text-gray-400 mt-1">
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" class="hover:text-primary-500 transition">豫ICP备2026035605号-1</a>
      </p>
    </div>
  </footer>
</template>

<script setup lang="ts">
const year = new Date().getFullYear()
</script>
