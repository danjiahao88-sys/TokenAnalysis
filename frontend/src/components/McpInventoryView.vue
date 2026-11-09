<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { demoInventory, inventorySchema } from '../mcp'
import type { McpInventory } from '../mcp'
import { dateLabel } from '../news'
import { assistantFetch } from '../assistant-client'
const props = defineProps<{ mode: 'demo' | 'local' | 'public'; timezone: string }>()
const inventory = ref<McpInventory>(),
  loading = ref(false),
  error = ref(''),
  query = ref('')
let run = 0,
  controller: AbortController | undefined
const labels = {
  ready: '已读取配置',
  missing: '未找到配置',
  invalid: '配置无效或超出限制',
  unreadable: '配置不可读取'
}
const visible = computed(() =>
  (inventory.value?.servers ?? []).filter((s) =>
    `${s.name} ${s.tool} ${s.transport}`.toLowerCase().includes(query.value.toLowerCase())
  )
)
async function load() {
  const generation = ++run
  controller?.abort()
  inventory.value = undefined
  error.value = ''
  loading.value = false
  if (props.mode === 'demo') {
    inventory.value = demoInventory
    return
  }
  if (props.mode !== 'local') return
  const requestController = new AbortController()
  controller = requestController
  const timer = setTimeout(() => requestController.abort(), 15000)
  loading.value = true
  try {
    const response = await assistantFetch('inventory', {
      cache: 'no-store',
      signal: requestController.signal
    })
    if (!response.ok) throw Error()
    const text = await response.text()
    if (text.length > 1024 * 1024) throw Error()
    const result = inventorySchema.parse(JSON.parse(text))
    if (generation === run) inventory.value = result
  } catch {
    if (generation === run)
      error.value = '无法读取本机配置清单。请确认本机后端已升级到 v0.3.0 并运行。'
  } finally {
    clearTimeout(timer)
    if (generation === run) loading.value = false
  }
}
watch(() => props.mode, load, { immediate: true })
onBeforeUnmount(() => {
  run++
  controller?.abort()
})
</script>
<template>
  <div class="notebook-page">
    <section class="report-hero panel">
      <div>
        <span class="eyebrow">MY AI CONNECTIONS</span>
        <h2>了解你的 MCP 配置版图。</h2>
        <p>
          只读 Codex 与 Claude Code
          用户级配置声明，不执行命令，也不连接服务。项目级配置与插件缓存不在扫描范围内。
        </p>
      </div>
      <button v-if="mode === 'local'" class="button secondary" :disabled="loading" @click="load">
        {{ loading ? '读取中…' : '重新读取配置' }}
      </button>
    </section>
    <p v-if="mode === 'demo'" class="insight-strip">
      虚构示例 · 下列 MCP 名称、类型和状态仅演示界面，未读取本机配置。
    </p>
    <p v-if="mode === 'public'" class="panel empty-inline">
      公开快照不包含 MCP 配置。请在本机运行前后端，再切换「本机数据」查看。
    </p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <template v-if="inventory"
      ><section class="panel notebook-card">
        <h3>配置覆盖范围</h3>
        <p class="muted">
          检查时间：{{ dateLabel(inventory.checkedAt, timezone) }} · {{ timezone }}
        </p>
        <ul class="news-source-status">
          <li v-for="source in inventory.sources" :key="source.id">
            <strong>{{ source.label }}</strong
            ><span>{{ labels[source.status] }} · {{ source.count }} 项</span>
          </li>
        </ul>
        <p>
          「配置启用」仅表示配置未禁用，不代表连接成功、调用成功或已授权。地址、命令、参数、环境变量和密钥不返回前端。
        </p>
      </section>
      <div class="notebook-filters">
        <label
          >搜索 MCP 配置<input
            v-model="query"
            type="search"
            placeholder="名称、工具或传输类型" /></label
        ><span>{{ visible.length }} 项配置声明</span>
      </div>
      <div class="notebook-columns">
        <article v-for="server in visible" :key="server.id" class="panel notebook-card">
          <div class="notebook-actions">
            <span class="outline-badge">{{
              server.tool === 'codex' ? 'Codex' : 'Claude Code'
            }}</span
            ><span>{{
              server.transport === 'unknown' ? '传输类型未知' : server.transport.toUpperCase()
            }}</span>
          </div>
          <h3 class="mcp-name">{{ server.name }}</h3>
          <p>{{ server.enabled ? '配置启用' : '配置禁用' }} · 未测试连接</p>
        </article>
      </div>
      <p v-if="!visible.length" class="panel empty-inline">
        没有匹配的 MCP 配置声明。检查上方来源状态，或清空搜索。
      </p></template
    >
    <p v-else-if="loading" class="panel empty-inline" role="status">正在读取本机 MCP 配置声明…</p>
  </div>
</template>
