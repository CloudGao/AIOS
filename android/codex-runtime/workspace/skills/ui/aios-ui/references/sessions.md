# 会话选择、应用发现与前台控制

## 找到会话再操作

固定一个主屏、四个虚拟屏。已有本会话虚拟屏跨轮复用；小助手在目标主屏应用上则直接操作当前主屏页面，其他情况后台。宿主根据本次请求来源决定位置，mode 参数不能授权搬屏。主屏冲突由原对话确认卡片选择，工具挂起等待用户，收到结果后使用实际 session_id，不轮询等待。空树不是前台占用，重读仍空则 screenshot，截图可用就继续。

先 `list`，按本会话归属复用目标应用会话；没有则 `create`，包名由 apps.list 查询。之后始终传返回 session_id；status active 后再观察，不重复 create。服务重启留下的 paused 槽位仅在用户新请求下 resume 原槽位，不自动删除。

用户要查看或接管任务，不是创建 foreground 会话的理由。保留原 background 会话、应用和虚拟屏，让用户从 Launcher 的虚拟屏卡片进入；进入后默认实时观看，用户在查看器内明确点击“接管操作”才获得触控，明确交回后助手继续同一会话；退出查看器只离开界面，不自动交回控制权。不得为了查看或接管调用 handover 把应用迁移到真实主屏，也不得另建同应用前台会话。

foreground 使用主屏无障碍控制，background 使用隔离虚拟屏。同一应用不能同时被两个通道独占。

后台最多四个虚拟屏槽位。槽位已满时报告实际状态，请用户关闭不再需要的槽位；不得自动关闭、挤占或销毁旧任务。普通查看、接管、交回和退出查看器都保留槽位，用户明确关闭任务或点击卡片 × 会释放对应虚拟屏；任务停止连续五分钟后宿主也会自动释放。运行中的任务不会因暂时没有界面输入而超时释放。



前台界面操作已新增独立通道：aios_ui_action create 的 arguments 使用 mode=foreground 与 package；后台 mode=background。主屏位置由宿主依据上述规则决定，仍需用户启用 AIOS 前台控制无障碍服务，不自动授权。始终带 session_id；前台每次输入前用 tree/screenshot 返回的 observation_id，坐标来自实际截图。observation_pending 只重读观察，不重放动作。用户触摸或按键会暂停，user_takeover 后必须等用户新请求，不自行恢复；锁屏、授权撤销或目标离开前台时停止操作。不要把动作提交等同目标完成。

前台会话规则：输入法弹出的键盘属于目标应用的输入辅助窗口，不为键盘另建前台会话；只有操作输入法自身设置页才以输入法包创建会话。failed/permission_required/not_supported 不占用主屏租约；新用户请求可替换旧请求的前台会话，同请求切换仍在运行的应用先 close。user_takeover 后不可 close/create 绕过；等新用户请求。waiting_for_app 最多等待15秒，失败后读取 detail，不反复重建同一失败会话。每次创建读取新的 session_id，旧 ID 和观察令牌不得复用。

应用发现与跳转：用户点名高德、搜狗等应用时，用 apps.list 的 query 从所有已安装应用中查询名称/包名；不要从首个未过滤分页推断未安装，不猜包名。maps.open/browser.open 使用查到的 package 与明确 mode。selection_required 是系统存在多个处理器，读取 candidates 按用户指定选取；未指定且无法确定时询问，不将 ResolverActivity/android 当成可控应用。maps.open 只搜索地点，路线规划走地图应用实际路线界面。

旧会话若专用工具 schema 尚未包含 query/package/mode，使用 aios_phone_action(capability="apps.list"/"maps.open"/"browser.open", arguments={...}) 调用当前能力，参数按最新文档填写；不需要丢弃原会话。