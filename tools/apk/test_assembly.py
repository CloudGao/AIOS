#!/usr/bin/env python3
"""Offline assembly/identity regressions. These do not claim Android runtime acceptance."""
from pathlib import Path
import json, re, sys, unittest
import xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parent))
import stage

class AssemblyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.product=stage.selection.ProductSelection(stage.ROOT,stage.ROOT/'android/host-apk/product.json')
        cls.manifest=ET.fromstring(stage.manifest(cls.product))
    def test_miniapp_shortcut_boundary(self):
        activities={n.get(stage.A+'name'):n for n in self.manifest.iter('activity')}
        self.assertEqual(activities['os.aios.miniapp.ApkMiniAppEntryActivity'].get(stage.A+'exported'),'true')
        for name in ['HostActivity','LibraryActivity','ManageActivity']:
            self.assertEqual(activities['os.aios.miniapp.'+name].get(stage.A+'exported'),'false')
        meta={n.get(stage.A+'name'):n.get(stage.A+'value') for n in self.manifest.iter('meta-data')}
        self.assertEqual(meta['os.aios.miniapp.DESKTOP_HOST'],'os.aios.miniapp.ApkDesktopHost')
    def test_optional_choices_follow_host_route(self):
        names={n.get(stage.A+'name') for n in self.manifest.iter('activity')}
        self.assertIn('os.aios.agent.SpeechModelSelectionActivity',names)
        for name in ['os.aios.agent.OptionalAppsActivity','os.aios.core.embedding.PhotoModelSelectionActivity','os.aios.core.embedding.FaceModelActivity','os.aios.core.inference.LocalTextModelActivity','os.aios.speech.SpeakerModelActivity']:
            self.assertNotIn(name,names)
        self.assertFalse((stage.DEST/'src/os/aios/agent/OptionalAppsActivity.java').exists())
        rom=ET.parse(stage.ROOT/'android/agent/AndroidManifest.xml')
        self.assertIn('.OptionalAppsActivity',{n.get(stage.A+'name') for n in rom.iter('activity')})
        shared=(stage.ROOT/'android/agent/MainActivity.java').read_text()
        self.assertNotIn('OptionalAppsActivity.class',shared)
        self.assertIn('addOptionalAppsSettings',shared)
        apk=(stage.ROOT/'android/host-apk/src/os/aios/agent/ApkSettingsActivity.java').read_text()
        self.assertNotIn('OptionalAppsActivity',apk)
    def test_one_launcher_one_package(self):
        self.assertEqual(self.manifest.get('package'),'os.aios.app')
        launchers=[n for n in self.manifest.iter('category') if n.get(stage.A+'name')=='android.intent.category.LAUNCHER']
        self.assertEqual(len(launchers),1)
    def test_public_assist_entry_is_isolated_from_internal_activity(self):
        activities={n.get(stage.A+'name'):n for n in self.manifest.iter('activity')}
        entry=activities['os.aios.agent.ApkAssistantEntryActivity']
        self.assertEqual(entry.get(stage.A+'exported'),'true')
        self.assertEqual(entry.get(stage.A+'taskAffinity'),'os.aios.app.assistant.entry')
        self.assertEqual(entry.get(stage.A+'launchMode'),'singleTask')
        self.assertEqual(entry.get(stage.A+'noHistory'),'true')
        self.assertIn('android.intent.action.ASSIST',[n.get(stage.A+'name') for n in entry.iter('action')])
        self.assertEqual(activities['os.aios.agent.AssistantActivity'].get(stage.A+'exported'),'false')
        source=(stage.ROOT/'android/host-apk/src/os/aios/agent/ApkAssistantEntryActivity.java').read_text()
        self.assertNotIn('getIntent()',source)
    def test_quick_settings_service_requires_system_binding(self):
        services={n.get(stage.A+'name'):n for n in self.manifest.iter('service')}
        tile=services['os.aios.agent.ApkAssistantTileService']
        self.assertEqual(tile.get(stage.A+'permission'),'android.permission.BIND_QUICK_SETTINGS_TILE')
        self.assertEqual(tile.get(stage.A+'exported'),'true')
        self.assertIn('android.service.quicksettings.action.QS_TILE',[n.get(stage.A+'name') for n in tile.iter('action')])
    def test_removed_features_do_not_disable_notification_events(self):
        for feature in ['core','harness','notification-events','virtual-screen','miniapps']:self.assertTrue(self.product.has(feature))
        for feature in ['notification-governance','minutes','recording']:self.assertFalse(self.product.has(feature))
        names={n.get(stage.A+'name') for n in self.manifest.iter()}
        self.assertIn('os.aios.agent.PhoneNotifications',names)
        self.assertNotIn('os.aios.agent.NotificationGovernanceService',names)
        self.assertNotIn('os.aios.agent.NotificationManagementActivity',names)
    def test_providers_isolated_from_rom_and_external_apps(self):
        authorities=[]
        for node in self.manifest.iter('provider'):
            authority=node.get(stage.A+'authorities');authorities.append(authority)
            self.assertTrue(authority.startswith('os.aios.app.'))
            if authority.endswith('.activation'):
                self.assertEqual(node.get(stage.A+'permission'),'android.permission.DUMP')
            else:self.assertEqual(node.get(stage.A+'exported'),'false')
        self.assertEqual(len(authorities),len(set(authorities)))
    def test_class_names_not_relocated(self):
        original='new ComponentName("os.aios.agent", "os.aios.agent.AgentService");'
        self.assertEqual(stage.relocate(original),'new ComponentName("os.aios.app", "os.aios.agent.AgentService");')
        self.assertEqual(stage.relocate('package os.aios.core;'),'package os.aios.core;')
    def test_identity_relocation_covers_content_uri_paths(self):
        for old,new in stage.IDENTITIES.items():self.assertEqual(stage.relocate('"content://'+old+'/data/"'),'"content://'+new+'/data/"')
    def test_no_system_signature_permissions_requested(self):
        requested={n.get(stage.A+'name') for n in self.manifest.iter('uses-permission')}
        for permission in ['ACCESS_COMPUTER_CONTROL','INSTALL_PACKAGES','FORCE_STOP_PACKAGES','REBOOT']:
            self.assertNotIn('android.permission.'+permission,requested)
    def test_localizations_and_placeholders(self):
        maps=[]
        for locale in ['values','values-zh-rCN','values-zh-rTW']:
            tree=ET.parse(stage.ROOT/'android/host-apk/res'/locale/'apk.xml')
            maps.append({n.get('name'):n.text for n in tree.getroot()})
        self.assertEqual(set(maps[0]),set(maps[1]));self.assertEqual(set(maps[0]),set(maps[2]))
        for key in maps[0]:
            self.assertTrue(all(m[key] for m in maps))
            self.assertEqual(re.findall(r'%\d*\$?[sd]',maps[0][key]),re.findall(r'%\d*\$?[sd]',maps[1][key]))
    def test_core_and_workspace_sources_are_present(self):
        base=stage.DEST/'src'
        for name in ['os/aios/core/workspace/WorkspaceStore.java','os/aios/workspace/WorkspaceActivity.java','os/aios/codex/RuntimeService.java','os/aios/agent/AssistantCodexBridge.java','os/aios/agent/PhoneNotifications.java','os/aios/miniapp/HostActivity.java']:
            self.assertTrue((base/name).is_file(),name)
    def test_excluded_implementations_not_staged(self):
        names={p.name for p in (stage.DEST/'src').rglob('*.java')}
        for name in ['NotificationGovernanceService.java','MinutesCodexBridge.java','RecorderServices.java','RomScreenBackend.java','RomDeviceBindings.java']:self.assertNotIn(name,names)
    def test_speech_fallback_packaging_and_isolation(self):
        services={n.get(stage.A+'name'):n for n in self.manifest.iter('service')}
        speech=services['os.aios.speech.SenseVoiceService']
        self.assertEqual(speech.get(stage.A+'exported'),'false')
        self.assertEqual(speech.get(stage.A+'permission'),'android.permission.BIND_SPEECH_RECOGNITION_SERVICE')
        self.assertEqual(speech.get(stage.A+'process'),':speech')
        self.assertTrue((stage.DEST/'assets/ggml-small-q5_1.bin').is_file())
        self.assertTrue((stage.DEST/'deps/speech/classes.jar').is_file())
        for name in ['speaker.onnx','en.wav','zh.wav','model.int8.onnx','tokens.txt']:self.assertFalse((stage.DEST/'assets'/name).exists())
        source=(stage.DEST/'src/os/aios/speech/SpeechCapture.java').read_text()
        self.assertNotIn('.setContext(',source)
        names={p.name for p in (stage.DEST/'src/os/aios/speech').glob('*.java')}
        self.assertEqual(names,{'SenseVoiceService.java','WhisperRecognizer.java','SpeechEndpoint.java','SpeechCapture.java'})
    def test_reviewed_notices_are_in_apk_assets(self):
        import hashlib
        manifest=json.loads((stage.ROOT/'licenses/apk-notices.json').read_text())
        self.assertGreater(len(manifest['files']),10)
        for entry in manifest['files']:
            path=stage.DEST/'assets/third-party-notices'/entry['path']
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(),entry['sha256'])

    def test_assembly_hashes_and_workspace_payload(self):
        assembly=json.loads((stage.DEST/'assembly.json').read_text())
        import hashlib
        for name,record in assembly['files'].items():self.assertEqual(hashlib.sha256((stage.DEST/name).read_bytes()).hexdigest(),record['sha256'],name)
        self.assertTrue((stage.DEST/'assets/aios-workspace/AGENTS.md').is_file())

if __name__=='__main__':unittest.main(verbosity=2)
