<template>
  <!-- 1360 而不是默认的 1280（max-w-7xl）：1920 屏上 7xl 两侧各留 320px，
       中间栏被压到 624px，列表卡片的摘要 line-clamp-2 常断在半句。
       加宽 80px 全部给了中栏（左右两栏与栏间距不变），中栏 624 → 704。
       ⚠️ 只在 ≥1360 视口真正生效；1280–1359 区间仍是中栏 624，这是预期的。 -->
  <div>
    <!-- ⚠️ 外层这个 div 只是为了让页面保持单根（路由过渡、Teleport 都要求单根）。
         皮肤切换时整棵子树会被销毁重建，这是预期行为：换的是版式，不是样式。 -->
    <div v-if="layoutFamily === 'classic'" class="max-w-[1360px] mx-auto">
      <!-- 轻量 Hero：左侧问候 / 中间置顶堆叠 / 右侧热门标签 + 源码入口 -->
      <div v-if="!filtered" class="px-4 pt-6 pb-1 lg:px-0">
        <div class="glass rounded-2xl border shadow-paper px-6 sm:px-10 py-8 relative overflow-hidden">
          <div class="relative grid gap-6 lg:gap-8 lg:grid-cols-[1.3fr_1fr_1fr] lg:items-center">
            <!-- 左：站名式口号 + 一句写作方向 + 自我介绍 + 关注方向 + 行动按钮
                 版式来自 2026-11-24 设计稿：小标签 → 大标题 → 副标题 → 自我介绍 → 标签 → 按钮。
                 三段文案是常量（见 script 里的 HERO_TAGLINE / HERO_LEAD / HERO_INTRO_FALLBACK），
                 想换成自己的话直接改那三行；博主名与简介仍读后台「站点设置」。
                 原来那个「你好，我是 X 👋」的 logo + 小标题一并去掉：
                 站名在顶栏已经有了，Hero 里再来一遍只是重复占位。 -->
            <div class="min-w-0">
              <span class="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-medium tracking-[0.08em] theme-chip">
                PERSONAL BLOG · {{ currentYear }}
              </span>

              <h1 class="hero-title mt-3.5 text-[1.65rem] sm:text-[2.3rem] font-extrabold text-gray-900 dark:text-gray-100 leading-[1.22]">
                {{ HERO_TAGLINE }}
              </h1>

              <p class="mt-2.5 text-lg sm:text-xl text-gray-500 dark:text-gray-400 leading-relaxed">
                {{ HERO_LEAD }}
              </p>

              <p class="mt-3 text-sm text-gray-500 dark:text-gray-400 leading-relaxed">
                你好，我是{{ bloggerName }}。{{ bloggerBio || HERO_INTRO_FALLBACK }}
              </p>

              <!-- 关注方向：第一个用主题色，其余中性。
                   设计稿里第一个是蓝底、后两个灰底，这里换成主题色 ——
                   全站只有一个强调色（陶土），多一种蓝就破了色彩体系。 -->
              <div v-if="focusTags.length" class="flex flex-wrap items-center gap-2 mt-4">
                <span v-for="(t, i) in focusTags" :key="t"
                  class="text-xs px-2.5 py-1 rounded-full border"
                  :class="i === 0
                    ? 'bg-primary-50 dark:bg-primary-900/25 text-primary-700 dark:text-primary-300 border-primary-200 dark:border-primary-800'
                    : 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300 border-transparent'">
                  {{ t }}
                </span>
              </div>

              <!-- 三个按钮必须允许换行：加了「申请成为作者」之后总宽超过 1.3fr 栏宽，
                   不换行会把 Hero 撑破（窄屏更是直接溢出）。 -->
              <div class="flex flex-wrap items-center gap-4 mt-5">
                <a href="#articles"
                  class="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-85 transition">
                  开始阅读
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
                </a>
                <NuxtLink to="/about"
                  class="inline-flex items-center gap-1 text-sm text-gray-700 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 transition">
                  关于我
                  <!-- 斜向上箭头：表示「去另一个页面」，与左边「原地跳到列表」的右箭头区分开 -->
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 17 17 7M9.5 7H17v7.5"/></svg>
                </NuxtLink>
                <!-- 申请发布权限：只有访客和普通用户看得到。
                     已有权限的人不显示，免得点进来只看到一句「你已经有权限了」。 -->
                <button v-if="applyVisible" type="button" @click="openApply"
                  class="px-4 py-1.5 rounded-full border border-primary-300 dark:border-primary-700 text-primary-600 dark:text-primary-400 text-xs font-medium whitespace-nowrap hover:bg-primary-50 dark:hover:bg-primary-900/20 transition">
                  <!-- 实际授予的是 author（作者），不是 admin。
                       原来写「申请管理员发布文章」，用户点进去以为能拿管理权限，
                       通过后只是获得「写文章 + 提交审核」的资格。2026-11-23 改。 -->
                  申请成为作者
                </button>
              </div>
            </div>

            <!-- 中：置顶文章（叠压小卡 + 一键展开）。⚠️ 与下面的中栏解耦：这里是置顶，中栏是精品。
               筛选态整页退回单列表，这里让位 -->
            <div class="min-w-0 lg:border-l lg:border-gray-200/70 dark:lg:border-gray-700/70 lg:pl-7">
              <FeaturedStack :articles="pinned" :total="pinnedFeed.total" :error="pinnedFeed.error">
                <HomeFeedMore :more="pinnedFeed.hasMore" :error="pinnedFeed.error" :pending="pinnedFeed.pending" @load="pinnedFeed.next()" />
              </FeaturedStack>
            </div>

            <!-- 右：热门标签 + 源码入口（GitHub 图标与「查看源码」挨着，不再两端对齐） -->
            <div class="lg:border-l lg:border-gray-200/70 dark:lg:border-gray-700/70 lg:pl-7">
              <div class="text-[11px] font-bold text-gray-400 mb-2 tracking-wide">热门标签</div>
              <!-- 两列而不是一行流式：标签名长短差很多（「408」和「编程（计算机相关资料）」），
                   一行排下来会参差不齐、把 Hero 右栏撑得忽高忽低。 -->
              <div class="grid grid-cols-2 gap-1.5">
                <NuxtLink v-for="t in hotTags.slice(0, 6)" :key="t.id" :to="taxonomyPath('/tag', t)"
                  class="inline-flex items-baseline justify-between gap-1.5 min-w-0 px-2.5 py-1 rounded-full text-xs bg-transparent text-gray-600 dark:text-gray-300 border border-gray-200/90 dark:border-gray-600 hover:border-primary-400 hover:text-primary-600 dark:hover:text-primary-400 transition">
                  <span class="truncate">{{ t.name }}</span>
                  <span v-if="t.articleCount" class="text-[10px] opacity-50 flex-shrink-0">{{ t.articleCount }}</span>
                </NuxtLink>
                <NuxtLink v-if="hotTags.length === 0" to="/tags" class="text-xs text-primary-600 dark:text-primary-400 hover:underline">浏览全部标签 →</NuxtLink>
              </div>
              <NuxtLink to="/categories" class="inline-flex items-center gap-1 mt-2.5 text-xs text-primary-600 dark:text-primary-400 hover:underline">
                全部分类 <span aria-hidden="true">→</span>
              </NuxtLink>

              <div class="mt-3 pt-3 border-t border-gray-200/70 dark:border-gray-700/70">
                <a href="https://github.com/WeHoBo/blog" target="_blank" rel="noopener noreferrer"
                  aria-label="本站源码仓库（GitHub），点击查看源码"
                  class="inline-flex items-center gap-2 group">
                  <span class="w-6 h-6 flex-shrink-0 rounded-full bg-gray-900 dark:bg-gray-100 flex items-center justify-center text-white dark:text-gray-900 group-hover:opacity-80 transition">
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                      <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
                    </svg>
                  </span>
                  <span class="text-xs text-primary-600 dark:text-primary-400 group-hover:underline">
                    查看源码 <span aria-hidden="true">→</span>
                  </span>
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div id="articles" class="flex gap-6 py-6">
        <!-- Left sidebar: 个人信息 → 文章分类 → 发布热力图 -->
        <aside class="hidden lg:block w-52 flex-shrink-0">
          <div class="sticky top-20 space-y-4">
            <!-- 作者卡片：登录显示「我」，未登录显示站点博主。两套内容互不回落 ——
                 否则普通用户没填简介时会看到管理员那份（这个坑踩过两次）。 -->
            <div class="glass rounded-2xl shadow-sm overflow-hidden">
              <div class="p-3 text-center">
                <!-- ① 登录用户：自己的资料 -->
                <template v-if="showMine">
                  <template v-if="me">
                    <img v-if="myAvatar" :src="myAvatar" alt="" width="48" height="48" decoding="async"
                      class="w-12 h-12 mx-auto mb-2 rounded-full object-cover ring-1 ring-gray-300/70 dark:ring-gray-600" />
                    <div v-else
                      class="w-12 h-12 mx-auto mb-2 rounded-full flex items-center justify-center text-lg font-bold text-white bg-primary-600 dark:bg-primary-500">
                      {{ myInitial }}
                    </div>
                    <div class="font-bold text-gray-900 dark:text-gray-100 text-[13px] truncate">{{ myName }}</div>
                    <p v-if="myBio" class="text-[11px] text-gray-500 mt-1 leading-relaxed whitespace-pre-line line-clamp-3">{{ myBio }}</p>
                    <NuxtLink v-else to="/profile" class="block text-[11px] text-gray-400 mt-1 hover:text-primary-500 transition">还没写简介，去补一句 →</NuxtLink>
                    <NuxtLink to="/profile" class="inline-block mt-1.5 text-[11px] text-primary-600 dark:text-primary-400 hover:underline">编辑我的资料 →</NuxtLink>
                  </template>
                  <!-- 已登录但资料还没取到：占位骨架，绝不显示博主的 -->
                  <template v-else>
                    <div class="w-12 h-12 mx-auto mb-2 rounded-full bg-gray-200 dark:bg-gray-700 animate-pulse"></div>
                    <div class="h-3 w-20 mx-auto rounded bg-gray-200 dark:bg-gray-700 animate-pulse"></div>
                    <div class="h-3 w-full mt-2 rounded bg-gray-100 dark:bg-gray-700/60 animate-pulse"></div>
                    <NuxtLink to="/profile" class="inline-block mt-1.5 text-[11px] text-primary-600 dark:text-primary-400 hover:underline">编辑我的资料 →</NuxtLink>
                  </template>
                </template>

                <!-- ② 访客：站点博主（公开信息，SSR 直出） -->
                <template v-else>
                  <img src="/logo.svg" alt="" width="48" height="48" decoding="async"
                    class="w-12 h-12 mx-auto mb-2 rounded-2xl object-contain p-1.5 ring-1 ring-white/80 dark:ring-gray-600"
                    style="background: rgba(255, 255, 255, .9)" />
                  <div class="font-bold text-gray-900 dark:text-gray-100 text-[13px] truncate">{{ bloggerName }}</div>
                  <p class="text-[11px] text-gray-500 mt-1 leading-relaxed whitespace-pre-line line-clamp-3">{{ bloggerBio || '记录技术成长，分享编程心得' }}</p>
                  <NuxtLink to="/about" class="inline-block mt-1.5 text-[11px] text-primary-600 dark:text-primary-400 hover:underline">关于我 →</NuxtLink>
                </template>
              </div>
            </div>

            <div class="glass rounded-2xl shadow-sm overflow-hidden">
              <div class="px-4 py-3 border-b dark:border-gray-700 flex items-center justify-between">
                <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">文章分类</h3>
              </div>
              <div class="py-1 max-h-[38vh] overflow-y-auto">
              <!-- 全部 -->
              <button @click="clearFilters"
                :class="!currentCategory
                  ? 'bg-primary-600/10 dark:bg-primary-400/15 text-primary-800 dark:text-primary-300 border-l-2 border-primary-600'
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
                    <button v-for="child in cat.children" :key="child.id" @click="selectCategory(child)"
                      :class="currentCategory === child.id
                        ? 'bg-primary-600/10 dark:bg-primary-400/15 text-primary-800 dark:text-primary-300 border-l-2 border-primary-600'
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

            <!-- 发布热力图：分类下方的「我什么时候写过东西」 -->
            <ContributionCalendar />
          </div>
        </aside>

        <!-- Center: 精品（常态）/ 筛选结果（筛选态），整块是可滚动的「滚动屏」 -->
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

          <HomeFilterResults v-if="filtered" v-bind="filterResultProps" @clear="clearFilters" @more="articleFeed.next()" />
          <template v-else>
            <!-- Heading + 只切换中间的布局 -->
            <div class="flex items-center justify-between mb-4">
              <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                <span class="w-1 h-5 bg-primary-600 rounded-full"></span>
                精品
                <span v-if="featured.length" class="text-xs font-normal text-gray-400">{{ featuredFeed.total }} 篇</span>
              </h2>
              <!-- 只切中间：右侧「最新」固定为列表面形态，不跟着变 -->
              <button @click="toggleHomeSwap"
                :title="homeSwapped ? '把中间切回列表' : '把中间切成网格'"
                class="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-300 hover:border-primary-400 hover:text-primary-600 dark:hover:text-primary-400 transition active:scale-95">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.6" d="M8 3L4 7l4 4M4 7h16M16 21l4-4-4-4M20 17H4"/></svg>
                <span class="hidden sm:inline">{{ homeSwapped ? '切成列表' : '切成网格' }}</span>
              </button>
            </div>

            <!-- Loading -->
            <div v-if="featuredFeed.pending && !featured.length" class="space-y-3">
              <div v-for="i in 4" :key="i" class="bg-white dark:bg-gray-800 rounded-lg p-5 animate-pulse">
                <div class="flex gap-4">
                  <div class="hidden sm:block w-28 h-20 bg-gray-200 dark:bg-gray-700 rounded-lg flex-shrink-0"></div>
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
            <div v-else-if="!featuredFeed.error && centerArticles.length === 0" class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-12 text-center text-gray-400">
              <p class="text-gray-500 dark:text-gray-400 font-medium">还没有精品文章</p>
              <p v-if="isSuper" class="text-xs mt-1 opacity-70">在后台给文章勾上「设为精品」，它就会出现在首页中间</p>
              <NuxtLink v-if="isSuper" to="/admin/articles"
                class="inline-block mt-4 px-4 py-1.5 rounded-full bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-xs hover:opacity-85 transition">
                去后台挑
              </NuxtLink>
            </div>

            <!-- 滚动屏：上面滚出去的卡片被顶部渐隐盖住，滚到底有「我是有底线的」
                 高度取 1040：左右两栏的白面板是 742，1040 让中栏往下多探出约 300px，
                 视觉上像「拾光手记」那种一条长到底的列表，而不是卡在左右栏齐平的位置。
                 中栏底部到页脚横线始终留 104px（24 的容器内边距 + 80 的 footer mt-20），
                 所以不会贴到横线上。 -->
            <ScrollPane v-else :max-height="1040" :count="centerArticles.length" :complete="!featuredFeed.hasMore && !featuredFeed.error">
              <Transition name="swap" mode="out-in">
                <div :key="centerLayout">
                  <div v-if="centerLayout === 'list'" class="space-y-3">
                    <ArticleCard v-for="article in centerArticles" :key="article.id" :article="article" />
                  </div>
                  <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <ArticleCard v-for="article in centerArticles" :key="article.id" :article="article" layout="grid" />
                  </div>
                </div>
              </Transition>
              <HomeFeedMore :more="featuredFeed.hasMore" :error="featuredFeed.error" :pending="featuredFeed.pending" @load="featuredFeed.next()" />
            </ScrollPane>
          </template>

          <!-- 窄屏（<1280px）拿不到右栏，这里补一份「最新」。
               形状与右栏一致：一整块白面板 + 细线分隔，别在手机上又变回一行一张浮卡。
               手机上关掉内滚动：页面本身能滚，再套一层会让手指滚不动。 -->
          <section v-if="showSide" class="xl:hidden mt-8">
            <div class="glass-solid rounded-2xl border shadow-sm px-3.5 py-3.5">
              <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2 mb-1 px-1">
                <span class="w-1 h-5 bg-primary-600 rounded-full"></span>
                最新
                <span class="text-xs font-normal text-gray-400">{{ totalMatched }} 篇</span>
              </h2>
              <ScrollPane :scrollable="false" :count="articles.length" :complete="!articleFeed.hasMore && !articleFeed.error">
                <div class="divide-y divide-gray-100 dark:divide-gray-700/60">
                  <ArticleRow v-for="article in articles" :key="article.id" :article="article" flat />
                </div>
                <HomeFeedMore :more="articleFeed.hasMore" :error="articleFeed.error" :pending="articleFeed.pending" @load="articleFeed.next()" />
              </ScrollPane>
            </div>
          </section>
        </main>

        <!-- Right sidebar: 最新，固定列表面（紧凑行），自己的滚动条
             形状按 2026-11-24 第二版设计稿：**一整块白面板**，不是每行一张浮卡。
             面板里从上到下是 标题 → 分区胶囊 → 细线分隔的行。
             行与行之间只有一条 1px 线（divide-y），没有间距 —— 那样才读得成「一列」。 -->
        <aside v-if="showSide" class="hidden xl:block w-[25rem] flex-shrink-0">
          <div class="glass-solid rounded-2xl border shadow-sm px-3.5 py-3.5">
            <div class="flex items-baseline justify-between mb-2.5 px-1">
              <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                <span class="w-1 h-5 bg-primary-600 rounded-full"></span>
                {{ sideCatName }}
              </h2>
              <span class="text-xs text-gray-400">{{ sideFeed.total }} 篇</span>
            </div>

            <!-- 分区切换：按分类看，避免「想找实验报告得刷过十几篇外文阅读」。
                 ⚠️ 这是**右栏局部**的筛选，与顶栏搜索/左栏分类抽屉那套全局筛选互不干扰：
                 全局筛选一旦生效，整页会退回单列表（showSide=false），这里直接不渲染。 -->
            <div v-if="sideCats.length" class="flex flex-wrap gap-1.5 mb-2 px-1">
              <button type="button" @click="selectSideCat(null)"
                class="px-2.5 py-1 rounded-full text-[11px] border transition"
                :class="sideCatId === null
                  ? 'bg-primary-600 text-white border-primary-600'
                  : 'bg-white border-gray-200 dark:bg-white/5 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:border-primary-400 hover:text-primary-600 dark:hover:text-primary-400'">
                全部
              </button>
              <button v-for="c in sideCats" :key="c.id" type="button" @click="selectSideCat(c.id)"
                class="px-2.5 py-1 rounded-full text-[11px] border transition"
                :class="sideCatId === c.id
                  ? 'bg-primary-600 text-white border-primary-600'
                  : 'bg-white border-gray-200 dark:bg-white/5 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:border-primary-400 hover:text-primary-600 dark:hover:text-primary-400'">
                {{ c.name }}
              </button>
            </div>

            <ScrollPane :max-height="sidePaneMax" :count="sideList.length" :complete="!sideFeed.hasMore && !sideFeed.error">
              <div class="divide-y divide-gray-100 dark:divide-gray-700/60" :class="sideCatLoading ? 'opacity-50 transition-opacity' : ''">
                <ArticleRow v-for="article in sideList" :key="article.id" :article="article" flat />
                <p v-if="!sideCatLoading && !sideFeed.error && sideList.length === 0" class="text-xs text-gray-400 py-6 text-center">
                  这个分区还没有文章
                </p>
              </div>
              <HomeFeedMore :more="sideFeed.hasMore" :error="sideFeed.error" :pending="sideFeed.pending" @load="sideFeed.next()" />
            </ScrollPane>
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
                <button @click="clearFilters(); mobileCatOpen = false"
                  :class="!currentCategory ? 'bg-primary-600/10 dark:bg-primary-400/15 text-primary-800 dark:text-primary-300' : 'text-gray-600 dark:text-gray-400'"
                  class="w-full text-left px-4 py-2.5 rounded-xl text-sm font-medium transition flex items-center gap-2">
                  <span class="flex-1">全部文章</span>
                </button>
                <template v-for="cat in categoryTree" :key="cat.id">
                  <button @click="selectCategory(cat); mobileCatOpen = false"
                    :class="currentCategory === cat.id ? 'bg-primary-600/10 dark:bg-primary-400/15 text-primary-800 dark:text-primary-300' : 'text-gray-600 dark:text-gray-400'"
                    class="w-full text-left px-4 py-2.5 rounded-xl text-sm transition flex items-center gap-2 mt-0.5">
                    <span v-html="catIcon(cat.name)" class="flex-shrink-0"></span>
                    <span class="flex-1">{{ cat.name }}</span>
                    <span class="text-xs opacity-50">{{ cat.articleCount || 0 }}</span>
                  </button>
                  <div v-if="cat.children && cat.children.length" class="ml-6 border-l border-gray-100 dark:border-gray-700 pl-2">
                    <button v-for="child in cat.children" :key="child.id" @click="selectCategory(child); mobileCatOpen = false"
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

      <!-- 申请发布权限：题目与判分都在后端，弹窗只负责收集选项 -->
      <ApplyAuthorDialog v-model:open="applyOpen" />
    </div>

    <!-- 另外两套版式。数据全部来自上面那份（同一批 useAsyncData），
         它们自己不发请求，只换个排版方式。 -->
    <HomeSkinSidebar
      v-else-if="layoutFamily === 'sidebar'"
      :featured="featured"
      :featured-total="featuredFeed.total"
      :error="articleFeed.error || featuredFeed.error"
      :article-total="totalMatched + featuredFeed.total"
      :articles="articles"
      :categories="categoryList"
      :tags="tags"
      :loading="loading"
      :site-name="siteName"
      :site-desc="siteDesc"
      :blogger-name="bloggerName"
      :blogger-bio="bloggerBio"
      :author="cardAuthor"
    >
      <template #more>
        <div v-if="featuredFeed.hasMore || featuredFeed.error" aria-label="精品文章">
          <HomeFeedMore :more="featuredFeed.hasMore" :error="featuredFeed.error" :pending="featuredFeed.pending" @load="featuredFeed.next()" />
        </div>
        <HomeFeedMore v-else :more="articleFeed.hasMore" :error="articleFeed.error" :pending="articleFeed.pending" @load="articleFeed.next()" />
      </template>
      <template v-if="filtered" #results>
        <HomeFilterResults v-bind="filterResultProps" @clear="clearFilters" @more="articleFeed.next()" />
      </template>
    </HomeSkinSidebar>
    <HomeSkinMint
      v-else-if="layoutFamily === 'mint'"
      :featured="featured"
      :featured-total="featuredFeed.total"
      :error="articleFeed.error || featuredFeed.error"
      :article-total="totalMatched + featuredFeed.total"
      :articles="articles"
      :loading="loading"
      :blogger-name="bloggerName"
      :blogger-bio="bloggerBio"
      :author="cardAuthor"
    >
      <template #more>
        <div v-if="featuredFeed.hasMore || featuredFeed.error" aria-label="精品文章">
          <HomeFeedMore :more="featuredFeed.hasMore" :error="featuredFeed.error" :pending="featuredFeed.pending" @load="featuredFeed.next()" />
        </div>
        <HomeFeedMore v-else :more="articleFeed.hasMore" :error="articleFeed.error" :pending="articleFeed.pending" @load="articleFeed.next()" />
      </template>
      <template v-if="filtered" #results>
        <HomeFilterResults v-bind="filterResultProps" @clear="clearFilters" @more="articleFeed.next()" />
      </template>
    </HomeSkinMint>
    <HomeReferenceEdition
      v-else
      :key="layoutFamily"
      :edition="layoutFamily"
      :feed="articleFeed"
      :featured="featuredFeed"
      :pinned="pinnedFeed"
      :categories="categoryList"
      :tags="hotTags"
      :tag-total="tags.length"
      :listing="referenceListing"
      :filtered="filtered"
      :title="pageTitle"
      :keyword="keyword"
      :empty-text="emptyText"
      :site-name="siteName"
      :site-desc="siteCfg?.siteDesc || ''"
      :blogger-name="bloggerName"
      :blogger-bio="bloggerBio"
      :blogger-avatar="siteCfg?.bloggerAvatar"
      :author="cardAuthor"
      :write-path="me?.role === 'author' ? '/author/create' : ['admin','super'].includes(me?.role) ? '/admin/articles/create' : ''"
      @clear="clearFilters"
    />
  </div>
