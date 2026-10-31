"""Placeholder art for every catalogue key (the owner replaces any PNG by drawing over it; the generator never
overwrites an existing PNG unless --force-art).

Slice 1 and the generic state icons have a hand-built picture; every other key (features, travel, island, shop) gets
the generic badge: a rounded tile in its meaning colour with the first letter of its name. Real motifs for those are
the owner's job (resourcepack/README.md).
"""
import math

from . import draw
from .draw import sprite

DOT = '.'


def _mask(fn):
    """16 rows built from fn(x, y) -> character ('.' = transparent)."""
    return [''.join(fn(x, y) or DOT for x in range(draw.SIZE)) for y in range(draw.SIZE)]


def _dist(x, y, cx, cy):
    return math.hypot(x - cx, y - cy)


# ---------------------------------------------------------------------------------------------- slice 1

def menu():
    """Five-point star with a small red ember core (the hotbar menu item)."""
    return sprite([
        '................',
        '.......##.......',
        '.......##.......',
        '......####......',
        '......####......',
        '.##############.',
        '..############..',
        '...####rr####...',
        '....###rr###....',
        '....########....',
        '...##########...',
        '...####..####...',
        '..####....####..',
        '..###......###..',
        '..##........##..',
        '................',
    ], {'#': draw.REWARD, 'r': draw.PHOENIX_RED})


def currency_money():
    """Green coin with a $ mark."""
    return sprite([
        '................',
        '.....######.....',
        '...##########...',
        '..#####d######..',
        '.#####dddd#####.',
        '.#####d########.',
        '.#####d########.',
        '.#####dddd#####.',
        '.########d#####.',
        '.########d#####.',
        '.#####dddd#####.',
        '..#####d######..',
        '..############..',
        '...##########...',
        '.....######.....',
        '................',
    ], {'#': draw.MONEY, 'd': '#1E7A3E'})


def currency_crystals():
    """Faceted aqua gem."""
    return sprite([
        '................',
        '................',
        '................',
        '....########....',
        '...#f##ff##f#...',
        '..#f##f##f##f#..',
        '.##############.',
        '..##f######f##..',
        '...##f####f##...',
        '....##f##f##....',
        '.....##ff##.....',
        '......####......',
        '.......##.......',
        '................',
        '................',
        '................',
    ], {'#': draw.CRYSTALS, 'f': '#C8F8FF'})


def currency_shards():
    """Two pink prism shards."""
    return sprite([
        '................',
        '................',
        '..........###...',
        '.........####...',
        '........####....',
        '.......####.....',
        '......####......',
        '.....####.......',
        '....####...ss...',
        '...####...sss...',
        '...###...sss....',
        '..##....sss.....',
        '........ss......',
        '................',
        '................',
        '................',
    ], {'#': draw.SHARDS, 's': draw.SHARDS}, shaded='#s')


def currency_credits():
    """Orange diamond with a small sparkle."""
    return sprite([
        '................',
        '................',
        '.............w..',
        '............www.',
        '....########.w..',
        '...##f####f##...',
        '..##f######f##..',
        '..############..',
        '...##########...',
        '....########....',
        '.....######.....',
        '......####......',
        '.......##.......',
        '................',
        '................',
        '................',
    ], {'#': draw.CREDITS, 'f': '#FFC27A', 'w': draw.WHITE})


def nav_back():
    """Chunky left arrow, ember orange."""
    return sprite([
        '................',
        '................',
        '......##........',
        '.....###........',
        '....####........',
        '...#####........',
        '..############..',
        '.#############..',
        '.#############..',
        '..############..',
        '...#####........',
        '....####........',
        '.....###........',
        '......##........',
        '................',
        '................',
    ], {'#': draw.MOVE})


_PAGE_PREV = [
    '................',
    '...##########...',
    '...##########...',
    '...##########...',
    '...######oo##...',
    '...#####oo###...',
    '...####oo####...',
    '...###oo#####...',
    '...###oo#####...',
    '...####oo####...',
    '...#####oo###...',
    '...######oo##...',
    '...##########...',
    '...##########...',
    '...##########...',
    '................',
]


