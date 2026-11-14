<template>
  <div class="space-y-6">
    <!-- 标题 -->
    <div class="flex items-center justify-between gap-3 flex-wrap">
      <div>
        <h1 class="text-2xl font-bold">AI 模型</h1>
        <p class="text-xs text-gray-400 mt-1">
          这里改完最迟 5 秒对全体用户生效，无需重启服务。
        </p>
      </div>
      <div class="flex items-center gap-2">
        <NuxtLink to="/admin/ai/usage" class="px-3 py-2 text-sm rounded-md border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:border-primary-400 hover:text-primary-600 transition">
          用量分析
        </NuxtLink>
        <button class="px-3 py-2 text-sm rounded-md bg-ink text-paper hover:opacity-90 transition" @click="openCreate">
          新增模型
        </button>
      </div>
    </div>

    <!-- 当前生效 -->
    <div class="grid gap-4 sm:grid-cols-3">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-400">默认模型</span>
          <span class="text-[10px] px-1.5 py-0.5 rounded bg-primary-50 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300">全站生效</span>
        </div>
        <div class="mt-2 font-medium truncate">{{ defaultModel?.displayName || '未设置' }}</div>
        <div class="text-xs text-gray-400 font-mono truncate">{{ defaultModel?.modelId || '将按排序取第一个启用的模型' }}</div>
      </div>

      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-400">降级模型</span>
          <span v-if="fallbackModel" class="text-[10px] px-1.5 py-0.5 rounded bg-primary-50 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300">已启用</span>
          <span v-else class="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400">未配置</span>
        </div>
        <div class="mt-2 font-medium truncate">{{ fallbackModel?.displayName || '未设置' }}</div>
        <div class="text-xs text-gray-400 leading-relaxed">
          {{ fallbackModel
            ? '主模型 429/超时/5xx 时自动改用它重试一次，并在回答下方告知用户'
            : '未配置：主模型失败时直接报错。建议选一家不同厂商的模型' }}
        </div>
      </div>

      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
        <div class="flex items-center justify-between">
          <span class="text-xs text-gray-400">用户自选</span>
          <span class="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400">待接入</span>
        </div>
        <div class="mt-2 font-medium">{{ setting?.allowUserChoice === 1 ? '已开启' : '已关闭' }}</div>
        <div class="text-xs text-gray-400 leading-relaxed">前台还没有选模型的入口，这个开关暂时不产生效果</div>
      </div>
    </div>

    <!-- 列表 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[920px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="w-10 px-2 py-3"></th>
              <th class="px-3 py-3 text-left">显示名</th>
              <th class="px-3 py-3 text-left">厂商 / 模型 ID</th>
              <th class="px-3 py-3 text-left">今日消耗</th>
              <th class="px-3 py-3 text-left">状态</th>
              <th class="px-3 py-3 text-left w-44">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="px-4 py-10 text-center text-gray-400 text-sm">加载中…</td>
            </tr>
            <tr v-else-if="!models.length">
              <td colspan="6" class="px-4 py-10 text-center text-gray-400 text-sm">
                还没有模型。点右上角「新增模型」，或先执行 <code class="font-mono">sql/ai-model.sql</code> 导入默认的 DeepSeek 配置。
              </td>
            </tr>
            <template v-else>
            <tr
              v-for="(m, i) in models"
              :key="m.id"
              class="border-t dark:border-gray-700 transition"
              :class="[
                dragOver === i && dragFrom !== i ? 'bg-primary-50 dark:bg-gray-700/60' : 'hover:bg-gray-50 dark:hover:bg-gray-700/50',
                dragFrom === i ? 'opacity-40' : ''
              ]"
              draggable="true"
              @dragstart="onDragStart(i, $event)"
              @dragover.prevent="dragOver = i"
              @drop.prevent="onDrop(i)"
              @dragend="onDragEnd"
            >
              <!-- 拖拽手柄：只有从手柄按下才能拖，否则选中文字也会误触发 -->
              <td class="px-2 py-3 text-gray-300 dark:text-gray-600">
                <span class="inline-flex cursor-move select-none" title="拖动调整顺序" @mousedown="armed = true">
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor" aria-hidden="true">
                    <circle cx="4" cy="3" r="1.2" /><circle cx="10" cy="3" r="1.2" />
                    <circle cx="4" cy="7" r="1.2" /><circle cx="10" cy="7" r="1.2" />
                    <circle cx="4" cy="11" r="1.2" /><circle cx="10" cy="11" r="1.2" />
                  </svg>
                </span>
              </td>
              <td class="px-3 py-3">
                <div class="flex items-center gap-1.5 flex-wrap">
                  <span class="font-medium">{{ m.displayName }}</span>
                  <span v-if="m.isDefault" class="text-[10px] px-1.5 py-0.5 rounded bg-primary-50 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300">默认</span>
                  <span v-if="m.isFallback" class="text-[10px] px-1.5 py-0.5 rounded border border-gray-300 dark:border-gray-600 text-gray-500">降级</span>
                </div>
                <div class="flex items-center gap-1 mt-1">
                  <span v-for="cap in caps(m)" :key="cap.label" class="text-[10px] px-1.5 py-0.5 rounded"
                        :class="cap.on ? 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300' : 'text-gray-300 dark:text-gray-600 line-through'">
                    {{ cap.label }}
                  </span>
                </div>
              </td>
              <td class="px-3 py-3">
                <div class="text-gray-600 dark:text-gray-300">{{ m.provider }}</div>
                <div class="text-xs text-gray-400 font-mono truncate max-w-[220px]" :title="m.modelId">{{ m.modelId }}</div>
              </td>
              <td class="px-3 py-3 whitespace-nowrap">
                <div class="text-gray-600 dark:text-gray-300">{{ m.todayRequests }} 次</div>
                <div class="text-xs" :class="costClass(m)">¥{{ money(m.todayCost) }}</div>
              </td>
              <td class="px-3 py-3">
                <button
                  role="switch"
                  :aria-checked="m.enabled === 1"
                  :aria-label="`启用 ${m.displayName}`"
                  :disabled="m.isDefault && m.enabled === 1"
                  class="relative inline-flex h-5 w-9 items-center rounded-full transition disabled:cursor-not-allowed disabled:opacity-50"
                  :class="m.enabled === 1 ? 'bg-primary-500' : 'bg-gray-300 dark:bg-gray-600'"
                  :title="m.isDefault && m.enabled === 1 ? '默认模型不能直接停用，请先切换默认模型' : ''"
                  @click="toggleEnabled(m)"
                >
                  <span class="inline-block h-3.5 w-3.5 rounded-full bg-white transition" :class="m.enabled === 1 ? 'translate-x-[18px]' : 'translate-x-[3px]'"></span>
                </button>
              </td>
              <td class="px-3 py-3 whitespace-nowrap">
                <button class="text-primary-600 hover:underline text-xs" @click="openEdit(m)">编辑</button>
                <span class="text-gray-300 dark:text-gray-600 mx-1.5">·</span>
                <button class="text-primary-600 hover:underline text-xs disabled:opacity-40" :disabled="testingId === m.id" @click="test(m)">
                  {{ testingId === m.id ? '测试中…' : '测试' }}
                </button>
                <span class="text-gray-300 dark:text-gray-600 mx-1.5">·</span>
                <button class="text-gray-500 hover:text-primary-600 hover:underline text-xs" :disabled="m.isDefault" @click="setDefault(m)">设为默认</button>
                <span class="text-gray-300 dark:text-gray-600 mx-1.5">·</span>
                <!-- 降级模型（P4）：再点一次取消。默认模型不能兼任，后端也会拒，这里直接禁掉更省事 -->
                <button class="text-gray-500 hover:text-primary-600 hover:underline text-xs disabled:opacity-40"
                  :disabled="m.isDefault"
                  :title="m.isDefault ? '默认模型不能同时作为降级模型' : ''"
                  @click="setFallback(m)">{{ m.isFallback ? '取消降级' : '设为降级' }}</button>
                <span class="text-gray-300 dark:text-gray-600 mx-1.5">·</span>
                <button class="text-gray-400 hover:text-primary-600 hover:underline text-xs" @click="remove(m)">删除</button>
              </td>
            </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <p class="text-xs text-gray-400 leading-relaxed">
      顺序即用户看到的先后（默认模型失效时按此顺序取第一个启用的模型兜底）。
      单价单位：元 / 百万 token；填 0 表示不计成本。
    </p>

    <!-- 编辑抽屉 -->
    <div v-if="drawer" class="fixed inset-0 z-50 flex justify-end bg-black/40" @click.self="closeDrawer">
      <div class="w-full max-w-md h-full bg-white dark:bg-gray-800 shadow-xl overflow-y-auto">
        <div class="sticky top-0 z-10 flex items-center justify-between px-5 py-3 bg-white dark:bg-gray-800 border-b dark:border-gray-700">
          <h2 class="font-bold">{{ form.id ? '编辑模型' : '新增模型' }}</h2>
          <button class="text-gray-400 hover:text-primary-600 transition" aria-label="关闭" @click="closeDrawer">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div class="px-5 py-4 space-y-6">
          <!-- ① 基本信息 -->
          <section class="space-y-3">
            <h3 class="text-xs font-medium text-gray-400 tracking-wide">基本信息</h3>
            <div>
              <label for="f-display" class="text-xs text-gray-500 dark:text-gray-400">显示名</label>
              <input id="f-display" v-model="form.displayName" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" placeholder="DeepSeek 对话" />
            </div>
            <div>
              <label for="f-provider" class="text-xs text-gray-500 dark:text-gray-400">厂商</label>
              <input id="f-provider" v-model="form.provider" list="ai-providers" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" placeholder="deepseek" />
              <datalist id="ai-providers">
                <option v-for="p in PRESETS" :key="p.provider" :value="p.provider" />
              </datalist>
              <div class="flex flex-wrap gap-1.5 mt-2">
                <button v-for="p in PRESETS" :key="p.provider" type="button"
                        class="text-[11px] px-2 py-0.5 rounded border border-gray-200 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:border-primary-400 hover:text-primary-600 transition"
                        @click="applyPreset(p)">
                  {{ p.provider }}
                </button>
              </div>
            </div>
            <div>
              <label for="f-model-id" class="text-xs text-gray-500 dark:text-gray-400">模型 ID</label>
              <input id="f-model-id" v-model="form.modelId" class="w-full mt-1 px-3 py-2 border rounded text-sm font-mono dark:bg-gray-700 dark:border-gray-600" placeholder="deepseek-chat" />
            </div>
            <div>
              <label for="f-base-url" class="text-xs text-gray-500 dark:text-gray-400">base_url</label>
              <input id="f-base-url" v-model="form.baseUrl" class="w-full mt-1 px-3 py-2 border rounded text-sm font-mono dark:bg-gray-700 dark:border-gray-600" placeholder="https://api.deepseek.com/v1" />
              <p class="text-[11px] text-gray-400 mt-1">OpenAI 兼容端点，末尾不用带 /chat/completions</p>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label for="f-sort" class="text-xs text-gray-500 dark:text-gray-400">排序</label>
                <input id="f-sort" v-model.number="form.sort" type="number" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
              </div>
              <div class="flex items-end">
                <label class="flex items-center gap-2 text-sm cursor-pointer">
                  <input v-model="form.enabled" type="checkbox" :true-value="1" :false-value="0" class="rounded" />
                  <span>启用</span>
                </label>
              </div>
            </div>
          </section>

          <!-- ② 密钥 -->
          <section class="space-y-3">
            <h3 class="text-xs font-medium text-gray-400 tracking-wide">密钥</h3>
            <div>
              <label for="f-key" class="text-xs text-gray-500 dark:text-gray-400">API Key</label>
              <input id="f-key" v-model="form.apiKey" type="password" autocomplete="new-password"
                     class="w-full mt-1 px-3 py-2 border rounded text-sm font-mono dark:bg-gray-700 dark:border-gray-600"
                     :placeholder="keyPlaceholder" />
              <p class="text-[11px] text-gray-400 mt-1 leading-relaxed">
                留空保存 = <strong>不修改</strong>已保存的密钥（出于安全，编辑时不会回显明文）。
              </p>
              <label v-if="form.id && (form.apiKeyMask || form.hasKey)" class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 mt-2 cursor-pointer">
                <input v-model="form.clearApiKey" type="checkbox" class="rounded" />
                <span>清除已保存的密钥</span>
              </label>
            </div>
            <div>
              <label for="f-key-env" class="text-xs text-gray-500 dark:text-gray-400">或：密钥来自环境变量（填变量名）</label>
              <input id="f-key-env" v-model="form.apiKeyEnv" class="w-full mt-1 px-3 py-2 border rounded text-sm font-mono dark:bg-gray-700 dark:border-gray-600" placeholder="DEEPSEEK_API_KEY" />
              <p class="text-[11px] text-gray-400 mt-1 leading-relaxed">
                变量从 <strong>RAG 服务</strong>的 .env 读取。填了 API Key 这里就会被清空，两种模式只能用一个。
              </p>
            </div>
          </section>

          <!-- ③ 参数与能力 -->
          <section class="space-y-3">
            <h3 class="text-xs font-medium text-gray-400 tracking-wide">参数与能力</h3>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label for="f-temp" class="text-xs text-gray-500 dark:text-gray-400">temperature</label>
                <input id="f-temp" v-model.number="form.temperature" type="number" step="0.05" min="0" max="2" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
              </div>
              <div>
                <label for="f-max-tokens" class="text-xs text-gray-500 dark:text-gray-400">max_tokens</label>
                <input id="f-max-tokens" v-model.number="form.maxTokens" type="number" min="1" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
              </div>
              <div>
                <label for="f-ctx" class="text-xs text-gray-500 dark:text-gray-400">上下文长度</label>
                <input id="f-ctx" v-model.number="form.contextWindow" type="number" min="1000" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
              </div>
              <div>
                <label for="f-price-in" class="text-xs text-gray-500 dark:text-gray-400">输入单价 / 百万 token</label>
                <input id="f-price-in" v-model.number="form.inputPrice" type="number" step="0.01" min="0" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
              </div>
              <div>
                <label for="f-price-out" class="text-xs text-gray-500 dark:text-gray-400">输出单价 / 百万 token</label>
                <input id="f-price-out" v-model.number="form.outputPrice" type="number" step="0.01" min="0" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
              </div>
            </div>
            <div class="space-y-2 pt-1">
              <label class="flex items-center gap-2 text-sm cursor-pointer">
                <input v-model="form.supportStream" type="checkbox" :true-value="1" :false-value="0" class="rounded" />
                <span>支持流式输出</span>
              </label>
              <label class="flex items-start gap-2 text-sm cursor-pointer">
                <input v-model="form.supportTools" type="checkbox" :true-value="1" :false-value="0" class="rounded mt-0.5" />
                <span>
                  支持工具调用（function calling）
                  <span class="block text-[11px] text-gray-400">关掉后这个模型不再检索站内文章，只会凭自身知识回答</span>
                </span>
              </label>
            </div>
            <div>
              <label for="f-remark" class="text-xs text-gray-500 dark:text-gray-400">备注</label>
              <input id="f-remark" v-model="form.remark" class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" placeholder="选填" />
            </div>
          </section>
        </div>

        <div class="sticky bottom-0 flex justify-end gap-2 px-5 py-3 bg-white dark:bg-gray-800 border-t dark:border-gray-700">
          <button class="px-4 py-2 border rounded text-sm dark:border-gray-600" @click="closeDrawer">取消</button>
          <button class="px-4 py-2 rounded text-sm bg-ink text-paper hover:opacity-90 disabled:opacity-50 transition" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: ['admin', 'super-admin'], layout: 'admin' })

