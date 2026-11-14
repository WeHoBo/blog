/**
 * 主动清页面缓存（配合 `server/routes/__cache/purge.post.ts`）。
 *
 * 用在下述时机：发布/更新/删除文章、改站点设置（站名、横幅文案、AI 配置）。
 * 不调用它的后果是「改了但首页没变，要等 10–20 秒」——用户会以为是没保存成功。
 *
 * 一律吞掉异常：清缓存失败只意味着「晚几十秒生效」，不该让保存动作报错。
 */
export const usePurgeCache = () => {
  const token = useCookie<string | null>('token')

  return async function purgeCache(): Promise<boolean> {
    const auth = token.value ? `Bearer ${token.value}` : ''
    if (!auth) return false
    try {
      await $fetch('/__cache/purge', {
        method: 'POST',
        headers: { authorization: auth }
      })
      return true
    } catch {
      return false
    }
  }
}
