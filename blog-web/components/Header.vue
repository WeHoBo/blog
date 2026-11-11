<template>
  <header
    class="fixed top-0 inset-x-0 z-50 transition-all duration-300 border-b border-gray-300/70 backdrop-blur-md bg-gray-50/85 dark:bg-gray-900/85 dark:border-gray-700"
    :class="hidden ? '-translate-y-full' : 'translate-y-0'"
  >
    <div class="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between gap-3">
      <!-- Logo -->
      <NuxtLink to="/" class="flex items-center gap-2 flex-shrink-0 mr-2 group" style="perspective:600px">
        <div class="relative w-9 h-9 transition-transform duration-500" style="transform-style:preserve-3d" :class="{'rotate-y-180': logoFlipped}" @mouseenter="logoFlipped = true" @mouseleave="logoFlipped = false">
          <div class="absolute inset-0 flex items-center justify-center rounded-lg bg-primary-600 text-white font-bold text-sm" style="backface-visibility:hidden">B</div>
          <div class="absolute inset-0 flex items-center justify-center rounded-lg bg-primary-600 text-white font-bold text-sm" style="backface-visibility:hidden;transform:rotateY(180deg)">&lt;/&gt;</div>
        </div>
        <span class="font-extrabold text-gray-900 dark:text-white tracking-tight text-lg sm:text-xl">好啵博客</span>
      </NuxtLink>

      <!-- Desktop Nav -->
      <nav class="hidden lg:flex items-center gap-1 bg-white/40 dark:bg-white/5 rounded-full px-1 py-1 backdrop-blur-sm">
        <NuxtLink to="/" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition">首页</NuxtLink>
        <NuxtLink to="/about" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition">关于</NuxtLink>
        <NuxtLink to="/archive" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition">归档</NuxtLink>
        <NuxtLink to="/categories" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition">分类</NuxtLink>
        <NuxtLink to="/tags" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/10 transition">标签</NuxtLink>
      </nav>

      <!-- Search -->
      <div class="flex-1 max-w-md hidden sm:block mx-2">
        <div ref="searchWrap" class="relative">
          <input ref="searchInput" v-model="searchText" @keydown="onSearchKeydown" @focus="onSearchFocus" placeholder="搜索文章、标签、分类" class="w-full pl-9 pr-14 py-1.5 rounded-full border border-gray-300 bg-white dark:bg-gray-800 dark:border-gray-600 dark:text-gray-100 text-sm focus:border-primary-500 dark:focus:border-primary-500 focus:outline-none transition" />
          <span class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M21 21l-4.35-4.35M17 10.5a6.5 6.5 0 11-13 0 6.5 6.5 0 0113 0z"/></svg>
          </span>
          <span class="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-400 border dark:border-gray-600 hidden sm:block">Ctrl K</span>

          <!-- 搜索历史：聚焦即展开，↑↓ 选择、Enter 搜索、Esc 关闭 -->
          <Transition name="search-pop">
            <div v-if="historyOpen && historyItems.length"
              class="absolute left-0 right-0 top-full mt-2 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-paper overflow-hidden z-50">
              <div class="flex items-center justify-between px-3 pt-2 pb-1">
                <span class="text-[11px] text-gray-400">最近搜索</span>
                <button type="button" @click="clearHistory" class="text-[11px] text-gray-400 hover:text-primary-600 dark:hover:text-primary-400 transition">清空</button>
              </div>
              <ul class="pb-1">
                <li v-for="(h, i) in historyItems" :key="h">
                  <div class="group flex items-center gap-1 px-1.5 rounded-lg"
                    :class="i === activeIndex ? 'bg-gray-100 dark:bg-white/10' : ''"
                    @mouseenter="activeIndex = -1">
                    <button type="button" @click="pickHistory(h)"
                      class="flex-1 min-w-0 flex items-center gap-2 px-1.5 py-1.5 text-left text-sm text-gray-700 dark:text-gray-200">
                      <svg class="w-3.5 h-3.5 flex-shrink-0 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M12 8v4l3 2M3.05 11a9 9 0 1 0 .5-4.5M3 4v4h4"/></svg>
                      <span class="truncate">{{ h }}</span>
                    </button>
                    <button type="button" @click.stop="removeHistory(h)" title="删除这条历史" aria-label="删除这条历史"
                      class="flex-shrink-0 w-6 h-6 rounded flex items-center justify-center text-gray-300 dark:text-gray-500 hover:text-gray-700 dark:hover:text-gray-200 transition sm:opacity-0 sm:group-hover:opacity-100">
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M18 6 6 18M6 6l12 12"/></svg>
                    </button>
                  </div>
                </li>
              </ul>
            </div>
          </Transition>
        </div>
      </div>

      <!-- Right actions -->
      <div class="flex items-center gap-2 flex-shrink-0">
        <template v-if="authStore.isLoggedIn">
          <NuxtLink v-if="authStore.isAdmin" to="/admin/articles/create" class="hidden sm:inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-xs sm:text-sm font-medium hover:opacity-85 transition-opacity duration-200">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
            写文章
          </NuxtLink>
          <!-- User dropdown -->
          <div class="relative">
            <button @click="userOpen = !userOpen" class="hidden sm:flex items-center gap-1.5 pl-1 pr-2 py-1 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition">
              <img v-if="authStore.user?.avatar" :src="ossImage(authStore.user.avatar, { w: IMG_W.avatar })" alt="avatar" class="w-6 h-6 rounded-full object-cover" />
              <span v-else class="w-6 h-6 rounded-full bg-primary-600 text-white flex items-center justify-center text-xs font-bold">{{ authStore.user?.nickname?.[0] || 'U' }}</span>
              <span class="text-sm text-gray-500 dark:text-gray-400 max-w-[6rem] truncate">{{ authStore.user?.nickname }}</span>
              <svg class="w-3 h-3 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M19 9l-7 7-7-7"/></svg>
            </button>
            <div v-if="userOpen" class="absolute right-0 mt-2 w-44 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-100 dark:border-gray-700 py-1.5 z-50">
              <NuxtLink to="/profile" @click="userOpen = false" class="flex items-center gap-2 px-4 py-2 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
                <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                个人主页
              </NuxtLink>
              <NuxtLink v-if="authStore.isAdmin" to="/admin/articles" @click="userOpen = false" class="flex items-center gap-2 px-4 py-2 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">
                <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/></svg>
                管理后台
              </NuxtLink>
              <!-- 「设置」入口已移除：和顶栏调色板按钮完全重复，留一个就够 -->
              <div class="border-t border-gray-100 dark:border-gray-700 my-1"></div>
              <button @click="handleLogout" class="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition">
                <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/></svg>
                退出登录
              </button>
            </div>
          </div>
          <button @click="authStore.logout" class="sm:hidden text-sm text-gray-400 hover:text-red-500 transition">退出</button>
        </template>
        <template v-else>
          <NuxtLink to="/login" class="px-4 py-1.5 rounded-full bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-85 transition">登录</NuxtLink>
        </template>
        <!-- 明暗切换：与设置面板里的开关同一套逻辑（含圆形扩散过渡，圆心取本按钮） -->
        <button @click="toggleColor" class="p-1.5 rounded-md text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-white/10 transition" :title="isDark ? '切换到浅色' : '切换到深色'" :aria-label="isDark ? '切换到浅色' : '切换到深色'">
          <!-- 深色时显示太阳（点了变浅），浅色时显示月亮（点了变深）：图标表示「点了会变成什么」 -->
          <svg v-if="isDark" class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>
          <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>
        </button>
        <!-- 外观设置：调色板图标（桌面与移动端都能打开；未登录访客也需要调主图） -->
        <button @click="settingsOpen = true" class="p-1.5 rounded-md text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-white/10 transition" title="外观设置" aria-label="外观设置">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z"/><circle cx="7.6" cy="11.4" r="1.05" fill="currentColor" stroke="none"/><circle cx="12" cy="7.8" r="1.05" fill="currentColor" stroke="none"/><circle cx="16.4" cy="11.4" r="1.05" fill="currentColor" stroke="none"/></svg>
        </button>
        <!-- Hamburger：用 SVG 而非 ☰/✕ 字符，避免不同系统字形不一致、与左侧新图标风格打架 -->
        <button @click="mobileOpen = !mobileOpen" class="lg:hidden p-1.5 rounded-md text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-white/10 transition" :aria-label="mobileOpen ? '关闭菜单' : '打开菜单'" :aria-expanded="mobileOpen">
          <svg v-if="!mobileOpen" class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
          <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M18 6 6 18M6 6l12 12"/></svg>
        </button>
      </div>
    </div>

    <!-- Mobile menu -->
    <div v-if="mobileOpen" class="lg:hidden border-t dark:border-gray-700 py-3 space-y-1 pb-4 px-4">
      <NuxtLink to="/" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10">首页</NuxtLink>
      <NuxtLink v-if="authStore.isAdmin" to="/admin/articles" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10">管理后台</NuxtLink>
      <NuxtLink to="/about" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10">关于</NuxtLink>
      <NuxtLink to="/archive" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10">归档</NuxtLink>
      <NuxtLink to="/categories" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10">分类</NuxtLink>
      <NuxtLink to="/tags" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10">标签</NuxtLink>
      <NuxtLink v-if="authStore.isLoggedIn" to="/profile" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10">个人主页</NuxtLink>
      <div class="px-3 py-2">
        <input v-model="searchText" @keydown="onSearchKeydown" placeholder="搜索标题、摘要、标签、分类" class="w-full px-3 py-1.5 rounded-full border text-sm dark:bg-gray-800 dark:border-gray-600 focus:outline-none" />
      </div>
    </div>

    <!-- Settings Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="settingsOpen" class="fixed inset-0 z-[60] flex items-center justify-center p-4" @click.self="settingsOpen = false">
          <div class="absolute inset-0 bg-black/20" @click="settingsOpen = false"></div>
          <div class="relative w-full max-w-sm rounded-2xl shadow-paper border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-6">
            <div class="flex items-center justify-between mb-5">
              <h3 class="flex items-center gap-2 text-lg font-bold text-gray-900 dark:text-white">
                <svg class="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z"/><circle cx="7.6" cy="11.4" r="1.05" fill="currentColor" stroke="none"/><circle cx="12" cy="7.8" r="1.05" fill="currentColor" stroke="none"/><circle cx="16.4" cy="11.4" r="1.05" fill="currentColor" stroke="none"/></svg>
                外观设置
              </h3>
              <button @click="settingsOpen = false" class="w-8 h-8 rounded-full flex items-center justify-center text-gray-400 hover:bg-gray-100 dark:hover:bg-white/10 hover:text-gray-600 dark:hover:text-gray-200 transition" aria-label="关闭">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M18 6 6 18M6 6l12 12"/></svg>
              </button>
            </div>

            <div class="space-y-4">
              <!-- 主题色：只调色相，饱和/亮度锁死（守住单一强调色的观感）。
                   首屏由 nuxt.config 的内联脚本先把 --ph 写好，刷新不跳色。 -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <span class="flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-gray-200">
                    <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/></svg>
                    主题色
                    <button v-if="!isDefaultHue" @click="resetHue" class="p-0.5 rounded text-gray-400 hover:text-primary-600 dark:hover:text-primary-400 transition" title="恢复默认色" aria-label="恢复默认色">
                      <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/></svg>
                    </button>
                  </span>
                  <span class="px-2 py-0.5 rounded-md bg-gray-100 dark:bg-white/10 text-xs font-bold tabular-nums text-gray-600 dark:text-gray-300">{{ hue }}</span>
                </div>
                <input
                  class="hue-slider"
                  type="range"
                  :min="0"
                  :max="360"
                  :step="5"
                  :value="hue"
                  aria-label="主题色相"
                  @input="onHueInput"
                />
                <p class="mt-2 text-[11px] leading-relaxed text-gray-400">只改变强调色（按钮、链接、标签）。纸面底色与正文墨色不动。</p>
              </div>

              <!-- 主图背景：壁纸显示方式 -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <span class="flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-gray-200">
                    <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21"/></svg>
                    主图背景
                  </span>
                  <span class="text-xs text-gray-400">{{ currentWallpaperLabel }}</span>
                </div>
                <div class="grid grid-cols-4 gap-1 p-1 rounded-xl bg-gray-50 dark:bg-white/5 border border-gray-300 dark:border-gray-700">
                  <button
                    v-for="m in wallpaperOptions"
                    :key="m.value"
                    @click="setMode(m.value)"
                    class="flex flex-col items-center gap-1 py-2 rounded-lg text-[11px] transition"
                    :class="mode === m.value
                      ? 'bg-white dark:bg-gray-800 text-gray-900 dark:text-white shadow-sm ring-1 ring-gray-300 dark:ring-gray-600'
                      : 'text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path :d="m.d" /></svg>
                    <span>{{ m.label }}</span>
                  </button>
                </div>
                <p class="mt-2 text-[11px] leading-relaxed text-gray-400">{{ currentWallpaperHint }}</p>
              </div>

              <!-- 波浪动画：横幅底部的水波纹（Banner Options → Waves Animation） -->
              <div class="flex items-center justify-between py-2">
                <span class="flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-gray-200">
                  <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M2 7c.6.5 1.2 1 2.5 1C7 8 7 6 9.5 6c2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1"/><path d="M2 13c.6.5 1.2 1 2.5 1C7 14 7 12 9.5 12c2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1"/><path d="M2 19c.6.5 1.2 1 2.5 1C7 20 7 18 9.5 18c2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1"/></svg>
                  波浪动画
                  <span v-if="mode !== 'banner'" class="text-[10px] text-gray-400">（仅横幅模式可见）</span>
                </span>
                <button
                  @click="setWave(!wave)"
                  class="relative w-12 h-6 rounded-full transition-colors duration-200 flex-shrink-0"
                  :class="wave ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'"
                  role="switch"
                  :aria-checked="wave"
                  aria-label="波浪动画"
                >
                  <span class="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200" :class="wave ? 'translate-x-6' : ''"></span>
                </button>
              </div>

              <!-- 文章布局 -->
              <div class="flex items-center justify-between py-2">
                <span class="flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-gray-200">
                  <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="7" rx="1"/><rect x="3" y="14" width="18" height="7" rx="1"/></svg>
                  文章布局
                </span>
                <div class="flex items-center gap-1 bg-gray-50 dark:bg-white/5 rounded-full p-1 border border-gray-300 dark:border-gray-700">
                  <button @click="setLayout('list')" class="px-3 py-1 rounded-full text-xs transition" :class="layout === 'list' ? 'bg-primary-600 text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700'">列表</button>
                  <button @click="setLayout('grid')" class="px-3 py-1 rounded-full text-xs transition" :class="layout === 'grid' ? 'bg-primary-600 text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700'">网格</button>
                </div>
              </div>

              <!-- 文章排序 -->
              <div class="flex items-center justify-between py-2">
                <span class="flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-gray-200">
                  <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="m3 16 4 4 4-4"/><path d="M7 20V4"/><path d="m21 8-4-4-4 4"/><path d="M17 4v16"/></svg>
                  文章排序
                </span>
                <div class="flex items-center gap-1 bg-gray-50 dark:bg-white/5 rounded-full p-1 border border-gray-300 dark:border-gray-700">
                  <button @click="setSort('time')" class="px-3 py-1 rounded-full text-xs transition" :class="sort === 'time' ? 'bg-primary-600 text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700'">发布时间</button>
                  <button @click="setSort('title')" class="px-3 py-1 rounded-full text-xs transition" :class="sort === 'title' ? 'bg-primary-600 text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700'">标题首字母</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </header>
