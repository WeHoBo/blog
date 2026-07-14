export default defineNuxtRouteMiddleware(() => {
  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    return navigateTo('/login')
  }
  if (!authStore.isAdmin) {
    return navigateTo('/')
  }
})
