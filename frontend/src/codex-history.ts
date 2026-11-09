import { z } from 'zod'
export const historySchema = z
  .object({
    status: z.enum(['ready', 'missing', 'unreadable', 'incompatible']),
    checkedAt: z.iso.datetime(),
    checkedSessions: z.number().int().min(0).max(1000),
    availableRollouts: z.number().int().min(0).max(1000),
    skippedRows: z.number().int().min(0).max(1000),
    truncated: z.boolean(),
    groups: z
      .array(
        z.object({
          model: z.string().min(1).max(100),
          sessions: z.number().int().min(1).max(1000),
          indexTokens: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
          lastSeenAt: z.iso.datetime().nullable()
        })
      )
      .max(1000)
  })
  .superRefine((v, ctx) => {
    if (
      new Set(v.groups.map((g) => g.model)).size !== v.groups.length ||
      v.groups.reduce((n, g) => n + g.sessions, 0) + v.availableRollouts + v.skippedRows !==
        v.checkedSessions
    )
      ctx.addIssue({ code: 'custom', message: '历史索引来源计数不一致' })
  })
export type CodexHistory = z.infer<typeof historySchema>
