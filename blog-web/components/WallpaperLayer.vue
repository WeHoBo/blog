<template>
  <div id="wallpaper-layer" class="pointer-events-none" aria-hidden="true">
    <WallpaperArtwork :skin="skin" :image="siteImage" @image-error="markImageFailed" />
    <div class="wp-scrim wp-scrim-light"></div>
    <div class="wp-scrim wp-scrim-dark"></div>
    <!-- 水波纹只在「横幅」模式有意义：full 是固定满屏、cover 是整页重复，波浪会飘在内容中间 -->
    <BannerWaves v-if="wave && bannerVisible" />
  </div>
</template>

<script setup lang="ts">
const { current: skin } = useSkin()
const { siteImage, wave, bannerVisible, markImageFailed } = useWallpaper()
</script>
