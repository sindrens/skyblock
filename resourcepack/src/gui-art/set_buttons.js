/* Standard menu buttons: phoenix:menu/<id> */
(() => {
  const { def, fromRows, stamp, mirror, paint, grid, put, at, fillDisc, fillRect } = ART;
  const R = (id, pal, rows, opts = {}) => def(id, () => paint(fromRows(rows, 16, 16, id), pal, { name: id, ...opts }));

  const DISC = [
    '................',
    '.....AAAAAA.....',
    '...AAAAAAAAAA...',
    '..AAAAAAAAAAAA..',
    '..AAAAAAAAAAAA..',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '..AAAAAAAAAAAA..',
    '..AAAAAAAAAAAA..',
    '...AAAAAAAAAA...',
    '.....AAAAAA.....',
    '................'];
  const PLATE = [
    '................',
    '..AAAAAAAAAAAA..',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '..AAAAAAAAAAAA..',
    '................'];
  const XMARK = [ // white cross with a dark drop shadow (s)
    '..WW....WW..',
    '..WWW..WWW..',
    '...WWWWWW...',
    '....WWWWs...',
    '....WWWWs...',
    '...WWWWWWs..',
    '..WWWssWWWs.',
    '..WWs...WWs.',
    '...s.....s..'];
  const CHECK = [
    '.........WW.',
    '........WWWs',
    '.......WWWs.',
    'WW....WWWs..',
    'WWW..WWWs...',
    '.WWWWWWs....',
    '..WWWWs.....',
    '...WWs......',
    '....s.......'];
  const withPlate = (base, sym, ox, oy) => { const g = fromRows(base); stamp(g, sym, ox, oy); return g; };

  // ---- navigation ----
  const ARROW = [
    '................',
    '................',
    '.......E........',
    '......EE........',
    '.....EEE........',
    '....EEEEEEEEEE..',
    '...EEEEEEEEEEE..',
    '..EEEEEEEEEEEE..',
    '...EEEEEEEEEEE..',
    '....EEEEEEEEEE..',
    '.....EEE........',
    '......EE........',
    '.......E........',
    '................',
    '................',
    '................'];
  def('menu/previous', () => paint(stamp(fromRows(ARROW), ['ww'], 9, 5), { E: 'ember' }));
  def('menu/next', () => paint(stamp(mirror(fromRows(ARROW)), ['ww'], 5, 5), { E: 'ember' }));
  R('menu/back', { E: 'ember' }, [
    '................',
    '................',
    '.....E..........',
    '....EE..........',
    '...EEEEEEEEE....',
    '..EEEEEEEEEEEE..',
    '...EEEEEEEEEEEE.',
    '....EE.....EEEE.',
    '.....E......EEE.',
    '............EEE.',
    '.......EEEEEEEE.',
    '......EEEEEEEE..',
    '.......EEEEEE...',
    '................',
    '................',
    '................']);

  // ---- frame buttons ----
  def('menu/close', () => paint(withPlate(DISC, XMARK, 2, 4), { A: 'red', W: '#FFF6EC', s: 'red.2' }));
  def('menu/info', () => paint(stamp(fromRows(DISC), [
    '..WWWW..',
    '.WWWWWW.',
    '.WW..WWs',
    '....WWWs',
    '...WWWs.',
    '...WWs..',
    '....s...',
    '...WW...',
    '...WWs..',
    '....s...'], 4, 3), { A: 'blue', W: '#FFFFFF', s: 'blue.2' }));
  R('menu/help', { R: 'red', S: 'red.2', P: 'parch', Y: 'gold', y: 'gold.2' }, [
    '................',
    '..RRRRRRRRRRR...',
    '..SRRRRRRRRRRR..',
    '..SRRRRYYYRRRRP.',
    '..SRRRYYyYYRRRP.',
    '..SRRRRRRYYRRRP.',
    '..SRRRRRYYyRRRP.',
    '..SRRRRYYyRRRRP.',
    '..SRRRRYYRRRRRP.',
    '..SRRRRRRRRRRRP.',
    '..SRRRRYYRRRRRP.',
    '..SRRRRYYRRRRRP.',
    '..SRRRRRRRRRRRP.',
    '..SPPPPPPPPPPPP.',
    '...SSSSSSSSSSS..',
    '................']);
  R('menu/main_menu', { G: 'green', Y: 'gold', V: 'violet', E: 'ember' }, [
    '................',
    '..GGGG....YYYY..',
    '.GwGGGG..YwYYYY.',
    '.GGGGGG..YYYYYY.',
    '.GGGGGG..YYYYYY.',
    '.GGGGGG..YYYYYY.',
    '..GGGG....YYYY..',
    '................',
    '................',
    '..VVVV....EEEE..',
    '.VwVVVV..EwEEEE.',
    '.VVVVVV..EEEEEE.',
    '.VVVVVV..EEEEEE.',
    '.VVVVVV..EEEEEE.',
    '..VVVV....EEEE..',
    '................']);

  // ---- confirm / cancel ----
  def('menu/confirm', () => paint(withPlate(PLATE, CHECK, 2, 4), { A: 'green', W: '#FFFFFF', s: 'green.2' }));
  def('menu/confirm_side', () => paint(fromRows(PLATE), { A: 'green' }));
  def('menu/cancel', () => paint(withPlate(PLATE, XMARK, 2, 4), { A: 'red', W: '#FFF6EC', s: 'red.2' }));
  def('menu/cancel_side', () => paint(fromRows(PLATE), { A: 'red' }));

  // ---- toggles ----
  const TRACK = [
    '................',
    '................',
    '................',
    '................',
    '...AAAAAAAAAA...',
    '..AAAAAAAAAAAA..',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '.AAAAAAAAAAAAAA.',
    '..AAAAAAAAAAAA..',
    '...AAAAAAAAAA...',
    '................',
    '................',
    '................',
    '................'];
  const KNOB = ['..oooo..', '.oKKKKo.', 'oKKKKKKo', 'oKKKKKKo', 'oKKKKKKo', 'oKKKKKKo', '.oKKKKo.', '..oooo..'];
  def('menu/toggle_on', () => {
    const g = fromRows(TRACK); stamp(g, KNOB, 7, 4); stamp(g, ['....w', '...w.', 'w.w..', '.w...'], 2, 6);
    return paint(g, { A: 'lime', K: 'iron', w: '#FFFFFF' });
  });
  def('menu/toggle_off', () => {
    const g = fromRows(TRACK); stamp(g, KNOB, 1, 4); stamp(g, ['.xx.', 'x..x', 'x..x', '.xx.'], 10, 6);
    return paint(g, { A: 'ash', K: 'stone', x: '#2B2B33' });
  });

  // ---- states ----
  R('menu/loading', { W: 'wood', g: 'glass.1', G: 'glass.2', Y: 'gold' }, [
    '................',
    '..WWWWWWWWWWWW..',
    '..WWWWWWWWWWWW..',
    '...gYYYYYYYYG...',
    '...gYYYYYYYYG...',
    '....gYYYYYYG....',
    '.....gYYYYG.....',
    '......gYYG......',
    '......gYgG......',
    '.....gggYgG.....',
    '....gggggggG....',
    '...ggggYYgggG...',
    '...gYYYYYYYYG...',
    '..WWWWWWWWWWWW..',
    '..WWWWWWWWWWWW..',
    '................']);
  R('menu/error', { R: 'red', W: '#FFFFFF', s: 'red.2' }, [
    '................',
    '.......RR.......',
    '......RRRR......',
    '......RRRR......',
    '.....RRWWRR.....',
    '.....RRWWsR.....',
    '....RRRWWsRR....',
    '....RRRWWsRR....',
    '...RRRRWWsRRR...',
    '...RRRRRssRRR...',
    '..RRRRRRRRRRRR..',
    '..RRRRRWWRRRRR..',
    '.RRRRRRWWsRRRRR.',
    '.RRRRRRRssRRRRR.',
    '.RRRRRRRRRRRRRR.',
    '................']);
  R('menu/empty', { L: '#A3A3AD' }, [ // faint cobweb: "nothing here yet"
    '................',
    '.L.....L.....L..',
    '..L...LLL...L...',
    '...LLL.L.LLL....',
    '...L.L.L.L.L....',
    '..L..LLLLL..L...',
    '.L...L.L.L...L..',
    'LLLLLLLLLLLLLLL.',
    '.L...L.L.L...L..',
    '..L..LLLLL..L...',
    '...L.L.L.L.L....',
    '...LLL.L.LLL....',
    '..L...LLL...L...',
    '.L.....L.....L..',
    '................',
    '................'], { outline: false });
  R('menu/locked', { I: 'iron', L: 'ash', k: '#1A1416' }, [
    '................',
    '.....IIIIII.....',
    '....IIIIIIII....',
    '...III....III...',
    '...II......II...',
    '...II......II...',
    '..LLLLLLLLLLLL..',
    '..LwLLLLLLLLLL..',
    '..LLLLLkkLLLLL..',
    '..LLLLLkkLLLLL..',
    '..LLLLLLkLLLLL..',
    '..LLLLLLkLLLLL..',
    '..LLLLLLLLLLLL..',
    '...LLLLLLLLLL...',
    '................',
    '................']);
  R('menu/maxed', { Y: 'gold', W: '#FFFFFF' }, [ // gold star: maxed / done
    '................',
    '.......Y........',
    '......YYY.......',
    '......YWY.......',
    '.....YYYYY......',
    '.YYYYYWYYYYYYY..',
    '..YYYYYYYYYYY...',
    '...YYYYYYYYY....',
    '....YYYYYYY.....',
    '....YYYYYYY.....',
    '...YYYYYYYYY....',
    '...YYYY.YYYY....',
    '..YYY.....YYY...',
    '..YY.......YY...',
    '................',
    '................']);
  // ---- amount plates: + / ++ / +++ (and - / -- / ---) = small / mid / big step; the exact number is in the item name ----
  const SIGNS = {
    '+': { 1: ['..WW..', '..WW..', 'WWWWWW', 'WWWWWW', '..WW..', '..WW..'], 2: ['..W..', '..W..', 'WWWWW', '..W..', '..W..'], 3: ['.W.', 'WWW', '.W.'] },
    '-': { 1: ['......', '......', 'WWWWWW', 'WWWWWW', '......', '......'], 2: ['.....', '.....', 'WWWWW', '.....', '.....'], 3: ['...', 'WWW', '...'] },
  };
  const amount = (sign, n) => {
    const g = fromRows(PLATE); const m = SIGNS[sign][n], sw = m[0].length, gap = n === 3 ? 1 : 2;
    const total = n * sw + (n - 1) * gap; let x = Math.floor((16 - total) / 2); const y = Math.floor((16 - m.length) / 2);
    for (let i = 0; i < n; i++) { stamp(g, m, x, y); x += sw + gap; }
    return g;
  };
  [['small', 1], ['mid', 2], ['big', 3]].forEach(([k, n]) => {
    def('menu/minus_' + k, () => paint(amount('-', n), { A: 'red', W: '#FFFFFF' }));
    def('menu/plus_' + k, () => paint(amount('+', n), { A: 'green', W: '#FFFFFF' }));
  });
  R('menu/set_min', { Y: 'gold', B: 'ember' }, [
    '................',
    '................',
    '......YYYY......',
    '......YYYY......',
    '......YYYY......',
    '...YYYYYYYYYY...',
    '....YYYYYYYY....',
    '.....YYYYYY.....',
    '......YYYY......',
    '.......YY.......',
    '................',
    '..BBBBBBBBBBBB..',
    '..BBBBBBBBBBBB..',
    '................',
    '................',
    '................']);
  R('menu/set_max', { Y: 'gold', B: 'ember' }, [
    '................',
    '................',
    '..BBBBBBBBBBBB..',
    '..BBBBBBBBBBBB..',
    '................',
    '.......YY.......',
    '......YYYY......',
    '.....YYYYYY.....',
    '....YYYYYYYY....',
    '...YYYYYYYYYY...',
    '......YYYY......',
    '......YYYY......',
    '......YYYY......',
    '................',
    '................',
    '................']);
  def('menu/type_amount', () => { // a slip with 123 and a pencil: "type a number"
    const g = fromRows([
      '................',
      '................',
      '................',
      '................',
      '................',
      '.PPPPPPPPPPPP...',
      'PPPPPPPPPPPPPP..',
      'PPPPPPPPPPPPPP..',
      'PPPPPPPPPPPPPP..',
      'PPPPPPPPPPPPPP..',
      'PPPPPPPPPPPPPP..',
      'PPPPPPPPPPPPPP..',
      'PPPPPPPPPPPPPP..',
      '.PPPPPPPPPPPP...',
      '................',
      '................']);
    stamp(g, ['.d..ddd.ddd', 'dd....d...d', '.d..ddd..dd', '.d..d.....d', 'ddd.ddd.ddd'], 2, 7);
    stamp(g, ['...pp', '..ppp', '.YYp.', 'YYY..', 'nY...'], 11, 0);
    stamp(g, ['YY', 'n.'], 10, 4); put(g, 10, 5, 'n'); put(g, 9, 6, 'g');
    return paint(g, { P: 'parch', d: '#5C3A22', Y: 'gold', p: 'pink', n: 'wood.0', g: 'coal.1' });
  });
  // ---- money buttons ----
  const COIN = [ // 10x10 coin, $ embossed
    '...YYYY...',
    '.YYYYYYYY.',
    '.YYYddYYY.',
    'YYYddddYYY',
    'YYYddYYYYY',
    'YYYYdddYYY',
    'YYYYYYddYY',
    '.YYddddYY.',
    '.YYYddYYY.',
    '...YYYY...'];
  def('menu/sell', () => { const g = grid(); stamp(g, COIN, 1, 4); stamp(g, ['..GG..', '..GG..', 'GGGGGG', 'GGGGGG', '..GG..', '..GG..'], 9, 1); return paint(g, { Y: 'gold', d: 'gold.2', G: 'lime' }); });
  def('menu/sell_all', () => { const g = grid(); stamp(g, COIN, 0, 6); stamp(g, COIN, 6, 6); stamp(g, COIN, 3, 2);
    stamp(g, ['.GG.', 'GGGG', 'GGGG', '.GG.'], 11, 0); stamp(g, ['.W..', 'WWW.', '.W..'], 11, 0); return paint(g, { Y: 'gold', d: 'gold.2', G: 'lime', W: '#FFFFFF' }); });
  R('menu/buy', { B: 'parch', h: 'wood', Y: 'gold', d: 'gold.2' }, [
    '................',
    '.....hhhhhh.....',
    '....hh....hh....',
    '....h......h....',
    '..BBBBBBBBBBBB..',
    '..BBBBBBBBBBBB..',
    '..BBBBBYYBBBBB..',
    '..BBBBYYYYBBBB..',
    '..BBBYYdYYYBBB..',
    '..BBBYYYdYYBBB..',
    '..BBBYYdYYYBBB..',
    '..BBBBYYYYBBBB..',
    '..BBBBBYYBBBBB..',
    '..BBBBBBBBBBBB..',
    '................',
    '................']);
  const CHEST_OPEN = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '..LLLLLLLLLLLL..',
    '.WWWWWWWWWWWWWW.',
    '.WYYYYYYYYYYYYW.',
    '.WWWWWWYYWWWWWW.',
    '.WWWWWWYYWWWWWW.',
    '.WWWWWWWWWWWWWW.',
    '.WWWWWWWWWWWWWW.',
    '.YYYYYYYYYYYYYY.',
    '................',
    '................'];
  def('menu/add_inventory', () => { const g = fromRows(CHEST_OPEN); stamp(g, ['..GGGG..', '..GGGG..', 'GGGGGGGG', '.GGGGGG.', '..GGGG..', '...GG...'], 4, 0); return paint(g, { W: 'wood', L: 'darkwood.2', Y: 'gold', G: 'lime' }); });
  def('menu/take_all', () => { const g = fromRows(CHEST_OPEN); stamp(g, ['...GG...', '..GGGG..', '.GGGGGG.', 'GGGGGGGG', '..GGGG..', '..GGGG..'], 4, 0); return paint(g, { W: 'wood', L: 'darkwood.2', Y: 'gold', G: 'ember' }); });

  // ---- buy page ----
  const DIG = {
    '1': ['.XX..', 'XXX..', '.XX..', '.XX..', '.XX..', '.XX..', 'XXXX.'],
    '6': ['.XXX.', 'XX...', 'XX...', 'XXXX.', 'XX.XX', 'XX.XX', '.XXX.'],
    '4': ['...XX', '..XXX', '.X.XX', 'X..XX', 'XXXXX', '...XX', '...XX'],
  };
  const numPlate = (txt) => { const g = fromRows(PLATE); const w = txt.length * 6 - 1 - (txt === '1' ? 1 : 0); let x = Math.floor((16 - w) / 2);
    for (const d of txt) { stamp(g, DIG[d].map(r => r.replace(/X/g, 'W')), x, 4); x += 6; } return g; };
  def('menu/buy_1', () => paint(numPlate('1'), { A: 'green', W: '#FFFFFF' }));
  def('menu/buy_16', () => paint(numPlate('16'), { A: 'green', W: '#FFFFFF' }));
  def('menu/buy_64', () => paint(numPlate('64'), { A: 'green', W: '#FFFFFF' }));
  def('menu/fill', () => { // a backpack grid where every slot is filled
    const g = fromRows(PLATE.map(r => r.replace(/A/g, 'D')));
    for (let j = 0; j < 3; j++) for (let i = 0; i < 3; i++) { fillRect(g, 2 + i * 4, 2 + j * 4, 4, 4, 'k'); fillRect(g, 3 + i * 4, 3 + j * 4, 3, 3, 'G'); }
    return paint(g, { D: 'dark', k: '#140F0F', G: 'lime' });
  });
  def('menu/undo', () => { // gold plate with a white "go back" arrow (count = seconds left)
    const g = fromRows(DISC);
    stamp(g, [
      '..W.......',
      '.WW.......',
      'WWWWWWWW..',
      'WWWWWWWWW.',
      '.WW....WWW',
      '..W.....WW',
      '........WW',
      '.......WWW',
      '..WWWWWWW.',
      '..WWWWWW..'], 3, 3);
    return paint(g, { A: 'gold', W: '#6E2E08' });
  });
  R('menu/search', { I: 'gold', G: 'glass', H: 'red' }, [
    '................',
    '....IIIII.......',
    '...IGGGGGI......',
    '..IGwwGGGGI.....',
    '..IGwGGGGGI.....',
    '..IGGGGGGGI.....',
    '..IGGGGGGGI.....',
    '..IGGGGGGGI.....',
    '...IGGGGGII.....',
    '....IIIIIIHH....',
    '.........HHHH...',
    '..........HHHH..',
    '...........HHHH.',
    '............HH..',
    '................',
    '................']);
  R('menu/filter', { I: 'iron', C: 'cyan' }, [
    '................',
    '................',
    '.IIIIIIIIIIIIII.',
    '.IIIIIIIIIIIIII.',
    '..IIIIIIIIIIII..',
    '...IIIIIIIIII...',
    '....IIIIIIII....',
    '.....IIIIII.....',
    '......IIII......',
    '......IIII......',
    '......IIII......',
    '......IIII......',
    '................',
    '.......CC.......',
    '.......CC.......',
    '................']);
  R('menu/sort', { P: 'parch', Y: 'gold' }, [
    '................',
    '................',
    '.PPPPPPPPP...YY.',
    '.PPPPPPPPP...YY.',
    '.............YY.',
    '.............YY.',
    '.PPPPPPP.....YY.',
    '.PPPPPPP.....YY.',
    '.............YY.',
    '...........YYYYY',
    '.PPPPP......YYY.',
    '.PPPPP.......Y..',
    '................',
    '................',
    '................',
    '................']);
  def('menu/blank', () => ({ w: 16, h: 16, px: new Array(256).fill(null) }));
})();
