# AIOS Launcher integration

The LineageOS Launcher3QuickStep has an AIOS side page with Workspace summaries and four fixed background slots. Viewing a slot does not move its app to the main display; native takeover and return use the same session. Current behavior and device verification are described in docs/DESIGN.md and docs/ARCHITECTURE.md.

The authoritative overlay is `src/com/android/launcher3/aios/AiosOverlay.java`; the read-only summary provider is `android/agent/WorkspaceHomeProvider.java`. The similarly named old file at this module root is historical and is not a build input. Platform integration lives in the LineageOS Launcher3 tree.

Build using `tools/lineage/build-experience.py` (includes Launcher3QuickStep) and verify with `tools/lineage/check-experience-provenance.py`. Old standalone AOSP build/integration scripts are historical and must not be used for the current product.


Mini apps are projected into the native app list by `src/com/android/launcher3/aios/MiniApps.java`, with individual search, workspace/folder entries, non-text badges and management shortcuts. Android Settings continues to show the single MiniApp host. `tools/lineage/stage-miniapp-launcher.py` applies the guarded integration patches; `tools/lineage/build-miniapp-launcher.py` builds only the host and Launcher under the fixed-out lock. See `docs/changes/2026-10-23-miniapp-launcher.md` and `tests/miniapp/README.md` for verification and reproduction.
