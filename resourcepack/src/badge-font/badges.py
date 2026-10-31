"""Rank badges as a FONT, so the rank name stays editable in LuckPerms.

The name typed in LuckPerms (group display name) is drawn letter by letter in font `phoenix:badge`:
every letter glyph carries its own piece of the plate, so any text becomes a badge. The text colour tints
the glyph (Minecraft multiplies glyph pixels by the text colour), so one font serves every rank colour.

Fonts written:
  phoenix:badge        dark plate, glowing letters (Phoenix Flame style, players)
  phoenix:badge_shine  the same, every pixel at alpha 254: the pack's text shader adds the moving shine
  phoenix:badge_solid  light plate, dark letters (staff)
Each font: A-Z (a-z map to the same caps), 0-9, space and + - ! ' . plus end caps on private chars and a
-1 px space (U+F801) that the plugin puts between letters so the plate has no gaps.

usage (repo root): python3 resourcepack/src/badge-font/badges.py
"""
import json, os, sys
from PIL import Image

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '../../..'))
FONT_DIR = os.path.join(ROOT, 'resourcepack/java/assets/phoenix/font')
TEX_DIR = os.path.join(ROOT, 'resourcepack/java/assets/phoenix/textures/font/badge')
PREVIEW = os.path.join(ROOT, 'resourcepack/src/preview')

LETTERS = {
    'A': ['010', '101', '111', '101', '101'], 'B': ['110', '101', '110', '101', '110'], 'C': ['011', '100', '100', '100', '011'],
    'D': ['110', '101', '101', '101', '110'], 'E': ['111', '100', '110', '100', '111'], 'F': ['111', '100', '110', '100', '100'],
    'G': ['011', '100', '101', '101', '011'], 'H': ['101', '101', '111', '101', '101'], 'I': ['111', '010', '010', '010', '111'],
    'J': ['001', '001', '001', '101', '010'], 'K': ['101', '101', '110', '101', '101'], 'L': ['100', '100', '100', '100', '111'],
    'M': ['10001', '11011', '10101', '10001', '10001'], 'N': ['1001', '1101', '1011', '1001', '1001'], 'O': ['010', '101', '101', '101', '010'],
    'P': ['110', '101', '110', '100', '100'], 'Q': ['010', '101', '101', '110', '011'], 'R': ['110', '101', '110', '101', '101'],
    'S': ['011', '100', '010', '001', '110'], 'T': ['111', '010', '010', '010', '010'], 'U': ['101', '101', '101', '101', '111'],
    'V': ['101', '101', '101', '101', '010'], 'W': ['10001', '10001', '10101', '11011', '10001'], 'X': ['101', '101', '010', '101', '101'],
    'Y': ['101', '101', '010', '010', '010'], 'Z': ['111', '001', '010', '100', '111'],
    '0': ['111', '101', '101', '101', '111'], '1': ['010', '110', '010', '010', '111'], '2': ['111', '001', '111', '100', '111'],
    '3': ['111', '001', '011', '001', '111'], '4': ['101', '101', '111', '001', '001'], '5': ['111', '100', '111', '001', '111'],
    '6': ['111', '100', '111', '101', '111'], '7': ['111', '001', '010', '010', '010'], '8': ['111', '101', '111', '101', '111'],
    '9': ['111', '101', '111', '001', '111'], '+': ['000', '010', '111', '010', '000'], '-': ['000', '000', '111', '000', '000'],
    '!': ['1', '1', '1', '0', '1'], "'": ['1', '1', '0', '0', '0'], '.': ['0', '0', '0', '0', '1'], ' ': ['0'],
}

# grey levels: Minecraft multiplies them by the text colour, so 255 = the rank colour itself
DARK = {'rimT': (168, 168, 168, 255), 'rimB': (104, 104, 104, 255), 'plate': (30, 25, 25, 255), 'glow': (58, 46, 46, 255), 'hi': (255, 255, 255, 255), 'lo': (214, 214, 214, 255),
        'F': (255, 255, 255, 255), 'f': (190, 190, 190, 255)}
