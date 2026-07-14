<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">分类管理</h1>
      <button @click="openDialog()" class="px-4 py-2 bg-primary-500 text-white rounded-lg text-sm hover:bg-primary-600">添加分类</button>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
          <tr><th class="px-4 py-3 text-left">名称</th><th class="px-4 py-3 text-left">别名</th><th class="px-4 py-3 text-left w-20">文章数</th><th class="px-4 py-3 text-left w-16">排序</th><th class="px-4 py-3 text-left w-28">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in categories" :key="c.id" class="border-t dark:border-gray-700">
            <td class="px-4 py-3">{{ c.name }}</td>
            <td class="px-4 py-3 text-gray-400">{{ c.slug }}</td>
            <td class="px-4 py-3">{{ c.articleCount }}</td>
            <td class="px-4 py-3">{{ c.sort }}</td>
            <td class="px-4 py-3">
              <button @click="openDialog(c)" class="text-primary-500 hover:underline text-xs mr-3">编辑</button>
              <button @click="handleDelete(c.id)" class="text-red-500 hover:underline text-xs">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="dialogVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50" @click.self="dialogVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-96">
        <h2 class="text-lg font-bold mb-4">{{ editing ? '编辑分类' : '添加分类' }}</h2>
        <div class="space-y-3">
          <input v-model="dialogForm.name" placeholder="名称" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600" />
          <input v-model="dialogForm.slug" placeholder="别名" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600" />
          <input v-model.number="dialogForm.sort" type="number" placeholder="排序" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600" />
        </div>
        <div class="flex justify-end gap-2 mt-4">
          <button @click="dialogVisible = false" class="px-4 py-2 border rounded text-sm">取消</button>
          <button @click="handleSave" class="px-4 py-2 bg-primary-500 text-white rounded text-sm">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get, post, put, del } = useApi()
const categories = ref<any[]>([])
const dialogVisible = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const dialogForm = reactive({ name: '', slug: '', sort: 0 })

async function fetchList() {
  const res = await get<any>('/category/list')
  if (res.code === 200) categories.value = res.data || []
}

function openDialog(row?: any) {
  if (row) {
    editing.value = true; editId.value = row.id
    dialogForm.name = row.name; dialogForm.slug = row.slug; dialogForm.sort = row.sort
  } else {
    editing.value = false; editId.value = null
    dialogForm.name = ''; dialogForm.slug = ''; dialogForm.sort = 0
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (editing.value) {
    await put(`/category/${editId.value}`, dialogForm)
  } else {
    await post('/category', dialogForm)
  }
  dialogVisible.value = false
  fetchList()
}

async function handleDelete(id: number) {
  if (confirm('确定删除？')) {
    await del(`/category/${id}`)
    fetchList()
  }
}

fetchList()
</script>
