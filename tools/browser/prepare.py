#!/usr/bin/env python3
"""Recreate the pinned browser checkout and apply the reviewed local patch only."""
import os
import json
import hashlib
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCK = json.loads((ROOT / 'tools/browser/upstream.json').read_text())
DEST = Path(os.environ.get('AIOS_BROWSER_SOURCE', str(ROOT/'.local/agent-browser'))).expanduser().resolve()

def git(*args):
    return subprocess.run(['git', '-C', str(DEST), *args], check=True, capture_output=True, text=True).stdout.strip()

if not DEST.exists():
    subprocess.run(['git', 'clone', '--depth', '1', '--branch', LOCK['tag'], LOCK['repository'], str(DEST)], check=True)
if git('status', '--porcelain'):
    raise SystemExit('Checkout has local changes; preserve them before preparation.')
if git('rev-parse', 'HEAD') != LOCK['upstream_commit']:
    raise SystemExit('Checkout is not at the pinned upstream revision; refusing to reset it.')
patch = ROOT / 'patches/agent-browser/local-cdp.patch'
if hashlib.sha256(patch.read_bytes()).hexdigest() != LOCK['patch_sha256']:
    raise SystemExit('Patch hash does not match lock file.')
subprocess.run(['git', '-C', str(DEST), 'apply', '--check', str(patch)], check=True)
subprocess.run(['git', '-C', str(DEST), 'apply', str(patch)], check=True)
print('Pinned source prepared. No build or device operation was performed.')