/**
 * PDF → Markdown（前端解析，不经过后端）
 *
 * 为什么放前端：
 *   1. 后端解析要动 Java 服务、重新打 jar、再部署一次，改造成本和风险都翻倍；
 *   2. PDF 往往带版权/隐私内容，不上传原文件更稳妥（只有提取出来的图片会进 OSS）；
 *   3. 解析失败时用户能立刻看到原因，不用去翻服务器日志。
 *
 * 文字重组策略（刻意保守）：
 *   PDF 里的文字是按「排版指令」存的，没有段落概念 —— 同一个段落通常是很多个独立文本项，
 *   靠 `hasEOL` 才能判断换行。所以这里**一行对一行**输出，不猜段落。
 *   好处是不会把两段话粘成一段、也不会把表格搅乱；配合站点 `breaks: true` 的渲染设置，
 *   导入后的观感与原文接近，作者再手动删几个空行即可。
 *   唯一的智能判断是「字号明显大于正文的行」→ 当作 `##` 小标题（技术文档里很常见）。
 *
 * 图片提取：
 *   走 `page.getOperatorList()`，收集 `paintImageXObject` / `paintJpegXObject` 的 objId，
 *   再从 `page.objs` 取像素数据画到 canvas 导出 PNG。会跳过：
 *   - 边长小于 96px 的图（大多是项目符号、图标、装饰线）；
 *   - 同一页里重复出现的 objId（水印、页眉 logo）；
 *   - `paintImageMaskXObject` 蒙版（不是独立图像，导出来是黑块）。
 *   如果整篇没有文字层（扫描件），图片仍会全部导出 —— 相当于「按页导成图片」，并且会带回警告。
 */

import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

export interface PdfProgress {
  stage: 'loading' | 'text' | 'images' | 'upload' | 'done'
  /** 当前页（从 1 开始）；非分页阶段为 0 */
  page: number
  totalPages: number
  detail?: string
}

export interface PdfImportResult {
  markdown: string
  pages: number
  /** 成功导出并上传的图片数 */
  images: number
  /** 因为太小/重复/超上限被跳过的图片数 */
  skippedImages: number
  /** 提取到的正文字符数（不含空白） */
  chars: number
  warnings: string[]
}

export interface PdfImportOptions {
  /**
   * 上传一张图片并返回可访问 URL。
   * 之所以做成注入而不是这里直接调接口：这个模块要保持可单测（不依赖 Nuxt 上下文与登录态）。
   */
  uploadImage?: (blob: Blob, filename: string) => Promise<string>
  onProgress?: (p: PdfProgress) => void
  /**
   * 单页解析完把中间结果吐出来（每行的文字/字号/y、每张图的位置与尺寸）。
   * 只给排查奇怪的 PDF 用 —— 比如图片插错位置、标题没识别出来时，
   * 一眼就能看出是坐标空间不对还是阈值不对，不用改代码加 console。
   */
  onDebug?: (page: {
    page: number
    lines: { text: string; size: number; y: number }[]
    images: { y: number; width: number; height: number; skipped?: string }[]
  }) => void
  /** 小于这个边长（px）的图视为图标丢弃，默认 96 */
  minImageSide?: number
  /** 最多导出多少张图，默认 30（防止几十页扫描件把 OSS 和正文都撑爆） */
  maxImages?: number
  /** 不收图，只取文字 */
  skipImages?: boolean
}

/**
 * PDF 空间里的仿射矩阵乘法（[a b c d e f]）。
 * ⚠️ 必须和 pdf.js 的 `Util.transform(m1, m2)` 完全一致（行向量约定）：
 *    e' = m1[0]*m2[4] + m1[2]*m2[5] + m1[4]
 * 写成列向量约定（e' = m1[4]*m2[0] + ...）在第一步看不出来，
 * 但页面级的翻转矩阵（`1 0 0 -1 0 792`）和图像自己的 cm 复合时，
 * 平移量会被乘成几十万倍，图片就被判定「在所有文字下方」而堆到页尾。
 */
