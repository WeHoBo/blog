<template>
  <!-- 未设置壁纸图，或用户选择「隐藏壁纸」：回到纯纸面 -->
  <StarryBackground v-if="!image || mode === 'none'" />

  <!--
    壁纸层。z-0，正文在 z-10，永远在图片之上。
    位置/尺寸/蒙层全部由 main.css 根据 html[data-wallpaper] 决定 ——
    那个属性是 head 里的内联脚本在首次绘制前写好的，所以刷新时不会先画错再纠正。
  -->
  <div v-else id="wallpaper-layer" class="pointer-events-none" aria-hidden="true">
    <div class="wp-image" :style="{ backgroundImage: `url(${wallpaperUrl})` }"></div>
    <div class="wp-scrim wp-scrim-light"></div>
    <div class="wp-scrim wp-scrim-dark"></div>
    <!-- 水波纹只在「横幅」模式有意义：full 是固定满屏、cover 是整页重复，波浪会飘在内容中间 -->
    <BannerWaves v-if="wave && mode === 'banner'" />
  </div>
</template>

<script setup lang="ts">
import { ossImage, IMG_W } from '~/utils/image'

const { mode, image, wave, hydrate } = useWallpaper()

onMounted(hydrate)

/**
 * 壁纸按 hero 尺寸取图（1920 宽）：原图往往是相机直出的几 MB，
 * 而横幅实际只需要 1440–1920px。质量降到 78 肉眼无差，体积能小一个数量级。
 */
const wallpaperUrl = computed(() => ossImage(image.value, { w: IMG_W.hero, q: 78 }))
</script>
