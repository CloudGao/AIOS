#!/usr/bin/env python3
"""Verify a release and inspect one explicitly selected ADB device.

No unlock, relock, flash, wipe, reboot, or existing DSU replacement operations.
"""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile

def digest(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def verify(root):
    manifest=json.loads((root/'release.json').read_text(encoding='utf-8'))
    if manifest.get('schema')!=1 or manifest.get('device')!='frankel':raise ValueError('Unsupported release manifest')
    for name,metadata in manifest['files'].items():
        if Path(name).name!=name or '/' in name or '\\' in name:raise ValueError('Unsafe manifest path')
        path=root/name
        if path.stat().st_size!=metadata['bytes'] or digest(path)!=metadata['sha256']:raise ValueError('Hash mismatch: '+name)
    with zipfile.ZipFile(root/'dsu.zip') as z:
        if set(z.namelist())!={'system.img','system_ext.img','product.img'}:raise ValueError('Unexpected DSU partitions')
        for part,metadata in manifest['partitions'].items():
            with z.open(part+'.img') as f:
                if hashlib.file_digest(f,'sha256').hexdigest()!=metadata['raw_sha256']:raise ValueError('DSU partition mismatch')
    return manifest

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--release',type=Path,default=Path(__file__).resolve().parent)
    p.add_argument('--adb',default='adb')
    p.add_argument('--serial',help='Required to inspect or install on a device')
    p.add_argument('--install-dsu',action='store_true',help='Open Android installer only on an empty DSU host')
    p.add_argument('--userdata-gib',type=int,default=32)
    a=p.parse_args();root=a.release.resolve();m=verify(root)
    result={'package_verified':True,'device':m['device'],'acceptance':m['acceptance'],'distributable':m['distributable']}
    if a.install_dsu and not a.serial:p.error('--install-dsu requires --serial')
    if a.serial:
        def adb(*args):return subprocess.check_output([a.adb,'-s',a.serial,*args],text=True,stderr=subprocess.STDOUT).strip()
        props={key:adb('shell','getprop',key) for key in ['ro.product.device','ro.boot.flash.locked','ro.gsid.image_running','ro.vendor.build.fingerprint','ro.build.version.sdk']}
        result['properties']=props
        if props['ro.product.device']!='frankel':raise RuntimeError('This package supports only frankel')
        if a.install_dsu:
            if not m['distributable']:raise RuntimeError('Candidate acceptance is incomplete; installation is gated')
            if props['ro.gsid.image_running'] in ('1','true'):raise RuntimeError('Current DSU must be preserved; replacement refused')
            if props['ro.boot.flash.locked']!='0':raise RuntimeError('Custom DSU trust has not been established on this locked host')
            # Root is not started here. It is required to enumerate every existing slot.
            if adb('shell','id','-u')!='0':raise RuntimeError('Cannot verify all DSU slots without an already authorized root ADB shell')
            if adb('shell','gsi_tool','status')!='normal':raise RuntimeError('An existing or unknown DSU state must be preserved')
            allowed=m.get('compatible_vendor_fingerprints',[])
            if props['ro.vendor.build.fingerprint'] not in allowed:raise RuntimeError('Vendor fingerprint has no recorded compatibility acceptance')
            if not 8<=a.userdata_gib<=128:p.error('--userdata-gib must be between 8 and 128')
            remote='/sdcard/Download/aios-'+m['files']['dsu.zip']['sha256'][:16]+'.zip'
            adb('push',str(root/'dsu.zip'),remote)
            remote_hash=adb('shell','sha256sum',remote).split()[0]
            if remote_hash!=m['files']['dsu.zip']['sha256']:raise RuntimeError('Device-side archive hash mismatch')
            adb('shell','am','start-activity','-n','com.android.dynsystem/com.android.dynsystem.VerificationActivity',
                '-a','android.os.image.action.START_INSTALL','-d','file://'+remote,'--el','KEY_USERDATA_SIZE',str(a.userdata_gib*1024**3))
            result['installer_opened']=True
    print(json.dumps(result,indent=2,ensure_ascii=False))

if __name__=='__main__':main()
