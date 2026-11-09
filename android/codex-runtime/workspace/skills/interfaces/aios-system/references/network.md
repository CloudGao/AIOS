# 网络、蓝牙与连接

查看或调整 Wi-Fi、移动数据、热点及蓝牙连接。先 read 取得 expected 状态，提交后 read 回验；硬件不支持应报告 not_supported。蓝牙配对可能需要对端确认。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## network.read

用途：Active network transports, validation and metering

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## bluetooth.read

用途：Bluetooth adapter and paired devices

模式：direct；写入：否；验证标记：unverified；前提：android.permission.BLUETOOTH_CONNECT。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## network.wifi.read

用途：Bounded diagnostic context; raw Android service output

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## wifi.set

用途：Request radio state change with expected-state guard

模式：handoff；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"enabled":{"type":"boolean"},"expected_enabled":{"type":"boolean"}},"required":["enabled","expected_enabled"],"type":"object"}
```

## bluetooth.set

用途：Request radio state change with expected-state guard

模式：handoff；写入：是；验证标记：unverified；前提：android.permission.BLUETOOTH_CONNECT。

```json
{"additionalProperties":false,"properties":{"enabled":{"type":"boolean"},"expected_enabled":{"type":"boolean"}},"required":["enabled","expected_enabled"],"type":"object"}
```

## wifi.read

用途：Read Wi-Fi enabled/state; use to verify wifi.set completion

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## hotspot.read

用途：Read actual platform state; absent hardware/SIM returns not_supported

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## hotspot.set

用途：Request platform state transition with expected-state guard; read back to verify

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"enabled":{"type":"boolean"},"expected_enabled":{"type":"boolean"}},"required":["enabled","expected_enabled"],"type":"object"}
```

## mobile_data.read

用途：Read actual platform state; absent hardware/SIM returns not_supported

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"subscription_id":{"default":-1,"minimum":-1,"type":"integer"}},"type":"object"}
```

## mobile_data.set

用途：Request platform state transition with expected-state guard; read back to verify

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"subscription_id":{"default":-1,"minimum":-1,"type":"integer"},"enabled":{"type":"boolean"},"expected_enabled":{"type":"boolean"}},"required":["enabled","expected_enabled"],"type":"object"}
```

## bluetooth.device

用途：Read pairing/connection state

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"address":{"pattern":"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$","type":"string"}},"required":["address"],"type":"object"}
```

## bluetooth.pair

用途：Request Bluetooth bonding change; pairing may require peer confirmation

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"address":{"pattern":"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$","type":"string"}},"required":["address"],"type":"object"}
```

## bluetooth.unpair

用途：Request Bluetooth bonding change; pairing may require peer confirmation

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"address":{"pattern":"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$","type":"string"}},"required":["address"],"type":"object"}
```

## bluetooth.connect

用途：Request Bluetooth audio-profile connection/active-device change; verify device state

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"address":{"pattern":"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$","type":"string"},"profile":{"default":"a2dp","enum":["a2dp","headset"],"type":"string"}},"required":["address"],"type":"object"}
```

## bluetooth.disconnect

用途：Request Bluetooth audio-profile connection/active-device change; verify device state

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"address":{"pattern":"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$","type":"string"},"profile":{"default":"a2dp","enum":["a2dp","headset"],"type":"string"}},"required":["address"],"type":"object"}
```

## bluetooth.active.set

用途：Request Bluetooth audio-profile connection/active-device change; verify device state

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"address":{"pattern":"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$","type":"string"},"profile":{"default":"a2dp","enum":["a2dp","headset"],"type":"string"}},"required":["address"],"type":"object"}
```