</template>

<script setup lang="ts">
// 标签改跳独立落地页 /tag/{slug}，而不是回退到首页的 ?tagId= 过滤
// （聚合页此前没有独立 URL，等于把 SEO 权重都让给了首页）
import { taxonomyPath } from '~/utils/taxonomy'
import { isReferenceLayout } from '~/composables/useSkin'

const { get } = useApi()
const route = useRoute()
const router = useRouter()

// 每个列表按需继续取数，服务端 total 决定是否还有下一页。
const LIST_PAGE_SIZE = 24

const keyword = ref((route.query.keyword as string) || '')
const currentCategory = ref<number | undefined>(route.query.categoryId ? Number(route.query.categoryId) : undefined)
const currentTag = ref<number | undefined>(route.query.tagId ? Number(route.query.tagId) : undefined)
const articleFeed = useHomeFeed(loadArticles, LIST_PAGE_SIZE)
const featuredFeed = useHomeFeed(page => loadPage(page, new URLSearchParams({ featured: '1' })), LIST_PAGE_SIZE)
const pinnedFeed = useHomeFeed(page => loadPage(page, new URLSearchParams({ top: '1' })), LIST_PAGE_SIZE)
const articles = computed(() => articleFeed.records)
const featured = computed(() => featuredFeed.records)
const pinned = computed(() => pinnedFeed.records)
const categoryTree = ref<any[]>([])
const categoryList = ref<any[]>([])
const tags = ref<any[]>([])
const loading = computed(() => articleFeed.pending && !articleFeed.records.length)
const mobileCatOpen = ref(false)
/** 当前列表的数据总量（筛选态的命中数、右栏标题都用它） */
const totalMatched = computed(() => articleFeed.total)

