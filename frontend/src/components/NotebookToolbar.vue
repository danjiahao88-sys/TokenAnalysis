<script setup lang="ts">
import { ref } from 'vue'
import { downloadFile, MAX_BACKUP_BYTES } from '../notebook'
import type { Notebook } from '../useNotebook'
const props = defineProps<{ notebook: Notebook }>()
const file = ref<HTMLInputElement>(),
  message = ref(''),
  failed = ref(false),
  busy = ref(false)
function backup() {
  try {
    const value = JSON.stringify(props.notebook.backup(), null, 2)
    if (new Blob([value]).size > MAX_BACKUP_BYTES)
      throw new Error('档案过大，请减少记录内容后重试。')
    downloadFile(value, 'ai-footprint-notebook.json')
    message.value = '已导出档案备份，可能包含你填写的个人笔记。'
    failed.value = false
  } catch (error) {
    failed.value = true
    message.value = error instanceof Error ? error.message : '档案导出失败，请重试。'
  }
}
async function restore(event: Event) {
  const input = event.target as HTMLInputElement,
    selected = input.files?.[0]
  if (!selected) return
  busy.value = true
  try {
    if (selected.size > MAX_BACKUP_BYTES) throw new Error('文件超过 32 MB 限制。')
    const expectedScope = props.notebook.scope.value
    const result = await props.notebook.restore(await selected.text(), expectedScope)
    message.value = `导入 ${result.imported} 条，保留 ${result.skipped} 条已有较新记录。`
    failed.value = false
  } catch (error) {
    failed.value = true
    message.value =
      error instanceof Error && !error.message.startsWith('[')
        ? error.message
        : '备份格式无效，原有记录已保留。'
  } finally {
    busy.value = false
    input.value = ''
  }
}
</script>
<template>
  <aside class="notebook-toolbar panel">
    <div>
      <strong>{{
        notebook.scope.value === 'demo' ? '演示档案 · 虚构示例' : '个人档案 · 仅此浏览器'
      }}</strong>
      <p>实践与实验保存在当前站点的浏览器数据库。定期备份；切换数据来源时隔离记录。</p>
    </div>
    <div class="notebook-actions">
      <button class="button secondary" :disabled="!notebook.ready.value || busy" @click="backup">
        备份档案</button
      ><button
        class="button secondary"
        :disabled="!notebook.ready.value || busy"
        @click="file?.click()"
      >
        {{ busy ? '正在导入…' : '导入备份' }}
      </button>
    </div>
    <input ref="file" type="file" accept=".json,application/json" hidden @change="restore" />
    <p v-if="message" :role="failed ? 'alert' : 'status'" :class="{ 'form-error': failed }">
      {{ message }}
    </p>
    <p v-if="notebook.error.value" role="alert" class="form-error">{{ notebook.error.value }}</p>
  </aside>
</template>
