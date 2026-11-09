# 源码冻结与本地交付

2026-11-09：自有代码 GPL-3.0-only，第三方许可保持原样。MobileCLIP2 和 SenseVoice 已实际替换为 MIT OpenCLIP 与 MIT Whisper，完成模型对比、GPU 后端及 APK 语音链路测试；不再列为待替换。结果见 MODEL_REPLACEMENT.md 与 BUILD_ACCEPTANCE.md。

源码按显式白名单导出，不带旧 Git 历史、模型权重、APK/native 负载、设备资料或私钥。模型恢复记录固定来源、哈希和构建方法。五份既有对应源码附件随交付保留，Whisper 源码直接包含在源码包中。

APK 0.1.11（versionCode 12）和完整 frankel userdebug 产品已构建。APK 同签名升级、锁屏中英文样本及签名/对齐核验通过。完整 ROM 的归档、签名及离线结果以本次交付目录记录为准，实机安装与保留数据升级未验收。所有材料仅本地准备，未公开上传。冻结提交、文件范围和哈希记录在交付目录的 FREEZE.json、SOURCE_MANIFEST.json 与 SHA256SUMS.txt。

eastasianwidth 的旧版本原始许可已补齐；debugserver-types@0.5.0、@npmcli/agent@3.0.0、err-code@2.0.3 的原始完整声明仍未取得，不冒充已闭环。详情见 licenses/original-notice-review.json。当前候选不等于公开二进制发行已全面放行。

修改仅在独立开源整理分支，主开发仓库不变；此前保存提交保留。源码包用于建立新的公开历史，不应直接推送内部仓库历史。对应源码附件需与适用二进制同址提供。