const { sortMode, homeSwapped, toggleHomeSwap, restoreHomeSwap } = useLayout()

/**
 * 首页版式族。
 *
 * <p>皮肤存的是 cookie（不是 localStorage），所以 SSR 阶段读得到 —— 服务端直接
 * 按用户选的版式吐 HTML，不会「先暖纸三栏、水合后突然变成薄荷大卡」。
 * 代价是每种版式的数据必须在服务端一次备齐，所以下面三个版式共用同一批请求。
 */
const { current: currentSkin } = useSkin()

const layoutFamily = computed(() => currentSkin.value.layout)
const referenceEdition = computed(() => isReferenceLayout(layoutFamily.value))
const referenceListing = computed(() => route.query.view === 'articles' || filtered.value)

/** 是否处于「搜索 / 分类 / 标签」筛选态：此时整页退回单列表 */
const filtered = computed(() => !!keyword.value || !!currentCategory.value || !!currentTag.value)

/** 中栏：常态是精品，筛选态是结果 */
const centerArticles = computed(() => (filtered.value ? articles.value : featured.value))
/** 右栏与窄屏「最新」：筛选态让位给中栏，不重复展示 */
const showSide = computed(() => !filtered.value)

/** 中栏形态：false = 列表，true = 网格。右侧固定列表，不参与互换 */
const centerLayout = computed((): 'grid' | 'list' => (homeSwapped.value ? 'grid' : 'list'))

