<div align="center">

# AI Footprint

**让每一次 AI 探索，看得见。**

一个本地优先的个人 AI 使用分析工作台。记录 Token、模型与项目足迹，整理 Skills、实践与实验，生成使用周报。

Vue 3 · TypeScript · ECharts · Java 21 · Spring Boot · SQLite

[在线演示](https://danjiahao88-sys.github.io/TokenAnalysis/) · [源码](https://github.com/danjiahao88-sys/TokenAnalysis) · [构建状态](https://github.com/danjiahao88-sys/TokenAnalysis/actions)

</div>

![AI Footprint 演示仪表盘](docs/images/dashboard.jpg)

## 可以做什么

- **使用总览**：输入 / 输出趋势、缓存占比、活跃会话、13 周热力图、项目排行。
- **模型分析**：按工具和 7 / 30 / 90 天筛选，查看模型用量、会话及缓存占比。
- **工具箱**：真实连接状态与适配边界；本地采集支持 Codex、Claude Code，没有有效日志时显示等待数据。
- **Skills 工作台**：扫描用户与系统 Skills 安装清单，支持搜索和能力分类。
- **探索雷达**：OpenAI、Anthropic、DeepMind、DeepSeek、Qwen、Hugging Face 官方来源导航。
- **数据与隐私**：本机同步、采集覆盖提示、脱敏快照导出、浏览器内快照导入。
- **公开演示**：不需要后端即可部署到 GitHub Pages；示例数据始终标记为虚构。
- **AI 使用周报**：周一自然周、未完整周的等长环比，汇总用量与实践，导出 Markdown；不额外调用模型。
- **实践时间线**：记录任务、过程、成果、证据链接与下一步；支持搜索、分类、收藏、编辑、归档及恢复。
- **模型实验室**：同题、同验收标准记录 2–6 组模型的用量、耗时与人工评价；未知值留空，未验证组不计入通过率。

当前版本 v0.2.0。包含周报、实践档案、模型实验与 Claude Code 适配；发布与验收记录见 [项目进度](docs/STATUS.md)。

这里的 Token 是日志统计，不是账单，也不是生产力分数。Skills 安装数不代表调用次数。探索雷达目前是来源导航，不是实时新闻流。

## 快速体验

环境：Node.js 22.12+、JDK 21、Maven 3.9+。仅看演示只需要 Node.js。

```sh
npm --prefix frontend ci
npm run dev
```

访问 `http://127.0.0.1:5173`，默认显示虚构示例，不自动读取本机数据。

### 连接本机数据

另开终端：

```sh
cd backend
mvn verify
java -Xmx256m -jar target/ai-footprint-0.2.0.jar
```

回到页面点击「本机数据」。后端启动时采集一次；后续在「数据与隐私」点击同步。服务只监听 `127.0.0.1:17890`，SQLite 位于项目 `.local/`，被 Git 忽略。

Windows 也可分别运行：

```powershell
.\scripts\start-backend.ps1
.\scripts\start-web.ps1
```

可选环境变量（启动后端前配置）：

| 变量 | 默认值 | 用途 |
|---|---|---|
| `AIF_CODEX_HOME` | 用户目录下 `.codex` | 仅读取 `sessions`、`archived_sessions` 与 `skills` |
| `AIF_CLAUDE_HOME` | 用户目录下 `.claude` | 仅读取 `projects` 内的会话及子代理 JSONL 计数元数据 |
| `AIF_DATABASE` | `../.local/footprint.db` | SQLite 路径，相对 backend 工作目录 |
| `AIF_TIMEZONE` | `Asia/Shanghai` | 日期聚合时区 |
| `AIF_PORT` | `17890` | 本地接口端口；调整后同步修改 Vite 代理 |

首次连接时缺失的工具目录视为无数据；已有用量的目录消失或任一文件不可读时保留此前快照。部分无效 JSONL 行会跳过并显示数量。本机尚无 Claude Code 日志，适配器以合成夹具验证，未声称已完成真实 Claude 会话联调。

### 保存实践与实验

三个新页面共用当前站点的浏览器 IndexedDB。刷新或关闭标签后仍保留；清除站点数据、换浏览器、换域名或端口不会自动迁移。演示、本机、每份公开快照分别保存；演示档案与个人档案不能相互导入。公开快照的档案以其 `generatedAt` 区分，新导入不同时间的快照会进入独立空间。

点击「备份档案」下载 JSON，再用「导入备份」恢复；同 ID 保留 `updatedAt` 较新的版本。每个空间最多 500 条实践、300 个实验，归档仍占容量；备份导入上限 32 MB。档案备份和周报可能含你手工填写的私人笔记，不包含在用量公开快照中，也不会自动上传到 GitHub。模型实验为手工记录，不自动运行模型。

![第二期虚构周报预览](docs/images/weekly-report.jpg)

## 部署到 GitHub Pages

当前仓库的在线演示已部署到 [GitHub Pages](https://danjiahao88-sys.github.io/TokenAnalysis/)。Fork 后可按以下步骤发布自己的版本：

1. 将项目上传到自己的 GitHub 仓库，默认分支使用 `main`。
2. 仓库 **Settings → Pages → Source** 选择 **GitHub Actions**。
3. 执行 **Deploy demo to GitHub Pages** 工作流，或向 `main` 推送前端变更。

构建使用相对资源路径，兼容仓库子路径；页面导航采用 hash。Actions 只上传 `frontend/dist`，不会运行采集器或携带本机数据库。

公开站点默认展示虚构示例。你可以在本地导出公开快照，在公开站点用「数据与隐私 → 导入快照」浏览；导入仅发生在访问者浏览器内，不会把文件上传或自动保存为全站默认数据。

GitHub Pages 上的「本机数据」不会跨站连接你的本地服务。真实采集需要运行本机前后端。

## 数据怎么计算

```text
Codex / Claude Code JSONL / Skills frontmatter
             │  只保留计数与必要标签
             ▼
Java 采集适配器 → SQLite（本机）→ /api/snapshot → Vue 分析工作台
                                      │
                                 /api/export
                                      ▼
                         脱敏 JSON → 浏览器内导入
```

- 总 Token = 输入 + 输出。缓存 Token 已包含在输入中，推理 Token 已包含在输出中。
- 优先读取 `token_usage_record.usage`，按 `response_id` 跨文件去重。
- 对历史 `event_msg.token_count` 使用累计差值、处理计数重置；通过对应累计快照排除与新格式的重复计数。
- Claude Code 的输入归一化为非缓存输入 + 缓存读 + 缓存写；缓存命中只计缓存读。相同 message / request 的流式片段与复制日志按计数最大值合并，不逐片叠加。
- 模型来自请求记录或当时的 `turn_context`；缺失时标记 `unknown`，不按日期猜模型。
- 项目仅保留 `cwd` 最后一段名称；不同完整路径的同名目录会合并。
- 日志重新扫描后在事务内替换统计，重复同步不会叠加。第一版未做文件增量索引，日志很多时扫描耗时会增加。
- 旧格式分叉历史缺少全局响应身份，特殊压缩/历史改写可能有偏差；详见页面统计说明。仅覆盖本机可读日志，不宣称覆盖全部账号。

公开导出匿名化项目与 Skills 名称，排除对话、完整路径、原始会话 ID 和密钥。日期、模型与用量仍是活动信息，由使用者决定是否分享。

## 开发与验证

```sh
npm run test
npm run build
cd backend
mvn verify
```

GitHub CI 同时验证前端和后端。数据协议、技术决策和当前进度分别见 [架构说明](docs/ARCHITECTURE.md)、[项目进度](docs/STATUS.md) 与 [依赖及参考来源](docs/THIRD_PARTY.md)。

```text
frontend/         Vue 界面、ECharts 图表、Zod 快照校验与示例数据
backend/          Java 日志适配器、SQLite、只监听本机的 API
scripts/          Windows 启动入口、可复现虚构数据生成器
docs/             架构、统计边界、交付状态与演示截图
.github/workflows/ CI 和 GitHub Pages 部署
.local/           运行数据库与本机验证资料（不入 Git）
```

## 接下来

- Trae 等更多可验证的采集适配器；目前调研到的自动方案依赖解密密钥提取，暂不采用。
- 带来源时间与抓取时间的官方动态聚合。
- Claude Code 真实会话联调、更多版本与分叉格式覆盖。
- 增量采集、多设备合并、用户选择的公开字段。
- MCP 清单与有调用证据的 Skill 使用分析。

## License

MIT。依赖保留各自许可证；见 [第三方说明](docs/THIRD_PARTY.md)。
