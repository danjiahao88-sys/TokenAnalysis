import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { Ref } from 'vue'
import { NotebookRepository } from './notebook-db'
import { notebookSchema, notebookScope } from './notebook'
import type { Experiment, Practice } from './notebook'
import type { Snapshot } from './data'

export function useNotebook(snapshot: Ref<Snapshot>) {
  let repository: NotebookRepository | undefined
  const scope = computed(() => notebookScope(snapshot.value))
  const entries = ref<Practice[]>([]),
    experiments = ref<Experiment[]>([])
  const ready = ref(false),
    error = ref('')
  let generation = 0
  const getRepository = () => (repository ??= new NotebookRepository())
  async function load() {
    const run = ++generation,
      currentScope = scope.value
    ready.value = false
    entries.value = []
    experiments.value = []
    error.value = ''
    try {
      const result = await getRepository().load(currentScope)
      if (run !== generation) return
      entries.value = result.entries
      experiments.value = result.experiments
      ready.value = true
    } catch {
      if (run === generation)
        error.value =
          '浏览器本地数据库不可用。请允许站点存储或使用普通浏览窗口；未保存的表单内容会保留。'
    }
  }
  watch(scope, load, { immediate: true })
  onBeforeUnmount(() => {
    generation++
    void repository?.close()
  })
  async function saveEntry(entry: Practice) {
    const selected = scope.value
    await getRepository().saveEntry(selected, { ...entry, updatedAt: new Date().toISOString() })
    if (selected === scope.value) await load()
  }
  async function saveExperiment(experiment: Experiment) {
    const selected = scope.value
    await getRepository().saveExperiment(selected, {
      ...experiment,
      updatedAt: new Date().toISOString()
    })
    if (selected === scope.value) await load()
  }
  function backup() {
    return notebookSchema.parse({
      format: 'ai-footprint-notebook-v1',
      exportedAt: new Date().toISOString(),
      provenance: scope.value === 'demo' ? 'demo' : 'personal',
      entries: entries.value,
      experiments: experiments.value
    })
  }
  async function restore(text: string, expectedScope = scope.value) {
    if (expectedScope !== scope.value)
      throw new Error('数据来源已切换，请在当前档案中重新选择备份。')
    const backup = notebookSchema.parse(JSON.parse(text)),
      selected = scope.value
    const result = await getRepository().merge(selected, backup)
    if (selected === scope.value) await load()
    return result
  }
  return { entries, experiments, ready, error, scope, saveEntry, saveExperiment, backup, restore }
}
export type Notebook = ReturnType<typeof useNotebook>
