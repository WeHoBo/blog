<template>
  <div class="fixed inset-0 pointer-events-none overflow-hidden" style="z-index: 0">
    <!-- Day mode: floating light particles -->
    <div v-if="colorMode.preference !== 'dark'" class="absolute inset-0">
      <div
        v-for="i in 20"
        :key="'day'+i"
        class="absolute rounded-full opacity-20"
        :style="{
          width: 4 + Math.random() * 8 + 'px',
          height: 4 + Math.random() * 8 + 'px',
          left: Math.random() * 100 + '%',
          top: Math.random() * 100 + '%',
          background: ['#60a5fa', '#a78bfa', '#f472b6', '#34d399'][i % 4],
          animation: `floatUp ${4 + Math.random() * 6}s ease-in-out infinite`,
          animationDelay: Math.random() * 5 + 's',
          transform: `translateY(${scrollY * (0.03 + Math.random() * 0.05)}px)`
        }"
      />
    </div>

    <!-- Night mode: stars -->
    <div v-else class="absolute inset-0">
      <!-- Small stars layer (slow parallax) -->
      <div class="absolute inset-0" :style="{ transform: `translateY(${scrollY * 0.02}px)` }">
        <div
          v-for="i in 80"
          :key="'s'+i"
          class="absolute rounded-full bg-white"
          :style="{
            width: 1 + Math.random() * 2 + 'px',
            height: 1 + Math.random() * 2 + 'px',
            left: Math.random() * 100 + '%',
            top: Math.random() * 100 + '%',
            opacity: 0.4 + Math.random() * 0.6,
            animation: `twinkle ${2 + Math.random() * 4}s ease-in-out infinite`,
            animationDelay: Math.random() * 3 + 's'
          }"
        />
      </div>

      <!-- Medium stars layer (medium parallax) -->
      <div class="absolute inset-0" :style="{ transform: `translateY(${scrollY * 0.05}px)` }">
        <div
          v-for="i in 40"
          :key="'m'+i"
          class="absolute rounded-full bg-white"
          :style="{
            width: 2 + Math.random() * 3 + 'px',
            height: 2 + Math.random() * 3 + 'px',
            left: Math.random() * 100 + '%',
            top: Math.random() * 100 + '%',
            opacity: 0.5 + Math.random() * 0.5,
            boxShadow: '0 0 ' + (2 + Math.random() * 4) + 'px rgba(255,255,255,0.5)',
            animation: `twinkle ${1.5 + Math.random() * 3}s ease-in-out infinite`,
            animationDelay: Math.random() * 2 + 's'
          }"
        />
      </div>

      <!-- Bright stars layer (fast parallax) -->
      <div class="absolute inset-0" :style="{ transform: `translateY(${scrollY * 0.08}px)` }">
        <div
          v-for="i in 15"
          :key="'b'+i"
          class="absolute rounded-full"
          :style="{
            width: 2 + Math.random() * 3 + 'px',
            height: 2 + Math.random() * 3 + 'px',
            left: Math.random() * 100 + '%',
            top: Math.random() * 100 + '%',
            background: ['#fef3c7', '#bfdbfe', '#e0e7ff'][i % 3],
            opacity: 0.7 + Math.random() * 0.3,
            boxShadow: '0 0 ' + (3 + Math.random() * 6) + 'px ' + ['#fef08a', '#93c5fd', '#a5b4fc'][i % 3],
            animation: `twinkle ${1 + Math.random() * 2.5}s ease-in-out infinite`,
            animationDelay: Math.random() * 1.5 + 's'
          }"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const colorMode = useColorMode()
const scrollY = ref(0)

onMounted(() => {
  window.addEventListener('scroll', () => {
    scrollY.value = window.scrollY
  })
})
</script>

<style scoped>
@keyframes twinkle {
  0%, 100% { opacity: 0.3; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.3); }
}

@keyframes floatUp {
  0%, 100% { transform: translateY(0px) scale(1); opacity: 0.15; }
  50% { transform: translateY(-30px) scale(1.1); opacity: 0.3; }
}
</style>
