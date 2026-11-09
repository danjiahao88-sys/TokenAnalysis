<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { newsSchema, dateLabel, isStale } from '../news'
import type { News } from '../news'
import { sourceLinks } from '../data'
const props = defineProps<{ timezone: string }>()
const news = ref<News>(),
  loading = ref(false),
  error = ref(''),
  selected = ref('all'),
  query = ref('')
const visible = computed(() =>
  (news.value?.items ?? [])
    .filter(
      (item) =>
        (selected.value === 'all' || selected.value === item.sourceId) &&
        item.title.toLowerCase().includes(query.value.toLowerCase())
    )
    .sort((a, b) =>
      (b.publishedAt ?? b.updatedAt ?? '').localeCompare(a.publishedAt ?? a.updatedAt ?? '')
    )
)
const sourceName = (id: string) => news.value?.sources.find((s) => s.id === id)?.name ?? id
async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await fetch(`${import.meta.env.BASE_URL}news.json`, {
      cache: 'no-store',
      signal: AbortSignal.timeout(15000)
    })
    if (!response.ok) throw Error()
    const text = await response.text()
    if (text.length > 1024 * 1024) throw Error()
    news.value = newsSchema.parse(JSON.parse(text))
  } catch {
    error.value = '无法读取动态快照。已有内容保留，请稍后重试或直接访问官方来源。'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="notebook-page">
    <section class="report-hero panel">
      <div>
        <span class="eyebrow">FOLLOW THE SOURCE</span>
        <h2>保持好奇，与 AI 的变化同频。</h2>
        <p>官方新闻与版本发布原始标题。每条链接回原文，不把工具版本当作模型发布。</p>
      </div>
      <button class="button secondary" :disabled="loading" @click="load">
        {{ loading ? '正在读取…' : '重新读取动态' }}
      </button>
    </section>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    <div v-if="news" class="news-state panel notebook-card">
      <strong>公开信息 · 与你的使用量数据独立</strong>
      <p>
        快照生成：{{ dateLabel(news.generatedAt, timezone) }} · {{ timezone }}。发布后部署任务每 12
        小时尝试更新，平台调度可能延迟；重新读取按钮只读取已发布快照。
      </p>
      <p
        v-if="
          isStale(news.generatedAt) ||
          news.sources.some((s) => !s.lastSuccessAt || isStale(s.lastSuccessAt))
        "
        class="form-error"
        role="status"
      >
        快照或部分来源超过 48 小时未成功更新，或暂无成功记录，请以官方原文为准。
      </p>
      <details>
        <summary>查看 {{ news.sources.length }} 个抓取来源与检查结果</summary>
        <ul class="news-source-status">
          <li v-for="source in news.sources" :key="source.id">
            <a :href="source.homeUrl" target="_blank" rel="noopener noreferrer"
              >{{ source.name }} ↗</a
            ><span :class="{ 'form-error': source.status === 'error' }">{{ source.message }}</span
            ><small
              >检查 {{ dateLabel(source.checkedAt, timezone) }} / 上次成功
              {{ dateLabel(source.lastSuccessAt, timezone) }}</small
            >
          </li>
        </ul>
      </details>
    </div>
    <div class="notebook-filters">
      <label
        >搜索官方标题<input
          v-model="query"
          type="search"
          placeholder="模型名、版本号或关键词" /></label
      ><label
        >来源<select v-model="selected">
          <option value="all">全部来源</option>
          <option v-for="source in news?.sources" :key="source.id" :value="source.id">
            {{ source.name }}
          </option>
        </select></label
      ><span>{{ visible.length }} 条</span>
    </div>
    <section aria-label="官方动态列表" class="news-list">
      <a
        v-for="item in visible"
        :key="item.url"
        :href="item.url"
        target="_blank"
        rel="noopener noreferrer"
        class="panel news-item"
        ><div>
          <span class="outline-badge">{{ sourceName(item.sourceId) }}</span
          ><span
            v-if="news?.sources.find((s) => s.id === item.sourceId)?.status === 'error'"
            class="muted"
            >保留的历史内容</span
          >
        </div>
        <h3>{{ item.title }} <span aria-hidden="true">↗</span></h3>
        <p class="muted">
          {{
            item.publishedAt
              ? '发布日期'
              : item.updatedAt
                ? '来源更新时间（未提供发布日期）'
                : '来源未提供日期'
          }}{{
            item.publishedAt || item.updatedAt
              ? `：${dateLabel(item.publishedAt ?? item.updatedAt, props.timezone)}`
              : ''
          }}
        </p></a
      >
    </section>
    <p v-if="!visible.length" class="panel empty-inline">
      {{ loading ? '正在读取官方动态快照…' : '没有匹配的动态。可清空筛选或访问下方官方入口。' }}
    </p>
    <section class="panel notebook-card">
      <h3>更多官方入口</h3>
      <p class="muted">以下仅为来源导航；未提供聚合数据的来源不显示抓取成功。</p>
      <div class="source-links">
        <a
          v-for="source in sourceLinks"
          :key="source.name"
          :href="source.url"
          target="_blank"
          rel="noopener noreferrer"
          >{{ source.name }} ↗</a
        >
      </div>
    </section>
  </div>
</template>
