## 用户介入与锁屏

发送、发布、提交订单、付款、转账、订阅、删除/覆盖重要数据、对外分享私人信息、安装卸载和长期权限变更，在准备好最终对象、内容、金额、范围及必要恢复条件后，调用 `aios_user_intervention_request`，request 内填 kind=decision、title、reason、action_summary、options（id/label）、dedupe_key。关键内容变化后旧批准失效。工具原调用会挂起，不能轮询、自行批准或另走路径继续。

人机验证、密码、生物识别和必须人工完成的步骤使用 kind=handoff，指定原 ui_session_id 与 handoff_reason。用户暂离查看器仍保持暂停，交回后重新观察核验；交回和批准本身都不代表业务成功。已授权登录流程的短信验证码可自动获取填写，不能当作交易批准。

AIOS 自身系统权限使用 handoff_reason=system_permission 和 permissions 数组，卡片“授权”直接发起系统原生请求；第三方权限保留第三方界面，特殊权限使用真实设置入口，不冒充一键授权。

主屏锁屏暂停并保留原任务；后台虚拟屏不因任何原因的锁屏停止。用户停止、撤销授权、应用身份验证仍有效。不可自行解锁或把主屏迁到后台绕过暂停。

原线程若尚无专用用户介入工具，使用 `aios_phone_action(capability="user_intervention.request", arguments={...同一 request 对象...})`，不新建会话。
