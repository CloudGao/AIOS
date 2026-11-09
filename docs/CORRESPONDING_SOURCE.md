# 对应源码与重建交付

## 自有应用与 SPAKE2

自有代码采用GPL-3.0-only，SPAKE2 Java/JNI仍保留LGPLv3原文及版权；GPLv3应用与LGPLv3库兼容，不因许可差异强制换算法。GNU官方兼容表：https://www.gnu.org/licenses/gpl-faq.en.html#AllCompatibility 。本次没有替换配对实现。

源码ZIP包含 `android/host-apk/third_party/spake2/` 的实际Java和JNI源、`third_party/provenance.json` 的固定上游与修改说明、GPL/LGPL文本、`tools/apk/stage.py` 的实际Soong规则及构建脚本。JNI静态链接的BoringSSL固定提交为0cd1f6a94b670e6b61af95a06e71e690ae8e3262，对应源码单独附在 `corresponding-source/boringssl-source.tar.gz`。不能只给Java文件而遗漏JNI和静态链接库。

重建步骤：按BUILDING.md恢复固定Lineage环境与锁定输入；修改上述Java/JNI或BoringSSL源码；运行 `python3 tools/apk/build.py` 重新生成并编译应用。源码提供构建及重新组合所需规则，不锁定只能使用开发者私钥。构建脚本会使用自己的开发签名；新签名不能直接覆盖另一个签名的已安装应用。不要为测试擅自卸载旧应用或删除数据；需要用户先导出/迁移数据或在独立测试设备安装。已签名设备、ROM安装信息和数据保留条件须按实际发行形态另行验证。

## Codex、Node 与传递依赖

`corresponding-source/` 的附件清单和SHA-256见 `licenses/corresponding-source-bundle.json`：

- codex-android-source.tar.gz：固定上游加实际Android修改后的受跟踪源码，含Cargo.lock、源码哈希表，无Git历史、编译target、账号或私钥。
- codex-dependency-sources.tar.gz：实际构建图出现的包名对应registry原始crate和固定git源码；registry逐项匹配Cargo.lock，保守保留同名不同版本。内有SOURCE_INDEX.json。不是最终链接符号SBOM。
- node-v22.23.4.tar.xz：官方Node源码，含对应npm；安卓补丁和构建入口在主源码中。npm实际ZIP的重建配方见tools/distribution/rebuild-npm.py。
- mpl-source-verified.tar.gz：原有MPL候选组件的精确源码附件，保留原许可；即使有上述更广的附件仍保留便于核对。
- boringssl-source.tar.gz：SPAKE2 JNI实际静态链接上游的固定提交源码。

Codex构建配方为tools/codex-runtime/build-native.sh；本次对应的是既有编译产物及其fingerprint，不宣称已完成全新环境native重建。官方Lineage/Android系统库与工具链由resolved-manifest和构建文档固定；第三方运行时的原始许可、源/二进制来源核对仍以依赖清单为准。

## 发布方式

源码与对应附件应和适用二进制在同一下载位置提供，保留哈希、版本及修改说明，不能仅用仓库首页替代实际对应源码。当前只完成本地附件与候选，不存在可引用的已公开源码下载URL。正式上传时必须把附件一起上传并更新发行页；不得只上传APK而遗漏附件。

模型权重不属于上述源码附件；它们有各自许可。当前已替换为 MIT OpenCLIP 与 MIT Whisper，固定来源和原文见 MODEL_REPLACEMENT.md。模型权重不进入源码 ZIP；二进制随包声明保持原始版权与许可。
