# 联系人

查询和维护联系人。query 包含无电话号码的联系人；详情用 get。更新 raw contact 前 raw.get 取得版本，保留其他数据；edit 打开编辑器，由用户保存。

以下为 2026-10-10 运行服务的接口快照。schema 为 arguments 的 JSON Schema；required 以外字段可省略，default 是服务默认值。注册/implemented 不保证设备当前支持或已授权；verification=unverified 表示未标记验证，不等于未实现。

## contacts.query

用途：Query rows in ID order; after_id paginates. contacts.query includes contacts without phone numbers; use contacts.get for details.

模式：direct；写入：否；验证标记：unverified；前提：android.permission.READ_CONTACTS。

```json
{"additionalProperties":false,"properties":{"limit":{"default":50,"maximum":200,"minimum":1,"type":"integer"},"after_id":{"default":0,"minimum":0,"type":"integer"}},"type":"object"}
```

## contacts.create

用途：Direct local-contact creation with raw/contact IDs and version readback

模式：direct；写入：是；验证标记：unverified；前提：android.permission.WRITE_CONTACTS。

```json
{"additionalProperties":false,"properties":{"name":{"maxLength":200,"minLength":1,"type":"string"},"phone":{"default":"","maxLength":2000,"type":"string"},"email":{"default":"","maxLength":2000,"type":"string"}},"required":["name"],"type":"object"}
```

## contacts.edit

用途：Open editor for selected ID; user saves

模式：ui_workflow；写入：是；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## contacts.delete

用途：Delete selected ID under unified authorization

模式：direct；写入：是；验证标记：unverified；前提：android.permission.WRITE_CONTACTS。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":0,"type":"integer"}},"required":["id"],"type":"object"}
```

## contacts.get

用途：Read a contact and bounded data rows (phones, emails, names)

模式：direct；写入：否；验证标记：unverified；前提：android.permission.READ_CONTACTS。

```json
{"additionalProperties":false,"properties":{"id":{"minimum":1,"type":"integer"}},"required":["id"],"type":"object"}
```

## contacts.update

用途：Replace one raw contact's name/phone/email after expected_version check; other data retained

模式：direct；写入：是；验证标记：unverified；前提：android.permission.WRITE_CONTACTS。

```json
{"additionalProperties":false,"properties":{"name":{"maxLength":200,"minLength":1,"type":"string"},"phone":{"default":"","maxLength":2000,"type":"string"},"email":{"default":"","maxLength":2000,"type":"string"},"raw_contact_id":{"minimum":1,"type":"integer"},"expected_version":{"minimum":0,"type":"integer"}},"required":["name","raw_contact_id","expected_version"],"type":"object"}
```

## contacts.raw.get

用途：Read raw contact ID, version and fields

模式：direct；写入：否；验证标记：unverified；前提：无额外注册前提（仍受运行时授权限制）。

```json
{"additionalProperties":false,"properties":{"raw_contact_id":{"minimum":1,"type":"integer"}},"required":["raw_contact_id"],"type":"object"}
```

