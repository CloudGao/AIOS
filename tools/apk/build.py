#!/usr/bin/env python3
"""Build only the APK and its embedded shell payload in the existing Lineage ARM64 out."""
from pathlib import Path
import argparse, datetime, fcntl, hashlib, json, os, shutil, subprocess, sys, zipfile
ROOT=Path(__file__).resolve().parents[2]

STATE=ROOT/'tools/apk/state'
sys.path.insert(0,str(ROOT/'tools/lineage'))
from target_config import load_target, STATE as BUILD_STATE
from paths import LINEAGE, snapshot as space, enough_space
from stage import stage, DEST

def package(target, signing_dir=None):
    host=LINEAGE/target.out/'host/linux-x86/bin'
    base=LINEAGE/target.out/'soong/.intermediates/vendor/aios-apk'
    app=base/'AIOSApk/android_common/AIOSApk.apk'
    helper=base/'AIOSApkShell/android_common/dex/AIOSApkShell.jar'
    if not helper.is_file():
        candidates=list((base/'AIOSApkShell').rglob('*.jar'))
        candidates=[p for p in candidates if zipfile.is_zipfile(p) and 'classes.dex' in zipfile.ZipFile(p).namelist()]
        if not candidates:raise RuntimeError('No compiled shell DEX payload')
        helper=candidates[0]
    unsigned=STATE/'aios-unsigned.apk'
    with zipfile.ZipFile(app) as original,zipfile.ZipFile(helper) as shell,zipfile.ZipFile(unsigned,'w') as output:
        names=set(original.namelist());number=2
        for item in original.infolist():
            if item.filename.startswith('META-INF/'):continue
            output.writestr(item,original.read(item.filename))
        for name in shell.namelist():
            if not name.endswith('.dex'):continue
            while 'classes'+str(number)+'.dex' in names:number+=1
            output.writestr('classes'+str(number)+'.dex',shell.read(name),compress_type=zipfile.ZIP_DEFLATED);number+=1
    aligned=STATE/'aios-aligned.apk'
    subprocess.run([str(host/'zipalign'),'-f','-p','4',str(unsigned),str(aligned)],check=True)
    # Private persistent development key; do not ship the globally known platform/test keys.
    keys=signing_dir or STATE/'signing'
    if signing_dir is not None and not all((keys/n).is_file() for n in ('development.pk8','development.x509.pem')):
        raise RuntimeError('Explicit signing directory must contain the existing key pair')
    keys.mkdir(exist_ok=True,mode=0o700)
    key=keys/'development.pk8';cert=keys/'development.x509.pem'
    if key.exists()!=cert.exists():raise RuntimeError('Incomplete signing identity; recover the existing key pair before building')
    if not key.exists():
        pem=keys/'key.pem'
        subprocess.run(['openssl','req','-new','-x509','-newkey','rsa:3072','-nodes','-keyout',str(pem),'-out',str(cert),'-days','3650','-subj','/CN=AIOS APK Development'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:subprocess.run(['openssl','pkcs8','-topk8','-nocrypt','-in',str(pem),'-outform','DER','-out',str(key)],check=True)
        finally:pem.unlink(missing_ok=True)
        key.chmod(0o600)
    final=STATE/'AIOS.apk'
    signer=host/'apksigner'
    env=os.environ.copy();env['JAVA_HOME']=str(LINEAGE/'prebuilts/jdk/jdk21/linux-x86');env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    subprocess.run([str(signer),'sign','--key',str(key),'--cert',str(cert),'--out',str(final),str(aligned)],check=True,env=env)
    subprocess.run([str(signer),'verify','--verbose',str(final)],check=True,env=env)
    return {'apk':str(final),'sha256':hashlib.sha256(final.read_bytes()).hexdigest(),'bytes':final.stat().st_size,'device_tested':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--stage-only',action='store_true');parser.add_argument('--reuse-graph',action='store_true');parser.add_argument('--signing-dir',type=Path,help='Existing persistent identity; never creates a replacement when explicitly supplied');args=parser.parse_args()
    if DEST != (LINEAGE/'vendor/aios-apk').resolve():
        parser.error('Build requires the configured Lineage vendor/aios-apk path; alternate staging is for offline validation only')
    STATE.mkdir(parents=True,exist_ok=True)
    target=load_target('frankel');lock_path=BUILD_STATE/'image-build.lock';lock_path.parent.mkdir(parents=True,exist_ok=True)
    with lock_path.open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        record={'started_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'before':space(),'scope':'single APK, no device operations','status':'running'}
        def save():
            temp=STATE/'build.json.next';temp.write_text(json.dumps(record,indent=2)+'\n');temp.replace(STATE/'build.json')
        try:
            if not enough_space(record['before'],24*2**30):raise RuntimeError('Need 24 GiB free before build')
            record['assembly']=stage();save()
            if args.stage_only:record['status']='staged_not_built';return
            env=target.environment();env.update(AIOS_SOONG_GOMAXPROCS='4',AIOS_SOONG_GOMEMLIMIT='14GiB',AIOS_SOONG_GOGC='25')
            targets=[target.out+'/soong/.intermediates/vendor/aios-apk/AIOSApk/android_common/AIOSApk.apk',target.out+'/soong/.intermediates/vendor/aios-apk/AIOSApkShell/android_common/dex/AIOSApkShell.jar']
            command=['build/soong/soong_ui.bash','--make-mode','-j4','-k0',*targets]
            if args.reuse_graph:
                variables=json.loads((LINEAGE/target.out/'soong'/('soong.'+target.product+'.variables')).read_text())
                if variables.get('DeviceArch')!='arm64':raise RuntimeError('Wrong graph architecture')
                command=[str(LINEAGE/'prebuilts/build-tools/linux-x86/bin/ninja'),'-f',target.out+'/combined-'+target.product+'.ninja','-j4','-k0',*targets]
                env['JAVA_HOME']=str(LINEAGE/'prebuilts/jdk/jdk21/linux-x86');env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
            with (STATE/'build.log').open('w') as log:
                child=subprocess.Popen(command,cwd=LINEAGE,env=env,stdout=log,stderr=subprocess.STDOUT);record['pid']=child.pid;save()
                try:
                    while child.poll() is None:
                        try:child.wait(timeout=30)
                        except subprocess.TimeoutExpired:pass
                        record['current']=space();save()
                        if not enough_space(record['current'],20*2**30):raise RuntimeError('20 GiB free-space reserve reached')
                finally:
                    if child.poll() is None:child.terminate();child.wait(timeout=30)
            if child.returncode:raise RuntimeError('Build failed; see '+str(STATE/'build.log'))
            record.update(package(target,args.signing_dir));record['status']='built_not_device_tested'
        except Exception as error:record.update(status='failed',error=str(error));raise
        finally:record['after']=space();save()
    print(json.dumps(record,indent=2))
if __name__=='__main__':main()
