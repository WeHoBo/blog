<template>
  <span class="miniature" :data-layout="skin.layout" :data-tone="dark ? 'dark' : 'light'"
    :style="{ '--accent': skin.accent }" aria-hidden="true">
    <WallpaperArtwork :skin="skin" :image="image" :plain="plain" :dark="dark" :width="160" />
    <span class="sketch">
      <span class="nav-strip"></span>
      <span class="masthead"><i></i><i></i></span>
      <span class="statistics"><i v-for="n in 4" :key="n"></i></span>
      <span class="cards"><i v-for="n in 3" :key="n"></i></span>
    </span>
  </span>
</template>

<script setup lang="ts">
import type { SkinDef } from '~/composables/useSkin'
defineProps<{ skin: SkinDef; image: string; plain: boolean; dark: boolean }>()
</script>

<style scoped>
.miniature { --panel: #fffcf4ee; --stroke: #615e614d; position: relative; display: block; width: 100%; aspect-ratio: 2 / 1; overflow: hidden; border-radius: 5px; }
.miniature[data-tone='dark'] { --panel: #37373cea; --stroke: #f3ece980; }
.sketch { position: absolute; inset: 9% 7%; display: grid; grid-template: 10% 32% 1fr / 1fr; gap: 6%; }
.nav-strip, .masthead, .cards i, .statistics i { border-radius: 2px; background: var(--panel); box-shadow: 0 0 0 0.5px var(--stroke); }
.nav-strip { width: 100%; }
.masthead { position: relative; display: flex; align-items: center; padding-left: 6%; }
.masthead i:first-child { width: 36%; height: 14%; min-height: 1px; background: var(--accent); }
.cards, .statistics { display: grid; gap: 4%; }
.cards { grid-template-columns: 20fr 49fr 31fr; }
.cards i { position: relative; }
.cards i::after { content: ''; position: absolute; left: 12%; top: 23%; width: 68%; height: 1px; background: var(--stroke); box-shadow: 0 3px var(--stroke); }
.statistics { display: none; }
[data-layout='platform'] .sketch, [data-layout='sidebar'] .sketch,
[data-layout='mint'] .sketch, [data-layout='album'] .sketch { grid-template: 42% 1fr / 15% 1fr; }
:is([data-layout='platform'],[data-layout='sidebar'],[data-layout='mint'],[data-layout='album']) .nav-strip { grid-row: 1 / -1; border-top: 2px solid var(--accent); }
:is([data-layout='platform'],[data-layout='sidebar']) .sketch { grid-template-rows: 36% 15% 1fr; }
:is([data-layout='platform'],[data-layout='sidebar']) .statistics { display: grid; grid-template-columns: repeat(4,1fr); }
:is([data-layout='platform'],[data-layout='sidebar'],[data-layout='mint']) .cards { grid-template-columns: 2fr 1fr; }
:is([data-layout='platform'],[data-layout='sidebar'],[data-layout='mint'],[data-layout='album']) .cards i:last-child { display: none; }
:is([data-layout='album'],[data-layout='journal']) .masthead i:nth-child(2) { position: absolute; inset: 4% 10% 3% auto; width: 21%; border: 2px solid #fff9f1; border-bottom-width: 3px; transform: rotate(7deg); background: url('/images/skins/postcard.webp') center / cover; }
[data-layout='album'] .cards { grid-template-columns: 1fr 1fr; }
[data-layout='album'] .cards i { background: linear-gradient(to bottom,#a9bda4 0 47%,var(--panel) 47%); }
[data-layout='alpine'] .masthead { background: repeating-linear-gradient(90deg,transparent 0 32%,var(--stroke) 32% 33%,transparent 33% 34%),var(--panel); }
[data-layout='journal'] .cards i:nth-child(2) { background: linear-gradient(to right,var(--panel) 66%,#bcac99 66%); }
</style>
