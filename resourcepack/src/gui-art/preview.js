// Render art icons to a labelled contact sheet: each icon at 4x on the night well, on the Bedrock grey well, and at 2x.
// usage: node art/preview.js <out.png> [filter-prefix]
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const out = process.argv[2] || '/tmp/claude-0/art.png'; const filter = process.argv[3] || '';
  const dir = path.join(__dirname);
  const files = ['core.js', ...fs.readdirSync(dir).filter(f => /^set_.*\.js$/.test(f)).sort()];
  const js = files.map(f => fs.readFileSync(path.join(dir, f), 'utf8')).join('\n;\n');
  const b = await chromium.launch(); const p = await b.newPage(); const logs = [];
  p.on('console', m => logs.push(m.text())); p.on('pageerror', e => logs.push('ERR ' + e));
  await p.setContent(`<!doctype html><body><script>${js}</script></body>`);
  const url = await p.evaluate((filter) => {
    const ids = Object.keys(ART.defs).filter(i => i.startsWith(filter));
    const cols = 6, cw = 196, ch = 104;
    const c = document.createElement('canvas'); c.width = cols * cw; c.height = Math.ceil(ids.length / cols) * ch; const x = c.getContext('2d');
    x.fillStyle = '#221A19'; x.fillRect(0, 0, c.width, c.height); x.imageSmoothingEnabled = false;
    ids.forEach((id, i) => {
      let ic; try { ic = ART.icon(id); } catch (e) { console.log('fail', id, e.message); return; }
      const cx = (i % cols) * cw + 6, cy = Math.floor(i / cols) * ch + 6;
      const well = (wx, wy, s, fill, tl, br) => { x.fillStyle = fill; x.fillRect(wx, wy, 18 * s, 18 * s); x.fillStyle = tl; x.fillRect(wx, wy, 17 * s, s); x.fillRect(wx, wy, s, 17 * s); x.fillStyle = br; x.fillRect(wx + s, wy + 17 * s, 17 * s, s); x.fillRect(wx + 17 * s, wy + s, s, 17 * s); };
      const draw = (ox, oy, s) => ic.px.forEach((col, k) => { if (col) { x.fillStyle = col; x.fillRect(ox + (k % ic.w) * s, oy + Math.floor(k / ic.w) * s, s, s); } });
      well(cx, cy, 4, '#140F0F', '#070404', '#45342F'); draw(cx + 4, cy + 4, 4);
      well(cx + 76, cy, 2, '#8B8B8B', '#373737', '#FFFFFF'); draw(cx + 78, cy + 2, 2);
      well(cx + 76, cy + 40, 2, '#140F0F', '#070404', '#45342F'); draw(cx + 78, cy + 42, 2);
      draw(cx + 120, cy + 8, 1);
      x.fillStyle = '#E8DCC0'; x.font = '11px monospace'; x.fillText(id.replace('menu/', ''), cx + 112, cy + 86);
    });
    return c.toDataURL('image/png');
  }, filter);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, Buffer.from(url.split(',')[1], 'base64'));
  console.log('written', out, logs.slice(0, 20).join('\n'));
  await b.close();
})();
