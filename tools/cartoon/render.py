#!/usr/bin/env python3
"""Icons v4: chunky cartoon menu icons (owner 2 Nov 2026). See tools/cartoon/README.md.

Every icon is drawn at S x S (256) with thick dark outlines, flat fills, a highlight and a shade, then scaled to 64x64.
Usage: python3 tools/cartoon/render.py [--only key,key] [--sheet-only]
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'resourcepack', 'pack', 'assets', 'phoenix', 'textures', 'item', 'icon')
SHEET = os.path.join(ROOT, 'resourcepack', 'preview', 'cartoon-sheet.png')
FONT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'Poppins-Bold.ttf')

S = 256          # drawing size
SIZE = 64        # PNG size
W = 14           # outline width at S
OL = (40, 24, 32, 255)          # outline
WHITE = (255, 255, 255, 255)


def rgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    """f < 1 darker, f > 1 lighter (towards white)."""
    r, g, b, a = c
    if f < 1:
        return (int(r * f), int(g * f), int(b * f), a)
    t = f - 1
    return (int(r + (255 - r) * t), int(g + (255 - g) * t), int(b + (255 - b) * t), a)


# ------------------------------------------------------------------------------------------------- primitives

class Pen:
    def __init__(self):
        self.img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    # filled shapes with outline -------------------------------------------------------------
    def circle(self, cx, cy, r, fill, outline=True, w=W):
        if not outline:
            self.d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill)
            return
        self.d.ellipse((cx - r - w / 2, cy - r - w / 2, cx + r + w / 2, cy + r + w / 2), fill=OL)
        ri = max(1, r - w / 2)
        self.d.ellipse((cx - ri, cy - ri, cx + ri, cy + ri), fill=fill)

    def ellipse(self, box, fill, outline=True, w=W):
        x0, y0, x1, y1 = box
        if outline:
            self.d.ellipse((x0 - w / 2, y0 - w / 2, x1 + w / 2, y1 + w / 2), fill=OL)
        self.d.ellipse((x0 + w / 2, y0 + w / 2, x1 - w / 2, y1 - w / 2), fill=fill)

    def rrect(self, box, r, fill, outline=True, w=W):
        x0, y0, x1, y1 = box
        if not outline:
            self.d.rounded_rectangle(box, r, fill=fill)
            return
        if outline:
            self.d.rounded_rectangle((x0 - w / 2, y0 - w / 2, x1 + w / 2, y1 + w / 2), r + w / 2, fill=OL)
        self.d.rounded_rectangle((x0 + w / 2, y0 + w / 2, x1 - w / 2, y1 - w / 2), max(1, r - w / 2), fill=fill)

    def poly(self, pts, fill, outline=True, w=W):
        if outline:
            self.d.polygon(pts, fill=OL)
            self._thick_closed(pts, w * 1.0)
        inner = shrink(pts, w / 2)
        self.d.polygon(inner, fill=fill)

    def _thick_closed(self, pts, w):
        n = len(pts)
        for i in range(n):
            self.d.line([pts[i], pts[(i + 1) % n]], fill=OL, width=int(w * 2))
            x, y = pts[i]
            self.d.ellipse((x - w, y - w, x + w, y + w), fill=OL)

    def line(self, pts, width, fill, outline=True, w=W):
        """A thick stroke with round ends and joints (cartoon outline around it)."""
        if outline:
            self._stroke(pts, width + 2 * w, OL)
        self._stroke(pts, width, fill)

    def _stroke(self, pts, width, color):
        r = width / 2
        for i in range(len(pts) - 1):
            self.d.line([pts[i], pts[i + 1]], fill=color, width=int(width))
        for x, y in pts:
            self.d.ellipse((x - r, y - r, x + r, y + r), fill=color)

    def arc_stroke(self, cx, cy, r, a0, a1, width, fill, outline=True, steps=40):
        pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / steps)),
                cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / steps))) for i in range(steps + 1)]
        self.line(pts, width, fill, outline)

    # decorations ---------------------------------------------------------------------------
    def shine(self, box, a=150):
        """A soft white highlight blob (top-left)."""
        layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        ImageDraw.Draw(layer).ellipse(box, fill=(255, 255, 255, a))
        self.img.alpha_composite(layer)

    def text(self, cx, cy, s, size, fill=WHITE, stroke=W * 0.9):
        f = ImageFont.truetype(FONT, int(size))
        self.d.text((cx, cy), s, font=f, fill=fill, anchor='mm', stroke_width=int(stroke), stroke_fill=OL)

    def sparkle(self, cx, cy, r, fill=WHITE):
        pts = []
        for i in range(8):
            ang = math.radians(i * 45 - 90)
            rr = r if i % 2 == 0 else r * 0.32
            pts.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
        self.poly(pts, fill, w=7)

    def star(self, cx, cy, r, fill, w=W, ratio=0.46):
        pts = []
        for i in range(10):
            ang = math.radians(i * 36 - 90)
            rr = r if i % 2 == 0 else r * ratio
            pts.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
        self.poly(pts, fill, w=w)

    def done(self):
        return self.img.resize((SIZE, SIZE), Image.LANCZOS)


def shrink(pts, d):
    """Moves every point towards the centroid by about d (a cheap inset for convex-ish shapes)."""
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    out = []
    for x, y in pts:
        dx, dy = x - cx, y - cy
        L = math.hypot(dx, dy) or 1
        out.append((x - dx / L * d, y - dy / L * d))
    return out


# ------------------------------------------------------------------------------------------------- shared parts

def coin(p, cx, cy, r, body, rim, sign='$', sign_size=None):
    p.circle(cx, cy, r, shade(body, 0.72))
    p.circle(cx, cy - r * 0.04, r * 0.80, body, w=8)
    p.shine((cx - r * 0.62, cy - r * 0.72, cx + r * 0.05, cy - r * 0.18), 110)
    if sign:
        p.text(cx, cy + r * 0.02, sign, sign_size or r * 1.25)


def button(p, fill, box=(30, 30, 226, 226), r=44):
    p.rrect(box, r, shade(fill, 0.70))
    x0, y0, x1, y1 = box
    p.rrect((x0, y0, x1, y1 - 18), r, fill, w=0, outline=False)
    p.d.rounded_rectangle((x0 + W / 2, y0 + W / 2, x1 - W / 2, y1 - 18 - W / 2), r - W / 2, fill=fill)
    p.shine((x0 + 20, y0 + 16, x0 + 100, y0 + 52), 120)


def round_button(p, fill, cx=128, cy=128, r=98):
    p.circle(cx, cy, r, shade(fill, 0.70))
    p.circle(cx, cy - 8, r - 10, fill, w=0, outline=False)
    p.shine((cx - r * 0.7, cy - r * 0.78, cx + r * 0.1, cy - r * 0.25), 120)


def arrow_left(p, cx, cy, s, fill=WHITE):
    pts = [(cx - s, cy), (cx - s * 0.05, cy - s * 0.85), (cx - s * 0.05, cy - s * 0.38), (cx + s, cy - s * 0.38),
           (cx + s, cy + s * 0.38), (cx - s * 0.05, cy + s * 0.38), (cx - s * 0.05, cy + s * 0.85)]
    p.poly(pts, fill, w=12)


def arrow_up(p, cx, cy, s, fill):
    pts = [(cx, cy - s), (cx + s * 0.85, cy - s * 0.05), (cx + s * 0.38, cy - s * 0.05), (cx + s * 0.38, cy + s),
           (cx - s * 0.38, cy + s), (cx - s * 0.38, cy - s * 0.05), (cx - s * 0.85, cy - s * 0.05)]
    p.poly(pts, fill, w=12)


def chevron(p, cx, cy, s, left):
    k = -1 if left else 1
    p.line([(cx - k * s * 0.45, cy - s), (cx + k * s * 0.55, cy), (cx - k * s * 0.45, cy + s)], 34, WHITE, w=12)


def cross(p, cx, cy, s, fill=WHITE, width=38):
    a = [(cx - s, cy - s), (cx + s, cy + s)]
    b = [(cx + s, cy - s), (cx - s, cy + s)]
    p._stroke(a, width + 24, OL)   # both outlines first, then both fills: one clean X
    p._stroke(b, width + 24, OL)
    p._stroke(a, width, fill)
    p._stroke(b, width, fill)


def check(p, cx, cy, s, fill=WHITE, width=40):
    p.line([(cx - s, cy), (cx - s * 0.3, cy + s * 0.7), (cx + s, cy - s * 0.75)], width, fill, w=12)


def gem(p, cx, cy, r, body):
    top = cy - r * 0.55
    pts = [(cx - r, top), (cx - r * 0.55, cy - r), (cx + r * 0.55, cy - r), (cx + r, top), (cx, cy + r)]
    p.poly(pts, body)
    p.d.polygon([(cx - r * 0.86, top + 2), (cx + r * 0.86, top + 2), (cx, cy + r * 0.82)], fill=shade(body, 0.82))
    p.d.polygon([(cx - r * 0.45, cy - r * 0.86), (cx + r * 0.45, cy - r * 0.86), (cx + r * 0.25, top),
                 (cx - r * 0.25, top)], fill=shade(body, 1.35))
    p.d.line([(cx - r * 0.86, top), (cx + r * 0.86, top)], fill=OL, width=6)
    p.d.line([(cx - r * 0.25, top), (cx, cy + r * 0.85), (cx + r * 0.25, top)], fill=shade(body, 0.6), width=5)


def book(p, cx, cy, w, h, cover, pages=rgb('f4ead2')):
    p.rrect((cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), 14, cover)
    p.d.rectangle((cx - w / 2 + 14, cy + h / 2 - 30, cx + w / 2 - 10, cy + h / 2 - 14), fill=pages)
    p.d.line([(cx - w / 2 + 12, cy + h / 2 - 30), (cx + w / 2 - 10, cy + h / 2 - 30)], fill=OL, width=5)
    p.d.rectangle((cx - w / 2 + 8, cy - h / 2 + 8, cx - w / 2 + 26, cy + h / 2 - 32), fill=shade(cover, 0.75))


def pickaxe(p, cx, cy, head, s=1.0):
    p.line([(cx - 70 * s, cy + 78 * s), (cx + 48 * s, cy - 40 * s)], 30 * s, rgb('b7793f'))
    p.d.line([(cx - 62 * s, cy + 64 * s), (cx + 40 * s, cy - 38 * s)], fill=shade(rgb('b7793f'), 1.25), width=int(7 * s))
    p.arc_stroke(cx + 40 * s, cy + 40 * s, 112 * s, 200, 340, 34 * s, head)
    p.arc_stroke(cx + 40 * s, cy + 40 * s, 112 * s, 215, 300, 10 * s, shade(head, 1.4), outline=False)


def bubble(p, fill, box=(32, 44, 224, 180)):
    x0, y0, x1, y1 = box
    p.poly([(x0 + 50, y1 - 6), (x0 + 30, y1 + 46), (x0 + 96, y1 - 6)], fill)
    p.rrect(box, 48, fill)
    p.d.polygon([(x0 + 52, y1 - 12), (x0 + 38, y1 + 30), (x0 + 92, y1 - 12)], fill=fill)
    p.shine((x0 + 22, y0 + 14, x0 + 96, y0 + 46), 110)


def gear(p, cx, cy, r, fill, teeth=8):
    pts = []
    for i in range(teeth * 2):
        ang = math.pi * 2 * i / (teeth * 2)
        rr = r if i % 2 == 0 else r * 0.78
        for k in (-0.17, 0.17):
            a = ang + k * math.pi * 2 / (teeth * 2)
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    p.poly(pts, fill)
    p.circle(cx, cy, r * 0.34, shade(fill, 0.55))


def chest(p, cx, cy, w, h, wood=rgb('b8743a'), band=rgb('f2c14e')):
    x0, y0, x1, y1 = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    p.rrect((x0, y0 + h * 0.38, x1, y1), 14, wood)
    p.rrect((x0, y0, x1, y0 + h * 0.44), 22, shade(wood, 1.12))
    p.rrect((cx - 16, y0 + h * 0.3, cx + 16, y0 + h * 0.62), 6, band, w=9)
    p.shine((x0 + 16, y0 + 10, x0 + 80, y0 + 30), 100)


def island_shape(p, cx, cy):
    p.poly([(cx - 96, cy - 6), (cx + 96, cy - 6), (cx + 50, cy + 70), (cx + 10, cy + 100), (cx - 30, cy + 74), (cx - 70, cy + 40)],
           rgb('9a6234'))
    p.rrect((cx - 104, cy - 34, cx + 104, cy + 6), 18, rgb('6cc24a'))
    p.shine((cx - 90, cy - 30, cx - 20, cy - 16), 90)


def house(p, cx, cy):
    p.rrect((cx - 72, cy - 10, cx + 72, cy + 86), 10, rgb('f2e3c6'))
    p.poly([(cx - 100, cy), (cx, cy - 92), (cx + 100, cy)], rgb('e2463c'))
    p.rrect((cx - 22, cy + 26, cx + 22, cy + 86), 6, rgb('9a6234'), w=10)
    p.rrect((cx + 34, cy + 14, cx + 62, cy + 42), 4, rgb('7fd3ff'), w=8)


def person(p, cx, cy, s, fill):
    p.circle(cx, cy - 44 * s, 30 * s, rgb('ffd7a8'))
    p.d.pieslice((cx - 58 * s, cy - 8 * s, cx + 58 * s, cy + 108 * s), 180, 360, fill=OL)
    p.d.pieslice((cx - 50 * s, cy, cx + 50 * s, cy + 100 * s), 180, 360, fill=fill)


def palette(p, cx, cy):
    p.ellipse((cx - 96, cy - 74, cx + 96, cy + 74), rgb('e9c89b'))
    p.circle(cx + 40, cy + 30, 18, (0, 0, 0, 0), outline=False)
    p.d.ellipse((cx + 26, cy + 18, cx + 54, cy + 46), fill=OL)
    for (x, y, c) in ((-52, -10, 'ef4b4b'), (-22, -42, 'f7c948'), (18, -44, '4bbf6b'), (52, -14, '3d9cf0'), (-40, 30, 'b061ff')):
        p.circle(cx + x, cy + y, 17, rgb(c), w=8)


def scroll(p, cx, cy, paper=rgb('f4e2b6')):
    p.rrect((cx - 70, cy - 86, cx + 70, cy + 86), 12, paper)
    p.rrect((cx - 90, cy - 102, cx + 90, cy - 70), 16, shade(paper, 0.82))
    p.rrect((cx - 90, cy + 70, cx + 90, cy + 102), 16, shade(paper, 0.82))
    for i, y in enumerate((-40, -10, 20, 50)):
        p.d.line([(cx - 44, cy + y), (cx + (44 if i % 2 == 0 else 20), cy + y)], fill=shade(paper, 0.55), width=10)


# ------------------------------------------------------------------------------------------------- icons

ICONS = {}


def icon(key, base=None, name=None, meaning=None, bases=None, parent=None):
    def wrap(fn):
        ICONS[key] = dict(fn=fn, base=base, name=name, meaning=meaning, bases=bases, parent=parent)
        return fn
    return wrap


GREEN = rgb('3cc95a')
BLUE = rgb('3d8ff0')
RED = rgb('ec3c48')
ORANGE = rgb('f78c25')
YELLOW = rgb('f7c62f')
GOLD = rgb('f5b82e')
PINK = rgb('ff7fa6')
AQUA = rgb('3fd6ff')
PURPLE = rgb('a35cf0')
GRAY = rgb('8b8f9c')


# currency ---------------------------------------------------------------------------------------
@icon('currency/money')
def _(p):
    coin(p, 128, 128, 104, GREEN, None, '$', 172)


@icon('currency/shards')
def _(p):
    for (x, top, w, c) in ((-62, 82, 36, 0.88), (62, 92, 34, 0.88), (0, 26, 44, 1.0)):
        cx = 128 + x
        body = shade(PINK, c)
        p.poly([(cx - w, 200), (cx - w, top + 40), (cx, top), (cx + w, top + 40), (cx + w, 200), (cx, 226)], body)
        p.d.polygon([(cx, top + 10), (cx + w - 8, top + 42), (cx + w - 8, 196), (cx, 216)], fill=shade(body, 0.8))
        p.d.line([(cx - w * 0.45, top + 46), (cx - w * 0.45, 190)], fill=shade(body, 1.5), width=8)


@icon('currency/crystals')
def _(p):
    gem(p, 128, 136, 98, AQUA)
    p.sparkle(206, 50, 30)


@icon('currency/credits')
def _(p):
    p.rrect((24, 58, 232, 198), 18, ORANGE)
    p.d.rounded_rectangle((44, 76, 212, 180), 10, outline=shade(ORANGE, 1.35), width=6)
    for y in (90, 128, 166):
        p.circle(24, y, 12, (0, 0, 0, 0), outline=False)
    p.star(128, 128, 50, WHITE, w=11)
    p.shine((40, 66, 120, 92), 100)


# nav ---------------------------------------------------------------------------------------------
@icon('nav/back')
def _(p):
    round_button(p, ORANGE)
    arrow_left(p, 128, 122, 62)


@icon('nav/prev')
def _(p):
    button(p, BLUE)
    chevron(p, 124, 118, 52, True)


@icon('nav/next')
def _(p):
    button(p, BLUE)
    chevron(p, 132, 118, 52, False)


@icon('nav/close')
def _(p):
    round_button(p, RED)
    cross(p, 128, 120, 46)


@icon('nav/danger')
def _(p):
    p.poly([(128, 22), (236, 216), (20, 216)], RED)
    p.d.polygon([(128, 50), (214, 204), (128, 204)], fill=shade(RED, 0.82))
    p.line([(128, 92), (128, 150)], 30, WHITE, w=10)
    p.circle(128, 185, 15, WHITE, w=10)


@icon('nav/help')
def _(p):
    round_button(p, YELLOW)
    p.text(128, 122, '?', 170)


@icon('nav/search')
def _(p):
    p.line([(160, 160), (222, 222)], 40, rgb('9a5a2e'))
    p.line([(186, 186), (212, 212)], 22, rgb('c07a43'), outline=False)
    p.circle(104, 104, 84, GOLD)
    p.circle(104, 104, 62, rgb('aee8ff'), w=10)
    p.d.pieslice((52, 52, 156, 156), 200, 260, fill=WHITE)
    p.shine((64, 56, 112, 92), 160)


# ui ------------------------------------------------------------------------------------------------
@icon('ui/info')
def _(p):
    round_button(p, BLUE)
    p.text(128, 122, 'i', 168)


@icon('ui/type')
def _(p):
    p.line([(64, 196), (200, 60)], 52, YELLOW)
    p.line([(184, 76), (210, 50)], 52, PINK)
    p.poly([(40, 222), (52, 170), (90, 208)], rgb('f4d3a6'), w=10)
    p.d.polygon([(40, 222), (46, 196), (64, 214)], fill=OL)
    p.line([(30, 238), (120, 238)], 12, OL, outline=False)


# state ---------------------------------------------------------------------------------------------
@icon('state/confirm')
def _(p):
    button(p, GREEN)
    check(p, 128, 118, 56)


@icon('state/cancel')
def _(p):
    button(p, RED)
    cross(p, 128, 116, 44)


@icon('state/on')
def _(p):
    p.rrect((18, 72, 238, 184), 56, GREEN)
    p.circle(182, 128, 40, WHITE, w=10)
    check(p, 78, 126, 28, WHITE, 24)


@icon('state/off')
def _(p):
    p.rrect((18, 72, 238, 184), 56, rgb('5b6070'))
    p.circle(74, 128, 40, rgb('e6e8ee'), w=10)
    cross(p, 178, 128, 20, rgb('e6e8ee'), 22)


@icon('state/locked')
def _(p):
    p.arc_stroke(128, 112, 54, 180, 360, 26, rgb('c9ced8'))
    p.line([(74, 112), (74, 128)], 26, rgb('c9ced8'))
    p.line([(182, 112), (182, 128)], 26, rgb('c9ced8'))
    p.rrect((50, 118, 206, 230), 22, GOLD)
    p.circle(128, 162, 16, OL, outline=False)
    p.d.polygon([(118, 166), (138, 166), (134, 200), (122, 200)], fill=OL)
    p.shine((66, 128, 130, 150), 110)


@icon('state/undo')
def _(p):
    p.arc_stroke(132, 140, 66, -90, 160, 34, ORANGE)
    p.poly([(36, 58), (110, 30), (110, 100)], ORANGE)


@icon('state/loading')
def _(p):
    p.rrect((54, 22, 202, 46), 10, rgb('b7793f'))
    p.rrect((54, 210, 202, 234), 10, rgb('b7793f'))
    p.poly([(72, 46), (184, 46), (138, 128), (184, 210), (72, 210), (118, 128)], rgb('d9f3ff'))
    p.d.polygon([(96, 70), (160, 70), (128, 116)], fill=GOLD)
    p.d.polygon([(128, 150), (164, 200), (92, 200)], fill=GOLD)


@icon('state/empty')
def _(p):
    p.rrect((40, 90, 216, 220), 16, rgb('c9a06a'))
    p.poly([(40, 90), (20, 54), (110, 54), (128, 90)], shade(rgb('c9a06a'), 1.15))
    p.poly([(216, 90), (236, 54), (146, 54), (128, 90)], shade(rgb('c9a06a'), 0.9))
    p.d.rectangle((52, 100, 204, 112), fill=shade(rgb('c9a06a'), 0.7))


# shop -----------------------------------------------------------------------------------------------
@icon('shop/buy')
def _(p):
    p.arc_stroke(128, 92, 44, 180, 360, 20, rgb('f2e3c6'))
    p.rrect((36, 86, 220, 226), 26, ORANGE)
    p.shine((52, 98, 118, 124), 110)
    p.text(128, 160, '$', 120)


@icon('shop/sell')
def _(p):
    coin(p, 108, 148, 86, GREEN, None, '$', 140)
    arrow_up(p, 196, 72, 50, YELLOW)


@icon('shop/sell_all')
def _(p):
    p.poly([(92, 70), (164, 70), (150, 96), (106, 96)], rgb('b8834f'))
    p.ellipse((40, 88, 214, 236), rgb('c9965e'))
    p.rrect((96, 84, 160, 104), 8, RED, w=8)
    p.shine((64, 112, 120, 140), 100)
    p.text(126, 166, '$', 110)
    arrow_up(p, 202, 66, 46, YELLOW)


@icon('shop/fill')
def _(p):
    chest(p, 128, 158, 196, 136)
    for (x, y, c) in ((-48, 64, GREEN), (0, 48, AQUA), (48, 64, YELLOW)):
        p.rrect((128 + x - 26, y - 26, 128 + x + 26, y + 26), 10, c, w=10)
    p.poly([(102, 104), (154, 104), (154, 118), (176, 118), (128, 150), (80, 118), (102, 118)], WHITE, w=9)


# features (hub) -------------------------------------------------------------------------------------
@icon('feature/bank', base='gold_block')
def _(p):
    p.ellipse((30, 84, 210, 220), PINK)
    p.circle(206, 146, 30, shade(PINK, 1.1), w=10)
    p.circle(198, 142, 5, OL, outline=False)
    p.circle(212, 142, 5, OL, outline=False)
    p.poly([(64, 92), (76, 52), (104, 86)], shade(PINK, 0.9), w=10)
    p.rrect((60, 204, 86, 234), 6, shade(PINK, 0.8), w=9)
    p.rrect((150, 204, 176, 234), 6, shade(PINK, 0.8), w=9)
    p.circle(150, 120, 6, OL, outline=False)
    p.d.line([(100, 96), (140, 96)], fill=OL, width=10)
    coin(p, 120, 50, 34, GOLD, None, '$', 50)
    p.shine((52, 100, 120, 130), 100)


@icon('feature/top')
def _(p):
    p.arc_stroke(64, 92, 34, 90, 270, 16, GOLD)
    p.arc_stroke(192, 92, 34, -90, 90, 16, GOLD)
    p.poly([(56, 40), (200, 40), (186, 128), (128, 160), (70, 128)], GOLD)
    p.rrect((112, 150, 144, 196), 6, shade(GOLD, 0.8), w=10)
    p.rrect((70, 192, 186, 228), 10, RED)
    p.star(128, 92, 34, WHITE, w=10)
    p.shine((72, 50, 120, 80), 120)


@icon('feature/depths_lit')
def _(p):
    p.circle(128, 128, 110, (255, 214, 92, 90), outline=False)
    p.circle(128, 128, 92, rgb('3a3550'))
    pickaxe(p, 118, 132, AQUA, 0.95)
    p.sparkle(196, 54, 30, YELLOW)


@icon('feature/auction')
def _(p):
    p.rrect((40, 196, 196, 228), 10, rgb('9a5a2e'))
    p.line([(76, 176), (170, 82)], 26, rgb('c07a43'))
    p.rrect((118, 22, 226, 96), 20, rgb('b8743a'))
    p.d.rectangle((144, 22 + W, 156, 96 - W), fill=GOLD)
    p.d.rectangle((188, 22 + W, 200, 96 - W), fill=GOLD)
    p.shine((132, 30, 180, 48), 110)


@icon('feature/home', base='oak_door')
def _(p):
    house(p, 128, 128)


@icon('feature/island', base='oak_sapling')
def _(p):
    island_shape(p, 128, 140)
    p.line([(150, 108), (150, 64)], 18, rgb('9a6234'))
    p.circle(150, 52, 38, rgb('3fae4a'))
    p.circle(118, 70, 26, rgb('58c45a'))
    p.rrect((60, 84, 96, 108), 6, rgb('9a9aa6'), w=9)


@icon('feature/depths')
def _(p):
    p.circle(128, 128, 104, rgb('3a3550'))
    pickaxe(p, 118, 132, rgb('c9ced8'), 0.95)


@icon('feature/travel', base='ender_eye')
def _(p):
    p.circle(128, 128, 104, rgb('f4ead2'))
    p.circle(128, 128, 84, shade(rgb('f4ead2'), 0.94), w=8)
    p.poly([(128, 40), (150, 128), (106, 128)], RED, w=10)
    p.poly([(128, 216), (150, 128), (106, 128)], rgb('e6e8ee'), w=10)
    p.circle(128, 128, 12, GOLD, w=8)


@icon('feature/sell', base='gold_nugget')
def _(p):
    for i, y in enumerate((196, 168, 140)):
        p.ellipse((52, y - 26, 204, y + 26), shade(GOLD, 0.8 + i * 0.08))
    coin(p, 128, 92, 70, GOLD, None, '$', 104)


@icon('feature/shop', base='emerald')
def _(p):
    p.rrect((44, 108, 212, 226), 10, rgb('f2e3c6'))
    p.rrect((96, 150, 160, 226), 6, rgb('9a6234'), w=10)
    for i in range(4):
        x0 = 30 + i * 49
        p.rrect((x0, 50, x0 + 49, 112), 12, RED if i % 2 == 0 else WHITE, w=10)
    p.rrect((24, 34, 232, 62), 10, shade(RED, 0.8))


@icon('feature/forge', base='smithing_table')
def _(p):
    p.poly([(36, 92), (220, 92), (190, 132), (160, 132), (170, 186), (86, 186), (96, 132), (36, 120)], rgb('6f7486'))
    p.rrect((70, 182, 186, 220), 8, rgb('4d5160'))
    p.shine((52, 98, 140, 114), 90)
    for (x, y) in ((150, 58), (186, 44), (118, 40)):
        p.sparkle(x, y, 18, YELLOW)


@icon('feature/skills', base='experience_bottle')
def _(p):
    p.circle(128, 128, 96, rgb('7bdc4c'))
    p.circle(128, 128, 70, rgb('b6f36a'), w=8)
    p.star(128, 128, 54, rgb('fff1a6'), w=10)
    p.shine((64, 56, 120, 90), 120)


@icon('feature/enchanter', base='enchanting_table')
def _(p):
    p.circle(128, 128, 110, (190, 120, 255, 80), outline=False)
    book(p, 128, 136, 156, 176, PURPLE)
    p.star(140, 122, 30, rgb('ffe6ff'), w=9)
    p.sparkle(206, 54, 24)
    p.sparkle(52, 196, 18)


@icon('feature/wardrobe', base='iron_chestplate')
def _(p):
    p.poly([(60, 40), (100, 40), (128, 64), (156, 40), (196, 40), (232, 92), (198, 116), (190, 220), (66, 220), (58, 116), (24, 92)], RED)
    p.d.polygon([(128, 70), (186, 110), (180, 210), (128, 210)], fill=shade(RED, 0.82))
    p.d.line([(128, 70), (128, 214)], fill=GOLD, width=10)
    p.shine((70, 60, 112, 110), 110)


@icon('feature/rewards', base='chest_minecart')
def _(p):
    p.rrect((40, 104, 216, 228), 12, RED)
    p.rrect((28, 74, 228, 116), 12, shade(RED, 1.15))
    p.d.rectangle((114, 80, 142, 222), fill=GOLD)
    p.ellipse((70, 26, 126, 80), GOLD, w=12)
    p.ellipse((130, 26, 186, 80), GOLD, w=12)
    p.shine((52, 82, 110, 100), 110)


@icon('feature/staff', base='iron_sword')
def _(p):
    p.poly([(128, 24), (220, 56), (210, 150), (128, 232), (46, 150), (36, 56)], BLUE)
    p.d.polygon([(128, 40), (204, 66), (196, 146), (128, 214)], fill=shade(BLUE, 0.82))
    p.star(128, 118, 46, GOLD, w=10)


@icon('feature/profiles', base='armor_stand')
def _(p):
    person(p, 92, 128, 0.9, rgb('6f8bff'))
    person(p, 160, 120, 1.0, ORANGE)


@icon('feature/titles', base='name_tag')
def _(p):
    p.poly([(30, 92), (202, 92), (234, 128), (202, 164), (30, 164)], GOLD)
    p.circle(200, 128, 10, OL, outline=False)
    for x in (60, 100, 140):
        p.d.line([(x, 120), (x + 26, 120)], fill=shade(GOLD, 0.55), width=10)
    p.d.line([(60, 140), (150, 140)], fill=shade(GOLD, 0.55), width=10)


@icon('feature/achievements', base='totem_of_undying')
def _(p):
    p.poly([(84, 24), (120, 24), (128, 96), (100, 96)], BLUE, w=10)
    p.poly([(172, 24), (136, 24), (128, 96), (156, 96)], RED, w=10)
    coin(p, 128, 160, 72, GOLD, None, None)
    p.star(128, 160, 40, WHITE, w=10)


@icon('feature/settings', base='comparator')
def _(p):
    gear(p, 128, 128, 100, rgb('a8aec0'))
    p.shine((60, 56, 118, 92), 100)


@icon('feature/store', base='diamond')
def _(p):
    gem(p, 128, 140, 96, rgb('ff5f8f'))
    p.star(196, 52, 26, YELLOW, w=9)


@icon('feature/rules', base='paper')
def _(p):
    scroll(p, 128, 128)


@icon('feature/quests', base='writable_book')
def _(p):
    scroll(p, 116, 136)
    p.circle(196, 64, 40, YELLOW)
    p.text(196, 62, '!', 70)


@icon('feature/collection', base='bookshelf')
def _(p):
    book(p, 84, 140, 70, 168, RED)
    book(p, 150, 136, 70, 176, BLUE)
    p.star(200, 56, 28, YELLOW, w=9)


# travel ---------------------------------------------------------------------------------------------
@icon('travel/spawn', base='campfire')
def _(p):
    p.poly([(128, 20), (196, 120), (186, 196), (128, 230), (70, 196), (60, 120)], ORANGE)
    p.poly([(128, 92), (166, 150), (160, 196), (128, 212), (96, 196), (90, 150)], YELLOW, w=10)
    p.shine((86, 98, 118, 140), 90)


@icon('travel/crate_yard', base='tripwire_hook')
def _(p):
    chest(p, 120, 150, 180, 140)
    p.line([(170, 92), (226, 36)], 20, GOLD)
    p.circle(166, 96, 26, GOLD)
    p.d.ellipse((156, 86, 176, 106), fill=OL)


@icon('travel/market', base='emerald')
def _(p):
    p.rrect((44, 108, 212, 226), 10, rgb('f2e3c6'))
    for i, c in enumerate((GREEN, YELLOW, RED)):
        p.circle(84 + i * 44, 186, 20, c, w=9)
    for i in range(4):
        x0 = 30 + i * 49
        p.rrect((x0, 50, x0 + 49, 112), 12, GREEN if i % 2 == 0 else WHITE, w=10)
    p.rrect((24, 34, 232, 62), 10, shade(GREEN, 0.8))


@icon('travel/visit', base='map')
def _(p):
    p.poly([(28, 60), (96, 40), (160, 60), (228, 40), (228, 196), (160, 216), (96, 196), (28, 216)], rgb('f4e2b6'))
    p.d.line([(96, 46), (96, 192)], fill=shade(rgb('f4e2b6'), 0.7), width=6)
    p.d.line([(160, 64), (160, 210)], fill=shade(rgb('f4e2b6'), 0.7), width=6)
    p.circle(128, 98, 36, RED)
    p.poly([(102, 116), (154, 116), (128, 170)], RED, w=10)
    p.circle(128, 98, 13, WHITE, w=7)


# island ---------------------------------------------------------------------------------------------
@icon('island/permissions', base='iron_door')
def _(p):
    p.rrect((56, 30, 200, 228), 14, rgb('9aa0ae'))
    p.rrect((78, 52, 178, 120), 8, rgb('c9ced8'), w=9)
    p.circle(166, 140, 9, OL, outline=False)
    p.arc_stroke(128, 168, 26, 180, 360, 12, rgb('e6e8ee'))
    p.rrect((96, 166, 160, 214), 10, GOLD, w=10)


@icon('island/warps', base='ender_pearl')
def _(p):
    p.circle(128, 128, 104, rgb('5a2ea6'))
    for r, c in ((76, '8a4fff'), (52, 'b98bff'), (28, 'efe0ff')):
        p.arc_stroke(128, 128, r, 0, 300, 16, rgb(c), outline=False)
    p.sparkle(200, 52, 22)


@icon('island/settings', base='repeater')
def _(p):
    gear(p, 128, 128, 96, rgb('6cc24a'))
    p.shine((60, 60, 116, 92), 100)


@icon('island/invites', base='paper')
def _(p):
    p.rrect((24, 60, 232, 200), 16, rgb('f4ead2'))
    p.poly([(36, 72), (220, 72), (128, 146)], shade(rgb('f4ead2'), 0.86), w=10)
    p.circle(196, 66, 30, RED)
    p.text(196, 64, '!', 48, stroke=8)


@icon('island/notes', base='writable_book')
def _(p):
    p.rrect((48, 34, 208, 228), 14, rgb('fff3a6'))
    for y in (92, 128, 164, 198):
        p.d.line([(74, y), (182, y)], fill=rgb('8fbce6'), width=6)
    p.circle(128, 46, 18, RED, w=9)


@icon('island/chat_on', base='oak_sign')
def _(p):
    bubble(p, GREEN)
    for x in (88, 128, 168):
        p.circle(x, 112, 13, WHITE, w=7)


@icon('island/chat_off', base='gray_dye')
def _(p):
    bubble(p, GRAY)
    p.line([(60, 196), (196, 40)], 18, RED, w=10)


@icon('island/chat_colors', base='orange_dye')
def _(p):
    bubble(p, rgb('ffffff'))
    for i, c in enumerate(('ef4b4b', 'f7c948', '4bbf6b', '3d9cf0')):
        p.circle(78 + i * 34, 112, 14, rgb(c), w=7)


@icon('island/visit', base='map')
def _(p):
    island_shape(p, 128, 168)
    p.circle(128, 70, 40, RED)
    p.poly([(98, 88), (158, 88), (128, 140)], RED, w=10)
    p.circle(128, 70, 14, WHITE, w=7)


@icon('island/stats', base='knowledge_book')
def _(p):
    p.rrect((24, 32, 232, 224), 18, rgb('2f3448'))
    for i, (h, c) in enumerate(((60, BLUE), (100, GREEN), (140, YELLOW), (90, ORANGE))):
        x0 = 50 + i * 44
        p.rrect((x0, 196 - h, x0 + 30, 196), 6, c, w=8)


@icon('island/bank', base='gold_block')
def _(p):
    p.rrect((30, 44, 226, 220), 18, rgb('7c8296'))
    p.circle(128, 132, 54, rgb('a8aec0'), w=10)
    for a in range(0, 360, 60):
        x = 128 + 40 * math.cos(math.radians(a))
        y = 132 + 40 * math.sin(math.radians(a))
        p.d.line([(128, 132), (x, y)], fill=OL, width=8)
    p.circle(128, 132, 14, GOLD, w=7)
    p.text(196, 196, '$', 70, fill=GOLD, stroke=8)


@icon('island/upgrades', base='anvil')
def _(p):
    p.circle(128, 128, 102, rgb('2f3448'))
    arrow_up(p, 128, 128, 74, GREEN)
    p.star(196, 62, 22, YELLOW, w=8)


@icon('island/visitors_open', base='oak_fence_gate')
def _(p):
    for x in (34, 222):
        p.rrect((x - 14, 60, x + 14, 220), 6, rgb('9a6234'), w=10)
    p.poly([(48, 90), (110, 70), (110, 200), (48, 180)], rgb('c07a43'), w=10)
    p.poly([(208, 90), (146, 70), (146, 200), (208, 180)], rgb('c07a43'), w=10)
    check(p, 128, 132, 26, GREEN, 22)


@icon('island/visitors_locked', base='iron_bars')
def _(p):
    p.rrect((30, 50, 226, 220), 10, rgb('6f7486'))
    for x in (64, 104, 152, 192):
        p.d.line([(x, 60), (x, 210)], fill=rgb('c9ced8'), width=12)
    p.arc_stroke(128, 140, 30, 180, 360, 14, rgb('e6e8ee'))
    p.rrect((90, 136, 166, 204), 12, GOLD, w=10)


# ------------------------------------------------------------------------------------------------- custom items
# Item textures (owner 2 Nov 2026: "crate keys per crate, custom pickaxe, skins, fuel cells, shard blocks, everything
# custom"). Key item/<id> (or a variant), base = the item's vanilla material.

def key_item(p, metal, gem_c):
    p.circle(84, 84, 58, metal)
    p.circle(84, 84, 24, (0, 0, 0, 0), outline=False)
    p.d.ellipse((62, 62, 106, 106), fill=OL)
    p.d.ellipse((70, 70, 98, 98), fill=(0, 0, 0, 0))
    p.line([(120, 120), (214, 214)], 34, metal)
    p.line([(170, 170), (200, 140)], 26, metal)
    p.line([(196, 196), (226, 166)], 26, metal)
    p.shine((42, 36, 92, 62), 140)
    p.circle(84, 40, 18, gem_c, w=8)


CRATE_KEYS = {
    'ember': ('c9733a', 'ff9a3d'), 'cinder': ('8b8f9c', 'ef5a3c'), 'blaze': ('f7a02e', 'fff06a'),
    'inferno': ('d8303a', 'ff8a1f'), 'phoenix': ('f5c542', 'ff2d3f'), 'eclipse': ('4a2a7a', 'f5c542'),
    'conqueror': ('6f86b8', 'd8303a'),
}
for _crate, (_metal, _gem) in CRATE_KEYS.items():
    def _make(metal=_metal, gemc=_gem):
        return lambda p: key_item(p, rgb(metal), rgb(gemc))
    icon('item/crate_key_' + _crate, base='tripwire_hook')(_make())


@icon('item/shard_block', base='infested_stone')
def _(p):
    p.poly([(128, 40), (224, 88), (224, 192), (128, 240), (32, 192), (32, 88)], rgb('7a7f8e'))
    p.d.polygon([(128, 54), (210, 94), (128, 134), (46, 94)], fill=rgb('9aa0ae'))
    p.d.polygon([(128, 140), (210, 98), (210, 186), (128, 228)], fill=rgb('5f6474'))
    for (x, top, w) in ((96, 34, 20), (140, 14, 26), (178, 44, 18)):
        p.poly([(x - w, 120), (x - w, top + 30), (x, top), (x + w, top + 30), (x + w, 120)], PINK, w=10)
        p.d.line([(x - w * 0.4, top + 34), (x - w * 0.4, 112)], fill=shade(PINK, 1.5), width=6)


@icon('item/fuel_cell', base='magma_cream')
def _(p):
    p.rrect((64, 52, 192, 234), 30, rgb('4d5160'))
    p.rrect((84, 74, 172, 214), 20, ORANGE, w=10)
    p.rrect((100, 26, 156, 58), 8, rgb('9aa0ae'))
    p.poly([(128, 92), (156, 140), (150, 182), (128, 196), (106, 182), (100, 140)], YELLOW, w=9)
    p.shine((92, 82, 118, 140), 120)


@icon('item/money_note', base='paper')
def _(p):
    p.rrect((18, 62, 238, 194), 14, rgb('6fd26a'))
    p.d.rounded_rectangle((38, 80, 218, 176), 10, outline=shade(rgb('6fd26a'), 0.7), width=6)
    p.circle(128, 128, 40, rgb('a8ec92'), w=9)
    p.text(128, 128, '$', 74, stroke=9)
    p.shine((34, 70, 110, 90), 110)


@icon('item/disenchant_scroll', base='paper')
def _(p):
    scroll(p, 128, 128, rgb('e9d8ff'))
    p.star(128, 128, 36, PURPLE, w=9)
    p.line([(96, 160), (160, 96)], 10, WHITE, outline=False)


@icon('item/tag_item', base='name_tag')
def _(p):
    p.poly([(30, 92), (202, 92), (234, 128), (202, 164), (30, 164)], GOLD)
    p.circle(200, 128, 10, OL, outline=False)
    p.star(110, 128, 26, WHITE, w=8)
    p.sparkle(60, 60, 22)


@icon('item/tool_blueprint', base='paper')
def _(p):
    p.rrect((26, 40, 230, 216), 12, rgb('3d7fd8'))
    for x in range(54, 220, 36):
        p.d.line([(x, 52), (x, 204)], fill=rgb('6aa0ea'), width=3)
    for y in range(70, 210, 36):
        p.d.line([(38, y), (218, y)], fill=rgb('6aa0ea'), width=3)
    p.arc_stroke(150, 150, 70, 200, 340, 14, WHITE, outline=False)
    p.line([(84, 184), (150, 110)], 12, WHITE, outline=False)


BOOSTER_COLORS = {'sell': 'f5c542', 'skill_xp': '7bdc4c', 'tool_xp': '3fd6ff', 'crystals': 'b98bff'}
BOOSTER_BASES = {'sell': 'gold_ingot', 'skill_xp': 'lapis_lazuli', 'tool_xp': 'echo_shard', 'crystals': 'prismarine_crystals'}


def booster(p, color, global_=False):
    p.rrect((104, 22, 152, 58), 8, rgb('b8834f'))
    p.circle(128, 150, 82, rgb(color))
    p.d.pieslice((60, 82, 196, 218), 20, 160, fill=shade(rgb(color), 0.82))
    p.shine((76, 92, 122, 124), 130)
    arrow_up(p, 128, 150, 42, WHITE)
    if global_:
        p.star(200, 54, 30, YELLOW, w=9)


for _t, _c in BOOSTER_COLORS.items():
    def _mk(c=_c, glob=False):
        return lambda p: booster(p, c, glob)
    icon('item/booster_' + _t, base=BOOSTER_BASES[_t])(_mk())
    icon('item/global_booster_' + _t, base='nether_star')(_mk(glob=True))


# Depths tools: one picture per tier (head colour per tier), held like vanilla tools.
def tool(p, kind, head, handle=rgb('b7793f'), glow=None):
    if glow:
        p.circle(128, 128, 112, glow, outline=False)
    if kind == 'pickaxe':
        p.line([(40, 216), (176, 80)], 26, handle)
        p.arc_stroke(40, 216, 190, -76, -14, 40, head)
        p.arc_stroke(40, 216, 190, -68, -42, 10, shade(head, 1.45), outline=False)
    elif kind == 'axe':
        p.line([(42, 214), (176, 80)], 26, handle)
        p.poly([(124, 54), (184, 36), (226, 92), (198, 150), (150, 118)], head)
        p.d.polygon([(150, 68), (182, 56), (210, 94), (192, 126)], fill=shade(head, 1.3))
    elif kind == 'sword':
        p.line([(48, 208), (88, 168)], 26, handle)
        p.line([(56, 150), (106, 200)], 24, rgb('f5c542'))
        p.poly([(86, 158), (204, 40), (226, 30), (216, 52), (98, 170)], head)
        p.d.line([(104, 150), (208, 46)], fill=shade(head, 1.45), width=8)
    elif kind == 'hoe':
        p.line([(42, 214), (176, 80)], 26, handle)
        p.poly([(150, 70), (210, 40), (230, 62), (176, 100)], head)
        p.poly([(196, 48), (232, 64), (226, 116), (204, 104)], head)


TOOL_TIERS = {
    'pickaxe': [('coal', '3c3c44', 'stone_pickaxe'), ('copper', 'd9824b', 'copper_pickaxe'), ('iron', 'd8dde6', 'iron_pickaxe'),
                ('gold', 'f5c542', 'iron_pickaxe,golden_pickaxe'), ('redstone', 'e0302f', 'iron_pickaxe'), ('lapis', '2f5fd0', 'iron_pickaxe'),
                ('diamond', '4fe3e0', 'diamond_pickaxe'), ('emerald', '3ccf6a', 'diamond_pickaxe'), ('amethyst', 'a35cf0', 'diamond_pickaxe'),
                ('quartz', 'efe7dc', 'diamond_pickaxe'), ('netherite', '5a4a52', 'netherite_pickaxe'), ('voidstone', '3a1a66', 'netherite_pickaxe')],
    'axe': [('oak', 'b8834f', 'wooden_axe'), ('birch', '9aa0ae', 'stone_axe'), ('spruce', 'd9824b', 'copper_axe'), ('jungle', 'd8dde6', 'iron_axe'),
            ('acacia', 'e0884a', 'iron_axe'), ('dark_oak', '6a4a32', 'iron_axe'), ('mangrove', 'b0403a', 'diamond_axe'),
            ('cherry', 'f2a7c0', 'diamond_axe'), ('pale_oak', 'e6e0d6', 'diamond_axe'), ('crimson', 'b02e5a', 'netherite_axe'),
            ('warped', '2ea89a', 'netherite_axe')],
    'sword': [('grave', '7f8a7a', 'stone_sword'), ('bone', 'f1ecd8', 'copper_sword'), ('venom', '6ac24a', 'iron_sword'),
              ('slime', '7fdc6a', 'iron_sword'), ('tide', '3fb6d6', 'iron_sword'), ('hex', 'a35cf0', 'diamond_sword'),
              ('raider', '8a6a52', 'diamond_sword'), ('blaze', 'ff9a2e', 'diamond_sword'), ('wither', '2e2a30', 'netherite_sword'),
              ('warlord', 'c9302f', 'netherite_sword')],
    'hoe': [('basic', '9aa0ae', 'stone_hoe'), ('copper', 'd9824b', 'copper_hoe'), ('iron', 'd8dde6', 'iron_hoe'),
            ('gold', 'f5c542', 'iron_hoe,golden_hoe'), ('diamond', '4fe3e0', 'diamond_hoe'), ('netherite', '5a4a52', 'netherite_hoe'),
            ('sunscorch', 'ff8a2e', 'netherite_hoe'), ('briarthorn', '4a8a3a', 'netherite_hoe'), ('netherbloom', 'c9305a', 'netherite_hoe'),
            ('torchbloom', 'ffd24a', 'netherite_hoe'), ('verdant', '3ccf6a', 'netherite_hoe')],
}
GLOWING = {'voidstone': (160, 90, 255, 70), 'warlord': (255, 80, 60, 60), 'verdant': (90, 255, 120, 60), 'warped': (60, 230, 200, 60)}
for _kind, _tiers in TOOL_TIERS.items():
    for _id, _col, _bases in _tiers:
        _b = _bases.split(',')
        def _mt(kind=_kind, col=_col, tid=_id):
            return lambda p: tool(p, kind, rgb(col), glow=GLOWING.get(tid))
        icon('item/tool_%s_%s' % (_kind, _id), base=_b[0], bases=_b[1:], parent='minecraft:item/handheld')(_mt())

# Fishing rods (owner 2 Nov 2026): one picture per rod tier; held like the vanilla rod.
ROD_TIERS = [('basic', 'b7793f', 'e0302f'), ('reed', 'c9b26a', 'f7c62f'), ('willow', '7a9a4a', 'f2e3c6'), ('copper', 'd9824b', '3fb6d6'),
             ('iron', 'd8dde6', 'e0302f'), ('gold', 'f5c542', 'ffffff'), ('coral', 'ff7f8f', 'ffd24a'), ('diamond', '4fe3e0', 'ffffff'),
             ('emerald', '3ccf6a', 'f5c542'), ('stormcall', '6f86b8', 'fff06a'), ('netherite', '5a4a52', 'ff8a2e'), ('abyssal', '3a1a66', '3fd6ff')]


def rod(p, stick, bob):
    p.line([(40, 220), (120, 120), (200, 36)], 20, stick)
    p.d.line([(200, 36), (206, 140)], fill=OL, width=7)
    p.circle(206, 172, 34, bob, w=11)
    p.d.pieslice((178, 144, 234, 200), 0, 180, fill=WHITE)
    p.d.arc((176, 142, 236, 202), 0, 360, fill=OL, width=10)
    p.d.line([(176, 172), (236, 172)], fill=OL, width=8)


for _id, _stick, _bob in ROD_TIERS:
    def _mr(stick=_stick, bob=_bob):
        return lambda p: rod(p, rgb(stick), rgb(bob))
    icon('item/tool_rod_' + _id, base='fishing_rod', parent='minecraft:item/handheld_rod')(_mr())


for _skin, (_head, _handle, _glow) in {'frostbite': ('9fe6ff', 'e8f8ff', None), 'phoenix_flame': ('ff5a2e', 'ffd24a', None),
                                         'royal_gold': ('f5c542', '7a3fd0', None)}.items():
    def _msr(stick=_head, bob=_handle):
        return lambda p: rod(p, rgb(stick), rgb(bob))
    icon('item/skin_%s_rod' % _skin, base='fishing_rod', parent='minecraft:item/handheld_rod')(_msr())


# Island tools (Complete Developer Spec D2/T3): wood, stone, iron, gold, diamond, netherite for each kind; a warm handle
# and a small ember gem so they read as "island" next to the Depths tools.
ISLAND_TIERS = [('wood', 'b8834f', 'wooden'), ('stone', '9aa0ae', 'stone'), ('iron', 'd8dde6', 'iron'), ('gold', 'f5c542', 'golden'),
                ('diamond', '4fe3e0', 'diamond'), ('netherite', '5a4a52', 'netherite')]


def island_tool(p, kind, head):
    tool(p, kind, head, rgb('c98a4a'))
    p.circle(70, 186, 14, rgb('ff8c1a'), w=8)


for _kind in ('pickaxe', 'axe', 'hoe', 'sword'):
    for _id, _col, _mat in ISLAND_TIERS:
        def _mi(kind=_kind, col=_col):
            return lambda p: island_tool(p, kind, rgb(col))
        icon('item/island_%s_%s' % (_kind, _id), base='%s_%s' % (_mat, _kind), parent='minecraft:item/handheld')(_mi())


# Tool skins (/tool → Skins): one picture per skin and kind, on every base that kind can have.
SKINS = {'frostbite': ('9fe6ff', 'e8f8ff', (150, 230, 255, 70)), 'phoenix_flame': ('ff5a2e', 'ffd24a', (255, 140, 40, 80)),
         'royal_gold': ('f5c542', '7a3fd0', None)}
for _skin, (_head, _handle, _glow) in SKINS.items():
    for _kind, _tiers in TOOL_TIERS.items():
        _all = []
        for _id, _col, _bases in _tiers:
            for b in _bases.split(','):
                if b not in _all:
                    _all.append(b)
        def _ms(kind=_kind, head=_head, handle=_handle, glow=_glow):
            return lambda p: tool(p, kind, rgb(head), rgb(handle), glow)
        icon('item/skin_%s_%s' % (_skin, _kind), base=_all[0], bases=_all[1:], parent='minecraft:item/handheld')(_ms())


# ------------------------------------------------------------------------------------------------- output

def file_of(key):
    return key.replace('/', '_')


# Owner art (3 Nov 2026): the owner's own icon set, 128x128 masters in tools/cartoon/owner/<group>__<name>.png, cut from
# the sheets he sent. Where he drew several looks (a/b/c), OWNER_PICKS says which one is used; change a pick and re-run.
OWNER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'owner')
OWNER_PICKS = {
    'nav/back': 'nav__back_c', 'nav/prev': 'nav__prev_c', 'nav/next': 'nav__next_c', 'nav/close': 'nav__close_b',
    'state/confirm': 'state__confirm_c', 'state/cancel': 'state__cancel_c',
    'shop/buy': 'shop__buy_a', 'shop/sell': 'shop__sell_a', 'shop/sell_all': 'shop__sell_all_a',
}


def owner_art(key):
    """The owner's picture for an icon key (64x64), or None when he has not drawn one."""
    name = OWNER_PICKS.get(key, key.replace('/', '__'))
    path = os.path.join(OWNER, name + '.png')
    if not os.path.exists(path):
        return None
    return Image.open(path).convert('RGBA').resize((64, 64), Image.LANCZOS)


