<template>
  <div>
    <h1 class="text-2xl font-bold mb-6">用户管理</h1>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[880px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="px-4 py-3 text-left">ID</th>
              <th class="px-4 py-3 text-left">用户名</th>
              <th class="px-4 py-3 text-left">昵称</th>
              <th class="px-4 py-3 text-left">邮箱</th>
              <th class="px-4 py-3 text-left">角色</th>
              <th class="px-4 py-3 text-left">来源</th>
              <th class="px-4 py-3 text-left">状态</th>
              <th class="px-4 py-3 text-left">创建时间</th>
              <th class="px-4 py-3 text-left w-28">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id" class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-3 text-gray-400">{{ u.id }}</td>
              <td class="px-4 py-3 font-medium">{{ u.username }}</td>
              <td class="px-4 py-3">{{ u.nickname || '-' }}</td>
              <td class="px-4 py-3 text-gray-400 text-xs">{{ u.email || '-' }}</td>
              <td class="px-4 py-3">
                <span :class="u.role === 'admin' ? 'text-red-500' : 'text-gray-500'" class="text-xs font-medium">{{ u.role === 'admin' ? '管理员' : '用户' }}</span>
              </td>
              <td class="px-4 py-3 text-xs text-gray-400">{{ u.source || 'local' }}</td>
              <td class="px-4 py-3">
                <span :class="u.status === 1 ? 'text-green-500' : 'text-red-500'" class="text-xs">{{ u.status === 1 ? '正常' : '禁用' }}</span>
              </td>
              <td class="px-4 py-3 text-gray-400 text-xs whitespace-nowrap">{{ u.createTime?.substring(0, 10) }}</td>
              <td class="px-4 py-3 whitespace-nowrap">
                <button @click="openDialog(u)" class="text-primary-500 hover:underline text-xs">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="dialogVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4" @click.self="dialogVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-full max-w-sm">
        <h2 class="text-lg font-bold mb-4">编辑用户 - {{ editUser?.username }}</h2>
        <div class="space-y-3">
          <div>
            <label class="text-xs text-gray-400">昵称</label>
            <input v-model="dialogForm.nickname" aria-label="昵称" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm mt-1" />
          </div>
          <div>
            <label class="text-xs text-gray-400">角色</label>
            <select v-model="dialogForm.role" aria-label="角色" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm mt-1">
              <option value="user">用户</option>
              <option value="admin">管理员</option>
            </select>
          </div>
          <div>
            <label class="text-xs text-gray-400">状态</label>
            <select v-model="dialogForm.status" aria-label="状态" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm mt-1">
              <option :value="1">正常</option>
              <option :value="0">禁用</option>
            </select>
          </div>
          <div>
            <label class="text-xs text-gray-400">新密码（留空不修改）</label>
            <input v-model="dialogForm.password" type="password" autocomplete="new-password" aria-label="新密码" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm mt-1" />
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

const { get, put } = useApi()
const { toast } = useFeedback()
const users = ref<any[]>([])
const dialogVisible = ref(false)
const saving = ref(false)
const editUser = ref<any>(null)
const dialogForm = reactive({ nickname: '', role: 'user', status: 1, password: '' })

async function fetchList() {
  const res = await get<any>('/admin/user/list?pageSize=100')
  if (res.code === 200 && res.data) users.value = res.data.records || []
}

function openDialog(u: any) {
  editUser.value = u
  dialogForm.nickname = u.nickname || ''
  dialogForm.role = u.role
  dialogForm.status = u.status
  dialogForm.password = ''
  dialogVisible.value = true
}

async function handleSave() {
  if (saving.value) return
  saving.value = true
  try {
    await put(`/admin/user/${editUser.value.id}`, dialogForm)
    dialogVisible.value = false
    toast('保存成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('保存失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    saving.value = false
  }
}

fetchList()
</script>
