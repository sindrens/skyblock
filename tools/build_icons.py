#!/usr/bin/env python3
"""Phoenix icon pack v2 (I7, build spec GUI-P1-PACK): catalogue -> placeholder art -> pack tree -> contact sheet.

Single source of truth: resourcepack/icons.json (also bundled into the plugin by Gradle and read by the unit tests).
Python 3.9, standard library only. Run from anywhere; paths are relative to the repository root.

  python3 tools/build_icons.py fetch-vanilla   one-time, needs network: vanilla 1.21.5 item definitions -> vanilla-fallback.json
  python3 tools/build_icons.py art             draws the PNG of every catalogue entry that has none yet
                                               (--force-art redraws all, --only <key> just one; owner art is never touched otherwise)
  python3 tools/build_icons.py pack            regenerates pack.mcmeta, pack.png, models and item definitions
  python3 tools/build_icons.py sheet           resourcepack/preview/contact-sheet.png (every icon at 4x on the slot grey)
  python3 tools/build_icons.py check           exits 1 if the committed tree is not what art (missing only) + pack would write

The generated zip is built by Gradle (modules/pack, task packZip) from resourcepack/pack.
"""
import argparse
import hashlib
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from icons_lib import art, draw, font5x7, png  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RP = os.path.join(ROOT, 'resourcepack')
CATALOGUE = os.path.join(RP, 'icons.json')
FALLBACK = os.path.join(RP, 'vanilla-fallback.json')
PACK = os.path.join(RP, 'pack')
TEXTURES = os.path.join(PACK, 'assets', 'phoenix', 'textures', 'item', 'icon')
MODELS = os.path.join(PACK, 'assets', 'phoenix', 'models', 'item', 'icon')
ITEMS = os.path.join(PACK, 'assets', 'minecraft', 'items')
SHEET = os.path.join(RP, 'preview', 'contact-sheet.png')
LOGO = os.path.join(ROOT, 'branding', 'skybornmc-logo-128.png')
CACHE = os.path.join(ROOT, 'tools', 'icons_lib', '.cache')

VANILLA_VERSION = '1.21.5'
MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
# 1.21.5 .. 26.3 (v1 values; re-check the 26.3 number in the Paper changelog before a release).
FORMAT_MIN, FORMAT_MAX = 55, 97
DESCRIPTION = 'Phoenix Skyblock icons'
SIZE_BUDGET = 150 * 1024

KEY_RE = re.compile(r'^[a-z0-9_]+(/[a-z0-9_]+)?$')
MEANINGS = ('money', 'reward', 'move', 'depths', 'skills', 'people', 'home', 'danger', 'neutral', 'locked',
            'currency-money', 'currency-crystals', 'currency-shards', 'currency-credits')
FIELDS = ('key', 'base', 'name', 'meaning', 'file', 'motif', 'slice', 'used')


def j(obj):
    """Deterministic JSON text: sorted keys, 1-space indent, trailing newline."""
    return json.dumps(obj, indent=1, sort_keys=True) + '\n'


def rel(path):
    return os.path.relpath(path, ROOT)


# ---------------------------------------------------------------------------------------------- catalogue

def load_catalogue():
    with open(CATALOGUE, encoding='utf-8') as f:
        data = json.load(f)
    icons = data.get('icons', [])
    problems = []
    keys, files = set(), set()
    for i, icon in enumerate(icons):
        where = 'icons[%d] %s' % (i, icon.get('key'))
        for field in FIELDS:
            if field not in icon:
                problems.append('%s: missing %s' % (where, field))
        key = icon.get('key', '')
        if not KEY_RE.match(key):
            problems.append('%s: bad key format' % where)
        if key in keys:
            problems.append('%s: duplicate key' % where)
        keys.add(key)
        if icon.get('file') != key.replace('/', '_'):
            problems.append('%s: file must be %s' % (where, key.replace('/', '_')))
        if icon.get('file') in files:
            problems.append('%s: duplicate file' % where)
        files.add(icon.get('file'))
        if icon.get('meaning') not in MEANINGS:
            problems.append('%s: unknown meaning %r' % (where, icon.get('meaning')))
        if icon.get('slice') not in (1, 2, 3):
            problems.append('%s: slice must be 1..3' % where)
    if problems:
        sys.exit('icons.json problems:\n  ' + '\n  '.join(problems))
    return icons


