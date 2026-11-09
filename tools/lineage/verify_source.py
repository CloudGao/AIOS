#!/usr/bin/env python3
"""Verify local checkouts against the resolved LineageOS manifest; no network."""
from paths import SOURCE as SOURCE_ROOT, LINEAGE as LINEAGE_ROOT, STATE as BUILD_STATE, INPUTS as INPUT_ROOT
import concurrent.futures
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = LINEAGE_ROOT
STATE = BUILD_STATE


def git(path, *args):
    return subprocess.check_output(['git', '-C', str(path), *args], text=True).strip()


def main():
    manifest = STATE / 'resolved-manifest.xml'
    tree = ET.parse(manifest).getroot()
    origin = git(ROOT / '.repo/manifests', 'remote', 'get-url', 'origin')
    if origin.removesuffix('.git') != 'https://github.com/LineageOS/android':
        raise RuntimeError('Unexpected manifest origin')
    if tree.find('default').get('revision') != 'refs/heads/lineage-24.0':
        raise RuntimeError('Unexpected LineageOS branch')
    remotes = {r.get('name'): r.get('fetch') for r in tree.findall('remote')}

    def check(project):
        name = project.get('name')
        path = project.get('path', name)
        expected = project.get('revision', '')
        remote = project.get('remote', tree.find('default').get('remote'))
        base = remotes[remote]
        if base == '..':
            base = 'https://github.com'
        expected_url = base.rstrip('/') + '/' + name
        record = dict(path=path, expected=expected, expected_url=expected_url)
        try:
            actual = git(ROOT / path, 'rev-parse', 'HEAD')
            url = git(ROOT / path, 'remote', 'get-url', remote)
            record.update(actual=actual, url=url,
                          ok=bool(re.fullmatch('[0-9a-f]{40}', expected))
                          and actual == expected
                          and url.removesuffix('.git') == expected_url.removesuffix('.git'))
        except subprocess.CalledProcessError as exc:
            record.update(ok=False, error=str(exc))
        return record

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        projects = list(pool.map(check, tree.findall('project')))
    result = dict(time=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  manifest_origin=origin,
                  manifest_sha256=hashlib.sha256(manifest.read_bytes()).hexdigest(),
                  project_count=len(projects),
                  verified_count=sum(p['ok'] for p in projects), projects=projects,
                  scope='HEAD and configured origin only; not a clean-tree or build validation')
    (STATE / 'source-verification.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: v for k, v in result.items() if k != 'projects'}, indent=2))
    failures = [p for p in projects if not p['ok']]
    if failures:
        print(json.dumps(failures, indent=2))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
