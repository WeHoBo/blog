<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">分类管理</h1>
      <button @click="openDialog()" class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 transition">添加一级分类</button>
    </div>

    <div class="space-y-3">
      <div v-for="parent in topCategories" :key="parent.id" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
        <!-- 一级分类行 -->
        <div class="flex items-center px-4 py-3 bg-gray-50 dark:bg-gray-700/50 flex-wrap gap-2">
          <div class="flex-1 flex items-center gap-3 flex-wrap min-w-0">
            <span class="text-primary-600 dark:text-primary-400 font-bold text-lg">●</span>
            <span class="font-medium text-gray-900 dark:text-gray-100">{{ parent.name }}</span>
            <span class="text-xs text-gray-400">{{ parent.slug }}</span>
            <span class="text-xs bg-primary-100 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 px-2 py-0.5 rounded-full">{{ parent.articleCount || 0 }} 篇</span>
            <span class="text-xs text-gray-400">排序: {{ parent.sort }}</span>
          </div>
          <div class="flex gap-2 shrink-0">
            <button @click="openChildDialog(parent.id)" class="px-3 py-1 text-xs border border-primary-300 dark:border-primary-600 text-primary-600 dark:text-primary-400 rounded hover:bg-primary-50 dark:hover:bg-primary-900/20 transition">+ 子分类</button>
            <button @click="openDialog(parent)" class="px-3 py-1 text-xs border rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition">编辑</button>
            <button @click="handleDelete(parent.id)" class="px-3 py-1 text-xs border border-red-200 dark:border-red-800 text-red-500 rounded hover:bg-red-50 dark:hover:bg-red-900/20 transition">删除</button>
          </div>
        </div>

        <!-- 二级分类列表 -->
        <div v-if="getChildren(parent.id).length > 0" class="divide-y dark:divide-gray-700">
          <div v-for="child in getChildren(parent.id)" :key="child.id" class="flex items-center px-4 py-2.5 pl-10 flex-wrap gap-2">
            <div class="flex-1 flex items-center gap-3 flex-wrap min-w-0">
              <span class="text-gray-300 dark:text-gray-600">└</span>
              <span class="text-gray-700 dark:text-gray-300">{{ child.name }}</span>
              <span class="text-xs text-gray-400">{{ child.slug }}</span>
              <span class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-500 px-2 py-0.5 rounded-full">{{ child.articleCount || 0 }} 篇</span>
              <span class="text-xs text-gray-400">排序: {{ child.sort }}</span>
            </div>
            <div class="flex gap-2 shrink-0">
              <button @click="openDialog(child)" class="px-3 py-1 text-xs border rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition">编辑</button>
              <button @click="handleDelete(child.id)" class="px-3 py-1 text-xs border border-red-200 dark:border-red-800 text-red-500 rounded hover:bg-red-50 dark:hover:bg-red-900/20 transition">删除</button>
            </div>
          </div>
        </div>
        <div v-else class="px-4 py-3 pl-10 text-xs text-gray-400">暂无子分类</div>
      </div>
    </div>

    <!-- Modal -->
    <div v-if="dialogVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4" @click.self="dialogVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-full max-w-sm">
        <h2 class="text-lg font-bold mb-4">{{ editing ? '编辑分类' : (dialogForm.parentId > 0 ? '添加子分类' : '添加一级分类') }}</h2>
        <div class="space-y-3">
          <div v-if="dialogForm.parentId > 0">
            <label class="text-xs text-gray-400 mb-1 block">所属父分类</label>
            <div class="px-3 py-2 bg-gray-50 dark:bg-gray-700 rounded text-sm text-gray-600 dark:text-gray-300">
              {{ getParentName(dialogForm.parentId) }}
            </div>
          </div>
          <div v-else>
            <label class="text-xs text-gray-400 mb-1 block">父分类</label>
            <select v-model="dialogForm.parentId" aria-label="父分类" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm">
              <option :value="0">无（作为一级分类）</option>
              <option v-for="c in topCategories" :key="c.id" :value="c.id" :disabled="c.id === editId">{{ c.name }}</option>
            </select>
          </div>
          <input v-model="dialogForm.name" placeholder="分类名称" aria-label="分类名称" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <input v-model="dialogForm.slug" placeholder="URL 别名（可选）" aria-label="URL 别名" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <input v-model.number="dialogForm.sort" type="number" placeholder="排序数字" aria-label="排序" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
        </div>
        <div class="flex justify-end gap-2 mt-4">
          <button @click="dialogVisible = false" class="px-4 py-2 border rounded text-sm dark:border-gray-600">取消</button>
          <button @click="handleSave" :disabled="saving" class="px-4 py-2 bg-primary-600 text-white rounded text-sm hover:bg-primary-700 disabled:opacity-50 transition">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get, post, put, del } = useApi()
const { toast, confirmDialog } = useFeedback()
const categories = ref<any[]>([])
const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const dialogForm = reactive({ name: '', slug: '', sort: 0, parentId: 0 as number })

const topCategories = computed(() => categories.value.filter((c: any) => !c.parentId || c.parentId === 0))

function getChildren(parentId: number) {
  return categories.value.filter((c: any) => c.parentId === parentId)
}

function getParentName(parentId: number) {
  const p = categories.value.find((c: any) => c.id === parentId)
  return p ? p.name : ''
}

async function fetchList() {
  const res = await get<any>('/category/list')
  if (res.code === 200) categories.value = res.data || []
}

function openDialog(row?: any) {
  if (row) {
    editing.value = true; editId.value = row.id
    dialogForm.name = row.name; dialogForm.slug = row.slug || ''
    dialogForm.sort = row.sort; dialogForm.parentId = row.parentId || 0
  } else {
    editing.value = false; editId.value = null
    dialogForm.name = ''; dialogForm.slug = ''; dialogForm.sort = 0; dialogForm.parentId = 0
  }
  dialogVisible.value = true
}

function openChildDialog(parentId: number) {
  editing.value = false
  editId.value = null
  dialogForm.name = ''
  dialogForm.slug = ''
  dialogForm.sort = 0
  dialogForm.parentId = parentId
  dialogVisible.value = true
}

async function handleSave() {
  if (saving.value) return
  if (!dialogForm.name.trim()) {
    toast('分类名不能为空', 'error')
    return
  }
  saving.value = true
  try {
    // 同样要判 body.code：后端业务失败是 HTTP 200 + code!=200，不判就会误报「保存成功」
    const res = editing.value
      ? await put<any>(`/category/${editId.value}`, dialogForm)
      : await post<any>('/category', dialogForm)
    if (res && res.code !== 200) {
      toast('保存失败: ' + (res.message || '未知错误'), 'error')
      return
    }
    dialogVisible.value = false
    toast('保存成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('保存失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  const ok = await confirmDialog('确定删除该分类吗？其下【所有子分类会一并级联删除】，这些分类下的文章会变为「未建档文章」。此操作不可撤销。')
  if (!ok) return
  try {
    // 注意：后端业务失败返回的是 HTTP 200 + body.code !== 200（useApi 只对 HTTP 错误抛异常），
    // 所以这里必须自己判 code —— 否则「内置分类不可删除」会被误报成「删除成功」。
    const res = await del<any>(`/category/${id}`)
    if (res && res.code !== 200) {
      toast(res.message || '删除失败', 'error')
      return
    }
    const removed = typeof res?.data === 'number' ? res.data : 1
    toast(removed > 1 ? `删除成功（含 ${removed - 1} 个子分类）` : '删除成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('删除失败: ' + (e.message || '未知错误'), 'error')
  }
}

fetchList()
</script>
