# agent-browser Android local CDP adaptation

Pinned upstream and license: tools/browser/upstream.json. Source checkout is separate from the LineageOS source tree. Android release binaries from third-party repositories are not used.

Current slice adds filesystem Unix and Linux/Android abstract socket transport to upstream CDP. It reuses the existing command/event engine, snapshots, references and actions. It does not supply elevated privileges, an AIOS runtime tool, a Chrome launcher, or production-ready authorization. Never ship a tool that bypasses the pending host authorization/session integration.

Source restoration: tools/browser/prepare.py only clones the pinned tag if absent; otherwise requires an exact clean upstream checkout. It never resets existing work. Apply patches/agent-browser/local-cdp.patch to the pinned source. Inspect before applying.

Prepared regression coverage: endpoint parsing, HTTP rejection and length checks, fragmented discovery that cannot redirect onto TCP, Unix WebSocket command round-trip and canonical CLI/MCP parsing. Tests have not been run in the code-modification phase. Upstream documentation changes also require its docs test/type-check/build/routes suite during unified validation.

Known restrictions: local discovery requires HTTP/1.x with Content-Length or connection-close framing, no compression or chunked encoding. Discovery cap 64 KiB; handshake/discovery deadlines 5 seconds; local WebSocket messages 32 MiB. Capability mismatch and permission errors are explicit; no network/browser fallback.

Remaining before unified build: ROM connection authorization and SELinux, bounded command adapter, Core resource and thread binding, foreground/virtual-screen takeover integration, Android lifecycle/viewport cases, product staging. No binary is registered in the product yet.