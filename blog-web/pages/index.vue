<template>
  <div class="max-w-7xl mx-auto">
    <!-- 轻量 Hero：左侧问候 / 中间站点概览 / 右侧热门标签，把整条横幅填满，避免中间和右侧大片留白 -->
    <div class="px-4 pt-6 pb-1 lg:px-0">
      <div class="rounded-xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-paper px-6 sm:px-10 py-8 relative overflow-hidden">
        <div class="relative grid gap-6 lg:gap-8 lg:grid-cols-[1.5fr_1fr_1.1fr] lg:items-center">
          <!-- 左：问候 + 关注方向 + 行动按钮
               文案来自后台「站点设置」（博主名称/简介/关注方向），取不到就回落到默认值，
               所以后台没配也不会出现空白横幅。
               头像固定用站点 Logo —— 全站只有一个「作者形象」，不再有
               「首页是站点头像、侧栏是登录用户头像」这种两套来源打架的情况。 -->
          <div class="min-w-0">
            <div class="flex items-center gap-2.5">
              <img src="/logo.svg" alt="" width="40" height="40" decoding="async"
                class="w-10 h-10 rounded-xl object-contain p-1 ring-1 ring-gray-300/70 dark:ring-gray-600 shrink-0"
                style="background: var(--page-bg, #F7F2E8)" />
              <h1 class="text-xl sm:text-2xl font-extrabold text-gray-900 dark:text-gray-100 truncate">你好，我是{{ bloggerName }} 👋</h1>
            </div>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-1.5">{{ heroSubtitle }}</p>
            <div v-if="focusTags.length" class="mt-3">
              <div class="text-[11px] font-bold text-gray-400 mb-1.5 tracking-wide">关注方向</div>
              <div class="flex flex-wrap items-center gap-2">
                <span v-for="t in focusTags" :key="t" class="text-xs px-2.5 py-1 rounded-full theme-chip">{{ t }}</span>
              </div>
            </div>
            <div class="flex gap-3 mt-4">
              <a href="#articles" class="px-4 py-1.5 rounded-full bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-xs font-medium hover:opacity-85 transition">开始阅读</a>
              <NuxtLink to="/about" class="px-4 py-1.5 rounded-full border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 text-xs font-medium hover:bg-gray-100 dark:hover:bg-gray-700 transition">关于我</NuxtLink>
            </div>
          </div>

          <!-- 中：站点概览（真实数据，来自首屏 SSR payload） -->
          <div class="grid grid-cols-3 lg:grid-cols-1 gap-3 lg:gap-2.5 lg:border-l lg:border-gray-200/70 dark:lg:border-gray-700/70 lg:pl-7">
            <div v-for="s in siteStats" :key="s.label" class="text-center lg:text-left">
              <div class="text-xl sm:text-2xl font-extrabold theme-text leading-none">{{ s.value }}</div>
              <div class="text-[11px] text-gray-400 mt-1">{{ s.label }}</div>
            </div>
          </div>

          <!-- 右：热门标签快捷入口 -->
          <div class="lg:border-l lg:border-gray-200/70 dark:lg:border-gray-700/70 lg:pl-7">
            <div class="text-[11px] font-bold text-gray-400 mb-2 tracking-wide">热门标签</div>
            <div class="flex flex-wrap gap-1.5">
              <NuxtLink v-for="t in hotTags.slice(0, 6)" :key="t.id" :to="taxonomyPath('/tag', t)"
                class="inline-flex items-baseline gap-1 px-2.5 py-1 rounded-full text-xs bg-transparent text-gray-600 dark:text-gray-300 border border-gray-200 dark:border-gray-600 hover:border-primary-400 hover:text-primary-600 dark:hover:text-primary-400 transition">
                {{ t.name }}
                <span v-if="t.articleCount" class="text-[10px] opacity-50">{{ t.articleCount }}</span>
              </NuxtLink>
              <NuxtLink v-if="hotTags.length === 0" to="/tags" class="text-xs text-primary-600 dark:text-primary-400 hover:underline">浏览全部标签 →</NuxtLink>
            </div>
            <NuxtLink to="/categories" class="inline-flex items-center gap-1 mt-2.5 text-xs text-primary-600 dark:text-primary-400 hover:underline">
              全部分类 <span aria-hidden="true">→</span>
            </NuxtLink>
          </div>
        </div>
      </div>
    </div>

    <div id="articles" class="flex gap-6 py-6">
      <!-- Left sidebar: category nav -->
      <aside class="hidden lg:block w-52 flex-shrink-0">
        <div class="sticky top-20 space-y-4">
          <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden">
            <div class="px-4 py-3 border-b dark:border-gray-700 flex items-center justify-between">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">文章分类</h3>
            </div>
            <div class="py-1 max-h-[46vh] overflow-y-auto">
            <!-- 全部 -->
            <button @click="currentCategory = undefined; pageNum = 1"
              :class="!currentCategory
                ? 'bg-gray-100 dark:bg-white/10 text-gray-900 dark:text-gray-100 border-l-2 border-primary-600'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'"
              class="w-full text-left px-4 py-2 text-sm font-medium transition flex items-center gap-2">
              <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h4l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2z"/></svg>
              <span class="flex-1 min-w-0 truncate">全部</span>
            </button>

            <!-- 分类树（一级 + 折叠子级） -->
            <template v-for="cat in categoryTree" :key="cat.id">
              <div>
                <button @click="selectCategory(cat)"
                  :class="catBtnClass(cat)"
                  class="w-full text-left px-3 py-2 text-sm transition flex items-center gap-1.5 border-l-2 border-transparent">
                  <span v-if="cat.children && cat.children.length" @click.stop="toggleExpand(cat.id)"
                    class="w-4 h-4 flex items-center justify-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 flex-shrink-0">
                    <svg class="w-3 h-3 transition-transform duration-200" :class="isExpanded(cat.id) ? 'rotate-90' : ''" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7"/></svg>
                  </span>
                  <svg v-else class="w-3 h-3 text-transparent flex-shrink-0" viewBox="0 0 24 24"></svg>
                  <span v-html="catIcon(cat.name)" class="flex-shrink-0 w-4 h-4"></span>
                  <span class="flex-1 min-w-0 truncate font-medium">{{ cat.name }}</span>
                  <span class="text-xs opacity-50">{{ cat.articleCount || 0 }}</span>
                </button>

                <div v-if="cat.children && cat.children.length && isExpanded(cat.id)" class="pb-1">
                  <button v-for="child in cat.children" :key="child.id" @click="currentCategory = child.id; pageNum = 1"
                    :class="currentCategory === child.id
                      ? 'bg-gray-100 dark:bg-white/10 text-gray-900 dark:text-gray-100 border-l-2 border-primary-600'
                      : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'"
                    class="w-full text-left pl-9 pr-3 py-1.5 text-sm transition flex items-center gap-2">
                    <span class="w-1 h-1 rounded-full bg-gray-300 dark:bg-gray-600 flex-shrink-0"></span>
                    <span class="flex-1 min-w-0 truncate">{{ child.name }}</span>
                    <span class="text-xs opacity-40">{{ child.articleCount || 0 }}</span>
                  </button>
                </div>
              </div>
            </template>
            </div>
          </div>

          <!-- 发布热力图：分类下方的「我什么时候写过东西」。
               放在左栏而不是右栏，是因为它属于「导航/概览」性质，
               和分类树一起构成「我从哪儿进文章」的入口。 -->
          <ContributionCalendar />
        </div>
      </aside>

      <!-- Center: article feed -->
      <main class="flex-1 min-w-0">
        <!-- 移动端分类入口 -->
        <div class="flex items-center gap-2 mb-3 lg:hidden">
          <button @click="mobileCatOpen = true"
            class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 text-sm text-gray-600 dark:text-gray-300 shadow-sm active:scale-95 transition">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h10"/></svg>
            分类
          </button>
          <span v-if="currentCategory" class="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-gray-100 dark:bg-white/10 text-gray-900 dark:text-gray-100 text-xs">
            {{ pageTitle }}
            <button @click="clearFilters" class="ml-0.5 hover:text-primary-800">✕</button>
          </span>
        </div>

        <!-- Heading + view toggle -->
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
            <span class="w-1 h-5 bg-primary-600 rounded-full"></span>
            {{ pageTitle }}
            <!-- 搜索时把命中总数摆出来：避免「只有 1 篇」被误认为「没搜到」 -->
            <span v-if="keyword && !loading" class="text-xs font-normal text-gray-400">{{ totalMatched }} 篇</span>
          </h2>
          <div class="flex bg-white dark:bg-gray-800 rounded-lg border dark:border-gray-700 p-1">
            <button @click="setViewMode('list')" title="列表视图"
              class="px-2.5 py-1 rounded-md text-sm transition"
              :class="viewMode === 'list' ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900' : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700'">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>
            </button>
            <button @click="setViewMode('grid')" title="网格视图"
              class="px-2.5 py-1 rounded-md text-sm transition"
              :class="viewMode === 'grid' ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900' : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700'">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"/></svg>
            </button>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="space-y-3">
          <div v-for="i in 5" :key="i" class="bg-white dark:bg-gray-800 rounded-lg p-5 animate-pulse">
            <div class="flex gap-4">
              <div class="hidden sm:block w-32 h-20 bg-gray-200 dark:bg-gray-700 rounded-lg flex-shrink-0"></div>
              <div class="flex-1 space-y-2">
                <div class="h-5 bg-gray-200 dark:bg-gray-700 rounded w-3/4"></div>
                <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-full"></div>
                <div class="flex gap-4 mt-2">
                  <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-20"></div>
                  <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-16"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty -->
        <div v-else-if="articles.length === 0" class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-16 text-center text-gray-400">
          <span class="text-4xl mb-4 block">🔍</span>
          <p class="text-gray-500 dark:text-gray-400 font-medium">{{ emptyText }}</p>
          <!-- 搜不到时把搜索范围说清楚：正文不参与匹配，避免用户以为站内没有这篇 -->
          <p class="text-xs mt-1 opacity-70">换个关键词试试？可搜索标题、摘要、标签与分类</p>
          <button v-if="keyword || currentCategory || currentTag" @click="clearFilters"
            class="mt-4 px-4 py-1.5 rounded-full bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-xs hover:opacity-85 transition">
            清除筛选
          </button>
        </div>

        <!-- List view -->
        <div v-else-if="viewMode === 'list'" class="space-y-3">
          <ArticleCard v-for="article in articles" :key="article.id" :article="article" :keyword="keyword" />
        </div>

        <!-- Grid view -->
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <ArticleCard v-for="article in articles" :key="article.id" :article="article" layout="grid" :keyword="keyword" />
        </div>

        <Pagination v-if="totalPages > 1" :current="pageNum" :total="totalPages" @change="pageNum = $event" />

        <!-- 右侧栏在 xl 以下会被隐藏：这里补一个源码入口，避免窄屏完全看不到 -->
        <div class="xl:hidden mt-8">
          <a href="https://github.com/WeHoBo/blog" target="_blank" rel="noopener noreferrer"
            class="mt-3 flex items-start gap-3 bg-white dark:bg-gray-800 rounded-xl shadow-sm p-4 hover:shadow-md transition group">
            <span class="w-8 h-8 flex-shrink-0 rounded-full bg-gray-900 dark:bg-gray-100 flex items-center justify-center text-white dark:text-gray-900">
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
              </svg>
            </span>
            <span class="flex-1 min-w-0">
              <span class="block text-sm font-bold text-gray-900 dark:text-gray-100">开源仓库 · WeHoBo/blog</span>
              <span class="block text-xs text-gray-500 dark:text-gray-400 mt-1 leading-relaxed">本站源码已开源，欢迎阅读、Star 与提 Issue。</span>
            </span>
          </a>
        </div>
      </main>

      <!-- Right sidebar -->
      <aside class="hidden xl:block w-60 flex-shrink-0 space-y-4">
        <div class="sticky top-20">
          <!-- 作者卡片：登录显示「我」，未登录显示站点博主。两套内容互不回落 ——
               否则普通用户没填简介时会看到管理员那份（这个坑踩过两次）。 -->
          <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden mb-4">
            <div class="p-4 text-center">
              <!-- ① 登录用户：自己的资料 -->
              <template v-if="showMine">
                <template v-if="me">
                  <img v-if="myAvatar" :src="myAvatar" alt="" width="64" height="64" decoding="async"
                    class="w-16 h-16 mx-auto mb-3 rounded-full object-cover ring-1 ring-gray-300/70 dark:ring-gray-600" />
                  <div v-else
                    class="w-16 h-16 mx-auto mb-3 rounded-full flex items-center justify-center text-xl font-bold text-white bg-primary-600 dark:bg-primary-500">
                    {{ myInitial }}
                  </div>
                  <div class="font-bold text-gray-900 dark:text-gray-100 text-sm">{{ myName }}</div>
                  <p v-if="myBio" class="text-xs text-gray-500 mt-1 leading-relaxed whitespace-pre-line">{{ myBio }}</p>
                  <NuxtLink v-else to="/profile" class="block text-xs text-gray-400 mt-1 hover:text-primary-500 transition">还没写简介，去补一句 →</NuxtLink>
                  <NuxtLink to="/profile" class="inline-block mt-2 text-xs text-primary-600 dark:text-primary-400 hover:underline">编辑我的资料 →</NuxtLink>
                </template>
                <!-- 已登录但资料还没取到：占位骨架，绝不显示博主的 -->
                <template v-else>
                  <div class="w-16 h-16 mx-auto mb-3 rounded-full bg-gray-200 dark:bg-gray-700 animate-pulse"></div>
                  <div class="h-3 w-24 mx-auto rounded bg-gray-200 dark:bg-gray-700 animate-pulse"></div>
                  <div class="h-3 w-full mt-2 rounded bg-gray-100 dark:bg-gray-700/60 animate-pulse"></div>
                  <NuxtLink to="/profile" class="inline-block mt-2 text-xs text-primary-600 dark:text-primary-400 hover:underline">编辑我的资料 →</NuxtLink>
                </template>
              </template>

              <!-- ② 访客：站点博主（公开信息，SSR 直出） -->
              <template v-else>
                <img src="/logo.svg" alt="" width="64" height="64" decoding="async"
                  class="w-16 h-16 mx-auto mb-3 rounded-2xl object-contain p-2 ring-1 ring-gray-300/70 dark:ring-gray-600"
                  style="background: var(--page-bg, #F7F2E8)" />
                <div class="font-bold text-gray-900 dark:text-gray-100 text-sm">{{ bloggerName }}</div>
                <p class="text-xs text-gray-500 mt-1 leading-relaxed whitespace-pre-line">{{ bloggerBio || '记录技术成长，分享编程心得' }}</p>
                <NuxtLink to="/about" class="inline-block mt-2 text-xs text-primary-600 dark:text-primary-400 hover:underline">关于我 →</NuxtLink>
              </template>
            </div>
          </div>

          <!-- GitHub 仓库：给想自己读源码 / 本地跑一遍的读者一个入口 -->
          <a href="https://github.com/WeHoBo/blog" target="_blank" rel="noopener noreferrer"
            class="block bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden mb-4 p-4 hover:shadow-md transition group">
            <div class="flex items-center gap-3">
              <span class="w-9 h-9 flex-shrink-0 rounded-full bg-gray-900 dark:bg-gray-100 flex items-center justify-center text-white dark:text-gray-900">
                <svg class="w-5 h-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
                </svg>
              </span>
              <div class="min-w-0 flex-1">
                <div class="text-sm font-bold text-gray-900 dark:text-gray-100">开源仓库</div>
                <div class="text-xs text-gray-400 truncate">WeHoBo/blog</div>
              </div>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-3 leading-relaxed">
              本站前端 Nuxt 3、后端 Spring Boot 3 与 RAG 知识库源码已全部开源，欢迎阅读、Star 与提 Issue。
            </p>
            <span class="inline-flex items-center gap-1 mt-2.5 text-xs font-medium text-primary-600 dark:text-primary-400 group-hover:gap-2 transition-all">
              查看源码 <span aria-hidden="true">→</span>
            </span>
          </a>

          <!-- 热门文章 / 热门标签两块已按需求移除：
               热门榜只有 5 条、且和「按时间排序的文章流」高度重合，
               占掉右栏 1/3 的高度却不带来新的信息（阅读量还会被缓存窗口拉平）。 -->
        </div>
      </aside>
    </div>

    <!-- 移动端分类抽屉（Teleport 放在根节点内，保证页面单根，避免过渡卡死） -->
    <Teleport to="body">
      <Transition name="drawer">
        <div v-if="mobileCatOpen" class="fixed inset-0 z-[70] lg:hidden" @click.self="mobileCatOpen = false">
          <div class="absolute inset-0 bg-black/30" @click="mobileCatOpen = false"></div>
          <div class="absolute bottom-0 inset-x-0 bg-white dark:bg-gray-900 rounded-t-2xl max-h-[75vh] overflow-y-auto pb-8">
            <div class="sticky top-0 bg-white/90 dark:bg-gray-900/90 backdrop-blur px-4 py-3 border-b dark:border-gray-700 flex items-center justify-between">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">选择分类</h3>
              <button @click="mobileCatOpen = false" class="w-8 h-8 rounded-full flex items-center justify-center text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition">✕</button>
            </div>
            <div class="p-3">
              <button @click="currentCategory = undefined; pageNum = 1; mobileCatOpen = false"
                :class="!currentCategory ? 'bg-gray-100 dark:bg-white/10 text-gray-900 dark:text-gray-100' : 'text-gray-600 dark:text-gray-400'"
                class="w-full text-left px-4 py-2.5 rounded-xl text-sm font-medium transition flex items-center gap-2">
                <span class="flex-1">全部文章</span>
              </button>
              <template v-for="cat in categoryTree" :key="cat.id">
                <button @click="selectCategory(cat); mobileCatOpen = false"
                  :class="currentCategory === cat.id ? 'bg-gray-100 dark:bg-white/10 text-gray-900 dark:text-gray-100' : 'text-gray-600 dark:text-gray-400'"
                  class="w-full text-left px-4 py-2.5 rounded-xl text-sm transition flex items-center gap-2 mt-0.5">
                  <span v-html="catIcon(cat.name)" class="flex-shrink-0"></span>
                  <span class="flex-1">{{ cat.name }}</span>
                  <span class="text-xs opacity-50">{{ cat.articleCount || 0 }}</span>
                </button>
                <div v-if="cat.children && cat.children.length" class="ml-6 border-l border-gray-100 dark:border-gray-700 pl-2">
                  <button v-for="child in cat.children" :key="child.id" @click="currentCategory = child.id; pageNum = 1; mobileCatOpen = false"
                    :class="currentCategory === child.id ? 'text-primary-600 dark:text-primary-400' : 'text-gray-500 dark:text-gray-400'"
                    class="w-full text-left px-3 py-2 rounded-lg text-sm transition flex items-center gap-2">
                    <span class="flex-1">{{ child.name }}</span>
                    <span class="text-xs opacity-40">{{ child.articleCount || 0 }}</span>
                  </button>
                </div>
              </template>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
