import { z } from 'zod'

const count = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER)
const label = z.string().min(1).max(160)
const date = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/)
  .refine((v) => {
    const d = new Date(`${v}T00:00:00Z`)
    return !Number.isNaN(+d) && d.toISOString().slice(0, 10) === v
  }, '日期无效')
const usage = z
  .object({
    date,
    tool: label,
    model: label,
    project: label,
    sessionRef: label,
    inputTokens: count,
    cachedInputTokens: count,
    outputTokens: count,
    reasoningOutputTokens: count,
    totalTokens: count
  })
  .superRefine((v, ctx) => {
    if (
      v.cachedInputTokens > v.inputTokens ||
      v.reasoningOutputTokens > v.outputTokens ||
      v.totalTokens !== v.inputTokens + v.outputTokens
    ) {
      ctx.addIssue({ code: 'custom', message: 'Token 计数口径不一致' })
    }
  })
export const snapshotSchema = z
  .object({
    schemaVersion: z.literal(1),
    mode: z.enum(['demo', 'local', 'public']),
    generatedAt: z.iso.datetime(),
    timezone: label.refine((value) => {
      try {
        new Intl.DateTimeFormat('en', { timeZone: value }).format()
        return true
      } catch {
        return false
      }
    }, '统计时区无效'),
    collection: z.object({
      collectedAt: z.iso.datetime().nullable(),
      files: count,
      malformedLines: count,
      unreadableFiles: count,
      legacyFiles: count
    }),
    usage: z.array(usage).max(50000),
    tools: z
      .array(
        z.object({
          id: label,
          name: label,
          status: z.enum(['connected', 'planned', 'empty']),
          description: z.string().max(500),
          url: z.url().refine((v) => v.startsWith('https://'))
        })
      )
      .max(100),
    skills: z
      .array(
        z.object({
          id: label,
          name: label,
          description: z.string().max(500),
          category: label,
          source: label,
          status: z.literal('installed')
        })
      )
      .max(2000),
    rules: z.array(z.string().max(1000)).max(30)
  })
  .superRefine((v, ctx) => {
    const ids = v.tools.map((t) => t.id)
    if (new Set(ids).size !== ids.length || v.usage.some((r) => !ids.includes(r.tool)))
      ctx.addIssue({ code: 'custom', message: '工具关联无效' })
    if (new Set(v.skills.map((s) => s.id)).size !== v.skills.length)
      ctx.addIssue({ code: 'custom', message: 'Skill 身份重复' })
    if (!Number.isSafeInteger(v.usage.reduce((n, r) => n + r.totalTokens, 0)))
      ctx.addIssue({ code: 'custom', message: '统计超出安全范围' })
  })
export type Snapshot = z.infer<typeof snapshotSchema>
export type UsageRow = Snapshot['usage'][number]

export function parseImport(text: string): Snapshot {
  const result = snapshotSchema.parse(JSON.parse(text))
  if (result.mode === 'local') throw new Error('请先从本地工作台导出公开快照，再导入。')
  return result
}
export function fmt(value: number, precision = 1): string {
  if (value >= 1e6) return `${(value / 1e6).toFixed(precision)}M`
  if (value >= 1e3) return `${(value / 1e3).toFixed(precision)}K`
  return value.toLocaleString('en-US')
}
export function shiftDate(value: string, offset: number): string {
  const d = new Date(`${value}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + offset)
  return d.toISOString().slice(0, 10)
}
export function lastDate(data: Snapshot): string {
  return (
    data.usage.reduce((latest, r) => (r.date > latest ? r.date : latest), '') ||
    new Intl.DateTimeFormat('sv-SE', {
      timeZone: data.timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(new Date(data.generatedAt))
  )
}
export function windowRows(
  data: Snapshot,
  days: number,
  tool = 'all',
  previous = false
): UsageRow[] {
  const end = shiftDate(lastDate(data), previous ? -days : 0)
  const start = shiftDate(end, 1 - days)
  return data.usage.filter(
    (r) => r.date >= start && r.date <= end && (tool === 'all' || r.tool === tool)
  )
}
export function summarize(rows: UsageRow[]) {
  const input = rows.reduce((n, r) => n + r.inputTokens, 0)
  const output = rows.reduce((n, r) => n + r.outputTokens, 0)
  const cached = rows.reduce((n, r) => n + r.cachedInputTokens, 0)
  return {
    input,
    output,
    cached,
    total: input + output,
    cacheRate: input ? (cached / input) * 100 : 0,
    sessions: new Set(rows.map((r) => r.sessionRef)).size,
    models: new Set(rows.map((r) => r.model)).size,
    activeDays: new Set(rows.map((r) => r.date)).size,
    projects: new Set(rows.map((r) => r.project)).size
  }
}
export function groupRows(rows: UsageRow[], key: 'model' | 'project' | 'tool') {
  const map = new Map<string, UsageRow[]>()
  for (const row of rows) {
    const list = map.get(row[key]) || []
    list.push(row)
    map.set(row[key], list)
  }
  return [...map]
    .map(([name, values]) => ({ name, ...summarize(values) }))
    .sort((a, b) => b.total - a.total)
}
export const palette = ['#287957', '#85b895', '#b4ca74', '#dfb861', '#7c94ae', '#c397ac', '#849373']

export const sourceLinks = [
  {
    name: 'OpenAI',
    category: '模型与 API',
    description: '模型发布、能力更新与 API 变更',
    url: 'https://openai.com/news/',
    color: '#287957',
    initials: 'O'
  },
  {
    name: 'Anthropic',
    category: '模型与 Agent',
    description: 'Claude 模型、研究与产品公告',
    url: 'https://www.anthropic.com/news',
    color: '#ad7863',
    initials: 'A'
  },
  {
    name: 'Google DeepMind',
    category: '模型与研究',
    description: 'Gemini 与前沿 AI 研究动态',
    url: 'https://deepmind.google/discover/blog/',
    color: '#5886b9',
    initials: 'G'
  },
  {
    name: 'DeepSeek',
    category: '开源与模型',
    description: '官方模型仓库与技术报告',
    url: 'https://github.com/deepseek-ai',
    color: '#6077bf',
    initials: 'D'
  },
  {
    name: 'Qwen',
    category: '开源与模型',
    description: '通义千问模型系列与发布说明',
    url: 'https://qwenlm.github.io/',
    color: '#8876b2',
    initials: 'Q'
  },
  {
    name: 'Hugging Face',
    category: '开源生态',
    description: '模型、数据集和社区技术文章',
    url: 'https://huggingface.co/blog',
    color: '#b48b35',
    initials: 'H'
  }
]
