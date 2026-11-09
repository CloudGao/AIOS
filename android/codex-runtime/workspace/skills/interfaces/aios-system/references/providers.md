# 应用函数与外部 MCP

使用设备已提供的 AppFunctions 或显式配置的 MCP provider。先针对 provider 发现工具及参数再调用，不猜测外部工具 schema。仅允许受信任配置/白名单；OAuth 使用 authorize 与 authorization，登录由用户完成。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## apps.functions.discover

用途：Discover local AppFunctions through platform service

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## apps.mcp.discover

用途：List providers configured in the phone Codex runtime; empty means none configured

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## apps.functions.execute

用途：Execute app-provided function under platform access checks; no consent auto-send

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"package":{"pattern":"^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$","type":"string"},"function":{"maxLength":500,"minLength":1,"type":"string"},"parameters":{"additionalProperties":true,"type":"object"}},"required":["package","function","parameters"],"type":"object"}
```

## apps.mcp.tools

用途：Discover tools for an explicitly configured provider

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"provider":{"maxLength":100,"type":"string"}},"required":["provider"],"type":"object"}
```

## apps.mcp.call

用途：Call a locally allowlisted provider tool

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"provider":{"maxLength":100,"type":"string"},"tool":{"maxLength":200,"type":"string"},"arguments":{"additionalProperties":true,"type":"object"}},"required":["provider","tool","arguments"],"type":"object"}
```

## apps.mcp.authorize

用途：Start OAuth login for a trusted configured HTTP provider; poll authorization for user login URL

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"provider":{"maxLength":100,"type":"string"}},"required":["provider"],"type":"object"}
```

## apps.mcp.authorization

用途：Poll OAuth status; tokens never returned

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"provider":{"maxLength":100,"type":"string"}},"required":["provider"],"type":"object"}
```


当前手机 MCP 使用原生 app-server 配置，不读取电脑 remote_providers。调用工具需在该 provider 的 enabled_tools 显式白名单中，且不能出现在 disabled_tools；先发现真实工具 schema。authorize 返回登录地址，authorization 读取真实状态，不能将已生成地址说成登录成功。App Functions 通过 LineageOS AppFunctionManager 执行；超时会请求取消但效果仍可能未知。