// 标签改跳独立落地页 /tag/{slug}，而不是回退到首页的 ?tagId= 过滤
// （聚合页此前没有独立 URL，等于把 SEO 权重都让给了首页）
import { taxonomyPath } from '~/utils/taxonomy'

const { get } = useApi()
const route = useRoute()
const router = useRouter()

const pageNum = ref(1)
const keyword = ref((route.query.keyword as string) || '')
const currentCategory = ref<number | undefined>(undefined)
const currentTag = ref<number | undefined>(route.query.tagId ? Number(route.query.tagId) : undefined)
const articles = ref<any[]>([])
const categoryTree = ref<any[]>([])
const categoryList = ref<any[]>([])
const tags = ref<any[]>([])
const totalPages = ref(0)
const totalArticles = ref(0)
/** 当前筛选条件下的命中总数（搜索时用来告诉用户「找到了多少篇」） */
const totalMatched = ref(0)
const loading = ref(true)
const mobileCatOpen = ref(false)

const { viewMode, setViewMode, sortMode } = useLayout()

/* 右栏作者卡片要显示「当前这个登录用户自己」的头像/昵称/简介（用户在个人中心自己填）。
   没登录时回落到站点设置里的博主信息 —— 那是站点广播，访客看同一份。 */
const authStore = useAuthStore()