def render(key):
    art = owner_art(key)
    if art is not None:
        return art
    p = Pen()
    ICONS[key]['fn'](p)
    return p.done()


def sheet(images):
    cols = 10
    cell = 96
    keys = list(images)
    rows = (len(keys) + cols - 1) // cols
    img = Image.new('RGBA', (cols * cell, rows * (cell + 18) + 10), (139, 139, 139, 255))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, 10)
    for i, k in enumerate(keys):
        x = (i % cols) * cell
        y = (i // cols) * (cell + 18) + 6
        d.rectangle((x + 14, y, x + 14 + 68, y + 68), fill=(198, 198, 198, 255), outline=(55, 55, 55, 255), width=2)
        img.alpha_composite(images[k], (x + 16, y + 2))
        d.text((x + cell / 2, y + 80), k, font=f, fill=(20, 20, 20, 255), anchor='mm')
    return img


def main(argv):
    only = None
    if '--only' in argv:
        only = set(argv[argv.index('--only') + 1].split(','))
    os.makedirs(OUT, exist_ok=True)
    images = {}
    import json
    with open(os.path.join(ROOT, 'resourcepack', 'icons.json'), encoding='utf-8') as f:
        catalog = {i['key'] for i in json.load(f)['icons']}
    for key in ICONS:
        if only and key not in only:
            continue
        if key not in catalog:  # drawn once but taken out of the pack (owner 3 Nov 2026: tools and rods look vanilla)
            continue
        images[key] = render(key)
        if '--sheet-only' not in argv:
            small = images[key].quantize(colors=48, method=Image.Quantize.FASTOCTREE).convert('RGBA')
            small.save(os.path.join(OUT, file_of(key) + '.png'), optimize=True)
    os.makedirs(os.path.dirname(SHEET), exist_ok=True)
    sheet(images).save(SHEET)
    print('cartoon: %d icons -> %s, sheet %s' % (len(images), os.path.relpath(OUT, ROOT), os.path.relpath(SHEET, ROOT)))


if __name__ == '__main__':
    main(sys.argv[1:])
