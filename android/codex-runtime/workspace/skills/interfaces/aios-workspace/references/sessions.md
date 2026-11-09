# 会话、关联与附件

以下 capability 加前缀 `workspace.`；`?` 表示可选。ID 为 1..100 字符，title 为 1..300 字符，expected_revision 为当前正整数版本。

| capability | arguments | 功能 |
|---|---|---|
| session.list | `{}` | 按最近活动列出会话记录 |
| session.get | `{id,limit?,before_row?}` | 返回会话、关联任务及消息页；limit 默认 100，范围 1..500 |
| session.search | `{query,project_id?,task_id?,session_id?,limit?,offset?}` | 搜索本地与已发布镜像消息；limit默认10、最大50，排除遗忘来源及派生会话 |
| session.summary.get | `{session_id}` | 返回summary或null，包含覆盖序号、generation、revision |
| session.summary.upsert | `{session_id,expected_revision,through_sequence,content,generation?}` | 当前AI写摘要；首次版本为0，正文最多12000字符 |
| session.create | `{title,task_id?}` | 创建本地会话记录，可关联任务 |
| session.update | `{id,expected_revision,title}` | 重命名本地会话 |
| session.delete | `{id,expected_revision,delete_memories?}` | 删除记录、消息与关联，保留任务；delete_memories 默认 false |
| message.append | `{session_id,message_id,role,content_md}` | 保存实际消息，不生成回复；role 为 user/assistant/system，正文 1..1,000,000 字符 |
| relation.link | `{task_id,session_id}` | 建立多对多关联，幂等 |
| relation.unlink | `{task_id,session_id}` | 只解除关联，幂等 |
| attachment.get | `{id}` | 读取附件元数据，content URI 仅手机本地可用 |
| attachment.delete | `{id}` | 只移除引用，不删除原始媒体 |

首次读取省略 before_row；如 has_more 为 true，将返回的 next_before_row 作为下一次 before_row，读取更早消息。不要自行填入超出 JavaScript 安全整数范围的默认上界。message.append 重试保留 message_id 和外层 request_id。

摘要 through_sequence 来自真实消息且不得回退。Codex摘要指定当前generation，本地会话为空。快照变更使旧摘要失效；遗忘会清除可能含派生内容的摘要。原始记录仍保留。

`codex:<thread ID>` 是本项目 Codex 历史镜像，不是独立可编辑的本地会话。继续对话、重命名在 Codex 执行；删除手机镜像会设置隐藏标记，不删除电脑会话，不主动恢复已隐藏记录。

message.append 不启动模型推理。继续已有手机线程使用 Workspace 中的“继续此会话”；不运行电脑 assistant-bridge。
