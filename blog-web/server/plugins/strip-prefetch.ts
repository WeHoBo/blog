import { existsSync, readdirSync, statSync } from 'node:fs'
import { resolve } from 'node:path'

// 大 chunk 阈值：超过此大小的 chunk 一律不做 prefetch（如 mermaid/hljs 及其子模块），
// 避免首屏空闲时下载 1MB+ 的资源；页面级 chunk（一般 < 90KB）保留 prefetch，
// 保证客户端导航是预加载的、点击后立即响应。
const BIG_CHUNK_SIZE = 90 * 1024

let bigChunks: Set<string> | null = null

function loadBigChunks(): Set<string> {
  if (bigChunks) return bigChunks
  bigChunks = new Set()
  const candidates = [
    resolve(process.cwd(), 'public', '_nuxt'),
    resolve(process.cwd(), '.output', 'public', '_nuxt')
  ]
  for (const dir of candidates) {
    if (!existsSync(dir)) continue
    for (const file of readdirSync(dir)) {
      if (!file.endsWith('.js')) continue
      try {
        if (statSync(resolve(dir, file)).size > BIG_CHUNK_SIZE) {
          bigChunks.add(file)
        }
      } catch { /* ignore */ }
    }
    break
  }
  return bigChunks
}

function isBigPrefetch(tag: string): boolean {
  if (!tag.includes('rel="prefetch"')) return false
  // 名字兜底过滤
  if (/mermaid|hljs|cynefin|cytoscape|katex/i.test(tag)) return true
  // 按文件大小过滤
  const href = /href="([^"]+)"/.exec(tag)
  if (!href) return false
  const name = href[1].split('/').pop() || ''
  return loadBigChunks().has(name)
}

export default defineNitroPlugin((nitroApp) => {
  const strip = (html: string) =>
    html.replace(/<link\b[^>]*\brel="prefetch"[^>]*>/g, (tag) => (isBigPrefetch(tag) ? '' : tag))

  nitroApp.hooks.hook('render:html', (html) => {
    for (const key of ['head', 'bodyPrepend', 'body', 'bodyAppend'] as const) {
      const arr = (html as any)[key]
      if (!Array.isArray(arr)) continue
      ;(html as any)[key] = arr.map((item: any) => {
        if (typeof item === 'string') return strip(item)
        if (Array.isArray(item)) return item.map((s: any) => (typeof s === 'string' ? strip(s) : s))
        return item
      })
    }
  })
})
