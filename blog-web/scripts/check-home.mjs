/**
 * check-home.mjs —— 首页第二轮改版的结构性静态断言
 *
 * 为什么不用 grep：首页模板是单行大段 HTML，`grep -c` 会把整文件算成 1 行，
 * 计数完全失真。这里统一用 indexOf 判断「在不在」，用 split 计数。
 *
 * 覆盖的是「改完容易被后来的人悄悄改回去」的硬约定：
 *   置顶驱动中间栏 / 右栏固定列表 / 两栏各自独立滚动 / 只切中间 / 取消 6 篇上限。
 * 不覆盖视觉效果 —— 视觉只能在浏览器里看。
 *
 * 用法：node scripts/check-home.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = (p) => readFileSync(resolve(ROOT, p), 'utf8')
/**
 * 去掉 <!-- --> 注释再断言。
 * 注释里会自然提到「开源仓库」「6 篇」这些被删掉的东西（解释为什么删），
 * 不剥掉的话断言会把注释当代码，天天误报。
 */
const stripHtml = (s) => s.replace(/<!--[\s\S]*?-->/g, '')

let failed = 0
function check(name, fn) {
  try {
    fn()
    console.log(`  ✓  ${name}`)
  } catch (e) {
    failed++
    console.log(`  ✗  ${name}`)
    console.log(`        ${e.message}`)
  }
}
function must(hay, needle, hint) {
  if (!hay.includes(needle)) throw new Error(`缺少「${needle}」${hint ? ' —— ' + hint : ''}`)
}
function mustNot(hay, needle, hint) {
  if (hay.includes(needle)) throw new Error(`不该出现「${needle}」${hint ? ' —— ' + hint : ''}`)
}
function count(hay, needle) {
  return hay.split(needle).length - 1
}

const files = {
  index: 'pages/index.vue',
  card: 'components/ArticleCard.vue',
  stack: 'components/FeaturedStack.vue',
  row: 'components/ArticleRow.vue',
  pane: 'components/ScrollPane.vue',
  layout: 'composables/useLayout.ts',
  adminList: 'pages/admin/articles/index.vue',
  editor: 'components/ArticleEditor.vue',
}
for (const [k, p] of Object.entries(files)) {
  if (!existsSync(resolve(ROOT, p))) {
    console.log(`  ✗  文件缺失: ${p}`)
    process.exit(1)
  }
}

const idx = stripHtml(read(files.index))
const card = stripHtml(read(files.card))
const stack = stripHtml(read(files.stack))
const row = stripHtml(read(files.row))
const pane = stripHtml(read(files.pane))
const layout = read(files.layout)
const adminList = read(files.adminList)
const editor = read(files.editor)

console.log('首页布局')

check('横幅中间是置顶堆叠', () => {
  must(idx, '<FeaturedStack :articles="pinned"', '横幅那块小堆叠放置顶')
})

check('横幅不再有统计数字列', () => {
  mustNot(idx, 'siteStats', '「43 篇文章/11 个分类/8 个标签」那栏已删除')
  mustNot(idx, '个分类', '统计文案不应残留')
})

check('开源仓库卡片已删除，GitHub 只保留一处入口', () => {
  mustNot(idx, '开源仓库', '原来右栏那张大卡片去掉')
  must(idx, 'github.com/WeHoBo/blog', 'GitHub 入口仍在')
  if (count(idx, 'github.com/WeHoBo/blog') !== 1) {
    throw new Error(`GitHub 入口应只出现 1 次，实际 ${count(idx, 'github.com/WeHoBo/blog')} 次`)
  }
  must(idx, '查看源码', '要保留「查看源码 →」')
})

check('GitHub 图标与「查看源码」挨着（inline-flex + gap）', () => {
  // 第二轮返工明确要求：两者相邻，不再 justify-between 顶到两端
  const at = idx.indexOf('github.com/WeHoBo/blog')
  const seg = idx.slice(Math.max(0, at - 400), at + 400)
  must(seg, 'inline-flex items-center gap-2', '图标与文字必须在同一个 inline-flex 里')
  mustNot(seg, 'justify-between', '两端对齐会把文字甩到最右边')
})

check('左栏顺序：个人信息 → 分类 → 热力图', () => {
  const aside = idx.slice(idx.indexOf('<aside'), idx.indexOf('<main'))
  const a = aside.indexOf('我的资料')
  const b = aside.indexOf('文章分类')
  const c = aside.indexOf('<ContributionCalendar')
  if (!(a > -1 && b > -1 && c > -1)) throw new Error('左栏三块没找全')
  if (!(a < b && b < c)) throw new Error(`顺序不对（个人信息 ${a} / 分类 ${b} / 热力 ${c}）`)
})

