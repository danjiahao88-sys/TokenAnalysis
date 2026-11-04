# 依赖与参考来源

核查日期：2026-11-04。公开仓库内容仅用于实现研究；未运行仓库中的安装脚本，未读取账号凭据或调用订阅配额接口。

## 研究结论

| 项目 / 固定修订 | 核查的具体源码 | 许可证 / 决策 |
|---|---|---|
| [ccusage/ccusage](https://github.com/ccusage/ccusage/tree/f674ef69401fa620351f6b163f4278991460e355) | `rust/adapters/codex/src/parser.rs`、`replay.rs`、`apps/ccusage/LICENSE` | MIT。参考请求身份、新旧记录重复与压缩记录的边界；未移植完整 Rust 状态机 |
| [getagentseal/codeburn](https://github.com/getagentseal/codeburn/tree/3faea16a60f162a80f9b874cff7a156855620898) | `src/codex-jsonl-fields.ts`、`src/codex-fork-replay.ts`、`LICENSE` | MIT。参考旧格式分叉历史的问题与隐私读取边界；未采用其配额/凭据读取或桌面框架 |

采用 Java 独立适配器是为了支持本项目选定的 Spring Boot / SQLite 后端和较小的部署范围。使用标准 Jackson 读取 JSONL，而非重写 JSON 扫描器。当前支持范围小于上述成熟项目，在架构说明与页面中保留限制。

GitHub 全站代码搜索未使用（未要求账号或 token）。研究渠道为公开 GitHub API、固定版本 raw 源码与 npm registry。没有找到已安装 MCP 可直接完成本项目的本机日志统一统计。

## 运行时依赖

| 依赖 | 许可 | 用途 |
|---|---|---|
| Vue 3 / Vite / TypeScript / vue-tsc / Vitest | MIT（TypeScript 为 Apache-2.0） | 界面、构建、类型检查和测试 |
| Apache ECharts | Apache-2.0 | 趋势与模型分布图 |
| Lucide Vue (`@lucide/vue`) | ISC；部分图标包含 MIT 来源 | 一致的 SVG 图标 |
| Zod | MIT | 不可信快照校验与字段白名单 |
| DM Sans / IBM Plex Mono (`@fontsource/*`) | OFL-1.1 | 随包部署的拉丁字体 |
| Spring Boot / Spring Framework / Jackson | Apache-2.0 | 本机 API、JSON、事务与 JDBC |
| Xerial SQLite JDBC | Apache-2.0 / BSD-2-Clause；内嵌 SQLite 为 public domain | 本机持久化 |

精确前端版本以 `frontend/package-lock.json` 为准；后端直接依赖以 `backend/pom.xml` 为准。各包附带的原始 LICENSE / NOTICE 继续适用。工具名、模型名与官方来源链接仅用于说明兼容性或导航，不表示这些厂商赞助本项目。
