#!/usr/bin/env python3
"""Owner preview of the icon set: ONE PNG atlas (icons + mock chest GUIs at 1x) and an HTML page that shows the atlas
scaled up with labels, every variant side by side. Developer tool only (not part of `check`, needs Pillow).

  python3 tools/icons_preview.py --out /path/to/feature-overview/inspiration [--jar client.jar] [--open]

Writes <out>/icons-v2-preview.png, <out>/icons-v2-preview.html and <out>/icons-v2-preview-3x.png (the atlas pre-scaled
3x, for a quick look without a browser). The atlas contains vanilla Minecraft textures read from a client jar
(default: the 1.21.5 jar cached by `build_icons.py fetch-vanilla`) so icons are judged next to real items:
**do not commit the preview files** (Mojang art, owner's eyes only).
"""
import argparse
import json
import os
import subprocess
import sys
import zipfile
from io import BytesIO

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from icons_lib import font5x7, sprites  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOGUE = os.path.join(ROOT, 'resourcepack', 'icons.json')
CACHE_JAR = os.path.join(ROOT, 'tools', 'icons_lib', '.cache', 'client-1.21.5.jar')
NAME = 'icons-v2-preview'

PANEL = (198, 198, 198, 255)
SLOT = (139, 139, 139, 255)
SLOT_DARK = (55, 55, 55, 255)
WHITE = (255, 255, 255, 255)
SHADOW = (85, 85, 85, 255)
BLACK = (0, 0, 0, 255)
TITLE = (64, 64, 64, 255)


# ---------------------------------------------------------------------------------------------- pixels
def canvas_image(canvas):
    im = Image.new('RGBA', (canvas.w, canvas.h))
    for y in range(canvas.h):
        for x in range(canvas.w):
            im.putpixel((x, y), tuple(canvas.px[y][x]))
    return im


def draw_text(im, s, x, y, colour=TITLE):
    for ch in s:
        g = font5x7.glyph(font5x7.FONT_5X7, ch)
        for gy, row in enumerate(g):
            for gx, c in enumerate(row):
                if c == '#':
                    im.putpixel((x + gx, y + gy), colour)
        x += len(g[0]) + 1


def paste(im, icon, x, y):
    im.alpha_composite(icon, (x, y))


def grey(icon):
    out = icon.copy()
    for y in range(icon.height):
        for x in range(icon.width):
            r, g, b, a = icon.getpixel((x, y))
            if a:
                v = int(0.299 * r + 0.587 * g + 0.114 * b)
                out.putpixel((x, y), (v, v, v, a))
    return out


def deutan(icon):
    """Rough red-green colour blindness (deuteranopia) simulation."""
    m = ((0.367, 0.861, -0.228), (0.280, 0.673, 0.047), (-0.012, 0.043, 0.969))
    out = icon.copy()
    for y in range(icon.height):
        for x in range(icon.width):
            r, g, b, a = icon.getpixel((x, y))
            if a:
                px = tuple(max(0, min(255, int(m[i][0] * r + m[i][1] * g + m[i][2] * b))) for i in range(3))
                out.putpixel((x, y), px + (a,))
    return out


# ---------------------------------------------------------------------------------------------- vanilla textures
class Vanilla:
    def __init__(self, jar_path):
        self.jar = zipfile.ZipFile(jar_path) if jar_path and os.path.exists(jar_path) else None
        self.cache = {}

    def tex(self, kind, name):
        if self.jar is None:
            return None
        path = 'assets/minecraft/textures/%s/%s.png' % (kind, name)
        try:
            return Image.open(BytesIO(self.jar.read(path))).convert('RGBA')
        except KeyError:
            return None

    def item(self, name):
        if name in self.cache:
            return self.cache[name]
        im = self.tex('item', {'compass': 'compass_16', 'clock': 'clock_00'}.get(name, name))
        if im is None:
            im = self.block(name)
        if im is None:
            im = Image.new('RGBA', (16, 16), (255, 0, 255, 255))   # visible: texture not found
        self.cache[name] = im.crop((0, 0, 16, 16))
        return self.cache[name]

    def block(self, name):
        faces = {
            'grass_block': ('grass_block_top', 'grass_block_side', 'grass_block_side'),
            'bookshelf': ('oak_planks', 'bookshelf', 'bookshelf'),
            'crafting_table': ('crafting_table_top', 'crafting_table_front', 'crafting_table_side'),
            'oak_log': ('oak_log_top', 'oak_log', 'oak_log'),
            'gold_block': ('gold_block',) * 3,
            'stone_bricks': ('stone_bricks',) * 3,
            'emerald_block': ('emerald_block',) * 3,
            'quartz_block': ('quartz_block_top', 'quartz_block_side', 'quartz_block_side'),
            'tnt': ('tnt_top', 'tnt_side', 'tnt_side'),
            'barrel': ('barrel_top', 'barrel_side', 'barrel_side'),
            'enchanting_table': ('enchanting_table_top', 'enchanting_table_side', 'enchanting_table_side'),
        }.get(name)
        if not faces:
            return None
        top, left, right = (self.tex('block', f) for f in faces)
        if top is None or left is None or right is None:
            return None
        tint = (121, 192, 90) if name == 'grass_block' else None
        if tint:
            top = multiply(top, tint)
        sides = left
        if name == 'grass_block':
            overlay = self.tex('block', 'grass_block_side_overlay')
            if overlay is not None:
                sides = left.copy()
                sides.alpha_composite(multiply(overlay, tint))
            right = sides
        return iso_cube(top, sides, right)


