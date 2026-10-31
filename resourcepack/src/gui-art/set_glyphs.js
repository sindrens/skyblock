/* Font glyphs (font phoenix:icons, 9 px tall, ascent 7): currencies, logo, rank badges. */
(() => {
  const { def, fromRows, paint, grid, put, stamp } = ART;
  const G = (id, pal, rows) => def('glyph/' + id, () => paint(fromRows(rows, rows[0].length, 9, id), pal, { name: id }));

  G('money', { Y: 'gold', d: 'gold.2' }, [
    '.........',
    '...YYY...',
    '..YYddY..',
    '.YYdYYYY.',
    '.YYYdYYY.',
    '.YYYYdYY.',
    '..YddYY..',
    '...YYY...',
    '.........']);
  G('crystals', { L: 'cyan.0', C: 'cyan.1', D: 'cyan.2' }, [ // crystal cluster: one tall prism, one small
    '.........',
    '.....L...',
    '....LCD..',
    '....LCD..',
    '.L..LCD..',
    'LCD.LCD..',
    'LCDLLCDD.',
    '.........',
    '.........']);
  G('shards', { L: 'pink.0', P: 'pink.1', D: 'pink.2' }, [ // broken shard with a jagged bottom
    '.........',
    '......L..',
    '.....LPD.',
    '....LLPD.',
    '...LLPD..',
    '..LLPPD..',
    '..L.P.D..',
    '.........',
    '.........']);
  G('credits', { L: 'ember.0', M: 'ember.1', D: 'ember.2', w: '#FFFFFF' }, [ // cut gem: credits (was gems)
    '.........',
    '..LLMMD..',
    '.LwLMMDD.',
    '.MMMMMMM.',
    '..LLMDD..',
    '...LMD...',
    '....M....',
    '.........',
    '.........']);
  G('logo_small', { R: 'red.1', E: 'ember.1', Y: 'gold.1', W: '#FFF6C0' }, [ // phoenix flame
    '....R....',
    '...RR..R.',
    '.R.RER.R.',
    '.RRREERR.',
    '.REEYYER.',
    '.REYWYER.',
    '..EYYYE..',
    '...EEE...',
    '.........'], );

  // ---- rank badges: full names, 9 px tall pill, dark text on the rank colour ----
  const F = {
    A: ['010', '101', '111', '101', '101'], B: ['110', '101', '110', '101', '110'], C: ['011', '100', '100', '100', '011'], D: ['110', '101', '101', '101', '110'],
    E: ['111', '100', '110', '100', '111'], F: ['111', '100', '110', '100', '100'], G: ['011', '100', '101', '101', '011'], H: ['101', '101', '111', '101', '101'],
    I: ['111', '010', '010', '010', '111'], L: ['100', '100', '100', '100', '111'], M: ['10001', '11011', '10101', '10001', '10001'], N: ['1001', '1101', '1011', '1001', '1001'],
    O: ['010', '101', '101', '101', '010'], P: ['110', '101', '110', '100', '100'], R: ['110', '101', '110', '101', '101'], S: ['011', '100', '010', '001', '110'],
    T: ['111', '010', '010', '010', '010'], U: ['101', '101', '101', '101', '111'], W: ['10001', '10001', '10101', '11011', '10001'], Y: ['101', '101', '010', '010', '010'],
  };
  const textW = s => [...s].reduce((a, ch) => a + F[ch][0].length, 0) + s.length - 1;
  const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
  const lum = h => { const [r, g, b] = hex(h).map(v => v / 255); return .2126 * r + .7152 * g + .0722 * b; };
  const mix = (a, b, t) => { const A = hex(a), B = hex(b); return '#' + A.map((v, i) => Math.round(v + (B[i] - v) * t).toString(16).padStart(2, '0')).join(''); };
  const ramp = (stops, t) => { const n = stops.length - 1, i = Math.min(n - 1, Math.floor(t * n)); return mix(stops[i], stops[i + 1], t * n - i); };
  // Badge v2. One shape per family, so the family reads before the colour:
  //   free = outline · bought = round pill, pointed ends from Sunfeather up · won = ribbon (notched) ends · staff = square plate.
  // spec: fill (hex or stops), ink, cap, frame (border colour or null = darker fill), edge ('gold' = gold top/bottom), shine, enchant
  const CAP = { // filled rows per end column (outer first) and how many columns the cap takes before the text
    round: { cols: [[1, 7]], pad: 0 }, point: { cols: [[3, 5], [1, 7]], pad: 0 },
    ribbon: { cols: [[0, 2, 6, 8]], pad: 0 }, square: { cols: [[0, 8]], pad: 1 },
  };
  function badge(label, sp) {
    const cap = CAP[sp.cap], capW = cap.cols.length, tw = textW(label);
    const w = tw + 2 * (capW + cap.pad), h = 9; const px = new Array(w * h).fill(null);
    const inMask = (x, y) => {
      if (x < 0 || y < 0 || x >= w || y >= h) return false;
      const k = x < capW ? x : (x >= w - capW ? w - 1 - x : -1);
      if (k < 0) return sp.cap === 'square' || sp.cap === 'ribbon' ? true : y >= 0 && y <= 8;
      const r = cap.cols[capW - 1 - k] || cap.cols[0]; const rr = cap.cols[k];
      if (rr.length === 4) return (y >= rr[0] && y <= rr[1]) || (y >= rr[2] && y <= rr[3]);
      return y >= rr[0] && y <= rr[1];
    };
    const fillAt = x => Array.isArray(sp.fill) ? ramp(sp.fill, x / (w - 1)) : sp.fill;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      if (!inMask(x, y)) continue;
      const border = !inMask(x - 1, y) || !inMask(x + 1, y) || !inMask(x, y - 1) || !inMask(x, y + 1);
      let c = fillAt(x);
      if (border) c = sp.frame || mix(fillAt(x), '#000000', .45);
      else { if (sp.shine !== false && !inMask(x, y - 2)) c = mix(c, '#FFFFFF', .3); else if (!inMask(x, y + 2)) c = mix(c, '#000000', .14); }
      if (sp.edge === 'gold' && border && (y === 0 || y === h - 1)) c = y === 0 ? '#FFD21A' : '#C48F0B';
      px[y * w + x] = c;
    }
    const ink = sp.ink || (lum(Array.isArray(sp.fill) ? sp.fill[1] : sp.fill) > .42 ? '#2B1210' : '#FFFFFF');
    let x = capW + cap.pad;
    for (const ch of label) { const m = F[ch]; m.forEach((row, j) => [...row].forEach((b, i) => { if (b === '1') px[(2 + j) * w + x + i] = ink; })); x += m[0].length + 1; }
    if (sp.enchant) { // still version of the glint (Bedrock / no shader): two sparkles on the frame, never on the text
      px[0 * w + (w - capW - 3)] = '#FFFFFF'; px[0 * w + (w - capW - 4)] = '#F3E6FF';
      px[(h - 1) * w + (capW + 2)] = '#E8D4FF';
    }
    return { w, h, px, enchant: !!sp.enchant };
  }
  const D = '#2B1210';
  const RANKS = [ // id, label, family, spec
    ['ashborn', 'ASHBORN', 'free', { fill: '#2A2426', frame: '#9E948E', ink: '#C9C0BA', cap: 'round', shine: false }],
    ['hatchling', 'HATCHLING', 'bought', { fill: '#F2D3A8', cap: 'round' }],
    ['fledgling', 'FLEDGLING', 'bought', { fill: '#FF8C1A', cap: 'round' }],
    ['firewing', 'FIREWING', 'bought', { fill: '#E8323F', cap: 'round', edge: 'gold' }],
    ['flamecrest', 'FLAMECREST', 'bought', { fill: '#D93C93', cap: 'round', edge: 'gold' }],
    ['sunfeather', 'SUNFEATHER', 'bought', { fill: ['#FFE873', '#FFD21A', '#FFE873'], frame: '#B87A0A', ink: D, cap: 'point' }],
    ['pyrelord', 'PYRELORD', 'bought', { fill: ['#3A0A10', '#5E1218', '#3A0A10'], frame: '#FFD21A', ink: '#FFD21A', cap: 'point', enchant: true }],
    ['immortal', 'IMMORTAL', 'bought', { fill: ['#FF3D2E', '#FFB000', '#FFF2A8'], frame: '#FFD21A', ink: D, cap: 'point', enchant: true }],
    ['torchbearer', 'TORCHBEARER', 'won', { fill: '#2EC4B6', ink: D, cap: 'ribbon', edge: 'gold' }],
    ['flameheart', 'FLAMEHEART', 'won', { fill: '#A55BF0', ink: '#FFFFFF', cap: 'ribbon', edge: 'gold', enchant: true }],
    ['firecrown', 'FIRECROWN', 'won', { fill: ['#FFB000', '#FFE27A', '#FFB000'], ink: D, cap: 'ribbon', edge: 'gold', frame: '#B87A0A', enchant: true }],
    ['owner', 'OWNER', 'staff', { fill: '#FFC21A', ink: D, cap: 'square' }],
    ['admin', 'ADMIN', 'staff', { fill: '#FF6B6B', ink: D, cap: 'square' }],
    ['mod', 'MOD', 'staff', { fill: '#6CC4FF', ink: D, cap: 'square' }],
    ['helper', 'HELPER', 'staff', { fill: '#7CEB8A', ink: D, cap: 'square' }],
    ['builder', 'BUILDER', 'staff', { fill: '#FF9A3D', ink: D, cap: 'square' }]];
  RANKS.forEach(([id, label, fam, sp]) => def('glyph/rank_' + id, () => badge(label, sp)));
  ART.RANKS = RANKS; ART.badgeText = textW;
})();
