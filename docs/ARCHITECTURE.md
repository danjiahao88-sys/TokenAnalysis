# 架构与数据契约

## 范围

第一版是本机单用户工具和静态公开演示的组合。Vue 展示层只依赖 v1 快照；Java 负责日志解析、Skills 清单、SQLite 持久化和脱敏导出。没有云端账户系统、计费服务或自动上传。

## 模块

| 模块 | 职责 |
|---|---|
| `CodexCollector` | 流式逐行读取会话文件，提取模型与计数，合并新旧格式并去重 |
| `SkillInventory` | 只读取 Skill 前置元数据窗口，不执行 Skill，不扫描历史插件缓存 |
| `FootprintStore` | SQLite schema、事务替换和读取 |
| `SnapshotService` | 指定时区按日期/会话/模型/项目聚合，构造公开字段白名单 |
| `LocalHostFilter` / `ApiController` | 回环地址服务、Host 校验、写操作自定义头与 Origin 校验 |
| `frontend/src/data.ts` | Zod 契约、日期窗口、独立会话数、分组统计 |
| `DataChart.vue` | 按需注册 ECharts、ResizeObserver、销毁清理、减少动画偏好 |

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

## 发布边界

Git 忽略 `.local`、SQLite、目标包、依赖目录与环境文件。Pages 只发布前端 dist。构建不读取真实日志；虚构示例由固定随机种子生成，`mode: demo` 不会在导出时改成 `public`。Fonts 随站点构建，无第三方字体请求。

公开导出保留日期、模型、工具、Token、匿名项目引用、匿名 Skills 类别与来源。它是最小化分享，不是统计匿名性证明；仍需使用者判断活动信息是否适合公开。
