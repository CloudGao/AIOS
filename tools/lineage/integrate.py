#!/usr/bin/env python3
"""Plan or stage the locked AIOS integration. Never builds or accesses a device."""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import zipfile
from target_config import SOURCE, ROOT, STATE, load_target, relative
from sync_source import validate_source
from product_selection import ProductSelection
from optional_dependencies import OptionalDependencies

BUNDLE = SOURCE / 'integration/lineage-24.0'
MODULES = ('agent','codex-runtime','workspace','automation','core-runtime','core-services','host-rom','capability-contracts','device-api','device-common','device-rom',
           'speech','miniapp','assistant-overlay','permission-anchor','browser-bridge','tts',
           'prebuilts','search-models','text-models','sepolicy')
from paths import INPUTS


def sha(data): return hashlib.sha256(data).hexdigest()


def file_hash(path):
    if not path.exists(): return None
    if path.is_symlink() or not path.is_file():
        raise ValueError('Not a regular file: ' + str(path))
    with path.open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()


def contained(root, name):
    path = root / relative(name)
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Path escapes root: ' + str(path))
    # Reject even in-root symlinks: the manifest names actual files.
    current = path
    while current != root:
        if current.is_symlink(): raise ValueError('Symlink in managed path: ' + str(current))
        current = current.parent
    return path


@contextmanager
def asset_stream(entry):
    prefix, name = entry['origin'].split(':', 1)
    root = {'source': SOURCE, 'inputs': INPUTS}[prefix]
    path = contained(root, name)
    if entry['kind'] == 'file':
        with path.open('rb') as stream: yield stream
    elif entry['kind'] == 'zip':
        with zipfile.ZipFile(path) as archive:
            with archive.open(entry['member']) as stream: yield stream
    elif entry['kind'] == 'tar':
        with tarfile.open(path) as archive:
            member = archive.getmember(entry['member'])
            if not member.isfile(): raise ValueError('Non-file model member')
            with archive.extractfile(member) as stream: yield stream
    else: raise ValueError('Unknown artifact kind')


class Plan:
    def __init__(self, root, baseline, previous):
        self.root, self.baseline, self.previous = root, baseline, previous
        self.items = {}

    def put(self, name, data=None, source=None, asset=None, allowed=(), mode=0o644):
        dest = contained(self.root, name)
        if source is not None:
            expected = file_hash(source)
            if expected is None: raise ValueError('Missing source: ' + str(source))
        elif asset is not None: expected = asset['sha256']
        else: expected = sha(data) if data is not None else None
        current = file_hash(dest)
        old = self.items.get(name)
        accepted = set(allowed) | {expected, self.baseline.get(name), self.previous.get(name)}
        if old:
            accepted.add(old['after'])
            accepted.add(old['before'])
        if current not in accepted:
            raise ValueError('Unrecognized local changes; no writes performed: ' + name)
        self.items[name] = dict(path=name, before=current, after=expected, data=data,
                                source=source, asset=asset, mode=mode)

    def receipt(self):
        return {name: item['after'] for name,item in self.items.items()}

    def apply(self):
        # Recheck the complete plan immediately before the first write.
        for item in self.items.values():
            if file_hash(contained(self.root,item['path'])) != item['before']:
                raise ValueError('File changed after preflight: ' + item['path'])
        for item in self.items.values():
            if item['before'] == item['after']: continue
            dest = contained(self.root,item['path'])
            if item['after'] is None:
                dest.unlink()  # Only individually inventoried obsolete integration source files.
                continue
            dest.parent.mkdir(parents=True,exist_ok=True)
            fd, temp_name = tempfile.mkstemp(prefix='.aios-',dir=dest.parent)
            temp = Path(temp_name)
            try:
                with os.fdopen(fd,'wb') as output:
                    if item['source'] is not None:
                        with item['source'].open('rb') as stream: shutil.copyfileobj(stream,output)
                    elif item['asset'] is not None:
                        with asset_stream(item['asset']) as stream: shutil.copyfileobj(stream,output)
                    else: output.write(item['data'])
                if file_hash(temp) != item['after']: raise ValueError('Source changed during staging')
                temp.chmod(item['mode'])
                temp.replace(dest)
            finally:
                if temp.exists(): temp.unlink()


