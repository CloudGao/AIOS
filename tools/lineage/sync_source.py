#!/usr/bin/env python3
"""Sync the official LineageOS checkout with a lock and host/disk safeguards.

No device access, deletion, force checkout, AOSP fallback, or automatic build.
"""
import argparse
import fcntl
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time
import xml.etree.ElementTree as ET

from paths import LINEAGE as ROOT, STATE, snapshot, enough_space
GIB = 1024 ** 3


def validate_source(root):
    origin = subprocess.check_output(
        ['git', '-C', str(root / '.repo/manifests'), 'remote', 'get-url', 'origin'],
        text=True).strip()
    if origin.rstrip('/').removesuffix('.git') != 'https://github.com/LineageOS/android':
        raise ValueError('Refusing non-official LineageOS manifest origin')
    manifest = ET.parse(root / '.repo/manifests/default.xml').getroot()
    if manifest.find('default').get('revision') != 'refs/heads/lineage-24.0':
        raise ValueError('Unexpected LineageOS branch; review latest baseline explicitly')
    return subprocess.check_output(
        ['git', '-C', str(root / '.repo/manifests'), 'rev-parse', 'HEAD'], text=True).strip()


def sync_environment(mirror):
    env = os.environ.copy()
    env['GIT_TERMINAL_PROMPT'] = '0'
    env['PATH'] = os.environ.get('PATH', '/usr/bin:/bin')
    # Fail a stalled HTTP transfer instead of occupying every repo worker indefinitely.
    env['GIT_HTTP_LOW_SPEED_LIMIT'] = '1024'
    env['GIT_HTTP_LOW_SPEED_TIME'] = '90'
    if mirror == 'tuna':
        # Process-local URL substitutions; retain the unmodified official manifest.
        offset = int(env.get('GIT_CONFIG_COUNT', '0'))
        for index, (source, destination) in enumerate([
            ('https://github.com/LineageOS/', 'https://mirrors.tuna.tsinghua.edu.cn/git/lineageOS/LineageOS/'),
            ('https://android.googlesource.com/', 'https://mirrors.tuna.tsinghua.edu.cn/git/AOSP/'),
        ], offset):
            env[f'GIT_CONFIG_KEY_{index}'] = f'url.{destination}.insteadOf'
            env[f'GIT_CONFIG_VALUE_{index}'] = source
        env['GIT_CONFIG_COUNT'] = str(offset + 2)
    return env


def save(record):
    tmp = STATE / 'sync.json.tmp'
    tmp.write_text(json.dumps(record, indent=2) + '\n')
    tmp.replace(STATE / 'sync.json')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', action='store_true')
    parser.add_argument('--mirror', choices=['official', 'tuna'], default='official')
    args = parser.parse_args()
    revision = validate_source(ROOT)
    repo = ROOT / '.repo/repo/repo'
    command = [str(repo), 'sync', '-c', '--no-manifest-update', '--no-tags',
               '--no-clone-bundle', '--fail-fast', '--retry-fetches=2', '-j4']
    if args.plan:
        print(json.dumps(dict(root=str(ROOT), manifest_commit=revision, command=command, mirror=args.mirror,
                              start_reserve_gib=60, stop_reserve_gib=30,
                              progress_check_minutes=5), indent=2))
        return
    STATE.mkdir(parents=True, exist_ok=True)
    with (STATE / 'sync.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        before = snapshot()
        if not enough_space(before, 60 * GIB):
            raise RuntimeError('Need 60 GiB free on each configured volume before sync')
        record = dict(status='running', manifest_commit=revision, root=str(ROOT), mirror=args.mirror,
                      command=command, before=before, pid=os.getpid())
        save(record)
        env = sync_environment(args.mirror)
        child = None
        with (STATE / 'sync.log').open('ab', buffering=0) as log:
            log.write(('\nSync start ' + time.ctime() + '\n').encode())
            try:
                child = subprocess.Popen(command, cwd=ROOT, env=env, stdout=log,
                                         stderr=subprocess.STDOUT, start_new_session=True)
                record['child_pid'] = child.pid
                save(record)
                while child.poll() is None:
                    try:
                        child.wait(timeout=30)
                    except subprocess.TimeoutExpired:
                        pass
                    record['current'] = snapshot()
                    if not enough_space(record['current'], 30 * GIB):
                        record['status'] = 'stopped_low_space'
                        break
                    save(record)
                if record['status'] == 'running':
                    record['status'] = 'succeeded' if child.returncode == 0 else 'failed'
            except BaseException as exc:
                record.update(status='interrupted', error=str(exc))
                raise
            finally:
                if child and child.poll() is None:
                    os.killpg(child.pid, signal.SIGTERM)
                    try:
                        child.wait(timeout=20)
                    except subprocess.TimeoutExpired:
                        os.killpg(child.pid, signal.SIGKILL)
                        child.wait()
                record['exit_code'] = child.returncode if child else None
                record['after'] = snapshot()
                save(record)
                with (STATE / 'sync-history.jsonl').open('a') as history:
                    history.write(json.dumps(record) + '\n')
        if record['status'] == 'succeeded':
            with (STATE / 'resolved-manifest.xml').open('w') as output:
                subprocess.run([str(repo), 'manifest', '-r'], cwd=ROOT,
                               stdout=output, check=True)
        print(json.dumps(record, indent=2))
        if record['status'] != 'succeeded':
            raise SystemExit(1)


if __name__ == '__main__':
    def stop(signum, frame):
        raise KeyboardInterrupt('Sync interrupted by signal')
    signal.signal(signal.SIGTERM, stop)
    main()
