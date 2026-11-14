<template>
  <div class="max-w-2xl">
    <h1 class="text-2xl font-bold mb-1">站点设置</h1>
    <p class="text-xs text-gray-400 mb-6">这里的改动保存后会立即清掉页面缓存，回首页刷新一次就能看到。</p>

    <!-- 基础信息 -->
    <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 space-y-4 mb-4">
      <h2 class="text-sm font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
        <span class="w-1 h-4 bg-primary-600 rounded-full"></span>基础信息
      </h2>
      <div>
        <label class="field-label" for="set-siteName">站点名称</label>
        <input id="set-siteName" v-model="form.siteName" autocomplete="off" class="field" placeholder="博观" />
      </div>
      <div>
        <label class="field-label" for="set-siteDesc">站点描述</label>
        <input id="set-siteDesc" v-model="form.siteDesc" autocomplete="off" class="field" />
        <p class="hint">用于 SEO 描述、以及首页横幅副标题的兜底文案。</p>
      </div>
    </section>

    <!-- 首页横幅 -->
    <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 space-y-4 mb-4">
      <h2 class="text-sm font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
        <span class="w-1 h-4 bg-primary-600 rounded-full"></span>首页横幅
      </h2>
      <div>
        <label class="field-label" for="set-bloggerName">博主名称</label>
        <input id="set-bloggerName" v-model="form.bloggerName" autocomplete="off" class="field" placeholder="显示在「你好，我是 ___」里" />
      </div>
      <div>
        <label class="field-label" for="set-bloggerBio">博主简介</label>
        <textarea id="set-bloggerBio" v-model="form.bloggerBio" rows="2" autocomplete="off" class="field"
          placeholder="📝 记录技术成长&#10;💻 分享编程心得"></textarea>
        <p class="hint">横幅只取第一行；首页右栏的小卡片用全部行。</p>
      </div>
      <div>
        <label class="field-label" for="set-bloggerFocus">关注方向</label>
        <input id="set-bloggerFocus" v-model="form.bloggerFocus" autocomplete="off" class="field" placeholder="Java,AI,Python,Linux,Docker" />
        <p class="hint">用英文逗号分隔，横幅上渲染成一个个小胶囊。最多显示 6 个。</p>
      </div>
      <div>
        <label class="field-label" for="set-bgImage">自定义背景图</label>
        <div class="flex gap-2">
          <input id="set-bgImage" v-model="form.bgImage" autocomplete="off" placeholder="背景图片URL" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <label class="px-3 py-2 border-2 border-dashed rounded-lg text-xs cursor-pointer hover:border-primary-500 hover:text-primary-500 whitespace-nowrap flex items-center">
            <input type="file" accept="image/*" class="hidden" @change="uploadBg" />
            {{ bgUploading ? '上传中...' : '上传' }}
          </label>
        </div>
        <button v-if="form.bgImage" @click="form.bgImage = ''" class="text-xs text-red-500 hover:underline mt-1">清除背景</button>
      </div>

      <!-- 头像：统一用站点 logo。原来这里有个「博主头像URL」输入框，
           但前台同时存在「站点头像」和「登录用户头像」两套来源，
           导致首页横幅和侧栏卡片显示的不是同一张图（用户第一反应是「谁写死的」）。
           现在只剩一个来源：站点 logo 文件。换 logo 换的就是全站头像。 -->
      <div class="flex items-start gap-3 rounded-lg bg-gray-50 dark:bg-white/5 p-3">
        <img src="/logo.svg" alt="站点 logo" class="w-10 h-10 rounded-lg p-1 ring-1 ring-gray-300/70 dark:ring-gray-600 shrink-0"
          style="background: var(--page-bg, #F7F2E8)" />
        <p class="text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
          头像统一使用站点 Logo（<code class="text-[11px]">public/logo.svg</code>），换 logo 即换全站头像。
          <br />需要换图时执行 <code class="text-[11px]">node scripts/gen-brand-assets.mjs</code> 重新生成各尺寸。
        </p>
      </div>
    </section>

    <!-- 关于页 -->
    <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 space-y-4 mb-4">
      <h2 class="text-sm font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
        <span class="w-1 h-4 bg-primary-600 rounded-full"></span>关于页
      </h2>
      <div>
        <label class="field-label" for="set-aboutContent">关于页内容（Markdown）</label>
        <textarea id="set-aboutContent" v-model="form.aboutContent" rows="10" autocomplete="off"
          class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm font-mono"></textarea>
      </div>
    </section>

    <!-- AI 助手 -->
    <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 space-y-4 mb-4">
      <h2 class="text-sm font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
        <span class="w-1 h-4 bg-primary-600 rounded-full"></span>AI 助手
      </h2>
      <div class="grid sm:grid-cols-2 gap-4">
        <div>
          <label class="field-label" for="set-aiName">AI 名字</label>
          <input id="set-aiName" v-model="form.aiName" autocomplete="off" class="field" placeholder="博观 AI" />
        </div>
        <div>
          <label class="field-label" for="set-aiGreeting">欢迎语</label>
          <input id="set-aiGreeting" v-model="form.aiGreeting" autocomplete="off" class="field" placeholder="我可以根据本站的文章帮你解答。" />
        </div>
      </div>

      <div>
        <div class="flex items-center justify-between mb-2">
          <label class="field-label mb-0">问题池</label>
          <span class="text-[11px] text-gray-400">
            共 {{ questions.length }} 条 · 前台一次显示 {{ SHOW_COUNT }} 条
          </span>
        </div>
        <div class="space-y-2">
          <div v-for="(q, i) in questions" :key="i" class="flex items-center gap-2">
            <span class="text-[11px] text-gray-300 dark:text-gray-600 w-5 text-right tabular-nums shrink-0">{{ i + 1 }}</span>
            <input v-model="questions[i]" autocomplete="off" class="field" placeholder="例如：站内有哪些「操作系统」的文章？" />
            <button @click="removeQuestion(i)" class="w-8 h-8 shrink-0 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition flex items-center justify-center" title="删除这条" aria-label="删除这条">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M18 6 6 18M6 6l12 12"/></svg>
            </button>
          </div>
          <p v-if="!questions.length" class="text-xs text-gray-400 py-2">
            问题池为空时，前台会按站内最新文章自动生成 —— 不填也能用。
          </p>
        </div>
        <button @click="addQuestion" class="mt-3 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-dashed text-xs text-gray-500 hover:border-primary-400 hover:text-primary-600 transition">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>
          添加问题
        </button>
        <p class="hint mt-2">
          前台点「换一批」时优先抽没出现过的；池子不足 {{ SHOW_COUNT }} 条时会允许重复，只打乱顺序。
        </p>
      </div>
    </section>

    <!-- 维护 -->
    <section class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 mb-4">
      <h2 class="text-sm font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2 mb-3">
        <span class="w-1 h-4 bg-primary-600 rounded-full"></span>维护
      </h2>
      <div class="flex items-center justify-between gap-4">
        <p class="text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
          首页等公开页有 10–60 秒的页面缓存（省服务器）。保存本页会自动清一次；
          手改数据库或 RAG 内容后可以在这里手动清。
        </p>
        <button @click="clearCache" :disabled="purging"
          class="shrink-0 px-4 py-2 rounded-lg border text-xs text-gray-600 dark:text-gray-300 hover:border-primary-400 hover:text-primary-600 transition disabled:opacity-50">
          {{ purging ? '清理中…' : '清除页面缓存' }}
        </button>
      </div>
    </section>

    <div class="sticky bottom-4">
      <button @click="handleSave" :disabled="saving"
        class="px-6 py-2.5 bg-primary-600 text-white rounded-lg hover:bg-primary-700 text-sm transition disabled:opacity-50 shadow-sm">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SITE_CONFIG_KEY } from '~/composables/useSiteConfig'

