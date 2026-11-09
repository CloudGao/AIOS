# 技能发现与文件接口

Core 文件包是正文唯一事实源，SQL 保存证据和发布账本。Runtime 随包指引是只读发布内容，学习不要写入其中。

- `skill.list` / `skill.search`：query、domain、knowledge_kind、enabled_only、offset、limit；返回包/功能摘要与 id、revision、resource_path，按 next_offset 翻页。文字检索零命中时改写查询、去掉筛选或浏览，不能断言不存在。
- `skill.get`：id、view="body"；只读正文和资源目录。默认兼容视图会附带资源，模型按需读取必须传 view=body。
- `skill.resource.get`：id、revision、path、offset、limit；固定 revision，offset/next_offset 为 UTF-8 字节位置，按需分页直到 complete。不混版本，不绕过删除或停用。
- `skill.learn`：title、description、content_md、package、domain、evidence_id、verification、verification_kind、goal_id；可选 knowledge_kind、capability_id、capabilities、tags、parameters、success_conditions、dependencies。完整学习条件与归位规则见 [经验整理](../../../domain/aios-skill-learning/SKILL.md)。
- 修订传 id、expected_revision；首次资源传 assets，增量传 assets_patch（null 删除指定资源）。功能摘要 capabilities 含 capability_id/name/summary/path/conditions，path 指向实际资源。
- merge_status 为 created/updated/evidence_added/unchanged；ignored 和 protected_evidence_added 不能报告正文已更新。冲突先重读，不覆盖他人修改。
- 人工维护使用 skill.create/update；版本查询 skill.versions；回滚 skill.rollback（id、expected_revision、target_revision）；删除 skill.delete（id、expected_revision）。用户编辑、启停与墓碑优先。
- `skill.report`：id、expected_revision、execution_id，可选 capability_id，reason 为 network/account_permission/user_cancelled/observation/page_changed/method_error/outcome_unknown。具体归因和复验见 [维护规则](../../../domain/aios-skill-learning/references/maintenance.md)。

已知 id/功能位置直接读取，不强制先搜分类。返回 needs_revalidation 时先核对真实环境；auto_paused 分支不可直接套用；分类不授予执行权限。
