/**
 * check-companion.mjs —— AI 助手入口（右下角角色）的结构性断言
 *
 * 挡的是「改完容易被后人悄悄改回去」的硬约定，不验视觉：
 *   ① 入口只有一个，且旧圆按钮/旧气泡不许复活
 *   ② 渲染器不许自己判断聊天状态（必须从 useCompanionState 读）
 *   ③ 状态机里所有定时器都必须守服务端（否则 SSR 每次渲染漏一个 timer）
 *   ④ 映射层只读不写（入口永远不许反过来影响聊天）
 *
 * 用法：node scripts/check-companion.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = (p) => readFileSync(resolve(ROOT, p), 'utf8')
/** 剥掉 HTML 与 JS 注释：注释里会解释"为什么删掉了 XX"，不剥会天天误报 */
const strip = (s) =>
  s.replace(/<!--[\s\S]*?-->/g, '').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')

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
  float: 'components/AiFloat.vue',
  entry: 'components/CompanionEntry.vue',
  css: 'components/CompanionCss.vue',
  standee: 'components/CompanionStandee.vue',
  bubble: 'components/CompanionBubble.vue',
  state: 'composables/useCompanionState.ts',
  appConfig: 'app.config.ts',
}
for (const [k, p] of Object.entries(files)) {
  if (!existsSync(resolve(ROOT, p))) {
    console.log(`  ✗  文件缺失: ${p}`)
    process.exit(1)
  }
}

const float = strip(read(files.float))
const entry = strip(read(files.entry))
const css = strip(read(files.css))
const standee = strip(read(files.standee))
const bubble = strip(read(files.bubble))
const state = strip(read(files.state))
const appConfig = strip(read(files.appConfig))

console.log('\n入口唯一性')

check('AiFloat 用 CompanionEntry 当唯一入口', () => {
  must(float, '<CompanionEntry', '右下角的入口必须走 CompanionEntry')
  if (count(float, '<CompanionEntry') !== 1) throw new Error('CompanionEntry 只能出现一次')
})

check('旧的渐变圆按钮已删除', () => {
  // 面板 Header 里那个小图标也是这个渐变，不能一刀切成 from-primary-500，
  // 所以断言旧按钮独有的特征：56×56 尺寸类 + 悬停名称提示。
  mustNot(float, 'w-14 h-14 rounded-2xl bg-gradient', '旧按钮的尺寸+渐变组合不该回来')
  mustNot(float, 'absolute right-full mr-3', '旧按钮的 hover 名称提示')
})

check('旧的首屏提示气泡已删除（改由角色气泡承担）', () => {
  mustNot(float, 'ai-pop', '旧的提示气泡 Transition 名')
  mustNot(float, 'ai-hint-dismissed', '旧 key 永久只弹一次，已改成按天')
  must(entry, 'ai-companion-greeted', '新的按天招呼 key 应该在 CompanionEntry 里')
})

check('入口总开关存在，关掉时整个不渲染', () => {
  must(entry, 'companion?.enabled !== false', 'app.config.ts 的 companion.enabled 必须生效')
  must(entry, 'v-if="enabled"', '总开关要挂在根节点上')
  /* P2 起口径变了：面板打开时角色**必须继续显示**。
     旧版两者占同一坐标（都 fixed 右下角），所以只能把角色藏起来；
     现在面板锚定在角色左上（.cc-panel-slot），两者共存才有"拖着角色、窗口跟着走"。
     这条断言反过来守：谁要是又把它藏起来，拖拽和跟随就直接失效了。 */
  mustNot(entry, 'enabled && !open', '面板打开时立绘不能被隐藏 —— 面板是锚在它左上角的')
})

check('面板锚定在立绘左上，不靠 JS 算坐标', () => {
  must(entry, 'cc-panel-slot', '面板槽必须有，位置由它决定')
  must(entry, '<slot name="panel"', '面板要通过 slot 塞进来，不能继续 Teleport 到 body')
  mustNot(entry, 'Teleport', 'Teleport 之后面板就不在容器坐标系里了，拖拽不会跟随')
  /* 负 margin / 手算 left 都是"每帧计算"路线的残留，会抖 */
  must(entry, 'bottom: calc(100% + 12px)', '面板底边贴在立绘顶边上方 12px')
})