def load_fallback():
    if not os.path.exists(FALLBACK):
        sys.exit('%s is missing: run "python3 tools/build_icons.py fetch-vanilla" once (needs network)' % rel(FALLBACK))
    with open(FALLBACK, encoding='utf-8') as f:
        return json.load(f)


# ---------------------------------------------------------------------------------------------- fetch-vanilla

def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read()


def cmd_fetch_vanilla(_args):
    icons = load_catalogue()
    os.makedirs(CACHE, exist_ok=True)
    jar_path = os.path.join(CACHE, 'client-%s.jar' % VANILLA_VERSION)
    if not os.path.exists(jar_path):
        manifest = json.loads(fetch(MANIFEST))
        version = next((v for v in manifest['versions'] if v['id'] == VANILLA_VERSION), None)
        if version is None:
            sys.exit('version %s is not in the Mojang manifest' % VANILLA_VERSION)
        meta = json.loads(fetch(version['url']))
        client = meta['downloads']['client']
        print('downloading %s client jar (%d MB)...' % (VANILLA_VERSION, client['size'] // (1024 * 1024)))
        data = fetch(client['url'])
        if hashlib.sha1(data).hexdigest() != client['sha1']:
            sys.exit('client jar SHA-1 mismatch')
        with open(jar_path + '.tmp', 'wb') as f:
            f.write(data)
        os.replace(jar_path + '.tmp', jar_path)
    out = {}
    missing = []
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        for base in sorted({i['base'] for i in icons}):
            entry = 'assets/minecraft/items/%s.json' % base
            if entry not in names:
                missing.append(base)
                continue
            out[base] = json.loads(jar.read(entry))['model']
    if missing:
        sys.exit('no %s item definition for: %s (not a %s item id)' % (VANILLA_VERSION, ', '.join(missing), VANILLA_VERSION))
    with open(FALLBACK, 'w', encoding='utf-8') as f:
        f.write(j(out))
    print('wrote %s (%d base items)' % (rel(FALLBACK), len(out)))


# ---------------------------------------------------------------------------------------------- art

def png_path(icon):
    return os.path.join(TEXTURES, icon['file'] + '.png')


def art_bytes(icon):
    c = art.render(icon)
    return png.encode(c.w, c.h, c.rows())


def missing_art(icons):
    return [i for i in icons if not os.path.exists(png_path(i))]


def cmd_art(args):
    icons = load_catalogue()
    if args.only:
        icons = [i for i in icons if i['key'] == args.only]
        if not icons:
            sys.exit('no catalogue key %r' % args.only)
    todo = icons if (args.force_art or args.only) else missing_art(icons)
    os.makedirs(TEXTURES, exist_ok=True)
    for icon in todo:
        with open(png_path(icon), 'wb') as f:
            f.write(art_bytes(icon))
        print('drew', rel(png_path(icon)))
    print('art: %d drawn, %d kept' % (len(todo), len(icons) - len(todo)))


# ---------------------------------------------------------------------------------------------- pack

def mcmeta():
    return {'pack': {'description': DESCRIPTION, 'pack_format': FORMAT_MIN, 'supported_formats': [FORMAT_MIN, FORMAT_MAX],
                     'min_format': FORMAT_MIN, 'max_format': FORMAT_MAX}}


def model(icon):
    return {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'phoenix:item/icon/%s' % icon['file']}}


def item_definition(base, icons, fallback):
    cases = [{'when': i['key'], 'model': {'type': 'minecraft:model', 'model': 'phoenix:item/icon/%s' % i['file']}}
             for i in sorted(icons, key=lambda i: i['key'])]
    return {'model': {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
                      'cases': cases, 'fallback': fallback[base]}}


def generated(icons, fallback):
    """{absolute path: bytes} of every file `pack` owns."""
    unknown = sorted({i['base'] for i in icons} - set(fallback))
    if unknown:
        sys.exit('base items missing from vanilla-fallback.json: %s (run fetch-vanilla)' % ', '.join(unknown))
    files = {os.path.join(PACK, 'pack.mcmeta'): j(mcmeta()).encode('utf-8')}
    with open(LOGO, 'rb') as f:
        files[os.path.join(PACK, 'pack.png')] = png.clean(f.read())
    for icon in icons:
        files[os.path.join(MODELS, icon['file'] + '.json')] = j(model(icon)).encode('utf-8')
    by_base = {}
    for icon in icons:
        by_base.setdefault(icon['base'], []).append(icon)
    for base, group in by_base.items():
        files[os.path.join(ITEMS, base + '.json')] = j(item_definition(base, group, fallback)).encode('utf-8')
    return files


def stale(files):
    """Generated files on disk that `pack` would not write any more."""
    out = []
    for d in (MODELS, ITEMS):
        if os.path.isdir(d):
            for name in sorted(os.listdir(d)):
                p = os.path.join(d, name)
                if p not in files:
                    out.append(p)
    return out


def orphan_art(icons):
    """PNGs without a catalogue entry (owner art: reported, never deleted)."""
    wanted = {png_path(i) for i in icons}
    if not os.path.isdir(TEXTURES):
        return []
    return [os.path.join(TEXTURES, n) for n in sorted(os.listdir(TEXTURES)) if os.path.join(TEXTURES, n) not in wanted]


def cmd_pack(_args):
    icons = load_catalogue()
    files = generated(icons, load_fallback())
    written = 0
    for path, data in sorted(files.items()):
        old = None
        if os.path.exists(path):
            with open(path, 'rb') as f:
                old = f.read()
        if old != data:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'wb') as f:
                f.write(data)
            written += 1
    for path in stale(files):
        os.remove(path)
        print('deleted', rel(path))
    for path in orphan_art(icons):
        print('WARNING: %s has no catalogue entry (kept; delete it by hand if the icon is gone)' % rel(path))
    for icon in missing_art(icons):
        print('WARNING: %s has no PNG yet (run: python3 tools/build_icons.py art)' % icon['key'])
    print('pack: %d files written, %d unchanged, tree %d bytes' % (written, len(files) - written, tree_size()))