</template>

<script setup lang="ts">
import type { WallpaperMode } from '~/composables/useWallpaper'
import { ossImage, IMG_W } from '~/utils/image'
import { withViewTransition, originFromElement } from '~/utils/viewTransition'

const authStore = useAuthStore()
const colorMode = useColorMode()
const router = useRouter()
const route = useRoute()
const searchText = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
const searchWrap = ref<HTMLElement | null>(null)
const historyOpen = ref(false)
const activeIndex = ref(-1)
const mobileOpen = ref(false)
const logoFlipped = ref(false)
const settingsOpen = ref(false)
const userOpen = ref(false)

const {
  items: historyItems,
  load: loadHistory,
  add: addHistory,
  remove: removeHistory,
  clear: clearHistory
} = useSearchHistory()

const hidden = ref(false)
let lastScrollY = 0
let scrollRafId = 0

function handleLogout() {
  userOpen.value = false
  authStore.logout()
  router.push('/')
}

// Ctrl+K / Cmd+K 聚焦搜索框（独立命名，便于卸载时移除）
function handleHotkey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    searchInput.value?.focus()
  }
}

function handleScroll() {
  // rAF 节流：滚动事件频率极高，直接写响应式数据会导致持续的整页重渲染
  if (scrollRafId) return
  scrollRafId = requestAnimationFrame(() => {
    scrollRafId = 0
    const y = window.scrollY
    if (y > lastScrollY && y > 60) {
      hidden.value = true
    } else if (y < lastScrollY) {
      hidden.value = false
    }
    lastScrollY = y
  })
}