/* ---------------------------------------------------------------
   横幅文案（Banner Title）
   原来这行「你好，我是好啵」是写死在模板里的，想改一个字得改代码重新构建；
   后台「站点设置」里本来就有 bloggerName / bloggerBio / bloggerAvatar / siteDesc
   四个字段，却一个都没被前台使用（待办清单第 3 条）。
   现在统一走 useSiteConfig（与布局里的壁纸共用同一次请求），取不到就回落到原文案。
   --------------------------------------------------------------- */
const { data: siteCfg } = await useSiteConfig()

const bloggerName = computed(() => (siteCfg.value?.bloggerName || '').trim() || '博观')
/** 侧栏作者卡片用完整简介（可能多行），横幅只用第一行 */
const bloggerBio = computed(() => (siteCfg.value?.bloggerBio || '').trim())
/** 「关注方向」小胶囊：后台填英文逗号分隔，最多 6 个 */
const focusTags = computed(() => {
  const raw = (siteCfg.value?.bloggerFocus || '').trim()
  if (!raw) return ['Java', 'AI', 'Python', 'Linux', 'Docker']
  return [...new Set(raw.split(/[,，]/).map((s) => s.trim()).filter(Boolean))].slice(0, 6)
})
const heroSubtitle = computed(() => {
  // 简介允许分多行（常见写法是「📝 记录技术成长 / 💻 分享编程心得」），
  // 横幅只要第一行：多了会把卡片撑高，和右侧标签栏对不齐。
  const bio = (siteCfg.value?.bloggerBio || '')
    .split('\n').map((s) => s.trim()).find(Boolean) || ''
  return bio || (siteCfg.value?.siteDesc || '').trim() || '记录我的计算机学习与技术实践'
})