definePageMeta({ middleware: 'admin', layout: 'admin' })
const { get, post } = useApi()
const { toast } = useFeedback()
const bgUploading = ref(false)
const saving = ref(false)
const purging = ref(false)
const { upload } = useUpload()
const purgeCache = usePurgeCache()

/** 前台一次显示几个问题（与 AiFloat 保持一致） */
const SHOW_COUNT = 4

/** 逗号分隔 → 去空去重 */
function parseFocus(raw: string): string {
  const parts = String(raw || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
  return [...new Set(parts)].slice(0, 6).join(',')
}

/** 问题池在库里存成 JSON 数组字符串；脏数据一律降级为空池，不让后台崩 */
function parseQuestions(raw: string | undefined): string[] {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return []
    return [...new Set(arr.map((s: any) => String(s || '').trim()).filter(Boolean))].slice(0, 30)
  } catch {
    return []
  }
}

/**
 * ⚠️ 这里必须 `await useAsyncData`，不能用「setup 里直接发请求」的写法。
 *
 * 原来的写法是 `fetchConfig()` 不 await：服务端渲染时表单还没拿到数据就把 HTML 吐出去了，
 * 页面一进来是一排空格子；等客户端的请求回来，Vue 只更新了数据、没把值写回已存在的
 * input DOM —— 表现就是「字段全空，但随便动一个格子，其它格子的原值才冒出来」。
 * 用 useAsyncData 的好处是：数据进 SSR payload，首屏 HTML 里就是真值，不存在这个时序。
 */
