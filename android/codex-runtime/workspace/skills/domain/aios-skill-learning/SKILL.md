---
name: aios-skill-learning
description: 在复杂操作、恢复或用户纠正后，整理已验证且可复用的方法，更新已有技能及功能参考；普通闲聊、一次性数据和未知结果不形成成功技能。
---

# 整理与维护操作经验

## 自动学习

首次复杂目标成功、纠正后成功或恢复成功，且过程和结果真实可见时，当前模型直接 `skill.learn`，不等待用户逐条审批，不额外发起总结模型。普通闲聊和重复简单操作不总结。未验证、未知结果、只有输入提交成功都不能发布成功技能。

参数：title（160）、description（500，说明什么时候用）、content_md（24000）、package（真实包名，可空）、domain、evidence_id（真实读回的 learning_evidence_id）、verification（1200，逐项说明目标成功判据与观察结果）、verification_kind（independent/demonstration/corrected）、goal_id（稳定子目标身份）。凭据必须属于本会话和正确应用，24 小时内且版本未变化。

knowledge_kind 使用现有 ui/interface/app/domain 元数据：跨 APP 界面方法归 ui，接口用法归 interface，特定 APP 的功能知识归 app，领域方法与跨 APP 流程归 domain。来源和验证状态与分类分开。根据内容判断归属，不要求用户选择。优先修订已有 APP 包内具体功能；已知路径直接读取。旧记录可能未分类，查找时不要只依赖分类筛选。

其他可选字段、capability_id、tags、parameters、success_conditions、dependencies；capabilities 是最多 32 个功能摘要：capability_id/name/summary/path/conditions，path 必须对应包内实际资源。APP 主包保留入口，复杂功能放 references。

已有方法用 id 和 expected_revision 修订；同方法同应用沿用稳定身份。assets_patch 仅替换列出的资源，null 删除该资源，其他资源保留。首次创建用 assets。字段改变须有新证据；返回 merge_status 为 created/updated/evidence_added/unchanged，ignored 表示人工保护或墓碑抑制，不能说已更新。

具体地址、联系人、账号、聊天等参数化；不保存密码、验证码、截图或整段私人对话。用户补参数不等于代操作；关键步骤由用户完成但不可见时只学习可证实子步骤。完整可见示范用 demonstration，不算独立完成。不为学习重复真实外部写入。


随包技能由版本发布维护，不写入 Runtime 的 skills 目录。动态经验仍使用 Core 的现有文件资产库，按 knowledge_kind、package、domain 和功能摘要组织；逻辑归类不另建一份物理库。只适用于一个 APP 的入口与页面方法归该 APP；跨 APP 目标归领域；不把一次特定 APP 的成功泛化为通用 UI 方法。

发布前查找同一能力，按真实应用身份、功能与适用条件选择已有包；有匹配则追加/修订 references 与 capabilities，避免换名重复。摘要写“什么时候用、完成什么”，正文保留方法与成功判据，复杂功能独立放 references。临时坐标、地址、联系人留在当前任务，稳定偏好沿用个人记忆。

接口参数、结果和资源读取见 [Workspace 技能接口](../../interfaces/aios-workspace/references/skills.md)。失败归因、停用、回滚和证据维护时读 [维护规则](references/maintenance.md)，不在每个普通任务中预读。
