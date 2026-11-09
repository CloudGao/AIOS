# 依赖原始来源

优先使用上游原始地址，无需默认自行托管。固定当前版本和 SHA-256，不自动升级。自有代码许可证仍待决定。

48 项文件中：18 项填写了直接下载地址，20 项可从上游 AAR/模型归档校验后提取；8 项属于本地编译、转换或重打包；2 项旧版 APK 尚无可用的精确下载地址。

2026-11-08历史校验范围（下述SenseVoice归档已退出当前装配）：13 个直链文件已实际下载并匹配 SHA-256；App Manager、Collabora、Silero、LFM 使用上游发布摘要核对；speaker 下载遇到 TLS 中断，只确认上游资产及大小。15 个 AAR 成员已核对归档和成员哈希。2026-11-08补验SenseVoice官方163002883字节归档及5个成员全部匹配，已启用固定成员恢复。

Chromium 已定位源码版本，但尚未找到当前 APK；Kokoro 1.13.8 历史下载地址返回 404。源码地址不能当作同一二进制文件的直链。原始模型授权仍按各自条款保留。

## 完整清单

### 当前图文模型

- [固定OpenCLIP checkpoint](https://huggingface.co/laion/CLIP-ViT-B-16-DataComp.XL-s13B-b90K/resolve/d110532e8d4ff91c574ee60a342323f28468b287/open_clip_pytorch_model.bin)
- 原始checkpoint 598599013字节，SHA-256 `80905176486d952914c1deb3b753b8e653ab54aa4ffb14208bc5007cf7643a16`；转换入口tools/distribution/export-openclip.py。
- 当前锁定image/text/token-embeddings输出；不再使用旧production-models.tar。

### 当前语音模型

- [固定Whisper Small Q5_1](https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q5_1.bin)
- 190085487字节，SHA-256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`；MIT原始声明及运行时源码来源见licenses/whisper-provenance.json。


### release-inputs/appmanager.apk

upstream release.

- [上游原始地址](https://github.com/MuntashirAkon/AppManager/releases/download/v4.1.1/AppManager_v4.1.1.apk)
- 校验状态：upstream_release_digest_verified
- SHA-256：`2540d6d9352acd8b04d6210140630a380abb1bc46ad4745c89ad97603dee3c40`

### release-inputs/chromium.apk

Source version 158.0.8122.0 found; exact APK download unresolved.

- [上游原始地址](https://chromium.googlesource.com/chromium/src/+/99cd20dd000f91790b4a27f88a3452a43fbe35ad)
- 校验状态：source_identified; exact artifact reconstruction/download pending
- SHA-256：`1f11bc1870586e31df6b6b99939164452f29e47a86e41181f473c6b8a64309b1`

### validation/document-capabilities/apps/collabora.apk

upstream release.

- [上游原始地址](https://www.collaboraoffice.com/downloads/fdroid/repo/collabora-office-mobile-26.04.4.2-937-release-arm64-v8a-2026-10-05.apk)
- 校验状态：upstream_index_digest_verified
- SHA-256：`79e72f810d711706d205a9ff457b4bb830c607a2b988cf44246829ec4593c393`

### validation/document-capabilities/apps/editor.apk

upstream release.

- [上游原始地址](https://f-droid.org/repo/org.billthefarmer.editor_198.apk)
- 校验状态：download_sha256_verified
- SHA-256：`343d17e11d0914e1a7708df2781580a0413e52501c0a41d91f7ef725d9615fe9`

### validation/document-capabilities/apps/mj-pdf.apk

upstream release.

- [上游原始地址](https://gitlab.com/-/project/38392439/uploads/4ab1e84c9420fbd0ccb9107ded93ccec/mj-pdf-arm64.apk)
- 校验状态：download_sha256_verified
- SHA-256：`c095c1d339aa58eb9dddca701054419c373957941bd518df50eacca33737a5d2`

### android/browser-bridge/agent-browser

Patched Android build: tools/browser/prepare.py.

- [上游原始地址](https://github.com/vercel-labs/agent-browser/tree/50c34e78c98dbe8add0b3ff36b4bcd7a47f1022b)
- 校验状态：source_identified; exact artifact reconstruction/download pending
- SHA-256：`67fcac36c4e9a1930d5808a5349f39174b7ccd718f6b56b615b3df3974532a1e`

### android/codex-runtime/assets/cli-runtime/npm.zip

Node 22.23.4 bundled npm; the npm registry TGZ is not the matching source. Rebuild with tools/distribution/rebuild-npm.py.

- [上游原始地址](https://nodejs.org/dist/v22.23.4/node-v22.23.4.tar.xz)
- 校验状态：official_source_members_and_rebuilt_zip_sha256_verified
- SHA-256：`dc834ba76c8fcaa5ad17360f61d6947d9e235c61d6479baba6c6e67937577a0e`

### android/codex-runtime/native/libaios_cli_launcher.so

Project source android/codex-runtime/cli-launcher.c; tools/lineage/build-phone-node.sh.

- 校验状态：fresh_ndk_r29_build_sha256_verified
- SHA-256：`8c185a77b949a7076ce6c916c8a8fef51b5498a8372fc593a0ac2b52825fec34`

### android/codex-runtime/native/libaios_node.so

Android build: tools/lineage/build-phone-node.sh.

- [上游原始地址](https://nodejs.org/dist/v22.23.4/node-v22.23.4.tar.xz)
- 校验状态：source_identified; exact artifact reconstruction/download pending
- SHA-256：`211c50d6f91f96b9df10f2129bd7872fb26c386ccdef3b567d14f6a17c93bad1`

### android/core-runtime/third_party/commonmark/commonmark-ext-gfm-strikethrough.jar

upstream release.

- [上游原始地址](https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-gfm-strikethrough/0.24.0/commonmark-ext-gfm-strikethrough-0.24.0.jar)
- 校验状态：download_sha256_verified
- SHA-256：`7385cb637f04dc4cbda4ddca9c2fcd2af7ac536a50e4c8d2c77f4748bb14bf41`

### android/core-runtime/third_party/commonmark/commonmark-ext-gfm-tables.jar

upstream release.

- [上游原始地址](https://repo.maven.apache.org/maven2/org/commonmark/commonmark-ext-gfm-tables/0.24.0/commonmark-ext-gfm-tables-0.24.0.jar)
- 校验状态：download_sha256_verified
- SHA-256：`b54dc332f931e6d07c2766144c087b08f3693677e368151a67020b4e95bb4b99`

### android/core-runtime/third_party/commonmark/commonmark.jar

upstream release.

- [上游原始地址](https://repo.maven.apache.org/maven2/org/commonmark/commonmark/0.24.0/commonmark-0.24.0.jar)
- 校验状态：download_sha256_verified
- SHA-256：`679338e0b7fc15c02d275d598654b01a149893bc28a87992e90123c8d06af25b`

### android/core-services/third_party/documents/bcpkix-jdk15to18-1.72.jar

upstream release.

- [上游原始地址](https://repo.maven.apache.org/maven2/org/bouncycastle/bcpkix-jdk15to18/1.72/bcpkix-jdk15to18-1.72.jar)
- 校验状态：download_sha256_verified
- SHA-256：`d9b97477b72499bcee02f5a906510810257ff36a94bf69fbca0b1e65e7ffdb6e`

### android/core-services/third_party/documents/bcprov-jdk15to18-1.72.jar

upstream release.

- [上游原始地址](https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk15to18/1.72/bcprov-jdk15to18-1.72.jar)
- 校验状态：download_sha256_verified
- SHA-256：`ea66ea8a450810b2193e8bf9a7ad3e46307c9896224c0f407d1b7d96ba1221cc`

### android/core-services/third_party/documents/bcutil-jdk15to18-1.72.jar

upstream release.

- [上游原始地址](https://repo.maven.apache.org/maven2/org/bouncycastle/bcutil-jdk15to18/1.72/bcutil-jdk15to18-1.72.jar)
- 校验状态：download_sha256_verified
- SHA-256：`d92184bdeb3105a11ad9e36acbd66b5f8eed091b08b9c8f3e2549e42b7f131f1`

### android/core-services/third_party/documents/pdfbox-android-2.0.27.0.aar

upstream release.

- [上游原始地址](https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.aar)
- 校验状态：download_sha256_verified
- SHA-256：`30277f879cfd571db2a137582c95516a0d4ea6778e945519bc58ca93d57d88c7`

### android/core-services/third_party/litert/litert-2.3.0.aar

upstream release.

- [上游原始地址](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/2.3.0/litert-2.3.0.aar)
- 校验状态：download_sha256_verified
- SHA-256：`9508c6e9142646f0f2bc1fe68b3925fb470ee7ddd29114540339a04a8ab189dc`

### android/core-services/third_party/litert/litert-api-2.3.0.aar

upstream release.

- [上游原始地址](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-api/2.3.0/litert-api-2.3.0.aar)
- 校验状态：download_sha256_verified
- SHA-256：`20f630e9ed0b4f0564da616fd3d6406f3013cacad7d492cbe2963330f519256b`

### android/core-services/third_party/litert/litert-api/jni/arm64-v8a/liblitert_jni.so

upstream archive member.

- [上游原始地址](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-api/2.3.0/litert-api-2.3.0.aar)
- 归档成员：`jni/arm64-v8a/liblitert_jni.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`b4bc082f740076f09d566b2deaa8a1af6b1ec1252d20c3688acb8405d69871dc`

### android/core-services/third_party/litert/litert-gpu-2.3.0.aar

upstream release.

- [上游原始地址](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-gpu/2.3.0/litert-gpu-2.3.0.aar)
- 校验状态：download_sha256_verified
- SHA-256：`173a4c3813601f7e9542f4e6a49e237775e2f9ce1e0948f35407098bffb1824f`

### android/core-services/third_party/litert/litert-gpu/jni/arm64-v8a/libLiteRtClGlAccelerator.so

upstream archive member.

- [上游原始地址](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-gpu/2.3.0/litert-gpu-2.3.0.aar)
- 归档成员：`jni/arm64-v8a/libLiteRtClGlAccelerator.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`c200369269a3994b646a8d0ac56ff29127c6ce742abcfd345a7d478eab3b6c6d`

### android/core-services/third_party/litert/litert-offline-api.jar

Selected unchanged LiteRT 2.3.0 class members; reconstruct with tools/distribution/rebuild-litert-api.py.

- [上游原始地址](https://github.com/google-ai-edge/LiteRT)
- 校验状态：upstream_class_members_and_rebuilt_jar_sha256_verified
- SHA-256：`c7c9c45fa0581323b4760861ba3395100c5217833cdf85806ac4858575bba003`

### android/core-services/third_party/litert/litert/jni/arm64-v8a/libLiteRt.so

upstream archive member.

- [上游原始地址](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/2.3.0/litert-2.3.0.aar)
- 归档成员：`jni/arm64-v8a/libLiteRt.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`e5b7e3413a8b428b1a1b669f368cf613058868bc7b63e10d0b9c2e6ba55c97d8`

### android/core-services/third_party/text-inference/arm64-v8a/libaios_llama_server.so

Use vendored third_party/llama and tools/lineage/build-text-inference.sh.

- [上游原始地址](https://github.com/ggml-org/llama.cpp)
- 校验状态：source_identified; exact artifact reconstruction/download pending
- SHA-256：`62445fa7d1fe612107eb68779e42910e8d1c70514802ce434c6f3aaa81cec8e3`

### android/miniapp/third_party/webkit-1.13.0.aar

upstream release.

- [上游原始地址](https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/1.13.0/webkit-1.13.0.aar)
- 校验状态：download_sha256_verified
- SHA-256：`001c836ea612e835991288a7a66877f25288e4cf5ec89729ee7ae066e3491a35`

### android/speech/assets/LICENSE

Verified upstream archive member; runtime file hash unchanged.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2)
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`221c6df10b0931a5629adad671ea48fb7747e034c414b6d2bfa275bc3dd4ea17`

- 归档成员：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/LICENSE`

### android/speech/assets/en.wav

Verified upstream archive member; runtime file hash unchanged.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2)
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`eb1eb008904465b74c304aad8342e8c7d3c6e61ffe9f66adcaca9cf0f76a93f4`

- 归档成员：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/test_wavs/en.wav`

### android/speech/assets/model.int8.onnx

Verified upstream archive member; runtime file hash unchanged.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2)
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51`

- 归档成员：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/model.int8.onnx`

