# 宿主保存契约

输出对象：`batch_id`, `status`（complete / partial）, `changes` 数组, `remaining_source_ids` 数组, `summary`（至多 1500 字符）。每个变更需要 `kind`（skill / memory / task / schedule）、`action`（create / propose / update）、`source_ids`（本批原始证据 ID）、`title`、`payload`。update 还需要 `target_id` 和 `expected_revision`，技能通过证据校验后自动发布；其余成为待确认建议。

Payload:
- skill: title、description（500 字符）、content_md（24000 字符）、domain、package、evidence_id、verification。evidence_id 必须是来源工具返回的真实 learning_evidence_id；描述实际结果而非声称成功。引用过期或不匹配会拒绝；无证据的建议不会自动成为技能。
- memory: content、kind（preference/fact/convention/experience）、scope_type（user/project/task）、scope_id（项目默认 aios:phone）、valid_until（毫秒，0 永久），明确用户事实带 explicit_quote。宿主绑定全部真实来源；回顾不使用自动保存偏好，不提前创建候选记忆，用户确认后创建 active 记忆。
- task: content_md 写当前全貌；change_summary 写本次变化；status 仅 note/todo/doing/done，完成必须有明确证据；time_text 为粗略时间，start_date/due_date 为明确 YYYY-MM-DD 或空。更新必须在变更对象顶层给 target_id 和 expected_revision，payload 提供更新后全貌，保留原有仍成立内容，不把全部历史复制进正文。
- schedule: intent、timezone（IANA）、recurrence（自然语言）。不包含 JavaScript；用户决定后交正常定时任务编辑流程处理。

sources 的 body 是 JSON 字符串，items 是原始可见消息及工具回执，turn_status 与 truncated 明确来源局限。所有 source_ids 必须来自本批。partial 必须列出剩余来源。无材料无需编造变更。禁止从 daily_index、prior_changes 或已存技能反推新的成功事实。

每个变更附 topic_key（稳定语义主题，不能用日期或随机值逃过去重）、reason。重新询问已拒绝事项时附 reconsiders（旧变更 ID）和 reconsider_reason，且 source_ids 中必须有拒绝后新增的实质证据。prior_changes 是决定记录，不是新事实来源。

宿主先事务保存提案与处理游标，不提前写正式记忆或任务；技能通过验证后由持久发布队列自动写文件资产。前台弹窗逐项同意/不同意；关闭不拒绝。接受时重新校验目标 revision 与来源，任务当前全貌和历史变化在同一事务写入，并关联来源会话；不会自动执行任务。技能不进入人工审批队列，失败保留可重试发布记录，定时安排仍需正常人工编辑。来源删除、内容冲突或输出非法不允许覆盖；人工维护内容优先。
