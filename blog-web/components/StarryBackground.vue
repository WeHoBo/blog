<template>
  <div class="fixed inset-0 pointer-events-none" style="z-index: 0">
    <!-- Light mode background：跟随主题色（默认 hue 217 时 ≈ 原来的浅蓝灰） -->
    <div class="absolute inset-0 dark:hidden" style="background: linear-gradient(180deg, hsl(var(--ph) 45% 98%) 0%, hsl(var(--ph) 38% 95%) 100%)">
      <div class="absolute bottom-0 right-0 w-96 h-96 rounded-full opacity-10" style="background: radial-gradient(circle, hsl(var(--ph) 91% 60%) 0%, transparent 70%)"></div>
      <div class="absolute top-20 left-10 w-64 h-64 rounded-full opacity-10" style="background: radial-gradient(circle, hsl(calc(var(--ph) + 25) 90% 68%) 0%, transparent 70%)"></div>
      <!-- Corner glow lines -->
      <svg class="absolute bottom-0 right-0 w-full h-64 opacity-[0.03]" viewBox="0 0 800 200">
        <line x1="0" y1="180" x2="800" y2="0" stroke="hsl(var(--ph) 91% 60%)" stroke-width="0.5" />
        <line x1="200" y1="200" x2="800" y2="20" stroke="hsl(calc(var(--ph) + 20) 85% 62%)" stroke-width="0.3" />
        <line x1="400" y1="200" x2="800" y2="60" stroke="hsl(calc(var(--ph) + 40) 85% 65%)" stroke-width="0.2" />
      </svg>
    </div>
    <!-- Dark mode background -->
    <div class="absolute inset-0 hidden dark:block" style="background: linear-gradient(180deg, hsl(var(--ph) 30% 9%) 0%, hsl(var(--ph) 26% 13%) 100%)">
      <div class="absolute bottom-0 right-0 w-96 h-96 rounded-full opacity-[0.06]" style="background: radial-gradient(circle, hsl(var(--ph) 80% 55%) 0%, transparent 70%)"></div>
      <div class="absolute top-10 left-20 w-80 h-80 rounded-full opacity-[0.04]" style="background: radial-gradient(circle, hsl(calc(var(--ph) + 25) 80% 62%) 0%, transparent 70%)"></div>
      <!-- Corner glow lines -->
      <svg class="absolute bottom-0 right-0 w-full h-64 opacity-[0.04]" viewBox="0 0 800 200">
        <line x1="0" y1="180" x2="800" y2="0" stroke="hsl(var(--ph) 80% 55%)" stroke-width="0.5" />
        <line x1="200" y1="200" x2="800" y2="20" stroke="hsl(calc(var(--ph) + 20) 80% 60%)" stroke-width="0.3" />
        <line x1="400" y1="200" x2="800" y2="60" stroke="hsl(calc(var(--ph) + 40) 80% 63%)" stroke-width="0.2" />
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped></style>
