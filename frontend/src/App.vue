<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Activity,
  ArrowDownToLine,
  ArrowRight,
  ArrowUpRight,
  Blocks,
  BookOpen,
  CalendarDays,
  Check,
  ChevronRight,
  CircleHelp,
  Cpu,
  Database,
  ExternalLink,
  Fingerprint,
  Layers,
  LayoutDashboard,
  LockKeyhole,
  Menu,
  Moon,
  Radio,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles,
  Sun,
  Terminal,
  Upload,
  X,
  Zap
} from '@lucide/vue'
import DataChart from './components/DataChart.vue'
import NotebookToolbar from './components/NotebookToolbar.vue'
import WeeklyReportView from './components/WeeklyReportView.vue'
import PracticeTimelineView from './components/PracticeTimelineView.vue'
import ExperimentLabView from './components/ExperimentLabView.vue'
import NewsRadarView from './components/NewsRadarView.vue'
import McpInventoryView from './components/McpInventoryView.vue'
import PortfolioView from './components/PortfolioView.vue'
import CodexHistoryView from './components/CodexHistoryView.vue'
import AssistantConnectionView from './components/AssistantConnectionView.vue'
import {
  assistantFetch,
  assistantToken,
  liveStatusSchema,
  rememberSource,
  shouldReconnect
} from './assistant-client'
import type { LiveStatus } from './assistant-client'
import { useNotebook } from './useNotebook'
import {
  fmt,
  groupRows,
  lastDate,
  palette as lightPalette,
  parseImport,
  shiftDate,
  snapshotSchema,
  summarize,
  windowRows
} from './data'
import type { Snapshot } from './data'
import demoJson from './demo.json'