SOLID = {'rim': (90, 90, 90, 255), 'top': (255, 255, 255, 255), 'base': (226, 226, 226, 255), 'shade': (190, 190, 190, 255), 'ink': (36, 26, 26, 255)}

# end caps (left; the right cap is the mirror). A/a = rim light/dark, D = plate, F/f = flame bright/mid, . = empty
CAPS = {
    'plain': ['.A', 'AD', 'AD', 'AD', 'aD', 'aD', 'aD', 'aD', '.a'],
    'ember': ['...A', '..AD', '..AD', '.fAD', 'FFAD', '.faD', '..aD', '..aD', '...a'],
    'wing': ['Ff..A', '.FfAD', '..fAD', '...AD', '...AD', '...aD', '..faD', '.FfaD', 'Ff..a'],
    'sun': ['.f...A', '..F.AD', '...fAD', '...fAD', 'fFFFAD', '...faD', '...faD', '..F.aD', '.f...a'],
    'phoenix': ['Ff...A', '.Ff.AD', 'f..fAD', '.FffAD', 'FFFFAD', '.FffaD', 'f..faD', '.Ff.aD', 'Ff...a'],
    'ribbon': ['AAAA', '.ADD', '..AD', '...D', '...D', '...D', '..aD', '.aDD', 'aaaa'],
}
SOLID_CAP = ['KK', 'KH', 'KB', 'KB', 'KB', 'KB', 'KB', 'KS', 'KK']
CAP_CHARS = {'plain': (0xE000, 0xE001), 'ember': (0xE002, 0xE003), 'wing': (0xE004, 0xE005), 'sun': (0xE006, 0xE007),
             'phoenix': (0xE008, 0xE009), 'ribbon': (0xE00A, 0xE00B), 'square': (0xE00E, 0xE00F)}
NEG1 = 0xF801  # -1 px advance, between every two badge glyphs

def letter_glyph(ch, style):
    m = LETTERS[ch]; lw = len(m[0]); w = lw + 1  # one plate column after each letter
    img = Image.new('RGBA', (w, 9), (0, 0, 0, 0))
    for x in range(w):
        if style == 'solid':
            img.putpixel((x, 0), SOLID['rim']); img.putpixel((x, 8), SOLID['rim'])
            for y in range(1, 8): img.putpixel((x, y), SOLID['top'] if y == 1 else SOLID['shade'] if y == 7 else SOLID['base'])
        else:
            img.putpixel((x, 0), DARK['rimT']); img.putpixel((x, 8), DARK['rimB'])
            for y in range(1, 8): img.putpixel((x, y), DARK['glow'] if y in (1, 7) else DARK['plate'])
    for j, row in enumerate(m):
        for i, b in enumerate(row):
            if b == '1': img.putpixel((i, 2 + j), SOLID['ink'] if style == 'solid' else (DARK['hi'] if j < 3 else DARK['lo']))
    return img

def cap_glyph(kind, style, right=False):
    rows = SOLID_CAP if style == 'solid' else CAPS[kind]
    w = len(rows[0]); img = Image.new('RGBA', (w, 9), (0, 0, 0, 0))
    pal = {'K': SOLID['rim'], 'H': SOLID['top'], 'B': SOLID['base'], 'S': SOLID['shade']} if style == 'solid' else \
          {'A': DARK['rimT'], 'a': DARK['rimB'], 'D': DARK['plate'], 'F': DARK['F'], 'f': DARK['f']}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal: img.putpixel(((w - 1 - x) if right else x, y), pal[ch])
    return img