check('中间是滚动屏，不再分页', () => {
  // 不匹配整个标签：中栏那个 ScrollPane 带 v-else（只在非筛选态渲染），
  // 写成 '<ScrollPane :max-height="620"' 永远匹配不到 —— 这条断言曾因此长期误报。
  //
  // 2026-11-24 晚的定稿：中栏高度 1040，**要比左右两栏（742）明显长**，
  // 观感上像「拾光手记」那种一条长到底的列表；620 那版会卡在比左右两栏还矮 76px 的位置。
  const tag = idx.match(/<ScrollPane[^>]*>/)
  if (!tag) throw new Error('中栏没找到 <ScrollPane>')
  const mh = tag[0].match(/:max-height="(\d+)"/)
  if (!mh) throw new Error('中栏 ScrollPane 缺 :max-height（不设就永远 620 的默认值）')
  if (Number(mh[1]) < 900) {
    throw new Error(`中栏 :max-height=${mh[1]} 太矮，必须 ≥900（左右两栏是 742，要比它们明显长）`)
  }
  must(idx, ':count="centerArticles.length"', '滚动屏要知道条数以决定画不画底线')
  mustNot(idx, '<Pagination', '分页会把「我是有底线的」设计破坏掉')
})

check('中栏放精品，且与横幅的置顶是两条独立查询', () => {
  must(idx, "new URLSearchParams({ featured: '1' })", '中栏取精品')
  must(idx, "new URLSearchParams({ top: '1' })", '横幅取置顶（独立请求，不共享数据）')
  must(idx, 'filtered.value ? articles.value : featured.value', '中栏数据源必须是精品')
  mustNot(idx, 'featured.slice', '精品不设篇数上限')
  mustNot(idx, 'pinned.slice', '置顶不设篇数上限')
})

check('窄屏补一份全部文章，且关掉内滚动', () => {
  must(idx, 'xl:hidden', '窄屏兜底区块（<1280px 拿不到右栏）')
  must(idx, ':scrollable="false"', '手机端页面本身能滚，再套内滚动会滚不动')
})

console.log('\n右栏「全部文章」')

check('右栏固定为列表形态（ArticleRow），不再有网格分支', () => {
  // ⚠️ 2026-11-24 起首页有两块右栏：
  //   - 系列主导版式的「按主题找文章」（w-[22rem]）
  //   - 常态的「最新」（w-[25rem]，ArticleRow）
  // 原来靠「最后一个 <aside>」定位，加了第二块之后就指错了。
  // 现在先找 w-[25rem] 这个宽度标记，再往前回退到它的 <aside> 开头。
  const anchor = idx.indexOf('w-[25rem]')
  if (anchor < 0) throw new Error('找不到常态右栏（w-[25rem]）')
  const start = idx.lastIndexOf('<aside', anchor)
  const end = idx.indexOf('</aside>', anchor)
  const aside = idx.slice(start, end > 0 ? end : undefined)
  must(aside, '<ArticleRow', '固定用紧凑行')
  mustNot(aside, 'layout="grid"', '右栏固定列表，不参与布局切换')
  mustNot(aside, 'ArticleCard', '右栏不再用大卡片')
})

check('右栏宽度 400px（中栏 ≈ 原来 80% 的前提）', () => {
  must(idx, 'w-[25rem]', '25rem = 400px，改这里等于改中栏宽度')
})

check('右栏自己一个 ScrollPane（与中栏不同步滚动）', () => {
  if (count(idx, '<ScrollPane') < 3) {
    throw new Error(`应有 3 个 ScrollPane（中栏/右栏/窄屏兜底），实际 ${count(idx, '<ScrollPane')}`)
  }
})

check('右栏常态排除精品文章（不排除置顶）', () => {
  must(idx, "params.set('featured', '0')", '否则精品在中间和右侧各出现一次')
  must(idx, "if (!filtered.value && !referenceEdition.value)", '搜索和整站时间流需包含精品，旧版首页右栏排除精品')
  mustNot(idx, "params.set('top', '0')", '置顶仍要留在全部文章里，它只是横幅的快捷入口')
})

check('筛选态退回单列表', () => {
  must(idx, 'const filtered = computed(', '缺 filtered 判定')
  must(idx, 'const showSide = computed(() => !filtered.value)', '右栏要在筛选态让位')
})

check('切换布局只切中间', () => {
  must(idx, 'toggleHomeSwap', '首页要有切换按钮')
  must(idx, 'const centerLayout = computed(', '中栏布局由切换状态驱动')
  mustNot(idx, 'sideLayout', '右栏不参与切换，出现 sideLayout 说明又写回去了')
  must(idx, 'name="swap"', '切换要有过渡动画')
})

console.log('\n滚动屏组件')

check('底线在滚动内容内部（要滚到底才看得到）', () => {
  const slotAt = pane.indexOf('<slot />')
  const lineAt = pane.indexOf('我是有底线的')
  if (slotAt === -1 || lineAt === -1) throw new Error('缺 slot 或底线文案')
  if (lineAt < slotAt) throw new Error('底线跑到内容之前了，会一直显示在顶上')
  must(pane, 'v-if="count > 0 && complete"', '没有条目或还有下一页时不画底线')
})

check('顶部渐隐用 mask-image', () => {
  must(pane, 'linear-gradient(to bottom, transparent 0, #000 24px)', '滚上去的卡片要被「盖住」而不是硬切')
})

