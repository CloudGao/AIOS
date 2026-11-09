#!/usr/bin/env python3
"""Assemble the single APK without changing ROM inputs or the ROM staging tree."""
from pathlib import Path
import hashlib, importlib.util, json, os, re, sys, zipfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools/lineage'))
from paths import LINEAGE
DEST=Path(os.environ.get('AIOS_APK_STAGE', str(LINEAGE/'vendor/aios-apk'))).resolve()
A='{http://schemas.android.com/apk/res/android}'
T='{http://schemas.android.com/tools}'
PACKAGE='os.aios.app'
ET.register_namespace('android',A[1:-1]);ET.register_namespace('tools',T[1:-1])
spec=importlib.util.spec_from_file_location('product_selection',ROOT/'tools/lineage/product_selection.py')
selection=importlib.util.module_from_spec(spec);spec.loader.exec_module(selection)
IDENTITIES={'os.aios.core.runtime':'os.aios.app.core','os.aios.core.capabilities':'os.aios.app.capabilities','os.aios.agent.inference':'os.aios.app.inference','os.aios.agent.home':'os.aios.app.home','os.aios.agent.history':'os.aios.app.history','os.aios.agent.legacy':'os.aios.app.legacy','os.aios.codex.miniapps':'os.aios.app.projects','os.aios.miniapp.manage':'os.aios.app.miniapps.manage','os.aios.miniapp.launcher':'os.aios.app.miniapps.launcher'}
PACKAGES=['os.aios.agent','os.aios.core','os.aios.codex','os.aios.miniapp','os.aios.permissionanchor']

def relocate(text):
    for old,new in IDENTITIES.items():text=text.replace('content://'+old,'content://'+new).replace('"'+old+'"','"'+new+'"')
    for old in PACKAGES:text=text.replace('"'+old+'"','"'+PACKAGE+'"')
    return text.replace('os.aios.permission.','os.aios.app.permission.').replace('"aios_cc_poc"','"aios_apk_control"').replace('"aios_codex_runtime_debug"','"aios_apk_runtime_debug"')

