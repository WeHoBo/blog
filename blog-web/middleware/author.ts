/**
 * 作者后台守卫：登录 + role === 'author'。
 *
 * <p>为什么不复用 `admin` 中间件：`isAdmin` 是 `admin || super`，
 * 而 author **不是**管理员的一种 —— 后台那 8 个页面、37 个管理员接口都跟它无关。
 * 混用会让作者被放进 `/admin`，然后在一个个 403 里迷路。
 *
 * <p>服务端直接放行：`authStore.user` 在 localStorage，SSR 读不到，
 * 在服务端判会把「登录后硬刷新」的作者误弹回首页。真正的权限由后端
 * `@PreAuthorize("hasRole('author')")` 保证，这里只管体验。
 */
export default defineNuxtRouteMiddleware(() => {
  if (import.meta.server) return

  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    return navigateTo('/login')
  }
  if (!authStore.isAuthor) {
    return navigateTo('/')
  }
})
