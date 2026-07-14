interface LoginForm {
  username: string
  password: string
}

interface RegisterForm {
  username: string
  password: string
  nickname?: string
  email?: string
}

export const useAuth = () => {
  const { post } = useApi()
  const authStore = useAuthStore()

  async function login(form: LoginForm) {
    const res = await post<any>('/auth/login', form)
    if (res.code === 200) {
      authStore.setAuth(res.data.token, {
        userId: res.data.userId,
        username: res.data.username,
        nickname: res.data.nickname,
        avatar: res.data.avatar,
        role: res.data.role
      })
      return true
    }
    throw new Error(res.message || '登录失败')
  }

  async function register(form: RegisterForm) {
    await post('/auth/register', form)
    return true
  }

  return { login, register }
}
