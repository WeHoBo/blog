<template>
  <div :data-reference-layout="edition" class="edition">
    <section v-if="listing" class="edition-listing" data-article-stream>
      <header class="listing-head">
        <div><h1>{{ filtered ? title : '文章列表' }}</h1><p v-if="!feed.pending && !feed.error">共 {{ feed.total }} 篇文章</p></div>
        <div class="list-actions">
          <button v-if="filtered" class="subtle-button" type="button" @click="$emit('clear')">清除筛选</button>
          <div class="view-options" aria-label="文章列表视图">
            <button v-for="option in views" :key="option.id" type="button" :aria-pressed="view === option.id" @click="view = option.id">{{ option.label }}</button>
          </div>
        </div>
      </header>
      <div class="listing-cards" :data-view="view">
        <HomeEditionCard v-for="article in feed.records" :key="article.id" :article="article"
          :variant="view === 'grid' ? 'grid' : 'row'" :keyword="keyword" />
      </div>
      <p v-if="!feed.records.length && !feed.error && !feed.pending" class="blank">{{ emptyText }}</p>
      <HomeFeedMore :more="feed.hasMore" :pending="feed.pending" :error="feed.error" @load="feed.next()" />
    </section>
    <template v-else>
      <header class="edition-hero" :class="{ 'has-postcard': postcardEdition, 'has-ridgeline': edition === 'alpine' && visible, 'blend-backdrop': edition === 'platform' && visible && !bannerVisible }">
        <div v-if="edition === 'platform' && bannerVisible" aria-hidden="true" class="platform-scene">
          <WallpaperArtwork :skin="current" :image="siteImage" @image-error="markImageFailed" />
        </div>
        <div class="hero-intro">
          <div v-if="edition === 'alpine'" class="hero-subject">{{ siteName }} · {{ year }}</div>
          <p v-else-if="edition !== 'platform'" class="hero-author">{{ bloggerName }} 的博客</p>
          <h1>{{ headlines[edition] }}</h1>
          <p class="hero-description">{{ bloggerBio || siteDesc || '用文字记录生活与技术。' }}</p>
          <div v-if="edition === 'album'" class="hero-counts"><span>{{ feed.total }} 篇文章</span><span>{{ categories.length }} 个分类</span><span>{{ tagTotal }} 个标签</span></div>
          <div class="hero-actions">
            <NuxtLink v-if="edition === 'platform' && writePath" class="read-button" :to="writePath">写一篇文章 <span aria-hidden="true">↗</span></NuxtLink>
            <a v-else class="read-button" href="#articles">开始阅读 <span aria-hidden="true">↓</span></a>
            <NuxtLink v-if="edition !== 'platform' && edition !== 'album'" class="about-link" to="/about">关于我 <span aria-hidden="true">↗</span></NuxtLink>
          </div>
        </div>
        <figure v-if="postcardEdition" class="postcard">
          <template v-if="photoStory && postcardSrc !== defaultPostcard">
            <NuxtLink class="postcard-link" :to="articlePath(photoStory)" :aria-label="'阅读' + photoStory.title">
              <img :src="postcardSrc" alt="" :key="postcardSrc" @error="failedPostcard = postcardSrc" />
            </NuxtLink>
            <figcaption>{{ (photoStory.publishAt || photoStory.createTime || '').slice(0, 10) }}</figcaption>
          </template>
          <img v-else :src="defaultPostcard" alt="" />
        </figure>
        <template v-if="edition === 'alpine'">
          <section v-if="heroStory" class="hero-pin">
            <h2>{{ pinned.records.length ? '置顶文章' : '最近发布' }}</h2>
            <HomeEditionCard :article="heroStory" variant="compact" />
          </section>
          <div v-if="tags.length" class="hero-tags">
            <h2>热门标签</h2><div><NuxtLink v-for="t in tags.slice(0, 6)" :key="t.id" :to="taxonomyPath('/tag', t)">{{ t.name }}</NuxtLink></div>
          </div>
        </template>
      </header>

      <dl v-if="edition === 'platform'" class="station-stats" aria-label="站点统计">
        <div v-for="metric in metrics" :key="metric.label" class="paper">
          <span class="metric-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path :d="metric.path"/></svg></span>
          <dt>{{ metric.label }}</dt><dd>{{ metric.value }}</dd>
        </div>
      </dl>

      <div id="articles" class="edition-columns" :class="{ 'three-columns': threeColumns }">
        <aside v-if="threeColumns" class="profile-column">
          <div class="paper author-card">
            <template v-if="author.pending">
              <div class="author-skeleton"></div>
            </template>
            <template v-else>
              <img v-if="avatarSrc" :src="avatarSrc" alt="" width="54" height="54" @error="failedAvatar = avatarSrc" />
              <div v-else class="author-initial">{{ author.initial || author.name.slice(0, 1) }}</div>
              <NuxtLink :to="author.href">{{ author.name }}</NuxtLink><p v-if="author.bio">{{ author.bio }}</p>
            </template>
            <div class="author-numbers"><span><b>{{ feed.total }}</b>文章</span><span><b>{{ categories.length }}</b>分类</span><span><b>{{ tagTotal }}</b>标签</span></div>
          </div>
          <div v-if="categories.length" class="paper category-panel"><h2>文章分类</h2>
            <NuxtLink v-for="c in topCategories" :key="c.id" :to="{path:'/',query:{categoryId:c.id}}"><span>{{ c.name }}</span><small v-if="c.articleCount != null">{{ c.articleCount }}</small></NuxtLink>
            <NuxtLink class="all-link" to="/categories">全部分类 <span aria-hidden="true">→</span></NuxtLink>
          </div>
          <ContributionCalendar v-if="edition === 'alpine'" />
        </aside>
        <section class="story-column" :class="{paper: !threeColumns}" data-article-stream>
          <div class="section-heading"><h2>{{ threeColumns && featured.records.length ? '精选文章' : streamTitle }}</h2>
              <div v-if="edition === 'alpine'" class="view-options" aria-label="精选文章视图">
                <button v-for="option in views" :key="option.id" type="button" :aria-pressed="homeView === option.id" @click="homeView = option.id">{{ option.label }}</button>
              </div>
            <NuxtLink v-else :to="listPath">全部文章 <span aria-hidden="true">→</span></NuxtLink>
          </div>
          <div class="home-cards" :class="{ 'home-grid': edition === 'alpine' && homeView === 'grid' }">
            <HomeEditionCard v-for="article in homeArticles" :key="article.id" :article="article" :variant="edition === 'alpine' && homeView === 'grid' ? 'grid' : threeColumns ? 'feature' : 'row'" />
          </div>
          <NuxtLink v-if="edition === 'alpine' && homeArticles.length" class="all-stories-link" :to="listPath">全部文章 <span aria-hidden="true">→</span></NuxtLink>
          <p v-if="!homeArticles.length && !feed.pending && !feed.error" class="blank">还没有公开文章</p>
          <HomeFeedMore v-if="feed.error || feed.pending" :more="false" :pending="feed.pending" :error="feed.error" @load="feed.next()" />
          <HomeFeedMore v-if="threeColumns && featured.error" :more="false" :pending="featured.pending" :error="true" @load="featured.next()" />
        </section>
        <aside v-if="threeColumns && (latestItems.length || feed.pending || feed.error)" class="paper latest-column">
          <div class="section-heading"><h2>{{ streamTitle }}</h2><NuxtLink :to="listPath" aria-label="查看全部文章">更多 →</NuxtLink></div>
          <HomeEditionCard v-for="article in latestItems" :key="article.id" :article="article" variant="compact" />
        </aside>
        <aside v-else-if="edition === 'platform'" class="paper station-categories">
          <div class="section-heading"><h2>分类导航</h2><NuxtLink to="/categories">全部 →</NuxtLink></div>
          <NuxtLink v-for="c in topCategories" :key="c.id" class="station-category" :to="{path:'/',query:{categoryId:c.id}}">
            <span class="folder-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 7V5a1 1 0 0 1 1-1h5l2 3h9a1 1 0 0 1 1 1v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z"/></svg></span>
            <span><strong>{{ c.name }}</strong><small v-if="c.articleCount != null">{{ c.articleCount }} 篇</small></span>
          </NuxtLink>
          <p v-if="!categories.length" class="blank">暂无分类</p>
        </aside>
        <aside v-else-if="edition === 'album'" class="calendar-column"><ContributionCalendar /></aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { useHomeFeed } from '~/composables/useHomeFeed'
