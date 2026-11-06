"""Pixel canvas helpers for the icon generator: an RGBA grid, hex colours, text for the contact sheet. No
anti-aliasing: every pixel is fully opaque or fully transparent. The sprites themselves are in sprites.py."""
from . import font5x7

SIZE = 16
CLEAR = (0, 0, 0, 0)


def rgb(hex_colour):
    h = hex_colour.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def rgba(hex_colour):
    return rgb(hex_colour) + (255,)


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


def opaque_or_clear(canvas):
    """Raises if a pixel is half transparent (the generator only writes alpha 0 or 255)."""
    for row in canvas.px:
        for p in row:
            if p[3] not in (0, 255):
                raise ValueError('pixel with alpha %d' % p[3])
    return canvas


def colour_count(canvas):
    return len({p for row in canvas.px for p in row if p[3] == 255})
