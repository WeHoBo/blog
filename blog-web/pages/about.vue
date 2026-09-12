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
        <img v-if="link.avatar" :src="link.avatar" :alt="link.name" class="w-10 h-10 rounded-full object-cover" />
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
import MarkdownIt from 'markdown-it'

const { get } = useApi()
const links = ref<any[]>([])
const siteName = ref('好啵博客')
const aboutContent = ref('')

const renderedAbout = computed(() => {
  if (!aboutContent.value) return ''
  // html: false —— 与 MarkdownRenderer 保持一致，禁用裸 HTML，避免 v-html 造成存储型 XSS。
  // 关于页内容支持标准 Markdown（标题/列表/表格/换行等），无需放开任意 HTML。
  return new MarkdownIt({ html: false, linkify: true }).render(aboutContent.value)
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
