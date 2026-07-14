<template>
  <div class="flex justify-center py-32">
    <div class="animate-spin h-8 w-8 border-2 border-blue-600 border-t-transparent rounded-full"></div>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

if (process.client) {
  const token = route.query.token as string
  const userId = route.query.userId as string
  const username = route.query.username as string
  const nickname = route.query.nickname as string
  const avatar = route.query.avatar as string
  const role = route.query.role as string

  if (token) {
    authStore.setAuth(token, {
      userId: Number(userId),
      username,
      nickname: nickname || username,
      avatar: avatar || '',
      role: role || 'user'
    })
  }
  router.push('/')
}
</script>
