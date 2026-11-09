# Validation scope

The release snapshot is assessed separately from historical development builds. Current machine-readable evidence and final artifact hashes are delivered alongside the source in VALIDATION.json and SHA256SUMS.txt; see RELEASE_FREEZE.md for the final status.

## Unified host batch

77 Python checks cover optional payload selection, product selection/integration, locale resources, release audits, dependency/source export safety and real APK assembly. 34 Java behavior assertions cover bounded/hash-verified atomic model installation and speech-service selection, including invalid or removed providers. These assertions do not simulate a full Android device.

The actual Lineage/Soong APK build, APK signing/alignment/ZIP checks, per-notice content hashes and affected ROM module builds are performed on the fixed ARM64 out. Existing native input hashes are verified; this is not a clean-environment rebuild of every transitive native library or a new complete ROM image.

## Device batch

Pixel 10 / Android 17 (API 37), existing ADB connection only. A development-signed candidate matching the installed application's certificate is installed with `adb install -r`; no uninstall, data wipe, bootloader unlock/reset, Android MCP or phone conversation sync. The pre-test APK is retained privately as an application binary, not described as a full user-data backup.

Scope: launch, voice settings entry, explicit service-change disclosure, selected provider persistence and settings summary, English/Simplified/Traditional Chinese text, Arabic English fallback and RTL back-button placement. An earlier candidate also exercised the optional-app directory and system links; that directory is ROM-only in the final route design, and those earlier APK results do not validate a ROM device. Temporary app-locale override and voice selection are restored.

The first device batch identified a shared hardcoded Chinese back-button accessibility label. The repair uses the common localized resource; a targeted incremental build and English/Arabic-fallback device retest passed. The subsequent route correction removes both APK optional-app entry points and the Activity implementation/registration. The final APK upgrade, entry removal and retained speech choices passed targeted device checks; see BUILD_ACCEPTANCE.md.

## Not covered by this release batch

- Broad speech accuracy, microphone routing through third-party providers, or long meeting transcription; the replacement recognition service has passed the bounded Chinese/English fixture tests described in MODEL_REPLACEMENT.md.
- Full-ROM model download/import and end-to-end migration acceptance; the shared device GPU backend and 128-image retrieval comparison are tested separately in MODEL_REPLACEMENT.md.
- Communication-language changes in an existing assistant conversation and full-system language switching; app-locales UI tests do not establish these.
- Long background scheduling, OEM compatibility, every permission flow, or an entire product/device regression suite.
- Full ROM images, Pixel proprietary-component redistribution, or fresh native builds for all binary inputs.

OpenCLIP and Whisper replacements are integrated and tested. See MODEL_REPLACEMENT.md for measurements and limits, and BUILD_ACCEPTANCE.md for the final 62-check batch and successful APK/five-module builds. Runtime success and redistribution permission are separate gates.
