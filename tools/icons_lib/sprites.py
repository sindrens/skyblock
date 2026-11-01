"""Icon set v3 ("cartoony ember"): hand-built 16x16 pixel sprites for every CUSTOM icon (things that are not a real
Minecraft item: currencies, UI actions, a few feature symbols). Everything that has a good vanilla item is NOT drawn
here; it stays that vanilla item (see resourcepack/icons.json "vanilla" and docs/design/ICON_MAP.md).

Style rules (owner, 31 Oct 2026; MC Hub inspired): 16x16 like a vanilla item, ONE dark 1 px outline (added
automatically around the silhouette), bright flat colours, one highlight and one shade per shape, chunky 2 px
strokes, rounded corners, a phoenix/ember palette. Every pixel is fully opaque or fully transparent.

Authoring model: a sprite is a function ``fn() -> Spr`` that draws palette letters on a 16x16 grid ('.' = empty). The
outline letter ``k`` is added around every opaque pixel by ``Spr.canvas`` (4-neighbour), so shapes stay inside
x/y 1..14. Many icons come in variants (A/B/C): ``VARIANTS[key][style]`` -> sprite function. Python stdlib only.
"""
import math

from . import draw

SIZE = draw.SIZE

# letter -> hex. Each hue has base / light (top-left highlight) / dark (bottom-right shade).
PALETTE = {
    'k': '#2A1D24',                                   # the outline (and the odd dark detail)
    'R': '#FF2D3F', 'r': '#FF8A92', 'q': '#B8142B',   # phoenix red
    'O': '#FF8C1A', 'o': '#FFC070', 'p': '#C45A00',   # ember orange
    'Y': '#FFD21A', 'y': '#FFF08A', 'z': '#D69A00',   # flame yellow
    'G': '#4DFF6A', 'g': '#B4FFC2', 'h': '#1FA640',   # lime green
    'B': '#3DC8FF', 'b': '#A8EBFF', 'c': '#1A86C8',   # ice blue
    'W': '#F4F4F4', 'w': '#FFFFFF', 's': '#B9C0CC',   # white / frost
    'K': '#4A4A57', 'L': '#7A7A8A', 'J': '#2E2E38',   # charcoal
    'N': '#B8733A', 'n': '#E3A265', 'm': '#7A4620',   # wood / leather
    'F': '#FF6B8A', 'f': '#FFB0C2', 'e': '#C23A5C',   # shard pink
}
OUTLINE = 'k'