### android/speech/assets/silero_vad.onnx

upstream release.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx)
- 校验状态：upstream_release_digest_verified
- SHA-256：`9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`

### android/speech/assets/speaker.onnx

upstream release.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx)
- 校验状态：upstream_asset_size_verified; download interrupted; SHA verification required
- SHA-256：`f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11`

### android/speech/assets/tokens.txt

Verified upstream archive member; runtime file hash unchanged.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2)
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc`

- 归档成员：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/tokens.txt`

### android/speech/assets/zh.wav

Verified upstream archive member; runtime file hash unchanged.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2)
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`b77f1794fe374a0ba1ee1dc458bfaf9349496cbbfc32780c50ba3c5a7ad8e373`

- 归档成员：`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/test_wavs/zh.wav`

### android/speech/deps/classes.jar

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`classes.jar`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`8af5aa647494ec9cb6596959d6d20864377cf4a9dedf1c1b178d936bfb7cea95`

### android/speech/deps/lib/arm64-v8a/libonnxruntime.so

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`jni/arm64-v8a/libonnxruntime.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1`

### android/speech/deps/lib/arm64-v8a/libsherpa-onnx-c-api.so

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`jni/arm64-v8a/libsherpa-onnx-c-api.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`c85e2382e2ca12b0513826be926eaccfea896b4cbf568182e287671ba526f80d`

### android/speech/deps/lib/arm64-v8a/libsherpa-onnx-cxx-api.so

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`jni/arm64-v8a/libsherpa-onnx-cxx-api.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`5eabb2bcf5a8af66aeab37431c09fab4a9c65cdebf02bd7e50f8c589044bd70e`

