# Workspace

Workspace is the shared UI/client library for conversations, memory, tasks and skills. Core Services owns persistent business state; Core Runtime defines the shared IPC client. ROM and APK hosts reuse this library.

Build through the host entrypoints in [BUILDING](../../docs/BUILDING.md). Do not use retired standalone APK, desktop synchronization or emulator instructions. Current product behavior is in [DESIGN](../../docs/DESIGN.md); verified scope and limitations are in [VALIDATION](../../docs/VALIDATION.md).

Tests under tests/workspace use dedicated fixtures. Device execution requires explicit authorization and is not part of a source-only review.