class Spr:
    """A 16x16 grid of palette letters."""

    def __init__(self):
        self.g = [['.'] * SIZE for _ in range(SIZE)]
        self.outline = True     # False: no dark outline (the vanilla-look star has none)
        self.extra = {}         # sprite-local palette letters

    # -- primitives ------------------------------------------------------------------------------------------
    def set(self, x, y, ch):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.g[y][x] = ch
        return self

    def get(self, x, y):
        return self.g[y][x] if 0 <= x < SIZE and 0 <= y < SIZE else '.'

    def rect(self, x0, y0, x1, y1, ch):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, ch)
        return self

    def disc(self, cx, cy, r, ch):
        """Pixels whose centre is within r of (cx, cy) (pixel (x, y) has its centre at x + .5, y + .5)."""
        for y in range(SIZE):
            for x in range(SIZE):
                if math.hypot(x + .5 - cx, y + .5 - cy) <= r:
                    self.g[y][x] = ch
        return self

    def poly(self, pts, ch):
        """Filled polygon (even-odd) through pixel-centre sampling."""
        n = len(pts)
        for y in range(SIZE):
            for x in range(SIZE):
                px, py = x + .5, y + .5
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = pts[i]
                    xj, yj = pts[j]
                    if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                if inside:
                    self.g[y][x] = ch
        return self

    def stamp(self, rows, ox, oy, ch=None):
        """Overlay an ASCII glyph; '.' keeps what is below, '#' draws ``ch``, any other character draws itself."""
        for dy, row in enumerate(rows):
            for dx, c in enumerate(row):
                if c == '.':
                    continue
                self.set(ox + dx, oy + dy, ch if (c == '#' and ch) else c)
        return self

    def mirror(self):
        """Left-right flipped copy."""
        s = Spr()
        s.g = [list(reversed(r)) for r in self.g]
        return s

    def flip(self):
        """Upside-down copy."""
        s = Spr()
        s.g = [list(r) for r in reversed(self.g)]
        return s

    def shade(self, ch, light, dark):
        """One highlight and one shade: a pixel of ``ch`` whose upper or left neighbour is not ``ch`` becomes
        ``light``; whose lower or right neighbour is not ``ch`` becomes ``dark`` (both = stays base)."""
        same = lambda x, y: self.get(x, y) == ch
        out = []
        for y in range(SIZE):
            for x in range(SIZE):
                if self.g[y][x] != ch:
                    continue
                lit = not same(x, y - 1) or not same(x - 1, y)
                shd = not same(x, y + 1) or not same(x + 1, y)
                if lit and not shd:
                    out.append((x, y, light))
                elif shd and not lit:
                    out.append((x, y, dark))
        for x, y, c in out:
            self.g[y][x] = c
        return self

    def rows(self):
        return [''.join(r) for r in self.g]

    # -- output ----------------------------------------------------------------------------------------------
    def canvas(self, extra=None):
        pal = dict(PALETTE)
        pal.update(self.extra)
        if extra:
            pal.update(extra)
        c = draw.Canvas()
        for y in range(SIZE):
            for x in range(SIZE):
                ch = self.g[y][x]
                if ch != '.':
                    if ch not in pal:
                        raise ValueError('no colour for %r' % ch)
                    c.set(x, y, draw.rgba(pal[ch]))
        if not self.outline:
            return c
        ol = draw.rgba(pal[OUTLINE])
        for y in range(SIZE):
            for x in range(SIZE):
                if self.g[y][x] != '.':
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if self.get(x + dx, y + dy) != '.':
                        c.set(x, y, ol)
                        break
        return c


def spr(rows=None):
    s = Spr()
    if rows:
        if len(rows) != SIZE or any(len(r) != SIZE for r in rows):
            raise ValueError('sprite rows must be 16x16: %s' % [len(r) for r in rows])
        s.g = [list(r) for r in rows]
    return s


# -------------------------------------------------------------------------------------------------- glyphs
# 2 px strokes, even sizes, so they centre exactly on the 14 px (x 1..14) discs and squares.

TICK = [  # 10 x 7
    '........##',
    '.......###',
    '......###.',
    '##...###..',
    '###.###...',
    '.#####....',
    '..###.....',
]
CROSS = [  # 8 x 8
    '##....##',
    '###..###',
    '.######.',
    '..####..',
    '..####..',
    '.######.',
    '###..###',
    '##....##',
]
PLUS = [  # 8 x 8
    '...##...',
    '...##...',
    '...##...',
    '########',
    '########',
    '...##...',
    '...##...',
    '...##...',
]
MINUS = ['########', '########']  # 8 x 2
CHEV_L = [  # 6 x 10
    '....##',
    '...##.',
    '..##..',
    '.##...',
    '##....',
    '##....',
    '.##...',
    '..##..',
    '...##.',
    '....##',
]
CHEV_R = [r[::-1] for r in CHEV_L]
BANG = [  # 2 x 8
    '##', '##', '##', '##', '##', '..', '##', '##',
]
QMARK = [  # 6 x 10
    '.####.',
    '##..##',
    '##..##',
    '....##',
    '...##.',
    '..##..',
    '..##..',
    '......',
    '..##..',
    '..##..',
]
INFO_I = [  # 2 x 9
    '##', '##', '..', '##', '##', '##', '##', '##', '##',
]
DOLLAR = [  # 3 x 7
    '.#.',
    '###',
    '#..',
    '###',
    '..#',
    '###',
    '.#.',
]


def button(ch, light, dark, r=7.0, cx=8.0, cy=8.0):
    """A round 14 px button in one hue with one highlight and one shade."""
    return Spr().disc(cx, cy, r, ch).shade(ch, light, dark)


