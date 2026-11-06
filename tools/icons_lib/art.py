"""Icon art: the pixel sprites live in sprites.py; this module only picks the right one for a catalogue key + style.

The generator never overwrites an existing PNG unless --force-art / --only (so owner-drawn art survives).
"""
from . import draw, sprites


def render(key, style=None):
    """The canvas of one custom icon in one style (``style`` None = the icon has a single look)."""
    if key in sprites.SINGLE and style in (None, 'default'):
        c = sprites.SINGLE[key]().canvas()
    else:
        variants = sprites.VARIANTS.get(key)
        if not variants or style not in variants:
            raise KeyError('no sprite for %s style %s' % (key, style))
        c = variants[style]().canvas()
    return draw.opaque_or_clear(c)
