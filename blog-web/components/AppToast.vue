<template>
  <div class="fixed top-4 right-4 z-[100] flex flex-col gap-2 pointer-events-none" aria-live="polite" role="status">
    <TransitionGroup name="toast">
      <div
        v-for="t in toasts"
        :key="t.id"
        class="pointer-events-auto flex items-start gap-2 min-w-[220px] max-w-sm px-4 py-3 rounded-lg shadow-lg text-sm text-white"
        :class="t.type === 'success' ? 'bg-green-600' : t.type === 'error' ? 'bg-red-600' : 'bg-gray-800 dark:bg-gray-700'"
      >
        <span class="flex-1 break-words">{{ t.message }}</span>
        <button
          type="button"
          class="opacity-70 hover:opacity-100 leading-none text-base"
          aria-label="关闭提示"
          @click="dismissToast(t.id)"
        >&times;</button>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
const { toasts, dismissToast } = useFeedback()
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
