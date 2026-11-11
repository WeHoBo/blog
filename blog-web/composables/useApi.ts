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
