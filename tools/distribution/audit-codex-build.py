#!/usr/bin/env python3
"""Inventory the dependency closure of an existing Cargo build without rebuilding."""
from pathlib import Path
import argparse,collections,hashlib,json

def inventory(target):
    index=collections.defaultdict(list);units={}
    for base in [target/'release/.fingerprint',target/'aarch64-linux-android/release/.fingerprint']:
        for path in base.glob('*/*.json'):
            fingerprint=path.with_suffix('')
            if not fingerprint.is_file() or fingerprint.stat().st_size!=16:continue
            try:
                value=json.loads(path.read_text());key=int.from_bytes(bytes.fromhex(fingerprint.read_text()),'little')
            except (ValueError,UnicodeError):continue
            if 'deps' in value:index[key].append(path);units[path]=value
    roots=[p for p in units if p.name=='bin-codex-app-server.json']
    if len(roots)!=1:raise ValueError('Expected exactly one codex-app-server binary fingerprint')
    todo=roots[:];seen=set()
    while todo:
        path=todo.pop()
        if path in seen:continue
        seen.add(path)
        for dependency in units[path]['deps']:
            candidates=index[dependency[3]]
            if len(candidates)>1:
                same=[p for p in candidates if ('aarch64-linux-android' in p.parts)==('aarch64-linux-android' in path.parts)]
                if len(same)==1:candidates=same
            if len(candidates)!=1:raise ValueError('Unresolved build edge: '+str(dependency[:2]))
            todo+=candidates
    records=[]
    for path in sorted(seen):
        records.append({'package_name':path.parent.name.rsplit('-',1)[0],'unit':path.name,
            'target':'aarch64-linux-android' if 'aarch64-linux-android' in path.parts else 'host',
            'features':json.loads(units[path].get('features') or '[]'),
            'fingerprint_sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    binary=target/'aarch64-linux-android/release/codex-app-server'
    return {'schema':1,'scope':'Existing Cargo fingerprint closure rooted at ARM64 codex-app-server, including host build/proc-macro units; not a symbol-level linked-code SBOM or clean rebuild proof.',
        'binary':{'sha256':hashlib.sha256(binary.read_bytes()).hexdigest(),'bytes':binary.stat().st_size},
        'root':'codex-app-server','unresolved_graph_edges':0,'units':records}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--target',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    result=inventory(args.target);args.output.write_text(json.dumps(result,indent=2)+'\n');print('Build units:',len(result['units']))
