import { z } from 'zod'
import { profileSchema } from './portfolio-profile'
import type { Snapshot } from './data'

export const calendarDate = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/)
  .refine((value) => {
    const date = new Date(`${value}T12:00:00Z`)
    return !Number.isNaN(+date) && date.toISOString().slice(0, 10) === value
  }, '请输入有效日期')
const text = (max: number) => z.string().trim().max(max, `最多 ${max} 字`)
const id = z.string().min(1).max(100)
const timestamp = z.iso.datetime()
export const entryCategories = ['项目实践', '学习笔记', '工具探索', '问题复盘', '开源贡献'] as const
export const practiceSchema = z.object({
  id,
  date: calendarDate,
  title: text(100).min(2, '标题至少 2 字'),
  category: z.enum(entryCategories),
  project: text(100),
  tool: text(60),
  model: text(100),
  summary: text(3000).min(8, '请用至少 8 字记录实践过程'),
  outcome: text(1500),
  nextStep: text(1000),
  link: text(500).refine(
    (value) =>
      !value ||
      (() => {
        try {
          return new URL(value).protocol === 'https:'
        } catch {
          return false
        }
      })(),
    '请使用完整的 HTTPS 链接'
  ),
  favorite: z.boolean(),
  archived: z.boolean(),
  createdAt: timestamp,
  updatedAt: timestamp
})
const metric = z.number().int().nonnegative().max(1e12).nullable()
export const trialSchema = z.object({
  id,
  model: text(100).min(1, '填写模型与版本'),
  tool: text(60).min(1, '填写使用工具'),
  inputTokens: metric,
  outputTokens: metric,
  durationSeconds: z.number().nonnegative().max(604800).nullable(),
  verdict: z.enum(['pass', 'fail', 'untested']),
  score: z.number().int().min(1).max(5).nullable(),
  notes: text(1500)
})
export const experimentSchema = z
  .object({
    id,
    date: calendarDate,
    title: text(100).min(2, '实验标题至少 2 字'),
    task: text(3000).min(8, '请用至少 8 字描述相同的测试任务'),
    criteria: text(1500).min(4, '请先写清验证标准'),
    trials: z.array(trialSchema).min(2, '至少比较两组结果').max(6, '最多比较六组结果'),
    archived: z.boolean(),
    createdAt: timestamp,
    updatedAt: timestamp
  })
  .superRefine((value, ctx) => {
    if (new Set(value.trials.map((t) => t.id)).size !== value.trials.length)
      ctx.addIssue({ code: 'custom', path: ['trials'], message: '实验组身份重复' })
  })
export type Practice = z.infer<typeof practiceSchema>
export type Trial = z.infer<typeof trialSchema>
export type Experiment = z.infer<typeof experimentSchema>
export const MAX_PRACTICES = 500
export const MAX_EXPERIMENTS = 300
export const MAX_BACKUP_BYTES = 32 * 1024 * 1024
export const notebookSchema = z
  .object({
    format: z.literal('ai-footprint-notebook-v1'),
    exportedAt: timestamp,
    provenance: z.enum(['demo', 'personal']),
    entries: z.array(practiceSchema).max(MAX_PRACTICES),
    experiments: z.array(experimentSchema).max(MAX_EXPERIMENTS),
    profile: profileSchema.optional()
  })
  .superRefine((value, ctx) => {
    if (
      new Set(value.entries.map((e) => e.id)).size !== value.entries.length ||
      new Set(value.experiments.map((e) => e.id)).size !== value.experiments.length
    )
      ctx.addIssue({ code: 'custom', message: '备份存在重复身份' })
  })
export type NotebookBackup = z.infer<typeof notebookSchema>
export function notebookScope(snapshot: Snapshot): string {
  return snapshot.mode === 'public' ? `public:${snapshot.generatedAt}` : snapshot.mode
}
export function today(timezone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(new Date())
  const part = (type: string) => parts.find((p) => p.type === type)?.value
  return `${part('year')}-${part('month')}-${part('day')}`
}
export function blankPractice(date: string): Practice {
  const at = new Date().toISOString()
  return {
    id: crypto.randomUUID(),
    date,
    title: '',
    category: '项目实践',
    project: '',
    tool: '',
    model: '',
    summary: '',
    outcome: '',
    nextStep: '',
    link: '',
    favorite: false,
    archived: false,
    createdAt: at,
    updatedAt: at
  }
}
export function blankTrial(): Trial {
  return {
    id: crypto.randomUUID(),
    model: '',
    tool: 'Codex',
    inputTokens: null,
    outputTokens: null,
    durationSeconds: null,
    verdict: 'untested',
    score: null,
    notes: ''
  }
}
export function blankExperiment(date: string): Experiment {
  const at = new Date().toISOString()
  return {
    id: crypto.randomUUID(),
    date,
    title: '',
    task: '',
    criteria: '',
    trials: [blankTrial(), blankTrial()],
    archived: false,
    createdAt: at,
    updatedAt: at
  }
}
export function trialTotal(trial: Trial): number | null {
  return trial.inputTokens === null || trial.outputTokens === null
    ? null
    : trial.inputTokens + trial.outputTokens
}
export function experimentSummary(experiments: Experiment[]) {
  const trials = experiments.filter((e) => !e.archived).flatMap((e) => e.trials)
  const tested = trials.filter((t) => t.verdict !== 'untested')
  return {
    count: experiments.filter((e) => !e.archived).length,
    trials: trials.length,
    tested: tested.length,
    passed: tested.filter((t) => t.verdict === 'pass').length,
    passRate: tested.length
      ? (tested.filter((t) => t.verdict === 'pass').length / tested.length) * 100
      : null
  }
}
export function validationErrors(error: z.ZodError): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const issue of error.issues) errors[issue.path.join('.')] ??= issue.message
  return errors
}
export function downloadFile(value: string, filename: string, type = 'application/json') {
  const url = URL.createObjectURL(new Blob([value], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
