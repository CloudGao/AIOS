import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import sync_source


class SourceGuardTest(unittest.TestCase):
    def test_rejects_non_lineage_origin(self):
        with patch('subprocess.check_output', return_value='https://android.googlesource.com/platform/manifest\n'):
            with self.assertRaisesRegex(ValueError, 'non-official'):
                sync_source.validate_source(Path('/unused'))

    def test_rejects_old_branch_and_accepts_pinned_lineage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / '.repo/manifests/default.xml'
            manifest.parent.mkdir(parents=True)
            manifest.write_text('<manifest><default revision="refs/heads/lineage-23.2"/></manifest>')
            with patch('subprocess.check_output', return_value='https://github.com/LineageOS/android.git\n'):
                with self.assertRaisesRegex(ValueError, 'branch'):
                    sync_source.validate_source(root)
            manifest.write_text('<manifest><default revision="refs/heads/lineage-24.0"/></manifest>')
            with patch('subprocess.check_output', side_effect=['https://github.com/LineageOS/android.git\n', 'revision\n']):
                self.assertEqual(sync_source.validate_source(root), 'revision')

    def test_both_filesystems_must_have_reserve(self):
        for linux, host, expected in [(100, 10, False), (10, 100, False), (60, 60, True)]:
            self.assertEqual(sync_source.enough_space(
                dict(linux_free_bytes=linux, f_free_bytes=host), 60), expected)

    def test_mirror_rewrite_is_scoped_and_preserves_existing_config(self):
        with patch.dict('os.environ', {'GIT_CONFIG_COUNT': '1', 'GIT_CONFIG_KEY_0': 'example.key',
                                       'GIT_CONFIG_VALUE_0': 'keep'}, clear=True):
            env = sync_source.sync_environment('tuna')
            self.assertEqual(env['GIT_CONFIG_COUNT'], '3')
            self.assertEqual(env['GIT_CONFIG_VALUE_0'], 'keep')
            self.assertEqual(env['GIT_CONFIG_VALUE_1'], 'https://github.com/LineageOS/')
            self.assertIn('/git/lineageOS/LineageOS/', env['GIT_CONFIG_KEY_1'])
            self.assertEqual(sync_source.sync_environment('official')['GIT_CONFIG_COUNT'], '1')


if __name__ == '__main__':
    unittest.main()
