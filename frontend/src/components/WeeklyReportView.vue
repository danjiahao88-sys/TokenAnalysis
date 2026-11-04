<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Snapshot } from '../data'
import { fmt } from '../data'
import { downloadFile } from '../notebook'
import type { Notebook } from '../useNotebook'
import { reportMarkdown, weeklyReport, weekOptions } from '../weekly-report'
const props = defineProps<{ snapshot: Snapshot; notebook: Notebook }>()
const options = computed(() => weekOptions(props.snapshot)),
  week = ref(options.value[0]!)
watch(options, (value) => {
  if (!value.includes(week.value)) week.value = value[0]!
})
const report = computed(() =>
  weeklyReport(
    props.snapshot,
    week.value,
    props.notebook.entries.value,
    props.notebook.experiments.value
  )
)
const exported = ref(false)
function exportReport() {
  downloadFile(
    reportMarkdown(props.snapshot, report.value),
    `ai-weekly-${week.value}.md`,
    'text/markdown;charset=utf-8'
  )
  exported.value = true
}
</script>
<template>
  <div class="notebook-page">
    <section class="report-hero panel">
      <div>
        <span class="eyebrow">YOUR WEEK IN AI</span>
        <h2>把数字，写成成长记录。</h2>
        <p>由日志统计与手工档案生成，不额外调用模型。</p>
      </div>
      <div class="notebook-actions">
        <label
          >选择周一<select v-model="week">
            <option v-for="date in options" :key="date" :value="date">{{ date }} 这一周</option>
          </select></label
        ><button class="button primary" :disabled="!notebook.ready.value" @click="exportReport">
          导出 Markdown
        </button>
      </div>
    </section>
    <p v-if="exported" role="status">周报已导出，含当前档案中的实践与实验内容。</p>
    <p class="muted">
      {{ report.start }} — {{ report.end }} · {{ snapshot.timezone
      }}<span v-if="report.partial"> · 本周未完整，截至快照最新活动日</span> ·
      {{ snapshot.mode === 'demo' ? '全部为虚构示例' : '仅覆盖当前快照' }}
    </p>
    <div class="notebook-metrics">
      <article class="panel">
        <span>总 Token</span><strong>{{ fmt(report.stats.total) }}</strong
        ><small>输入 {{ fmt(report.stats.input) }} / 输出 {{ fmt(report.stats.output) }}</small>
      </article>
      <article class="panel">
        <span>独立会话 / 活跃天</span
        ><strong>{{ report.stats.sessions }} / {{ report.stats.activeDays }}</strong
        ><small>会话跨模型与日期去重</small>
      </article>
      <article class="panel">
        <span>上周对应天数环比</span
        ><strong>{{
          report.delta === null ? '—' : `${report.delta >= 0 ? '+' : ''}${report.delta.toFixed(1)}%`
        }}</strong
        ><small>{{ report.delta === null ? '缺少基期数据' : '仅表示用量变化' }}</small>
      </article>
    </div>
    <div class="notebook-columns">
      <section class="panel notebook-card">
        <h3>本周模型</h3>
        <ol class="report-ranking">
          <li v-for="model in report.models" :key="model.name">
            <span>{{ model.name }}</span
            ><strong>{{ fmt(model.total) }}</strong>
          </li>
        </ol>
        <p v-if="!report.models.length" class="empty-inline">本周没有用量记录。</p>
      </section>
      <section class="panel notebook-card">
        <h3>项目足迹</h3>
        <ol class="report-ranking">
          <li v-for="project in report.projects" :key="project.name">
            <span>{{ project.name }}</span
            ><strong>{{ fmt(project.total) }}</strong>
          </li>
        </ol>
        <p v-if="!report.projects.length" class="empty-inline">本周没有项目记录。</p>
      </section>
    </div>
    <section class="panel notebook-card">
      <h3>实践与验证</h3>
      <p>
        {{ report.entries.length }} 条实践，{{ report.experiments.length }}
        个手工模型实验。评价由你填写，系统不推断效率提升。
      </p>
      <article v-for="entry in report.entries" :key="entry.id" class="report-note">
        <small>{{ entry.date }} · {{ entry.category }}</small>
        <h4>{{ entry.title }}</h4>
        <p>{{ entry.summary }}</p>
        <p v-if="entry.outcome">成果：{{ entry.outcome }}</p>
        <p v-if="entry.nextStep">下一步：{{ entry.nextStep }}</p>
      </article>
      <article v-for="experiment in report.experiments" :key="experiment.id" class="report-note">
        <small>模型对比 · {{ experiment.date }}</small>
        <h4>{{ experiment.title }}</h4>
        <p>{{ experiment.criteria }}</p>
        <span class="outline-badge">{{ experiment.trials.length }} 组人工记录</span>
      </article>
      <p v-if="!report.entries.length && !report.experiments.length" class="empty-inline">
        去实践时间线记下本周的一次探索吧。
      </p>
    </section>
  </div>
</template>
