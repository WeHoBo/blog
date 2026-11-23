export const useApi = () => {
  const config = useRuntimeConfig()
  const baseURL = config.public.apiBase

  const token = useCookie('token')

  async function request<T>(url: string, opts: any = {}): Promise<T> {
    const tokenValue = token.value
    const res = await $fetch<T>(`${baseURL}${url}`, {
      ...opts,
      headers: {
        // 告诉后端「这次是服务端渲染」，详情接口据此不计阅读数：
        // 页面 HTML 上了 routeRules 缓存后同一 URL 只渲染一次，按 GET 计数会漏记，
        // 改由浏览器挂载后 POST /api/article/{id}/view 打点（见 ArticleDetail.vue）。
        ...(process.server ? { 'X-Blog-Client': 'ssr' } : {}),
        ...opts.headers,
        ...(tokenValue ? { Authorization: `Bearer ${tokenValue}` } : {})
      },
      onResponseError({ response }) {
        // 凭证被撤销之后（账号被禁用、被降权、改了密码），旧 token 会被后端当成未登录，
        // 表现为 401。这里顺手清掉本地登录态，让顶栏立刻变回「登录」——
        // 否则界面还显示着自己的昵称，点什么都失败，用户只会觉得网站坏了。
        if (response.status === 401 && process.client) {
          try {
            const auth = useAuthStore()
            if (auth.isLoggedIn) auth.logout()
          } catch {
            // 极少数在 setup 之外调用 useApi 的场景：拿不到 store 就跳过，
            // 401 该报错还是照常报
          }
        }
        const data = response._data as any
        if (data?.message) {
          throw new Error(data.message)
        }
      }
    })
    return res
  }

  return {
    get: <T>(url: string) => request<T>(url, { method: 'GET' }),
    post: <T>(url: string, body?: any) => request<T>(url, { method: 'POST', body }),
    put: <T>(url: string, body?: any) => request<T>(url, { method: 'PUT', body }),
    del: <T>(url: string) => request<T>(url, { method: 'DELETE' })
  }
}