import type { ReferenceLayout } from '~/composables/useSkin'
import { taxonomyPath, type TaxonomyItem } from '~/utils/taxonomy'
import { ossImage } from '~/utils/image'
import { articlePath } from '~/utils/articlePath'

type Feed = ReturnType<typeof useHomeFeed>
const props = defineProps<{
  edition: ReferenceLayout; feed: Feed; featured: Feed; pinned: Feed; categories: TaxonomyItem[]; tags: TaxonomyItem[];
  tagTotal: number; listing: boolean; filtered: boolean; title: string; keyword: string; emptyText: string;
  siteName: string; siteDesc: string; bloggerName: string; bloggerBio: string; bloggerAvatar?: string; writePath: string
  /** 作者栏要显示的人（登录=自己 / 访客=站点博主），由 index.vue 的 cardAuthor 算好传入 */
  author: {
    pending: boolean; isMe: boolean; name: string; bio: string
    avatar: string; initial: string; href: string; action: string
  }
}>()
defineEmits<{ clear: [] }>()
const { current } = useSkin()
const { visible, bannerVisible, siteImage, markImageFailed } = useWallpaper()
const { sortMode } = useLayout()
const streamTitle = computed(() => sortMode.value === 'title' ? '文章列表' : '最新文章')
const threeColumns = computed(() => props.edition === 'journal' || props.edition === 'alpine')
const postcardEdition = computed(() => props.edition === 'journal' || props.edition === 'album')
const homeArticles = computed(() => {
  const source = threeColumns.value && props.featured.records.length ? props.featured : props.feed
  return source.records.slice(0, threeColumns.value ? 5 : 8)
})
const latestItems = computed(() => {
  const displayed = new Set(homeArticles.value.map(a => a.id))
  return props.feed.records.filter(a => !displayed.has(a.id)).slice(0, 8)
})
const heroStory = computed(() => props.pinned.records[0] || props.feed.records[0])
const photoStory = computed(() => [...props.pinned.records, ...props.featured.records, ...props.feed.records].find(article => article.cover))
const defaultPostcard = '/images/skins/postcard.webp'
const failedPostcard = ref('')
const postcardSrc = computed(() => {
  const photo = photoStory.value?.cover && ossImage(photoStory.value.cover, { w: 520 })
  return photo && photo !== failedPostcard.value ? photo : defaultPostcard
})
const failedAvatar = ref('')
const avatarSrc = computed(() => {
  // 只做「图片加载失败」的兜底，不做「两个人互相替代」——
  // 显示谁由 index.vue 的 cardAuthor 决定，这里拿到什么就是什么。
  const a = props.author.avatar
  return a && a !== failedAvatar.value ? a : ''
})
const topCategories = computed(() => props.categories.filter(c => !c.parentId).slice(0, 7))
const year = new Date().getFullYear()
const listPath = { path: '/', query: { view: 'articles' } }
const views = [{id:'grid',label:'网格'},{id:'list',label:'列表'}] as const
const view = useState<'grid' | 'list'>(`edition-view-${props.edition}`, () => props.edition === 'album' ? 'grid' : 'list')
const homeView = useState<'grid' | 'list'>('alpine-feature-view', () => 'list')
const headlines = computed(() => ({
  platform: `你好，欢迎来到${props.siteName}。`,
  journal: '记录生活的每一刻。',
  alpine: '保持好奇，持续探索。',
  album: '记下每一个值得的瞬间。'
}))
const metrics = computed(() => [
  { label:'公开文章',value:props.feed.total,path:'M5 3h9l5 5v13H5zM14 3v6h5M8 13h8M8 17h6' },
  { label:'文章分类',value:props.categories.length,path:'M3 7V5h6l3 3h9v11H3z' },
  { label:'文章标签',value:props.tagTotal,path:'M3 4h8l10 10-7 7L3 10zM7 7h.01' },
  { label:'精选文章',value:props.featured.total,path:'m12 3 2.6 5.4 6 1-4.2 4.1.9 6-5.3-2.8-5.3 2.8.9-6L3.4 9.4l6-1z' },
])
</script>

