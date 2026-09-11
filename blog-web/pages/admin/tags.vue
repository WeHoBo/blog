<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">标签管理</h1>
      <button @click="openDialog()" class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 transition">添加标签</button>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[520px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr><th class="px-4 py-3 text-left">名称</th><th class="px-4 py-3 text-left">别名</th><th class="px-4 py-3 text-left w-36">时间</th><th class="px-4 py-3 text-left w-28">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="t in tags" :key="t.id" class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-3">{{ t.name }}</td>
              <td class="px-4 py-3 text-gray-400">{{ t.slug }}</td>
              <td class="px-4 py-3 text-gray-400 whitespace-nowrap">{{ t.createTime?.substring(0, 10) }}</td>
              <td class="px-4 py-3 whitespace-nowrap">
                <button @click="openDialog(t)" class="text-primary-500 hover:underline text-xs mr-3">编辑</button>
                <button @click="handleDelete(t.id)" class="text-red-500 hover:underline text-xs">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="dialogVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4" @click.self="dialogVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-full max-w-sm">
        <h2 class="text-lg font-bold mb-4">{{ editing ? '编辑标签' : '添加标签' }}</h2>
        <div class="space-y-3">
          <input v-model="dialogForm.name" placeholder="名称" aria-label="标签名称" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600" />
          <input v-model="dialogForm.slug" placeholder="别名" aria-label="标签别名" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600" />
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
const tags = ref<any[]>([])
const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const dialogForm = reactive({ name: '', slug: '' })

async function fetchList() {
  const res = await get<any>('/tag/list')
  if (res.code === 200) tags.value = res.data || []
}

function openDialog(row?: any) {
  if (row) {
    editing.value = true; editId.value = row.id
    dialogForm.name = row.name; dialogForm.slug = row.slug
  } else {
    editing.value = false; editId.value = null
    dialogForm.name = ''; dialogForm.slug = ''
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (saving.value) return
  if (!dialogForm.name.trim()) {
    toast('标签名不能为空', 'error')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await put(`/tag/${editId.value}`, dialogForm)
    } else {
      await post('/tag', dialogForm)
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
  if (!(await confirmDialog('确定删除这个标签？'))) return
  try {
    await del(`/tag/${id}`)
    toast('删除成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('删除失败: ' + (e.message || '未知错误'), 'error')
  }
}

fetchList()
</script>
