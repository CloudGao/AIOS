#!/usr/bin/env python3
"""Compatibility alias for complete, guarded source integration; never builds."""
from pathlib import Path
import subprocess
import sys
if __name__ == '__main__':
    raise SystemExit(subprocess.call([sys.executable, str(Path(__file__).with_name('integrate.py')),
                                     *sys.argv[1:], '--apply']))
