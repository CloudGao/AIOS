# 文件与下载

读取共享存储、选择授权文档、分块上传或下载。绝对路径是 Android 路径，不是电脑路径。文本 write 拒绝覆盖；二进制替换用 upload 流程及当前 SHA256。begin → append 精确 offset → commit；不确定时 status 后续传，不能盲目重放。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## files.list

用途：List primary shared storage without a document picker

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"path":{"default":"/storage/emulated/0","maxLength":4096,"type":"string"},"offset":{"default":0,"minimum":0,"type":"integer"},"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"}},"type":"object"}
```

## files.select

用途：User document picker

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"mime":{"default":"*/*","pattern":"^[A-Za-z0-9*.+-]+/[A-Za-z0-9*.+-]+$","type":"string"}},"type":"object"}
```

## files.read

用途：Read <=64KiB text from absolute shared-storage path, or legacy managed filename

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"name":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"}},"required":["name"],"type":"object"}
```

## files.write

用途：Create text at absolute shared-storage path or managed filename; rejects overwrite

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"name":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"},"text":{"maxLength":16000,"type":"string"}},"required":["name","text"],"type":"object"}
```

## files.delete

用途：Delete shared file or empty directory directly; absolute path or managed filename

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"name":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"}},"required":["name"],"type":"object"}
```

## files.rename

用途：Rename shared or managed file; refuses overwrite

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"name":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"},"new_name":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"}},"required":["name","new_name"],"type":"object"}
```

## files.selected_metadata

用途：Read only a user-selected content URI with a granted read permission

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"uri":{"maxLength":4096,"pattern":"^content://[^\\s]+$","type":"string"}},"required":["uri"],"type":"object"}
```

## files.selected_text

用途：Read only a user-selected content URI with a granted read permission

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"uri":{"maxLength":4096,"pattern":"^content://[^\\s]+$","type":"string"}},"required":["uri"],"type":"object"}
```

## files.access

用途：Read all-files and full media access status

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{},"type":"object"}
```

## files.mkdir

用途：Create shared storage directory

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"path":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"}},"required":["path"],"type":"object"}
```

## files.read_bytes

用途：Read arbitrary shared file as bounded base64 chunks

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"path":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"},"offset":{"default":0,"minimum":0,"type":"integer"},"limit":{"default":65536,"maximum":262144,"minimum":1,"type":"integer"}},"required":["path"],"type":"object"}
```

## files.stat

用途：Read file size and SHA256; full file hashing may take time

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"path":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"}},"required":["path"],"type":"object"}
```

## files.upload.begin

用途：Start staged binary upload. Replacing existing file requires its current SHA256

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"path":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"},"bytes":{"minimum":0,"type":"integer"},"expected_sha256":{"default":"","pattern":"^([a-f0-9]{64})?$","type":"string"}},"required":["path","bytes"],"type":"object"}
```

## files.upload.append

用途：Append <=64KiB decoded bytes at exact staged offset

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"pattern":"^[a-f0-9-]{36}$","type":"string"},"offset":{"minimum":0,"type":"integer"},"base64":{"maxLength":87384,"type":"string"}},"required":["id","offset","base64"],"type":"object"}
```

## files.upload.commit

用途：Verify size/hash and atomically replace destination if precondition still holds

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"pattern":"^[a-f0-9-]{36}$","type":"string"},"sha256":{"pattern":"^[a-f0-9]{64}$","type":"string"}},"required":["id","sha256"],"type":"object"}
```

## files.upload.cancel

用途：Remove only this staged upload

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"pattern":"^[a-f0-9-]{36}$","type":"string"}},"required":["id"],"type":"object"}
```

## files.upload.status

用途：Read persisted staged upload status

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"pattern":"^[a-f0-9-]{36}$","type":"string"}},"required":["id"],"type":"object"}
```

## downloads.start

用途：Start persistent Android DownloadManager HTTPS download into a new shared-storage file

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"url":{"maxLength":8192,"pattern":"^https://","type":"string"},"path":{"maxLength":4096,"minLength":1,"pattern":"^(?:/[^\\x00]+|[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$","type":"string"}},"required":["url","path"],"type":"object"}
```

## downloads.status

用途：Read download progress, state and Android failure reason code

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":1,"type":"integer"}},"required":["id"],"type":"object"}
```

## downloads.cancel

用途：Cancel own download and remove its downloaded file

模式：direct；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":1,"type":"integer"}},"required":["id"],"type":"object"}
```

