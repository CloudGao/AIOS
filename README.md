# AIOS

An AI assistant, Workspace and automation for Android. Available as APK and ROM hosts sharing the same core business logic.

[简体中文](README.zh-CN.md) · [Usage](docs/USAGE.md) · [Build guide](docs/BUILDING.md) · [Architecture](docs/ARCHITECTURE.md)

## What it does

- **Assistant and conversations**: an on-device Codex harness, tool calls and skills.
- **Workspace**: memories, tasks and their conversation associations.
- **Automation and MiniApps**: scheduled tasks and small applications.
- **ROM integration**: notification governance, recording and meeting notes, and image/text search.

The UI includes English, Simplified Chinese and Traditional Chinese; other languages fall back to English. Data is managed on the phone; relevant inputs are sent to configured cloud inference services when those services are used. See [product design](docs/DESIGN.md) for capabilities and limitations.

## APK or ROM?

| | APK | ROM |
|---|---|---|
| Target | Android 15+, ARM64 | Pixel 10 frankel, ARM64, LineageOS 24.0 |
| Installation | One APK | System images |
| System access | Android permissions and local wireless ADB pairing | Platform services and Lineage integration |
| Shared features | Assistant, conversations, memories, tasks, skills, scheduling, MiniApps | Same shared business layer |
| Differences | Subject to OEM, shell permission and background restrictions | Adds notification governance, recording/notes and image/text search |
| Optional dependencies | Only models and options used by the APK | ROM models and third-party app options; five optional apps are excluded by default |

The APK does not offer ROM app choices such as Chrome or email clients, or bundle ROM image/text search models. See [optional dependencies](docs/OPTIONAL_DEPENDENCIES.md).

## Status and downloads

**This repository contains the source code. Binary downloads are not published yet.** APK 0.1.11 and a complete ROM candidate have been prepared locally.

- **APK**: signature, alignment, same-signature upgrade and locked-screen Chinese/English speech samples passed. This is not full feature or universal device acceptance.
- **ROM**: the full product was built and passed eight offline checks. Hardware installation, boot and data-preserving upgrade remain untested; redistribution authorization for Pixel proprietary components is unconfirmed. It remains a candidate.
- **Models**: MobileCLIP2 has been replaced with MIT-licensed OpenCLIP, and SenseVoice with MIT-licensed Whisper. See [model replacement results](docs/MODEL_REPLACEMENT.md) for the comparison and test scope.

Three dependency original-notice gaps and clean reconstruction limitations remain disclosed. Read the [freeze and delivery record](docs/RELEASE_FREEZE.md), [build acceptance](docs/BUILD_ACCEPTANCE.md) and [third-party release materials](docs/THIRD_PARTY_RELEASE_MATERIALS.md). Local candidates do not imply full public binary release clearance.

## Build from source

Both hosts currently require the Lineage/Soong environment; **there is no standalone Gradle build**. Start with the [build guide](docs/BUILDING.md), then inspect dependencies:

```sh
# Python 3.11+; run from the repository root
python3 tools/distribution/dependencies.py check --profile apk
python3 tools/distribution/dependencies.py prepare --profile apk --cache /path/to/dependency-cache
```

Use `--profile rom` for ROM inputs. Cache files are named by SHA-256. `--download` only fetches existing pinned URLs; some dependencies are not publicly hosted and are reported as missing. A one-command build from a fresh environment is not guaranteed. Models, APKs and precompiled payloads are excluded from Git source. See [dependency and distribution instructions](docs/DISTRIBUTION.md).

## Repository map

| Path | Contents |
|---|---|
| `android/` | Shared modules, APK/ROM hosts, speech and UI |
| `integration/`, `patches/` | Lineage integration and upstream adaptations |
| `tools/` | Build, dependency restoration, source export and release tools |
| `tests/` | Verification code and fixtures |
| `docs/` | Product, architecture, build, usage and acceptance documentation |
| `licenses/`, `third_party/` | Third-party licenses, notices and related source |
| `.github/` | Issue and pull request templates |

[GitHub layout and first publication](docs/GITHUB_PUBLISHING.md) · [Validation scope](docs/VALIDATION.md) · [Upstream sources](docs/UPSTREAM_SOURCES.md)

## Contributing and licensing

Original AIOS code is **GPL-3.0-only**. Third-party code and models retain their own licenses. See [LICENSE](LICENSE), [NOTICE](NOTICE), [licensing scope](docs/LICENSING.md) and [corresponding source](docs/CORRESPONDING_SOURCE.md).

Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes. Report the host, version, device and reproduction steps, with personal data and credentials removed. See [SECURITY.md](SECURITY.md) for security reports; do not post sensitive reproduction material publicly.
