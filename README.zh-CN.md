# AIOS

Android 上的 AI 助手、工作空间与自动化。提供 APK 和 ROM 两条路线，共用核心业务代码。

[English](README.md) · [使用说明](docs/USAGE.md) · [构建说明](docs/BUILDING.md) · [架构](docs/ARCHITECTURE.md)

## 可以做什么

- **助手与会话**：手机端 Codex 运行环境、工具调用与技能。
- **Workspace**：管理记忆、任务和会话关联。
- **自动化与小程序**：调度任务，运行 MiniApps。
- **ROM 系统能力**：进一步接入通知治理、录音纪要和图文搜索。

界面提供英文、简体中文、繁体中文，其他语言回退英文。数据在手机本地管理；使用配置的云端推理服务时，相关输入会发送给该服务。完整能力与限制见 [产品设计](docs/DESIGN.md)。

## 选择 APK 还是 ROM

| | APK | ROM |
|---|---|---|
| 目标环境 | Android 15+、ARM64 | Pixel 10 frankel、ARM64、LineageOS 24.0 |
| 安装形态 | 一个 APK | 系统镜像 |
| 系统接入 | Android 权限、本机无线 ADB 配对 | 平台服务与 Lineage 适配 |
| 公共能力 | 助手、会话、记忆、任务、技能、调度、小程序 | 同一公共业务层 |
| 路线差异 | 受厂商系统、shell 权限和后台策略限制 | 增加通知治理、录音纪要、图文搜索等系统集成 |
| 可选依赖 | 仅显示 APK 使用的模型等选项 | 提供 ROM 使用的模型和第三方应用选项，默认不预装五项可选应用 |

APK 不提供 Chrome、邮箱等 ROM 应用选择，也不包含 ROM 图文搜索模型。可选项及默认值见 [可选依赖](docs/OPTIONAL_DEPENDENCIES.md)。

## 当前状态与下载

**本仓库提供源码，尚未发布二进制下载。** APK 0.1.11 和完整 ROM 候选交付包已在本地准备。

- **APK**：签名、对齐、同签名升级及锁屏中英文语音样本验证通过；这不代表全部功能或所有机型已完成验收。
- **ROM**：完整产品已构建，八项离线校验通过；实机安装、启动及保留数据升级尚未验收，Pixel 专有组件再分发授权尚未确认。因此仍是候选包。
- **模型**：MobileCLIP2 已替换为 MIT OpenCLIP，SenseVoice 已替换为 MIT Whisper。模型对比与测试范围见 [替换记录](docs/MODEL_REPLACEMENT.md)。

发布材料仍如实披露三项依赖原始声明缺口及完整重建限制。请阅读 [冻结与交付记录](docs/RELEASE_FREEZE.md)、[构建验收](docs/BUILD_ACCEPTANCE.md) 和 [第三方发行材料](docs/THIRD_PARTY_RELEASE_MATERIALS.md)，不要把本地候选视为已全面放行的正式发行。

## 从源码开始

两条路线目前都依赖 Lineage/Soong 构建环境，**没有独立 Gradle 构建**。先阅读 [构建说明](docs/BUILDING.md)，再检查依赖：

```sh
# Python 3.11+；在仓库根目录执行
python3 tools/distribution/dependencies.py check --profile apk
python3 tools/distribution/dependencies.py prepare --profile apk --cache /path/to/dependency-cache
```

ROM 使用 `--profile rom`。缓存按 SHA-256 命名；`--download` 仅下载清单已有的固定地址。部分依赖尚未公开托管，缺项会明确报告，因此不能保证全新环境一条命令即可构建。模型、APK 和预编译负载不进入 Git 源码。恢复方式见 [依赖与发行](docs/DISTRIBUTION.md)。

## 仓库导航

| 路径 | 内容 |
|---|---|
| `android/` | 公共业务模块、APK/ROM 宿主、语音与界面 |
| `integration/`、`patches/` | Lineage 集成与上游适配 |
| `tools/` | 构建、依赖恢复、源码导出与发行工具 |
| `tests/` | 验证代码及测试样本 |
| `docs/` | 产品、架构、构建、使用和验收说明 |
| `licenses/`、`third_party/` | 第三方许可、声明及相关源码 |
| `.github/` | 问题反馈和 Pull Request 模板 |

[GitHub 目录与首发安排](docs/GITHUB_PUBLISHING.md) · [验证范围](docs/VALIDATION.md) · [上游来源](docs/UPSTREAM_SOURCES.md)

## 贡献与许可

自有代码采用 **GPL-3.0-only**；第三方代码和模型保留各自许可，不因仓库许可而改变。详见 [LICENSE](LICENSE)、[NOTICE](NOTICE)、[许可范围](docs/LICENSING.md) 与 [对应源码交付](docs/CORRESPONDING_SOURCE.md)。

提交改动前请阅读 [贡献约定](CONTRIBUTING.md)。反馈问题时注明 APK/ROM、版本、设备和复现步骤；公开内容请移除个人数据和凭据。安全问题参见 [SECURITY.md](SECURITY.md)，不要公开敏感复现材料。
