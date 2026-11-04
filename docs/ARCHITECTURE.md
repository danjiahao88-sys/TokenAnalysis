# 架构与数据契约

## 范围

本项目是本机单用户工具和静态公开演示的组合。Vue 用量展示依赖 v1 快照；Java 负责日志解析、Skills 清单、SQLite 持久化和脱敏导出。第二期增加独立的浏览器实践/实验档案与本地规则周报。没有云端账户系统、计费服务或自动上传。

## 模块

| 模块 | 职责 |
|---|---|
| `CodexCollector` | 流式逐行读取会话文件，提取模型与计数，合并新旧格式并去重 |
| `ClaudeCollector` | 读取 projects JSONL 元数据，包含子代理，缓存归一化及重复 message/request 合并 |
| `SkillInventory` | 只读取 Skill 前置元数据窗口，不执行 Skill，不扫描历史插件缓存 |
| `FootprintStore` | SQLite schema、事务替换和读取 |
| `SnapshotService` | 指定时区按日期/会话/模型/项目聚合，构造公开字段白名单 |
| `LocalHostFilter` / `ApiController` | 回环地址服务、Host 校验、写操作自定义头与 Origin 校验 |
| `frontend/src/data.ts` | Zod 契约、日期窗口、独立会话数、分组统计 |
| `DataChart.vue` | 按需注册 ECharts、ResizeObserver、销毁清理、减少动画偏好 |
| `notebook.ts` / `notebook-db.ts` / `useNotebook.ts` | 独立 Zod 档案契约、idb 存储、来源隔离、较新版本导入及异步来源切换保护 |
| `weekly-report.ts` | 周一自然周、快照日期截止、等长环比、关联非归档记录、Markdown 白名单文本输出 |
| `WeeklyReportView` / `PracticeTimelineView` / `ExperimentLabView` | 第二期独立页面；档案工具栏共用备份导入导出 |

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 版本与存活状态 |
| POST | `/api/collect` | 扫描配置的本机目录，成功后事务替换；要求 `X-AI-Footprint: local` |
| GET | `/api/snapshot` | 本机聚合快照，不含完整路径和聊天正文 |
| GET | `/api/export` | 公共字段白名单快照，带附件响应头 |

没有 CORS 放行。前端开发和预览通过 Vite 同源代理访问。固定回环绑定与 Host 校验用于避免误暴露和 DNS rebinding。并非多用户授权系统，不应自行改为公网监听。

## v1 JSON

顶层为 `schemaVersion: 1`、`mode: demo | local | public`、`generatedAt`、`timezone`、`collection`、`usage`、`tools`、`skills`、`rules`。

`usage` 每行包含：

```json
{
  "date": "2026-11-04",
  "tool": "codex",
  "model": "example-model",
  "project": "项目 01",
  "sessionRef": "s1",
  "inputTokens": 100,
  "cachedInputTokens": 40,
  "outputTokens": 20,
  "reasoningOutputTokens": 5,
  "totalTokens": 120
}
```

`sessionRef` 在一份快照内有效：跨日期/模型汇总会话时必须去重，不能简单对分组会话数求和。公开导出与本机页面都从原会话 ID 映射到不透明的短引用。导出不包含原始 event ID。

浏览器导入限制 5 MB、最多 50,000 行，校验真实日历日期、整数安全范围、Token 子集关系、合计与工具关联。只接受 `demo` 和 `public`，删除未知字段；仅保存在页面内存。当前不合并多个快照，不提供跨快照会话身份保证。

时间筛选以快照最新活动日为终点，而不是访问者当前日期；界面始终展示「截至」日期，保证历史演示可以复现。环比使用紧邻的等长前一周期；无基期记录则不显示百分比。

## 计数选择

请求级记录以 `response_id` 去重。旧计数每遇到相同累计值不会新增事件；输入或输出累计回落视为新计数段。新格式对应的 thread 累计值或同时间同用量匹配用于抑制旧事件。

第一版没有照搬成熟项目的全部复杂分叉与压缩状态机：

