<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-4xl grid md:grid-cols-2 bg-white dark:bg-gray-800 rounded-2xl shadow-2xl shadow-emerald-500/10 dark:shadow-black/30 overflow-hidden ring-1 ring-gray-100 dark:ring-gray-700/50">
      <!-- Left: Brand -->
      <div class="hidden md:flex bg-gradient-to-br from-emerald-500 via-teal-600 to-cyan-700 p-10 flex-col justify-between text-white relative overflow-hidden">
        <div class="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGRlZnM+PHBhdHRlcm4gaWQ9ImdyaWQiIHdpZHRoPSI2MCIgaGVpZ2h0PSI2MCIgcGF0dGVyblVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHBhdGggZD0iTSA2MCAwIEwgMCAwIDAgNjAiIGZpbGw9Im5vbmUiIHN0cm9rZT0icmdiYSgyNTUsMjU1LDI1NSwwLjA1KSIgc3Ryb2tlLXdpZHRoPSIxIi8+PC9wYXR0ZXJuPjwvZGVmcz48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSJ1cmwoI2dyaWQpIi8+PC9zdmc+')] opacity-50" />
        <div class="relative z-10">
          <div class="flex items-center gap-2 mb-8">
            <div class="w-10 h-10 bg-white/20 backdrop-blur rounded-xl flex items-center justify-center text-lg font-bold">
              H
            </div>
            <NuxtLink to="/" class="text-2xl font-bold tracking-tight">好啵博客</NuxtLink>
          </div>
          <div class="space-y-6">
            <h1 class="text-4xl font-bold leading-tight">加入我们</h1>
            <p class="text-emerald-100 leading-relaxed text-base">
              创建你的账号，开始记录技术成长。只需几秒钟，即刻开启你的技术写作之旅。
            </p>
            <div class="flex flex-col gap-3 mt-8">
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-200 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-emerald-100">免费注册，永久使用</span>
              </div>
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-200 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-emerald-100">支持 OAuth 一键登录</span>
              </div>
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-200 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-emerald-100">发布属于你的技术文章</span>
              </div>
            </div>
          </div>
        </div>
        <div class="relative z-10 text-sm text-emerald-200/70">
          &copy; {{ new Date().getFullYear() }} 好啵博客 &mdash; 用心记录每一行代码
        </div>
      </div>

      <!-- Right: Form -->
      <div class="p-8 sm:p-12 flex flex-col">
        <div class="mb-8">
          <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">创建账号</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">开始你的写作之旅</p>
        </div>

        <form @submit.prevent="handleRegister" class="space-y-4 flex-1">
          <div>
            <label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1.5 uppercase tracking-wider">用户名 <span class="text-red-400">*</span></label>
            <div class="relative">
              <svg class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>
              <input v-model="form.username" type="text" required minlength="3" placeholder="3-20位字符" class="w-full pl-10 pr-4 py-3 rounded-xl border-2 border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 focus:border-emerald-500 dark:focus:border-emerald-400 focus:ring-4 focus:ring-emerald-500/10 dark:focus:ring-emerald-400/10 focus:outline-none transition-all text-sm" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1.5 uppercase tracking-wider">昵称</label>
            <div class="relative">
              <svg class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z"/></svg>
              <input v-model="form.nickname" type="text" placeholder="你的昵称（可选）" class="w-full pl-10 pr-4 py-3 rounded-xl border-2 border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 focus:border-emerald-500 dark:focus:border-emerald-400 focus:ring-4 focus:ring-emerald-500/10 dark:focus:ring-emerald-400/10 focus:outline-none transition-all text-sm" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1.5 uppercase tracking-wider">邮箱</label>
            <div class="relative">
              <svg class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/></svg>
              <input v-model="form.email" type="email" placeholder="example@mail.com（可选）" class="w-full pl-10 pr-4 py-3 rounded-xl border-2 border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 focus:border-emerald-500 dark:focus:border-emerald-400 focus:ring-4 focus:ring-emerald-500/10 dark:focus:ring-emerald-400/10 focus:outline-none transition-all text-sm" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1.5 uppercase tracking-wider">密码 <span class="text-red-400">*</span></label>
            <div class="relative">
              <svg class="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/></svg>
              <input v-model="form.password" :type="showPassword ? 'text' : 'password'" required minlength="6" placeholder="至少6位字符" class="w-full pl-10 pr-12 py-3 rounded-xl border-2 border-gray-200 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 focus:border-emerald-500 dark:focus:border-emerald-400 focus:ring-4 focus:ring-emerald-500/10 dark:focus:ring-emerald-400/10 focus:outline-none transition-all text-sm" />
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
          <p v-if="successMsg" class="text-emerald-500 text-xs bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-100 dark:border-emerald-800/30 rounded-xl px-4 py-3 flex items-center gap-2">
            <svg class="w-4 h-4 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
            {{ successMsg }}
          </p>

          <button type="submit" :disabled="submitting" class="w-full py-3 bg-gradient-to-r from-emerald-500 via-teal-600 to-cyan-600 text-white rounded-xl font-semibold text-sm hover:shadow-lg hover:shadow-emerald-500/25 hover:-translate-y-0.5 disabled:opacity-50 disabled:hover:translate-y-0 disabled:hover:shadow-none transition-all duration-200 tracking-wide">
            {{ submitting ? '创建中...' : '创建账号' }}
          </button>
        </form>

        <p class="mt-6 text-center text-sm text-gray-500 dark:text-gray-400">
          已有账号？<NuxtLink to="/login" class="text-emerald-600 dark:text-emerald-400 font-semibold hover:underline underline-offset-4">立即登录</NuxtLink>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const { register } = useAuth()
const router = useRouter()

const form = reactive({ username: '', password: '', nickname: '', email: '' })
const errorMsg = ref('')
const successMsg = ref('')
const submitting = ref(false)
const showPassword = ref(false)

async function handleRegister() {
  errorMsg.value = ''
  successMsg.value = ''
  submitting.value = true
  try {
    await register({ username: form.username, password: form.password, nickname: form.nickname, email: form.email })
    successMsg.value = '注册成功，即将跳转到登录页面...'
    setTimeout(() => router.push('/login'), 1500)
  } catch (e: any) {
    errorMsg.value = e.message || '注册失败'
  } finally {
    submitting.value = false
  }
}
</script>
