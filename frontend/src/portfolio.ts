import { profileSchema, safeHttps } from './portfolio-profile'
import type { PortfolioProfile } from './portfolio-profile'
import type { Practice, Experiment } from './notebook'
import { trialTotal } from './notebook'
import type { Snapshot } from './data'
import { summarize } from './data'

export const escapeHtml = (s: string) =>
  s.replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!
  )
const md = (s: string) =>
  s
    .replace(/\\/g, '\\\\')
    .replace(/[\x00-\x08\x0b-\x1f\x7f]/g, '')
    .replace(/[<>`*_\[\]#!|~]/g, '\\$&')
    .replace(/\r?\n/g, '\n\n')
const number = (v: number | null) => (v === null ? '未记录' : v.toLocaleString('en-US'))

export function buildPortfolio(
  input: PortfolioProfile,
  entries: Practice[],
  experiments: Experiment[],
  snapshot: Snapshot
) {
  const profile = profileSchema.parse(input)
  const selectedEntries = entries.filter((e) => !e.archived && profile.entryIds.includes(e.id))
  const selectedExperiments = experiments.filter(
    (e) => !e.archived && profile.experimentIds.includes(e.id)
  )
  const provenance =
    snapshot.mode === 'demo'
      ? '虚构示例 · 不代表个人经历'
      : snapshot.mode === 'local'
        ? '本机记录 · 用户手动选择分享'
        : '公开快照 · 用户手动选择分享'
  type Section = { title: string; paragraphs: string[]; link?: string }
  const sections: Section[] = selectedEntries.map((e) => ({
    title: e.title,
    paragraphs: [
      `${e.date} · ${e.category}`,
      [e.tool, e.model].filter(Boolean).join(' / '),
      e.summary,
      ...(e.outcome ? [`成果与验证：${e.outcome}`] : [])
    ].filter(Boolean),
    link: safeHttps(e.link) ? e.link : undefined
  }))
  for (const e of selectedExperiments)
    sections.push({
      title: `对比实验：${e.title}`,
      paragraphs: [
        `${e.date} · 人工记录，非标准基准测试`,
        `相同任务：${e.task}`,
        `验证标准：${e.criteria}`,
        ...e.trials.map(
          (t) =>
            `${t.model} / ${t.tool}；输入 ${number(t.inputTokens)}，输出 ${number(t.outputTokens)}，总 Token ${number(trialTotal(t))}；耗时（秒）${number(t.durationSeconds)}；人工结论 ${t.verdict === 'pass' ? '通过' : t.verdict === 'fail' ? '未通过' : '未测试'}；人工评分 ${number(t.score)}${t.notes ? `。备注：${t.notes}` : ''}`
        )
      ]
    })
  if (profile.showUsage) {
    const s = summarize(snapshot.usage)
    sections.push({
      title: 'AI 使用概览',
      paragraphs: [
        `统计时区：${snapshot.timezone}；快照生成：${snapshot.generatedAt}`,
        `全部快照记录：${snapshot.usage.length ? `${[...new Set(snapshot.usage.map((r) => r.date))].sort()[0]} 至 ${[...new Set(snapshot.usage.map((r) => r.date))].sort().at(-1)}` : '暂无使用量'}；总 Token ${number(s.total)}，输入 ${number(s.input)}，输出 ${number(s.output)}。缓存和推理为子集，不重复加总。`,
        `模型：${[...new Set(snapshot.usage.map((r) => r.model))].join('、') || '暂无记录'}。仅覆盖已采集日志，不代表账单或工作能力。`
      ]
    })
  }
  if (profile.showSkills)
    sections.push({
      title: 'Skills 探索',
      paragraphs: [
        snapshot.skills.map((s) => s.name).join('、') || '暂无已安装记录',
        '这里只表示已安装清单，不代表调用次数或掌握程度。'
      ]
    })
  const markdown =
    [
      `# ${md(profile.name)}`,
      md(profile.role),
      `> ${md(provenance)}`,
      md(profile.bio),
      ...(profile.repository
        ? [`项目链接：<${profile.repository.replace(/[<>\s]/g, (c) => encodeURIComponent(c))}>`]
        : []),
      ...sections.flatMap((s) => [
        `## ${md(s.title)}`,
        ...s.paragraphs.map(md),
        ...(s.link
          ? [`相关链接：<${s.link.replace(/[<>\s]/g, (c) => encodeURIComponent(c))}>`]
          : [])
      ]),
      '---',
      '由 AI Footprint 生成。仅包含勾选内容；实践与实验为用户手工记录，不自动推断个人成果。'
    ]
      .filter(Boolean)
      .join('\n\n') + '\n'
  const h = escapeHtml
  const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"><title>${h(profile.name)} · AI 实践档案</title><style>body{font:16px/1.85 system-ui,sans-serif;background:#f5f7f2;color:#233c30;margin:0;padding:32px 20px}main{max-width:860px;margin:auto}header,section{padding:28px;background:#fff;border:1px solid #dce4dc;border-radius:16px;margin-bottom:20px;overflow-wrap:anywhere}h1{font-size:36px;margin:0}h2{font-size:22px}p{white-space:pre-wrap}small,footer{color:#586e60}a{color:#246343}a:focus-visible{outline:3px solid #3d8058}footer{padding:16px}@media(max-width:500px){body{padding:16px 10px}header,section{padding:20px}h1{font-size:28px}}@media print{body{background:#fff;padding:0}header,section{break-inside:avoid;border:0;border-bottom:1px solid #ddd}a{color:inherit}}</style></head><body><main><header><small>AI PRACTICE PORTFOLIO</small><h1>${h(profile.name)}</h1><p>${h(profile.role)}</p><small>${h(provenance)}</small><p>${h(profile.bio)}</p>${profile.repository ? `<a href="${h(profile.repository)}" target="_blank" rel="noopener noreferrer">查看项目 ↗</a>` : ''}</header>${sections.map((s) => `<section><h2>${h(s.title)}</h2>${s.paragraphs.map((p) => `<p>${h(p)}</p>`).join('')}${s.link ? `<a href="${h(s.link)}" target="_blank" rel="noopener noreferrer">相关链接 ↗</a>` : ''}</section>`).join('')}<footer>由 AI Footprint 生成。实践与实验为用户手工记录，不自动推断个人成果。</footer></main></body></html>`
  return {
    markdown,
    html,
    entries: selectedEntries.length,
    experiments: selectedExperiments.length
  }
}
