/**
 * check-companion-state.mjs —— 用真实代码跑状态迁移（不是抄一遍逻辑）
 *
 * 背景：本机不能 `nuxi dev/build`，所以状态机这种「纯逻辑、但埋在 Nuxt 里」的东西
 * 平时只能靠肉眼看。这里用 esbuild 把它打成单文件，注入 useState/computed 的极简替身，
 * 然后在 Node 里真的跑一遍迁移 —— 包括 setTimeout 的自动回落。
 *
 * ⚠️ 它验的是「状态怎么变」，不验「长什么样」。视觉只能在浏览器里看。
 *
 * 用法：node scripts/check-companion-state.mjs
 */
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import * as esbuild from 'esbuild'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/** Nuxt 的两个自动导入最小替身：够状态机用，不引 @vue/reactivity */
const STUB = `
const __store = new Map();
const __ref = (v) => ({ value: v });
const useState = (key, init) => {
  if (!__store.has(key)) __store.set(key, __ref(init ? init() : undefined));
  return __store.get(key);
};
const computed = (fn) => ({ get value() { return fn(); } });
`

const built = await esbuild.build({
  entryPoints: [resolve(ROOT, 'composables/useCompanionState.ts')],
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node',
  target: 'node20',
  // 客户端路径：要走 setTimeout 才能验自动回落
  define: { 'import.meta.server': 'false' },
  banner: { js: STUB },
})
const code = built.outputFiles[0].text
const mod = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'))

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
function eq(actual, expected, label) {
  if (actual !== expected) throw new Error(`${label || ''} 期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}`)
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const s = mod.useCompanionState()

console.log('\n状态迁移')

check('初始是 idle，没有气泡', () => {
  eq(s.state.value, 'idle', '初始状态')
  eq(s.bubbleText.value, '', 'idle 不该有气泡')
})

check('thinking：气泡「正在想…」', () => {
  s.applyDerived('thinking')
  eq(s.state.value, 'thinking')
  eq(s.bubbleText.value, '正在想…')
})

check('tools：气泡带上工具名', () => {
  s.applyDerived('tools', '站内检索')
  eq(s.state.value, 'tools')
  eq(s.bubbleText.value, '正在查：站内检索')
})

check('tools 换工具名要跟着换（同一个状态也不能提前 return）', () => {
  s.applyDerived('tools', '联网搜索')
  eq(s.bubbleText.value, '正在查：联网搜索')
})

check('typing：气泡「整理答案中…」', () => {
  s.applyDerived('typing')
  eq(s.state.value, 'typing')
  eq(s.bubbleText.value, '整理答案中…')
})

check('done：气泡引导去看窗口', () => {
  s.applyDerived('done')
  eq(s.state.value, 'done')
  eq(s.bubbleText.value, '好啦，看窗口 →')
})

check('面板打开时不出气泡（避免和面板里的状态重复）', () => {
  s.setPanelOpen(true)
  eq(s.bubbleText.value, '')
  eq(s.state.value, 'done', '开面板不该改状态')
  s.setPanelOpen(false)
})

check('error：文案不能说"没有"，只能说"这次答不上来"', () => {
  s.applyDerived('error')
  eq(s.state.value, 'error')
  const t = s.bubbleText.value
  if (!t.includes('换个问法')) throw new Error('error 气泡要给出下一步动作')
})

console.log('\n自动回落（真实计时器）')

await sleep(3300)
check('error 3 秒后自己回 idle', () => {
  eq(s.state.value, 'idle', '3.3 秒后应已回落')
})

await (async () => {
  s.applyDerived('done')
  await sleep(2000)
  check('done 1.8 秒后自己回 idle（气泡不会永远挂着）', () => {
    eq(s.state.value, 'idle')
  })
})()

await (async () => {
  s.applyDerived('thinking')
  await sleep(2100)
  check('thinking 2 秒后仍是 thinking（还在跑就别假装结束）', () => {
    eq(s.state.value, 'thinking')
  })
})()

console.log('\n打盹与唤醒')

check('忙的时候不允许睡', () => {
  s.applyDerived('typing')
  s.sleep()
  eq(s.state.value, 'typing', '正在出字时点"睡"应该被忽略')
})

check('空闲时能睡，点一下能醒', () => {
  s.applyDerived('idle')
  s.sleep()
  eq(s.state.value, 'sleep')
  eq(s.bubbleText.value, '', '睡着了不打扰')
  s.wake()
  eq(s.state.value, 'idle')
})

console.log('\n开关面板的边角')

check('面板关闭时如果停在 done，要重新起回落计时', () => {
  s.setPanelOpen(true)
  s.applyDerived('done')
  s.clearTimers() // 模拟：入口随面板卸载，计时器被清掉
  s.setPanelOpen(false)
  eq(s.state.value, 'done', '重挂载时状态还是 done')
  // 关键：它必须重新长出一个计时器，否则会永远停在 done
})

await (async () => {
  await sleep(2000)
  check('重挂载后 done 依然会自己回 idle', () => {
    eq(s.state.value, 'idle', '停在 done 说明 clearTimers 之后没人重新计时')
  })
})()

s.clearTimers()

console.log('')
if (failed) {
  console.log(`✗  ${failed} 条失败\n`)
  process.exit(1)
} else {
  console.log('全部通过\n')
}
process.exit(0)
