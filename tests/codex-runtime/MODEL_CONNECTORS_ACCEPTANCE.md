# 模型连接器待统一验收

2026-10-29 仅准备代码；以下均未运行。不得把现有发布包当作包含本次改动。

统一构建时，使用固定 Lineage out 的 AIOSCodexRuntime.jar 和 Android SDK 编译 AccountContractTest.java、ProviderProtocolContractTest.java，以 model-connectors-manifest.xml 生成独立的平台同签名验收 APK。测试类不得纳入发布 Runtime。AccountContractTest 只清理 account_contract_test 隔离首选项，不操作生产账号。仍须用户另行授权才能通过 ADB 安装、运行。

## 隔离夹具

- AccountContractTest：新旧配置、套餐隔离、协议不可伪造、地址校验、留空保留密钥、修改地址重输 Key、状态脱敏。
- ProviderProtocolContractTest：请求转换、Unicode、图片、命名空间、流式分片、多轮 call_id、并行工具图片顺序、正常终态、截断拒绝、未知工具、SSE 帧及断流。

## 传输与真实服务

1. 未认证回环请求不得访问上游；错误原文和密钥不得进入日志/事件；重定向不得转发 Key。
2. 验证系统代理、VPN、TLS、401/403/429/5xx、空闲超时、取消请求及子进程崩溃清理。故障后不得重放已发布的工具调用。
3. 每个提供商分别检查文字、多轮、工具调用/结果恢复和并发。Claude/Kimi 另验图片；阿里入口只声明文字输入。
4. 使用真实套餐 Key 核实 GLM/Kimi 原生 Responses、阿里 Chat Completions、Claude Messages。保存配置不等于认证成功。
5. 英/简中/繁中、其他语言英文回退、RTL、运行中语言切换、配置草稿和旧账号/原线程保持。
6. 长上下文本地压缩、附件预算、未知内容失败；适配路径不支持扩展思考、内建 web search 或任意 Responses 私有项。

所有结果记录在 docs/changes/2026-10-29-model-connectors.md，保留失败与阻塞，不逐项立即构建部署修复。
