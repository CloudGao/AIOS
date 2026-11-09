# AIOS 技术架构

当前基线：LineageOS 24.0、ARM64、ROM/APK 双宿主及共享业务层。
公开候选在原构建路径、依赖准备和源码导出整理之上，纳入2026-11-08主开发源码快照。权限与IPC实现沿用该快照，不在整理中扩大设备授权。

## 模块职责

| 模块 | 职责 |
|---|---|
| android/core-services | Workspace 持久状态、事件、调度、记忆、上下文、检索及可选推理能力 |
| android/core-runtime | Core IPC 客户端与共享原生 UI |
| android/agent | 助手会话、授权/介入、输入与平台能力编排 |
| android/codex-runtime | 手机本地 Codex app-server、提供商与逐轮上下文、CLI/MCP 扩展 |
| android/workspace、automation | Workspace 与自动化 UI/客户端 |
| android/host-rom、device-rom | 平台签名和系统服务适配 |
| android/host-apk | 单 APK、公开 SDK、应用内无线 ADB 与 shell 适配 |
| android/device-api、device-common、capability-contracts | 宿主契约与公共能力实现 |
| android/miniapp | 小程序存储、发布、管理与桌面集成 |
| android/speech | 语音采集与离线识别 |
| integration/lineage-24.0 | 固定上游 revision、补丁、附加文件与外部输入清单 |

ROM 的独立应用与系统服务通过权限、签名和 UID 校验协作。APK 将选定业务装配为一个包，shell 服务与主应用相互验证 UID；同包小程序/Runtime 的不同进程不能当作安全隔离。

## 调用与数据边界

助手通过 Runtime 发起原生模型轮次；动态工具调用由 Agent 路由至宿主或 Core。授权由原生 UI 与宿主校验，模型参数不构成授权。Core 保存业务投影与调度账本；原始会话由原生 Runtime 保存。
提供商凭据由 Runtime UID 的 Keystore 保护。通知无会话推理使用独立 HTTP 请求路径，不创建普通聊天历史。云端能力及失败回退由用户配置。

调度复用 WorkManager、Core 持久执行账本和资源池。屏幕归属绑定原生会话，输入前验证观察与目标，用户接管撤销操作能力。失败、重启恢复和提交未知必须保留各自状态，不能盲目重复外部动作。

## 产品装配

ProductSelection 读取产品能力清单；ROM integration 和 APK stage 选取共享源码与宿主实现。APK 标识在装配阶段转换，共享业务不维护第二份副本。两种宿主均使用 Soong/Lineage 构建，APK 还将 shell DEX 合入最终包。ROM Codex Runtime通过独立filegroup引入共享CompatFiles与CoreRuntimeClient源文件，不为文件IO和Core IPC引入整个UI库；单APK装配仍只包含一份实现。
当前唯一目标配置为 frankel，固定 out。其他机型必须新增明确配置并独立验收。

## 开源构建与导出

tools/lineage/paths.py 从环境读取 Lineage、外部输入及共享构建状态路径。默认 Lineage 为源码目录的同级 lineage；固定 out 规则仍由 target_config.py 维护。共享 checkout 的公开入口使用同一个构建锁。
dependencies.lock.json 按 source/inputs、APK/ROM 范围列出二进制和模型输入。依赖准备只读取列出的文件；采用字节数与 SHA-256 校验、临时文件与原子发布，不覆盖内容不符的现有文件。上游归档只读取锁定的普通成员，归档与成员均验证哈希；具体输入及归档成员恢复范围以当前锁定清单为准。
Codex 许可证直接保存为文本，不再依赖旧 APK 提取。integration 的来源哈希继续约束实际输入。
源码导出采用白名单，拒绝已有目标目录、符号链接和未登记大文件；不复制 .git、历史发行快照、本地状态或依赖负载。它生成完整文件哈希清单，不创建远程仓库或上传内容。

## 复现边界

固定 integration revisions 与依赖哈希只能约束已记录的输入，不替代设备专有组件和宿主工具链说明。当前完整仓库 HEAD 清单已保存为 integration/lineage-24.0/resolved-manifest.xml；未提交的 AIOS 修改由独立 integration 补丁提供。公共依赖下载地址尚未全部建立。APK将已审查的许可/归属文本按独立清单校验并装入assets/third-party-notices；该清单不是完整法律放行或最终链接依赖SBOM。原有设备证据不等于新公开导出已通过全新环境完整构建。详见 [BUILDING](BUILDING.md)、[VALIDATION](VALIDATION.md)。

## 最新主开发快照接入