const demo = snapshotSchema.parse(demoJson)
const snapshot = ref<Snapshot>(demo)
const notebook = useNotebook(snapshot)
const page = ref('overview')
const days = ref(30)
const tool = ref('all')
const loading = ref(false)
const notice = ref('')
const failure = ref(false)
const mobileMenu = ref(false)
const narrow = ref(false)
let viewportMedia: MediaQueryList | undefined
const dark = ref(false)
const query = ref('')
const skillCategory = ref('全部分类')
const trendMetric = ref('tokens')
const uploadInput = ref<HTMLInputElement>()
const navigation = [
  { id: 'overview', label: '使用总览', en: 'Overview', icon: LayoutDashboard },
  { id: 'models', label: '模型分析', en: 'Models', icon: Layers },
  { id: 'tools', label: '工具箱', en: 'Tools', icon: Terminal },
  { id: 'skills', label: 'Skills 工作台', en: 'Skills', icon: Blocks },
  { id: 'mcp', label: 'MCP 配置', en: 'MCP', icon: Blocks },
  { id: 'portfolio', label: '个人展示页', en: 'Portfolio', icon: Fingerprint },
  { id: 'weekly', label: 'AI 使用周报', en: 'Weekly', icon: CalendarDays },
  { id: 'practice', label: '实践时间线', en: 'Practice', icon: BookOpen },
  { id: 'experiments', label: '模型实验室', en: 'Experiments', icon: Cpu },
  { id: 'radar', label: '探索雷达', en: 'Explore', icon: Radio },
  { id: 'data', label: '数据与隐私', en: 'Data', icon: Database }
]
const currentNav = computed(() => navigation.find((n) => n.id === page.value)!)
const rows = computed(() => windowRows(snapshot.value, days.value, tool.value))
const stats = computed(() => summarize(rows.value))
const previous = computed(() => summarize(windowRows(snapshot.value, days.value, tool.value, true)))
const models = computed(() => groupRows(rows.value, 'model'))
const projects = computed(() => groupRows(rows.value, 'project'))
const toolGroups = computed(() => groupRows(rows.value, 'tool'))
const filteredModels = computed(() =>
  models.value.filter((m) => m.name.toLowerCase().includes(query.value.toLowerCase()))
)
const categories = computed(() => [
  '全部分类',
  ...new Set(snapshot.value.skills.map((s) => s.category))
])
const filteredSkills = computed(() =>
  snapshot.value.skills.filter(
    (s) =>
      (skillCategory.value === '全部分类' || s.category === skillCategory.value) &&
      `${s.name} ${s.description}`.toLowerCase().includes(query.value.toLowerCase())
  )
)
const isDemo = computed(() => snapshot.value.mode === 'demo')
const isLocal = computed(() => snapshot.value.mode === 'local')
const sourceLabel = computed(() =>
  isDemo.value ? '示例数据' : isLocal.value ? '本机数据' : '公开快照'
)
const endDate = computed(() => lastDate(snapshot.value))
const delta = computed(() =>
  previous.value.total ? (stats.value.total / previous.value.total - 1) * 100 : null
)
const palette = computed(() =>
  dark.value
    ? ['#8fc394', '#b6d8af', '#c6ce84', '#e3c578', '#9cb8d1', '#d0abc3', '#a7bf92']
    : lightPalette
)
const chartText = computed(() => (dark.value ? '#aab9af' : '#68766c'))
const chartGrid = computed(() => (dark.value ? '#2a3930' : '#edf0eb'))
const trend = computed(() => {
  const map = new Map<string, { input: number; output: number; sessions: Set<string> }>()
  for (let i = days.value - 1; i >= 0; i--)
    map.set(shiftDate(endDate.value, -i), { input: 0, output: 0, sessions: new Set() })
  for (const r of rows.value) {
    const d = map.get(r.date)!
    d.input += r.inputTokens
    d.output += r.outputTokens
    d.sessions.add(r.sessionRef)
  }
  return [...map].map(([date, v]) => ({ date, ...v }))
})
const trendOption = computed(() => ({
  color: palette.value,
  textStyle: { fontFamily: 'inherit' },
  tooltip: {
    trigger: 'axis',
    confine: true,
    valueFormatter: (value: number) => value.toLocaleString()
  },
  grid: { left: 5, right: 12, top: 22, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trend.value.map((v) => v.date.slice(5).replace('-', '/')),
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: {
      color: chartText.value,
      margin: 16,
      interval: Math.max(0, Math.floor(days.value / 6) - 1)
    }
  },
  yAxis: {
    type: 'value',
    splitNumber: 3,
    axisLabel: {
      color: chartText.value,
      formatter: (v: number) => (trendMetric.value === 'tokens' ? fmt(v, 0) : v)
    },
    splitLine: { lineStyle: { color: chartGrid.value, type: 'dashed' } }
  },
  series:
    trendMetric.value === 'tokens'
      ? [
          {
            name: '输入 Token',
            type: 'line',
            smooth: 0.25,
            symbol: 'circle',
            showSymbol: false,
            data: trend.value.map((v) => v.input),
            lineStyle: { width: 2.5 },
            areaStyle: { color: palette.value[0], opacity: 0.08 }
          },
          {
            name: '输出 Token',
            type: 'line',
            smooth: 0.25,
            showSymbol: false,
            data: trend.value.map((v) => v.output),
            lineStyle: { width: 2, type: 'dashed' }
          }
        ]
      : [
          {
            name: '活跃会话',
            type: 'bar',
            barMaxWidth: 18,
            itemStyle: { borderRadius: [3, 3, 0, 0] },
            data: trend.value.map((v) => v.sessions.size)
          }
        ]
}))
const donutOption = computed(() => ({
  color: palette.value,
  tooltip: {
    trigger: 'item',
    confine: true,
    formatter: (p: { name: string; value: number; percent: number }) =>
      `${p.name}: ${fmt(p.value)} (${p.percent}%)`,
    renderMode: 'richText'
  },
  series: [
    {
      type: 'pie',
      radius: ['68%', '88%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      padAngle: 3,
      itemStyle: { borderRadius: 4 },
      label: { show: false },
      emphasis: { scaleSize: 3 },
      data: models.value.map((m) => ({ name: m.name, value: m.total }))
    }
  ]
}))
const heatmap = computed(() => {
  const end = endDate.value
  const weekday = (new Date(`${end}T12:00:00Z`).getUTCDay() + 6) % 7
  const start = shiftDate(end, -weekday - 12 * 7)
  const totals = new Map<string, number>()
  for (const r of snapshot.value.usage.filter((r) => tool.value === 'all' || r.tool === tool.value))
    totals.set(r.date, (totals.get(r.date) || 0) + r.totalTokens)
  const max = Math.max(1, ...totals.values())
  return Array.from({ length: 91 }, (_, i) => {
    const date = shiftDate(start, i),
      total = totals.get(date) || 0
    return {
      date,
      total,
      future: date > end,
      level: !total ? 0 : Math.min(4, Math.ceil((total / max) * 4))
    }
  })
})
const heatmapActive = computed(() => heatmap.value.filter((d) => d.total > 0 && !d.future).length)
const modelTopShare = computed(() =>
  stats.value.total ? Math.round(((models.value[0]?.total || 0) / stats.value.total) * 100) : 0
)

function setMobileMenu(open: boolean) {
  mobileMenu.value = open
  nextTick(() =>
    document
      .querySelector<HTMLButtonElement>(open ? '.sidebar .nav-item' : '.mobile-toggle')
      ?.focus()
  )
}
function navigate(id: string) {
  page.value = id
  if (mobileMenu.value) setMobileMenu(false)
  query.value = ''
  window.scrollTo({ top: 0, behavior: 'instant' })
  history.replaceState(null, '', `#${id}`)
}
function viewTool(id: string) {
  tool.value = id
  navigate('models')
}
function viewportChanged(event: MediaQueryListEvent) {
  narrow.value = event.matches
  if (!event.matches) mobileMenu.value = false
}
function message(text: string, error = false) {
  notice.value = text
  failure.value = error
}
function toggleTheme() {
  dark.value = !dark.value
  document.documentElement.dataset.theme = dark.value ? 'dark' : 'light'
  localStorage.setItem('footprint-theme', dark.value ? 'dark' : 'light')
}
function useDemo() {
  stopLive()
  rememberSource(false)
  snapshot.value = demo
  tool.value = 'all'
  skillCategory.value = '全部分类'
  message('使用量与个人档案已切换到虚构演示；官方动态仍为独立的公开信息。')
}
async function responseJson(response: Response) {
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}
async function connectLocal(sync = false) {
  stopLive()
  const generation = liveGeneration
  loading.value = true
  try {
    if (sync)
      await responseJson(
        await assistantFetch('collect', {
          method: 'POST',
          headers: { 'X-AI-Footprint': 'local' },
          signal: AbortSignal.timeout(120000)
        })
      )
    const result = snapshotSchema.parse(
      await responseJson(
        await assistantFetch('snapshot', { cache: 'no-store', signal: AbortSignal.timeout(15000) })
      )
    )
    if (result.mode !== 'local') throw new Error('Unexpected source')
    if (generation !== liveGeneration) return
    snapshot.value = result
    tool.value = 'all'
    skillCategory.value = '全部分类'
    message(
      sync ? '本机数据已同步，重复导入不会叠加计数。' : '已连接本地服务，当前显示本机日志统计。'
    )
    rememberSource(true)
    offline.value = false
    liveTimer = setTimeout(pollLive, 5000)
  } catch {
    if (generation !== liveGeneration) return
    showAssistant.value = true
    offline.value = true
    if (assistantToken.value) liveTimer = setTimeout(() => void connectLocal(), 10000)
    message(
      sync
        ? '同步未完成，当前数据已保留。请检查本地服务与日志读取权限。'
        : '暂时无法连接采集助手，请按下方步骤安装并配对；如已安装，请检查本地网络权限。',
      true
    )
  } finally {
    loading.value = false
  }
}
async function importFile(event: Event) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0]
  if (!file) return
  try {
    if (file.size > 5 * 1024 * 1024) throw new Error('快照不能超过 5 MB。')
    const result = parseImport(await file.text())
    stopLive()
    rememberSource(false)
    snapshot.value = result
    tool.value = 'all'
    skillCategory.value = '全部分类'
    message('快照已在浏览器内加载，没有上传到服务器。')
  } catch (error) {
    message(
      error instanceof Error && error.message.includes('请先')
        ? error.message
        : '导入失败：请选择符合 v1 格式、大小不超过 5 MB 的公开或演示快照。',
      true
    )
  } finally {
    input.value = ''
  }
}
async function exportSnapshot() {
  loading.value = true
  try {
    const data = isLocal.value
      ? snapshotSchema.parse(
          await responseJson(
            await assistantFetch('export', {
              cache: 'no-store',
              signal: AbortSignal.timeout(15000)
            })
          )
        )
      : snapshot.value
    if (data.mode === 'local') throw new Error('Private snapshot')
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob),
      link = document.createElement('a')
    link.href = url
    link.download = `ai-footprint-${data.mode}.json`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    message(
      isDemo.value
        ? '已导出演示快照，保留虚构数据标识。'
        : '已导出公开快照。分享前请确认用量、模型和日期符合你的公开范围。'
    )
  } catch {
    message('导出未完成，请检查本地服务后重试。', true)
  } finally {
    loading.value = false
  }
}
function toolInitial(id: string) {
  return id === 'codex' ? 'C' : id === 'trae' ? 'T' : 'A'
}
function readableTime(time: string | null) {
  return time
    ? new Date(time).toLocaleString('zh-CN', { timeZone: snapshot.value.timezone, hour12: false })
    : '尚未采集'
}
onMounted(() => {
  dark.value = localStorage.getItem('footprint-theme') === 'dark'
  document.documentElement.dataset.theme = dark.value ? 'dark' : 'light'
  const id = location.hash.slice(1)
  if (navigation.some((n) => n.id === id)) page.value = id
  viewportMedia = matchMedia('(max-width: 760px)')
  narrow.value = viewportMedia.matches
  viewportMedia.addEventListener('change', viewportChanged)
  if (shouldReconnect()) void connectLocal()
})
onBeforeUnmount(() => {
  viewportMedia?.removeEventListener('change', viewportChanged)
  stopLive()
})

