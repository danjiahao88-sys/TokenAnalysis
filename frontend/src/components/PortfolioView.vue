<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Notebook } from '../useNotebook'
import type { Snapshot } from '../data'
import { blankProfile, profileSchema } from '../portfolio-profile'
import { buildPortfolio } from '../portfolio'
import { downloadFile, validationErrors } from '../notebook'
import NotebookField from './NotebookField.vue'
const props = defineProps<{ notebook: Notebook; snapshot: Snapshot }>()
const draft = ref(blankProfile()),
  errors = ref<Record<string, string>>({}),
  message = ref(''),
  failure = ref(''),
  saving = ref(false)
const fields = [
  { key: 'name', label: '展示名称', required: true },
  { key: 'role', label: '个人方向 / 求职目标' },
  { key: 'bio', label: 'AI 兴趣与实践简介', multiline: true },
  { key: 'repository', label: '项目或个人主页（HTTPS）' }
] as const
watch(
  () => props.notebook.profile.value,
  (p) => {
    draft.value = p
      ? profileSchema.parse(p)
      : {
          ...blankProfile(),
          ...(props.snapshot.mode === 'demo'
            ? {
                name: 'AI 探索者（虚构示例）',
                role: '探索 AI 工程与开发者工具',
                bio: '这里是用于展示功能的虚构简介。请切换本机数据后填写自己的真实实践。'
              }
            : {})
        }
  },
  { immediate: true }
)
const entries = computed(() => props.notebook.entries.value.filter((e) => !e.archived))
const experiments = computed(() => props.notebook.experiments.value.filter((e) => !e.archived))
const preview = computed(() =>
  profileSchema.safeParse(draft.value).success
    ? buildPortfolio(draft.value, entries.value, experiments.value, props.snapshot)
    : null
)
function validate() {
  const parsed = profileSchema.safeParse(draft.value)
  errors.value = {}
  failure.value = ''
  message.value = ''
  if (!parsed.success) {
    errors.value = validationErrors(parsed.error)
    failure.value = '请先修正展示信息，再保存或导出。'
    return null
  }
  return parsed.data
}
async function save() {
  const profile = validate()
  if (!profile) return
  saving.value = true
  try {
    await props.notebook.saveProfile(profile)
    message.value = '展示配置已保存在当前浏览器，并会随档案备份导出。'
  } catch {
    failure.value = '保存失败，请检查浏览器存储权限。当前表单仍保留。'
  } finally {
    saving.value = false
  }
}
function exportFile(type: 'md' | 'html') {
  const profile = validate()
  if (!profile) return
  const result = buildPortfolio(profile, entries.value, experiments.value, props.snapshot)
  downloadFile(
    type === 'md' ? result.markdown : result.html,
    `ai-practice-portfolio.${type}`,
    type === 'md' ? 'text/markdown;charset=utf-8' : 'text/html;charset=utf-8'
  )
  message.value = '已生成下载文件。导出仅保存在本机；发布前请检查个人文字与链接。'
}
</script>
<template>
  <div class="notebook-page">
    <section class="report-hero panel">
      <div>
        <span class="eyebrow">TURN CURIOSITY INTO EVIDENCE</span>
        <h2>让 AI 兴趣，有实践可见。</h2>
        <p>
          填写个人介绍，勾选你愿意分享的实践与实验。下方预览与独立 HTML 导出内容一致，可放入自己的
          GitHub 仓库或作为简历附件。
        </p>
      </div>
      <span class="outline-badge">{{
        snapshot.mode === 'demo' ? '虚构展示示例' : '手动选择分享'
      }}</span>
    </section>
    <p v-if="notebook.error.value" role="alert" class="form-error">{{ notebook.error.value }}</p>
    <p v-if="!notebook.ready.value" role="status">正在读取浏览器档案…</p>
    <template v-else
      ><form class="panel notebook-card" @submit.prevent="save">
        <h3>01 / 你的 AI 名片</h3>
        <p>
          仅保存在当前浏览器的当前数据空间。用量统计与 Skills 默认不导出，勾选后会出现在预览中。
        </p>
        <div class="notebook-form-grid">
          <NotebookField
            v-for="field in fields"
            :id="`portfolio-${field.key}`"
            :key="field.key"
            v-model="draft[field.key]"
            :label="field.label"
            :required="'required' in field && field.required"
            :multiline="'multiline' in field && field.multiline"
            :error="errors[field.key]"
          />
        </div>
        <div class="notebook-actions">
          <button class="button secondary" :disabled="saving" type="submit">
            {{ saving ? '保存中…' : '保存展示配置' }}</button
          ><span v-if="message" role="status">{{ message }}</span>
        </div>
        <p v-if="failure" class="form-error" role="alert">{{ failure }}</p>
      </form>
      <section class="panel notebook-card">
        <h3>02 / 选择可公开的内容</h3>
        <p class="muted">
          仅列出未归档记录。实践输出日期、类别、工具、模型、过程、成果与证据链接；实验输出任务、验证标准、各组指标和备注。项目名与下一步字段不输出。
        </p>
        <div class="notebook-columns">
          <fieldset class="portfolio-choices">
            <legend>实践记录（{{ entries.length }}）</legend>
            <label v-for="entry in entries" :key="entry.id"
              ><input v-model="draft.entryIds" type="checkbox" :value="entry.id" /><span
                >{{ entry.title }}<small>{{ entry.date }} · {{ entry.category }}</small></span
              ></label
            >
            <p v-if="!entries.length" class="muted">请先在实践时间线记录真实过程。</p>
          </fieldset>
          <fieldset class="portfolio-choices">
            <legend>对比实验（{{ experiments.length }}）</legend>
            <label v-for="experiment in experiments" :key="experiment.id"
              ><input v-model="draft.experimentIds" type="checkbox" :value="experiment.id" /><span
                >{{ experiment.title
                }}<small
                  >{{ experiment.date }} · {{ experiment.trials.length }} 组人工记录</small
                ></span
              ></label
            >
            <p v-if="!experiments.length" class="muted">请先在模型实验室记录对比结果。</p>
          </fieldset>
        </div>
        <div class="portfolio-choices">
          <label
            ><input v-model="draft.showUsage" type="checkbox" />附上全部快照的 Token
            汇总、模型名称与日期范围</label
          ><label
            ><input v-model="draft.showSkills" type="checkbox" />附上 Skills
            安装名称清单（不表示调用或熟练程度）</label
          >
        </div>
      </section>
      <section class="panel notebook-card">
        <div class="notebook-actions portfolio-preview-heading">
          <div>
            <h3>03 / 预览与导出</h3>
            <p class="muted">
              {{ preview?.entries ?? 0 }} 条实践 ·
              {{ preview?.experiments ?? 0 }} 个实验。导出不会自动发布或上传。
            </p>
          </div>
          <div class="notebook-actions">
            <button class="button secondary" :disabled="!preview" @click="exportFile('md')">
              导出 Markdown</button
            ><button class="button primary" :disabled="!preview" @click="exportFile('html')">
              导出独立 HTML
            </button>
          </div>
        </div>
        <iframe
          v-if="preview"
          class="portfolio-preview"
          title="个人 AI 实践展示页预览"
          sandbox=""
          referrerpolicy="no-referrer"
          :srcdoc="preview.html"
        ></iframe>
        <p v-else class="empty-inline">填写有效的展示名称与链接后，将在这里生成预览。</p>
      </section></template
    >
  </div>
</template>
