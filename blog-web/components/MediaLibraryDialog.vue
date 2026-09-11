<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center p-4">
    <div class="absolute inset-0 bg-black/50" @click="close"></div>
    <div class="relative bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-3xl max-h-[85vh] flex flex-col">
      <div class="flex items-center justify-between px-5 py-4 border-b dark:border-gray-700">
        <h2 class="text-base font-medium">图片素材库</h2>
        <button @click="close" class="text-gray-400 hover:text-gray-600 text-xl leading-none" aria-label="关闭">×</button>
      </div>

      <div class="px-5 py-3 border-b dark:border-gray-700 flex gap-2 flex-wrap">
        <input
          v-model="keyword"
          placeholder="按文件名搜索"
          class="px-3 py-1.5 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm flex-1 min-w-[120px]"
          @keyup.enter="reload"
        />
        <select v-model="extFilter" class="px-2 py-1.5 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" @change="reload">
          <option value="">全部类型</option>
          <option value="png">png</option>
          <option value="jpg">jpg</option>
          <option value="jpeg">jpeg</option>
          <option value="webp">webp</option>
          <option value="gif">gif</option>
        </select>
        <button @click="reload" class="px-3 py-1.5 border rounded text-sm hover:border-primary-500 hover:text-primary-500">刷新</button>
      </div>

      <div class="flex-1 overflow-y-auto p-4">
        <div v-if="loading" class="text-center py-10 text-gray-400 text-sm">加载中...</div>
        <div v-else-if="files.length === 0" class="text-center py-10 text-gray-400 text-sm">还没有上传过图片</div>
        <div v-else class="grid grid-cols-3 sm:grid-cols-4 gap-3">
          <button
            v-for="f in files"
            :key="f.id"
            class="group relative aspect-square rounded-lg overflow-hidden border dark:border-gray-700 hover:ring-2 hover:ring-primary-400 transition"
            :title="f.originalName"
            @click="pick(f)"
          >
            <img :src="f.url" :alt="f.originalName || '图片'" class="w-full h-full object-cover" loading="lazy" />
            <span class="absolute inset-x-0 bottom-0 bg-black/60 text-white text-[11px] px-1 py-0.5 truncate opacity-0 group-hover:opacity-100 transition">
              {{ f.ext }} · {{ formatSize(f.fileSize) }}
            </span>
          </button>
        </div>
      </div>

      <div v-if="totalPages > 1" class="px-5 py-3 border-t dark:border-gray-700 flex items-center justify-center gap-3 text-sm">
        <button @click="goto(page - 1)" :disabled="page <= 1" class="px-3 py-1 border rounded disabled:opacity-40">上一页</button>
        <span class="text-gray-500">{{ page }} / {{ totalPages }}</span>
        <button @click="goto(page + 1)" :disabled="page >= totalPages" class="px-3 py-1 border rounded disabled:opacity-40">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'pick', url: string): void }>()

const { get } = useApi()
const page = ref(1)
const keyword = ref('')
const extFilter = ref('')
const files = ref<any[]>([])
const totalPages = ref(0)
const loading = ref(false)

function close() {
  emit('close')
}

function pick(f: any) {
  emit('pick', f.url)
  close()
}

function formatSize(bytes: number) {
  if (!bytes) return '0B'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(2) + 'MB'
}

async function reload() {
  loading.value = true
  try {
    const params: any = { pageNum: page.value, pageSize: 24 }
    if (keyword.value) params.keyword = keyword.value
    if (extFilter.value) params.ext = extFilter.value
    const qs = new URLSearchParams(params).toString()
    const res = await get<any>(`/upload/files?${qs}`)
    if (res.code === 200 && res.data) {
      files.value = res.data.records || []
      totalPages.value = res.data.pages || 0
    }
  } finally {
    loading.value = false
  }
}

function goto(p: number) {
  page.value = p
  reload()
}

watch(() => props.open, (open) => {
  if (open) {
    page.value = 1
    reload()
  }
})
</script>