<style scoped>
.edition {
  --edition-paper: rgba(255, 252, 246, .94);
  --edition-panel: rgba(255, 251, 244, .9);
  --edition-ink: #302921;
  --edition-sub: #6b6056;
  --edition-accent: hsl(var(--ph) 39% 33%);
  --edition-edge: rgba(111, 81, 52, .17);
  --edition-soft: hsl(var(--ph) 48% 91%);
  --edition-shadow: 0 2px 4px rgba(70, 43, 17, .045), 0 12px 26px -23px #896241;
  max-width: 1260px; margin: 0 auto; padding: clamp(.75rem, 2vw, 2rem) 0 3rem; color: var(--edition-ink);
}
.edition[data-reference-layout=alpine] {
  --edition-paper: rgba(255,255,255,.96); --edition-panel: rgba(248,249,255,.94);
  --edition-accent:hsl(var(--ph) 35% 42%); --edition-ink:#272638; --edition-sub:#626274; --edition-soft:hsl(var(--ph) 46% 95%);
  --edition-edge: rgba(112,101,151,.18); --edition-shadow: 0 2px 5px rgba(60,54,83,.045), 0 12px 24px -22px #857b9d;
}
.edition[data-reference-layout=journal] { --edition-ink:#332820; }
.edition[data-reference-layout=album] { --edition-paper:rgba(255,255,249,.94); --edition-soft:hsl(var(--ph) 35% 90%); --edition-accent:hsl(var(--ph) 30% 31%); }
.dark .edition {
  --edition-paper:rgba(41,37,33,.96); --edition-panel:rgba(38,34,31,.95); --edition-ink:#f7efe6;
  --edition-sub:#c6bcb0; --edition-accent:hsl(var(--ph) 57% 79%); --edition-soft:hsl(var(--ph) 17% 25%); --edition-edge:rgba(229,207,178,.19);
  --edition-shadow:0 5px 20px rgba(0,0,0,.16);
}
.dark .edition[data-reference-layout=alpine] { --edition-paper:#282837; --edition-panel:#252533; --edition-ink:#f1effc; --edition-sub:#c3bed7; --edition-accent:hsl(var(--ph) 65% 83%); --edition-soft:hsl(var(--ph) 18% 26%); }
.edition .paper, .edition-hero, .edition-listing { border:1px solid var(--edition-edge); border-radius:14px; background:var(--edition-panel); box-shadow:var(--edition-shadow); }
.edition-hero { overflow:hidden; position:relative; isolation:isolate; display:grid; align-items:center; gap:1.35rem; padding:clamp(1.4rem,3.5vw,3.15rem); min-height:225px; }
.hero-intro { position:relative; min-width:0; }
.edition-hero h1, .listing-head h1 { font-family:Georgia,'Songti SC','STSong','Noto Serif CJK SC',serif; font-weight:700; line-height:1.24; font-size:clamp(1.85rem,3.05vw,3.1rem); letter-spacing:-.035em; overflow-wrap:anywhere; text-wrap:balance; }
.hero-author, .hero-subject { margin-bottom:.72rem; font-size:.78rem; font-weight:600; color:var(--edition-accent); }
.hero-subject { display:inline-block; padding:.2rem .6rem; border-radius:1rem; background:var(--edition-soft); }
.hero-description { margin-top:.7rem; line-height:1.7; font-size:.94rem; max-width:54ch; color:var(--edition-sub); display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden; }
.hero-actions, .hero-counts { display:flex; gap:.7rem; align-items:center; flex-wrap:wrap; margin-top:1.05rem; }
.read-button, .about-link, .subtle-button { display:inline-flex; gap:.65rem; align-items:center; border-radius:999px; padding:.48rem 1rem; font-size:.82rem; line-height:1.3; font-weight:650; }
.read-button { color:white; background:var(--edition-accent); }
.dark .read-button { color:#211d23; }
.about-link, .subtle-button { background:var(--edition-paper); color:var(--edition-accent); border:1px solid var(--edition-edge); }
.edition-hero.has-postcard { grid-template-columns:minmax(0,1fr) minmax(160px,27%); }
.postcard { justify-self:center; width:clamp(148px,17vw,232px); background:#fffaf1; padding:.5rem .5rem 1.3rem; margin:0; transform:rotate(5deg); position:relative; box-shadow:0 7px 18px #4c3a2c32; }
.postcard::before { content:''; width:30%; height:15px; position:absolute; z-index:1; top:-6px; left:35%; transform:rotate(-4deg); background:#e2c59ac4; }
.postcard img { width:100%; aspect-ratio:1.48; object-fit:cover; }
.postcard-link { display:block; }
.postcard figcaption { line-height:1; position:absolute; bottom:.38rem; right:.65rem; color:#705948; font-size:.63rem; letter-spacing:.08em; }
[data-reference-layout=journal] .edition-hero { background:linear-gradient(108deg,var(--edition-paper) 0%,var(--edition-panel) 48%,transparent 86%); }
[data-reference-layout=journal] .hero-intro { border-inline-start:2px solid var(--edition-edge); padding-inline-start:clamp(.7rem,1.6vw,1.55rem); }
[data-reference-layout=album] .postcard::after { content:''; position:absolute; z-index:-1; inset:4px 1px -3px -5px; transform:rotate(-9deg); background:var(--edition-paper); border:1px solid var(--edition-edge); }
.postcard img { position:relative; z-index:1; }
.postcard figcaption { z-index:2; }
.hero-counts { font-size:.73rem; color:var(--edition-accent); gap:.4rem; }
.hero-counts span, .hero-tags a { background:var(--edition-soft); border-radius:999px; padding:.21rem .64rem; }
[data-reference-layout=alpine] .edition-hero { grid-template-columns:minmax(0,1.2fr) minmax(0,.78fr) minmax(0,.72fr); gap:1rem; }
.has-ridgeline::before { content:''; pointer-events:none; position:absolute; inset:auto 0 0; height:clamp(32px,5vw,74px); z-index:-1; background:url('/images/skins/ridgeline.webp') right bottom / 100% 100% no-repeat; mix-blend-mode:multiply; }
.dark .has-ridgeline::before { filter:invert(1) hue-rotate(180deg); mix-blend-mode:screen; opacity:.65; }
[data-reference-layout=alpine] .edition-hero h1 { font-family:inherit; font-size:clamp(1.75rem,2.85vw,2.85rem); }
.hero-pin,.hero-tags { border-inline-start:1px solid var(--edition-edge); padding-inline-start:clamp(.75rem,1.8vw,1.65rem); min-width:0; }
.hero-pin h2,.hero-tags h2 { font-size:.78rem; font-weight:600; color:var(--edition-sub); }
.hero-tags div { display:flex; flex-wrap:wrap; margin-top:.65rem; gap:.42rem; }
.hero-tags a { font-size:.74rem; overflow-wrap:anywhere; }
[data-reference-layout=platform] .edition-hero { min-height:clamp(210px,20vw,300px); }
[data-reference-layout=platform] .hero-intro { width:fit-content; max-width:75%; }
.platform-scene, .platform-scene::after { position:absolute; inset:0; z-index:-1; }
.platform-scene::after { content:''; z-index:0; }
.platform-scene::after,.edition-hero.blend-backdrop { background:linear-gradient(95deg,var(--edition-paper),var(--edition-panel) 33%,transparent 75%); }
.station-stats { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:.75rem; margin:.85rem 0; }
.station-stats>div { display:grid; align-items:center; grid-template-columns:auto 1fr; gap:.18rem .9rem; padding:clamp(.75rem,1.6vw,1.3rem); }
.station-stats dt { font-size:.8rem; color:var(--edition-sub); }
.station-stats dd { font-size:clamp(1.25rem,1.8vw,1.7rem); line-height:1.1; font-weight:750; font-variant-numeric:tabular-nums; }
.metric-icon,.folder-icon { display:grid; place-items:center; height:2.45rem; width:2.45rem; background:var(--edition-soft); color:var(--edition-accent); border-radius:.8rem; }
.metric-icon { grid-row:span 2; }
.metric-icon svg,.folder-icon svg { width:1.2rem; height:1.2rem; stroke:currentColor; fill:none; stroke-width:1.6; stroke-linecap:round; stroke-linejoin:round; }
.station-stats>div:nth-child(2) .metric-icon { background:#e5efe4; color:#4b6e49; }
.station-stats>div:nth-child(3) .metric-icon { background:#e4e9f3; color:#4c6587; }
.edition-columns { display:grid; grid-template-columns:minmax(0,2fr) minmax(200px,.92fr); gap:clamp(.7rem,1.4vw,1.4rem); align-items:start; margin-top:1.2rem; scroll-margin-top:5.5rem; }
.edition-columns.three-columns { grid-template-columns:minmax(160px,.76fr) minmax(260px,2.12fr) minmax(190px,1.03fr); }
.three-columns:not(:has(.latest-column)) .story-column { grid-column:span 2; }
.edition-columns>* { min-width:0; }
.story-column.paper,.latest-column,.station-categories,.category-panel,.author-card { padding:clamp(.85rem,1.3vw,1.2rem); }
.section-heading { display:flex; align-items:center; justify-content:space-between; gap:.55rem; padding-bottom:.55rem; }
.section-heading h2,.category-panel h2 { font-size:1.04rem; font-weight:750; color:var(--edition-ink); }
.section-heading a,.all-link { font-size:.74rem; font-weight:600; color:var(--edition-accent); white-space:nowrap; }
.three-columns .story-column,.three-columns .latest-column { background:var(--edition-panel); border:1px solid var(--edition-edge); border-radius:14px; padding:clamp(.65rem,1.1vw,1.05rem); }
.three-columns .home-cards { display:grid; gap:.65rem; }
.three-columns .home-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
.all-stories-link { display:inline-flex; margin-top:.8rem; font-size:.76rem; color:var(--edition-accent); }
.story-column.paper .home-cards>:not(:first-child),.latest-column :deep(.edition-story + .edition-story) { border-top:1px solid var(--edition-edge); border-radius:0; }
.profile-column { display:grid; gap:.75rem; }
.author-card { display:grid; justify-items:center; gap:.5rem; text-align:center; }
.author-card img { border-radius:50%; width:54px; height:54px; object-fit:cover; }
.author-card>a { font-size:1.04rem; font-weight:700; }
.author-card p { font-size:.76rem; color:var(--edition-sub); }
.author-numbers { display:flex; justify-content:space-evenly; gap:.8rem; margin-top:.35rem; width:100%; }
.author-numbers span { display:grid; color:var(--edition-sub); font-size:.67rem; }
.author-numbers b { font-size:.92rem; font-variant-numeric:tabular-nums; color:var(--edition-ink); }
/* 没传头像时用昵称首字母的圆章（和青野/暖纸那张卡保持一致），别留一个空位 */
.author-initial { width:54px; height:54px; border-radius:50%; display:grid; place-items:center; background:hsl(var(--ph) var(--ps) 44%); color:#fff; font-weight:700; font-size:1.15rem; }
/* 已登录但 /auth/me 还没回来：骨架，绝不能先显示博主那一份 */
.author-skeleton { width:54px; height:54px; border-radius:50%; background:var(--edition-edge); animation:authorPulse 1.6s ease-in-out infinite; }
@keyframes authorPulse { 0%,100%{opacity:.35} 50%{opacity:.7} }
.category-panel h2 { margin-bottom:.45rem; }
.category-panel a { display:flex; align-items:baseline; justify-content:space-between; padding:.4rem .2rem; gap:.5rem; font-size:.83rem; border-bottom:1px solid var(--edition-edge); }
.category-panel a:last-child { border-bottom:0; }
.category-panel small,.station-category small { color:var(--edition-sub); font-size:.69rem; }
.station-category { display:flex; align-items:center; padding:.6rem 0; gap:.75rem; border-bottom:1px solid var(--edition-edge); }
.station-category:last-child { border-bottom:0; }
.station-category strong,.station-category small { display:block; }
.station-category strong { font-size:.85rem; overflow-wrap:anywhere; }
.station-category small { margin-top:.2rem; }
.edition-listing { padding:clamp(1rem,2vw,2rem); }
.listing-head { display:flex; justify-content:space-between; flex-wrap:wrap; align-items:center; gap:.8rem; margin-bottom:1.25rem; }
.listing-head h1 { font-size:clamp(1.6rem,2.5vw,2.2rem); }
.listing-head p,.blank { color:var(--edition-sub); font-size:.84rem; margin:.4rem 0; }
.list-actions,.view-options { display:flex; align-items:center; gap:.4rem; flex-wrap:wrap; }
.view-options { border:1px solid var(--edition-edge); padding:.2rem; border-radius:999px; background:var(--edition-paper); }
.view-options button { font-size:.78rem; padding:.34rem .65rem; border-radius:999px; color:var(--edition-sub); }
.view-options button[aria-pressed=true] { background:var(--edition-soft); color:var(--edition-accent); font-weight:700; }
.listing-cards { display:grid; }
.listing-cards[data-view=grid] { grid-template-columns:repeat(2,minmax(0,1fr)); gap:.85rem; }
.listing-cards[data-view=list]>*+* { border-top:1px solid var(--edition-edge); border-radius:0; }
@media(max-width:1050px) and (min-width:761px) {
  .edition-columns.three-columns { grid-template-columns:minmax(145px,.85fr) minmax(0,2fr); }
  .three-columns .latest-column { grid-column:2; }
  .three-columns:not(:has(.latest-column)) .story-column { grid-column:auto; }
}
@media(max-width:760px) {
  .edition { padding:.65rem 0 1.6rem; }
  .edition-columns,.edition-columns.three-columns { grid-template-columns:minmax(0,1fr); gap:.85rem; }
  .three-columns:not(:has(.latest-column)) .story-column { grid-column:auto; }
  .profile-column { order:2; }
  .latest-column { order:1; }
  .profile-column>.paper,.calendar-column { width:100%; }
  .profile-column :deep(.glass),.calendar-column :deep(.glass) { max-width:400px; }
  .edition-hero,.edition-hero.has-postcard { min-height:0; }
  .edition-hero.has-postcard { grid-template-columns:minmax(0,1fr) minmax(82px,24%); gap:.65rem; }
  .postcard { width:100%; padding:.3rem .3rem .95rem; }
  .postcard figcaption { font-size:.5rem; bottom:.21rem; }
  [data-reference-layout=alpine] .edition-hero { grid-template-columns:1fr; }
  .hero-pin,.hero-tags { padding:.65rem 0 0; border-inline-start:0; border-top:1px solid var(--edition-edge); }
  .hero-tags div { margin-top:.35rem; }
  .hero-pin :deep(.edition-story) { max-width:27rem; }
  .station-stats { grid-template-columns:repeat(2,minmax(0,1fr)); gap:.48rem; }
  [data-reference-layout=platform] .hero-intro { max-width:92%; }
  [data-reference-layout=platform] .edition-hero { min-height:190px; }
}
@media(max-width:520px) {
  .listing-cards[data-view=grid] { grid-template-columns:minmax(0,1fr); }
  .three-columns .home-grid { grid-template-columns:minmax(0,1fr); }
  .edition-hero h1 { font-size:1.64rem; }
  .hero-description { font-size:.83rem; }
  .edition-hero,.edition-hero.has-postcard { padding:1.05rem; }
  .listing-head { align-items:start; }
}
</style>
