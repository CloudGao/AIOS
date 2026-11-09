# Contributing

Original AIOS contributions are licensed under GPL-3.0-only. Read LICENSE, NOTICE and docs/LICENSING.md before contributing; preserve third-party licenses.

Read docs/DESIGN.md and docs/ARCHITECTURE.md first. Keep shared business logic in common modules and host behavior in ROM/APK adapters. Describe the user-visible problem, scope and verification in each change. Do not treat one device's tests as universal support.

Maintain English, Simplified Chinese and Traditional Chinese resources together, with consistent placeholders and plurals. Test relevant language changes, fallback and RTL. Do not hardcode display strings or translate protocol identifiers.

Use the supported build configuration in docs/BUILDING.md. Finish the code batch, run the applicable unified acceptance checks, then fix the collected failures and rerun affected checks. Never include personal data, signing keys or credentials in tests.

Small fixes need focused validation; larger changes need a proposal under docs/changes. Update current-state docs when behavior or architecture changes. Preserve upstream copyright and license notices.
