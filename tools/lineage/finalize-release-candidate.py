#!/usr/bin/env python3
"""Attach public verification material to an offline-verified local candidate."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import time

KEYS=Path.home()/'.local/share/aios-release/keys'
from paths import LINEAGE
HOST=LINEAGE/'out/host/linux-x86/bin'
def run(args,**kwargs):return subprocess.run(list(map(str,args)),check=True,**kwargs)
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def finalize(root):
    report=json.loads((root/'offline-verification.json').read_text())
    if report.get('status')!='passed':raise ValueError('Offline verification has not passed')
    manifest=json.loads((root/'release.json').read_text())
    # Recheck the original immutable outputs before attaching new metadata.
    for name,spec in manifest['files'].items():
        if Path(name).name!=name or sha(root/name)!=spec['sha256']:raise ValueError('Candidate changed after verification: '+name)
    certificates={}
    for name in ['releasekey','platform']:
        cert=root/(name+'.x509.pem')
        cert.write_bytes((KEYS/(name+'.x509.pem')).read_bytes())
        der=run(['openssl','x509','-in',cert,'-outform','DER'],capture_output=True).stdout
        certificates[name]=dict(file=cert.name,certificate_sha256=hashlib.sha256(der).hexdigest())
    run(['openssl','x509','-in',root/'releasekey.x509.pem','-pubkey','-noout','-out',root/'release-public.pem'])
    run(['openssl','pkey','-in',KEYS/'avb.pem','-pubout','-out',root/'avb-public.pem'])
    run([HOST/'avbtool','extract_public_key','--key',KEYS/'avb.pem','--output',root/'avb.avbpubkey'])
    certificates['avb']=dict(public_pem='avb-public.pem',avb_public_key='avb.avbpubkey',avb_public_key_sha256=sha(root/'avb.avbpubkey'))
    (root/'VERIFY.txt').write_text('Verify release.json against release.json.sig using OpenSSL:\n\nopenssl dgst -sha256 -verify release-public.pem -signature release.json.sig release.json\n\nObtain the expected release certificate fingerprint or manifest SHA-256 from an independent trusted distribution channel. The public key shipped beside a package does not by itself establish publisher identity. Then run python install-release.py --release . for archive and partition integrity checks.\n\nAll PEM files in this public directory contain public certificates or public keys only. Device boot, firmware compatibility, first-use and data-preserving upgrade acceptance remain separate from offline verification.\n')
    for path in root.glob('*.pem'):
        if b'PRIVATE KEY' in path.read_bytes():raise ValueError('Private key found in public candidate')
    ignored={'release.json','release.json.sig','SHA256SUMS'}
    manifest['files']={p.name:dict(sha256=sha(p),bytes=p.stat().st_size) for p in root.iterdir() if p.is_file() and p.name not in ignored}
    manifest['certificates']=certificates
    manifest['acceptance']['host_artifact_verification']='passed'
    manifest['offline_verified_at']=time.time()
    manifest['distributable']=False
    (root/'release.json').write_text(json.dumps(manifest,indent=2)+'\n')
    run(['openssl','dgst','-sha256','-sign',KEYS/'releasekey.pem','-out',root/'release.json.sig',root/'release.json'])
    run(['openssl','dgst','-sha256','-verify',root/'release-public.pem','-signature',root/'release.json.sig',root/'release.json'])
    (root/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in sorted(root.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
    print(json.dumps(dict(status='verified_candidate_complete',output=str(root),certificates=certificates),indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('release',type=Path);args=parser.parse_args();finalize(args.release.resolve())
