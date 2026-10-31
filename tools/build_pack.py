#!/usr/bin/env python3
"""Build the Phoenix Java resource pack (RP1). Plain Python 3 + Pillow, no network.

  python3 tools/build_pack.py            # build both zips + pack-ids.yml into plugin resources
  python3 tools/build_pack.py --check    # build into a temp dir and fail if the committed output differs

Inputs (all generated art, never edited by hand):
  resourcepack/java/assets/phoenix/...      textures, crate models, badge fonts, sounds
  resourcepack/src/gui-art/ids_gui.json     icon / background / glyph ids
  resourcepack/src/gui-art/coverage.json    menu class -> background
  resourcepack/src/badge-font/ranks.json    badge look per LuckPerms group
  resourcepack/src/crate-art/hinges.json    crate lid hinge points
  resourcepack/src/shaders/text.fsh         shine overlay shader (26.3 / format 97 only)
  branding/skybornmc-logo-128.png           pack.png
Outputs (plugin/src/main/resources/pack/):
  phoenix-java-<version>-<sha8>.zip         "safe" pack: still badges, no shader
  phoenix-java-<version>-shine-<sha8>.zip   same + overlay with the moving badge shine
  pack-ids.yml                              everything the plugin needs to know about the pack

The zips are byte-for-byte reproducible (sorted entries, fixed timestamps, fixed attributes),
so an unchanged pack keeps its SHA-1 and clients keep their cached copy.
"""
import hashlib, io, json, os, re, sys, tempfile, zipfile
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
RP = os.path.join(ROOT, 'resourcepack')
SRC = os.path.join(RP, 'java')
OUT = os.path.join(ROOT, 'plugin', 'src', 'main', 'resources', 'pack')
VERSION = open(os.path.join(RP, 'VERSION')).read().strip()

FORMAT_MIN, FORMAT_MAX = 55, 97          # 1.21.5 .. 26.3 (26.3 is 97.1); keep in step with ViaVersion
SHINE_DIR = 'shine_97'                   # overlay only for format 97: the shader layout changes between versions
MENU_ASCENT = 13                         # RESOURCE_PACK_GUI §2: tune once on a client (RP3)
TITLE_OFFSET = 8
PANE_CARRIERS = ['black', 'red', 'orange', 'gray', 'lime', 'yellow', 'purple']
SPACE = {0xF801 + i: -(1 << i) for i in range(9)} | {0xF821 + i: (1 << i) for i in range(9)}
FIXED_TIME = (1980, 1, 1, 0, 0, 0)


def j(obj):
    return (json.dumps(obj, indent=1, ensure_ascii=True, sort_keys=False) + '\n').encode()


def load(rel):
    with open(os.path.join(RP, rel)) as f:
        return json.load(f)


def icon_ids(ids):
    """Every flat 2D icon in the pack: (custom_model_data string, texture path under phoenix:item/)."""
    out = [(f'menu/{b[0]}', f'menu/{b[0]}') for b in ids['buttons']]
    out += [(f'menu/icon/{f[0]}', f'menu/icon/{f[0]}') for f in ids['features']]
    out += [(f'menu/tab/{t[0]}', f'menu/tab/{t[0]}') for t in ids['tabs']]
    out += [(f'currency/{c[0]}', f'currency/{c[0]}') for c in ids['currencies']]
    return out


def crate_ids():
    d = os.path.join(SRC, 'assets/phoenix/models/item/crate/block')
    return sorted(f[:-5] for f in os.listdir(d) if f.endswith('.json') and '_' not in f)


def model_case(cmd, model):
    return {'when': cmd, 'model': {'type': 'minecraft:model', 'model': model}}


def items_json(vanilla, cases):
    fb = {'type': 'minecraft:model', 'model': f'minecraft:item/{vanilla}'}
    if not cases:
        return {'model': fb}
    return {'model': {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
                      'cases': cases, 'fallback': fb}}