/** 只有超管能看到「去后台挑」的入口（mounted 后才 true，避免水合不一致） */
const isSuper = useIsSuper()

/* 左栏作者卡片要显示「当前这个登录用户自己」的头像/昵称/简介。 */
const authStore = useAuthStore()

/* 横幅文案统一走 useSiteConfig（与布局里的壁纸共用同一次请求），取不到就回落到原文案。 */
const { data: siteCfg } = await useSiteConfig()

/* ---------------------------------------------------------------
   横幅文案（三段）
   ---------------------------------------------------------------
   设计稿给的是一句口号 + 一句写作方向 + 一句自我介绍。站内没有这三个字段，
   先写成常量：要换成自己的话，直接改下面三行，不用动布局。
   将来若要让后台可配，再迁到 site_config（key-value 表，加 key 不需要 DDL）。

   为什么不直接复用 siteDesc：那是给 SEO 的长描述（一百多字），
   塞到大标题下面会把整个横幅撑成一篇短文。
   --------------------------------------------------------------- */
const HERO_TAGLINE = '保持好奇，持续探索。'
const HERO_LEAD = '用代码记录成长，用文字观察世界。'
const HERO_INTRO_FALLBACK = '一名持续探索计算机技术、AI 和知识世界的学习者。'

/** 小标签上的年份。SSR 与客户端在同一秒内渲染，不存在水合不一致 */
const currentYear = new Date().getFullYear()

