# 架构与数据契约

## 范围

本项目是本机单用户工具和静态公开演示的组合。Vue 用量展示依赖 v1 快照；Java 负责日志解析、Skills 清单、SQLite 持久化和脱敏导出。第二期增加独立的浏览器实践/实验档案与本地规则周报。第三期增加独立的公开动态管线、本机 MCP 配置只读 API 和可选个人展示导出。没有云端账户系统、计费服务或个人数据自动上传。

## 模块

| 模块 | 职责 |
|---|---|
| `McpInventory` | tomlj / Jackson 解析固定用户配置，只返回声明白名单，不连接服务 |
| `CodexHistoryInventory` / `CodexHistoryView` | 只读桌面索引，独立展示原日志缺失的会话累计参考，不参与请求统计 |
| `news.mjs` / `NewsRadarView` | 有界抓取固定官方 RSS/Atom，发布公开快照、日期与失败状态 |
| `portfolio-profile.ts` / `portfolio.ts` / `PortfolioView` | 个人展示配置、明确选择、同一数据投影预览与安全导出 |
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
| GET | `/api/inventory` | 本机 MCP 配置声明与来源读取状态，no-store，不包含连接参数 |
| GET | `/api/codex-history` | 独立 Codex 历史索引参考，no-store，不进入用量快照 |
| GET | `/api/snapshot` | 本机聚合快照，不含完整路径和聊天正文 |
| GET | `/api/export` | 公共字段白名单快照，带附件响应头 |

原 `/api/` 没有 CORS 放行，供本机前端同源代理与管理页使用。v0.4 仅独立 `/bridge/` 按精确来源开放受控 CORS，私有数据仍要求配对令牌。所有路由保留回环 Host 校验，不应自行改为公网监听。

## 网站与本机助手（v0.4）

网站静态部署与采集服务解耦。网站固定请求当前设备 `http://127.0.0.1:17890/bridge/`，不接受任意内网地址，不向云端发送用量。安装包使用 jpackage 原生 Windows 入口，携带可分发 Temurin JRE 与后端 JAR；无需用户 Java / Node / Maven。桌面模式数据库和日志在当前用户 LocalAppData 独立目录，文件锁避免同一目录重复启动；可选系统托盘与用户显式开启的登录启动快捷方式。源码模式保持原 `.local` 数据位置。

`AssistantAccess` 的 8 位随机码只能由本机管理页通过带自定义头的本地请求生成，5 分钟有效，成功一次即失效，每分钟最多 5 次配对尝试，重发码不重置限制。`/bridge/pair` 只接受配置的完整 Origin，成功发 256 位随机令牌；SQLite `assistant_grant` 仅保存 SHA-256 哈希、来源和创建时间，最多 20 个浏览器授权。每次私有读取都校验 Origin + Authorization；没有 Cookie、URL 令牌、通配来源或 credentials CORS。`/bridge/health` 仅有产品、协议与版本，不返回用量；本机管理可撤销全部，网站可撤销当前授权。

`AssistantBridgeFilter` 复用 Spring `DefaultCorsProcessor` / `CorsConfiguration`，方法与请求头白名单、精确允许来源、兼容旧 PNA 预检。默认网站为项目 GitHub Pages 所在 Origin；授权按 Origin 而非仓库路径。自定义 HTTPS 域名由 `AIF_ALLOWED_ORIGINS` 显式配置并重新配对。Chrome 142 起的本地网络权限仍由浏览器决定；CORS / 配对不绕过该权限，不修改浏览器安全选项。全部页面禁用 iframe，管理页 CSP 限制本地脚本与样式，接口 no-store，连接请求不带 Cookie、不跟随跳转。

`LiveCollection` 每 5 秒比较日志 / Skill 的文件名、大小与修改时间指纹，变化时复用原事务采集器，每 60 秒强制重扫以覆盖元数据未变化的改写。同一时刻只运行一个扫描，失败保留原数据与最近成功时间，状态独立为 waiting / collecting / ready / error。现版本仍完整重扫请求日志，未实现增量解析或全部格式兼容。

