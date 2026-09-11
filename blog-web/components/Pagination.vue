<template>
  <div v-if="total > 1" class="flex items-center justify-center gap-1 mt-8 text-sm flex-wrap">
    <button :disabled="current <= 1" @click="$emit('change', 1)" aria-label="第一页"
      class="px-2 py-1.5 rounded border disabled:opacity-30 hover:bg-gray-100 dark:hover:bg-gray-700 dark:border-gray-600 transition">首页</button>
    <button :disabled="current <= 1" @click="$emit('change', current - 1)" aria-label="上一页"
      class="px-3 py-1.5 rounded border disabled:opacity-30 hover:bg-gray-100 dark:hover:bg-gray-700 dark:border-gray-600 transition">上一页</button>

    <template v-for="p in visiblePages" :key="p">
      <span v-if="p === '...'" class="px-1 text-gray-400">...</span>
      <button v-else @click="$emit('change', p)" :aria-label="`第 ${p} 页`" :aria-current="p === current ? 'page' : undefined"
        class="px-3 py-1.5 rounded border transition min-w-[36px] dark:border-gray-600"
        :class="p === current ? 'bg-primary-600 text-white border-primary-600' : 'hover:bg-gray-100 dark:hover:bg-gray-700'">
        {{ p }}
      </button>
    </template>

    <button :disabled="current >= total" @click="$emit('change', current + 1)" aria-label="下一页"
      class="px-3 py-1.5 rounded border disabled:opacity-30 hover:bg-gray-100 dark:hover:bg-gray-700 dark:border-gray-600 transition">下一页</button>
    <button :disabled="current >= total" @click="$emit('change', total)" aria-label="最后一页"
      class="px-2 py-1.5 rounded border disabled:opacity-30 hover:bg-gray-100 dark:hover:bg-gray-700 dark:border-gray-600 transition">末页</button>

    <span class="text-gray-400 ml-2 mr-1">共 {{ total }} 页</span>
    <span class="text-gray-400">跳至</span>
    <input v-model="jumpPage" @keyup.enter="doJump" type="number" :min="1" :max="total" aria-label="跳转到页码"
      class="w-14 px-2 py-1.5 border rounded text-center dark:bg-gray-700 dark:border-gray-600 focus:outline-none focus:ring-1 focus:ring-primary-500" />
    <button @click="doJump" class="px-2 py-1.5 bg-primary-600 text-white rounded text-xs hover:bg-primary-700 transition" aria-label="跳转">GO</button>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ current: number; total: number }>()
const emit = defineEmits<{ change: [page: number] }>()

const jumpPage = ref(props.current)

watch(() => props.current, (v) => { jumpPage.value = v })

const visiblePages = computed(() => {
  const total = props.total
  const current = props.current
  const pages: (number | string)[] = []
  if (total <= 7) {
    for (let i = 1; i <= total; i++) pages.push(i)
    return pages
  }
  pages.push(1)
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  if (start > 2) pages.push('...')
  for (let i = start; i <= end; i++) pages.push(i)
  if (end < total - 1) pages.push('...')
  pages.push(total)
  return pages
})

function doJump() {
  const p = Number(jumpPage.value)
  if (p >= 1 && p <= props.total && p !== props.current) {
    emit('change', p)
  } else {
    jumpPage.value = props.current
  }
}
</script>
