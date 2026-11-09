# 技能维护



`skill.report`：id、expected_revision、execution_id、可选 capability_id、reason。reason 为 network/account_permission/user_cancelled/observation/page_changed/method_error/outcome_unknown。环境故障只记失败；页面变化标待复验，确定方法错误标自动暂停。记录真实归因，不为维持成功率改判据。

人工创建/编辑使用 skill.create / skill.update；编辑保护优先。skill.update 支持 assets_patch 或显式整包 assets，二者不能同时传。停用、删除和回滚须遵守用户决定。skill.rollback 使用 id、expected_revision、target_revision；回滚不自动重新启用。skill.delete 保留墓碑，换名不能绕过用户删除。发布冲突先重读再合并，禁止覆盖别人修改。

Dreaming 从文件摘要取材料，经持久发布待办复用同一学习路径；记忆和任务仍按原有确认流程。数据库升级不等于兼容旧 APK；旧表退役有受保护快照，恢复须还原真实数据。

成功后对原技能调用 learn，传原 id、最新 revision、实际 evidence_id、verification_kind 和 capability_id。同正文只追加独立样本，不能把同一证据重复计数。health 按 revision 和 capability_id 记录；auto_paused 的分支不能直接套用，needs_revalidation 先观察验证。失败通过 skill.report 记录 execution_id、原因及受影响 capability_id。网络、权限和用户取消不当成方法错误。用户编辑正文保护不覆盖，仍可追加真实证据。已删除技能相同能力身份不可换名复活。依赖 unavailable 时必须明确阻塞，不得跳过后宣布成功。
