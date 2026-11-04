<script setup lang="ts">
import { computed, nextTick, ref, toRaw } from 'vue'
import type { Notebook } from '../useNotebook'
import {
  blankExperiment,
  blankTrial,
  experimentSchema,
  experimentSummary,
  today,
  trialTotal,
  validationErrors
} from '../notebook'
import type { Experiment } from '../notebook'
import NotebookField from './NotebookField.vue'
const props = defineProps<{ notebook: Notebook; timezone: string }>()
const draft = ref<Experiment>(),
  query = ref(''),
  archived = ref(false),
  errors = ref<Record<string, string>>({}),
  failure = ref(''),
  message = ref(''),
  saving = ref(false),
  summary = ref<HTMLElement>()
const records = computed(() =>
  props.notebook.experiments.value
    .filter(
      (e) =>
        e.archived === archived.value &&
        `${e.title} ${e.task} ${e.trials.map((t) => t.model).join(' ')}`
          .toLowerCase()
          .includes(query.value.toLowerCase())
    )
    .sort((a, b) => b.date.localeCompare(a.date))
)
const stats = computed(() => experimentSummary(props.notebook.experiments.value))
const verdicts = { pass: '通过', fail: '未通过', untested: '未验证' }
const metrics = [
  { key: 'inputTokens', label: '输入 Token' },
  { key: 'outputTokens', label: '输出 Token' },
  { key: 'durationSeconds', label: '耗时（秒）' },
  { key: 'score', label: '人工评分（1–5）' }
] as const
function edit(record?: Experiment) {
  draft.value = record ? structuredClone(toRaw(record)) : blankExperiment(today(props.timezone))
  errors.value = {}
  failure.value = ''
  message.value = ''
  void nextTick(() => document.getElementById('experiment-title')?.focus())
}
function focusField(key: string) {
  document.getElementById(`experiment-${key.replaceAll('.', '-')}`)?.focus()
}
async function focusError() {
  await nextTick()
  summary.value?.focus()
}
async function save() {
  if (!draft.value) return
  errors.value = {}
  failure.value = ''
  const result = experimentSchema.safeParse(draft.value)
  if (!result.success) {
    errors.value = validationErrors(result.error)
    await focusError()
    return
  }
  saving.value = true
  try {
    await props.notebook.saveExperiment(result.data)
    draft.value = undefined
    message.value = '模型实验已保存。所有结果均为人工记录。'
  } catch (error) {
    failure.value =
      error instanceof Error && error.message.includes('最多保存')
        ? error.message
        : '保存失败，内容仍在表单中。请检查站点存储。'
    await focusError()
  } finally {
    saving.value = false
  }
}
async function archive(record: Experiment) {
  try {
    await props.notebook.saveExperiment({ ...record, archived: !record.archived })
    message.value = record.archived ? '已恢复实验。' : '已归档，可在归档筛选中恢复。'
  } catch {
    message.value = '更新失败，原记录已保留。'
  }
}
</script>
<template>
  <div class="notebook-page">
    <section class="report-hero panel">
      <div>
        <span class="eyebrow">MODEL EXPERIMENT LAB</span>
        <h2>同一道题，比较不同的解法。</h2>
        <p>手工记录相同任务的用量、耗时和验证证据。这里不会自动运行模型。</p>
      </div>
      <button class="button primary" :disabled="!notebook.ready.value || !!draft" @click="edit()">
        新增实验
      </button>
    </section>
    <p v-if="message" role="status">{{ message }}</p>
    <div class="notebook-metrics">
      <article class="panel">
        <span>已记录实验</span><strong>{{ stats.count }}</strong
        ><small>归档记录不计入</small>
      </article>
      <article class="panel">
        <span>实验组 / 已验证</span><strong>{{ stats.trials }} / {{ stats.tested }}</strong
        ><small>未验证不参与通过率</small>
      </article>
      <article class="panel">
        <span>人工验收通过率</span
        ><strong>{{ stats.passRate === null ? '—' : `${stats.passRate.toFixed(0)}%` }}</strong
        ><small>只代表你记录的样本</small>
      </article>
    </div>
    <form v-if="draft" class="panel notebook-card notebook-form" novalidate @submit.prevent="save">
      <h3>实验设置</h3>
      <div
        v-if="Object.keys(errors).length || failure"
        ref="summary"
        role="alert"
        tabindex="-1"
        class="form-error-summary"
      >
        <strong>{{ failure || '请检查以下内容，尚未保存：' }}</strong>
        <ul>
          <li v-for="(error, key) in errors" :key="key">
            <a
              :href="`#experiment-${String(key).replaceAll('.', '-')}`"
              @click.prevent="focusField(String(key))"
              >{{ error }}</a
            >
          </li>
        </ul>
      </div>
      <div class="notebook-form-grid">
        <NotebookField
          id="experiment-title"
          v-model="draft.title"
          label="实验标题"
          required
          :error="errors.title"
        /><NotebookField
          id="experiment-date"
          v-model="draft.date"
          label="日期"
          type="date"
          required
          :error="errors.date"
        /><NotebookField
          id="experiment-task"
          v-model="draft.task"
          label="相同测试任务与条件"
          multiline
          required
          :error="errors.task"
          class="wide-field"
        /><NotebookField
          id="experiment-criteria"
          v-model="draft.criteria"
          label="验收标准与验证方法"
          multiline
          required
          :error="errors.criteria"
          class="wide-field"
        />
      </div>
      <div id="experiment-trials" class="trial-edit-grid">
        <fieldset v-for="(trial, index) in draft.trials" :key="trial.id" class="trial-editor">
          <legend>实验组 {{ index + 1 }}</legend>
          <NotebookField
            :id="`experiment-trials-${index}-model`"
            v-model="trial.model"
            label="模型与确切版本"
            required
            :error="errors[`trials.${index}.model`]"
          /><NotebookField
            :id="`experiment-trials-${index}-tool`"
            v-model="trial.tool"
            label="使用工具"
            required
            :error="errors[`trials.${index}.tool`]"
          />
          <p class="muted">未知指标留空；输入含缓存，输出含推理。</p>
          <NotebookField
            v-for="metric in metrics"
            :id="`experiment-trials-${index}-${metric.key}`"
            :key="metric.key"
            v-model="trial[metric.key]"
            :label="metric.label"
            type="number"
            :error="errors[`trials.${index}.${metric.key}`]"
          />
          <div class="notebook-field">
            <label :for="`verdict-${index}`">人工验收结果</label
            ><select :id="`verdict-${index}`" v-model="trial.verdict">
              <option v-for="(label, value) in verdicts" :key="value" :value="value">
                {{ label }}
              </option>
            </select>
          </div>
          <NotebookField
            :id="`experiment-trials-${index}-notes`"
            v-model="trial.notes"
            label="验证证据与备注"
            multiline
            :error="errors[`trials.${index}.notes`]"
          /><button
            type="button"
            class="button secondary"
            :disabled="draft.trials.length <= 2"
            @click="draft.trials.splice(index, 1)"
          >
            移除此组
          </button>
        </fieldset>
      </div>
      <p v-if="errors.trials" class="form-error">{{ errors.trials }}</p>
      <div class="notebook-actions">
        <button
          type="button"
          class="button secondary"
          :disabled="draft.trials.length >= 6"
          @click="draft.trials.push(blankTrial())"
        >
          增加实验组（最多 6 组）</button
        ><button class="button primary" :disabled="saving">
          {{ saving ? '正在保存…' : '保存实验' }}</button
        ><button
          type="button"
          class="button secondary"
          :disabled="saving"
          @click="draft = undefined"
        >
          取消编辑
        </button>
      </div>
    </form>
    <div class="notebook-filters">
      <label>搜索实验<input v-model="query" type="search" placeholder="标题、任务或模型" /></label
      ><label class="check-label"><input v-model="archived" type="checkbox" />归档记录</label>
    </div>
    <article v-for="record in records" :key="record.id" class="panel notebook-card">
      <small class="muted">{{ record.date }} · 人工记录</small>
      <h3>{{ record.title }}</h3>
      <p class="preserve-lines">{{ record.task }}</p>
      <p class="preserve-lines"><strong>验收标准</strong> {{ record.criteria }}</p>
      <div class="experiment-table">
        <table>
          <caption class="sr-only">
            {{
              record.title
            }}的实验组比较
          </caption>
          <thead>
            <tr>
              <th>模型 / 工具</th>
              <th>总 Token</th>
              <th>耗时</th>
              <th>验收</th>
              <th>评分</th>
              <th>证据与备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="trial in record.trials" :key="trial.id">
              <td>
                <strong>{{ trial.model }}</strong
                ><br /><small>{{ trial.tool }}</small>
              </td>
              <td>{{ trialTotal(trial)?.toLocaleString() ?? '未知' }}</td>
              <td>{{ trial.durationSeconds === null ? '未知' : `${trial.durationSeconds} 秒` }}</td>
              <td>{{ verdicts[trial.verdict] }}</td>
              <td>{{ trial.score === null ? '未评价' : `${trial.score} / 5` }}</td>
              <td class="preserve-lines">{{ trial.notes || '未记录' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="notebook-actions">
        <button class="button secondary" :disabled="!!draft" @click="edit(record)">编辑实验</button
        ><button class="button secondary" @click="archive(record)">
          {{ record.archived ? '恢复实验' : '归档实验' }}
        </button>
      </div>
    </article>
    <p v-if="!records.length" class="panel empty-inline">
      {{
        notebook.ready.value
          ? '还没有匹配的实验。先定义一项任务和验收标准，再记录两组结果。'
          : '正在读取本地档案…'
      }}
    </p>
  </div>
</template>
