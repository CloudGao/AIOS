# Dependency and source distribution

## Preparation, not publication

Original AIOS code is GPL-3.0-only; see LICENSING.md for scope. Prefer upstream downloads; custom build outputs still require reproducible build instructions or reviewed distribution. Do not label this snapshot as an approved open-source release or upload internal Git history as part of this workflow.

## Dependency contract

`dependencies.lock.json` records scope (repository source or external inputs), normalized path, profiles, bytes, SHA-256, mode and available URL. Existing correct files are reused. Missing files can be restored from an explicit original checkout, external input directory, content-addressed cache, or an approved HTTPS URL.

```sh
python3 tools/distribution/dependencies.py check --profile all
python3 tools/distribution/dependencies.py prepare --profile all \
  --from-source /path/to/original-source --from-inputs /path/to/original-inputs
python3 tools/distribution/dependencies.py prepare --profile rom --download
```

The final command may report missing unpublished artifacts. No mutable branch URL is treated as an immutable binary source. Hash mismatches are errors. A failed download never becomes the destination. Conflicting existing files and symlinks are rejected.

The original development checkout keeps its files. Adding ignore rules does not remove old Git blobs; this worktree remains an internal review branch. Only the source export is suitable as the starting point for a new public history.

## Reviewable export

```sh
python3 tools/distribution/export.py --list
python3 tools/distribution/export.py /path/to/new-aios-source
python3 tools/distribution/audit.py --root /path/to/new-aios-source
```

Export only accepts a nonexistent destination outside the source tree. Its allowlist is `tools/distribution/public-files.json`; it includes runtime source, necessary current build scripts, integration, tests, upstream licenses and curated documents. It excludes dependencies, .git, historical releases, internal change logs and local state. `SOURCE_MANIFEST.json` records each exported file's hash and byte size.

This export is a local draft. It does not create a remote repository, commit, push, publish an APK, or authorize redistribution. Review legal and security findings before creating the first public commit.

## Release gates

1. Original-code GPL-3.0-only scope is recorded in LICENSING.md; preserve all upstream notices.
2. Review third-party notices, model terms and any corresponding-source obligations; retain upstream provenance.
3. Complete remaining upstream artifact checks and custom source reconstruction; self-host only where necessary.
4. Review the captured resolved-manifest.xml together with the AIOS integration and device input provenance.
5. Complete clean-environment builds and the applicable unified acceptance batch.
6. Scan the exact export; manually review content and binary inputs. If retaining history instead, scan all reachable history separately.

The pattern scanner emits paths/line numbers, never matched secret values. Its absence of findings is not a privacy or security guarantee.

## Revert

Keep the original development checkout and its signing material. The preparation branch is independent; stop using it to return to the original workflow. Do not use a destructive reset/clean operation to recover ignored model files or keys.

A local offline cache can be prepared with `python3 tools/distribution/cache.py /path/to/new-cache --root /path/to/original-source --inputs /path/to/original-inputs --profile apk`. This verifies every input first and copies only locked files. The cache is not cleared for public redistribution.

The top-level legacy desktop skills and one-off device smoke wrappers are excluded; the current phone runtime skills remain under android/codex-runtime/workspace. Historical module instructions containing a specific developer device are also excluded or replaced with current build documentation.

## Upstream sources

See [UPSTREAM_SOURCES.md](UPSTREAM_SOURCES.md) for original URLs and verification limits. `prepare --download` also supports individually pinned ZIP/AAR members; both archive and extracted file must match their recorded hashes. It never extracts whole archives.

## Optional upstream test data

The source export omits llama.cpp vocabulary-only GGUF fixtures and two mtmd sample media files. These are optional upstream test/demo data, not AIOS runtime model weights; the AIOS llama-server build already disables upstream tests/examples. The vendored implementation and copyright/license files remain. Running upstream tokenizer/media tests requires independently restoring the matching upstream fixtures and reviewing their terms.
