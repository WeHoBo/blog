import { describe, expect, it } from 'vitest'
import { uploadResult, validateBackground } from '../../utils/upload'

describe('background uploads', () => {
  it('accepts supported images up to the displayed file limit', () => {
    const limit = 10 * 1024 * 1024
    expect(validateBackground({ name: 'cover.PNG', size: limit })).toBe('')
    expect(validateBackground({ name: 'cover.png', size: limit + 1 })).toMatch(/10 MiB/)
    expect(validateBackground({ name: 'cover.tiff', size: 20 })).toMatch(/格式/)
  })

  it('retains the request ID for both application and gateway errors', () => {
    expect(() => uploadResult({ code: 415, message: '不支持的格式' }, 415, 'trace-1')).toThrow(/不支持的格式.*trace-1/)
    expect(() => uploadResult('<html>', 413, 'trace-2')).toThrow(/大小限制.*trace-2/)
    expect(() => uploadResult({ code: 500, message: '上传失败' }, 200, 'trace-3')).toThrow(/上传失败.*trace-3/)
    expect(uploadResult({ code: 200, data: '/uploads/photo.jpg' }, 200, 'ok')).toBe('/uploads/photo.jpg')
  })
})
