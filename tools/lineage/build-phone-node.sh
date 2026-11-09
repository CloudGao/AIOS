#!/bin/bash
set -euo pipefail
source_root="$(cd -- "$(dirname -- "$0")/../.." && pwd)"
node_root="${AIOS_NODE_SOURCE:?Set AIOS_NODE_SOURCE to extracted Node 22.23.4}"
ndk_root="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK r29}"
cd "$node_root"
if ! grep -q 'Android cross-build still runs' tools/v8_gypfiles/v8.gyp; then
  patch -p1 < "$source_root/tools/lineage/node-22-android-host.patch"
fi
export CC_host=gcc CXX_host=g++
mkdir -p out
"$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang" -O2 -fPIC -c "$ndk_root/sources/android/cpufeatures/cpu-features.c" -o out/aios-cpufeatures.o
export LDFLAGS="-static-libstdc++ -Wl,-z,max-page-size=16384 $node_root/out/aios-cpufeatures.o"
./android-configure "$ndk_root" 28 arm64
make -j4
mkdir -p "$source_root/android/codex-runtime/native"
"$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" -o "$source_root/android/codex-runtime/native/libaios_node.so" out/Release/node
"$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang" -O2 -fPIE -pie -Wl,-z,max-page-size=16384 "$source_root/android/codex-runtime/cli-launcher.c" -o "$source_root/android/codex-runtime/native/libaios_cli_launcher.so"
"$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -h -d "$source_root/android/codex-runtime/native/libaios_node.so"