const { data: initial } = await useAsyncData('admin-site-config', async () => {
  try {
    const res = await get<any>('/site-config/public')
    return (res?.code === 200 ? res.data : {}) || {}
  } catch {
    return {}
  }
})

const cfg = (initial.value || {}) as Record<string, string>

const form = reactive({
  siteName: cfg.siteName || '',
  siteDesc: cfg.siteDesc || '',
  aboutContent: cfg.aboutContent || '',
  bgImage: cfg.bgImage || '',
  bloggerName: cfg.bloggerName || '',
  bloggerBio: cfg.bloggerBio || '',
  // 没配过就给一个默认值，省得每次都要自己想一遍填什么
  bloggerFocus: cfg.bloggerFocus || 'Java,AI,Python,Linux,Docker',
  aiName: cfg.aiName || '',
  aiGreeting: cfg.aiGreeting || ''
})

const questions = ref<string[]>(parseQuestions(cfg.aiQuestions))

function addQuestion() {
  if (questions.value.length >= 30) {
    toast('问题池最多 30 条', 'error')
    return
  }
  questions.value.push('')
}

function removeQuestion(i: number) {
  questions.value.splice(i, 1)
}

async function uploadBg(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  bgUploading.value = true
  try {
    form.bgImage = await upload(file)
    toast('上传成功', 'success')
  } catch (e: any) {
    toast('上传失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    bgUploading.value = false
    input.value = ''
  }
}

async function handleSave() {
  if (saving.value) return
  saving.value = true
  try {
    const pool = [...new Set(questions.value.map((s) => String(s || '').trim()).filter(Boolean))]
    const payload: Record<string, string> = {
      siteName: form.siteName.trim(),
      siteDesc: form.siteDesc.trim(),
      aboutContent: form.aboutContent,
      bgImage: form.bgImage,
      bloggerName: form.bloggerName.trim(),
      bloggerBio: form.bloggerBio,
      bloggerFocus: parseFocus(form.bloggerFocus),
      aiName: form.aiName.trim(),
      aiGreeting: form.aiGreeting.trim(),
      aiQuestions: JSON.stringify(pool)
    }
    const res = await post<{ code: number; message?: string }>('/site-config/save', payload)
    if (res?.code !== 200) {
      toast(res?.message || '保存失败', 'error')
      return
    }
    questions.value = pool
    // 站点配置是 useAsyncData 按 key 缓存的，不清掉的话 SPA 内切到首页还是旧文案
    clearNuxtData(SITE_CONFIG_KEY)
    await purgeCache()
    toast('保存成功', 'success')
  } catch (e: any) {
    toast(e.message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function clearCache() {
  if (purging.value) return
  purging.value = true
  try {
    const ok = await purgeCache()
    toast(ok ? '缓存已清除' : '清除失败，请重新登录后再试', ok ? 'success' : 'error')
  } finally {
    purging.value = false
  }
}
</script>

<style scoped>
.field-label {
  @apply text-sm text-gray-500 mb-1 block;
}
.field {
  @apply w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm;
}
.hint {
  @apply mt-1 text-[11px] text-gray-400 leading-relaxed;
}
</style>
