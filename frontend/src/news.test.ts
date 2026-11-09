import { expect, it } from 'vitest'
import { newsSchema, isStale } from './news'
import { readFileSync } from 'node:fs'
const news = JSON.parse(readFileSync(new URL('../public/news.json', import.meta.url), 'utf8'))
it('新闻快照校验来源关联、HTTPS与缺失日期，时间新鲜度以48小时为界', () => {
  const parsed = newsSchema.parse(news)
  expect(parsed.items.length).toBeGreaterThan(0)
  expect(
    newsSchema.safeParse({ ...news, items: [{ ...news.items[0], sourceId: 'untrusted' }] }).success
  ).toBe(false)
  expect(
    newsSchema.safeParse({ ...news, items: [{ ...news.items[0], url: 'javascript:alert(1)' }] })
      .success
  ).toBe(false)
  expect(
    newsSchema.safeParse({
      ...news,
      items: [{ ...news.items[0], publishedAt: null, updatedAt: null }]
    }).success
  ).toBe(true)
  expect(isStale('2026-11-01T00:00:00Z', Date.parse('2026-11-03T00:00:00Z'))).toBe(false)
  expect(isStale('2026-11-01T00:00:00Z', Date.parse('2026-11-03T00:00:01Z'))).toBe(true)
})
