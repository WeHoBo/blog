<template>
  <span aria-hidden="true" class="wallpaper-artwork absolute inset-0 block overflow-hidden" :data-skin="skin.id" :data-tone="dark === undefined ? undefined : dark ? 'dark' : 'light'">
    <SkinWallpaper v-if="!plain" :skin="skin" :dark="dark" />
    <span v-if="url && !plain" :key="url" class="wp-image" :style="{ backgroundImage: 'url(' + JSON.stringify(url) + ')' }">
      <img ref="probe" :src="url" :data-original="image" alt="" class="absolute h-px w-px opacity-0" @error="onError($event.currentTarget as HTMLImageElement)" />
    </span>
  </span>
</template>

<script setup lang="ts">
import type { SkinDef } from '~/composables/useSkin'
import { IMG_W, ossImage } from '~/utils/image'

const props = withDefaults(defineProps<{ skin: SkinDef; image?: string; dark?: boolean; plain?: boolean; width?: number }>(), {
  image: '', dark: undefined, width: IMG_W.hero,
})
const emit = defineEmits<{ 'image-error': [url: string] }>()
const failed = ref('')
const probe = ref<HTMLImageElement | null>(null)
const url = computed(() => props.image && props.image !== failed.value ? ossImage(props.image, { w: props.width, q: 78 }) : '')
watch(() => props.image, () => { failed.value = '' }, { flush: 'sync' })
function onError(element: HTMLImageElement) {
  if (element !== probe.value || element.dataset.original !== props.image || !props.image) return
  failed.value = props.image
  emit('image-error', props.image)
}
onMounted(() => {
  if (probe.value?.complete && !probe.value.naturalWidth) onError(probe.value)
})
</script>

<style scoped>
.wallpaper-artwork { background: var(--page-bg); }
.wallpaper-artwork :deep(.wp-image) { position: absolute; inset: 0; background-size: cover; background-repeat: no-repeat; background-position: center; }
</style>