def certificate(path):
    return sha(subprocess.check_output(['openssl','x509','-in',str(path),'-outform','DER'])).upper()


def source_files(plan, selection, optional=None):
    optional=optional or OptionalDependencies(SOURCE)
    # Explicit modules, no recursive copies of a checkout, output, downloads or device data.
    for module in MODULES:
        root = SOURCE / 'android' / module
        if not root.is_dir(): raise ValueError('Missing module: ' + module)
        for path in sorted(root.rglob('*')):
            rel = path.relative_to(root)
            if any(part.startswith('.') for part in rel.parts) or rel.parts[0] in ('deps','workspace'):
                continue
            if path.is_symlink(): raise ValueError('Source symlink: ' + str(path))
            if path.is_file():
                if not selection.includes_source(module,rel) or not optional.includes(module,rel):
                    continue
                name='vendor/aios/' + module + '/' + str(rel)
                if path.name in ('Android.bp','AndroidManifest.xml','models.mk','prebuilts.mk') or module=='host-rom' and path.suffix=='.java':
                    data=optional.transform(module,rel,selection.transform(module,rel,path.read_bytes()))
                    if data!=path.read_bytes():
                        plan.put(name,data=data,mode=path.stat().st_mode & 0o777)
                        continue
                plan.put(name,source=path,mode=path.stat().st_mode & 0o777)
    root = SOURCE / 'android/codex-runtime/workspace'
    for path in sorted(root.rglob('*')):
        rel = path.relative_to(root)
        if path.is_file() and not any(part.startswith('.') for part in rel.parts):
            plan.put('vendor/aios/codex-runtime/assets/aios-workspace/'+str(rel),source=path)
    for name in ('aios.mk','BoardConfigAIOS.mk'):
        path=SOURCE/'android/product'/name
        if name=='aios.mk':
            plan.put('vendor/aios/'+name,data=optional.product_makefile(selection.product_makefile(path.read_bytes())))
        else:
            plan.put('vendor/aios/'+name,source=path)
    # Launcher files remain authored in android/launcher, not in the captured baseline.
    root = SOURCE / 'android/launcher'
    for folder in ('src','res'):
        for path in sorted((root/folder).rglob('*')):
            if path.is_file():
                plan.put('packages/apps/Launcher3/'+str(path.relative_to(root)),source=path)
    plan.put('frameworks/base/packages/Shell/src/com/android/shell/AiosBrowserBridgeService.java',
             source=SOURCE/'android/shell/AiosBrowserBridgeService.java')
    plan.put('frameworks/base/services/core/java/com/android/server/AiosAssistantPolicy.java',
             source=SOURCE/'patches/lineage/assistant-overlay-bypass/AiosAssistantPolicy.java')
    for name,dest in (
        ('ComputerControlSession.java','core/java/android/companion/virtual/computercontrol/'),
        ('IComputerControlSession.aidl','core/java/android/companion/virtual/computercontrol/'),
        ('ComputerControlSessionImpl.java','services/companion/java/com/android/server/companion/virtual/computercontrol/')):
        plan.put('frameworks/base/'+dest+name,source=SOURCE/'android/framework/computercontrol'/name)


