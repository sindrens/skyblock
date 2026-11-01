"""The logo glyph (GUI-FONT 4.2, owner Q18 a: scoreboard title and tab header only): a small flame mark, PHOENIX in the
cartoon lettering and SKYBLOCK in small white capitals, all with a 1 px dark outline, at most 96 x 20."""
from icons_lib import draw, font5x7

from . import lettering
from . import palette as P

FLAME = [  # 11 x 18; R red, O orange, Y gold, W white heart
    '.....R.....',
    '....RR.....',
    '....RRR....',
    '...RRRR..R.',
    '..RRRORR.R.',
    '..RROORRRR.',
    '.RRROOORRR.',
    '.RROOOOORR.',
    'RRROOYOORRR',
    'RROOYYYOORR',
    'RROOYYYOORR',
    'RROYYWYYORR',
    'RROYYWWYORR',
    '.RROYWWYOR.',
    '.RROYYYYOR.',
    '..RROOOORR.',
    '...RRRRRR..',
    '....RRRR...',
]
COLOURS = {'R': P.FLAME_RED, 'O': P.FLAME_ORANGE, 'Y': P.GOLD, 'W': P.WHITE}


def _outlined(img):
    """Adds a 1 px dark outline (8 neighbours) around the opaque pixels of ``img`` (same size)."""
    out = draw.Canvas(img.w, img.h)
    for y in range(img.h):
        for x in range(img.w):
            if img.px[y][x][3]:
                continue
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if img.get(x + dx, y + dy)[3]:
                        out.set(x, y, P.OUTLINE)
    out.blit(img, 0, 0)
    return out


def sprite(width=96, height=20):
    out = draw.Canvas(width, height)
    flame = draw.Canvas(13, 20)
    for y, row in enumerate(FLAME):
        for x, ch in enumerate(row):
            if ch != '.':
                flame.set(x + 1, y + 1, COLOURS[ch])
    out.blit(_outlined(flame), 0, 0)
    x = 14
    for ch in 'PHOENIX':
        g = lettering.glyph(ch, 12)
        out.blit(g, x, 0)
        x += g.w - 1          # outlines touch, like one word
    word_end = x + 1
    small = draw.Canvas(width, 7)
    sx = 15
    for ch in 'SKYBLOCK':
        g = font5x7.glyph(font5x7.FONT_3X5, ch)
        for gy, row in enumerate(g):
            for gx, c in enumerate(row):
                if c == '#':
                    small.set(sx + gx, 1 + gy, P.WHITE)
        sx += len(g[0]) + 1
    # centre SKYBLOCK under PHOENIX
    shift = max(0, ((14 + word_end) - (15 + sx - 2)) // 2)
    shifted = draw.Canvas(width, 7)
    shifted.blit(small, shift, 0)
    out.blit(_outlined(shifted), 0, 13)
    return out
