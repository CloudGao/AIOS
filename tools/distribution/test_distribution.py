"""Host-only acceptance of integrity boundaries and relocated build paths."""
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile
import tarfile
from types import SimpleNamespace
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parent))
import dependencies as dep
import export as exporter

class DependencyTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.source=self.root/"source";self.source.write_bytes(b"verified fixture")
        self.item={"scope":"source","path":"nested/file","bytes":self.source.stat().st_size,
                   "sha256":dep.digest(self.source),"profiles":["apk"],"mode":420}
        self.dest=self.root/"dest/file"
    def test_restore_and_idempotence(self):
        dep.install(self.item,self.dest,self.source)
        before=self.dest.stat().st_mtime_ns
        dep.install(self.item,self.dest,self.source)
        self.assertEqual(self.dest.read_bytes(),b"verified fixture")
        self.assertEqual(before,self.dest.stat().st_mtime_ns)
    def test_corrupt_existing_is_preserved(self):
        self.dest.parent.mkdir();self.dest.write_bytes(b"do not overwrite")
        with self.assertRaises(ValueError):dep.install(self.item,self.dest,self.source)
        self.assertEqual(self.dest.read_bytes(),b"do not overwrite")
    def test_bad_hash_not_published(self):
        with self.assertRaises(ValueError):dep.install(dict(self.item,sha256="0"*64),self.dest,self.source)
        self.assertFalse(self.dest.exists())
        self.assertEqual(list(self.dest.parent.iterdir()),[])
    def test_oversize_not_published(self):
        with self.assertRaises(ValueError):dep.install(dict(self.item,bytes=1),self.dest,self.source)
        self.assertFalse(self.dest.exists())
    def test_traversal_rejected(self):
        for name in ["../key","/absolute","a/../../key","a//b","a\\b"]:
            with self.subTest(name=name),self.assertRaises(ValueError):dep.contained(self.root,name)
    def test_symlink_rejected(self):
        (self.root/"alias").symlink_to(self.source)
        with self.assertRaises(ValueError):dep.contained(self.root,"alias")
    def test_parent_symlink_rejected(self):
        (self.root/"alias").symlink_to(self.root,target_is_directory=True)
        with self.assertRaises(ValueError):dep.contained(self.root,"alias/file")
    def test_urls_and_redirects(self):
        for url in ["http://example.org/file","file:///etc/passwd","https://user:pass@example.org/x"]:
            with self.assertRaises(ValueError):dep.check_url(url)
        dep.check_url("https://example.org/pinned")
        with self.assertRaises(ValueError):
            dep.HttpsRedirect().redirect_request(None,None,302,"",{},"http://example.org/x")
    def test_interrupted_stream_cleanup(self):
        class Broken(io.BytesIO):
            def read(self,*args):raise OSError("interrupted")
        class Opener:
            def open(self,*args,**kwargs):return Broken()
        with patch.object(dep.urllib.request,"build_opener",return_value=Opener()):
            with self.assertRaises(OSError):dep.install(self.item,self.dest,url="https://example.org/pinned")
        self.assertFalse(self.dest.exists())
        self.assertEqual(list(self.dest.parent.iterdir()),[])
    def test_duplicate_lock_rejected(self):
        lock=self.root/"lock.json";lock.write_text(json.dumps({"schema":1,"files":[self.item,self.item]}))
        with self.assertRaises(ValueError):dep.load(lock)

    def test_archive_restore_and_integrity(self):
        for kind in ('zip', 'tar.bz2'):
            with self.subTest(kind=kind):
                arc=self.root/('archive.'+kind)
                if kind=='zip':
                    with zipfile.ZipFile(arc,'w') as z:z.writestr('nested/file',self.source.read_bytes())
                else:
                    with tarfile.open(arc,'w:bz2') as z:
                        info=tarfile.TarInfo('nested/file');info.size=self.item['bytes'];z.addfile(info,io.BytesIO(self.source.read_bytes()))
                cache=self.root/('cache-'+kind);cache.mkdir();(cache/dep.digest(arc)).write_bytes(arc.read_bytes())
                spec={'url':'https://example.org/archive','format':kind,'member':'nested/file','bytes':arc.stat().st_size,'sha256':dep.digest(arc)}
                item=dict(self.item,archive=spec);dest=self.root/('restored-'+kind)
                args=SimpleNamespace(cache=cache,root=self.root,download=False)
                self.assertTrue(dep.restore_archive(item,dest,args));self.assertEqual(dest.read_bytes(),self.source.read_bytes())
                (cache/spec['sha256']).write_bytes(b'corrupt')
                with self.assertRaises(ValueError):dep.restore_archive(item,self.root/'bad',args)
    def test_archive_links_and_paths_rejected(self):
        arc=self.root/'link.zip'
        with zipfile.ZipFile(arc,'w') as z:
            info=zipfile.ZipInfo('link');info.create_system=3;info.external_attr=0o120777<<16;z.writestr(info,b'target')
        for member in ('link','../link'):
            with self.assertRaises(ValueError):
                with dep.member_stream(arc,{'format':'zip','member':member},6):pass
        tar=self.root/'link.tar.bz2'
        with tarfile.open(tar,'w:bz2') as z:
            info=tarfile.TarInfo('link');info.type=tarfile.SYMTYPE;info.linkname='/outside';z.addfile(info)
        with self.assertRaises(ValueError):
            with dep.member_stream(tar,{'format':'tar.bz2','member':'link'},0):pass

class ExportTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.base=Path(self.temp.name);self.root=self.base/"repo";self.root.mkdir()
        subprocess.run(["git","init","-q",str(self.root)],check=True)
        (self.root/"tools/distribution").mkdir(parents=True)
        (self.root/"tools/distribution/public-files.json").write_text(json.dumps({"include":["src/*"],"exclude":[],"max_file_bytes":100}))
        (self.root/"dependencies.lock.json").write_text(json.dumps({"files":[{"scope":"source","path":"src/binary"}]}))
        (self.root/"src").mkdir();(self.root/"src/code.py").write_text("pass\n")
        (self.root/"src/binary").write_bytes(b"large dependency")
        (self.root/"secret.pk8").write_bytes(b"not selected")
        self.dest=self.base/"export"
    def test_export_excludes_dependencies_history_and_private_files(self):
        result=exporter.export(self.root,self.dest)
        self.assertEqual(result["files"],1)
        self.assertFalse((self.dest/".git").exists())
        self.assertFalse((self.dest/"src/binary").exists())
        self.assertFalse((self.dest/"secret.pk8").exists())
        manifest=json.loads((self.dest/"SOURCE_MANIFEST.json").read_text())
        self.assertEqual(manifest["files"]["src/code.py"]["sha256"],dep.digest(self.dest/"src/code.py"))
    def test_existing_output_preserved(self):
        self.dest.mkdir();(self.dest/"sentinel").write_text("keep")
        with self.assertRaises(ValueError):exporter.export(self.root,self.dest)
        self.assertEqual((self.dest/"sentinel").read_text(),"keep")
    def test_nested_output_rejected(self):
        with self.assertRaises(ValueError):exporter.export(self.root,self.root/"output")
    def test_source_symlink_rejected(self):
        (self.root/"src/link").symlink_to(self.root/"secret.pk8")
        with self.assertRaises(ValueError):exporter.export(self.root,self.dest)
    def test_unmanaged_big_file_rejected_before_output(self):
        (self.root/"src/large").write_bytes(b"x"*101)
        with self.assertRaises(ValueError):exporter.export(self.root,self.dest)
        self.assertFalse(self.dest.exists())

class RepositoryCoverageTests(unittest.TestCase):
    def test_rom_integration_binary_inputs_are_declared(self):
        root=Path(__file__).resolve().parents[2]
        locked={x['path']:x['sha256'] for x in dep.load(root/'dependencies.lock.json')['files'] if x['scope']=='source'}
        for path,sha in json.loads((root/'integration/lineage-24.0/source-binaries.json').read_text()).items():
            self.assertEqual(locked.get(path),sha,path)

class PathTests(unittest.TestCase):
    def test_relocated_paths_without_wsl(self):
        root=Path(__file__).resolve().parents[2]
        script="from paths import LINEAGE,INPUTS,STATE;print(LINEAGE);print(INPUTS);print(STATE)"
        env=dict(os.environ,PYTHONPATH=str(root/"tools/lineage"),AIOS_LINEAGE_ROOT="/tmp/aios-test-lineage",AIOS_INPUTS="/tmp/aios-test-inputs")
        env.pop("AIOS_BUILD_STATE",None)
        output=subprocess.check_output([sys.executable,"-c",script],env=env,text=True).splitlines()
        self.assertEqual(output,["/tmp/aios-test-lineage","/tmp/aios-test-inputs","/tmp/aios-test-lineage/.aios-state"])

if __name__=="__main__":
    unittest.main()
