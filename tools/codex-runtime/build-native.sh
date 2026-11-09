#!/usr/bin/env bash
set -euo pipefail
# Source must be the pinned rust-v0.162.1 checkout, with patches/ applied.
SOURCE=${CODEX_SOURCE:-/tmp/aios-codex-port/source}
NDK=${ANDROID_NDK_HOME:-/tmp/aios-codex-toolchain/android-ndk-r29}
export RUSTUP_HOME=${RUSTUP_HOME:-/tmp/aios-codex-toolchain/rustup}
export CARGO_HOME=${CARGO_HOME:-/tmp/aios-codex-toolchain/cargo}
export PATH="$CARGO_HOME/bin:$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
TARGET=${1:-aarch64-linux-android}
case "$TARGET" in
  aarch64-linux-android) ;;
  *) echo 'Only the AIOS ARM64 Android target is supported' >&2; exit 2 ;;
esac
KEY=${TARGET//-/_}
export "CC_${KEY}=${TARGET}28-clang"
export "CXX_${KEY}=${TARGET}28-clang++"
export "AR_${KEY}=llvm-ar"
export "CARGO_TARGET_${KEY^^}_LINKER=${TARGET}28-clang"
export CARGO_PROFILE_RELEASE_LTO=false
export CARGO_PROFILE_RELEASE_DEBUG=0
export CARGO_PROFILE_RELEASE_STRIP=symbols
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
export CARGO_BUILD_JOBS=${CARGO_BUILD_JOBS:-4}
cd "$SOURCE/codex-rs"
cargo +1.95.0 build --locked --release --target "$TARGET" -p codex-app-server --bin codex-app-server
