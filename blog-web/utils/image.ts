/**
 * 图片地址的唯一加工入口。
 *
 * 背景：站点图片全部放在阿里云 OSS（wehobo.oss-cn-wulanchabu），此前前端一律直接引用
 * 原图。后台编辑器上传的截图动辄 200–350KB，而列表卡片实际只显示 144–300px 宽 ——
 * 手机上打开首页要下接近 3MB 的图。实测同一张封面：
 *   原图 341KB → `?x-oss-process=image/resize,w_640/format,webp/quality,q_80` 后 8KB，
 *   体积降 97%，肉眼看不出差别。
 *
 * OSS 的图片处理是**读时生效**的：URL 加参数即可，不需要预先转码、也不需要新增依赖。
 * 所以这里只做一件事 —— 按「实际显示宽度」拼处理参数。
 *
 * 三条硬约束：
 *   1. 非 OSS 域名（读者贴的外链图、GitHub 头像等）必须原样返回，不能拼接参数；
 *   2. 已经带 `x-oss-process` 的地址不再追加，否则参数会叠加导致 OSS 报错；
 *   3. 处理失败时 OSS 会返回原图，不需要前端兜底。
 */

/** 允许加工的 OSS 域名。新增 bucket 时在这里补一条 */
const OSS_HOSTS = ['wehobo.oss-cn-wulanchabu.aliyuncs.com']

export interface OSSImageOptions {
  /** 目标宽度（px）。按元素实际显示宽度 × DPR 给，别直接写原图宽度 */
  w?: number
  /** 压缩质量，默认 80。文字截图建议 85，照片 75–80 */
  q?: number
  /** 输出格式。webp 兼容性已足够，Safari 14+ 都支持 */
  format?: 'webp' | 'avif' | 'jpeg' | 'png'
  /** 是否限制高度（一般不传，让宽度单边缩放保持比例） */
  h?: number
}

/** 该地址是否值得加工（是 OSS 且尚未带处理参数） */
export function isOptimizableImage(url: string | null | undefined): boolean {
  if (!url) return false
  if (!url.startsWith('http')) return false
  if (url.includes('x-oss-process')) return false
  return OSS_HOSTS.some((host) => url.includes(host))
}

/**
 * 给 OSS 图片拼接处理参数。
 *
 * ```ts
 * ossImage(cover, { w: 640 })                  // 列表卡片
 * ossImage(cover, { w: 1280 })                 // 正文配图
 * ossImage(bgImage, { w: 1920, q: 78 })        // 主图背景
 * ```
 */
export function ossImage(url: string | null | undefined, opts: OSSImageOptions = {}): string {
  const u = (url || '').trim()
  if (!isOptimizableImage(u)) return u

  const { w, h, q = 80, format = 'webp' } = opts
  const ops: string[] = []
  if (w && h) ops.push(`resize,w_${w},h_${h},m_lfit`)
  else if (w) ops.push(`resize,w_${w}`)
  else if (h) ops.push(`resize,h_${h}`)
  ops.push(`format,${format}`, `quality,q_${q}`)

  return `${u}${u.includes('?') ? '&' : '?'}x-oss-process=image/${ops.join('/')}`
}

/**
 * 生成 srcset，让 2 倍屏拿 2 倍图。
 * 只需给「宽度随容器变化」的图用（卡片的 width 固定，单个 w 就够了）。
 */
export function ossSrcSet(
  url: string | null | undefined,
  widths: number[],
  opts: Omit<OSSImageOptions, 'w'> = {}
): string {
  if (!isOptimizableImage(url)) return ''
  return widths.map((w) => `${ossImage(url, { ...opts, w })} ${w}w`).join(', ')
}

/** 常用尺寸：集中在这里，避免各处随手写数字导致同一张图出好几个规格 */
export const IMG_W = {
  /** 列表卡片封面（显示 144–300px，2 倍屏） */
  card: 640,
  /** 详情页右侧方形缩略图（显示 80–128px） */
  thumb: 320,
  /** 头像（显示 24–56px） */
  avatar: 160,
  /** 正文配图（正文列宽 704px，2 倍屏够用） */
  content: 1280,
  /** 灯箱、主图背景这类接近满屏的场景 */
  hero: 1920,
} as const
