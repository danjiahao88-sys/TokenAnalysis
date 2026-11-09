# 依赖与参考来源

核查日期：2026-11-05。公开仓库内容仅用于实现研究；未运行仓库中的安装脚本，未读取账号凭据或调用订阅配额接口。

## 研究结论

| 项目 / 固定修订 | 核查的具体源码 | 许可证 / 决策 |
|---|---|---|
| [ccusage/ccusage](https://github.com/ccusage/ccusage/tree/f674ef69401fa620351f6b163f4278991460e355) | `rust/adapters/codex/src/parser.rs`、`replay.rs`、`apps/ccusage/LICENSE` | MIT。参考请求身份、新旧记录重复与压缩记录的边界；未移植完整 Rust 状态机 |
| [getagentseal/codeburn](https://github.com/getagentseal/codeburn/tree/3faea16a60f162a80f9b874cff7a156855620898) | `src/codex-jsonl-fields.ts`、`src/codex-fork-replay.ts`、`LICENSE` | MIT。参考旧格式分叉历史的问题与隐私读取边界；未采用其配额/凭据读取或桌面框架 |
| ccusage 同一固定修订 | `rust/adapters/claude/src/daily.rs`、`paths.rs`、`docs/guide/weekly-reports.md` | MIT。研究 Claude 日志目录和自然周；本项目周一开始，不移植 CLI |
| codeburn 同一固定修订 | `src/providers/claude.ts`、`src/parser.ts` | MIT。研究缓存读/写口径与重复消息身份；自行实现 Java 适配器，未读取其凭据或运行脚本 |
| [jakearchibald/idb](https://github.com/jakearchibald/idb/tree/654c746bef13f9fe7f5871e03ac58015cebace5a) | README 的 openDB/事务示例、LICENSE；npm 8.0.4 | ISC。直接采用轻量 IndexedDB 封装，无云端存储依赖 |
| [Lumine-scence/trae-token-usage](https://github.com/Lumine-scence/trae-token-usage/tree/402eed437980c76aa75fa518a69241c737db0313) | README，自动方案需 capture_key_once.py | MIT。仅调研；需要提取解密密钥，未采用、未运行 |
| [islon/trae-token-tracker](https://github.com/islon/trae-token-tracker/tree/f66853c4b5e1bce32b0d22e2f42cc796be985398) | README 的演示/手动导入范围 | MIT。未发现可直接复用的自动日志适配器，未引入 |

采用 Java 独立适配器是为了支持本项目选定的 Spring Boot / SQLite 后端和较小的部署范围。使用标准 Jackson 读取 JSONL，而非重写 JSON 扫描器。当前支持范围小于上述成熟项目，在架构说明与页面中保留限制。

GitHub 全站代码搜索未使用（未要求账号或 token）。研究渠道为公开 GitHub API、固定版本 raw 源码与 npm registry。没有找到已安装 MCP 可直接完成本项目的本机日志统一统计。

## 第三期新增研究与采用

| 项目 / 固定版本或修订 | 具体核查与采用边界 | 许可 |
|---|---|---|
| [bobby-brennan/rss-parser](https://github.com/bobby-brennan/rss-parser/tree/394178530e87fa010efc6e8d5dd4e801715c5e63)，npm 3.13.0 | 核查 lib/parser.js 与 LICENSE；采用 parseString RSS/Atom 解析，自行限制 HTTP 大小 / 超时 / 来源；Atom 原始日期额外映射避免 updated 回填 pubDate | MIT |
| [tomlj/tomlj](https://github.com/tomlj/tomlj/tree/80c0ce8bcd12fcdb7c13aba6087a33dc3c0081e9)，Maven 2.2.0 | README、TomlTable.java、LICENSE；采用 TOML 规范解析，列表 key 保留带点的原始服务名；不执行配置 | Apache-2.0 |
| https-proxy-agent 7.0.6 | 核查 npm 版本、Node 兼容性与许可；仅构建抓取使用运行环境显式代理，不更改系统代理 | MIT |

候选 feedparser 的指定分支 API 查询失败（422），未采用；未运行第三方仓库安装脚本。个人展示沿用现有 idb / Zod 与浏览器下载，无额外 Markdown 渲染或第三方托管依赖。依赖原始许可证随 npm / Maven 包保留。

## 运行时依赖

### v0.4 采集助手

- Spring Framework **v6.2.12** 的 `spring-web/src/main/java/org/springframework/web/cors/DefaultCorsProcessor.java` 与 `CorsConfiguration`，Apache-2.0：直接采用已有 Spring CORS 处理器完成精确来源、方法、请求头和 PNA 预检；本项目只补配对授权，不自行拼接宽泛 CORS。
- OpenJDK **jdk-21.0.8-ga** 的 `src/jdk.jpackage/windows/classes/jdk/jpackage/internal/WindowsAppImageBuilder.java`（固定标签对象 `cb85c9f9dd00efac4f435061634cee46a62c3149`）、LICENSE，GPL-2.0 + Classpath Exception：核查原生入口与运行环境复制，使用 JDK 自带 jpackage，不复制源码，不分发本机 Oracle JDK。
- Eclipse Temurin **21.0.13+9 Windows x64 JRE**，官方 `adoptium/temurin21-binaries` 的固定 release；SHA256 `2332c0b4cff6bed722da3567c91d5e47568bcba863fa4945d0a8d6fc76158628`。采用其可分发运行时，保留原 LICENSE、NOTICE、legal 与依赖许可，源码与构建信息由该 [官方 release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9) 提供。
- WiX **3.14.1**，官方 `wixtoolset/wix3` release `wix3141rtm` 的 wix314-binaries.zip，实测 SHA256 `6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31`；MS-RL，保留包内 LICENSE。只用 candle / light 构建原生安装器，不运行第三方项目安装脚本，不修改 WiX 源码。
- 浏览器兼容依据：[Chrome 官方 Local Network Access 说明](https://developer.chrome.com/blog/local-network-access)，2026-11-09 核查，文档注明 Chrome 142 开始权限提示。仅用于确认网络权限与安全上下文边界；未要求用户关安全设置，不保证其他浏览器或企业策略可用。

未引入 Electron、Tauri 或其他桌面框架；安装包、锁、托盘和定时扫描采用现有 Java / Spring 标准能力。未使用新 MCP 或私有凭据；本次没有声称覆盖全站 GitHub 代码搜索。Windows 打包工作流仅手动构建并上传指定产物，不自动发布 Release。

Codex 历史索引字段语义另核查 [openai/codex 固定修订 c903541](https://github.com/openai/codex/tree/c90354172c5c49535b122443e9a8ed52c2233b80) 的 `codex-rs/state/src/extract.rs`（TokenCount 分支把 total_token_usage.total_tokens 写入 tokens_used）、`model/thread_metadata.rs`、`runtime/threads.rs` 与相关测试，以及根 LICENSE（Apache-2.0）。仅参考累计值与最后模型的证据边界，沿用现有 SQLite JDBC，不移植 Rust 实现，不将缺失日志的索引累计伪装为逐请求、逐模型或逐日账单。该结论来自开源源码与本机元数据，不是官方文档对该内部数据库的兼容保证。

| 依赖 | 许可 | 用途 |
|---|---|---|
| Vue 3 / Vite / TypeScript / vue-tsc / Vitest | MIT（TypeScript 为 Apache-2.0） | 界面、构建、类型检查和测试 |
| Apache ECharts | Apache-2.0 | 趋势与模型分布图 |
| Lucide Vue (`@lucide/vue`) | ISC；部分图标包含 MIT 来源 | 一致的 SVG 图标 |
| Zod | MIT | 不可信快照校验与字段白名单 |
| idb 8.0.4 | ISC | 浏览器实践、模型实验和备份事务持久化 |
| fake-indexeddb（仅开发） | Apache-2.0 | 在 Vitest 中验证真实 IndexedDB API 行为、来源隔离与导入事务 |
| DM Sans / IBM Plex Mono (`@fontsource/*`) | OFL-1.1 | 随包部署的拉丁字体 |
| Spring Boot / Spring Framework / Jackson | Apache-2.0 | 本机 API、JSON、事务与 JDBC |
| tomlj 2.2.0 | Apache-2.0 | 本机 Codex MCP 配置 TOML 解析 |
| rss-parser 3.13.0 / https-proxy-agent 7.0.6（构建期） | MIT | 公开新闻解析与已有代理适配 |
| Xerial SQLite JDBC | Apache-2.0 / BSD-2-Clause；内嵌 SQLite 为 public domain | 本机持久化 |

精确前端版本以 `frontend/package-lock.json` 为准；后端直接依赖以 `backend/pom.xml` 为准。各包附带的原始 LICENSE / NOTICE 继续适用。工具名、模型名与官方来源链接仅用于说明兼容性或导航，不表示这些厂商赞助本项目。
