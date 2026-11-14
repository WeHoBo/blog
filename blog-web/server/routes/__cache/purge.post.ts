/**
 * 清除 Nitro 的页面缓存（`routeRules` 里那批 `cache: { maxAge, swr }`）。
 *
 * 为什么需要它：首页缓 20s、详情页缓 10s。发完文章回到首页，看到的还是旧 HTML，
 * 最久要等 20 秒 —— 用户层面就是「新增文章主页有延迟」。缩短 maxAge 只是把延迟变短，
 * 治不了根；正确做法是「内容变了就把缓存清掉」，让下次访问必然重新渲染。
 *
 * 为什么放在 `/__cache/purge` 而不是 `/api/...`：
 * `routeRules` 把 `/api/**` 整体 proxy 到 Spring Boot，Nitro 这边根本没有机会接住；
 * 放在 /api 下会被转发给后端，得到一个 404。
 *
 * 鉴权：直接拿浏览器带过来的 token 去问后端 `/api/auth/me`，确认是 admin 才清。
 * 不额外引入密钥、不新增配置项 —— 少一个能配错的地方。
 * （否则这就是一个人人可调的「把缓存打光」的按钮，属于低成本 DoS 入口。）
 */
export default defineEventHandler(async (event) => {
  const auth = String(getRequestHeader(event, 'authorization') || '')
  if (!/^bearer\s+\S+/i.test(auth)) {
    throw createError({ statusCode: 401, statusMessage: '需要管理员凭证' })
  }

  // 服务端取数不能用相对路径（没有 origin），直接打本机后端；
  // 与 nuxt.config 里 `/api/**` 的 proxy 目标保持一致。
  const origin = process.env.BLOG_API_ORIGIN || 'http://127.0.0.1:8080'
  let role = ''
  try {
    const me = await $fetch<{ code?: number; data?: { role?: string } }>(`${origin}/api/auth/me`, {
      headers: { authorization: auth },
      timeout: 3000
    })
    role = String(me?.data?.role || '')
  } catch {
    throw createError({ statusCode: 401, statusMessage: '凭证已失效，请重新登录后再试' })
  }
  // 超管（role=super）也必须能清缓存 —— 否则它发完文章页面不更新，
  // 反而比普通管理员少一项能力。后端把 super 当「管理员 + 额外权限」，这里要跟上。
  if (!/admin|super/i.test(role)) {
    throw createError({ statusCode: 403, statusMessage: '需要管理员权限' })
  }

  const storage = useStorage('cache')
  const keys = await storage.getKeys()
  for (const key of keys) {
    await storage.removeItem(key)
  }
  return { code: 200, message: `已清除 ${keys.length} 条页面缓存`, data: { removed: keys.length } }
})
