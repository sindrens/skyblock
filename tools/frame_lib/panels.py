"""Panel, plaque, player cell and soft tile: placeholder sprites (art) and the composed final glyphs (pack).

Sprites are small pieces the owner can repaint (resourcepack/frame/art/*.png); the finals are composed from them:
a panel is the 9-slice of ``panel_frame`` stretched to 176 x (rows*18+113), a divider and label strip between the
container and the player inventory, and ``panel_cell`` on every player slot. Everything is fully opaque except the
vanilla window's own rounded corners (``corner_mask`` in panels.json), so the panel covers the vanilla window exactly.
"""
import math

from icons_lib import draw

from . import palette as P

FRAME = 24          # panel_frame.png size; 9-slice with 8 px corners
CORNER = 8
CELL = 18
CAP = 12            # plaque cap width
MID = 8             # plaque middle piece width


def _rounded_depth(w, h, x, y, radius):
    """How many pixels (x, y) lies inside a rounded rectangle w x h with corner ``radius`` (0 = the outermost pixel
    ring, negative = outside). Pixel centres are sampled, so radius 3 clears exactly 3 pixels in each corner."""
    px, py = x + .5, y + .5
    dx = max(radius - px, px - (w - radius), 0)
    dy = max(radius - py, py - (h - radius), 0)
    if dx > 0 and dy > 0:
        return int(math.floor(radius - math.hypot(dx, dy)))
    return int(min(px, py, w - px, h - py))


# ---------------------------------------------------------------------------------------------- placeholder sprites

def sprite_panel_frame():
    """24 x 24, 8 px corners: dark-ember outline, brand-red band (light top-left, shaded bottom-right), gold trim,
    a light bevel on the inner top-left, parchment fill."""
    c = draw.Canvas(FRAME, FRAME)
    for y in range(FRAME):
        for x in range(FRAME):
            d = _rounded_depth(FRAME, FRAME, x, y, 3)
            if d < 0:
                continue
            bottom_right = (x + y) >= FRAME
            if d == 0:
                col = P.OUTLINE
            elif d == 1:
                col = P.RED_DARK if bottom_right else P.RED_LIGHT
            elif d in (2, 3):
                col = P.RED_DARK if (bottom_right and d == 2) else P.RED
            elif d == 4:
                col = P.GOLD_DARK if bottom_right else P.GOLD
            elif d == 5:
                col = P.PARCHMENT_SHADE if bottom_right else P.BEVEL
            else:
                col = P.PARCHMENT
            c.set(x, y, col)
    return c


def sprite_panel_cell():
    """18 x 18 player inventory cell in our style: inset (shadow top-left, light bottom-right), soft corners."""
    c = draw.Canvas(CELL, CELL)
    for y in range(CELL):
        for x in range(CELL):
            if (x, y) in ((0, 0), (CELL - 1, 0), (0, CELL - 1), (CELL - 1, CELL - 1)):
                continue
            if x == 0 or y == 0:
                col = P.CELL_SHADOW
            elif x == CELL - 1 or y == CELL - 1:
                col = P.CELL_LIGHT
            else:
                col = P.CELL
            c.set(x, y, col)
    return c


def sprite_tile():
    """18 x 18 soft tile behind a menu item: a light rounded square with a soft edge and a shade along the bottom."""
    c = draw.Canvas(CELL, CELL)
    for y in range(CELL):
        for x in range(CELL):
            d = _rounded_depth(CELL, CELL, x, y, 3)
            if d < 0:
                continue
            if d == 0:
                col = P.TILE_EDGE
            elif y == 1 or x == 1:
                col = P.TILE_LIGHT
            elif y == CELL - 2:
                col = P.PARCHMENT
            else:
                col = P.TILE
            c.set(x, y, col)
    return c


def _plaque(width, height):
    """A whole plaque: rounded ember-red plate, gold trim, dark outline, a glossy top and a shaded bottom."""
    c = draw.Canvas(width, height)
    for y in range(height):
        for x in range(width):
            d = _rounded_depth(width, height, x, y, 5)
            if d < 0:
                continue
            if d == 0:
                col = P.OUTLINE
            elif d == 1:
                col = P.GOLD_DARK if y >= height // 2 else P.GOLD
            elif d == 2 and y >= height - 4:
                col = P.RED_DARK
            elif y <= 4:
                col = P.RED_LIGHT
            elif y >= height - 5:
                col = P.RED_DARK
            else:
                col = P.RED
            c.set(x, y, col)
    return c


