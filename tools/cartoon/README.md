# Cartoon icon renderer (icons v4, owner 2 Nov 2026)

`python3 tools/cartoon/render.py` draws every menu icon as a chunky cartoon picture (thick dark outline, flat colours,
a highlight and a shade) at 256 px and writes 64x64 PNGs into the pack (`resourcepack/pack/.../icon/`), plus a contact
sheet `resourcepack/preview/cartoon-sheet.png`. The art is code: change a drawing in `render.py`, run it again.

`Poppins-Bold.ttf` (SIL Open Font License 1.1, Indian Type Foundry) draws the readable symbols ($, ?, !, i).
