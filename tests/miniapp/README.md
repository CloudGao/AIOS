# MiniApp device acceptance

Run on a device only with explicit authorization for ADB deployment/testing. These tests do not use Android MCP or synchronize desktop conversations.

- `tools/lineage/build-miniapp-launcher.py` stages and builds AIOSMiniApps and Launcher3QuickStep in the fixed Lineage frankel out, under the shared build lock.
- `tools/lineage/build-miniapp-device-tests.py` builds platform/fixture instrumentation against the built host.
- `tools/lineage/build-miniapp-ui-probe.py [output.apk]` builds an independent UI probe so reading the active window does not kill the MiniApp process. Its manifest deliberately does not request the MiniApp read permission. The `permissions` instrumentation argument checks directory and Activity rejection plus Launcher/shell grant state.
- `MiniAppLauncherFixture` only creates, updates and deletes `launcher-test-a` and `launcher-test-b`. `identity` checks URI-only task restoration and contradictory identity rejection; `stale` checks generation validation.
- `test-launcher-device.py` reads `AIOS_ADB_SERIAL` and `AIOS_MINIAPP_VALIDATION_DIR`. Modes `first`, `host-update`, `update`, `manage`, `locale`, `real`, `cleanup`, `fallback`, `drag-final`, and `folder-verify` record assertions, screenshots and UI trees. Run the update/manage modes in that order. `host-update` requires `apps/AIOSMiniApps.apk`; `real` requires the recorded `before.json`. Registry assertions and drag screenshot capture require the authorized rooted debug ADB channel.
- UI selectors and `drag-final` spring-loaded target coordinates describe the verified Pixel 10, 1080×2424, Chinese system UI, five-column baseline. Do not blindly reuse drag coordinates on another grid. Inspect the actual UI and adapt first. `drag-final` verifies a two-item folder actually exists, not only that input was injected.
- Keep failed-round records before retesting. At completion remove fixtures and both test APKs, restore the application locale and screen settings, and verify existing apps and desktop layout remain intact.

Results and remaining limitations are recorded in `docs/changes/2026-10-23-miniapp-launcher.md`.
