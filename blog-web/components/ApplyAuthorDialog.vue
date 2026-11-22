<template>
  <Teleport to="body">
    <Transition name="apply-fade">
      <div v-if="open" class="fixed inset-0 z-[90] flex items-center justify-center px-4 py-6 bg-black/45"
        @click.self="close">
        <div class="w-full max-w-lg max-h-[86vh] overflow-y-auto rounded-2xl bg-white dark:bg-gray-800
                    border border-gray-200 dark:border-gray-700 shadow-paper" role="dialog" aria-modal="true"
          aria-labelledby="apply-title">
          <!-- 头 -->
          <div class="flex items-start justify-between gap-4 px-6 pt-5 pb-3">
            <div class="min-w-0">
              <h2 id="apply-title" class="text-lg font-bold text-gray-900 dark:text-gray-100">
                申请发布文章
              </h2>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
                答对 <b class="text-primary-600 dark:text-primary-400">{{ passCount }}</b> 题即可获得作者权限：
                可以提交文章、修改自己的文章，发布前由站长审核。
              </p>
            </div>
            <button type="button" aria-label="关闭" class="shrink-0 w-8 h-8 -mt-1 -mr-2 rounded-full
                     text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100
                     dark:hover:bg-gray-700 transition" @click="close">
              <svg class="w-4 h-4 mx-auto" fill="none" stroke="currentColor" stroke-width="1.6"
                viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="px-6 py-10 text-center text-sm text-gray-400">正在取题…</div>

          <!-- 出错 / 不可用 -->
          <div v-else-if="errorText" class="px-6 pb-6">
            <p class="text-sm text-amber-600 dark:text-amber-400">{{ errorText }}</p>
            <div class="mt-4 flex justify-end">
              <button class="px-4 py-2 rounded-lg border text-sm dark:border-gray-600" @click="close">知道了</button>
            </div>
          </div>

          <!-- 已经通过 -->
          <div v-else-if="passed" class="px-6 pb-6">
            <div class="rounded-xl bg-primary-50 dark:bg-primary-900/20 px-4 py-4">
              <p class="text-sm font-medium text-primary-700 dark:text-primary-300">
                恭喜，你已获得发布权限
              </p>
              <p class="mt-1 text-xs text-primary-600/80 dark:text-primary-400/80">
                右上角「我的文章」里可以开始写了。提交后我会尽快审核。
              </p>
            </div>
            <div class="mt-4 flex justify-end gap-2">
              <button class="px-4 py-2 rounded-lg border text-sm dark:border-gray-600" @click="close">稍后</button>
              <NuxtLink to="/author"
                class="px-4 py-2 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-85 transition"
                @click="close">
                去我的文章
              </NuxtLink>
            </div>
          </div>

          <!-- 题目 -->
          <form v-else class="px-6 pb-5" @submit.prevent="submit">
            <div v-for="(q, qi) in questions" :key="q.id" class="mb-4 last:mb-0">
              <p class="text-sm font-medium text-gray-800 dark:text-gray-200 leading-snug">
                <span class="text-gray-400 mr-1">{{ qi + 1 }}.</span>{{ q.question }}
              </p>
              <div class="mt-2 space-y-1.5">
                <label v-for="(opt, oi) in q.options" :key="oi"
                  class="flex items-start gap-2 px-3 py-2 rounded-lg border text-sm cursor-pointer transition"
                  :class="answers[q.id] === oi
                    ? 'border-primary-400 bg-primary-50/70 dark:bg-primary-900/20 text-gray-900 dark:text-gray-100'
                    : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'">
                  <input type="radio" :name="'q-' + q.id" :value="oi" v-model.number="answers[q.id]"
                    class="mt-[3px] accent-current shrink-0" />
                  <span class="min-w-0 break-words">{{ opt }}</span>
                </label>
              </div>
            </div>

            <p v-if="formError" class="mt-1 text-xs text-red-500">{{ formError }}</p>

            <div class="mt-5 flex items-center justify-between gap-3">
              <span class="text-[11px] text-gray-400">
                <template v-if="attemptsLeft > 0">今天还剩 {{ attemptsLeft }} 次机会</template>
                <template v-else>今天的次数已用完</template>
              </span>
              <div class="flex gap-2">
                <button type="button" class="px-4 py-2 rounded-lg border text-sm dark:border-gray-600"
                  @click="close">取消</button>
                <button type="submit" :disabled="submitting || !complete || attemptsLeft <= 0"
                  class="px-4 py-2 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium
                         hover:opacity-85 disabled:opacity-40 transition">
                  {{ submitting ? '提交中…' : '提交答卷' }}
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
/**
 * 「申请管理员发布文章」弹窗。
 *
 * ⚠️ 判分完全在服务端做：这里的 questions 里**没有正确答案**，
 * 所以本组件不承担任何"对错判断"，只负责收集选择并把 id->选项 传上去。
 * 这也是为什么它敢放在公开首页上 —— 打开网络面板也看不到答案。
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [boolean] }>()

const { get, post } = useApi()
const { toast } = useFeedback()
const authStore = useAuthStore()

const loading = ref(false)
const submitting = ref(false)
const errorText = ref('')
const formError = ref('')
const passed = ref(false)
const questions = ref<{ id: number; question: string; options: string[] }[]>([])
const passCount = ref(1)
const attemptsLeft = ref(0)
const answers = reactive<Record<number, number>>({})

/** 每道题都选了这个名字才能交：少选一道就不让点，比交上去再报错友好 */
const complete = computed(() =>
  questions.value.length > 0 && questions.value.every(q => answers[q.id] !== undefined))

