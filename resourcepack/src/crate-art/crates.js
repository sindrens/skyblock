/* Crate chests for /warp crates: one 3D chest model per crate, the look set by its rarity tier.
   Writes the real pack files (64x64 texture + item model JSON) and an isometric preview.
   usage (repo root): node resourcepack/src/crate-art/crates.js            Original art, no Mojang textures. */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const ROOT = process.argv[2] || path.resolve(__dirname, '../../..');

// tier ladder: the band colour is the rarity colour (same as crates.yml rarity); higher tiers add one thing each
const CRATES = [
  { id: 'ember', name: 'Ember', rarity: 'common', wood: ['#E0A86A', '#B8743E', '#764826'], band: ['#FFFFFF', '#C9CDD6', '#7C808C'], seam: null, emblem: null, body: 'wood' },
  { id: 'cinder', name: 'Cinder', rarity: 'uncommon', wood: ['#E0A86A', '#B8743E', '#764826'], band: ['#B8FFB0', '#4DD25A', '#1F7A33'], seam: null, emblem: null, body: 'wood' },
  { id: 'blaze', name: 'Blaze', rarity: 'rare', wood: ['#A06A40', '#6E4424', '#45291A'], band: ['#BFE4FF', '#3DA5FF', '#1F5DB0'], seam: null, emblem: 'gem', body: 'wood' },
  { id: 'inferno', name: 'Inferno', rarity: 'epic', wood: ['#A06A40', '#6E4424', '#45291A'], band: ['#E9CCFF', '#B36BFF', '#6A36B0'], seam: '#D9A6FF', emblem: 'gem', body: 'wood' },
  { id: 'phoenix', name: 'Phoenix', rarity: 'legendary', wood: ['#F0606A', '#C62A35', '#7E1420'], band: ['#FFF3A3', '#FFD21A', '#B87A0A'], seam: '#FFE27A', emblem: 'flame', body: 'paint' },
  { id: 'eclipse', name: 'Eclipse', rarity: 'mythic', wood: ['#4A3458', '#2A1D33', '#150E1B'], band: ['#FFC4F2', '#FF4FD8', '#A0208A'], seam: '#FF9BE9', emblem: 'eclipse', body: 'stars' },
  { id: 'conqueror', name: 'Conqueror', rarity: 'special', wood: ['#5A6272', '#363C48', '#1E222A'], band: ['#FF8A8A', '#FF2D3F', '#9C1B2B'], seam: '#FF6B6B', emblem: 'swords', body: 'iron' },
];

// texture atlas layout (64x64, pixel = model unit)
const L = {
  lidTop: [0, 0, 14, 14], lidBottom: [14, 0, 14, 14], bodyTop: [28, 0, 14, 14], bodyBottom: [42, 0, 14, 14],
  lidN: [0, 14, 14, 5], lidE: [14, 14, 14, 5], lidS: [28, 14, 14, 5], lidW: [42, 14, 14, 5],
  bodyN: [0, 19, 14, 9], bodyE: [14, 19, 14, 9], bodyS: [28, 19, 14, 9], bodyW: [42, 19, 14, 9],
  latchS: [56, 0, 2, 4], latchE: [58, 0, 1, 4], latchW: [59, 0, 1, 4], latchN: [60, 0, 2, 4], latchUp: [56, 4, 2, 1], latchDown: [58, 4, 2, 1],
};

