# Building AIOS / 构建说明

Both APK and ROM builds currently require Linux x86-64 (or WSL2), Python 3.11+, Git, the matching LineageOS 24.0 source tree, and its Android/Soong prerequisites. APK is not a standalone Gradle project. The only supplied device configuration is frankel / ARM64.

## Configure paths

Run from this repository root. Choose an existing, dedicated Lineage checkout; do not stage into another developer's active build tree.

```sh
export AIOS_LINEAGE_ROOT=/path/to/lineage
export AIOS_INPUTS=/path/to/aios-inputs
# Optional: shared across every worktree using this Lineage checkout.
export AIOS_BUILD_STATE="$AIOS_LINEAGE_ROOT/.aios-state"
# Optional WSL physical-volume and virtual-disk measurements:
# export AIOS_HOST_VOLUME=/mnt/f
# export AIOS_VHD_PATH=/path/to/ext4.vhdx
```

Without overrides, Lineage defaults to a sibling `lineage` directory, inputs to `.local/inputs`, and build state to Lineage's `.aios-state`. The target output remains `out`. No date-based output directories or implicit clean builds are introduced. APK signing keys remain under ignored `tools/apk/state/signing`; preserve them to update an existing installation.

## Recover pinned inputs

```sh
python3 tools/distribution/dependencies.py prepare --profile apk --cache /path/to/cache
python3 tools/distribution/dependencies.py check --profile apk
```

For ROM use `--profile rom`; this honors `android/host-rom/optional-dependencies.json`. Its default empty lists exclude the optional application/model payloads. Use `--profile all` only to inventory/restore the complete lock, including disabled payloads. See [optional dependencies](OPTIONAL_DEPENDENCIES.md). Offline restoration can use `--from-source /path/to/existing-source` and `--from-inputs /path/to/external-inputs`. Files with conflicting contents are rejected, not overwritten. Network access requires `--download`; unpublished entries remain missing. See [DISTRIBUTION](DISTRIBUTION.md).

The dependency lock is a content lock, not proof that all binary artifacts have complete source reconstruction or redistribution clearance. The full external-input set includes speech, Codex runtime, text inference, models and ROM application packages. Missing inputs must be resolved before compilation.

## Lineage source and integration

Use official LineageOS 24.0 sources and the frankel device dependencies. `integration/lineage-24.0/manifest.json` pins the upstream repositories touched by AIOS; it is not a complete repo resolved manifest. Do not apply patches to arbitrary newer heads. The captured complete manifest is integration/lineage-24.0/resolved-manifest.xml. It records current repository HEADs, not uncommitted patches; the separate AIOS integration applies those changes. Device proprietary inputs and availability still require review before a clean ROM build.

```sh
python3 tools/lineage/sync_source.py --plan
python3 tools/lineage/integrate.py --target frankel --plan
# After reviewing the plan, this modifies the selected Lineage tree:
python3 tools/lineage/integrate.py --target frankel --apply
```

Source sync is a separate deliberate action; the plan command never downloads. Device access is not part of integration.

## APK

```sh
python3 tools/apk/build.py --stage-only
python3 tools/apk/check_sdk.py
python3 tools/apk/build.py
```

Final APK: `tools/apk/state/AIOS.apk`; receipt: `tools/apk/state/build.json`. The builder uses a private local development signing identity. It does not install the APK. A new checkout with a new key cannot update an installation signed by someone else's key.

## ROM modules and images

```sh
python3 tools/lineage/build-experience.py --target frankel
python3 tools/lineage/build-unified.py --target frankel --images systemimage
# Full product only when explicitly needed:
python3 tools/lineage/build-unified.py --target frankel --full
```

Modules and images use the fixed target out and shared lock. Builds check free space on Linux and, when configured, the physical host volume. Original one-off native-validation bypasses are not a public build path.

## Native source alternatives

`tools/lineage/build-text-inference.sh` builds the vendored llama source with NDK r29 (`ANDROID_NDK_HOME`). `build-phone-node.sh` needs the exact Node 22.23.4 source (`AIOS_NODE_SOURCE`) and NDK r29. `tools/codex-runtime/prepare-source.py` creates a new pinned upstream checkout and applies reviewed patches; `build-native.sh` documents the Rust/NDK build.