function mulMatrix(m: number[], t: number[]): number[] {
  return [
    m[0] * t[0] + m[2] * t[1],
    m[1] * t[0] + m[3] * t[1],
    m[0] * t[2] + m[2] * t[3],
    m[1] * t[2] + m[3] * t[3],
    m[0] * t[4] + m[2] * t[5] + m[4],
    m[1] * t[4] + m[3] * t[5] + m[5]
  ]
}

/**
 * 从 `page.objs` 取一张图的数据。
 *
 * ⚠️ 必须带超时。pdf.js 的 `objs.get(objId, cb)` 是「数据到了才回调」：
 * 图片字节是由 worker 在需要时补发的，而软蒙版、被裁掉的图、损坏的对象
 * 可能**永远不会送过来**。没有超时的话，整个导入会卡死在「提取图片」这一步
 * 一动不动（用户看到的就是「卡住了」）。
 *
 * 超时返回 null，交给调用方按「跳过这张图」处理。
 */
function getPageObject(page: any, objId: string, timeout = 5000): Promise<any> {
  return new Promise((resolve) => {
    let settled = false
    let timer: any
    const done = (v: any) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(v)
    }
    timer = setTimeout(() => done(null), timeout)
    try {
      page.objs.get(objId, (obj: any) => done(obj))
    } catch {
      // 对象压根不在 store 里时 pdf.js 会同步抛，直接当跳过
      done(null)
    }
  })
}

/** 给上传加超时：XHR 卡在死连接上时 onload/onerror 都不会触发 */
function withTimeout<T>(p: Promise<T>, ms: number, message: string): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(message)), ms)
    p.then((v) => { clearTimeout(timer); resolve(v) }, (e) => { clearTimeout(timer); reject(e) })
  })
}

/** 把 pdf.js 的原始像素数据画到 canvas 再导出 PNG */
function imageObjectToBlob(obj: any): Promise<Blob | null> {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  if (!ctx) return Promise.resolve(null)

  // 已经有 ImageBitmap（pdf.js 对 JPEG 会直接解码成它）时不用自己拼像素
  if (obj?.bitmap) {
    canvas.width = obj.bitmap.width
    canvas.height = obj.bitmap.height
    ctx.drawImage(obj.bitmap, 0, 0)
    return new Promise((resolve) => canvas.toBlob((b) => resolve(b), 'image/png'))
  }

  const { width, height, kind, data } = obj || {}
  if (!width || !height || !data) return Promise.resolve(null)
  canvas.width = width
  canvas.height = height

  const imgData = ctx.createImageData(width, height)
  const out = imgData.data
  if (kind === 3) {
    // RGBA
    out.set(data.subarray(0, width * height * 4))
  } else if (kind === 2) {
    // RGB：补上 alpha
    for (let i = 0, j = 0; i < width * height; i++) {
      out[j++] = data[i * 3]
      out[j++] = data[i * 3 + 1]
      out[j++] = data[i * 3 + 2]
      out[j++] = 255
    }
  } else if (kind === 1) {
    // 1bpp 灰度：每位一个像素，1 = 白
    for (let i = 0; i < width * height; i++) {
      const bit = (data[i >> 3] >> (7 - (i & 7))) & 1
      const v = bit ? 255 : 0
      out[i * 4] = v
      out[i * 4 + 1] = v
      out[i * 4 + 2] = v
      out[i * 4 + 3] = 255
    }
  } else {
    return Promise.resolve(null)
  }

  ctx.putImageData(imgData, 0, 0)
  return new Promise((resolve) => canvas.toBlob((b) => resolve(b), 'image/png'))
}

/** 处理行内的连续空白（PDF 抽出来的文字常有 `第  一  章` 这种） */
function tidyLine(raw: string): string {
  return raw
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]{2,}/g, ' ')
    // 中文字符之间不该有空格（pdf.js 逐字输出时会插），英文单词之间保留一个
    .replace(/([\u4e00-\u9fff])\s+(?=[\u4e00-\u9fff])/g, '$1')
    .trim()
}

/** 行尾连字符断词：`informa-` + `tion` → `information`。
 *  合并时会丢掉一行，所以必须带着字号和 y 一起合并，否则后面的行会对错字号、图片会插错位置。 */
