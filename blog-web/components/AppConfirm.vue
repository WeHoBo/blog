<template>
  <div
    v-if="confirmState.visible"
    class="fixed inset-0 z-[110] bg-black/50 flex items-center justify-center px-4"
    role="dialog"
    aria-modal="true"
    aria-labelledby="app-confirm-title"
    @click.self="resolveConfirm(false)"
    @keydown.esc="resolveConfirm(false)"
  >
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-sm p-6">
      <h2 id="app-confirm-title" class="text-lg font-bold text-gray-900 dark:text-gray-100 mb-2">
        {{ confirmState.title }}
      </h2>
      <p class="text-sm text-gray-600 dark:text-gray-300 whitespace-pre-line break-words">
        {{ confirmState.message }}
      </p>
      <div class="flex justify-end gap-2 mt-6">
        <button
          type="button"
          class="px-4 py-2 border rounded-lg text-sm text-gray-700 dark:text-gray-200 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700 transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-500"
          @click="resolveConfirm(false)"
        >取消</button>
        <button
          type="button"
          class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-500"
          @click="resolveConfirm(true)"
        >确定</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const { confirmState, resolveConfirm } = useFeedback()
</script>
