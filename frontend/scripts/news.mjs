import Parser from 'rss-parser'
import https from 'node:https'
import { HttpsProxyAgent } from 'https-proxy-agent'
import { readFile, writeFile, mkdir, rename } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve, dirname } from 'node:path'

export const sources = [
  {
    id: 'openai-news',
    name: 'OpenAI 官方新闻',
    category: '模型与研究',
    feedUrl: 'https://openai.com/news/rss.xml',
    homeUrl: 'https://openai.com/news/',
    prefix: 'https://openai.com/'
  },
  {
    id: 'codex-releases',
    name: 'Codex 官方版本',
    category: '开发工具',
    feedUrl: 'https://github.com/openai/codex/releases.atom',
    homeUrl: 'https://github.com/openai/codex/releases',
    prefix: 'https://github.com/openai/codex/releases/'
  },
  {
    id: 'claude-releases',
    name: 'Claude Code 官方版本',
    category: '开发工具',
    feedUrl: 'https://github.com/anthropics/claude-code/releases.atom',
    homeUrl: 'https://github.com/anthropics/claude-code/releases',
    prefix: 'https://github.com/anthropics/claude-code/releases/'
  }
]
const parser = new Parser({
  customFields: {
    item: [
      ['published', 'rawPublished'],
      ['updated', 'rawUpdated']
    ]
  }
})
export function safeArticleUrl(value, source) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' &&
      !url.username &&
      !url.password &&
      url.href.startsWith(source.prefix)
      ? url.href
      : null
  } catch {
    return null
  }
}
const timestamp = (value) => {
  const date = new Date(value ?? '')
  return Number.isNaN(+date) ? null : date.toISOString()
}
export async function parseNews(xml, source) {
  if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('unsupported-xml')
  const feed = await parser.parseString(xml),
    seen = new Set(),
    atom = /<(?:\w+:)?feed[\s>]/.test(xml)
  return (feed.items ?? [])
    .flatMap((item) => {
      const url = safeArticleUrl(item.link, source),
        title = String(item.title ?? '')
          .replace(/<[^>]*>/g, '')
          .replace(/[\u0000-\u001f]/g, ' ')
          .trim()
          .slice(0, 300)
      if (!url || !title || seen.has(url)) return []
      seen.add(url)
      return [
        {
          sourceId: source.id,
          title,
          url,
          publishedAt: timestamp(atom ? item.rawPublished : (item.pubDate ?? item.isoDate)),
          updatedAt: timestamp(item.rawUpdated)
        }
      ]
    })
    .sort((a, b) =>
      (b.publishedAt ?? b.updatedAt ?? '').localeCompare(a.publishedAt ?? a.updatedAt ?? '')
    )
    .slice(0, 20)
}
export function fetchXml(url) {
  return new Promise((accept, reject) => {
    const proxy = process.env.AIF_NEWS_PROXY || process.env.HTTPS_PROXY
    const req = https.get(
      url,
      {
        agent: proxy ? new HttpsProxyAgent(proxy) : undefined,
        headers: {
          'User-Agent': 'AI-Footprint/0.3 (+https://github.com/danjiahao88-sys/TokenAnalysis)',
          Accept: 'application/atom+xml,application/rss+xml,application/xml'
        }
      },
      (response) => {
        if (response.statusCode !== 200) {
          response.resume()
          reject(new Error('http-failed'))
          return
        }
        const chunks = []
        let bytes = 0
        response.on('data', (chunk) => {
          bytes += chunk.length
          if (bytes > 4 * 1024 * 1024) {
            response.destroy(new Error('too-large'))
            return
          }
          chunks.push(chunk)
        })
        response.on('end', () => accept(Buffer.concat(chunks).toString('utf8')))
        response.on('error', reject)
      }
    )
    const timer = setTimeout(() => req.destroy(new Error('timeout')), 15000)
    req.on('close', () => clearTimeout(timer))
    req.on('error', reject)
  })
}
export async function collectNews(
  previous = null,
  fetcher = fetchXml,
  now = new Date().toISOString()
) {
  const results = await Promise.all(
    sources.map(async (source) => {
      try {
        const items = await parseNews(await fetcher(source.feedUrl), source)
        if (!items.length) throw new Error('empty-feed')
        return {
          source: {
            ...source,
            prefix: undefined,
            status: 'ok',
            checkedAt: now,
            lastSuccessAt: now,
            message: '已读取官方源'
          },
          items
        }
      } catch {
        const oldSource = (Array.isArray(previous?.sources) ? previous.sources : []).find(
          (s) => s?.id === source.id
        )
        const items = (Array.isArray(previous?.items) ? previous.items : [])
          .filter(
            (item) =>
              item?.sourceId === source.id &&
              safeArticleUrl(item.url, source) &&
              typeof item.title === 'string'
          )
          .slice(0, 20)
          .map((item) => ({
            sourceId: source.id,
            title: item.title
              .replace(/<[^>]*>/g, '')
              .replace(/[\u0000-\u001f]/g, ' ')
              .trim()
              .slice(0, 300),
            url: safeArticleUrl(item.url, source),
            publishedAt: timestamp(item.publishedAt),
            updatedAt: timestamp(item.updatedAt)
          }))
          .filter((item) => item.title)
        return {
          source: {
            ...source,
            prefix: undefined,
            status: 'error',
            checkedAt: now,
            lastSuccessAt: items.length ? timestamp(oldSource?.lastSuccessAt) : null,
            message: items.length ? '本次抓取失败，保留上次内容' : '抓取失败，暂无可用内容'
          },
          items
        }
      }
    })
  )
  return {
    format: 'ai-footprint-news-v1',
    generatedAt: now,
    sources: results.map((r) => r.source),
    items: results.flatMap((r) => r.items)
  }
}
async function main() {
  const target = fileURLToPath(new URL('../public/news.json', import.meta.url))
  let previous = null
  try {
    previous = JSON.parse(await readFile(target, 'utf8'))
  } catch {
    /* first build */
  }
  // Scheduled builds start from Git; prefer the last published snapshot for failure fallback.
  if (process.env.GITHUB_ACTIONS === 'true') {
    try {
      const published = JSON.parse(
        await fetchXml('https://danjiahao88-sys.github.io/TokenAnalysis/news.json')
      )
      if (
        published.format === 'ai-footprint-news-v1' &&
        Array.isArray(published.sources) &&
        Array.isArray(published.items) &&
        timestamp(published.generatedAt) &&
        (!previous || Date.parse(published.generatedAt) > Date.parse(previous.generatedAt))
      )
        previous = published
    } catch {
      /* checked-in snapshot remains available */
    }
  }
  const result = await collectNews(previous)
  await mkdir(dirname(target), { recursive: true })
  await writeFile(`${target}.tmp`, JSON.stringify(result, null, 2) + '\n')
  await rename(`${target}.tmp`, target)
  for (const source of result.sources)
    console.log(
      `${source.id}: ${source.status}; ${result.items.filter((i) => i.sourceId === source.id).length} items`
    )
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main()
