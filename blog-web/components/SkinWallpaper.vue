<template>
  <span v-if="skin.wallpaper" aria-hidden="true" class="skin-wp-image absolute inset-0"
    :data-tone="dark === undefined ? undefined : dark ? 'dark' : 'light'" :style="surface" />
</template>

<script setup lang="ts">
import type { SkinDef } from '~/composables/useSkin'

const props = withDefaults(defineProps<{ skin: SkinDef; dark?: boolean }>(), { dark: undefined })
const surface = computed(() => {
  const art = props.skin.wallpaper
  if (!art) return {}
  const photo = art.image ? `url("${art.image}")` : ''
  return {
    '--skin-gradient': photo ? `${photo}, ${art.gradient}` : art.gradient,
    '--skin-gradient-dark': photo ? `linear-gradient(#18232baa, #1c232dc7), ${photo}, ${art.darkGradient}` : art.darkGradient,
  }
})
</script>

<style scoped>
.skin-wp-image { background-size: cover; background-position: center; }
.skin-wp-image[data-tone='light'] { background-image: var(--skin-gradient); }
.skin-wp-image[data-tone='dark'] { background-image: var(--skin-gradient-dark); }
</style>
