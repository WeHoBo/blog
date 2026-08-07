import { describe, expect, it } from 'vitest'
import { parseLayout, parseSort } from '../../composables/useLayout'

describe('parseLayout', () => {
  it('returns grid for "grid"', () => {
    expect(parseLayout('grid')).toBe('grid')
  })

  it('returns list for "list"', () => {
    expect(parseLayout('list')).toBe('list')
  })

  it('falls back to list for null', () => {
    expect(parseLayout(null)).toBe('list')
  })

  it('falls back to list for unknown values', () => {
    expect(parseLayout('weird')).toBe('list')
  })
})

describe('parseSort', () => {
  it('returns title for "title"', () => {
    expect(parseSort('title')).toBe('title')
  })

  it('returns time for "time"', () => {
    expect(parseSort('time')).toBe('time')
  })

  it('falls back to time for null', () => {
    expect(parseSort(null)).toBe('time')
  })

  it('falls back to time for unknown values', () => {
    expect(parseSort('wat')).toBe('time')
  })
})
