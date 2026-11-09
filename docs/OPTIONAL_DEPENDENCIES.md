# 可选应用与模型

当前视觉/语音入口使用 OpenCLIP 与 Whisper Small，原 MobileCLIP2/SenseVoice 权重已退出装配。以下配置不构成任何第三方许可放行，也不处理 SPAKE2、Codex/npm 内部依赖或 Pixel 专有组件授权。

## 用户入口

- ROM：系统管理 → 模型管理 → LFM / BlazeFace / CAM++。查看官方来源与许可参考，LFM / CAM++ 可主动下载或导入固定版本文件。BlazeFace 当前只接受匹配哈希的稠密转换版导入，上游稀疏原文件不能直接使用。没有网络自动下载。退出页面中止传输，可重新开始；暂不支持断点续传。
- APK：应用设置 → 语音输入模型／服务。保留账号与助手模型配置，不展示 Chromium、文档应用、TTS 等 ROM 可选应用目录；不注册该目录 Activity。
- 仅 ROM 系统管理：可选应用。可访问官方应用来源，再交给 Android 安装；默认应用、文档选择器和 TTS 引擎由系统管理。未安装浏览器时需先自行安装浏览器才能打开外部页面。
- CAM++、BlazeFace 的精确权重许可仍待核实，界面已提示；不能用上游代码许可证代替权重授权。LFM 适用其独立条款。
- 仅支持内置描述中的固定兼容模型与 SHA-256。换文件或换地址不能自动支持另一个架构。下载/导入只写对应组件的私有模型目录。
- 缺少 LFM 时本地文本推理不可用，可另行配置云端模型；没有声纹模型不阻断转写；没有人脸模型不阻断 image/text，但不能提供完整的人脸筛选结果。
- 人脸下载成功后还需设备实际 GPU 验证；后续照片索引处理会补齐人脸结果。GPU 失败不伪造验证通过。

## 构建配置

`android/host-rom/optional-dependencies.json` 默认 `bundled_models: []`、`bundled_apps: []`。

可选模型 ID：`lfm`、`speaker`、`face`。
可选应用 ID：`AIOSChromium`、`AIOSCollabora`、`AIOSPdfReader`、`AIOSTextEditor`、`AIOSKokoroTts`。

仅在已准备精确锁定输入并自行完成对应许可审查时，把需要内置的 ID 加入列表。运行标准 integrate 的 plan/apply；旧 stage-release 入口也委托同一集成流程。配置只影响本项目维护的可选负载，不移除 Lineage 自身应用。不会自动清理 out 或卸载设备中的应用。

集成按配置过滤来源/成员/声明，关闭项不再要求恢复其独立二进制输入；图文模型和可选人脸模型已拆为独立输入，不再要求旧 MobileCLIP2 归档。release 对目标文件验证配置一致性与关闭项残留。旧构建产物不会因此自动变成新候选，必须重新构建并通过审查。

## 当前验证边界

已完成统一主机检查、APK 与五个相关 ROM 模块构建，以及 Pixel 10 上语音选择、应用语言切换和 RTL 的限定设备验收。路线调整后的检查及最终包以 BUILD_ACCEPTANCE.md 为准。真实模型下载、外部识别服务实际录音和设备 GPU/推理仍未验收；应用语言切换不等于全系统语言及已有助手会话验收。

## OpenCLIP 与 Whisper 选择

仅 ROM 的照片语义模型页提供当前 OpenCLIP ViT-B/16 DataComp 配对模型与停用选项；停用对后续模型请求生效，并暂停共用的照片索引/人脸处理流程，保留文件/索引，不代表从发行包移除了权重，也不改变许可。本次替换的配对导出、预处理、空间指纹和真机GPU验证见MODEL_REPLACEMENT.md。

语音输入模型/服务页保留产品原默认行为，也可明确选择 Whisper Small、Android 默认识别服务或已安装且符合 Android 识别服务接口的提供方；对下一次助手语音输入生效，切换时提示第三方可能联网处理音频。已卸载服务不会静默回退。录音/会议批量转写仍使用 Whisper Small，APK 权限页的离线引擎自检仍专门检查内置 Whisper Small。

## 人脸离线转换

官方稀疏文件为 676746 字节，SHA-256 `2c3728e6da56f21e21a320433396fb06d40d9088f2247c05e5635a688d45dfe1`；Android 当前需要稠密文件 3628816 字节，SHA-256 `3e82b513e65d00b56c35b572f964a594fdbd6e56717361bc0b64cbaf9e140a4d`。不能将两个地址/文件混用。

在隔离 Python 环境中安装 `tools/distribution/face-requirements.txt` 的锁定依赖后运行：

```sh
python tools/distribution/prepare-face.py --source /path/to/face_detection_full_range_sparse.tflite --output /path/to/new-face.tflite
```

脚本仅读取明确指定且哈希匹配的原文件，物化常量稀疏/FP16 运算，验证输出哈希与参考结果后写入新文件。不同工具版本若导致图不同会拒绝输出；不会放宽哈希、覆盖现有文件或下载权重。本轮使用 LiteRT 2.3.0、numpy 2.5.4、flatbuffers 25.12.19 重现了完全相同的输出 SHA-256；参考输出最大绝对误差为 0。主机数值比较不替代设备 GPU 验证。

`dependencies.py --profile rom` 现按可选负载配置准备/检查需要的输入；`--profile all` 仍用于核对完整锁文件库存。