def _page(rows):
    return sprite(rows, {'o': draw.MOVE, '#': '#F2EDE4'}, shaded='o#')


def nav_prev():
    """Chevron < on a white page."""
    return _page(_PAGE_PREV)


def nav_next():
    """Chevron > on a white page (mirror of Previous)."""
    return _page([row[::-1] for row in _PAGE_PREV])


def nav_close():
    """Neutral grey-white X."""
    return sprite([
        '................',
        '................',
        '..###......###..',
        '..####....####..',
        '...####..####...',
        '....########....',
        '.....######.....',
        '......####......',
        '......####......',
        '.....######.....',
        '....########....',
        '...####..####...',
        '..####....####..',
        '..###......###..',
        '................',
        '................',
    ], {'#': '#DCD6D0'})


def nav_danger():
    """Red triangle with a white !"""
    return sprite([
        '................',
        '.......##.......',
        '......####......',
        '......####......',
        '.....##ww##.....',
        '.....##ww##.....',
        '....###ww###....',
        '....###ww###....',
        '...####ww####...',
        '...##########...',
        '..#####ww#####..',
        '..############..',
        '.##############.',
        '.##############.',
        '................',
        '................',
    ], {'#': draw.DANGER, 'w': draw.WHITE})


# ---------------------------------------------------------------------------------------------- states

def state_confirm():
    """Lime tile with a white check."""
    return sprite([
        '................',
        '................',
        '...##########...',
        '..############..',
        '..############..',
        '..########ww##..',
        '..#######ww###..',
        '..##ww##ww####..',
        '..###wwww#####..',
        '..####ww######..',
        '..############..',
        '..############..',
        '..############..',
        '...##########...',
        '................',
        '................',
    ], {'#': '#4CB82E', 'w': draw.WHITE})


def state_cancel():
    """Red tile with a white cross."""
    return sprite([
        '................',
        '................',
        '...##########...',
        '..############..',
        '..############..',
        '..##ww####ww##..',
        '..###ww##ww###..',
        '..####wwww####..',
        '..####wwww####..',
        '..###ww##ww###..',
        '..##ww####ww##..',
        '..############..',
        '..############..',
        '...##########...',
        '................',
        '................',
    ], {'#': '#D93A3A', 'w': draw.WHITE})


_SWITCH_ON = [
    '................',
    '................',
    '................',
    '................',
    '....########....',
    '...##########...',
    '..#######www##..',
    '..######wwwww#..',
    '..######wwwww#..',
    '..#######www##..',
    '...##########...',
    '....########....',
    '................',
    '................',
    '................',
    '................',
]


def state_on():
    """Switch, knob right, lime."""
    return sprite(_SWITCH_ON, {'#': draw.HOME, 'w': draw.WHITE})


def state_off():
    """Switch, knob left, grey."""
    return sprite([row[::-1] for row in _SWITCH_ON], {'#': '#8A8A92', 'w': '#D8D8DC'})


def state_locked():
    """Grey padlock."""
    return sprite([
        '................',
        '................',
        '.....ssssss.....',
        '....ss....ss....',
        '....ss....ss....',
        '....ss....ss....',
        '..############..',
        '..############..',
        '..#####kk#####..',
        '..#####kk#####..',
        '..#####kk#####..',
        '..############..',
        '..############..',
        '................',
        '................',
        '................',
    ], {'#': draw.LOCKED, 's': draw.GREY, 'k': draw.OUTLINE}, shaded='#s')


