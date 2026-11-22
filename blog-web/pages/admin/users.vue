<template>
  <div>
    <div class="flex items-baseline justify-between mb-6 gap-3 flex-wrap">
      <h1 class="text-2xl font-bold">用户管理</h1>
      <p class="text-xs text-gray-400">共 {{ users.length }} 个账号</p>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[920px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <!-- 「序号」是本页的行号，不是数据库主键：
                   删掉中间某个账号后它会自动重排成 1..N，不会留空洞。
                   真实 ID 挂在 title 上，鼠标悬停仍可查到（排查数据时用得上）。 -->
              <th class="px-4 py-3 text-left w-16">序号</th>
              <th class="px-4 py-3 text-left">用户名</th>
              <th class="px-4 py-3 text-left">昵称</th>
              <th class="px-4 py-3 text-left">邮箱</th>
              <th class="px-4 py-3 text-left">角色</th>
              <th class="px-4 py-3 text-left">来源</th>
              <th class="px-4 py-3 text-left">状态</th>
              <th class="px-4 py-3 text-left">创建时间</th>
              <th class="px-4 py-3 text-left w-32">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(u, i) in users" :key="u.id" class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-3 text-gray-400 tabular-nums" :title="`数据库 ID: ${u.id}`">{{ i + 1 }}</td>
              <td class="px-4 py-3 font-medium">
                {{ u.username }}
                <span v-if="u.id === authStore.user?.userId" class="ml-1 text-[11px] text-gray-400">（你）</span>
              </td>
              <td class="px-4 py-3">{{ u.nickname || '-' }}</td>
              <td class="px-4 py-3 text-gray-400 text-xs">{{ u.email || '-' }}</td>
              <td class="px-4 py-3">
                <span v-if="u.role === 'super'" class="text-xs font-medium text-primary-600 dark:text-primary-400">超级管理员</span>
                <span v-else-if="u.role === 'author'" class="text-xs font-medium text-amber-600 dark:text-amber-400">作者</span>
                <span v-else :class="u.role === 'admin' ? 'text-red-500' : 'text-gray-500'" class="text-xs font-medium">{{ u.role === 'admin' ? '管理员' : '用户' }}</span>
              </td>
              <td class="px-4 py-3 text-xs text-gray-400">{{ u.source || 'local' }}</td>
              <td class="px-4 py-3">
                <span :class="u.status === 1 ? 'text-green-500' : 'text-red-500'" class="text-xs">{{ u.status === 1 ? '正常' : '禁用' }}</span>
              </td>
              <td class="px-4 py-3 text-gray-400 text-xs whitespace-nowrap">{{ u.createTime?.substring(0, 10) }}</td>
              <td class="px-4 py-3 whitespace-nowrap">
                <button @click="openDialog(u)" class="text-primary-500 hover:underline text-xs mr-3">编辑</button>
                <button @click="handleDelete(u)" :disabled="!canDelete(u)"
                        :title="canDelete(u) ? '删除该账号' : deleteHint(u)"
                        class="text-xs transition disabled:opacity-40 disabled:cursor-not-allowed"
                        :class="canDelete(u) ? 'text-red-500 hover:underline' : 'text-gray-400'">删除</button>
              </td>
            </tr>
            <tr v-if="!users.length">
              <td colspan="9" class="px-4 py-10 text-center text-gray-400 text-sm">还没有用户</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="dialogVisible" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4" @click.self="dialogVisible = false">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-full max-w-sm">
        <h2 class="text-lg font-bold mb-4">编辑用户 - {{ editUser?.username }}</h2>
        <div class="space-y-3">
          <div>
            <label class="text-xs text-gray-400">昵称</label>
            <input v-model="dialogForm.nickname" aria-label="昵称" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm mt-1" />
          </div>
          <div>
            <label class="text-xs text-gray-400">角色</label>
            <select v-model="dialogForm.role" aria-label="角色" :disabled="isSuperTarget"
                    class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm mt-1 disabled:opacity-60">
              <option value="user">用户</option>
              <option value="author">作者（仅能投稿，需审核）</option>
              <option value="admin">管理员</option>
              <!-- 仅为把超管那一行显示对；后端不允许改 super，所以这个选项选不了 -->
              <option value="super" disabled>超级管理员（仅数据库授予）</option>
            </select>
            <p class="text-[11px] text-gray-400 mt-1">超级管理员全站只有一个，只能在数据库里改，后台不提供授予入口。</p>
          </div>
          <div>
            <label class="text-xs text-gray-400">状态</label>
            <select v-model="dialogForm.status" aria-label="状态" :disabled="isSuperTarget"
                    class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm mt-1 disabled:opacity-60">
              <option :value="1">正常</option>
              <option :value="0">禁用</option>
            </select>
          </div>
          <div>
            <label class="text-xs text-gray-400">新密码（留空不修改）</label>
            <input v-model="dialogForm.password" type="password" autocomplete="new-password" aria-label="新密码" class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm mt-1" />
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-4">
          <button @click="dialogVisible = false" class="px-4 py-2 border rounded text-sm dark:border-gray-600">取消</button>
          <button @click="handleSave" :disabled="saving" class="px-4 py-2 bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900 rounded text-sm hover:opacity-90 disabled:opacity-50 transition">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: ['admin', 'super-admin'], layout: 'admin' })

