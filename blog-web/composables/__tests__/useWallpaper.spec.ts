import { computed, ref } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import { skinOf } from '../useSkin'
import { useWallpaper } from '../useWallpaper'

afterEach(() => vi.unstubAllGlobals())

it('applies the same display mode after switching between skin, site image and plain paper', () => {
  const current = ref(skinOf('mint'))
  vi.stubGlobal('useSkin', () => ({ current }))
  vi.stubGlobal('computed', computed)
  vi.stubGlobal('useCookie', () => ref(null))
  vi.stubGlobal('useState', (_key: string, initial: () => unknown) => ref(initial()))

  const wallpaper = useWallpaper()
  expect(wallpaper.source.value).toBe('skin')
  expect(wallpaper.bannerVisible.value).toBe(true)

  for (const mode of ['full', 'cover'] as const) {
    wallpaper.setMode(mode)
    expect(wallpaper.visible.value).toBe(true)
    expect(wallpaper.bannerVisible.value).toBe(false)
  }
  wallpaper.setMode('none')
  expect(wallpaper.visible.value).toBe(false)
  wallpaper.setImage(' /uploads/cover.jpg ')
  expect(wallpaper.source.value).toBe('site')
  expect(wallpaper.visible.value).toBe(false)

  wallpaper.setMode('banner')
  expect(wallpaper.bannerVisible.value).toBe(true)
  current.value = skinOf('paper')
  expect(wallpaper.source.value).toBe('site')
  wallpaper.setImage(' ')
  expect(wallpaper.source.value).toBe('none')
  expect(wallpaper.bannerVisible.value).toBe(false)

  current.value = skinOf('sky')
  expect(wallpaper.source.value).toBe('skin')
  expect(wallpaper.bannerVisible.value).toBe(true)
})

it('keeps a manual skin background, retries a failed station image and restores the last visible mode', () => {
  vi.stubGlobal('useSkin', () => ({ current: ref(skinOf('mint')) }))
  vi.stubGlobal('computed', computed)
  vi.stubGlobal('useCookie', () => ref(null))
  vi.stubGlobal('useState', (_key: string, initial: () => unknown) => ref(initial()))
  const wallpaper = useWallpaper()
  wallpaper.setImage('/a.jpg')
  wallpaper.setSource('skin')
  expect(wallpaper.source.value).toBe('skin')
  wallpaper.setMode('full')
  wallpaper.setSource('none')
  wallpaper.setSource('site')
  expect(wallpaper.mode.value).toBe('full')
  wallpaper.markImageFailed('/a.jpg')
  expect(wallpaper.source.value).toBe('skin')
  wallpaper.setSource('site')
  expect(wallpaper.source.value).toBe('site')
  wallpaper.setImage('/b.jpg')
  wallpaper.markImageFailed('/a.jpg')
  expect(wallpaper.siteImage.value).toBe('/b.jpg')
})
