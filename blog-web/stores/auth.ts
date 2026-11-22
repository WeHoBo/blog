import { defineStore } from 'pinia'

interface User {
  userId: number
  username: string
  nickname: string
  avatar: string
  /** 个人简介：用户自己在个人中心写，首页右栏作者卡片展示 */
  bio?: string
  role: string
  /** 手机号，未绑定为 null（OAuth 老账号都是这种情况） */
  phone?: string | null
  /**
   * 是否已设置过可用密码。
   *
   * <p>false 表示这是 OAuth 自动建的账号，库里的 password 只是随机占位，
   * 用户不可能知道原文 —— 个人中心据此提示"去设置密码"。
   */
  passwordSet?: boolean
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

  /**
   * 投稿作者：能提交文章、只能看和改自己的文章。
   *
   * <p>⚠️ 这是一个**独立**角色，不是管理员的一种。所以它不会让 {@link isAdmin} 变成 true ——
   * 后台那 8 个页面、37 个管理员接口都跟它无关。它走的是一条平行的
   * `/author` 路径，前端也只有那两三个页面为它放行。
   */
  const isAuthor = computed(() => user.value?.role === 'author')

  /** 有写作入口的人：作者本身，加上管理员（管理员另有后台，但判断入口时一起算） */
  const canWrite = computed(() => isAuthor.value || isAdmin.value)

  /** 角色中文名，页面直接展示用 */
  const roleLabel = computed(() => {
    const r = user.value?.role
    if (r === 'super') return '超级管理员'
    if (r === 'admin') return '管理员'
    if (r === 'author') return '作者'
    return '用户'
  })

  function setAuth(t: string, u: User) {
    token.value = t
    user.value = u
    if (process.client) localStorage.setItem('user', JSON.stringify(u))
  }

  /**
   * 由登录 / 注册接口返回的 LoginVO 直接落库。
   *
   * <p>集中在一处做字段映射：登录、注册、绑定手机三处都要把服务端返回写进 store，
   * 各写一遍的话，将来 LoginVO 加字段就会出现「某个入口进去后少一个字段」，
   * 而且编译器不会报错（对象字面量多缺字段都是合法的）。
   */
  function setAuthFromLogin(vo: any) {
    setAuth(vo.token, {
      userId: vo.userId,
      username: vo.username,
      nickname: vo.nickname,
      avatar: vo.avatar,
      bio: vo.bio,
      role: vo.role,
      phone: vo.phone ?? null,
      passwordSet: !!vo.passwordSet
    })
  }

  function logout() {
    token.value = null
    user.value = null
    if (process.client) localStorage.removeItem('user')
  }

  return {
    token, user, isLoggedIn, isAdmin, isSuper, isAuthor, canWrite, roleLabel,
    setAuth, setAuthFromLogin, logout
  }
})
