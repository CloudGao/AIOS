---
name: aios-scheduler
description: Manage event/time-triggered phone assistant tasks, their rules, results and preemption; also manage existing WorkManager JavaScript schedules.
---

# AIOS 定时任务

AIOS 已提供 **WorkManager + JavaScriptEngine** 定时任务模块。使用现有入口提交调度规则和 JS 小程序；不要自行猜测执行框架，或用会话 sleep、JS 定时循环、电脑计划任务代替手机端持久化任务。

## 发现与入口

1. 通过 `aios_automation_action` 调用 `{"action":"status","arguments":{}}`，检查手机支持和授权。action 去掉 automation. 前缀。
2. 阅读 [接口与 JS API](references/api.md)，只使用文档列出的 JS 能力。该环境不是 Node.js 或浏览器；没有直接网络、文件、DOM、shell 或任意 Android API。
3. 新建程序前调用 `automation.validate` 校验语法，再调用 `automation.create`。更新先读取当前版本，使用 `expected_revision`。
4. 提交后 `automation.get` 确认已保存、启用状态、`next_at` 和 `scheduling_state`；`scheduled` 表示已交给 WorkManager，不表示已经运行。用 `automation.runs` 验证真实结果。

工具不可用或服务离线时报告实际错误，不运行电脑 CLI、ADB、Windows 计划任务或另一套定时器。

## 必须保留的语义

- `job_key` 表示一个稳定目标。重试创建复用该 key；已存在时返回当前任务，不会覆盖用户修改。已删除 key 永久拒绝恢复。
- 不把用户暂停当成待修复状态。编辑保留暂停；只有用户要求恢复时调用 resume。
- WorkManager 不保证准点。间隔至少 15 分钟；daily/weekly 明确保存 IANA 时区。错过多个周期合并执行，不承诺后台高频运行或精确闹钟。
- `run` 是一次手动执行，不改变暂停状态或下次计划；复用它的 `request_id` 防止重复执行。
- 每次运行最大 30 秒、独立沙箱。执行与状态写入可能被中断，不能承诺外部操作严格执行一次。当前开放的 AIOS 能力均为只读。
- 手机端 AIOS → **定时任务** 可以查看程序、调度、日志和结果，也能暂停、恢复、编辑、删除。

不要把本 Skill 当作后台服务；真正执行的是手机模块，提交后不要求本次 LLM 会话在线。
## 事件或时间触发的助手任务

使用 `aios_automation_action` 的 `rule.*` 与 `execution.*` 接口。它们启动真实手机 Codex，不是 JS 程序。用户明确要求自动化后才创建；保存后回显条件、允许能力及启用状态。

示例：

```json
{"action":"rule.create","arguments":{"id":"user-battery-on-power","title":"接电源后检查电量","instruction":"读取实际电量并报告，不修改设置。","trigger":{"type":"event","topic":"android.intent.action.ACTION_POWER_CONNECTED"},"resource_mode":"headless","max_screens":0,"priority":0,"capabilities":["battery.read"]}}
```

- 事件主题支持既有 `notification.posted`、`notification.removed`、系统广播及 Context/Workspace/已开启的数据流；`trigger.equals` 是严格字段相等匹配，例如 `{"package":"实际包名"}`。通知携带 title/text，`content_truncated=true` 时不可当作完整消息。
- 时间条件：`{"type":"time","schedule":{"kind":"daily","time":"09:00","zone":"Asia/Hong_Kong"}}`；once、interval、weekly 沿用下文 Schedule 契约。
- `capabilities` 必须是任务实际需要的明确能力，例如 `battery.read`、`workspace.task.create`、`ui.action`。不允许通配授权、后台规则修改授权或任意 shell。用户确认外部写入仍使用原生介入。
- `resource_mode` 为 headless 或 screen。后台无屏最多五个；有屏总量四，调度池保底一屏，另外三屏用户优先。单任务 max_screens 最多四。被抢占会暂停并在资源可用时续用原会话。
- `rule.list/get` 查看真实规则；update 必须提供完整定义和 expected_revision。pause/resume/delete 使用 id 和 expected_revision；编辑不恢复暂停。create 重试必须复用 id。
- `execution.list` 可带 rule_id，`execution.get` 带执行 id；pause/resume/cancel 控制一次执行。结果未知不能直接 resume 或重复创建执行。
- `assistant.status` 查询配额和占用；`rule.sources` 查询设备事件发布与通知监听状态。
- 默认每规则串行，重复事件去重，下一条新事件生成新实例。queue_limit 默认30，event_ttl_ms 默认一天，cooldown_ms 默认0。队列溢出和事件过期可查，不宣称已处理。
- 不承诺精确时间。未匹配、等待资源、准备、运行、等待用户、暂停、未知结果和完成是不同状态。不能把 ready、已提交或模型片段当成功。

通知 trigger 支持 conditions 数组，每项 field=title/text、op=contains/not_contains/equals/starts_with/ends_with、value、ignore_case（默认 true），condition_mode=all/any。本地过滤通过才调用模型；最多 20 条、每值 500 字符。缺失字段不匹配，截断字段不能证明排除/相等/后缀。context_mode 默认 fresh，每次触发全新上下文；只有用户明确要求时设 rule 复用规则会话。同一次执行恢复仍使用原上下文。执行结果在自动化记录查看，不进入普通会话列表。
