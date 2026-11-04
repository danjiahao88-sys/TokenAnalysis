import { describe, expect, it } from 'vitest'
import {
  groupRows,
  lastDate,
  parseImport,
  shiftDate,
  snapshotSchema,
  summarize,
  windowRows
} from './data'
import demo from './demo.json'

describe('snapshot contract and accounting', () => {
  it('空快照的截止日期仍使用统计时区', () => {
    const data = snapshotSchema.parse({
      ...demo,
      usage: [],
      generatedAt: '2026-11-03T23:59:00Z',
      timezone: 'Asia/Shanghai'
    })
    expect(lastDate(data)).toBe('2026-11-04')
  })
  it('validates the public demo and counts cache only once', () => {
    const data = snapshotSchema.parse(demo)
    const stats = summarize(data.usage)
    expect(stats.total).toBe(stats.input + stats.output)
    expect(stats.cached).toBeLessThanOrEqual(stats.input)
    expect(groupRows(data.usage, 'model').reduce((n, r) => n + r.total, 0)).toBe(stats.total)
  })
  it('rejects invalid totals, calendar dates, tools and unsafe URLs', () => {
    const copy = () => structuredClone(demo)
    let d = copy()
    d.usage[0]!.totalTokens++
    expect(() => snapshotSchema.parse(d)).toThrow()
    d = copy()
    d.usage[0]!.date = '2026-02-31'
    expect(() => snapshotSchema.parse(d)).toThrow()
    d = copy()
    d.usage[0]!.tool = 'unknown-tool'
    expect(() => snapshotSchema.parse(d)).toThrow()
    d = copy()
    d.tools[0]!.url = 'javascript:alert(1)'
    expect(() => snapshotSchema.parse(d)).toThrow()
    d = copy()
    d.timezone = 'invalid-timezone'
    expect(() => snapshotSchema.parse(d)).toThrow()
    d = copy()
    d.skills.push(d.skills[0]!)
    expect(() => snapshotSchema.parse(d)).toThrow()
  })
  it('rejects private snapshots and strips fields outside the allowlist', () => {
    expect(() => parseImport(JSON.stringify({ ...demo, mode: 'local' }))).toThrow()
    expect(parseImport(JSON.stringify({ ...demo, secret: 'DO_NOT_KEEP' }))).not.toHaveProperty(
      'secret'
    )
  })
  it('filters exact adjacent date windows with a stable session count', () => {
    const data = snapshotSchema.parse(demo)
    const now = windowRows(data, 7),
      previous = windowRows(data, 7, 'all', true)
    expect(now.length).toBeGreaterThan(0)
    expect(now.every((r) => !previous.some((p) => p.date === r.date))).toBe(true)
    expect(windowRows(data, 30, 'codex').every((r) => r.tool === 'codex')).toBe(true)
    expect(shiftDate('2026-03-01', -1)).toBe('2026-02-28')
  })
})