const bloggerName = computed(() => (siteCfg.value?.bloggerName || '').trim() || '博观')
// 简介允许分多行（后台是 textarea），横幅里要当一句话读：把换行压成空格，
// 否则「你好，我是博观。」后面会突然断行，看起来像两段不相关的话。
const bloggerBio = computed(() => (siteCfg.value?.bloggerBio || '').trim().replace(/\s*\n+\s*/g, ' '))
const focusTags = computed(() => {
  const raw = (siteCfg.value?.bloggerFocus || '').trim()
  if (!raw) return ['Java', 'AI', 'Python', 'Linux', 'Docker']
  return [...new Set(raw.split(/[,，]/).map((s) => s.trim()).filter(Boolean))].slice(0, 6)
})

/* ---------------------------------------------------------------
   左栏「作者卡片」有两套内容，**绝不能互相回落**：
   - 访客（未登录）→ 站点博主的公开信息
   - 登录用户      → 严格显示他自己的头像/昵称/简介
   没填就显示占位文案，没传头像就显示昵称首字母，绝不借用别人的。
   ⚠️ 宁可在服务端多问一次 `/auth/me`：`authStore.user` 在 localStorage，
   SSR 读不到，直接渲染会「服务端吐 A、水合后变 B」。

   🔴🔴 2026-11-25 修的一个真 bug：
   `authStore.isLoggedIn` 依赖 store 里的 `useCookie('token')`，而 **pinia 的
   setup store 跑在 Nuxt 请求上下文之外，SSR 阶段读不到 cookie** ——
   实测 `useRequestHeaders(['cookie'])` 里明明有 `token=...`，store 里却是空。
   后果：`home-viewer` 永远直接 return null，登录用户在服务端一律被当成访客，
   「青野/暖纸」靠客户端 onMounted 补一刀才勉强显示自己（会闪），
   其它版式（薄荷/侧栏/四套参考版）干脆永远显示站长的名字。
   所以这里改成：**直接从请求头里抠 token**，不经过 store。
   --------------------------------------------------------------- */