/* ---------------------------------------------------------------
   右栏「作者卡片」有两套内容，**绝不能互相回落**：

   - 访客（未登录）→ 站点博主的公开信息（站点广播，SSR 直出，对 SEO 也友好）
   - 登录用户      → 严格显示他自己的头像/昵称/简介

   ⚠️⚠️ 上次的错误：给登录用户也写了「取不到就回落到 bloggerName / bloggerBio」。
   结果普通用户没填简介时，卡片上显示的是**管理员写的简介和昵称**，
   看起来就像「所有人共用同一个作者」。这里把回落彻底去掉：
   没填就显示占位文案，没传头像就显示昵称首字母，绝不借用别人的。

   ⚠️ 为什么宁可在服务端多问一次 `/auth/me`：
   `authStore.user` 存在 localStorage，SSR 读不到，直接渲染会「服务端吐 A、水合后变 B」。
   用 cookie 里的 token 在服务端取一次，值进 payload，首屏就是对的。
   --------------------------------------------------------------- */
const { data: viewer } = await useAsyncData('home-viewer', async () => {
  if (!authStore.isLoggedIn) return null
  try {
    const res = await get<any>('/auth/me')
    return res?.code === 200 ? res.data : null
  } catch {
    // token 过期/后端异常都按「没取到」处理，不要因此让首页 500
    return null
  }
})

