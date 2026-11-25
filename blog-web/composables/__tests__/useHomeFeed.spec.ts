import { expect, it, vi } from 'vitest'
import { useHomeFeed, type HomeArticlePage } from '../useHomeFeed'

it('keeps the selected category when an older request finishes late', async () => {
  let finish!: (data: HomeArticlePage) => void
  const fetch = vi.fn<(page: number) => Promise<HomeArticlePage>>()
    .mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    .mockResolvedValueOnce({ records: [{ id: 2 }], total: 1 })
  const feed = useHomeFeed(fetch, 2)
  const older = feed.load()
  await feed.load()
  finish({ records: [{ id: 9 }], total: 10 })
  await older
  expect(feed.records).toEqual([{ id: 2 }])
  expect(feed.hasMore).toBe(false)
})

it('retries a failed next page without losing earlier articles or requesting it twice', async () => {
  const fetch = vi.fn<(page: number) => Promise<HomeArticlePage>>()
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce({ records: [{ id: 2 }, { id: 3 }], total: 3 })
  const feed = useHomeFeed(fetch, 2)
  feed.reset({ records: [{ id: 1 }, { id: 2 }], total: 3 })
  await feed.next()
  expect(feed.error).toBe(true)
  expect(feed.records).toHaveLength(2)
  await Promise.all([feed.next(), feed.next()])
  expect(fetch.mock.calls).toEqual([[2], [2]])
  expect(feed.records.map(article => article.id)).toEqual([1, 2, 3])
  expect(feed.hasMore).toBe(false)
})
