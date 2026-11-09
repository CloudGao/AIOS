# AIOS 发布与安装

当前目标仅为 Pixel 10 `frankel`。DSU 与正式安装使用同一次完整构建、同一份签名 target-files；DSU 包含 system、system_ext、product，继续使用宿主的内核和 vendor。正式安装包还包含设备启动与 vendor 分区，因此两个 ZIP 不会字节相同。相同系统内容不代表任意宿主固件都兼容。

构建类型固定为 `userdebug`，保留开发者选项中的 Rooted debugging，由安装者自行开启。ADB 保持电脑逐台授权，不预装开发者电脑公钥。发行签名不使用公开测试密钥。

## 当前状态

当前模型替换版本的完整 userdebug 构建已通过；正式签名、封装和离线校验以本次交付目录的报告为准，正式镜像真机验收尚未完成。`release.json` 中 `distributable=false` 或任何必要验收为 `pending` 时，仅属于候选包，不应提供给普通用户安装。不得手改状态绕过 GPU、DSU、实机启动、首次使用及保留数据升级验收。

## 构建者

在 WSL 权威源码树运行：

```sh
python3 tools/lineage/release.py prepare
python3 tools/lineage/release.py build
python3 tools/lineage/release.py package --output ./release-output
python3 tools/lineage/verify-release-artifacts.py ./release-output
python3 tools/lineage/finalize-release-candidate.py ./release-output
```

`prepare` 校验明确列出的公开 APK 与模型文件，保留第三方 APK 原签名，暂存最新源码与 Framework 补丁，复用已有私有发行密钥（首次才生成），通过统一集成入口设置平台与 Launcher 最终证书信任。密钥保存在构建账号的 `~/.local/share/aios-release/keys`，不进入源码或公开包。必须另外加密备份密钥；丢失密钥会失去正常升级签名能力。不要用开发平台证书覆盖发行证书。

`build` 使用固定 LineageOS 树与固定 out，执行 `--full --variant userdebug`。`package` 只接受此次成功构建记录中的 target-files，拒绝过期/被替换的输入与已有输出目录。签名后生成 `ota.zip`、`images.zip`、`dsu.zip` 及分区哈希清单。签名和打包不重新构建另一套系统 APK。

离线验证检查 ZIP 完整性、正式证书信任资源、ADB 授权策略、AVB/OTA 签名及三个包的对应分区内容一致性。通过后 finalize 输出公开证书、签名清单和 SHA256SUMS；仍保留候选状态，不会把离线成功转为真机验收成功。

## 安装者：先验证

使用 Python 3.11 或更新版本，在解压后的发布目录运行：

```sh
python install-release.py --release .
python install-release.py --release . --serial YOUR_ADB_SERIAL
```

第一条只检查本地文件。第二条通过已有 ADB 授权读取所选设备身份，不启动 root、不重启、不刷写。应从独立可信发布渠道核对 `release.json` 自身的哈希；同目录哈希清单只能发现损坏，不能单独证明发布者身份。

## DSU

必须先有该宿主 vendor 指纹的兼容性验收，并确保没有任何现存 DSU 槽位。安装工具默认拒绝未验收候选包、已安装/运行中的 DSU、锁定设备或无法确认全部槽位的状态。确认满足条件后：

```sh
python install-release.py --release . --serial YOUR_ADB_SERIAL --install-dsu --userdata-gib 32
```

工具上传并校验包后打开 Android 安装界面，由使用者完成界面操作；不自动重启。32 GiB 是新 DSU 用户数据空间大小，不是清理现有数据的授权。退出 DSU 应使用 Android 提供的返回宿主功能；删除 DSU 会删除其中用户数据，必须独立备份并确认。

## 正式安装与恢复

`ota.zip` 是签名的完整 OTA；`images.zip` 是同源设备镜像包。现阶段实机刷入、恢复界面兼容和跨签名迁移未验收，因此不提供自动 fastboot 刷写或解锁脚本。首次安装步骤须在设备树对应恢复环境实测后才能发布，不能把 `fastboot update` 或 `adb sideload` 命令拼接出来就视为已验证安装方案。

解锁/重新锁定引导加载程序、恢复出厂设置、刷写 userdata、某些恢复环境初始化或 DSU 替换均可能清空数据。执行前须完成受影响范围的备份，验证可读性及恢复条件，并取得针对本次清空的明确确认。开发签名到发行签名不能假定可以原位保留应用数据。不得为了通过启动检查重新锁定 bootloader。

当前开发手机的加密备份和恢复记录属于个人私有文件，不随发行包交付。离线 tar/数据库读回通过不等于硬件密钥、登录态及整机恢复已验证。安装失败时不要继续清空；保留日志、当前槽位和备份，按已验证的对应设备恢复流程处理。
