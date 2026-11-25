/**
 * check-sfc.mjs —— 离线 SFC 编译检查
 *
 * 为什么需要它：本机不能跑 `nuxi dev/build`（oxc-minify 的原生模块被安全策略拦），
 * 所以「模板语法写错 / 引用了不存在的变量」这类错误，如果不在这里拦住，
 * 就只能等服务器构建时才发现。
 *
 * 做法：用 @vue/compiler-sfc 把指定 .vue 的 template 与 script 编译一遍，
 * 有错误就抛出来。只用 devDependency，不进构建链。
 *
 * 用法：
 *   node scripts/check-sfc.mjs components/ArticleCard.vue pages/index.vue
 *   不带参数时检查下面 FILES 里的默认清单
 */
import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse, compileTemplate, compileScript } from '@vue/compiler-sfc'
import * as esbuild from 'esbuild'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')

const DEFAULT_FILES = [
  'components/ArticleCard.vue',
  'components/ArticleRow.vue',
  // 详情页与阅读时长：2026-11-24 改版前这两处一直不在清单里，
  // 改完跑 check-sfc 报「全部通过」其实压根没检查它们。加进来。
  'components/ArticleDetail.vue',
  'components/ReadingTime.vue',
  'components/TableOfContents.vue',
  'pages/archive.vue',
  'components/ScrollPane.vue',
  'components/FeaturedStack.vue',
  'components/ArticleEditor.vue',
  'components/AiFloat.vue',
  'components/CompanionEntry.vue',
  'components/CompanionStandee.vue',
  'components/CompanionCss.vue',
  'components/CompanionBubble.vue',
  'pages/index.vue',
  'pages/admin/articles/index.vue',
  'composables/useIsSuper.ts',
  'composables/useLayout.ts',
  'composables/useCompanionState.ts',
  'composables/useCompanionDrag.ts',
  'components/Header.vue',
  // 皮肤系统（2026-11-25）：三套首页版式 + 背景层 + 状态源
  'composables/useSkin.ts',
  'composables/useWallpaper.ts',
  'components/WallpaperLayer.vue',
  'components/WallpaperArtwork.vue',
  'components/home/FeedMore.vue',
  'composables/useHomeFeed.ts',
  'components/AppConfirm.vue',
  // 2026-11-24 codex 那轮新增：壁纸成品组件、后台背景预览、草稿与上传工具。
  // 这几个之前没进清单，改完跑 check-sfc 会是「全部通过」但其实没检查。
  'components/AdminBackgroundPreview.vue',
  'pages/admin/background-preview.vue',
  'utils/editorDraft.ts',
  'utils/upload.ts',
  'components/home/FilterResults.vue',
  'components/home/SkinSidebar.vue',
  'components/home/SkinMint.vue',
  // 2026-11-24 第四轮（四套参考版式 + 缩略图选择器）。
  // 这几个是 codex 新建的，之前不在清单里 → 跑 check-sfc 报「全部通过」其实没检查过。
  'composables/useReferenceShell.ts',
  'components/home/ReferenceEdition.vue',
  'components/home/EditionCard.vue',
  'components/ReferenceRail.vue',
  'components/SkinThumbnail.vue',
  'components/SkinWallpaper.vue',
  'layouts/default.vue',
  'components/ApplyAuthorDialog.vue',
  'pages/author/index.vue',
  'pages/author/create.vue',
  'pages/author/[id].vue',
  'pages/admin/apply.vue',
  'pages/admin/users.vue',
  'pages/admin/logs.vue',
  'layouts/admin.vue',
  'components/Pagination.vue',
  // 账号体系（手机号 + 密码登录）
  'pages/login.vue',
  'pages/register.vue',
  'pages/forgot-password.vue',
  'pages/profile.vue',
  'stores/auth.ts',
]

const files = process.argv.slice(2).length ? process.argv.slice(2) : DEFAULT_FILES
let failed = 0

for (const rel of files) {
  const abs = resolve(ROOT, rel)
  if (!existsSync(abs)) {
    console.log(`  --  跳过（不存在）: ${rel}`)
    continue
  }
  const source = readFileSync(abs, 'utf8')

  // .ts 不是 SFC：用 esbuild 做一次转译，能抓到语法错误（类型错误仍得靠构建时的 vue-tsc/nuxi）。
  if (!rel.endsWith('.vue')) {
    try {
      esbuild.transformSync(source, { loader: 'ts', target: 'es2020' })
      console.log(`  ✓  ${rel}（ts 语法）`)
    } catch (e) {
      failed++
      console.log(`  ✗  ${rel} —— ts 语法错误`)
      console.log(`        ${e.message}`)
    }
    continue
  }

  const id = rel
  const { descriptor, errors } = parse(source, { filename: abs })
  if (errors.length) {
    failed++
    console.log(`  ✗  ${rel} —— SFC 解析失败`)
    for (const e of errors) console.log(`        ${e.message}`)
    continue
  }

  const problems = []

  if (descriptor.script || descriptor.scriptSetup) {
    try {
      compileScript(descriptor, { id })
    } catch (e) {
      problems.push(`script: ${e.message}`)
    }
  }

  if (descriptor.template) {
    const r = compileTemplate({
      source: descriptor.template.content,
      filename: abs,
      id,
      // 不生成 SSR 代码，只验证模板表达式能否编译通过
      ssr: false,
    })
    for (const e of r.errors || []) {
      problems.push(`template: ${typeof e === 'string' ? e : e.message}`)
    }
  }

  if (problems.length) {
    failed++
    console.log(`  ✗  ${rel}`)
    for (const p of problems) console.log(`        ${p}`)
  } else {
    console.log(`  ✓  ${rel}`)
  }
}

if (failed) {
  console.log(`\n${failed} 个文件未通过`)
  process.exit(1)
}
console.log('\n全部通过')
