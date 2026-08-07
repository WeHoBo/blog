export default defineNuxtConfig({
  devtools: { enabled: true },
  modules: ['@pinia/nuxt', '@nuxtjs/tailwindcss', '@nuxt/icon', '@nuxtjs/color-mode'],

  ssr: true,

  runtimeConfig: {
    public: {
      apiBase: '/api'
    }
  },

  nitro: {
    routeRules: {
      '/api/**': {
        proxy: 'http://127.0.0.1:8080/api/**'
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
