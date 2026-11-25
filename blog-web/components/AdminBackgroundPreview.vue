<template>
  <section aria-label="首页背景预览" class="mt-4">
    <div class="flex flex-wrap items-center gap-x-4 gap-y-2 mb-2 text-xs">
      <span class="font-medium">首页预览</span>
      <label>尺寸
        <select v-model="mobile" class="rounded border bg-transparent p-1">
          <option :value="false">桌面</option><option :value="true">手机</option>
        </select>
      </label>
      <label>外观
        <select v-model="dark" class="rounded border bg-transparent p-1">
          <option :value="false">浅色</option><option :value="true">深色</option>
        </select>
      </label>
    </div>
    <div ref="container" class="overflow-hidden rounded-xl border mx-auto" :style="{ maxWidth: mobile ? '390px' : undefined, height: open ? stageHeight * scale + 'px' : undefined }">
      <iframe v-if="open" :key="attempt" ref="frame" src="/admin/background-preview" title="首页背景、导航与文章卡片预览"
        tabindex="-1" inert loading="lazy" class="block origin-top-left pointer-events-none border-0"
        :style="{ width: stageWidth + 'px', height: stageHeight + 'px', transform: 'scale(' + scale + ')' }" />
      <button v-else type="button" @click="open = true" class="w-full bg-gray-50 dark:bg-gray-900 p-3 text-sm">加载首页预览</button>
    </div>
    <p v-if="open && !connected" role="status" class="text-xs mt-2">
      正在连接首页预览…
      <button type="button" class="underline" @click="attempt++">重新加载</button>
    </p>
    <p v-if="failedUrl && failedUrl === draft.bgImage" role="status" class="text-xs mt-2 text-amber-700 dark:text-amber-400">
      图片暂时无法加载，已显示皮肤背景。请检查图片地址或重新选择文件。
    </p>
  </section>
</template>

<script setup lang="ts">
import type { SiteConfig } from '~/composables/useSiteConfig'

const props = defineProps<{ draft: SiteConfig }>()
const { skin } = useSkin()
const { mode, preference, wave } = useWallpaper()
const { hue } = useThemeColor()
const frame = ref<HTMLIFrameElement | null>(null)
const container = ref<HTMLElement | null>(null)
const open = ref(false)
const connected = ref(false)
const attempt = ref(0)
const mobile = ref(false)
const dark = ref(false)
const availableWidth = ref(320)
const stageWidth = computed(() => mobile.value ? 390 : 1440)
const stageHeight = computed(() => mobile.value ? 844 : 960)
const scale = computed(() => Math.min(1, availableWidth.value / stageWidth.value))
const failedUrl = ref('')
let observer: ResizeObserver | undefined

function sendDraft() {
  if (!frame.value?.contentWindow) return
  frame.value.contentWindow.postMessage({
    type: 'blog-preview-update', dark: dark.value, draft: { ...props.draft },
    skin: skin.value, mode: mode.value, preference: preference.value, wave: wave.value, hue: hue.value
  }, window.location.origin)
}

function receive(event: MessageEvent) {
  if (event.origin !== window.location.origin || event.source !== frame.value?.contentWindow) return
  if (event.data?.type === 'blog-preview-ready') {
    connected.value = true
    sendDraft()
  }
  if (event.data?.type === 'blog-preview-image-error') failedUrl.value = event.data.url
}

watch([() => props.draft, dark, skin, mode, preference, wave, hue], sendDraft, { deep: true })
watch(() => props.draft.bgImage, () => { failedUrl.value = ''; open.value = true })
onMounted(() => {
  window.addEventListener('message', receive)
  observer = new ResizeObserver(entries => { availableWidth.value = entries[0].contentRect.width })
  if (container.value) observer.observe(container.value)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('message', receive)
})
</script>
