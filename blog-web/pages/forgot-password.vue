<template>
  <div class="min-h-[80vh] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md bg-white dark:bg-gray-800 rounded-2xl shadow-sm ring-1 ring-gray-100 dark:ring-gray-700/50 p-8 sm:p-10">
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">找回密码</h1>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          用注册时绑定的手机号验证身份
        </p>
      </div>

      <!-- 重置成功 -->
      <div v-if="done" class="space-y-5">
        <div class="flex items-start gap-3 p-4 rounded-lg bg-gray-50 dark:bg-gray-700/50">
          <svg class="w-5 h-5 text-emerald-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" stroke-width="1.6" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5" />
          </svg>
          <div class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">
            密码已重置。请用新密码登录。
          </div>
        </div>
        <NuxtLink to="/login"
          class="block w-full py-2.5 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium text-center hover:opacity-90 transition">
          去登录
        </NuxtLink>
      </div>

      <form v-else @submit.prevent="doReset" class="space-y-4">
        <div>
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">手机号</label>
          <input v-model.trim="form.phone" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" placeholder="注册时绑定的手机号"
            class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
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
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">新密码</label>
          <input v-model="form.newPassword" type="password" autocomplete="new-password" placeholder="至少 6 位"
            class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
        </div>

        <div>
          <label class="text-xs text-gray-500 dark:text-gray-400 mb-1.5 block">确认新密码</label>
          <input v-model="form.confirm" type="password" autocomplete="new-password" placeholder="再输一次"
            class="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent" />
        </div>

        <p v-if="error" class="text-xs text-red-500 leading-relaxed">{{ error }}</p>

        <button type="submit" :disabled="loading"
          class="w-full py-2.5 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-90 disabled:opacity-50 transition">
          {{ loading ? '提交中...' : '重置密码' }}
        </button>

        <p class="text-xs text-gray-400 text-center">
          想起来了？<NuxtLink to="/login" class="text-primary-600 dark:text-primary-400 hover:underline">返回登录</NuxtLink>
        </p>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
const { post } = useApi()

const form = reactive({
  phone: '',
  code: '',
  newPassword: '',
  confirm: ''
})

const loading = ref(false)
const sending = ref(false)
const error = ref('')
const done = ref(false)
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

async function sendCode() {
  if (countdown.value > 0 || sending.value) return
  error.value = ''
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    error.value = '请先填写正确的手机号'
    return
  }
  sending.value = true
  try {
    // scene=reset：验证码只能用于重置密码，不能拿去登录或注册
    const res: any = await post('/auth/sms/send', { phone: form.phone, scene: 'reset' })
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

async function doReset() {
  error.value = ''
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    error.value = '请输入正确的手机号'
    return
  }
  if (!/^\d{6}$/.test(form.code)) {
    error.value = '请输入 6 位短信验证码'
    return
  }
  if (form.newPassword.length < 6 || form.newPassword.length > 30) {
    error.value = '新密码长度需为 6-30 位'
    return
  }
  if (form.newPassword !== form.confirm) {
    error.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    const res: any = await post('/auth/password/reset', {
      phone: form.phone,
      code: form.code,
      newPassword: form.newPassword
    })
    if (res?.code !== 200) {
      error.value = res?.message || '重置失败'
      return
    }
    done.value = true
  } catch (e: any) {
    error.value = e?.message || '重置失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