const { get, put, del } = useApi()
const { toast, confirmDialog } = useFeedback()
const authStore = useAuthStore()
const users = ref<any[]>([])
const dialogVisible = ref(false)
const saving = ref(false)
const deleting = ref<number | null>(null)
const editUser = ref<any>(null)
const dialogForm = reactive({ nickname: '', role: 'user', status: 1, password: '' })

/** 正在编辑的这行是超管本人：角色/状态一律锁死（后端也会拒，这里只是别让人白点） */
const isSuperTarget = computed(() => editUser.value?.role === 'super')

/** 自己不能删自己、超管不能删 —— 和后端那两道防呆对齐，先在前端拦一道避免白跑一趟 */
function canDelete(u: any) {
  if (!u) return false
  if (u.id === authStore.user?.userId) return false
  if (u.role === 'super') return false
  return true
}

function deleteHint(u: any) {
  if (u.id === authStore.user?.userId) return '不能删除自己的账号'
  if (u.role === 'super') return '不能删除超级管理员'
  return ''
}

async function fetchList() {
  const res = await get<any>('/admin/user/list?pageSize=100')
  if (res.code === 200 && res.data) users.value = res.data.records || []
}

function openDialog(u: any) {
  editUser.value = u
  dialogForm.nickname = u.nickname || ''
  dialogForm.role = u.role
  dialogForm.status = u.status
  dialogForm.password = ''
  dialogVisible.value = true
}

/**
 * ⚠️ 业务异常是 HTTP 200 + body.code=400，`useApi` 不会抛错 ——
 * 所以这里必须自己判 `res.code`，否则「权限不够/角色非法」也会提示"保存成功"。
 */
async function handleSave() {
  if (saving.value) return
  saving.value = true
  try {
    const res = await put<any>(`/admin/user/${editUser.value.id}`, dialogForm)
    if (res.code === 200) {
      dialogVisible.value = false
      toast('保存成功', 'success')
      await fetchList()
    } else {
      toast(res.message || '保存失败', 'error')
    }
  } catch (e: any) {
    toast('保存失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    saving.value = false
  }
}

async function handleDelete(u: any) {
  if (!canDelete(u) || deleting.value !== null) return

  // 先问清楚「名下有多少东西」，确认框才能说准数字。
  // 不这么做的话，只能先删一次、看后端报错才知道有 4 篇文章 —— 白跑一趟还吓人。
  let articles = 0
  let comments = 0
  try {
    const refs = await get<any>(`/admin/user/${u.id}/refs`)
    if (refs.code === 200 && refs.data) {
      articles = refs.data.articles || 0
      comments = refs.data.comments || 0
    }
  } catch {
    // 预检失败不阻断：照旧走严格模式，让后端来判
  }

  const hasContent = articles > 0 || comments > 0
  const detail = [
    articles > 0 ? `${articles} 篇文章会转到你名下，继续留在站上` : '',
    comments > 0 ? `${comments} 条评论保留，作者显示为「匿名」` : ''
  ].filter(Boolean).join('；')

  const ok = await confirmDialog(
    hasContent
      ? `用户「${u.username}」名下有内容：${detail}。账号本身会被删除，且不可恢复。确定继续？`
      : `确定删除用户「${u.username}」？删除后不可恢复。`,
    hasContent ? '删除用户（保留内容）' : '删除用户',
    true
  )
  if (!ok) return

  deleting.value = u.id
  try {
    // force 只在这条路径上出现：站长已经在确认框里看过「文章转给我、评论显示匿名」才点的
    const res = await del<any>(`/admin/user/${u.id}${hasContent ? '?force=true' : ''}`)
    if (res.code === 200) {
      toast(hasContent ? `已删除「${u.username}」，内容已保留` : `已删除「${u.username}」`, 'success')
      await fetchList() // 列表重排，序号自动补成 1..N
    } else {
      // 后端会把「不能删自己」这类原因原样带回来，直接展示给站长
      toast(res.message || '删除失败', 'error', 6000)
    }
  } catch (e: any) {
    toast('删除失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    deleting.value = null
  }
}

fetchList()
</script>
