import { z } from 'zod'

export function safeHttps(value: string): boolean {
  try {
    const u = new URL(value)
    return u.protocol === 'https:' && !u.username && !u.password
  } catch {
    return false
  }
}
export const profileSchema = z.object({
  name: z.string().trim().min(1, '请填写展示名称').max(80),
  role: z.string().trim().max(120),
  bio: z.string().trim().max(1500),
  repository: z
    .string()
    .trim()
    .max(500)
    .refine((v) => !v || safeHttps(v), '请使用不含账号密码的 HTTPS 链接'),
  entryIds: z.array(z.string().min(1).max(100)).max(500),
  experimentIds: z.array(z.string().min(1).max(100)).max(300),
  showUsage: z.boolean(),
  showSkills: z.boolean(),
  updatedAt: z.iso.datetime()
})
export type PortfolioProfile = z.infer<typeof profileSchema>
export function blankProfile(): PortfolioProfile {
  return {
    name: '',
    role: '',
    bio: '',
    repository: '',
    entryIds: [],
    experimentIds: [],
    showUsage: false,
    showSkills: false,
    updatedAt: new Date().toISOString()
  }
}
