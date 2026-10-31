/* Crate chests v2 for /warp crates: a custom 3D chest per crate (owner round 2: "the crate chest itself is custom";
   his pictures were inspiration only). Same base build for all (frame posts, belt, latch), then each tier adds
   one clear thing you can see from far away. Colours = the crate's rarity (crates.yml).
   Also draws the matching crate keys (16x16 item icons).
   Writes: models/item/crate/block/<id>.json, textures/item/crate/block/<id>.png (128x128),
           textures/item/crate/key/<id>.png, src/preview/crates.png, src/preview/crate_keys.png
   usage (repo root): node resourcepack/src/crate-art/crates.js   (needs playwright; three.js from npm in /home/claude/three or THREE_JS)
   Original art: nothing copied from Mojang or from the reference pictures. */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const ROOT = process.argv[2] || path.resolve(__dirname, '../../..');
const THREE_JS = process.env.THREE_JS || '/home/claude/three/node_modules/three/build/three.min.js';
const TS = 128; // texture size

const R = { // rarity ramps: light, base, dark
  white: ['#FFFFFF', '#D7DBE3', '#8C919E'], green: ['#C6FFB8', '#4FD25C', '#1E7A33'], blue: ['#C4E6FF', '#3DA5FF', '#1E5CB0'],
  purple: ['#EBD1FF', '#B36BFF', '#6A36B0'], gold: ['#FFF3A3', '#FFD21A', '#B87A0A'], pink: ['#FFD0F4', '#FF4FD8', '#A0208A'], red: ['#FF9A9A', '#FF2D3F', '#9C1B2B'],
};
const CRATES = [
  { id: 'ember', name: 'Ember', rarity: 'common', wood: ['#E2A86C', '#B5733E', '#6F4324'], metal: ['#C9CCD4', '#8E929C', '#4E525C'], trim: R.white, feet: false, arch: false, extra: [] },
  { id: 'cinder', name: 'Cinder', rarity: 'uncommon', wood: ['#9CC060', '#6E9A3A', '#3E5E22'], metal: ['#C9CCD4', '#8E929C', '#4E525C'], trim: R.green, feet: false, arch: false, extra: ['vines', 'gem'] },
  { id: 'blaze', name: 'Blaze', rarity: 'rare', wood: ['#7FC4E8', '#3C86B8', '#1F4C70'], metal: ['#C9CCD4', '#8E929C', '#4E525C'], trim: R.gold, gem: R.blue, feet: true, arch: true, extra: ['gem'] },
  { id: 'inferno', name: 'Inferno', rarity: 'epic', wood: ['#B07BE0', '#7E46B8', '#45246E'], metal: ['#A8ACB6', '#6E727C', '#3A3E48'], trim: R.purple, glow: '#E2B8FF', feet: false, arch: false, extra: ['handles', 'gem'] },
  { id: 'phoenix', name: 'Phoenix', rarity: 'legendary', wood: ['#F0606A', '#C62A35', '#7E1420'], metal: R.gold, trim: R.gold, glow: '#FFE27A', feet: true, arch: true, extra: ['wings', 'flame'] },
  { id: 'eclipse', name: 'Eclipse', rarity: 'mythic', wood: ['#46385A', '#251C33', '#120D1A'], metal: ['#8E86A0', '#55506A', '#2C2838'], trim: R.pink, glow: '#FF9BE9', feet: false, arch: true, stars: true, extra: ['ring'] },
  { id: 'conqueror', name: 'Conqueror', rarity: 'special', wood: ['#5E6676', '#3A404C', '#1E222A'], metal: ['#8C93A2', '#565D6C', '#2C303A'], trim: R.red, glow: '#FF6B6B', feet: true, arch: false, extra: ['spikes', 'skull'] },
];