def manifest(product):
    root=ET.Element('manifest',{'package':PACKAGE,A+'versionCode':'12',A+'versionName':'0.1.11'})
    ET.SubElement(root,'uses-sdk',{A+'minSdkVersion':'35',A+'targetSdkVersion':'35'})
    app=ET.SubElement(root,'application',{A+'name':'os.aios.apk.ApkApplication',A+'label':'@string/apk_app_name',A+'icon':'@mipmap/ic_redzhong',A+'theme':'@style/AiosSystemTheme',A+'allowBackup':'false',A+'supportsRtl':'true',A+'extractNativeLibs':'true',A+'usesCleartextTraffic':'false'})
    seen=set();children=set()
    privileged={'REAL_GET_TASKS','DUMP','INTERNAL_SYSTEM_WINDOW','ACCESS_COMPUTER_CONTROL','ACCESSIBILITY_MOTION_EVENT_OBSERVING','OPEN_ACCESSIBILITY_DETAILS_SETTINGS','WRITE_SECURE_SETTINGS','REBOOT','FORCE_STOP_PACKAGES','START_ACTIVITIES_FROM_BACKGROUND','SET_TIME','MODIFY_PHONE_STATE','TETHER_PRIVILEGED','NETWORK_SETTINGS','BLUETOOTH_PRIVILEGED','MODIFY_AUDIO_ROUTING','EXECUTE_APP_FUNCTIONS_SYSTEM','INTERACT_ACROSS_USERS','INSTALL_PACKAGES','FOREGROUND_SERVICE_MICROPHONE'}
    for module in ['agent','core-services','codex-runtime','automation','workspace','miniapp','permission-anchor']:
        source=ROOT/'android'/module/'AndroidManifest.xml'
        tree=ET.fromstring(product.transform(module,'AndroidManifest.xml',source.read_bytes()))
        package=tree.get('package')
        for node in tree:
            if node.tag=='uses-sdk':continue
            if node.tag!='application':
                name=node.get(A+'name','')
                if node.tag=='uses-permission' and (name.split('.')[-1] in privileged or 'VOICE_MINUTES' in name or 'gsf' in name):continue
                if node.tag=='queries':continue
                if node.tag=='permission':node.set(A+'protectionLevel','signature');node.attrib.pop(A+'knownCerts',None)
                key=(node.tag,name)
                if key not in seen:root.insert(0,node);seen.add(key)
                continue
            for component in node:
                name=component.get(A+'name','')
                if name.startswith('.'):name=package+name
                component.set(A+'name',name)
                if name=='os.aios.agent.OptionalAppsActivity':continue
                if name in children:continue
                children.add(name)
                if name=='androidx.startup.InitializationProvider':component.set(A+'authorities',PACKAGE+'.androidx-startup');app.append(component);continue
                component.set(A+'exported','false')
                if name.startswith('os.aios.codex.'):component.set(A+'process',':runtime')
                if name.startswith('os.aios.miniapp.'):component.set(A+'process',':miniapps')
                if name=='os.aios.agent.PhoneNotifications':component.set(A+'exported','true');component.set(A+'label','@string/apk_notification_listener')
                if name=='os.aios.agent.ForegroundUiService':component.set(A+'exported','true');component.set(A+'label','@string/apk_accessibility_label')
                if name in ['os.aios.agent.ShareToAssistantActivity','os.aios.agent.SelectionAskActivity']:component.set(A+'exported','true')
                if name=='os.aios.agent.AgentService':component.set(A+'foregroundServiceType','specialUse|location')
                if name=='os.aios.agent.SelectionAskActivity':component.set(A+'label','@string/apk_open_assistant')
                for child in list(component):
                    if name=='os.aios.agent.AssistantActivity' and child.tag=='intent-filter' and any(x.tag=='action' and x.get(A+'name')=='android.intent.action.ASSIST' for x in child):
                        component.remove(child);continue
                    if child.tag=='intent-filter' and any(x.tag=='category' and x.get(A+'name')=='android.intent.category.LAUNCHER' for x in child):component.remove(child)
                app.append(component)
    queries=ET.SubElement(root,'queries');query=ET.SubElement(queries,'intent');ET.SubElement(query,'action',{A+'name':'android.speech.RecognitionService'})
    speech=ET.SubElement(app,'service',{A+'name':'os.aios.speech.SenseVoiceService',A+'exported':'false',A+'permission':'android.permission.BIND_SPEECH_RECOGNITION_SERVICE',A+'process':':speech',A+'foregroundServiceType':'microphone|mediaProcessing'})
    for permission in ('FOREGROUND_SERVICE_MICROPHONE','FOREGROUND_SERVICE_MEDIA_PROCESSING'):
        ET.SubElement(root,'uses-permission',{A+'name':'android.permission.'+permission})
    speech_filter=ET.SubElement(speech,'intent-filter');ET.SubElement(speech_filter,'action',{A+'name':'android.speech.RecognitionService'})
    ET.SubElement(root,'uses-permission',{A+'name':'android.permission.REQUEST_INSTALL_PACKAGES'})
    ET.SubElement(app,'activity',{A+'name':'os.aios.agent.AdbActivationActivity',A+'exported':'false',A+'resizeableActivity':'true'})
    ET.SubElement(app,'activity',{A+'name':'os.aios.agent.ApkSettingsActivity',A+'exported':'false'})
    ET.SubElement(app,'activity',{A+'name':'os.aios.agent.ApkPermissionsActivity',A+'exported':'false'})
    ET.SubElement(app,'meta-data',{A+'name':'os.aios.miniapp.DESKTOP_HOST',A+'value':'os.aios.miniapp.ApkDesktopHost'})
    ET.SubElement(app,'activity',{A+'name':'os.aios.miniapp.ApkMiniAppEntryActivity',A+'exported':'true',A+'process':':miniapps',A+'excludeFromRecents':'true',A+'noHistory':'true'})
    launcher=ET.SubElement(app,'activity',{A+'name':'os.aios.agent.ApkHomeActivity',A+'exported':'true',A+'launchMode':'singleTask'})
    intent=ET.SubElement(launcher,'intent-filter');ET.SubElement(intent,'action',{A+'name':'android.intent.action.MAIN'});ET.SubElement(intent,'category',{A+'name':'android.intent.category.LAUNCHER'})
    assistant=ET.SubElement(app,'activity',{A+'name':'os.aios.agent.ApkAssistantEntryActivity',A+'exported':'true',A+'label':'@string/apk_app_name',A+'theme':'@style/AIOSAssistantTheme',A+'excludeFromRecents':'true',A+'taskAffinity':PACKAGE+'.assistant.entry',A+'launchMode':'singleTask',A+'noHistory':'true'})
    assist=ET.SubElement(assistant,'intent-filter');ET.SubElement(assist,'action',{A+'name':'android.intent.action.ASSIST'});ET.SubElement(assist,'category',{A+'name':'android.intent.category.DEFAULT'})
    tile=ET.SubElement(app,'service',{A+'name':'os.aios.agent.ApkAssistantTileService',A+'exported':'true',A+'permission':'android.permission.BIND_QUICK_SETTINGS_TILE',A+'label':'@string/apk_assistant_tile',A+'icon':'@drawable/ic_apk_assistant_tile'})
    tile_filter=ET.SubElement(tile,'intent-filter');ET.SubElement(tile_filter,'action',{A+'name':'android.service.quicksettings.action.QS_TILE'})
    ET.SubElement(app,'provider',{A+'name':'os.aios.apk.ActivationProvider',A+'authorities':PACKAGE+'.activation',A+'exported':'true',A+'permission':'android.permission.DUMP'})
    return relocate(ET.tostring(root,encoding='unicode'))+'\n'

