<template>
  <div class="max-w-3xl mx-auto">
    <h1 class="text-3xl font-extrabold text-gray-900 dark:text-gray-100 mb-8">关于</h1>
    
    <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm p-8 mb-8">
      <div v-if="aboutContent" class="article-content max-w-none" v-html="renderedAbout"></div>
      <div v-else class="text-gray-500 dark:text-gray-400 leading-relaxed">
        <p>你好，欢迎来到{{ siteName }}。这里记录了我的技术思考、编程心得和学习笔记。</p>
        <p class="mt-4">技术栈：Java / SpringBoot / Vue / Nuxt / MySQL / Redis</p>
      </div>
    </div>

    <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-4">友链</h2>
    <div v-if="links.length === 0" class="text-gray-400 text-sm">暂无友链</div>
    <div v-else class="grid gap-3 sm:grid-cols-2">
      <a v-for="link in links" :key="link.id" :href="fixUrl(link.url)" target="_blank" rel="noopener" class="flex items-center gap-3 bg-white dark:bg-gray-800 rounded-xl shadow-sm p-4 hover:shadow-md transition group">
        <img v-if="link.avatar" :src="ossImage(link.avatar, { w: IMG_W.avatar })" :alt="link.name" loading="lazy" class="w-10 h-10 rounded-full object-cover" />
        <div v-else class="w-10 h-10 rounded-full bg-primary-100 dark:bg-primary-900 flex items-center justify-center text-primary-600 dark:text-primary-400 font-bold text-sm">{{ link.name[0] }}</div>
        <div>
          <div class="font-medium text-gray-900 dark:text-gray-100 group-hover:text-primary-600 dark:group-hover:text-primary-400 transition text-sm">{{ link.name }}</div>
          <div v-if="link.description" class="text-xs text-gray-400 mt-0.5">{{ link.description }}</div>
        </div>
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ossImage, IMG_W } from '~/utils/image'
import { renderMarkdown } from '~/utils/markdownCore'

const { get } = useApi()

/**
 * 站点名与「关于页内容」都来自后台站点设置。
 * 这里 await useSiteConfig()（key 与布局/首页共用，不会多发一次请求）而不是
 * setup 里裸发 fetch —— 后者在 SSR 阶段拿不到数据，缓存命中时会把空内容写进 HTML，
 * 爬虫看到的就是一页空「关于」。
 */
const { data: siteCfg } = await useSiteConfig()
const siteName = computed(() => (siteCfg.value?.siteName || '').trim() || '博观')
const aboutContent = computed(() => (siteCfg.value?.aboutContent || '').trim())

const { data: linkList } = await useAsyncData('about-links', async () => {
  try {
    const res = await get<any>('/friend-link/list')
    return res?.code === 200 ? (res.data || []) : []
  } catch {
    return []
  }
})
const links = computed<any[]>(() => linkList.value || [])

const renderedAbout = computed(() => {
  if (!aboutContent.value) return ''
  // 走全站唯一的正文渲染链路（utils/markdownCore）：这里此前是自己 new 了一个
  // 裸 MarkdownIt 实例，于是「关于」页的 `$...$` / `$$...$$` 会被原样当成文本输出，
  // 而同一段文字贴进文章就正常渲染 —— 同一份内容两处表现不一致，纯粹是两份实现分叉。
  // 复用之后顺带还能拿到：KaTeX 公式、代码高亮、提示块、表格横向滚动、标题锚点。
  // html:false 依然生效（禁裸 HTML，防 v-html 存储型 XSS），安全性没有放松。
  return renderMarkdown(aboutContent.value)
})

function fixUrl(url: string) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return 'https://' + url
}

useSeoMeta({
  title: () => `关于 - ${siteName.value}`,
  description: () => `关于${siteName.value}：一个程序员记录技术思考、编程心得与学习笔记的地方。`,
  ogTitle: () => `关于 - ${siteName.value}`,
  ogDescription: () => `关于${siteName.value}：记录技术思考与编程心得。`,
  ogImage: 'https://codeup.asia/logo.png'
})
useHead({ link: [{ rel: 'canonical', href: 'https://codeup.asia/about' }] })
</script>
