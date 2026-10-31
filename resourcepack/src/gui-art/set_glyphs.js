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
  G('crystals', { C: 'cyan' }, [
    '.........',
    '....C....',
    '...CwC...',
    '...CCC...',
    '...CCC...',
    '...CCC...',
    '....C....',
    '.........',
    '.........']);
  G('gems', { T: 'teal' }, [
    '.........',
    '..TTTTT..',
    '.TwTTTTT.',
    '.TTTTTTT.',
    '..TTTTT..',
    '...TTT...',
    '....T....',
    '.........',
    '.........']);
  G('shards', { P: 'pink' }, [
    '.........',
    '......P..',
    '.....PP..',
    '....PwP..',
    '...PPP...',
    '..PPP....',
    '..PP.....',
    '.........',
    '.........']);
  G('credits', { E: 'ember', W: '#FFFFFF' }, [
    '.........',
    '.........',
    '.EEEEEEE.',
    '.EEEWEEE.',
    '..EWWWE..',
    '.EEEWEEE.',
    '.EEEEEEE.',
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
  function badge(label, colour, kind) {
    const tw = textW(label), w = tw + 2, h = 9; const px = new Array(w * h).fill(null);
    const set = (x, y, c) => { if (x >= 0 && y >= 0 && x < w && y < h) px[y * w + x] = c; };
    const fillAt = x => { // fill colour per column
      if (kind === 'grad') { const t = x / (w - 1); return t < .5 ? mix('#FF3D2E', '#FFB000', t * 2) : mix('#FFB000', '#FFF2A8', (t - .5) * 2); }
      if (kind === 'gold') return mix('#FFB000', '#FFE27A', Math.abs(x / (w - 1) - .5) * 2);
      if (kind === 'staff') return '#1A1E24';
      if (kind === 'owner') return '#B3132A';
      if (kind === 'outline') return '#2A2426';
      return colour;
    };
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const corner = (x === 0 || x === w - 1) && (y === 0 || y === h - 1); if (corner) continue;
      let c = fillAt(x);
      if (y === 1 && kind !== 'staff' && kind !== 'outline' && kind !== 'owner') c = mix(c, '#FFFFFF', .35);         // top shine
      if (y === h - 2 && kind !== 'staff' && kind !== 'outline' && kind !== 'owner') c = mix(c, '#000000', .18);      // bottom shade
      if (y === 0 || y === h - 1) c = mix(fillAt(x), '#000000', .45); // top and bottom edge
      set(x, y, c);
    }
    if (kind === 'outline' || kind === 'staff') for (let x = 1; x < w - 1; x++) { set(x, 0, colour); set(x, h - 1, colour); }
    if (kind === 'won' || kind === 'gold' || kind === 'owner') for (let x = 1; x < w - 1; x++) { set(x, 0, '#FFD21A'); set(x, h - 1, '#C48F0B'); }
    const ink = kind === 'owner' ? '#FFD21A' : (kind === 'staff' || kind === 'outline') ? colour : (lum(kind === 'grad' || kind === 'gold' ? '#FFB000' : colour) > .42 ? '#2B1210' : '#FFFFFF');
    let x = 1;
    for (const ch of label) { const m = F[ch]; m.forEach((row, j) => [...row].forEach((b, i) => { if (b === '1') set(x + i, 2 + j, ink); })); x += m[0].length + 1; }
    return { w, h, px };
  }
  const RANKS = [
    ['ashborn', 'ASHBORN', '#B5ABA4', 'outline'], ['hatchling', 'HATCHLING', '#FFB37A', 'fill'], ['fledgling', 'FLEDGLING', '#FF8C1A', 'fill'],
    ['firewing', 'FIREWING', '#FF5A4D', 'fill'], ['flamecrest', 'FLAMECREST', '#FFB414', 'fill'], ['sunfeather', 'SUNFEATHER', '#FFF1C4', 'fill'],
    ['pyrelord', 'PYRELORD', '#FF2D3F', 'fill'], ['immortal', 'IMMORTAL', null, 'grad'],
    ['torchbearer', 'TORCHBEARER', '#2EC4B6', 'won'], ['flameheart', 'FLAMEHEART', '#B36BFF', 'won'], ['firecrown', 'FIRECROWN', null, 'gold'],
    ['builder', 'BUILDER', '#4DFF6A', 'staff'], ['helper', 'HELPER', '#33E0FF', 'staff'], ['mod', 'MOD', '#3DA5FF', 'staff'], ['admin', 'ADMIN', '#FF3B3B', 'staff'], ['owner', 'OWNER', '#D1202F', 'owner']];
  RANKS.forEach(([id, label, col, kind]) => def('glyph/rank_' + id, () => badge(label, col, kind)));
  ART.RANKS = RANKS; ART.badgeText = textW;
})();