check('拖拽只改外层坐标，不碰内层动画通道', () => {
  const drag = strip(read('composables/useCompanionDrag.ts'))
  /* 立绘内部三条通道已经被占满：.cs-sway 用 rotate（摇摆）、
     .cs-img 用 translate（呼吸）、内联 style 归视线跟随。
     拖拽要是也去写 transform，四者互相覆盖，表现是"拖的时候图乱扭"。 */
  mustNot(drag, 'transform', '拖拽不能碰 transform —— 那是呼吸/摇摆/视线跟随的地盘')
  must(drag, 'left:', '位置用 left/top 表达')
  must(drag, 'top:', '位置用 left/top 表达')
  must(drag, "right: 'auto'", 'left/top 生效时必须把 right/bottom 清成 auto，否则两边互相拉扯')
})

check('拖拽有边界夹取、边缘磁吸、手机不启用', () => {
  const drag = strip(read('composables/useCompanionDrag.ts'))
  must(drag, 'function clamp', '没夹取 = 甩出屏幕就再也找不回来')
  must(drag, 'function snap', '没磁吸 = 会停在离边缘 3px 的尴尬位置')
  must(drag, 'DESKTOP_MIN', '手机必须走单独的判定')
  must(drag, 'DRAG_THRESHOLD', '没有位移阈值，每次点击都会把桌宠挪一两像素')
  must(drag, 'justDragged', 'pointerup 之后浏览器必补一个 click，不挡住就会"拖一下顺带戳一下"')
})

check('面板高度会被立绘上方的剩余空间约束', () => {
  const panel = strip(read('components/AiFloat.vue'))
  /* 面板 580 + 立绘 167 = 747，视口矮一点就从顶部冲出去。
     实测 860 高的视口里，立绘一离开最底部，面板顶就到 y=-94。 */
  must(panel, '--dock-y', '面板高度必须参考容器位置，不能写死')
  must(panel, 'max(220px', '压扁要有下限，否则立绘在顶部时面板会塌成一条')
  must(entry, "--dock-y", 'CompanionEntry 要把容器纵坐标透出成 CSS 变量')
})

check('app.config.ts 里不许 import 任何运行时别名', () => {
  // 血泪教训：`import { defineAppConfig } from '#app'` 会让生产构建直接挂
  // （Nitro impound：Vue app aliases are not allowed in server runtime）。
  // app.config.ts 会被打进服务端产物，#app 是应用层别名，碰不得。
  mustNot(appConfig, "from '#app'", 'defineAppConfig 是自动导入的宏，不要 import')
  mustNot(appConfig, "from 'vue'", '运行时别名一律不许出现在 app.config.ts')
  must(appConfig, 'export default defineAppConfig(', '裸写这个宏即可')
})

check('画笔可切换，默认立绘', () => {
  must(appConfig, 'engine:', 'app.config.ts 里要有 companion.engine')
  must(appConfig, "'standee'", '默认是静态立绘')
  must(appConfig, "'css'", 'css 小鱼要留着当降级')
  // 大肥鱼的授权禁止重新分发模型文件，所以正式方案里不该出现 live2d
  mustNot(appConfig, "engine: 'live2d'", 'live2d 不该是默认值（授权不允许）')
})

check('立绘是唯一的画，且图挂了能降级', () => {
  must(entry, '<CompanionStandee', '默认画笔是立绘')
  must(entry, '<CompanionCss', '立绘失败要能退回 CSS 小鱼，入口不能凭空消失')
  must(standee, '@error="emit(\'fail\')"', '图片加载失败必须上报')
  must(entry, "renderer.value = 'css'", '失败要真的切过去')
})

check('立绘资源体积可控', () => {
  must(standee, '/companion/standee.webp', '主图路径')
  must(standee, 'standee@2x.webp', '要有 @2x，高分屏不能糊')
})

console.log('\n状态机是唯一状态源')

check('渲染器不许自己判断聊天状态', () => {
  for (const [name, body] of [['css', css], ['standee', standee]]) {
    for (const word of ['streaming', 'messages', 'msg.phase', 'steps']) {
      mustNot(body, word, `${name} 渲染器只读 state，聊天状态由 useCompanionState 统一映射`)
    }
    must(body, 'useCompanionState', `${name} 必须从状态机取状态`)
  }
})