function texture(c) {
  const T = new Array(64 * 64).fill(null);
  const set = (x, y, col) => { if (x >= 0 && y >= 0 && x < 64 && y < 64) T[y * 64 + x] = col; };
  const [wl, wb, wd] = c.wood, [bl, bb, bd] = c.band;
  const OUT = '#2B1210';
  // planks: side faces get horizontal planks, the lid top gets vertical planks
  const side = (r, lid) => { const [x0, y0, w, h] = r;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      let col = wb;
      if (c.body === 'wood' || c.body === 'paint') { if ((y + (lid ? 1 : 0)) % 3 === 2) col = wd; if (x === 0 && y % 3 === 0) col = wl; }
      if (c.body === 'iron') { col = wb; if ((x === 3 || x === w - 4) && (y === 1 || y === h - 2)) col = wl; }
      if (c.body === 'stars') { col = wb; if ((x * 7 + y * 11 + (lid ? 3 : 0)) % 23 === 0) col = '#FFE6FA'; }
      if (x === 0 || x === w - 1) col = bb;              // corner bands
      if (x === 1 || x === w - 2) col = bd;
      if (!lid && y === 0 && c.seam) col = c.seam;      // glowing seam under the lid
      if (lid && y === h - 1) col = bd;                   // lid rim
      if (lid && y === 0) col = bl;
      set(x0 + x, y0 + y, col);
    } };
  [L.lidN, L.lidE, L.lidS, L.lidW].forEach(r => side(r, true));
  [L.bodyN, L.bodyE, L.bodyS, L.bodyW].forEach(r => side(r, false));
  // lid top: planks + a band cross + emblem
  { const [x0, y0, w, h] = L.lidTop;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      let col = c.body === 'stars' ? ((x * 5 + y * 9) % 19 === 0 ? '#FFE6FA' : wb) : c.body === 'iron' ? wb : (x % 3 === 2 ? wd : wb);
      if (x === 0 || y === 0 || x === w - 1 || y === h - 1) col = bb;
      if (x === 1 || y === 1 || x === w - 2 || y === h - 2) col = c.body === 'wood' || c.body === 'paint' ? wl : col;
      if (x === 6 || x === 7) col = x === 6 ? bb : bd;  // band across the lid
      set(x0 + x, y0 + y, col);
    }
    const E = { // 6x6 emblems in the middle of the lid
      gem: ['..LL..', '.LwLB.', 'LLLBBB', '.LLBB.', '..LB..', '......'],
      flame: ['..L...', '..LL..', '.LwL..', '.LLLB.', 'LLLLBB', '.LLBB.'],
      eclipse: ['.BBBB.', 'BB..LB', 'B....L', 'B....L', 'BB..LB', '.BBBB.'],
      swords: ['L....L', '.L..L.', '..LL..', '..LL..', '.B..B.', 'B....B'],
    }[c.emblem];
    if (E) E.forEach((row, j) => [...row].forEach((ch, i) => { const col = { L: bl, B: bd, w: '#FFFFFF' }[ch]; if (col) set(x0 + 4 + i, y0 + 4 + j, col); }));
  }
  // insides (seen when open later) and bottoms
  [L.lidBottom, L.bodyTop, L.bodyBottom].forEach(([x0, y0, w, h]) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x0 + x, y0 + y, (x === 0 || y === 0 || x === w - 1 || y === h - 1) ? bb : wd); });
  // latch: rarity colour with a dark keyhole
  Object.entries(L).filter(([k]) => k.startsWith('latch')).forEach(([k, [x0, y0, w, h]]) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(x0 + x, y0 + y, y === 0 ? bl : bb); });
  set(L.latchS[0], L.latchS[1] + 2, OUT); set(L.latchS[0] + 1, L.latchS[1] + 2, OUT); set(L.latchS[0], L.latchS[1] + 3, bd);
  return T;
}

const uv = r => [r[0] / 4, r[1] / 4, (r[0] + r[2]) / 4, (r[1] + r[3]) / 4];
function model(id) {
  const t = '#t';
  const face = r => ({ uv: uv(r), texture: t });
  return {
    parent: 'minecraft:block/block', textures: { t: `phoenix:item/crate/block/${id}`, particle: `phoenix:item/crate/block/${id}` },
    elements: [
      { name: 'body', from: [1, 0, 1], to: [15, 9, 15], faces: { north: face(L.bodyN), east: face(L.bodyE), south: face(L.bodyS), west: face(L.bodyW), up: face(L.bodyTop), down: face(L.bodyBottom) } },
      { name: 'lid', from: [1, 9, 1], to: [15, 14, 15], faces: { north: face(L.lidN), east: face(L.lidE), south: face(L.lidS), west: face(L.lidW), up: face(L.lidTop), down: face(L.lidBottom) } },
      { name: 'latch', from: [7, 7, 15], to: [9, 11, 16], faces: { north: face(L.latchN), east: face(L.latchE), south: face(L.latchS), west: face(L.latchW), up: face(L.latchUp), down: face(L.latchDown) } },
    ],
  };
}

