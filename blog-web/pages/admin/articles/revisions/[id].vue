<template>
  <div>
    <div class="flex justify-between items-center mb-6 gap-3 flex-wrap">
      <h1 class="text-2xl font-bold">版本历史</h1>
      <NuxtLink :to="`/admin/articles/${articleId}`" class="text-sm text-gray-500 hover:text-primary-500">← 返回编辑</NuxtLink>
    </div>

    <div v-if="loading" class="text-center py-10 text-gray-400">加载中...</div>
    <div v-else-if="revisions.length === 0" class="text-center py-16 text-gray-400">
      <p class="mb-2 text-lg">还没有历史版本</p>
      <p class="text-sm">每次保存都会自动留一份快照，最多保留 30 份</p>
    </div>
    <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-4">
      <!-- 版本列表 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden lg:col-span-1">
        <div class="px-4 py-3 border-b dark:border-gray-700 text-sm text-gray-500">共 {{ revisions.length }} 个版本</div>
        <div class="max-h-[70vh] overflow-y-auto divide-y dark:divide-gray-700">
          <button
            v-for="r in revisions"
            :key="r.id"
            @click="select(r)"
            class="w-full text-left px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition"
            :class="{ 'bg-primary-50 dark:bg-primary-900/20': selected?.id === r.id }"
          >
            <div class="text-sm font-medium truncate">{{ r.title || '(无标题)' }}</div>
            <div class="text-xs text-gray-400 mt-0.5">
              {{ formatTime(r.createTime) }} · {{ r.wordCount || 0 }} 字
              <span v-if="r.status === 1" class="text-green-500">已发布</span>
              <span v-else class="text-amber-500">草稿</span>
            </div>
          </button>
        </div>
      </div>

      <!-- 版本详情 -->
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 lg:col-span-2">
        <div v-if="!selected" class="text-center py-16 text-gray-400 text-sm">选择左侧版本查看内容</div>
        <div v-else>
          <div class="flex items-center justify-between mb-3">
            <div class="text-sm text-gray-500">{{ formatTime(selected.createTime) }}</div>
            <button
              @click="restore(selected)"
              :disabled="restoring"
              class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 disabled:opacity-50 transition"
            >
              {{ restoring ? '回滚中…' : '回滚到此版本' }}
            </button>
          </div>
          <div class="border rounded-lg dark:border-gray-700 p-4 bg-gray-50 dark:bg-gray-700/40 max-h-[60vh] overflow-y-auto">
            <MarkdownRenderer v-if="selected.contentMd" :content="selected.contentMd" />
            <div v-else class="text-gray-400 text-sm">该版本正文为空</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const route = useRoute()
const articleId = route.params.id
const { get, post } = useApi()
const { toast, confirmDialog } = useFeedback()

const revisions = ref<any[]>([])
const selected = ref<any>(null)
const loading = ref(false)
const restoring = ref(false)

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

async function fetchRevisions() {
  loading.value = true
  try {
    const res = await get<any>(`/article/${articleId}/revisions`)
    if (res.code === 200) {
      revisions.value = res.data || []
    }
  } finally {
    loading.value = false
  }
}

async function select(r: any) {
  // 列表里只有元信息，正文按需拉取
  if (r.contentMd === undefined) {
    try {
      const res = await get<any>(`/article/${articleId}/revisions/${r.id}`)
      if (res.code === 200 && res.data) {
        r.contentMd = res.data.contentMd || ''
        r.summary = res.data.summary
      }
    } catch (e: any) {
      toast('加载版本失败: ' + (e.message || '未知错误'), 'error')
    }
  }
  selected.value = r
}

async function restore(r: any) {
  if (!(await confirmDialog('回滚会覆盖当前内容，但会先为当前版本留一份快照。确定回滚？'))) return
  restoring.value = true
  try {
    await post(`/article/${articleId}/revisions/${r.id}/restore`)
    toast('已回滚到该版本', 'success')
    await fetchRevisions()
  } catch (e: any) {
    toast('回滚失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    restoring.value = false
  }
}

fetchRevisions()
</script>