const showAssistant = ref(true),
  offline = ref(false),
  liveStatus = ref<LiveStatus>()
let liveGeneration = 0,
  liveTimer: ReturnType<typeof setTimeout> | undefined,
  liveController: AbortController | undefined
function stopLive() {
  liveGeneration++
  clearTimeout(liveTimer)
  liveController?.abort()
}
async function pollLive() {
  const generation = liveGeneration
  if (!isLocal.value) return
  if (document.hidden) {
    liveTimer = setTimeout(pollLive, 5000)
    return
  }
  const controller = new AbortController()
  liveController = controller
  const timeout = setTimeout(() => controller.abort(), 15000)
  try {
    const status = liveStatusSchema.parse(
      await responseJson(await assistantFetch('status', { signal: controller.signal }))
    )
    if (generation !== liveGeneration) return
    liveStatus.value = status
    if (offline.value || (status.lastSuccessAt && status.lastSuccessAt !== lastLiveSuccess)) {
      const updated = snapshotSchema.parse(
        await responseJson(await assistantFetch('snapshot', { signal: controller.signal }))
      )
      if (generation !== liveGeneration) return
      if (updated.mode !== 'local') throw Error()
      snapshot.value = updated
      lastLiveSuccess = status.lastSuccessAt
    }
    offline.value = false
  } catch {
    if (generation === liveGeneration) offline.value = true
  } finally {
    clearTimeout(timeout)
    if (generation === liveGeneration && isLocal.value) liveTimer = setTimeout(pollLive, 5000)
  }
}
let lastLiveSuccess: string | null = null
function assistantDisconnected() {
  useDemo()
  showAssistant.value = true
}
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="app-shell">
    <div v-if="mobileMenu" class="nav-backdrop" @click="setMobileMenu(false)"></div>
    <aside
      class="sidebar"
      :class="{ open: mobileMenu }"
      :inert="narrow && !mobileMenu"
      aria-label="主导航"
      @keydown.esc="setMobileMenu(false)"
    >
      <a class="brand" href="#overview" @click.prevent="navigate('overview')"
        ><span class="brand-symbol"><Fingerprint :size="25" /></span
        ><span>AI Footprint<span class="brand-sub">每一次探索，都有迹可循</span></span></a
      >
      <button class="workspace-card" @click="navigate('data')">
        <span class="avatar">ME</span
        ><span><strong>我的 AI 工作空间</strong><small>Personal workspace</small></span
        ><ChevronRight :size="15" />
      </button>
      <span class="nav-label">WORKSPACE</span>
      <nav>
        <button
          v-for="item in navigation"
          :key="item.id"
          :class="['nav-item', { active: page === item.id }]"
          :aria-current="page === item.id ? 'page' : undefined"
          @click="navigate(item.id)"
        >
          <component :is="item.icon" :size="19" /><span>{{ item.label }}</span
          ><span v-if="item.id === 'radar'" class="nav-new">NEW</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <div class="local-note">
          <ShieldCheck :size="20" /><strong>你的足迹，由你掌控</strong>
          <p>本地采集 · 按需分享<br />让数据留在自己的设备上。</p>
          <button @click="navigate('data')">了解数据边界 <ArrowUpRight :size="14" /></button>
        </div>
        <div class="sidebar-footer">
          <span class="version-dot"></span><span>v0.4.0 · Local first</span
          ><button
            class="icon-button"
            :aria-label="dark ? '切换浅色主题' : '切换深色主题'"
            @click="toggleTheme"
          >
            <Sun v-if="dark" :size="17" /><Moon v-else :size="17" />
          </button>
        </div>
      </div>
    </aside>

    <div class="main-shell" :inert="narrow && mobileMenu">
      <header class="topbar">
        <div class="breadcrumbs">
          <button
            class="icon-button mobile-toggle"
            aria-label="打开导航"
            :aria-expanded="mobileMenu"
            @click="setMobileMenu(!mobileMenu)"
          >
            <Menu :size="20" /></button
          ><span>我的工作空间</span><ChevronRight :size="14" /><strong>{{
            currentNav.label
          }}</strong>
        </div>
        <div class="topbar-actions">
          <span :class="['source-pill', { live: isLocal }]"><span></span>{{ sourceLabel }}</span
          ><button class="icon-button" aria-label="数据口径与帮助" @click="navigate('data')">
            <CircleHelp :size="19" /></button
          ><span class="top-avatar">ME</span>
        </div>
      </header>

      <main id="main-content" tabindex="-1">
        <div v-if="notice" :class="['notice', { error: failure }]" role="status">
          <CircleHelp v-if="failure" :size="18" /><Check v-else :size="18" /><span>{{
            notice
          }}</span
          ><button class="icon-button" aria-label="关闭提示" @click="notice = ''">
            <X :size="16" />
          </button>
        </div>
        <div class="page-heading">
          <div>
            <div class="eyebrow">{{ currentNav.en.toUpperCase() }} / AI FOOTPRINT</div>
            <h1>{{ page === 'overview' ? '让每一次 AI 探索，看得见。' : currentNav.label }}</h1>
            <p>
              {{
                page === 'overview'
                  ? '从一枚 Token 到一个新想法，记录你与 AI 共同成长的轨迹。'
                  : page === 'models'
                    ? '理解你的模型偏好，为每一种任务找到合适的搭档。'
                    : page === 'skills'
                      ? '把探索过的能力，整理成属于自己的工具集。'
                      : page === 'tools'
                        ? '连接日常使用的 AI 工具，让分散的足迹汇聚在一起。'
                        : page === 'radar'
                          ? '保持好奇，跟随可靠的来源，发现下一次探索的方向。'
                          : page === 'mcp'
                            ? '整理用户级 MCP 配置声明，区分安装、启用与真实调用。'
                            : page === 'portfolio'
                              ? '将愿意分享的探索过程，整理成可预览、可导出的个人作品。'
                              : ['weekly', 'practice', 'experiments'].includes(page)
                                ? '从日志统计到实践证据，积累属于自己的 AI 探索档案。'
                                : '了解数据从哪里来、如何统计，以及分享时会包含什么。'
              }}
            </p>
          </div>
          <button
            class="button secondary export-button"
            :disabled="loading"
            @click="exportSnapshot"
          >
            <ArrowDownToLine :size="16" />导出快照
          </button>
        </div>

        <div class="source-banner">
          <div>
            <span :class="['tiny-dot', { green: !isDemo }]"></span
            ><strong>{{
              isDemo
                ? '正在浏览演示空间'
                : isLocal
                  ? offline
                    ? '本机连接中断 · 上次数据'
                    : '已连接本机工作空间'
                  : '正在浏览公开快照'
            }}</strong
            ><span class="banner-detail">{{
              isDemo
                ? '所有用量均为虚构示例，切换本机数据可查看真实足迹。'
                : isLocal
                  ? '统计可读取的 Codex / Claude Code 日志；无日志的工具不计入用量。'
                  : '快照在浏览器内读取，没有上传服务器。'
            }}</span>
          </div>
          <div class="source-switch">
            <button :class="{ selected: isDemo }" @click="useDemo">演示</button
            ><button :disabled="loading" :class="{ selected: isLocal }" @click="connectLocal()">
              <span class="tiny-dot green"></span>本机数据
            </button>
          </div>
        </div>

        <AssistantConnectionView
          v-if="showAssistant || isLocal || assistantToken"
          :local="isLocal"
          :status="liveStatus"
          :offline="offline"
          @connected="connectLocal()"
          @disconnected="assistantDisconnected"
        />

        <template v-if="page === 'overview' || page === 'models'">
          <div class="filter-bar">
            <div class="section-kicker">
              <Activity :size="16" /><span>探索概况</span><span class="muted separator">/</span
              ><small>截至 {{ endDate }}</small>
            </div>
            <div class="filters">
              <label class="select-wrap"
                ><Terminal :size="14" /><select v-model="tool" aria-label="筛选 AI 工具">
                  <option value="all">全部工具</option>
                  <option v-for="t in snapshot.tools" :key="t.id" :value="t.id">
                    {{ t.name }}
                  </option>
                </select></label
              ><label class="select-wrap"
                ><CalendarDays :size="14" /><select v-model.number="days" aria-label="统计时间范围">
                  <option :value="7">最近 7 天</option>
                  <option :value="30">最近 30 天</option>
                  <option :value="90">最近 90 天</option>
                </select></label
              >
            </div>
          </div>
          <div class="metric-grid">
            <article class="metric-card">
              <div class="metric-label">总 Token 用量 <Zap :size="17" /></div>
              <div class="metric-value">{{ fmt(stats.total) }}<span>tokens</span></div>
              <div class="metric-foot">
                <span v-if="delta !== null" :class="['change', { down: delta < 0 }]"
                  ><ArrowUpRight :size="13" />{{ delta > 0 ? '+' : ''
                  }}{{ delta.toFixed(1) }}%</span
                ><span>{{ delta === null ? '前一周期无记录' : `较前 ${days} 天` }}</span>
              </div>
            </article>
            <article class="metric-card">
              <div class="metric-label">活跃会话 <Terminal :size="17" /></div>
              <div class="metric-value">
                {{ stats.sessions.toLocaleString() }}<span>sessions</span>
              </div>
              <div class="metric-foot">
                <span class="tiny-dot green"></span
                ><span>覆盖 {{ stats.activeDays }} 个活跃日</span>
              </div>
            </article>
            <article class="metric-card">
              <div class="metric-label">探索的模型 <Layers :size="17" /></div>
              <div class="metric-value">
                {{ stats.models.toString().padStart(2, '0') }}<span>models</span>
              </div>
              <div class="metric-foot">
                <span>{{ toolGroups.length }} 款工具 · {{ stats.projects }} 个项目</span>
              </div>
            </article>
            <article class="metric-card">
              <div class="metric-label">输入缓存占比 <Cpu :size="17" /></div>
              <div class="metric-value">{{ stats.cacheRate.toFixed(1) }}<span>%</span></div>
              <div class="metric-foot">
                <span class="cache-track"><i :style="{ width: `${stats.cacheRate}%` }"></i></span
                ><span>{{ fmt(stats.cached) }} 已缓存</span>
              </div>
            </article>
          </div>
        </template>

        <template v-if="page === 'overview'">
          <div class="chart-grid">
            <section class="panel trend-panel">
              <div class="panel-heading">
                <div>
                  <h2>用量趋势 <span class="heading-dot"></span></h2>
                  <p>每一次提问，都是探索的开始</p>
                </div>
                <div class="segmented">
                  <button
                    :class="{ selected: trendMetric === 'tokens' }"
                    @click="trendMetric = 'tokens'"
                  >
                    Token</button
                  ><button
                    :class="{ selected: trendMetric === 'sessions' }"
                    @click="trendMetric = 'sessions'"
                  >
                    会话
                  </button>
                </div>
              </div>
              <div class="chart-legend">
                <span
                  ><i :style="{ background: palette[0] }"></i
                  >{{ trendMetric === 'tokens' ? '输入 Token' : '活跃会话' }}</span
                ><span v-if="trendMetric === 'tokens'"
                  ><i :style="{ background: palette[1] }"></i>输出 Token</span
                ><span class="legend-unit">{{ days }} DAYS</span>
              </div>
              <DataChart
                :option="trendOption"
                :label="`最近 ${days} 天用量趋势，总计 ${stats.total.toLocaleString()} Token，${stats.sessions} 个活跃会话`"
              />
            </section>
            <section class="panel distribution-panel">
              <div class="panel-heading">
                <div>
                  <h2>模型使用分布</h2>
                  <p>与你一起思考的搭档</p>
                </div>
                <button class="icon-button" aria-label="查看模型分析" @click="navigate('models')">
                  <ArrowUpRight :size="18" />
                </button>
              </div>
              <div class="donut-wrap">
                <DataChart
                  :option="donutOption"
                  :height="192"
                  :label="
                    models.map((m) => `${m.name}: ${fmt(m.total)} Token`).join('，') ||
                    '暂无模型使用记录'
                  "
                />
                <div class="donut-center">
                  <strong>{{ stats.models }}</strong
                  ><span>使用中的模型</span>
                </div>
              </div>
              <div class="model-legend">
                <div v-for="(m, i) in models.slice(0, 4)" :key="m.name">
                  <span
                    ><i :style="{ background: palette[i % palette.length] }"></i>{{ m.name }}</span
                  ><strong
                    >{{ stats.total ? ((m.total / stats.total) * 100).toFixed(1) : 0
                    }}<small>%</small></strong
                  >
                </div>
                <p v-if="!models.length" class="empty-inline">这个时间范围内还没有记录。</p>
              </div>
            </section>
          </div>

          <div class="lower-grid">
            <section class="panel activity-panel">
              <div class="panel-heading">
                <div>
                  <h2>
                    持续探索 <span class="count-badge">{{ heatmapActive }} 个活跃日</span>
                  </h2>
                  <p>过去 13 周，点亮你的 AI 足迹</p>
                </div>
                <CalendarDays :size="18" class="muted" />
              </div>
              <div class="heatmap-months">
                <span>{{ heatmap[0]?.date.slice(0, 7) }}</span
                ><span>{{ endDate.slice(0, 7) }}</span>
              </div>
              <div class="heatmap-layout">
                <div class="weekday-labels">
                  <span>一</span><span>三</span><span>五</span><span>日</span>
                </div>
                <div
                  class="heatmap"
                  role="img"
                  :aria-label="`过去 13 周有 ${heatmapActive} 个活跃日，每日用量明细可在数据页面下载`"
                >
                  <div
                    v-for="d in heatmap"
                    :key="d.date"
                    :class="['heat-cell', `level-${d.level}`, { future: d.future }]"
                    :title="`${d.date} · ${d.future ? '尚未到来' : `${d.total.toLocaleString()} Token`}`"
                  ></div>
                </div>
              </div>
              <div class="heatmap-footer">
                <span>好奇心，是最好的驱动力。</span>
                <div>少 <i v-for="i in 5" :key="i" :class="`level-${i - 1}`"></i> 多</div>
              </div>
            </section>
            <section class="panel projects-panel">
              <div class="panel-heading">
                <div>
                  <h2>项目足迹</h2>
                  <p>让 AI 参与真正的创造</p>
                </div>
                <span class="mono muted">TOP {{ Math.min(4, projects.length) }}</span>
              </div>
              <div v-for="(p, i) in projects.slice(0, 4)" :key="p.name" class="project-row">
                <span class="project-index">0{{ i + 1 }}</span>
                <div>
                  <strong>{{ p.name }}</strong
                  ><span class="project-bar"
                    ><i
                      :style="{
                        width: `${projects[0]?.total ? (p.total / projects[0].total) * 100 : 0}%`
                      }"
                    ></i
                  ></span>
                </div>
                <span class="mono">{{ fmt(p.total) }}</span>
              </div>
              <p v-if="!projects.length" class="empty-inline">
                暂无项目记录，尝试调整工具或日期范围。
              </p>
            </section>
          </div>

          <div class="discovery-grid">
            <section class="explore-card">
              <div class="eyebrow">STAY CURIOUS. KEEP BUILDING.</div>
              <h2>把好奇心，<br />变成下一次创造。</h2>
              <p>从工具到模型，探索 AI 的更多可能。</p>
              <button @click="navigate('radar')">打开探索雷达 <ArrowRight :size="17" /></button>
              <div class="orbit-art" aria-hidden="true">
                <span></span><span></span><span></span><Sparkles :size="34" />
              </div>
            </section>
            <section class="panel skill-preview">
              <div class="panel-heading">
                <div>
                  <h2>我的能力拼图</h2>
                  <p>Skills，让工作流更进一步</p>
                </div>
                <span class="count-badge">{{ snapshot.skills.length }} 已安装</span>
              </div>
              <div class="skill-chips">
                <span v-for="s in snapshot.skills.slice(0, 6)" :key="s.id"
                  ><Blocks :size="13" />{{ s.name }}</span
                >
                <p v-if="!snapshot.skills.length" class="empty-inline">
                  暂未读取到已安装的 Skills。
                </p>
              </div>
              <button class="text-button" @click="navigate('skills')">
                查看 Skills 工作台 <ArrowRight :size="15" />
              </button>
            </section>
          </div>
        </template>

        <template v-else-if="page === 'models'">
          <div class="insight-strip">
            <Sparkles :size="20" />
            <p v-if="models.length">
              这个周期，你最常使用 <strong>{{ models[0]?.name }}</strong
              >，占总用量的
              <strong>{{ modelTopShare }}%</strong>。模型偏好反映使用分布，不代表效果排名。
            </p>
            <p v-else>当前筛选范围内没有用量记录，试试其他工具或时间范围。</p>
          </div>
          <section class="panel">
            <div class="panel-heading">
              <div>
                <h2>模型使用明细</h2>
                <p>按总 Token 排序 · 缓存包含在输入中</p>
              </div>
              <label class="search-field"
                ><Search :size="16" /><input
                  v-model="query"
                  placeholder="搜索模型…"
                  aria-label="搜索模型"
              /></label>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>模型</th>
                    <th>总 Token</th>
                    <th>输入 / 输出</th>
                    <th>会话</th>
                    <th>缓存占比</th>
                    <th>用量占比</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(m, i) in filteredModels" :key="m.name">
                    <td>
                      <span
                        class="model-mark"
                        :style="{
                          background: `${palette[i % palette.length]}18`,
                          color: palette[i % palette.length]
                        }"
                        ><Cpu :size="18" /></span
                      ><strong>{{ m.name }}</strong>
                    </td>
                    <td class="mono">{{ fmt(m.total) }}</td>
                    <td class="mono muted">{{ fmt(m.input) }} / {{ fmt(m.output) }}</td>
                    <td>{{ m.sessions }}</td>
                    <td>{{ m.cacheRate.toFixed(1) }}%</td>
                    <td>
                      <span class="table-bar"
                        ><i
                          :style="{
                            width: `${stats.total ? (m.total / stats.total) * 100 : 0}%`,
                            background: palette[i % palette.length]
                          }"
                        ></i></span
                      >{{ stats.total ? ((m.total / stats.total) * 100).toFixed(1) : 0 }}%
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!filteredModels.length" class="empty-inline">没有找到匹配的模型。</p>
            </div>
          </section>
          <CodexHistoryView
            :mode="snapshot.mode"
            :timezone="snapshot.timezone"
            :query="query"
            :tool="tool"
          />
          <div class="info-pair">
            <article class="panel info-card">
              <Layers :size="22" />
              <h3>模型实验室</h3>
              <p>记录同一任务的耗时、用量、验证结果与人工评价，让模型选择有据可依。</p>
              <button class="text-button" @click="navigate('experiments')">
                开始对比实验 <ArrowRight :size="14" />
              </button>
            </article>
            <article class="panel info-card">
              <BookOpen :size="22" />
              <h3>关于费用</h3>
              <p>
                订阅费用与 API 单价不能混算。第一版保留真实 Token 口径，尚未提供账单或价格估算。
              </p>
              <button class="text-button" @click="navigate('data')">
                查看统计说明 <ArrowRight :size="14" />
              </button>
            </article>
          </div>
        </template>

        <template v-else-if="['weekly', 'practice', 'experiments'].includes(page)">
          <NotebookToolbar :key="notebook.scope.value" :notebook="notebook" />
          <WeeklyReportView
            v-if="page === 'weekly'"
            :key="notebook.scope.value"
            :snapshot="snapshot"
            :notebook="notebook"
          />
          <PracticeTimelineView
            v-else-if="page === 'practice'"
            :key="notebook.scope.value"
            :timezone="snapshot.timezone"
            :notebook="notebook"
          />
          <ExperimentLabView
            v-else
            :key="notebook.scope.value"
            :timezone="snapshot.timezone"
            :notebook="notebook"
          />
        </template>
        <template v-else-if="page === 'tools'">
          <div class="section-intro">
            <h2>
              我的 AI 工具箱 <span class="count-badge">{{ snapshot.tools.length }} 款工具</span>
            </h2>
            <span class="muted">{{
              isDemo ? '演示连接状态 · 非真实适配能力' : '实际连接状态以可采集数据为准'
            }}</span>
          </div>
          <div class="tool-grid">
            <article v-for="t in snapshot.tools" :key="t.id" class="panel tool-card">
              <div class="tool-card-top">
                <span :class="['tool-logo', t.id]"
                  >{{ toolInitial(t.id) }}<Terminal v-if="t.id === 'codex'" :size="14" /></span
                ><span :class="['status-tag', { connected: t.status === 'connected' }]"
                  ><span class="tiny-dot" :class="{ green: t.status === 'connected' }"></span
                  >{{
                    isDemo
                      ? '演示已连接'
                      : t.status === 'connected'
                        ? '已连接'
                        : t.status === 'empty'
                          ? '等待数据'
                          : '待适配'
                  }}</span
                >
              </div>
              <h2>{{ t.name }}</h2>
              <p>{{ t.description }}</p>
              <div class="tool-stats">
                <div>
                  <small>累计 Token</small
                  ><strong>{{
                    fmt(groupRows(snapshot.usage, 'tool').find((g) => g.name === t.id)?.total || 0)
                  }}</strong>
                </div>
                <div>
                  <small>使用模型</small
                  ><strong>{{
                    groupRows(snapshot.usage, 'tool').find((g) => g.name === t.id)?.models || '—'
                  }}</strong>
                </div>
              </div>
              <div class="tool-card-footer">
                <a :href="t.url" target="_blank" rel="noopener noreferrer"
                  >官方文档 <ExternalLink :size="14" /></a
                ><button
                  v-if="t.status === 'connected'"
                  class="text-button"
                  @click="viewTool(t.id)"
                >
                  查看分析 <ArrowRight :size="14" /></button
                ><span v-else class="muted small">暂不纳入使用量</span>
              </div>
            </article>
          </div>
          <section class="panel adapter-callout">
            <Blocks :size="27" />
            <div>
              <h2>一个工作台，多种 AI 工作流</h2>
              <p>
                Codex 与 Claude Code 已提供适配器，后续可以为 Trae 或 API
                网关扩展适配器，共用同一份快照协议。
              </p>
            </div>
            <button class="button secondary" @click="navigate('data')">
              了解数据协议 <ArrowUpRight :size="15" />
            </button>
          </section>
        </template>

        <template v-else-if="page === 'skills'">
          <div class="skill-summary">
            <div>
              <Blocks :size="22" /><strong>{{ snapshot.skills.length }}</strong
              ><span>已安装 Skills</span>
            </div>
            <div>
              <Layers :size="22" /><strong>{{ categories.length - 1 }}</strong
              ><span>能力分类</span>
            </div>
            <p>安装清单帮助你了解可用能力。<br /><strong>安装状态不等同于实际调用。</strong></p>
          </div>
          <div class="list-toolbar">
            <label class="search-field"
              ><Search :size="16" /><input
                v-model="query"
                placeholder="搜索 Skill 名称或描述…"
                aria-label="搜索 Skill" /></label
            ><select v-model="skillCategory" aria-label="Skill 能力分类">
              <option v-for="c in categories" :key="c">{{ c }}</option></select
            ><span class="muted small">{{ filteredSkills.length }} 个结果</span>
          </div>
          <div class="skills-grid">
            <article v-for="s in filteredSkills" :key="s.id" class="panel skill-card">
              <div class="skill-card-top">
                <span class="skill-icon"><Blocks :size="21" /></span
                ><span class="outline-badge">{{ s.category }}</span>
              </div>
              <h3>{{ s.name }}</h3>
              <p>{{ s.description }}</p>
              <div class="skill-card-footer">
                <span><span class="tiny-dot green"></span>已安装</span
                ><span class="mono">{{
                  s.source === 'system' ? 'SYSTEM' : s.source === 'demo' ? 'DEMO' : 'USER'
                }}</span>
              </div>
            </article>
          </div>
          <div v-if="!filteredSkills.length" class="empty-state">
            <Search :size="30" />
            <h3>没有匹配的 Skill</h3>
            <p>试试其他关键词或分类；本地清单只覆盖用户与系统 skills 目录。</p>
          </div>
          <p class="section-note">
            <CircleHelp :size="15" />MCP 用户级配置可在「MCP 配置」查看；Skill
            真实调用统计仍待扩展。
          </p>
        </template>

        <NewsRadarView v-else-if="page === 'radar'" :timezone="snapshot.timezone" />
        <McpInventoryView
          v-else-if="page === 'mcp'"
          :mode="snapshot.mode"
          :timezone="snapshot.timezone"
        />
        <template v-else-if="page === 'portfolio'">
          <NotebookToolbar :key="notebook.scope.value" :notebook="notebook" />
          <PortfolioView :key="notebook.scope.value" :notebook="notebook" :snapshot="snapshot" />
        </template>

        <template v-else-if="page === 'data'">
          <div class="data-grid">
            <section class="panel">
              <div class="panel-heading">
                <div>
                  <h2>数据连接</h2>
                  <p>本机处理，按需导出</p>
                </div>
                <ShieldCheck :size="21" class="green-text" />
              </div>
              <div class="connection-box">
                <span class="connection-icon"><Database :size="25" /></span>
                <div>
                  <strong>{{ sourceLabel }}</strong>
                  <p>
                    {{
                      isDemo
                        ? '当前没有读取本机数据'
                        : isLocal
                          ? 'Spring Boot + SQLite · 127.0.0.1'
                          : '浏览器内存 · 关闭页面后清除'
                    }}
                  </p>
                </div>
                <span :class="['status-tag', { connected: isLocal }]">{{
                  isLocal ? '已连接' : '只读浏览'
                }}</span>
              </div>
              <dl class="data-details">
                <div>
                  <dt>最近采集</dt>
                  <dd>{{ readableTime(snapshot.collection.collectedAt) }}</dd>
                </div>
                <div>
                  <dt>统计时区</dt>
                  <dd>{{ snapshot.timezone }}</dd>
                </div>
                <div>
                  <dt>读取日志文件</dt>
                  <dd>{{ snapshot.collection.files }} 个</dd>
                </div>
                <div>
                  <dt>跳过的不完整 / 无效行</dt>
                  <dd>{{ snapshot.collection.malformedLines }} 行</dd>
                </div>
                <div>
                  <dt>包含旧格式的文件</dt>
                  <dd>{{ snapshot.collection.legacyFiles }} 个</dd>
                </div>
                <div>
                  <dt>快照协议</dt>
                  <dd class="mono">v{{ snapshot.schemaVersion }}</dd>
                </div>
              </dl>
              <button
                class="button primary full-width"
                :disabled="loading"
                @click="connectLocal(true)"
              >
                <RefreshCw :size="16" :class="{ spinning: loading }" />{{
                  loading ? '正在读取…' : '同步本机 AI 数据'
                }}
              </button>
              <p class="small muted connection-hint">
                需要先启动本地后端。服务只监听回环地址，采集目录通过本机环境变量配置。
              </p>
            </section>
            <section class="panel">
              <div class="panel-heading">
                <div>
                  <h2>让足迹可以分享</h2>
                  <p>公开展示，只携带必要信息</p>
                </div>
                <LockKeyhole :size="21" class="green-text" />
              </div>
              <div class="privacy-item">
                <Check :size="17" />
                <div>
                  <strong>保留统计结果</strong>
                  <p>日期、模型、工具、Token 数量和匿名会话引用。</p>
                </div>
              </div>
              <div class="privacy-item">
                <Check :size="17" />
                <div>
                  <strong>匿名化项目与 Skills</strong>
                  <p>公开导出使用「项目 01」「Skill 01」等名称，隐藏实际名称和描述。</p>
                </div>
              </div>
              <div class="privacy-item">
                <Check :size="17" />
                <div>
                  <strong>排除敏感内容</strong>
                  <p>不包含对话正文、系统提示、密钥、本机完整路径或原始会话 ID。</p>
                </div>
              </div>
              <div class="privacy-item">
                <CircleHelp :size="17" />
                <div>
                  <strong>分享之前，再看一眼</strong>
                  <p>日期、模型名称与使用量本身仍会披露活动信息，导出不代表自动发布。</p>
                </div>
              </div>
              <div class="data-buttons">
                <button class="button secondary" :disabled="loading" @click="exportSnapshot">
                  <ArrowDownToLine :size="16" />导出公开快照</button
                ><button class="button secondary" @click="uploadInput?.click()">
                  <Upload :size="16" />导入快照
                </button>
              </div>
            </section>
          </div>
          <section class="panel rules-panel">
            <div class="panel-heading">
              <div>
                <h2>让每个数字，都有清楚的依据</h2>
                <p>统计口径与覆盖范围</p>
              </div>
              <BookOpen :size="20" class="muted" />
            </div>
            <ol>
              <li v-for="(rule, i) in snapshot.rules" :key="rule">
                <span>{{ String(i + 1).padStart(2, '0') }}</span>
                <p>{{ rule }}</p>
              </li>
            </ol>
            <div class="method-note">
              <strong>采集边界</strong>
              <p>
                按需重扫 Codex / Claude Code 日志，并在事务中替换统计结果。Codex response_id
                在跨文件范围去重；旧日志使用累计差值，旧格式分叉与部分特殊压缩记录可能存在偏差。发现无效尾行会计数跳过，文件不可读时保留原快照。
              </p>
            </div>
          </section>
        </template>

        <footer class="page-footer">
          <span><Fingerprint :size="15" />记录探索，保持好奇。</span>
          <div>
            <span>AI Footprint</span><span class="footer-dot">·</span
            ><button @click="navigate('data')">
              本地优先，公开可选 <ArrowUpRight :size="12" />
            </button>
          </div>
        </footer>
      </main>
    </div>
  </div>
  <input
    ref="uploadInput"
    type="file"
    accept=".json,application/json"
    class="visually-hidden"
    aria-label="导入公开快照文件"
    @change="importFile"
  />
</template>