def build(style, alpha=255):
    """Return {char: Image} for one font."""
    gl = {}
    for ch in LETTERS: gl[ch] = letter_glyph(ch, style)
    for kind, (l, r) in CAP_CHARS.items():
        if style == 'solid' and kind != 'square': continue
        if style != 'solid' and kind == 'square': continue
        gl[chr(l)] = cap_glyph(kind, style); gl[chr(r)] = cap_glyph(kind, style, right=True)
    if alpha != 255:
        for k, im in gl.items():
            px = im.load()
            for y in range(im.height):
                for x in range(im.width):
                    r, g, b, a = px[x, y]
                    if a: px[x, y] = (r, g, b, alpha)
    return gl

def write_font(name, gl):
    """Grid PNG (cell 6x9, left aligned) + font JSON with a bitmap provider per row and the -1 px space."""
    chars = list(gl.keys()); per_row = 16; cw = 6
    rows = [chars[i:i + per_row] for i in range(0, len(chars), per_row)]
    img = Image.new('RGBA', (per_row * cw, len(rows) * 9), (0, 0, 0, 0))
    for r, row in enumerate(rows):
        for c, ch in enumerate(row): img.paste(gl[ch], (c * cw, r * 9))
    os.makedirs(TEX_DIR, exist_ok=True); img.save(os.path.join(TEX_DIR, name + '.png'))
    def pad(row): return ''.join(row) + '\u0000' * (per_row - len(row))
    providers = [{'type': 'bitmap', 'file': f'phoenix:font/badge/{name}.png', 'ascent': 7, 'height': 9, 'chars': [pad(r) for r in rows]}]
    # lower case uses the same pictures: a second provider on the same image with the lower-case chars
    low = [pad([c.lower() if c.isalpha() else '\u0000' for c in r]) for r in rows]
    providers.append({'type': 'bitmap', 'file': f'phoenix:font/badge/{name}.png', 'ascent': 7, 'height': 9, 'chars': low})
    providers.append({'type': 'space', 'advances': {chr(NEG1): -1}})
    os.makedirs(FONT_DIR, exist_ok=True)
    json.dump({'providers': providers}, open(os.path.join(FONT_DIR, name + '.json'), 'w'), ensure_ascii=True, indent=1)

# ---- the ranks: group id -> style. The NAME is never here: it comes from LuckPerms (group display name).
RANKS = [  # id, sample name, family, font, cap, colour (or gradient), cap colour, shine
    ('default', 'Ashborn', 'free', 'badge', 'plain', '#B5ABA4', None, False),
    ('hatchling', 'Hatchling', 'bought', 'badge', 'ember', '#FF9A4D', None, False),
    ('fledgling', 'Fledgling', 'bought', 'badge', 'ember', '#9BE84A', None, False),
    ('firewing', 'Firewing', 'bought', 'badge', 'wing', '#4FB4FF', None, False),
    ('flamecrest', 'Flamecrest', 'bought', 'badge', 'wing', '#B06BFF', None, False),
    ('sunfeather', 'Sunfeather', 'bought', 'badge', 'sun', '#FFD21A', None, False),
    ('pyrelord', 'Pyrelord', 'bought', 'badge_shine', 'sun', '#FF3B4A', None, True),
    ('immortal', 'Immortal', 'bought', 'badge_shine', 'phoenix', ['#FFE27A', '#FF8C1A', '#FF2D3F'], '#FFD21A', True),
    ('torchbearer', 'Torchbearer', 'won', 'badge', 'ribbon', '#2EE6D0', None, False),
    ('flameheart', 'Flameheart', 'won', 'badge_shine', 'ribbon', '#FF5FD2', None, True),
    ('firecrown', 'Firecrown', 'won', 'badge_shine', 'ribbon', '#FFFFFF', '#FFD21A', True),
    ('owner', 'Owner', 'staff', 'badge_solid', 'square', '#FFC21A', None, False),
    ('admin', 'Admin', 'staff', 'badge_solid', 'square', '#FF6B6B', None, False),
    ('mod', 'Mod', 'staff', 'badge_solid', 'square', '#6CC4FF', None, False),
    ('helper', 'Helper', 'staff', 'badge_solid', 'square', '#7CEB8A', None, False),
    ('builder', 'Builder', 'staff', 'badge_solid', 'square', '#FF9A3D', None, False),
]

