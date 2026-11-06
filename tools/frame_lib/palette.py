"""Colours of the frame layer (GUI-FONT 4.2). One panel colour for every menu (V3-Q9). Our own look: warm parchment in a
brand-red frame with a gold trim and a dark-ember outline (not MC Hub's grey)."""
from icons_lib import draw

OUTLINE = draw.rgba('#4A1218')      # dark ember outline
RED = draw.rgba('#D1202F')          # brand red frame
RED_LIGHT = draw.rgba('#E8434F')    # top/left light of the red band
RED_DARK = draw.rgba('#9E1622')     # bottom/right shade of the red band
GOLD = draw.rgba('#FFC21A')         # flame gold trim
GOLD_DARK = draw.rgba('#D9930B')
PARCHMENT = draw.rgba('#D9D2C0')    # panel
BEVEL = draw.rgba('#F0E9D6')        # lighter bevel (label strip, inner light edge)
PARCHMENT_SHADE = draw.rgba('#C7BEA9')
CELL = draw.rgba('#B8B09C')         # player inventory cell
CELL_SHADOW = draw.rgba('#8F8775')
CELL_LIGHT = draw.rgba('#E6DFCC')
TILE = draw.rgba('#F3EEDF')         # soft tile behind an item
TILE_EDGE = draw.rgba('#B3AB97')
TILE_LIGHT = draw.rgba('#FFFBEF')
LETTER_TOP = draw.rgba('#FFF3C4')   # lettering gradient
LETTER_HIGH = draw.rgba('#FFE47A')
LETTER_MID = draw.rgba('#FFD21A')
LETTER_LOW = draw.rgba('#FFB30D')
LETTER_BOTTOM = draw.rgba('#F59A00')
WHITE = draw.rgba('#FFFFFF')
FLAME_RED = draw.rgba('#FF3D2E')
FLAME_ORANGE = draw.rgba('#FF9A3C')
CLEAR = draw.CLEAR
