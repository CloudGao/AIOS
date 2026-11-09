#!/usr/bin/env python3
"""Fixed LineageOS ARM64 image build with a disk-space stop guard."""
import argparse
import datetime
import fcntl
import json
import os
import signal
import subprocess
from sync_source import ROOT, STATE, GIB, validate_source, snapshot, enough_space
from target_config import load_target

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--target")
    parser.add_argument("--images", nargs="+", choices=("systemimage","systemextimage","productimage"))
    parser.add_argument("--full",action="store_true")
    parser.add_argument("--variant", choices=("user", "userdebug"), default="userdebug")
    parser.add_argument("--start-reserve-gib", type=int, default=60)
    args=parser.parse_args()
    if args.full and args.images: parser.error("--images and --full are mutually exclusive")
    target=load_target(args.target)
    work=target.state
    work.mkdir(parents=True,exist_ok=True)
    if args.start_reserve_gib < 40 or (args.full and args.start_reserve_gib < 60):
        parser.error("Reserve must be >=40 GiB for incremental images, >=60 for full build")
    revision = validate_source(ROOT)
    env = target.environment(args.variant)
    env.update(AIOS_SOONG_GOMAXPROCS='4', AIOS_SOONG_GOMEMLIMIT='14GiB', AIOS_SOONG_GOGC='25')
    with (STATE / 'image-build.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        target.assert_staged()
        before = snapshot()
        if not enough_space(before, args.start_reserve_gib * GIB):
            raise RuntimeError('Insufficient starting disk space')
        record = dict(status='running',target=target.name,product=target.product,out_dir=target.out, before=before, manifest_commit=revision, start_reserve_gib=args.start_reserve_gib,
                      targets=['bacon','target-files-package'] if args.full else (args.images or ['systemimage','systemextimage','productimage']), started_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), full=args.full, variant=args.variant)
        def save():
            tmp = work / 'unified-build.json.tmp'
            tmp.write_text(json.dumps(record, indent=2)+'\n')
            tmp.replace(work / 'unified-build.json')
        child = None
        try:
            with (work / 'unified-build.log').open('ab') as log:
                child = subprocess.Popen(['build/soong/soong_ui.bash', '--make-mode', '-j4',
                    *record['targets']], cwd=ROOT, env=env, stdout=log,
                    stderr=subprocess.STDOUT, start_new_session=True)
                record['child_pid'] = child.pid
                save()
                while child.poll() is None:
                    try:
                        child.wait(timeout=30)
                    except subprocess.TimeoutExpired:
                        pass
                    record['current'] = snapshot()
                    save()
                    if not enough_space(record['current'], 30 * GIB):
                        raise RuntimeError('Stopped below 30 GiB free space')
                record.update(exit_code=child.returncode,
                              status='succeeded' if child.returncode == 0 else 'failed')
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
            record['after'] = snapshot()
            record['finished_at']=datetime.datetime.now(datetime.timezone.utc).isoformat()
            save()
        if record.get('exit_code', 1):
            raise SystemExit(record.get('exit_code', 1))

if __name__ == '__main__':
    main()
