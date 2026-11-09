# 电话、短信、通知与剪贴板

读取通话/短信/通知或处理明确授权的通信动作。dial 只打开拨号器；sms.compose 由用户发送；notifications.reply 不保证送达。剪贴板受前台策略限制。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## phone.dial

用途：Open dialer; does not assert a call was placed

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"number":{"pattern":"^\\+?[0-9 ()-]{1,32}$","type":"string"}},"required":["number"],"type":"object"}
```

## phone.call

用途：Direct call intent; requires CALL_PHONE

模式：handoff；写入：是；验证标记：unverified；前提：android.permission.CALL_PHONE。

```json
{"additionalProperties":false,"properties":{"number":{"pattern":"^\\+?[0-9 ()-]{1,32}$","type":"string"}},"required":["number"],"type":"object"}
```

## phone.read

用途：Telephony capability and SIM state

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## clipboard.read

用途：Clipboard subject to foreground policy

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## notifications.read

用途：Active notifications subject to listener authorization

模式：direct；写入：否；验证标记：unverified；前提：notification_listener_access。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## sms.query

用途：Query rows in ID order; after_id paginates. contacts.query includes contacts without phone numbers; use contacts.get for details.

模式：direct；写入：否；验证标记：unverified；前提：android.permission.READ_SMS。

```json
{"additionalProperties":false,"properties":{"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"},"after_id":{"default":0,"minimum":0,"type":"integer"}},"type":"object"}
```

## calllog.query

用途：Query rows in ID order; after_id paginates. contacts.query includes contacts without phone numbers; use contacts.get for details.

模式：direct；写入：否；验证标记：unverified；前提：android.permission.READ_CALL_LOG。

```json
{"additionalProperties":false,"properties":{"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"},"after_id":{"default":0,"minimum":0,"type":"integer"}},"type":"object"}
```

## clipboard.write

用途：Write clipboard text

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"text":{"maxLength":16000,"type":"string"}},"required":["text"],"type":"object"}
```

## sms.compose

用途：Open SMS editor; user sends

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"number":{"pattern":"^\\+?[0-9 ()-]{1,32}$","type":"string"},"text":{"maxLength":16000,"type":"string"}},"required":["number","text"],"type":"object"}
```

## notifications.dismiss

用途：Dismiss notification by key after listener authorization

模式：handoff；写入：是；验证标记：unverified；前提：notification_listener_access。

```json
{"additionalProperties":false,"properties":{"key":{"maxLength":2048,"type":"string"}},"required":["key"],"type":"object"}
```

## notifications.reply

用途：Authorized RemoteInput reply; delivery not asserted

模式：handoff；写入：是；验证标记：unverified；前提：notification_listener_access。

```json
{"additionalProperties":false,"properties":{"key":{"maxLength":2048,"type":"string"},"text":{"maxLength":16000,"type":"string"}},"required":["key","text"],"type":"object"}
```

