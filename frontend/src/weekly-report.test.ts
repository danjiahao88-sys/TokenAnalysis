import { expect, it } from 'vitest'
import { snapshotSchema } from './data'
import demo from './demo.json'
import { monday, reportMarkdown, weeklyReport } from './weekly-report'
import { blankPractice } from './notebook'
it('跨年周一、部分周等长环比、独立会话与归档过滤', () => {
  expect(monday('2027-01-03')).toBe('2026-12-28')
  const base = snapshotSchema.parse(demo),
    row = base.usage[0]!
  base.usage = ['2026-11-02', '2026-11-04', '2026-10-26', '2026-10-28', '2026-10-30'].map(
    (date) => ({
      ...row,
      date,
      sessionRef: 'same',
      inputTokens: 100,
      outputTokens: 20,
      cachedInputTokens: 30,
      reasoningOutputTokens: 0,
      totalTokens: 120
    })
  )
  const entry = {
    ...blankPractice('2026-11-03'),
    title: '验证<测试>',
    summary: '手工填写的实践测试内容'
  }
  const report = weeklyReport(
    base,
    '2026-11-02',
    [entry, { ...entry, id: 'archived', archived: true }],
    []
  )
  expect(report).toMatchObject({
    end: '2026-11-04',
    partial: true,
    delta: 0,
    stats: { total: 240, cached: 60, sessions: 1, activeDays: 2 }
  })
  expect(report.entries).toHaveLength(1)
  const markdown = reportMarkdown(base, report)
  expect(markdown).toContain('虚构演示')
  expect(markdown).toContain('上周对应天数')
  expect(markdown).toContain('验证\\<测试\\>')
  const empty = weeklyReport(base, '2026-10-19', [], [])
  expect(empty.delta).toBe(null)
  expect(empty.stats.total).toBe(0)
})
