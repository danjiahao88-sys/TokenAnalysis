<script setup lang="ts">
import { ref } from 'vue'
import {
  assistantBase,
  assistantToken,
  disconnectAssistant,
  pairAssistant
} from '../assistant-client'
import type { LiveStatus } from '../assistant-client'
defineProps<{ local: boolean; status?: LiveStatus; offline: boolean }>()
const emit = defineEmits<{ connected: []; disconnected: [] }>()
const code = ref(''),
  busy = ref(false),
  error = ref('')
async function pair() {
  busy.value = true
  error.value = ''
  try {
    await pairAssistant(code.value.trim())
    code.value = ''
    emit('connected')
  } catch (e) {
    error.value =
      e instanceof TypeError
        ? '无法连接助手。请确认已安装并运行，且浏览器允许本站访问本地网络。'
        : e instanceof Error
          ? e.message
          : '无法连接助手。请确认已安装并运行，且浏览器允许本地网络访问。'
  } finally {
    busy.value = false
  }
}
async function disconnect() {
  error.value = ''
  emit('disconnected')
  try {
    await disconnectAssistant()
  } catch {
    error.value = '当前浏览器已断开；可在本机助手中撤销全部授权。'
  }
}
</script>
<template>
  <section class="panel assistant-panel" aria-labelledby="assistant-title">
    <div class="assistant-heading">
      <div>
        <span class="eyebrow">YOUR COMPUTER · YOUR DATA</span>
        <h2 id="assistant-title">
          {{ local ? '本机助手 · 自动更新' : '把这台电脑的 AI 足迹连接进来。' }}
        </h2>
      </div>
      <span class="assistant-badge">{{
        local
          ? offline
            ? '连接中断 · 旧数据'
            : status?.state === 'error'
              ? '采集未完成'
              : '本机已连接'
          : assistantToken
            ? '已配对 · 当前浏览其他来源'
            : '首次连接'
      }}</span>
    </div>
    <template v-if="!assistantToken && (!local || offline)">
      <p>
        首次安装一次采集助手，以后打开本站自动重连。不需要 Java、Node.js
        或终端，用量保存在你的电脑。
      </p>
      <ol class="assistant-steps">
        <li>
          <strong>安装并打开助手</strong
          ><a
            href="https://github.com/danjiahao88-sys/TokenAnalysis/releases"
            target="_blank"
            rel="noreferrer"
            >前往 Windows 下载页 ↗</a
          ><small>仅使用标有采集助手的正式版本；未发布安装包时暂不可下载。</small>
        </li>
        <li>
          <strong>生成本机配对码</strong
          ><a :href="`${assistantBase}/assistant.html`" target="_blank" rel="noreferrer"
            >打开本机助手 ↗</a
          ><small>生成 8 位配对码，有效期 5 分钟。</small>
        </li>
        <li>
          <strong>授权当前浏览器</strong
          ><small>输入配对码；若浏览器询问本地网络权限，请允许本站。</small>
        </li>
      </ol>
      <form class="assistant-pair" @submit.prevent="pair">
        <label for="assistant-code"
          >8 位本机配对码<input
            id="assistant-code"
            v-model="code"
            inputmode="numeric"
            autocomplete="off"
            maxlength="8"
            pattern="[0-9]{8}"
            required
            placeholder="例如 12345678"
            :disabled="busy" /></label
        ><button class="button primary" :disabled="busy">
          {{ busy ? '正在连接…' : '连接我的电脑' }}
        </button>
      </form>
    </template>
    <template v-else
      ><p role="status" aria-atomic="true">
        {{
          offline
            ? '助手暂时离线，页面保留上次数据；恢复运行后会自动重连。'
            : status?.state === 'error'
              ? '最新采集未完成，当前显示上次成功的数据，请在本机助手检查日志读取状态。'
              : local
                ? '每 5 秒检查更新。工具写入用量日志后自动显示；不估算正在生成中的 Token。'
                : '已配对。点击上方「本机数据」可恢复自动更新。'
        }}
      </p>
      <div class="assistant-actions">
        <a :href="`${assistantBase}/assistant.html`" target="_blank" rel="noreferrer"
          >管理助手 / 设置开机启动 ↗</a
        ><button v-if="assistantToken" class="button secondary" @click="disconnect">
          断开并撤销本浏览器授权
        </button>
      </div></template
    >
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <small class="muted"
      >仅支持可读取的 Codex / Claude Code
      日志。浏览器或公司策略可能限制本地网络访问；不会自动上传数据或修改浏览器安全设置。</small
    >
  </section>
</template>
