#!/usr/bin/env python3
"""Phoenix icon pack v3 (I7): catalogue -> pixel art -> pack tree -> contact sheet.

Single source of truth: resourcepack/icons.json (also bundled into the plugin by Gradle and read by the unit tests).
Python 3.9, standard library only. Run from anywhere; paths are relative to the repository root.

Only things that are NOT a real Minecraft item get a custom icon (currencies, UI actions, a few feature symbols);
everything else is a plain vanilla item listed in the catalogue's "vanilla" section (docs/design/ICON_MAP.md).
Icons with several looks have variants (resourcepack/variants/<file>/<style>.png); "style" in the catalogue says which
one is live. A style called "vanilla" means: no custom model, the plain base item.

  python3 tools/build_icons.py art             draws the live PNG of every custom icon that has none yet, and every missing
                                               variant PNG (--force-art redraws all, --only <key> just one; any PNG that
                                               already exists, e.g. owner art, is never touched otherwise)
  python3 tools/build_icons.py use <key> <style>   makes a variant live (copies its PNG, updates "style" in icons.json)
  python3 tools/build_icons.py pack            regenerates pack.mcmeta, pack.png, models and item definitions
  python3 tools/build_icons.py sheet           resourcepack/preview/contact-sheet.png (every icon and variant at 4x)
  python3 tools/build_icons.py fetch-vanilla [--jar client.jar]   vanilla item definitions of the base items -> vanilla-fallback.json
                                               (network for 1.21.5, or a local client jar)
  python3 tools/build_icons.py verify-jars --jar a.jar [--jar b.jar]   fails if a fallback differs from a client jar, or
                                               points at a model that jar does not have (the "purple cube" check)
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

import shutil  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RP = os.path.join(ROOT, 'resourcepack')
CATALOGUE = os.path.join(RP, 'icons.json')
FALLBACK = os.path.join(RP, 'vanilla-fallback.json')
PACK = os.path.join(RP, 'pack')
TEXTURES = os.path.join(PACK, 'assets', 'phoenix', 'textures', 'item', 'icon')
MODELS = os.path.join(PACK, 'assets', 'phoenix', 'models', 'item', 'icon')
ITEMS = os.path.join(PACK, 'assets', 'minecraft', 'items')
SHEET = os.path.join(RP, 'preview', 'contact-sheet.png')
VARIANTS = os.path.join(RP, 'variants')
VANILLA_STYLE = 'vanilla'
LOGO = os.path.join(ROOT, 'branding', 'skybornmc-logo-128.png')
CACHE = os.path.join(ROOT, 'tools', 'icons_lib', '.cache')

VANILLA_VERSION = '1.21.5'
MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
# 1.21.5 .. 26.3 (v1 values; re-check the 26.3 number in the Paper changelog before a release).
FORMAT_MIN, FORMAT_MAX = 55, 97
DESCRIPTION = 'Phoenix Skyblock icons'
SIZE_BUDGET = 700 * 1024   # icons v4 + item textures (64x64 cartoon, 2 Nov 2026) + frame layer; hard limit 1 MB

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

def load_catalogue(with_vanilla=False):
    """The custom icons (list of dicts); with ``with_vanilla`` also the {key: vanilla item id} map."""
    with open(CATALOGUE, encoding='utf-8') as f:
        data = json.load(f)
    icons = data.get('icons', [])
    vanilla = data.get('vanilla', {})
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
        variants = icon.get('variants')
        if variants is not None:
            if not variants or len(set(variants)) != len(variants):
                problems.append('%s: variants must be a non-empty list of unique names' % where)
            elif icon.get('style') not in variants:
                problems.append('%s: style %r is not one of %s' % (where, icon.get('style'), variants))
        elif 'style' in icon:
            problems.append('%s: style without variants' % where)
    for key, material in vanilla.items():
        if not KEY_RE.match(key):
            problems.append('vanilla %s: bad key format' % key)
        if key in keys:
            problems.append('vanilla %s: also a custom icon (pick one)' % key)
        if not re.match(r'^[a-z0-9_]+$', str(material)):
            problems.append('vanilla %s: %r is not an item id' % (key, material))
    if problems:
        sys.exit('icons.json problems:\n  ' + '\n  '.join(problems))
    return (icons, vanilla) if with_vanilla else icons


def style_of(icon):
    """The live style of an icon: its "style" field, or None for an icon without variants."""
    return icon.get('style')


def is_active(icon):
    """True when the icon has a custom picture and model (style is not "vanilla")."""
    return style_of(icon) != VANILLA_STYLE


def active(icons):
    return [i for i in icons if is_active(i)]


def load_fallback():
    if not os.path.exists(FALLBACK):
        sys.exit('%s is missing: run "python3 tools/build_icons.py fetch-vanilla" once (needs network)' % rel(FALLBACK))
    with open(FALLBACK, encoding='utf-8') as f:
        return json.load(f)


# ---------------------------------------------------------------------------------------------- fetch-vanilla

def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read()


def cmd_fetch_vanilla(args):
    icons = load_catalogue()
    os.makedirs(CACHE, exist_ok=True)
    jar_path = args.jar or os.path.join(CACHE, 'client-%s.jar' % VANILLA_VERSION)
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
        # every base of the catalogue, also of icons whose style is "vanilla" (they can be switched on later)
        for base in sorted({i['base'] for i in icons}):
            entry = 'assets/minecraft/items/%s.json' % base
            if entry not in names:
                missing.append(base)
                continue
            out[base] = json.loads(jar.read(entry))['model']
    if missing:
        sys.exit('no item definition for: %s (not an item id in %s)' % (', '.join(missing), os.path.basename(jar_path)))
    with open(FALLBACK, 'w', encoding='utf-8') as f:
        f.write(j(out))
    print('wrote %s (%d base items, from %s)' % (rel(FALLBACK), len(out), os.path.basename(jar_path)))


def model_refs(node, acc):
    """Every model id a (vanilla) item model definition points at."""
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ('model', 'base') and isinstance(v, str):
                acc.add(v)
            else:
                model_refs(v, acc)
    elif isinstance(node, list):
        for v in node:
            model_refs(v, acc)
    return acc


def cmd_verify_jars(args):
    """The fallback of every base item must be exactly the item definition of every given client jar and every model it
    points at must exist there: otherwise our item definition REPLACES the vanilla one with a broken fallback and the
    client draws the purple-and-black missing-model cube (this is what happened to red_bed on 26.3)."""
    if not args.jar:
        sys.exit('give at least one client jar: verify-jars --jar ~/Library/Application\\ Support/minecraft/versions/26.3/26.3.jar')
    fallback = load_fallback()
    icons = load_catalogue()
    bases = sorted({i['base'] for i in active(icons)})
    problems = []
    for path in args.jar:
        with zipfile.ZipFile(path) as jar:
            names = set(jar.namelist())
            label = os.path.basename(path)
            for base in bases:
                entry = 'assets/minecraft/items/%s.json' % base
                if entry not in names:
                    problems.append('%s: no item definition for %s' % (label, base))
                    continue
                if json.loads(jar.read(entry))['model'] != fallback[base]:
                    problems.append('%s: item definition of %s differs from vanilla-fallback.json' % (label, base))
                for ref in sorted(model_refs(fallback[base], set())):
                    if 'assets/minecraft/models/%s.json' % ref.split(':', 1)[-1] not in names:
                        problems.append('%s: %s points at model %s, which does not exist' % (label, base, ref))
    if problems:
        print('verify-jars FAILED:\n  ' + '\n  '.join(problems))
        sys.exit(1)
    print('OK: %d base items identical in %d client jar(s)' % (len(bases), len(args.jar)))


# ---------------------------------------------------------------------------------------------- art

def png_path(icon):
    return os.path.join(TEXTURES, icon['file'] + '.png')


def variant_path(icon, style):
    return os.path.join(VARIANTS, icon['file'], style + '.png')


def art_bytes(icon, style=None):
    c = art.render(icon['key'], style or style_of(icon))
    return png.encode(c.w, c.h, c.rows())


def missing_art(icons):
    return [i for i in active(icons) if not os.path.exists(png_path(i))]


def missing_variants(icons):
    return [(i, s) for i in icons for s in i.get('variants', []) if s != VANILLA_STYLE and not os.path.exists(variant_path(i, s))]


def write_png(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(data)
    print('drew', rel(path))


def cmd_art(args):
    icons = load_catalogue()
    if args.only:
        icons = [i for i in icons if i['key'] == args.only]
        if not icons:
            sys.exit('no catalogue key %r' % args.only)
    force = args.force_art or args.only
    drawn = kept = 0
    for icon in icons:
        if is_active(icon):
            if force or not os.path.exists(png_path(icon)):
                write_png(png_path(icon), art_bytes(icon))
                drawn += 1
            else:
                kept += 1
        for style in icon.get('variants', []):
            if style == VANILLA_STYLE:
                continue
            if force or not os.path.exists(variant_path(icon, style)):
                write_png(variant_path(icon, style), art_bytes(icon, style))
                drawn += 1
            else:
                kept += 1
    print('art: %d drawn, %d kept' % (drawn, kept))


def cmd_use(args):
    """Make a variant the live look of an icon."""
    icons = load_catalogue()
    icon = next((i for i in icons if i['key'] == args.key), None)
    if icon is None:
        sys.exit('no custom icon %r (plain vanilla items have no variants)' % args.key)
    variants = icon.get('variants')
    if not variants:
        sys.exit('%s has only one look' % args.key)
    if args.style not in variants:
        sys.exit('%s has the styles %s, not %r' % (args.key, ', '.join(variants), args.style))
    if args.style == VANILLA_STYLE:
        if os.path.exists(png_path(icon)):
            os.remove(png_path(icon))
    else:
        if not os.path.exists(variant_path(icon, args.style)):
            sys.exit('%s is missing: run "art" first' % rel(variant_path(icon, args.style)))
        os.makedirs(TEXTURES, exist_ok=True)
        shutil.copyfile(variant_path(icon, args.style), png_path(icon))
    with open(CATALOGUE, encoding='utf-8') as f:
        lines = f.read().split('\n')
    needle = '"key": %s,' % json.dumps(args.key)
    for n, line in enumerate(lines):
        if needle in line:
            lines[n] = re.sub(r'"style": "[^"]*"', '"style": "%s"' % args.style, line)
            break
    with open(CATALOGUE, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    print('%s now uses style %s; run "pack" (and "check")' % (args.key, args.style))


# ---------------------------------------------------------------------------------------------- pack

def mcmeta():
    return {'pack': {'description': DESCRIPTION, 'pack_format': FORMAT_MIN, 'supported_formats': [FORMAT_MIN, FORMAT_MAX],
                     'min_format': FORMAT_MIN, 'max_format': FORMAT_MAX}}


def model(icon):
    # Tools are held like vanilla tools (item/handheld); everything else like a flat item.
    return {'parent': icon.get('parent', 'minecraft:item/generated'), 'textures': {'layer0': 'phoenix:item/icon/%s' % icon['file']}}


def item_definition(base, icons, fallback):
    cases = [{'when': i['key'], 'model': {'type': 'minecraft:model', 'model': 'phoenix:item/icon/%s' % i['file']}}
             for i in sorted(icons, key=lambda i: i['key'])]
    return {'model': {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
                      'cases': cases, 'fallback': fallback[base]}}


def bases_of(icon):
    """Every base item a picture sits on: 'base', plus 'bases' for item textures shared by several items (tool tiers)."""
    out = [icon['base']]
    for b in icon.get('bases', []):
        if b not in out:
            out.append(b)
    return out


def generated(icons, fallback):
    """{absolute path: bytes} of every file `pack` owns (only icons with a custom picture)."""
    icons = active(icons)
    unknown = sorted({b for i in icons for b in bases_of(i)} - set(fallback))
    if unknown:
        sys.exit('base items missing from vanilla-fallback.json: %s (run fetch-vanilla)' % ', '.join(unknown))
    files = {os.path.join(PACK, 'pack.mcmeta'): j(mcmeta()).encode('utf-8')}
    with open(LOGO, 'rb') as f:
        files[os.path.join(PACK, 'pack.png')] = png.clean(f.read())
    for icon in icons:
        files[os.path.join(MODELS, icon['file'] + '.json')] = j(model(icon)).encode('utf-8')
    by_base = {}
    for icon in icons:
        for b in bases_of(icon):
            by_base.setdefault(b, []).append(icon)
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
    """PNGs without a live catalogue entry (owner art: reported, never deleted)."""
    wanted = {png_path(i) for i in active(icons)}
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
    cells = []   # (group, label, sub label, path)
    for icon in icons:
        g = icon['key'].split('/')[0]
        if is_active(icon):
            style = style_of(icon)
            cells.append((g, icon['key'] + (' [%s]' % style if style else ''), icon['base'], png_path(icon)))
        else:
            cells.append((g, icon['key'] + ' [vanilla]', icon['base'], None))
        for style in icon.get('variants', []):
            if style != VANILLA_STYLE and style != style_of(icon):
                cells.append((g, '%s:%s' % (icon['key'], style), 'variant', variant_path(icon, style)))
    groups, index = [], {}
    for cell in cells:
        if cell[0] not in index:
            index[cell[0]] = len(groups)
            groups.append((cell[0], []))
        groups[index[cell[0]]][1].append(cell)
    height = MARGIN
    for _, members in groups:
        height += 12 + ((len(members) + PER_ROW - 1) // PER_ROW) * CELL_H
    width = MARGIN * 2 + PER_ROW * CELL_W
    sheet = draw.Canvas(width, height + MARGIN, SLOT_GREY)
    y = MARGIN
    for name, members in groups:
        sheet.text(font5x7.FONT_5X7, name, MARGIN, y, HEADER)
        y += 12
        for idx, (_, label, sub, path) in enumerate(members):
            cx = MARGIN + (idx % PER_ROW) * CELL_W
            cy = y + (idx // PER_ROW) * CELL_H
            if path and os.path.exists(path):
                with open(path, 'rb') as f:
                    rows = png.decode_rgba(f.read())
                src = draw.Canvas(len(rows[0]), len(rows))
                src.px = rows
                scale = SCALE if src.w == 16 else SCALE // 2
                sheet.blit(src, cx + (CELL_W - 64) // 2, cy, scale)
            sheet.text(font5x7.FONT_5X7, label, cx + 2, cy + 68, LABEL)
            sheet.text(font5x7.FONT_5X7, sub, cx + 2, cy + 78, LABEL)
        y += ((len(members) + PER_ROW - 1) // PER_ROW) * CELL_H
    os.makedirs(os.path.dirname(SHEET), exist_ok=True)
    with open(SHEET, 'wb') as f:
        f.write(png.encode(sheet.w, sheet.h, sheet.rows(), alpha=False))
    print('wrote %s (%dx%d)' % (rel(SHEET), sheet.w, sheet.h))


# ---------------------------------------------------------------------------------------------- check

def png_problems(path, label):
    if not os.path.exists(path):
        return ['missing PNG for %s (run art)' % label]
    with open(path, 'rb') as f:
        try:
            w, h, depth, ctype = png.header(f.read())
        except ValueError as e:
            return ['%s: %s' % (rel(path), e)]
    if w != h or w not in (16, 32, 64) or depth != 8 or ctype != 6:
        return ['%s: must be a 16x16, 32x32 or 64x64 8-bit RGBA PNG (got %dx%d, depth %d, colour type %d)' % (rel(path), w, h, depth, ctype)]
    return []


def cmd_check(_args):
    icons, vanilla = load_catalogue(with_vanilla=True)
    problems = []
    for icon in active(icons):
        problems += png_problems(png_path(icon), icon['key'])
    for icon, style in missing_variants(icons):
        problems.append('missing variant PNG %s:%s (run art)' % (icon['key'], style))
    for icon in icons:
        for style in icon.get('variants', []):
            if style != VANILLA_STYLE and os.path.exists(variant_path(icon, style)):
                problems += png_problems(variant_path(icon, style), '%s:%s' % (icon['key'], style))
    for p in orphan_art(icons):
        problems.append('%s has no live catalogue entry' % rel(p))
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
    print('OK: %d custom icons (%d live), %d plain vanilla keys, %d generated files, zip about %d bytes'
          % (len(icons), len(active(icons)), len(vanilla), len(files), size))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='command')
    p_fetch = sub.add_parser('fetch-vanilla')
    p_fetch.add_argument('--jar', help='read the item definitions from this client jar instead of downloading 1.21.5')
    p_verify = sub.add_parser('verify-jars')
    p_verify.add_argument('--jar', action='append', help='a client jar to compare the fallbacks with (repeatable)')
    p_use = sub.add_parser('use')
    p_use.add_argument('key')
    p_use.add_argument('style')
    p_art = sub.add_parser('art')
    p_art.add_argument('--force-art', action='store_true', help='redraw every PNG (overwrites owner art!)')
    p_art.add_argument('--only', help='redraw just this key')
    sub.add_parser('pack')
    sub.add_parser('sheet')
    sub.add_parser('check')
    args = parser.parse_args()
    commands = {'fetch-vanilla': cmd_fetch_vanilla, 'verify-jars': cmd_verify_jars, 'use': cmd_use, 'art': cmd_art,
                'pack': cmd_pack, 'sheet': cmd_sheet, 'check': cmd_check}
    if args.command not in commands:
        parser.print_help()
        sys.exit(2)
    commands[args.command](args)


if __name__ == '__main__':
    main()
