# 任务接口

下表 capability 均加前缀 `workspace.`。`?` 表示可选；ID 非空且最多 100 字符，title 为 1..300 字符，expected_revision 为当前正整数版本。

| capability | arguments | 功能 |
|---|---|---|
| task.list | `{}` | 返回 items，通过 parent_id 表达任务树 |
| task.get | `{id}` | 读取当前全貌、关联会话、媒体引用及最近 100 条历史摘要 |
| task.create | `{title,parent_id?,content_md?,status?,time_text?,start_date?,due_date?,change_summary?}` | 新建节点并记录历史；状态默认 todo，不执行工作 |
| task.update | `{id,expected_revision,title?,content_md?,status?,time_text?,start_date?,due_date?,change_summary?}` | 修改给出的字段，保留未给出的字段，按人工编辑处理并追加变化行 |
| task.history.list | `{id,offset?}` | 历史摘要每页 50 条，返回 items/has_more；offset 默认 0 |
| task.history.get | `{id,history_id}` | 读取该任务某次变化的 before_json/after_json、summary、change_id |
| task.move | `{id,expected_revision,parent_id?,position}` | 移动/排序；parent_id 空为根；position 从零起，目标同级列表不含自身；拒绝成环 |
| task.delete | `{id,expected_revision}` | 删除节点及子树，保留会话和原始媒体 |
| task.ai.report | 见下文 | AI 按稳定 work_key 汇报摘要、进度，保留人工覆盖 |
| task.ai.release | `{id,expected_revision}` | 用户明确要求时，将任务状态交回 AI 管理 |

time_text 最多 300 字符，可写“2026秋—2027秋”“2026年中—长期”。start_date/due_date 仅明确的 YYYY-MM-DD，未知留空，禁止从粗略时间捏造日期；截止不能早于开始。change_summary 最多 1500 字符。当前全貌放 content_md，历史变化单独存储，不把全部流水反复追加进正文。Dreaming 提案走宿主确认入口，不能调用普通接口绕过拒绝或确认。

普通 status 为 `todo/doing/done`；旧 `note` 状态为兼容历史数据保留，界面显示待整理；content_md 最多 1,000,000 字符。AI 汇报使用以下 arguments：

```json
{
  "work_key": "aios/稳定目标标识",
  "version": 1791600000000,
  "title": "工作标题",
  "summary": "工作摘要，Markdown",
  "progress": "具体进展、验证或阻碍，Markdown",
  "status": "doing",
  "evidence": "",
  "session_ids": []
}
```

- version 必须使用当前 UTC 毫秒时间戳并递增，不复制示例值；相同版本仅允许同内容重试。work_key 为 1..200 字符，整个目标保持不变。
- summary/progress 各最多 1,000,000 字符；evidence 可选，最多 100,000 字符；session_ids 必填，无关联时用空数组。AI status 仅为 todo/doing/done。
- 较大工作开始、重要进展和验收时汇报；普通问答及很小修改无需建任务。只有目标完成且验收通过才写 done，并提供 evidence；空闲或 final 回复不代表完成。
- session_ids 只填真实确认的 `codex:<thread ID>`，不能猜测；未确认时用空数组。关联依赖对应会话已同步。
- 用户的标题、正文和状态覆盖需保留；AI 更新使用 report，不用 task.update。未经要求不 release，不恢复已删除任务。

调用使用 aios_workspace_action/action=task.ai.report。仅记录手机本地目标；不读取、同步或代报电脑 Codex 会话。手机服务不可用时说明错误，不创建电脑待同步报告。

人工目标接入 AI 进度管理：先 task.get，再在首次 task.ai.report 中提供 task_id 与 expected_revision，并使用 work_key=task:<id>。已有 ai_key 时保持该 key。版本冲突先重新读取，不覆盖用户修改，不创建重复目标。


## 持续目标的记录与恢复

任务不是笔记。用户提出需要跨步骤、跨会话持续推进的目标时，先读取 workspace 的任务列表，复用相关目标；没有匹配目标才通过 task.ai.report 创建。普通问答、闲聊和单步操作不强制建任务。工作期间在阶段变化、遇到阻塞、得到证据时更新目标摘要和进度，包含计划、当前状态、阻塞、下一步、完成标准；不能以本轮回答结束作为任务完成。用户人工编辑和删除优先，每次恢复先 task.get，不能恢复已删除目标。

从任务页启动时必须维护给定 task_id，不创建另一份。人工任务尚无 ai_key 时，task.ai.report 可传 task_id 与 task.get 返回的 expected_revision，以 work_key=task:<id> 接管进度维护；保留人工字段及状态。已有 ai_key 时沿用该值，version 必须递增。完成需要实际证据。session_ids 只填写已存在的真实手机会话；尚未投影的会话可以暂不关联，随后补充。任务记录本身不是后台调度；等待未来条件需要用户授权的自动化，不谎称离开对话后仍持续执行。

