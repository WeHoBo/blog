<template>
  <div class="max-w-2xl mx-auto">
    <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-8">个人中心</h1>

    <div v-if="!current.username" class="text-center py-10 text-gray-400">加载中...</div>
    <template v-else>
      <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm p-8 space-y-6">
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

      <!-- 账号安全 ----------------------------------------------------
           存在的意义：OAuth 自动建号的老用户在这里补齐「可用密码」与「手机号」，
           补完之后即使第三方登录哪天不可用，也不至于进不来。 -->
      <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm p-8 mt-6">
        <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 mb-1">账号安全</h2>
        <p class="text-xs text-gray-400 mb-4">补齐这两项之后，账号就有了独立的登录凭证，不再依赖第三方平台。</p>

        <div class="divide-y divide-gray-100 dark:divide-gray-700">
          <div class="flex items-start justify-between gap-4 py-4">
            <div class="min-w-0">
              <div class="text-sm text-gray-800 dark:text-gray-200">登录密码</div>
              <div class="text-xs mt-1" :class="current.passwordSet ? 'text-gray-400' : 'text-amber-600 dark:text-amber-500'">
                {{ current.passwordSet ? '已设置，可用于密码登录' : '未设置 —— 第三方注册的账号没有可用密码，建议现在设置' }}
              </div>
            </div>
            <button type="button" @click="openPwdDialog"
              class="px-3 py-1.5 rounded-lg border border-gray-200 dark:border-gray-600 text-xs whitespace-nowrap text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              {{ current.passwordSet ? '修改密码' : '设置密码' }}
            </button>
          </div>

          <div class="flex items-start justify-between gap-4 py-4">
            <div class="min-w-0">
              <div class="text-sm text-gray-800 dark:text-gray-200">手机号</div>
              <div class="text-xs mt-1" :class="current.phone ? 'text-gray-400' : 'text-amber-600 dark:text-amber-500'">
                {{ current.phone ? maskPhone(current.phone) : '未绑定 —— 无法用验证码登录，也无法自助找回密码' }}
              </div>
            </div>
            <button type="button" @click="openPhoneDialog"
              class="px-3 py-1.5 rounded-lg border border-gray-200 dark:border-gray-600 text-xs whitespace-nowrap text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
              {{ current.phone ? '更换手机号' : '立即绑定' }}
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- 设置 / 修改密码 -->
    <div v-if="pwdVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4" @click.self="pwdVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 w-full max-w-sm shadow-xl">
        <h3 class="text-base font-bold text-gray-900 dark:text-gray-100 mb-4">
          {{ current.passwordSet ? '修改密码' : '设置密码' }}
        </h3>

        <div class="space-y-3">
          <div v-if="current.passwordSet">
            <label class="text-xs text-gray-500 mb-1 block">原密码</label>
            <input v-model="pwdForm.oldPassword" type="password" autocomplete="current-password"
              class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">新密码</label>
            <input v-model="pwdForm.newPassword" type="password" autocomplete="new-password" placeholder="6-30 位"
              class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">确认新密码</label>
            <input v-model="pwdForm.confirm" type="password" autocomplete="new-password"
              class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <p v-if="pwdError" class="text-xs text-red-500">{{ pwdError }}</p>
        </div>

        <div class="flex gap-2 mt-5">
          <button type="button" @click="pwdVisible = false"
            class="flex-1 py-2 rounded-lg border border-gray-200 dark:border-gray-600 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">取消</button>
          <button type="button" @click="submitPwd" :disabled="pwdBusy"
            class="flex-1 py-2 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-90 disabled:opacity-50 transition">
            {{ pwdBusy ? '提交中' : '确定' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 绑定 / 更换手机号 -->
    <div v-if="phoneVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4" @click.self="phoneVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-2xl p-6 w-full max-w-sm shadow-xl">
        <h3 class="text-base font-bold text-gray-900 dark:text-gray-100 mb-4">
          {{ current.phone ? '更换手机号' : '绑定手机号' }}
        </h3>

        <div class="space-y-3">
          <div>
            <label class="text-xs text-gray-500 mb-1 block">手机号</label>
            <input v-model.trim="phoneForm.phone" type="tel" inputmode="numeric" maxlength="11"
              class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">短信验证码</label>
            <div class="flex gap-2">
              <input v-model.trim="phoneForm.code" type="text" inputmode="numeric" maxlength="6"
                class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
              <button type="button" @click="sendBindCode" :disabled="phoneCountdown > 0 || phoneSending"
                class="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-600 text-xs whitespace-nowrap text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition">
                {{ phoneCountdown > 0 ? `${phoneCountdown} 秒` : (phoneSending ? '发送中' : '获取验证码') }}
              </button>
            </div>
          </div>
          <p v-if="phoneError" class="text-xs text-red-500">{{ phoneError }}</p>
        </div>

        <div class="flex gap-2 mt-5">
          <button type="button" @click="phoneVisible = false"
            class="flex-1 py-2 rounded-lg border border-gray-200 dark:border-gray-600 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">取消</button>
          <button type="button" @click="submitPhone" :disabled="phoneBusy"
            class="flex-1 py-2 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-90 disabled:opacity-50 transition">
            {{ phoneBusy ? '提交中' : '确定' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'auth' })
import { ossImage, IMG_W } from '~/utils/image'

const authStore = useAuthStore()
const { get, put, post } = useApi()
// 注意重命名：本页的 `uploadAvatar` 是「选文件后上传」的处理器，避免与上传方法同名
const { uploadAvatar: uploadAvatarApi } = useUpload()
const { toast } = useFeedback()

/* 表单值必须来自**接口**，不能只读 localStorage：
   本地缓存可能缺字段（bio 是后加的），也可能是别的设备上改过的旧值。
   只读缓存的表现就是「简介显示为空，随便改一格再保存才把原值带出来」——
   这和后台站点设置踩过的是同一个坑，所以同样用 `await useAsyncData` 让真值进 SSR payload。 */
const { data: me, refresh: refreshMe } = await useAsyncData('profile-me', async () => {
  try {
    const res = await get<any>('/auth/me')
    return res?.code === 200 ? res.data : null
  } catch {
    return null
  }
})

const current = computed<any>(() => me.value || authStore.user || {})

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
      role: current.value.role,
      phone: current.value.phone ?? null,
      passwordSet: !!current.value.passwordSet
    })
    msg.value = '保存成功'; msgType.value = 'ok'
  } catch (e: any) {
    msg.value = e.message || '保存失败'; msgType.value = 'err'
  } finally {
    saving.value = false
  }
}