def multiply(im, rgb):
    out = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            out.putpixel((x, y), (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a))
    return out


def iso_cube(top, left, right):
    """The isometric inventory look of a block in 16x16 (top bright, left medium, right dark)."""
    out = Image.new('RGBA', (16, 16))
    T, R, B, L = (8, 1), (15, 4.5), (8, 8), (1, 4.5)
    L2, R2, B2 = (1, 11.5), (15, 11.5), (8, 15)

    def solve(p, origin, e1, e2):
        a, b = e1[0] - origin[0], e1[1] - origin[1]
        c, d = e2[0] - origin[0], e2[1] - origin[1]
        det = a * d - b * c
        px, py = p[0] - origin[0], p[1] - origin[1]
        return (px * d - py * c) / det, (a * py - b * px) / det

    faces = ((top, T, R, L, 1.0), (left, L, B, L2, 0.78), (right, B, R, B2, 0.58))
    for y in range(16):
        for x in range(16):
            p = (x + .5, y + .5)
            for tex, origin, e1, e2, shade in faces:
                u, v = solve(p, origin, e1, e2)
                if 0 <= u < 1 and 0 <= v < 1:
                    r, g, b, a = tex.getpixel((min(15, int(u * 16)), min(15, int(v * 16))))
                    if a:
                        out.putpixel((x, y), (int(r * shade), int(g * shade), int(b * shade), 255))
                    break
    return out


# ---------------------------------------------------------------------------------------------- mock chest GUI
def bevel(im, x0, y0, x1, y1, fill, tl, br):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.putpixel((x, y), fill)
    for x in range(x0, x1 + 1):
        im.putpixel((x, y0), tl)
        im.putpixel((x, y1), br)
    for y in range(y0, y1 + 1):
        im.putpixel((x0, y), tl)
        im.putpixel((x1, y), br)


def gui(rows, title, slots, hotbar, vanilla):
    """A vanilla-looking generic chest screen (176 wide) with the player inventory and hotbar below.
    ``slots`` {slot index: 16x16 image}; ``hotbar`` {0..8: image}."""
    h = 114 + rows * 18
    im = Image.new('RGBA', (176, h), (0, 0, 0, 0))
    bevel(im, 0, 0, 175, h - 1, PANEL, WHITE, SHADOW)
    for x, y in ((0, 0), (175, 0), (0, h - 1), (175, h - 1)):
        im.putpixel((x, y), (0, 0, 0, 0))
    draw_text(im, title, 8, 6)
    for r in range(rows):
        for c in range(9):
            sx, sy = 7 + c * 18, 17 + r * 18
            bevel(im, sx, sy, sx + 17, sy + 17, SLOT, SLOT_DARK, WHITE)
            icon = slots.get(r * 9 + c)
            if icon is not None:
                paste(im, icon, sx + 1, sy + 1)
    inv_top = 17 + rows * 18 + 13
    draw_text(im, 'INVENTORY', 8, inv_top - 10)
    for r in range(3):
        for c in range(9):
            sx, sy = 7 + c * 18, inv_top + r * 18
            bevel(im, sx, sy, sx + 17, sy + 17, SLOT, SLOT_DARK, WHITE)
    for c in range(9):
        sx, sy = 7 + c * 18, inv_top + 58
        bevel(im, sx, sy, sx + 17, sy + 17, SLOT, SLOT_DARK, WHITE)
        if c in hotbar:
            paste(im, hotbar[c], sx + 1, sy + 1)
    return im


