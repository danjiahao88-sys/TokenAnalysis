import { groupRows, lastDate, shiftDate, summarize } from './data'
import type { Snapshot } from './data'
import type { Experiment, Practice } from './notebook'

export function monday(date: string) {
  const day = new Date(`${date}T12:00:00Z`).getUTCDay()
  return shiftDate(date, -(day + 6) % 7)
}
export function weekOptions(snapshot: Snapshot) {
  const start = monday(lastDate(snapshot))
  return Array.from({ length: 12 }, (_, i) => shiftDate(start, -7 * i))
}
export function weeklyReport(
  snapshot: Snapshot,
  start: string,
  entries: Practice[],
  experiments: Experiment[]
) {
  const latest = lastDate(snapshot),
    fullEnd = shiftDate(start, 6)
  const end = latest < fullEnd ? latest : fullEnd
  const inRange = (date: string) => date >= start && date <= end
  const rows = snapshot.usage.filter((r) => inRange(r.date))
  const previous = snapshot.usage.filter(
    (r) => r.date >= shiftDate(start, -7) && r.date <= shiftDate(end, -7)
  )
  const stats = summarize(rows),
    before = summarize(previous)
  return {
    start,
    end,
    fullEnd,
    partial: end < fullEnd,
    stats,
    delta: before.total ? (stats.total / before.total - 1) * 100 : null,
    models: groupRows(rows, 'model'),
    projects: groupRows(rows, 'project'),
    entries: entries.filter((e) => !e.archived && inRange(e.date)),
    experiments: experiments.filter((e) => !e.archived && inRange(e.date))
  }
}
const plain = (value: string) =>
  value.replace(/[\r\n]+/g, ' ').replace(/[\\`*_{}\[\]<>#|]/g, (char) => `\\${char}`)
export function reportMarkdown(snapshot: Snapshot, report: ReturnType<typeof weeklyReport>) {
  const s = report.stats,
    n = (value: number) => value.toLocaleString('en-US')
  return (
    `# AI 使用周报 · ${report.start}\n\n` +
    `数据来源：${snapshot.mode === 'demo' ? '虚构演示，不代表真实实践与实验' : snapshot.mode === 'local' ? '本机日志统计' : '导入的公开快照'}\n\n` +
    `统计时区：${snapshot.timezone}；范围：${report.start} 至 ${report.end}${report.partial ? '（本周尚未完整，仅截至快照最新活动日）' : ''}。\n\n` +
    `- 总 Token：${n(s.total)}（输入 ${n(s.input)}，输出 ${n(s.output)}）\n- 缓存读取：${n(s.cached)}，已包含在输入内\n- 独立会话：${s.sessions}；活跃天数：${s.activeDays}\n- 与上周对应天数相比：${report.delta === null ? '缺少基期数据，无法计算' : `${report.delta >= 0 ? '+' : ''}${report.delta.toFixed(1)}%`}\n\n` +
    `## 模型用量\n\n${report.models.map((m) => `- ${plain(m.name)}：${n(m.total)} Token`).join('\n') || '暂无记录'}\n\n` +
    `## 项目用量\n\n${report.projects.map((p) => `- ${plain(p.name)}：${n(p.total)} Token`).join('\n') || '暂无记录'}\n\n` +
    `## 实践记录（手工填写）\n\n${report.entries.map((e) => `### ${plain(e.title)}\n\n${plain(e.summary)}\n\n成果：${plain(e.outcome) || '未填写'}\n\n下一步：${plain(e.nextStep) || '未填写'}`).join('\n\n') || '暂无记录'}\n\n` +
    `## 模型实验（人工验证）\n\n${report.experiments.map((e) => `### ${plain(e.title)}\n\n任务：${plain(e.task)}\n\n验收标准：${plain(e.criteria)}\n\n${e.trials.map((t) => `- ${plain(t.model)} / ${plain(t.tool)}：${{ pass: '通过', fail: '未通过', untested: '未验证' }[t.verdict]}；输入 ${t.inputTokens ?? '未知'}，输出 ${t.outputTokens ?? '未知'}；耗时 ${t.durationSeconds ?? '未知'} 秒；评分 ${t.score ?? '未评价'}/5。${plain(t.notes)}`).join('\n')}`).join('\n\n') || '暂无记录'}\n\n` +
    `---\n仅覆盖当前快照中可读取的日志，不等于账单或生产力。缓存与推理为子集，不重复累计。实践和实验来自当前浏览器的独立记录，未自动调用模型；导出可能包含个人笔记，请自行决定分享范围。\n`
  )
}
