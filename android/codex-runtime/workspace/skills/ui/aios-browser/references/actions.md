# `aios_browser_action` 接口参考

统一调用格式：

```json
{"action":"observe","arguments":{"compact":true}}
```

`action` 必填，`arguments` 为动作参数对象。每个手机 Codex 线程拥有独立 agent-browser 会话，但操作的是手机上可见的 Chrome profile。成功结果为 `state="succeeded"`，有效载荷在 `data`；错误时按 `state` 和 `reason` 处理。

| action | arguments | 用途与用法 |
|---|---|---|
| `status` | `{}` | 检查 Chrome/CDP 会话是否可用。开始任务或故障恢复时使用。 |
| `observe` | `{"compact":true}` | 读取当前网页语义结构和元素引用。默认紧凑；缺目标时改为 `false`。 |
| `open` | `{"url":"https://…"}` | 在当前标签导航到明确 URL。调用后重新观察或等待。 |
| `back` | `{}` | 网页历史后退。 |
| `forward` | `{}` | 网页历史前进。 |
| `reload` | `{}` | 刷新当前网页。旧引用随即失效。 |
| `click` | `{"ref":"@e1"}` | 点击最近一次观察中的元素。适用于链接、按钮等。 |
| `fill` | `{"ref":"@e2","text":"内容"}` | 清空指定输入控件后填入完整文本。表单输入优先使用。 |
| `type` | `{"text":"内容"}` | 向当前已聚焦控件追加键入；只有焦点明确时使用。 |
| `press` | `{"key":"Enter"}` | 向页面发送按键，如 `Enter`、`Escape`、`Tab`。 |
| `hover` | `{"ref":"@e3"}` | 悬停元素，用于桌面式悬停菜单；触屏网页通常不需要。 |
| `select` | `{"ref":"@e4","value":"选项值"}` | 选择原生或可识别的下拉选项。value 使用页面实际值/文本。 |
| `check` | `{"ref":"@e5"}` | 勾选复选框或单选控件。 |
| `uncheck` | `{"ref":"@e5"}` | 取消勾选复选框。 |
| `scroll` | `{"direction":"down","pixels":600}` | 页面滚动。direction 默认为 `down`；可用 `up`、`down`、`left`、`right`，pixels 可省略。 |
| `wait` | `{"text":"完成"}` | 等待页面出现文字。也可传 `{"url":"…"}` 等待 URL，或 `{"ms":1000}` 做有界等待；三者选一。 |
| `tabs` | `{}` | 列出 Chrome 标签页及索引，切换或关闭前先读取。 |
| `tab_new` | `{}` 或 `{"url":"https://…"}` | 新建标签，可同时打开 URL。 |
| `tab_select` | `{"index":1}` | 按 `tabs` 返回的索引切换标签；随后重新观察。 |
| `tab_close` | `{}` 或 `{"index":1}` | 关闭当前标签或指定索引标签；关闭前确认不会丢失用户内容。 |
| `release` | `{}` | 关闭当前线程的 agent-browser 会话并释放资源。仅在明确需要时使用。 |

## 选择动作

- 设置输入框完整值用 `fill`；需要保留原内容并追加才用 `type`。
- 已知页面完成标志时先用 `wait`，再 `observe` 验证，减少重复快照。
- 搜索、登录、提交表单等多步流程，每次造成页面或 DOM 变化后重新观察。
- 提交订单、付款、发布、删除等外部副作用仍受用户授权边界约束；CDP 只是操作通道。
- `observe` 看不到目标时先使用完整观察、标签检查和合理等待。只有确认目标属于 Chrome/Android 原生界面后才使用 `aios_ui_action`。

## 示例

打开页面并点击链接：

```json
{"action":"open","arguments":{"url":"https://example.com"}}
{"action":"observe","arguments":{"compact":true}}
{"action":"click","arguments":{"ref":"@e1"}}
{"action":"observe","arguments":{"compact":true}}
```

填写并提交搜索表单：

```json
{"action":"fill","arguments":{"ref":"@e2","text":"AIOS"}}
{"action":"press","arguments":{"key":"Enter"}}
{"action":"wait","arguments":{"url":"search"}}
{"action":"observe","arguments":{"compact":true}}
```
