#!/usr/bin/env python3
"""Verify or restore pinned build inputs. Reads only pinned regular archive members; never scans private data."""
import argparse
from contextlib import contextmanager
import tarfile
import zipfile
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import sys
import tempfile
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]

def contained(root, name):
    p = PurePosixPath(name)
    if not name or p.is_absolute() or ".." in p.parts or str(p) != name or "\\" in name:
        raise ValueError("Invalid relative dependency path: " + name)
    root = Path(root).resolve()
    dest = root.joinpath(*p.parts)
    current = dest
    while current != root:
        if current.is_symlink():
            raise ValueError("Symlink in dependency path: " + name)
        current = current.parent
    if not dest.resolve().is_relative_to(root):
        raise ValueError("Dependency path escapes root")
    return dest

def digest(path):
    with Path(path).open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()

def load(lock):
    value = json.loads(Path(lock).read_text())
    if value.get("schema") != 1:
        raise ValueError("Unsupported lock schema")
    seen = set()
    for item in value["files"]:
        key = (item["scope"], item["path"])
        if key in seen or item["scope"] not in ("source", "inputs"):
            raise ValueError("Duplicate or invalid dependency scope")
        seen.add(key)
        contained(ROOT, item["path"])
        if len(item["sha256"]) != 64 or any(c not in "0123456789abcdef" for c in item["sha256"]):
            raise ValueError("Invalid SHA-256")
        if type(item["bytes"]) is not int or item["bytes"] < 0:
            raise ValueError("Invalid byte size")
        if item.get("url"):
            check_url(item["url"])
        if item.get("archive"):
            archive = item["archive"]
            check_url(archive["url"])
            contained(ROOT, archive["member"])
            if archive["format"] not in ("zip", "tar.bz2"):
                raise ValueError("Unsupported dependency archive")
            if type(archive["bytes"]) is not int or archive["bytes"] < 0:
                raise ValueError("Invalid archive size")
            if len(archive["sha256"]) != 64 or any(c not in "0123456789abcdef" for c in archive["sha256"]):
                raise ValueError("Invalid archive SHA-256")
    return value

def check_url(url):
    p = urllib.parse.urlsplit(url)
    if p.scheme != "https" or not p.hostname or p.username or p.password:
        raise ValueError("Only HTTPS URLs without embedded credentials are allowed")

class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        check_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)

def matches(path, item):
    return path.is_file() and path.stat().st_size == item["bytes"] and digest(path) == item["sha256"]

def install(item, destination, source=None, url=None, stream_factory=None):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        if matches(destination, item):
            return
        raise ValueError("Existing file differs; preserve and inspect it before replacement: " + str(destination))
    fd, temporary = tempfile.mkstemp(prefix=".aios-dependency-", dir=destination.parent)
    try:
        with os.fdopen(fd, "wb") as out:
            if stream_factory is not None:
                stream = stream_factory()
            elif source is not None:
                stream = source.open("rb")
            else:
                check_url(url)
                stream = urllib.request.build_opener(HttpsRedirect()).open(url, timeout=60)
            with stream as reader:
                size = 0
                while chunk := reader.read(1024 * 1024):
                    size += len(chunk)
                    if size > item["bytes"]:
                        raise ValueError("Download exceeds pinned size")
                    out.write(chunk)
        if not matches(Path(temporary), item):
            raise ValueError("Dependency checksum/size mismatch: " + item.get("path", "upstream archive"))
        os.chmod(temporary, item.get("mode", 420))
        # Atomic no-clobber publication; another process may have completed first.
        try:
            os.link(temporary, destination)
        except FileExistsError:
            if not matches(destination, item):
                raise ValueError("Concurrent dependency destination differs")
    finally:
        Path(temporary).unlink(missing_ok=True)