BEDROCK = {'default': '&7', 'hatchling': '&6', 'fledgling': '&a', 'firewing': '&9', 'flamecrest': '&5', 'sunfeather': '&e', 'pyrelord': '&c', 'immortal': '&e&l>&6&l>&c&l', 'torchbearer': '&3', 'flameheart': '&d', 'firecrown': '&f&l', 'owner': '&e&l', 'admin': '&c&l', 'mod': '&9&l', 'helper': '&a&l', 'builder': '&6&l'}  # Bedrock has only the 16 legacy colours: bold keeps staff apart from players
LEGACY = {'0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA', '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA', '8': '#555555', '9': '#5555FF', 'a': '#55FF55', 'b': '#55FFFF', 'c': '#FF5555', 'd': '#FF55FF', 'e': '#FFFF55', 'f': '#FFFFFF'}

def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
def lerp(a, b, t): return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))
def gradient(stops, n):
    cs = [hexc(s) for s in stops]
    if n == 1: return [cs[0]]
    out = []
    for k in range(n):
        t = k / (n - 1) * (len(cs) - 1); i = min(int(t), len(cs) - 2); out.append(lerp(cs[i], cs[i + 1], t - i))
    return out

def render(fonts, rank, name, scale=4, shadow=True):
    """Draw a badge the way the client does: glyph pixels x text colour, shadow first, -1 px between glyphs."""
    rid, _, fam, font, cap, col, capcol, shine = rank
    gl = fonts[font]; l, r = CAP_CHARS[cap]
    chars = [chr(l)] + [c.upper() for c in name if c.upper() in LETTERS] + [chr(r)]  # other characters are left out
    letters = len(chars) - 2
    cols = gradient(col, letters) if isinstance(col, list) else [hexc(col)] * letters
    capc = hexc(capcol) if capcol else (cols[0] if not isinstance(col, list) else hexc(col[0]))
    capc_r = hexc(capcol) if capcol else (cols[-1])
    colors = [capc] + cols + [capc_r]
    w = sum(gl[c].width for c in chars) + 2
    out = Image.new('RGBA', (w * scale, 10 * scale), (0, 0, 0, 0))
    def draw(dx, dy, dim):
        x = 0
        for c, cc in zip(chars, colors):
            g = gl[c]; px = g.load()
            for yy in range(9):
                for xx in range(g.width):
                    pr, pg, pb, pa = px[xx, yy]
                    if not pa: continue
                    t = tuple(int(v * k / 255 * dim) for v, k in zip((pr, pg, pb), cc))
                    for sy in range(scale):
                        for sx in range(scale): out.putpixel(((x + xx + dx) * scale + sx, (yy + dy) * scale + sy), t + (255,))
            x += g.width  # advance = width + 1, then the -1 px space
    if shadow: draw(1, 1, 0.25)
    draw(0, 0, 1.0)
    return out