check('滚动屏是非循环的', () => {
  mustNot(pane, 'cloneNode', '不做首尾克隆，滚到底就停')
  mustNot(pane, 'requestAnimationFrame', '不做惯性循环')
  must(pane, 'overscroll-behavior: contain', '滚到底不要把整页带着一起滚')
})

check('scrollable 开关存在（手机端要能关掉内滚动）', () => {
  must(pane, "scrollable ? 'scroll-pane' : ''")
})

check('内滚动只在 ≥1280px 生效（窄屏再套一层会滚不动）', () => {
  must(pane, '@media (min-width: 1280px)', '与右栏出现的断点保持一致')
  const before = pane.slice(0, pane.indexOf('@media (min-width: 1280px)'))
  mustNot(before, 'overflow-y: auto', '媒体查询之前不能就有内滚动')
  must(pane, 'var(--pane-max', '高度必须走 CSS 变量，由 prop 注入')
})

console.log('\n紧凑行组件')

check('右栏与置顶堆叠共用同一套尺寸', () => {
  must(row, 'h-[68px]', '行高固定，两处才对齐（横幅折叠态 74px = 68 + 6px 偏移）')
  must(row, 'withTopOffset', '叠压态靠这个 prop 偏移')
  must(idx, '<ArticleRow', '首页确实在用')
  must(stack, '<ArticleRow', '堆叠也在用')
})

console.log('\n卡片组件')

check('2:1 封面用字面量类名（拼接的类名 Tailwind 扫不到）', () => {
  must(card, 'aspect-[2/1]', 'JIT 只认字面量')
  must(card, "ratio === '2:1'", '由 ratio prop 决定')
})

check('精品徽标与置顶徽标并存', () => {
  must(card, 'article.isFeatured', '精品徽标')
  must(card, '精品', '文案应为「精品」')
  mustNot(card, '门面', '「门面」这个叫法已废弃')
  must(card, 'article.isTop', '置顶徽标不能丢')
})

check('列表与网格两种形态都要画徽标（曾漏过：列表只有精品没有置顶）', () => {
  // 按徽标特有的配色计数，不按 article.isFeatured 计数 ——
  // isFeatured 还会命中封面边框和顶部 2px 细线（共 4 处），
  // 按它统计的话「漏画徽标」会被细线掩盖，口径会越来越歪
  const top = count(card, 'text-[10px] bg-primary-600 text-white')
  const feat = count(card, 'text-[10px] bg-primary-600/10 text-primary-700')
  if (top !== 2) throw new Error(`置顶徽标应出现 2 次（列表+网格），实际 ${top} 次`)
  if (feat !== 2) throw new Error(`精品徽标应出现 2 次（列表+网格），实际 ${feat} 次`)
})

console.log('\n切换状态的持久化')

check('切换状态有独立 key', () => {
  must(layout, "HOME_SWAP_KEY = 'blog-home-swap'")
})

check('setup 期间不读切换状态的 localStorage（防 SSR 水合不一致）', () => {
  const restore = layout.indexOf('function restoreHomeSwap')
  const readClient = layout.indexOf('localStorage.getItem(HOME_SWAP_KEY)', restore)
  if (restore === -1 || readClient === -1) throw new Error('restoreHomeSwap 应只在函数体内读 localStorage')
  const setupRead = layout.slice(0, restore).includes('getItem(HOME_SWAP_KEY)')
  if (setupRead) throw new Error('setup 阶段读了 HOME_SWAP_KEY，会水合不一致')
})

console.log('\n精品权限')

check('编辑器勾选框只对超管渲染', () => {
  must(editor, 'form.isFeatured', '表单要有这个字段')
  must(editor, 'v-if="isSuper"', '勾选框必须挂 isSuper')
  must(editor, 'if (isSuper.value) payload.isFeatured', '非超管不能把字段发上去')
})

check('后台列表有「设为精品」且挂 isSuper', () => {
  must(adminList, 'toggleFeatured', '缺少切换函数')
  must(adminList, 'v-if="isSuper && a.status === 1"', '入口只给超管，且只对已发布文章')
  must(adminList, "res?.code !== 200", '必须自判 code：业务异常是 HTTP 200 + code=400')
})

check('精品已取消「最多 6 篇」的限制', () => {
  mustNot(adminList, '最多 6 篇', '第二轮明确取消上限')
  mustNot(adminList, 'featuredCount', '不再统计精品数量')
})

console.log('\n置顶堆叠')

check('堆叠只做叠压与展开，没有手势/拖拽依赖', () => {
  must(stack, 'open = ref(false)')
  must(stack, '展开全部', '要有展开入口')
  mustNot(stack, 'touchstart', '不做手势，参考即可')
  mustNot(stack, 'draggable', '不做拖拽')
})

check('堆叠空态说的是「置顶」', () => {
  must(stack, '还没有置顶文章')
  mustNot(stack, '还没有精品文章', '横幅那块是置顶')
})

if (failed) {
  console.log(`\n${failed} 项未通过`)
  process.exit(1)
}
console.log('\n全部通过')
