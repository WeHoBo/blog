<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">友链管理</h1>
      <button @click="openDialog()" class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 transition">添加友链</button>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="px-4 py-3 text-left">名称</th>
              <th class="px-4 py-3 text-left">链接</th>
              <th class="px-4 py-3 text-left">描述</th>
              <th class="px-4 py-3 text-left w-16">排序</th>
              <th class="px-4 py-3 text-left w-24">状态</th>
              <th class="px-4 py-3 text-left w-28">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in links" :key="l.id" class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-3 font-medium">{{ l.name }}</td>
              <td class="px-4 py-3 text-gray-400 text-xs truncate max-w-xs">{{ l.url }}</td>
              <td class="px-4 py-3 text-gray-400 text-xs">{{ l.description }}</td>
              <td class="px-4 py-3">{{ l.sort }}</td>
              <td class="px-4 py-3">
                <span :class="l.status === 1 ? 'text-green-500' : 'text-gray-400'" class="text-xs">{{ l.status === 1 ? '显示' : '隐藏' }}</span>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <button @click="openDialog(l)" class="text-primary-500 hover:underline text-xs mr-3">编辑</button>
                <button @click="handleDelete(l.id)" class="text-red-500 hover:underline text-xs">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="dialogVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4" @click.self="dialogVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-full max-w-sm">
        <h2 class="text-lg font-bold mb-4">{{ editing ? '编辑友链' : '添加友链' }}</h2>
        <div class="space-y-3">
          <input v-model="dialogForm.name" placeholder="名称" aria-label="友链名称" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <input v-model="dialogForm.url" placeholder="链接URL" aria-label="友链链接" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <input v-model="dialogForm.avatar" placeholder="头像URL（可选）" aria-label="头像地址" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <input v-model="dialogForm.description" placeholder="描述（可选）" aria-label="友链描述" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <div class="flex gap-3">
            <div class="flex-1">
              <label class="text-xs text-gray-400">排序</label>
              <input v-model.number="dialogForm.sort" type="number" aria-label="排序" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
            </div>
            <div class="flex-1">
              <label class="text-xs text-gray-400">状态</label>
              <select v-model.number="dialogForm.status" aria-label="显示状态" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm">
                <option :value="1">显示</option>
                <option :value="0">隐藏</option>
              </select>
            </div>
          </div>
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
const links = ref<any[]>([])
const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const dialogForm = reactive({ name: '', url: '', avatar: '', description: '', sort: 0, status: 1 })

async function fetchList() {
  const res = await get<any>('/friend-link/list')
  if (res.code === 200) links.value = res.data || []
}

function openDialog(row?: any) {
  if (row) {
    editing.value = true; editId.value = row.id
    dialogForm.name = row.name; dialogForm.url = row.url; dialogForm.avatar = row.avatar || ''
    dialogForm.description = row.description || ''; dialogForm.sort = row.sort; dialogForm.status = row.status
  } else {
    editing.value = false; editId.value = null
    dialogForm.name = ''; dialogForm.url = ''; dialogForm.avatar = ''; dialogForm.description = ''; dialogForm.sort = 0; dialogForm.status = 1
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (saving.value) return
  if (!dialogForm.name.trim() || !dialogForm.url.trim()) {
    toast('名称与链接不能为空', 'error')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await put(`/friend-link/${editId.value}`, dialogForm)
    } else {
      await post('/friend-link', dialogForm)
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
  if (!(await confirmDialog('确定删除这条友链？'))) return
  try {
    await del(`/friend-link/${id}`)
    toast('删除成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('删除失败: ' + (e.message || '未知错误'), 'error')
  }
}

fetchList()
</script>