function close() {
  emit('update:open', false)
}

async function load() {
  loading.value = true
  errorText.value = ''
  formError.value = ''
  passed.value = false
  questions.value = []
  Object.keys(answers).forEach(k => delete answers[Number(k)])
  try {
    const res = await get<any>('/apply/info')
    if (res?.code !== 200) {
      errorText.value = res?.message || '申请通道暂不可用'
      return
    }
    const d = res.data || {}
    if (d.alreadyAuthor) {
      // 已经有权限的人再点进来，没必要看题
      passed.value = true
      return
    }
    if (d.enabled === false) {
      errorText.value = '申请通道暂未开放，晚点再来吧'
      return
    }
    if (!d.questions?.length) {
      errorText.value = '站长还没有配置题目，稍后再试'
      return
    }
    questions.value = d.questions
    passCount.value = d.passCount || 1
    attemptsLeft.value = d.attemptsLeft ?? 0
  } catch (e: any) {
    errorText.value = e?.message || '加载失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (submitting.value || !complete.value) return
  submitting.value = true
  formError.value = ''
  try {
    const res = await post<any>('/apply/submit', { answers })
    if (res?.code !== 200) {
      formError.value = res?.message || '提交失败'
      return
    }
    const d = res.data || {}
    attemptsLeft.value = d.attemptsLeft ?? attemptsLeft.value
    if (!d.passed) {
      // 答错不关门：题目留着、选项留着，让人看到分数再改
      formError.value = d.message || `答对 ${d.score}/${d.total} 题`
      return
    }

    // 通过：必须用服务端新签发的 token 换掉旧的。
    // role 写在 JWT claim 里，不换 token 的话旧 token 仍然是 user，
    // 刷新页面也会一直 403 —— 看起来就像"通过了但没生效"。
    if (d.token) {
      let base = authStore.user as any
      if (!base) {
        // 极端情况：cookie 里有 token 但 store 还没填（比如直接打开弹窗）
        try {
          const meRes = await get<any>('/auth/me')
          base = meRes?.code === 200 ? meRes.data : null
        } catch { /* 拿不到就只换 token */ }
      }
      if (base) authStore.setAuth(d.token, { ...base, role: 'author' })
      else authStore.token = d.token
    }
    passed.value = true
    toast('已获得发布权限', 'success')
  } catch (e: any) {
    formError.value = e?.message || '提交失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

watch(() => props.open, (v) => {
  if (v) load()
})
</script>

<style scoped>
.apply-fade-enter-active,
.apply-fade-leave-active {
  transition: opacity 0.18s ease;
}
.apply-fade-enter-from,
.apply-fade-leave-to {
  opacity: 0;
}
</style>
