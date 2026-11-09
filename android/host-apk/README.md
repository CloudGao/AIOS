# 单 APK 宿主

一个 os.aios.app 包，共享 Agent、Core、Workspace、技能、调度及小程序业务，通过公开 SDK 与本机无线 ADB shell 接入能力。无需 Shizuku 或第二个辅助 APP。

目标 Android 15+ / ARM64；主开发有 vivo X200 Ultra / Android 16、Pixel / Android 17 的限定范围历史验证；新候选、其他机型及完整后台/触摸接管矩阵需分别验收。APK 不装入 ROM 通知治理、录音和纪要。独立进程但同 UID 的 Runtime、小程序不构成安全隔离。

装配：tools/apk/stage.py；构建：tools/apk/build.py；SDK 检查：tools/apk/check_sdk.py。构建依赖、路径配置和签名说明见 [BUILDING](../../docs/BUILDING.md)，功能和验收范围见 [DESIGN](../../docs/DESIGN.md)、[VALIDATION](../../docs/VALIDATION.md)。

本目录包含宿主 UI、激活、设备适配和同包 shell 服务。shell/应用双向校验 UID，主屏接管监听缺失或失联时禁用相关能力；OEM 权限与后台限制仍然生效。开发与静态检查不需要连接手机。