网站每 5 秒读取已授权状态，在成功采集时间变化时更新快照；页面隐藏时暂停轮询。自动更新不重置当前筛选。来源切换 / 卸载使用 generation 与 AbortController 阻止旧请求覆盖；演示和导入快照暂停自动读取。凭据仅存在当前网站浏览器存储，不持久化用量；401 清除失效令牌，离线展示上次数据并重试。新访问时有有效授权且未选择其他来源则自动重连。日志写入、扫描、页面检查各有延迟，不宣称逐 Token 实时或云端全账号覆盖。

桥接路由：GET health / status / snapshot / export / inventory / codex-history；POST pair / collect / disconnect。status 与所有统计接口需授权，只有 health 与提交配对码可未授权访问；旧 `/api/` 继续不放行跨站。

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

IndexedDB 名称 `ai-footprint-notebook`，版本 2（从 v1 原位新增 profiles store），entries / experiments 使用 `[scope,id]` 复合主键，metadata 只记录演示 seed。scope 为 demo、local 或 `public:<generatedAt>`；不同来源切换清空当前视图再读取，使用 generation 防止旧异步读覆盖新来源。演示 fixture 只首次初始化；不会重置访问者的演示编辑。

备份 `format: ai-footprint-notebook-v1` 包含 exportedAt、provenance（demo/personal）、entries、experiments、可选 profile；旧备份缺少 profile 时不覆盖已有配置；Zod 剥离未知字段，校验日历、长度、HTTPS 链接、实验组身份、整数计数。未知用量、耗时、评分为 null；通过率分母仅为 pass/fail，不含 untested。归档可恢复，不物理删除，归档不进入周报或实验统计。

单空间最多 500 条实践、300 个实验，归档计入；UI 备份导入上限 32 MB。保存及合并在事务内先校验容量，整份备份先验证再写入。相同 ID 只接收 updatedAt 较新版本，按时间戳值比较；演示和个人档案禁止混合。文件读取期间若切换来源，拒绝继续导入。不是多人协作系统，没有跨设备自动同步与冲突日志。

档案保存在当前浏览器当前 origin；清除站点数据会丢失，需手动备份。用量快照只在页面内存，独立档案不进入 `/api/export`。备份与 Markdown 包含用户手工笔记，不自动脱敏，也不自动上传。公开快照重新导入相同 generatedAt 可找回该档案；不同 generatedAt 进入新空间。

周报依据快照统计时区的日期字段，周一至周日，截止快照最新活动日。未完整周只与上周相同天数比较，无基期则不显示百分比。无用量时按 generatedAt 在统计时区的日期选周。实践与实验按各自手工日期关联，只输出统计与手工内容，不调用付费 API，不推断生产力或自动模型评分。

## 第三期独立数据流

### Codex 历史索引补充（v0.3.1）

默认 Codex home 下按数字选择最新 `state_*.sqlite`，使用既有 SQLite JDBC 只读连接与 `query_only`，不创建或修改 Codex 索引。只选择 threads 的模型、累计计数、原日志位置和更新时间；日志位置仅用于核验，不返回。索引文件本身的符号链接不跟随。索引格式与未来版本并非稳定公共协议，不兼容时整次返回状态，不回退旧库冒充当前结果。

最多检查按更新排序的 1,000 个会话，多读一条判断 truncated。日志路径必须为配置 home 的 sessions / archived_sessions 内绝对路径；兼容 Windows 扩展路径前缀。仍有常规原日志的会话排除；路径无法核验、非法或空计数记 skippedRows，不补零。只对确定缺失的日志按最后模型归组，返回会话数、索引累计 Token 与最近索引更新。更新日期不是请求日期。无标题、正文、原会话身份或完整路径；不持久化到项目用量表。

