<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex flex-col relative">
    <StarryBackground />
    <div class="relative z-10 flex flex-col flex-1">
      <Header />
      <main class="flex-1 px-4">
        <slot />
      </main>
      <Footer />
    </div>
  </div>
</template>
