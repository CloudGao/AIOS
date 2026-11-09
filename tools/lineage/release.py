#!/usr/bin/env python3
"""One Lineage build, one signed target-files, DSU + OTA + fastboot packaging.

Runs only on the build host. No device operations, wipes, or implicit uploads.
"""
from paths import SOURCE as SOURCE_ROOT, LINEAGE as LINEAGE_ROOT, STATE as BUILD_STATE, INPUTS as INPUT_ROOT
from pathlib import Path
from target_config import require_legacy_frankel
require_legacy_frankel()
import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import time
import zipfile
from contextlib import contextmanager
import fcntl
from optional_dependencies import OptionalDependencies

SOURCE = SOURCE_ROOT
TREE = LINEAGE_ROOT
HOST = TREE/'out/host/linux-x86/bin'
STATE = SOURCE/'tools/lineage/state/release'
KEYS = Path.home()/'.local/share/aios-release/keys'
EXCLUDED = {'io.github.muntashirakon.AppManager','com.tencent.android.qqdownloader','com.tencent.mm','com.autonavi.minimap',
            'com.xingin.xhs','com.taobao.taobao','com.sankuai.meituan','com.github.metacubex.clash.meta'}
REQUIRED = {'AIOSCoreServices','AIOSControlAgent','AIOSMiniApps','AIOSCodexRuntime',
            'AIOSSpeech'}

def sha(path):
    with Path(path).open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def run(args, **kwargs):
    env=os.environ.copy()
    env.pop('WITH_ADB_INSECURE',None)
    env.pop('ADB_VENDOR_KEYS',None)
    env['PATH']=str(HOST)+':'+str(TREE/'prebuilts/jdk/jdk21/linux-x86/bin')+':'+env['PATH']
    env['JAVA_HOME']=str(TREE/'prebuilts/jdk/jdk21/linux-x86')
    return subprocess.run(list(map(str,args)),check=True,env=env,**kwargs)

def save(path, value):
    temp=path.with_suffix(path.suffix+'.tmp')
    temp.write_text(json.dumps(value,indent=2)+'\n');temp.replace(path)

def key(name):
    if not re.fullmatch(r'[a-zA-Z0-9_.-]+',name):
        raise ValueError('Invalid key name')
    KEYS.mkdir(parents=True,exist_ok=True,mode=0o700);KEYS.chmod(0o700)
    base=KEYS/name
    files=[Path(str(base)+suffix) for suffix in ('.pem','.pk8','.x509.pem')]
    present=[p.exists() for p in files]
    if any(present) and not all(present):
        raise RuntimeError('Incomplete signing identity; recover it instead of replacing it: '+name)
    if not any(present):
        old=os.umask(0o077)
        try:
            run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:4096','-out',files[0]],stderr=subprocess.DEVNULL)
            run(['openssl','pkcs8','-topk8','-nocrypt','-in',files[0],'-outform','DER','-out',files[1]])
            run(['openssl','req','-new','-x509','-sha256','-days','10000','-key',files[0],'-out',files[2],'-subj','/CN=AIOS '+name+'/O=AIOS'])
        finally:os.umask(old)
    for p in files:p.chmod(0o600)
    return base

def inputs():
    files={}
    for folder in ('android','patches/lineage','integration','tools/lineage/targets'):
        for path in sorted((SOURCE/folder).rglob('*')):
            if path.is_file() and not any(part in ('.git','__pycache__') for part in path.parts):
                files[str(path.relative_to(SOURCE))]=sha(path)
    for path in sorted((SOURCE/'tools/lineage').glob('*.py')):files[str(path.relative_to(SOURCE))]=sha(path)
    return hashlib.sha256(json.dumps(files,sort_keys=True).encode()).hexdigest(),files

def prepare():
    for name in ('releasekey','platform','shared','media','networkstack','avb'):key(name)
    cert=run(['openssl','x509','-in',str(KEYS/'releasekey')+'.x509.pem','-outform','DER'],capture_output=True).stdout
    pin=hashlib.sha256(cert).hexdigest().upper()
    run(['python3',SOURCE/'tools/lineage/integrate.py','--target','frankel','--apply','--signing-certificates',KEYS])
    receipt=TREE/'vendor/aios/integration-state.json'
    staged=json.loads(receipt.read_text())
    save(STATE/'trust.json',dict(launcher_certificate_sha256=pin,
         platform_certificate_sha256=staged['platform_certificate_sha256'],integration_sha256=sha(receipt)))
    input_hash,files=inputs()
    save(STATE/'source-inputs.json',files)
    save(STATE/'prepared.json',dict(time=time.time(),variant='userdebug',integration_sha256=sha(receipt),trust=pin,source_inputs_sha256=input_hash))

