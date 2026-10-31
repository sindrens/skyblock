/* Menu background definitions (Phoenix Night, inventory painted): which slots get wells, tints, rims and decor. Drawn by bg_engine.js. */
const PACK = (() => {
  const R = (a, b) => Array.from({ length: b - a + 1 }, (_, i) => a + i);
  const content6 = [...R(10, 16), ...R(19, 25), ...R(28, 34), ...R(37, 43)];
  const bar6 = R(45, 53);
  const bg = [];
  const areas = ['core', 'island', 'economy', 'depths', 'progress', 'rewards', 'staff'];
  areas.forEach(a => {
    bg.push({ file: `frame_${a}_6`, rows: 6, area: a, wells: [4, ...content6, ...bar6], note: `Generic 6-row frame, ${a} area` });
    bg.push({ file: `frame_${a}_6_tabs`, rows: 6, area: a, wells: [...R(1, 7), ...content6, ...bar6], decor: 'tabs', note: `6-row frame with a tab row (slots 1-7), ${a} area` });
    bg.push({ file: `frame_${a}_3`, rows: 3, area: a, wells: [4, ...R(10, 16), 18, 22], note: `Small 3-row frame (Back 18, Close 22), ${a} area` });
  });
  bg.push({ file: 'frame_core_4', rows: 4, area: 'core', wells: [4, ...R(10, 16), ...R(19, 25), ...R(27, 35)], note: '4-row frame' });
  bg.push({ file: 'frame_staff_4', rows: 4, area: 'staff', wells: [4, ...R(10, 16), ...R(19, 25), ...R(27, 35)], note: '4-row staff frame' });
  const ISL = '#5CBF38', ECO = '#FFC21A', PRO = '#B36BFF', EVT = '#FF6A3D', DEP = '#5AB4E5';
  bg.push({ file: 'hub', rows: 6, area: 'core', decor: 'hub2', wells: [4, 10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 39, 41, 48, 49, 50],
    rims: { 4: '#FFD21A', 10: ISL, 12: ISL, 14: DEP, 16: ISL, 19: ECO, 21: ECO, 23: ECO, 25: ECO, 28: PRO, 30: PRO, 32: PRO, 34: PRO, 39: '#FFD21A', 41: EVT },
    marks: { 9: ISL, 18: ECO, 27: PRO, 36: EVT }, note: '/menu: 15 buttons in 4 groups, wings around your head, group marks in 9/18/27/36' });
  bg.push({ file: 'shop', rows: 6, area: 'economy', decor: 'tabs', wells: [...R(0, 8), ...content6, ...bar6], note: '/shop: 9 tabs in row 0, 28 items, bottom bar' });
  bg.push({ file: 'shop_buy', rows: 6, area: 'economy', decor: 'tabs', wells: [...R(0, 8), 13, 20, 22, 24, 30, 32, ...bar6],
    tint: { 13: '#5A4410', 20: '#1A4A26', 22: '#1A4A26', 24: '#1A4A26', 30: '#1A4A26', 32: '#3A3A44' }, note: 'Shop buy page: item 13, Buy 1/16/64, Fill, Type' });
  bg.push({ file: 'sell_chest', rows: 6, area: 'economy', decor: 'sell', wells: [...R(0, 35), ...bar6], tint: { 47: '#5A4410', 52: '#5A4410', 53: '#5A4410' }, note: '/sell: 4 input rows in a crate, divider, total 47, Sell 52-53' });
  bg.push({ file: 'number_picker', rows: 6, area: 'core', wells: [...R(19, 25), 30, 31, 32, 39, 40, 41, ...bar6],
    tint: { 19: '#5A1A1E', 20: '#5A1A1E', 21: '#5A1A1E', 22: '#5A4410', 23: '#1A4A26', 24: '#1A4A26', 25: '#1A4A26', 39: '#1A4A26', 40: '#1A4A26', 41: '#1A4A26' }, note: 'Any amount screen' });
  bg.push({ file: 'confirm', rows: 3, area: 'core', wells: [...R(10, 16), 18, 22], tint: { 10: '#1A4A26', 11: '#1A4A26', 12: '#1A4A26', 13: '#3A2C29', 14: '#5A1A1E', 15: '#5A1A1E', 16: '#5A1A1E' }, note: 'Every confirm: yes 10-12, details 13, no 14-16' });
  bg.push({ file: 'island', rows: 6, area: 'island', decor: 'island', wells: [4, ...R(10, 16), ...R(19, 25), 40, ...bar6], tint: { 40: '#5A1A1E' }, note: '/is: clouds and island, Leave in a red well at 40' });
  bg.push({ file: 'top', rows: 6, area: 'core', decor: 'top', wells: [4, ...R(11, 15), 21, 22, 23, ...R(28, 34), ...bar6], note: 'Top: tabs 11-15, podium 21-23, #4-#10 at 28-34' });

  return { bg };
})();
