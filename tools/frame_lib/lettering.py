"""Cartoon title lettering (our own style, GUI-FONT 4.2): every glyph is a few polyline strokes on a small grid, swept
with a 2 x 2 brush (chunky 2 px strokes), outlined in dark ember by 8-neighbour dilation with the outer corner pixels
dropped (rounded silhouette), filled with a vertical flame gradient and a white glint on the top-left shoulders. All
caps. The colours are baked in, so the text colour is white.

Grid: x 0..4 (M, N, W 0..5; I, 0 and punctuation narrower), y 0..8 (top to bottom). Pixel = grid + 1 (outline), so a
glyph is 12 px tall (body 10 + outline 2) and 4..9 px wide. Developer note: the spec's 3 x 3 plus brush leaves 1 px
counters that close up at this size; the 2 x 2 brush keeps 2 px dark counters (A, B, O, 8 read at GUI scale 2).
"""
from icons_lib import draw

from . import palette as P

STROKES = {
    'A': [[(0, 8), (0, 2), (2, 0), (4, 2), (4, 8)], [(0, 5), (4, 5)]],
    'B': [[(0, 0), (0, 8), (3, 8), (4, 7), (4, 5), (3, 4), (0, 4)], [(0, 0), (3, 0), (4, 1), (4, 3), (3, 4)]],
    'C': [[(4, 1), (3, 0), (1, 0), (0, 1), (0, 7), (1, 8), (3, 8), (4, 7)]],
    'D': [[(0, 0), (0, 8), (2, 8), (4, 6), (4, 2), (2, 0), (0, 0)]],
    'E': [[(4, 0), (0, 0), (0, 8), (4, 8)], [(0, 4), (3, 4)]],
    'F': [[(4, 0), (0, 0), (0, 8)], [(0, 4), (3, 4)]],
    'G': [[(4, 0), (1, 0), (0, 1), (0, 7), (1, 8), (3, 8), (4, 7), (4, 5), (2, 5)]],
    'H': [[(0, 0), (0, 8)], [(4, 0), (4, 8)], [(0, 4), (4, 4)]],
    'I': [[(0, 0), (0, 8)]],
    'J': [[(4, 0), (4, 7), (3, 8), (1, 8), (0, 7), (0, 6)]],
    'K': [[(0, 0), (0, 8)], [(4, 0), (1, 4), (4, 8)]],
    'L': [[(0, 0), (0, 8), (4, 8)]],
    'M': [[(0, 8), (0, 0), (2, 3), (3, 3), (5, 0), (5, 8)]],
    'N': [[(0, 8), (0, 0), (5, 8), (5, 0)]],
    'O': [[(1, 0), (3, 0), (4, 1), (4, 7), (3, 8), (1, 8), (0, 7), (0, 1), (1, 0)]],
    'P': [[(0, 8), (0, 0), (3, 0), (4, 1), (4, 3), (3, 4), (0, 4)]],
    'Q': [[(1, 0), (3, 0), (4, 1), (4, 6), (2, 8), (1, 8), (0, 7), (0, 1), (1, 0)], [(2, 5), (5, 8)]],
    'R': [[(0, 8), (0, 0), (3, 0), (4, 1), (4, 3), (3, 4), (0, 4)], [(2, 4), (4, 8)]],
    'S': [[(4, 1), (3, 0), (1, 0), (0, 1), (0, 3), (1, 4), (3, 4), (4, 5), (4, 7), (3, 8), (1, 8), (0, 7)]],
    'T': [[(0, 0), (4, 0)], [(2, 0), (2, 8)]],
    'U': [[(0, 0), (0, 7), (1, 8), (3, 8), (4, 7), (4, 0)]],
    'V': [[(0, 0), (0, 4), (2, 8), (4, 4), (4, 0)]],
    'W': [[(0, 0), (0, 8), (2, 5), (3, 5), (5, 8), (5, 0)]],
    'X': [[(0, 0), (0, 1), (4, 7), (4, 8)], [(4, 0), (4, 1), (0, 7), (0, 8)]],
    'Y': [[(0, 0), (0, 2), (2, 4), (4, 2), (4, 0)], [(2, 4), (2, 8)]],
    'Z': [[(0, 0), (4, 0), (4, 1), (0, 7), (0, 8), (4, 8)]],
    '0': [[(1, 0), (2, 0), (3, 1), (3, 7), (2, 8), (1, 8), (0, 7), (0, 1), (1, 0)]],
    '1': [[(0, 2), (2, 0), (2, 8)], [(0, 8), (4, 8)]],
    '2': [[(0, 1), (1, 0), (3, 0), (4, 1), (4, 3), (0, 8), (4, 8)]],
    '3': [[(0, 1), (1, 0), (3, 0), (4, 1), (4, 3), (3, 4), (1, 4)], [(3, 4), (4, 5), (4, 7), (3, 8), (1, 8), (0, 7)]],
    '4': [[(3, 8), (3, 0), (0, 5), (4, 5)]],
    '5': [[(4, 0), (0, 0), (0, 4), (3, 4), (4, 5), (4, 7), (3, 8), (0, 8)]],
    '6': [[(3, 0), (1, 0), (0, 2), (0, 7), (1, 8), (3, 8), (4, 7), (4, 5), (3, 4), (0, 4)]],
    '7': [[(0, 0), (4, 0), (4, 1), (1, 8)]],
    '8': [[(1, 3), (0, 2), (0, 1), (1, 0), (3, 0), (4, 1), (4, 2), (3, 3), (1, 3), (0, 4), (0, 7), (1, 8), (3, 8), (4, 7),
           (4, 4), (3, 3)]],
    '9': [[(1, 8), (3, 8), (4, 6), (4, 1), (3, 0), (1, 0), (0, 1), (0, 3), (1, 4), (4, 4)]],
    '&': [[(0, 4), (4, 4)], [(2, 2), (2, 6)]],   # a chunky plus: a real ampersand is a blob at 12 px
    "'": [[(0, 0), (0, 2)]],
    '-': [[(0, 4), (2, 4)]],
    '!': [[(0, 0), (0, 4)], [(0, 8), (0, 8)]],
    '?': [[(0, 1), (1, 0), (3, 0), (4, 1), (4, 2), (2, 4), (2, 5)], [(2, 8), (2, 8)]],
    '(': [[(2, 0), (1, 1), (0, 3), (0, 5), (1, 7), (2, 8)]],
    ')': [[(0, 0), (1, 1), (2, 3), (2, 5), (1, 7), (0, 8)]],
    '/': [[(0, 8), (3, 0)]],
    '.': [[(0, 8), (0, 8)]],
    ',': [[(1, 6), (1, 7), (0, 8)]],
    ':': [[(0, 2), (0, 2)], [(0, 7), (0, 7)]],
}

