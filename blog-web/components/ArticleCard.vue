<template>
  <NuxtLink :to="articlePath(article)"
    class="article-card glass-solid relative block rounded-xl border transition-all duration-200 overflow-hidden group hover:-translate-y-0.5 hover:shadow-md"
    :class="[layout === 'grid' ? 'flex flex-col h-full' : '',
             article.isFeatured
               ? 'article-card--featured'
               : 'hover:border-gray-300 dark:hover:border-gray-600']">

    <!-- 精品标记：顶部 2px 主题色细线。
         只靠标题前那个小徽章，一屏扫下来和普通文章几乎没差别；
         加一道通栏细线之后，「这是精选」在余光里就能读到，而且不抢正文。
         右栏「最新」用的是 ArticleRow（紧凑行），没有这条线 —— 两边天然区分。 -->
    <span v-if="article.isFeatured" class="absolute inset-x-0 top-0 h-[2px] bg-primary-600/55 z-10" aria-hidden="true"></span>
    <!-- 网格模式：顶部插图 + 下方信息 -->
    <template v-if="layout === 'grid'">
      <!-- 封面比例由 ratio 决定：默认 16:9；首页右栏「全部文章」用 2:1（更扁，窄栏里不显笨重） -->
      <!-- 有封面才占图区。没封面时整块不渲染 —— 一个文件夹图标既不是内容也不是装饰，
           只是把标题往下推了上百像素，读者得滚动才看到真正想读的那行字。 -->
      <div v-if="article.cover" class="overflow-hidden bg-gray-100 dark:bg-gray-700 flex-shrink-0" :class="coverAspect">
        <img :src="ossImage(article.cover, { w: IMG_W.card })" :alt="article.title" loading="lazy" decoding="async"
          class="w-full h-full object-cover group-hover:scale-[1.03] transition-transform duration-500" />
      </div>
      <div class="p-4 flex-1 flex flex-col">
        <!-- 网格卡先给分类：无封面的卡直接从「分类 + 标题」起排，
             读者扫一列卡片时先靠分类定位，再读标题，比一上来就是标题更快。 -->
        <span v-if="article.categoryName" class="text-[11px] font-medium theme-text mb-1.5">{{ article.categoryName }}</span>
        <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100 line-clamp-2 title-hover transition-colors leading-snug">
          <span v-if="article.isFeatured" class="inline-block text-[10px] bg-primary-600/10 text-primary-700 dark:bg-primary-400/15 dark:text-primary-300 border border-primary-300/60 dark:border-primary-500/40 px-1.5 py-0.5 rounded mr-1.5 align-middle font-medium">精品</span><span v-if="article.isTop" class="inline-block text-[10px] bg-primary-600 text-white px-1.5 py-0.5 rounded mr-1.5 align-middle font-medium">置顶</span><span v-html="hlTitle"></span>
        </h2>
        <p v-if="article.summary" class="mt-1.5 text-sm text-gray-500 dark:text-gray-400 line-clamp-1 leading-relaxed" v-html="hlSummary"></p>
        <div class="mt-3 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400 mt-auto pt-3">
          <span>{{ shownDate }}</span>
          <ReadingTime :word-count="article.wordCount || 0" />
        </div>
      </div>
    </template>

    <!-- 列表模式：左侧信息 + 右侧插图（按 2026-11-24 设计稿重排）
         信息顺序：分类 + 日期 → 标题 → 摘要两行 → 分隔线 → 时长·字数 / 阅读全文。
         图1 那张设计稿把「分类」提到最上面一行左右两端（左分类、右日期）：
         扫一列卡片时先靠分类判断「这块是不是我要的」，再读标题，比反过来快。 -->
    <template v-else>
      <div class="flex items-stretch">
        <div class="flex-1 min-w-0 p-5 flex flex-col">

          <!-- 顶行：分类胶囊（左） + 日期（右） -->
          <div class="flex items-center justify-between gap-3 mb-2.5">
            <!-- 分类胶囊用主题色（低饱和底 + 深主题字）：全站分类链接本来就是主题色，
                 这里换成中性灰会和「未建档」看不出区别，也丢了「分类=导航」的暗示。
                 必须带 min-w-0：flex 子项默认 min-width:auto，光写 truncate 不会收缩，
                 分类名一长就把右边的日期挤出卡片。 -->
            <span class="inline-flex items-center min-w-0 text-[11px] font-medium px-2 py-0.5 rounded bg-primary-50 dark:bg-primary-900/25 text-primary-700 dark:text-primary-300 border border-primary-200 dark:border-primary-800 truncate">
              {{ article.categoryName || '未建档' }}
            </span>
            <span class="text-[11px] text-gray-400 tabular-nums flex-shrink-0">{{ shownDate }}</span>
          </div>

          <!-- 标题前原来有一道 3px 主题色竖线。删掉的原因：它和「精品」徽章在同一位置重复表达
               「这篇不一样」，而且每行都画一竖，整页扫下去像一排书签，正文反而被切碎。
               真正需要区分的精品，靠徽章 + 卡片顶部那道通栏细线（见上方 z-10 的 span）就够了。 -->
          <h2 class="title-hover text-lg sm:text-xl font-bold text-gray-900 dark:text-gray-100 line-clamp-2 transition-colors leading-snug">
            <span v-if="article.isFeatured" class="inline-block text-[10px] bg-primary-600/10 text-primary-700 dark:bg-primary-400/15 dark:text-primary-300 border border-primary-300/60 dark:border-primary-500/40 px-1.5 py-0.5 rounded mr-1.5 align-middle font-medium">精品</span><span v-if="article.isTop" class="inline-block text-[10px] bg-primary-600 text-white px-1.5 py-0.5 rounded mr-1.5 align-middle font-medium">置顶</span><span v-html="hlTitle"></span>
          </h2>

          <!-- 摘要：设计稿要求最多两行。没摘要就整段不渲染 ——
               补一句「暂无摘要」只会让人以为文章没写完。 -->
          <p v-if="article.summary" class="mt-2 text-sm text-gray-500 dark:text-gray-400 line-clamp-2 leading-relaxed" v-html="hlSummary"></p>

          <!-- 底行：左边时长 + 字数，右边「阅读全文」。
               用 mt-auto 顶到底部，有没有摘要的卡片底部信息都对齐，扫一列不会参差。 -->
          <div class="mt-auto pt-3 flex items-center justify-between gap-3 border-t border-gray-100 dark:border-gray-700/70">
            <span class="flex items-center gap-1.5 text-[11px] text-gray-500 dark:text-gray-400 min-w-0">
              <ReadingTime :word-count="article.wordCount || 0" class="flex-shrink-0" />
              <template v-if="article.wordCount">
                <span class="opacity-40" aria-hidden="true">·</span>
                <span class="tabular-nums flex-shrink-0">{{ article.wordCount }} 字</span>
              </template>
            </span>
            <span class="inline-flex items-center gap-1 text-[11px] text-gray-500 dark:text-gray-400 group-hover:text-primary-600 dark:group-hover:text-primary-400 transition-colors flex-shrink-0">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6.5 4h11A1.5 1.5 0 0 1 19 5.5V20l-7-4-7 4V5.5A1.5 1.5 0 0 1 6.5 4Z" />
              </svg>
              阅读全文
            </span>
          </div>
        </div>

        <!-- 同上：没封面就不留这条图栏，文字区自动吃满整行宽度 -->
        <div v-if="article.cover" class="hidden sm:block w-36 lg:w-44 flex-shrink-0 border-l border-gray-200 dark:border-gray-700">
          <div class="h-full min-h-[10rem] theme-illustration flex items-center justify-center">
            <img :src="ossImage(article.cover, { w: IMG_W.card })" :alt="article.title" loading="lazy" decoding="async" class="w-full h-full object-cover" />
          </div>
        </div>
      </div>
    </template>
  </NuxtLink>
