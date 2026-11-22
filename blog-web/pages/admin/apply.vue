<template>
  <div>
    <div class="flex justify-between items-start gap-3 flex-wrap mb-6">
      <div>
        <h1 class="text-2xl font-bold">申请设置</h1>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          首页「申请管理员发布文章」弹窗里的题目。答对 {{ passCount }} 题即自动获得作者权限（提交文章需你审核）。
        </p>
      </div>
      <button @click="openDialog()" :disabled="questions.length >= 5"
        class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 disabled:opacity-40 transition">
        添加题目
      </button>
    </div>

    <!-- 全局设置 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 sm:p-5 mb-4">
      <div class="flex flex-wrap items-center gap-x-8 gap-y-4">
        <label class="flex items-center gap-2 text-sm">
          <input type="checkbox" v-model="form.enabled" :true-value="1" :false-value="0" />
          开放申请入口
        </label>
        <div class="flex items-center gap-2 text-sm">
          <span class="text-gray-500">答对</span>
          <input v-model.number="form.passCount" type="number" min="1" :max="Math.max(1, questions.length)"
            aria-label="通过所需题数"
            class="w-16 px-2 py-1.5 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <span class="text-gray-500">题算通过</span>
        </div>
        <button @click="saveSetting" :disabled="savingSetting"
          class="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:border-gray-600 disabled:opacity-50 transition">
          {{ savingSetting ? '保存中…' : '保存设置' }}
        </button>
      </div>
      <p class="mt-3 text-[11px] text-gray-400 leading-relaxed">
        通过的题数会自动限制在 1 ～ {{ Math.max(1, questions.length) }} 之间；题目的<b>正确答案不会下发给前端</b>，
        判分只在服务端做。每人每天最多提交 3 次。
      </p>
    </div>

    <!-- 题目列表 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[640px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="px-4 py-3 text-left w-12">序号</th>
              <th class="px-4 py-3 text-left">题干</th>
              <th class="px-4 py-3 text-left w-20">正确答案</th>
              <th class="px-4 py-3 text-left w-16">启用</th>
              <th class="px-4 py-3 text-left w-28">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!questions.length">
              <td colspan="5" class="px-4 py-10 text-center text-gray-400 text-sm">
                还没有题目。至少加 1 道，首页的申请入口才有内容。
              </td>
            </tr>
            <tr v-for="(q, i) in questions" :key="q.id"
              class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-3 text-gray-400">{{ i + 1 }}</td>
              <td class="px-4 py-3">
                <div class="text-gray-800 dark:text-gray-200 break-words">{{ q.question }}</div>
                <div class="mt-1 text-[11px] text-gray-400 space-x-2">
                  <span>A. {{ q.optionA }}</span><span>B. {{ q.optionB }}</span><span>C. {{ q.optionC }}</span>
                </div>
              </td>
              <td class="px-4 py-3 text-primary-600 dark:text-primary-400">{{ ['A', 'B', 'C'][q.answer] || '?' }}</td>
              <td class="px-4 py-3">
                <span :class="q.enabled === 1 ? 'text-green-600' : 'text-gray-400'">
                  {{ q.enabled === 1 ? '是' : '否' }}
                </span>
              </td>
              <td class="px-4 py-3 whitespace-nowrap">
                <button @click="openDialog(q)" class="text-primary-500 hover:underline text-xs mr-3">编辑</button>
                <button @click="handleDelete(q.id)" class="text-red-500 hover:underline text-xs">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="dialogVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4 py-6"
      @click.self="dialogVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-full max-w-lg max-h-[88vh] overflow-y-auto">
        <h2 class="text-lg font-bold mb-4">{{ editing ? '编辑题目' : '添加题目' }}</h2>
        <div class="space-y-3">
          <div>
            <label class="text-xs text-gray-500 mb-1 block">题干</label>
            <textarea v-model="dialogForm.question" rows="2" aria-label="题干"
              class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          </div>
          <div v-for="(key, idx) in ['optionA', 'optionB', 'optionC']" :key="key">
            <label class="text-xs text-gray-500 mb-1 block">选项 {{ ['A', 'B', 'C'][idx] }}</label>
            <input v-model="(dialogForm as any)[key]" aria-label="选项"
              class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
          </div>
          <div class="flex flex-wrap items-end gap-4">
            <div>
              <label class="text-xs text-gray-500 mb-1 block">正确答案</label>
              <select v-model.number="dialogForm.answer" aria-label="正确答案"
                class="px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm">
                <option :value="0">A</option>
                <option :value="1">B</option>
                <option :value="2">C</option>
              </select>
            </div>
            <label class="flex items-center gap-2 text-sm pb-2">
              <input type="checkbox" v-model="dialogForm.enabled" :true-value="1" :false-value="0" />
              启用
            </label>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-5">
          <button @click="dialogVisible = false" class="px-4 py-2 border rounded text-sm dark:border-gray-600">取消</button>
          <button @click="handleSave" :disabled="saving"
            class="px-4 py-2 bg-primary-600 text-white rounded text-sm hover:bg-primary-700 disabled:opacity-50 transition">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'super-admin', layout: 'admin' })

