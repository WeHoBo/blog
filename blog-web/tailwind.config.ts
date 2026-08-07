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
        primary: {
          50: 'hsl(var(--ph) var(--ps) 97%)',
          100: 'hsl(var(--ph) var(--ps) 93%)',
          200: 'hsl(var(--ph) var(--ps) 85%)',
          300: 'hsl(var(--ph) var(--ps) 75%)',
          400: 'hsl(var(--ph) var(--ps) 66%)',
          500: 'hsl(var(--ph) var(--ps) 55%)',
          600: 'hsl(var(--ph) var(--ps) 50%)',
          700: 'hsl(var(--ph) var(--ps) 42%)',
          800: 'hsl(var(--ph) var(--ps) 33%)',
          900: 'hsl(var(--ph) var(--ps) 24%)'
        }
      }
    }
  },
  plugins: []
}
