# 当前第三方发行材料

自有代码许可已确定为GPL-3.0-only，第三方原许可不变。当前最新范围与门槛见RELEASE_READINESS.md，不沿用旧的27/29份声明计数作为最终APK事实。

## 本轮完成

- Codex使用现有ARM64 app-server的Cargo fingerprint递归闭包，而不是只看Cargo.lock；1231个编译单元、862个包名、零未解析边。含主机build/proc-macro单元，不是链接符号级SBOM，见licenses/codex-selected-build.json。
- 原13项中fax和fax_derive未进入当前构建图，已明确排除当前二进制适用范围；保留其候选源码记录。
- 剩余11项保存精确crate声明、已存在版权头、作者元数据、完整所选协议文本；10项有明确Apache-2.0选项，debugserver-types声明MIT。不得凭作者字段捏造版权年份。标准文本被标记为补充，未冒充上游独立LICENSE。
- npm7项保存精确版本/提交、原README、作者元数据与所声明协议全文。sigstore原始许可、imurmurhash README完整许可已保留；SPDX例外列表CC-BY-3.0和license-ids CC0全文及归属补齐。
- 自有GPLv3全文及范围加入随包声明。所有声明由apk-notices.json固定SHA-256并在最终包核验。
- 对应源码附件覆盖Codex实际Android源码、选中包名的保守版本集合、固定git源码、Node/npm、BoringSSL及MPL材料。见CORRESPONDING_SOURCE.md。附件不混入源码仓库，正式分发二进制时须同位置提供。

## 不能伪造的证据边界

eastasianwidth已补齐完整上游MIT声明与覆盖0.2.0的历史许可说明。debugserver-types、@npmcli/agent、err-code的已检查原始版本仍未找到独立完整版权/许可文件；已保留其明确许可声明、作者事实与标准全文，但不能声称获得额外上游确认。无VCS的Apache crate以原始发布包及哈希作为来源，未将未知仓库NOTICE写成已确认不存在。

当前随包材料的整理已落地，不等于所有二进制重建和许可疑点已获外部确认。没有向上游发送消息。MobileCLIP2/SenseVoice已退出当前装配，新模型采用MIT并保留来源；完整ROM专有组件授权未解决。

## 默认不再随包的应用

Chromium、Collabora、MJ PDF、Text Editor、Kokoro默认不预装；App Manager也不在默认集成。因此旧独立APK的再分发材料缺口不再阻塞当前默认源码/应用包。若显式重新启用某预装项，原始来源、对应源码与嵌入依赖审查重新适用。Pixel专有组件只按拟交付ROM的实际内容判断。
