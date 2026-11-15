#!/usr/bin/env python3
"""Capture QA UI evidence on one physical device, not model-experience proof.

DEV=<adb serial> python3 tools/ui-verify/capture_ui_0253_matrix.py --label after
Uses existing isolated QA state; never seeds, clears, or opens the production package.
Restores original display overrides, font scale and night mode even on failure.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = 'com.mengyalife.app.qa'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--label', required=True, choices=['before', 'after'])
    parser.add_argument('--out', default='docs/reports/mengya-0.25.3/screenshots')
    args = parser.parse_args()
    serial = os.environ.get('DEV')
    if not serial:
        parser.error('DEV must identify the authorized physical QA device')
    prefix = ['adb', '-s', serial]

    def adb(*parts):
        return subprocess.run(prefix + list(parts), check=True, capture_output=True).stdout.decode(errors='replace')

    package = adb('shell', 'dumpsys', 'package', PKG)
    if not re.search(r'versionName=\S+-qa\b', package) or 'DEBUGGABLE' not in package:
        raise RuntimeError('Isolated debuggable QA package required')

    def tree():
        adb('shell', 'uiautomator', 'dump', '/sdcard/mengya-ui-0253.xml')
        result = ET.fromstring(adb('shell', 'cat', '/sdcard/mengya-ui-0253.xml'))
        if not any(node.get('package') == PKG for node in result.iter('node')):
            raise RuntimeError('QA package is not visible; refusing unrelated app input')
        return result

    def tap_tag(tag):
        for node in tree().iter('node'):
            if node.get('resource-id') == tag:
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
                if x2 <= x1 or y2 <= y1:
                    break
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                return
        raise RuntimeError(f'Missing visible QA control: {tag}')

    def override(setting):
        value = adb('shell', 'wm', setting)
        found = re.search(r'Override (?:size|density): (\S+)', value)
        return found.group(1) if found else 'reset'

    original = {
        'size': override('size'),
        'density': override('density'),
        'font': adb('shell', 'settings', 'get', 'system', 'font_scale').strip(),
        'night': adb('shell', 'cmd', 'uimode', 'night').strip().split(':')[-1].strip(),
    }
    output = Path(args.out)
    output.mkdir(parents=True, exist_ok=True)
    manifest = {'label': args.label, 'qaVersion': re.search(r'versionName=(\S+)', package).group(1),
                'physicalDevice': True, 'originalDisplay': original, 'captures': []}
    try:
        adb('shell', 'am', 'start', '-n', PKG + '/com.mengyalife.app.MainActivity')
        adb('shell', 'wm', 'density', '480')
        for width in (320, 360, 412):
            height = round(width * 6.5)
            adb('shell', 'wm', 'size', f'{width*3}x{height}')
            for theme in ('light', 'dark'):
                adb('shell', 'cmd', 'uimode', 'night', 'no' if theme == 'light' else 'yes')
                for scale in ('1.0', '1.3'):
                    adb('shell', 'settings', 'put', 'system', 'font_scale', scale)
                    time.sleep(.7)  # Allow configuration recreation; not a background-job poll.
                    for tag, page in [('tab_today', 'today'), ('tab_advisor', 'advisor'), ('tab_path', 'trajectory')]:
                        tap_tag(tag)
                        snapshot = tree()
                        name = f'{args.label}-{width}-{theme}-{scale.replace(".", "")}-{page}'
                        adb('shell', 'screencap', '-p', '/sdcard/mengya-ui-0253.png')
                        adb('pull', '/sdcard/mengya-ui-0253.png', str(output / (name + '.png')))
                        (output / (name + '.xml')).write_text(ET.tostring(snapshot, encoding='unicode'))
                        manifest['captures'].append({'file': name+'.png', 'widthDp': width, 'fontScale': float(scale), 'theme': theme, 'page': page})
                        print(name, flush=True)
    finally:
        adb('shell', 'wm', 'size', original['size'])
        adb('shell', 'wm', 'density', original['density'])
        if original['font'] in ('null', ''):
            adb('shell', 'settings', 'delete', 'system', 'font_scale')
        else:
            adb('shell', 'settings', 'put', 'system', 'font_scale', original['font'])
        adb('shell', 'cmd', 'uimode', 'night', original['night'])
        (output / (args.label + '-capture-manifest.json')).write_text(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