def build():
    if not (STATE/'prepared.json').exists():raise RuntimeError('Run prepare first')
    prepared=json.loads((STATE/'prepared.json').read_text())
    if prepared.get('variant')!='userdebug':raise RuntimeError('Prepare the userdebug release first')
    if sha(TREE/'vendor/aios/integration-state.json')!=prepared['integration_sha256']:raise RuntimeError('Integration changed after prepare')
    expected=prepared['source_inputs_sha256']
    if inputs()[0]!=expected:raise RuntimeError('Source changed after prepare')
    started=time.time()
    run(['python3',SOURCE/'tools/lineage/build-unified.py','--full','--variant','userdebug'])
    if inputs()[0]!=expected:raise RuntimeError('Source changed during build; candidate is not reproducible')
    directory=TREE/'out/target/product/frankel/obj/PACKAGING/target_files_intermediates'
    targets=[p for p in directory.glob('*.zip') if p.stat().st_mtime>=started]
    if len(targets)!=1:raise RuntimeError('Expected exactly one newly built target-files archive')
    save(STATE/'built.json',dict(variant='userdebug',started=started,completed=time.time(),target_files=str(targets[0]),sha256=sha(targets[0]),prepared_sha256=sha(STATE/'prepared.json')))

def properties(text):
    return dict(line.split('=',1) for line in text.splitlines() if '=' in line and not line.startswith('#'))

