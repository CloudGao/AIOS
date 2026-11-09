---
name: aios-miniapp-development
description: 在 AIOS 手机上编写、更新、发布和卸载 HTML/CSS/JavaScript 小程序，使用 IndexedDB 保存业务数据，并通过 Ling API 调用目录中实际支持的系统能力。
---

# AIOS 小程序开发

用于用户要求制作可反复打开的本地界面或工具，例如日历、清单、记账；普通网页浏览和原生应用操作不需要此技能。

先读 [平台契约](references/platform.md)，调用 `aios_phone_action` 的 `miniapp.catalog`，确认真实环境与能力。所有管理调用使用同一个工具，`capability="miniapp.<操作>"`，参数放在 `arguments`。网页不能调用这些开发接口。

默认使用静态 HTML/CSS/JavaScript；业务数据放 IndexedDB，少量设置放 localStorage。界面须适配手机、触摸与软键盘，显示保存失败，不用插入 HTML 的方式显示用户输入。不需要后端服务。手机未承诺 Node/npm；复杂框架须先核实本机工具链。不要把在线 CDN 脚本作为启动依赖。

源代码保存在本机工作目录 `miniapps/<app-id>/`，连同 manifest 一起维护。先完成全部文件与检查，再调用 miniapp.install（备用为 begin/write/publish）；更新先 get，使用返回版本作 expected_version。不得为更新换 app-id，否则会成为另一份应用。发布只更新代码，不清除 IndexedDB。数据结构升级应向后兼容；代码回滚不会恢复旧数据。

页面引入 `/_ling/ling.js` 后才能调用 `ling`。先检查能力目录和回执，manifest 只声明确实使用的能力。能力不存在就说明限制，不调用手机助手工具替网页绕过授权。Skill、manifest 和模型授权都不能代替宿主/Android 的实际授权。

网页关闭后没有可靠后台 JS；不要用 setTimeout、Service Worker 或打开隐藏页面实现闹钟/后台工作。当前目录中尚未接通的后台、AI、系统日历直读写等接口不可编造。日历小程序可以完全用本地 IndexedDB；系统日历编辑器交接不是已保存日程。

完成后打开小程序，验证主要操作及关闭重开后的数据保留；报告实际检查与未验证项。不要仅凭 publish 成功声称小程序功能通过。用户要求卸载时调用 uninstall；只卸载目标 MiniApp，不卸载宿主 APK。对用户已有数据的清空应明确得到授权。
