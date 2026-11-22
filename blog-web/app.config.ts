/**
 * 应用级运行时配置（Nuxt app.config）
 *
 * 和 nuxt.config.ts 的区别：
 *   - nuxt.config.ts     构建期配置，改完必须重新 build；
 *   - app.config.ts      运行时配置，会被序列化进 payload，改这里不用重 build。
 *
 * AI 助手入口（CompanionEntry）读的就是这里的 `companion`。
 * 想临时关掉右下角的角色（比如排查它挡住了别的东西），把 enabled 改 false 即可。
 *
 * 🔴 这里**绝对不能** `import ... from '#app'`（也别 import 任何 vue/nuxt 运行时别名）。
 *    app.config.ts 会被打进服务端产物，而 `#app` 是 Vue 应用层别名，
 *    Nitro 的 impound 插件会直接报「Vue app aliases are not allowed in server runtime」。
 *    `defineAppConfig` 是自动导入的宏，裸写就行。
 */
export default defineAppConfig({
  companion: {
    /** 总开关。false = 右下角不渲染任何 AI 入口 */
    enabled: true,

    /**
     * 画笔：
     *   'standee' 静态立绘（默认）。一张自己渲染的 PNG，174×240，约 18KB。
     *             CSS 补呼吸/摆动/视线跟随，状态照样能表达，手机也能跑。
     *   'css'     内联 SVG 小鱼（约 4KB）。立绘加载失败时的自动降级，也可手动切过来。
     *
     * ⚠️ 为什么没有 'live2d'：
     *   技术上是通的（实测 oh-my-live2d + Cubism Core 5 能渲出完整半身立绘），
     *   但这个模型（大肥鱼 / DeepSeek-chan）的使用须知禁止「重新分发完整模型文件」，
     *   而网页 Live2D 必须把 moc3 发给每个访客，等于分发。所以正式方案只发一张渲染图。
     *   如果以后换成明确允许网页展示的模型（如 Live2D 官方 sample），
     *   再加 'live2d' 这个取值，并把 CompanionLive2D.vue 挂上来。
     */
    engine: 'standee' as 'standee' | 'css',
  },
})
