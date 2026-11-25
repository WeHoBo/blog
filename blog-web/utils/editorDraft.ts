export interface DraftFields {
  title: string
  summary: string
  cover: string
  contentMd: string
  categoryId: number | null
  tagIds: number[]
  isTop: number
  isFeatured: number
  series: string
  slug: string
  publishAt: string
}

export interface LocalArticleDraft {
  fields: DraftFields
  updatedAt: number
  articleId?: string
  editVersion?: string
}

export function draftPrefix(userId: number, mode: string, articleId?: string | number) {
  return `boguan:editor:${userId}:${mode}:${articleId ?? 'new'}:`
}

export function readDrafts(storage: Storage, prefix: string) {
  const found: { key: string; draft: LocalArticleDraft }[] = []
  for (let i = 0; i < storage.length; i++) {
    const key = storage.key(i)
    if (!key?.startsWith(prefix)) continue
    try {
      const draft = JSON.parse(storage.getItem(key) || 'null')
      const f = draft?.fields
      if (!f || !Number.isFinite(draft.updatedAt) ||
          !['title', 'summary', 'cover', 'contentMd', 'series', 'slug', 'publishAt'].every(k => typeof f[k] === 'string') ||
          ![0, 1].includes(f.isTop) || ![0, 1].includes(f.isFeatured) ||
          !(f.categoryId === null || Number.isSafeInteger(f.categoryId)) ||
          !Array.isArray(f.tagIds) || !f.tagIds.every(Number.isSafeInteger) ||
          (draft.articleId !== undefined && !/^\d+$/.test(draft.articleId))) continue
      found.push({ key, draft })
    } catch { /* 跳过损坏的副本，继续读取其他稿件。 */ }
  }
  return found.sort((a, b) => b.draft.updatedAt - a.draft.updatedAt)
}
