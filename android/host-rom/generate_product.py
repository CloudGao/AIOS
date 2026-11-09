#!/usr/bin/env python3
"""Generate immutable product metadata. No runtime switches or device access."""
import argparse
import hashlib
import json
from pathlib import Path


def resolve(catalog, product):
    if catalog.get('schema_version') != 1 or product.get('schema_version') != 1:
        raise ValueError('unsupported_manifest_version')
    modules = {}
    selectors = set()
    for source in catalog['modules']:
        item = dict(source)
        key = item['id']
        if key in modules:
            raise ValueError('duplicate_module:' + key)
        if item['provider'] not in ('core', 'agent', 'runtime'):
            raise ValueError('unknown_provider:' + key)
        for selector in item['capabilities']:
            if selector in selectors:
                raise ValueError('duplicate_capability:' + selector)
            selectors.add(selector)
        modules[key] = item
    selected = set(product['modules'])
    limits = product.get('limits', {})
    for key in ('virtual_sessions', 'scheduler_reserved', 'headless_sessions'):
        if type(limits.get(key)) is not int or not 0 <= limits[key] <= 64:
            raise ValueError('invalid_product_limit:' + key)
    if limits['scheduler_reserved'] > limits['virtual_sessions'] or limits['headless_sessions'] == 0:
        raise ValueError('inconsistent_product_limits')
    if len(selected) != len(product['modules']) or selected - modules.keys():
        raise ValueError('duplicate_or_unknown_selection')
    order, visiting, visited = [], set(), set()
    def visit(key):
        if key in visiting:
            raise ValueError('dependency_cycle:' + key)
        if key in visited:
            return
        visiting.add(key)
        item = modules[key]
        for dependency in item['requires']:
            if dependency not in modules:
                raise ValueError('unknown_dependency:' + dependency)
            if key in selected and dependency not in selected:
                raise ValueError('missing_dependency:' + key + ':' + dependency)
            visit(dependency)
        visiting.remove(key)
        visited.add(key)
        order.append(key)
    for key, item in modules.items():
        if item.get('fixed') and key not in selected:
            raise ValueError('fixed_core_cannot_be_excluded:' + key)
        visit(key)
    result = dict(product)
    result['modules'] = [dict(modules[key], packaged=key in selected) for key in order]
    payload = json.dumps(result, ensure_ascii=True, sort_keys=True, separators=(',', ':'))
    result['manifest_hash'] = hashlib.sha256(payload.encode()).hexdigest()
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--product', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    args = parser.parse_args()
    result = resolve(json.loads(args.catalog.read_text()), json.loads(args.product.read_text()))
    payload = json.dumps(result, ensure_ascii=True, separators=(',', ':'))
    args.java.write_text('package os.aios.host;\n'
                        '/** Generated from trusted build input; not a user preference. */\n'
                        'public final class ProductManifest {\n'
                        ' private ProductManifest() {}\n'
                        ' public static final String JSON = ' + json.dumps(payload) + ';\n}\n')


if __name__ == '__main__':
    main()
