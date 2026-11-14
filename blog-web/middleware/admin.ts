/**
 * 后台守卫：登录 + 管理员（含超管）。
 *
 * <p>`isAdmin` 已经在 store 里放宽成 `admin || super`，这里不用重复判断角色。
 *
 * <p>服务端渲染时直接放行：`authStore.user` 存在 localStorage，SSR 读不到，
 * 在服务端判断会让「登录后在后台硬刷新」的管理员被误弹回首页。
 * 真正的权限由后端接口（Spring Security）保证，前端守卫只负责体验。
 */
export default defineNuxtRouteMiddleware(() => {
  if (import.meta.server) return

  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    return navigateTo('/login')
  }
  if (!authStore.isAdmin) {
    return navigateTo('/')
  }
})
