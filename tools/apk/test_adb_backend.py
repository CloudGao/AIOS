from pathlib import Path
import subprocess,xml.etree.ElementTree as ET,sys
r=Path(__file__).resolve().parents[2]
out=r/'tools/apk/state/adb-backend-tests';out.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(r/'tools/lineage'))
from paths import LINEAGE
j=LINEAGE/'prebuilts/jdk/jdk21/linux-x86/bin'
subprocess.run([str(j/'javac'),'-d',str(out),str(r/'android/host-apk/shell/os/aios/apk/shell/SharedPath.java'),str(r/'tests/apk-adb/SharedPathTests.java')],check=True)
subprocess.run([str(j/'java'),'-cp',str(out),'os.aios.apk.shell.SharedPathTests'],check=True)
shell=(r/'android/host-apk/shell/os/aios/apk/shell/ShellMain.java').read_text()
assert 'Binder.getCallingUid()!=owner' in shell
assert 'stale_shell_instance' in shell
backend=(r/'android/host-apk/src/os/aios/agent/ApkPackageBackend.java').read_text()
assert 'PackageServices.read(c,"download-"+p.getLong("id"))' in backend
assert 'installation_backend_lost_query_package' in backend
assert 'id=-(1L+' in backend
files=(r/'android/host-apk/shell/os/aios/apk/shell/ShellFiles.java').read_text()
assert 'O_EXCL' in files and 'O_NOFOLLOW' in files
pkg=(r/'android/agent/PackageServices.java').read_text()
assert pkg.index('APK SHA256 mismatch') < pkg.index('backend.install(id,j,files)')
assert pkg.index('Split package/version/signers differ') < pkg.index('backend.install(id,j,files)')
print('PASS: protocol ownership, epoch, job routing and validation source checks (not device execution)')
