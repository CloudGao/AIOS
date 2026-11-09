# MiniApp API v1

## 发布与管理

`aios_phone_action(capability="miniapp.catalog", arguments={})` 返回宿主特性、能力支持状态和上传限额。普通管理成功为 `{state:"succeeded",data:...}`，打开为 `handed_to_system`，失败读 error。

| 操作 | arguments | data |
|---|---|---|
| install | `{id,expected_version,request_id}` | 从手机当前提供商工作区 miniapps/id 读取 manifest.json 和静态文件，验证快照后一次发布；优先使用此入口 |
| list | `{}` | 已安装应用数组，含 manifest、generation、state、versions、grants |
| get | `{id}` | 同上单个应用 |
| begin | `{request_id,manifest,expected_version}` | stage_id、manifest、state 等 |
| write | `{stage_id,path,base64}` | 上传文件 bytes、sha256 |
| publish | `{stage_id}` | id、version、generation |
| discard | `{stage_id}` | 删除上传记录与临时文件，不删已发布版本 |
| open | `{id}` | 请求打开；需观察页面验证 |
| rollback | `{id,expected_version,version}` | 切换到已有代码版本，清除能力授权；业务数据不回滚 |
| uninstall | `{id}` | 删除小程序全部本地数据、代码、授权、请求记录和所属通知 |

manifest 示例：
```json
{"id":"my-calendar","name":"我的日历","version":1,"entry":"index.html","capabilities":[]}
```

id 为 3–48 个小写字母、数字、连字符，字母开头；version 为递增正整数。首次 expected_version=0，更新填写 get 的当前版本。request_id 自选稳定标识，只能字母、数字、下划线、连字符，1–100 字符。同一次 begin 重试沿用原值。stage_id 即此值。不同发布用新值。

优先在 `miniapps/<id>/` 写好 `manifest.json` 和全部静态文件，再调用 `miniapp.install`。该接口通过只读文件描述符传输快照，不需要模型转述文件或 Base64。目录不能包含符号链接或非普通文件，不能指向 Runtime 账号目录。`request_id` 相同但文件内容变更会拒绝；修改后用新 request_id。发布完 discard 对应 request_id 释放上传记录。

以下 begin/write/publish 是备用上传路径：每个文件 Base64 编码后上传，原始文件不超过 48 KiB，单包不超过 128 文件、2 MiB。大脚本拆模块。只允许普通相对路径，不允许 `..`、绝对路径、符号链接或保留的 `_ling/`。整个包上传后 publish，校验哈希并原子切换版本。publish 未知结果先 get，核对版本，再查 stage/retry 原 publish；不要另起安装。完成后 discard 释放 staging 配额（最多64个）。

源码与应用产物均保存在手机；Runtime 的源工作区不是浏览器数据库。默认没有打包工具时直接生成标准静态文件。导出/备份业务数据可由小程序用 files.saveText 实现，不能通过 shell 读取宿主私有浏览数据库。

## 网页 API

```html
<script src="/_ling/ling.js"></script>
```

```javascript
const catalog = await ling.capabilities();
const requestId = crypto.randomUUID(); // 发生超时后保留这个 ID
const result = await ling.call('notifications.post',
  {id:'saved', title:'已保存', body:'你的记录已保存在本机'}, {requestId});
if (result.state === 'outcome_unknown') {
  const saved = await ling.result(requestId);
  // 查询仍未知时显示未知，不再自动发送一份。
}
```

`ling.call` 的第三参数可省略，此时 SDK 生成请求 ID；对需要恢复的写入自行持久保存 requestId。回执除 state 外含 data 或 error。保存过的同 ID 同参数请求返回原回执；不同参数拒绝。当前每安装代际最多512条能力请求回执，达到上限拒绝新写入，不静默丢弃去重历史。capabilities/requests.get 不占此额度。

能力须出现在 manifest，运行时第一次使用显示原生同意对话框；用户可在管理中撤销。

| 能力 | 参数 | 结果语义 |
|---|---|---|
| notifications.post | id(1–80),title(1–100),body(0–2000) | Android 通知权限及渠道启用后提交；同 id 替换本应用通知 |
| notifications.cancel | id(1–80) | 移除本应用该通知；不影响其他应用 |
| calendar.compose | title(1–200),begin_ms>=0,end_ms>begin_ms | 打开系统日历编辑器，由用户保存；仅 handed_to_system |
| sms.compose | number(1–40),text(0–2000) | 打开短信编辑器，由用户发送；不直接发送 |
| files.pickText | `{}` | 用户选取 UTF-8 文本，最多64KiB；返回 data.text |
| files.saveText | name(1–80文件名),text(UTF-8≤64KiB) | 用户选取导出位置，写入结果；不自动取得其他文件权限 |

没有安装相应编辑器时 compose 返回 not_supported。Android 通知权限首次申请可能先返回 permission_required；用户完成授权后，明确的新尝试使用新请求 ID。拒绝/取消不能自动重试。

未接通：系统日历查询/同步、直接短信发送、后台 JS、提醒调度、系统闹钟、AI 推理、Context 查询；catalog 会明确返回 not_supported。网页 IndexedDB 日历不受这些限制。

## 运行边界

每应用独立 Profile 与 HTTPS Origin，更新保持，卸载重装换代际。网页仅主框架、当前前台页面可调用桥；身份由宿主绑定。外链需用户选择浏览器打开；不能继承桥。普通 HTTPS fetch 遵循 CORS，不提供绕过代理。CSP 禁止远程脚本、iframe、Service Worker、eval、表单提交；允许本地脚本/样式、HTTPS 网络和图片。能力不是网络防泄漏沙箱。

WebView 不支持必要隔离特性时拒绝运行。卸载先禁用调用、关闭页面、取消通知，再删除 Profile 和文件；等待浏览数据删除完成回调后才报告卸载成功；空的 Profile 元数据在宿主下次启动回收。清理失败保留 deleting 状态，可再次卸载。用户已在外部应用保存的日历或短信不归宿主删除。

本地持久化示例需要处理 IndexedDB onerror/onblocked 与事务完成；“已保存”必须等事务 oncomplete。日期使用本地年月日字符串保存日期，不用 `toISOString()` 截取当天造成时区偏移。时间事件保存时间戳及必要时区；整日日程保存日期语义。
