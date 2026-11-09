# GitHub 目录与首发安排

## 首页与目录

GitHub 仓库首页直接展示根目录 `README.md`，不需要 `index.html`。英文首页链接到 `README.zh-CN.md`，中文首页链接回英文。现有功能和限制由 DESIGN、ARCHITECTURE 维护，首页负责介绍和导航。

```text
AIOS/
├── README.md                  # 英文首页
├── README.zh-CN.md             # 中文首页
├── LICENSE / NOTICE           # GPL-3.0-only 与第三方声明
├── CONTRIBUTING.md / SECURITY.md
├── .github/
│   ├── ISSUE_TEMPLATE/        # 缺陷、功能建议
│   └── pull_request_template.md
├── android/                   # 公共代码与 APK、ROM 宿主
├── integration/ / patches/    # Lineage 适配
├── tools/ / tests/            # 构建发行工具与验证
├── docs/                      # 使用、构建、架构、验收
├── licenses/ / third_party/   # 第三方材料与源码
├── examples/ / assets/        # 示例和源码资源
└── dependencies.lock.json     # 固定来源、版本、大小和哈希
```

## Git 源码与下载附件分开

| 位置 | 内容 |
|---|---|
| Git 仓库 | 白名单导出的源码、文本资源、文档、构建工具、许可与依赖锁定清单 |
| 适用版本的下载附件 | APK、对应源码附件、校验清单、安装说明和验收记录 |
| ROM 下载附件（放行后） | OTA、镜像、DSU、公开验签材料、对应源码和安装说明；按所选下载平台实际限制安排存储 |
| 不公开 | 签名私钥、凭据、手机数据、内部 Git 历史、构建缓存和中间产物 |

权重与预编译依赖按锁定清单恢复，不塞入源码目录。不得只上传二进制而漏掉其对应源码和声明。完整 ROM 当前仍有实机验收及 Pixel 专有组件授权缺口，不能列为正式发行下载。

## 建立仓库时的操作顺序

1. 从整理工作区运行 `python3 tools/distribution/export.py /path/to/new-aios-source`，目标必须是不存在的新目录；再运行 `python3 tools/distribution/audit.py --root /path/to/new-aios-source` 并审阅导出内容。
2. 只在该干净导出目录建立新的 Git 历史。不要直接推送内部开发仓库或整理分支的旧历史。
3. 创建空的 GitHub 仓库，再将干净目录作为首次提交推送。推荐仓库描述：`AI assistant, Workspace and automation for Android — APK and LineageOS ROM hosts.`
4. 配置安全问题的私下报告渠道，并更新 SECURITY.md。首页保留当前真实状态；拿到实际下载地址后再补链接，不放占位下载按钮。
5. 对已放行的交付物建立 Release，说明 APK/ROM 路线、设备范围、版本、已测与未测项，并附对应源码、许可、安装说明和哈希。仓库建好不等于 ROM 自动放行。

此次只整理 GitHub 展示文件，不重新构建 APK/ROM，不修改已有二进制冻结记录。首页改动后的源码版本应单独记录；已有二进制仍对应其 FREEZE.json 中的提交，不能将其标成由新提交重新构建。

## Release 说明建议字段

- 类型：源码 / APK / ROM 候选。
- 版本与对应源码提交。
- 支持的系统、架构和设备范围。
- 本次变化及已执行的验证。
- 已知限制与尚未确认的发行材料。
- 附件名称、对应源码、安装说明、SHA-256 校验入口。

远程源码仓库为 [CloudGao/AIOS](https://github.com/CloudGao/AIOS)。二进制下载尚未发布；上述流程不代表 APK 或 ROM 已上传。
