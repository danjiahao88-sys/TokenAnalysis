"""Reproducible fictional dataset, never reads the user's machine."""
import datetime as dt
import json
import pathlib
import random

rng = random.Random(2048)
end = dt.date(2026, 11, 4)
usage = []
models = [("codex", "gpt-5"), ("claude-code", "claude-sonnet-4"), ("trae", "DeepSeek-V3"), ("codex", "gpt-5-mini")]
projects = ["AI Footprint", "个人知识库", "开源贡献", "Java 学习笔记", "创意实验室"]
for day in range(90):
    date = end - dt.timedelta(days=89-day)
    if rng.random() < 0.12:
        continue
    for session in range(rng.randint(2, 6)):
        tool, model = rng.choices(models, weights=[45, 27, 18, 10])[0]
        input_tokens = rng.randint(12000, 68000) + day * 250
        output_tokens = rng.randint(2000, 16000)
        usage.append(dict(date=date.isoformat(), tool=tool, model=model, project=rng.choice(projects),
                          sessionRef=f"demo-{day}-{session}", inputTokens=input_tokens,
                          cachedInputTokens=int(input_tokens*rng.uniform(.3, .8)), outputTokens=output_tokens,
                          reasoningOutputTokens=int(output_tokens*.24) if tool == "codex" else 0,
                          totalTokens=input_tokens+output_tokens))

skills = [
    ("search-first", "检索与研究", "先检索成熟开源实现，再选择依赖、适配或实现。"),
    ("ui-ux-pro-max", "设计与创作", "界面布局、交互设计、可访问性与视觉规范。"),
    ("memory", "开发与工作流", "记录持续协作中的偏好、决策与项目上下文。"),
    ("openai-docs", "检索与研究", "查阅官方开发文档与 API 使用说明。"),
    ("documents", "文档与数据", "创建和编辑文档，并进行排版检查。"),
    ("spreadsheets", "文档与数据", "分析表格、编写公式、制作可视化。"),
    ("imagegen", "设计与创作", "探索图像创作与视觉资产生成。"),
    ("skill-creator", "开发与工作流", "将可复用的工作流程整理为 Skill。"),
    ("pdf", "文档与数据", "PDF 提取、生成与页面检查。"),
    ("find-skills", "检索与研究", "发现适合当前任务的能力扩展。"),
    ("presentations", "文档与数据", "组织演示内容和幻灯片。"),
    ("code-review", "开发与工作流", "从正确性与可维护性角度审查代码。")
]
data = dict(schemaVersion=1, mode="demo", generatedAt="2026-11-04T08:00:00Z", timezone="Asia/Shanghai",
            collection=dict(collectedAt=None, files=0, malformedLines=0, unreadableFiles=0, legacyFiles=0), usage=usage,
            tools=[dict(id=key, name=name, status="connected", description="虚构示例数据，用于体验跨工具统计；不代表真实采集能力。", url=url)
                   for key, name, url in [("codex", "Codex", "https://developers.openai.com/codex/"),
                                         ("claude-code", "Claude Code", "https://code.claude.com/docs/"),
                                         ("trae", "Trae", "https://www.trae.cn/")]],
            skills=[dict(id=f"demo-skill-{i}", name=name, description=desc, category=cat, source="demo", status="installed")
                    for i, (name, cat, desc) in enumerate(skills)],
            rules=["当前全部用量、工具连接状态与 Skills 均为虚构演示，非本机真实记录。",
                   "总 Token = 输入 + 输出；缓存输入、推理输出分别是输入和输出的子集。",
                   "演示中的跨工具用量只用于展示界面；本地第一版实际支持 Codex。",
                   "Token 不能直接换算为生产力；没有计价依据时不展示推算账单。",
                   "Skills 清单表示安装状态，不代表调用次数。"])
target = pathlib.Path(__file__).resolve().parents[1] / "frontend/src/demo.json"
target.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
print(f"Generated {len(usage)} fictional sessions")
