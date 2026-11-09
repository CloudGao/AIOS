#!/usr/bin/env python3
"""Explicit user-run activation only. Does not install, erase, root, or grant permissions."""
import argparse, subprocess, sys
PACKAGE = 'os.aios.app'

def activate(adb, serial):
    base = [adb, '-s', serial]
    def shell(*args):
        return subprocess.run(base + ['shell', *args], check=True, capture_output=True, text=True, timeout=20).stdout.strip()
    paths = shell('pm', 'path', PACKAGE).splitlines()
    if len(paths) != 1 or not paths[0].startswith('package:/data/app/'):
        raise RuntimeError('Expected one installed AIOS APK; install the signed single APK first.')
    path = paths[0][8:]
    import shlex
    # Fixed entry point; shell path is quoted independently, never interpreted as command text.
    command = 'CLASSPATH=' + shlex.quote(path) + ' nohup app_process /system/bin os.aios.apk.shell.ShellMain </dev/null >/data/local/tmp/aios-apk-shell.log 2>&1 &'
    shell(command)
    print('Activation requested. Refresh AIOS on the phone to verify the connection.')
    print('If unavailable, inspect: adb -s ' + serial + ' shell cat /data/local/tmp/aios-apk-shell.log')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True, help='Explicit target device; never select an arbitrary phone')
    options = parser.parse_args()
    activate(options.adb, options.serial)