def tree_size():
    total = 0
    for dp, _, fs in os.walk(PACK):
        for f in fs:
            total += os.path.getsize(os.path.join(dp, f))
    return total


def zip_size():
    """Size of a deflated zip of the tree (what Gradle's packZip produces, roughly)."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as z:
        for dp, _, fs in sorted(os.walk(PACK)):
            for f in sorted(fs):
                p = os.path.join(dp, f)
                info = zipfile.ZipInfo(os.path.relpath(p, PACK).replace(os.sep, '/'), (1980, 2, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(p, 'rb') as fh:
                    z.writestr(info, fh.read())
    return len(buf.getvalue())


# ---------------------------------------------------------------------------------------------- sheet

SLOT_GREY = (0x8B, 0x8B, 0x8B, 255)
LABEL = (0x20, 0x20, 0x20, 255)
HEADER = (0xFF, 0xFF, 0xFF, 255)
SCALE = 4
CELL_W = 140
CELL_H = 64 + 30
PER_ROW = 8
MARGIN = 8


def cmd_sheet(_args):
    icons = load_catalogue()
    groups = []
    index = {}
    for icon in icons:
        g = icon['key'].split('/')[0]
        if g not in index:
            index[g] = len(groups)
            groups.append((g, []))
        groups[index[g]][1].append(icon)
    height = MARGIN
    for _, members in groups:
        height += 12 + ((len(members) + PER_ROW - 1) // PER_ROW) * CELL_H
    width = MARGIN * 2 + PER_ROW * CELL_W
    sheet = draw.Canvas(width, height + MARGIN, SLOT_GREY)
    y = MARGIN
    for name, members in groups:
        sheet.text(font5x7.FONT_5X7, name, MARGIN, y, HEADER)
        y += 12
        for idx, icon in enumerate(members):
            cx = MARGIN + (idx % PER_ROW) * CELL_W
            cy = y + (idx // PER_ROW) * CELL_H
            path = png_path(icon)
            if os.path.exists(path):
                with open(path, 'rb') as f:
                    rows = png.decode_rgba(f.read())
                src = draw.Canvas(len(rows[0]), len(rows))
                src.px = rows
                scale = SCALE if src.w == 16 else SCALE // 2
                sheet.blit(src, cx + (CELL_W - 64) // 2, cy, scale)
            sheet.text(font5x7.FONT_5X7, icon['key'], cx + 2, cy + 68, LABEL)
            sheet.text(font5x7.FONT_5X7, icon['base'], cx + 2, cy + 78, LABEL)
        y += ((len(members) + PER_ROW - 1) // PER_ROW) * CELL_H
    os.makedirs(os.path.dirname(SHEET), exist_ok=True)
    with open(SHEET, 'wb') as f:
        f.write(png.encode(sheet.w, sheet.h, sheet.rows(), alpha=False))
    print('wrote %s (%dx%d)' % (rel(SHEET), sheet.w, sheet.h))


# ---------------------------------------------------------------------------------------------- check

def cmd_check(_args):
    icons = load_catalogue()
    problems = []
    for icon in missing_art(icons):
        problems.append('missing PNG for %s (run art)' % icon['key'])
    for icon in icons:
        p = png_path(icon)
        if not os.path.exists(p):
            continue
        with open(p, 'rb') as f:
            try:
                w, h, depth, ctype = png.header(f.read())
            except ValueError as e:
                problems.append('%s: %s' % (rel(p), e))
                continue
        if w != h or w not in (16, 32) or depth != 8 or ctype != 6:
            problems.append('%s: must be a 16x16 or 32x32 8-bit RGBA PNG (got %dx%d, depth %d, colour type %d)'
                            % (rel(p), w, h, depth, ctype))
    for p in orphan_art(icons):
        problems.append('%s has no catalogue entry' % rel(p))
    files = generated(icons, load_fallback())
    for path, data in sorted(files.items()):
        if not os.path.exists(path):
            problems.append('%s is missing (run pack)' % rel(path))
            continue
        with open(path, 'rb') as f:
            if f.read() != data:
                problems.append('%s differs from the generator (run pack)' % rel(path))
    for path in stale(files):
        problems.append('%s is stale (run pack)' % rel(path))
    size = zip_size()
    if size >= SIZE_BUDGET:
        problems.append('zip is %d bytes, budget %d' % (size, SIZE_BUDGET))
    if problems:
        print('check FAILED:\n  ' + '\n  '.join(problems))
        sys.exit(1)
    print('OK: %d icons, %d generated files, zip about %d bytes' % (len(icons), len(files), size))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='command')
    sub.add_parser('fetch-vanilla')
    p_art = sub.add_parser('art')
    p_art.add_argument('--force-art', action='store_true', help='redraw every PNG (overwrites owner art!)')
    p_art.add_argument('--only', help='redraw just this key')
    sub.add_parser('pack')
    sub.add_parser('sheet')
    sub.add_parser('check')
    args = parser.parse_args()
    commands = {'fetch-vanilla': cmd_fetch_vanilla, 'art': cmd_art, 'pack': cmd_pack, 'sheet': cmd_sheet, 'check': cmd_check}
    if args.command not in commands:
        parser.print_help()
        sys.exit(2)
    commands[args.command](args)


if __name__ == '__main__':
    main()
