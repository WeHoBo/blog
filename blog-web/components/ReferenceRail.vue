<template>
  <aside class="edition-rail" aria-label="站点侧栏">
    <NuxtLink to="/" class="edition-wordmark">
      <img src="/logo.svg" alt="" width="34" height="34" />
      <strong>{{ siteName }}</strong>
    </NuxtLink>
    <nav aria-label="站点导航" class="edition-rail-links">
      <NuxtLink v-for="item in items" :key="item.to" :to="item.to"
        :class="{ selected: active(item.to) }" :aria-current="active(item.to) ? 'page' : undefined">
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path :d="item.icon" /></svg>
        {{ item.label }}
      </NuxtLink>
    </nav>
    <button type="button" class="edition-rail-settings" @click="$emit('appearance')">
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M4 7h8m4 0h4M4 17h3m4 0h9"/><circle cx="14" cy="7" r="2.5"/><circle cx="9" cy="17" r="2.5"/></svg>
      外观设置
    </button>
    <NuxtLink to="/about" class="edition-signature" aria-label="关于博主">
      <img v-if="current.id === 'platform' && mode !== 'none'" src="/images/skins/postcard.webp" alt="" class="signature-photo" />
      <span v-else class="signature-logo"><img src="/logo.svg" alt="" width="32" height="32" /></span>
      <strong>{{ config?.bloggerName || siteName }}</strong>
      <span v-if="config?.bloggerBio">{{ config.bloggerBio }}</span>
    </NuxtLink>
  </aside>
</template>

<script setup lang="ts">
import { SITE_CONFIG_KEY, type SiteConfig } from '~/composables/useSiteConfig'

const props = defineProps<{ preview?: boolean }>()
defineEmits<{ appearance: [] }>()
const route = useRoute()
const { current } = useSkin()
const { mode } = useWallpaper()
const { data: config } = useNuxtData<SiteConfig>(SITE_CONFIG_KEY)
const siteName = computed(() => config.value?.siteName || '博观')
const items = [
  { to: '/', label: '首页', icon: 'M3 10.5 12 3l9 7.5M5.5 9v11h5v-6h3v6h5V9' },
  { to: '/?view=articles', label: '文章', icon: 'M5 4h14v16H5zM8 8h8m-8 4h8m-8 4h5' },
  { to: '/categories', label: '分类', icon: 'M3 7V5h7l2 3h9v12H3V7z' },
  { to: '/tags', label: '标签', icon: 'M3 4h8l10 10-7 7L3 10zM7.2 7.4h.1' },
  { to: '/archive', label: '归档', icon: 'M3 8V4h18v4H3zm2 1v11h14V9m-10 3h6' },
  { to: '/about', label: '关于', icon: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zm0-10v6m0-10v.1' },
  { to: '/about#links', label: '友链', icon: 'm10 8 2-2a4 4 0 0 1 6 6l-3 3m-1 1-2 2a4 4 0 0 1-6-6l3-3m0 5 6-6' },
]
function active(to: string) {
  if (props.preview) return to === '/'
  if (to === '/?view=articles') return (route.path === '/' && !!(route.query.view || route.query.keyword || route.query.categoryId || route.query.tagId)) || /^\/(post|article)\//.test(route.path)
  if (to === '/') return route.path === '/' && !route.query.view && !route.query.keyword && !route.query.categoryId && !route.query.tagId
  if (to.startsWith('/about')) return route.path === '/about' && (to.endsWith('#links') === (route.hash === '#links'))
  if (to === '/categories') return route.path === to || route.path.startsWith('/category/')
  if (to === '/tags') return route.path === to || route.path.startsWith('/tag/')
  return route.path === to
}
</script>
