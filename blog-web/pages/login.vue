<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-4xl grid md:grid-cols-2 bg-white dark:bg-gray-800 rounded-2xl shadow-2xl shadow-blue-500/10 dark:shadow-black/30 overflow-hidden ring-1 ring-gray-100 dark:ring-gray-700/50">
      <!-- Left: Brand -->
      <div class="hidden md:flex bg-gradient-to-br from-blue-600 via-indigo-600 to-indigo-800 p-10 flex-col justify-between text-white relative overflow-hidden">
        <div class="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGRlZnM+PHBhdHRlcm4gaWQ9ImdyaWQiIHdpZHRoPSI2MCIgaGVpZ2h0PSI2MCIgcGF0dGVyblVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHBhdGggZD0iTSA2MCAwIEwgMCAwIDAgNjAiIGZpbGw9Im5vbmUiIHN0cm9rZT0icmdiYSgyNTUsMjU1LDI1NSwwLjA1KSIgc3Ryb2tlLXdpZHRoPSIxIi8+PC9wYXR0ZXJuPjwvZGVmcz48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSJ1cmwoI2dyaWQpIi8+PC9zdmc+')] opacity-50" />
        <div class="relative z-10">
          <div class="flex items-center gap-2 mb-8">
            <div class="w-10 h-10 bg-white/20 backdrop-blur rounded-xl flex items-center justify-center text-lg font-bold">
              H
            </div>
            <NuxtLink to="/" class="text-2xl font-bold tracking-tight">好啵博客</NuxtLink>
          </div>
          <div class="space-y-6">
            <h1 class="text-4xl font-bold leading-tight">欢迎回来</h1>
            <p class="text-blue-100 leading-relaxed text-base">
              记录技术思考，分享编程心得。用 GitHub 或 Gitee 一键登录，继续你的技术之旅。
            </p>
            <div class="flex flex-col gap-3 mt-8">
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-300 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-blue-100">极速 OAuth 登录</span>
              </div>
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-300 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-blue-100">支持 Markdown 写作</span>
              </div>
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-300 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-blue-100">技术文章分享社区</span>
              </div>
            </div>
          </div>
        </div>
        <div class="relative z-10 text-sm text-blue-200/70">
          &copy; {{ new Date().getFullYear() }} 好啵博客 &mdash; 用心记录每一行代码
        </div>
      </div>

      <!-- Right: Form -->
      <div class="p-8 sm:p-12 flex flex-col">
        <div class="mb-8">
          <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">登录账号</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">选择你的登录方式</p>
        </div>

        <!-- OAuth -->
        <div class="space-y-3">
          <a :href="`${apiBase}/auth/oauth/github`" class="group flex items-center justify-center gap-3 w-full py-3 rounded-xl border-2 border-gray-200 dark:border-gray-600 hover:border-gray-800 dark:hover:border-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-all duration-200 text-sm font-medium text-gray-700 dark:text-gray-300">
            <svg class="w-5 h-5 group-hover:scale-110 transition-transform" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>
            <span>使用 GitHub 登录</span>
          </a>
          <a :href="`${apiBase}/auth/oauth/gitee`" class="group flex items-center justify-center gap-3 w-full py-3 rounded-xl border-2 border-gray-200 dark:border-gray-600 hover:border-red-400 dark:hover:border-red-500 hover:bg-red-50 dark:hover:bg-red-900/10 transition-all duration-200 text-sm font-medium text-gray-700 dark:text-gray-300">
            <svg class="w-5 h-5 group-hover:scale-110 transition-transform" viewBox="0 0 1024 1024" fill="#C71D23"><path d="M512 1024C229.222 1024 0 794.778 0 512S229.222 0 512 0s512 229.222 512 512-229.222 512-512 512z m259.149-568.889h-290.91c0 92.016 65.707 168.448 153.374 186.653v130.105h-65.991v-90.282c-32.711 15.162-69.147 23.592-107.418 23.592-56.482 0-108.657-17.296-148.871-51.965-40.213-34.669-66.056-82.443-76.515-134.054-4.818-38.191-2.158-77.186 7.559-113.667 9.738-36.46 25.907-69.179 47.894-96.813 22.03-27.635 49.222-49.069 80.173-63.545 30.952-14.474 64.873-21.731 100.516-21.731 72.972 0 138.903 26.724 187.493 79.618l-49.836 56.091c-39.403-40.427-88.8-61.028-137.657-61.028-30.739 0-59.134 7.088-84.141 21.069-25.007 13.98-45.087 33.572-59.284 58.064-14.198 24.491-22.572 52.817-24.781 83.427-2.232 30.634 3.115 59.879 15.686 86.144 12.571 26.264 31.125 47.316 54.814 62.291 23.688 14.976 51.092 22.572 80.924 22.572 29.832 0 58.047-6.999 83.534-20.823 25.487-13.821 46.223-33.701 61.438-58.809"/></svg>
            <span>使用 Gitee 登录</span>
          </a>
        </div>

        <div class="flex items-center my-6">
          <div class="flex-1 border-t dark:border-gray-600"></div>
          <span class="px-4 text-xs text-gray-400 dark:text-gray-500 uppercase tracking-wider font-medium">或 账号登录</span>
          <div class="flex-1 border-t dark:border-gray-600"></div>
        </div>

        <!-- Account Login -->
        <form @submit.prevent="handleLogin" class="space-y-4 flex-1">
          <div>
            <label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1.5 uppercase tracking-wider">用户名</label>
            <div class="relative">
              <svg class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>
              <input v-model="form.username" type="text" required placeholder="请输入用户名" class="w-full pl-10 pr-4 py-3 rounded-xl border-2 border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 focus:border-blue-500 dark:focus:border-blue-400 focus:ring-4 focus:ring-blue-500/10 dark:focus:ring-blue-400/10 focus:outline-none transition-all text-sm" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1.5 uppercase tracking-wider">密码</label>
            <div class="relative">
              <svg class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/></svg>
              <input v-model="form.password" :type="showPassword ? 'text' : 'password'" required placeholder="请输入密码" class="w-full pl-10 pr-12 py-3 rounded-xl border-2 border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 focus:border-blue-500 dark:focus:border-blue-400 focus:ring-4 focus:ring-blue-500/10 dark:focus:ring-blue-400/10 focus:outline-none transition-all text-sm" />
              <button type="button" @click="showPassword = !showPassword" class="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors">
                <svg v-if="showPassword" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l2.122 2.122M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/></svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>
              </button>
            </div>
          </div>

          <p v-if="errorMsg" class="text-red-500 text-xs bg-red-50 dark:bg-red-900/20 border border-red-100 dark:border-red-800/30 rounded-xl px-4 py-3 flex items-center gap-2">
            <svg class="w-4 h-4 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
            {{ errorMsg }}
          </p>

          <button type="submit" :disabled="submitting" class="w-full py-3 bg-gradient-to-r from-blue-600 via-blue-700 to-indigo-700 text-white rounded-xl font-semibold text-sm hover:shadow-lg hover:shadow-blue-500/25 hover:-translate-y-0.5 disabled:opacity-50 disabled:hover:translate-y-0 disabled:hover:shadow-none transition-all duration-200 tracking-wide">
            {{ submitting ? '登录中...' : '登录' }}
          </button>
        </form>

        <p class="mt-6 text-center text-sm text-gray-500 dark:text-gray-400">
          没有账号？<NuxtLink to="/register" class="text-blue-600 dark:text-blue-400 font-semibold hover:underline underline-offset-4">立即注册</NuxtLink>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const { login } = useAuth()
const router = useRouter()
const config = useRuntimeConfig()
const apiBase = config.public.apiBase

const form = reactive({ username: '', password: '' })
const errorMsg = ref('')
const submitting = ref(false)
const showPassword = ref(false)

async function handleLogin() {
  errorMsg.value = ''
  submitting.value = true
  try {
    await login({ username: form.username, password: form.password })
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>
