<template>
  <footer class="border-t border-gray-300/70 dark:border-gray-700 mt-20">
    <div class="max-w-7xl mx-auto px-4 py-8 text-center">
      <p class="text-xs text-gray-500 dark:text-gray-400 tracking-wide">&copy; {{ year }} 博观 · Built with SpringBoot + Nuxt3</p>
      <p class="text-xs text-gray-500 dark:text-gray-400 mt-2">
        <NuxtLink to="/privacy" class="hover:text-primary-600 dark:hover:text-primary-400 transition">隐私政策</NuxtLink>
        <span class="mx-2 opacity-40">·</span>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" class="hover:text-primary-600 dark:hover:text-primary-400 transition">豫ICP备2026035605号-1</a>
      </p>
    </div>
  </footer>
</template>

<script setup lang="ts">
const year = new Date().getFullYear()
</script>