def audit_target(path, signed=False):
    with zipfile.ZipFile(path) as z:
        names=z.namelist(); apps=[n for n in names if n.endswith('.apk') and not n.startswith('META/')]
        props={}
        for name in names:
            if name.startswith(('SYSTEM/','SYSTEM_EXT/','PRODUCT/','VENDOR/')) and name.endswith('/build.prop'):
                props.update(properties(z.read(name).decode()))
        if props.get('ro.build.type')!='userdebug':raise ValueError('Release must preserve userdebug')
        if props.get('ro.adb.secure')!='1':raise ValueError('Release must require ADB host authorization')
        if props.get('persist.sys.root_access','0')!='0':raise ValueError('Root access must remain user opt-in')
        if props.get('service.adb.root','0')!='0':raise ValueError('ADB must not default to root')
        public_signers=set()
        if signed:
            for cert in (TREE/'build/make/target/product/security').glob('*.x509.pem'):
                der=run(['openssl','x509','-in',cert,'-outform','DER'],capture_output=True).stdout
                public_signers.add(hashlib.sha256(der).hexdigest())
        present={Path(n).stem for n in apps}
        optional=OptionalDependencies(SOURCE)
        if json.loads(z.read('SYSTEM_EXT/etc/aios/optional-dependencies.json'))!=optional.data:
            raise ValueError('Optional dependency configuration differs from image')
        notices=json.loads((SOURCE/'licenses/apk-notices.json').read_text())
        if json.loads(z.read('SYSTEM_EXT/etc/aios/licenses/index.json'))!=notices:
            raise ValueError('Release notice index differs from source')
        for entry in notices['files']:
            name='SYSTEM_EXT/etc/aios/licenses/'+entry['sha256']+'.txt'
            if name not in names or hashlib.sha256(z.read(name)).hexdigest()!=entry['sha256']:
                raise ValueError('Missing or altered release notice: '+entry['path'])
        missing=(REQUIRED|optional.apps)-present
        from optional_dependencies import APPS
        forbidden=(APPS-optional.apps)&present
        if forbidden:raise ValueError('Disabled optional apps remain: '+str(sorted(forbidden)))
        for app in (APPS-optional.apps)|{'AIOSAppManager'}:
            native='aios-kokoro' if app=='AIOSKokoroTts' else app
            prefixes=('SYSTEM_EXT/lib64/'+native+'/','SYSTEM_EXT/app/'+app+'/','SYSTEM_EXT/priv-app/'+app+'/')
            if any(name.startswith(prefixes) for name in names):raise ValueError('Disabled optional application residue: '+app)
        if missing:raise ValueError('Missing release apps: '+str(sorted(missing)))
        for name in names:
            if Path(name).name=='adb_keys':
                standard_link=(stat.S_ISLNK(z.getinfo(name).external_attr >> 16)
                    and z.read(name)==b'/product/etc/security/adb_keys'
                    and 'PRODUCT/etc/security/adb_keys' not in names)
                if not standard_link:raise ValueError('Private/development ADB key in target-files: '+name)
            if Path(name).name in {'auth.json','validated.json','key.dpapi'} or 'dsu-diagnostics' in name:
                raise ValueError('Private/development artifact in target-files: '+name)
        # APK metadata, not file names, enforces the excluded package list.
        temp=STATE/'audit.apk'
        try:
            for name in apps:
                with z.open(name) as src,temp.open('wb') as dst:shutil.copyfileobj(src,dst)
                badging=run([HOST/'aapt2','dump','badging',temp],capture_output=True,text=True).stdout
                match=re.search(r"package: name='([^']+)'",badging)
                if not match:raise ValueError('Unreadable APK: '+name)
                if match[1] in EXCLUDED:raise ValueError('Excluded application: '+match[1])
                if signed:
                    report=run([HOST/'apksigner','verify','--print-certs',temp],capture_output=True,text=True).stdout
                    signers=re.findall(r'(?:V[0-9.]+ )?Signer(?: #[0-9]+)?:? certificate SHA-256 digest: ([a-fA-F0-9]{64})',report)
                    if not signers:raise ValueError('APK signer unavailable: '+name)
                    if public_signers.intersection(v.lower() for v in signers):raise ValueError('Public test signer in release: '+name)
                    if Path(name).stem in ('Launcher3QuickStep','AIOSCoreServices','AIOSControlAgent','AIOSMiniApps','AIOSCodexRuntime','AIOSSpeech'):
                        identity='releasekey' if Path(name).stem=='Launcher3QuickStep' else 'platform'
                        cert=run(['openssl','x509','-in',str(KEYS/identity)+'.x509.pem','-outform','DER'],capture_output=True).stdout
                        if {v.lower() for v in signers}!={hashlib.sha256(cert).hexdigest()}:raise ValueError('Release signer mismatch: '+name)
        finally:temp.unlink(missing_ok=True)
        model_files=['manifest.json','tokenizer.json','image.tflite','text.tflite','token-embeddings.fp16','LICENSE']
        if 'face' in optional.models:model_files.append('face.tflite')
        for name in model_files:
            if 'SYSTEM_EXT/etc/aios/search-models/'+name not in names:raise ValueError('Missing bundled model: '+name)
        if 'face' not in optional.models and 'SYSTEM_EXT/etc/aios/search-models/face.tflite' in names:
            raise ValueError('Disabled face model remains')
        lfm=[n for n in names if n.endswith('/LFM2.5-350M-Q4_K_M.gguf')]
        if bool(lfm)!=('lfm' in optional.models):raise ValueError('Local text model packaging mismatch')
        import io
        speech=[n for n in apps if Path(n).stem=='AIOSSpeech']
        for entry in speech:
            with zipfile.ZipFile(io.BytesIO(z.read(entry))) as apk:
                if ('assets/speaker.onnx' in apk.namelist())!=('speaker' in optional.models):raise ValueError('Speaker model packaging mismatch')
        info=properties(z.read('META/misc_info.txt').decode())
        return dict(apps=len(apps),variant=props['ro.build.type'],adb_secure=props['ro.adb.secure'],root_default=props.get('persist.sys.root_access','0'),dynamic_partitions=info.get('dynamic_partition_list','').split())

