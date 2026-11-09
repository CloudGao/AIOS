#!/usr/bin/env python3
"""Build the vendored Whisper decoder, never downloads models or operates a device."""
from pathlib import Path
import argparse,subprocess,shutil,hashlib,json
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--ndk',type=Path,required=True);p.add_argument('--host',action='store_true');a=p.parse_args()
build=ROOT/'.local/whisper-build'/('host' if a.host else 'arm64');build.mkdir(parents=True,exist_ok=True)
args=['cmake','-S',str(ROOT/'android/speech/jni'),'-B',str(build),'-DCMAKE_BUILD_TYPE=Release']
if not a.host:args+=['-DCMAKE_TOOLCHAIN_FILE='+str(a.ndk/'build/cmake/android.toolchain.cmake'),'-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-35','-DANDROID_STL=c++_static']
subprocess.run(args,check=True);subprocess.run(['cmake','--build',str(build),'-j4'],check=True)
if not a.host:
 output=ROOT/'android/speech/lib/libaios_whisper.so';output.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(build/'libaios_whisper.so',output)
 subprocess.run([str(a.ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip'),'--strip-unneeded',str(output)],check=True)
 print(json.dumps({'file':str(output.relative_to(ROOT)),'bytes':output.stat().st_size,'sha256':hashlib.sha256(output.read_bytes()).hexdigest()}))
