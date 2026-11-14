<template>
  <div class="max-w-2xl mx-auto">
    <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-8">个人中心</h1>

    <div v-if="!current.username" class="text-center py-10 text-gray-400">加载中...</div>
    <div v-else class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm p-8 space-y-6">
      <div>
        <label class="text-sm text-gray-500 mb-2 block">头像</label>
        <div class="flex items-center gap-4">
          <img v-if="form.avatar" :src="ossImage(form.avatar, { w: IMG_W.thumb })" class="w-20 h-20 rounded-full object-cover border-2 border-gray-200" />
          <div v-else class="w-20 h-20 rounded-full bg-primary-100 flex items-center justify-center text-primary-600 font-bold text-2xl">{{ current.nickname?.[0] || '?' }}</div>
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
        <input :value="current.username" disabled class="w-full px-3 py-2 border rounded-lg bg-gray-50 dark:bg-gray-700 dark:border-gray-600 text-sm text-gray-400" />
      </div>

      <div>
        <label class="text-sm text-gray-500 mb-1 block">昵称</label>
        <input v-model="form.nickname" placeholder="你的昵称" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>

      <div>
        <label class="text-sm text-gray-500 mb-1 block">
          个人简介
          <span class="text-xs text-gray-400 ml-1">（展示在首页右栏卡片，最多 200 字）</span>
        </label>
        <textarea v-model="form.bio" rows="3" maxlength="200" placeholder="简单介绍一下自己，比如：记录技术成长，分享编程心得"
          class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-y"></textarea>
        <div class="text-right text-xs text-gray-400 mt-1">{{ form.bio.length }}/200</div>
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
import { ossImage, IMG_W } from '~/utils/image'

const authStore = useAuthStore()
const { get, put } = useApi()
// 注意重命名：本页的 `uploadAvatar` 是「选文件后上传」的处理器，避免与上传方法同名
const { uploadAvatar: uploadAvatarApi } = useUpload()
const { toast } = useFeedback()

/* 表单值必须来自**接口**，不能只读 localStorage：
   本地缓存可能缺字段（bio 是后加的），也可能是别的设备上改过的旧值。
   只读缓存的表现就是「简介显示为空，随便改一格再保存才把原值带出来」——
   这和后台站点设置踩过的是同一个坑，所以同样用 `await useAsyncData` 让真值进 SSR payload。 */
const { data: me } = await useAsyncData('profile-me', async () => {
  try {
    const res = await get<any>('/auth/me')
    return res?.code === 200 ? res.data : null
  } catch {
    return null
  }
})

const current = computed(() => me.value || authStore.user || {})

const form = reactive({
  nickname: current.value.nickname || '',
  avatar: current.value.avatar || '',
  bio: current.value.bio || ''
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
    form.avatar = await uploadAvatarApi(file)
    toast('头像上传成功，记得点「保存修改」', 'success')
  } catch (e: any) {
    toast('头像上传失败: ' + (e.message || ''), 'error')
  } finally {
    avatarUploading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await put('/auth/profile', form)
    // 用整份对象重建，而不是 `authStore.user!.xxx = ...`：
    // localStorage 里没缓存时 `authStore.user` 是 null，直接改属性会抛错。
    authStore.setAuth(authStore.token || '', {
      userId: current.value.userId,
      username: current.value.username,
      nickname: form.nickname,
      avatar: form.avatar,
      bio: form.bio,
      role: current.value.role
    })
    msg.value = '保存成功'; msgType.value = 'ok'
  } catch (e: any) {
    msg.value = e.message || '保存失败'; msgType.value = 'err'
  } finally {
    saving.value = false
  }
}
</script>