// ---------------- geometry ----------------
function build(c) {
  const E = []; const b = c.feet ? 1 : 0;
  const box = (name, from, to, mat, opt = {}) => E.push({ name, from, to, mat, skip: opt.skip || [], rot: opt.rot, cut: opt.cut });
  if (c.feet) for (const [x, z] of [[1, 2], [13, 2], [1, 12], [13, 12]]) box('foot', [x, 0, z], [x + 2, 1, z + 2], 'gold', { skip: ['up'] });
  box('body', [2, b, 3], [14, b + 8, 13], 'wood', { skip: ['up'] });
  box('lid', [2, b + 8, 3], [14, b + 12, 13], 'lid', { skip: ['down'] });
  if (c.arch) box('arch', [3, b + 12, 4], [13, b + 13, 12], 'lidtop', { skip: ['down'] });
  for (const [x, z] of [[1, 2], [13, 2], [1, 12], [13, 12]]) { box('post', [x, b, z], [x + 2, b + 8, z + 2], 'metal'); box('postlid', [x, b + 8, z], [x + 2, b + 12, z + 2], 'metal'); }
  box('belt', [3, b + 7, 2], [13, b + 9, 3], 'trim'); box('belt', [3, b + 7, 13], [13, b + 9, 14], 'trim');
  box('belt', [1, b + 7, 4], [2, b + 9, 12], 'trim'); box('belt', [14, b + 7, 4], [15, b + 9, 12], 'trim');
  box('latch', [7, b + 5, 14], [9, b + 10, 15], c.extra.includes('skull') ? 'skull' : 'latch');
  // lid frame: metal bars along the top edges, like a banded chest
  box('lidbar', [3, b + 11, 2], [13, b + 12, 3], 'metal'); box('lidbar', [3, b + 11, 13], [13, b + 12, 14], 'metal');
  box('lidbar', [1, b + 11, 4], [2, b + 12, 12], 'metal'); box('lidbar', [14, b + 11, 4], [15, b + 12, 12], 'metal');
  const top = b + (c.arch ? 13 : 12);
  if (c.extra.includes('gem')) { box('gembase', [6, top, 6], [10, top + 1, 10], 'gold'); box('gem', [6.5, top + 1, 6.5], [9.5, top + 3, 9.5], 'gem', { rot: { origin: [8, top + 2, 8], axis: 'y', angle: 45 } }); }
  if (c.extra.includes('vines')) { box('vine', [2, b + 4, 13.2], [14, b + 11, 13.2], 'vine', { cut: true }); box('vine', [1.9, b + 4, 3], [1.9, b + 11, 13], 'vine', { cut: true }); box('vine', [14.1, b + 4, 3], [14.1, b + 11, 13], 'vine', { cut: true }); }
  if (c.extra.includes('handles')) { box('handle', [0, b + 4, 7], [1, b + 6, 9], 'metal'); box('handle', [15, b + 4, 7], [16, b + 6, 9], 'metal'); }
  if (c.extra.includes('wings')) {
    box('wing', [0.5, b + 3, 4], [0.5, b + 13, 12], 'wing', { cut: true, rot: { origin: [1, b + 6, 8], axis: 'z', angle: 22.5 } });
    box('wing', [15.5, b + 3, 4], [15.5, b + 13, 12], 'wing', { cut: true, rot: { origin: [15, b + 6, 8], axis: 'z', angle: -22.5 } });
  }
  if (c.extra.includes('flame')) { box('flame', [8, top, 5], [8, top + 6, 11], 'flame', { cut: true }); box('flame', [5, top, 8], [11, top + 6, 8], 'flame', { cut: true }); }
  if (c.extra.includes('ring')) { box('ring', [8, top, 4.5], [8, top + 7, 11.5], 'ring', { cut: true }); box('ring', [4.5, top, 8], [11.5, top + 7, 8], 'ring', { cut: true }); }
  if (c.extra.includes('spikes')) for (const [x, z] of [[1, 2], [13, 2], [1, 12], [13, 12]]) {
    box('spike', [x, b + 12, z], [x + 2, b + 14, z + 2], 'spike'); box('spike', [x + 0.5, b + 14, z + 0.5], [x + 1.5, b + 16, z + 1.5], 'spiketip'); }
  return E;
}
const DIRS = ['north', 'east', 'south', 'west', 'up', 'down'];
function faceSize(e, d) { // texels (1 per unit, at least 1)
  const [x1, y1, z1] = e.from, [x2, y2, z2] = e.to, dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
  const s = { north: [dx, dy], south: [dx, dy], east: [dz, dy], west: [dz, dy], up: [dx, dz], down: [dx, dz] }[d];
  return s.map(v => Math.max(1, Math.round(v)));
}
function facesOf(e) { // zero-thickness planes only get their two broad faces
  const [x1, y1, z1] = e.from, [x2, y2, z2] = e.to;
  if (x1 === x2) return ['east', 'west']; if (z1 === z2) return ['north', 'south']; if (y1 === y2) return ['up', 'down'];
  return DIRS.filter(d => !e.skip.includes(d));
}

