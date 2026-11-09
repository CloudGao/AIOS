# AIOS automation API v1

通过 `execute_action` 调用 capability；外层 request_id 是 MCP 幂等标识。每次独立查询使用新外层 ID，避免读到旧 action 缓存；重试同一次写入保持外层 ID 不变。

| capability | arguments |
|---|---|
| automation.status | `{}` |
| automation.validate | `{source}`，只校验语法，不执行程序 |
| automation.create | 下述完整定义，另有 `job_key`、可选 `enabled`（默认 true） |
| automation.list | `{}`，不含代码与程序状态 |
| automation.get | `{id}` |
| automation.update | 完整定义 + `id, expected_revision`；保留启用/暂停状态 |
| automation.pause / resume / delete | `{id, expected_revision}` |
| automation.run | `{id, expected_revision, request_id}`；内层 request_id 使用字母、数字、点、下划线或短横线 |
| automation.runs | `{id, limit?: 1..100}`，最新在前 |
| automation.versions | `{id}`，最新 100 个定义快照 |

完整定义：`title`（1..300 字符）、`description`（可选）、`source`（1..65536 字符）、`schedule`、`capabilities`（默认空）、`timeout_ms`（1000..30000，默认 30000）、`network_required`（默认 false）。网络条件只控制调度，不为 JS 提供 fetch。

调度格式：

```json
{"kind":"once","at":1791600000000}
{"kind":"interval","minutes":30}
{"kind":"daily","time":"09:00","zone":"Asia/Hong_Kong"}
{"kind":"weekly","time":"09:00","zone":"Asia/Hong_Kong","day":1}
```

`at` 是 UTC 毫秒时间戳，按真实目标日期生成，不复用示例值；`day` 是周一 1 至周日 7。interval 第一次在一个间隔后执行。once 的过去时间会尽快执行；重新 resume 已结束的一次性任务会重新安排它。

## JavaScript 入口

定义 `async function main(ctx)` 或同步 `function main(ctx)`，返回可 JSON 序列化的结果；无返回值存为 null。

- `ctx.runId`：本次运行标识。
- `ctx.scheduledAt`：计划时间 UTC 毫秒。
- `await ctx.call(name, args)`：调用声明且受支持的只读能力，直接返回 data。
- `await ctx.state.get()`：获取本程序持久 JSON 对象。
- `await ctx.state.set(object)`：替换本程序状态，最多 16384 字符；立即持久化，即使后续脚本失败也不回滚。
- `await ctx.log(value)`：记录文本日志，单次累计限制 8192 字符。

每次运行最多 64 次宿主调用（包含 state 和 log），结果与单条能力响应限制 65536 字符，沙箱堆上限 16 MiB。不要启动未 await 的后台工作。超时、暂停、版本变化或授权撤回会终止执行。

可声明的 capability 只有：

| 名称 | 参数 / 返回 data |
|---|---|
| workspace.task.list | `{}` → `{items:[...]}`，在 JS 中自行筛选状态 |
| workspace.task.get | `{id}` → 完整任务 |
| workspace.memory.list | `{}` → `{items:[...]}` |
| workspace.settings.get | `{}` → 工作空间设置 |

不得假设其他 MCP 能力也能在 JS 中调用。

## 示例：每日待办汇总

下面是 CLI 请求文件；MCP 调用时将 params 作为 arguments，capability 为 `automation.create`。

```json
{
  "action": "create",
  "params": {
    "job_key": "daily-todo-summary",
    "title": "每日待办汇总",
    "description": "每天整理未完成事项，在运行结果中查看。",
    "schedule": {"kind":"daily","time":"09:00","zone":"Asia/Hong_Kong"},
    "capabilities": ["workspace.task.list"],
    "source": "async function main(ctx) { const {items} = await ctx.call('workspace.task.list', {}); const pending = items.filter(t => t.status === 'todo' || t.status === 'doing'); await ctx.log('汇总完成'); return {count: pending.length, titles: pending.map(t => t.title)}; }"
  }
}
```

成功提交后查询 get，读取 `id/revision/enabled/next_at/scheduling_state`；`scheduling_state=error` 时检查 `scheduling_error`。运行状态为 `running/succeeded/failed/timed_out/cancelled/interrupted`，与调度的 enabled 状态分开。

UI 或 CLI 的暂停/删除可停止后续执行。系统强行停止应用后需用户重新打开，不能承诺此时仍运行；普通进程退出和设备重启由持久调度恢复。
