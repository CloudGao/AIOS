#!/usr/bin/env python3
"""Offline verification of the produced candidate, with no device connection."""
from paths import SOURCE as SOURCE_ROOT, LINEAGE as LINEAGE_ROOT, STATE as BUILD_STATE, INPUTS as INPUT_ROOT
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import re
import shutil
import struct
import stat
import subprocess
import sys
import tempfile
import time
import zipfile

TREE=LINEAGE_ROOT
HOST=TREE/'out/host/linux-x86/bin'
SOURCE=SOURCE_ROOT
KEYS=Path.home()/'.local/share/aios-release/keys'
STATE=SOURCE/'tools/lineage/state/release'

def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def stream_sha(stream):return hashlib.file_digest(stream,'sha256').hexdigest()

def run(args,**kwargs):
    env=os.environ.copy();env['PATH']=str(HOST)+':'+env['PATH']
    return subprocess.run(list(map(str,args)),check=True,env=env,**kwargs)

def extract(z,name,dest):
    with z.open(name) as src,dest.open('wb') as out:shutil.copyfileobj(src,out,1024*1024)

def verify(root, scratch=None):
    manifest=json.loads((root/'release.json').read_text())
    report={'started':time.time(),'device_operations':False,'checks':{}}
    for name,spec in manifest['files'].items():
        if Path(name).name!=name:raise ValueError('Unsafe release path')
        path=root/name
        if path.stat().st_size!=spec['bytes'] or sha(path)!=spec['sha256']:raise ValueError('Release file digest mismatch: '+name)
    report['checks']['file_hashes']='passed'
    print('PASS '+'file_hashes',flush=True)
    with zipfile.ZipFile(root/'signed-target-files.zip') as target,zipfile.ZipFile(root/'ota.zip') as ota,zipfile.ZipFile(root/'images.zip') as images,zipfile.ZipFile(root/'dsu.zip') as dsu:
        for label,z in [('target_files',target),('ota',ota),('images',images),('dsu',dsu)]:
            damaged=z.testzip()
            if damaged:raise ValueError('ZIP CRC failure: '+label+'/'+damaged)
        report['checks']['zip_crc']='passed'
        print('PASS '+'zip_crc',flush=True)
        # Signed release build properties, independent of the manifest's claim.
        build=[]
        for name in target.namelist():
            if name.startswith(('SYSTEM/','SYSTEM_EXT/','PRODUCT/','VENDOR/')) and name.endswith('/build.prop'):
                build.append(target.read(name).decode())
        props=dict(line.split('=',1) for text in build for line in text.splitlines() if '=' in line and not line.startswith('#'))
        if props.get('ro.build.type')!='userdebug' or manifest.get('variant')!='userdebug':raise ValueError('Expected a userdebug release product')
        if props.get('ro.adb.secure')!='1':raise ValueError('ADB host authorization must be enabled')
        if props.get('service.adb.root','0')!='0' or props.get('persist.sys.root_access','0')!='0':raise ValueError('Root must remain user opt-in')
        for name in target.namelist():
            if Path(name).name=='adb_keys' and not (stat.S_ISLNK(target.getinfo(name).external_attr >> 16)
                and target.read(name)==b'/product/etc/security/adb_keys'
                and 'PRODUCT/etc/security/adb_keys' not in target.namelist()):
                raise ValueError('Preauthorized ADB host key in release: '+name)
        if 'release-keys' not in props.get('ro.build.tags','') or 'test-keys' in props.get('ro.build.tags',''):raise ValueError('Release build tags are not finalized')
        report['build_properties']={k:props.get(k) for k in ['ro.build.type','ro.debuggable','ro.adb.secure','ro.build.tags','ro.build.fingerprint','ro.build.version.incremental']}
        report['checks']['release_build_properties']='passed'
        print('PASS '+'release_build_properties',flush=True)
        scratch = (scratch or STATE).resolve()
        scratch.mkdir(parents=True, exist_ok=True)
        if shutil.disk_usage(scratch).free<50*1024**3:raise RuntimeError('Require 50 GiB verification scratch space')
        with tempfile.TemporaryDirectory(prefix='artifact-verification-',dir=scratch) as tmp:
            temp=Path(tmp);parts={}
            report['embedded_trust']={}
            for app,identity in [('AIOSAssistantOverlay','platform'),('AIOSMiniApps','releasekey')]:
                candidates=[n for n in target.namelist() if n.endswith('/'+app+'.apk')]
                if not candidates and app=='AIOSAssistantOverlay':
                    report['embedded_trust'][app]='not_packaged'
                    continue
                if len(candidates)!=1:raise ValueError('Ambiguous or missing trust resource APK: '+app)
                apk=temp/(app+'.apk');extract(target,candidates[0],apk)
                resources=run([HOST/'aapt2','dump','resources',apk],capture_output=True,text=True).stdout
                cert=run(['openssl','x509','-in',KEYS/(identity+'.x509.pem'),'-outform','DER'],capture_output=True).stdout
                fingerprint=hashlib.sha256(cert).hexdigest()
                if fingerprint not in resources.lower():raise ValueError('Final certificate trust resource mismatch: '+app)
                report['embedded_trust'][app]=fingerprint
                apk.unlink()
            report['checks']['final_certificate_trust_resources']='passed'
            print('PASS final_certificate_trust_resources',flush=True)
            for name in target.namelist():
                if re.fullmatch(r'(?:IMAGES|RADIO)/[a-zA-Z0-9_]+\.img',name):
                    part=Path(name).stem
                    if part in parts:raise ValueError('Ambiguous target partition: '+part)
                    path=temp/(part+'.img');extract(target,name,path)
                    parts[part]={'signed_sha256':sha(path)}
            result=run([HOST/'avbtool','verify_image','--image',temp/'vbmeta.img','--key',KEYS/'avb.pem','--follow_chain_partitions'],capture_output=True,text=True)
            report['avb_verification']=result.stdout
            for part in ['vbmeta','vbmeta_system','vbmeta_vendor']:
                image=temp/(part+'.img')
                if not image.exists():continue
                info=run([HOST/'avbtool','info_image','--image',image],capture_output=True,text=True).stdout
                flags=re.search(r'^Flags:\s+(\d+)',info,re.MULTILINE)
                if not flags or int(flags[1])!=0:raise ValueError('Disabled AVB verification: '+part)
                run([HOST/'avbtool','verify_image','--image',image,'--key',KEYS/'avb.pem','--follow_chain_partitions'],capture_output=True,text=True)
            report['checks']['avb_chain_and_flags']='passed'
            print('PASS '+'avb_chain_and_flags',flush=True)
            # Fastboot image entries must be byte-identical to the signed target-files.
            for entry in images.namelist():
                if entry.endswith('.img'):
                    part=Path(entry).stem
                    if part in ('userdata','cache'):raise ValueError('Unexpected user-data image')
                    if part not in parts:raise ValueError('Image has no signed target-files counterpart: '+entry)
                    with images.open(entry) as src:
                        if stream_sha(src)!=parts[part]['signed_sha256']:raise ValueError('Fastboot image mismatch: '+entry)
            report['checks']['fastboot_images_same_source']='passed'
            print('PASS '+'fastboot_images_same_source',flush=True)
            # Check both the whole OTA signature and the update-engine payload signature.
            run([HOST/'check_ota_package_signature',str(KEYS/'releasekey')+'.x509.pem',root/'ota.zip'],capture_output=True,text=True)
            payload=temp/'payload.bin';extract(ota,'payload.bin',payload)
            pub=temp/'ota-public.pem'
            run(['openssl','x509','-in',str(KEYS/'releasekey')+'.x509.pem','-pubkey','-noout','-out',pub])
            run([HOST/'delta_generator','--in_file='+str(payload),'--public_key='+str(pub)],capture_output=True,text=True)
            report['checks']['ota_and_payload_signatures']='passed'
            print('PASS '+'ota_and_payload_signatures',flush=True)
            # Parse the authoritative payload schema to compare the OTA's target hashes.
            proto=TREE/'system/update_engine/update_metadata.proto'
            # The distribution's compiler/runtime pair parses the checked-out proto2 schema.
            run(['/usr/bin/protoc','--proto_path='+str(proto.parent),'--python_out='+str(temp),proto.name],cwd=proto.parent)
            spec=importlib.util.spec_from_file_location('aios_update_metadata',temp/'update_metadata_pb2.py')
            module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
            with payload.open('rb') as f:
                magic,major,length=struct.unpack('>4sQQ',f.read(20))
                if magic!=b'CrAU' or major!=2 or length>64*1024**2:raise ValueError('Unexpected payload header')
                f.read(4);data=f.read(length)
            delta=module.DeltaArchiveManifest();delta.ParseFromString(data)
            if delta.minor_version!=0 or delta.partial_update:raise ValueError('Expected a complete full OTA')
            for update in delta.partitions:
                part=update.partition_name
                if part not in parts:raise ValueError('OTA partition missing from target-files: '+part)
                image=temp/(part+'.img')
                with image.open('rb') as f:sparse=f.read(4)==b'\x3a\xff\x26\xed'
                raw=temp/(part+'.raw.img') if sparse else image
                if sparse:run([HOST/'simg2img',image,raw])
                digest=sha(raw)
                if digest!=update.new_partition_info.hash.hex() or raw.stat().st_size!=update.new_partition_info.size:raise ValueError('OTA target partition mismatch: '+part)
                parts[part].update(raw_sha256=digest,bytes=raw.stat().st_size)
                if part in ('system','system_ext','product'):
                    with dsu.open(part+'.img') as src:
                        if stream_sha(src)!=digest:raise ValueError('DSU partition mismatch: '+part)
                    if manifest['partitions'][part]['raw_sha256']!=digest:raise ValueError('Partition manifest mismatch: '+part)
                if sparse:raw.unlink()
            if not {'system','system_ext','product'}<=set(u.partition_name for u in delta.partitions):raise ValueError('Incomplete OTA')
            report['checks']['ota_dsu_target_files_same_partitions']='passed'
            print('PASS '+'ota_dsu_target_files_same_partitions',flush=True)
            report['partitions']=parts
    report.update(status='passed',finished=time.time(),hardware_acceptance='not_performed')
    output=root/'offline-verification.json';output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'status':'passed','report':str(output),'checks':report['checks']},indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('release',type=Path);parser.add_argument('--scratch-dir',type=Path,help='Temporary extraction volume with at least 50 GiB free');args=parser.parse_args();verify(args.release.resolve(),args.scratch_dir)
