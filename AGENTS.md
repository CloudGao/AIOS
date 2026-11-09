# AIOS contributor working rules

Read docs/DESIGN.md and docs/ARCHITECTURE.md before non-trivial changes.
Record medium/large changes in docs/changes before implementation. Keep product
and architecture documentation aligned with verified behavior; do not promote
planned or untested capabilities to completed ones.

Shared business logic belongs in shared modules. Host-specific behavior belongs
in ROM/APK adapters. Do not bypass Android permission or UID boundaries.
Maintain English, Simplified Chinese and Traditional Chinese UI resources together;
use localized strings, consistent placeholders, locale-aware formatting and
start/end layout directions. Validate fallback, language changes and RTL as relevant.

Finish the planned code batch before unified build/test acceptance. Record all
acceptance failures before a repair batch; rerun only affected checks afterward.

Host build paths are configured as described in docs/BUILDING.md. Keep one fixed
out per target, preserve existing artifacts, and do not reuse a graph for another
architecture. No device access, installation, wipe or desktop conversation sync is
implied by a source task. Device work requires explicit task authorization.
Any operation that could erase a device requires a complete, recoverable backup
verified first, followed by explicit confirmation for that exact erase.

Keep credentials, signing keys, device exports, conversations and local build state
out of source exports. Preserve upstream copyright and license files.
