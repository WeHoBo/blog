export const useUpload = () => {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase
  const token = useCookie('token')

  async function upload(file: File): Promise<string> {
    const formData = new FormData()
    formData.append('file', file)
    const res: any = await $fetch(`${apiBase}/upload`, {
      method: 'POST',
      body: formData,
      headers: token.value ? { Authorization: `Bearer ${token.value}` } : {}
    })
    if (res.code === 200) {
      return res.data
    }
    throw new Error(res.message || '上传失败')
  }

  /**
   * 带进度的上传。这里直接用 XHR 逐块回报进度，行为可控、SSR 下也不会误触发。
   */
  function uploadWithProgress(file: File, onProgress?: (percent: number) => void): Promise<string> {
    return new Promise((resolve, reject) => {
      const formData = new FormData()
      formData.append('file', file)
      const xhr = new XMLHttpRequest()
      xhr.open('POST', `${apiBase}/upload`)
      if (token.value) {
        xhr.setRequestHeader('Authorization', `Bearer ${token.value}`)
      }
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) {
          onProgress(Math.round((e.loaded / e.total) * 100))
        }
      }
      xhr.onload = () => {
        try {
          const res = JSON.parse(xhr.responseText)
          if (res.code === 200) {
            resolve(res.data)
          } else {
            reject(new Error(res.message || '上传失败'))
          }
        } catch {
          reject(new Error('服务器返回格式异常'))
        }
      }
      xhr.onerror = () => reject(new Error('网络错误，上传失败'))
      xhr.send(formData)
    })
  }

  /**
   * 前端压缩：超过 1MB 的图片转成 WebP 并压到 1600px 以内再上传。
   * 博客插图重在清晰可读，1600px 对绝大多数正文插图已经绰绰有余，
   * 能显著减少对象存储流量与首屏加载时间。压缩失败时返回原文件，绝不阻塞上传。
   */
  async function compressImage(file: File, maxWidth = 1600, quality = 0.82): Promise<File> {
    if (!file.type.startsWith('image/')) return file
    if (file.type === 'image/gif' || file.type === 'image/svg+xml') return file
    if (file.size < 1024 * 1024) return file
    try {
      const bitmap = await createImageBitmap(file)
      const scale = bitmap.width > maxWidth ? maxWidth / bitmap.width : 1
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(bitmap.width * scale)
      canvas.height = Math.round(bitmap.height * scale)
      const ctx = canvas.getContext('2d')
      if (!ctx) return file
      ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
      bitmap.close()
      const blob: Blob = await new Promise((resolve, reject) =>
        canvas.toBlob(b => (b ? resolve(b) : reject(new Error('encode failed'))), 'image/webp', quality)
      )
      const name = file.name.replace(/\.[^.]+$/, '') + '.webp'
      return new File([blob], name, { type: 'image/webp' })
    } catch {
      return file
    }
  }

  return { upload, uploadWithProgress, compressImage }
}
