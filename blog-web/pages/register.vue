<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md bg-white dark:bg-gray-800 rounded-2xl shadow-sm ring-1 ring-gray-100 dark:ring-gray-700/50 p-8 sm:p-10">
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">创建账号</h1>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          已有账号？
          <NuxtLink to="/login" class="text-primary-600 dark:text-primary-400 font-medium hover:underline">去登录</NuxtLink>
        </p>
      </div>

      <form @submit.prevent="doRegister" class="space-y-4">
        <div>
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">用户名</label>
          <input v-model.trim="form.username" type="text" autocomplete="username" placeholder="3-20 位，字母数字下划线"
            class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
          <p class="text-[11px] text-gray-400 mt-1">登录时使用，注册后不可修改</p>
        </div>

        <div>
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">密码</label>
          <input v-model="form.password" type="password" autocomplete="new-password" placeholder="至少 6 位"
            class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
        </div>

        <div>
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">确认密码</label>
          <input v-model="form.confirm" type="password" autocomplete="new-password" placeholder="再输一次"
            class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
        </div>

        <div>
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">手机号</label>
          <input v-model.trim="form.phone" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" placeholder="11 位手机号"
            class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
          <p class="text-[11px] text-gray-400 mt-1">用于找回密码，不会公开展示</p>
        </div>

        <div>
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">短信验证码</label>
          <div class="flex gap-2">
            <input v-model.trim="form.code" type="text" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="6 位验证码"
              class="flex-1 px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
            <button type="button" @click="sendCode" :disabled="countdown > 0 || sending"
              class="px-3 py-2.5 rounded-lg border border-gray-200 dark:border-gray-600 text-xs whitespace-nowrap text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition">
              {{ countdown > 0 ? `${countdown} 秒` : (sending ? '发送中' : '获取验证码') }}
            </button>
          </div>
        </div>

        <div>
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">昵称（可选）</label>
          <input v-model.trim="form.nickname" type="text" maxlength="50" placeholder="留空则使用用户名"
            class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
        </div>

        <p v-if="error" class="text-xs text-red-500 leading-relaxed">{{ error }}</p>

        <button type="submit" :disabled="loading"
          class="w-full py-2.5 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-90 disabled:opacity-50 transition">
          {{ loading ? '注册中...' : '注册并登录' }}
        </button>

        <p class="text-[11px] text-gray-400 text-center leading-relaxed">
          手机号仅用于账号找回，不会公开展示。
        </p>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
const authStore = useAuthStore()
const { post } = useApi()

const form = reactive({
  username: '',
  password: '',
  confirm: '',
  phone: '',
  code: '',
  nickname: ''
})

const loading = ref(false)
const sending = ref(false)
const error = ref('')
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

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

/** 前端先拦一道：后端也会校验，但本地报错更即时，也少一次往返 */
function validate(): string {
  if (!/^[A-Za-z0-9_-]{3,20}$/.test(form.username)) {
    return '用户名需为 3-20 位字母、数字、下划线或短横线'
  }
  if (form.password.length < 6 || form.password.length > 30) {
    return '密码长度需为 6-30 位'
  }
  if (form.password !== form.confirm) {
    return '两次输入的密码不一致'
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    return '请输入正确的手机号'
  }
  if (!/^\d{6}$/.test(form.code)) {
    return '请输入 6 位短信验证码'
  }
  return ''
}

async function sendCode() {
  if (countdown.value > 0 || sending.value) return
  error.value = ''
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    error.value = '请先填写正确的手机号'
    return
  }
  sending.value = true
  try {
    const res: any = await post('/auth/sms/send', { phone: form.phone, scene: 'register' })
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

async function doRegister() {
  error.value = ''
  const msg = validate()
  if (msg) {
    error.value = msg
    return
  }

  loading.value = true
  try {
    const res: any = await post('/auth/register', {
      username: form.username,
      password: form.password,
      phone: form.phone,
      code: form.code,
      nickname: form.nickname || undefined
    })
    // 业务失败是 HTTP 200 + code=400，必须自己判断，否则「点了没反应」
    if (res?.code !== 200) {
      error.value = res?.message || '注册失败'
      return
    }
    authStore.setAuthFromLogin(res.data)
    await navigateTo('/')
  } catch (e: any) {
    error.value = e?.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
