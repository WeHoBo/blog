export default defineNuxtConfig({
  devtools: { enabled: true },
  modules: ['@pinia/nuxt', '@nuxtjs/tailwindcss', '@nuxt/icon', '@nuxtjs/color-mode'],

  ssr: true,

  runtimeConfig: {
    public: {
      apiBase: 'http://localhost:8080/api'
    }
  },

  app: {
    head: {
      title: '好啵博客',
      charset: 'utf-8',
      viewport: 'width=device-width, initial-scale=1',
      meta: [
        { name: 'description', content: '一个程序员的个人技术博客' },
        { name: 'keywords', content: '技术博客,编程,Java,SpringBoot,Vue' }
      ]
    }
  },

  tailwindcss: {
    configPath: 'tailwind.config.ts'
  },

  typescript: {
    strict: true
  }
})