def slot_cell(icon):
    """One icon on a vanilla slot (18x18)."""
    im = Image.new('RGBA', (18, 18))
    bevel(im, 0, 0, 17, 17, SLOT, SLOT_DARK, WHITE)
    paste(im, icon, 1, 1)
    return im


# ---------------------------------------------------------------------------------------------- atlas
class Atlas:
    def __init__(self, width=1056):
        self.w = width
        self.items = []     # (name, image)
        self.rects = {}

    def add(self, name, im):
        self.items.append((name, im))
        return name

    def build(self):
        x = y = rowh = 0
        placed = []
        for name, im in self.items:
            if x + im.width > self.w:
                x, y, rowh = 0, y + rowh + 2, 0
            placed.append((name, im, x, y))
            self.rects[name] = (x, y, im.width, im.height)
            x += im.width + 2
            rowh = max(rowh, im.height)
        sheet = Image.new('RGBA', (self.w, y + rowh), (0, 0, 0, 0))
        for name, im, px, py in placed:
            sheet.paste(im, (px, py))
        return sheet


def load_catalogue():
    with open(CATALOGUE, encoding='utf-8') as f:
        return json.load(f)


# ---------------------------------------------------------------------------------------------- the page
STYLE_NAMES = {
    'nav': {'A': 'A · chunky arrows', 'B': 'B · door and pointing gloves', 'C': 'C · chevron buttons'},
    'yn': {'A': 'A · tick and cross', 'B': 'B · thumbs up and down', 'C': 'C · lime and red round buttons'},
    'shop': {'A': 'A · coin bag', 'B': 'B · price tags + and -', 'C': 'C · cart and coins'},
}


