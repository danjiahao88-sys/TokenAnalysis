import { z } from 'zod'
const httpsUrl = z.url().refine((value) => {
  const url = new URL(value)
  return url.protocol === 'https:' && !url.username && !url.password
})
export const newsSchema = z
  .object({
    format: z.literal('ai-footprint-news-v1'),
    generatedAt: z.iso.datetime(),
    sources: z
      .array(
        z.object({
          id: z.string().max(80),
          name: z.string().max(100),
          category: z.string().max(60),
          feedUrl: httpsUrl,
          homeUrl: httpsUrl,
          status: z.enum(['ok', 'error']),
          checkedAt: z.iso.datetime(),
          lastSuccessAt: z.iso.datetime().nullable(),
          message: z.string().max(200)
        })
      )
      .max(10),
    items: z
      .array(
        z.object({
          sourceId: z.string().max(80),
          title: z.string().min(1).max(300),
          url: httpsUrl,
          publishedAt: z.iso.datetime().nullable(),
          updatedAt: z.iso.datetime().nullable()
        })
      )
      .max(200)
  })
  .superRefine((value, ctx) => {
    const ids = value.sources.map((s) => s.id)
    if (
      new Set(ids).size !== ids.length ||
      value.items.some((item) => !ids.includes(item.sourceId))
    )
      ctx.addIssue({ code: 'custom', message: '来源关联无效' })
  })
export type News = z.infer<typeof newsSchema>
export function dateLabel(value: string | null, timezone: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', {
        timeZone: timezone,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      }).format(new Date(value))
    : '未知'
}
export function isStale(value: string, now = Date.now()) {
  return now - Date.parse(value) > 48 * 3600000
}
