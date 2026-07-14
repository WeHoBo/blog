export default defineNuxtPlugin(async () => {
  const authStore = useAuthStore()
  if (authStore.token && !authStore.user) {
    try {
      const { get } = useApi()
      const res = await get<any>('/auth/me')
      if (res.code === 200 && res.data) {
        authStore.setAuth(authStore.token || '', {
          userId: res.data.userId,
          username: res.data.username,
          nickname: res.data.nickname,
          avatar: res.data.avatar,
          role: res.data.role
        })
      }
    } catch {
      authStore.logout()
    }
  }
})
