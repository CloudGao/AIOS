#!/usr/bin/env python3
"""Make a local offline cache from verified explicit inputs. Does not authorize redistribution."""
import argparse
import json
from pathlib import Path
import shutil
from dependencies import ROOT, contained, load, matches

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("output",type=Path)
    p.add_argument("--root",type=Path,default=ROOT)
    p.add_argument("--inputs",type=Path,required=True)
    p.add_argument("--profile",choices=["all","apk","rom"],default="all")
    args=p.parse_args()
    if args.output.exists() or args.output.is_symlink():
        p.error("Cache destination must not exist")
    files=[x for x in load(ROOT/"dependencies.lock.json")["files"] if args.profile=="all" or args.profile in x["profiles"]]
    roots={"source":args.root,"inputs":args.inputs}
    sources=[]
    for item in files:
        source=contained(roots[item["scope"]],item["path"])
        if not matches(source,item):
            p.error("Missing or invalid input: "+item["scope"]+":"+item["path"])
        sources.append((item,source))
    args.output.mkdir(parents=True)
    for item,source in sources:
        target=args.output/item["sha256"]
        if not target.exists():shutil.copyfile(source,target)
        if not matches(target,item):raise ValueError("Cache verification failed")
    (args.output/"cache-manifest.json").write_text(json.dumps({"schema":1,"profile":args.profile,"files":files,"redistribution":"not_reviewed"},indent=2)+"\n")
    print(json.dumps({"files":len(files),"output":str(args.output)}))

if __name__=="__main__":
    main()
