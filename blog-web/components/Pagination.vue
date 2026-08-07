<template>
  <div v-if="total > 1" class="flex items-center justify-center gap-1 mt-8 text-sm">
    <button :disabled="current <= 1" @click="$emit('change', 1)"
      class="px-2 py-1.5 rounded border disabled:opacity-30 hover:bg-gray-100 dark:hover:bg-gray-700 transition">首页</button>
    <button :disabled="current <= 1" @click="$emit('change', current - 1)"
      class="px-3 py-1.5 rounded border disabled:opacity-30 hover:bg-gray-100 dark:hover:bg-gray-700 transition">上一页</button>

    <template v-for="p in visiblePages" :key="p">
      <span v-if="p === '...'" class="px-1 text-gray-400">...</span>
      <button v-else @click="$emit('change', p)"
        class="px-3 py-1.5 rounded border transition min-w-[36px]"
        :class="p === current ? 'bg-primary-600 text-white border-primary-600' : 'hover:bg-gray-100 dark:hover:bg-gray-700'">
        {{ p }}
      </button>
    </template>

    <button :disabled="current >= total" @click="$emit('change', current + 1)"
      class="px-3 py-1.5 rounded border disabled:opacity-30 hover:bg-gray-100 dark:hover:bg-gray-700 transition">下一页</button>
    <button :disabled="current >= total" @click="$emit('change', total)"
      class="px-2 py-1.5 rounded border disabled:opacity-30 hover:bg-gray-100 dark:hover:bg-gray-700 transition">末页</button>

    <span class="text-gray-400 ml-2 mr-1">共 {{ total }} 页</span>
    <span class="text-gray-400">跳至</span>
    <input v-model="jumpPage" @keyup.enter="doJump" type="number" :min="1" :max="total"
      class="w-14 px-2 py-1.5 border rounded text-center dark:bg-gray-700 dark:border-gray-600 focus:outline-none focus:ring-1 focus:ring-primary-500" />
    <button @click="doJump" class="px-2 py-1.5 bg-primary-600 text-white rounded text-xs hover:bg-primary-700 transition">GO</button>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ current: number; total: number }>()
const emit = defineEmits<{ change: [page: number] }>()

const jumpPage = ref(props.current)

function doJump() {
  const p = Number(jumpPage.value)
  if (p >= 1 && p <= props.total && p !== props.current) {
    emit('change', p)
  } else {
    jumpPage.value = props.current
  }
}
</script>
