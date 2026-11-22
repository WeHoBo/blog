/**
 * 超管判断（带水合保护）
 *
 * `authStore.user` 存在 localStorage，SSR 读不到 → `isSuper` 在服务端恒为 false。
 * 如果模板里直接写 `v-if="authStore.isSuper"`，会出现「服务端不渲染 / 客户端渲染」
 * 的不一致（本项目在顶栏菜单、右栏作者卡上都踩过）。
 *
 * 这里统一返回一个 **mounted 之后才置 true** 的 ref：服务端与首次客户端渲染都是 false，
 * 挂载后再切到真实值，两端输出一致。
 *
 * 用法：`const isSuper = useIsSuper()`（模板里自动解包，脚本里用 `isSuper.value`）
 */
export function useIsSuper() {
  const authStore = useAuthStore()
  const isSuper = ref(false)
  onMounted(() => { isSuper.value = authStore.isSuper })
  return isSuper
}
