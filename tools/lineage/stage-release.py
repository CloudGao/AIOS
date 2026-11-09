#!/usr/bin/env python3
"""Stage only enumerated, hash-pinned public release inputs; never device data."""
from paths import SOURCE as SOURCE_ROOT, LINEAGE as LINEAGE_ROOT, STATE as BUILD_STATE, INPUTS as INPUT_ROOT
from pathlib import Path
from target_config import require_legacy_frankel
require_legacy_frankel()
import hashlib
import json
import shutil
import subprocess
import tarfile
import zipfile

SOURCE = SOURCE_ROOT
TREE = LINEAGE_ROOT
INPUT = INPUT_ROOT
APPS = [
    ('AIOSChromium', 'org.chromium.chrome', 'release-inputs/chromium.apk', '1f11bc1870586e31df6b6b99939164452f29e47a86e41181f473c6b8a64309b1'),
    ('AIOSCollabora', 'com.collabora.libreoffice', 'validation/document-capabilities/apps/collabora.apk', '79e72f810d711706d205a9ff457b4bb830c607a2b988cf44246829ec4593c393'),
    ('AIOSPdfReader', 'com.gitlab.mudlej.MjPdfReader', 'validation/document-capabilities/apps/mj-pdf.apk', 'c095c1d339aa58eb9dddca701054419c373957941bd518df50eacca33737a5d2'),
    ('AIOSTextEditor', 'org.billthefarmer.editor', 'validation/document-capabilities/apps/editor.apk', '343d17e11d0914e1a7708df2781580a0413e52501c0a41d91f7ef725d9615fe9'),
]
MODEL_HASH = '86cceffbccd96ac4f4422af61ae594d5daeed81111a1dc005d7ef0ee61a2cd42'

def digest(path):
    with open(path, 'rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def copy_verified(src, dst, expected):
    if digest(src) != expected:
        raise ValueError('Input digest mismatch: ' + str(src))
    dst.parent.mkdir(parents=True, exist_ok=True)
    if not dst.exists() or digest(dst) != expected:
        shutil.copyfile(src, dst)
    if digest(dst) != expected:
        raise ValueError('Staged digest mismatch: ' + str(dst))

def main():
    import sys
    subprocess.run([sys.executable,str(SOURCE/'tools/lineage/integrate.py'),'--target','frankel','--apply'],check=True)

if __name__ == '__main__':
    main()
