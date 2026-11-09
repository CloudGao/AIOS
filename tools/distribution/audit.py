#!/usr/bin/env python3
"""Read-only screening; reports locations, never matched secret values. Not a security certification."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from export import selected

PATTERNS = {
    "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
    "token": re.compile(rb"(?:sk-proj-|ghp_|github_pat_|sk-ant-)[A-Za-z0-9_-]{20,}"),
    "local-path": re.compile(rb"/home/aosp/|/mnt/f/AIOS|F:[/\\]AIOS"),
}
def inspect(name, data):
    findings = []
    for kind, pattern in PATTERNS.items():
        line, previous = 1, 0
        for match in pattern.finditer(data):
            line += data.count(b"\n", previous, match.start())
            previous = match.start()
            findings.append({"path":name,"line":line,"kind":kind})
    return findings

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root",type=Path,default=Path(__file__).resolve().parents[2])
    p.add_argument("--history",action="store_true",help="Scan all reachable Git blobs; may take minutes")
    args=p.parse_args()
    findings=[]
    if args.history:
        rows=subprocess.check_output(["git","-C",str(args.root),"rev-list","--objects","--all"],text=True).splitlines()
        proc=subprocess.Popen(["git","-C",str(args.root),"cat-file","--batch"],stdin=subprocess.PIPE,stdout=subprocess.PIPE)
        try:
            for row in rows:
                oid,_,name=row.partition(" ")
                proc.stdin.write((oid+"\n").encode());proc.stdin.flush()
                header=proc.stdout.readline().decode().split()
                if len(header)!=3:raise ValueError("Unexpected git object")
                size=int(header[2]);data=proc.stdout.read(size);proc.stdout.read(1)
                if header[1]=="blob":
                    findings.extend(inspect(name+"@"+oid[:12],data))
        finally:
            proc.stdin.close();proc.stdout.close();proc.wait()
    else:
        names=selected(args.root) if (args.root/".git").exists() else json.loads((args.root/"SOURCE_MANIFEST.json").read_text())["files"]
        for name in names:
            findings.extend(inspect(name,(args.root/name).read_bytes()))
    print(json.dumps({"findings":findings,"scope":"reachable-history" if args.history else "public-source","limitations":"Pattern scan only; binary archives, personal content and licenses require separate review."},indent=2))
    return int(any(x["kind"]!="local-path" for x in findings))

if __name__=="__main__":
    raise SystemExit(main())
