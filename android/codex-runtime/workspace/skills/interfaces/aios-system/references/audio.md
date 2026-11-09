# 音频与录音

调节音量、选择通信音频路由、控制播放或录音。音量是整数档位，不是百分比；写入前读当前值。录音 start/record 后以 status/stop 核实文件状态。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## system.audio.media.get

用途：Read media volume

模式：direct；写入：否；验证标记：verified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## system.audio.media.adjust

用途：Adjust media volume with stale-state protection

模式：direct；写入：是；验证标记：verified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"delta":{"maximum":15,"minimum":-15,"type":"integer"},"expected_current":{"minimum":0,"type":"integer"}},"required":["delta","expected_current"],"type":"object"}
```

## audio.read

用途：Volume streams, ringer and output devices

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## audio.set

用途：Set audio stream using expected volume precondition

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"stream":{"enum":[0,2,3,4,5],"type":"integer"},"value":{"maximum":100,"minimum":0,"type":"integer"},"expected":{"maximum":100,"minimum":0,"type":"integer"}},"required":["stream","value","expected"],"type":"object"}
```

## microphone.record

用途：Record microphone to a new MPEG4/AAC file with bounded duration

模式：direct；写入：是；验证标记：unverified；前提：android.permission.RECORD_AUDIO。

```json
{"additionalProperties":false,"properties":{"path":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"},"seconds":{"default":60,"maximum":600,"minimum":1,"type":"integer"}},"required":["path"],"type":"object"}
```

## media.sessions

用途：Bounded diagnostic context; raw Android service output

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## media.control

用途：Dispatch media key

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"key":{"enum":["play","pause","play-pause","stop","next","previous"],"type":"string"}},"required":["key"],"type":"object"}
```

## dnd.read

用途：Current interruption filter and policy grant

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## dnd.set

用途：Set interruption filter after policy access

模式：direct；写入：是；验证标记：unverified；前提：notification_policy_access。

```json
{"additionalProperties":false,"properties":{"filter":{"enum":[1,2,3,4],"type":"integer"}},"required":["filter"],"type":"object"}
```

## audio.routes

用途：List communication audio routes and current selected device

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## audio.route.set

用途：Select communication audio route; ID 0 restores system default

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## microphone.start

用途：Record microphone to a new MPEG4/AAC file with bounded duration

模式：direct；写入：是；验证标记：unverified；前提：android.permission.RECORD_AUDIO。

```json
{"additionalProperties":false,"properties":{"path":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"},"seconds":{"default":60,"maximum":600,"minimum":1,"type":"integer"}},"required":["path"],"type":"object"}
```

## microphone.status

用途：Read active recorder and output state

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## microphone.stop

用途：Finalize recording and expose output file

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

