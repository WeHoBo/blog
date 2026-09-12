import { ref } from 'vue'

export interface ToastItem {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
}

export interface ConfirmState {
  visible: boolean
  title: string
  message: string
  /** 危险操作（删除等）：确认按钮显示为红色，给用户「有分量」的视觉提示 */
  danger: boolean
  resolve: ((ok: boolean) => void) | null
}

/**
 * 全局轻量反馈：Toast 提示 + 确认对话框。
 *
 * 只有用户交互（点击）才会触发，因此这里的模块级状态不会参与 SSR 渲染，
 * 也不会在不同请求之间互相污染（服务端渲染时始终为空）。
 */
const toasts = ref<ToastItem[]>([])
let toastSeq = 0

const confirmState = ref<ConfirmState>({
  visible: false,
  title: '确认操作',
  message: '',
  danger: false,
  resolve: null
})

export function useFeedback() {
  function dismissToast(id: number) {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }

  function toast(message: string, type: ToastItem['type'] = 'info', duration = 3000) {
    const id = ++toastSeq
    toasts.value.push({ id, message, type })
    if (typeof window !== 'undefined') {
      window.setTimeout(() => dismissToast(id), duration)
    }
  }

  function resolveConfirm(ok: boolean) {
    const resolver = confirmState.value.resolve
    confirmState.value = { visible: false, title: '确认操作', message: '', danger: false, resolve: null }
    if (resolver) resolver(ok)
  }

  function confirmDialog(message: string, title = '确认操作', danger = false): Promise<boolean> {
    // 同一时间只保留一个确认框：并发调用时先把上一个按「取消」收尾，避免 Promise 永久悬挂
    if (confirmState.value.resolve) {
      resolveConfirm(false)
    }
    return new Promise<boolean>((resolve) => {
      confirmState.value = { visible: true, title, message, danger, resolve }
    })
  }

  return { toasts, dismissToast, toast, confirmState, confirmDialog, resolveConfirm }
}