def package(output):
    record=json.loads((STATE/'built.json').read_text());source=Path(record['target_files'])
    if sha(source)!=record['sha256'] or sha(STATE/'prepared.json')!=record['prepared_sha256']:
        raise ValueError('Build identity changed; rebuild instead of mixing artifacts')
    if output.exists():raise FileExistsError('Output must be new; existing releases are never overwritten')
    output.mkdir(parents=True);audit_target(source)
    signed=output/'signed-target-files.zip'
    command=[HOST/'sign_target_files_apks','-o','-d',KEYS]
    with zipfile.ZipFile(source) as z:
        apkcerts=z.read('META/apkcerts.txt').decode()
        for cert in sorted(set(re.findall(r'certificate="([^"]+)"',apkcerts))):
            if cert in ('PRESIGNED','EXTERNAL'):continue
            src=cert.removesuffix('.x509.pem')
            name=Path(src).name
            if name in ('testkey','devkey'):name='releasekey'
            elif name not in ('platform','shared','media','networkstack'):name='apk-'+hashlib.sha256(src.encode()).hexdigest()[:16]
            command += ['-k',src+'='+str(key(name))]
        for line in z.read('META/apexkeys.txt').decode().splitlines():
            fields=dict(re.findall(r'(\w+)="([^"]*)"',line))
            if fields.get('private_key') in ('PRESIGNED','',None):continue
            name=fields['name'];identity=key('apex-'+hashlib.sha256(name.encode()).hexdigest()[:16])
            command += ['--extra_apks',name+'='+str(identity),'--extra_apex_payload_key',name+'='+str(identity)+'.pem']
        info=properties(z.read('META/misc_info.txt').decode())
        for partition in ('boot','init_boot','dtbo','recovery','system','system_ext','product','vendor','odm','vbmeta','vbmeta_system','vbmeta_vendor','vendor_boot','vendor_kernel_boot','vendor_dlkm','odm_dlkm','system_dlkm','pvmfw','system_other'):
            if info.get('avb_'+partition+'_key_path'):
                command += ['--avb_'+partition+'_key',str(KEYS/'avb.pem'),'--avb_'+partition+'_algorithm','SHA256_RSA4096']
        # Preserve rollback indices and padding; discard only verification-disabled flags.
        args=info.get('avb_vbmeta_args','')
        args=re.sub(r'--flags(?:=|\s+)\d+','',args).strip()
        command += ['--avb_vbmeta_extra_args','--flags 0']
    run([*command,source,signed],cwd=TREE)
    finish_package(output, signed, record)

def finish_package(output, signed, record):
    audit=audit_target(signed,signed=True)
    run([HOST/'ota_from_target_files','-k',KEYS/'releasekey',signed,output/'ota.zip'],cwd=TREE)
    run([HOST/'img_from_target_files','--exclude','IMAGES/userdata.img','--exclude','IMAGES/cache.img',signed,output/'images.zip'],cwd=TREE)
    dsu_parts=('system','system_ext','product')
    partitions={}
    with zipfile.ZipFile(signed) as z,zipfile.ZipFile(output/'dsu.zip','w',compression=zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as dsu:
        for part in dsu_parts:
            image=output/(part+'.img');raw=output/(part+'.raw.img')
            with z.open('IMAGES/'+part+'.img') as src,image.open('wb') as dst:shutil.copyfileobj(src,dst)
            with image.open('rb') as f:sparse=f.read(4)==b'\x3a\xff\x26\xed'
            if sparse:run([HOST/'simg2img',image,raw])
            else:shutil.copyfile(image,raw)
            partitions[part]=dict(signed_image_sha256=sha(image),raw_sha256=sha(raw),bytes=raw.stat().st_size)
            dsu.write(raw,part+'.img');image.unlink();raw.unlink()
    shutil.copyfile(SOURCE/'tools/lineage/install-release.py',output/'install-release.py')
    shutil.copyfile(SOURCE/'docs/RELEASE_INSTALL.md',output/'INSTALL.md')
    files={p.name:dict(sha256=sha(p),bytes=p.stat().st_size) for p in output.iterdir() if p.is_file()}
    save(output/'release.json',dict(schema=1,device='frankel',variant='userdebug',build=record,files=files,partitions=partitions,audit=audit,
         acceptance=dict(host_packaging='passed',dsu_boot='pending',physical_install='pending',first_use='pending',gpu_validation='pending',data_preserving_upgrade='pending'),
         distributable=False,warning='Candidate only. Device compatibility and recovery acceptance are required. Never relock the bootloader based on this manifest.'))
    shutil.copyfile(SOURCE/'tools/lineage/install-release.py',output/'install-release.py')
    shutil.copyfile(SOURCE/'docs/RELEASE_INSTALL.md',output/'INSTALL.md')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase',choices=['prepare','build','package'])
    parser.add_argument('--output',type=Path)
    args=parser.parse_args();STATE.mkdir(parents=True,exist_ok=True)
    with (STATE/'pipeline.lock').open('w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if args.phase=='prepare':prepare()
        elif args.phase=='build':build()
        elif args.output:package(args.output.resolve())
        else:parser.error('package requires --output')

if __name__=='__main__':main()
