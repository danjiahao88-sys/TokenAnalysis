import type { Experiment, Practice } from './notebook'

const at = '2026-11-04T08:00:00Z'
export const demoEntries: Practice[] = [
  {
    id: 'demo-practice-1',
    date: '2026-11-04',
    title: '从使用记录到一份可追溯的周报',
    category: '项目实践',
    project: 'AI Footprint',
    tool: 'Codex',
    model: 'gpt-5',
    summary:
      '尝试把分散的 Token 记录按周汇总。先统一统计时区，再区分缓存子集与总量，用相同时间窗口比较趋势。',
    outcome: '得到一份包含统计依据的 Markdown 周报；使用量与成果评价分开记录。',
    nextStep: '增加真实任务验证结果，让周报不止停留在消耗统计。',
    link: '',
    favorite: true,
    archived: false,
    createdAt: at,
    updatedAt: at
  },
  {
    id: 'demo-practice-2',
    date: '2026-11-02',
    title: '让模型对比从主观印象走向验证',
    category: '学习笔记',
    project: '模型实验室',
    tool: 'Claude Code',
    model: 'claude-sonnet-4',
    summary: '为两组模型提供相同的任务和验收标准，记录耗时、Token 与验证结论，未知用量保留为空。',
    outcome: '发现“回答看起来不错”与“通过测试”需要分别评价。',
    nextStep: '增加重复试验，记录不同提示词对结果的影响。',
    link: '',
    favorite: false,
    archived: false,
    createdAt: at,
    updatedAt: at
  },
  {
    id: 'demo-practice-3',
    date: '2026-10-29',
    title: '整理我的 Skills 能力地图',
    category: '工具探索',
    project: '个人知识库',
    tool: 'Codex',
    model: '',
    summary:
      '按设计、研究、文档和工作流整理已安装的 Skills，记录每项能力解决的问题，不将安装数量当作调用次数。',
    outcome: '形成一份便于检索的能力清单。',
    nextStep: '结合实际使用案例补充推荐场景。',
    link: '',
    favorite: true,
    archived: false,
    createdAt: at,
    updatedAt: at
  }
]
export const demoExperiments: Experiment[] = [
  {
    id: 'demo-experiment-1',
    date: '2026-11-03',
    title: '同一道 Java 边界问题，两种解法',
    task: '为一个分页计算函数修复空列表与越界页码问题，输出实现和对应的单元测试。两组使用相同题面。',
    criteria: '空列表返回空页；页码边界明确；提交的测试覆盖越界与整页边界。演示结果均为虚构。',
    trials: [
      {
        id: 'demo-trial-1',
        model: 'gpt-5',
        tool: 'Codex',
        inputTokens: 12500,
        outputTokens: 2400,
        durationSeconds: 68,
        verdict: 'pass',
        score: 4,
        notes: '虚构示例：测试覆盖了题目要求，仍需人工确认异常策略。'
      },
      {
        id: 'demo-trial-2',
        model: 'claude-sonnet-4',
        tool: 'Claude Code',
        inputTokens: 10300,
        outputTokens: 3100,
        durationSeconds: 74,
        verdict: 'pass',
        score: 4,
        notes: '虚构示例：解释较完整，补充了页码从 1 开始的约定。'
      }
    ],
    archived: false,
    createdAt: at,
    updatedAt: at
  }
]
