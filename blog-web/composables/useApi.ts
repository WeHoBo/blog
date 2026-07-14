export const useApi = () => {
  const config = useRuntimeConfig()
  const baseURL = config.public.apiBase

  const token = useCookie('token')

  async function request<T>(url: string, opts: any = {}): Promise<T> {
    const tokenValue = token.value
    const res = await $fetch<T>(`${baseURL}${url}`, {
      ...opts,
      headers: {
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
