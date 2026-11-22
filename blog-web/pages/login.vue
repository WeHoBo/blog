<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4 py-12">
    <canvas ref="particleCanvas" class="fixed inset-0 pointer-events-none opacity-30"></canvas>
    <div class="w-full max-w-4xl grid md:grid-cols-2 bg-white dark:bg-gray-800 rounded-2xl shadow-2xl shadow-primary-500/10 dark:shadow-black/30 overflow-hidden ring-1 ring-gray-100 dark:ring-gray-700/50">
      <!-- Left: Brand -->
      <div class="hidden md:flex bg-gradient-to-br from-primary-600 via-primary-700 to-primary-800 p-10 flex-col justify-between text-white relative overflow-hidden">
        <div class="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGRlZnM+PHBhdHRlcm4gaWQ9ImdyaWQiIHdpZHRoPSI2MCIgaGVpZ2h0PSI2MCIgcGF0dGVyblVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHBhdGggZD0iTSA2MCAwIEwgMCAwIDAgNjAiIGZpbGw9Im5vbmUiIHN0cm9rZT0icmdiYSgyNTUsMjU1LDI1NSwwLjA1KSIgc3Ryb2tlLXdpZHRoPSIxIi8+PC9wYXR0ZXJuPjwvZGVmcz48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSJ1cmwoI2dyaWQpIi8+PC9zdmc+')] opacity-50" />
        <div class="relative z-10">
          <div class="flex items-center gap-2 mb-8">
            <div class="w-10 h-10 bg-white/20 backdrop-blur rounded-xl flex items-center justify-center text-lg font-bold">
              H
            </div>
            <NuxtLink to="/" class="text-2xl font-bold tracking-tight">博观</NuxtLink>
          </div>
          <div class="space-y-6">
            <h1 class="text-4xl font-bold leading-tight">欢迎回来</h1>
            <p class="text-primary-100 leading-relaxed text-base">
              记录技术思考，分享编程心得。登录后可以评论、收藏，也可以申请作者权限发布文章。
            </p>
            <div class="flex flex-col gap-3 mt-8">
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-300 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-primary-100">账号密码登录</span>
              </div>
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-300 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-primary-100">手机验证码登录</span>
              </div>
              <div class="flex items-center gap-3 text-sm">
                <svg class="w-5 h-5 text-emerald-300 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
                <span class="text-primary-100">支持第三方账号</span>
              </div>
            </div>
          </div>
        </div>
        <div class="relative z-10 text-sm text-primary-200/70">
          &copy; {{ new Date().getFullYear() }} 博观 &mdash; 用心记录每一行代码
        </div>
      </div>

      <!-- Right: Form -->
      <div class="p-8 sm:p-12 flex flex-col">
        <div class="mb-6">
          <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">登录</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
            还没有账号？
            <NuxtLink to="/register" class="text-primary-600 dark:text-primary-400 font-medium hover:underline">立即注册</NuxtLink>
          </p>
        </div>

        <!-- 两种登录方式切换 -->
        <div class="flex gap-1 p-1 bg-gray-100 dark:bg-gray-700/60 rounded-full mb-6">
          <button v-for="t in tabs" :key="t.value" type="button" @click="switchTab(t.value)"
            class="flex-1 py-2 rounded-full text-sm transition"
            :class="tab === t.value
              ? 'bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 font-medium shadow-sm'
              : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'">
            {{ t.label }}
          </button>
        </div>

        <form @submit.prevent="doLogin" class="space-y-4">
          <!-- 密码登录 -->
          <template v-if="tab === 'password'">
            <div>
              <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">用户名</label>
              <input v-model.trim="pwdForm.username" type="text" autocomplete="username" placeholder="你的用户名"
                class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
            </div>
            <div>
              <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">密码</label>
              <input v-model="pwdForm.password" type="password" autocomplete="current-password" placeholder="你的密码"
                class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
            </div>
          </template>

          <!-- 验证码登录 -->
          <template v-else>
            <div>
              <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">手机号</label>
              <input v-model.trim="smsForm.phone" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" placeholder="11 位手机号"
                class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
            </div>
            <div>
              <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">验证码</label>
              <div class="flex gap-2">
                <input v-model.trim="smsForm.code" type="text" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="6 位验证码"
                  class="flex-1 px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
                <button type="button" @click="sendCode" :disabled="countdown > 0 || sending"
                  class="px-3 py-2.5 rounded-lg border border-gray-200 dark:border-gray-600 text-xs whitespace-nowrap text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition">
                  {{ countdown > 0 ? `${countdown} 秒` : (sending ? '发送中' : '获取验证码') }}
                </button>
              </div>
            </div>
          </template>

          <!-- 业务失败提示（HTTP 200 + code=400，必须自己判断，不能只靠 catch） -->
          <p v-if="error" class="text-xs text-red-500 leading-relaxed">{{ error }}</p>

          <button type="submit" :disabled="loading"
            class="w-full py-2.5 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-90 disabled:opacity-50 transition">
            {{ loading ? '登录中...' : '登录' }}
          </button>

          <div class="flex items-center justify-between text-xs">
            <NuxtLink to="/forgot-password" class="text-gray-500 dark:text-gray-400 hover:text-primary-600 dark:hover:text-primary-400">忘记密码？</NuxtLink>
            <NuxtLink to="/register" class="text-gray-500 dark:text-gray-400 hover:text-primary-600 dark:hover:text-primary-400">注册新账号</NuxtLink>
          </div>
        </form>

        <!-- 第三方登录：保留作为老用户的备用入口 -->
        <div class="mt-7">
          <div class="flex items-center gap-3 mb-4">
            <span class="flex-1 h-px bg-gray-200 dark:bg-gray-700"></span>
            <span class="text-xs text-gray-400 dark:text-gray-500">或使用第三方账号</span>
            <span class="flex-1 h-px bg-gray-200 dark:bg-gray-700"></span>
          </div>
          <div class="grid grid-cols-3 gap-2">
            <a :href="`${apiBase}/auth/oauth/github`" title="使用 GitHub 登录"
              class="flex items-center justify-center py-2.5 rounded-lg border border-gray-200 dark:border-gray-600 hover:border-gray-800 dark:hover:border-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
              <svg class="w-5 h-5 text-gray-700 dark:text-gray-300" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>
            </a>
            <a :href="`${apiBase}/auth/oauth/gitee`" title="使用 Gitee 登录"
              class="flex items-center justify-center py-2.5 rounded-lg border border-gray-200 dark:border-gray-600 hover:border-red-400 dark:hover:border-red-500 hover:bg-red-50 dark:hover:bg-red-900/10 transition-colors">
              <svg class="w-5 h-5" viewBox="0 0 1024 1024" fill="#C71D23"><path d="M512 1024C229.222 1024 0 794.778 0 512S229.222 0 512 0s512 229.222 512 512-229.222 512-512 512z m259.149-568.889h-290.91c0 92.016 65.707 168.448 153.374 186.653v130.105h-65.991v-90.282c-32.711 15.162-69.147 23.592-107.418 23.592-56.482 0-108.657-17.296-148.871-51.965-40.213-34.669-66.056-82.443-76.515-134.054-4.818-38.191-2.158-77.186 7.559-113.667 9.738-36.46 25.907-69.179 47.894-96.813 22.03-27.635 49.222-49.069 80.173-63.545 30.952-14.474 64.873-21.731 100.516-21.731 72.972 0 138.903 26.724 187.493 79.618l-49.836 56.091c-39.403-40.427-88.8-61.028-137.657-61.028-30.739 0-59.134 7.088-84.141 21.069-25.007 13.98-45.087 33.572-59.284 58.064-14.198 24.491-22.572 52.817-24.781 83.427-2.232 30.634 3.115 59.879 15.686 86.144 12.571 26.264 31.125 47.316 54.814 62.291 23.688 14.976 51.092 22.572 80.924 22.572 29.832 0 58.047-6.999 83.534-20.823 25.487-13.821 46.223-33.701 61.438-58.809"/></svg>
            </a>
            <a :href="`${apiBase}/auth/oauth/huawei`" title="使用华为账号登录"
              class="flex items-center justify-center py-2.5 rounded-lg border border-gray-200 dark:border-gray-600 hover:border-red-500 dark:hover:border-red-400 hover:bg-red-50 dark:hover:bg-red-900/10 transition-colors">
              <svg class="w-5 h-5" viewBox="0 0 24 24" fill="#C7000B"><path d="M3.67 6.14S1.82 7.91 1.72 9.78v.35c.08 1.51 1.22 2.4 1.22 2.4 1.83 1.79 6.26 4.04 7.3 4.55 0 0 .06.03.1-.01l.02-.04v-.04C7.52 10.8 3.67 6.14 3.67 6.14zM9.65 18.6c-.02-.08-.1-.08-.1-.08l-7.38.26c.8 1.43 2.15 2.53 3.56 2.2.96-.25 3.16-1.78 3.88-2.3.06-.05.04-.09.04-.09zm.08-.78C6.49 15.63.21 12.28.21 12.28c-.15.46-.2.9-.21 1.3v.07c0 1.07.4 1.82.4 1.82.8 1.69 2.34 2.2 2.34 2.2.7.3 1.4.31 1.4.31.12.02 4.4 0 5.54 0 .05 0 .08-.05.08-.05v-.06c0-.03-.03-.05-.03-.05zM9.06 3.19a3.42 3.42 0 00-2.57 3.15v.41c.03.6.16 1.05.16 1.05.66 2.9 3.86 7.65 4.55 8.65.05.05.1.03.1.03a.1.1 0 00.06-.1c1.06-10.6-1.11-13.42-1.11-13.42-.32.02-1.19.23-1.19.23zm8.299 2.27s-.49-1.8-2.44-2.28c0 0-.57-.14-1.17-.22 0 0-2.18 2.81-1.12 13.43.01.07.06.08.06.08.07.03.1-.03.1-.03.72-1.03 3.9-5.76 4.55-8.64 0 0 .36-1.4.02-2.34zm-2.92 13.07s-.07 0-.09.05c0 0-.01.07.03.1.7.51 2.85 2 3.88 2.3 0 0 .16.05.43.06h.14c.69-.02 1.9-.37 3-2.26l-7.4-.25zm7.83-8.41c.14-2.06-1.94-3.97-1.94-3.98 0 0-3.85 4.66-6.67 10.8 0 0-.03.08.02.13l.04.01h.06c1.06-.53 5.46-2.77 7.28-4.54 0 0 1.15-.93 1.21-2.42zm1.52 2.14s-6.28 3.37-9.52 5.55c0 0-.05.04-.03.11 0 0 .03.06.07.06 1.16 0 5.56 0 5.67-.02 0 0 .57-.02 1.27-.29 0 0 1.56-.5 2.37-2.27 0 0 .73-1.45.17-3.14z"/></svg>
            </a>
          </div>
          <p class="mt-3 text-center text-[11px] text-gray-400 dark:text-gray-500 leading-relaxed">
            首次使用第三方账号登录会自动创建账号，<br class="sm:hidden" />登录后建议在个人中心设置密码并绑定手机号
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const config = useRuntimeConfig()
const apiBase = config.public.apiBase