### android/speech/deps/lib/arm64-v8a/libsherpa-onnx-jni.so

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`jni/arm64-v8a/libsherpa-onnx-jni.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`3c6492ea91ea68fd3b72b9495efdde6dd913f6b58c387e3b07389cf335f4d26f`

### android/speech/deps/lib/x86_64/libonnxruntime.so

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`jni/x86_64/libonnxruntime.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`087e84297cad64acb823c3d901d6e0cc934e91add7cdb20da802cd1c0bea5c3e`

### android/speech/deps/lib/x86_64/libsherpa-onnx-c-api.so

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`jni/x86_64/libsherpa-onnx-c-api.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`631ca2828e47b61d8afe4b3c75a157423cbafaba6f8c0db89c27d21f1b173d9a`

### android/speech/deps/lib/x86_64/libsherpa-onnx-cxx-api.so

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`jni/x86_64/libsherpa-onnx-cxx-api.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`09cd2ea23b15ae3234b0b4678fbef14639c58792dd36d2cd435c030dc030c5d0`

### android/speech/deps/lib/x86_64/libsherpa-onnx-jni.so

upstream archive member.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)
- 归档成员：`jni/x86_64/libsherpa-onnx-jni.so`
- 校验状态：archive_and_member_sha256_verified
- SHA-256：`1dc24c88fb364f1022a5217b70ab3bc3c9cae3120ae2e1ed358e1f68ed120b04`

