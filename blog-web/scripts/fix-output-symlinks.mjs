/**
 * 构建后把 `.output` 里的符号链接全部替换成真实拷贝。
 *
 * ## 为什么需要（2026-11-07 生产 500 事故现场）
 *
 * Nitro 为了支持「同一个包存在多个版本」，会把实体放进
 * `.output/server/node_modules/.nitro/<包名>@<版本>/`，然后在 `node_modules/<包名>`
 * 建一个**符号链接**指过去 —— 而且写的是**绝对路径**，本地构建时就是
 * `/d/cxdownload/Blog/blog-web/.output/server/node_modules/.nitro/entities@7.0.1`。
 *
 * 在构建机上一切正常（链接能解析），一旦 tar + 上传到 Linux，这些绝对路径全部失效：
 *
 *   H3Error: Cannot find module 'entities/decode'
 *   at @vue/compiler-core/dist/compiler-core.cjs.prod.js:11
 *   → 全站 SSR 500，但进程 systemctl 显示 active（它确实起来了）
 *
 * 之所以「本地怎么测都是 200」：本地跑的是同一份带链接的产物，而链接在 Windows 上有效。
 * 这就是**产物可移植性**问题，不是代码 bug。
 *
 * ## 做法
 *
 * 遍历 `.output`，遇到符号链接就：读真实目标 → 删链接 → 把目标整棵拷过来。
 * 之后 `.output` 里不存在任何链接，tar / zip / scp -r / rsync 随便哪种方式传都不会坏。
 * `.nitro/` 目录本身不需要删：拷完之后没人再引用它，留着也不影响运行（体积很小）。
 *
 * 用法：`node scripts/fix-output-symlinks.mjs`（已挂在 `npm run build` 后面）
 */
import { lstatSync, readlinkSync, realpathSync, statSync, rmSync, cpSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'

const ROOT = resolve(process.cwd(), '.output')

let replaced = 0

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    let st
    try {
      st = lstatSync(full)
    } catch {
      continue // 断链：lstat 失败说明链接本身都读不到，跳过
    }

    if (st.isSymbolicLink()) {
      let target
      try {
        target = realpathSync(full) // 解析成绝对真实路径
      } catch {
        console.warn(`  ! 断链，无法修复（请检查构建机）：${full} -> ${readlinkSync(full)}`)
        continue
      }
      const isDir = statSync(target).isDirectory()
      rmSync(full, { recursive: isDir, force: true }) // 只删链接本身，不动目标
      cpSync(target, full, { recursive: true, dereference: true })
      replaced++
      console.log(`  ✓ ${full.slice(ROOT.length + 1)} -> 已实体化`)
      if (isDir) walk(full) // 拷进来的目录里可能还有链接
      continue
    }

    if (st.isDirectory()) walk(full)
  }
}

try {
  walk(ROOT)
  console.log(replaced
    ? `\n完成：${replaced} 个符号链接已替换为真实文件，.output 现在可直接打包上传。`
    : '\n完成：没有符号链接，.output 本来就是可移植的。')
} catch (e) {
  console.error('修复失败：' + e.message)
  process.exit(1)
}
