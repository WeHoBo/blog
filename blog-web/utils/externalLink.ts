/**
 * 外部来源链接的安全处理（纯函数，不依赖 vue / DOM）。
 *
 * 为什么前端还要再校验一次协议：服务端（`web_search.py`）已经过滤过 `javascript:` /
 * `data:` 这类协议，但那是"另一处代码"。纵深防御在这里成本极低 ——
 * 一个 `<a :href="s.url">` 只要漏掉一次校验，就是可点击的 XSS 入口。
 */

const ALLOWED = ['http:', 'https:']

/**
 * 返回可安全渲染的 URL，非法一律返回 null。
 *
 * 用 `new URL()` 而不是正则：正则判协议会被 `java\nscript:`、前后空格、
 * 大小写混写绕过，URL 解析器不会。
 */
export function safeExternalUrl(url: unknown): string | null {
  if (typeof url !== 'string') return null
  const raw = url.trim()
  if (!raw) return null
  try {
    const parsed = new URL(raw)
    if (!ALLOWED.includes(parsed.protocol)) return null
    // 必须有主机名，`http:///path` 这种解析出来 host 为空
    if (!parsed.hostname) return null
    return parsed.toString()
  } catch {
    return null
  }
}

/** 取域名用于卡片副行（去掉 www. 与端口）。解析失败返回空串，模板里退回不显示。 */
export function domainOf(url: unknown): string {
  const safe = safeExternalUrl(url)
  if (!safe) return ''
  try {
    return new URL(safe).hostname.toLowerCase().replace(/^www\./, '')
  } catch {
    return ''
  }
}

/**
 * 站外来来源判定。
 *
 * 注意不能只看 `type === 'web'`：服务端将来可能加别的站外类型（比如 `news`），
 * 所以"有合法外部 URL 且不是站内相对路径"也要算站外。
 */
export function isExternalSource(s: any): boolean {
  if (!s) return false
  if (s.type === 'web') return true
  const url = typeof s.url === 'string' ? s.url.trim() : ''
  return /^https?:\/\//i.test(url)
}

/** 卡片主标题：超过 max 字截断。中文按字符算，不能用 slice(0, n) 之外的估算。 */
export function truncateTitle(title: unknown, max = 24): string {
  const text = String(title ?? '').trim()
  if (!text) return '未命名来源'
  return text.length > max ? text.slice(0, max) + '…' : text
}

/**
 * 站内路径白名单：只接受以单个 `/` 开头的相对路径。
 *
 * 为什么需要它：卡片模板里"站外链接 <a> → 站内 <NuxtLink> → 纯文本"是三档
 * `v-if / v-else-if / v-else`。如果 `v-else-if` 只判断 `s.url` 是否为真，
 * 那么一条 `type=web` 但 URL 非法的来源（`javascript:…`、`data:…`）就会从
 * 第一档掉进第二档，被当成站内路由渲染 —— 等于绕开了上面的协议校验。
 * `//evil.com` 也一起挡掉（协议相对 URL，会被当外部地址但不是绝对 http）。
 */
export function internalPath(url: unknown): string | null {
  if (typeof url !== 'string') return null
  const raw = url.trim()
  if (!raw.startsWith('/')) return null
  if (raw.startsWith('//')) return null
  return raw
}

