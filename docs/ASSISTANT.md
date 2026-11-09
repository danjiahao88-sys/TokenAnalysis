# AI Footprint 本机采集助手

Windows x64 首次安装一次，以后使用同一浏览器访问 https://danjiahao88-sys.github.io/TokenAnalysis/ 即可重连自己的电脑。网站不会跨设备显示其他人的用量。

## 普通用户

1. 从本项目 GitHub Releases 下载标有采集助手的 Windows 安装程序，校验同版本 SHA256SUMS；选择当前用户安装。也可解压便携包并双击 `AI Footprint Assistant.exe`。无需另装 Java、Node.js 或 Maven。
2. 从开始菜单或桌面打开助手，管理页将在默认浏览器打开。若未打开，访问 http://127.0.0.1:17890/assistant.html 。
3. 在本机管理页生成 8 位配对码（5 分钟有效、成功一次即失效），回到网站的连接区域输入。确认只在本项目网站输入，不向他人提供配对码。
4. 浏览器可能询问本地网络访问权限，允许本站后连接。此权限与配对授权分别生效；企业策略禁止访问时不能绕过。
5. 连接后每 5 秒检查更新；需等待工具实际写入日志，扫描与页面检查可能合计约 10 秒以上。没有有效日志的工具不计用量，正在生成中的 Token 不保证立即显示。
6. 在管理页选择「开启登录后自动启动」后，下次 Windows 登录助手在后台运行；此选项默认关闭。系统托盘可打开管理页或退出。未开启自动启动时，每次登录后需要双击启动一次。

网站只在第一次配对时保存该浏览器的授权令牌，不把用量持久化到网站或上传 GitHub。配对授权涵盖用量、项目短名称、Skills 安装清单、MCP 声明清单与独立历史索引；不包含聊天正文、密钥和 MCP 连接参数。手工笔记仍保存在当前站点的浏览器档案。

切换演示或公开快照会暂停自动读取；点击「本机数据」恢复。断线显示旧数据和连接状态，恢复助手后重试；清空浏览器数据、更换浏览器 / 域名需要重新配对。网站可撤销当前浏览器授权；本机管理页可撤销全部授权。助手离线时网站只能清除浏览器凭据，恢复后可在管理页撤销旧授权。

## 当前边界

- 仅 Windows x64 提供安装包；macOS / Linux 暂未提供免命令安装器。支持浏览器需允许 HTTPS 页面连接 HTTP 回环地址；以当前 Chrome / Edge 为优先验收目标，不承诺所有浏览器与企业策略兼容。
- 网站固定连接当前电脑的 127.0.0.1:17890，不支持任意内网 IP、远程设备或上传服务器。手机打开网站不能读取另一台电脑。
- 自定义域名需要在助手配置中加入精确 HTTPS Origin 并重新配对；只绑定域名不会自动获准。默认受信任线上来源为 `https://danjiahao88-sys.github.io`，来源授权按域名和端口生效，无法按 GitHub Pages 的仓库路径隔离。
- 安装器尚无代码签名证书；发布前需检查构建来源与校验值。不要关闭浏览器安全检查或系统防护以连接助手。
- 卸载前先在管理页关闭登录自动启动，避免留下失效的启动快捷方式。卸载程序不自动删除个人统计数据库。彻底清除数据前应自行备份，并删除助手的用户数据目录；无需为此修改 Codex / Claude 自身日志。
- 开发者不能把私人数据库、真实日志、配对码 / 令牌或真实截图打进安装包；构建输入仅包括后端 JAR、许可证与说明文档。

## 开发者打包

使用 JDK 21 的 jpackage、官方 Eclipse Temurin **21.0.13+9 Windows x64 JRE** 和 WiX **3.14.1**。JRE 保留 LICENSE、NOTICE、legal 目录，Java 依赖许可保留于 JAR。当前版本和校验值见 THIRD_PARTY.md，不分发本机 Oracle JDK。

```powershell
.\scripts\build-assistant.ps1 -RuntimeHome '<Temurin JRE 目录>' -WixHome '<WiX 目录>' -Build
```

产物在 Git 忽略的 `.local/assistant-release/<version>/`：当前用户安装 EXE、便携 ZIP、SHA256SUMS 和待验收 app-image。脚本不删除已有产物，重建时另选 `-OutputDirectory`。安装及发布是独立步骤，脚本不会安装到开发者电脑或上传 GitHub。

桌面模式的数据库与日志位于 Windows 当前用户 LocalAppData 下的 `AI Footprint/data`，与开发目录 `.local/footprint.db` 分开。使用当前用户的默认 Codex / Claude 目录，不随安装包携带开发者配置。仅验证时可用 `AIF_ASSISTANT_HOME` 指向专用临时目录，`AIF_CODEX_HOME` / `AIF_CLAUDE_HOME` 指向合成日志，`AIF_PORT` 指向专用回环端口；生产网站固定使用 17890。