// ---------------- texture ----------------
function texture(c, E) {
  const T = new Array(TS * TS).fill(null); const set = (x, y, col) => { if (x >= 0 && y >= 0 && x < TS && y < TS) T[y * TS + x] = col; };
  const regions = []; let cx = 0, cy = 0, rowH = 0;
  const alloc = (w, h) => { if (cx + w > TS) { cx = 0; cy += rowH; rowH = 0; } const r = [cx, cy, w, h]; cx += w; rowH = Math.max(rowH, h); if (cy + h > TS) throw new Error('atlas full ' + c.id); return r; };
  const hash = (a, b2) => ((a * 73856093) ^ (b2 * 19349663)) >>> 0;
  const [wl, wb, wd] = c.wood, [ml, mb, md] = c.metal, [tl, tb, td] = c.trim, [gl, gb, gd] = R.gold, gem = c.gem || c.trim;
  const P = {
    wood(r, d, e) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      let col = (y % 3 === 2) ? wd : (hash(x >> 2, y) % 7 === 0 ? wl : wb); if (c.stars && hash(x, y + w) % 23 === 0) col = '#FFE6FA';
      if (y === 0) col = wd; set(x0 + x, y0 + y, col); } },
    lid(r, d, e) { if (d === 'up') return P.lidtop(r, d, e); P.wood(r, d, e); const [x0, y0, w, h] = r; for (let x = 0; x < w; x++) set(x0 + x, y0, wl); },
    lidtop(r, d, e) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      let col = (x % 3 === 2) ? wd : wb; if (c.stars && hash(x + 7, y) % 19 === 0) col = '#FFE6FA'; if (x === 0 || y === 0) col = wl; if (x === w - 1 || y === h - 1) col = wd; set(x0 + x, y0 + y, col); } },
    metal(r) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { let col = mb; if (x === 0 || y === 0) col = ml; if (x === w - 1 || y === h - 1) col = md; if (h > 3 && w > 1 && x === 1 && (y === 1 || y === h - 2)) col = ml; set(x0 + x, y0 + y, col); } },
    trim(r) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { let col = y === 0 ? tl : y === h - 1 ? td : tb; if (c.glow && h >= 2 && y === h - 1) col = c.glow; if (x % 5 === 2 && y === 0 && w > 4) col = '#FFFFFF'; set(x0 + x, y0 + y, col); } },
    gold(r) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x0 + x, y0 + y, y === 0 || x === 0 ? gl : (x === w - 1 || y === h - 1) ? gd : gb); },
    latch(r, d) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x0 + x, y0 + y, y === 0 ? gl : y === h - 1 ? gd : gb);
      if (d === 'north' || d === 'south') { set(x0, y0 + 2, '#1A0E0C'); set(x0 + 1, y0 + 2, '#1A0E0C'); set(x0 + (w > 1 ? 1 : 0), y0 + 3, '#1A0E0C'); } },
    skull(r, d) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x0 + x, y0 + y, y === 0 ? ml : md);
      if (d === 'north' || d === 'south') { set(x0, y0 + 1, '#FF5A5A'); set(x0 + 1, y0 + 1, '#FF5A5A'); set(x0, y0 + 2, '#FF2D3F'); set(x0 + 1, y0 + 2, '#FF2D3F'); set(x0, y0 + 3, '#9C1B2B'); } },
    gem(r) { const [x0, y0, w, h] = r; const [a, bb, dd] = gem; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x0 + x, y0 + y, x === 0 ? a : x === w - 1 ? dd : bb); set(x0, y0, '#FFFFFF'); },
    spike(r) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x0 + x, y0 + y, x === 0 ? ml : x === w - 1 ? md : mb); },
    spiketip(r) { const [x0, y0, w, h] = r; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x0 + x, y0 + y, y === 0 ? tl : tb); },
    vine(r) { const [x0, y0, w, h] = r; for (let x = 0; x < w; x++) { if (hash(x, 3) % 3 === 0) continue; const len = 2 + hash(x, 9) % (h - 1);
      for (let y = 0; y < len; y++) set(x0 + x, y0 + y, (y + x) % 3 === 0 ? '#9BE84A' : '#3E8A2A'); if (hash(x, 5) % 2) set(x0 + x, y0 + len, '#6CC63A'); } },
    wing(r) { const [x0, y0, w, h] = r; // a wing: high tip, feathers hanging down
      const M = ['......GG', '....GGGg', '..GGGgOO', 'GGGgOOOO', 'gOOOOOrO', 'OOOrOrOr', 'OrOrOrO.', 'rOrOr.r.', 'r.r.r...', '........'];
      const pal = { G: gl, g: gb, O: '#FF8C1A', r: '#FF2D3F' };
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const ch = (M[Math.floor(y * M.length / h)] || '')[Math.floor(x * 8 / w)]; if (pal[ch]) set(x0 + x, y0 + y, pal[ch]); } },
    flame(r) { const [x0, y0, w, h] = r; const mid = (w - 1) / 2;
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const half = (y / h) * (w / 2) + 0.5; if (Math.abs(x - mid) > half) continue;
        const k = Math.abs(x - mid) / (half + .01); set(x0 + x, y0 + y, y < 2 ? '#FFF3A3' : k < .45 ? '#FFE27A' : k < .8 ? '#FF8C1A' : '#FF2D3F'); } },
    ring(r) { const [x0, y0, w, h] = r; const cx2 = (w - 1) / 2, cy2 = (h - 1) / 2;
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const dd = Math.hypot(x - cx2, y - cy2); if (dd <= 2.2) set(x0 + x, y0 + y, '#120D1A'); else if (dd <= 3.3) set(x0 + x, y0 + y, (x + y) % 2 ? tb : tl); } },
  };
  const faces = [];
  E.forEach((e, ei) => facesOf(e).forEach(d => { const [w, h] = faceSize(e, d); const r = alloc(w, h); P[e.mat](r, d, e); faces.push({ ei, d, r }); }));
  return { T, faces };
}
const uvOf = r => [r[0] * 16 / TS, r[1] * 16 / TS, (r[0] + r[2]) * 16 / TS, (r[1] + r[3]) * 16 / TS];
function modelJson(c, E, faces) {
  const els = E.map((e, ei) => { const f = {}; faces.filter(x => x.ei === ei).forEach(x => f[x.d] = { uv: uvOf(x.r), texture: '#t' });
    const o = { name: e.name, from: e.from, to: e.to, faces: f }; if (e.rot) o.rotation = { origin: e.rot.origin, axis: e.rot.axis, angle: e.rot.angle }; if (e.cut) o.shade = false; return o; });
  return { parent: 'minecraft:block/block', textures: { t: `phoenix:item/crate/block/${c.id}`, particle: `phoenix:item/crate/block/${c.id}` }, elements: els };
}

