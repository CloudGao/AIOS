# 事件、定位与传感器

需要新位置时用 location.current；location.read 只有带时间戳的旧位置。流是有期限、进程生命周期内采样，使用 events.read/poll 消费并处理 gap/重启；结束后 stop/unsubscribe。订阅不是后台推送或持久调度。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## events.subscribe

用途：Subscribe to supported event topics; use events.poll

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"topics":{"items":{"enum":["android.intent.action.BATTERY_CHANGED","android.intent.action.SCREEN_","android.intent.action.ACTION_POWER_","notification.","sensor.","location.","workspace."],"type":"string"},"maxItems":7,"minItems":1,"type":"array"}},"required":["topics"],"type":"object"}
```

## location.read

用途：Last known locations with timestamps; does not request a fresh fix

模式：direct；写入：否；验证标记：unverified；前提：android.permission.ACCESS_COARSE_LOCATION。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## sensors.list

用途：Sensor capabilities

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## events.read

用途：Bounded native event queue: battery, screen, power and notification metadata

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"after":{"default":0,"minimum":0,"type":"integer"}},"type":"object"}
```

## events.poll

用途：Consume events since stored cursor; reports gaps and process restarts

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"subscription":{"pattern":"^[a-f0-9-]{36}$","type":"string"}},"required":["subscription"],"type":"object"}
```

## events.unsubscribe

用途：Stop event subscription

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"subscription":{"pattern":"^[a-f0-9-]{36}$","type":"string"}},"required":["subscription"],"type":"object"}
```

## sensors.sample

用途：Read one sensor sample within 3 seconds

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"type":{"maximum":100,"minimum":1,"type":"integer"}},"required":["type"],"type":"object"}
```

## location.current

用途：Request fresh location, wait <=10s; reports no-fix rather than substituting stale location

模式：direct；写入：否；验证标记：unverified；前提：android.permission.ACCESS_FINE_LOCATION。

```json
{"additionalProperties":false,"properties":{"provider":{"default":"gps","enum":["gps","network","fused"],"type":"string"}},"type":"object"}
```

## location.stream

用途：Start expiring location stream; consume location.sample through events.read/poll

模式：direct；写入：是；验证标记：unverified；前提：android.permission.ACCESS_FINE_LOCATION。

```json
{"additionalProperties":false,"properties":{"seconds":{"default":60,"maximum":3600,"minimum":1,"type":"integer"},"interval_ms":{"default":1000,"maximum":60000,"minimum":100,"type":"integer"},"provider":{"default":"gps","enum":["gps","network","fused"],"type":"string"}},"type":"object"}
```

## sensors.stream

用途：Start expiring sensor stream; consume sensor.sample through events.read/poll

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"seconds":{"default":60,"maximum":3600,"minimum":1,"type":"integer"},"interval_ms":{"default":1000,"maximum":60000,"minimum":100,"type":"integer"},"type":{"maximum":100,"minimum":1,"type":"integer"}},"required":["type"],"type":"object"}
```

## streams.list

用途：List active process-lifetime sampling streams

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## streams.stop

用途：Stop sampling stream

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"pattern":"^[a-f0-9-]{36}$","type":"string"}},"required":["id"],"type":"object"}
```

