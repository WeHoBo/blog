<template>
  <!--
    「薄荷 / 晴空」版首页。
    与「侧栏」版的差别在气质：卡片更圆更大、留白更多、左侧多一张日历
    （日历上有点的日子 = 那天发过文章，点靠真实发布数据，不是装饰）。

    两套皮肤（mint / sky）共用这一份模板，区别只有纸面底色、背景图和主题色相 ——
    所以这里一个颜色都不写死，全部吃 `--page-bg` 与 Tailwind 的 primary-*。
  -->
  <div class="max-w-[1240px] mx-auto px-4 sm:px-6 py-6">
    <div class="flex gap-6 items-start">
      <!-- 左竖栏 -->
      <aside class="hidden lg:block w-60 flex-shrink-0">
        <div class="sticky top-20 space-y-3">
          <!-- 作者卡：登录显示「我」，访客显示站点博主。两套内容互不回落 ——
               否则普通用户没填简介时会看到站长那份（这个坑踩过两次）。
               数据由 index.vue 的 cardAuthor 统一算好，这里只管画。 -->
          <div class="rounded-3xl bg-white/90 dark:bg-gray-800/85 backdrop-blur border border-white/70 dark:border-gray-700 px-5 py-6 text-center shadow-[0_10px_30px_-18px_rgba(0,0,0,0.25)]">
            <template v-if="author.pending">
              <div class="w-14 h-14 mx-auto rounded-2xl bg-gray-200 dark:bg-gray-700 animate-pulse"></div>
              <div class="mt-3 h-3 w-20 mx-auto rounded bg-gray-200 dark:bg-gray-700 animate-pulse"></div>
              <div class="mt-2 h-3 w-full rounded bg-gray-100 dark:bg-gray-700/60 animate-pulse"></div>
            </template>
            <template v-else>
              <img v-if="author.avatar" :src="author.avatar" alt="" width="56" height="56" decoding="async"
                class="w-14 h-14 mx-auto object-cover"
                :class="author.isMe ? 'rounded-full ring-1 ring-gray-300/70 dark:ring-gray-600' : 'rounded-2xl object-contain'" />
              <div v-else class="w-14 h-14 mx-auto rounded-full flex items-center justify-center text-xl font-bold text-white bg-primary-600 dark:bg-primary-500">
                {{ author.initial }}
              </div>
              <div class="mt-3 text-sm font-bold text-gray-900 dark:text-gray-100 truncate">{{ author.name }}</div>
              <p v-if="author.bio" class="mt-1.5 text-[11px] leading-relaxed text-gray-500 dark:text-gray-400 line-clamp-4">{{ author.bio }}</p>
            </template>
            <!-- 链接放在分支外：骨架态（/auth/me 还没回来或 token 失效）也要能点进去 -->
            <NuxtLink :to="author.href" class="inline-block mt-2 text-[11px] text-primary-600 dark:text-primary-400 hover:underline">{{ author.action }}</NuxtLink>
          </div>

          <ContributionCalendar v-if="!$slots.results" />
        </div>
      </aside>

      <!-- 主区 -->
      <main class="flex-1 min-w-0">
        <slot name="results">
          <header class="flex flex-wrap items-end justify-between gap-3 mb-4">
            <div class="min-w-0">
              <h1 class="hero-title text-[1.5rem] sm:text-[1.9rem] font-extrabold text-gray-900 dark:text-gray-100 leading-tight">
                最近写了什么
              </h1>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                共 {{ articleTotal }} 篇 · 更新到 {{ latestDate || '—' }}
              </p>
            </div>
            <NuxtLink to="/archive"
              class="inline-flex items-center gap-1 px-3.5 py-1.5 rounded-full bg-white/90 dark:bg-gray-800/85 border border-white/70 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 transition">
              全部归档
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
            </NuxtLink>
          </header>

          <div v-if="loading" class="space-y-3">
            <div v-for="i in 3" :key="i" class="rounded-3xl bg-white/70 dark:bg-gray-800/70 h-28 animate-pulse"></div>
          </div>

          <div v-else-if="!list.length && !error" class="rounded-3xl bg-white/90 dark:bg-gray-800/85 border border-white/70 dark:border-gray-700 p-12 text-center">
            <p class="text-sm text-gray-500 dark:text-gray-400">还没有文章</p>
          </div>

          <div v-else class="space-y-3">
            <ArticleCard v-for="a in list" :key="a.id" :article="a" />
          </div>
          <slot name="more" />
        </slot>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 「薄荷 / 晴空」版首页。数据同样全部由 pages/index.vue 传入。
 * 图标、颜色一律走主题变量：换皮肤只换底色与色相，模板不动。
 */
const props = defineProps<{
  featured: any[]
  articles: any[]
  featuredTotal: number
  articleTotal: number
  error?: boolean
  loading?: boolean
  bloggerName: string
  bloggerBio: string
  /** 作者卡要显示的人（登录=自己 / 访客=站点博主），由 index.vue 的 cardAuthor 算好传入 */
  author: {
    pending: boolean; isMe: boolean; name: string; bio: string
    avatar: string; initial: string; href: string; action: string
  }
}>()

const fallbackBio = '一名持续探索计算机技术、AI 和知识世界的学习者。'

/* ---------------- 列表 ---------------- */
/** 精品 + 普通合并去重：精品在接口里被排除在普通列表之外，不合并会「从首页消失」 */
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

/** 最近发布日期：取前 10 位，纯字符串操作，避免时区问题 */
const latestDate = computed(() => {
  let max = ''
  for (const a of list.value) {
    const raw = String(a?.publishAt || a?.createTime || '').slice(0, 10)
    if (raw > max) max = raw
  }
  return max
})
</script>
