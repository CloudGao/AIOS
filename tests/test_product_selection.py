"""Prepared for unified acceptance; no device access."""
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


selection_module = load('product_selection', ROOT / 'tools/lineage/product_selection.py')
generator = load('product_generator', ROOT / 'android/host-rom/generate_product.py')


class ProductSelectionTest(unittest.TestCase):
    def setUp(self):
        self.selection = selection_module.ProductSelection(ROOT)

    def test_fixed_core_and_dependencies(self):
        catalog = json.loads((ROOT / 'android/host-rom/modules.json').read_text())
        product = json.loads((ROOT / 'android/host-rom/product.json').read_text())
        product['modules'].remove('core')
        with self.assertRaises(ValueError):
            generator.resolve(catalog, product)
        product['modules'].append('core')
        product['modules'].remove('photos')
        with self.assertRaisesRegex(ValueError, 'missing_dependency'):
            generator.resolve(catalog, product)

    def test_optional_core_has_no_factory_references(self):
        self.selection.selected -= {'photo-index','documents','local-inference'}
        source = ROOT / 'android/host-rom/core/os/aios/core/CoreFeatureBindings.java'
        result = self.selection.transform('host-rom',source.relative_to(ROOT/'android/host-rom'),source.read_bytes()).decode()
        for symbol in ('DocumentCoordinator','LocalTextInference','IndexedPhotoSearch','BundledModels','ModelRegistry'):
            self.assertNotIn(symbol,result)
        self.assertFalse(self.selection.includes_source('core-services','src/os/aios/core/indexing/PhotoIndexStore.java'))
        self.assertFalse(self.selection.includes_source('core-services','src/os/aios/core/documents/DocumentCoordinator.java'))
        self.assertTrue(self.selection.includes_source('core-services','src/os/aios/core/search/SearchCoordinator.java'))
        self.assertTrue(self.selection.includes_source('core-services','src/os/aios/core/workspace/WorkspaceStore.java'))
        self.assertTrue(self.selection.includes_source('core-services','src/os/aios/core/automation/AutomationService.java'))

    def test_optional_components_and_packaged_libraries_removed(self):
        self.selection.selected -= {'photo-index','documents','local-inference'}
        root = ROOT / 'android/core-services'
        manifest = self.selection.transform('core-services','AndroidManifest.xml',(root/'AndroidManifest.xml').read_bytes()).decode()
        for symbol in ('DocumentWorkerService','EmbeddingService','DeviceModelValidation','networkSecurityConfig'):
            self.assertNotIn(symbol,manifest)
        self.assertIn('CoreRuntimeProvider',manifest)
        bp = self.selection.transform('core-services','Android.bp',(root/'Android.bp').read_bytes()).decode()
        app = bp.split('prebuilt_etc {',1)[0]
        for symbol in ('AIOSDocumentPdfBox','AIOSLiteRtOfflineApi','libLiteRt','libaios_search','libaios_llama_server','aios-local-text-model'):
            self.assertNotIn(symbol,app)

    def test_full_product_preserves_components(self):
        root = ROOT / 'android/core-services'
        for name in ('Android.bp','AndroidManifest.xml'):
            original = (root/name).read_bytes()
            self.assertEqual(original,self.selection.transform('core-services',name,original))

    def test_common_device_contributions_are_physically_excluded(self):
        features={
            'streams':('Streams.java',),
            'recording':('RecorderServices.java',),
            'files':('FileOperations.java','FileTransfers.java'),
            'photos':('PhotoOperations.java',),
        }
        root=ROOT/'android/host-rom'
        path='agent/os/aios/agent/DeviceContributions.java'
        for owner,names in features.items():
            with self.subTest(owner=owner):
                selection=selection_module.ProductSelection(ROOT)
                selection.selected.remove(owner)
                output=selection.transform('host-rom',path,(root/path).read_bytes()).decode()
                for name in names:
                    self.assertFalse(selection.includes_source('device-common','src/os/aios/agent/'+name))
                    self.assertNotIn(name.removesuffix('.java'),output)
                # Storage policy is also needed by recording and package inspection, independently of file tools.
                self.assertTrue(selection.includes_source('device-common','src/os/aios/agent/SharedFiles.java'))

    def test_device_common_does_not_call_agent_implementation(self):
        root=ROOT/'android/device-common/src'
        for source in root.rglob('*.java'):
            text=source.read_text()
            with self.subTest(source=source.name):
                for implementation in ('AgentService.','EventStore.','UserRequest.','RomPlatformServices','os.aios.core.'):
                    self.assertNotIn(implementation,text)

    def test_document_tool_is_an_optional_contribution(self):
        self.selection.selected.remove('documents')
        self.assertFalse(self.selection.includes_source('agent','DocumentTools.java'))
        root=ROOT/'android/host-rom'
        path='agent/os/aios/agent/AgentOperationContributions.java'
        output=self.selection.transform('host-rom',path,(root/path).read_bytes()).decode()
        self.assertNotIn('DocumentTools',output)
        self.assertNotIn('DocumentTools',(ROOT/'android/agent/PlatformServices.java').read_text())

    def test_notification_events_survive_without_governance(self):
        self.selection.selected.remove('notification-governance')
        self.assertTrue(self.selection.includes_source('agent','PhoneNotifications.java'))
        self.assertTrue(self.selection.includes_source('agent','NotificationPolicyWorker.java'))
        self.assertFalse(self.selection.includes_source('agent','NotificationPolicyImplementation.java'))
        self.assertFalse(self.selection.includes_source('agent','res/values/notification_management.xml'))
        root=ROOT/'android/agent'
        manifest=self.selection.transform('agent','AndroidManifest.xml',(root/'AndroidManifest.xml').read_bytes()).decode()
        self.assertIn('PhoneNotifications',manifest)
        self.assertNotIn('NotificationGovernanceService',manifest)
        bindings=self.selection.transform('host-rom','agent/os/aios/agent/AgentFeatureInstaller.java',b'').decode()
        self.assertNotIn('NotificationPolicyImplementation',bindings)
        self.assertNotIn('NotificationGovernanceHooks',bindings)
        self.assertIn('not_packaged',bindings)


if __name__ == '__main__':
    unittest.main()