def generate(ids):
    """Return {path in zip: bytes} for the safe pack (without the shine overlay)."""
    files = {}
    # 1. copy the generated art (textures, crate models, badge fonts, sounds)
    for dp, _, fs in os.walk(SRC):
        for f in fs:
            p = os.path.join(dp, f)
            rel = os.path.relpath(p, SRC).replace(os.sep, '/')
            if f.endswith('.png'):
                files[rel] = clean_png(p)
            else:
                with open(p, 'rb') as fh:
                    files[rel] = fh.read()

    # 2. 2D icon models + carriers
    icons = icon_ids(ids)
    for cmd, tex in icons:
        files[f'assets/phoenix/models/item/{tex}.json'] = j({'parent': 'minecraft:item/generated',
                                                             'textures': {'layer0': f'phoenix:item/{tex}'}})
    crates = crate_ids()
    for c in crates:
        files[f'assets/phoenix/models/item/crate/key/{c}.json'] = j({'parent': 'minecraft:item/generated',
                                                                    'textures': {'layer0': f'phoenix:item/crate/key/{c}'}})
    paper = [model_case(cmd, f'phoenix:item/{tex}') for cmd, tex in icons]
    for c in crates:
        for part in ('', '_body', '_lid'):
            paper.append(model_case(f'crate/block/{c}{part}', f'phoenix:item/crate/block/{c}{part}'))
        paper.append(model_case(f'crate/key/{c}', f'phoenix:item/crate/key/{c}'))
    files['assets/minecraft/items/paper.json'] = j(items_json('paper', paper))
    files['assets/minecraft/items/tripwire_hook.json'] = j(items_json(
        'tripwire_hook', [model_case(f'crate/key/{c}', f'phoenix:item/crate/key/{c}') for c in crates]))
    for col in PANE_CARRIERS:
        n = f'{col}_stained_glass_pane'
        files[f'assets/minecraft/items/{n}.json'] = j(items_json(n, [model_case('menu/blank', 'phoenix:item/menu/blank')]))

    # 3. fonts: icons, menu backgrounds, spaces
    files['assets/phoenix/font/icons.json'] = j({'providers': [
        {'type': 'bitmap', 'file': f'phoenix:font/icons/{g[0]}.png', 'ascent': 7, 'height': 9,
         'chars': [chr(int(g[1], 16))]} for g in ids['glyphs']]})
    menu = []
    for bg in ids['backgrounds']:
        name, cp, rows = bg[0], bg[1], bg[2]
        h = Image.open(os.path.join(SRC, f'assets/phoenix/textures/gui/menu/{name}.png')).size[1]
        assert h == 114 + rows * 18, (name, h, rows)
        menu.append({'type': 'bitmap', 'file': f'phoenix:gui/menu/{name}.png', 'ascent': MENU_ASCENT, 'height': h,
                     'chars': [chr(int(cp, 16))]})
    files['assets/phoenix/font/menu.json'] = j({'providers': menu})
    files['assets/phoenix/font/space.json'] = j({'providers': [
        {'type': 'space', 'advances': {chr(k): v for k, v in sorted(SPACE.items())}}]})

    # 4. sounds
    sounds = {}
    for dp, _, fs in os.walk(os.path.join(SRC, 'assets/phoenix/sounds')):
        for f in sorted(fs):
            rel = os.path.relpath(os.path.join(dp, f), os.path.join(SRC, 'assets/phoenix/sounds'))[:-4].replace(os.sep, '/')
            ev = rel.replace('/', '.')
            sounds[ev] = {'sounds': [{'name': f'phoenix:{rel}'}], 'subtitle': f'subtitles.phoenix.{ev}'}
    files['assets/phoenix/sounds.json'] = j(dict(sorted(sounds.items())))
    subs = {'ui.click': 'Menu clicks', 'ui.success': 'Success', 'ui.error': 'Not possible', 'crate.chime': 'Crate chimes'}
    files['assets/phoenix/lang/en_us.json'] = j({f'subtitles.phoenix.{k}': subs.get(k, k) for k in sorted(sounds)})

    # 5. pack.mcmeta + pack.png
    files['pack.png'] = clean_png(os.path.join(ROOT, 'branding/skybornmc-logo-128.png'))
    return files, icons, crates, sorted(sounds)


def mcmeta(shine):
    meta = {'pack': {'description': f'Phoenix Pack v{VERSION}', 'pack_format': FORMAT_MIN,
                     'supported_formats': [FORMAT_MIN, FORMAT_MAX], 'min_format': FORMAT_MIN, 'max_format': FORMAT_MAX}}
    if shine:
        meta['overlays'] = {'entries': [{'directory': SHINE_DIR, 'formats': [FORMAT_MAX, FORMAT_MAX],
                                         'min_format': FORMAT_MAX, 'max_format': FORMAT_MAX}]}
    return j(meta)


def clean_png(path):
    """RGBA, no metadata, deterministic bytes."""
    im = Image.open(path).convert('RGBA')
    b = io.BytesIO()
    im.save(b, 'PNG', optimize=True)
    return b.getvalue()


def check_shine_alpha(files):
    """Alpha 254 marks shine pixels for the shader: only the *_shine badge fonts may use it."""
    bad = []
    for p, data in files.items():
        drawn_as_text = '/textures/font/' in p or '/textures/gui/menu/' in p
        if p.endswith('.png') and drawn_as_text and not ('font/badge/' in p and 'shine' in p):
            if Image.open(io.BytesIO(data)).getchannel('A').histogram()[254]:
                bad.append(p)
    if bad:
        sys.exit('alpha 254 is reserved for the shine fonts, found in: ' + ', '.join(bad))


def zip_bytes(files):
    b = io.BytesIO()
    with zipfile.ZipFile(b, 'w') as z:
        for name in sorted(files):
            zi = zipfile.ZipInfo(name, FIXED_TIME)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zi.create_system = 3
            z.writestr(zi, files[name], compresslevel=9)
    return b.getvalue()


def yaml_str(s):
    return '"' + ''.join(c if 32 <= ord(c) < 127 and c not in '"\\' else
                         ('\\' + c if c in '"\\' else f'\\u{ord(c):04X}') for c in s) + '"'


