<template>
  <!--
    「侧栏」版首页。
    与默认版（三栏：左分类 / 中精品 / 右最新）的区别只有排版：
    导航从顶栏收进左竖栏，首屏用一句欢迎 + 四张数字卡开场，下面是文章列表。

    刻意保留的东西：
    - 数据来源与默认版完全一致（都是 index.vue 取好的那一份），不额外发请求；
    - 卡片仍然是 ArticleCard，换皮肤不会换掉「标题 / 摘要 / 时长」的信息层级。
  -->
  <div class="max-w-[1240px] mx-auto px-4 sm:px-6 py-6">
    <!-- 窄屏：竖栏撑不开，把导航压成一排胶囊放在最上面 -->
    <nav class="lg:hidden flex gap-1.5 overflow-x-auto pb-4 -mx-1 px-1">
      <NuxtLink
        v-for="n in nav"
        :key="n.to"
        :to="n.to"
        class="flex-shrink-0 px-3.5 py-2 rounded-full text-xs font-medium bg-white/80 dark:bg-gray-800/80 border border-gray-200/80 dark:border-gray-700 text-gray-600 dark:text-gray-300"
      >
        {{ n.label }}
      </NuxtLink>
    </nav>

    <div class="flex gap-6 items-start">
      <!-- 左竖栏 -->
      <aside class="hidden lg:block w-56 flex-shrink-0">
        <div class="sticky top-20 space-y-3">
          <!-- 个人信息卡：登录显示「我」，访客显示站点博主。
               和其它版式共用 index.vue 的 cardAuthor，两套内容互不回落。 -->
          <div class="rounded-2xl bg-white/85 dark:bg-gray-800/85 backdrop-blur border border-gray-200/80 dark:border-gray-700 p-5">
            <template v-if="author.pending">
              <div class="w-11 h-11 rounded-xl bg-gray-200 dark:bg-gray-700 animate-pulse"></div>
              <div class="mt-3 h-3 w-20 rounded bg-gray-200 dark:bg-gray-700 animate-pulse"></div>
              <div class="mt-2 h-3 w-full rounded bg-gray-100 dark:bg-gray-700/60 animate-pulse"></div>
            </template>
            <template v-else>
              <img v-if="author.avatar" :src="author.avatar" alt="" width="44" height="44" decoding="async"
                class="w-11 h-11 object-cover"
                :class="author.isMe ? 'rounded-full ring-1 ring-gray-300/70 dark:ring-gray-600' : 'rounded-xl object-contain'" />
              <div v-else class="w-11 h-11 rounded-full flex items-center justify-center text-base font-bold text-white bg-primary-600 dark:bg-primary-500">
                {{ author.initial }}
              </div>
              <div class="mt-3 text-sm font-bold text-gray-900 dark:text-gray-100 truncate">{{ author.name }}</div>
              <p v-if="author.bio" class="mt-1 text-[11px] leading-relaxed text-gray-500 dark:text-gray-400 line-clamp-3">{{ author.bio }}</p>
            </template>
            <!-- 链接放在分支外：骨架态（/auth/me 还没回来或 token 失效）也要能点进去 -->
            <NuxtLink :to="author.href" class="inline-block mt-2 text-[11px] text-primary-600 dark:text-primary-400 hover:underline">{{ author.action }}</NuxtLink>
          </div>

          <nav class="rounded-2xl bg-white/85 dark:bg-gray-800/85 backdrop-blur border border-gray-200/80 dark:border-gray-700 p-2">
            <NuxtLink
              v-for="n in nav"
              :key="n.to"
              :to="n.to"
              class="flex items-center gap-2.5 px-3 py-2 rounded-xl text-sm text-gray-600 dark:text-gray-300 hover:bg-primary-50 dark:hover:bg-primary-900/20 hover:text-primary-700 dark:hover:text-primary-300 transition"
            >
              <svg class="w-4 h-4 flex-shrink-0 opacity-70" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true"><path :d="n.d" /></svg>
              <span class="flex-1 min-w-0 truncate">{{ n.label }}</span>
            </NuxtLink>
          </nav>
        </div>
      </aside>

      <!-- 主区 -->
      <main class="flex-1 min-w-0">
        <slot name="results">
          <!-- 欢迎区 -->
          <section class="rounded-2xl bg-white/85 dark:bg-gray-800/85 backdrop-blur border border-gray-200/80 dark:border-gray-700 px-6 sm:px-8 py-7">
            <span class="theme-chip inline-flex items-center px-2.5 py-1 rounded-full text-[11px] tracking-[0.08em]">PERSONAL BLOG · {{ year }}</span>
            <h1 class="hero-title mt-3 text-[1.6rem] sm:text-[2.1rem] font-extrabold text-gray-900 dark:text-gray-100 leading-[1.25]">
              你好，我是{{ bloggerName }}。
            </h1>
            <p class="mt-2.5 text-sm sm:text-[15px] leading-relaxed text-gray-500 dark:text-gray-400 max-w-[42rem]">
              {{ bloggerBio || fallbackBio }}
            </p>
          </section>

          <!-- 数字卡：四格，手机上两列 -->
          <section class="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-3">
            <div v-for="s in stats" :key="s.label"
              class="rounded-2xl bg-white/85 dark:bg-gray-800/85 backdrop-blur border border-gray-200/80 dark:border-gray-700 px-4 py-3.5">
              <div class="text-xl font-extrabold tabular-nums text-gray-900 dark:text-gray-100">{{ s.value }}</div>
              <div class="mt-0.5 text-[11px] text-gray-500 dark:text-gray-400">{{ s.label }}</div>
            </div>
          </section>

          <!-- 文章列表 -->
          <section class="mt-5">
            <div class="flex items-baseline justify-between mb-3">
              <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                <span class="w-1 h-5 bg-primary-600 rounded-full"></span>
                文章
              </h2>
              <NuxtLink to="/archive" class="text-xs text-primary-600 dark:text-primary-400 hover:underline">全部归档 →</NuxtLink>
            </div>

            <div v-if="loading" class="space-y-3">
              <div v-for="i in 3" :key="i" class="rounded-2xl bg-white/70 dark:bg-gray-800/70 h-24 animate-pulse"></div>
            </div>

            <div v-else-if="!list.length && !error" class="rounded-2xl bg-white/85 dark:bg-gray-800/85 border border-gray-200/80 dark:border-gray-700 p-12 text-center">
              <p class="text-sm text-gray-500 dark:text-gray-400">还没有文章</p>
            </div>

            <div v-else class="space-y-3">
              <ArticleCard v-for="a in list" :key="a.id" :article="a" />
            </div>
          </section>
          <slot name="more" />
        </slot>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 「侧栏」版首页。数据全部由 pages/index.vue 传入 ——
 * 这里不发请求，也不自己判断角色：版式归版式，取数只在一个地方。
 */