def sheet(fonts):
    from PIL import ImageDraw, ImageFont
    try: F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf', 15); FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 17)
    except Exception: F = FB = ImageFont.load_default()
    W = 1180; rows = []
    groups = [('FREE', 'free'), ('BOUGHT  (flame ends grow with the tier)', 'bought'), ('WON IN A SEASON  (ribbon ends)', 'won'), ('STAFF  (solid plates, your colours)', 'staff')]
    for title, fam in groups:
        rows.append(('h', title))
        for r in RANKS:
            if r[2] == fam: rows.append(('r', r, r[1]))
    rows.append(('h', 'RENAMED IN LUCKPERMS  (same group, new display name: the badge follows)'))
    for nm, rid in [('Baby Phoenix', 'hatchling'), ('VIP+', 'fledgling'), ('Ember Lord', 'pyrelord'), ('Head Admin', 'admin'), ('Sr. Mod', 'mod')]:
        rows.append(('r', [x for x in RANKS if x[0] == rid][0], nm))
    rows.append(('h', 'BEDROCK / NO PACK  (16 legacy colours only; staff are bold)'))
    rows.append(('bg', [r for r in RANKS]))
    H = 20 + sum(34 if t[0] == 'h' else 150 if t[0] == 'bg' else 50 for t in rows)
    img = Image.new('RGB', (W, H), (26, 20, 19)); d = ImageDraw.Draw(img); y = 14
    for t in rows:
        if t[0] == 'h': d.text((16, y + 8), t[1], fill=(255, 210, 26), font=FB); y += 34; continue
        d.rectangle([10, y - 2, W - 10, y + 44], fill=(14, 11, 11))
        if t[0] == 'bg':
            d.rectangle([10, y - 2, W - 10, y + 144], fill=(14, 11, 11))
            for i, r in enumerate(t[1]):
                cx, cy = 22 + (i % 4) * 285, y + 8 + (i // 4) * 34; parts = BEDROCK[r[0]].split('>'); n = len(parts); name = r[1]
                seg = [name[k * len(name) // n:(k + 1) * len(name) // n] for k in range(n)]; x = cx
                d.text((x, cy), '[', fill=hexc(LEGACY[parts[0][1]]), font=FB if 'l' in parts[0] else F); x += 11
                for pt, sg in zip(parts, seg):
                    fnt = FB if '&l' in pt else F; d.text((x, cy), sg, fill=hexc(LEGACY[pt[1]]), font=fnt); x += (11 if '&l' in pt else 9) * len(sg)
                d.text((x, cy), ']', fill=hexc(LEGACY[parts[-1][1]]), font=FB if 'l' in parts[-1] else F)
            y += 150; continue
        if t[0] == 'b':
            r = t[1]; col = r[5][1] if isinstance(r[5], list) else r[5]
            d.text((22, y + 12), '[' + r[1] + ']', fill=hexc(col), font=FB); d.text((22 + 14 * (len(r[1]) + 3), y + 12), 'Steve: hello!', fill=(255, 255, 255), font=F); y += 50; continue
        r, name = t[1], t[2]
        big = render(fonts, r, name, scale=4); img.paste(big, (22, y + 2), big)
        sm = render(fonts, r, name, scale=2); x2 = 22 + big.width + 30; img.paste(sm, (x2, y + 12), sm)
        d.text((x2 + sm.width + 10, y + 13), 'Steve: hello!', fill=(255, 255, 255), font=F)
        col = r[5] if isinstance(r[5], str) else ' > '.join(r[5])
        d.text((W - 360, y + 13), f"{r[0]:<12} {col}{'  shine' if r[7] else ''}", fill=(168, 153, 149), font=F)
        y += 50
    return img

if __name__ == '__main__':
    fonts = {'badge': build('dark'), 'badge_shine': build('dark', alpha=254), 'badge_solid': build('solid')}
    for n, gl in fonts.items(): write_font(n, gl)
    json.dump([{'group': r[0], 'family': r[2], 'font': 'phoenix:' + r[3], 'cap': r[4], 'color': r[5], 'cap_color': r[6], 'shine': r[7], 'bedrock': BEDROCK[r[0]],
                'caps': [chr(CAP_CHARS[r[4]][0]), chr(CAP_CHARS[r[4]][1])]} for r in RANKS],
              open(os.path.join(os.path.dirname(__file__), 'ranks.json'), 'w'), ensure_ascii=True, indent=1)
    sheet(fonts).save(os.path.join(PREVIEW, 'badges.png'))
    print('fonts written', [ (n, len(g)) for n, g in fonts.items()])