/** 从任意 Cookie 串（请求头 或 document.cookie）里取 token */
function tokenFromCookieString(raw: string) {
  const m = raw.match(/(?:^|;\s*)token=([^;]*)/)
  return m && m[1] ? decodeURIComponent(m[1]) : ''
}

/**
 * 这次页面会话的 token。
 *
 * <p>服务端只能从请求头拿（store 在 SSR 读不到，见上面那段）；客户端**必须**读
 * `document.cookie`，不能写 `authStore.token.value` —— pinia 的 setup store 在实例上
 * 已经把 ref 解包成字符串，`.value` 恒为 undefined，于是水合时判定成「没登录」，
 * 卡片会从「我自己」翻回「站长」。SSR HTML 里明明是对的，打开浏览器却变了，就是这个。
 */
const activeToken = computed(() => {
  if (import.meta.server) return tokenFromCookieString(useRequestHeaders(['cookie']).cookie || '')
  const fromCookie = tokenFromCookieString(document.cookie)
  if (fromCookie) return fromCookie
  const st = authStore as any
  return typeof st.token === 'string' ? st.token : ''
})

const { data: viewer } = await useAsyncData('home-viewer', async () => {
  if (!activeToken.value) return null
  // 不用 useApi 的 get()：它内部的 useCookie 在服务端同样拿不到 token，
  // 会发出一个没有 Authorization 的请求（必然 401）。这里显式带上。
  try {
    const res = await $fetch<any>('/api/auth/me', {
      headers: { Authorization: `Bearer ${activeToken.value}` },
    })
    return res?.code === 200 ? res.data : null
  } catch {
    return null
  }
})

const showMine = computed(() => !!activeToken.value)

const clientUser = ref<any>(null)
onMounted(() => {
  if (!viewer.value && authStore.user) clientUser.value = authStore.user
  // 互换状态只能在浏览器里恢复：SSR 读不到 localStorage
  restoreHomeSwap()
})
const me = computed(() => viewer.value || clientUser.value || null)

