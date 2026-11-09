# 系统闹钟与计时器

操作系统 DeskClock 的实际记录；create/update/delete 后按 get/list 核实。需要定时执行 JS 程序时改用 aios-scheduler Skill，不把闹钟当程序调度。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## alarm.create

用途：Create and schedule in system DeskClock; returns persisted alarm

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"hour":{"maximum":23,"minimum":0,"type":"integer"},"minute":{"maximum":59,"minimum":0,"type":"integer"},"enabled":{"default":true,"type":"boolean"},"label":{"default":"AIOS","maxLength":200,"type":"string"},"repeat_bits":{"default":0,"maximum":127,"minimum":0,"type":"integer"}},"required":["hour","minute"],"type":"object"}
```

## timer.create

用途：Create actual DeskClock timer, optionally start

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"seconds":{"maximum":86400,"minimum":1,"type":"integer"},"label":{"default":"AIOS","maxLength":200,"type":"string"},"start":{"default":true,"type":"boolean"}},"required":["seconds"],"type":"object"}
```

## alarm.list

用途：Read actual system DeskClock records

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## timer.list

用途：Read actual system DeskClock records

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## alarm.get

用途：Read actual system DeskClock record

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## alarm.delete

用途：Delete actual system DeskClock record and cancel scheduling

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## timer.get

用途：Read actual system DeskClock record

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## timer.delete

用途：Delete actual system DeskClock record and cancel scheduling

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## alarm.update

用途：Replace alarm fields and reschedule system DeskClock instances

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"hour":{"maximum":23,"minimum":0,"type":"integer"},"minute":{"maximum":59,"minimum":0,"type":"integer"},"enabled":{"default":true,"type":"boolean"},"label":{"default":"AIOS","maxLength":200,"type":"string"},"repeat_bits":{"default":0,"maximum":127,"minimum":0,"type":"integer"},"id":{"minimum":1,"type":"integer"}},"required":["hour","minute","id"],"type":"object"}
```

## timer.update

用途：Change duration/label of stopped or paused timer

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"seconds":{"maximum":86400,"minimum":1,"type":"integer"},"label":{"default":"AIOS","maxLength":200,"type":"string"},"id":{"minimum":0,"type":"integer"}},"required":["seconds","id"],"type":"object"}
```

## timer.start

用途：Control actual system DeskClock timer

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## timer.pause

用途：Control actual system DeskClock timer

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## timer.reset

用途：Control actual system DeskClock timer

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

