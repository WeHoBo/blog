<template>
  <NuxtLink :to="articlePath(article)" class="edition-story" :data-card-variant="variant" :class="{ 'has-cover': cover }">
    <span v-if="cover" class="story-photo">
      <img :src="cover" alt="" loading="lazy" decoding="async" @error="failed = article.cover || ''" />
      <time v-if="variant === 'grid' && date" class="photo-date" :datetime="date">{{ date.slice(5) }}</time>
    </span>
    <span class="story-copy">
      <span v-if="variant === 'feature'" class="story-context">
        <span v-if="article.categoryName || article.isFeatured" class="category-stamp">{{ article.categoryName || '精选' }}</span>
        <time v-if="date" :datetime="date">{{ date }}</time>
      </span>
      <strong class="story-title" v-html="highlightKeyword(article.title, keyword)"></strong>
      <span v-if="variant !== 'compact' && article.summary" class="story-summary" v-html="highlightKeyword(article.summary, keyword)"></span>
      <span class="story-foot">
        <time v-if="variant === 'row' || variant === 'compact' || (variant === 'grid' && !cover)" :datetime="date">{{ date }}</time>
        <ReadingTime v-if="variant === 'feature' && article.wordCount" :word-count="article.wordCount" />
        <span v-if="variant === 'feature' && article.wordCount">{{ article.wordCount }} 字</span>
        <span v-if="(variant === 'grid' || variant === 'row') && article.viewCount != null" class="story-metric" :aria-label="article.viewCount + ' 次阅读'">
          <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/></svg>{{ article.viewCount }}
        </span>
        <span v-if="variant === 'grid' && article.commentCount != null" class="story-metric" :aria-label="article.commentCount + ' 条评论'">
          <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 4h16v12H9l-5 4z"/></svg>{{ article.commentCount }}
        </span>
        <span v-if="variant !== 'feature' && variant !== 'compact' && article.categoryName" class="category-stamp">{{ article.categoryName }}</span>
      </span>
    </span>
  </NuxtLink>
</template>

<script setup lang="ts">
import { articlePath } from '~/utils/articlePath'
import { ossImage } from '~/utils/image'
import { highlightKeyword } from '~/utils/highlight'

const props = defineProps<{
  article: { id: number; slug?: string; title: string; cover?: string; summary?: string; publishAt?: string;
    createTime?: string; viewCount?: number; wordCount?: number; commentCount?: number; categoryName?: string; isFeatured?: number }
  variant: 'row' | 'compact' | 'grid' | 'feature'
  keyword?: string
}>()
const failed = ref('')
const cover = computed(() => props.article.cover && props.article.cover !== failed.value
  ? ossImage(props.article.cover, { w: props.variant === 'compact' ? 200 : props.variant === 'grid' ? 800 : 440 }) : '')
const date = computed(() => (props.article.publishAt || props.article.createTime || '').slice(0, 10))
</script>

<style scoped>
.edition-story { display: flex; min-width: 0; width: 100%; color: var(--edition-ink); border-radius: 8px; align-items: stretch; }
.edition-story:focus-visible { outline: 2px solid var(--edition-accent); outline-offset: 2px; }
.story-copy { min-width: 0; flex: 1; display: flex; flex-direction: column; justify-content: center; }
.story-title, .story-summary { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; overflow-wrap: anywhere; }
.story-title { font-size: 1rem; line-height: 1.42; font-weight: 700; }
.story-summary { margin-top: .36rem; color: var(--edition-sub); font-size: .82rem; line-height: 1.6; }
.story-photo { flex: none; display: block; position: relative; overflow: hidden; border-radius: 6px; background: var(--edition-soft); }
.story-photo img { position: absolute; height: 100%; width: 100%; inset: 0; object-fit: cover; }
.story-foot, .story-context { display: flex; align-items: center; flex-wrap: wrap; gap: .36rem .72rem; color: var(--edition-sub); font-size: .69rem; line-height: 1.4; }
.story-foot { margin-top: .65rem; }
.story-context { justify-content: space-between; margin-bottom: .4rem; }
.story-metric { display: inline-flex; align-items: center; gap: .2rem; font-variant-numeric: tabular-nums; }
.story-metric svg { width: .83rem; height: .83rem; fill: none; stroke: currentColor; stroke-width: 1.55; }
.category-stamp { background: var(--edition-soft); color: var(--edition-accent); border-radius: .36rem; padding: .12rem .46rem; }
.story-foot .category-stamp { margin-left: auto; }
[data-card-variant=row] { align-items: center; gap: 1rem; padding: .68rem 0; }
[data-card-variant=row] .story-photo { width: 27%; max-width: 174px; aspect-ratio: 1.8; }
[data-card-variant=row] .story-summary { -webkit-line-clamp: 1; }
[data-card-variant=compact] { align-items: center; gap: .68rem; padding: .65rem 0; }
[data-card-variant=compact] .story-photo { width: clamp(43px, 24%, 75px); aspect-ratio: 1.57; }
[data-card-variant=compact] .story-title { font-size: .83rem; font-weight: 600; }
[data-card-variant=compact] .story-foot { margin-top: .25rem; }
[data-card-variant=feature], [data-card-variant=grid] { background: var(--edition-paper); box-shadow: var(--edition-shadow); border: 1px solid var(--edition-edge); overflow: hidden; }
[data-card-variant=feature] { flex-direction: row-reverse; padding: .4rem; }
[data-card-variant=feature] .story-copy { padding: .55rem .8rem; min-height: 122px; }
[data-card-variant=feature] .story-title { font-family: Georgia, 'Songti SC', 'Noto Serif CJK SC', serif; font-size: clamp(1.07rem, 1.4vw, 1.35rem); line-height: 1.35; }
[data-card-variant=feature] .story-photo { width: 29%; }
[data-card-variant=feature] .story-foot { margin-top: auto; padding-top: .65rem; }
[data-card-variant=grid] { flex-direction: column; padding: .38rem; }
[data-card-variant=grid] .story-photo { aspect-ratio: 2.65; }
[data-card-variant=grid] .story-copy { padding: .68rem .65rem .55rem; }
[data-card-variant=grid] .story-summary { margin-bottom: .65rem; }
[data-card-variant=grid] .story-foot { margin-top: auto; }
.photo-date { position: absolute; top: .55rem; left: .55rem; padding: .2rem .45rem; background: rgba(255,253,249,.94); color: #443b33; border-radius: .4rem; font-size: .7rem; }
@media (hover:hover) { .edition-story:hover .story-title { color: var(--edition-accent); text-decoration: underline; text-underline-offset: .16em; } }
@media(max-width:520px) {
  [data-card-variant=row] { gap: .7rem; }
  [data-card-variant=row] .story-photo { width: 29%; aspect-ratio: 1.45; }
  [data-card-variant=feature] .story-copy { padding: .4rem; }
  [data-card-variant=feature] .story-context time { font-size: .63rem; }
}
</style>