def pack_ids(ids, icons, crates, sounds, zips):
    cov = load('src/gui-art/coverage.json')
    bgs = {b[0]: b for b in ids['backgrounds']}
    ranks = load('src/badge-font/ranks.json')
    hinges = load('src/crate-art/hinges.json')
    L = ['# GENERATED by tools/build_pack.py; do not edit. Everything the plugin knows about the pack.',
         f'version: {yaml_str(VERSION)}',
         'zips:']
    for variant, (name, sha) in zips.items():
        L += [f'  {variant}:', f'    file: {yaml_str(name)}', f'    sha1: {yaml_str(sha)}']
    L += ['menu:', f'  ascent: {MENU_ASCENT}', f'  title-offset: {TITLE_OFFSET}', '  title-color: "#E8DCC0"',
          '  width: 176', '  backgrounds:']
    for name, b in bgs.items():
        L.append(f'    {name}: {{char: {yaml_str(chr(int(b[1], 16)))}, rows: {b[2]}}}')
    L.append('  # menu class (module/SimpleClassName) -> background')
    L.append('  classes:')
    for m in sorted(cov, key=lambda m: (m['module'], m['class'])):
        assert m['bg'] in bgs, m
        L.append(f'    {m["module"]}/{m["class"]}: {m["bg"]}')
    L.append('icons:')
    L += [f'  - {yaml_str(cmd)}' for cmd, _ in icons]
    L += [f'  - {yaml_str(f"crate/key/{c}")}' for c in crates]
    L.append('glyphs:')
    L += [f'  {g[0]}: {yaml_str(chr(int(g[1], 16)))}' for g in ids['glyphs']]
    L += ['space:', '  font: "phoenix:space"']
    L += [f'  "{v}": {yaml_str(chr(k))}' for k, v in sorted(SPACE.items(), key=lambda kv: kv[1])]
    L += ['badges:', '  letter-gap: "\\uF801"', '  ranks:']
    for r in ranks:
        L.append(f'    {r["group"]}:')
        L.append(f'      font: {yaml_str(r["font"])}')
        L.append(f'      color: {yaml_str(r["color"] if isinstance(r["color"], str) else ",".join(r["color"]))}')
        if r.get('cap_color'):
            L.append(f'      cap-color: {yaml_str(r["cap_color"])}')
        L.append(f'      shine: {str(bool(r["shine"])).lower()}')
        L.append(f'      family: {yaml_str(r["family"])}')
        L.append(f'      bedrock: {yaml_str(r["bedrock"])}')
        L.append(f'      caps: [{yaml_str(r["caps"][0])}, {yaml_str(r["caps"][1])}]')
    L.append('sounds:')
    L += [f'  - {yaml_str("phoenix:" + s)}' for s in sounds]
    L += ['crates:', '  # item display: transform NONE, model centre (8,8,8) on the display origin; front = south (+z)']
    for c in crates:
        h = hinges.get(c, {'y': 8, 'z': 2})
        L.append(f'  {c}: {{model: "crate/block/{c}", body: "crate/block/{c}_body", lid: "crate/block/{c}_lid", '
                 f'hinge-y: {h["y"]}, hinge-z: {h["z"]}}}')
    return ('\n'.join(L) + '\n').encode()


def build(out):
    ids = load('src/gui-art/ids_gui.json')
    files, icons, crates, sounds = generate(ids)
    check_shine_alpha(files)
    safe = dict(files, **{'pack.mcmeta': mcmeta(False)})
    shine = dict(files, **{'pack.mcmeta': mcmeta(True)})
    with open(os.path.join(RP, 'src/shaders/text.fsh'), 'rb') as f:
        shine[f'{SHINE_DIR}/assets/minecraft/shaders/core/text.fsh'] = f.read()
    os.makedirs(out, exist_ok=True)
    for f in os.listdir(out):
        if re.match(r'phoenix-java-.*\.zip$', f):
            os.remove(os.path.join(out, f))
    zips = {}
    for variant, fs in (('safe', safe), ('shine', shine)):
        data = zip_bytes(fs)
        sha = hashlib.sha1(data).hexdigest()
        tag = '' if variant == 'safe' else 'shine-'
        name = f'phoenix-java-{VERSION}-{tag}{sha[:8]}.zip'
        with open(os.path.join(out, name), 'wb') as f:
            f.write(data)
        zips[variant] = (name, sha)
        size = len(data) / 1e6
        assert size <= 8, f'{name} is {size:.1f} MB (budget 8 MB)'
        print(f'{name}  {size:.2f} MB  sha1 {sha}  ({len(fs)} files)')
    with open(os.path.join(out, 'pack-ids.yml'), 'wb') as f:
        f.write(pack_ids(ids, icons, crates, sounds, zips))
    return zips


if __name__ == '__main__':
    if '--check' in sys.argv:
        with tempfile.TemporaryDirectory() as td:
            build(td)
            for f in sorted(os.listdir(td)):
                a = open(os.path.join(td, f), 'rb').read()
                p = os.path.join(OUT, f)
                if not os.path.exists(p) or open(p, 'rb').read() != a:
                    sys.exit(f'out of date: {f} (run python3 tools/build_pack.py)')
        print('pack up to date')
    else:
        build(OUT)
