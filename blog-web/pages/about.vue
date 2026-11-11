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
const links = ref<any[]>([])
const siteName = ref('好啵博客')
const aboutContent = ref('')

const renderedAbout = computed(() => {
  if (!aboutContent.value) return ''
  // 走全站唯一的正文渲染链路（utils/markdownCore）：这里此前是自己 new 了一个
  // 裸 MarkdownIt 实例，于是「关于」页的 `$...$` / `$$...$$` 会被原样当成文本输出，
  // 而同一段文字贴进文章就正常渲染 —— 同一份内容两处表现不一致，纯粹是两份实现分叉。
  // 复用之后顺带还能拿到：KaTeX 公式、代码高亮、提示块、表格横向滚动、标题锚点。
  // html:false 依然生效（禁裸 HTML，防 v-html 存储型 XSS），安全性没有放松。
  return renderMarkdown(aboutContent.value)
})

async function fetchLinks() {
  try {
    const res = await get<any>('/friend-link/list')
    if (res.code === 200) links.value = res.data || []
  } catch { /* 容错 */ }
}

async function fetchConfig() {
  try {
    const res = await get<any>('/site-config/public')
    if (res.code === 200 && res.data) {
      siteName.value = res.data.siteName || '好啵博客'
      aboutContent.value = res.data.aboutContent || ''
    }
  } catch { /* 容错 */ }
}

function fixUrl(url: string) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return 'https://' + url
}

fetchLinks()
fetchConfig()

useSeoMeta({
  title: '关于 - 好啵博客',
  description: '关于好啵博客：一个程序员记录技术思考、编程心得与学习笔记的地方。',
  ogTitle: '关于 - 好啵博客',
  ogDescription: '关于好啵博客：记录技术思考与编程心得。',
  ogImage: 'https://codeup.asia/apple-touch-icon.png'
})
useHead({ link: [{ rel: 'canonical', href: 'https://codeup.asia/about' }] })
</script>
