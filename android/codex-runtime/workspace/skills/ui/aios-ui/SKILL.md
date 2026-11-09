---
name: aios-ui
description: 操作 Android 原生应用页面；观察、定位、点击、输入、滚动并核对结果。网页内容优先使用浏览器技能，只有原生外壳或系统弹窗使用此技能。
---

# 原生 UI 操作

通过 `aios_ui_action` 调用，外层为 operation、arguments。首次选择/创建会话、切换应用或处理前台控制时读 [会话与应用发现](references/sessions.md)。先 list 复用本线程目标应用会话，没有时按真实包名 create；位置由宿主决定，mode 不授权搬屏。始终传返回的 session_id。

读取 tree 或 screenshot，依据真实页面选择目标。每次输入携带同会话最新 observation_id；坐标使用该观察的 coordinate_space，截图用 image_pixels，树用有效 bounds，不二次缩放或猜坐标。动作回执有有效新 observation 时直接复用，否则重新观察。首次输入或参数不确定时读 [输入参数与坐标](references/input.md)，不加载其他参考来猜参数。

输入提交不等于业务完成，按用户目标逐项核对同一阶段的真实结果；阶段汇报后仍有步骤就继续。观察失效只重新观察与决策，未知写入结果先读回，不能直接重放。空树、页面加载、连续两次没有进展或需要复杂结果核验时读 [恢复与结果验证](references/recovery.md)。

用户接管或暂停时停止输入，不用 close/create/resume 绕过。查看器退出不表示交回；明确交回后重新观察。主屏锁屏暂停，后台虚拟屏不因物理锁屏停止；权限撤销、身份验证和用户停止始终有效。需要批准、系统权限或人工操作时读 [用户介入](references/intervention.md)。

网页操作使用 [浏览器技能](../aios-browser/SKILL.md)。系统接口可直接完成目标时使用 [系统接口](../../interfaces/aios-system/SKILL.md)，不强制转成 UI 操作。APP 特定功能经验通过已有 skill.search/list 返回具体摘要；已知功能引用直接读，不全量加载 APP 知识。
