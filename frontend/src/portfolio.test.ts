import 'fake-indexeddb/auto'
import { openDB } from 'idb'
import { describe, expect, it } from 'vitest'
import { buildPortfolio } from './portfolio'
import { blankProfile } from './portfolio-profile'
import { blankPractice, blankExperiment, notebookSchema } from './notebook'
import { NotebookRepository } from './notebook-db'
import { snapshotSchema } from './data'
import demoJson from './demo.json'

describe('可审核的个人展示导出', () => {
  it('只导出明确勾选的未归档记录，不带项目、下一步和默认统计', () => {
    const selected = {
        ...blankPractice('2026-11-04'),
        title: '公开实践',
        summary: '公开的过程记录与验证',
        project: 'PRIVATE_PROJECT',
        nextStep: 'PRIVATE_NEXT'
      },
      hidden = { ...selected, id: 'hidden', summary: 'PRIVATE_HIDDEN' },
      archived = { ...selected, id: 'archived', archived: true, summary: 'PRIVATE_ARCHIVED' }
    const profile = { ...blankProfile(), name: '测试名片', entryIds: [selected.id, archived.id] }
    const output = buildPortfolio(
      profile,
      [selected, hidden, archived],
      [],
      snapshotSchema.parse(demoJson)
    )
    expect(output.entries).toBe(1)
    expect(output.html).toContain('公开的过程记录与验证')
    expect(output.html + output.markdown).not.toMatch(/PRIVATE_|AI 使用概览|Skills 探索/)
    expect(output.markdown).toContain('虚构示例')
    expect(
      buildPortfolio({ ...profile, entryIds: [] }, [selected], [], snapshotSchema.parse(demoJson))
        .entries
    ).toBe(0)
  })
  it('HTML转义、CSP与HTTPS白名单；未知指标不当作零或通过', () => {
    const experiment = blankExperiment('2026-11-04')
    experiment.title = '人工实验'
    experiment.task = '相同输入与验证步骤'
    experiment.criteria = '测试标准'
    experiment.trials.forEach((t) => (t.model = 'test-model'))
    const profile = {
      ...blankProfile(),
      name: '<script>alert(1)</script>',
      bio: '<img src=x onerror=alert(1)>',
      experimentIds: [experiment.id]
    }
    const output = buildPortfolio(profile, [], [experiment], snapshotSchema.parse(demoJson))
    expect(output.html).not.toContain('<script>')
    expect(output.html).not.toContain('<img')
    expect(output.html).toContain('&lt;script&gt;')
    expect(output.html).toContain("default-src 'none'")
    expect(output.markdown).toContain('总 Token 未记录')
    expect(output.markdown).toContain('未测试')
    expect(() =>
      buildPortfolio(
        { ...profile, repository: 'javascript:alert(1)' },
        [],
        [],
        snapshotSchema.parse(demoJson)
      )
    ).toThrow()
    expect(() =>
      buildPortfolio(
        { ...profile, repository: 'https://secret@example.com' },
        [],
        [],
        snapshotSchema.parse(demoJson)
      )
    ).toThrow()
  })
  it('从旧IndexedDB迁移保留记录，配置按来源隔离，旧备份不清空新配置', async () => {
    const name = crypto.randomUUID(),
      legacy = await openDB(name, 1, {
        upgrade(db) {
          db.createObjectStore('entries', { keyPath: ['scope', 'id'] }).createIndex(
            'scope',
            'scope'
          )
          db.createObjectStore('experiments', { keyPath: ['scope', 'id'] }).createIndex(
            'scope',
            'scope'
          )
          db.createObjectStore('metadata')
        }
      })
    const entry = {
      ...blankPractice('2026-11-04'),
      title: '旧记录',
      summary: '旧版本保存的真实实践过程'
    }
    await legacy.put('entries', { ...entry, scope: 'local' })
    legacy.close()
    const repo = new NotebookRepository(name),
      profile = {
        ...blankProfile(),
        name: '本人',
        entryIds: [entry.id],
        updatedAt: '2027-01-01T00:00:00Z'
      }
    expect((await repo.load('local')).entries).toEqual([entry])
    await repo.saveProfile('local', profile)
    expect((await repo.load('demo')).profile).toBe(null)
    const backup = notebookSchema.parse({
      format: 'ai-footprint-notebook-v1',
      provenance: 'personal',
      exportedAt: entry.updatedAt,
      entries: [],
      experiments: []
    })
    await repo.merge('local', backup)
    expect((await repo.load('local')).profile).toEqual(profile)
    await repo.merge('local', {
      ...backup,
      profile: { ...profile, name: '过时配置', updatedAt: '2026-01-01T00:00:00Z' }
    })
    expect((await repo.load('local')).profile?.name).toBe('本人')
    await repo.close()
    const reopened = new NotebookRepository(name)
    expect((await reopened.load('local')).profile).toEqual(profile)
    await reopened.close()
  })
})