const props = defineProps<{
  featured: any[]
  articles: any[]
  featuredTotal: number
  articleTotal: number
  error?: boolean
  categories: any[]
  tags: any[]
  loading?: boolean
  siteName: string
  siteDesc: string
  bloggerName: string
  bloggerBio: string
  /** 个人信息卡要显示的人（登录=自己 / 访客=站点博主），由 index.vue 的 cardAuthor 算好传入 */
  author: {
    pending: boolean; isMe: boolean; name: string; bio: string
    avatar: string; initial: string; href: string; action: string
  }
}>()

const fallbackBio = '一名持续探索计算机技术、AI 和知识世界的学习者。'

/** 年份在 SSR 与客户端同一秒内渲染，不会水合不一致 */
const year = new Date().getFullYear()

const nav = [
  { to: '/', label: '首页', d: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z' },
  { to: '/archive', label: '归档', d: 'M4 7h16M6 7V4h12v3M5 7v12a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V7M10 12h4' },
  { to: '/categories', label: '分类', d: 'M4 6a2 2 0 0 1 2-2h3l2 2h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z' },
  { to: '/tags', label: '标签', d: 'M20.6 13.4 12 22l-9-9V4h9l8.6 8.6a1 1 0 0 1 0 1.4zM7.5 7.5h.01' },
  { to: '/about', label: '关于', d: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 8h.01M11 12h1v5h1' }
]

/**
 * 列表 = 精品在前 + 其余按时间。
 * 精品在接口里是**被排除**在普通列表之外的（`featured=0`），
 * 不合并就会出现「勾了精品的文章从首页消失」。
 */
const list = computed(() => {
  const seen = new Set<number>()
  const out: any[] = []
  for (const a of [...props.featured, ...props.articles]) {
    if (!a || seen.has(a.id)) continue
    seen.add(a.id)
    out.push(a)
  }
  return out
})

const stats = computed(() => [
  { label: '篇文章', value: props.articleTotal },
  { label: '个分类', value: props.categories.length },
  { label: '个标签', value: props.tags.length },
  { label: '篇精品', value: props.featuredTotal }
])

/** 侧栏那句简介：站点描述一般较长，压到 40 字，多了 line-clamp 会切得很难看 */
const siteDescShort = computed(() => {
  const s = (props.siteDesc || '').trim()
  return s.length > 40 ? s.slice(0, 40) + '…' : s
})
</script>
