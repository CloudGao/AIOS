# Third-party inventory / 第三方声明

This is a source inventory, not a completed legal clearance. Original AIOS code is GPL-3.0-only. Existing notices stay with their files; models and bundled applications are not automatically covered by the AIOS license.

| Component | Local source / notice | Review status |
|---|---|---|
| Android / LineageOS modifications | integration/ and patches/, upstream headers | Retain upstream notices; review per-file provenance |
| Codex runtime | tools/codex-runtime/patches/, licenses/CODEX_LICENSE.txt | Pinned upstream preparation script retained; binary reconstruction needs final review |
| llama.cpp | third_party/llama/LICENSE and vendored source | Vendored tree retained to avoid losing exact source |
| Node.js and npm runtime | android/codex-runtime/native/NOTICE and assets/cli-runtime/NOTICE | Native build script and patch retained; dependency notices need distribution review |
| LFM model | android/text-models/LICENSE and provenance.json | LFM Open License v1.0, separate from source license |
| MobileCLIP2 / search models | android/core-services/third_party/models/ | Separate model terms; external converted assets need provenance review |
| LiteRT | android/core-services/third_party/litert/ | Upstream LICENSE and THIRD_PARTY_NOTICE retained |
| SQLite / sqlite-vec | android/core-services/third_party/sqlite*/ | Local notices retained |
| PDFBox / BouncyCastle | android/core-services/third_party/documents/ | License texts and source metadata retained |
| commonmark | android/core-runtime/third_party/commonmark/ | License and provenance retained |
| WebKit support | android/miniapp/third_party/ | Local license retained |
| libadb / spake2 / BoringSSL | android/host-apk/third_party/ | Per-file SPDX includes alternative licenses; directory names alone do not determine licensing |
| agent-browser | tools/browser/upstream.json and patches/agent-browser/ | Fixed upstream commit and adaptation patch retained |
| Speech / TTS / bundled APKs | dependencies.lock.json and integration/lineage-24.0/assets.json | Binary origin, complete notices and redistribution review pending |

Machine-readable notice inventory: [third-party-files.json](../licenses/third-party-files.json).
A file's presence in that inventory does not mean every embedded dependency has been audited. Preserve the complete relevant notices in binary distributions, not only this summary.

## 2026-11-07 review

See [REDISTRIBUTION_REVIEW.md](REDISTRIBUTION_REVIEW.md) for concrete findings and unresolved release conditions. The 48 locked inputs are covered by licenses/redistribution-review.json; Codex candidate dependencies are recorded in licenses/codex-dependency-review.json. Reviewed supplementary APK notices are in licenses/apk-notices with a hash manifest. MobileCLIP2 is research-restricted, SPAKE2 remains LGPL, and model licenses are separate from inference code. No blanket redistribution clearance is claimed.

## Supplemental material

[Codex notice index](../licenses/codex-notice-index.json), [exact-revision upstream text index](../licenses/codex-upstream-notices.json) and [MPL candidate source attachment manifest](../licenses/mpl-corresponding-source.json) record further evidence. The supplemental source attachment is a separate local deliverable; these indexes do not claim every candidate is linked or every redistribution condition is closed.
