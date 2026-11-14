export default defineNuxtConfig({
  devtools: { enabled: true },
  modules: ['@pinia/nuxt', '@nuxtjs/tailwindcss', '@nuxtjs/color-mode'],

  ssr: true,

  // KaTeX 数学公式的样式必须全局引入（而不是写在组件里）：
  // 公式在 SSR 阶段就已渲染成 HTML，样式若晚到，首屏会出现「先错乱、再抖正」的闪动。
  // katex.min.css 里 @font-face 的字体路径会被 Vite 重写并随构建产物一起拷贝。
  css: ['katex/dist/katex.min.css'],

  runtimeConfig: {
    public: {
      apiBase: '/api'
    }
  },

  nitro: {
    // 构建期生成 .gz / .br 压缩产物，服务器 Nginx 用 gzip_static 直接发
    compressPublicAssets: { gzip: true, brotli: true },
    // pdfjs-dist（37MB / 355 个文件）只在浏览器里跑，是 `utils/pdfImport.ts` 里的动态 import。
    // 但作为「外部依赖」它会被 nitro 追进 .output/server/node_modules，构建时 tracing 慢到
    // 卡住（Windows 上实测十几分钟不动），部署包也会白胖 37MB。
    // 内联进服务端 bundle：SSR 下这段代码根本不执行，构建快、包也小。
    externals: { inline: ['pdfjs-dist'] },
    routeRules: {
      '/api/**': {
        proxy: 'http://127.0.0.1:8080/api/**'
      },
      // sitemap 由后端 SitemapController 动态生成（含最新文章），
      // 这里转发一层，使 https://codeup.asia/sitemap.xml 可直接访问，无需再改 nginx
      '/sitemap.xml': {
        proxy: 'http://127.0.0.1:8080/sitemap.xml'
      },

      // ---- 公开页缓存（P2-9）----
      // 一篇文章要串 5 个接口才吐出 HTML，每次访问都重算一遍很浪费。这里给公开页一个
      // **很短的** SWR 窗口：窗口内复用同一份 HTML；过期后先返回旧页、后台重新渲染，
      // 所以访客永远不等 SSR（响应永远来自内存）。
      //
      // 两个必须注意的点：
      // 1. `varies: ['cookie']` 不能省 —— 顶部导航按登录态渲染（isLoggedIn 读 cookie），
      //    不分桶会把某个人的登录态发给所有人。没有 token cookie 的匿名访客共用一份缓存，
      //    这才是收益所在。
      // 2. 详情页的阅读数改为浏览器打点（POST /api/article/{id}/view），
      //    否则缓存窗口内的访问都不会被计入（见 ArticleDetail.vue）。
      //
      // 窗口值：详情页 10s、首页 20s、聚合页 30–60s、静态页 2–5min。
      // 后台 / 个人中心 / 登录 / OAuth / AI 一律不缓存（没有规则 = 不缓存）。
      '/': { cache: { maxAge: 20, swr: true, varies: ['cookie'] } },
      '/post/**': { cache: { maxAge: 10, swr: true, varies: ['cookie'] } },
      '/article/**': { cache: { maxAge: 10, swr: true, varies: ['cookie'] } },
      '/tag/**': { cache: { maxAge: 30, swr: true, varies: ['cookie'] } },
      '/category/**': { cache: { maxAge: 30, swr: true, varies: ['cookie'] } },
      '/archive': { cache: { maxAge: 30, swr: true, varies: ['cookie'] } },
      '/tags': { cache: { maxAge: 60, swr: true, varies: ['cookie'] } },
      '/categories': { cache: { maxAge: 60, swr: true, varies: ['cookie'] } },
      '/about': { cache: { maxAge: 120, swr: true, varies: ['cookie'] } },
      '/privacy': { cache: { maxAge: 300, swr: true, varies: ['cookie'] } }
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
      title: '博观 - codeup.asia',
      charset: 'utf-8',
      viewport: 'width=device-width, initial-scale=1',
      link: [
        // svg 优先（缩放不糊），png 是给不认 svg 图标的旧浏览器的退路
        { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' },
        { rel: 'icon', type: 'image/png', href: '/favicon-32.png', sizes: '32x32' },
        { rel: 'icon', type: 'image/png', href: '/favicon-16.png', sizes: '16x16' },
        { rel: 'apple-touch-icon', href: '/apple-touch-icon.png' }
      ],
      meta: [
        { name: 'description', content: '博观 - 一个程序员的技术博客，记录 Java / SpringBoot / Vue / Nuxt / Python / Linux / Docker 的实践与思考' },
        { name: 'keywords', content: '博观,技术博客,编程,Java,SpringBoot,Vue,codeup' },
        { property: 'og:title', content: '博观 - codeup.asia' },
        { property: 'og:description', content: '博观 - 一个程序员的技术博客' },
        { property: 'og:url', content: 'https://codeup.asia' },
        { property: 'og:type', content: 'website' },
        { property: 'og:site_name', content: '博观' },
        { property: 'og:image', content: 'https://codeup.asia/logo.png' }
      ],
      // 壁纸模式存在 localStorage，服务端读不到。这段内联脚本在首次绘制前就把模式
      // 写进 <html data-wallpaper>，CSS 据此决定壁纸几何与正文上边距 ——
      // 否则选了「隐藏壁纸」的人每次刷新都会先看到一张图再消失。
      script: [
        {
          tagPosition: 'head',
          innerHTML: `(function(){try{var m=localStorage.getItem('blog-wallpaper-mode');var ok=(m==='full'||m==='cover'||m==='none');document.documentElement.setAttribute('data-wallpaper',ok?m:'banner')}catch(e){document.documentElement.setAttribute('data-wallpaper','banner')}})();`
        },
        // 主题色相同理：不先写好 --ph，选了非默认色的人每次刷新都会先看到陶土色再跳色。
        // 只认 0–360 的整数，越界/脏数据一律忽略（保持 main.css 里的默认 22）。
        {
          tagPosition: 'head',
          innerHTML: `(function(){try{var h=parseInt(localStorage.getItem('blog-hue')||'',10);if(h>=0&&h<=360){document.documentElement.style.setProperty('--ph',String(h))}}catch(e){}})();`
        }
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
