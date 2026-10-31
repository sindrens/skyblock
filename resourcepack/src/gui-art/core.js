/* Phoenix pack art v2: cartoon pixel style.
   Every icon is a letter map (16x16). Letters map to colour ramps; ramp letters get automatic
   cartoon shading (light top/left edge, dark bottom/right edge) and every shape gets a warm dark outline.
   Original art only: nothing here is copied or traced from Mojang textures. */
const ART = (() => {
  const RAMPS = {
    red:    ['#FF8A7A', '#E8323F', '#9C1B2B'],
    ember:  ['#FFC46B', '#FF8C1A', '#C0560F'],
    gold:   ['#FFF3A3', '#FFD21A', '#C48F0B'],
    green:  ['#A6F58F', '#45C44E', '#1F7A33'],
    leaf:   ['#7FD45A', '#3C9A2E', '#22621C'],
    grass:  ['#B4F06A', '#6CC63A', '#3E8A24'],
    dirt:   ['#C99062', '#8F5C38', '#5C3A22'],
    wood:   ['#E8B070', '#B8743E', '#764826'],
    darkwood: ['#A06A40', '#6E4424', '#45291A'],
    stone:  ['#E2E2EA', '#A9A9B6', '#686874'],
    iron:   ['#FFFFFF', '#CDD2DA', '#808796'],
    steel:  ['#8E96A6', '#5A6272', '#363C48'],
    dark:   ['#6A5450', '#3E2E2B', '#241A19'],
    parch:  ['#FFF9E6', '#EEDFBC', '#BBA577'],
    blue:   ['#A9DBFF', '#3DA5FF', '#1F5DB0'],
    cyan:   ['#C8FAFF', '#33E0FF', '#138CA8'],
    teal:   ['#A6FFE8', '#2EE6B0', '#128F6A'],
    violet: ['#E2BCFF', '#B36BFF', '#6A36B0'],
    pink:   ['#FFC9D4', '#FF6B8A', '#B0304E'],
    pig:    ['#FFD6DE', '#FF9EB5', '#D0607E'],
    skin:   ['#FFD9B3', '#E8AE7E', '#B07850'],
    glass:  ['#F4FDFF', '#CDEFF7', '#8FC4D4'],
    lime:   ['#C4FFA8', '#6BE35A', '#2E9A38'],
    ash:    ['#B9B9C2', '#7E7E8A', '#4E4E58'],
    coal:   ['#5C5C66', '#34343C', '#1C1C22'],
    brick:  ['#E98A6A', '#B8543A', '#7A3022'],
  };
  const OUTLINE = '#2B1210';
  const N = 16;

  // palette: { letter: 'ramp' (auto shade) | 'ramp.0/1/2' (fixed tone) | '#hex' (flat) }
  const BASE_PAL = { o: OUTLINE, w: '#FFFFFF', k: OUTLINE };
  function resolve(spec) {
    if (!spec) return null;
    if (spec[0] === '#') return { flat: spec };
    const [r, t] = spec.split('.');
    if (!RAMPS[r]) throw new Error('unknown ramp ' + r);
    return t === undefined ? { ramp: RAMPS[r] } : { flat: RAMPS[r][+t] };
  }

  // letter grid
  function grid(w = N, h = N) { return { w, h, c: new Array(w * h).fill('.') }; }
  function at(g, x, y) { return (x < 0 || y < 0 || x >= g.w || y >= g.h) ? '.' : g.c[y * g.w + x]; }
  function put(g, x, y, ch) { if (x >= 0 && y >= 0 && x < g.w && y < g.h) g.c[y * g.w + x] = ch; }
  function fromRows(rows, w = N, h = N, name = '') {
    const g = grid(w, h);
    rows.forEach((r, y) => {
      if (r.length !== w) console.warn(`[${name}] row ${y} has ${r.length} chars`);
      for (let x = 0; x < w; x++) { const ch = r[x] || '.'; if (ch !== '.' && ch !== ' ') put(g, x, y, ch); }
    });
    return g;
  }
  // overlay rows at offset (spaces and dots are transparent)
  function stamp(g, rows, ox = 0, oy = 0) { rows.forEach((r, y) => { for (let x = 0; x < r.length; x++) { const ch = r[x]; if (ch !== '.' && ch !== ' ') put(g, ox + x, oy + y, ch); } }); return g; }
  function mirror(g) { const m = grid(g.w, g.h); for (let y = 0; y < g.h; y++) for (let x = 0; x < g.w; x++) put(m, g.w - 1 - x, y, at(g, x, y)); return m; }
  function fillRect(g, x, y, w, h, ch) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) put(g, x + i, y + j, ch); }
  function fillDisc(g, cx, cy, r, ch) { for (let y = 0; y < g.h; y++) for (let x = 0; x < g.w; x++) { const dx = x + .5 - cx, dy = y + .5 - cy; if (dx * dx + dy * dy <= r * r) put(g, x, y, ch); } }

  // letter grid -> colours, with cartoon shading + outline
  function paint(g, pal, opts = {}) {
    const P = Object.assign({}, BASE_PAL, pal);
    const spec = {}; Object.keys(P).forEach(k => { spec[k] = typeof P[k] === 'string' ? resolve(P[k]) : P[k]; });
    const out = new Array(g.w * g.h).fill(null);
    for (let y = 0; y < g.h; y++) for (let x = 0; x < g.w; x++) {
      const ch = at(g, x, y); if (ch === '.') continue;
      const s = spec[ch]; if (!s) { console.warn('no colour for', ch, opts.name); continue; }
      if (s.flat) { out[y * g.w + x] = s.flat; continue; }
      const same = (dx, dy) => at(g, x + dx, y + dy) === ch;
      const up = !same(0, -1), dn = !same(0, 1), lf = !same(-1, 0), rt = !same(1, 0);
      let t = 1;
      if (dn && !up) t = 2; else if (up && !dn) t = 0;
      else if (!up && !dn) { if (rt && !lf) t = 2; else if (lf && !rt) t = 0; }
      out[y * g.w + x] = s.ramp[t];
    }
    if (opts.outline !== false) {
      const o = out.slice();
      for (let y = 0; y < g.h; y++) for (let x = 0; x < g.w; x++) {
        if (out[y * g.w + x]) continue;
        const f = (dx, dy) => { const X = x + dx, Y = y + dy; return X >= 0 && Y >= 0 && X < g.w && Y < g.h && out[Y * g.w + X]; };
        if (f(1, 0) || f(-1, 0) || f(0, 1) || f(0, -1)) o[y * g.w + x] = OUTLINE;
      }
      return { w: g.w, h: g.h, px: o };
    }
    return { w: g.w, h: g.h, px: out };
  }

  const FONT3 = { // 3x5 pixel font for counts and stand-in codes
    '0': ['111', '101', '101', '101', '111'], '1': ['010', '110', '010', '010', '111'], '2': ['111', '001', '111', '100', '111'], '3': ['111', '001', '011', '001', '111'],
    '4': ['101', '101', '111', '001', '001'], '5': ['111', '100', '111', '001', '111'], '6': ['111', '100', '111', '101', '111'], '7': ['111', '001', '010', '010', '010'],
    '8': ['111', '101', '111', '101', '111'], '9': ['111', '101', '111', '001', '111'], '?': ['111', '001', '011', '000', '010'],
    A: ['010', '101', '111', '101', '101'], B: ['110', '101', '110', '101', '110'], C: ['011', '100', '100', '100', '011'], D: ['110', '101', '101', '101', '110'],
    E: ['111', '100', '110', '100', '111'], F: ['111', '100', '110', '100', '100'], G: ['011', '100', '101', '101', '011'], H: ['101', '101', '111', '101', '101'],
    I: ['111', '010', '010', '010', '111'], J: ['001', '001', '001', '101', '010'], K: ['101', '101', '110', '101', '101'], L: ['100', '100', '100', '100', '111'],
    M: ['101', '111', '111', '101', '101'], N: ['110', '101', '101', '101', '101'], O: ['010', '101', '101', '101', '010'], P: ['110', '101', '110', '100', '100'],
    Q: ['010', '101', '101', '110', '011'], R: ['110', '101', '110', '101', '101'], S: ['011', '100', '010', '001', '110'], T: ['111', '010', '010', '010', '010'],
    U: ['101', '101', '101', '101', '111'], V: ['101', '101', '101', '101', '010'], W: ['101', '101', '111', '111', '101'], X: ['101', '101', '010', '101', '101'],
    Y: ['101', '101', '010', '010', '010'], Z: ['111', '001', '010', '100', '111'],
  };
  const defs = {};           // id -> () => {w,h,px}
  function def(id, fn) { defs[id] = fn; }
  function icon(id) { if (!defs[id]) throw new Error('no art ' + id); return defs[id](); }
  return { FONT3, RAMPS, OUTLINE, grid, at, put, fromRows, stamp, mirror, fillRect, fillDisc, paint, def, icon, defs };
})();
if (typeof module !== 'undefined') module.exports = ART;
