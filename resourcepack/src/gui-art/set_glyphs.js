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

  // Rank badges are not here any more: they are a font now (resourcepack/src/badge-font), so names stay editable in LuckPerms.
})();
