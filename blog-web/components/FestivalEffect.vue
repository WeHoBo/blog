<template>
  <div class="fixed inset-0 pointer-events-none z-40" v-if="active">
    <div v-for="i in 30" :key="i" class="absolute" :style="itemStyle(i)">
      {{ effectEmoji }}
    </div>
  </div>
</template>

<script setup lang="ts">
const active = ref(false)
const effectEmoji = ref('❄️')

function itemStyle(i: number) {
  const left = Math.random() * 100
  const delay = Math.random() * 5
  const duration = 5 + Math.random() * 10
  return {
    left: `${left}%`,
    animation: `floatDown ${duration}s linear ${delay}s infinite`,
    fontSize: `${12 + Math.random() * 16}px`,
    opacity: 0.6 + Math.random() * 0.4
  }
}

onMounted(() => {
  const month = new Date().getMonth() + 1
  if (month === 12 || month <= 2) { effectEmoji.value = '❄️'; active.value = true }
  else if (month >= 3 && month <= 5) { effectEmoji.value = '🌸'; active.value = true }
  else if (month >= 6 && month <= 8) { effectEmoji.value = '☀️'; active.value = false }
  else { effectEmoji.value = '🍂'; active.value = true }
})
</script>

<style scoped>
@keyframes floatDown {
  0% { transform: translateY(-10vh) rotate(0deg); opacity: 0.8; }
  100% { transform: translateY(110vh) rotate(360deg); opacity: 0.2; }
}
</style>