const { get, post, put, del } = useApi()
const { toast, confirmDialog } = useFeedback()

const questions = ref<any[]>([])
const form = reactive({ passCount: 1, enabled: 1 })
const savingSetting = ref(false)

const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const dialogForm = reactive<any>({
  id: null, question: '', optionA: '', optionB: '', optionC: '', answer: 0, enabled: 1
})

async function fetchAll() {
  try {
    const [qRes, sRes] = await Promise.all([
      get<any>('/admin/apply/questions'),
      get<any>('/admin/apply/setting')
    ])
    if (qRes?.code === 200) questions.value = qRes.data || []
    if (sRes?.code === 200 && sRes.data) {
      form.passCount = sRes.data.passCount ?? 1
      form.enabled = sRes.data.enabled ?? 1
    }
  } catch (e: any) {
    toast(e?.message || '加载失败', 'error')
  }
}

async function saveSetting() {
  if (savingSetting.value) return
  savingSetting.value = true
  try {
    const res = await put<any>('/admin/apply/setting', {
      passCount: form.passCount,
      enabled: form.enabled
    })
    if (res?.code !== 200) {
      toast(res?.message || '保存失败', 'error')
      return
    }
    // 后端会把 passCount 限制在 1..题目数，回写一次免得界面显示的和实际不一致
    if (res.data) form.passCount = res.data.passCount
    toast('已保存', 'success')
  } catch (e: any) {
    toast(e?.message || '保存失败', 'error')
  } finally {
    savingSetting.value = false
  }
}

function openDialog(row?: any) {
  if (row) {
    editing.value = true
    Object.assign(dialogForm, {
      id: row.id, question: row.question,
      optionA: row.optionA, optionB: row.optionB, optionC: row.optionC,
      answer: row.answer ?? 0, enabled: row.enabled ?? 1
    })
  } else {
    editing.value = false
    Object.assign(dialogForm, {
      id: null, question: '', optionA: '', optionB: '', optionC: '', answer: 0, enabled: 1
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (saving.value) return
  if (!dialogForm.question.trim()) {
    toast('题干不能为空', 'error')
    return
  }
  if (!dialogForm.optionA?.trim() || !dialogForm.optionB?.trim() || !dialogForm.optionC?.trim()) {
    toast('三个选项都要填', 'error')
    return
  }
  saving.value = true
  try {
    const res = await post<any>('/admin/apply/questions', dialogForm)
    if (res?.code !== 200) {
      toast(res?.message || '保存失败', 'error')
      return
    }
    dialogVisible.value = false
    toast('已保存', 'success')
    fetchAll()
  } catch (e: any) {
    toast(e?.message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  if (!(await confirmDialog('删除这道题？已答过题的人不受影响，但之后的申请会少一道。', '删除题目', 'danger'))) return
  try {
    const res = await del<any>(`/admin/apply/questions/${id}`)
    if (res?.code !== 200) {
      toast(res?.message || '删除失败', 'error')
      return
    }
    toast('已删除', 'success')
    fetchAll()
  } catch (e: any) {
    toast(e?.message || '删除失败', 'error')
  }
}

onMounted(fetchAll)
</script>
