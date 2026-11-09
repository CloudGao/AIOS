"""Exercise the shell bootstrap against old/new framework behavior, without a device."""
from pathlib import Path
import re, subprocess, tempfile, xml.etree.ElementTree as ET, sys
r=Path(__file__).resolve().parents[2]
translations=[]
for language in ['values','values-zh-rCN','values-zh-rTW']:
 tree=ET.parse(r/f'android/host-apk/res/{language}/permissions.xml')
 translations.append({n.get('name'):n.text for n in tree.getroot()})
for translation in translations[1:]:
 assert translation.keys()==translations[0].keys()
 for key,value in translations[0].items():
  assert translation[key]
  assert re.findall(r'%[0-9]*\$?[sd]',value)==re.findall(r'%[0-9]*\$?[sd]',translation[key]),key
print('PASS: permission resources and placeholders in English, Simplified and Traditional Chinese')
source=(r/'android/host-apk/shell/os/aios/apk/shell/ShellMain.java').read_text()
start=source.index('    private static void initializeWindowManager()')
end=source.index('    private void start()',start)
method=source[start:end].replace('android.animation.ValueAnimator','ValueAnimator')
body='''
class RemoteException extends Exception {}
interface IWindowManager { float getCurrentAnimatorScale() throws RemoteException; }
class ValueAnimator { static float scale=-1; static void setDurationScale(float v){scale=v;} }
class WindowManagerGlobal {
 static int calls,mode;
 static IWindowManager getWindowManagerService(){
  calls++;
  if(mode==1&&calls==1||mode==4)throw new IllegalStateException("ApplicationSharedMemory not initialized");
  if(mode==2)throw new IllegalStateException("different failure");
  return ()->{if(mode==3)throw new RemoteException();return 0.5f;};
 }
}
public class BootstrapTest {
 METHOD
 static void reset(int mode){WindowManagerGlobal.mode=mode;WindowManagerGlobal.calls=0;ValueAnimator.scale=-1;}
 public static void main(String[] args)throws Exception{
  reset(0);initializeWindowManager();if(WindowManagerGlobal.calls!=1||ValueAnimator.scale!=-1)throw new AssertionError("old behavior altered");
  reset(1);initializeWindowManager();if(WindowManagerGlobal.calls!=2||ValueAnimator.scale!=0.5f)throw new AssertionError("shared memory fallback");
  reset(2);try{initializeWindowManager();throw new AssertionError("swallowed unrelated error");}catch(IllegalStateException expected){if(!expected.getMessage().equals("different failure"))throw expected;}
  reset(4);try{initializeWindowManager();throw new AssertionError("swallowed persistent failure");}catch(IllegalStateException expected){if(WindowManagerGlobal.calls!=2)throw new AssertionError("unbounded retry");}
  System.out.println("PASS: old initialization, Android 17 fallback, unrelated error propagation, bounded persistent failure");
 }
}
'''.replace('METHOD',method)
sys.path.insert(0,str(r/'tools/lineage'))
from paths import LINEAGE
j=LINEAGE/'prebuilts/jdk/jdk21/linux-x86/bin'
with tempfile.TemporaryDirectory() as directory:
 p=Path(directory)/'BootstrapTest.java';p.write_text(body)
 subprocess.run([str(j/'javac'),str(p)],check=True)
 subprocess.run([str(j/'java'),'-cp',directory,'BootstrapTest'],check=True)