</template>

<script setup lang="ts">
// 文章链接统一走 articlePath()：避免各处手写模板串导致 /post/{slug} 与 /article/{id} 混用
import { articlePath } from '~/utils/articlePath'
// 封面按显示尺寸取图：卡片宽 144–300px，用 640 宽（2 倍屏够用），实测单张 341KB → 8KB
import { ossImage, IMG_W } from '~/utils/image'
// 高亮返回的是已转义的 HTML 片段（见 utils/highlight.ts），只有它才能进 v-html
import { highlightKeyword } from '~/utils/highlight'

const props = withDefaults(defineProps<{
  article: { id: number; title: string; slug?: string; summary: string; cover: string; isTop: number; isFeatured?: number; viewCount: number; commentCount: number; createTime: string; publishAt?: string; wordCount?: number; categoryName?: string; tags?: { id: number; name: string }[]; author?: { nickname: string; avatar: string } }
  layout?: 'list' | 'grid'
  /** 封面比例（只对 grid 生效）：默认 16:9；首页右栏用 2:1 */
  ratio?: '16:9' | '2:1'
  /** 搜索结果页传当前关键词进来；为空时等同于原样输出（仍然会转义） */
  keyword?: string
}>(), { layout: 'list', ratio: '16:9', keyword: '' })

/**
 * 封面比例类名。
 * ⚠️ 必须写成字面量（`aspect-[2/1]`），不能让 Tailwind 去拼字符串 ——
 * 拼接出来的类名不会被 JIT 扫描到，线上就是「没有这个样式」。
 */
const coverAspect = computed(() => (props.ratio === '2:1' ? 'aspect-[2/1]' : 'aspect-video'))

/**
 * 卡片上显示的日期 = 发布时间，没有就退回创建时间。
 * 后端现在会给已发布文章保留 publish_at（用户选的发布时间），
 * 历史文章是 NULL，于是自然回落到 create_time —— 两种数据都能正确显示。
 */
const shownDate = computed(() => {
  const d = props.article.publishAt || props.article.createTime || ''
  return d.substring(0, 10)
})

/** 标题走的是 v-html，所以必须无条件过一遍 escape —— 高亮与否都安全 */
const hlTitle = computed(() => highlightKeyword(props.article.title, props.keyword))
const hlSummary = computed(() => highlightKeyword(props.article.summary, props.keyword))

</script>
