#!/usr/bin/env python3
"""Repack one explicit APK; never extract or recursively archive a working directory."""
import argparse
import copy
import hashlib
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tempfile
import zipfile

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lineage'))
from paths import LINEAGE
MAX_UNPACKED = 1024 ** 3


def unsigned_apk(source, output):
    source, output = Path(source).resolve(), Path(output).resolve()
    if source.suffix.lower() != '.apk' or not source.is_file():
        raise ValueError('Source must be an explicit existing APK file')
    if source == output:
        raise ValueError('Unsigned staging output must not overwrite its input')
    with zipfile.ZipFile(source) as src:
        entries = src.infolist()
        if sum(item.file_size for item in entries) > MAX_UNPACKED:
            raise ValueError('APK unpacked size exceeds the 1 GiB safety limit')
        seen = set()
        for item in entries:
            path = PurePosixPath(item.filename)
            if path.is_absolute() or '..' in path.parts or '\\' in item.filename or item.filename in seen:
                raise ValueError('Unsafe or duplicate APK entry: ' + item.filename)
            seen.add(item.filename)
        with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_STORED) as dst:
            for item in entries:
                name = item.filename.upper()
                if name.startswith('META-INF/') and (name == 'META-INF/MANIFEST.MF' or name.endswith(('.SF', '.RSA', '.DSA', '.EC'))):
                    continue
                output_item = copy.copy(item)
                output_item.compress_type = zipfile.ZIP_STORED
                with src.open(item) as inp, dst.open(output_item, 'w') as out:
                    shutil.copyfileobj(inp, out, length=1024*1024)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--key', type=Path, required=True)
    parser.add_argument('--cert', type=Path, required=True)
    args = parser.parse_args()
    source, output = args.source.resolve(), args.output.resolve()
    if output.suffix.lower() != '.apk':
        parser.error('Output must be an APK file')
    for path in (source, args.key, args.cert):
        if not path.is_file():
            parser.error('Missing input: ' + str(path))
    output.parent.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(output.parent).free < 4 * MAX_UNPACKED:
        parser.error('Need at least 4 GiB free for APK staging')
    env = os.environ.copy()
    env['JAVA_HOME'] = str(LINEAGE / 'prebuilts/jdk/jdk21/linux-x86')
    env['PATH'] = env['JAVA_HOME'] + '/bin:' + env.get('PATH', '')
    host = LINEAGE / 'out/host/linux-x86/bin'
    with tempfile.TemporaryDirectory(prefix='aios-apk-', dir=output.parent) as folder:
        stage = Path(folder)
        unsigned_apk(source, stage/'unsigned.apk')
        subprocess.run([str(host/'zipalign'), '-f', '-p', '4', str(stage/'unsigned.apk'), str(stage/'aligned.apk')], check=True, env=env)
        subprocess.run([str(host/'apksigner'), 'sign', '--key', str(args.key.resolve()), '--cert', str(args.cert.resolve()),
                        '--out', str(stage/'signed.apk'), str(stage/'aligned.apk')], check=True, env=env)
        subprocess.run([str(host/'apksigner'), 'verify', str(stage/'signed.apk')], check=True, env=env)
        os.replace(stage/'signed.apk', output)
    with output.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    print(str(output), output.stat().st_size, digest)


if __name__ == '__main__':
    main()