PAD = 1          # the outline
# body pixels (cell coordinates) taken out after the sweep: the waist of 8, so it never reads as O
CUTS = {'8': ((1, 4), (6, 4))}
BRUSH = ((0, 0), (1, 0), (0, 1), (1, 1))
GRADIENT = (P.LETTER_TOP, P.LETTER_TOP, P.LETTER_HIGH, P.LETTER_HIGH, P.LETTER_MID, P.LETTER_MID,
            P.LETTER_LOW, P.LETTER_LOW, P.LETTER_BOTTOM, P.LETTER_BOTTOM)


def _line(a, b):
    """Grid points of a stroke segment, sampled finely and rounded (no gaps on diagonals)."""
    (x0, y0), (x1, y1) = a, b
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 4) + 1
    pts = set()
    for i in range(steps + 1):
        t = i / steps
        pts.add((int(round(x0 + (x1 - x0) * t + 1e-9)), int(round(y0 + (y1 - y0) * t + 1e-9))))
    return pts


def body(ch):
    """The set of body pixels (in cell coordinates) of ``ch``."""
    pts = set()
    for stroke in STROKES[ch]:
        if len(stroke) == 1:
            pts |= _line(stroke[0], stroke[0])
        for a, b in zip(stroke, stroke[1:]):
            pts |= _line(a, b)
    out = set()
    for (x, y) in pts:
        for dx, dy in BRUSH:
            out.add((x + dx + PAD, y + dy + PAD))
    return out - set(CUTS.get(ch, ()))


def glyph(ch, height):
    """The canvas of one letter: tight width (outline included), ``height`` rows (art at the top)."""
    b = body(ch)
    outline = set()
    for (x, y) in b:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                p = (x + dx, y + dy)
                if p not in b:
                    outline.add(p)
    # rounded silhouette: drop outline pixels that touch the body only diagonally and lie on the outside
    corner = {p for p in outline if not any((p[0] + dx, p[1] + dy) in b for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
              and _outside(p, b, outline)}
    outline -= corner
    xs = [p[0] for p in b | outline]
    width = max(xs) + 1
    c = draw.Canvas(width, height)
    for (x, y) in outline:
        c.set(x, y, P.OUTLINE)
    for (x, y) in b:
        row = y - 1
        col = GRADIENT[max(0, min(len(GRADIENT) - 1, row))]
        c.set(x, y, col)
    # a lighter pixel on the top-left of each round shoulder: body pixels with outline above and to the left
    for (x, y) in b:
        if (x - 1, y) not in b and (x, y - 1) not in b and (x + 1, y) in b and (x, y + 1) in b and y <= 3:
            c.set(x, y, P.WHITE)
    return c


def _outside(p, b, outline):
    """True when ``p`` can reach the cell border without crossing the body (it is not inside a counter)."""
    seen = {p}
    stack = [p]
    while stack:
        x, y = stack.pop()
        if x < 0 or y < 0 or x > 12 or y > 12:
            return True
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in seen and q not in b:
                seen.add(q)
                stack.append(q)
    return False


def sheet(chars, cols, cell_w, cell_h, art_h):
    """The letters sprite: ``cols`` x rows cells of cell_w x art_h, each glyph left-aligned."""
    rows = (len(chars) + cols - 1) // cols
    out = draw.Canvas(cols * cell_w, rows * art_h)
    for i, ch in enumerate(chars):
        g = glyph(ch, art_h)
        out.blit(g, (i % cols) * cell_w, (i // cols) * art_h)
    return out


def final_sheet(sprite, chars, cols, cell_w, cell_h, art_h):
    """The font texture: the sprite's cells of cell_w x art_h placed at the top of cells of cell_w x cell_h."""
    rows = (len(chars) + cols - 1) // cols
    out = draw.Canvas(cols * cell_w, rows * cell_h)
    for r in range(rows):
        for c in range(cols):
            for y in range(art_h):
                for x in range(cell_w):
                    out.px[r * cell_h + y][c * cell_w + x] = sprite.get(c * cell_w + x, r * art_h + y)
    return out


def advances(final, chars, cols, cell_w, cell_h, pad):
    """char -> advance as the client computes it: right-most non-transparent column + 1, plus ``pad``."""
    out = {}
    for i, ch in enumerate(chars):
        x0, y0 = (i % cols) * cell_w, (i // cols) * cell_h
        right = -1
        for x in range(cell_w):
            if any(final.px[y0 + y][x0 + x][3] for y in range(cell_h)):
                right = x
        out[ch] = right + 1 + pad
    return out
