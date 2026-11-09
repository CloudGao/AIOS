#!/usr/bin/env python3
"""Fast public-SDK compilation of the actual assembled sources, with real aapt2 resources."""
from pathlib import Path
import json, subprocess, zipfile, sys
from stage import DEST,ROOT
sys.path.insert(0,str(ROOT/'tools/lineage'))
from paths import LINEAGE
L=LINEAGE;O=ROOT/'tools/apk/state/sdk-check';O.mkdir(parents=True,exist_ok=True)
aapt=L/'out/host/linux-x86/bin/aapt2';sdk=L/'prebuilts/sdk/35/public/android.jar'
records=[]
def run(args,name):
    p=subprocess.run(list(map(str,args)),capture_output=True,text=True);(O/(name+'.log')).write_text(p.stdout+p.stderr)
    records.append({'step':name,'exit_code':p.returncode});return p.returncode
jars=[sdk,L/'prebuilts/sdk/35/public/core-for-system-modules.jar',L/'external/kotlinc/lib/kotlin-stdlib.jar',L/'prebuilts/tools/common/m2/repository/com/google/guava/guava/listenablefuture/1.0/listenablefuture-1.0.jar']+list((DEST/'deps').rglob('*.jar'))
aars=list((DEST/'deps').rglob('*.aar'))
for pattern in ['androidx/work/work-runtime/*/*.aar','androidx/javascriptengine/javascriptengine/*/*.aar','androidx/core/core/*/*.aar','androidx/annotation/annotation-jvm/*/annotation-jvm-*.jar','androidx/concurrent/concurrent-futures/*/*.jar','com/google/guava/listenablefuture/*/*.jar']:
    for p in (L/'prebuilts/sdk/current/androidx/m2repository').glob(pattern):
        if p.suffix=='.aar':aars.append(p)
        elif not p.name.endswith('-sources.jar'):jars.append(p)
for n,path in enumerate(aars):
    with zipfile.ZipFile(path) as z:
        jar=O/('dependency-'+str(n)+'.jar');jar.write_bytes(z.read('classes.jar'));jars.append(jar)
resources=[]
for folder in (DEST/'resources').iterdir():
    output=O/(folder.name+'.zip')
    if run([aapt,'compile','--dir',folder,'-o',output],'resources-'+folder.name)==0:resources.append(output)
# Use the same overlay order as the product, not arbitrary directory enumeration.
order=['core-runtime','workspace','automation','core-services','codex-runtime','miniapp','agent','host-apk']
resources=sorted(resources,key=lambda p:order.index(p.stem))
args=[aapt,'link','-o',O/'resources.apk','-I',sdk,'--manifest',DEST/'AndroidManifest.xml','--java',O/'generated','--auto-add-overlay','--extra-packages','os.aios.agent:os.aios.core:os.aios.workspace:os.aios.automation:os.aios.miniapp:os.aios.codex:os.aios.apk:os.aios.runtime','--min-sdk-version','35','--target-sdk-version','35']
for resource in resources:args+=['-R',resource]
if run(args,'resource-link')==0:
    sources=list((DEST/'src').rglob('*.java'))+list((O/'generated').rglob('*.java'))
    (O/'sources.rsp').write_text('\n'.join(map(str,sources)))
    run([L/'prebuilts/jdk/jdk21/linux-x86/bin/javac','--system',L/'out/soong/.intermediates/prebuilts/sdk/sdk_public_35_system_modules/android_common/system','-source','17','-target','17','-proc:none','-XDstringConcat=inline','-Xmaxerrs','150','-cp',':'.join(map(str,jars)),'-d',O/'classes','@'+str(O/'sources.rsp')],'javac')
(O/'results.json').write_text(json.dumps(records,indent=2));print(json.dumps(records,indent=2))

sys.exit(1 if any(record["exit_code"] for record in records) else 0)
