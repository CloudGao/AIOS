---
name: aios-browser
description: Read and operate web pages in on-device Chrome through structured CDP elements. Use for browsing, web search, forms, links, tabs, and page verification; prefer this over Android UI control for page content.
---

# AIOS 浏览器操作

网页内容优先使用 `aios_browser_action`。它通过手机本地 CDP 读取语义结构和操作元素，不依赖截图、坐标、电脑、ADB 或 MCP。浏览器地址栏、权限弹窗、文件选择器、系统分享页等 Chrome 外壳不属于网页 DOM，确有需要时才读取 [AIOS 系统 UI 参考](../aios-ui/SKILL.md)。

## 标准循环

1. 用 `status` 确认桥接和 Chrome 状态；需要打开网址时调用 `open`。
2. 调用 `observe`，默认 `compact=true`，取得当前页可交互元素和 `@e1` 一类引用。
3. 用引用执行 `click`、`fill`、`select`、`check` 等语义动作。不要把引用改成坐标，也不要先截图寻找同一元素。
4. 页面动作后重新 `observe`，或用 `wait` 等待明确文字/URL，再核验用户目标是否完成。
5. 完成后通常保留浏览器和会话；只有用户要求关闭或任务需要释放浏览器资源时调用 `release`。

引用只属于最近一次有效观察。页面导航、刷新、标签切换、用户触摸或按键后重新观察。返回 `observation_required` 表示动作没有执行，应重新观察并重新决定；返回 `outcome_unknown` 时先读取页面状态，不能直接重放可能产生副作用的动作。

优先用紧凑观察；只有紧凑结果缺失目标时再使用 `compact=false`。用 `wait` 代替频繁 observe 轮询。网页任务完成依据页面读回结果，而不是工具调用成功。

完整 action、参数和用途见 [接口参考](references/actions.md)。调用不兼容或接口版本变化时以运行时工具 schema 为准。

## 与 UI 操作的边界

以下内容继续使用 CDP：网页正文、链接、按钮、输入框、下拉框、复选框、页面滚动、历史导航和网页标签。

以下内容才使用 UI：地址栏原生建议、Chrome 菜单、浏览器权限气泡、系统文件选择器、验证码的系统级交互、下载确认和其他不在 `observe` 结果中的浏览器外壳。切到 UI 前先确认目标确实不在完整 CDP 观察中；UI 操作完成并回到网页后，立即重新 `observe`，不复用旧引用。
