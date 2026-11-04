import 'fake-indexeddb/auto'
import { describe, expect, it } from 'vitest'
import { NotebookRepository } from './notebook-db'
import {
  blankPractice,
  MAX_PRACTICES,
  blankExperiment,
  experimentSchema,
  experimentSummary,
  notebookSchema,
  practiceSchema,
  trialTotal
} from './notebook'

function entry() {
  return { ...blankPractice('2026-11-04'), title: '测试实践', summary: '记录实际验证过程与成果' }
}
describe('个人档案契约与持久化', () => {
  it('容量限制在事务写入前校验，防止备份无法再次导入', async () => {
    const repo = new NotebookRepository(crypto.randomUUID()),
      first = entry()
    const backup = notebookSchema.parse({
      format: 'ai-footprint-notebook-v1',
      provenance: 'personal',
      exportedAt: first.updatedAt,
      entries: Array.from({ length: MAX_PRACTICES }, (_, index) => ({ ...first, id: `e${index}` })),
      experiments: []
    })
    expect((await repo.merge('local', backup)).imported).toBe(MAX_PRACTICES)
    await expect(repo.saveEntry('local', entry())).rejects.toThrow('最多保存')
    await repo.saveEntry('local', { ...first, id: 'e0', title: '容量内仍可修改' })
    await expect(repo.merge('local', { ...backup, entries: [entry()] })).rejects.toThrow(
      '超过档案容量'
    )
    expect((await repo.load('local')).entries).toHaveLength(MAX_PRACTICES)
    await repo.close()
  })
  it('校验日历、链接、组数；未知值不冒充零或通过', () => {
    expect(practiceSchema.safeParse({ ...entry(), date: '2026-02-30' }).success).toBe(false)
    expect(practiceSchema.safeParse({ ...entry(), link: 'javascript:alert(1)' }).success).toBe(
      false
    )
    const e = blankExperiment('2026-11-04')
    e.title = '相同任务'
    e.task = '同一个输入与相同测试条件'
    e.criteria = '测试全部通过'
    e.trials.forEach((t) => {
      t.model = 'test-model'
    })
    expect(experimentSchema.safeParse(e).success).toBe(true)
    expect(trialTotal(e.trials[0]!)).toBe(null)
    expect(experimentSummary([e]).passRate).toBe(null)
    e.trials[0]!.verdict = 'pass'
    expect(experimentSummary([e])).toMatchObject({ tested: 1, passRate: 100 })
    expect(experimentSchema.safeParse({ ...e, trials: [e.trials[0]] }).success).toBe(false)
  })
  it('刷新后持久保存、来源隔离、归档可恢复、导入合并不覆盖较新版本', async () => {
    const name = crypto.randomUUID(),
      repo = new NotebookRepository(name),
      saved = entry()
    await repo.saveEntry('local', saved)
    await repo.close()
    const reopened = new NotebookRepository(name)
    expect((await reopened.load('local')).entries).toEqual([saved])
    expect((await reopened.load('public:other')).entries).toEqual([])
    const newer = { ...saved, archived: true, updatedAt: '2027-01-01T00:00:00Z' }
    await reopened.saveEntry('local', newer)
    const backup = notebookSchema.parse({
      format: 'ai-footprint-notebook-v1',
      provenance: 'personal',
      exportedAt: saved.updatedAt,
      entries: [saved],
      experiments: []
    })
    expect(await reopened.merge('local', backup)).toEqual({ imported: 0, skipped: 1 })
    expect((await reopened.load('local')).entries[0]?.archived).toBe(true)
    await reopened.saveEntry('local', { ...newer, archived: false })
    expect((await reopened.load('local')).entries[0]?.archived).toBe(false)
    await expect(reopened.merge('demo', backup)).rejects.toThrow('不能混合')
    const demo = await reopened.load('demo'),
      demoAgain = await reopened.load('demo')
    expect(demo.entries.length).toBeGreaterThan(0)
    expect(demoAgain).toEqual(demo)
    await expect(reopened.merge('local', { ...backup, provenance: 'demo' })).rejects.toThrow(
      '不能混合'
    )
    const invalid = { ...backup, entries: [entry(), { ...entry(), date: 'invalid' }] }
    await expect(reopened.merge('local', invalid)).rejects.toThrow()
    expect((await reopened.load('local')).entries).toHaveLength(1)
    await reopened.close()
  })
})
