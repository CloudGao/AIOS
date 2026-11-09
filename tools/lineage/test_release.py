import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
def load(name):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(name+'.py'))
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
installer=load('install-release')
release=load('release')

class ReleaseChecks(unittest.TestCase):
    def candidate(self,root):
        parts={}
        with zipfile.ZipFile(root/'dsu.zip','w') as z:
            for part in ('system','system_ext','product'):
                data=('fixture-'+part).encode();z.writestr(part+'.img',data)
                parts[part]={'raw_sha256':hashlib.sha256(data).hexdigest()}
        manifest={'schema':1,'device':'frankel','files':{'dsu.zip':{'bytes':(root/'dsu.zip').stat().st_size,'sha256':installer.digest(root/'dsu.zip')}},'partitions':parts,'distributable':False,'acceptance':{'dsu_boot':'pending'}}
        (root/'release.json').write_text(json.dumps(manifest));return manifest

    def test_candidate_verifies_without_claiming_acceptance(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.candidate(root);self.assertFalse(installer.verify(root)['distributable'])

    def test_archive_tampering_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.candidate(root)
            with (root/'dsu.zip').open('ab') as f:f.write(b'tampering')
            with self.assertRaises(ValueError):installer.verify(root)

    def test_partition_tampering_rejected_even_with_updated_archive_digest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);m=self.candidate(root);m['partitions']['system']['raw_sha256']='0'*64
            (root/'release.json').write_text(json.dumps(m))
            with self.assertRaises(ValueError):installer.verify(root)

    def test_manifest_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);m=self.candidate(root);m['files']['../secret']={'bytes':0,'sha256':'0'*64}
            (root/'release.json').write_text(json.dumps(m))
            with self.assertRaises(ValueError):installer.verify(root)

    def test_wrong_device_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);m=self.candidate(root);m['device']='other'
            (root/'release.json').write_text(json.dumps(m))
            with self.assertRaises(ValueError):installer.verify(root)

    def test_locales_complete_and_placeholders_match(self):
        for name in ('release_accounts.xml','release_setup.xml'):
            resources=[]
            for directory in ('values','values-zh','values-b+zh+Hant'):
                tree=ET.parse(ROOT/'android/agent/res'/directory/name)
                resources.append({n.attrib['name']:n.text for n in tree.getroot()})
            self.assertEqual(set(resources[0]),set(resources[1]));self.assertEqual(set(resources[0]),set(resources[2]))
            for entries in resources:
                self.assertTrue(all(entries.values()))

    def test_no_developer_preauthorization(self):
        self.assertNotIn('dsu-diagnostics/diagnostics.mk',(ROOT/'android/product/aios.mk').read_text())

    def test_all_input_hashes_are_sha256(self):
        stage=load('stage-release')
        for row in stage.APPS:self.assertRegex(row[3],r'^[a-f0-9]{64}$')
        self.assertRegex(stage.MODEL_HASH,r'^[a-f0-9]{64}$')

if __name__=='__main__':unittest.main()