- 旧格式缺少响应 ID，跨不同 session 的复制历史不能完全去重。
- 特殊压缩/历史重写的记录语义可能不同，尚未穷尽适配。
- 同一响应后续修订用量时保留首条有效记录；不保证覆盖未来格式。
- 文件读取失败不会替换此前快照，JSON 行解析失败会跳过并计数。整个文件若被截短但仍是合法 JSON，无法自动推断丢失范围。
- 采集采用重扫而非增量 checkpoint，成本随日志规模增长。

这些限制在 README 和页面上明确说明。新增适配器应先取得真实格式及回归夹具，不能仅凭工具安装或活跃进程估算 Token。

### Claude Code 与迁移

`AIF_CLAUDE_HOME` 默认用户目录下 `.claude`，只扫描 `projects/**/*.jsonl`，不读取认证文件。仅处理 assistant message 的用量、日期、模型、会话和 cwd 末段；不存正文。原 `Usage` 默认工具为 Codex，增加 tool 字段；启动时通过 SQLite table_info 检查旧表，缺少 tool 才追加默认 codex 的非空列，不删表。快照聚合键含工具，sessionRef 映射以工具+原会话身份隔离。

Claude 输入 = `input_tokens + cache_read_input_tokens + cache_creation_input_tokens`；顶层缓存写缺失时才取 `ephemeral_5m_input_tokens + ephemeral_1h_input_tokens`。cachedInputTokens 仅取缓存读；推理用量无独立证据时为零，不额外加总输出。message.id 与 requestId 构成身份，重复或流式累计片段取各项最大值，首次日期归属；缺失模型标 unknown，缺失消息身份或非法计数跳过并计入无效行。没有实现压缩归档格式或所有未来版本，缺 requestId 与带 requestId 的异构副本不能保证合并。

两种工具重扫后在同一事务替换用量。任一文件读取失败，或已有用量的目录消失，不替换此前快照；首次无某工具目录允许为空。工具连接状态以实际有效用量为准。当前 Claude 只有合成夹具证据，本机无真实日志，不能据此宣称所有 Claude 版本已兼容。

## 浏览器档案协议

IndexedDB 名称 `ai-footprint-notebook`，版本 1，entries / experiments 使用 `[scope,id]` 复合主键，metadata 只记录演示 seed。scope 为 demo、local 或 `public:<generatedAt>`；不同来源切换清空当前视图再读取，使用 generation 防止旧异步读覆盖新来源。演示 fixture 只首次初始化；不会重置访问者的演示编辑。

备份 `format: ai-footprint-notebook-v1` 包含 exportedAt、provenance（demo/personal）、entries、experiments；Zod 剥离未知字段，校验日历、长度、HTTPS 链接、实验组身份、整数计数。未知用量、耗时、评分为 null；通过率分母仅为 pass/fail，不含 untested。归档可恢复，不物理删除，归档不进入周报或实验统计。

单空间最多 500 条实践、300 个实验，归档计入；UI 备份导入上限 32 MB。保存及合并在事务内先校验容量，整份备份先验证再写入。相同 ID 只接收 updatedAt 较新版本，按时间戳值比较；演示和个人档案禁止混合。文件读取期间若切换来源，拒绝继续导入。不是多人协作系统，没有跨设备自动同步与冲突日志。

档案保存在当前浏览器当前 origin；清除站点数据会丢失，需手动备份。用量快照只在页面内存，独立档案不进入 `/api/export`。备份与 Markdown 包含用户手工笔记，不自动脱敏，也不自动上传。公开快照重新导入相同 generatedAt 可找回该档案；不同 generatedAt 进入新空间。

周报依据快照统计时区的日期字段，周一至周日，截止快照最新活动日。未完整周只与上周相同天数比较，无基期则不显示百分比。无用量时按 generatedAt 在统计时区的日期选周。实践与实验按各自手工日期关联，只输出统计与手工内容，不调用付费 API，不推断生产力或自动模型评分。

## 发布边界

Git 忽略 `.local`、SQLite、目标包、依赖目录与环境文件。Pages 只发布前端 dist。构建不读取真实日志；虚构示例由固定随机种子生成，`mode: demo` 不会在导出时改成 `public`。Fonts 随站点构建，无第三方字体请求。

公开导出保留日期、模型、工具、Token、匿名项目引用、匿名 Skills 类别与来源。它是最小化分享，不是统计匿名性证明；仍需使用者判断活动信息是否适合公开。