def stage(destination=DEST):
    destination=Path(destination).resolve()
    if destination!=DEST.resolve():raise ValueError('Only fixed APK staging path is allowed')
    destination.mkdir(parents=True,exist_ok=True)
    product=selection.ProductSelection(ROOT,ROOT/'android/host-apk/product.json')
    ledger={};written=set()
    def write(relative,data,source=None):
        target=destination/relative;target.parent.mkdir(parents=True,exist_ok=True)
        if isinstance(data,str):data=data.encode()
        if not target.exists() or target.read_bytes()!=data:target.write_bytes(data)
        written.add(relative);ledger[relative]={'sha256':hashlib.sha256(data).hexdigest(),'source':str(source.relative_to(ROOT)) if source else 'generated'}
    def source_file(source,module,relative):
        if module=='agent' and source.name=='OptionalAppsActivity.java':return
        data=product.transform(module,relative,source.read_bytes())
        if source.suffix=='.java':
            text=data.decode()
            match=re.search(r'package\s+([\w.]+)\s*;',text)
            if not match:raise ValueError('Missing package: '+str(source))
            text=re.sub(r'os\.aios\.(?:agent|core|runtime|codex|miniapp|workspace|automation|apk)\.R\b','os.aios.app.R',text)
            if 'import os.aios.app.R;' not in text:
                text=text[:match.end()]+'\nimport os.aios.app.R;\n'+text[match.end():]
            write('src/'+match.group(1).replace('.','/')+'/'+source.name,relocate(text),source)
        else:write('resources/'+module+'/'+str(relative)[4:],data,source)
    for module in ['core-runtime','core-services','capability-contracts','device-api','device-common','agent','codex-runtime','workspace','automation','miniapp','permission-anchor']:
        base=ROOT/'android'/module;allowed=None
        if module in ['workspace','automation']:allowed=set(re.findall(r'"(src/[^\"]+\.java)"',(base/'Android.bp').read_text()))
        for source in sorted(base.rglob('*.java')):
            rel=source.relative_to(base)
            if rel.parts[0] in ['third_party','tests','assets','workspace']:continue
            if allowed is not None and str(rel) not in allowed:continue
            if product.includes_source(module,rel):source_file(source,module,rel)
        if (base/'res').exists():
            for source in sorted((base/'res').rglob('*')):
                if source.is_file() and product.includes_source(module,source.relative_to(base)):source_file(source,module,source.relative_to(base))
    for name in ['SenseVoiceService.java','SpeechEndpoint.java','WhisperRecognizer.java']:
        source=ROOT/'android/speech/src'/name;source_file(source,'speech',Path('src')/name)
    for name in ['ggml-small-q5_1.bin','silero_vad.onnx','LICENSE']:
        source=ROOT/'android/speech/assets'/name;write('assets/'+('SPEECH_LICENSE.txt' if name=='LICENSE' else name),source.read_bytes(),source)
    source=ROOT/'android/speech/deps/classes.jar';write('deps/speech/classes.jar',source.read_bytes(),source)
    for name in ['libonnxruntime.so','libsherpa-onnx-jni.so','libsherpa-onnx-c-api.so','libsherpa-onnx-cxx-api.so']:
        source=ROOT/'android/speech/deps/lib/arm64-v8a'/name;write('native/'+name,source.read_bytes(),source)
    source=ROOT/'android/speech/lib/libaios_whisper.so';write('native/libaios_whisper.so',source.read_bytes(),source)
    host=ROOT/'android/host-rom';skip={'AiosApplication.java','CoreApplication.java','RomDeviceBindings.java','RomModelIdentity.java','HostEnvironment.java','RomUiHost.java','RomSelectionController.java'}
    for source in sorted(host.rglob('*.java')):
        if source.name not in skip:source_file(source,'host-rom',source.relative_to(host))
    overrides=ROOT/'android/host-apk'
    for source in sorted((overrides/'src').rglob('*.java')):source_file(source,'host-apk',source.relative_to(overrides))
    for source in sorted((overrides/'third_party').rglob('*.java')):
        source_file(source,'host-apk',source.relative_to(overrides))
    for source in sorted((overrides/'third_party').rglob('*')):
        if source.is_file() and source.suffix not in ('.java','.cpp'):write('assets/adb-licenses/'+str(source.relative_to(overrides/'third_party')),source.read_bytes(),source)
    for source in sorted((overrides/'native').glob('*')):
        if source.is_file():write('native/'+source.name,source.read_bytes(),source)
    write('native/spake2_jni.cpp',(overrides/'third_party/spake2/spake2_jni.cpp').read_bytes(),overrides/'third_party/spake2/spake2_jni.cpp')
    for source in sorted((overrides/'res').rglob('*')):
        if source.is_file():write('resources/host-apk/'+str(source.relative_to(overrides/'res')),source.read_bytes(),source)
    payload=json.dumps(product.manifest,ensure_ascii=True,separators=(',',':'))
    write('src/os/aios/host/ProductManifest.java','package os.aios.host; public final class ProductManifest { public static final String JSON='+json.dumps(payload)+'; }\n')
    write('AndroidManifest.xml',manifest(product))
    payload_record=next(item for item in json.loads((ROOT/'integration/lineage-24.0/assets.json').read_text())['files'] if item['destination'].endswith('/aios-codex-app-server'))
    payload_file=ROOT/payload_record['origin'].removeprefix('source:')
    if hashlib.sha256(payload_file.read_bytes()).hexdigest()!=payload_record['sha256']:raise ValueError('Codex payload provenance mismatch')
    for source,name in [(ROOT/'tools/codex-runtime/build/arm64-v8a/release/libcodex_app_server.so','libcodex_app_server.so'),(ROOT/'android/codex-runtime/native/libaios_node.so','libaios_node.so'),(ROOT/'android/codex-runtime/native/libaios_cli_launcher.so','libaios_cli_launcher.so')]:write('native/'+name,source.read_bytes(),source)
    for directory,target in [('android/codex-runtime/assets','assets'),('android/codex-runtime/workspace','assets/aios-workspace'),('android/miniapp/assets','assets'),('android/core-services/third_party/documents/licenses','assets/document-licenses')]:
        for source in sorted((ROOT/directory).rglob('*')):
            if source.is_file():write(target+'/'+str(source.relative_to(ROOT/directory)),source.read_bytes(),source)
    license_entry=next(item for item in json.loads((ROOT/'integration/lineage-24.0/assets.json').read_text())['files'] if item['destination'].endswith('/assets/CODEX_LICENSE.txt'))
    license_archive=ROOT/license_entry['origin'].removeprefix('source:')
    license_data=license_archive.read_bytes() if license_entry['kind']=='file' else zipfile.ZipFile(license_archive).read(license_entry['member'])
    if hashlib.sha256(license_data).hexdigest()!=license_entry['sha256']:raise ValueError('Codex license provenance mismatch')
    write('assets/CODEX_LICENSE.txt',license_data,license_archive)
    notice=ROOT/'android/codex-runtime/native/NOTICE';write('assets/NODE_NOTICE.txt',notice.read_bytes(),notice)
    # Carry reviewed attribution texts into the binary, not only the source tree.
    notices=json.loads((ROOT/'licenses/apk-notices.json').read_text())
    for entry in notices['files']:
        relative=Path(entry['path'])
        if relative.is_absolute() or '..' in relative.parts:
            raise ValueError('Invalid notice path')
        source=ROOT/'licenses/apk-notices'/relative
        data=source.read_bytes()
        if hashlib.sha256(data).hexdigest()!=entry['sha256']:
            raise ValueError('Notice provenance mismatch: '+str(relative))
        write('assets/third-party-notices/'+str(relative),data,source)

    for directory,target in [('android/core-runtime/third_party/commonmark','commonmark'),('android/core-services/third_party/documents','documents'),('android/miniapp/third_party','webkit')]:
        for source in sorted((ROOT/directory).glob('*')):
            if source.suffix in ['.jar','.aar'] or source.name=='LICENSE':write('deps/'+target+'/'+source.name,source.read_bytes(),source)
    for source in (overrides/'shell').rglob('*.java'):write('shell/'+str(source.relative_to(overrides/'shell')),source.read_bytes(),source)
    resource_dirs=['resources/'+module for module in ['core-runtime','workspace','automation','core-services','codex-runtime','miniapp','agent','host-apk'] if (destination/'resources'/module).exists()]
    bp='''android_app {
 name: "AIOSApk", srcs: ["src/**/*.java"], sdk_version: "35", min_sdk_version: "35", target_sdk_version: "35",
 manifest: "AndroidManifest.xml", resource_dirs: RESOURCES, asset_dirs: ["assets"],
 aaptflags: ["--auto-add-overlay"],
 static_libs: ["AIOSApkSpeech", "AIOSApkCommonmark", "AIOSApkPdfbox", "AIOSApkWebkit", "kotlin-stdlib", "androidx.work_work-runtime", "androidx.javascriptengine_javascriptengine"],
 jni_libs: ["libaios_apk_whisper", "libaios_apk_onnx", "libaios_apk_sherpa", "libaios_apk_sherpa_c", "libaios_apk_sherpa_cxx", "libaios_apk_codex", "libaios_apk_node", "libaios_apk_cli", "libaios_apk_spake2", "libaios_apk_touch"],
 use_embedded_native_libs: false, compile_multilib: "64", updatable: true,
 optimize: { enabled: false }, dex_preopt: { enabled: false }, certificate: "testkey",
}
java_import { name: "AIOSApkSpeech", jars: ["deps/speech/classes.jar"], sdk_version: "35", min_sdk_version: "35" }
java_import { name: "AIOSApkCommonmark", jars: ["deps/commonmark/*.jar"], sdk_version: "35", min_sdk_version: "35" }
java_import { name: "AIOSApkCrypto", jars: ["deps/documents/*.jar"], sdk_version: "35", min_sdk_version: "35" }
android_library_import { name: "AIOSApkPdfbox", aars: ["deps/documents/pdfbox-android-2.0.27.0.aar"], sdk_version: "35", static_libs: ["AIOSApkCrypto"] }
android_library_import { name: "AIOSApkWebkit", aars: ["deps/webkit/webkit-1.13.0.aar"], sdk_version: "35", min_sdk_version: "23", static_libs: ["androidx.annotation_annotation", "androidx.annotation_annotation-experimental", "androidx.core_core", "jspecify"] }
java_library { name: "AIOSApkShell", srcs: ["shell/**/*.java"], installable: true, compile_dex: true, min_sdk_version: "35", libs: ["framework"], optimize: { enabled: false }, dex_preopt: { enabled: false } }
'''.replace('RESOURCES',json.dumps(resource_dirs))
    for module,filename in [('libaios_apk_whisper','libaios_whisper.so'),('libaios_apk_onnx','libonnxruntime.so'),('libaios_apk_sherpa','libsherpa-onnx-jni.so'),('libaios_apk_sherpa_c','libsherpa-onnx-c-api.so'),('libaios_apk_sherpa_cxx','libsherpa-onnx-cxx-api.so'),('libaios_apk_codex','libcodex_app_server.so'),('libaios_apk_node','libaios_node.so'),('libaios_apk_cli','libaios_cli_launcher.so')]:
        bp+='cc_prebuilt_library_shared { name: "'+module+'", stem: "'+filename[:-3]+'", srcs: ["native/'+filename+'"], sdk_version: "35", min_sdk_version: "35", compile_multilib: "64", stl: "none", check_elf_files: false, strip: { none: true } }\n'
    bp+='cc_library_shared { name: "libaios_apk_spake2", stem: "libspake2", srcs: ["native/spake2_jni.cpp"], static_libs: ["libcrypto_static"], header_libs: ["jni_headers"], sdk_version: "35", min_sdk_version: "35", compile_multilib: "64", stl: "libc++_static", cflags: ["-Wno-writable-strings", "-Wno-unused-parameter"] }\n'
    bp+='cc_library_shared { name: "libaios_apk_touch", stem: "libaios_touch", srcs: ["native/touch_monitor.cpp"], header_libs: ["jni_headers"], sdk_version: "35", min_sdk_version: "35", compile_multilib: "64", stl: "libc++_static", cflags: ["-Wall", "-Wextra", "-Werror"] }\n'
    write('Android.bp',bp)
    previous=destination/'assembly.json'
    if previous.exists():
        for name in json.loads(previous.read_text()).get('files',{}):
            if name not in written:
                target=(destination/name).resolve()
                if not target.is_relative_to(destination):raise ValueError('Invalid previous manifest path')
                if target.is_file():target.unlink()
    previous.write_text(json.dumps({'product':product.manifest,'files':ledger},indent=2)+'\n')
    return {'files':len(written),'destination':str(destination),'manifest_hash':product.manifest['manifest_hash']}

if __name__=='__main__':print(json.dumps(stage(),indent=2))