def sprite_plaque_parts(height):
    """(left cap 12 x h, middle 8 x h, right cap 12 x h) cut from one 32 px plaque."""
    whole = _plaque(CAP + MID + CAP, height)
    return crop(whole, 0, 0, CAP, height), crop(whole, CAP, 0, MID, height), crop(whole, CAP + MID, 0, CAP, height)


# ---------------------------------------------------------------------------------------------- canvas helpers

def crop(src, x0, y0, w, h):
    c = draw.Canvas(w, h)
    for y in range(h):
        for x in range(w):
            c.px[y][x] = src.get(x0 + x, y0 + y)
    return c


def paste(dst, src, ox, oy):
    """Copies every pixel (also transparent ones) of ``src`` to ``dst`` at (ox, oy)."""
    for y in range(src.h):
        for x in range(src.w):
            dst.set(ox + x, oy + y, src.px[y][x])


def over(dst, src, ox, oy):
    """Draws the opaque pixels of ``src`` over ``dst``."""
    dst.blit(src, ox, oy)


def nine_slice(src, w, h, corner):
    """``src`` stretched to w x h: corners copied, edges and centre tiled (pixel art, no scaling)."""
    out = draw.Canvas(w, h)
    sw, sh = src.w, src.h
    mid_w, mid_h = sw - 2 * corner, sh - 2 * corner
    for y in range(h):
        if y < corner:
            sy = y
        elif y >= h - corner:
            sy = sh - (h - y)
        else:
            sy = corner + (y - corner) % mid_h
        for x in range(w):
            if x < corner:
                sx = x
            elif x >= w - corner:
                sx = sw - (w - x)
            else:
                sx = corner + (x - corner) % mid_w
            out.px[y][x] = src.px[sy][sx]
    return out


# ---------------------------------------------------------------------------------------------- finals

def panel_height(cat, rows):
    return rows * 18 + cat['rows_extra']


def compose_panel(cat, rows, frame, cell):
    """The final panel glyph of a ``rows``-row chest: 176 x (rows*18+113)."""
    w, h = cat['window_width'], panel_height(cat, rows)
    out = nine_slice(frame, w, h, CORNER)
    inner_left, inner_right = 5, w - 6
    top = rows * 18 + 17
    for x in range(inner_left, inner_right + 1):       # divider + light label strip ("Inventory")
        out.set(x, top, P.RED_DARK)
        out.set(x, top + 1, P.RED)
        for y in range(top + 2, top + 12):
            out.set(x, y, P.BEVEL)
        out.set(x, top + 12, P.PARCHMENT_SHADE)
    for k in range(3):
        for col in range(9):
            over(out, cell, 7 + 18 * col, rows * 18 + 30 + 18 * k)
    for col in range(9):
        over(out, cell, 7 + 18 * col, rows * 18 + 88)
    apply_corner_mask(cat, out)
    return out


def corner_clear(cat, w, h):
    """The set of pixels the vanilla window leaves transparent."""
    m = cat['corner_mask']
    out = set()
    for key, ox, oy in (('tl', 0, 0), ('tr', w - 5, 0), ('bl', 0, h - 5), ('br', w - 5, h - 5)):
        for dy, row in enumerate(m[key]):
            for dx, ch in enumerate(row):
                if ch == '.':
                    out.add((ox + dx, oy + dy))
    return out


def apply_corner_mask(cat, img):
    """Clears the vanilla corner pixels, fills any other gap with the outline colour and re-draws the outline along the
    new edge, so the panel is opaque everywhere the vanilla window is."""
    clear = corner_clear(cat, img.w, img.h)
    for y in range(img.h):
        for x in range(img.w):
            if (x, y) in clear:
                img.px[y][x] = P.CLEAR
            elif img.px[y][x][3] == 0:
                img.px[y][x] = P.OUTLINE
    edge = []
    for y in range(img.h):
        for x in range(img.w):
            if img.px[y][x][3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < img.w and 0 <= ny < img.h) or img.px[ny][nx][3] == 0:
                    edge.append((x, y))
                    break
    for x, y in edge:
        img.px[y][x] = P.OUTLINE


def compose_plaque(width, height, left, mid, right):
    """Left cap + n middles + right cap, plus one transparent row below (glyph height 21 = ascent, GUI-FONT 3)."""
    out = draw.Canvas(width, height + 1)
    paste(out, left, 0, 0)
    x = left.w
    while x < width - right.w:
        paste(out, crop(mid, 0, 0, min(mid.w, width - right.w - x), height), x, 0)
        x += mid.w
    paste(out, right, width - right.w, 0)
    return out
