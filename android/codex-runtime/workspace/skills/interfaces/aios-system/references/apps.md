# 应用与安装

列出应用、查处理器、启动/管理应用、安装 APK。handlers 仅表明能解析 Intent，不保证应用正常。安装先 inspect 核实包名/哈希/兼容性，start 后 status，再 package.get 核实；queued 不代表已安装。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## apps.list

用途：先按 query 在全部已安装应用名称/包名中匹配，再按 offset/limit 分页；data 仍为数组，顶层 total/has_more/next_offset 表示结果范围。不能只查第一页就判定应用未安装。

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"query":{"default":"","maxLength":200,"type":"string"},"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"},"offset":{"default":0,"minimum":0,"type":"integer"}},"type":"object"}
```

## apps.launch

用途：Open app or details under unified authorization

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"package":{"pattern":"^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$","type":"string"},"mode":{"type":"string","enum":["foreground","background"]},"session_id":{"type":"string","minLength":1,"maxLength":100}},"required":["package","mode"],"type":"object"}
```

## apps.details

用途：Open app or details under unified authorization

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"package":{"pattern":"^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$","type":"string"}},"required":["package"],"type":"object"}
```

## maps.open

用途：Open map search

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"query":{"maxLength":1000,"type":"string"},"package":{"type":"string","maxLength":300},"mode":{"type":"string","enum":["foreground","background"]},"session_id":{"type":"string","maxLength":100}},"required":["query"],"type":"object"}
```

## browser.open

用途：Open http(s) URL under unified authorization

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"url":{"maxLength":4096,"pattern":"^https?://[^\\s]+$","type":"string"},"package":{"type":"string","maxLength":300},"mode":{"type":"string","enum":["foreground","background"]},"session_id":{"type":"string","maxLength":100}},"required":["url"],"type":"object"}
```

## apps.uninstall

用途：Open system uninstall flow after user review

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"package":{"pattern":"^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$","type":"string"}},"required":["package"],"type":"object"}
```

## apps.force_stop

用途：Authorized force-stop; control-critical packages are excluded

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"package":{"pattern":"^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$","type":"string"}},"required":["package"],"type":"object"}
```

## apps.handlers

用途：Resolve installed handlers for UI workflows without launching them; resolution is not a crash-free guarantee

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## apps.apk.inspect

用途：Read APK package/version, SHA256, signer metadata and SDK/ABI compatibility; installer validates signature

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"path":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"}},"required":["path"],"type":"object"}
```

## apps.package.get

用途：Verify installed package version and launcher availability

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"package":{"pattern":"^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$","type":"string"}},"required":["package"],"type":"object"}
```

## apps.install.start

用途：Queue base/split APK install with pinned hashes and package. Poll apps.install.status; queued does not mean installed.

模式：direct；写入：是；验证标记：unverified；前提：android.permission.INSTALL_PACKAGES。

```json
{"additionalProperties":false,"properties":{"paths":{"items":{"type":"string"},"maxItems":50,"minItems":1,"type":"array"},"sha256":{"items":{"pattern":"^[a-f0-9]{64}$","type":"string"},"maxItems":50,"minItems":1,"type":"array"},"expected_package":{"maxLength":255,"minLength":3,"type":"string"},"replace":{"default":false,"type":"boolean"},"expected_installed_version":{"default":-1,"minimum":-1,"type":"integer"}},"required":["paths","sha256","expected_package"],"type":"object"}
```

## apps.install.status

用途：Read durable install job, installer error or verified installed version

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"pattern":"^[a-f0-9-]{36}$","type":"string"}},"required":["id"],"type":"object"}
```


复用后台会话时先 ui.action list，再显式传 session_id。位置由宿主按照 ui.md 的小助手来源规则选择；主屏冲突由原生弹窗处理，不能把 mode 参数当作搬屏授权。handover_requested/handed_to_app 都不是业务成功，读回状态再报告。

用户指定应用时先 apps.list(query=应用名称)，用返回的真实 package。结果不唯一时按用户指定判断或询问，禁止猜包名。maps.open/browser.open 可显式传 package、mode、session_id。selection_required 表示多个处理器且尚未启动；返回 candidates 包含包名、标签和组件，不能解释为未安装或无效包名。maps.open 仅进行地点搜索；路线规划须进入选定地图应用的路线界面并观察确认，不把搜索结果当成路线已规划。

旧会话若专用工具 schema 尚未包含 query/package/mode，使用 aios_phone_action(capability="apps.list"/"maps.open"/"browser.open", arguments={...}) 调用当前能力，参数按最新文档填写；不需要丢弃原会话。


屏幕位置由宿主统一按 ui.md 规则决定：已有虚拟屏复用，当前小助手所在的目标主屏应用直接主屏操作，其余后台；主屏冲突弹原生选择框。mode 参数不授权搬屏。
