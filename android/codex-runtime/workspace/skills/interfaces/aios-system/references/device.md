# 设备状态与诊断

查看设备、存储、电池、屏幕、输入法、安全状态或诊断问题。优先选择具体 read；需要综合状态时用 context.snapshot。诊断原始输出仅在排查时读取。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## system.context.read

用途：Android build, foreground app and media volume

模式：direct；写入：否；验证标记：verified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## device.info

用途：Device model/build and supported features

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## battery.read

用途：Battery/charging/temperature/power-save

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## display.read

用途：Brightness, rotation and font scale

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## storage.read

用途：Internal storage capacity

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## input.read

用途：Enabled input methods

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## security.read

用途：Lock state, no authentication secrets

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## settings.read

用途：Locale, timezone, clock

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## display.brightness.set

用途：Set brightness; automatic brightness may override

模式：direct；写入：是；验证标记：unverified；前提：write_settings_access。

```json
{"additionalProperties":false,"properties":{"value":{"maximum":255,"minimum":0,"type":"integer"}},"required":["value"],"type":"object"}
```

## usb.read

用途：Bounded diagnostic context; raw Android service output

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## windows.read

用途：Bounded diagnostic context; raw Android service output

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## power.stats

用途：Bounded diagnostic context; raw Android service output

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## diagnostics.read

用途：Bounded service diagnostic output

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"service":{"enum":["meminfo","cpuinfo","diskstats","thermalservice","battery","activity"],"type":"string"}},"required":["service"],"type":"object"}
```

## context.snapshot

用途：Combined low-sensitivity phone state with partial failure reporting

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## power.reboot

用途：Reboot under unified authorization; will disconnect bridge

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

