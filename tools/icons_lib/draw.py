"""Pixel-art drawing for the placeholder icons: palette, colour ramps, ASCII sprites with automatic outline and
3-tone shading, and the generic badge. No anti-aliasing: every pixel is fully opaque or fully transparent.

Art rules (GUI-P1-PACK step 1): 16x16, transparent background, 1 px outline in a dark tone of the main colour,
3-tone ramp (highlight top-left, base, shadow bottom-right), at most 6 colours plus the outline, silhouette centred.
"""
from . import font5x7

SIZE = 16
CLEAR = (0, 0, 0, 0)

# Palette (I9 section 8/9 and style.yml). Hex strings so the owner can compare them with style.yml.
MONEY = '#4DFF88'
REWARD = '#FFD21A'     # flame
MOVE = '#FF8C1A'       # ember
DEPTHS = '#5AB4E5'     # info, steel sky
SKILLS = '#B36BFF'
PEOPLE = '#3DA5FF'
HOME = '#7CFF4D'
DANGER = '#FF3B3B'
WHITE = '#FFFFFF'
GREY = '#B5ABA4'
LOCKED = '#5E5E66'
CREDITS = '#FF8C1A'
SHARDS = '#FF6B8A'
CRYSTALS = '#33E0FF'
PHOENIX_RED = '#FF2D3F'
OUTLINE = '#2B1B17'

# Meaning (icons.json) -> colour of the icon's main shape.
MEANING_COLOUR = {
    'money': MONEY,
    'reward': REWARD,
    'move': MOVE,
    'depths': DEPTHS,
    'skills': SKILLS,
    'people': PEOPLE,
    'home': HOME,
    'danger': DANGER,
    'neutral': GREY,
    'locked': LOCKED,
    'currency-money': MONEY,
    'currency-crystals': CRYSTALS,
    'currency-shards': SHARDS,
    'currency-credits': CREDITS,
}


def rgb(hex_colour):
    h = hex_colour.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def rgba(hex_colour):
    return rgb(hex_colour) + (255,)


def mix(a, b, t):
    """Colour between a and b (hex strings), t = 0..1 towards b, as an opaque RGBA tuple."""
    ra, rb = rgb(a), rgb(b)
    return tuple(int(round(ra[i] + (rb[i] - ra[i]) * t)) for i in range(3)) + (255,)


def ramp(base):
    """(highlight, base, shadow, outline) of a main colour."""
    return mix(base, WHITE, 0.35), rgba(base), mix(base, '#000000', 0.32), mix(base, OUTLINE, 0.78)


def luminance(hex_colour):
    r, g, b = rgb(hex_colour)
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0


class Canvas:
    """A 16x16 (or any size) RGBA pixel grid."""

    def __init__(self, width=SIZE, height=SIZE, fill=CLEAR):
        self.w = width
        self.h = height
        self.px = [[fill for _ in range(width)] for _ in range(height)]

    def set(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = colour

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return CLEAR

    def rows(self):
        return self.px

    def blit(self, other, ox, oy, scale=1):
        """Copies the opaque pixels of ``other`` at (ox, oy), each pixel ``scale`` x ``scale``."""
        for y in range(other.h):
            for x in range(other.w):
                p = other.px[y][x]
                if p[3] == 0:
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        self.set(ox + x * scale + dx, oy + y * scale + dy, p)

    def text(self, font, s, ox, oy, colour, scale=1, spacing=1):
        """Draws ``s`` with a built-in font; returns the width drawn."""
        x = ox
        for ch in s:
            g = font5x7.glyph(font, ch)
            for gy, row in enumerate(g):
                for gx, c in enumerate(row):
                    if c == '#':
                        for dy in range(scale):
                            for dx in range(scale):
                                self.set(x + gx * scale + dx, oy + gy * scale + dy, colour)
            x += (len(g[0]) + spacing) * scale
        return x - ox


def sprite(art, colours, shaded='#'):
    """A canvas from an ASCII map (16 strings of 16 characters, '.' = transparent).

    ``colours`` maps each character to a hex colour. Characters in ``shaded`` get the 3-tone ramp of their colour
    (highlight where the pixel above or to the left is not the same character, shadow where the pixel below or to the
    right is not); every other character is drawn flat. An outline in the dark tone of the first shaded character's
    colour is added around the whole silhouette (4-neighbour), so the map itself must leave a 1 px margin.
    """
    if len(art) != SIZE or any(len(r) != SIZE for r in art):
        raise ValueError('sprite maps must be 16x16, got %s' % [len(r) for r in art])
    c = Canvas()
    main = None
    for ch in shaded:
        if ch in colours:
            main = colours[ch]
            break
    if main is None:
        main = next(iter(colours.values()))
    outline = ramp(main)[3]

    def same(x, y, ch):
        return 0 <= x < SIZE and 0 <= y < SIZE and art[y][x] == ch

    for y in range(SIZE):
        for x in range(SIZE):
            ch = art[y][x]
            if ch == '.':
                continue
            if ch not in colours:
                raise ValueError('no colour for %r' % ch)
            if ch in shaded:
                hi, base, sh, _ = ramp(colours[ch])
                lit = not same(x, y - 1, ch) or not same(x - 1, y, ch)
                dark = not same(x, y + 1, ch) or not same(x + 1, y, ch)
                c.set(x, y, hi if lit and not dark else sh if dark and not lit else base)
            else:
                c.set(x, y, rgba(colours[ch]))
    for y in range(SIZE):
        for x in range(SIZE):
            if art[y][x] != '.':
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and art[ny][nx] != '.':
                    c.set(x, y, outline)
                    break
    return c


BADGE_TILE = [
    '................',
    '................',
    '...##########...',
    '..############..',
    '..############..',
    '..############..',
    '..############..',
    '..############..',
    '..############..',
    '..############..',
    '..############..',
    '..############..',
    '..############..',
    '...##########...',
    '................',
    '................',
]


def badge(colour, letter):
    """Generic placeholder: a rounded tile in ``colour`` with one 3x5 glyph drawn 2x (white, or dark on light tiles)."""
    c = sprite(BADGE_TILE, {'#': colour})
    ink = ramp(colour)[3] if luminance(colour) > 0.62 else rgba(WHITE)
    g = Canvas(3, 5)
    g.text(font5x7.FONT_3X5, letter, 0, 0, ink)
    c.blit(g, 5, 3, scale=2)
    return c


def opaque_or_clear(canvas):
    """Raises if a pixel is half transparent (the generator only writes alpha 0 or 255)."""
    for row in canvas.px:
        for p in row:
            if p[3] not in (0, 255):
                raise ValueError('pixel with alpha %d' % p[3])
    return canvas


def colour_count(canvas):
    return len({p for row in canvas.px for p in row if p[3] == 255})
