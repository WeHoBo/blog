import { defineStore } from 'pinia'

interface User {
  userId: number
  username: string
  nickname: string
  avatar: string
  role: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = useCookie('token', { maxAge: 60 * 60 * 24 })

  const saved = process.client ? localStorage.getItem('user') : null
  const user = ref<User | null>(saved ? JSON.parse(saved) : null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'admin')

  function setAuth(t: string, u: User) {
    token.value = t
    user.value = u
    if (process.client) localStorage.setItem('user', JSON.stringify(u))
  }

  function logout() {
    token.value = null
    user.value = null
    if (process.client) localStorage.removeItem('user')
  }

  return { token, user, isLoggedIn, isAdmin, setAuth, logout }
})