// ---------------- 账号安全：设置密码 ----------------

const pwdVisible = ref(false)
const pwdBusy = ref(false)
const pwdError = ref('')
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

function openPwdDialog() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirm = ''
  pwdError.value = ''
  pwdVisible.value = true
}

async function submitPwd() {
  pwdError.value = ''
  if (pwdForm.newPassword.length < 6 || pwdForm.newPassword.length > 30) {
    pwdError.value = '新密码长度需为 6-30 位'
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    pwdError.value = '两次输入的密码不一致'
    return
  }
  pwdBusy.value = true
  try {
    const res: any = await put('/auth/password', {
      // 没设过密码的账号没有"原密码"这个概念，不传即可
      oldPassword: current.value.passwordSet ? pwdForm.oldPassword : undefined,
      newPassword: pwdForm.newPassword
    })
    if (res?.code !== 200) {
      pwdError.value = res?.message || '设置失败'
      return
    }
    pwdVisible.value = false
    toast('密码已保存，下次可以用用户名 + 密码登录', 'success')
    await refreshMe()
    syncStoreFromMe()
  } catch (e: any) {
    pwdError.value = e?.message || '设置失败'
  } finally {
    pwdBusy.value = false
  }
}

// ---------------- 账号安全：绑定手机 ----------------

const phoneVisible = ref(false)
const phoneBusy = ref(false)
const phoneSending = ref(false)
const phoneError = ref('')
const phoneCountdown = ref(0)
const phoneForm = reactive({ phone: '', code: '' })
let phoneTimer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (phoneTimer) clearInterval(phoneTimer)
})

function openPhoneDialog() {
  phoneForm.phone = ''
  phoneForm.code = ''
  phoneError.value = ''
  phoneVisible.value = true
}

function startPhoneCountdown() {
  phoneCountdown.value = 60
  if (phoneTimer) clearInterval(phoneTimer)
  phoneTimer = setInterval(() => {
    phoneCountdown.value -= 1
    if (phoneCountdown.value <= 0 && phoneTimer) {
      clearInterval(phoneTimer)
      phoneTimer = null
    }
  }, 1000)
}

async function sendBindCode() {
  if (phoneCountdown.value > 0 || phoneSending.value) return
  phoneError.value = ''
  if (!/^1[3-9]\d{9}$/.test(phoneForm.phone)) {
    phoneError.value = '请输入正确的手机号'
    return
  }
  phoneSending.value = true
  try {
    const res: any = await post('/auth/sms/send', { phone: phoneForm.phone, scene: 'bind' })
    if (res?.code !== 200) {
      phoneError.value = res?.message || '发送失败'
      return
    }
    startPhoneCountdown()
  } catch (e: any) {
    phoneError.value = e?.message || '发送失败'
  } finally {
    phoneSending.value = false
  }
}

async function submitPhone() {
  phoneError.value = ''
  if (!/^1[3-9]\d{9}$/.test(phoneForm.phone)) {
    phoneError.value = '请输入正确的手机号'
    return
  }
  if (!/^\d{6}$/.test(phoneForm.code)) {
    phoneError.value = '请输入 6 位验证码'
    return
  }
  phoneBusy.value = true
  try {
    const res: any = await post('/auth/phone/bind', { phone: phoneForm.phone, code: phoneForm.code })
    if (res?.code !== 200) {
      phoneError.value = res?.message || '绑定失败'
      return
    }
    phoneVisible.value = false
    toast('手机号已绑定', 'success')
    await refreshMe()
    syncStoreFromMe()
  } catch (e: any) {
    phoneError.value = e?.message || '绑定失败'
  } finally {
    phoneBusy.value = false
  }
}

/**
 * 把接口最新值同步进 localStorage。
 * 不刷新页面，但 store 里的 phone/passwordSet 要跟着变，
 * 否则「刚绑定完，页面还显示未绑定」。
 */
function syncStoreFromMe() {
  const v = me.value as any
  if (!v) return
  authStore.setAuth(authStore.token || '', {
    userId: v.userId,
    username: v.username,
    nickname: v.nickname,
    avatar: v.avatar,
    bio: v.bio,
    role: v.role,
    phone: v.phone ?? null,
    passwordSet: !!v.passwordSet
  })
}

/** 138****8000 */
function maskPhone(phone?: string | null) {
  if (!phone || phone.length < 7) return phone || ''
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}
</script>