APK公共文件、下载落盘与校验后安装在ADB激活时优先走shell后端；已提交任务不跨后端重放。shell调用继续检查调用UID及实例代际，文件创建拒绝符号链接覆盖。权限页按实际授权与服务状态判断就绪；Runtime在普通新轮和运行中引导提交前加载有预算的记忆参考，并记录读取版本，失败不复用旧快照。共享离线语音服务已接入 WhisperRecognizer JNI；保留 SenseVoiceService 组件标识以兼容已有服务选择。

Runtime通过独立filegroup复用CompatFiles和CoreRuntimeClient；Core IPC客户端依赖已链接的HostBindings，不引入整个Core UI库。主开发目录不被此次合并修改，合并文件哈希保存在本地证据中。

## 可选第三方负载

ROM `optional-dependencies.json` 独立选择可选应用及三类权重的内置范围，默认空列表。integrate 在来源校验、源文件、归档成员、Soong 声明和产品 makefile 各层按同一配置过滤，并用既有清单哈希核对后移除固定 staging 中已关闭的旧负载；不删除用户私有文件。集成回执记录配置指纹，系统镜像携带配置，release 审查核对实际 APK/权重与选择一致。OpenCLIP 的图像/文本模型、词表及词嵌入表由新空间指纹绑定；Whisper 权重和 JNI 库独立锁定，旧 SenseVoice 权重不再装配。

CoreRuntime 提供固定版本模型描述、流式大小/哈希校验与原子文件提交。Core 的 LFM/人脸设置和 Speech 的声纹设置运行在各自 UID，通过签名权限限制跨包启动，文件写入实际消费者的私有目录。没有自定义模型架构或任意文件路径执行。退出设置页取消传输；下载错误保留原文件。LFM 优先加载私有固定版本，否则兼容原系统模型；声纹兼容经校验的旧私有模型。

人脸作为独立可用性处理：ModelRegistry 保留原空间标识和旧 profile，通过同一 SHA 核验可选文件。真实 GPU 验证只给成功角色写证据；人脸失败不阻断 image/text。照片账本记录 model_unavailable，验证成功后的后续处理恢复 pending，查询覆盖率不把缺失人脸结果视为完整。既有向量和用户模型不自动清空。

最新选择入口：OpenCLIP 支持当前配对模型或停用，保留既有文件/索引；Whisper 与系统/已安装 Android 识别服务可供后续助手语音输入选择，会议批量转写和内置离线引擎自检使用同一 Whisper 实现。旧 SenseVoiceService 组件名保留用于升级兼容。BlazeFace 仅支持稠密转换版导入，官方稀疏文件不能直接加载。

发行声明由licenses/apk-notices.json统一锁定：APK放入assets/third-party-notices，ROM集成放入system_ext/etc/aios/licenses；发行审查逐文件核对缺失和哈希，不把源目录有声明当作二进制已包含。自有代码GPLv3与第三方原许可的范围见LICENSING.md。

第三方应用目录由 RomUiHost 注入设置页；HostUi 默认不显示该入口，APK stage 同时排除 OptionalAppsActivity 的实现及清单注册。APK 的语音服务选择保留，照片、本地文本及声纹模型入口按实际产品模块过滤。

## 模型替换实现

OpenCLIP 使用224像素CLIP标准归一化。词嵌入查表与EOT掩码在Java侧准备，查表文件校验固定SHA；两个神经网络图使用LiteRT GPU。文本图显式展开各注意力头的因果掩码，避免当前OpenGL后端的广播数值偏差，文本计算使用FP32精度、权重保持FP16。真机验证必须证明图内全部节点委托给GPU，不把CPU查表描述为神经网络GPU算子。

新配对模型写入search-models/profiles/<space_id>，最后原子更新active-profile.json。旧根目录/旧空间保留，旧索引按其空间定位原模型，仍须满足当前ROM的验证要求；新索引按新空间生成，不混用新旧向量。没有搬移或删除用户照片。

Whisper Small Q5_1由vendored whisper.cpp v1.9.5执行；Java生命周期读写锁避免解码、取消、释放之间的悬空native指针。助手服务与MinutesEngine共用解码器，VAD和可选声纹仍复用原sherpa依赖。模型首次复制到私有目录前验证SHA，流式写入与原子提交；单次输入不超过30秒。RecognitionService 根据麦克风输入或传入 PCM 分别使用 microphone / mediaProcessing 前台服务类型，识别会话持有最长 240 秒 PARTIAL_WAKE_LOCK，完成或取消时释放并移除通知；保留四线程 native 解码，不修改系统后台白名单。
