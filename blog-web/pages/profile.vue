<template>
  <div class="max-w-2xl mx-auto">
    <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-8">个人中心</h1>

    <div v-if="!authStore.user" class="text-center py-10 text-gray-400">加载中...</div>
    <div v-else class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm p-8 space-y-6">
      <div>
        <label class="text-sm text-gray-500 mb-2 block">头像</label>
        <div class="flex items-center gap-4">
          <img v-if="form.avatar" :src="form.avatar" class="w-20 h-20 rounded-full object-cover border-2 border-gray-200" />
          <div v-else class="w-20 h-20 rounded-full bg-primary-100 flex items-center justify-center text-primary-600 font-bold text-2xl">{{ authStore.user?.nickname?.[0] || '?' }}</div>
          <div class="flex flex-col gap-2">
            <label class="px-4 py-2 border-2 border-dashed rounded-lg text-sm cursor-pointer hover:border-primary-500 hover:text-primary-500 transition text-center">
              <input type="file" accept="image/*" class="hidden" @change="uploadAvatar" />
              {{ avatarUploading ? '上传中...' : '上传头像' }}
            </label>
            <input v-model="form.avatar" placeholder="或输入头像URL" class="px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-xs w-48" />
          </div>
        </div>
      </div>

      <div>
        <label class="text-sm text-gray-500 mb-1 block">用户名</label>
        <input :value="authStore.user?.username" disabled class="w-full px-3 py-2 border rounded-lg bg-gray-50 dark:bg-gray-700 dark:border-gray-600 text-sm text-gray-400" />
      </div>

      <div>
        <label class="text-sm text-gray-500 mb-1 block">昵称</label>
        <input v-model="form.nickname" placeholder="你的昵称" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>

      <div>
        <label class="text-sm text-gray-500 mb-1 block">新密码（留空不修改）</label>
        <input v-model="form.password" type="password" placeholder="输入新密码" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>

      <p v-if="msg" :class="msgType === 'ok' ? 'text-green-500' : 'text-red-500'" class="text-sm">{{ msg }}</p>

      <button @click="handleSave" :disabled="saving" class="px-6 py-2.5 bg-primary-600 text-white rounded-lg font-medium text-sm hover:bg-primary-700 disabled:opacity-50 transition">
        {{ saving ? '保存中...' : '保存修改' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'auth' })
const authStore = useAuthStore()
const { put } = useApi()
const { upload } = useUpload()

const form = reactive({
  nickname: authStore.user?.nickname || '',
  avatar: authStore.user?.avatar || '',
  password: ''
})
const msg = ref('')
const msgType = ref('')
const saving = ref(false)
const avatarUploading = ref(false)

async function uploadAvatar(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  avatarUploading.value = true
  try {
    form.avatar = await upload(file)
  } catch (e: any) {
    alert('头像上传失败: ' + (e.message || ''))
  } finally {
    avatarUploading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await put('/auth/profile', form)
    authStore.user!.nickname = form.nickname
    authStore.user!.avatar = form.avatar
    if (process.client) localStorage.setItem('user', JSON.stringify(authStore.user))
    msg.value = '保存成功'; msgType.value = 'ok'
  } catch (e: any) {
    msg.value = e.message || '保存失败'; msgType.value = 'err'
  } finally {
    saving.value = false
  }
}
</script>
