# 相册、媒体库与相机

查询图片/视频/音频，读取媒体字节，或打开相机界面。query 按返回 ID 分页；select/capture/video 涉及用户界面，打开不代表已拍摄。删除只针对用户指定对象。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## photos.select

用途：User photo picker, result URI scoped to chosen media

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## photos.query

用途：Query full image library in ID order; pass last _id as after_id for next page

模式：direct；写入：否；验证标记：unverified；前提：android.permission.READ_MEDIA_IMAGES。

```json
{"additionalProperties":false,"properties":{"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"},"after_id":{"default":0,"minimum":0,"type":"integer"}},"type":"object"}
```

## camera.list

用途：Camera capabilities

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## photos.delete

用途：Delete specified photo IDs directly under all-media authorization

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"ids":{"items":{"type":"integer"},"maxItems":50,"minItems":1,"type":"array"}},"required":["ids"],"type":"object"}
```

## camera.capture

用途：Open capture/record UI; user operates

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## camera.video

用途：Open capture/record UI; user operates

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## photos.read

用途：Read photo bytes by MediaStore ID without selection UI

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"},"offset":{"default":0,"minimum":0,"type":"integer"},"limit":{"default":65536,"maximum":262144,"minimum":1,"type":"integer"}},"required":["id"],"type":"object"}
```

## media.library.query

用途：Read full photo/video/audio metadata with keyset pagination

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"kind":{"enum":["image","video","audio"],"type":"string"},"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"},"after_id":{"default":0,"minimum":0,"type":"integer"}},"required":["kind"],"type":"object"}
```

## media.library.read

用途：Read photo/video/audio bytes by ID in bounded base64 chunks

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"kind":{"enum":["image","video","audio"],"type":"string"},"id":{"minimum":1,"type":"integer"},"offset":{"default":0,"minimum":0,"type":"integer"},"limit":{"default":65536,"maximum":262144,"minimum":1,"type":"integer"}},"required":["kind","id"],"type":"object"}
```

## media.library.delete

用途：Delete a specific media item

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"kind":{"enum":["image","video","audio"],"type":"string"},"id":{"minimum":1,"type":"integer"}},"required":["kind","id"],"type":"object"}
```

