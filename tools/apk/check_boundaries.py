#!/usr/bin/env python3
"""Static source-boundary audit only. Does not compile or execute Android code."""
from pathlib import Path
import ast
import re
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]

def check():
    for source in (ROOT/'tools/apk').glob('*.py'):
        ast.parse(source.read_text(),filename=str(source))
    assert not (ROOT/'android/host-apk/assembly.py').exists(), 'legacy behavioral adapter still exists'
    staging=(ROOT/'tools/apk/stage.py').read_text()
    for token in ('adapt_shared_source','platform_services(original','apk_adapter','setSilent','accessibilityTitle'):
        assert token not in staging, token
    forbidden=('com.android.internal.', 'ACTION_ACCESSIBILITY_DETAILS_SETTINGS', '.accessibilityTitle=', '.setSilent(',
               'new RomPlatformServices(', 'new ApkPlatformServices(', 'os.aios.apk.')
    for source in (ROOT/'android/agent').glob('*.java'):
        for token in forbidden:
            assert token not in source.read_text(), str(source)+': '+token
    shared=(ROOT/'android/agent/SharedPlatformServices.java').read_text()
    for token in ('RadioServices','AppFunctionServices','SystemDiagnostics','DeviceContributions','com.android.deskclock.aios'):
        assert token not in shared, token
    methods=re.findall(r'abstract (?:JSONObject|void) (\w+)\(',shared)
    for source in (ROOT/'android/device-rom/src/os/aios/agent/RomPlatformServices.java',
                   ROOT/'android/host-apk/src/os/aios/agent/ApkPlatformServices.java'):
        text=source.read_text()
        for method in methods:
            assert re.search(r'\b'+method+r'\(',text), str(source)+': '+method
    for folder,prefix in [('host-rom/agent','Rom'),('host-apk/src','Apk')]:
        base=ROOT/'android'/folder/'os/aios/agent'
        ui=(base/(prefix+'UiHost.java')).read_text()
        for method in ('accessibilitySettingsAction','configureOverlay','interventionNotification','selectionController'):
            assert method+'(' in ui, prefix+': '+method
        controller=(base/(prefix+'SelectionController.java')).read_text()
        for method in ('selecting','finishSelection','start','windowFocusChanged'):
            assert method+'(' in controller, prefix+': '+method
    assert not (ROOT/'android/host-apk/src/os/aios/host/HostBindings.java').exists(), 'duplicated shared binding logic'
    keys=[]
    for locale in ('values','values-zh-rCN','values-zh-rTW'):
        keys.append({node.get('name') for node in ET.parse(ROOT/'android/agent/res'/locale/'host_ui.xml').getroot()})
    assert keys[0]==keys[1]==keys[2]
    print('Static host boundaries, implementation coverage and localized resource keys passed; no compilation.')

if __name__=='__main__':check()
