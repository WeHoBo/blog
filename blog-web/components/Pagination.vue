<template>
  <div v-if="total > 1" class="flex justify-center gap-2 mt-8">
    <button
      :disabled="current <= 1"
      @click="$emit('change', current - 1)"
      class="px-3 py-1.5 rounded text-sm border disabled:opacity-30 disabled:cursor-not-allowed hover:bg-gray-100 dark:hover:bg-gray-700 transition"
    >
      上一页
    </button>
    <button
      v-for="p in total"
      :key="p"
      @click="$emit('change', p)"
      class="px-3 py-1.5 rounded text-sm border transition"
      :class="p === current ? 'bg-primary-500 text-white border-primary-500' : 'hover:bg-gray-100 dark:hover:bg-gray-700'"
    >
      {{ p }}
    </button>
    <button
      :disabled="current >= total"
      @click="$emit('change', current + 1)"
      class="px-3 py-1.5 rounded text-sm border disabled:opacity-30 disabled:cursor-not-allowed hover:bg-gray-100 dark:hover:bg-gray-700 transition"
    >
      下一页
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ current: number; total: number }>()
defineEmits<{ change: [page: number] }>()
</script>
