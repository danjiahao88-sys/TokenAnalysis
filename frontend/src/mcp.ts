import { z } from 'zod'
export const inventorySchema = z
  .object({
    checkedAt: z.iso.datetime(),
    sources: z
      .array(
        z.object({
          id: z.string().max(80),
          label: z.string().max(100),
          status: z.enum(['ready', 'missing', 'invalid', 'unreadable']),
          count: z.number().int().min(0).max(200)
        })
      )
      .max(10),
    servers: z
      .array(
        z.object({
          id: z.string().max(80),
          name: z.string().max(100),
          tool: z.enum(['codex', 'claude-code']),
          source: z.string().max(80),
          transport: z.enum(['stdio', 'http', 'sse', 'unknown']),
          enabled: z.boolean()
        })
      )
      .max(400)
  })
  .superRefine((v, ctx) => {
    if (
      new Set(v.sources.map((s) => s.id)).size !== v.sources.length ||
      new Set(v.servers.map((s) => s.id)).size !== v.servers.length ||
      v.servers.some((s) => !v.sources.some((source) => source.id === s.source))
    )
      ctx.addIssue({ code: 'custom', message: '配置来源身份无效' })
  })
export type McpInventory = z.infer<typeof inventorySchema>
export const demoInventory: McpInventory = {
  checkedAt: '2026-11-04T12:00:00Z',
  sources: [
    { id: 'example-codex', label: '虚构 Codex 用户配置', status: 'ready', count: 2 },
    { id: 'example-claude', label: '虚构 Claude Code 用户配置', status: 'missing', count: 0 }
  ],
  servers: [
    {
      id: 'example-1',
      name: 'example-docs',
      tool: 'codex',
      source: 'example-codex',
      transport: 'http',
      enabled: true
    },
    {
      id: 'example-2',
      name: 'example-workspace',
      tool: 'codex',
      source: 'example-codex',
      transport: 'stdio',
      enabled: false
    }
  ]
}
