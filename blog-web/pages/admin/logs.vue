<template>
  <div>
    <h1 class="text-2xl font-bold mb-6">操作日志</h1>
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
          <tr>
            <th class="px-4 py-3 text-left w-40">时间</th>
            <th class="px-4 py-3 text-left w-24">用户</th>
            <th class="px-4 py-3 text-left">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id" class="border-t dark:border-gray-700">
            <td class="px-4 py-3 text-gray-400 text-xs">{{ log.createTime?.substring(0, 16) }}</td>
            <td class="px-4 py-3 text-gray-600 dark:text-gray-300 text-xs font-medium">{{ log.username }}</td>
            <td class="px-4 py-3 text-sm">{{ log.action }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })
const { get } = useApi()
const logs = ref<any[]>([])
const { data } = await useAsyncData('logs', async () => {
  const res = await get<any>('/admin/log/list')
  if (res.code === 200 && res.data) logs.value = res.data.records || []
})
</script>
