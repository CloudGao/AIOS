# 日历

查询日历、事件和重复事件实例。先 calendars 选可写日历；get 读取提醒；instances 用于有界时间窗口。update 会影响整个重复系列；editor/edit 打开界面不等于保存成功。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## calendar.query

用途：Read nondeleted events with ID pagination

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"},"after_id":{"default":0,"minimum":0,"type":"integer"}},"type":"object"}
```

## calendar.create

用途：Direct event/series save and readback. RRULE supports DAILY/WEEKLY/MONTHLY/YEARLY with INTERVAL,COUNT,UNTIL,BYDAY,BYMONTH,BYMONTHDAY,WKST. Update affects the full series.

模式：direct；写入：是；验证标记：unverified；前提：android.permission.READ_CALENDAR, android.permission.WRITE_CALENDAR。

```json
{"additionalProperties":false,"properties":{"begin_ms":{"minimum":0,"type":"integer"},"end_ms":{"minimum":0,"type":"integer"},"location":{"default":"","maxLength":2000,"type":"string"},"description":{"default":"","maxLength":16000,"type":"string"},"all_day":{"default":false,"type":"boolean"},"reminder_minutes":{"items":{"maximum":40320,"minimum":0,"type":"integer"},"maxItems":5,"type":"array"},"calendar_id":{"default":0,"minimum":0,"type":"integer"},"rrule":{"default":"","maxLength":500,"type":"string"}},"required":["title","begin_ms","end_ms"],"type":"object"}
```

## calendar.edit

用途：Open editor for selected ID; user saves

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## calendar.delete

用途：Delete selected ID under unified authorization

模式：direct；写入：是；验证标记：unverified；前提：android.permission.WRITE_CALENDAR。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## calendar.calendars

用途：List calendars with account and write-access level, paginated by ID

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"},"after_id":{"default":0,"minimum":0,"type":"integer"}},"type":"object"}
```

## calendar.get

用途：Read one event and its reminder rows

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":1,"type":"integer"}},"required":["id"],"type":"object"}
```

## calendar.editor.open

用途：Open native editor; save with UI control, then query to verify

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"begin_ms":{"minimum":0,"type":"integer"},"end_ms":{"minimum":0,"type":"integer"}},"required":["title","begin_ms","end_ms"],"type":"object"}
```

## calendar.update

用途：Direct event/series save and readback. RRULE supports DAILY/WEEKLY/MONTHLY/YEARLY with INTERVAL,COUNT,UNTIL,BYDAY,BYMONTH,BYMONTHDAY,WKST. Update affects the full series.

模式：direct；写入：是；验证标记：unverified；前提：android.permission.READ_CALENDAR, android.permission.WRITE_CALENDAR。

```json
{"additionalProperties":false,"properties":{"begin_ms":{"minimum":0,"type":"integer"},"end_ms":{"minimum":0,"type":"integer"},"location":{"default":"","maxLength":2000,"type":"string"},"description":{"default":"","maxLength":16000,"type":"string"},"all_day":{"default":false,"type":"boolean"},"reminder_minutes":{"items":{"maximum":40320,"minimum":0,"type":"integer"},"maxItems":5,"type":"array"},"id":{"minimum":1,"type":"integer"},"expected_begin_ms":{"minimum":0,"type":"integer"},"rrule":{"default":"","maxLength":500,"type":"string"}},"required":["title","begin_ms","end_ms","id","expected_begin_ms"],"type":"object"}
```

## calendar.instances

用途：Query occurrences (including existing recurring events) in a <=366-day window; cursor is last begin/event_id

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"begin_ms":{"minimum":0,"type":"integer"},"end_ms":{"minimum":0,"type":"integer"},"after_begin_ms":{"default":-1,"minimum":-1,"type":"integer"},"after_event_id":{"default":0,"minimum":0,"type":"integer"},"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"}},"required":["begin_ms","end_ms"],"type":"object"}
```

