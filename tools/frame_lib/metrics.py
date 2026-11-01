"""Font JSON and frame-metrics.json (GUI-FONT 4.3 and 5): every number the Java side needs, measured from the final
PNGs, so the Java code never hard-codes a pixel."""
import json


def cp(hex_code):
    return chr(int(hex_code, 16))


def space_codepoints(cat):
    s = cat['space']
    neg0, pos0 = int(s['neg_first'], 16), int(s['pos_first'], 16)
    neg = [{'cp': '%04X' % (neg0 + i), 'step': -step} for i, step in enumerate(s['steps'])]
    pos = [{'cp': '%04X' % (pos0 + i), 'step': step} for i, step in enumerate(s['steps'])]
    return neg, pos


def visible_width(img):
    right = -1
    for x in range(img.w):
        if any(img.px[y][x][3] for y in range(img.h)):
            right = x
    return right + 1


def frame_font(cat, panel_heights, plaque_heights):
    neg, pos = space_codepoints(cat)
    advances = {}
    for e in neg + pos:
        advances[cp(e['cp'])] = e['step']
    providers = [{'type': 'space', 'advances': advances}]
    for key, p in cat['panels'].items():
        providers.append({'type': 'bitmap', 'file': 'phoenix:font/frame/panel_%s.png' % key,
                          'ascent': cat['title_baseline'], 'height': panel_heights[key], 'chars': [cp(p['cp'])]})
    for key, p in cat['plaques'].items():
        h = plaque_heights[key]
        providers.append({'type': 'bitmap', 'file': 'phoenix:font/frame/plaque_%s.png' % key,
                          'ascent': cat['title_baseline'] + cat['plaque_extend'], 'height': h, 'chars': [cp(p['cp'])]})
    cell = cat['cell']
    for r, code in enumerate(cell['cp']):
        providers.append({'type': 'bitmap', 'file': 'phoenix:font/frame/cell.png', 'ascent': cell['first_ascent'] - cell['step'] * r,
                          'height': cell['size'], 'chars': [cp(code)]})
    logo = cat['logo']
    for kind in ('wide', 'small'):
        providers.append({'type': 'bitmap', 'file': 'phoenix:font/frame/logo.png', 'ascent': logo[kind]['ascent'],
                          'height': logo['height'], 'chars': [cp(logo[kind]['cp'])]})
    return {'providers': providers}


def title_font(cat):
    L = cat['letters']
    chars, cols = L['chars'], L['cols']
    rows = []
    lower = []
    padded = chars + '\u0000' * (-len(chars) % cols)
    for i in range(0, len(padded), cols):
        row = padded[i:i + cols]
        rows.append(row)
        lower.append(''.join(c.lower() if 'A' <= c <= 'Z' else '\u0000' for c in row))
    return {'providers': [
        {'type': 'space', 'advances': {' ': L['space']}},
        {'type': 'bitmap', 'file': 'phoenix:font/title/letters.png', 'ascent': L['ascent'], 'height': L['cell_h'], 'chars': rows},
        {'type': 'bitmap', 'file': 'phoenix:font/title/letters.png', 'ascent': L['ascent'], 'height': L['cell_h'], 'chars': lower},
    ]}


def metrics(cat, panels, plaques, cell_img, logo_img, letter_advances):
    pad = cat['advance_pad']
    neg, pos = space_codepoints(cat)
    out = {
        'version': cat['version'],
        'title_origin_x': cat['title_origin_x'],
        'title_baseline': cat['title_baseline'],
        'plaque_extend': cat['plaque_extend'],
        'window_width': cat['window_width'],
        'panels': {}, 'plaques': {}, 'cell': {}, 'logo': {}, 'space': {'neg': neg, 'pos': pos},
        'letters': {'advance': letter_advances, 'space': cat['letters']['space']},
    }
    for key, p in cat['panels'].items():
        img = panels[key]
        out['panels'][key] = {'rows': p['rows'], 'width': img.w, 'height': img.h, 'advance': visible_width(img) + pad, 'cp': p['cp']}
    inner_pad = cat['plaque_padding']
    for key, p in cat['plaques'].items():
        img = plaques[key]
        out['plaques'][key] = {'width': img.w, 'advance': visible_width(img) + pad, 'inner': img.w - 2 * inner_pad, 'cp': p['cp']}
    c = cat['cell']
    out['cell'] = {'size': c['size'], 'x0': c['x0'], 'y0': c['y0'], 'step': c['step'], 'advance': visible_width(cell_img) + pad,
                   'cp': list(c['cp'])}
    lg = cat['logo']
    for kind in ('wide', 'small'):
        out['logo'][kind] = {'cp': lg[kind]['cp'], 'advance': visible_width(logo_img) + pad, 'ascent': lg[kind]['ascent'],
                             'width': logo_img.w, 'height': logo_img.h}
    return out


def dumps(obj):
    """Deterministic JSON: 1-space indent, ASCII escapes (private-use code points as \\uE300), trailing newline."""
    return json.dumps(obj, indent=1, ensure_ascii=True) + '\n'
