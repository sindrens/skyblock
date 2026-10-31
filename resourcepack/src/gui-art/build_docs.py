# Writes docs/plans/RESOURCE_PACK_GUI.md tables from ids_gui.json + coverage.json + the exported PNGs.
# usage (repo root): python3 resourcepack/src/gui-art/build_docs.py
import json, os, struct
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '../../..'))
ids = json.load(open(os.path.join(HERE, 'ids_gui.json'))); cov = json.load(open(os.path.join(HERE, 'coverage.json')))
TEX = os.path.join(ROOT, 'resourcepack/java/assets/phoenix/textures')
def png_w(p):
    with open(p, 'rb') as f: f.read(16); return struct.unpack('>II', f.read(8))
def table(head, rows): return '\n'.join(['| ' + ' | '.join(head) + ' |', '|' + '---|' * len(head)] + ['| ' + ' | '.join(str(c) for c in r) + ' |' for r in rows])
by_bg = {}
for c in cov: by_bg.setdefault(c['bg'], []).append(c)

T = {}
T['BG'] = table(['File (`gui/menu/…`)', 'Glyph', 'Rows', 'Replaces brief', 'Used by (menus)', 'What'],
    [[f"`{f}.png`", f"U+{cp}", r, rep, len(by_bg.get(f, [])) or 'spare', what] for f, cp, r, rep, what in ids['backgrounds']])
T['BUTTONS'] = table(['Model `phoenix:menu/…`', 'What it shows', 'Where', 'Carrier today'], [[f"`{i}`", w, s, m] for i, w, s, m in ids['buttons']])
T['FEATURES'] = table(['Model `phoenix:menu/icon/…`', 'Feature', 'Where', 'Carrier today'], [[f"`{i}`", w, s, m] for i, w, s, m in ids['features']])
T['TABS'] = table(['Model `phoenix:menu/tab/…`', 'Tab', 'Shop slot'], [[f"`{i}`", n, s] for i, n, s in ids['tabs']])
T['CURRENCIES'] = table(['Model `phoenix:currency/…`', 'Currency', 'Carrier today'], [[f"`{i}`", n, m] for i, n, m in ids['currencies']])
T['GLYPHS'] = table(['File (`font/icons/…`)', 'Char', 'Width', 'What'], [[f"`{i}.png`", f"U+{cp}", f"{png_w(os.path.join(TEX, 'font/icons', i + '.png'))[0]} px", w] for i, cp, w in ids['glyphs']])
mods = sorted({c['module'] for c in cov})
rows = []
for m in mods:
    for c in sorted([c for c in cov if c['module'] == m], key=lambda c: c['class']):
        rows.append([m, f"`{c['class']}`", c['rows'], f"`{c['bg']}`", c['note']])
T['COVERAGE'] = table(['Module', 'Menu class', 'Rows', 'Background', 'Note'], rows)
T['NCOV'] = str(len(cov)); T['NBGUSED'] = str(len(by_bg)); T['NICONS'] = str(len(ids['buttons']) + len(ids['features']) + len(ids['tabs']) + len(ids['currencies']))
T['NBUTTONS'] = str(len(ids['buttons'])); T['NFEATURES'] = str(len(ids['features']))
T['NGLYPHS'] = str(len(ids['glyphs'])); T['NBG'] = str(len(ids['backgrounds']))
total = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(TEX) for f in fs if f.endswith('.png'))
nfiles = sum(1 for d, _, fs in os.walk(TEX) for f in fs if f.endswith('.png'))
T['SIZE'] = f"{nfiles} PNGs, {total // 1024} KB"
tpl = open(os.path.join(HERE, 'RESOURCE_PACK_GUI.template.md')).read()
for k, v in T.items(): tpl = tpl.replace('{{' + k + '}}', v)
assert '{{' not in tpl, tpl[tpl.index('{{'):tpl.index('{{') + 40]
out = os.path.join(ROOT, 'docs/plans/RESOURCE_PACK_GUI.md'); os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, 'w').write(tpl); print('wrote', out, len(tpl.splitlines()), 'lines')
