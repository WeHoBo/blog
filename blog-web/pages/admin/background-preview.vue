<template>
  <div v-if="ready" inert class="min-h-screen relative" :class="{ 'edition-shell': reference, 'edition-side-shell': rail }">
    <WallpaperLayer v-if="visible && (mode !== 'banner' || current.layout !== 'platform')" />
    <div class="editorial relative z-10">
      <Header preview />
      <main id="site-main" class="px-4" :class="{ 'home-banner': !reference }"><HomePage /></main>
    </div>
  </div>
</template>

<script setup lang="ts">
import HomePage from '~/pages/index.vue'
import type { SiteConfig } from '~/composables/useSiteConfig'
import { isSkinId } from '~/composables/useSkin'
import { parseWallpaperMode } from '~/composables/useWallpaper'

definePageMeta({ layout: false, middleware: 'admin' })
const { current, reference, rail } = useReferenceShell()
const { mode, wave, preference, visible, failedImage, hydrate, setImage } = useWallpaper()
// 只同步这个 iframe 内的状态；父窗口的偏好由其设置面板持久化。
const { setSkin } = useSkin()
const ready = ref(false)
const dark = ref(false)
const { data: settings } = await useSiteConfig()

useHead({
  meta: [{ name: 'robots', content: 'noindex, nofollow' }],
  htmlAttrs: {
    'data-skin': () => current.value.id,
    'data-wallpaper': () => mode.value,
    'data-has-wallpaper': () => visible.value ? '1' : '0'
  }
})

watch(failedImage, url => {
  if (url) window.parent.postMessage({ type: 'blog-preview-image-error', url }, window.location.origin)
})

function receive(event: MessageEvent) {
  if (window.parent === window || event.source !== window.parent || event.origin !== window.location.origin) return
  if (event.data?.type !== 'blog-preview-update' || !event.data.draft) return
  const draft = event.data.draft as SiteConfig
  if (isSkinId(event.data.skin)) setSkin(event.data.skin, { persist: false })
  mode.value = parseWallpaperMode(event.data.mode)
  preference.value = event.data.preference === 'skin' ? 'skin' : 'site'
  wave.value = event.data.wave === true
  if (Number.isFinite(event.data.hue)) document.documentElement.style.setProperty('--ph', String(event.data.hue))
  const next = String(draft.bgImage || '').trim()
  setImage(next)
  // 仅覆盖 iframe 内这一份 SSR 数据，不调用设置保存、主题偏好或壁纸持久化接口。
  settings.value = { ...settings.value, ...draft }
  dark.value = event.data.dark === true
  document.documentElement.classList.toggle('dark', dark.value)
  document.documentElement.classList.toggle('light', !dark.value)
  ready.value = true
}

onMounted(() => {
  hydrate()
  window.addEventListener('message', receive)
  if (window.parent !== window) window.parent.postMessage({ type: 'blog-preview-ready' }, window.location.origin)
})
onBeforeUnmount(() => window.removeEventListener('message', receive))
</script>