const authStore = useAuthStore()
const route = useRoute()
const { post } = useApi()

type Tab = 'password' | 'sms'
const tabs: { value: Tab; label: string }[] = [
  { value: 'password', label: '密码登录' },
  { value: 'sms', label: '验证码登录' }
]

const tab = ref<Tab>('password')
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const countdown = ref(0)

const pwdForm = reactive({ username: '', password: '' })
const smsForm = reactive({ phone: '', code: '' })

let timer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function switchTab(t: Tab) {
  tab.value = t
  error.value = ''
}

function startCountdown() {
  countdown.value = 60
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

/**
 * 业务失败是 HTTP 200 + body.code=400，useApi 的 onResponseError 不会触发，
 * 所以必须自己看 code —— 只看 try/catch 会出现「点了没反应」。
 */
async function doLogin() {
  error.value = ''
  if (tab.value === 'password') {
    if (!pwdForm.username || !pwdForm.password) {
      error.value = '请输入用户名和密码'
      return
    }
  } else {
    if (!/^1[3-9]\d{9}$/.test(smsForm.phone)) {
      error.value = '请输入正确的手机号'
      return
    }
    if (!/^\d{6}$/.test(smsForm.code)) {
      error.value = '请输入 6 位验证码'
      return
    }
  }

  loading.value = true
  try {
    const res: any = tab.value === 'password'
      ? await post('/auth/login', { username: pwdForm.username, password: pwdForm.password })
      : await post('/auth/login/sms', { phone: smsForm.phone, code: smsForm.code })

    if (res?.code !== 200) {
      error.value = res?.message || '登录失败'
      return
    }
    authStore.setAuthFromLogin(res.data)
    await navigateTo((route.query.redirect as string) || '/')
  } catch (e: any) {
    error.value = e?.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  if (countdown.value > 0 || sending.value) return
  error.value = ''
  if (!/^1[3-9]\d{9}$/.test(smsForm.phone)) {
    error.value = '请输入正确的手机号'
    return
  }
  sending.value = true
  try {
    // scene=login：后端会先查这个号码是否已注册，没注册就不白花一条短信
    const res: any = await post('/auth/sms/send', { phone: smsForm.phone, scene: 'login' })
    if (res?.code !== 200) {
      error.value = res?.message || '验证码发送失败'
      return
    }
    startCountdown()
  } catch (e: any) {
    error.value = e?.message || '验证码发送失败'
  } finally {
    sending.value = false
  }
}

// 粒子背景（原有视觉效果，保持不动）
const particleCanvas = ref<HTMLCanvasElement | null>(null)
onMounted(() => {
  const canvas = particleCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  canvas.width = window.innerWidth
  canvas.height = window.innerHeight
  const rootStyle = getComputedStyle(document.documentElement)
  const ph = rootStyle.getPropertyValue('--ph').trim() || '217'
  const ps = rootStyle.getPropertyValue('--ps').trim() || '91%'
  const particleColor = `hsl(${ph} ${ps} 60%)`
  const particles: any[] = []
  for (let i = 0; i < 50; i++) {
    particles.push({
      x: Math.random() * canvas.width, y: Math.random() * canvas.height,
      r: Math.random() * 2 + 1, dx: (Math.random() - 0.5) * 0.5, dy: (Math.random() - 0.5) * 0.5
    })
  }
  function draw() {
    ctx!.clearRect(0, 0, canvas!.width, canvas!.height)
    ctx!.fillStyle = particleColor
    for (const p of particles) {
      ctx!.beginPath(); ctx!.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx!.fill()
      p.x += p.dx; p.y += p.dy
      if (p.x < 0 || p.x > canvas!.width) p.dx *= -1
      if (p.y < 0 || p.y > canvas!.height) p.dy *= -1
    }
    requestAnimationFrame(draw)
  }
  draw()
})
</script>