def build(args):
    cat = load_catalogue()
    live = {i['key']: i.get('style') for i in cat['icons']}
    vanilla = Vanilla(args.jar if args.jar else CACHE_JAR)
    atlas = Atlas()

    def custom(key, style=None):
        if key in sprites.SINGLE:
            fn = sprites.SINGLE[key]
        else:
            fn = sprites.VARIANTS[key][style or live[key]]
        return canvas_image(fn().canvas())

    def v(name):
        return vanilla.item(name)

    # --- icon cells (16x16, shown big by the page)
    for key, fn in sprites.SINGLE.items():
        atlas.add('i:' + key, custom(key))
    for key, styles in sprites.VARIANTS.items():
        for style in styles:
            atlas.add('i:%s:%s' % (key, style), custom(key, style))
    vn = ['grass_block', 'iron_pickaxe', 'compass', 'emerald', 'experience_bottle', 'enchanting_table', 'name_tag', 'totem_of_undying',
          'bookshelf', 'crafting_table', 'oak_log', 'diamond', 'gold_ingot', 'nether_star', 'ender_pearl', 'writable_book',
          'iron_sword', 'bread', 'book', 'iron_chestplate', 'golden_apple', 'spyglass', 'oak_sign', 'filled_map', 'campfire',
          'iron_door', 'repeater', 'knowledge_book', 'ender_eye', 'clock', 'gold_nugget']
    for name in vn:
        atlas.add('v:' + name, v(name))
    for key in ('currency/money', 'currency/shards', 'currency/crystals', 'currency/credits'):
        atlas.add('g:' + key, grey(custom(key)))
        atlas.add('d:' + key, deutan(custom(key)))

    # --- hotbar star options
    hot = {0: v('iron_sword'), 1: v('iron_pickaxe'), 2: v('bread'), 3: v('golden_apple')}
    star_vanilla = v('nether_star')
    star_ember = custom('menu', 'ember')
    for tag, star in (('vanilla', star_vanilla), ('ember', star_ember)):
        im = Image.new('RGBA', (176, 22), (0, 0, 0, 0))
        bevel(im, 0, 0, 175, 21, PANEL, WHITE, SHADOW)
        for c in range(9):
            sx = 7 + c * 18 - 1 + 1
            bevel(im, sx - 0, 2, sx + 17, 19, SLOT, SLOT_DARK, WHITE)
            icon = hot.get(c)
            if c == 8:
                icon = star
            if icon is not None:
                paste(im, icon, sx + 1, 3)
        atlas.add('s:hotbar:' + tag, im)

    # --- scenes
    base_hot = {0: v('iron_sword'), 1: v('iron_pickaxe'), 2: v('bread'), 8: star_vanilla}

    def scene_nav(style):
        slots = {10: v('emerald'), 11: v('diamond'), 12: v('gold_ingot'), 13: v('oak_log'), 14: v('bookshelf'), 15: v('grass_block'),
                 16: v('crafting_table'),
                 18: custom('nav/back', style), 21: custom('nav/prev', style), 22: custom('nav/close', style),
                 23: custom('nav/next', style)}
        return gui(3, 'Shop - Wood 2/3', slots, base_hot, vanilla)

    def scene_yn(style):
        slots = {11: custom('state/confirm', style), 13: v('diamond'), 15: custom('state/cancel', style)}
        return gui(3, 'Are you sure?', slots, base_hot, vanilla)

    def scene_shop(style):
        slots = {4: v('emerald'), 11: custom('shop/buy', style), 13: v('oak_log'), 15: custom('shop/sell', style),
                 20: custom('shop/fill'), 22: custom('ui/type'), 24: custom('shop/sell_all', style)}
        return gui(3, 'Shop - Buy Oak Log', slots, base_hot, vanilla)

    for style in ('A', 'B', 'C'):
        atlas.add('x:nav:' + style, scene_nav(style))
        atlas.add('x:yn:' + style, scene_yn(style))
        atlas.add('x:shop:' + style, scene_shop(style))

    hub = {10: v('grass_block'), 11: v('iron_pickaxe'), 12: v('compass'), 13: v('emerald'), 14: custom('feature/bank'),
           15: custom('feature/auction'), 16: custom('feature/top'),
           19: v('experience_bottle'), 20: v('enchanting_table'), 21: v('totem_of_undying'), 22: v('writable_book'),
           23: v('bookshelf'), 24: custom('currency/credits'), 25: v('name_tag'),
           28: custom('currency/money'), 29: custom('currency/crystals'), 30: custom('currency/shards'),
           32: custom('nav/help'), 33: custom('ui/info'), 34: custom('nav/danger')}
    atlas.add('x:hub', gui(4, 'Menu', hub, base_hot, vanilla))
    toggles = {10: custom('state/on'), 11: custom('state/off'), 12: custom('state/locked'), 13: custom('state/undo'),
               14: custom('nav/search'), 15: v('oak_sign'), 16: v('filled_map')}
    atlas.add('x:misc', gui(3, 'Settings and more', toggles, base_hot, vanilla))

    sheet = atlas.build()
    os.makedirs(args.out, exist_ok=True)
    png_path = os.path.join(args.out, NAME + '.png')
    sheet.save(png_path)
    sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST).save(os.path.join(args.out, NAME + '-3x.png'))
    html = page(atlas, sheet, live, cat)
    html_path = os.path.join(args.out, NAME + '.html')
    with open(html_path, 'w', encoding='utf-8') as f:
        f.write(html)
    print('wrote', png_path, sheet.size)
    print('wrote', html_path)
    return html_path


