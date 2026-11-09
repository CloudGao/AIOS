from pathlib import Path
import xml.etree.ElementTree as ET
import re
r=Path(__file__).resolve().parents[2]
resources=[]
for locale in ('values','values-zh','values-b+zh+Hant'):
    p=r/'android/core-runtime/res'/locale/'documents.xml'
    resources.append({e.attrib['name']:''.join(e.itertext()) for e in ET.parse(p).getroot()})
assert resources[0].keys()==resources[1].keys()==resources[2].keys()
for k in resources[0]:
    placeholders=[re.findall(r'%\d+\$[ds]',x[k]) for x in resources]
    assert placeholders[0]==placeholders[1]==placeholders[2],k
manifest=ET.parse(r/'android/core-services/AndroidManifest.xml')
ns='{http://schemas.android.com/apk/res/android}'
worker=[e for e in manifest.iter('service') if e.get(ns+'name')=='.documents.DocumentWorkerService'][0]
assert worker.get(ns+'isolatedProcess')=='true' and worker.get(ns+'exported')=='false'
for module in ('agent','core-services'):
    for service in ET.parse(r/'android'/module/'AndroidManifest.xml').iter('service'):
        process=service.get(ns+'process')
        if process:assert re.fullmatch(r':?[A-Za-z0-9_.]+',process),process
print(f'PASS {len(resources[0])} localized document strings, placeholder consistency and isolated service declaration')
