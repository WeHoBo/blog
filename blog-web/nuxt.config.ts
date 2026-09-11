export default defineNuxtConfig({
  devtools: { enabled: true },
  modules: ['@pinia/nuxt', '@nuxtjs/tailwindcss', '@nuxtjs/color-mode'],

  ssr: true,

  runtimeConfig: {
    public: {
      apiBase: '/api'
    }
  },

  nitro: {
    // 构建期生成 .gz / .br 压缩产物，服务器 Nginx 用 gzip_static 直接发
    compressPublicAssets: { gzip: true, brotli: true },
    routeRules: {
      '/api/**': {
        proxy: 'http://127.0.0.1:8080/api/**'
      },
      // sitemap 由后端 SitemapController 动态生成（含最新文章），
      // 这里转发一层，使 https://codeup.asia/sitemap.xml 可直接访问，无需再改 nginx
      '/sitemap.xml': {
        proxy: 'http://127.0.0.1:8080/sitemap.xml'
      }
    }
  },

  vite: {
    build: {
      rollupOptions: {
        output: {
          // 保留 chunk 名称（便于按名字控制 prefetch）；hljs 单独分包。
          // 只列代码真正 import 的 lib/common，避免把全量 highlight.js 也拉进来。
          chunkFileNames: '_nuxt/[name]-[hash].js',
          manualChunks: {
            hljs: ['highlight.js/lib/common']
          }
        }
      }
    }
  },

  app: {
    head: {
      title: '好啵博客 - codeup.asia',
      charset: 'utf-8',
      viewport: 'width=device-width, initial-scale=1',
      link: [
        { rel: 'icon', type: 'image/png', href: '/favicon-32.png', sizes: '32x32' },
        { rel: 'icon', type: 'image/png', href: '/favicon-16.png', sizes: '16x16' },
        { rel: 'apple-touch-icon', href: '/apple-touch-icon.png' }
      ],
      meta: [
        { name: 'description', content: '一个程序员的个人技术博客 - codeup.asia' },
        { name: 'keywords', content: '技术博客,编程,Java,SpringBoot,Vue,codeup' },
        { property: 'og:title', content: '好啵博客 - codeup.asia' },
        { property: 'og:description', content: '一个程序员的个人技术博客' },
        { property: 'og:url', content: 'https://codeup.asia' },
        { property: 'og:type', content: 'website' },
        { property: 'og:site_name', content: '好啵博客' }
      ]
    },
    pageTransition: {
      name: 'page',
      mode: 'out-in'
    }
  },

  tailwindcss: {
    configPath: 'tailwind.config.ts'
  },

  typescript: {
    strict: true
  }
})