/* ---------------- 申请发布权限（首页入口） ---------------- */
const applyOpen = ref(false)

/**
 * 只有访客和普通用户看得到这个入口。
 *
 * <p>判断用 `me`（服务端 /auth/me 的结果）而不是 `authStore.user`：
 * 后者存在 localStorage，SSR 时是 null，直接拿来判会「服务端显示按钮、水合后消失」。
 * 作者和管理员已经不需要申请，藏起来免得点进来只看到一句「你已经有权限了」。
 */
const applyVisible = computed(() => {
  const role = me.value?.role
  return !role || role === 'user'
})

function openApply() {
  // 答题必须知道是谁，未登录先去登录（本站唯一登录方式是 OAuth）
  if (!authStore.isLoggedIn) {
    navigateTo('/login')
    return
  }
  applyOpen.value = true
}

const myAvatar = computed(() => (me.value?.avatar || '').trim())
const myName = computed(() => {
  const v = me.value
  return ((v?.nickname || v?.username || '').trim()) || '我'
})
const myInitial = computed(() => (myName.value || '?').slice(0, 1).toUpperCase())
const myBio = computed(() => (me.value?.bio || '').trim())

/**
 * 首页「个人信息卡」显示谁 —— 所有版式共用这一份判断。
 *
 * <p>登录：头像 / 昵称 / 自己的简介 / 去「编辑我的资料」。
 * 访客：站点 logo / 博主名 / 博主简介 / 去「关于我」。
 *
 * <p>🔴 三条显式分支，**不是** `myBio || bloggerBio` 那种回落：
 * 普通用户没填简介时只能显示「去补一句」，绝不能掉到站长那份简介上。
 * 已登录但 `/auth/me` 还没回来时给 `pending`，卡片画骨架 —— 同样不许借用博主的。
 *
 * <p>为什么放这里而不是各版式自己算：`SkinSidebar` / `SkinMint` / `ReferenceEdition`
 * 原来都直接吃 `bloggerName`/`bloggerBio`，登录后看到的仍是站长的名字，这版修掉。
 */
const cardAuthor = computed(() => {
  if (!showMine.value) {
    return {
      pending: false, isMe: false, name: bloggerName.value,
      bio: bloggerBio.value || '记录技术成长，分享编程心得',
      avatar: '/logo.svg', initial: '', href: '/about', action: '关于我 →',
    }
  }
  if (!me.value) {
    return { pending: true, isMe: true, name: '', bio: '', avatar: '', initial: '', href: '/profile', action: '编辑我的资料 →' }
  }
  return {
    pending: false, isMe: true, name: myName.value, bio: myBio.value,
    avatar: myAvatar.value, initial: myInitial.value, href: '/profile', action: '编辑我的资料 →',
  }
})

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
  if (currentTag.value) return '该标签下暂无文章'
  if (currentCategory.value) return '该分类下暂无文章'
  return '暂无文章'
})

const filterResultProps = computed(() => ({
  title: pageTitle.value,
  keyword: keyword.value,
  articles: articles.value,
  total: totalMatched.value,
  loading: loading.value,
  error: articleFeed.error,
  more: articleFeed.hasMore,
  pending: articleFeed.pending,
  emptyText: emptyText.value,
  layout: layoutFamily.value === 'classic' ? centerLayout.value : 'list' as const
}))

function clearFilters() {
  keyword.value = ''
  currentCategory.value = undefined
  currentTag.value = undefined
  if (route.query.keyword !== undefined || route.query.tagId !== undefined || route.query.categoryId !== undefined) {
    const query = { ...route.query }
    delete query.keyword
    delete query.tagId
    delete query.categoryId
    router.replace({ query })
  }
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
    ? base + ' bg-primary-600/10 dark:bg-primary-400/15 text-primary-800 dark:text-primary-300 border-primary-600'
    : base + ' text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
}

