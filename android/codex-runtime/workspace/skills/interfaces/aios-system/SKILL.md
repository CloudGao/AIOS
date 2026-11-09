---
name: aios-system
description: 通过手机本地接口读取设备状态、管理文件与应用、使用通信、媒体、定位及系统服务。只读取当前场景参考；原生界面操作使用独立 UI 技能。
---

# AIOS 系统能力

已知系统能力通过 `aios_phone_action` 调用：`{"capability":"接口名","arguments":{…}}`。已有专用工具时可直接使用。工具请求标识由手机运行时维护。

## 按场景读取

只读当前任务相关的参考，不加载整个目录。文档含接口功能、完整输入 schema、执行模式和授权前提。

- [设备状态与诊断](references/device.md)
- [网络、蓝牙与连接](references/network.md)
- [音频与录音](references/audio.md)
- [文件与下载](references/files.md)
- [相册、媒体库与相机](references/media.md)
- [联系人](references/contacts.md)
- [日历](references/calendar.md)
- [系统闹钟与计时器](references/clock.md)
- [电话、短信、通知与剪贴板](references/communication.md)
- [应用与安装](references/apps.md)
- [应用函数与外部 MCP](references/providers.md)
- [事件、定位与传感器](references/events.md)
- [Android 原生界面控制](../../ui/aios-ui/SKILL.md)
- [授权、设置与请求](references/permissions.md)

工作空间数据使用 [aios-workspace](../aios-workspace/SKILL.md)，定时程序使用 [aios-scheduler](../aios-scheduler/SKILL.md)。

## 结果与重试

检查返回 JSON 的 state 和 data；不能把工具返回成功当作业务已完成。handed_to_app/system、queued 和等待同意均不是最终完成。操作结果未知时先读回目标或持久 ticket/job，不盲目再调用写操作。状态、版本、哈希冲突先重新读取。

本机没有电脑 MCP 的 list_capabilities/get_action_result/get_action_image 等入口，不尝试加载它们。按本地参考和工具 schema 使用；参数不兼容或能力缺失时报告实际原因，不猜测桌面备用入口。截图以 ui.action 返回的真实图像为准。

这些参考保存了原有 167 项契约及参数；当前 LineageOS 适配正在统一验收，参考中的旧 verification 标记不证明当前功能可用。手机原生实现的错误、权限和设备状态优先。
