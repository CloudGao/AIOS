# 第三方再分发审查（当前快照）

自有代码GPL-3.0-only已落地。第三方许可保持原样；源码导出不含模型、APK/native负载或Pixel专有组件。当前不能标记为所有二进制已获公开分发放行。36份声明由同一哈希清单装配，实际包验收见RELEASE_FREEZE.md。

| 范围 | 当前处理 | 未闭环边界 |
|---|---|---|
| 图文模型 | 已替换为 MIT OpenCLIP ViT-B/16 DataComp；原权重不装配 | 固定模型卡、许可证及GPU实测见 MODEL_REPLACEMENT.md |
| 语音模型 | 已接入 MIT Whisper Small Q5_1；助手和会议共用后端，原 SenseVoice 权重不装配 | 当前应用验收状态见 MODEL_REPLACEMENT.md |
| LFM/CAM++/BlazeFace | 默认不预装，固定版本可选下载/导入 | 各模型原条款和精确权重证据按OPTIONAL_DEPENDENCIES执行 |
| libadb/SPAKE2/BoringSSL | libadb采用Apache分支；SPAKE2保留LGPLv3，与GPLv3应用组合；实际JNI/Java、BoringSSL源码与重建说明提供 | 不再因为许可证不同强制替换；最终应用的源码/重建和安装条件需按实际发布包核验 |
| Codex | 固定上游+Android修改源码、声明、传递源码附件和实际Cargo构建图已整理 | 既有产物对应记录不是干净环境native重建；最终链接符号SBOM未声称完成 |
| Codex原13项 | fax/fax_derive不在当前构建图；其余11项保存原manifest、作者、版权头和完整声明协议 | debugserver-types原始独立MIT notice未出版；无VCS Apache crate以固定发布包为依据，不伪造仓库NOTICE结论 |
| Node/npm | 官方Node源码包含匹配npm；7项补充归属及所声明全文保留 | eastasianwidth已补齐上游完整许可及旧版本适用说明；@npmcli/agent、err-code仍未找到原始完整声明 |
| sherpa/ONNX/Silero、Android库及其他native | 原许可和NOTICE随源码及装配清单保留，锁定版本与哈希 | 未把所有预编译native输入宣称为已从全新环境重建 |
| 第三方独立应用 | Chromium/Collabora/MJ PDF/Editor/Kokoro默认排除，App Manager不集成 | 重新启用时恢复其完整来源/对应源码/嵌入依赖义务；当前默认包不因此携带旧APK |
| Pixel专有组件 | 纯源码导出不包含；完整ROM已本地构建，未公开上传 | 缺覆盖拟分发专有负载的明确授权，不提供公开ROM下载 |

详细材料、标准文本与上游原文的区别见THIRD_PARTY_RELEASE_MATERIALS.md；对应源码见CORRESPONDING_SOURCE.md。没有发送上游联系消息，没有把未知项写成已确认。

## 主要原始依据

- [Apple锁定版本协议](https://huggingface.co/apple/MobileCLIP2-S0/blob/3136ea51c8ed56b9f9abfab04cb816735aaad6cb/LICENSE)
- [LFM锁定版本协议](https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/blob/657e078c94084481950a2d555a941481f715536b/LICENSE)
- [SenseVoice模型卡](https://huggingface.co/FunAudioLLM/SenseVoiceSmall)、[FunASR模型条款](https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE)
- [libadb锁定来源](https://github.com/MuntashirAkon/libadb-android/tree/c849886ebc6d48e7b46d967e78a6bb65c90c3b74)、[SPAKE2来源](https://github.com/MuntashirAkon/spake2-java/tree/7615ddd680b990e14513ebb66eac4cb0dbf82464)
- [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html)、[LGPLv3](https://www.gnu.org/licenses/lgpl-3.0.html)、[MPL2](https://www.mozilla.org/en-US/MPL/2.0/)
- [App Manager固定版本COPYING](https://github.com/MuntashirAkon/AppManager/blob/v4.1.1/COPYING)、[Editor许可](https://github.com/billthefarmer/editor/blob/v1.98/LICENSE)、[MJ PDF许可](https://gitlab.com/mudlej_android/mj_pdf_reader/-/blob/V3.1.0/LICENSE)
- [Collabora官方源码说明](https://www.collaboraoffice.org/post/faq/)

下载的上游条款、哈希及错误回执保存在本次本地审查证据目录。对明确采用自定义许可、缺失来源或未复现的产物保持待处理，不把“未发现问题”写成已经全部批准。

四项原始声明的逐项核查结果：`licenses/original-notice-review.json`。eastasianwidth已闭环，其余三项不冒充上游确认。
