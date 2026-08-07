<template>
  <div class="max-w-2xl">
    <h1 class="text-2xl font-bold mb-6">站点设置</h1>
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 space-y-4">
      <div>
        <label class="text-sm text-gray-500 mb-1 block">站点描述</label>
        <input v-model="form.siteDesc" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
      </div>
      <div>
        <label class="text-sm text-gray-500 mb-1 block">关于页内容 (Markdown)</label>
        <textarea v-model="form.aboutContent" rows="10" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm font-mono"></textarea>
      </div>
      <div>
        <label class="text-sm text-gray-500 mb-1 block">自定义背景图</label>
        <div class="flex gap-2">
          <input v-model="form.bgImage" placeholder="背景图片URL" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <label class="px-3 py-2 border-2 border-dashed rounded-lg text-xs cursor-pointer hover:border-primary-500 hover:text-primary-500 whitespace-nowrap flex items-center">
            <input type="file" accept="image/*" class="hidden" @change="uploadBg" />
            {{ bgUploading ? '上传中...' : '上传' }}
          </label>
        </div>
        <button @click="clearBg" class="text-xs text-red-500 hover:underline mt-1" v-if="form.bgImage">清除背景</button>
      </div>
      <div>
        <label class="text-sm text-gray-500 mb-1 block">博主名称</label>
        <input v-model="form.bloggerName" placeholder="显示在关于页" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
      </div>
      <div>
        <label class="text-sm text-gray-500 mb-1 block">博主简介</label>
        <textarea v-model="form.bloggerBio" rows="2" placeholder="📝 记录技术成长&#10;💻 分享编程心得" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm"></textarea>
      </div>
      <div>
        <label class="text-sm text-gray-500 mb-1 block">博主头像URL</label>
        <input v-model="form.bloggerAvatar" placeholder="https://..." class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
      </div>
      <button @click="handleSave" class="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 text-sm transition">保存</button>
      <p v-if="msg" class="text-sm" :class="msgType === 'ok' ? 'text-green-500' : 'text-red-500'">{{ msg }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })
const { get, post } = useApi()
const msg = ref('')
const msgType = ref('')
const bgUploading = ref(false)
const { upload } = useUpload()

const form = reactive({ siteDesc: '', aboutContent: '', bgImage: '', bloggerName: '', bloggerBio: '', bloggerAvatar: '' })

async function fetchConfig() {
  const res = await get<any>('/site-config/public')
  if (res.code === 200 && res.data) {
    form.siteDesc = res.data.siteDesc || ''
    form.aboutContent = res.data.aboutContent || ''
    form.bgImage = res.data.bgImage || ''
    form.bloggerName = res.data.bloggerName || ''
    form.bloggerBio = res.data.bloggerBio || ''
    form.bloggerAvatar = res.data.bloggerAvatar || ''
  }
}

async function uploadBg(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  bgUploading.value = true
  try { form.bgImage = await upload(file) }
  catch (e: any) { alert('上传失败') }
  finally { bgUploading.value = false }
}

function clearBg() { form.bgImage = '' }

async function handleSave() {
  try {
    await post('/site-config/save', form)
    msg.value = '保存成功'; msgType.value = 'ok'
  } catch (e: any) {
    msg.value = e.message || '保存失败'; msgType.value = 'err'
  }
}

fetchConfig()
</script>
