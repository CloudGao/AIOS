from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[2]
out=r/'tools/lineage/state/turn-memory-host';out.mkdir(parents=True,exist_ok=True)
from paths import LINEAGE
j=LINEAGE/'prebuilts/jdk/jdk21/linux-x86/bin'
cp=LINEAGE/'prebuilts/misc/common/json/json-prebuilt.jar'
if not cp.is_file():raise SystemExit('Lineage host JSON jar unavailable')
subprocess.run([str(j/'javac'),'-cp',str(cp),'-d',str(out),str(r/'android/codex-runtime/TurnMemoryContext.java'),str(r/'tests/runtime/TurnMemoryContextTests.java')],check=True)
subprocess.run([str(j/'java'),'-cp',str(out)+':'+str(cp),'os.aios.codex.TurnMemoryContextTests'],check=True)