/** 是否走「我的资料」这套 UI。用 cookie 判断，SSR 与客户端结果一致，不会水合打架 */
const showMine = computed(() => authStore.isLoggedIn)

/* 服务端那次 `/auth/me` 可能失败（最常见是 token 过期，而 localStorage 里还留着用户信息，
   于是顶栏看起来是登录状态、右栏却取不到人）。这时退到浏览器缓存里的用户，
   保证「头像昵称至少是自己」，绝不再回落到站点广播。
   ⚠️ 只能在 onMounted 之后读 `authStore.user`：SSR 没有 localStorage，
   在 computed 里直接读会导致「服务端 null / 客户端有值」的水合不一致。 */
const clientUser = ref<any>(null)
onMounted(() => {
  if (!viewer.value && authStore.user) clientUser.value = authStore.user
})
const me = computed(() => viewer.value || clientUser.value || null)

const myAvatar = computed(() => (me.value?.avatar || '').trim())
const myName = computed(() => {
  const v = me.value
  return ((v?.nickname || v?.username || '').trim()) || '我'
})
/** 首字母占位头像：没传头像时用它，而不是退回站点 Logo（那会让人误以为是站点图） */
const myInitial = computed(() => (myName.value || '?').slice(0, 1).toUpperCase())
/** 简介只用自己的。空就是空 —— 模板里显示占位提示，不借站点广播的文案 */
const myBio = computed(() => (me.value?.bio || '').trim())

