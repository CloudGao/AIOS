from pathlib import Path
import os,subprocess,json,datetime,fcntl,shutil,signal,sys
from app_batch import APPS
from sync_source import validate_source
from target_config import load_target, STATE
from paths import LINEAGE, snapshot as space, enough_space
import argparse
selector=argparse.ArgumentParser(add_help=False);selector.add_argument("--target")
selection,remaining=selector.parse_known_args();sys.argv=[sys.argv[0],*remaining]
target=load_target(selection.target)
r=LINEAGE;state=STATE;work=target.state/'experience';targets=list(APPS)+( ["services"] if "--with-services" in sys.argv else [])
requested=next((arg.split('=',1)[1].split(',') for arg in sys.argv if arg.startswith('--modules=')),None)
if requested:
 if not requested or any(name not in APPS for name in requested):raise ValueError('Unknown application module')
 targets=requested
if '--apk-only' in sys.argv:
 folders={'AIOSCoreServices':'core-services','AIOSControlAgent':'agent','AIOSCodexRuntime':'codex-runtime','AIOSMiniApps':'miniapp','AIOSSpeech':'speech','Launcher3QuickStep':'launcher'}
 if any(name not in folders for name in targets):raise ValueError('APK-only supports the AIOS application modules')
 targets=[target.out+'/soong/.intermediates/'+('packages/apps/Launcher3' if name=='Launcher3QuickStep' else 'vendor/aios/'+folders[name])+'/'+name+'/android_common/'+name+'.apk' for name in targets]
if '--combined-only' in sys.argv:
 targets=[target.out+'/soong/.intermediates/vendor/aios/'+folder+'/'+module+'/android_common/'+module+'.apk' for folder,module in [('core-services','AIOSCoreServices'),('agent','AIOSControlAgent'),('codex-runtime','AIOSCodexRuntime')]]+[target.out+'/soong/.intermediates/frameworks/base/services/services/android_common/aligned/services.jar']
work.mkdir(parents=True,exist_ok=True)
validate_source(r)
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def save():
 tmp=work/'build.json.tmp';tmp.write_text(json.dumps(record,indent=2)+'\n');tmp.replace(work/'build.json')
with (state/'image-build.lock').open('w') as lock:
 fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
 target.assert_staged()
 before=space()
 if not enough_space(before,24*2**30):raise RuntimeError('Need 24 GiB free for this incremental application batch')
 env=target.environment();env.update(AIOS_SOONG_GOMAXPROCS='4',AIOS_SOONG_GOMEMLIMIT='14GiB',AIOS_SOONG_GOGC='25')
 record=dict(status='running',target=target.name,product=target.product,out_dir=target.out,targets=targets,started_at=now(),before=before);child=None
 try:
  command=['build/soong/soong_ui.bash','--make-mode','-j4','-k0',*targets]
  if '--reuse-graph' in sys.argv:
   variables=json.loads((r/target.out/'soong'/('soong.'+target.product+'.variables')).read_text())
   assert variables.get('DeviceName')==target.device and variables.get('DeviceArch')=='arm64'
   assert (r/target.out/('combined-'+target.product+'.ninja')).is_file()
   # Only for content-only fixes after the same batch generated this fixed-out graph.
   env['JAVA_HOME']=str(r/'prebuilts/jdk/jdk21/linux-x86');env['PATH']=env['JAVA_HOME']+'/bin:'+str(r/target.out/'host/linux-x86/bin')+':'+env['PATH']
   command=[str(r/'prebuilts/build-tools/linux-x86/bin/ninja'),'-f',target.out+'/combined-'+target.product+'.ninja','-j4','-k0',*targets]
  if '--reuse-installed-speech-libs' in sys.argv:
   raise ValueError('Use ordinary ELF validation for public builds')
  with (work/'build.log').open('w') as log:
   child=subprocess.Popen(['taskset','-c',','.join(map(str,sorted(os.sched_getaffinity(0))[:4])),*command],cwd=r,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);record['pid']=child.pid;save()
   while child.poll() is None:
    try:child.wait(timeout=30)
    except subprocess.TimeoutExpired:pass
    record['current']=space();save()
    if not enough_space(record['current'],20*2**30):raise RuntimeError('Stopped at 20 GiB reserve')
   record.update(status='succeeded' if child.returncode==0 else 'failed',exit_code=child.returncode)
 except BaseException as e:record.update(status='interrupted',error=str(e))
 finally:
  if child and child.poll() is None:
   os.killpg(child.pid,signal.SIGTERM)
   try:child.wait(timeout=20)
   except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
  record.update(after=space(),finished_at=now());save()

sys.exit(0 if record["status"]=="succeeded" else 1)
