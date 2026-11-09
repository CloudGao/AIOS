import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('optional',ROOT/'tools/lineage/optional_dependencies.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class OptionalDependencyTest(unittest.TestCase):
    def setUp(self):self.config=module.OptionalDependencies(ROOT)
    def test_default_excludes_optional_payloads_and_legacy_models(self):
        for path in ['android/speech/assets/model.int8.onnx','android/speech/assets/tokens.txt','vendor/aios/prebuilts/AIOSChromium/app.apk','vendor/aios/prebuilts/AIOSAppManager/app.apk','android/text-models/LFM2.5-350M-Q4_K_M.gguf','android/speech/assets/speaker.onnx','vendor/aios/search-models/face.tflite','vendor/aios/tts/kokoro-arm64.apk']:
            self.assertFalse(self.config.includes_path(path),path)
        for path in ['android/speech/assets/ggml-small-q5_1.bin','android/speech/assets/silero_vad.onnx','vendor/aios/search-models/image.tflite','vendor/aios/search-models/text.tflite','vendor/aios/search-models/manifest.json']:
            self.assertTrue(self.config.includes_path(path),path)
    def test_generated_build_rules_do_not_reference_disabled_inputs(self):
        for owner,name,forbidden in [('core-services','Android.bp','"aios-local-text-model"'),('search-models','models.mk','/face.tflite:'),('prebuilts','Android.bp','android_app_import'),('prebuilts','prebuilts.mk','AIOSChromium')]:
            result=self.config.transform(owner,name,(ROOT/'android'/owner/name).read_bytes()).decode()
            self.assertNotIn(forbidden,result)
        self.assertNotIn(b'AIOSKokoroTts',self.config.product_makefile((ROOT/'android/product/aios.mk').read_bytes()))
    def test_selective_opt_in_and_rejection(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'selection.json'
            data={'schema':1,'bundled_models':['speaker'],'bundled_apps':['AIOSPdfReader']};path.write_text(json.dumps(data))
            config=module.OptionalDependencies(ROOT,path)
            self.assertTrue(config.includes_path('android/speech/assets/speaker.onnx'))
            text=config.transform('prebuilts','Android.bp',(ROOT/'android/prebuilts/Android.bp').read_bytes()).decode()
            self.assertIn('name: "AIOSPdfReader"',text);self.assertIn('AIOSPdfReader_libjniPdfium',text)
            self.assertNotIn('AIOSChromium',text);self.assertNotIn('AIOSAppManager',text)
            self.assertNotEqual(config.fingerprint,self.config.fingerprint)
            for invalid in [['unknown'],['face','face'],[True],'face']:
                data['bundled_models']=invalid;path.write_text(json.dumps(data))
                with self.assertRaises(ValueError):module.OptionalDependencies(ROOT,path)
    def test_offline_assets_are_filtered_before_source_origins(self):
        assets=json.loads((ROOT/'integration/lineage-24.0/assets.json').read_text())
        chosen=[x for x in assets['files'] if self.config.includes_path(x['destination'])]
        self.assertTrue(chosen);self.assertLess(len(chosen),len(assets['files']))
        self.assertTrue(any(x['destination'].endswith('/image.tflite') for x in chosen))
        self.assertFalse(any('/prebuilts/' in x['destination'] or '/tts/' in x['destination'] or x['destination'].endswith('/face.tflite') for x in chosen))
    def test_localization_keys_and_placeholders(self):
        import re
        for owner,filename in [('core-runtime','optional_models.xml'),('agent','optional_apps.xml')]:
            parsed=[]
            for folder in ['values','values-zh-rCN','values-zh-rTW','values-zh','values-b+zh+Hant']:
                values={n.attrib['name']:''.join(n.itertext()) for n in ET.parse(ROOT/'android'/owner/'res'/folder/filename).getroot()}
                parsed.append(values)
            for other in parsed[1:]:
                self.assertEqual(parsed[0].keys(),other.keys())
                for key in other:self.assertEqual(re.findall(r'%\d+\$[ds]',parsed[0][key]),re.findall(r'%\d+\$[ds]',other[key]))
    def test_new_choice_resources(self):
        keys=[]
        for folder in ['values','values-zh-rCN','values-zh-rTW','values-zh','values-b+zh+Hant']:
            tree=ET.parse(ROOT/'android/core-runtime/res'/folder/'model_choices.xml')
            keys.append({node.get('name') for node in tree.getroot()})
        self.assertTrue(keys[0]);self.assertTrue(all(x==keys[0] for x in keys))

    def test_release_rejects_disabled_residue_and_config_drift(self):
        import io,sys,zipfile
        from unittest.mock import patch
        sys.path.insert(0,str(ROOT/'tools/lineage'))
        release_spec=importlib.util.spec_from_file_location('optional_release',ROOT/'tools/lineage/release.py')
        release=importlib.util.module_from_spec(release_spec);release_spec.loader.exec_module(release)
        class Report:stdout="package: name='os.aios.fixture'"
        with tempfile.TemporaryDirectory() as folder:
            directory=Path(folder);release.STATE=directory
            for residue in [None,'face','lfm','speaker','app','native','config','notice']:
                archive=directory/((residue or 'clean')+'.zip')
                with zipfile.ZipFile(archive,'w') as z:
                    z.writestr('SYSTEM/build.prop','ro.build.type=userdebug\nro.adb.secure=1\n')
                    z.writestr('META/misc_info.txt','dynamic_partition_list=system system_ext product\n')
                    config=self.config.data if residue!='config' else {'schema':1,'bundled_models':['face'],'bundled_apps':[]}
                    z.writestr('SYSTEM_EXT/etc/aios/optional-dependencies.json',json.dumps(config))
                    z.writestr('SYSTEM_EXT/etc/aios/licenses/index.json',(ROOT/'licenses/apk-notices.json').read_bytes())
                    for entry in json.loads((ROOT/'licenses/apk-notices.json').read_text())['files']:
                        data=(ROOT/'licenses/apk-notices'/entry['path']).read_bytes()
                        z.writestr('SYSTEM_EXT/etc/aios/licenses/'+entry['sha256']+'.txt',b'tampered' if residue=='notice' else data)
                    for app in release.REQUIRED:
                        data=io.BytesIO()
                        with zipfile.ZipFile(data,'w') as apk:
                            apk.writestr('AndroidManifest.xml','fixture')
                            if app=='AIOSSpeech' and residue=='speaker':apk.writestr('assets/speaker.onnx','residue')
                        z.writestr('SYSTEM_EXT/app/'+app+'/'+app+'.apk',data.getvalue())
                    for name in ['manifest.json','tokenizer.json','image.tflite','text.tflite','LICENSE']:
                        z.writestr('SYSTEM_EXT/etc/aios/search-models/'+name,'fixture')
                    if residue=='face':z.writestr('SYSTEM_EXT/etc/aios/search-models/face.tflite','residue')
                    if residue=='lfm':z.writestr('SYSTEM_EXT/etc/aios/text-models/LFM2.5-350M-Q4_K_M.gguf','residue')
                    if residue=='native':z.writestr('SYSTEM_EXT/lib64/AIOSCollabora/libandroidapp.so','residue')
                    if residue=='app':z.writestr('SYSTEM_EXT/app/AIOSChromium/AIOSChromium.apk','residue')
                with patch.object(release,'run',return_value=Report()):
                    if residue is None:self.assertEqual(release.audit_target(archive)['variant'],'userdebug')
                    else:
                        with self.assertRaises(ValueError,msg=residue):release.audit_target(archive)

    def test_missing_models_keep_setup_accessible_and_imports_are_recognized(self):
        page=(ROOT/'android/agent/ModelManagementActivity.java').read_text()
        setup=page[page.index('LinearLayout optional='):page.index('if(optional.getChildCount()')]
        self.assertNotIn('SharedModelCatalog.ready',setup)
        for owner in ('local-inference','photo-index','minutes'):
            self.assertIn('HostProduct.includes("'+owner+'")',setup)
        probe=(ROOT/'android/core-services/src/os/aios/core/CoreCapabilities.java').read_text()
        self.assertIn('OptionalModelStore.file(context, "lfm").isFile()',probe)
        self.assertIn('|| HostBindings.bundledTextModel().isFile()',probe)

    def test_owner_manifest_permissions(self):
        a='{http://schemas.android.com/apk/res/android}'
        for owner,classes,permission in [('core-services',['os.aios.core.inference.LocalTextModelActivity','os.aios.core.embedding.FaceModelActivity'],'CORE_RUNTIME'),('speech',['.SpeakerModelActivity'],'VOICE_MINUTES')]:
            tree=ET.parse(ROOT/'android'/owner/'AndroidManifest.xml')
            for name in classes:
                item=next(n for n in tree.findall('./application/activity') if n.get(a+'name')==name)
                self.assertEqual(item.get(a+'permission'),'os.aios.permission.'+permission)
                self.assertEqual(item.get(a+'exported'),'true')

if __name__=='__main__':unittest.main()
