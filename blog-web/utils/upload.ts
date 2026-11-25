const backgroundExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'ico', 'avif']
export const backgroundAccept = backgroundExtensions.map(ext => '.' + ext).join(',')
export const backgroundFormats = backgroundExtensions.join('、').toUpperCase()

export function validateBackground(file: Pick<File, 'name' | 'size'>): string {
  if (file.size === 0) return '图片文件为空'
  if (file.size > 10 * 1024 * 1024) return '背景图片不能超过 10 MiB，请缩小图片后再上传'
  const ext = /\.([^.]+)$/.exec(file.name)?.[1].toLowerCase()
  return ext && backgroundExtensions.includes(ext) ? '' : '不支持该图片格式，请选择 ' + backgroundFormats
}

export function uploadResult(body: unknown, status: number, requestId: string | null): string {
  const result = body && typeof body === 'object' ? body as Record<string, unknown> : {}
  if (status >= 200 && status < 300 && result.code === 200 && typeof result.data === 'string' && result.data) {
    return result.data
  }
  const fallback: Record<number, string> = {
    401: '登录已失效，请重新登录', 403: '没有上传权限',
    413: '文件超出上传大小限制', 415: '不支持此文件格式'
  }
  const message = typeof result.message === 'string' && result.message ? result.message : fallback[status] || `上传服务返回 HTTP ${status}`
  throw new Error(requestId ? `${message}（请求 ID：${requestId}）` : message)
}
