#!/usr/bin/env python3
"""Rebuild the pinned offline API JAR from the three verified LiteRT 2.3.0 class JARs."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile
from dependencies import contained, digest, install
ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, default=ROOT / "android/core-services/third_party/litert/litert-offline-api.jar")
    args = parser.parse_args()
    layout = json.loads((ROOT / "tools/distribution/litert-layout.json").read_text())
    payload = {}
    for name, expected in layout["sources"].items():
        source = contained(args.source_root, name)
        if digest(source) != expected:
            raise ValueError("LiteRT input hash mismatch: " + name)
        with zipfile.ZipFile(source) as archive:
            for entry in layout["entries"]:
                if entry["source"] == name:
                    data = archive.read(entry["path"])
                    if hashlib.sha256(data).hexdigest() != entry["sha256"]:
                        raise ValueError("LiteRT class member mismatch")
                    payload[entry["path"]] = data
    if args.output.is_symlink():
        raise ValueError("Refusing symlink output")
    with tempfile.TemporaryDirectory(prefix="aios-litert-api-") as temporary:
        archive_path = Path(temporary) / "offline-api.jar"
        with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_STORED) as archive:
            for entry in layout["entries"]:
                contained(ROOT, entry["path"].removesuffix("/"))
                info = zipfile.ZipInfo(entry["path"], tuple(entry["date_time"]))
                info.create_system = entry["create_system"]
                info.external_attr = entry["external_attr"]
                archive.writestr(info, b"" if entry["source"] is None else payload[entry["path"]])
        if digest(archive_path) != layout["output_sha256"]:
            raise ValueError("Rebuilt LiteRT JAR differs; do not change the lock to bypass this error")
        item = {"path": str(args.output), "bytes": archive_path.stat().st_size, "sha256": layout["output_sha256"]}
        install(item, args.output, source=archive_path)
    print(json.dumps({"status": "rebuilt_verified", "sha256": layout["output_sha256"], "entries": len(layout["entries"])}, indent=2))
if __name__ == "__main__":
    main()
