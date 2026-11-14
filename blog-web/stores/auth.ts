import { defineStore } from 'pinia'

interface User {
  userId: number
  username: string
  nickname: string
  avatar: string
  /** 个人简介：用户自己在个人中心写，首页右栏作者卡片展示 */
  bio?: string
  role: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = useCookie('token', { maxAge: 60 * 60 * 24 })

  const saved = process.client ? localStorage.getItem('user') : null
  const user = ref<User | null>(saved ? JSON.parse(saved) : null)

  const isLoggedIn = computed(() => !!token.value)

  /**
   * 能进后台。
   *
   * <p>注意这里必须是「admin 或 super」而不是 `=== 'admin'` —— 超管在后端是被当成
   * 管理员 + 额外权限的角色（过滤器里同时发 ROLE_admin 和 ROLE_super），
   * 如果前端还按精确匹配，超管点「后台」会被自己的守卫弹回首页。
   */
  const isAdmin = computed(() => user.value?.role === 'admin' || user.value?.role === 'super')

  /** 超级管理员：用户管理、AI 模型增删改、预算限额、文章彻底删除 */
  const isSuper = computed(() => user.value?.role === 'super')

  /** 角色中文名，页面直接展示用 */
  const roleLabel = computed(() => {
    const r = user.value?.role
    if (r === 'super') return '超级管理员'
    if (r === 'admin') return '管理员'
    return '用户'
  })

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

  return { token, user, isLoggedIn, isAdmin, isSuper, roleLabel, setAuth, logout }
})
