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
const { post } = useApi()

const errorMessage = ref('')

/**
 * 回调页只负责一件事：拿地址里的**一次性 code** 去换登录凭证。
 *
 * ⚠️ 凭证不能放在回调 URL 里：那条 URL 会被 nginx access log、浏览器历史、
 * Referer 各记一份，而它背后是一个 24 小时有效的管理员 token。
 * 现在地址里只有一个 60 秒、用一次即删的 code。
 */
onMounted(async () => {
  const error = route.query.error as string
  const code = route.query.code as string
  // 过渡期兼容：jar 与前端是两个产物，若只换了其中一个，旧回调仍会带 token 过来
  const legacyToken = route.query.token as string

  if (error) {
    errorMessage.value = error
    return
  }

  if (code) {
    try {
      const res: any = await post('/auth/oauth/exchange', { code })
      if (res?.code === 200 && res.data?.token) {
        authStore.setAuthFromLogin(res.data)
        // replace 而不是 push：别让带 code 的 URL 留在浏览器历史里
        await router.replace('/')
      } else {
        errorMessage.value = res?.message || '登录凭证已失效，请重新发起登录'
      }
    } catch (e: any) {
      errorMessage.value = e?.message || '登录凭证换取失败，请重新发起登录'
    }
    return
  }

  if (legacyToken) {
    authStore.setAuth(legacyToken, {
      userId: Number(route.query.userId),
      username: route.query.username as string,
      nickname: (route.query.nickname as string) || (route.query.username as string),
      avatar: (route.query.avatar as string) || '',
      role: (route.query.role as string) || 'user'
    })
    await router.replace('/')
    return
  }

  // 既没有 code 也没有 error：回调参数异常，给出提示而不是无限转圈
  errorMessage.value = '登录回调缺少必要参数，请重新发起登录'
})
</script>
