"""Offline fixtures for the later unified acceptance batch. Never touches real ROM/out."""
import importlib.util
import json
from pathlib import Path
import sys
import subprocess
import tempfile
import unittest
from unittest.mock import patch

TOOLS=Path(__file__).resolve().parents[1]/'tools/lineage'
sys.path.insert(0,str(TOOLS))
import target_config as config
spec=importlib.util.spec_from_file_location('aios_integration',TOOLS/'integrate.py')
integration=importlib.util.module_from_spec(spec)
spec.loader.exec_module(integration)


class TargetTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.base=json.loads((TOOLS/'targets/frankel.json').read_text())
        self.override=patch.object(config,'CONFIGS',self.root);self.override.start()
        self.addCleanup(self.override.stop);self.addCleanup(self.temp.cleanup)

    def load(self,data=None,name='frankel'):
        (self.root/(name+'.json')).write_text(json.dumps(data or self.base))
        return config.load_target(name)

    def test_pixel_default_contract(self):
        target=self.load();env=target.environment()
        self.assertEqual((env['TARGET_PRODUCT'],env['OUT_DIR'],env['TARGET_BUILD_VARIANT']),
                         ('lineage_frankel','out','userdebug'))

    def test_new_device_uses_fixed_separate_output(self):
        data=dict(self.base,device='fixture',product='lineage_fixture',out_dir='out-aios-fixture',
                  device_integrations=[],legacy_policy=False)
        self.assertEqual(self.load(data,'fixture').environment()['OUT_DIR'],'out-aios-fixture')

    def test_reject_wrong_arch_branch_path_and_pixel_leak(self):
        for key,value in [('arch','x86_64'),('branch','lineage-23.0'),('board_config','../escape'),
                          ('out_dir','out-today'),('device_integrations',['unknown'])]:
            with self.subTest(key=key),self.assertRaises(ValueError):self.load(dict(self.base,**{key:value}))
        with self.assertRaises(ValueError):
            self.load(dict(self.base,device='fixture',product='lineage_fixture',out_dir='out-aios-fixture'),'fixture')

    def test_unknown_and_path_target_rejected(self):
        for name in ('missing','../frankel'):
            with self.assertRaises(ValueError):config.load_target(name)

    def test_build_rejects_pending_or_wrong_target(self):
        target=self.load();path=self.root/'vendor/aios/integration-state.json';path.parent.mkdir(parents=True)
        with patch.object(config,'ROOT',self.root):
            for status,name in [('applying','frankel'),('staged_not_built','fixture')]:
                path.write_text(json.dumps(dict(status=status,target=name,config_sha256=target.fingerprint)))
                with self.assertRaises(ValueError):target.assert_staged()

    def test_build_rejects_wrong_output_arch(self):
        target=self.load();path=self.root/'vendor/aios/integration-state.json';path.parent.mkdir(parents=True)
        path.write_text(json.dumps(dict(status='staged_not_built',target='frankel',config_sha256=target.fingerprint)))
        variables=self.root/'out/soong/soong.lineage_frankel.variables';variables.parent.mkdir(parents=True)
        variables.write_text(json.dumps(dict(DeviceName='frankel',DeviceArch='x86_64')))
        with patch.object(config,'ROOT',self.root),self.assertRaises(ValueError):target.assert_staged()


class PlanTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def test_conflict_does_not_write_prior_entries(self):
        (self.root/'existing').write_bytes(b'user edit')
        plan=integration.Plan(self.root,{},{});plan.put('new',data=b'new')
        with self.assertRaises(ValueError):plan.put('existing',data=b'replacement')
        self.assertFalse((self.root/'new').exists())
        self.assertEqual((self.root/'existing').read_bytes(),b'user edit')

    def test_apply_and_repeat_are_idempotent(self):
        plan=integration.Plan(self.root,{},{});plan.put('file',data=b'one');plan.apply()
        again=integration.Plan(self.root,{},plan.receipt());again.put('file',data=b'one')
        self.assertEqual(again.items['file']['before'],again.items['file']['after']);again.apply()

    def test_known_source_upgrade_and_unknown_edit(self):
        (self.root/'file').write_bytes(b'old');receipt={'file':integration.sha(b'old')}
        plan=integration.Plan(self.root,{},receipt);plan.put('file',data=b'new');plan.apply()
        (self.root/'file').write_bytes(b'local')
        with self.assertRaises(ValueError):integration.Plan(self.root,{},receipt).put('file',data=b'new')

    def test_edit_between_plan_and_apply_prevents_all_writes(self):
        plan=integration.Plan(self.root,{},{});plan.put('first',data=b'a');plan.put('second',data=b'b')
        (self.root/'second').write_bytes(b'user')
        with self.assertRaises(ValueError):plan.apply()
        self.assertFalse((self.root/'first').exists())

    def test_only_known_tombstone_removed(self):
        path=self.root/'old';path.write_bytes(b'old')
        plan=integration.Plan(self.root,{'old':integration.sha(b'old')},{})
        plan.put('old');plan.apply();self.assertFalse(path.exists())
        path.write_bytes(b'new user source')
        with self.assertRaises(ValueError):plan.put('old')

    def test_traversal_and_symlink_rejected(self):
        plan=integration.Plan(self.root,{}, {})
        with self.assertRaises(ValueError):plan.put('../outside',data=b'x')
        (self.root/'real').mkdir();(self.root/'alias').symlink_to(self.root/'real',target_is_directory=True)
        with self.assertRaises(ValueError):plan.put('alias/file',data=b'x')

    def test_patch_materialized_without_modifying_checkout(self):
        checkout=self.root/'checkout';checkout.mkdir()
        def git(*args):
            return subprocess.check_output(['git',*args],cwd=checkout,stderr=subprocess.DEVNULL)
        git('init');git('config','user.name','Fixture');git('config','user.email','fixture@example.invalid')
        (checkout/'file').write_bytes(b'old\n');git('add','file');git('commit','-m','fixture')
        revision=git('rev-parse','HEAD').decode().strip()
        (checkout/'file').write_bytes(b'new\n');delta=git('diff','HEAD')
        (checkout/'file').write_bytes(b'old\n')
        bundle=self.root/'bundle';bundle.mkdir();(bundle/'change.patch').write_bytes(delta)
        entry=dict(kind='patch',path='file',before_sha256=integration.sha(b'old\n'),
                   after_sha256=integration.sha(b'new\n'))
        repo=dict(review_patch='change.patch',patch_sha256=integration.sha(delta),revision=revision,files=[entry])
        with patch.object(integration,'BUNDLE',bundle):
            self.assertEqual(integration.materialize(repo,checkout)['file'],b'new\n')
            self.assertEqual((checkout/'file').read_bytes(),b'old\n')
            entry['after_sha256']='0'*64
            with self.assertRaises(ValueError):integration.materialize(repo,checkout)


if __name__=='__main__':unittest.main()
