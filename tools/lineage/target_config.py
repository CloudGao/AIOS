"""One selected ARM64 product; no device access or build at import time."""
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import sys

SOURCE = Path(__file__).resolve().parents[2]
from paths import LINEAGE as ROOT, STATE
CONFIGS = Path(__file__).with_name('targets')


def relative(value):
    p = Path(value)
    if not value or p.is_absolute() or '..' in p.parts or str(p) != value:
        raise ValueError('Expected normalized relative path: ' + value)
    if not re.fullmatch(r'[A-Za-z0-9_./+-]+', value):
        raise ValueError('Invalid path characters: ' + value)
    return p


@dataclass(frozen=True)
class Target:
    name: str
    config: dict

    @property
    def device(self): return self.config['device']
    @property
    def product(self): return self.config['product']
    @property
    def out(self): return self.config['out_dir']
    @property
    def state(self):
        return STATE if self.name == 'frankel' else STATE / 'targets' / self.name
    @property
    def fingerprint(self):
        return hashlib.sha256(json.dumps(self.config, sort_keys=True).encode()).hexdigest()

    def environment(self, variant='userdebug'):
        if variant not in ('user', 'userdebug'):
            raise ValueError('Unsupported build variant')
        env = os.environ.copy()
        env.pop('OUT_DIR_COMMON_BASE', None)
        env.update(AIOS_TARGET=self.name, LINEAGE_BUILD=self.device, SOONG_NINJA='ninja',
                   OUT_DIR=self.out, TARGET_PRODUCT=self.product,
                   TARGET_RELEASE=self.config['release'], TARGET_BUILD_VARIANT=variant)
        return env

    def assert_staged(self):
        p = ROOT / 'vendor/aios/integration-state.json'
        data = json.loads(p.read_text())
        if data.get('status') != 'staged_not_built' or data.get('target') != self.name or data.get('config_sha256') != self.fingerprint:
            raise ValueError('Stage the selected target before building; integration belongs to another configuration')
        if (ROOT / self.out).resolve() != ROOT.resolve() / self.out:
            raise ValueError('Output directory must not alias another target')
        # Never reuse a Ninja graph for another device/architecture.
        variables = ROOT / self.out / 'soong' / ('soong.' + self.product + '.variables')
        if variables.exists():
            values = json.loads(variables.read_text())
            if values.get('DeviceName') != self.device or values.get('DeviceArch') != 'arm64':
                raise ValueError('Existing output is not the selected ARM64 target')


def load_target(name=None):
    name = name or os.environ.get('AIOS_TARGET', 'frankel')
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]*', name):
        raise ValueError('Invalid AIOS target name')
    path = CONFIGS / (name + '.json')
    if not path.is_file():
        raise ValueError('Unknown AIOS target: ' + name)
    data = json.loads(path.read_text())
    required = {'schema','device','product','branch','arch','release','out_dir',
                'product_makefile','board_config','platform_certificate','launcher_certificate',
                'device_integrations','legacy_policy','acceptance'}
    if set(data) != required or data['schema'] != 1:
        raise ValueError('Invalid target schema')
    if data['branch'] != 'lineage-24.0' or data['arch'] != 'arm64':
        raise ValueError('Only the locked LineageOS branch and ARM64 are supported')
    for key in ('device','product','release'):
        if not re.fullmatch(r'[a-zA-Z0-9_]+', data[key]):
            raise ValueError('Invalid target field: ' + key)
    if name != data['device']:
        raise ValueError('Target file name must match the device codename')
    if data['product'] != 'lineage_' + data['device']:
        raise ValueError('Expected official LineageOS product name')
    for key in ('product_makefile','board_config','platform_certificate','launcher_certificate','out_dir'):
        relative(data[key])
    if not data['product_makefile'].startswith('device/') or not data['board_config'].startswith('device/'):
        raise ValueError('Device integration entry points must be under device/')
    expected_out = 'out' if name == 'frankel' else 'out-aios-' + name
    if data['out_dir'] != expected_out:
        raise ValueError('Use the fixed per-target output: ' + expected_out)
    if data['device_integrations'] not in ([], ['frankel']):
        raise ValueError('Unknown device integration')
    if (data['device_integrations'] or data['legacy_policy']) and (name != 'frankel' or data['device'] != 'frankel'):
        raise ValueError('Pixel-specific integrations cannot be used by another target')
    if type(data['legacy_policy']) is not bool:
        raise ValueError('legacy_policy must be boolean')
    return Target(name, data)


def require_legacy_frankel():
    if any(arg == '--target' or arg.startswith('--target=') for arg in sys.argv[1:]):
        raise ValueError('Historical entry point does not accept --target; use the unified builders')
    receipt = ROOT / 'vendor/aios/integration-state.json'
    if receipt.exists():
        active = json.loads(receipt.read_text())
        if active.get('target') != 'frankel' or active.get('status') != 'staged_not_built':
            raise ValueError('Historical entry point cannot use another or incomplete integration')
    if load_target().name != 'frankel':
        raise ValueError('This historical/release entry point supports frankel only; use integrate.py and the unified builders')