def square(ch, light, dark, x0=1, y0=1, x1=14, y1=14):
    """A rounded 14 px square button (corner pixels removed)."""
    s = Spr().rect(x0, y0, x1, y1, ch)
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        s.set(x, y, '.')
    return s.shade(ch, light, dark)


# -------------------------------------------------------------------------------------------------- currencies
# Four currencies: four different silhouettes (round coin, thin shard pair, cut gem, five-point star) AND four hues.

def currency_money():
    s = button('G', 'g', 'h', r=6.6, cx=7.5, cy=7.5)
    s.disc(7.5, 7.5, 4.4, 'g').shade('g', 'w', 'G')
    s.stamp(DOLLAR, 6, 4, 'h')
    return s


def currency_shards():
    """Three pointed prism crystals (pink): one big, two small."""
    s = spr([
        '................',
        '........F.......',
        '.......fFe......',
        '......ffFee.....',
        '......ffFee.....',
        '......ffFee..F..',
        '...F..ffFee.fFe.',
        '..fFe.ffFee.fFe.',
        '..fFe.ffFee.fFe.',
        '..fFe.ffFee.fFe.',
        '..fFe.ffFee.fFe.',
        '..fFe.ffFee.fFe.',
        '..fFe.ffFee.fFe.',
        '..eee.eeeee.eee.',
        '................',
        '................',
    ])
    s.set(7, 3, 'w').set(7, 4, 'w').set(3, 7, 'w').set(13, 7, 'w')
    return s


def currency_crystals():
    return spr([
        '................',
        '................',
        '....wbbbBBBB....',
        '...bbbbBBBBBB...',
        '..bbbbbBBBBBBB..',
        '.bbbbbbBBBBBBBB.',
        '.BBBBBBBccccccc.',
        '..BBBBBBcccccc..',
        '...BBBBBccccc...',
        '....BBBBcccc....',
        '.....BBBccc.....',
        '......BBcc......',
        '.......Bc.......',
        '................',
        '................',
        '................',
    ])


def currency_credits():
    s = spr([
        '................',
        '.......OO.......',
        '.......OO.......',
        '......OOOO......',
        '......OOOO......',
        '.OOOOOOOOOOOOOO.',
        '..OOOOOOOOOOOO..',
        '...OOOOOOOOOO...',
        '....OOOOOOOO....',
        '....OOOOOOOO....',
        '...OOOO..OOOO...',
        '...OOO....OOO...',
        '..OOO......OOO..',
        '..OO........OO..',
        '................',
        '................',
    ])
    s.shade('O', 'o', 'p')
    s.stamp(['yy', 'yy'], 7, 6)   # warm core
    return s