check('9 个状态都有气泡文案', () => {
  for (const s of ['idle', 'hint', 'thinking', 'tools', 'typing', 'done', 'error', 'sleep']) {
    must(state, `\n  ${s}:`, `BUBBLES 里缺 ${s} 的文案`)
  }
})

check('一次性状态会自己回落（否则气泡永远挂着）', () => {
  must(state, 'AUTO_IDLE_MS', 'done/error/hint 必须能自动回 idle')
  must(state, 'done: 1800', 'done 停留时间')
  must(state, 'error: 3000', 'error 要多留一会儿让人看见')
})

check('换工具名也要更新气泡（同为 tools 状态）', () => {
  must(state, "next === 'tools' && tool !== toolName.value", '状态没变但工具变了，不能直接 return')
})

console.log('\nSSR / 计时器安全')

check('所有定时器都守服务端', () => {
  // armSleep 是唯一的 sleep 入口，set / setPanelOpen / setPageHidden 都要在它前面守住
  const armBody = state.slice(state.indexOf('function armSleep'))
  must(armBody.slice(0, 400), 'import.meta.server', 'armSleep 里没有守卫 → SSR 每次渲染漏一个 90s timer')
  must(state, "if (import.meta.server) { state.value = next; return }", 'set() 里要守住计时器')
})

check('状态存 useState，而不是模块级变量', () => {
  must(state, "useState<CompanionState>('companion-state'", '状态必须进 useState，否则 SSR 会串用户')
  // 模块级只允许放计时器句柄
  mustNot(state, 'let currentState', '别用模块级变量存状态')
})

check('关面板时给一次性状态重新起计时', () => {
  must(state, "cur === 'done' || cur === 'error' || cur === 'hint'", '入口随面板卸载会清掉计时器，重挂载后必须重新计时')
})

console.log('\n映射层只读不写')

check('AiFloat 不改聊天状态，只读', () => {
  mustNot(float, 'streaming.value =', '入口动效不许影响聊天')
  mustNot(float, 'messages.value =', '入口动效不许影响聊天')
  must(float, 'applyDerived', '只通过 applyDerived 单向推送状态')
})

check('面板打开时不推状态给入口', () => {
  must(float, 'open.value ? null : busyPhase.value', '面板开着时角色是隐藏的，推状态只会让关面板后的表现打架')
})

console.log('\n渲染器（CSS 小鱼）')

check('零外部请求，纯内联 SVG', () => {
  mustNot(css, '<img', '不能用图片，降级方案必须零请求')
  mustNot(css, 'http', '不能引外链字体/图片/脚本')
  must(css, '<svg', '必须是内联 SVG')
})

check('顶栏/入口图标不许用 emoji', () => {
  // 用码点判断，避免这里自己写出 emoji 又被扫
  for (const ch of ['🐟', '🤖', '👋', '💬']) {
    mustNot(css, ch, '入口必须是线条/形状，不能用 emoji')
  }
})

check('瞳孔位移走 transition（JS 会改 transform）', () => {
  must(css, 'transition: transform 0.28s', 'JS 每帧写 translate，用 animation 会打架')
})

check('页面不可见时停动画', () => {
  must(css, 'animation-play-state: paused', '切标签页还在跑动画是白烧电')
  must(standee, 'animation-play-state: paused', '立绘也要停')
  must(entry, 'visibilitychange', '要有可见性监听')
})

check('尊重系统的「减少动效」', () => {
  must(css, 'prefers-reduced-motion', '无障碍要求')
  must(standee, 'prefers-reduced-motion', '立绘也要')
  must(bubble, 'prefers-reduced-motion', '气泡也要')
})

check('气泡走主题色，不用写死的蓝', () => {
  must(bubble, 'var(--ph)', '气泡边框/三角必须跟主题色联动')
  mustNot(bubble, '#3b82f6', '不许写死冷色')
})

console.log('')
if (failed) {
  console.log(`✗  ${failed} 条断言失败\n`)
  process.exit(1)
} else {
  console.log('全部通过\n')
}