def materialize(repo, directory):
    """Recreate the reviewed delta in a temporary directory, never patch the live tree."""
    patch_path=contained(BUNDLE,repo['review_patch'])
    if file_hash(patch_path)!=repo['patch_sha256']:
        raise ValueError('Integration patch changed without updating manifest')
    entries=[e for e in repo['files'] if e['kind']=='patch']
    if not entries: return {}
    with tempfile.TemporaryDirectory(prefix='aios-integration-') as temp:
        scratch=Path(temp)
        for entry in entries:
            data=subprocess.check_output(['git','show',repo['revision']+':'+entry['path']],cwd=directory)
            if sha(data)!=entry['before_sha256']: raise ValueError('Patch base hash mismatch')
            path=contained(scratch,entry['path']);path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(data)
        subprocess.run(['git','apply','--check',str(patch_path)],cwd=scratch,check=True,capture_output=True)
        subprocess.run(['git','apply','--whitespace=nowarn',str(patch_path)],cwd=scratch,check=True,capture_output=True)
        result={}
        for entry in entries:
            data=contained(scratch,entry['path']).read_bytes()
            if sha(data)!=entry['after_sha256']: raise ValueError('Patch result hash mismatch: '+entry['path'])
            result[entry['path']]=data
        return result


def prepare(target, root=ROOT, signing_certificates=None):
    validate_source(root)
    selection=ProductSelection(SOURCE)
    optional=OptionalDependencies(SOURCE)
    manifest = json.loads((BUNDLE/'manifest.json').read_text())
    baseline = json.loads((BUNDLE/'staged-baseline.json').read_text())
    receipt_path = root/'vendor/aios/integration-state.json'
    previous = json.loads(receipt_path.read_text()).get('files',{}) if receipt_path.exists() else {}
    plan = Plan(root,baseline,previous)
    for repo in manifest['repositories']:
        selected = repo['scope']=='common' or repo['scope'] in target.config['device_integrations']
        directory = contained(root,repo['path'])
        if not selected:
            # Restore only exact, captured device-specific additions when changing targets.
            for entry in repo['files']:
                name = repo['path']+'/'+entry['path']
                if file_hash(contained(root,name)) not in {entry['after_sha256'],entry.get('observed_after_sha256',entry['after_sha256'])}: continue
                old = subprocess.run(['git','show',repo['revision']+':'+entry['path']],cwd=directory,capture_output=True)
                data = old.stdout if old.returncode==0 else None
                if (sha(data) if data is not None else None) != entry['before_sha256']:
                    raise ValueError('Device integration restore baseline unavailable')
                plan.put(name,data=data,allowed=(entry['after_sha256'],entry.get('observed_after_sha256',entry['after_sha256'])))
            continue
        actual = subprocess.check_output(['git','rev-parse','HEAD'],cwd=directory,text=True).strip()
        if actual != repo['revision']:
            raise ValueError('Review integration against changed repository revision: '+repo['path'])
        patched=materialize(repo,directory)
        for entry in repo['files']:
            allowed=(entry['before_sha256'],entry.get('observed_after_sha256',entry['after_sha256']))
            if entry['kind']=='patch':
                plan.put(repo['path']+'/'+entry['path'],data=patched[entry['path']],allowed=allowed,mode=entry['mode'])
            else:
                source=contained(SOURCE,entry['source'])
                if file_hash(source)!=entry['after_sha256']:
                    raise ValueError('Integration addition changed without updating manifest: '+entry['source'])
                plan.put(repo['path']+'/'+entry['path'],source=source,allowed=allowed,mode=entry['mode'])
    for name,expected in json.loads((BUNDLE/'source-binaries.json').read_text()).items():
        if not optional.includes_path(name):continue
        if file_hash(contained(SOURCE,name)) != expected:
            raise ValueError('Missing or changed source dependency: '+name)
    source_files(plan,selection,optional)
    plan.put('vendor/aios/optional-dependencies.json',data=(json.dumps(optional.data,sort_keys=True)+'\n').encode())
    make='vendor/aios/aios.mk'
    data=plan.items[make]['data']
    plan.put(make,data=data+b'\nPRODUCT_COPY_FILES += vendor/aios/optional-dependencies.json:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/aios/optional-dependencies.json\n')
    # Deliver the same pinned attribution set with ROM images, not only APKs.
    notices = json.loads((SOURCE/'licenses/apk-notices.json').read_text())
    copies = set()
    for entry in notices['files']:
        relative = Path(entry['path'])
        if relative.is_absolute() or '..' in relative.parts:
            raise ValueError('Invalid notice path')
        notice_root = (SOURCE/'licenses/apk-notices').resolve()
        source = (notice_root/relative).resolve()
        if not source.is_relative_to(notice_root):
            raise ValueError('Notice escapes source root')
        if file_hash(source) != entry['sha256']:
            raise ValueError('Notice provenance mismatch: '+str(relative))
        # Hash filenames also handle npm scoped names without relaxing path rules.
        filename = entry['sha256']+'.txt'
        staged = 'vendor/aios/licenses/'+filename
        plan.put(staged, source=source)
        copies.add(staged+':$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/aios/licenses/'+filename)
    plan.put('vendor/aios/licenses/index.json', data=(json.dumps(notices,sort_keys=True)+'\n').encode())
    copies.add('vendor/aios/licenses/index.json:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/aios/licenses/index.json')
    plan.put(make, data=plan.items[make]['data']+('\nPRODUCT_COPY_FILES += '+' '.join(sorted(copies))+'\n').encode())
    assets = json.loads((BUNDLE/'assets.json').read_text())
    selected_assets=[entry for entry in assets['files'] if optional.includes_path(entry['destination'])]
    selected_origins={entry['origin'] for entry in selected_assets}
    for origin,expected in assets['origins'].items():
        if origin not in selected_origins:continue
        prefix,name = origin.split(':',1)
        if file_hash(contained({'source':SOURCE,'inputs':INPUTS}[prefix],name)) != expected:
            raise ValueError('Missing or changed pinned artifact: '+origin)
    for entry in selected_assets:
        with asset_stream(entry) as stream:
            if hashlib.file_digest(stream,'sha256').hexdigest() != entry['sha256']:
                raise ValueError('Artifact member digest mismatch: '+entry['destination'])
        plan.put(entry['destination'],asset=entry,mode=entry['mode'])
    # Existing process and signature boundaries are unchanged. Only certificate pins vary.
    agent_path = (signing_certificates/'platform.x509.pem') if signing_certificates else contained(root,target.config['platform_certificate'])
    launcher_path = (signing_certificates/'releasekey.x509.pem') if signing_certificates else contained(root,target.config['launcher_certificate'])
    agent_cert = certificate(agent_path)
    launcher_cert = certificate(launcher_path)
    for name,old,new in (
        ('assistant-overlay/res/values/config.xml','C8A2E9BCCF597C2FB6DC66BEE293FC13F2FC47EC77BC6B2B0D52C11F51192AB8',agent_cert),
        ('miniapp/res/values/launcher_trust.xml','A40DA80A59D170CAA950CF15C18C454D47A39B26989D8B640ECD745BA71BF5DC',launcher_cert)):
        text=(SOURCE/'android'/name).read_text()
        if text.count(old)!=1: raise ValueError('Certificate template changed: '+name)
        plan.put('vendor/aios/'+name,data=text.replace(old,new).encode())
    # Preserve original device content, append only the exact common include.
    for field,line in (
        ('product_makefile','$(call inherit-product, vendor/aios/aios.mk)'),
        ('board_config','include vendor/aios/BoardConfigAIOS.mk')):
        name=target.config[field];path=contained(root,name)
        if not path.is_file(): raise ValueError('Missing target integration entry: '+name)
        before=path.read_bytes();text=before.decode()
        if line not in text.splitlines():
            plan.put(name,data=(text.rstrip()+'\n\n# AIOS shared integration\n'+line+'\n').encode(),allowed=(sha(before),))
        else: plan.put(name,data=before)
    if target.config['legacy_policy']:
        for item in manifest['legacy_policy']:
            if item['source'] and file_hash(contained(SOURCE,item['source'])) != item['after_sha256']:
                raise ValueError('Legacy policy restore snapshot changed')
            plan.put(item['path'],source=contained(SOURCE,item['source']) if item['source'] else None,
                     allowed=(item['before_sha256'],))
    # Tombstones carry exact old hashes; never delete unexpected source or user data.
    removed=['vendor/aios/codex-runtime/ExecutionLease.java']
    removed += ['vendor/aios/workspace/src/os/aios/workspace/'+n+'.java' for n in
                ('ContextStore','SkillFileStore','StandardRecords','UnifiedRecall','WorkspaceDream','WorkspaceMemory','WorkspaceSkills','WorkspaceSync')]
    removed += ['vendor/aios/automation/src/os/aios/automation/'+n+'.java' for n in
                ('AutomationStore','AutomationWorker','DagRuntime','PolicyLedger','Schedule','ScriptRuntime')]
    for name in removed: plan.put(name)
    # File moves and build exclusions must not leave old classes/resources in staged globs.
    # Only exact receipt-owned source files, never output trees or user/application data.
    managed=tuple('vendor/aios/'+module+'/' for module in MODULES)
    # Disabled payloads must not survive in globs from a previous full integration.
    for name,old_hash in {**baseline,**previous}.items():
        if old_hash and name.startswith(managed) and not optional.includes_path(name) and name not in plan.items:
            if contained(root,name).exists():plan.put(name)
    for name,old_hash in previous.items():
        if old_hash and name.startswith(managed) and name not in plan.items:
            module,relative_name=name[len('vendor/aios/'):].split('/',1)
            if name.endswith('.java') or not selection.includes_source(module,relative_name):
                plan.put(name)
    return plan, dict(schema=1,target=target.name,config_sha256=target.fingerprint,
                      product_manifest_sha256=selection.manifest['manifest_hash'],
                      optional_dependencies=optional.data,optional_dependencies_sha256=optional.fingerprint,
                      branch=target.config['branch'],files=plan.receipt(),
                      platform_certificate_sha256=agent_cert,launcher_certificate_sha256=launcher_cert,
                      status='staged_not_built')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target')
    parser.add_argument('--signing-certificates', type=Path, help='Final signing public certificates; private keys are never copied')
    actions=parser.add_mutually_exclusive_group(required=True)
    actions.add_argument('--plan',action='store_true')
    actions.add_argument('--apply',action='store_true')
    args=parser.parse_args();target=load_target(args.target)
    # The same lock serializes all products: they share the source tree.
    STATE.mkdir(parents=True,exist_ok=True)
    with (STATE/'image-build.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        plan,receipt=prepare(target, signing_certificates=args.signing_certificates)
        changed=[dict(path=x['path'],before_sha256=x['before'],after_sha256=x['after'])
                 for x in plan.items.values() if x['before']!=x['after']]
        if args.apply:
            path=ROOT/'vendor/aios/integration-state.json'
            old=json.loads(path.read_text()) if path.exists() else dict(files={})
            old['status']='applying'
            path.parent.mkdir(parents=True,exist_ok=True)
            pending=path.with_suffix('.json.tmp');pending.write_text(json.dumps(old,indent=2)+'\n');pending.replace(path)
            plan.apply()
            tmp=path.with_suffix('.json.tmp');tmp.write_text(json.dumps(receipt,indent=2)+'\n');tmp.replace(path)
            evidence=target.state/'experience';evidence.mkdir(parents=True,exist_ok=True)
            records=[dict(source=str(x['source']),staged=str(ROOT/x['path']),sha256=x['after'],
                          staged_sha256=file_hash(ROOT/x['path']),match=file_hash(ROOT/x['path'])==x['after'])
                     for x in plan.items.values() if x['source'] is not None]
            (evidence/'source-provenance.json').write_text(json.dumps(records,indent=2)+'\n')
            (evidence/'staging.json').write_text(json.dumps(dict(target=target.name,changes=changed),indent=2)+'\n')
        print(json.dumps(dict(target=target.name,status='staged_not_built' if args.apply else 'plan_only',
                              changed_files=len(changed),changes=changed),indent=2))


if __name__=='__main__': main()