// ---------------- keys (16x16) ----------------
function key(c) { // matches its crate: ring bow in the crate's trim colour with the crate's gem, metal shaft, chunky bit, the crate's one detail
  const rows = [
    '..........fffff.',
    '..........fkkkkF',
    '..........fkGGkF',
    '..........fkGWkF',
    '..........fkkkkF',
    '.........SsFFFd.',
    '........SSs.....',
    '.......SSs......',
    '......SSs.......',
    '.....SSs........',
    '....SSs.........',
    '...SSs..........',
    '..BBs...........',
    '.BBBB...........',
    '.B.Bd...........',
    '................'];
  const g = rows.map(r => r.split(''));
  const deco = {
    cinder: [[7, 9, 'v'], [8, 10, 'v'], [5, 11, 'v'], [9, 7, 'v'], [10, 8, 'v']],
    blaze: [],
    inferno: [[8, 2, 'p'], [8, 3, 'p'], [9, 1, 'p']],
    phoenix: [[9, 0, 'y'], [8, 1, 'o'], [9, 1, 'y'], [8, 2, 'o'], [9, 2, 'r'], [9, 3, 'o']],
    eclipse: [[7, 2, 's'], [5, 5, 's'], [8, 4, 'S2']],
    conqueror: [[10, 0, 't'], [15, 0, 't'], [9, 0, 't']],
    ember: [],
  }[c.id] || [];
  deco.forEach(([x, y, ch]) => { if (g[y][x] === '.') g[y][x] = ch; });
  const frame = c.id === 'ember' ? R.white : c.trim; const gem = c.gem || (c.id === 'ember' ? ['#FFE0B0', '#E2A86C', '#6F4324'] : c.trim);
  const ring = c.id === 'eclipse' ? ['#5A5070', '#2C2838', '#120D1A'] : c.id === 'conqueror' ? c.metal : frame;
  const pal = { f: ring[0], F: ring[1], d: ring[2], k: '#1A0E0C', G: gem[1], W: '#FFFFFF', S: c.metal[0], s: c.metal[2], B: frame[1],
    v: '#4FD25C', p: '#B36BFF', y: '#FFF3A3', o: '#FF8C1A', r: '#FF2D3F', s2: '#FFD0F4', t: '#FF2D3F' };
  pal.S2 = '#FFD0F4'; if (c.id === 'eclipse') pal.s = c.metal[2];
  const px = []; for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const ch = g[y][x]; px.push(ch === 's' && c.id === 'eclipse' && y < 6 && x < 9 ? '#FFD0F4' : (pal[ch] || null)); }
  const out = px.slice();
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { if (px[y * 16 + x]) continue; const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => { const X = x + dx, Y = y + dy; return X >= 0 && Y >= 0 && X < 16 && Y < 16 && px[Y * 16 + X]; }); if (n) out[y * 16 + x] = '#2B1210'; }
  return out;
}

