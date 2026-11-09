<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { historySchema } from '../codex-history'
import type { CodexHistory } from '../codex-history'
import { dateLabel } from '../news'
import { assistantFetch } from '../assistant-client'
const props = defineProps<{
  mode: 'demo' | 'local' | 'public'
  timezone: string
  query: string
  tool: string
}>()
const history = ref<CodexHistory>(),
  loading = ref(false),
  error = ref('')
let generation = 0,
  controller: AbortController | undefined
const visible = computed(() =>
  (history.value?.groups ?? []).filter((g) =>
    g.model.toLowerCase().includes(props.query.trim().toLowerCase())
  )
)
const statusText = {
  missing: '未找到 Codex 历史索引。',
  unreadable: '历史索引不可读取，请检查本机权限。',
  incompatible: '索引版本不兼容、暂时占用或数据无效，未展示不完整结果。',
  ready: ''
}
async function load() {
  const run = ++generation
  controller?.abort()
  history.value = undefined
  error.value = ''
  loading.value = false
  if (props.mode !== 'local') return
  const request = new AbortController()
  controller = request
  const timer = setTimeout(() => request.abort(), 15000)
  loading.value = true
  try {
    const response = await assistantFetch('codex-history', {
      cache: 'no-store',
      signal: request.signal
    })
    if (!response.ok) throw Error()
    const text = await response.text()
    if (text.length > 1024 * 1024) throw Error()
    const parsed = historySchema.parse(JSON.parse(text))
    if (run === generation) history.value = parsed
  } catch {
    if (run === generation)
      error.value = '历史索引读取失败，请确认本机后端已更新到 v0.3.1；上方日志统计不受影响。'
  } finally {
    clearTimeout(timer)
    if (run === generation) loading.value = false
  }
}
watch(() => props.mode, load, { immediate: true })
onBeforeUnmount(() => {
  generation++
  controller?.abort()
})
</script>
<template>
  <section
    v-if="mode === 'local' && (tool === 'all' || tool === 'codex')"
    class="panel history-panel"
  >
    <div class="panel-heading">
      <div>
        <h2>Codex 历史索引 · 明细缺失</h2>
        <p>发现历史会话，但原始日志已不在索引位置时，单独展示桌面索引保留的累计值。</p>
      </div>
      <button class="button secondary" :disabled="loading" @click="load">
        {{ loading ? '读取中…' : '重新读取历史索引' }}
      </button>
    </div>
    <div class="history-explanation">
      <p>
        <strong>独立历史参考，不计入上方统计。</strong
        >仅按会话最后记录的模型归组；可能包含会话内模型切换或分叉历史，无法确认全部 Token
        均属于该模型。
      </p>
      <p>
        没有输入 / 输出 /
        缓存拆分，也无法还原每日用量。因此不应用上方日期筛选，不进入趋势、占比、周报或公开导出，不代表账单。
      </p>
    </div>
    <p v-if="error" class="history-explanation form-error" role="alert">{{ error }}</p>
    <p v-if="history && history.status !== 'ready'" class="history-explanation" role="status">
      {{ statusText[history.status] }}
    </p>
    <template v-if="history?.status === 'ready'"
      ><p class="history-explanation muted">
        检查 {{ dateLabel(history.checkedAt, timezone) }} · 已核对
        {{ history.checkedSessions }} 个索引会话；{{ history.availableRollouts }} 个仍有日志；{{
          history.skippedRows
        }}
        项未能核验。{{ history.truncated ? '仅检查最近更新的 1,000 个会话，结果不完整。' : '' }}
      </p>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>索引最后记录的模型</th>
              <th>缺日志会话</th>
              <th>索引累计 Token（历史参考）</th>
              <th>最近索引更新</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="group in visible" :key="group.model">
              <td>
                <strong>{{ group.model }}</strong>
              </td>
              <td>{{ group.sessions }}</td>
              <td class="mono">{{ group.indexTokens.toLocaleString() }}</td>
              <td>{{ dateLabel(group.lastSeenAt, timezone) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!visible.length" class="empty-inline">
        {{
          history.groups.length
            ? '没有匹配的历史模型，请清空模型搜索。'
            : '没有发现原日志缺失的历史会话。'
        }}
      </p></template
    >
  </section>
</template>
