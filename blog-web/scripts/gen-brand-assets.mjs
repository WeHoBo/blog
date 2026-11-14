/**
 * 由 logo 的**几何定义**生成站点所需的全部品牌图。
 *
 * 为什么不直接缩放源图：源图只有 109×100，放大到 512 必然发虚；而它本身只是
 * 4 条圆角横条（下面 BARS 就是从源图逐像素量出来的坐标），矢量重绘在任意尺寸下都清晰。
 *
 * 改 logo 时：改 BARS / COLORS，然后 `node scripts/gen-brand-assets.mjs`。
 * 会重新产出 logo.svg + 5 个 PNG（favicon 16/32、apple-touch 180、logo 96/512）。
 *
 * 用法：node scripts/gen-brand-assets.mjs
 */
import { createCanvas } from '@napi-rs/canvas'
import { writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT = resolve(HERE, '../public')

/** 画布坐标系（源图量出来的原始像素），内容区 76×58 */
const BOX = { w: 76, h: 58 }
/** [x, y, w, h, 颜色] —— 四条横条自短到长，最短那条用陶土色当强调 */
const BARS = [
  [0, 0, 20, 8, '#8a6350'],
  [0, 17, 39, 8, '#2a251e'],
  [0, 33, 57, 8, '#2a251e'],
  [0, 50, 76, 8, '#2a251e']
]
const RADIUS = 4
/** 纸色底：与站点 --page-bg 一致，小图标在深色标签栏上也不会糊成一团 */
const PAPER = '#F7F2E8'

/** 透明底 SVG（顶栏里放在圆角纸色块上使用，颜色可被 CSS 覆盖也没必要） */
const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${BOX.w} ${BOX.h}" role="img" aria-label="博观">
${BARS.map(([x, y, w, h, c]) => `  <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${RADIUS}" fill="${c}"/>`).join('\n')}
</svg>
`
await writeFile(resolve(OUT, 'logo.svg'), svg, 'utf8')
console.log('[brand] logo.svg')

/**
 * @param {number} size 输出边长
 * @param {number} pad  四周留白占比（16px 要更紧，否则笔画糊成一块）
 */
function render(size, pad) {
  const c = createCanvas(size, size)
  const ctx = c.getContext('2d')
  ctx.fillStyle = PAPER
  ctx.fillRect(0, 0, size, size)
  const scale = (size * (1 - pad * 2)) / BOX.w
  const offX = (size - BOX.w * scale) / 2
  const offY = (size - BOX.h * scale) / 2
  for (const [x, y, w, h, color] of BARS) {
    ctx.fillStyle = color
    // 圆角按比例缩放：小尺寸下 rx 不能超过 height/2，否则变成胶囊、笔画消失
    const r = Math.min(RADIUS * scale, (h * scale) / 2)
    ctx.beginPath()
    ctx.roundRect(offX + x * scale, offY + y * scale, w * scale, h * scale, r)
    ctx.fill()
  }
  return c
}

const targets = [
  { file: 'logo.png', size: 512, pad: 0.1 },
  { file: 'logo-96.png', size: 96, pad: 0.1 },
  { file: 'apple-touch-icon.png', size: 180, pad: 0.14 },
  { file: 'favicon-32.png', size: 32, pad: 0.06 },
  { file: 'favicon-16.png', size: 16, pad: 0.02 }
]

for (const t of targets) {
  const canvas = render(t.size, t.pad)
  await writeFile(resolve(OUT, t.file), await canvas.encode('png'))
  console.log(`[brand] ${t.file}  ${t.size}×${t.size}`)
}
