import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseNews, collectNews, sources, safeArticleUrl } from './news.mjs'
const rss = `<rss version="2.0"><channel><title>Official</title><item><title>New &amp; useful</title><link>https://openai.com/index/test/</link><pubDate>Tue, 03 Nov 2026 12:00:00 GMT</pubDate></item><item><title>Duplicate</title><link>https://openai.com/index/test/</link></item><item><title>Bad host</title><link>https://openai.com.evil.test/test</link></item></channel></rss>`
test('RSS normalization, escaping and URL identity', async () => {
  const result = await parseNews(rss, sources[0])
  assert.equal(result.length, 1)
  assert.equal(result[0].title, 'New & useful')
  assert.equal(result[0].publishedAt, '2026-11-03T12:00:00.000Z')
  assert.equal(safeArticleUrl('https://x@y.test/', sources[0]), null)
  await assert.rejects(parseNews('<!DOCTYPE feed><rss/>', sources[0]))
})
test('Atom release dates and partial failures retain last success without pretending fresh', async () => {
  const atom = `<feed xmlns="http://www.w3.org/2005/Atom"><title>Codex</title><entry><title>v1.2</title><link href="https://github.com/openai/codex/releases/tag/v1.2"/><updated>2026-11-04T10:00:00Z</updated><id>x</id></entry></feed>`
  const parsed = await parseNews(atom, sources[1])
  assert.equal(parsed.length, 1)
  assert.equal(parsed[0].updatedAt, '2026-11-04T10:00:00.000Z')
  assert.equal(parsed[0].publishedAt, null)
  const at = '2026-11-04T12:00:00Z',
    later = '2026-11-05T12:00:00Z'
  const previous = await collectNews(
    null,
    async (url) => {
      if (url.includes('openai.com')) return rss
      throw Error()
    },
    at
  )
  const result = await collectNews(
    previous,
    async () => {
      throw Error('sensitive network details')
    },
    later
  )
  assert.equal(result.items.length, 1)
  assert.equal(result.sources[0].status, 'error')
  assert.equal(Date.parse(result.sources[0].lastSuccessAt), Date.parse(at))
  assert.equal(result.sources[0].checkedAt, later)
  assert.ok(!JSON.stringify(result).includes('sensitive network details'))
})
test('invalid XML, empty feeds and malformed fallback never appear as a fresh success', async () => {
  await assert.rejects(parseNews('<rss><broken>', sources[0]))
  const result = await collectNews(
    { sources: [null], items: [null] },
    async () => '<rss version="2.0"><channel><title>Empty</title></channel></rss>'
  )
  assert.equal(result.items.length, 0)
  assert.ok(result.sources.every((s) => s.status === 'error' && s.lastSuccessAt === null))
  const malformedDate = await parseNews(
    '<rss version="2.0"><channel><item><title>No date</title><link>https://openai.com/index/unknown</link><pubDate>invalid</pubDate></item></channel></rss>',
    sources[0]
  )
  assert.equal(malformedDate[0].publishedAt, null)
})