### android/text-models/LFM2.5-350M-Q4_K_M.gguf

upstream pinned revision; LFM Open License v1.0 applies.

- [上游原始地址](https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/resolve/657e078c94084481950a2d555a941481f715536b/LFM2.5-350M-Q4_K_M.gguf)
- 校验状态：upstream_lfs_digest_verified
- SHA-256：`7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4`

### tools/codex-runtime/build/arm64-v8a/release/libcodex_app_server.so

Patched Android build: tools/codex-runtime/prepare-source.py and build-native.sh.

- [上游原始地址](https://github.com/openai/codex/tree/092d3acd6bec3e3a14bdc7e7a2810ab628ab759d)
- 校验状态：source_identified; exact artifact reconstruction/download pending
- SHA-256：`fae801495ad3a126b7293da30c172d78f667940b0546e025ff67add641228973`

### tools/lineage/state/kokoro-arm64.apk

Historical official 1.13.8 APK URL returns 404; do not substitute latest.

- [上游原始地址](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8)
- [上游原始地址](https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh)
- 校验状态：source_identified; exact artifact reconstruction/download pending
- SHA-256：`71f41df45800fe1c4f1208f5f5fd1abd92e123f2ecd8f251e80688157d18647b`

## 构建验收补充

ROM 集成还依赖 LiteRT、LiteRT API、LiteRT GPU 三个 classes.jar，已补入锁清单。均从对应 2.3.0 AAR 的 classes.jar 成员提取，归档与成员哈希均已验证。

本地加工项中，CLI启动器已新编译匹配、npm ZIP和LiteRT offline API JAR已从上游输入重建匹配；Node和Codex完成既有输出对应核验。其余完整原生重建与模型转换仍按各自状态保留待办。