onMounted(() => {
  lastScrollY = window.scrollY
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('keydown', handleHotkey)
  document.addEventListener('click', onDocumentClick)
  loadHistory()
  hydrate()
  hydrateHue()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('keydown', handleHotkey)
  document.removeEventListener('click', onDocumentClick)
  if (scrollRafId) cancelAnimationFrame(scrollRafId)
})

/** 用「实际解析后的主题」驱动开关：跟随系统时 preference 是 'system'，直接比较会永远显示为关 */
const isDark = computed(() => colorMode.value === 'dark')

function toggleColor(event?: MouseEvent) {
  const next = isDark.value ? 'light' : 'dark'
  // 圆心取开关本身（不是鼠标点）：键盘触发时没有可信的鼠标坐标
  const el = (event?.currentTarget as HTMLElement | null) || null
  withViewTransition(originFromElement(el), async () => {
    colorMode.preference = next
    // color-mode 的 watcher 是 pre-flush，class 要到下一次 tick 才落到 <html> 上；
    // 不等这一拍，浏览器拍到的新快照还是旧主题，动画等于没放
    await nextTick()
    await nextTick()
  })
}

const { mode, wave, setMode, setWave, hydrate } = useWallpaper()
const { hue, isDefaultHue, setHue, resetHue, hydrate: hydrateHue } = useThemeColor()