官方源码表明 tokens_used 来自 total_token_usage.total_tokens 的最后观察值，不能恢复逐请求拆分；模型只是会话最后记录值，可能含模型切换及分叉复制，不能全量归属该模型。前端 Zod 检查安全整数、唯一模型和来源计数一致性，切换来源取消旧请求并清空。仅本机模型页可见，复用模型搜索与工具过滤，不应用日期窗口；不并入 snapshot、趋势、周报、公开导出或个人展示。

### MCP 声明

`/api/inventory` 复用回环 Host 防护与同源代理，不修改 v1 用量快照。扫描 Codex home 下 config.toml 和 AIF_CLAUDE_CONFIG（默认用户 .claude.json）；仅读顶层用户声明，Claude projects 中的路径键完全忽略。输入限 1 MB；NOFOLLOW_LINKS 打开文件，每来源最多 200 个 server；解析失败整来源不半成功。状态区分 ready / missing / invalid / unreadable，无原始异常内容。服务端只构造 id、短名称、tool、source、transport、enabled；id 为来源加原名称的 SHA-256 短标识；名称不符合短标签字符集时替换。没有环境变量替换、命令执行或网络探测。响应不持久化，不进入公开导出。配置中的短名称本身也可能带个人语义，用户仍应自行判断分享范围。

### 公开新闻

Node https 有界获取三个固定源，rss-parser 3.13.0 只负责 parseString；请求拒绝跳转、15 秒期限、4 MB 上限，拒绝 XML DOCTYPE / ENTITY。链接按源 HTTPS 前缀白名单，标题作为文本显示，不渲染 feed HTML。Atom 使用自定义 rawPublished/rawUpdated，绕开库默认将 updated 回填 pubDate 的行为。每源最多 20 条，按有效日期排序并按 URL 去重。快照 ai-footprint-news-v1 分离 generatedAt、checkedAt、lastSuccessAt、publishedAt / updatedAt；错误保留验证后的上次内容，不能将本次检查当作抓取成功。

静态文件 public/news.json 只含公开信息。Pages 在 push / 手动 / 每日两次调度中读取官方源，失败优先回退到已发布新闻，再回退仓库快照；没有自动 commit。调度不保证准时，前端按 48 小时阈值与各来源失败显示状态。运行时不读本机日志、不使用用户 API Key；按钮只重读静态文件。AIF_NEWS_PROXY 或 HTTPS_PROXY 可指定运行环境已有代理，代理值和抓取异常不输出到产物。

### 个人展示

profiles 以 scope 为主键，与 entries / experiments 一起来源隔离。保存先 Zod 白名单，备份保留可选 profile，较新 updatedAt 合并；旧 v1 数据迁移只新增 store，不重建原记录。演示配置默认使用虚构名片，个人默认空白且所有可选统计关闭。entryIds / experimentIds 明确选择，已归档或不存在的身份不导出。用量可选项仅输出整个当前快照的日期、模型和汇总，不输出项目和会话；Skills 仅可选安装名称，无 MCP 信息。

buildPortfolio 同时生成 Markdown 与独立 HTML，HTML 预览使用 sandbox 空权限 srcdoc；所有用户文字转义，链接仅允许无 userinfo 的 HTTPS。HTML 带 default-src none CSP 与内联样式，无脚本或外部请求资源。Markdown 转义文本和链接边界；未知指标仍显示未记录，人工实验结论不推断。导出只下载到本机，不修改线上默认页；手工笔记不做自动内容脱敏。

## 发布边界

Git 忽略 `.local`、SQLite、目标包、依赖目录与环境文件。Pages 只发布前端 dist。构建不读取真实日志；虚构示例由固定随机种子生成，`mode: demo` 不会在导出时改成 `public`。Fonts 随站点构建，无第三方字体请求。

公开导出保留日期、模型、工具、Token、匿名项目引用、匿名 Skills 类别与来源。它是最小化分享，不是统计匿名性证明；仍需使用者判断活动信息是否适合公开。