const pageTitle = computed(() => {
  if (keyword.value) return `搜索：${keyword.value}`
  if (currentTag.value) {
    const t = tags.value.find((x: any) => x.id === currentTag.value)
    return t ? `标签：${t.name}` : '标签文章'
  }
  if (currentCategory.value) {
    const c = categoryList.value.find((x: any) => x.id === currentCategory.value)
    return c ? c.name : '分类文章'
  }
  return '全部文章'
})

const emptyText = computed(() => {
  if (keyword.value) return '没有找到相关文章'
  if (currentCategory.value || currentTag.value) return '该分类下暂无文章'
  return '暂无文章'
})

function clearFilters() {
  keyword.value = ''
  currentCategory.value = undefined
  currentTag.value = undefined
  pageNum.value = 1
  router.replace({ query: {} })
  fetchArticles()
}

// 分类图标（SVG，统一线性风格）
const CAT_ICONS: [string, string][] = [
  ['AI', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M12 3l1.8 5.5L19 10l-5.2 1.5L12 17l-1.8-5.5L5 10l5.2-1.5z"/><path stroke-linecap="round" stroke-width="1.8" d="M19 3v3M17.5 4.5h3"/></svg>'],
  ['大模型', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="5" y="5" width="14" height="14" rx="3" stroke-width="1.8"/><circle cx="9" cy="9" r="1.2" fill="currentColor" stroke="none"/><circle cx="15" cy="9" r="1.2" fill="currentColor" stroke="none"/><circle cx="9" cy="15" r="1.2" fill="currentColor" stroke="none"/><circle cx="15" cy="15" r="1.2" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none"/></svg>'],
  ['Java', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M8 3.5s4.5 1 6.5 4.5c2.5-1 4-1.5 4-1.5s-2 6 .5 9.5c-2 .5-4.5 1-7 0 1-3 .5-8-4-12.5z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M8 3.5s-4 3 0 12.5c-1.5.5-3 0-3 0s1 4 6 4c0 0 .5-2-1-3 1.5 0 4.5.5 6.5-3"/></svg>'],
  ['Python', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linejoin="round" stroke-width="1.8" d="M12 3c-1.8 0-3 .4-3 1v1.5c1.8-1 5-1 6.5 0M15 3c1.8 0 3 .4 3 1v1.5c-1.8-1-5-1-6.5 0" fill="none" stroke-linecap="round"/><path stroke-width="1.8" fill="none" d="M9 5.5C6.8 6 5.5 7 5.5 9.5c0 2 .3 2.5 1.5 2.5M15 5.5c2.2.5 3.5 1.5 3.5 4 0 2-.3 2.5-1.5 2.5"/><path stroke-linejoin="round" stroke-width="1.8" d="M9 13.5c0 1-1 1-1.5 1H8c-1.5 0-2.5.5-2.5 2v2c0 1.5 1 2 2.5 2 1.5 0 3.5.5 5 0M12 21c1.8 0 3-.4 3-1v-1.5c-1.8 1-5 1-6.5 0" fill="none" stroke-linecap="round"/><path stroke-width="1.8" fill="none" d="M15 18.5c2.2-.5 3.5-1.5 3.5-4 0-2-.3-2.5-1.5-2.5M9 18.5C6.8 18 5.5 17 5.5 14.5"/></svg>'],
  ['Docker', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M3 13h1.5M6.5 13H8m-5-3h1.5M6.5 10H8m-2.5-3H7m5 6h9a5 5 0 01-5 5c-3.5 0-6-1.5-6-5z"/><path stroke-width="1.8" d="M4 13c0 3.5 2.5 5.5 6 5.5" fill="none" stroke-linecap="round"/></svg>'],
  ['Linux', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M4 17l6-6-6-6M12 19h8"/></svg>'],
  ['操作系统', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="2.5" y="4" width="19" height="12" rx="2" stroke-width="1.8"/><path stroke-linejoin="round" stroke-width="1.8" d="M8 20h8M12 16v4" fill="none" stroke-linecap="round"/><path stroke-width="1.8" d="M6 8h2M6 11h2" fill="none" stroke-linecap="round"/></svg>'],
  ['网络', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke-width="1.8"/><path stroke-width="1.8" d="M3 12h18M12 3a15 15 0 010 18M12 3a15 15 0 000 18" fill="none"/></svg>'],
  ['数据库', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><ellipse cx="12" cy="5.5" rx="8" ry="2.8" stroke-width="1.8"/><path stroke-width="1.8" d="M4 5.5V18.5c0 1.5 3.6 2.8 8 2.8s8-1.3 8-2.8V5.5" fill="none"/><path stroke-width="1.8" d="M4 12c0 1.5 3.6 2.8 8 2.8s8-1.3 8-2.8" fill="none"/></svg>'],
  ['课程', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linejoin="round" stroke-width="1.8" d="M4 19.5A2.5 2.5 0 016.5 17H20V3H6.5A2.5 2.5 0 004 5.5z" fill="none" stroke-linecap="round"/><path stroke-width="1.8" d="M4 19.5A2.5 2.5 0 016.5 17H20" fill="none" stroke-linecap="round"/></svg>'],
  ['科普', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linejoin="round" stroke-width="1.8" d="M9 18h6M10 22h4M12 2a7 7 0 00-4.5 12.4c.6.5 1.5 1.6 1.5 2.6h6c0-1 .9-2.1 1.5-2.6A7 7 0 0012 2z" fill="none" stroke-linecap="round"/></svg>']
]
function catIcon(name: string) {
  const hit = CAT_ICONS.find(([k]) => (name || '').includes(k))
  return hit ? hit[1] : '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M12 6v6m0 0l3-2m-3 2l-3-2"/><circle cx="12" cy="12" r="9" stroke-width="1.8"/></svg>'
}

const expandedCats = ref<Set<number>>(new Set())
function toggleExpand(id: number) {
  const s = new Set(expandedCats.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  expandedCats.value = s
}
function isExpanded(id: number) { return expandedCats.value.has(id) }

function catBtnClass(cat: any) {
  const active = currentCategory.value === cat.id
  const base = 'w-full text-left px-3 py-2 text-sm transition flex items-center gap-1.5 border-l-2 border-transparent'
  return active
    ? base + ' bg-gray-100 dark:bg-white/10 text-gray-900 dark:text-gray-100 border-primary-600'
    : base + ' text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
}

function selectCategory(cat: any) {
  currentCategory.value = cat.id
  pageNum.value = 1
  // 有子分类且未展开 → 自动展开
  if (cat.children && cat.children.length && !isExpanded(cat.id)) {
    toggleExpand(cat.id)
  }
}

// 扁平分类列表 → 树（父分类不存在的子分类提升为一级，保证不丢失）
function buildCategoryTree(list: any[]): any[] {
  const map = new Map<number, any>()
  list.forEach((c: any) => map.set(c.id, { ...c, children: [] }))
  const roots: any[] = []
  list.forEach((c: any) => {
    const node = map.get(c.id)
    const parent = c.parentId ? map.get(c.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
}

const hotTags = computed(() =>
  [...tags.value].sort((a: any, b: any) => (b.articleCount || 0) - (a.articleCount || 0)).slice(0, 8)
)

/** Hero 中间栏的站点概览：全部复用首屏 SSR 数据，不额外发请求 */
const siteStats = computed(() => [
  { label: '篇文章', value: totalArticles.value },
  { label: '个分类', value: categoryList.value.length },
  { label: '个标签', value: tags.value.length }
])

/** 拉取文章列表（只返回数据，便于首屏 SSR 序列化复用） */
async function loadArticles(): Promise<{ records: any[]; pages: number; total: number }> {
  const params = new URLSearchParams({ pageNum: String(pageNum.value), pageSize: String(15) })
  if (currentCategory.value) params.set('categoryId', String(currentCategory.value))
  if (currentTag.value) params.set('tagId', String(currentTag.value))
  if (keyword.value) params.set('keyword', keyword.value)
  if (sortMode.value) params.set('sort', sortMode.value)
  const res = await get<any>(`/article/list?${params.toString()}`)
  if (res?.code === 200 && res.data) {
    return {
      records: res.data.records || [],
      pages: res.data.pages || 0,
      total: res.data.total || 0
    }
  }
  return { records: [], pages: 0, total: 0 }
}

/** 用户翻页 / 切换筛选时的增量加载（首屏由下方 useAsyncData 负责） */
async function fetchArticles() {
  loading.value = true
  try {
    const { records, pages, total } = await loadArticles()
    articles.value = records
    totalPages.value = pages
    totalMatched.value = total
  } catch { /* 网络异常不阻塞页面 */ } finally { loading.value = false }
}

// 首屏数据在服务端取好：Nuxt payload 会把它序列化给客户端，hydration 时不再重复请求；
// 同时爬虫/用户拿到的首屏 HTML 里就带有文章列表（此前 init() 是「发射后不管」，首屏只有骨架屏 + 客户端请求两次）。
const { data: homeInit } = await useAsyncData('home-init', async () => {
  const [catRes, tagRes, totalRes, art] = await Promise.all([
    get<any>('/category/list').catch(() => null),
    get<any>('/article/tags').catch(() => null),
    // 只要总数给 Hero 的「站点概览」用：pageSize=1 即可，
    // 之前拉 5 条是为了做「热门文章」榜，榜单已移除，没必要再多传 5 篇的字段。
    get<any>('/article/list?pageSize=1').catch(() => null),
    loadArticles().catch(() => ({ records: [], pages: 0, total: 0 }))
  ])
  const categories: any[] = catRes?.code === 200 ? (catRes.data || []) : []
  return {
    categories,
    tags: tagRes?.code === 200 ? (tagRes.data || []) : [],
    articles: art.records,
    totalPages: art.pages,
    totalMatched: art.total,
    totalArticles: totalRes?.code === 200 ? (totalRes.data?.total || 0) : 0
  }
})

// 用 payload 初始化响应式状态：服务端与客户端 hydration 得到同一份数据，避免内容闪烁与 hydration mismatch
if (homeInit.value) {
  const d = homeInit.value
  categoryList.value = d.categories
  categoryTree.value = buildCategoryTree(d.categories)
  tags.value = d.tags
  articles.value = d.articles
  totalPages.value = d.totalPages
  totalMatched.value = d.totalMatched || 0
  totalArticles.value = d.totalArticles || 0
}
loading.value = false

watch([pageNum, currentCategory, currentTag], () => { keyword.value = ''; fetchArticles() })
watch(sortMode, () => { pageNum.value = 1; fetchArticles() })
watch(() => route.query.keyword, (val) => {
  keyword.value = (val as string) || ''
  currentCategory.value = undefined
  currentTag.value = undefined
  pageNum.value = 1
  fetchArticles()
})
watch(() => route.query.tagId, (val) => {
  currentTag.value = val ? Number(val) : undefined
  currentCategory.value = undefined
  keyword.value = ''
  pageNum.value = 1
  fetchArticles()
})

// 站点名 / 描述也走后台「站点设置」：填了就用，没填回落到原有文案（SEO 不能因为漏配而变空）
const siteName = computed(() => (siteCfg.value?.siteName || '').trim() || '博观')
const siteDesc = computed(() => (siteCfg.value?.siteDesc || '').trim()
  || '一个程序员的个人技术博客：记录 Java / SpringBoot / Vue / Nuxt / Python / Linux / Docker 等技术思考、编程心得与学习笔记。')

useSeoMeta({
  title: () => `${siteName.value} - codeup.asia`,
  description: () => siteDesc.value,
  ogTitle: () => `${siteName.value} - codeup.asia`,
  ogDescription: () => siteDesc.value,
  ogImage: 'https://codeup.asia/apple-touch-icon.png',
  ogType: 'website'
})
useHead({ link: [{ rel: 'canonical', href: 'https://codeup.asia/' }] })
</script>
