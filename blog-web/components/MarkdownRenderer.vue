<template>
  <div class="article-content" v-html="renderedHtml" ref="contentRef"></div>
</template>

<script setup lang="ts">
/**
 * 正文渲染组件。
 *
 * 只负责两件事：拿到 HTML（交给 utils/markdownCore 统一渲染，目录用的是同一套规则），
 * 以及渲染后对 DOM 做增强（mermaid、代码工具条、图片灯箱）。
 * 之前这里还塞着整个 markdown-it 配置与标题 id 生成逻辑，导致目录只能另写一份正则去猜标题，
 * 已经因此出过「代码块注释被当成标题」「同名小节跳错位置」的问题，现已全部收敛到 markdownCore。
 */
import { renderMarkdown, CODE_FOLD_THRESHOLD } from '~/utils/markdownCore'
// 正文图片点击放大。手写实现放独立模块，避免这里继续膨胀（见 utils/imageLightbox.ts 顶部注释）
import { bindImageLightbox } from '~/utils/imageLightbox'

const props = defineProps<{ content: string }>()

// mermaid 体积大（含上百个子模块，~3MB），放在 public/vendor 运行时按需加载，
// 不参与打包 —— 避免其所有子模块被 Nuxt prefetch 到每个页面
let mermaid: any = null
const needMermaid = (s: string) => /```mermaid\b/.test(s || '')

async function ensureMermaid() {
  if (!mermaid) {
    mermaid = (await import(/* @vite-ignore */ '/vendor/mermaid/mermaid.esm.min.mjs')).default
    mermaid.initialize({ startOnLoad: false, theme: 'default' })
  }
}

const renderedHtml = computed(() => renderMarkdown(props.content || ''))
const contentRef = ref<HTMLElement | null>(null)

/**
 * 所有「依赖渲染后 DOM」的增强统一在这里做：
 *   - mermaid 渲染
 *   - 代码块工具条（复制 / 换行 / 展开折叠）
 *   - mermaid 图点击放大
 *   - 正文图片点击放大（灯箱）
 * <p>
 * 以前这些逻辑写在 onMounted 里、只在组件挂载时跑一次；而正文可能：
 *   - 在编辑器里实时变化（v-html 被反复重写）
 *   - 在文章页做客户端路由跳转（组件不重新挂载，只换 props）
 * 两种情况都会让这些增强在旧的 DOM 上失效。改成 watch + nextTick 之后，
 * 每次内容真正变化都会重新挂一遍增强，实时预览里的 mermaid 和复制按钮才能正常工作。
 */
async function enhance(el: HTMLElement) {
  // Mermaid rendering（按需加载）
  const mermaidEls = el.querySelectorAll('.mermaid')
  if (mermaidEls.length > 0 || needMermaid(props.content)) {
    await ensureMermaid()
    const nodes = el.querySelectorAll('.mermaid')
    if (nodes.length > 0) {
      try {
        await mermaid.run({ nodes: Array.from(nodes) })
      } catch {}
    }
  }

  // 代码块工具条：复制 / 换行开关 / 展开折叠
  // 先清旧的再挂，避免重复绑定（内容重渲染会整段替换 DOM，但编辑器实时预览可能连续触发 enhance）
  el.querySelectorAll('.code-actions').forEach((b) => b.remove())
  el.querySelectorAll('.code-wrapper').forEach((wrapper) => {
    const node = wrapper as HTMLElement
    const pre = node.querySelector('pre.hljs')
    if (!pre) return
    node.style.position = 'relative'

    const actions = document.createElement('div')
    actions.className = 'code-actions'

    // 复制：按「行」重建原文并补回 \n。
    // v-html 渲染出的 <code> 里每行是独立的 block 级 <span class="line">，
    // 行与行之间没有任何换行符，直接取 textContent 会把整段代码挤成一行。
    const copyBtn = document.createElement('button')
    copyBtn.type = 'button'
    copyBtn.className = 'code-action-btn'
    copyBtn.textContent = '复制'
    copyBtn.setAttribute('aria-label', '复制代码')
    copyBtn.onclick = () => {
      const codeEl = pre.querySelector('code')
      const lineEls = codeEl ? Array.from(codeEl.querySelectorAll<HTMLElement>('.line')) : []
      const code = lineEls.length
        ? lineEls.map((l) => l.textContent || '').join('\n')
        : (codeEl?.textContent || '')
      const done = () => { copyBtn.textContent = '✓'; setTimeout(() => copyBtn.textContent = '复制', 2000) }
      if (navigator.clipboard) navigator.clipboard.writeText(code).then(done)
      else { const t = document.createElement('textarea'); t.value = code; document.body.appendChild(t); t.select(); document.execCommand('copy'); document.body.removeChild(t); done() }
    }

    // 行号开关：默认关闭。短代码块里一排数字只是噪声，需要按行讨论（「看第 12 行」）时再打开。
    // 数字由 CSS 的 counter 生成，这里只切 class，不重建 DOM。
    const lineBtn = document.createElement('button')
    lineBtn.type = 'button'
    lineBtn.className = 'code-action-btn'
    lineBtn.textContent = '行号'
    lineBtn.setAttribute('aria-label', '切换行号显示')
    lineBtn.setAttribute('aria-pressed', 'false')
    lineBtn.onclick = () => {
      const on = node.classList.toggle('show-line-no')
      lineBtn.classList.toggle('is-active', on)
      lineBtn.setAttribute('aria-pressed', String(on))
    }

    // 换行开关：长行代码默认横向滚动，窄屏读者可切到自动换行
    const wrapBtn = document.createElement('button')
    wrapBtn.type = 'button'
    wrapBtn.className = 'code-action-btn'
    wrapBtn.textContent = '换行'
    wrapBtn.setAttribute('aria-label', '切换自动换行')
    wrapBtn.setAttribute('aria-pressed', 'false')
    wrapBtn.onclick = () => {
      const on = node.classList.toggle('code-wrap')
      wrapBtn.classList.toggle('is-active', on)
      wrapBtn.setAttribute('aria-pressed', String(on))
    }

    actions.appendChild(copyBtn)
    actions.appendChild(lineBtn)
    actions.appendChild(wrapBtn)

    // 只在超长代码块上出现（短块没必要多一个按钮）
    if (Number(node.dataset.lines || 0) > CODE_FOLD_THRESHOLD) {
      const foldBtn = document.createElement('button')
      foldBtn.type = 'button'
      foldBtn.className = 'code-action-btn'
      foldBtn.textContent = node.classList.contains('code-collapsed') ? '展开' : '收起'
      foldBtn.setAttribute('aria-label', '展开或收起代码块')
      foldBtn.onclick = () => {
        const collapsed = node.classList.toggle('code-collapsed')
        foldBtn.textContent = collapsed ? '展开' : '收起'
      }
      actions.appendChild(foldBtn)
    }

    node.appendChild(actions)
  })

  // 代码组 Tab（```java tab="后端" 连续两段以上）：面板显隐交给 CSS，这里只切 is-active。
  // dataset 标记防止 enhance 重入时重复绑定（重复绑不是致命错，但会一次点击切两次、看起来"没反应"）。
  el.querySelectorAll('.code-group').forEach((group) => {
    const node = group as HTMLElement
    if (node.dataset.tabBound === '1') return
    node.dataset.tabBound = '1'
    const tabs = Array.from(node.querySelectorAll<HTMLElement>('.code-group-tab'))
    const panels = Array.from(node.querySelectorAll<HTMLElement>('.code-group-panel'))
    tabs.forEach((tab, i) => {
      tab.addEventListener('click', () => {
        tabs.forEach((t, k) => {
          const active = k === i
          t.classList.toggle('is-active', active)
          t.setAttribute('aria-selected', String(active))
        })
        panels.forEach((p, k) => p.classList.toggle('is-active', k === i))
      })
    })
  })

  // Mermaid click to zoom
  el.querySelectorAll('.mermaid-wrapper').forEach(wrapper => {
    if ((wrapper as HTMLElement).dataset.zoomBound === '1') return
    ;(wrapper as HTMLElement).dataset.zoomBound = '1'
    wrapper.addEventListener('click', async () => {
      if (!mermaid) {
        try { await ensureMermaid() } catch { return }
      }
      const div = document.createElement('div')
      div.className = 'fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-8'
      div.innerHTML = '<div class="bg-white dark:bg-gray-900 rounded-xl p-6 max-w-4xl max-h-full overflow-auto"><button aria-label="关闭" class="absolute top-4 right-4 text-2xl text-gray-500 hover:text-gray-800 dark:hover:text-white">&times;</button></div>'
      const clone = (wrapper.querySelector('.mermaid') as HTMLElement).cloneNode(true) as HTMLElement
      clone.style.transform = 'scale(1.2)'
      const box = div.querySelector('div')!
      box.appendChild(clone)
      box.querySelector('button')!.onclick = () => div.remove()
      div.onclick = (e) => { if (e.target === div) div.remove() }
      document.body.appendChild(div)
      try { mermaid.run({ nodes: [clone.querySelector('svg')].filter(Boolean) }) } catch {}
    })
    ;(wrapper as HTMLElement).style.cursor = 'pointer'
    wrapper.setAttribute('title', '点击放大')
  })

  // 正文图片点击放大（灯箱）：与上面的 mermaid 放大对称，图片终于也能点开了
  bindImageLightbox(el)
}

onMounted(async () => {
  const el = contentRef.value
  if (el) await enhance(el)
})

// 内容变化时重新挂增强：flush:'post' 保证此刻 v-html 已经更新到 DOM
watch(() => props.content, async () => {
  await nextTick()
  const el = contentRef.value
  if (el) await enhance(el)
})
</script>