/** 滑块输入：只更新颜色 + 存盘（step=5，拖动最多 72 次，不必额外节流） */
function onHueInput(event: Event) {
  setHue(Number((event.target as HTMLInputElement).value))
}

const wallpaperOptions: { value: WallpaperMode; label: string; d: string }[] = [
  { value: 'banner', label: '横幅', d: 'M3 4h18v6H3zM3 13h18v7H3z' },
  { value: 'full', label: '全屏', d: 'M3 4h18v16H3z' },
  { value: 'cover', label: '覆盖', d: 'M3 4h18v16H3zM12 7.5v5M9.5 10l2.5 2.5L14.5 10' },
  { value: 'none', label: '隐藏', d: 'M3 4h18v16H3zM5 20L19 4' }
]

const currentWallpaperLabel = computed(() => wallpaperOptions.find(o => o.value === mode.value)?.label || '横幅')

const currentWallpaperHint = computed(() => {
  if (mode.value === 'banner') return '主图只出现在页首，滚动时内容从图片下方开始'
  if (mode.value === 'full') return '主图铺满整个屏幕并固定，页面滚动时图片不动'
  if (mode.value === 'cover') return '主图铺满整篇页面，跟着内容一起滚动'
  return '不显示主图，只保留纸张底色'
})

const { viewMode: layout, setViewMode: setLayout, sortMode: sort, setSortMode: setSort } = useLayout()

