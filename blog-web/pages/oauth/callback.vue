<template>
  <div class="max-w-md mx-auto px-6 py-24">
    <!-- 失败：展示后端带回来的原因，并引导用户自己解决 -->
    <div v-if="errorMessage" class="text-center">
      <div class="mx-auto w-12 h-12 rounded-full bg-red-50 dark:bg-red-900/20 flex items-center justify-center mb-5">
        <svg class="w-6 h-6 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
        </svg>
      </div>
      <h1 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">登录失败</h1>
      <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed mb-6">{{ errorMessage }}</p>
      <div class="flex items-center justify-center gap-3">
        <NuxtLink to="/login"
          class="px-4 py-2 rounded-lg bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 transition-colors">
          重新登录
        </NuxtLink>
        <NuxtLink to="/"
          class="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-600 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
          返回首页
        </NuxtLink>
      </div>
    </div>

    <!-- 处理中：登录成功时短暂展示，随即跳转 -->
    <div v-else class="flex justify-center py-8">
      <div class="animate-spin h-8 w-8 border-2 border-primary-600 border-t-transparent rounded-full"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const errorMessage = ref('')

if (process.client) {
  const token = route.query.token as string
  // 后端失败时会重定向到这里并带上 error（URL 编码过的原因）
  const error = route.query.error as string

  if (error) {
    errorMessage.value = error
  } else if (token) {
    const userId = route.query.userId as string
    const username = route.query.username as string
    const nickname = route.query.nickname as string
    const avatar = route.query.avatar as string
    const role = route.query.role as string

    authStore.setAuth(token, {
      userId: Number(userId),
      username,
      nickname: nickname || username,
      avatar: avatar || '',
      role: role || 'user'
    })
    router.push('/')
  } else {
    // 既没有 token 也没有 error：回调参数异常，给出提示而不是无限转圈
    errorMessage.value = '登录回调缺少必要参数，请重新发起登录'
  }
}
</script>
