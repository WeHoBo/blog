<template>
  <header class="sticky top-0 z-50 bg-white dark:bg-gray-900 border-b dark:border-gray-800 shadow-sm">
    <div class="max-w-7xl mx-auto px-4">
      <div class="flex items-center justify-between h-14">
        <!-- Logo -->
        <NuxtLink to="/" class="flex items-center gap-1.5 flex-shrink-0 mr-6">
          <span class="text-xl sm:text-2xl font-extrabold text-gray-900 dark:text-white tracking-tight">好啵博客</span>
        </NuxtLink>

        <!-- Nav links -->
        <nav class="hidden lg:flex items-center gap-1 mr-4">
          <NuxtLink to="/" class="px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-gray-800 transition">首页</NuxtLink>
          <NuxtLink v-if="authStore.isAdmin" to="/admin/articles" class="px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-gray-800 transition">管理</NuxtLink>
          <NuxtLink to="/about" class="px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-gray-800 transition">关于</NuxtLink>
          <ColorScheme placeholder="system">
            <button @click="toggleColor" class="px-2 py-2 rounded-md text-sm text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 transition" :title="colorMode.preference === 'dark' ? '切换亮色' : '切换暗色'">
              {{ colorMode.preference === 'dark' ? '☀' : '🌙' }}
            </button>
          </ColorScheme>
        </nav>

        <!-- Search -->
        <div class="flex-1 max-w-md hidden sm:block mx-4">
          <div class="relative">
            <input v-model="searchText" @keyup.enter="doSearch" placeholder="搜索文章" class="w-full pl-9 pr-4 py-1.5 rounded-full border-2 border-gray-200 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 text-sm focus:border-blue-400 dark:focus:border-blue-500 focus:outline-none transition" />
            <span class="absolute left-3 top-1.5 text-gray-400 text-sm">🔍</span>
          </div>
        </div>

        <!-- Right actions -->
        <div class="flex items-center gap-3 flex-shrink-0">
          <template v-if="authStore.isLoggedIn">
            <NuxtLink v-if="authStore.isAdmin" to="/admin/articles/create" class="hidden sm:inline-flex px-4 py-1.5 rounded-full bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition">写文章</NuxtLink>
            <span class="hidden sm:block text-sm text-gray-500 dark:text-gray-400">{{ authStore.user?.nickname }}</span>
            <button @click="authStore.logout" class="text-sm text-gray-400 hover:text-red-500 transition">退出</button>
          </template>
          <template v-else>
            <NuxtLink to="/login" class="px-4 py-1.5 rounded-full bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition">登录/注册</NuxtLink>
          </template>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
const authStore = useAuthStore()
const colorMode = useColorMode()
const router = useRouter()
const searchText = ref('')

function toggleColor() {
  colorMode.preference = colorMode.preference === 'dark' ? 'light' : 'dark'
}

function doSearch() {
  if (searchText.value.trim()) {
    router.push(`/?keyword=${encodeURIComponent(searchText.value.trim())}`)
  }
}
</script>