function doSearch() {
  mobileOpen.value = false
  userOpen.value = false
  historyOpen.value = false
  activeIndex.value = -1
  const q = searchText.value.trim()
  if (!q) return
  addHistory(q)
  router.push(`/?keyword=${encodeURIComponent(q)}`)
}

/** 点历史条目：先把词填回输入框，再走同一条搜索流程（会把它重新提到历史首位） */
function pickHistory(q: string) {
  searchText.value = q
  doSearch()
}

function onSearchFocus() {
  activeIndex.value = -1
  if (historyItems.value.length) historyOpen.value = true
}

/**
 * 键盘操作：↑↓ 在历史里移动，Enter 走当前高亮项或直接搜索，Esc 收起。
 * 没按方向键时 activeIndex 为 -1，Enter 依然是「搜我输入的内容」，不改变原有习惯。
 */
function onSearchKeydown(e: KeyboardEvent) {
  // 中文输入法用 Enter 确认候选词时同样会触发 keydown，这一下不能当成搜索
  if (e.isComposing || e.keyCode === 229) return

  const list = historyItems.value
  if (e.key === 'Escape') {
    historyOpen.value = false
    activeIndex.value = -1
    return
  }
  if (e.key === 'Enter') {
    if (historyOpen.value && activeIndex.value >= 0 && list[activeIndex.value]) {
      searchText.value = list[activeIndex.value]
    }
    doSearch()
    return
  }
  if (!list.length) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    historyOpen.value = true
    activeIndex.value = activeIndex.value >= list.length - 1 ? 0 : activeIndex.value + 1
    return
  }
  if (e.key === 'ArrowUp') {
    e.preventDefault()
    historyOpen.value = true
    activeIndex.value = activeIndex.value <= 0 ? list.length - 1 : activeIndex.value - 1
  }
}

/** 点击搜索框以外的地方收起历史面板 */
function onDocumentClick(e: MouseEvent) {
  if (!historyOpen.value) return
  const el = searchWrap.value
  if (el && e.target instanceof Node && el.contains(e.target)) return
  historyOpen.value = false
  activeIndex.value = -1
}

/** 地址栏/首页「清除筛选」改变了 keyword 时，输入框跟着同步，避免显示上一次的旧词 */
watch(
  () => route.query.keyword,
  (val) => {
    const q = typeof val === 'string' ? val : ''
    if (q !== searchText.value) searchText.value = q
  }
)
</script>

<style scoped>
/* 搜索历史面板：轻微下沉淡入，避免突然弹出 */
.search-pop-enter-active,
.search-pop-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.search-pop-enter-from,
.search-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active .relative,
.modal-leave-active .relative {
  transition: transform 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-from .relative,
.modal-leave-to .relative {
  transform: scale(0.95);
}
</style>
