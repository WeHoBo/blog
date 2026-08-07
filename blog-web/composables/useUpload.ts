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

  return { upload }
}
