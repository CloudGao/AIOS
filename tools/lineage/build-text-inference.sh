#!/bin/bash
set -euo pipefail
source_root="$(cd -- "$(dirname -- "$0")/../.." && pwd)"
lineage_root="${AIOS_LINEAGE_ROOT:-$(dirname "$source_root")/lineage}"
build_root="$lineage_root/out/aios-text-inference"
ndk_root="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK r29}"
"$lineage_root/prebuilts/cmake/linux-x86/bin/cmake" -S "$source_root/third_party/llama" -B "$build_root" \
  -DCMAKE_TOOLCHAIN_FILE="$ndk_root/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-35 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DLLAMA_CURL=OFF \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_APP=OFF \
  -DLLAMA_BUILD_TOOLS=ON -DLLAMA_BUILD_SERVER=ON -DLLAMA_BUILD_UI=OFF \
  -DLLAMA_BUILD_HTML=OFF -DLLAMA_OPENSSL=OFF
"$lineage_root/prebuilts/cmake/linux-x86/bin/cmake" --build "$build_root" --target llama-server -j4
install -D "$build_root/bin/llama-server" "$source_root/android/core-services/third_party/text-inference/arm64-v8a/libaios_llama_server.so"
