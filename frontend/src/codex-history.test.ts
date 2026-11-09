import { expect, it } from 'vitest'
import { historySchema } from './codex-history'
it('历史索引独立计数与来源校验，不推算输入输出', () => {
  const history = {
    status: 'ready',
    checkedAt: '2026-11-05T00:00:00Z',
    checkedSessions: 3,
    availableRollouts: 1,
    skippedRows: 0,
    truncated: false,
    groups: [{ model: 'deepseek-v4-flash', sessions: 2, indexTokens: 170, lastSeenAt: null }]
  }
  expect(historySchema.parse({ ...history, privatePath: 'SECRET' })).toEqual(history)
  expect(
    historySchema.safeParse({ ...history, groups: [{ ...history.groups[0], sessions: 3 }] }).success
  ).toBe(false)
  expect(
    historySchema.safeParse({ ...history, groups: [{ ...history.groups[0], indexTokens: -1 }] })
      .success
  ).toBe(false)
  expect(
    historySchema.safeParse({
      ...history,
      groups: [{ ...history.groups[0], indexTokens: Number.MAX_SAFE_INTEGER + 1 }]
    }).success
  ).toBe(false)
  expect(
    historySchema.safeParse({
      ...history,
      groups: [history.groups[0], history.groups[0]],
      checkedSessions: 5
    }).success
  ).toBe(false)
})