Locally rebuilt binaries can differ from locked bytes. Do not change the lock just to silence a mismatch: review source/toolchain provenance, integration hashes and acceptance together.

## Current acceptance

Only the checks explicitly recorded in [VALIDATION](VALIDATION.md) and [BUILD_ACCEPTANCE](BUILD_ACCEPTANCE.md) are claimed. Recorded incremental partition builds do not imply a clean full-product rebuild or universal device compatibility. Packaging/signing for distribution is separate from code compilation; do not run release tooling with another developer's signing keys.

For host-only assembly/SDK validation, AIOS_APK_STAGE may point to a dedicated temporary directory. The actual APK builder rejects alternate staging destinations; it only builds vendor/aios-apk in the configured Lineage tree.

## Optional speech probe

`tests/apk-first-run/build_probe.py --zh-wav /path/to/zh.wav --en-wav /path/to/en.wav` builds an instrumentation probe using caller-provided, appropriately licensed mono 16-bit 16kHz WAV fixtures and the existing local APK development key. Audio fixtures are not included in the source export. Building the probe does not install it or authorize device access.

## Reconstruct bundled npm

Download the official Node 22.23.4 source tar.xz linked in dependencies.lock.json, then run `python3 tools/distribution/rebuild-npm.py --node-archive /path/to/node-v22.23.4.tar.xz`. The helper verifies the upstream archive, every selected source member and the final ZIP against fixed hashes. It refuses conflicting output. This npm payload comes from Node’s bundled source, not the separately published npm registry TGZ. A different ZIP implementation/zlib result must be reviewed rather than silently changing the lock.

## Reconstruct LiteRT offline API

Restore the three LiteRT 2.3.0 classes.jar inputs using the dependency tool, then run `python3 tools/distribution/rebuild-litert-api.py`. The fixed member recipe reconstructs the existing API JAR without changing class bytes, and checks the final SHA-256. See android/core-services/third_party/litert/README.md.


## 当前离线模型输入

大型模型不随源码导出。Whisper Small Q5_1使用dependencies.lock.json中固定HF revision的直链和SHA；准备工具可下载该文件。JNI库由`third_party/whisper`源码构建：

```sh
python3 tools/distribution/build-whisper.py --ndk /path/to/android-ndk-r29
```

该命令要求CMake和Ninja/Make在PATH中，输出`android/speech/lib/libaios_whisper.so`。模型文件保持在`android/speech/assets/ggml-small-q5_1.bin`；这些二进制被Git忽略。

ROM图文模型使用`android/core-services/third_party/models/provenance.json`中固定revision的OpenCLIP checkpoint。校验checkpoint SHA后，用隔离环境的`tools/distribution/openclip-requirements.txt`运行：

```sh
python tools/distribution/export-openclip.py --checkpoint /path/to/open_clip_pytorch_model.bin --output android/search-models
```

生成image.tflite、text.tflite、token-embeddings.fp16和配套manifest/tokenizer。先比对dependencies.lock.json及集成清单中的SHA，再进行装配；不能用不同转换结果跳过验证。转换脚本还会输出参考向量和FP32中间文件，这些是本地验证输入，不进入源码包或ROM。可选人脸模型独立使用prepare-face.py，不再要求下载含旧MobileCLIP权重的归档。

## APK 发行签名与 ROM 打包空间

APK 0.1.11 使用 versionCode 12。`tools/apk/build.py --signing-dir /private/existing-signing-directory` 指向已有 `development.pk8` 和 `development.x509.pem`；名称是历史约定，不代表公开 Android testkey。显式目录缺任一文件会失败，不自动换证书。升级必须沿用发布者持有的同一私有身份，私钥不加入源码或安装包。

ROM 签名使用已有私有 release/platform/AVB 身份。打包进程的 `TMPDIR` 可指向空间充足的临时目录；离线验证支持 `--scratch-dir /path/to/scratch`，至少需 50 GiB 空闲。仅改变临时解包位置，不移动固定 out。