# -------------------------------------------------------------------------------------------------- the hotbar star
def menu_ember():
    """Vanilla-style pale four-point star (own pixels, no outline like the real one) with a golden heart and a tiny
    ember halo. Only drawn when the owner picks the "ember" style for the hotbar item."""
    widths = {2: (8, 8), 3: (7, 9), 4: (7, 9), 5: (6, 10), 6: (5, 11), 7: (3, 13), 8: (2, 14), 9: (3, 13), 10: (5, 11),
              11: (6, 10), 12: (7, 9), 13: (7, 9), 14: (8, 8)}
    s = Spr()
    s.outline = False
    s.extra = {'T': '#D3DEDE', 't': '#F4FAFA', 'u': '#7E9C9C', 'U': '#5E8080'}
    for y, (a, b) in widths.items():
        s.rect(a, y, b, y, 'T')
    mask = {(x, y) for y in range(SIZE) for x in range(SIZE) if s.g[y][x] == 'T'}
    for (x, y) in mask:
        outside = [(x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        if any(outside):
            s.g[y][x] = 'U' if (not (x + 1, y) in mask or not (x, y + 1) in mask) else 'u'
        elif (x - 1, y) not in mask or (x, y - 1) not in mask or (x - 1, y - 1) not in mask:
            s.g[y][x] = 't'
    for x, y in ((8, 7), (7, 8), (9, 8), (8, 9)):
        s.set(x, y, 'O')
    s.set(8, 8, 'Y')
    return s


# -------------------------------------------------------------------------------------------------- navigation
# Style A: chunky arrows / triangles. B: door and pointing gloves. C: chevrons in rounded square buttons.

def _arrow_left():
    return spr([
        '................',
        '................',
        '......OO........',
        '.....OOO........',
        '....OOOO........',
        '...OOOOOOOOOOOO.',
        '..OOOOOOOOOOOOO.',
        '.OOOOOOOOOOOOOO.',
        '.OOOOOOOOOOOOOO.',
        '..OOOOOOOOOOOOO.',
        '...OOOOOOOOOOOO.',
        '....OOOO........',
        '.....OOO........',
        '......OO........',
        '................',
        '................',
    ]).shade('O', 'o', 'p')


def back_a():
    return _arrow_left()


def _tri_left(ch, light, dark):
    return spr([
        '................',
        '................',
        '.......BBBBBB...',
        '......BBBBBBB...',
        '.....BBBBBBBB...',
        '....BBBBBBBBB...',
        '...BBBBBBBBBB...',
        '..BBBBBBBBBBB...',
        '..BBBBBBBBBBB...',
        '...BBBBBBBBBB...',
        '....BBBBBBBBB...',
        '.....BBBBBBBB...',
        '......BBBBBBB...',
        '.......BBBBBB...',
        '................',
        '................',
    ]).shade('B', light, dark)


def prev_a():
    return _tri_left('B', 'b', 'c')


def next_a():
    return _tri_left('B', 'b', 'c').mirror()


def back_b():
    """A little wooden door ajar, warm light spilling out."""
    s = spr([
        '................',
        '...mmmmmmmmmm...',
        '...mYYNNNNNNm...',
        '...mYYNnnnnNm...',
        '...mYYNnnnnNm...',
        '...mYYNnnnnNm...',
        '...mYYNNNNNNm...',
        '...mYYNNNNNNm...',
        '...mYYNnnnnNm...',
        '...mYYNnnnnNm...',
        '...mYYNnnnnNm...',
        '...mYYNnnnnNm...',
        '...mYYNNNNNNm...',
        '...mYYNNNNNNm...',
        '...mmmmmmmmmm...',
        '................',
    ])
    s.set(10, 8, 'Y').set(10, 9, 'z')   # knob
    return s


def _glove_right():
    """A white glove, red cuff, index finger pointing right, three curled fingers."""
    s = spr([
        '................',
        '................',
        '....WWWW........',
        '.RRWWWWWWW......',
        '.RRWWWWWWWWWWW..',
        '.RRWWWWWWWWWWWW.',
        '.RRWWWWWWWWWWW..',
        '.RRWWWWWWWWWW...',
        '.RRWWWWWWWWWW...',
        '.RRWWWWWWWWWW...',
        '.RRWWWWWWWWWW...',
        '.RRWWWWWWWW.....',
        '.RRWWWWWWWW.....',
        '................',
        '................',
        '................',
    ])
    s.shade('W', 'w', 's').shade('R', 'r', 'q')
    for x in (9, 10, 11, 12):
        s.set(x, 8, 's').set(x, 10, 's')
    return s


def prev_b():
    return _glove_right().mirror()


def next_b():
    return _glove_right()


def _square_chev(rows, ch, light, dark, ink='w'):
    s = square(ch, light, dark)
    s.stamp(rows, 5 if len(rows[0]) == 6 else 4, 3, ink)
    return s


def back_c():
    s = square('B', 'b', 'c')
    s.stamp(CHEV_L, 4, 3, 'w').stamp(CHEV_L, 7, 3, 'w')
    return s


def prev_c():
    return _square_chev(CHEV_L, 'B', 'b', 'c')


def next_c():
    return _square_chev(CHEV_R, 'B', 'b', 'c')


# -------------------------------------------------------------------------------------------------- close
def close_a():
    s = Spr().stamp(CROSS, 3, 3, 'R')
    s2 = Spr()
    # fat red X (3 px strokes): the 8x8 cross grown by one pixel right/down
    for y in range(SIZE):
        for x in range(SIZE):
            if s.get(x, y) != '.' or s.get(x - 1, y) != '.' or s.get(x, y - 1) != '.':
                s2.set(x, y, 'R')
    return s2.shade('R', 'r', 'q')


def close_b():
    s = button('R', 'r', 'q')
    s.stamp(CROSS, 4, 4, 'w')
    return s


def close_c():
    s = square('R', 'r', 'q')
    s.stamp(CROSS, 4, 4, 'w')
    return s


# -------------------------------------------------------------------------------------------------- confirm / cancel
def confirm_a():
    s = Spr().stamp(TICK, 2, 4, 'G')
    s2 = Spr()
    for y in range(SIZE):
        for x in range(SIZE):
            if s.get(x, y) != '.' or s.get(x, y - 1) != '.' or s.get(x - 1, y) != '.':
                s2.set(x, y, 'G')
    return s2.shade('G', 'g', 'h')


def cancel_a():
    return close_a()


def _thumb_up(ch, light, dark):
    return spr([
        '................',
        '......GG........',
        '.....GGGG.......',
        '.....GGGG.......',
        '.....GGGG.......',
        '.WW..GGGGGGGGG..',
        '.WWGGGGGGGGGGGG.',
        '.WWGGGGGGGGGGGG.',
        '.WWGGhhhhhhhGG..',
        '.WWGGGGGGGGGGG..',
        '.WWGGhhhhhhGG...',
        '.WWGGGGGGGGGG...',
        '.WWGGhhhhhGG....',
        '.WWGGGGGGGGG....',
        '................',
        '................',
    ]).shade('G', light, dark).shade('W', 'w', 's')


def confirm_b():
    return _thumb_up('G', 'g', 'h')


def cancel_b():
    t = _thumb_up('G', 'g', 'h').flip()
    for y in range(SIZE):
        for x in range(SIZE):
            c = t.g[y][x]
            t.g[y][x] = {'G': 'R', 'g': 'r', 'h': 'q'}.get(c, c)
    return t


def confirm_c():
    s = button('G', 'g', 'h')
    s.stamp(TICK, 3, 5, 'w')
    return s


def cancel_c():
    s = button('R', 'r', 'q')
    s.stamp(CROSS, 4, 4, 'w')
    return s


# -------------------------------------------------------------------------------------------------- other UI actions
def nav_danger():
    s = Spr()
    for y in range(1, 15):
        w = 2 + 2 * ((y - 1) // 2)
        x0 = 8 - w // 2
        s.rect(x0, y, x0 + w - 1, y, 'R')
    s.shade('R', 'r', 'q')
    s.stamp(BANG, 7, 5, 'w')
    return s


def ui_info():
    s = button('B', 'b', 'c')
    s.stamp(INFO_I, 7, 3, 'w')
    return s


def nav_help():
    s = button('Y', 'y', 'z')
    s.stamp(QMARK, 5, 3, 'm')
    return s


def nav_search():
    s = Spr()
    # handle first, lens on top
    for i in range(6):
        s.rect(9 + i, 9 + i, 10 + i, 10 + i, 'N')
    s.disc(6.0, 6.0, 5.2, 'O')
    s.disc(6.0, 6.0, 3.3, 'b')
    s.shade('O', 'o', 'p').shade('N', 'n', 'm')
    s.stamp(['ww', 'w.'], 3, 3)
    return s


def state_locked():
    s = spr([
        '................',
        '.....WWWWWW.....',
        '....WW....WW....',
        '....WW....WW....',
        '....WW....WW....',
        '....WW....WW....',
        '....WW....WW....',
        '..YYYYYYYYYYYY..',
        '..YYYYYYYYYYYY..',
        '..YYYYYkkYYYYY..',
        '..YYYYYkkYYYYY..',
        '..YYYYYYkYYYYY..',
        '..YYYYYYkYYYYY..',
        '..YYYYYYYYYYYY..',
        '................',
        '................',
    ])
    s.shade('Y', 'y', 'z').shade('W', 'w', 's')
    return s


def state_undo():
    """A chunky curved turn-back arrow."""
    s = Spr()
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x + .5 - 8.5, y + .5 - 8.5)
            if x + .5 >= 8.5 and y + .5 <= 8.5 and 2.5 <= d <= 5.5:
                s.set(x, y, 'O')
    s.rect(11, 8, 13, 13, 'O')
    s.rect(6, 3, 8, 5, 'O')
    s.poly([(0.5, 4.5), (6.0, 0.8), (6.0, 8.2)], 'O')
    return s.shade('O', 'o', 'p')


def _pill(track, light, dark, knob_right):
    s = Spr()
    s.rect(1, 4, 14, 11, track)
    for x, y in ((1, 4), (14, 4), (1, 11), (14, 11)):
        s.set(x, y, '.')
    s.shade(track, light, dark)
    kx = 11.0 if knob_right else 5.0
    s.disc(kx, 8.0, 3.2, 'W').shade('W', 'w', 's')
    return s


def state_on():
    s = _pill('G', 'g', 'h', True)
    s.stamp(['#', '#', '#', '#'], 4, 6, 'w')   # the "|" of on
    return s


def state_off():
    return _pill('K', 'L', 'J', False)


def shop_fill():
    """A backpack stuffed with goods: flap, buckle, side pocket."""
    s = spr([
        '................',
        '....GG..YY..RR..',
        '...GGGG.YY.RRRR.',
        '..OOOOOOOOOOOO..',
        '..OOOOOOOOOOOO..',
        '..OOOOOOOOOOOO..',
        '..OOOOOOOOOOOO..',
        '..OOOOOYYOOOOO..',
        '..OOOOOYYOOOOO..',
        '..OOOOOOOOOOOO..',
        '..OOnnnnnnnnOO..',
        '..OOnnnnnnnnOO..',
        '..OOOOOOOOOOOO..',
        '...OOOOOOOOOO...',
        '................',
        '................',
    ])
    s.shade('O', 'o', 'p').shade('n', 'o', 'p')
    for x in range(2, 14):
        s.set(x, 8, 'p') if s.get(x, 8) == 'O' else None
    return s


def ui_type():
    """A pencil writing a line."""
    s = Spr()
    for i in range(11):
        x, y = 13 - i, 1 + i
        if i < 2:
            ch, w = 'F', 4
        elif i == 2:
            ch, w = 's', 4
        elif i < 8:
            ch, w = 'Y', 4
        elif i < 10:
            ch, w = 'n', 3 if i == 8 else 2
        else:
            ch, w = 'k', 1
        s.rect(x - w + 1, y, x, y, ch)
    s.rect(1, 14, 14, 14, 'K')
    s.shade('Y', 'y', 'z')
    return s


def feature_bank():
    s = spr([
        '................',
        '.......WW.......',
        '......WWWW......',
        '.....WWYYWW.....',
        '....WWWYYWWW....',
        '...WWWWWWWWWW...',
        '..WWWWWWWWWWWW..',
        '..ssssssssssss..',
        '...WW..WW..WW...',
        '...WW..WW..WW...',
        '...WW..WW..WW...',
        '...WW..WW..WW...',
        '..ssssssssssss..',
        '.WWWWWWWWWWWWWW.',
        '................',
        '................',
    ])
    s.shade('W', 'w', 's')
    return s


def feature_top():
    s = spr([
        '................',
        '...YYYYYYYYYY...',
        '...YYYYYYYYYY...',
        '.zzYYwYYYYYYYzz.',
        '.z.YYwYYYYYYY.z.',
        '.zzYYYYYYYYYYzz.',
        '...YYYYYYYYYY...',
        '....YYYYYYYY....',
        '.....YYYYYY.....',
        '......YYYY......',
        '......YYYY......',
        '.....NNNNNN.....',
        '....NNNNNNNN....',
        '...mmmmmmmmmm...',
        '................',
        '................',
    ])
    s.shade('Y', 'y', 'z').shade('N', 'n', 'm')
    return s


def feature_auction():
    """A gavel on its sound block."""
    s = spr([
        '................',
        '................',
        '..NNYYNNNNYYNN..',
        '..NNYYNNNNYYNN..',
        '..NNYYNNNNYYNN..',
        '..NNYYNNNNYYNN..',
        '..NNYYNNNNYYNN..',
        '.......NN.......',
        '.......NN.......',
        '.......NN.......',
        '.......NN.......',
        '..KKKKKKKKKKKK..',
        '.KKKKKKKKKKKKKK.',
        '................',
        '................',
        '................',
    ])
    s.shade('N', 'n', 'm').shade('K', 'L', 'J')
    for y in range(2, 7):          # round the head's ends
        s.set(2, y, 'm').set(13, y, 'm') if y in (2, 6) else None
    s.set(2, 2, '.').set(13, 2, '.').set(2, 6, '.').set(13, 6, '.')
    return s


# -------------------------------------------------------------------------------------------------- shop actions
# Buy = green family, Sell = orange/gold family (UI_STYLE: buy green, sell warm). No arrows anywhere.
# A: coin bag. B: price tags with + and -. C: shopping cart and coin stacks.

def _bag(tie):
    s = spr([
        '................',
        '................',
        '................',
        '.......NN.......',
        '.....NNNNNN.....',
        '......TTTT......',
        '....NNNNNNNN....',
        '...NNNNNNNNNN...',
        '..NNNNNNNNNNNN..',
        '..NNNNNNNNNNNN..',
        '..NNNNNNNNNNNN..',
        '..NNNNNNNNNNNN..',
        '...NNNNNNNNNN...',
        '....NNNNNNNN....',
        '................',
        '................',
    ])
    s.shade('N', 'n', 'm')
    for y in range(SIZE):
        for x in range(SIZE):
            if s.g[y][x] == 'T':
                s.g[y][x] = tie
    s.stamp(DOLLAR, 6, 7, 'Y')
    return s


def _coin(s, cx, cy, r):
    return s.disc(cx, cy, r, 'Y').shade('Y', 'y', 'z')


def buy_a():
    """Green-tied bag, one coin dropping in."""
    s = _bag('G')
    _coin(s, 7.5, 2.6, 2.4)
    s.set(2, 1, 'g').set(2, 2, 'g').set(13, 1, 'g').set(13, 2, 'g')    # motion dashes
    return s


def sell_a():
    """Orange-tied bag, coins popping out."""
    s = _bag('O')
    _coin(s, 11.5, 2.8, 2.2)
    _coin(s, 4.5, 2.4, 1.9)
    return s


def sell_all_a():
    """The bag overflowing: a fan of five coins above it."""
    s = _bag('O')
    for cx, cy in ((3.0, 4.0), (5.5, 2.0), (8.0, 1.6), (10.5, 2.0), (13.0, 4.0)):
        _coin(s, cx, cy, 1.6)
    return s


def _tag(ch, light, dark, x0=2, y0=1, w=12, h=13):
    s = Spr()
    s.rect(x0, y0, x0 + w - 1, y0 + h - 1, ch)
    for x, y in ((x0, y0), (x0 + w - 1, y0), (x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1),
                 (x0 + 1, y0), (x0 + w - 2, y0)):
        s.set(x, y, '.')
    s.shade(ch, light, dark)
    hx = x0 + w // 2 - 1
    s.rect(hx, y0 + 2, hx + 1, y0 + 3, 'k')
    return s


def buy_b():
    s = _tag('G', 'g', 'h')
    s.stamp(PLUS, 4, 6, 'w')
    return s


def sell_b():
    s = _tag('O', 'o', 'p')
    s.stamp(MINUS, 4, 9, 'w')
    return s


def sell_all_b():
    s = _tag('O', 'o', 'p', x0=5, y0=0, w=10, h=11)
    front = _tag('O', 'o', 'p', x0=1, y0=3, w=10, h=12)
    for y in range(SIZE):
        for x in range(SIZE):
            if front.g[y][x] != '.':
                s.g[y][x] = front.g[y][x]
    # a ring of outline between the tags so the front one reads as in front
    for y in range(SIZE):
        for x in range(SIZE):
            if front.g[y][x] == '.' and s.g[y][x] != '.':
                near = any(front.get(x + dx, y + dy) != '.' for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if near:
                    s.g[y][x] = 'k'
    s.stamp(MINUS, 2, 10, 'w')
    return s


def buy_c():
    """Shopping cart with a green item."""
    s = Spr()
    s.rect(1, 2, 3, 3, 'L')                       # handle grip
    s.rect(3, 3, 3, 8, 'L')                       # handle post
    s.poly([(4.0, 4.0), (14.0, 4.0), (12.5, 9.5), (5.0, 9.5)], 'R')   # basket
    s.rect(4, 10, 13, 10, 'K')                    # chassis
    s.disc(6.0, 12.6, 1.9, 'K').disc(11.5, 12.6, 1.9, 'K')
    s.shade('R', 'r', 'q')
    for x in (7, 10):                             # basket wires
        for y in range(5, 9):
            if s.get(x, y) in 'Rr':
                s.set(x, y, 'q')
    s.rect(7, 2, 9, 4, 'G').shade('G', 'g', 'h')   # an item peeking out
    s.set(6, 12, 'L').set(11, 12, 'L')
    return s


def _stack(s, cx, top, n, w=10):
    """n coins stacked (3 px tall pills), the lowest first at the bottom."""
    x0 = int(cx - w / 2)
    for i in range(n):
        y = top + (n - 1 - i) * 3
        s.rect(x0, y, x0 + w - 1, y + 2, 'Y')
        s.set(x0, y, '.').set(x0 + w - 1, y, '.').set(x0, y + 2, '.').set(x0 + w - 1, y + 2, '.')
        s.rect(x0 + 1, y, x0 + w - 2, y, 'y')
        s.rect(x0 + 1, y + 2, x0 + w - 2, y + 2, 'z')
    return s


def sell_c():
    """A stack of three coins."""
    s = Spr()
    _stack(s, 8.0, 4, 3)
    s.set(4, 5, 'w')
    return s


def sell_all_c():
    """Two taller stacks."""
    s = Spr()
    _stack(s, 4.5, 6, 3, w=7)
    _stack(s, 11.0, 3, 4, w=7)
    return s


# -------------------------------------------------------------------------------------------------- tables
# key -> sprite function for the single (non-varying) icons. The default variant of a varying icon is the style in
# DEFAULT_STYLE; the others live in resourcepack/variants/.
SINGLE = {
    'currency/money': currency_money,
    'currency/shards': currency_shards,
    'currency/crystals': currency_crystals,
    'currency/credits': currency_credits,
    'nav/danger': nav_danger,
    'nav/help': nav_help,
    'nav/search': nav_search,
    'ui/info': ui_info,
    'ui/type': ui_type,
    'state/locked': state_locked,
    'state/undo': state_undo,
    'state/on': state_on,
    'state/off': state_off,
    'shop/fill': shop_fill,
    'feature/bank': feature_bank,
    'feature/top': feature_top,
    'feature/auction': feature_auction,
}

VARIANTS = {
    'menu': {'ember': menu_ember},
    'nav/back': {'A': back_a, 'B': back_b, 'C': back_c},
    'nav/prev': {'A': prev_a, 'B': prev_b, 'C': prev_c},
    'nav/next': {'A': next_a, 'B': next_b, 'C': next_c},
    'nav/close': {'A': close_a, 'B': close_b, 'C': close_c},
    'state/confirm': {'A': confirm_a, 'B': confirm_b, 'C': confirm_c},
    'state/cancel': {'A': cancel_a, 'B': cancel_b, 'C': cancel_c},
    'shop/buy': {'A': buy_a, 'B': buy_b, 'C': buy_c},
    'shop/sell': {'A': sell_a, 'B': sell_b, 'C': sell_c},
    'shop/sell_all': {'A': sell_all_a, 'B': sell_all_b, 'C': sell_all_c},
}
