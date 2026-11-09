from pathlib import Path
import subprocess,os,zipfile,wave,sys,argparse
parser=argparse.ArgumentParser(description='Build a speech probe using caller-provided, licensed 16kHz mono PCM WAV fixtures; does not install or run on a device.')
parser.add_argument('--zh-wav',type=Path,required=True)
parser.add_argument('--en-wav',type=Path,required=True)
parser.add_argument('--signing-dir',type=Path,help='Existing test signing identity; never copied into source')
args=parser.parse_args()
r=Path(__file__).resolve().parents[2];w=r/'tools/apk/state/first-run-probe';w.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(r/'tools/lineage'))
from paths import LINEAGE
l=LINEAGE;j=l/'prebuilts/jdk/jdk21/linux-x86/bin';h=l/'out/host/linux-x86/bin';sdk=l/'prebuilts/sdk/35/public/android.jar'
(w/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="os.aios.firsttest"><uses-sdk android:minSdkVersion="35" android:targetSdkVersion="35"/><application android:label="AIOS setup test"/><instrumentation android:name=".SpeechProbe" android:targetPackage="os.aios.app"/></manifest>')
for d in ['classes','dex','assets']:(w/d).mkdir(exist_ok=True)
for lang in ['zh','en']:
 with wave.open(str(getattr(args,lang+'_wav'))) as a:
  assert (a.getnchannels(),a.getsampwidth(),a.getframerate())==(1,2,16000)
  (w/'assets'/f'{lang}.pcm').write_bytes(a.readframes(a.getnframes()))
env=os.environ.copy();env['JAVA_HOME']=str(j.parent);env['PATH']=str(j)+':'+env['PATH']
def run(args):subprocess.run(list(map(str,args)),check=True,env=env)
run([j/'javac','-source','17','-target','17','-cp',sdk,'-d',w/'classes',r/'tests/apk-first-run/SpeechProbe.java'])
run([h/'d8','--lib',sdk,'--output',w/'dex',*list((w/'classes').rglob('*.class'))])
run([h/'aapt2','link','-I',sdk,'--manifest',w/'AndroidManifest.xml','-A',w/'assets','-o',w/'unsigned.apk'])
with zipfile.ZipFile(w/'unsigned.apk','a') as z:z.write(w/'dex/classes.dex','classes.dex')
run([h/'zipalign','-f','4',w/'unsigned.apk',w/'aligned.apk'])
keys=args.signing_dir or r/'tools/apk/state/signing'
assert (keys/'development.pk8').is_file(), 'Existing development signing key required'
run([h/'apksigner','sign','--key',keys/'development.pk8','--cert',keys/'development.x509.pem','--out',w/'probe.apk',w/'aligned.apk'])
print(w/'probe.apk')