def state_loading():
    """Hourglass with ember sand."""
    return sprite([
        '................',
        '..ffffffffffff..',
        '...#oooooooo#...',
        '....#oooooo#....',
        '.....#oooo#.....',
        '......#oo#......',
        '.......oo.......',
        '.......oo.......',
        '......#oo#......',
        '.....#gggg#.....',
        '....#ggoogg#....',
        '...#goooooog#...',
        '..ffffffffffff..',
        '................',
        '................',
        '................',
    ], {'f': '#8B5A2B', 'o': draw.MOVE, '#': '#9FC3D9', 'g': '#DCEFFA'}, shaded='fo')


def state_undo():
    """Hook arrow back (ember)."""
    return sprite([
        '................',
        '................',
        '................',
        '....#...........',
        '...##...........',
        '..###########...',
        '.############...',
        '..############..',
        '...##......###..',
        '....#......###..',
        '...........###..',
        '...##########...',
        '...#########....',
        '................',
        '................',
        '................',
    ], {'#': draw.MOVE})


def state_empty():
    """Grey ring with a slash: nothing here yet."""
    def px(x, y):
        r = _dist(x, y, 7.5, 7.5)
        if 4.4 <= r <= 6.6:
            return '#'
        if r < 4.4 and abs((x - 7.5) + (y - 7.5)) <= 1.0:
            return '#'
        return None
    return sprite(_mask(px), {'#': draw.GREY})


def nav_search():
    """Magnifier: blue lens, grey handle (auction house only, planned)."""
    def px(x, y):
        r = _dist(x, y, 6.0, 6.0)
        if 3.4 <= r <= 5.2:
            return 'r'
        if r < 3.4:
            return 'g'
        if x >= 9 and y >= 9 and x <= 13 and y <= 13 and abs(x - y) <= 1:
            return 'h'
        return None
    return sprite(_mask(px), {'r': '#C9C9D1', 'g': draw.DEPTHS, 'h': '#7A5232'}, shaded='rh')


def nav_help():
    """Light book with a ?"""
    rows = [
        '................',
        '................',
        '...sssssssss....',
        '...s########p...',
        '...s########p...',
        '...s########p...',
        '...s########p...',
        '...s########p...',
        '...s########p...',
        '...s########p...',
        '...s########p...',
        '...s########p...',
        '...s########p...',
        '...sssssssss....',
        '................',
        '................',
    ]
    c = sprite(rows, {'#': '#E8E2DA', 's': '#9C8A78', 'p': '#FFFFFF'}, shaded='#s')
    q = draw.Canvas(3, 5)
    q.text(draw.font5x7.FONT_3X5, '?', 0, 0, draw.rgba('#4A4A55'))
    c.blit(q, 5, 3, scale=2)
    return c


def ui_info():
    """Blue disc with a white i."""
    def px(x, y):
        if _dist(x, y, 7.5, 7.5) > 6.6:
            return None
        if x in (7, 8) and (y in (3, 4) or 6 <= y <= 11):
            return 'w'
        return '#'
    return sprite(_mask(px), {'#': draw.DEPTHS, 'w': draw.WHITE})


HAND_DRAWN = {
    'menu': menu,
    'currency/money': currency_money,
    'currency/crystals': currency_crystals,
    'currency/shards': currency_shards,
    'currency/credits': currency_credits,
    'nav/back': nav_back,
    'nav/prev': nav_prev,
    'nav/next': nav_next,
    'nav/close': nav_close,
    'nav/danger': nav_danger,
    'state/confirm': state_confirm,
    'state/cancel': state_cancel,
    'state/on': state_on,
    'state/off': state_off,
    'state/locked': state_locked,
    'state/loading': state_loading,
    'state/undo': state_undo,
    'state/empty': state_empty,
    'nav/search': nav_search,
    'nav/help': nav_help,
    'ui/info': ui_info,
}


def render(icon):
    """The placeholder canvas of one catalogue entry."""
    fn = HAND_DRAWN.get(icon['key'])
    if fn is not None:
        return draw.opaque_or_clear(fn())
    colour = draw.MEANING_COLOUR[icon['meaning']]
    return draw.opaque_or_clear(draw.badge(colour, icon['name'][:1]))
