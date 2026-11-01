#!/usr/bin/env python3
"""Phoenix frame layer (GUI-FONT, I7 v4): panels, plaques, soft tiles, cartoon lettering and the logo as a pack font.

Single source of truth: resourcepack/frame/panels.json. Python 3.9, standard library only. Run from anywhere.

  python3 tools/build_panels.py art      draws every MISSING sprite in resourcepack/frame/art/ as a placeholder
                                         (--force-art redraws all, --only <name> one; existing PNGs, e.g. owner art,
                                         are never touched otherwise)
  python3 tools/build_panels.py pack     composes the final glyph PNGs from the sprites (or takes
                                         resourcepack/frame/owner-final/<final>.png verbatim), writes the fonts
                                         phoenix:frame and phoenix:title and resourcepack/frame/frame-metrics.json
  python3 tools/build_panels.py sheet    resourcepack/preview/frame-sheet.png (a P6 menu with plaque, tiles and logo at
                                         3x, and the full letter set); only our own art, no game textures
  python3 tools/build_panels.py check    exits 1 when the committed tree differs from what art (missing only) + pack
                                         would write, or a rule fails (GUI-FONT 4.4)
  python3 tools/build_panels.py metrics  prints the advance table (for the spike log)

The pack zip itself is built by Gradle (modules/pack, task packZip); common bundles frame-metrics.json.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from icons_lib import draw, png  # noqa: E402
from frame_lib import lettering, logo, metrics, panels  # noqa: E402
from frame_lib import palette as P  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RP = os.path.join(ROOT, 'resourcepack')
FRAME = os.path.join(RP, 'frame')
CATALOGUE = os.path.join(FRAME, 'panels.json')
ART = os.path.join(FRAME, 'art')
OWNER = os.path.join(FRAME, 'owner-final')
METRICS = os.path.join(FRAME, 'frame-metrics.json')
PACK = os.path.join(RP, 'pack')
FONTS = os.path.join(PACK, 'assets', 'phoenix', 'font')
TEX_FRAME = os.path.join(PACK, 'assets', 'phoenix', 'textures', 'font', 'frame')
TEX_TITLE = os.path.join(PACK, 'assets', 'phoenix', 'textures', 'font', 'title')
SHEET = os.path.join(RP, 'preview', 'frame-sheet.png')
ICON_TEX = os.path.join(PACK, 'assets', 'phoenix', 'textures', 'item', 'icon')
SIZE_BUDGET = 250 * 1024   # whole pack tree (same budget as tools/build_icons.py)
PRIVATE = (0xE300, 0xE3FF)
SPACE = (0xF800, 0xF80F)
BEDROCK = (0xE000, 0xE1FF)


def rel(path):
    return os.path.relpath(path, ROOT)


def load_catalogue():
    with open(CATALOGUE, encoding='utf-8') as f:
        return json.load(f)


# ---------------------------------------------------------------------------------------------- sprites

def sprite_specs(cat):
    """name -> (width, height, draw function) of every sprite (resourcepack/frame/art/<name>.png)."""
    h = cat['plaque_height']
    L = cat['letters']
    rows = (len(L['chars']) + L['cols'] - 1) // L['cols']
    parts = lambda i: (lambda: panels.sprite_plaque_parts(h)[i])  # noqa: E731
    return {
        'panel_frame': (panels.FRAME, panels.FRAME, panels.sprite_panel_frame),
        'panel_cell': (panels.CELL, panels.CELL, panels.sprite_panel_cell),
        'tile': (panels.CELL, panels.CELL, panels.sprite_tile),
        'plaque_left': (panels.CAP, h, parts(0)),
        'plaque_mid': (panels.MID, h, parts(1)),
        'plaque_right': (panels.CAP, h, parts(2)),
        'letters': (L['cols'] * L['cell_w'], rows * L['art_h'],
                    lambda: lettering.sheet(L['chars'], L['cols'], L['cell_w'], L['cell_h'], L['art_h'])),
        'logo': (cat['logo']['width'], cat['logo']['height'], lambda: logo.sprite(cat['logo']['width'], cat['logo']['height'])),
    }


def encode(img):
    return png.encode(img.w, img.h, img.rows())


def decode(data):
    rows = png.decode_rgba(data)
    c = draw.Canvas(len(rows[0]), len(rows))
    c.px = [[tuple(p) if len(p) == 4 else tuple(p) + (255,) for p in row] for row in rows]
    return c


def read_png(path):
    with open(path, 'rb') as f:
        return decode(f.read())


def sprites(cat, memory_only=False):
    """name -> canvas: the committed sprite, or (missing) the placeholder. Wrong sizes stop the build."""
    out = {}
    for name, (w, h, fn) in sprite_specs(cat).items():
        path = os.path.join(ART, name + '.png')
        img = read_png(path) if os.path.exists(path) else fn()
        if (img.w, img.h) != (w, h):
            sys.exit('%s is %dx%d, must be %dx%d' % (rel(path), img.w, img.h, w, h))
        out[name] = img
    return out


def cmd_art(args):
    cat = load_catalogue()
    os.makedirs(ART, exist_ok=True)
    drawn = kept = 0
    for name, (w, h, fn) in sprite_specs(cat).items():
        path = os.path.join(ART, name + '.png')
        if args.only and name != args.only:
            continue
        if os.path.exists(path) and not args.force_art and not args.only:
            kept += 1
            continue
        img = fn()
        assert (img.w, img.h) == (w, h), name
        with open(path, 'wb') as f:
            f.write(encode(img))
        print('drew', rel(path))
        drawn += 1
    print('art: %d drawn, %d kept' % (drawn, kept))


# ---------------------------------------------------------------------------------------------- finals

def owner_final(name, w, h):
    path = os.path.join(OWNER, name + '.png')
    if not os.path.exists(path):
        return None
    img = read_png(path)
    if (img.w, img.h) != (w, h):
        sys.exit('%s is %dx%d, must be %dx%d' % (rel(path), img.w, img.h, w, h))
    return img


def build(cat):
    """Every generated file: {absolute path: bytes}, plus the canvases the sheet needs."""
    sp = sprites(cat)
    files = {}
    finals = {}
    for key, p in cat['panels'].items():
        h = panels.panel_height(cat, p['rows'])
        img = owner_final('panel_' + key, cat['window_width'], h) or panels.compose_panel(cat, p['rows'], sp['panel_frame'], sp['panel_cell'])
        finals['panel_' + key] = img
    ph = cat['plaque_height']
    for key, p in cat['plaques'].items():
        img = owner_final('plaque_' + key, p['width'], ph + 1) or panels.compose_plaque(
            p['width'], ph, sp['plaque_left'], sp['plaque_mid'], sp['plaque_right'])
        finals['plaque_' + key] = img
    finals['cell'] = owner_final('cell', panels.CELL, panels.CELL) or sp['tile']
    finals['logo'] = owner_final('logo', cat['logo']['width'], cat['logo']['height']) or sp['logo']
    L = cat['letters']
    rows = (len(L['chars']) + L['cols'] - 1) // L['cols']
    letters = owner_final('letters', L['cols'] * L['cell_w'], rows * L['cell_h']) or lettering.final_sheet(
        sp['letters'], L['chars'], L['cols'], L['cell_w'], L['cell_h'], L['art_h'])
    for name, img in finals.items():
        files[os.path.join(TEX_FRAME, name + '.png')] = encode(img)
    files[os.path.join(TEX_TITLE, 'letters.png')] = encode(letters)
    adv = lettering.advances(letters, L['chars'], L['cols'], L['cell_w'], L['cell_h'], cat['advance_pad'])
    panel_imgs = {k: finals['panel_' + k] for k in cat['panels']}
    plaque_imgs = {k: finals['plaque_' + k] for k in cat['plaques']}
    files[os.path.join(FONTS, 'frame.json')] = metrics.dumps(metrics.frame_font(
        cat, {k: v.h for k, v in panel_imgs.items()}, {k: v.h for k, v in plaque_imgs.items()})).encode()
    files[os.path.join(FONTS, 'title.json')] = metrics.dumps(metrics.title_font(cat)).encode()
    m = metrics.metrics(cat, panel_imgs, plaque_imgs, finals['cell'], finals['logo'], adv)
    files[METRICS] = metrics.dumps(m).encode()
    return files, finals, letters, m, sp


def cmd_pack(_args):
    cat = load_catalogue()
    files, *_ = build(cat)
    written = 0
    for path, data in sorted(files.items()):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if os.path.exists(path):
            with open(path, 'rb') as f:
                if f.read() == data:
                    continue
        with open(path, 'wb') as f:
            f.write(data)
        written += 1
    print('pack: %d files written, %d unchanged' % (written, len(files) - written))


# ---------------------------------------------------------------------------------------------- rules (GUI-FONT 4.4)

def tree_size(path):
    total = 0
    for base, _dirs, names in os.walk(path):
        for n in names:
            total += os.path.getsize(os.path.join(base, n))
    return total


def rules(cat, files, finals, letters, m):
    problems = []
    fonts = {}
    for name in ('frame.json', 'title.json'):
        fonts[name] = json.loads(files[os.path.join(FONTS, name)].decode())
    for fname, font in fonts.items():
        seen = set()
        for prov in font['providers']:
            chars = []
            if prov['type'] == 'space':
                chars = list(prov['advances'].keys())
            elif prov['type'] == 'bitmap':
                tex = prov['file'].split(':', 1)[1]
                path = os.path.join(PACK, 'assets', 'phoenix', 'textures', tex)
                if path not in files:
                    problems.append('%s: %s has no PNG' % (fname, prov['file']))
                if prov['ascent'] > prov['height']:
                    problems.append('%s: %s ascent %d above its height %d' % (fname, prov['file'], prov['ascent'], prov['height']))
                for row in prov['chars']:
                    chars.extend(c for c in row if c != '\u0000')
            for c in chars:
                code = ord(c)
                if fname == 'frame.json':
                    if not (PRIVATE[0] <= code <= PRIVATE[1] or SPACE[0] <= code <= SPACE[1]):
                        problems.append('frame.json: U+%04X outside E300..E3FF / F800..F80F' % code)
                    if BEDROCK[0] <= code <= BEDROCK[1]:
                        problems.append('frame.json: U+%04X is in Bedrock\'s emoji range' % code)
                if c in seen:
                    problems.append('%s: U+%04X defined twice' % (fname, code))
                seen.add(c)
    for name, img in list(finals.items()) + [('letters', letters)]:
        if img.w > 256 or img.h > 256:
            problems.append('%s is %dx%d, above 256' % (name, img.w, img.h))
    for key, p in cat['panels'].items():
        img = finals['panel_' + key]
        want = (cat['window_width'], p['rows'] * 18 + cat['rows_extra'])
        if (img.w, img.h) != want:
            problems.append('panel_%s is %dx%d, must be %dx%d' % (key, img.w, img.h, want[0], want[1]))
        clear = panels.corner_clear(cat, img.w, img.h)
        holes = [(x, y) for y in range(img.h) for x in range(img.w) if img.px[y][x][3] != 255 and (x, y) not in clear]
        if holes:
            problems.append('panel_%s has %d transparent pixels outside the vanilla corners (first %s)' % (key, len(holes), holes[0]))
    if cat['plaque_height'] + 1 < cat['title_baseline'] + cat['plaque_extend']:
        problems.append('plaque glyph height is below its ascent')
    L = cat['letters']
    if L['cell_h'] < 9 + cat['plaque_extend']:
        problems.append('lettering cell height %d < 9 + plaque_extend' % L['cell_h'])
    glyphs = {}
    for i, ch in enumerate(L['chars']):
        x0, y0 = (i % L['cols']) * L['cell_w'], (i // L['cols']) * L['cell_h']
        pix = frozenset((x, y, letters.px[y0 + y][x0 + x]) for y in range(L['cell_h']) for x in range(L['cell_w'])
                        if letters.px[y0 + y][x0 + x][3])
        allpix = [(x, y) for y in range(L['cell_h']) for x in range(L['cell_w']) if letters.px[y0 + y][x0 + x][3]]
        if not allpix:
            problems.append('letter %r is empty' % ch)
            continue
        width = max(x for x, _ in allpix) + 1
        if not 3 <= width <= 10:
            problems.append('letter %r is %d px wide (3..10)' % (ch, width))
        glyphs[ch] = frozenset(allpix), pix
    keys = list(glyphs)
    for i in range(len(keys)):
        for j in range(i + 1, len(keys)):
            a, b = glyphs[keys[i]][1], glyphs[keys[j]][1]
            diff = len({(x, y) for x, y, _c in a ^ b})
            if diff < 6:
                problems.append('letters %r and %r differ in only %d pixels' % (keys[i], keys[j], diff))
    lg = finals['logo']
    if lg.w > 96 or lg.h > 20:
        problems.append('logo is %dx%d, at most 96x20' % (lg.w, lg.h))
    size = tree_size(PACK)
    if size >= SIZE_BUDGET:
        problems.append('pack tree is %d bytes, budget %d' % (size, SIZE_BUDGET))
    return problems


def cmd_check(_args):
    cat = load_catalogue()
    missing = [n for n in sprite_specs(cat) if not os.path.exists(os.path.join(ART, n + '.png'))]
    files, finals, letters, m, _ = build(cat)
    problems = ['sprite %s.png is missing (run art)' % n for n in missing]
    for path, data in sorted(files.items()):
        if not os.path.exists(path):
            problems.append('%s is missing (run pack)' % rel(path))
            continue
        with open(path, 'rb') as f:
            if f.read() != data:
                problems.append('%s is not what pack writes (run pack)' % rel(path))
    problems += rules(cat, files, finals, letters, m)
    if problems:
        print('check FAILED:\n  ' + '\n  '.join(problems))
        sys.exit(1)
    print('OK: %d frame files, %d letters, pack tree %d bytes' % (len(files), len(cat['letters']['chars']), tree_size(PACK)))


def cmd_metrics(_args):
    cat = load_catalogue()
    _f, _finals, _l, m, _ = build(cat)
    for key, p in m['panels'].items():
        print('panel %s: %dx%d advance %d U+%s' % (key, p['width'], p['height'], p['advance'], p['cp']))
    for key, p in m['plaques'].items():
        print('plaque %s: width %d advance %d inner %d U+%s' % (key, p['width'], p['advance'], p['inner'], p['cp']))
    print('cell: advance %d, U+%s..U+%s' % (m['cell']['advance'], m['cell']['cp'][0], m['cell']['cp'][-1]))
    print('logo: advance %d' % m['logo']['wide']['advance'])
    print('letters: ' + ' '.join('%s=%d' % (k, v) for k, v in m['letters']['advance'].items()))


# ---------------------------------------------------------------------------------------------- sheet

def text_width(m, text):
    adv = m['letters']['advance']
    return sum(m['letters']['space'] if c == ' ' else adv.get(c, 0) for c in text)


def scene(cat, m, finals, letters, plaque_text, filled, rows=6):
    """A 1x mock of a framed menu as the client would draw it (panel, tiles, our item icons, plaque, lettering)."""
    top_pad = 12
    out = draw.Canvas(cat['window_width'] + 8, panels.panel_height(cat, rows) + top_pad + 4, draw.rgba('#2B2B33'))
    wx, wy = 4, top_pad
    out.blit(finals['panel_p%d' % rows], wx, wy)
    icons = sorted(f for f in os.listdir(ICON_TEX) if f.endswith('.png')) if os.path.isdir(ICON_TEX) else []
    for i, slot in enumerate(filled):
        r, c = divmod(slot, 9)
        out.blit(finals['cell'], wx + 7 + 18 * c, wy + 17 + 18 * r)
        if icons:
            out.blit(read_png(os.path.join(ICON_TEX, icons[i % len(icons)])), wx + 8 + 18 * c, wy + 18 + 18 * r)
    size = 's' if text_width(m, plaque_text) <= m['plaques']['s']['inner'] else ('m' if text_width(m, plaque_text) <= m['plaques']['m']['inner'] else 'l')
    pl = finals['plaque_' + size]
    px = (cat['window_width'] - pl.w) // 2
    out.blit(pl, wx + px, wy - cat['plaque_extend'])
    tx = px + (pl.w - text_width(m, plaque_text)) // 2
    L = cat['letters']
    for ch in plaque_text:
        if ch == ' ':
            tx += L['space']
            continue
        i = L['chars'].index(ch)
        cell = panels.crop(letters, (i % L['cols']) * L['cell_w'], (i // L['cols']) * L['cell_h'], L['cell_w'], L['cell_h'])
        out.blit(cell, wx + tx, wy + cat['title_baseline'] - L['ascent'])
        tx += m['letters']['advance'][ch]
    return out


def scale(img, k):
    out = draw.Canvas(img.w * k, img.h * k)
    out.blit(img, 0, 0, k)
    return out


def cmd_sheet(_args):
    cat = load_catalogue()
    _files, finals, letters, m, _ = build(cat)
    a = scene(cat, m, finals, letters, 'UPGRADES', [10, 11, 12, 13, 14, 15, 16, 20, 22, 24, 30, 32], 6)
    b = scene(cat, m, finals, letters, 'STONE & EARTH', [9, 11, 13, 15, 17, 19, 21, 23, 25], 4)
    c = scene(cat, m, finals, letters, 'SHOP', [11, 13, 15], 3)
    k = 3
    gap = 8
    width = (a.w + b.w + c.w + 2 * gap) * k
    lw = letters.w * 2
    height = max(a.h, b.h, c.h) * k + gap * k + letters.h * 4 + gap * k + finals['logo'].h * 4 + 16
    sheet = draw.Canvas(max(width, lw * 2), height, draw.rgba('#1E1E24'))
    sheet.blit(a, 0, 0, k)
    sheet.blit(b, (a.w + gap) * k, 0, k)
    sheet.blit(c, (a.w + b.w + 2 * gap) * k, 0, k)
    y = max(a.h, b.h, c.h) * k + gap * k
    bg = draw.Canvas(letters.w, letters.h, draw.rgba('#B8142B'))
    bg.blit(letters, 0, 0)
    sheet.blit(bg, 0, y, 4)
    y += letters.h * 4 + gap * k
    sheet.blit(finals['logo'], 0, y, 4)
    dark = draw.Canvas(finals['logo'].w, finals['logo'].h, draw.rgba('#F0E9D6'))
    dark.blit(finals['logo'], 0, 0)
    sheet.blit(dark, finals['logo'].w * 4 + 16, y, 4)
    os.makedirs(os.path.dirname(SHEET), exist_ok=True)
    with open(SHEET, 'wb') as f:
        f.write(encode(sheet))
    print('wrote %s (%dx%d)' % (rel(SHEET), sheet.w, sheet.h))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='cmd', required=True)
    p_art = sub.add_parser('art')
    p_art.add_argument('--force-art', action='store_true')
    p_art.add_argument('--only')
    for name in ('pack', 'sheet', 'check', 'metrics'):
        sub.add_parser(name)
    args = parser.parse_args()
    {'art': cmd_art, 'pack': cmd_pack, 'sheet': cmd_sheet, 'check': cmd_check, 'metrics': cmd_metrics}[args.cmd](args)


if __name__ == '__main__':
    main()
