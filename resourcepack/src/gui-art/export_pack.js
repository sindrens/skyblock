// Build the GUI part of the resource pack: backgrounds, button/feature/tab/currency icons, font glyphs,
// plus preview sheets and in-context screen mock-ups.
// usage (from the repo root): python3 resourcepack/src/gui-art/catalog.py resourcepack/src/gui-art/ids_gui.json && node resourcepack/src/gui-art/export_pack.js
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const OUT = process.argv[2] || path.resolve(__dirname, '../../..'); // repo root
  const RP = OUT + '/resourcepack'; const TEX = RP + '/java/assets/phoenix/textures';
  const ids = JSON.parse(fs.readFileSync(__dirname + '/ids_gui.json', 'utf8'));
  const artFiles = ['core.js', ...fs.readdirSync(__dirname).filter(f => /^set_.*\.js$/.test(f)).sort()].map(f => path.join(__dirname, f));
  const js = [...artFiles, path.join(__dirname, 'bg_engine.js'), path.join(__dirname, 'bg_defs.js')].map(f => fs.readFileSync(f, 'utf8')).join('\n;\n');
  const b = await chromium.launch(); const p = await b.newPage(); const logs = [];
  p.on('console', m => logs.push(m.text())); p.on('pageerror', e => logs.push('ERR ' + e));
  await p.setContent(`<!doctype html><body><script>${js}</script></body>`);
  const res = await p.evaluate((ids) => {
    const files = {}; const sheets = {};
    const cv = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; const x = c.getContext('2d'); x.imageSmoothingEnabled = false; return [c, x]; };
    const toURL = c => c.toDataURL('image/png');
    const pxCanvas = g => { const [c, x] = cv(g.w, g.h); g.px.forEach((col, i) => { if (col) { x.fillStyle = col; x.fillRect(i % g.w, Math.floor(i / g.w), 1, 1); } }); return c; };
    const blit = (x, g, ox, oy, s) => g.px.forEach((col, i) => { if (col) { x.fillStyle = col; x.fillRect(ox + (i % g.w) * s, oy + Math.floor(i / g.w) * s, s, s); } });

    // ---- textures ----
    const icon = {}; // model path -> grid
    ids.buttons.forEach(([id]) => icon['menu/' + id] = ART.icon('menu/' + id));
    ids.features.forEach(([id]) => icon['menu/icon/' + id] = ART.icon('menu/icon/' + id));
    ids.tabs.forEach(([id]) => icon['menu/tab/' + id] = ART.icon('menu/tab/' + id));
    ids.currencies.forEach(([id]) => icon['currency/' + id] = ART.icon('currency/' + id));
    Object.entries(icon).forEach(([k, g]) => files[`item/${k}.png`] = toURL(pxCanvas(g)));
    const glyph = {}; ids.glyphs.forEach(([id]) => { glyph[id] = ART.icon('glyph/' + id); files[`font/icons/${id}.png`] = toURL(pxCanvas(glyph[id])); });
    const bgs = {};
    ids.backgrounds.forEach(([file]) => { const d = PACK.bg.find(x => x.file === file); if (!d) { console.log('missing bg def', file); return; }
      const c = document.createElement('canvas'); GUI.draw(c, { ...d, bg: true, title: [], slots: {} }, 'night', 1); bgs[file] = c; files[`gui/menu/${file}.png`] = toURL(c); });

    // ---- preview sheets ----
    const label = (x, t, X, Y, col = '#E8DCC0', size = 11) => { x.fillStyle = col; x.font = `${size}px monospace`; x.fillText(t, X, Y); };
    const well = (x, X, Y, s, fill, tl, br) => { x.fillStyle = fill; x.fillRect(X, Y, 18 * s, 18 * s); x.fillStyle = tl; x.fillRect(X, Y, 17 * s, s); x.fillRect(X, Y, s, 17 * s); x.fillStyle = br; x.fillRect(X + s, Y + 17 * s, 17 * s, s); x.fillRect(X + 17 * s, Y + s, s, 17 * s); };
    const iconSheet = (title, keys, cols = 6) => {
      const cw = 200, ch = 112, rows = Math.ceil(keys.length / cols); const [c, x] = cv(cols * cw, rows * ch + 40);
      x.fillStyle = '#1A1413'; x.fillRect(0, 0, c.width, c.height); label(x, title, 10, 26, '#FFD21A', 18);
      keys.forEach((k, i) => { const X = (i % cols) * cw + 8, Y = Math.floor(i / cols) * ch + 44; const g = icon[k];
        well(x, X, Y, 4, '#140F0F', '#070404', '#45342F'); blit(x, g, X + 4, Y + 4, 4);
        well(x, X + 80, Y, 2, '#8B8B8B', '#373737', '#FFFFFF'); blit(x, g, X + 82, Y + 2, 2);
        blit(x, g, X + 124, Y + 10, 1);
        label(x, k.replace(/^menu\//, ''), X, Y + 88); });
      return toURL(c); };
    sheets.buttons = iconSheet('Menu buttons  phoenix:menu/<id>   (4x on the Phoenix Night well · 2x on the Bedrock/no-pack well · 1x)', ids.buttons.map(b => 'menu/' + b[0]));
    sheets.features = iconSheet('Feature icons  phoenix:menu/icon/<id>   (one icon per feature, the same everywhere)', ids.features.map(b => 'menu/icon/' + b[0]));
    sheets.tabs_currencies = iconSheet('Shop tabs  phoenix:menu/tab/<id>   ·   Currencies  phoenix:currency/<id>', [...ids.tabs.map(t => 'menu/tab/' + t[0]), ...ids.currencies.map(t => 'currency/' + t[0])]);
    { const keys = ids.glyphs; const cw = 300, ch = 70, cols = 3, rows = Math.ceil(keys.length / cols); const [c, x] = cv(cols * cw, rows * ch + 40);
      x.fillStyle = '#1A1413'; x.fillRect(0, 0, c.width, c.height); label(x, 'Font glyphs  font phoenix:icons  (9 px tall; 4x and 2x on a chat background)', 10, 26, '#FFD21A', 16);
      keys.forEach(([id, cp], i) => { const g = glyph[id]; const X = (i % cols) * cw + 10, Y = Math.floor(i / cols) * ch + 46;
        x.fillStyle = 'rgba(0,0,0,.5)'; x.fillRect(X - 4, Y - 4, g.w * 4 + 8, 44); blit(x, g, X, Y, 4); blit(x, g, X + g.w * 4 + 12, Y + 9, 2);
        label(x, `${id}  U+${cp}  ${g.w}px`, X, Y + 52); });
      sheets.glyphs = toURL(c); }
    { const list = ids.backgrounds; const cols = 4, cw = 176 * 2 + 20, ch = 222 * 2 + 40, rows = Math.ceil(list.length / cols); const [c, x] = cv(cols * cw, rows * ch + 40);
      x.fillStyle = '#0C0C0E'; x.fillRect(0, 0, c.width, c.height); label(x, 'Menu backgrounds  font phoenix:menu  (2x; the client draws the slots and items on top)', 10, 26, '#FFD21A', 18);
      list.forEach(([file, cp], i) => { const X = (i % cols) * cw + 10, Y = Math.floor(i / cols) * ch + 44; const bc = bgs[file]; if (!bc) return; x.drawImage(bc, X, Y, bc.width * 2, bc.height * 2); label(x, `${file}  U+${cp}`, X, Y + bc.height * 2 + 18, '#E8DCC0', 13); });
      sheets.backgrounds = toURL(c); }

    // ---- in-context mock-ups ----
    const F3 = ART.FONT3;
    const tinyText = (x, t, X, Y, s, col) => { let cx = X; for (const ch of t) { const m = F3[ch] || F3['?']; m.forEach((r, j) => [...r].forEach((b, i) => { if (b === '1') { x.fillStyle = col; x.fillRect(cx + i * s, Y + j * s, s, s); } })); cx += (m[0].length + 1) * s; } };
    const stand = (x, X, Y, s, code) => { x.fillStyle = '#5A5A5A'; x.fillRect(X + s, Y + s, 14 * s, 14 * s); x.fillStyle = '#9A9A9A'; x.fillRect(X + 2 * s, Y + 2 * s, 12 * s, 12 * s); x.fillStyle = '#7A7A7A'; x.fillRect(X + 2 * s, Y + 12 * s, 12 * s, 2 * s); tinyText(x, code, X + 4 * s, Y + 5 * s, s, '#FFFFFF'); };
    const count = (x, X, Y, s, n) => { const t = String(n); const w = t.length * 4 * s; tinyText(x, t, X + 17 * s - w + s, Y + 11 * s, s, '#3F3F3F'); tinyText(x, t, X + 17 * s - w, Y + 10 * s, s, '#FFFFFF'); };
    const glint = (x, X, Y, s) => { x.fillStyle = 'rgba(190,120,255,.35)'; x.fillRect(X + s, Y + s, 16 * s, 16 * s); };
    const screen = (bgFile, title, slots, s = 3) => {
      const bc = bgs[bgFile]; const [c, x] = cv(bc.width * s, bc.height * s); x.drawImage(bc, 0, 0, c.width, c.height);
      x.fillStyle = '#E8DCC0'; x.font = `bold ${8 * s}px monospace`; x.textBaseline = 'top'; x.fillText(title, 8 * s, 5 * s);
      const rows = (bc.height - 114) / 18; const iy = rows * 18 + 30;
      x.fillStyle = '#404040'; x.font = `${7 * s}px monospace`; x.fillText('Inventory', 9 * s, (iy - 11) * s);
      Object.entries(slots).forEach(([slot, v]) => { slot = +slot; const X = (7 + (slot % 9) * 18) * s, Y = (17 + Math.floor(slot / 9) * 18) * s;
        const items = Array.isArray(v) ? v : [v];
        items.forEach(it => { if (typeof it === 'number') count(x, X, Y, s, it); else if (it === '*glint') glint(x, X, Y, s); else if (it.startsWith('#')) stand(x, X, Y, s, it.slice(1)); else blit(x, icon[it], X + s, Y + s, s); }); });
      return c; };
    const bar6 = { 45: 'menu/back', 48: 'menu/info', 49: 'menu/close', 50: 'menu/main_menu' };
    const tabs = Object.fromEntries(ids.tabs.map(([id], i) => [i, 'menu/tab/' + id])); tabs[0] = ['menu/tab/essentials', '*glint'];
    const shopGoods = ['CT', 'FU', 'BF', 'SM', 'CH', 'BA', 'CO', 'SC', 'GR', 'AN', 'ET', 'BS', 'BR', 'CA', 'BU', 'WB', 'LB', 'FS', 'SH', 'FR', 'TO', 'LA', 'SF', 'BE', 'BD', 'BP'];
    const goods = {}; [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41].forEach((s, i) => goods[s] = '#' + shopGoods[i]);
    const mocks = [
      ['hub', 'Menu', { 4: '#ME', 10: 'menu/icon/home', 12: 'menu/icon/island', 14: 'menu/icon/depths', 16: 'menu/icon/travel', 19: 'menu/icon/sell', 21: 'menu/icon/shop', 23: 'menu/icon/bank', 25: 'menu/icon/auction',
        28: 'menu/icon/forge', 30: 'menu/icon/skills', 32: 'menu/icon/enchanter', 34: 'menu/icon/wardrobe', 39: ['menu/icon/rewards_ready', 3], 41: 'menu/icon/top', 48: 'menu/info', 49: 'menu/close', 50: 'menu/help' }],
      ['frame_core_3', 'You', { 4: '#ME', 10: 'menu/icon/profiles', 11: 'menu/icon/titles', 12: 'menu/icon/achievements', 13: 'menu/icon/settings', 14: 'menu/icon/store', 15: 'menu/icon/rules', 18: 'menu/back', 22: 'menu/close' }],
      ['frame_core_3', 'Travel', { 10: 'menu/icon/spawn', 11: 'menu/icon/home', 12: 'menu/icon/depths', 13: 'menu/icon/crates', 14: 'menu/icon/shop', 15: 'menu/icon/forge', 16: 'menu/icon/visit', 18: 'menu/back', 22: 'menu/close' }],
      ['island', 'Island', { 4: 'menu/icon/island', 10: 'menu/icon/home', 11: 'menu/icon/members', 12: 'menu/icon/permissions', 13: 'menu/icon/warps', 14: 'menu/icon/visitors', 15: 'menu/icon/island_settings', 16: 'menu/icon/invites',
        19: 'menu/icon/notes', 20: 'menu/icon/island_chat', 21: 'menu/icon/chat_colors', 22: 'menu/icon/visit', 23: 'menu/icon/island_stats', 24: 'menu/icon/island_bank', 25: 'menu/icon/upgrades', 40: 'menu/icon/disband', ...bar6 }],
      ['shop', 'Shop » Essentials', { ...tabs, ...goods, ...bar6, 46: 'currency/money', 47: ['menu/undo', 12], 51: 'menu/sell' }],
      ['shop_buy', 'Shop » Torch', { ...tabs, 13: ['#TO', 64], 20: 'menu/buy_1', 22: 'menu/buy_16', 24: 'menu/buy_64', 30: 'menu/fill', 32: 'menu/type_amount', ...bar6, 46: 'currency/money', 47: 'menu/undo', 51: 'menu/sell' }],
      ['number_picker', 'Bank » Withdraw', { 19: 'menu/minus_big', 20: 'menu/minus_mid', 21: 'menu/minus_small', 22: 'currency/money', 23: 'menu/plus_small', 24: 'menu/plus_mid', 25: 'menu/plus_big',
        30: 'menu/set_min', 31: 'menu/type_amount', 32: 'menu/set_max', 39: 'menu/confirm_side', 40: 'menu/confirm', 41: 'menu/confirm_side', ...bar6 }],
      ['confirm', 'Disband island?', { 10: 'menu/confirm_side', 11: 'menu/confirm', 12: 'menu/confirm_side', 13: 'menu/icon/disband', 14: 'menu/cancel_side', 15: 'menu/cancel', 16: 'menu/cancel_side', 18: 'menu/back', 22: 'menu/close' }],
      ['top', 'Top » Solo islands', { 11: ['menu/icon/island', '*glint'], 12: 'menu/icon/members', 13: 'menu/icon/calendar', 14: 'currency/money', 15: 'menu/icon/skills', 21: '#P2', 22: '#P1', 23: '#P3',
        28: '#P4', 29: '#P5', 30: '#P6', 31: '#P7', 32: '#P8', 33: '#P9', 34: '#10', ...bar6 }],
      ['sell_chest', 'Sell', { 0: ['#WH', 64], 1: ['#WH', 64], 2: ['#CA', 32], 3: ['#PO', 17], 9: ['#CO', 64], 10: ['#IR', 9], 46: 'menu/add_inventory', 47: 'currency/money', 52: 'menu/sell', 53: 'menu/sell', 45: 'menu/back', 48: 'menu/info', 49: 'menu/close', 50: 'menu/main_menu' }],
      ['frame_island_6', 'Island » Members', { 10: '#AL', 11: '#BO', 12: '#CY', 13: 'menu/plus_small', 45: 'menu/back', 48: 'menu/info', 49: 'menu/close', 50: 'menu/main_menu' }],
      ['frame_staff_6', 'Staff » Reports', { 10: '#R1', 11: '#R2', 12: '#R3', 45: 'menu/back', 48: 'menu/info', 49: 'menu/close', 50: 'menu/main_menu', 53: 'menu/next' }],
    ];
    const shots = mocks.map(([bg, t, sl]) => [bg + ' — ' + t, screen(bg, t, sl)]);
    { const cols = 3, cw = 176 * 3 + 24, ch = 222 * 3 + 44; const [c, x] = cv(cols * cw, Math.ceil(shots.length / cols) * ch + 40);
      x.fillStyle = '#0C0C0E'; x.fillRect(0, 0, c.width, c.height); label(x, 'In context (3x): backgrounds + icons. Grey tiles = vanilla items (shown with Minecraft’s own textures in game)', 10, 26, '#FFD21A', 18);
      shots.forEach(([t, sc], i) => { const X = (i % cols) * cw + 12, Y = Math.floor(i / cols) * ch + 44; x.drawImage(sc, X, Y); label(x, t, X, Y + sc.height + 20, '#E8DCC0', 14); });
      sheets.screens = toURL(c); }
    return { files, sheets };
  }, ids);
  const save = (f, url) => { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, Buffer.from(url.split(',')[1], 'base64')); };
  Object.entries(res.files).forEach(([k, u]) => save(`${TEX}/${k}`, u));
  Object.entries(res.sheets).forEach(([k, u]) => save(`${RP}/src/preview/${k}.png`, u));
  console.log('textures', Object.keys(res.files).length, 'sheets', Object.keys(res.sheets).length, logs.slice(0, 10));
  await b.close();
})();