@contextmanager
def member_stream(path, spec, expected_size):
    # Only one named regular member; never extractall or follow archive links.
    contained(ROOT, spec["member"])
    if spec["format"] == "zip":
        with zipfile.ZipFile(path) as archive:
            entries = [x for x in archive.infolist() if x.filename == spec["member"]]
            if len(entries) != 1:
                raise ValueError("Missing or duplicate ZIP member")
            entry = entries[0]
            kind = (entry.external_attr >> 16) & 0o170000
            if entry.is_dir() or kind not in (0, 0o100000) or entry.file_size != expected_size:
                raise ValueError("Not the expected regular ZIP member")
            with archive.open(entry) as stream:
                yield stream
    else:
        with tarfile.open(path, "r:bz2") as archive:
            entries = [x for x in archive.getmembers() if x.name == spec["member"]]
            if len(entries) != 1 or not entries[0].isfile() or entries[0].size != expected_size:
                raise ValueError("Not the expected regular TAR member")
            with archive.extractfile(entries[0]) as stream:
                yield stream


def restore_archive(item, dest, args):
    spec = item["archive"]
    candidates = []
    if args.cache:
        candidates.append(contained(args.cache, spec["sha256"]))
    cached = contained(args.root, ".local/downloads/" + spec["sha256"])
    candidates.append(cached)
    archive_path = next((p for p in candidates if p.is_file()), None)
    if archive_path is None:
        if not args.download:
            return False
        install(spec, cached, url=spec["url"])
        archive_path = cached
    if not matches(archive_path, spec):
        raise ValueError("Upstream archive checksum/size mismatch: " + item["path"])
    install(item, dest, stream_factory=lambda: member_stream(archive_path, spec, item["bytes"]))
    return True


def run(args):
    files = [x for x in load(args.lock)["files"] if args.profile == "all" or args.profile in x["profiles"]]
    if args.profile == "rom":
        import importlib.util
        spec=importlib.util.spec_from_file_location("aios_optional_dependencies",ROOT/"tools/lineage/optional_dependencies.py")
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        optional=module.OptionalDependencies(args.root)
        assets=json.loads((args.root/"integration/lineage-24.0/assets.json").read_text())
        origins={entry["origin"] for entry in assets["files"]}
        selected={entry["origin"] for entry in assets["files"] if optional.includes_path(entry["destination"])}
        files=[item for item in files if (item["scope"]!="source" or optional.includes_path(item["path"]))
               and (item["scope"]+":"+item["path"] not in origins or item["scope"]+":"+item["path"] in selected)]
    roots = {"source": args.root.resolve(), "inputs": args.inputs.resolve()}
    missing, invalid = [], []
    for item in files:
        dest = contained(roots[item["scope"]], item["path"])
        if dest.exists() and not matches(dest, item):
            invalid.append(item["scope"] + ":" + item["path"])
            continue
        if not dest.exists() and args.command == "prepare":
            source = None
            if args.cache:
                candidate = contained(args.cache, item["sha256"])
                if candidate.is_file():
                    source = candidate
            origin = args.from_source if item["scope"] == "source" else args.from_inputs
            if source is None and origin:
                candidate = contained(origin, item["path"])
                if candidate.is_file():
                    source = candidate
            if source is not None or (args.download and item.get("url")):
                install(item, dest, source, item.get("url"))
            elif item.get("archive"):
                restore_archive(item, dest, args)
        if not dest.exists():
            missing.append(item["scope"] + ":" + item["path"])
    print(json.dumps({"profile":args.profile, "checked":len(files), "missing":missing, "invalid":invalid}, indent=2))
    return 1 if missing or invalid else 0

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("command", choices=["check", "prepare"])
    p.add_argument("--root", type=Path, default=ROOT)
    p.add_argument("--inputs", type=Path, default=Path(os.environ.get("AIOS_INPUTS", ROOT/".local/inputs")))
    p.add_argument("--lock", type=Path, default=ROOT/"dependencies.lock.json")
    p.add_argument("--profile", choices=["all","apk","rom"], default="all")
    p.add_argument("--cache", type=Path, help="Content-addressed offline cache (SHA-256 filenames)")
    p.add_argument("--from-source", type=Path, help="Explicit local source tree; only lock-listed files are read")
    p.add_argument("--from-inputs", type=Path, help="Explicit external input directory")
    p.add_argument("--download", action="store_true", help="Allow pinned HTTPS downloads")
    args = p.parse_args()
    try:
        return run(args)
    except (ValueError, OSError, zipfile.BadZipFile, tarfile.TarError) as error:
        print(str(error), file=sys.stderr)
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
