const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const dir = __dirname; const files = ['core.js', ...fs.readdirSync(dir).filter(f => /^set_.*\.js$/.test(f)).sort()];
  const js = files.map(f => fs.readFileSync(path.join(dir, f), 'utf8')).join('\n;\n');
  const b = await chromium.launch(); const p = await b.newPage(); const logs = []; p.on('console', m => logs.push(m.text()));
  await p.setContent(`<!doctype html><body><script>${js}</script></body>`);
  const r = await p.evaluate(() => {
    const ids = Object.keys(ART.defs).filter(i => i.startsWith('glyph/'));
    const c = document.createElement('canvas'); c.width = 880; c.height = Math.ceil(ids.length / 3) * 80 + 20; const x = c.getContext('2d');
    x.fillStyle = '#221A19'; x.fillRect(0, 0, c.width, c.height);
    const widths = {};
    ids.forEach((id, i) => { const g = ART.icon(id); widths[id] = g.w; const cx = (i % 3) * 290 + 10, cy = Math.floor(i / 3) * 80 + 10;
      // chat-like background strip
      x.fillStyle = 'rgba(0,0,0,.45)'; x.fillRect(cx - 4, cy - 4, g.w * 4 + 8, 44);
      g.px.forEach((col, k) => { if (col) { x.fillStyle = col; x.fillRect(cx + (k % g.w) * 4, cy + Math.floor(k / g.w) * 4, 4, 4); } });
      g.px.forEach((col, k) => { if (col) { x.fillStyle = col; x.fillRect(cx + g.w * 4 + 14 + (k % g.w) * 2, cy + 8 + Math.floor(k / g.w) * 2, 2, 2); } });
      x.fillStyle = '#E8DCC0'; x.font = '11px monospace'; x.fillText(id.replace('glyph/', '') + ' ' + g.w + 'px', cx, cy + 58); });
    return { url: c.toDataURL(), widths };
  });
  fs.writeFileSync(process.argv[2], Buffer.from(r.url.split(',')[1], 'base64')); console.log(JSON.stringify(r.widths), logs.join('\n')); await b.close();
})();