def page(atlas, sheet, live, cat):
    R = atlas.rects
    SC = 6

    def crop(name, scale, extra_style='', cls=''):
        x, y, w, h = R[name]
        return ('<span class="px %s" style="width:%dpx;height:%dpx;background-position:-%dpx -%dpx;'
                'background-size:%dpx %dpx;%s"></span>'
                % (cls, w * scale, h * scale, x * scale, y * scale, sheet.width * scale, sheet.height * scale, extra_style))

    def cell(key, label, picked=False, style=None, sub=''):
        name = 'i:' + key + (':' + style if style else '')
        star = '<b class="pick">&#9733; my pick</b>' if picked else ''
        return ('<figure class="cell">%s<figcaption><span class="k">%s</span>%s%s</figcaption></figure>'
                % (crop(name, SC, cls='slot'), label, ('<br><small>%s</small>' % sub) if sub else '', star))

    def vcell(name, label):
        return ('<figure class="cell">%s<figcaption><span class="k">%s</span><br><small>plain vanilla item</small></figcaption></figure>'
                % (crop('v:' + name, SC, cls='slot'), label))

    def scene(name, scale=3):
        return '<div class="scene">%s</div>' % crop(name, scale)

    def group(title, intro, group_key, keys, labels, style_kind, scene_name):
        out = ['<section><h2>%s</h2><p class="intro">%s</p>' % (title, intro)]
        for style in ('A', 'B', 'C'):
            default = live[keys[0]] == style
            out.append('<div class="style"><h3>%s%s</h3><div class="row">' % (STYLE_NAMES[style_kind][style],
                       ' <b class="pick">&#9733; my pick</b>' if default else ''))
            for key, label in zip(keys, labels):
                out.append(cell(key, label, style=style))
            out.append('</div>')
            out.append(scene('x:%s:%s' % (scene_name, style)))
            out.append('</div>')
        out.append('</section>')
        return ''.join(out)

    p = []
    p.append('''<!doctype html><html><head><meta charset="utf-8"><title>Phoenix icons v2 preview</title><style>
body{margin:0;background:#23232b;color:#eee;font:15px/1.45 -apple-system,Segoe UI,Helvetica,Arial,sans-serif}
header,section{max-width:1700px;margin:0 auto;padding:18px 28px}
h1{margin:0 0 6px;color:#ff5a4d;font-size:30px}h2{margin:26px 0 4px;color:#ffd21a;border-bottom:2px solid #ff2d3f;padding-bottom:4px}
h3{margin:16px 0 6px;color:#fff}.intro{color:#bbb;margin:2px 0 8px;max-width:1000px}
.px{display:inline-block;image-rendering:pixelated;background-image:url(%s.png);background-repeat:no-repeat;vertical-align:top}
.slot{background-color:#8b8b8b;box-shadow:inset 3px 3px 0 #373737,inset -3px -3px 0 #fff;padding:0}
.row{display:flex;flex-wrap:wrap;gap:14px;margin-bottom:10px}.style{display:inline-block;vertical-align:top;margin:0 14px 14px 0}
.cell{margin:0;text-align:center;width:112px}.cell figcaption{font-size:13px;margin-top:4px}.k{font-weight:600}
small{color:#9a9aa6}.pick{display:block;color:#4dff6a;font-size:12px}h3 .pick{display:inline;margin-left:8px}
.panel{background:#c6c6c6;padding:14px 18px;display:inline-block;border:3px solid #fff;border-right-color:#555;border-bottom-color:#555;color:#222}
.panel small{color:#555}.scene{display:inline-block;margin:6px 0}
.grid{display:flex;flex-wrap:wrap;gap:6px}.note{background:#2f2f3a;border-left:4px solid #ff2d3f;padding:8px 14px;margin:12px 0;max-width:1000px}
code{background:#1a1a20;padding:1px 5px;border-radius:3px}table{border-collapse:collapse}td,th{padding:3px 10px;border-bottom:1px solid #444;text-align:left}
</style></head><body><header><h1>Phoenix Skyblock: icon set v2 preview</h1>
<p class="intro">Everything is drawn 16x16 like a vanilla item and shown here at 6x (icons) or 3x (mock chest screens). One dark outline,
bright flat colours, one highlight and one shade, chunky 2 px strokes, phoenix/ember palette. Only things that are <b>not</b> a real
Minecraft item are custom; everything with a good vanilla item stays that vanilla item (see <code>docs/design/ICON_MAP.md</code>).</p>
<div class="note"><b>How to answer:</b> one line, e.g. <code>nav B, confirm C, shop C, hotbar vanilla</code> (or "nav A except close C").
The generator switches by name: <code>python3 tools/build_icons.py use nav/back B</code>. The &#9733; marks my pick.</div></header>''' % NAME)

    # 1 hotbar
    p.append('<section><h2>1 · The hotbar Menu item</h2><p class="intro">Today it is a yellow star that replaces the Nether Star. '
             'Two natural options, both on the real Nether Star item. <b>Vanilla</b> needs no pack file at all, a Bedrock phone and a Java client look identical. '
             '<b>Ember</b> is my own drawing in the vanilla style with a tiny flame in its heart (Java with the pack only).</p><div class="row">')
    for tag, label, sub, pick in (('vanilla', 'Option 1: plain vanilla Nether Star', 'no custom model, nothing to download', True),
                                  ('ember', 'Option 2: vanilla look + ember', 'own pixels, a tiny flame at the heart', False)):
        star = 'v:nether_star' if tag == 'vanilla' else 'i:menu:ember'
        p.append('<div class="style"><h3>%s%s</h3><div class="row">%s</div>%s<br><small>%s</small></div>'
                 % (label, ' <b class="pick">&#9733; my pick</b>' if pick else '', cell_raw(crop, star, SC),
                    crop('s:hotbar:' + tag, 4), sub))
    p.append('</div></section>')

    # 2 currencies
    p.append('<section><h2>2 · Currencies: four silhouettes, four colours</h2><p class="intro">Round coin, three prism crystals, cut gem, five-point star. '
             'Below: the same four in grey and as a red-green colour-blind player sees them, so you can check that no two look alike.</p><div class="row">')
    for key, label, sub in (('currency/money', 'Money', 'green coin with $'), ('currency/shards', 'Shards', 'pink prism crystals'),
                            ('currency/crystals', 'Crystals', 'aqua cut gem'), ('currency/credits', 'Credits', 'orange star')):
        p.append(cell(key, label, sub=sub))
    p.append('</div><div class="row">')
    for prefix, label in (('g', 'grey'), ('d', 'colour-blind (red-green)')):
        p.append('<div><small>%s</small><br>' % label)
        for key in ('currency/money', 'currency/shards', 'currency/crystals', 'currency/credits'):
            p.append(crop('%s:%s' % (prefix, key), 4, cls='slot') + ' ')
        p.append('</div>')
    p.append('</div></section>')

    p.append(group('3 · Navigation: Back, Previous, Next, Close',
                   'Pick one family (they sit on the same bottom row). Mock 3-row menu on the right of each.',
                   'nav', ['nav/back', 'nav/prev', 'nav/next', 'nav/close'], ['Back', 'Previous', 'Next', 'Close'], 'nav', 'nav'))
    p.append(group('4 · Confirm and Cancel', 'Used on every confirm screen and the number picker.',
                   'yn', ['state/confirm', 'state/cancel'], ['Confirm', 'Cancel'], 'yn', 'yn'))
    p.append(group('5 · Buy, Sell, Sell all', 'No arrows. Buy is the green family, Sell the warm family. Fill inventory and Type an amount are single icons (shown in the mock).',
                   'shop', ['shop/buy', 'shop/sell', 'shop/sell_all'], ['Buy', 'Sell', 'Sell all'], 'shop', 'shop'))

    p.append('<section><h2>6 · The other custom icons (one look each)</h2><div class="row">')
    for key, label in (('nav/danger', 'Danger'), ('nav/help', 'Help'), ('ui/info', 'Info'), ('nav/search', 'Search (auction)'),
                       ('state/locked', 'Locked'), ('state/undo', 'Undo'), ('state/on', 'Switch on'), ('state/off', 'Switch off'),
                       ('shop/fill', 'Fill inventory'), ('ui/type', 'Type an amount'), ('feature/bank', 'Bank'),
                       ('feature/top', 'Top'), ('feature/auction', 'Auction House')):
        p.append(cell(key, label))
    p.append('</div>%s%s</section>' % (scene('x:hub'), scene('x:misc')))

    p.append('<section><h2>7 · Plain vanilla items (no custom icon, no letter tile)</h2><p class="intro">These stay the real Minecraft item. '
             'The full list with the exact material per menu key is <code>docs/design/ICON_MAP.md</code>. A few of them, for scale:</p><div class="row">')
    for name, label in (('grass_block', 'Island'), ('iron_pickaxe', 'The Depths'), ('compass', 'Travel'), ('emerald', 'Shop'),
                        ('experience_bottle', 'Skills'), ('enchanting_table', 'Enchanter'), ('name_tag', 'Titles'),
                        ('totem_of_undying', 'Achievements'), ('bookshelf', 'Collection'), ('writable_book', 'Quests')):
        p.append(vcell(name, label))
    p.append('</div></section></body></html>')
    return ''.join(p)


def cell_raw(crop, name, scale):
    return '<figure class="cell">%s</figure>' % crop(name, scale, cls='slot')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--out', required=True, help='directory for the preview files')
    ap.add_argument('--jar', help='client jar for vanilla textures (default: the cached 1.21.5 jar)')
    ap.add_argument('--open', action='store_true', help='open the HTML page (macOS)')
    args = ap.parse_args()
    html = build(args)
    if args.open:
        subprocess.call(['open', html])


if __name__ == '__main__':
    main()
