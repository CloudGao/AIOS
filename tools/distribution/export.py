#!/usr/bin/env python3
"""Export an explicit source allowlist to a NEW directory, without Git history or dependencies."""
import argparse
import fnmatch
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
from dependencies import contained

ROOT = Path(__file__).resolve().parents[2]

def selected(root):
    policy = json.loads((root/"tools/distribution/public-files.json").read_text())
    # Git is used only to inventory source; output never includes .git or ignored data.
    names = subprocess.check_output(["git","-C",str(root),"ls-files","--cached","--others","--exclude-standard","-z"]).decode().split("\0")
    removed = {x["path"] for x in json.loads((root/"dependencies.lock.json").read_text())["files"] if x["scope"] == "source"}
    result = []
    for name in sorted(set(names) - {""}):
        if name in removed or any(fnmatch.fnmatchcase(name, x) for x in policy["exclude"]):
            continue
        if not any(fnmatch.fnmatchcase(name, x) for x in policy["include"]):
            continue
        file = contained(root, name)
        if not file.is_file():
            raise ValueError("Missing selected file: " + name)
        if file.stat().st_size > policy["max_file_bytes"]:
            raise ValueError("Unmanaged large source file: " + name)
        result.append(name)
    return result

def export(root, dest):
    root, dest = root.resolve(), dest.absolute()
    if dest.exists() or dest.is_symlink() or dest.resolve().is_relative_to(root):
        raise ValueError("Destination must be a new directory outside the source tree")
    names = selected(root)
    dest.mkdir(parents=True)
    records = {}
    for name in names:
        source = contained(root, name)
        target = contained(dest, name)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        records[name] = {"sha256":hashlib.sha256(target.read_bytes()).hexdigest(), "bytes":target.stat().st_size}
    (dest/"SOURCE_MANIFEST.json").write_text(json.dumps({"schema":1,"files":records},indent=2)+"\n")
    return {"files":len(records),"bytes":sum(v["bytes"] for v in records.values()),"destination":str(dest)}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("destination", type=Path, nargs="?")
    p.add_argument("--list", action="store_true")
    args = p.parse_args()
    if args.list:
        print("\n".join(selected(ROOT)))
    elif args.destination:
        print(json.dumps(export(ROOT,args.destination),indent=2))
    else:
        p.error("Provide destination or --list")

if __name__ == "__main__":
    main()
