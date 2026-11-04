# 依赖与参考来源

核查日期：2026-11-04。公开仓库内容仅用于实现研究；未运行仓库中的安装脚本，未读取账号凭据或调用订阅配额接口。

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

## 运行时依赖

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
| Xerial SQLite JDBC | Apache-2.0 / BSD-2-Clause；内嵌 SQLite 为 public domain | 本机持久化 |

精确前端版本以 `frontend/package-lock.json` 为准；后端直接依赖以 `backend/pom.xml` 为准。各包附带的原始 LICENSE / NOTICE 继续适用。工具名、模型名与官方来源链接仅用于说明兼容性或导航，不表示这些厂商赞助本项目。