(async () => {
  const data = CRATES.map(c => { const E = build(c); const { T, faces } = texture(c, E); return { c, E, T, faces, model: modelJson(c, E, faces), key: key(c) }; });
  const b = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await b.newPage(); const logs = [];
  p.on('console', m => logs.push(m.text())); p.on('pageerror', e => logs.push('ERR ' + e));
  await p.setContent(`<!doctype html><body><script>${fs.readFileSync(THREE_JS, 'utf8')}</script></body>`);
  const res = await p.evaluate(({ data, TS }) => {
    const out = { tex: {}, key: {} };
    const canvasOf = (arr, w, h) => { const cv = document.createElement('canvas'); cv.width = w; cv.height = h; const x = cv.getContext('2d'); arr.forEach((col, i) => { if (col) { x.fillStyle = col; x.fillRect(i % w, Math.floor(i / w), 1, 1); } }); return cv; };
    // MC face corner order (texture TL, TR, BR, BL) in model space
    const corners = (f, t, d) => { const [x1, y1, z1] = f, [x2, y2, z2] = t; return {
      south: [[x1, y2, z2], [x2, y2, z2], [x2, y1, z2], [x1, y1, z2]], north: [[x2, y2, z1], [x1, y2, z1], [x1, y1, z1], [x2, y1, z1]],
      east: [[x2, y2, z2], [x2, y2, z1], [x2, y1, z1], [x2, y1, z2]], west: [[x1, y2, z1], [x1, y2, z2], [x1, y1, z2], [x1, y1, z1]],
      up: [[x1, y2, z1], [x2, y2, z1], [x2, y2, z2], [x1, y2, z2]], down: [[x1, y1, z2], [x2, y1, z2], [x2, y1, z1], [x1, y1, z1]] }[d]; };
    const shadeOf = { up: 1, down: .5, north: .8, south: .8, east: .6, west: .6 };
    const rotate = (v, rot) => { if (!rot) return v; const a = rot.angle * Math.PI / 180, [ox, oy, oz] = rot.origin; let [x, y, z] = [v[0] - ox, v[1] - oy, v[2] - oz];
      const c = Math.cos(a), s = Math.sin(a);
      if (rot.axis === 'y') [x, z] = [x * c + z * s, -x * s + z * c]; else if (rot.axis === 'x') [y, z] = [y * c - z * s, y * s + z * c]; else [x, y] = [x * c - y * s, x * s + y * c];
      return [x + ox, y + oy, z + oz]; };
    const W = 7 * 300, H = 420; const renderer = new THREE.WebGLRenderer({ antialias: false, preserveDrawingBuffer: true, alpha: true }); renderer.setSize(W, H); renderer.setPixelRatio(1);
    const scene = new THREE.Scene(); scene.background = new THREE.Color('#16110F');
    const Z = 11; const cam = new THREE.OrthographicCamera(-W / 2 / Z, W / 2 / Z, H / 2 / Z, -H / 2 / Z, -300, 300);
    cam.position.set(1, 0.82, 1).multiplyScalar(50); cam.lookAt(0, 0, 0); cam.position.y += 2.2;
    data.forEach(({ c, T, faces, model, key }, n) => {
      const tc = canvasOf(T, TS, TS); out.tex[c.id] = tc.toDataURL('image/png'); out.key[c.id] = canvasOf(key, 16, 16).toDataURL('image/png');
      const tex = new THREE.CanvasTexture(tc); tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter; tex.flipY = false; tex.colorSpace = THREE.SRGBColorSpace;
      const pos = [], uv = [], col = [];
      model.elements.forEach(el => Object.entries(el.faces).forEach(([d, f]) => {
        const q = corners(el.from, el.to, d).map(v => rotate(v, el.rotation)); const [u1, v1, u2, v2] = f.uv.map(x => x / 16);
        const tuv = [[u1, v1], [u2, v1], [u2, v2], [u1, v2]]; const s = el.shade === false ? 1 : shadeOf[d];
        [[0, 3, 2], [0, 2, 1]].forEach(tri => tri.forEach(i => { pos.push(...q[i]); uv.push(...tuv[i]); col.push(s, s, s); }));
      }));
      const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2)); g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
      const m = new THREE.Mesh(g, new THREE.MeshBasicMaterial({ map: tex, vertexColors: true, alphaTest: .5, side: THREE.DoubleSide }));
      // lay the crates out along the screen's x axis
      const off = (n - 3) * 26; const right = new THREE.Vector3(1, 0, -1).normalize(); m.position.set(-8 + right.x * off, -8, -8 + right.z * off);
      scene.add(m); m.userData.center = new THREE.Vector3(m.position.x + 8, m.position.y + 8, m.position.z + 8);
      out.centers = out.centers || []; out.centers.push(m.userData.center);
    });
    renderer.render(scene, cam);
    const cv2 = document.createElement('canvas'); cv2.width = W; cv2.height = H + 70; const x2 = cv2.getContext('2d'); x2.fillStyle = '#16110F'; x2.fillRect(0, 0, W, H + 70); x2.drawImage(renderer.domElement, 0, 30);
    const LBL = { common: '#E6E9EF', uncommon: '#4FD25C', rare: '#3DA5FF', epic: '#B36BFF', legendary: '#FFD21A', mythic: '#FF4FD8', special: '#FF2D3F' };
    data.forEach(({ c }, n) => { const v = out.centers[n].clone().project(cam); const X = (v.x + 1) / 2 * W; x2.textAlign = 'center'; x2.fillStyle = LBL[c.rarity]; x2.font = 'bold 22px monospace'; x2.fillText(c.name, X, 26); x2.fillStyle = '#E8DCC0'; x2.font = '15px monospace'; x2.fillText(c.rarity, X, H + 56); });
    delete out.centers; out.preview = cv2.toDataURL('image/png');
    // keys sheet
    const kc = document.createElement('canvas'); kc.width = 7 * 150; kc.height = 150; const kx = kc.getContext('2d'); kx.fillStyle = '#16110F'; kx.fillRect(0, 0, kc.width, kc.height); kx.imageSmoothingEnabled = false;
    data.forEach(({ c, key }, n) => { const X = n * 150 + 23; kx.fillStyle = '#140F0F'; kx.fillRect(X - 4, 10, 104, 104); kx.drawImage(canvasOf(key, 16, 16), X + 4, 18, 88, 88); kx.textAlign = 'center'; kx.fillStyle = LBL[c.rarity]; kx.font = 'bold 15px monospace'; kx.fillText(c.name + ' key', X + 48, 136); });
    out.keys = kc.toDataURL('image/png');
    return out;
  }, { data: data.map(d => ({ c: d.c, T: d.T, faces: d.faces, model: d.model, key: d.key })), TS });
  const save = (f, buf) => { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, buf); };
  const A = `${ROOT}/resourcepack/java/assets/phoenix`; const hinge = {};
  data.forEach(({ c, model }) => {
    save(`${A}/textures/item/crate/block/${c.id}.png`, Buffer.from(res.tex[c.id].split(',')[1], 'base64'));
    save(`${A}/models/item/crate/block/${c.id}.json`, JSON.stringify(model, null, 1) + '\n');
    // RP8 lid animation: the same model split in two (same coordinates); the plugin swings the lid display on its back hinge
    const LID = new Set(['lid', 'arch', 'postlid', 'lidbar', 'gembase', 'gem', 'flame', 'ring', 'spike', 'spiketip']);
    for (const [part, keep] of [['body', e => !LID.has(e.name)], ['lid', e => LID.has(e.name)]])
      save(`${A}/models/item/crate/block/${c.id}_${part}.json`, JSON.stringify({ ...model, elements: model.elements.filter(keep) }, null, 1) + '\n');
    hinge[c.id] = { y: (c.feet ? 1 : 0) + 8, z: 2 }; // model units (1/16 block): lid pivots on its back (north) edge
    save(`${A}/textures/item/crate/key/${c.id}.png`, Buffer.from(res.key[c.id].split(',')[1], 'base64'));
  });
  save(`${ROOT}/resourcepack/src/preview/crates.png`, Buffer.from(res.preview.split(',')[1], 'base64'));
  save(`${ROOT}/resourcepack/src/preview/crate_keys.png`, Buffer.from(res.keys.split(',')[1], 'base64'));
  save(`${ROOT}/resourcepack/src/crate-art/hinges.json`, JSON.stringify(hinge, null, 1) + '\n');
  console.log('crates', data.length, logs.slice(0, 5));
  await b.close();
})();
