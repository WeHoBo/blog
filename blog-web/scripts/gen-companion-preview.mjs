/**
 * gen-companion-preview.mjs —— 把 CompanionCss 的 8 个状态铺成一页，用于肉眼验收
 *
 * 为什么要这一步：本机不能 `nuxi dev`，但「小鱼长什么样、每个状态分不分得清」
 * 是必须看见才能判断的。所以这里**直接从 .vue 里读模板和样式**（不抄一份），
 * 拼成一个静态 HTML —— 改坏了 .vue，预览页会跟着坏，不会骗人。
 *
 * 用法：
 *   node scripts/gen-companion-preview.mjs
 *   → _poc/companion/states.html（直接用浏览器打开，或喂给 clipshot.mjs 截图）
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = (p) => readFileSync(resolve(ROOT, p), 'utf8')

const src = read('components/CompanionCss.vue')

function pick(tag) {
  const re = new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`)
  const m = src.match(re)
  if (!m) throw new Error(`CompanionCss.vue 里找不到 <${tag}> —— 结构变了，预览脚本要跟着改`)
  return m[1]
}

const template = pick('template')
const style = pick('style')

/** 取出 <button> 内部的图形部分 */
const openTagEnd = template.indexOf('>', template.indexOf('<button'))
const closeIdx = template.lastIndexOf('</button>')
if (openTagEnd < 0 || closeIdx < 0) throw new Error('找不到 <button> 的开闭标签')
let inner = template.slice(openTagEnd + 1, closeIdx)

/* 把 Vue 语法降级成静态 HTML。
   每一处都是"找不到就报错"，避免模板改了之后预览页静默失真。 */
function replaceOnce(hay, from, to, label) {
  if (!hay.includes(from)) throw new Error(`模板里找不到「${label}」，预览脚本需要同步更新`)
  return hay.split(from).join(to)
}
inner = replaceOnce(inner, ':style="pupilStyle"', 'style="transform:translate(0px,0px)"', ':style="pupilStyle"')
// HTML 不认 <i /> 这种自闭合，必须写全，否则后面的节点会被吞进去
inner = inner.split('<i />').join('<i></i>')

const STATES = ['idle', 'hint', 'thinking', 'tools', 'typing', 'done', 'error', 'sleep']
const HINTS = {
  idle: '待机 · 呼吸 + 眨眼 + 视线跟随',
  hint: '首次到访 · 轻跳两下',
  thinking: '已提问 · 三点 + 歪头',
  tools: '调工具 · 气泡带工具名',
  typing: '流出字 · 快速小幅抖',
  done: '答完了 · 笑眼 + 蹦一下',
  error: '失败 · × 眼 + 下沉',
  sleep: '90s 无人理 · 闭眼 + Z',
}

const cards = STATES.map(
  (st) => `
      <div class="card">
        <div class="stage"><div class="cc s-${st}">${inner}</div></div>
        <div class="name">${st}</div>
        <div class="hint">${HINTS[st]}</div>
      </div>`,
).join('')

const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>AI 助手入口 · 8 状态预览</title>
<style>
  :root {
    --ph: 22;
    --ps: 26%;
    --pl: 40%;
    --primary: hsl(var(--ph), var(--ps), var(--pl));
    --primary-bg: hsla(var(--ph), var(--ps), var(--pl), 0.1);
    --page-bg: hsl(38 30% 94%);
  }
  .dark {
    --primary: hsl(var(--ph), var(--ps), 52%);
    --page-bg: hsl(28 10% 8%);
    /* 深色区必须有浅色文字，否则状态名整片看不见（第一版就栽在这） */
    color: hsl(36 14% 84%);
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 28px;
    background: var(--page-bg);
    color: hsl(26 12% 20%);
    font-family: -apple-system, 'Segoe UI', 'Noto Sans SC', sans-serif;
  }
  .dark body, body.dark { color: hsl(36 14% 84%); }
  h2 { font-size: 13px; font-weight: 600; letter-spacing: .08em; opacity: .55; margin: 0 0 14px; }
  .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 30px; }
  .card {
    background: hsl(38 26% 97%);
    border: 1px solid hsl(30 16% 88%);
    border-radius: 14px;
    padding: 14px 10px 12px;
    text-align: center;
  }
  .dark .card { background: hsl(28 8% 12%); border-color: hsl(28 8% 18%); }
  .stage { height: 96px; display: flex; align-items: center; justify-content: center; }
  .name { font-size: 12px; font-weight: 600; margin-top: 6px; }
  .hint { font-size: 10px; opacity: .5; margin-top: 3px; line-height: 1.4; }
  /* 预览里让 .cc 不参与 fixed 定位（真实组件由 CompanionEntry 定位） */
  .cc { margin: 0 auto; }
${style}
</style>
</head>
<body>
  <h2>8 个状态（亮色）</h2>
  <div class="grid">${cards}</div>

  <div class="dark" style="padding: 20px; border-radius: 14px;">
    <h2 style="margin-bottom: 14px;">8 个状态（暗色）</h2>
    <div class="grid">${cards}</div>
  </div>
</body>
</html>
`

const outDir = resolve(ROOT, '../_poc/companion')
mkdirSync(outDir, { recursive: true })
const out = resolve(outDir, 'states.html')
writeFileSync(out, html, 'utf8')
console.log('已生成', out)