function dehyphenate(lines: { text: string; size: number; y: number }[]): { text: string; size: number; y: number }[] {
  const out: { text: string; size: number; y: number }[] = []
  for (const line of lines) {
    const prev = out[out.length - 1]
    if (prev && /[A-Za-z]-$/.test(prev.text) && /^[a-z]/.test(line.text)) {
      // 合并后的行用两行里较大的字号，避免被误判成正文；y 取上一行（版面位置更靠上）
      prev.text = prev.text.slice(0, -1) + line.text
      prev.size = Math.max(prev.size, line.size)
    } else {
      out.push({ ...line })
    }
  }
  return out
}

export async function pdfToMarkdown(file: File, options: PdfImportOptions = {}): Promise<PdfImportResult> {
  const {
    uploadImage,
    onProgress,
    onDebug,
    minImageSide = 96,
    maxImages = 30,
    skipImages = false
  } = options

  const warnings: string[] = []
  const pdfjs: any = await import('pdfjs-dist')
  pdfjs.GlobalWorkerOptions.workerSrc = workerUrl

  onProgress?.({ stage: 'loading', page: 0, totalPages: 0 })

  // 打开也要给上限：损坏或带奇怪 xref 的 PDF 会一直卡在 loading，
  // 用户看到的就是「点完没反应」。
  const doc = await withTimeout(
    pdfjs.getDocument({ data: await file.arrayBuffer() }).promise,
    60000,
    'PDF 打开超时，文件可能已损坏或受保护'
  )
  const totalPages: number = doc.numPages

  const blocks: string[] = []
  const seenImages = new Set<string>()
  let images = 0
  let skippedImages = 0
  let failedImages = 0
  /** 数据一直不来的图片张数；累计到阈值就停止后续页的图片提取，避免整篇在那儿空等 */
  let stalledImages = 0
  let uploadError = ''
  let chars = 0

  try {
    for (let pageNo = 1; pageNo <= totalPages; pageNo++) {
      const page = await doc.getPage(pageNo)
      onProgress?.({ stage: 'text', page: pageNo, totalPages, detail: '提取文字' })

      // ---- 1. 文字 ----
      // 单页文字也设上限：个别 PDF 的字体子集损坏时 getTextContent 会一直不返回
      let textContent: any
      try {
        textContent = await withTimeout(page.getTextContent(), 20000, '文字读取超时')
      } catch {
        warnings.push(`第 ${pageNo} 页文字读取超时，已跳过该页`)
        page.cleanup()
        continue
      }
      // 文字和字号必须成对收集：后面会丢掉空行，两个数组分开存一定错位
      const rawLines: { text: string; size: number; y: number }[] = []
      let buffer = ''
      let bufferSize = 0
      let bufferY = 0

      for (const item of textContent.items as any[]) {
        if (typeof item.str !== 'string') continue
        const size = Math.hypot(item.transform?.[2] || 0, item.transform?.[3] || 0) || item.height || 0
        if (!buffer) {
          bufferSize = size
          bufferY = item.transform?.[5] || 0
        }
        buffer += item.str
        // hasEOL 是 pdf.js 给出的「这一项结束于换行」标记，比按 y 坐标聚类靠谱
        if (item.hasEOL) {
          rawLines.push({ text: buffer, size: bufferSize, y: bufferY })
          buffer = ''
        }
      }
      if (buffer.trim()) rawLines.push({ text: buffer, size: bufferSize, y: bufferY })

      // 先规整文字、丢掉空行，再合并断词行（字号和 y 全程跟着行一起走）
      const prepared = rawLines
        .map((l) => ({ text: tidyLine(l.text), size: l.size, y: l.y }))
        .filter((l) => l.text.length > 0)
      const lines = dehyphenate(prepared)
      chars += lines.map((l) => l.text).join('').replace(/\s/g, '').length

      // 正文基准字号取中位数，避免个别大号数字把整页都判成"标题"
      const sizes = prepared.map((l) => l.size).filter((s) => s > 0).sort((a, b) => a - b)
      const median = sizes.length ? sizes[Math.floor(sizes.length / 2)] : 0

      const pageLines: string[] = lines.map((line) => {
        const size = line.size || median
        // 1.2 倍：技术文档里 h2 常常只是 18px 对 14px（1.28），阈值放到 1.35 会漏掉
        const isHeading = median > 0 && size >= median * 1.2 && line.text.length <= 40 && !/[。！？；，]$/.test(line.text)
        if (isHeading) return `## ${line.text}`
        // 已带列表符号的保留，顺手统一成 Markdown 的 `-`
        if (/^[•·▪◦]\s*/.test(line.text)) return '- ' + line.text.replace(/^[•·▪◦]\s*/, '')
        return line.text
      })

      // ---- 2. 图片 ----
      const debugImages: { y: number; width: number; height: number; skipped?: string }[] = []
      // 连续有多张图取不到数据，说明这份 PDF 的图片对象本来就取不出来（常见于加密/被裁剪的资源）
      // → 后面的页不再尝试，直接把文字导完，别让用户一直等
      const imagesDisabled = stalledImages >= 5
      if (!skipImages && uploadImage && images < maxImages && !imagesDisabled) {
        onProgress?.({ stage: 'images', page: pageNo, totalPages, detail: '提取图片' })
        let ops: any
        try {
          ops = await withTimeout(page.getOperatorList(), 30000, '绘制指令读取超时')
        } catch {
          warnings.push(`第 ${pageNo} 页的绘制指令读取失败，已跳过该页的图片`)
          page.cleanup()
          if (pageLines.length) blocks.push(pageLines.join('\n'))
          continue
        }

        const opList: number[] = ops.fnArray
        const argsList: any[] = ops.argsArray
        // 第一遍只扫指令（纯 CPU，很快），记下每张图的「版面 y + 对象 id」；
        // 取图片数据放第二遍，因为那一步要等 worker 送数据，必须能超时。
        const spots: { y: number; objId?: string; inline?: any }[] = []

        // 图片落在版面的哪个位置，只能从「绘制它那一刻的变换矩阵」里取。
        // 所以要跟着指令流维护一个 CTM 栈，否则只能把图一律堆到页尾。
        let ctm = [1, 0, 0, 1, 0, 0]
        const ctmStack: number[][] = []
        for (let i = 0; i < opList.length; i++) {
          const fn = opList[i]
          if (fn === pdfjs.OPS.save) {
            ctmStack.push(ctm.slice())
          } else if (fn === pdfjs.OPS.restore) {
            ctm = ctmStack.pop() || [1, 0, 0, 1, 0, 0]
          } else if (fn === pdfjs.OPS.transform) {
            ctm = mulMatrix(ctm, argsList[i])
          } else if (fn === pdfjs.OPS.paintFormXObjectBegin) {
            // 图大概率画在 Form XObject 里，进对象时要乘上它自己的矩阵，
            // 否则拿到的是对象内部坐标（常常接近 0），算出来的位置全是页底
            ctmStack.push(ctm.slice())
            if (argsList[i]?.[0]) ctm = mulMatrix(ctm, argsList[i][0])
          } else if (fn === pdfjs.OPS.paintFormXObjectEnd) {
            ctm = ctmStack.pop() || [1, 0, 0, 1, 0, 0]
          } else if (fn === pdfjs.OPS.paintInlineImageXObject) {
            // 内联小图（base64 直接塞在内容流里），对象本身就在 args 里
            spots.push({ y: ctm[5], inline: argsList[i]?.[0] })
          } else if (fn === pdfjs.OPS.paintImageXObject || fn === pdfjs.OPS.paintJpegXObject) {
            const objId = argsList[i]?.[0]
            if (!objId || seenImages.has(objId)) { skippedImages++; continue }
            seenImages.add(objId)
            spots.push({ y: ctm[5], objId })
          }
        }

        // 第二遍取像素数据。每张最多等 5s —— 拿不到就跳过这一张，
        // 绝不能让「某张图的数据永远不来」把整篇导入拖死。
        const found: { y: number; obj: any }[] = []
        let pageStalls = 0
        for (const spot of spots) {
          // 同一页连着 3 张都等不到数据，基本可以断定这页的图取不出来，
          // 剩下的不再一张张等（每张 5s，几十张就是几分钟）
          if (pageStalls >= 3) break
          onProgress?.({ stage: 'images', page: pageNo, totalPages, detail: `读取第 ${found.length + 1}/${spots.length} 张图` })
          if (spot.inline) {
            found.push({ y: spot.y, obj: spot.inline })
            continue
          }
          const obj = await getPageObject(page, spot.objId as string)
          if (!obj) {
            stalledImages++
            pageStalls++
            skippedImages++
            debugImages.push({ y: spot.y, width: 0, height: 0, skipped: '读取超时' })
            continue
          }
          found.push({ y: spot.y, obj })
        }

        // 按 y 从大到小（页面上到下）逐张处理，跳过图标/水印
        found.sort((a, b) => b.y - a.y)
        const inserts: { index: number; md: string }[] = []
        for (const { y, obj } of found) {
          const dw = obj?.width || obj?.bitmap?.width || 0
          const dh = obj?.height || obj?.bitmap?.height || 0
          if (images >= maxImages) { debugImages.push({ y, width: dw, height: dh, skipped: '达到张数上限' }); continue }
          if (!dw || !dh || dw < minImageSide || dh < minImageSide) {
            debugImages.push({ y, width: dw, height: dh, skipped: '尺寸过小' })
            skippedImages++
            continue
          }

          const blob = await imageObjectToBlob(obj)
          if (!blob) {
            debugImages.push({ y, width: dw, height: dh, skipped: '像素格式不支持' })
            skippedImages++
            continue
          }

          onProgress?.({ stage: 'upload', page: pageNo, totalPages, detail: `上传第 ${images + 1} 张图` })
          try {
            // 上传也可能卡住（死连接下 XHR 的 onload/onerror 都不触发），给 60s 上限
            const url = await withTimeout(uploadImage(blob, `pdf-p${pageNo}-${images + 1}.png`), 60000, '图片上传超时')
            // 插到「所有 y 比它高的行」之后 —— 即版面上它原本的位置
            const above = lines.filter((l) => l.y > y).length
            inserts.push({ index: Math.min(above, pageLines.length), md: `![](${url})` })
            debugImages.push({ y, width: dw, height: dh })
            images++
          } catch (e: any) {
            debugImages.push({ y, width: dw, height: dh, skipped: '上传失败' })
            failedImages++
            uploadError = e?.message || '未知错误'
          }
        }

        // inserts 的 index 是按「不含图片的行号」算的，从后往前插才不会互相错位
        inserts.sort((a, b) => b.index - a.index)
        for (const it of inserts) pageLines.splice(it.index, 0, it.md)
        page.cleanup()
      } else {
        page.cleanup()
      }

      onDebug?.({
        page: pageNo,
        lines: lines.map((l) => ({ text: l.text, size: l.size, y: l.y })),
        images: debugImages
      })
      if (pageLines.length) blocks.push(pageLines.join('\n'))
    }
  } finally {
    try { await doc.destroy() } catch { /* 关不掉也不影响结果 */ }
  }

  if (chars < 50) {
    warnings.push('这个 PDF 几乎没有文字层（大概率是扫描件/纯图片），已按图片页导入；需要可编辑文字的话得先做 OCR')
  }
  if (failedImages) {
    warnings.push(`${failedImages} 张图片没能上传，已从正文跳过（${uploadError}）；请确认登录状态后重新导入，或手动补图`)
  }
  if (stalledImages) {
    warnings.push(`有 ${stalledImages} 张图片的数据没能读出来（这类图的资源是延迟加载的），已跳过；文字部分不受影响`)
  }
  if (images >= maxImages) {
    warnings.push(`图片数量达到上限（${maxImages} 张），后面的图片没有导入`)
  }

  onProgress?.({ stage: 'done', page: totalPages, totalPages })

  return { markdown: blocks.join('\n\n'), pages: totalPages, images, skippedImages, chars, warnings }
}
