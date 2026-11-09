# 构建与验收结果

2026-11-09 当前候选：自有代码 GPL-3.0-only，第三方许可原样保留。MobileCLIP2 已替换为 MIT OpenCLIP ViT-B/16 DataComp；SenseVoice 已替换为 MIT Whisper Small Q5_1。实际测试和限制见 [模型替换验收](MODEL_REPLACEMENT.md)。

## 当前构建与检查

完整 APK 和 AIOSCoreServices、AIOSControlAgent、AIOSSpeech、AIOSMiniApps、AIOSCodexRuntime 五个 ROM 模块构建通过，复用固定 Lineage ARM64 out。新增 Whisper native 已完成主机与 ARM64 构建。完整 frankel userdebug 产品构建也已通过；最终签名归档及离线验证结果见本次交付报告，未刷入或替换现有 DSU。

模型替换阶段相关检查 62 项全部通过：APK 装配 16、源码分发 19、可选依赖 9、I18N 5、Lineage 集成 13。本次发布收尾通过 APK 装配 16、源码分发 19、发行工具 8、I18N 5 项检查；发行工具调整后必要复测 8 项通过。与前阶段存在重叠，不合并为独立测试总数。此前路线整理的其他检查及 34 项 Java 断言不重复计入本次测试。公开源码模式扫描的最终结果见交付目录 SOURCE-AUDIT.json；该扫描不是安全认证。

APK 仅保留实际装配的语音和账号模型选项，不显示 Chrome、邮箱等 ROM 应用选项。ROM 保留按实际模块划分的选择入口。SPAKE2 保留 LGPLv3 原许可，附对应源码与重建说明。

## 实际设备测试

Pixel 10 / Android 17 / API 37，经已有 ADB 通道同签名覆盖安装，保留用户数据；未卸载用户应用、清空、刷机、调用 Android MCP 或同步电脑会话。

- Whisper：实际 APK RecognitionService 连续识别中文、英文样本通过，耗时约 8.35 / 8.70 秒；native 和 JNI 连续识别也通过。原锁屏超时已通过识别前台服务修复；最终 APK 0.1.11（versionCode 12）熄屏锁定时连续 zh/en/zh/en 四次识别通过，首个中文样本约 8.46 秒，结束后识别服务退出。未据此宣称长会议、所有口音或其他语言质量达标。
- OpenCLIP：实际共享 Android GPU 后端图像 752/752、文本 709/709 节点各在一个 GPU 分区执行。128 张 COCO 图片实测 mAP 0.67524，原模型参考值 0.68234；只是小样本比较，不代表所有场景等质。图像 p50 56.37 ms。未将独立后端测试称为完整 ROM 服务验收。
- 此前 APK 路线、语音选择持久化、英文/简中/繁中和阿拉伯语下英文回退/RTL 的限定设置测试通过；不是全应用或全系统国际化验收。

## 交付边界

源码 ZIP 不包含 Git 历史、模型权重、签名私钥或设备资料。模型保留固定版本、地址、哈希和转换/构建脚本。当前 APK、完整 ROM 归档和对应源码作为本地候选整理，准确哈希见交付目录 FREEZE.json、VALIDATION.json、SHA256SUMS.txt。

四项原始声明缺口已关闭 eastasianwidth；debugserver-types@0.5.0、@npmcli/agent@3.0.0、err-code@2.0.3 仍只有许可声明等证据，缺少上游完整原始声明。详见 licenses/original-notice-review.json。不能将本地测试通过写成二进制再分发已全面放行。

完整 ROM 的离线归档结果另见交付目录；本次不包含 Pixel 专有组件再分发放行、全新环境重建所有既有 native 依赖或全功能设备回归。
