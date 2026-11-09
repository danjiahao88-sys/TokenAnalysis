import { openDB } from 'idb'
import { profileSchema } from './portfolio-profile'
import type { PortfolioProfile } from './portfolio-profile'
import type { DBSchema, IDBPDatabase } from 'idb'
import { demoEntries, demoExperiments } from './notebook-demo'
import {
  experimentSchema,
  notebookSchema,
  practiceSchema,
  MAX_PRACTICES,
  MAX_EXPERIMENTS
} from './notebook'
import type { Experiment, NotebookBackup, Practice } from './notebook'

interface NotebookDB extends DBSchema {
  entries: {
    key: [string, string]
    value: Practice & { scope: string }
    indexes: { scope: string }
  }
  experiments: {
    key: [string, string]
    value: Experiment & { scope: string }
    indexes: { scope: string }
  }
  metadata: { key: string; value: boolean }
  profiles: { key: string; value: PortfolioProfile }
}
export class NotebookRepository {
  private connection: Promise<IDBPDatabase<NotebookDB>>
  constructor(name = 'ai-footprint-notebook') {
    this.connection = openDB<NotebookDB>(name, 2, {
      upgrade(db, oldVersion) {
        if (oldVersion < 1) {
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
        if (oldVersion < 2) db.createObjectStore('profiles')
      },
      blocking: () => {
        void this.connection.then((db) => db.close())
      }
    })
  }
  async load(scope: string) {
    const db = await this.connection
    const tx = db.transaction(['entries', 'experiments', 'metadata', 'profiles'], 'readwrite')
    if (scope === 'demo' && !(await tx.objectStore('metadata').get('demo-seeded-v1'))) {
      for (const entry of demoEntries) await tx.objectStore('entries').put({ ...entry, scope })
      for (const experiment of demoExperiments)
        await tx.objectStore('experiments').put({ ...experiment, scope })
      await tx.objectStore('metadata').put(true, 'demo-seeded-v1')
    }
    const entries = await tx.objectStore('entries').index('scope').getAll(scope)
    const experiments = await tx.objectStore('experiments').index('scope').getAll(scope)
    const profile = await tx.objectStore('profiles').get(scope)
    await tx.done
    return {
      entries: entries.map((e) => practiceSchema.parse(e)),
      experiments: experiments.map((e) => experimentSchema.parse(e)),
      profile: profile ? profileSchema.parse(profile) : null
    }
  }
  async saveEntry(scope: string, entry: Practice) {
    const parsed = practiceSchema.parse(entry)
    const db = await this.connection
    const tx = db.transaction('entries', 'readwrite'),
      store = tx.objectStore('entries')
    if (
      !(await store.get([scope, parsed.id])) &&
      (await store.index('scope').count(scope)) >= MAX_PRACTICES
    ) {
      await tx.done
      throw new Error(`当前档案最多保存 ${MAX_PRACTICES} 条实践，归档记录也计入。`)
    }
    await store.put({ ...parsed, scope })
    await tx.done
  }
  async saveProfile(scope: string, profile: PortfolioProfile) {
    const parsed = profileSchema.parse(profile)
    await (await this.connection).put('profiles', parsed, scope)
  }
  async saveExperiment(scope: string, experiment: Experiment) {
    const parsed = experimentSchema.parse(experiment)
    const db = await this.connection
    const tx = db.transaction('experiments', 'readwrite'),
      store = tx.objectStore('experiments')
    if (
      !(await store.get([scope, parsed.id])) &&
      (await store.index('scope').count(scope)) >= MAX_EXPERIMENTS
    ) {
      await tx.done
      throw new Error(`当前档案最多保存 ${MAX_EXPERIMENTS} 个实验，归档记录也计入。`)
    }
    await store.put({ ...parsed, scope })
    await tx.done
  }
  async merge(scope: string, input: NotebookBackup) {
    const backup = notebookSchema.parse(input)
    if ((scope === 'demo') !== (backup.provenance === 'demo'))
      throw new Error('演示档案与个人档案不能混合，请切换到对应的数据来源后导入。')
    const db = await this.connection
    const tx = db.transaction(['entries', 'experiments', 'profiles'], 'readwrite')
    const entryIds = await tx.objectStore('entries').index('scope').getAllKeys(scope)
    const experimentIds = await tx.objectStore('experiments').index('scope').getAllKeys(scope)
    if (
      new Set([...entryIds.map((key) => key[1]), ...backup.entries.map((e) => e.id)]).size >
        MAX_PRACTICES ||
      new Set([...experimentIds.map((key) => key[1]), ...backup.experiments.map((e) => e.id)])
        .size > MAX_EXPERIMENTS
    ) {
      await tx.done
      throw new Error('合并后超过档案容量（500 条实践 / 300 个实验），原有记录已保留。')
    }
    let imported = 0,
      skipped = 0
    for (const entry of backup.entries) {
      const old = await tx.objectStore('entries').get([scope, entry.id])
      if (old && Date.parse(old.updatedAt) >= Date.parse(entry.updatedAt)) {
        skipped++
        continue
      }
      await tx.objectStore('entries').put({ ...entry, scope })
      imported++
    }
    for (const experiment of backup.experiments) {
      const old = await tx.objectStore('experiments').get([scope, experiment.id])
      if (old && Date.parse(old.updatedAt) >= Date.parse(experiment.updatedAt)) {
        skipped++
        continue
      }
      await tx.objectStore('experiments').put({ ...experiment, scope })
      imported++
    }
    if (backup.profile) {
      const old = await tx.objectStore('profiles').get(scope)
      if (!old || Date.parse(old.updatedAt) < Date.parse(backup.profile.updatedAt)) {
        await tx.objectStore('profiles').put(backup.profile, scope)
        imported++
      } else skipped++
    }
    await tx.done
    return { imported, skipped }
  }
  async close() {
    ;(await this.connection).close()
  }
}
