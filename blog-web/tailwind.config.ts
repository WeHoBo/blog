import type { Config } from 'tailwindcss'

export default <Config>{
  content: [
    './components/**/*.{vue,js,ts}',
    './layouts/**/*.vue',
    './pages/**/*.vue',
    './app.vue'
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // 主题色：由 🎨 滑块控制色相，但饱和度整体压到 44%，
        // 避免出现「高饱和亮蓝按钮」那种互联网后台感。
        primary: {
          50: 'hsl(var(--ph) var(--ps) 96%)',
          100: 'hsl(var(--ph) var(--ps) 92%)',
          200: 'hsl(var(--ph) var(--ps) 84%)',
          300: 'hsl(var(--ph) var(--ps) 72%)',
          400: 'hsl(var(--ph) var(--ps) 60%)',
          500: 'hsl(var(--ph) var(--ps) 50%)',
          600: 'hsl(var(--ph) var(--ps) 44%)',
          700: 'hsl(var(--ph) var(--ps) 36%)',
          800: 'hsl(var(--ph) var(--ps) 28%)',
          900: 'hsl(var(--ph) var(--ps) 21%)'
        },
        // 把 gray 整体替换为「纸与墨」的暖中性色。
        // 全站已有大量 text-gray-500 / dark:bg-gray-800，改这一处即可整体去冷色，
        // 不需要逐组件覆盖，也不需要用 !important 硬盖。
        gray: {
          50: 'hsl(38 30% 94%)',
          100: 'hsl(37 26% 89%)',
          200: 'hsl(36 21% 84%)',
          300: 'hsl(34 16% 73%)',
          400: 'hsl(33 10% 55%)',
          500: 'hsl(32 9% 43%)',
          600: 'hsl(30 8% 34%)',
          700: 'hsl(29 8% 24%)',
          800: 'hsl(28 9% 15%)',
          900: 'hsl(26 10% 9%)'
        },
        // 纸面底色：页面比卡片深一点点，形成「纸叠着纸」的层次
        paper: {
          DEFAULT: 'hsl(38 30% 94%)',
          deep: 'hsl(37 26% 89%)'
        },
        ink: {
          DEFAULT: 'hsl(26 12% 14%)',
          soft: 'hsl(28 9% 30%)'
        }
      },
      fontFamily: {
        // 正文用无衬线，标题用衬线：杂志感主要来自这一处对比
        serif: ['Georgia', 'Songti SC', 'Source Han Serif SC', 'Noto Serif CJK SC', 'STSong', 'SimSun', 'serif']
      },
      boxShadow: {
        // 暖调、极轻的投影，替代默认偏冷的 shadow-md
        paper: '0 1px 2px rgba(74, 59, 42, 0.04), 0 8px 24px -16px rgba(74, 59, 42, 0.18)',
        'paper-dark': '0 1px 2px rgba(0, 0, 0, 0.3)'
      }
    }
  },
  plugins: []
}
