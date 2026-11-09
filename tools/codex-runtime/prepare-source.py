#!/usr/bin/env python3
"""Create a new pinned source checkout and apply reviewed Android port patches."""
import argparse, subprocess
from pathlib import Path

COMMIT='092d3acd6bec3e3a14bdc7e7a2810ab628ab759d'
def run(*args):subprocess.run(list(map(str,args)),check=True)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('destination',type=Path);args=parser.parse_args()
    target=args.destination.resolve()
    if target.exists():raise SystemExit('Destination must not exist; existing work is never reset')
    target.mkdir(parents=True)
    run('git','init',target)
    run('git','-C',target,'remote','add','origin','https://github.com/openai/codex.git')
    run('git','-C',target,'fetch','--depth','1','origin',COMMIT)
    run('git','-C',target,'checkout','--detach','FETCH_HEAD')
    patches=sorted((Path(__file__).parent/'patches').glob('*.patch'))
    if not patches:raise SystemExit('Android patches missing')
    for patch in patches:run('git','-C',target,'apply',patch.resolve())
    print(target)
if __name__=='__main__':main()
