/**
 * 超管守卫：只有 role === 'super' 能进。
 *
 * <p>为什么要在客户端先拦一道：后端已经用 hasRole('super') 兜住了，但如果不拦，
 * 普通管理员点进「用户管理」会看到一页空白 + 一串 403 toast，看起来像坏了。
 * 这里直接把他送回后台首页，并在菜单里把入口藏掉（见 layouts/admin.vue）。
 *
 * <p>服务端渲染时直接放行：`authStore.user` 存在 localStorage，SSR 读不到，
 * 在服务端判断会把刷新的超管误判成普通用户弹回首页。真正的权限由后端接口保证，
 * 前端守卫只负责体验。
 */
export default defineNuxtRouteMiddleware((to) => {
  if (import.meta.server) return

  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    return navigateTo('/login')
  }
  if (!authStore.isSuper) {
    return navigateTo('/admin')
  }
})