(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.setContent('<!doctype html><body></body>');
  const data = CRATES.map(c => ({ c, tex: texture(c), model: model(c.id) }));
  const res = await p.evaluate(({ data, L }) => {
    const out = {};
    const toURL = c => c.toDataURL('image/png');
    data.forEach(({ c, tex }) => { const cv = document.createElement('canvas'); cv.width = 64; cv.height = 64; const x = cv.getContext('2d');
      tex.forEach((col, i) => { if (col) { x.fillStyle = col; x.fillRect(i % 64, Math.floor(i / 64), 1, 1); } }); out[c.id] = toURL(cv); });
    // isometric preview: draw every texel of the visible faces (up, south, east) as a parallelogram
    const S = 9, cos = Math.cos(Math.PI / 6), sin = Math.sin(Math.PI / 6);
    const W = 7 * 250, H = 330; const cv = document.createElement('canvas'); cv.width = W; cv.height = H; const g = cv.getContext('2d');
    g.fillStyle = '#16110F'; g.fillRect(0, 0, W, H);
    const shade = (hex, f) => { const v = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) * f); return `rgb(${v.map(Math.round).join(',')})`; };
    data.forEach(({ c, tex, model }, n) => {
      const ox = n * 250 + 125, oy = 230;
      const P = (x, y, z) => [ox + (x - z) * cos * S, oy - y * S + (x + z) * sin * S - 8 * S * sin * 2];
      const px = (r, i, j) => tex[(r[1] + j) * 64 + r[0] + i];
      const quad = (a, b2, c2, d, col) => { g.fillStyle = col; g.beginPath(); g.moveTo(...a); g.lineTo(...b2); g.lineTo(...c2); g.lineTo(...d); g.closePath(); g.fill(); g.strokeStyle = col; g.lineWidth = .6; g.stroke(); };
      // ground shadow + glow under higher tiers
      g.fillStyle = 'rgba(0,0,0,.45)'; g.beginPath(); g.ellipse(ox, oy + 6 * S * 0 + 20, 95, 30, 0, 0, Math.PI * 2); g.fill();
      model.elements.forEach(el => {
        const [x0, y0, z0] = el.from, [x1, y1, z1] = el.to;
        const fr = k => { const u = el.faces[k].uv; return [u[0] * 4, u[1] * 4, (u[2] - u[0]) * 4, (u[3] - u[1]) * 4]; };
        // up face: texel (i,j) -> x = x0+i, z = z0+j
        { const r = fr('up'); for (let j = 0; j < r[3]; j++) for (let i = 0; i < r[2]; i++) { const col = px(r, i, j); if (!col) continue;
          const sx = (x1 - x0) / r[2], sz = (z1 - z0) / r[3]; const X = x0 + i * sx, Z = z0 + j * sz;
          quad(P(X, y1, Z), P(X + sx, y1, Z), P(X + sx, y1, Z + sz), P(X, y1, Z + sz), shade(col, 1)); } }
        // south face (+z): texel (i,j) -> x = x0+i, y = y1-j
        { const r = fr('south'); for (let j = 0; j < r[3]; j++) for (let i = 0; i < r[2]; i++) { const col = px(r, i, j); if (!col) continue;
          const sx = (x1 - x0) / r[2], sy = (y1 - y0) / r[3]; const X = x0 + i * sx, Y = y1 - j * sy;
          quad(P(X, Y, z1), P(X + sx, Y, z1), P(X + sx, Y - sy, z1), P(X, Y - sy, z1), shade(col, .82)); } }
        // east face (+x): texel (i,j) -> z = z1-i, y = y1-j
        { const r = fr('east'); for (let j = 0; j < r[3]; j++) for (let i = 0; i < r[2]; i++) { const col = px(r, i, j); if (!col) continue;
          const sz = (z1 - z0) / r[2], sy = (y1 - y0) / r[3]; const Z = z1 - i * sz, Y = y1 - j * sy;
          quad(P(x1, Y, Z), P(x1, Y, Z - sz), P(x1, Y - sy, Z - sz), P(x1, Y - sy, Z), shade(col, .62)); } }
      });
      g.fillStyle = c.band[1]; g.font = 'bold 20px monospace'; g.textAlign = 'center'; g.fillText(c.name, ox, 28);
      g.fillStyle = '#E8DCC0'; g.font = '14px monospace'; g.fillText(c.rarity, ox, 48);
    });
    out.__preview = toURL(cv);
    return out;
  }, { data, L });
  const save = (f, buf) => { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, buf); };
  data.forEach(({ c, model }) => {
    save(`${ROOT}/resourcepack/java/assets/phoenix/textures/item/crate/block/${c.id}.png`, Buffer.from(res[c.id].split(',')[1], 'base64'));
    save(`${ROOT}/resourcepack/java/assets/phoenix/models/item/crate/block/${c.id}.json`, JSON.stringify(model, null, 1) + '\n');
  });
  save(`${ROOT}/resourcepack/src/preview/crates.png`, Buffer.from(res.__preview.split(',')[1], 'base64'));
  console.log('crates', data.length);
  await b.close();
})();
