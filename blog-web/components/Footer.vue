<template>
  <footer class="border-t dark:border-gray-800 bg-white dark:bg-gray-900 mt-16">
    <div class="container mx-auto px-4 max-w-5xl py-8 text-center">
      <p class="text-sm text-gray-400">
        &copy; {{ year }} 好啵博客 ·
        <span class="text-gray-300 dark:text-gray-600">Built with SpringBoot & Nuxt3</span>
      </p>
    </div>
  </footer>
</template>

<script setup lang="ts">
const year = new Date().getFullYear()
</script>