interface ModelRow {
  id: number
  displayName: string
  provider: string
  modelId: string
  baseUrl: string
  apiKeyMask?: string | null
  apiKeyEnv?: string | null
  hasKey?: boolean
  temperature: number
  maxTokens: number
  contextWindow: number
  supportStream: number
  supportTools: number
  inputPrice: number
  outputPrice: number
  enabled: number
  sort: number
  todayRequests: number
  todayCost: number
  isDefault: boolean
  isFallback: boolean
}

interface Setting {
  defaultModelId?: number | null
  fallbackModelId?: number | null
  allowUserChoice?: number
  dailyLimit?: number
  globalDailyBudget?: number
}

const PRESETS = [
  { provider: 'deepseek', baseUrl: 'https://api.deepseek.com/v1', modelId: 'deepseek-chat' },
  { provider: 'qwen', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelId: 'qwen-plus' },
  { provider: 'zhipu', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', modelId: 'glm-4-flash' },
  { provider: 'moonshot', baseUrl: 'https://api.moonshot.cn/v1', modelId: 'moonshot-v1-8k' },
  { provider: 'siliconflow', baseUrl: 'https://api.siliconflow.cn/v1', modelId: 'Qwen/Qwen2.5-7B-Instruct' },
  { provider: 'openrouter', baseUrl: 'https://openrouter.ai/api/v1', modelId: 'openai/gpt-4o-mini' }
]

const { get, post, put, del } = useApi()
const { toast, confirmDialog } = useFeedback()

const models = ref<ModelRow[]>([])
const setting = ref<Setting | null>(null)
const loading = ref(true)
const testingId = ref<number | null>(null)

const defaultModel = computed(() => models.value.find(m => m.isDefault))
const fallbackModel = computed(() => models.value.find(m => m.isFallback))

// ---------------- 数据 ----------------

async function load() {
  loading.value = true
  try {
    const [list, s] = await Promise.all([
      get<any>('/admin/ai/models'),
      get<any>('/admin/ai/setting')
    ])
    if (list.code === 200) models.value = list.data || []
    if (s.code === 200) setting.value = s.data
  } catch (e: any) {
    toast('加载失败：' + (e.message || '未知错误'), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------------- 展示辅助 ----------------

/** 金额很小（一次问答几分钱），固定两位会全变成 0.00，所以保留四位 */
function money(v: any) {
  const n = Number(v || 0)
  return n.toFixed(4)
}

function costClass(m: ModelRow) {
  const budget = Number(setting.value?.globalDailyBudget || 0)
  // 日预算没配就是 0，此时不做任何高亮（否则每行都是黄的）
  return budget > 0 && Number(m.todayCost) > budget * 0.3
    ? 'text-primary-700 dark:text-primary-300 font-medium'
    : 'text-gray-400'
}

function caps(m: ModelRow) {
  return [
    { label: '流式', on: m.supportStream === 1 },
    { label: '工具', on: m.supportTools === 1 },
    { label: '长上下文', on: Number(m.contextWindow) >= 128000 }
  ]
}

// ---------------- 行操作 ----------------

async function toggleEnabled(m: ModelRow) {
  const next = m.enabled === 1 ? 0 : 1
  const res = await put<any>(`/admin/ai/models/${m.id}`, { enabled: next })
  if (res.code !== 200) { toast(res.message || '操作失败', 'error'); return }
  m.enabled = next
  toast(next === 1 ? '已启用' : '已停用', 'success')
  // 停用的可能正是生效中的默认模型，后端会自动回退到别的模型，刷新一次看真实状态
  load()
}

async function setDefault(m: ModelRow) {
  const res = await post<any>(`/admin/ai/models/${m.id}/default`)
  if (res.code !== 200) { toast(res.message || '设置失败', 'error'); return }
  toast(`默认模型已切换为「${m.displayName}」，5 秒内全站生效`, 'success')
  load()
}

/**
 * 设为 / 取消降级模型（P4）。
 *
 * 走 PUT /setting 而不是加专门的接口：Other 字段都是包装类型，只传 fallbackModelId
 * 不会把其他阈值冲成 null（后端是"null 表示不改"）。传 0 是后端的清除约定。
 */
async function setFallback(m: ModelRow) {
  const clearing = m.isFallback
  const res = await put<any>('/admin/ai/setting', {
    fallbackModelId: clearing ? 0 : m.id
  })
  if (res.code !== 200) { toast(res.message || '设置失败', 'error'); return }
  toast(clearing
    ? '已取消降级模型：主模型失败时将直接报错'
    : `降级模型已设为「${m.displayName}」，5 秒内生效`, 'success')
  load()
}

async function test(m: ModelRow) {
  testingId.value = m.id
  try {
    const res = await post<any>(`/admin/ai/models/${m.id}/test`)
    if (res.code !== 200) { toast(res.message || '测试失败', 'error'); return }
    const d = res.data || {}
    toast(d.message || (d.ok ? '连接正常' : '连接失败'), d.ok ? 'success' : 'error', 6000)
  } catch (e: any) {
    toast('测试失败：' + (e.message || '未知错误'), 'error')
  } finally {
    testingId.value = null
  }
}

async function remove(m: ModelRow) {
  const ok = await confirmDialog(
    `删除后该模型不再可用，历史用量仍会保留。确定删除「${m.displayName}」？`,
    '删除模型', true
  )
  if (!ok) return
  const res = await del<any>(`/admin/ai/models/${m.id}`)
  if (res.code !== 200) { toast(res.message || '删除失败', 'error'); return }
  toast('已删除', 'success')
  load()
}

// ---------------- 拖拽排序 ----------------

// HTML5 拖拽必须挂在行上，但只有按住手柄才算数 —— 否则选中文字、点按钮都会触发拖动
const armed = ref(false)
const dragFrom = ref(-1)
const dragOver = ref(-1)

function onDragStart(i: number, e: DragEvent) {
  if (!armed.value) { e.preventDefault(); return }
  dragFrom.value = i
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(i))
  }
}

async function onDrop(to: number) {
  const from = dragFrom.value
  resetDrag()
  if (from < 0 || from === to) return

  const arr = [...models.value]
  const [moved] = arr.splice(from, 1)
  if (!moved) return
  arr.splice(to, 0, moved)
  models.value = arr

  // 重排后统一按 10 递增，只提交真正变化的那几条（模型通常就几个，逐条 PUT 足够）
  const changed = arr
    .map((m, i) => ({ m, sort: (i + 1) * 10 }))
    .filter(x => Number(x.m.sort) !== x.sort)

  if (!changed.length) return
  try {
    const results = await Promise.all(
      changed.map(x => put<any>(`/admin/ai/models/${x.m.id}`, { sort: x.sort }))
    )
    const bad = results.find(r => r.code !== 200)
    if (bad) toast(bad.message || '排序保存失败', 'error')
    else toast('顺序已保存', 'success', 1500)
  } catch (e: any) {
    toast('排序保存失败：' + (e.message || '未知错误'), 'error')
  }
  load()
}

function onDragEnd() { resetDrag() }

function resetDrag() {
  armed.value = false
  dragFrom.value = -1
  dragOver.value = -1
}

// 手柄按下后如果只是普通点击（没拖起来），dragend 不会触发，
// armed 就会一直为 true，下次在行内选文字也会误触发拖动 —— 靠全局 mouseup 兜底
function onGlobalMouseUp() { armed.value = false }

onMounted(() => window.addEventListener('mouseup', onGlobalMouseUp))
onBeforeUnmount(() => window.removeEventListener('mouseup', onGlobalMouseUp))

// ---------------- 抽屉表单 ----------------

const drawer = ref(false)
const saving = ref(false)

interface FormState {
  id?: number
  displayName: string
  provider: string
  modelId: string
  baseUrl: string
  apiKey: string
  apiKeyMask: string
  apiKeyEnv: string
  hasKey: boolean
  clearApiKey: boolean
  temperature: number
  maxTokens: number
  contextWindow: number
  supportStream: number
  supportTools: number
  inputPrice: number
  outputPrice: number
  enabled: number
  sort: number
  remark: string
}

function blank(): FormState {
  return {
    displayName: '', provider: '', modelId: '', baseUrl: '',
    apiKey: '', apiKeyMask: '', apiKeyEnv: '', hasKey: false, clearApiKey: false,
    temperature: 0.3, maxTokens: 4096, contextWindow: 64000,
    supportStream: 1, supportTools: 1, inputPrice: 0, outputPrice: 0,
    enabled: 1, sort: (models.value.length + 1) * 10, remark: ''
  }
}

const form = ref<FormState>(blank())

const keyPlaceholder = computed(() => {
  if (!form.value.id) return 'sk-...'
  if (form.value.apiKeyMask) return form.value.apiKeyMask
  if (form.value.apiKeyEnv) return `由环境变量 ${form.value.apiKeyEnv} 提供`
  return '尚未配置'
})

function openCreate() {
  form.value = blank()
  drawer.value = true
}

function openEdit(m: ModelRow) {
  form.value = {
    id: m.id,
    displayName: m.displayName || '',
    provider: m.provider || '',
    modelId: m.modelId || '',
    baseUrl: m.baseUrl || '',
    apiKey: '',
    apiKeyMask: m.apiKeyMask || '',
    apiKeyEnv: m.apiKeyEnv || '',
    hasKey: !!m.hasKey,
    clearApiKey: false,
    temperature: Number(m.temperature ?? 0.3),
    maxTokens: Number(m.maxTokens ?? 4096),
    contextWindow: Number(m.contextWindow ?? 64000),
    supportStream: m.supportStream === 1 ? 1 : 0,
    supportTools: m.supportTools === 1 ? 1 : 0,
    inputPrice: Number(m.inputPrice ?? 0),
    outputPrice: Number(m.outputPrice ?? 0),
    enabled: m.enabled === 1 ? 1 : 0,
    sort: Number(m.sort ?? 0),
    remark: (m as any).remark || ''
  }
  drawer.value = true
}

function closeDrawer() { drawer.value = false }

function applyPreset(p: typeof PRESETS[number]) {
  form.value.provider = p.provider
  form.value.baseUrl = p.baseUrl
  if (!form.value.modelId) form.value.modelId = p.modelId
}

async function save() {
  if (saving.value) return
  const f = form.value
  if (!f.displayName.trim()) { toast('请填写显示名', 'error'); return }
  if (!f.provider.trim()) { toast('请填写厂商', 'error'); return }
  if (!f.modelId.trim()) { toast('请填写模型 ID', 'error'); return }
  if (!f.baseUrl.trim()) { toast('请填写 base_url', 'error'); return }

  const body: Record<string, any> = {
    displayName: f.displayName.trim(),
    provider: f.provider.trim(),
    modelId: f.modelId.trim(),
    baseUrl: f.baseUrl.trim(),
    apiKeyEnv: f.apiKeyEnv.trim(),
    temperature: f.temperature,
    maxTokens: f.maxTokens,
    contextWindow: f.contextWindow,
    supportStream: f.supportStream,
    supportTools: f.supportTools,
    inputPrice: f.inputPrice,
    outputPrice: f.outputPrice,
    enabled: f.enabled,
    sort: f.sort,
    remark: f.remark
  }
  // 留空 = 不改密钥，所以只有真填了才带上这个字段
  if (f.apiKey.trim()) body.apiKey = f.apiKey.trim()
  if (f.clearApiKey) body.clearApiKey = true

  saving.value = true
  try {
    const res = f.id
      ? await put<any>(`/admin/ai/models/${f.id}`, body)
      : await post<any>('/admin/ai/models', body)
    if (res.code !== 200) { toast(res.message || '保存失败', 'error'); return }
    toast('已保存，5 秒内生效', 'success')
    drawer.value = false
    load()
  } catch (e: any) {
    toast('保存失败：' + (e.message || '未知错误'), 'error')
  } finally {
    saving.value = false
  }
}

useHead({ title: 'AI 模型 - 后台' })
</script>
