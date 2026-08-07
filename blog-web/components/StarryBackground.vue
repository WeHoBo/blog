<template>
  <div class="fixed inset-0 pointer-events-none" style="z-index: 0">
    <!-- Light mode background -->
    <div class="absolute inset-0 dark:hidden" style="background: linear-gradient(180deg, #f4f7fc 0%, #e9eff8 100%)">
      <div class="absolute bottom-0 right-0 w-96 h-96 rounded-full opacity-10" style="background: radial-gradient(circle, #60a5fa 0%, transparent 70%)"></div>
      <div class="absolute top-20 left-10 w-64 h-64 rounded-full opacity-8" style="background: radial-gradient(circle, #818cf8 0%, transparent 70%)"></div>
      <!-- Corner glow lines -->
      <svg class="absolute bottom-0 right-0 w-full h-64 opacity-[0.03]" viewBox="0 0 800 200">
        <line x1="0" y1="180" x2="800" y2="0" stroke="#3b82f6" stroke-width="0.5" />
        <line x1="200" y1="200" x2="800" y2="20" stroke="#6366f1" stroke-width="0.3" />
        <line x1="400" y1="200" x2="800" y2="60" stroke="#8b5cf6" stroke-width="0.2" />
      </svg>
    </div>
    <!-- Dark mode background -->
    <div class="absolute inset-0 hidden dark:block" style="background: linear-gradient(180deg, #0f1722 0%, #162133 100%)">
      <div class="absolute bottom-0 right-0 w-96 h-96 rounded-full opacity-[0.06]" style="background: radial-gradient(circle, #3b82f6 0%, transparent 70%)"></div>
      <div class="absolute top-10 left-20 w-80 h-80 rounded-full opacity-[0.04]" style="background: radial-gradient(circle, #6366f1 0%, transparent 70%)"></div>
      <!-- Corner glow lines -->
      <svg class="absolute bottom-0 right-0 w-full h-64 opacity-[0.04]" viewBox="0 0 800 200">
        <line x1="0" y1="180" x2="800" y2="0" stroke="#3b82f6" stroke-width="0.5" />
        <line x1="200" y1="200" x2="800" y2="20" stroke="#6366f1" stroke-width="0.3" />
        <line x1="400" y1="200" x2="800" y2="60" stroke="#8b5cf6" stroke-width="0.2" />
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped></style>
