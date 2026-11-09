# 授权、设置与请求

检查权限、特殊授权、打开设置及处理原生请求。权限查询不授予权限；permissions.request 走实际 UI，不用 shell grant。requests 的原生请求 ID 与 MCP action_id 分别按真实返回值使用。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## permissions.read

用途：Agent manifest permissions and grant state

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## settings.open

用途：Open selected system settings

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"page":{"enum":["main","wifi","bluetooth","display","sound","location","accessibility","default_apps","notifications","dnd","battery","date","language","input","storage","write_settings","usage"],"type":"string"},"mode":{"type":"string","enum":["foreground","background"]},"session_id":{"type":"string","minLength":1,"maxLength":100}},"required":["page","mode"],"type":"object"}
```

## permissions.request

用途：Queue runtime permission UI, no shell grant

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"permissions":{"items":{"enum":["android.permission.READ_CONTACTS","android.permission.WRITE_CONTACTS","android.permission.WRITE_CALENDAR","android.permission.READ_CALENDAR","android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_VISUAL_USER_SELECTED","android.permission.CALL_PHONE","android.permission.READ_PHONE_STATE","android.permission.READ_SMS","android.permission.READ_CALL_LOG","android.permission.ACCESS_COARSE_LOCATION","android.permission.ACCESS_FINE_LOCATION","android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_SCAN","android.permission.RECORD_AUDIO","android.permission.POST_NOTIFICATIONS","android.permission.READ_MEDIA_AUDIO","android.permission.ACCESS_MEDIA_LOCATION"],"type":"string"},"maxItems":19,"minItems":1,"type":"array"}},"required":["permissions"],"type":"object"}
```

## requests.get

用途：Read native user-request result

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"ticket":{"pattern":"^[a-f0-9-]{36}$","type":"string"}},"required":["ticket"],"type":"object"}
```

## requests.cancel

用途：Cancel an unstarted native user request

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"ticket":{"pattern":"^[a-f0-9-]{36}$","type":"string"}},"required":["ticket"],"type":"object"}
```

## permissions.special

用途：Read actual notification, DND, settings and storage special-access state

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```


UI 操作默认 mode=background，在虚拟屏创建或复用目标应用会话。用户说打开、搜索、点击、查看，都不意味着授权占用真实主屏。查看或接管使用原虚拟屏卡片；只有用户明确要求真实主屏，或实际确认虚拟屏不支持且用户同意时才用 foreground/handover。waiting_for_app 不是切换主屏的理由；目标正在主屏使用时保留后台请求并说明需要先让应用进入后台，不偷偷降级。复用后台会话时先 ui.action list，再显式传 session_id。handover_requested/handed_to_app 都不是已验证业务成功，读回状态再报告。