function selectCategory(cat: any) {
  keyword.value = ''
  currentTag.value = undefined
  currentCategory.value = cat.id
  const query = { ...route.query }
  query.categoryId = String(cat.id)
  delete query.keyword
  delete query.tagId
  router.replace({ query })
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

/* ---------------------------------------------------------------
   右栏「分区」
   ---------------------------------------------------------------
   解决的是这个具体场景：想找 Docker 实验报告，却要先刷过十几篇外文阅读。
   做法：按**一级分类**给右栏加一排切换胶囊，点一下右栏只显示那一类。

   几个刻意的取舍：
   - **只影响右栏**，不碰 `currentCategory`。全局筛选（顶栏搜索 / 左栏分类抽屉）
     一旦生效会整页退回单列表，两套状态混在一起会互相打架。
   - **默认「全部」仍是时间流**。分区是「想看某类时点一下」，不是把首页改成
     三个固定板块 —— 后者会让 620px 滚动屏变成无限长页面，破坏现有版式。
   - 首屏不发额外请求：默认分区直接复用 `articles`（SSR 已取好的那份）。
   --------------------------------------------------------------- */
const sideCatId = ref<number | null>(null)
const categoryFeed = useHomeFeed(page => loadPage(page, new URLSearchParams({
  featured: '0', categoryId: String(sideCatId.value),
})), LIST_PAGE_SIZE)
const sideFeed = computed(() => sideCatId.value === null ? articleFeed : categoryFeed)
const sideCatLoading = computed(() => sideFeed.value.pending)

/** 分区候选：只取**有文章的一级分类**，按篇数降序取前 5（右栏只有 400px，再多就换两三行） */
const sideCats = computed(() =>
  [...categoryList.value]
    .filter((c: any) => !c.parentId && (c.articleCount || 0) > 0)
    .sort((a: any, b: any) => (b.articleCount || 0) - (a.articleCount || 0))
    .slice(0, 5)
)

/**
 * 右栏标题。未选分区时叫「最新」而不是「全部文章」——
 * 这份列表是 featured=0 的最新时间流（已排除精品，与中栏数据不重叠），
 * 叫「全部」会让访客以为中栏是它的子集，也和 /archive 的「全部文章」撞名。
 */
const sideCatName = computed(() => {
  if (sideCatId.value === null) return '最新'
  const hit = sideCats.value.find((c: any) => c.id === sideCatId.value)
  return hit ? hit.name : '最新'
})

/** 「全部」用 SSR 那份时间流；选了分区才用另取的那份 */
const sideList = computed(() => sideFeed.value.records)

/**
 * 右栏滚动区高度。
 *
 * <p>中栏固定 620，右栏原本也是 620，两栏底边齐平。加了分区胶囊之后右栏多出
 * 约 32px（一行胶囊 + 间距），不减去就会「右栏比中栏长一截」—— 也就是那种
 * 说不出哪里别扭、但一看就不齐的排版问题。这里显式减掉，别写成魔法数字。
 */
// 588/620 是「标题 + 分区胶囊已经占掉的高度」扣完之后给列表留的可视高度。
// 右栏改成一块白面板后外面多了 py-3.5（上下各 14px），这里再减 28，
// 否则两栏底边会错开（中栏是滚动屏、右栏是面板，肉眼一比就歪）。
const sidePaneMax = computed(() => (sideCats.value.length ? 560 : 592))

function selectSideCat(id: number | null) {
  if (sideCatId.value === id) return
  sideCatId.value = id
  if (id !== null) void categoryFeed.load()
  else categoryFeed.reset(null)
}

/**
 * 拉取「全部文章」列表（只返回数据，便于首屏 SSR 序列化复用）。
 * 常态下只取**非精品**的文章：精品的在中间和横幅里已经出现过，再列一遍就是重复内容。
 * 筛选态则不能过滤 —— 用户搜的是全站，把精品排除掉会漏结果。
 */
async function loadPage(page: number, params: URLSearchParams) {
  params.set('pageNum', String(page))
  params.set('pageSize', String(LIST_PAGE_SIZE))
  if (sortMode.value) params.set('sort', sortMode.value)
  const res = await get<any>(`/article/list?${params}`)
  if (res?.code !== 200 || !res.data) throw new Error('读取文章失败')
  return { records: res.data.records || [], total: Number(res.data.total || 0) }
}

function loadArticles(page: number) {
  const params = new URLSearchParams()
  if (currentCategory.value) params.set('categoryId', String(currentCategory.value))
  if (currentTag.value) params.set('tagId', String(currentTag.value))
  if (keyword.value) params.set('keyword', keyword.value)
  // 新版首页的时间流也包含精品，分页顺序和总数以同一次查询为准。
  if (!filtered.value && !referenceEdition.value) params.set('featured', '0')
  return loadPage(page, params)
}

// 首屏数据在服务端取好：Nuxt payload 会把它序列化给客户端，hydration 时不再重复请求
const { data: homeInit } = await useAsyncData('home-init:' + (referenceEdition.value ? 'all:' : 'split:') + route.fullPath.split('#')[0], async () => {
  const [catRes, tagRes, featRes, topRes, art] = await Promise.all([
    get<any>('/category/list').catch(() => null),
    get<any>('/article/tags').catch(() => null),
    loadPage(1, new URLSearchParams({ featured: '1' })).catch(() => null),
    loadPage(1, new URLSearchParams({ top: '1' })).catch(() => null),
    loadArticles(1).catch(() => null)
  ])
  const categories: any[] = catRes?.code === 200 ? (catRes.data || []) : []
  return {
    categories,
    tags: tagRes?.code === 200 ? (tagRes.data || []) : [],
    featured: featRes,
    pinned: topRes,
    articles: art
  }
})

// 用 payload 初始化响应式状态：服务端与客户端 hydration 得到同一份数据，避免内容闪烁与 hydration mismatch
watch(homeInit, d => {
  if (!d) return
  categoryList.value = d.categories
  categoryTree.value = buildCategoryTree(d.categories)
  tags.value = d.tags
  featuredFeed.reset(d.featured)
  pinnedFeed.reset(d.pinned)
  articleFeed.reset(d.articles)
}, { immediate: true })

watch([() => route.query.keyword, () => route.query.tagId, () => route.query.categoryId], ([q, tag, category]) => {
  keyword.value = typeof q === 'string' ? q : ''
  currentTag.value = tag ? Number(tag) : undefined
  currentCategory.value = category ? Number(category) : undefined
})
watch([keyword, currentCategory, currentTag, sortMode, referenceEdition], () => articleFeed.load())
watch(sortMode, () => {
  if (sideCatId.value !== null) void categoryFeed.load()
  void featuredFeed.load()
  void pinnedFeed.load()
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

<style scoped>
/* 两栏视图互换时的交叉淡入：out-in 保证不出现两套卡片同时占位导致的跳动 */
.swap-enter-active,
.swap-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.swap-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.swap-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 尊重系统「减少动态效果」设置：直接切换，不做位移与淡出 */
@media (prefers-reduced-motion: reduce) {
  .swap-enter-active,
  .swap-leave-active {
    transition: none;
  }
}
</style>
