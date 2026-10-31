/* Draws Minecraft-style chest GUIs on canvas at 3x, in three looks. */
const GUI = (() => {
  let K = 3;
  const T = { // color tokens from UI_STYLE V2
    text: '#E0E0E0', muted: '#A0A0A0', value: '#FFFFFF', action: '#FFE135', header: '#FF2D3F', bar: '#D1202F',
    success: '#4DFF6A', error: '#FF3B3B', money: '#4DFF88', crystals: '#33E0FF', gems: '#2EFFC0', shards: '#FF6B8A',
    brand: '#FF2D3F', ember: '#FF8C1A', flame: '#FFD21A', locked: '#5E5E66', buy: '#4DFF88', sell: '#FF5A5A',
    top1: '#FFD21A', top2: '#E6E6E6', top3: '#FF9A3D', rare: '#3DA5FF', epic: '#C05CFF', legendary: '#FFB414',
  };
  const STYLES = {
    stone: { name: 'Ember Stone', outline: '#2A1A14', base: '#CFC6B8', hi: '#FFF8EE', sh: '#8A7E70',
      well: '#8E8375', wellTL: '#4A3F37', wellBR: '#FFF8EE', title: '#3F3F3F', crumb: '#8B8B8B', mark: '#D1202F',
      invBase: '#C6C6C6', invHi: '#FFFFFF', invSh: '#555555', invWell: '#8B8B8B', invTL: '#373737', invBR: '#FFFFFF', invLabel: '#404040', paintInv: false },
    night: { name: 'Phoenix Night', outline: '#050303', base: '#221A19', hi: '#3C2E2B', sh: '#0E0909', line: '#B8741E', lineCorner: '#FF2D3F',
      well: '#140F0F', wellTL: '#070404', wellBR: '#45342F', title: '#E8DCC0', crumb: '#9A8C80', mark: '#FF2D3F',
      invBase: '#221A19', invHi: '#3C2E2B', invSh: '#0E0909', invWell: '#140F0F', invTL: '#070404', invBR: '#45342F', invLabel: '#404040', paintInv: true },
    vanilla: { name: 'Bedrock / no pack', outline: '#000000', base: '#C6C6C6', hi: '#FFFFFF', sh: '#555555',
      well: '#8B8B8B', wellTL: '#373737', wellBR: '#FFFFFF', title: '#404040', crumb: '#404040', mark: '#404040',
      invBase: '#C6C6C6', invHi: '#FFFFFF', invSh: '#555555', invWell: '#8B8B8B', invTL: '#373737', invBR: '#FFFFFF', invLabel: '#404040', paintInv: false },
  };
  const AREAS = { // night look: the frame line tells you which part of the game you are in
    core: {}, island: { line: '#4E9A30' }, economy: { line: '#C8941A' }, depths: { line: '#3F86B0' }, progress: { line: '#8A4ACC' }, rewards: { line: '#D1202F', lineCorner: '#FFD21A' },
    staff: { base: '#1A1E24', hi: '#2C333D', sh: '#0A0C10', line: '#5A6A80', lineCorner: '#D1202F', well: '#0E1116', wellTL: '#050608', wellBR: '#36404C',
      invBase: '#1A1E24', invHi: '#2C333D', invSh: '#0A0C10', invWell: '#0E1116', invTL: '#050608', invBR: '#36404C', noFlames: true, labelPlate: '#C9D2DC' },
  };
  const iconCache = new Map();
  function iconData(key, fn) { if (!iconCache.has(key)) iconCache.set(key, fn()); return iconCache.get(key); }

  function draw(canvas, def, styleKey, scale) {
    K = scale || 3;
    let S = STYLES[styleKey];
    if (def.area && styleKey === 'night' && AREAS[def.area]) S = Object.assign({}, S, AREAS[def.area]);
    const rows = def.rows, W = 176, H = 114 + rows * 18;
    canvas.width = W * K; canvas.height = H * K;
    const c = canvas.getContext('2d'); c.imageSmoothingEnabled = false;
    const px = (x, y, w, h, col) => { c.fillStyle = col; c.fillRect(x * K, y * K, w * K, h * K); };
    const topH = 17 + rows * 18 + (S.paintInv ? 0 : 6);
    // panel
    panel(px, 0, 0, W, H, S.invBase, S.invHi, S.invSh, S.outline);
    if (styleKey !== 'vanilla') {
      const h = S.paintInv ? H : 17 + rows * 18 + 7;
      panel(px, 0, 0, W, h, S.base, S.hi, S.sh, S.outline, !S.paintInv);
      if (S.line) { frameLine(px, 3, 3, W - 6, h - 6, S.line, S.lineCorner); }
      if (!S.noFlames) corners(px, styleKey, W, h);
      if (def.decor) DECOR[def.decor] && DECOR[def.decor](px, styleKey, S, def);
    }
    // wells
    const isPack = styleKey !== 'vanilla';
    const slots = def.slots || {};
    for (let i = 0; i < rows * 9; i++) {
      const col = i % 9, row = Math.floor(i / 9), x = 7 + col * 18, y = 17 + row * 18;
      const has = slots[i];
      if (isPack) {
        if (has || (def.wells || []).includes(i)) well(px, x, y, (def.tint && def.tint[i]) || S.well, S.wellTL, S.wellBR, def.rims && def.rims[i], (def.ready || []).includes(i));
      } else well(px, x, y, S.well, S.wellTL, S.wellBR);
    }
    if (isPack && def.decor && POST[def.decor]) POST[def.decor](px, styleKey, S, def);
    // player inventory
    const iy = rows * 18 + 30;
    if (S.paintInv && isPack) { // parchment tab behind the client's own dark "Inventory" label (readable in every language)
      px(6, iy - 13, 72, 11, S.outline); px(7, iy - 12, 70, 9, S.labelPlate || '#E8DCC0'); px(7, iy - 12, 70, 1, '#FFF6DE'); px(7, iy - 4, 70, 1, '#BFAE88'); }
    if (def.bg) { for (let r = 0; r < 3; r++) for (let col = 0; col < 9; col++) well(px, 7 + col * 18, iy + r * 18, S.invWell, S.invTL, S.invBR);
      for (let col = 0; col < 9; col++) well(px, 7 + col * 18, iy + 58, S.invWell, S.invTL, S.invBR); canvas._slots = { rows, W, H }; return; }
    c.font = `${7 * K}px 'Pixelify Sans', monospace`; c.textBaseline = 'top';
    c.fillStyle = S.invLabel; c.fillText('Inventory', 8 * K, (iy - 11) * K);
    for (let r = 0; r < 3; r++) for (let col = 0; col < 9; col++) well(px, 7 + col * 18, iy + r * 18, S.invWell, S.invTL, S.invBR);
    for (let col = 0; col < 9; col++) well(px, 7 + col * 18, iy + 58, S.invWell, S.invTL, S.invBR);
    const hot = def.hotbar || HOTBAR;
    hot.forEach((it, col) => { if (it) icon(c, it.icon, 8 + col * 18, iy + 59, it.count); });
    // frame panes in vanilla view
    if (!isPack && def.frame) {
      def.frame.forEach((ch, i) => { if (ch && !slots[i]) { const ic = { R: ['pane', '#d1202f'], O: ['pane', '#ff8c1a'], B: ['pane', '#202020'], G: ['pane', '#7a7a7a'] }[ch]; if (ic) icon(c, ic, 8 + (i % 9) * 18, 18 + Math.floor(i / 9) * 18); } });
    }
    // items
    Object.entries(slots).forEach(([i, it]) => { i = +i; icon(c, it.icon, 8 + (i % 9) * 18, 18 + Math.floor(i / 9) * 18, it.count, it.glint); });
    // title
    c.font = `${8 * K}px 'Pixelify Sans', monospace`;
    let tx = 8 * K; const ty = 5 * K;
    if (isPack) { c.fillStyle = S.mark; c.fillText('▸', tx, ty); tx += c.measureText('▸ ').width; }
    def.title.forEach((part, n) => {
      if (n) { c.fillStyle = S.crumb; c.fillText(' » ', tx, ty); tx += c.measureText(' » ').width; }
      c.fillStyle = n === def.title.length - 1 ? S.title : S.crumb; c.fillText(part, tx, ty); tx += c.measureText(part).width;
    });
    if (def.page) { c.fillStyle = S.crumb; c.fillText(' ' + def.page, tx, ty); }
    canvas._slots = { rows, W, H };
  }
  function panel(px, x, y, w, h, base, hi, sh, ol, openBottom) {
    px(x + 2, y, w - 4, 1, ol); px(x, y + 2, 1, h - 4, ol); px(x + w - 1, y + 2, 1, h - 4, ol); if (!openBottom) px(x + 2, y + h - 1, w - 4, 1, ol);
    px(x + 1, y + 1, 1, 1, ol); px(x + w - 2, y + 1, 1, 1, ol); if (!openBottom) { px(x + 1, y + h - 2, 1, 1, ol); px(x + w - 2, y + h - 2, 1, 1, ol); }
    px(x + 1, y + 2, w - 2, h - (openBottom ? 2 : 4), base); px(x + 2, y + 1, w - 4, 1, base);
    if (!openBottom) px(x + 2, y + h - 2, w - 4, 1, base);
    px(x + 2, y + 1, w - 5, 2, hi); px(x + 1, y + 2, 2, h - (openBottom ? 3 : 5), hi);
    px(x + w - 3, y + 3, 2, h - (openBottom ? 3 : 5), sh); if (!openBottom) px(x + 3, y + h - 3, w - 5, 2, sh);
  }
  function frameLine(px, x, y, w, h, col, corner) {
    px(x + 1, y, w - 2, 1, col); px(x + 1, y + h - 1, w - 2, 1, col); px(x, y + 1, 1, h - 2, col); px(x + w - 1, y + 1, 1, h - 2, col);
    if (corner) { const L = 12; [[x + 1, y, L, 1], [x, y + 1, 1, L], [x + w - 1 - L, y, L, 1], [x + w - 1, y + 1, 1, L], [x + 1, y + h - 1, L, 1], [x, y + h - 1 - L, 1, L], [x + w - 1 - L, y + h - 1, L, 1], [x + w - 1, y + h - 1 - L, 1, L]].forEach(r => px(r[0], r[1], r[2], r[3], corner)); }
  }
  function well(px, x, y, fill, tl, br, rim, ready) {
    if (ready) { fill = mix(fill, '#FFD21A', .22); }
    else if (rim) { fill = mix(fill, rim, .16); }
    px(x, y, 18, 18, fill); px(x, y, 17, 1, tl); px(x, y, 1, 17, tl); px(x + 1, y + 17, 17, 1, br); px(x + 17, y + 1, 1, 17, br);
    const r = ready ? '#FFD21A' : rim;
    if (r) { px(x + 1, y + 16, 16, 1, r); px(x + 1, y + 1, 16, 1, mix(r, fill, .55)); px(x + 1, y + 2, 1, 14, mix(r, fill, .55)); px(x + 16, y + 2, 1, 14, mix(r, fill, .55)); }
    if (ready) { px(x + 14, y + 2, 2, 2, '#FFF3B0'); px(x + 13, y + 3, 1, 1, '#FFD21A'); px(x + 15, y + 4, 1, 1, '#FFD21A'); }
  }
  function mix(a, b, t) { const p = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16)); const A = p(a), B = p(b); return '#' + A.map((v, i) => Math.round(v + (B[i] - v) * t).toString(16).padStart(2, '0')).join(''); }
  function corners(px, styleKey, W, h) {
    const glow = styleKey === 'night' ? ['#FF2D3F', '#FF8C1A', '#FFD21A'] : ['#D1202F', '#FF8C1A', '#FFC21A'];
    const flameAt = (x, y, fx) => { // tiny 5x6 corner flame
      const pts = [[2, 0, 0], [1, 1, 0], [2, 1, 1], [3, 1, 0], [1, 2, 1], [2, 2, 2], [3, 2, 1], [0, 3, 0], [1, 3, 1], [2, 3, 2], [3, 3, 1], [4, 3, 0], [1, 4, 0], [2, 4, 1], [3, 4, 0]];
      pts.forEach(([dx, dy, k]) => px(x + (fx ? 4 - dx : dx), y + dy, 1, 1, glow[k]));
    };
    flameAt(4, 3, false); flameAt(W - 9, 3, true);
    if (styleKey === 'night') { flameAt(4, h - 9, false); flameAt(W - 9, h - 9, true); }
  }
  const POST = { // drawn after the wells, so nothing overwrites them
    top(px) { // gold / silver / bronze medal rings inside the podium wells 22 / 21 / 23
      [[21, '#E6E6E6'], [22, '#FFD21A'], [23, '#FF9A3D']].forEach(([s, col]) => { const x = 7 + (s % 9) * 18, y = 17 + Math.floor(s / 9) * 18;
        px(x + 1, y + 1, 16, 1, col); px(x + 1, y + 16, 16, 1, col); px(x + 1, y + 2, 1, 14, col); px(x + 16, y + 2, 1, 14, col); });
    },
  };
  const DECOR = {
    hub(px, sk, S, def, noMarks) {
      // phoenix wings across row 1 (frame slots 0-3 and 5-8), profile well at slot 4
      const cols = sk === 'night' ? ['#FF2D3F', '#FF6A1A', '#FFC21A', '#FFF3B0'] : ['#D1202F', '#E8601A', '#F0A81A', '#FFE38A'];
      const cx = 88, by = 33;
      for (let f = 0; f < 9; f++) {
        const len = 34 + f * 7, ang = (8 + f * 6.5) * Math.PI / 180;
        for (let side = -1; side <= 1; side += 2) {
          for (let t = 10; t < len; t++) {
            const x = Math.round(cx + side * t * Math.cos(ang)), y = Math.round(by - t * Math.sin(ang) * 0.42 - (f % 2));
            if (y < 18 || y > 34 || x < 6 || x > 169) continue;
            if (x > 78 && x < 98) continue;
            const k = t > len * .75 ? 3 : t > len * .5 ? 2 : t > len * .25 ? 1 : 0;
            px(x, y, 1, 1, cols[k]);
          }
        }
      }
      if (noMarks) return;
      // row markers in the left frame column: daily (sun), categories (grid), recent (clock), places (pin)
      const m = sk === 'night' ? '#FF8C1A' : '#B0461A';
      const mark = (slot, fn) => { const x = 7 + (slot % 9) * 18 + 5, y = 17 + Math.floor(slot / 9) * 18 + 5; fn(x, y); };
      mark(9, (x, y) => { px(x + 3, y, 2, 8, m); px(x, y + 3, 8, 2, m); px(x + 1, y + 1, 6, 6, m); px(x + 2, y + 2, 4, 4, sk === 'night' ? '#FFD21A' : '#F0A81A'); });
      mark(18, (x, y) => { [0, 3, 6].forEach(a => [0, 3, 6].forEach(b => px(x + a, y + b, 2, 2, m))); });
      mark(27, (x, y) => { px(x + 1, y, 6, 1, m); px(x, y + 1, 1, 6, m); px(x + 7, y + 1, 1, 6, m); px(x + 1, y + 7, 6, 1, m); px(x + 3, y + 2, 1, 3, m); px(x + 4, y + 4, 2, 1, m); });
      mark(36, (x, y) => { px(x + 2, y, 4, 1, m); px(x + 1, y + 1, 6, 3, m); px(x + 2, y + 4, 4, 1, m); px(x + 3, y + 5, 2, 2, m); px(x + 3, y + 2, 2, 1, sk === 'night' ? '#241C1C' : '#CFC6B8'); });
    },
    hub2(px, sk, S, def) { // wings around the profile well + one colour mark per group
      DECOR.hub(px, sk, S, def, true);
      Object.entries(def.marks || {}).forEach(([slot, col]) => { slot = +slot; const x = 7 + (slot % 9) * 18, y = 17 + Math.floor(slot / 9) * 18;
        px(x + 7, y + 3, 4, 12, col); px(x + 6, y + 4, 6, 10, col); px(x + 8, y + 2, 2, 14, col); px(x + 7, y + 4, 2, 4, '#FFFFFF'); });
    },
    tabs(px, sk) { // a line under the tab row
      const c = sk === 'night' ? '#B8741E' : '#B0461A'; px(6, 35, 164, 1, c);
    },
    shop(px, sk) { // market awning in the top band
      const a = sk === 'night' ? ['#D1202F', '#E8DCC0'] : ['#D1202F', '#FFF8EE'];
      for (let x = 110; x < 158; x++) { const k = Math.floor((x - 110) / 6) % 2; for (let y = 3; y < 13; y++) { if (y > 10 && (x % 6 === 0 || x % 6 === 5)) continue; px(x, y + 4, 1, 1, a[k]); } }
    },
    island(px, sk) { // clouds + island silhouette in row 1 around slot 4
      const cl = sk === 'night' ? '#4A3A38' : '#FFFFFF';
      [[14, 24], [44, 28], [118, 25], [146, 29]].forEach(([x, y]) => { px(x, y, 12, 3, cl); px(x + 3, y - 2, 6, 2, cl); });
      const g = sk === 'night' ? '#3F7A2A' : '#5CBF38', d = sk === 'night' ? '#4A3020' : '#8A5A32';
      px(70, 30, 36, 2, g); px(72, 32, 32, 2, d); px(76, 34, 24, 1, d);
    },
    top(px, sk, S, def) { // laurels beside the podium (the medal rings are drawn after the wells, see POST.top)
      // laurel marks
      const l = sk === 'night' ? '#6A9A3A' : '#4A8A2A';
      for (let i = 0; i < 5; i++) { px(64 - i * 3, 52 - i * 2, 2, 1, l); px(112 + i * 3, 52 - i * 2, 2, 1, l); }
    },
    sell(px, sk) { // wooden crate interior behind rows 0-3, divider bar row 4
      const w1 = sk === 'night' ? '#3A2618' : '#B8874A', w2 = sk === 'night' ? '#2A1A10' : '#9A6A3A';
      px(5, 15, 166, 76, w2);
      for (let y = 15; y < 91; y += 6) px(5, y, 166, 1, w1);
      const bar = sk === 'night' ? '#5A4A44' : '#7A6E62'; px(5, 17 + 4 * 18 + 6, 166, 6, bar); px(5, 17 + 4 * 18 + 6, 166, 1, '#FFD21A');
    },
    confirm() {}, picker() {},
  };

  function icon(c, spec, x, y, count, glint) {
    if (!spec) return;
    const [name, ...args] = Array.isArray(spec) ? spec : [spec];
    const key = name + JSON.stringify(args);
    const g = iconData(key, () => ICONS[name](...args));
    for (let j = 0; j < 16; j++) for (let i = 0; i < 16; i++) { const col = g[j * 16 + i]; if (col) { c.fillStyle = col; c.fillRect((x + i) * K, (y + j) * K, K, K); } }
    if (glint) { c.fillStyle = 'rgba(200,120,255,.18)'; c.fillRect(x * K, y * K, 16 * K, 16 * K); }
    if (count && count > 1) {
      c.font = `${8 * K}px 'Pixelify Sans', monospace`; c.textBaseline = 'top'; c.textAlign = 'right';
      c.fillStyle = '#3F3F3F'; c.fillText(String(count), (x + 17) * K + K, (y + 9) * K + K);
      c.fillStyle = '#FFFFFF'; c.fillText(String(count), (x + 17) * K, (y + 9) * K);
      c.textAlign = 'left';
    }
  }
  const HOTBAR = [{ icon: 'pickaxe' }, { icon: ['block', '#8a8a90', 3], count: 64 }, { icon: 'wheat', count: 12 }, null, null, null, null, { icon: 'bucket' }, { icon: 'star' }];
  function slotAt(canvas, def, ev) {
    const r = canvas.getBoundingClientRect(); const s = r.width / 176;
    const x = (ev.clientX - r.left) / s, y = (ev.clientY - r.top) / s;
    const col = Math.floor((x - 7) / 18), row = Math.floor((y - 17) / 18);
    if (x < 7 || col > 8 || row < 0 || row >= def.rows) return null;
    return { i: row * 9 + col, rect: { left: r.left + (7 + col * 18) * s, top: r.top + (17 + row * 18) * s, size: 18 * s } };
  }
  return { draw, slotAt, T, STYLES, AREAS };
})();
