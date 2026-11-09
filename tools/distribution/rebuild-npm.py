#!/usr/bin/env python3
"""Reproduce the pinned npm ZIP from the checksum-verified official Node source archive."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tarfile
import tempfile
import zipfile
from dependencies import contained, digest

ROOT = Path(__file__).resolve().parents[2]

def rebuild(archive, output):
    layout = json.loads((ROOT / "tools/distribution/npm-layout.json").read_text())
    if digest(archive) != layout["source_sha256"]:
        raise ValueError("Node source archive checksum mismatch")
    output = output.absolute()
    if output.is_symlink():
        raise ValueError("Refusing symlink output")
    if output.exists():
        if digest(output) == layout["zip_sha256"]:
            return {"status": "existing_verified", "sha256": layout["zip_sha256"]}
        raise ValueError("Existing npm ZIP differs; preserve and inspect it")
    entries = {entry["path"]: entry for entry in layout["entries"]}
    if len(entries) != len(layout["entries"]):
        raise ValueError("Duplicate recipe member")
    for name in entries:
        contained(ROOT, name)
    payload = {}
    with tarfile.open(archive, "r|xz") as source:
        for member in source:
            if not member.name.startswith(layout["source_prefix"]):
                continue
            name = member.name[len(layout["source_prefix"]):]
            if name not in entries:
                continue
            if name in payload or not member.isfile() or member.size > 32 * 1024 * 1024:
                raise ValueError("Invalid or duplicate npm source member")
            with source.extractfile(member) as stream:
                data = stream.read()
            if hashlib.sha256(data).hexdigest() != entries[name]["sha256"]:
                raise ValueError("npm source member mismatch: " + name)
            payload[name] = data
    if set(payload) != set(entries):
        raise ValueError("Node source archive is missing required npm members")
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=".npm-rebuild-", dir=output.parent)
    os.close(fd)
    temporary = Path(name)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as target:
            for entry in layout["entries"]:
                info = zipfile.ZipInfo(entry["path"], tuple(entry["date_time"]))
                info.create_system = 3
                info.external_attr = entry["external_attr"]
                info.compress_type = zipfile.ZIP_DEFLATED
                target.writestr(info, payload[entry["path"]], compresslevel=6)
        if digest(temporary) != layout["zip_sha256"]:
            raise ValueError("Rebuilt ZIP differs; check Python/zlib versions without changing the dependency lock")
        os.chmod(temporary, 0o644)
        try:
            os.link(temporary, output)
        except FileExistsError:
            if output.is_symlink() or digest(output) != layout["zip_sha256"]:
                raise ValueError("Concurrent npm destination differs")
        return {"status": "rebuilt_verified", "files": len(payload), "sha256": layout["zip_sha256"]}
    finally:
        temporary.unlink(missing_ok=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--node-archive", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "android/codex-runtime/assets/cli-runtime/npm.zip")
    args = parser.parse_args()
    print(json.dumps(rebuild(args.node_archive, args.output), indent=2))

if __name__ == "__main__":
    main()
