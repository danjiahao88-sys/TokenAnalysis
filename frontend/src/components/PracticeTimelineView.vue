<script setup lang="ts">
import { computed, nextTick, ref, toRaw } from 'vue'
import type { Notebook } from '../useNotebook'
import {
  blankPractice,
  entryCategories,
  practiceSchema,
  today,
  validationErrors
} from '../notebook'
import type { Practice } from '../notebook'
import NotebookField from './NotebookField.vue'
const props = defineProps<{ notebook: Notebook; timezone: string }>()
const query = ref(''),
  category = ref('全部'),
  archived = ref(false),
  favorites = ref(false)
const draft = ref<Practice>(),
  errors = ref<Record<string, string>>({}),
  failure = ref(''),
  message = ref(''),
  saving = ref(false),
  summary = ref<HTMLElement>()
const fields = [
  { key: 'title', label: '实践标题', required: true },
  { key: 'project', label: '关联项目' },
  { key: 'tool', label: 'AI 工具' },
  { key: 'model', label: '模型与版本' },
  { key: 'summary', label: '实践过程', multiline: true, required: true },
  { key: 'outcome', label: '成果与证据', multiline: true },
  { key: 'nextStep', label: '下一步', multiline: true },
  { key: 'link', label: '作品或证据链接（HTTPS）' }
] as const
const records = computed(() =>
  props.notebook.entries.value
    .filter(
      (e) =>
        e.archived === archived.value &&
        (!favorites.value || e.favorite) &&
        (category.value === '全部' || e.category === category.value) &&
        `${e.title} ${e.summary} ${e.project} ${e.model} ${e.tool}`
          .toLowerCase()
          .includes(query.value.toLowerCase())
    )
    .sort((a, b) => b.date.localeCompare(a.date) || b.updatedAt.localeCompare(a.updatedAt))
)
function edit(entry?: Practice) {
  draft.value = entry ? structuredClone(toRaw(entry)) : blankPractice(today(props.timezone))
  errors.value = {}
  failure.value = ''
  message.value = ''
  void nextTick(() => document.getElementById('practice-title')?.focus())
}
function focusField(key: string) {
  document.getElementById(`practice-${key}`)?.focus()
}
async function focusError() {
  await nextTick()
  summary.value?.focus()
}
async function save() {
  if (!draft.value) return
  errors.value = {}
  failure.value = ''
  const result = practiceSchema.safeParse(draft.value)
  if (!result.success) {
    errors.value = validationErrors(result.error)
    await focusError()
    return
  }
  saving.value = true
  try {
    await props.notebook.saveEntry(result.data)
    draft.value = undefined
    message.value = '实践已保存到当前浏览器。'
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
async function toggle(entry: Practice, key: 'favorite' | 'archived') {
  try {
    await props.notebook.saveEntry({ ...entry, [key]: !entry[key] })
    message.value =
      key === 'archived'
        ? entry.archived
          ? '已恢复实践。'
          : '已归档，可在归档筛选中恢复。'
        : '收藏状态已更新。'
  } catch {
    message.value = '更新失败，记录保持原样。'
  }
}
</script>
<template>
  <div class="notebook-page">
    <section class="report-hero panel">
      <div>
        <span class="eyebrow">BUILD · LEARN · REFLECT</span>
        <h2>记录你与 AI 一起做过的事。</h2>
        <p>把尝试、成果与复盘串成一条有证据的成长时间线。</p>
      </div>
      <button class="button primary" :disabled="!notebook.ready.value || !!draft" @click="edit()">
        新增实践
      </button>
    </section>
    <p v-if="message" role="status">{{ message }}</p>
    <form v-if="draft" class="panel notebook-card notebook-form" novalidate @submit.prevent="save">
      <h3>
        {{ notebook.entries.value.some((e) => e.id === draft?.id) ? '编辑实践' : '新的一次探索' }}
      </h3>
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
            <a :href="`#practice-${key}`" @click.prevent="focusField(String(key))">{{ error }}</a>
          </li>
        </ul>
      </div>
      <div class="notebook-form-grid">
        <NotebookField
          id="practice-date"
          v-model="draft.date"
          label="日期"
          type="date"
          required
          :error="errors.date"
        />
        <div class="notebook-field">
          <label for="practice-category">实践分类</label
          ><select id="practice-category" v-model="draft.category">
            <option v-for="item in entryCategories" :key="item">{{ item }}</option>
          </select>
        </div>
        <NotebookField
          v-for="field in fields"
          :id="`practice-${field.key}`"
          :key="field.key"
          v-model="draft[field.key]"
          :label="field.label"
          :multiline="'multiline' in field"
          :required="'required' in field"
          :error="errors[field.key]"
          :class="{ 'wide-field': 'multiline' in field || field.key === 'link' }"
        />
      </div>
      <div class="notebook-actions">
        <button class="button primary" :disabled="saving">
          {{ saving ? '正在保存…' : '保存实践' }}</button
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
      <label
        >搜索实践<input v-model="query" type="search" placeholder="标题、项目、工具或模型" /></label
      ><label
        >分类<select v-model="category">
          <option>全部</option>
          <option v-for="item in entryCategories" :key="item">{{ item }}</option>
        </select></label
      ><label class="check-label"><input v-model="favorites" type="checkbox" />仅收藏</label
      ><label class="check-label"><input v-model="archived" type="checkbox" />归档记录</label
      ><span>{{ records.length }} 条记录</span>
    </div>
    <div class="practice-timeline">
      <article v-for="entry in records" :key="entry.id" class="panel notebook-card practice-entry">
        <div class="entry-date">
          {{ entry.date }}<span class="outline-badge">{{ entry.category }}</span>
        </div>
        <h3>{{ entry.title }}</h3>
        <p class="muted">
          {{
            [entry.project, entry.tool, entry.model].filter(Boolean).join(' / ') ||
            '未关联工具或项目'
          }}
        </p>
        <p class="preserve-lines">{{ entry.summary }}</p>
        <p v-if="entry.outcome" class="preserve-lines"><strong>成果</strong> {{ entry.outcome }}</p>
        <p v-if="entry.nextStep" class="preserve-lines">
          <strong>下一步</strong> {{ entry.nextStep }}
        </p>
        <a
          v-if="entry.link"
          :href="entry.link"
          target="_blank"
          rel="noopener noreferrer"
          class="text-button"
          >查看作品与证据 ↗</a
        >
        <div class="notebook-actions">
          <button class="button secondary" :disabled="!!draft" @click="edit(entry)">编辑实践</button
          ><button
            class="button secondary"
            :aria-pressed="entry.favorite"
            @click="toggle(entry, 'favorite')"
          >
            {{ entry.favorite ? '已收藏' : '收藏' }}</button
          ><button class="button secondary" @click="toggle(entry, 'archived')">
            {{ entry.archived ? '恢复实践' : '归档实践' }}
          </button>
        </div>
      </article>
    </div>
    <p v-if="!records.length" class="panel empty-inline">
      {{
        notebook.ready.value
          ? '这里还没有匹配的实践。记录一次尝试，让成长留下痕迹。'
          : '正在读取本地档案…'
      }}
    </p>
  </div>
</template>
