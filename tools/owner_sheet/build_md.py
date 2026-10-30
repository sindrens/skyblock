import json, sys
D = '/tmp/claude-0/-home-claude/e0e0d16a-5da7-5cc0-8715-921df88d8d9f/scratchpad/sheet/'
src = open(D + 'build_sheet.py').read().split('rows = []')[0].replace('OUT = sys.argv[1]', 'OUT = None')
exec(src)
feats = []
for f in ['review_A.json', 'review_B.json']:
    feats += json.load(open(D + f))['features']
order = [a for a, _ in AREAS] + ['Other']
by = {}
for f in feats:
    by.setdefault(area_for(f['feature']), []).append(f)
esc = lambda s: str(s).replace('|', '\\|').replace('\n', ' ')
out = []
w = out.append
w('# Phoenix Skyblock · feature and test guide')
w('')
w('_Written 30 October 2026 after update83. The server runs all of this now (26 modules)._')
w('')
w('How to use this guide:')
w('- Each area has its features. Every feature has: what it does, its commands, and what to test.')
w('- Test on Java first, then on Bedrock where it says so.')
w('- The questions for every feature are in the spreadsheet **Phoenix_Skyblock_questions.xlsx** (1,037 questions). Start with Priority = "before launch".')
w('- Staff commands need the right rank. "admin" = only admins.')
w('')
w('## What is new since you went to sleep')
w('')
w('| Update | What |')
w('|---|---|')
for u, t in [('update79', 'Armor enchants on wardrobe set pieces (E5): new Armor tab in `/enchanter`.'),
             ('update80', 'Monthly punishment report `/preport` and the automatic "Honorable" title (P10). The punishment system is now complete.'),
             ('update81', '`/settings` toggles that work now: join messages, teleport requests, scoreboard, island invites, particles, menu sounds, event sounds (F1). Balance tuning for enchants and sets; combat book drops made rarer on the live server (TUNE).'),
             ('update82', 'The new V2 look: brighter colors, bold names, red `| Header` sections, green Buy / red Sell prices, bold `» CLICK` lines, new scoreboards (STYLE2). Resource pack plan written (docs/plans/RESOURCE_PACK.md).'),
             ('update83', '`/sell` is now a sell chest; `/shop` is a quick-buy grid (left 1, right 64, shift = amount); satchel has a Sell chest button (SELL2). AFK pool module (closed until you place an `afk_pool` region).')]:
    w(f'| {u} | {t} |')
w('')
w('Still waiting: E6 (hoe and rod enchants) needs Phase 7 (farming and fishing), which has no plan yet. STORE2 needs island storage, which does not exist yet. Both are questions in the sheet.')
w('')
w('## Test these first (most changed tonight)')
w('')
for t in ['`/sell`: put items in, Sell inventory, Sell, close with items inside (they come back), kill the server with items inside and rejoin.',
          '`/shop`: left-click buys 1, right-click 64, shift-click opens the amount screen; buy 64 with only 40 free slots.',
          'Look at tooltips: a forge tool, an enchant book, a set piece, a money note, shop items. Bold names, red `| Header` sections, bold yellow `» CLICK` lines.',
          'Scoreboards on your island, at spawn and in The Depths: gradient title, `| Profile` / `| Island` sections.',
          '`/settings`: switch off scoreboard (it disappears at once), menu sounds, teleport requests.',
          '`/enchanter` → Armor tab: put a book on a set piece you own.',
          '`/preport` (admin): last month\'s punishment report.',
          'AFK pool: add the `afk_pool` region to a build (or the box in `afk.yml`), stand in it, watch the action bar.',
          'Bedrock: open `/sell`, `/shop` (tap opens the amount screen), and check that all symbols show.']:
    w(f'1. {t}')
w('')
w('## Contents')
w('')
for a in order:
    if a in by:
        w(f'- [{a}](#{a.lower().replace(" & ", "--").replace(",", "").replace(" ", "-")})')
        for f in by[a]:
            pass
w('')
for a in order:
    if a not in by: continue
    w(f'## {a}')
    w('')
    for f in by[a]:
        w(f'### {f["feature"]}')
        w('')
        w(f['summary'])
        w('')
        if f['commands']:
            w('| Command | Who | What |')
            w('|---|---|---|')
            for c in f['commands']:
                w(f'| `{esc(c["command"])}` | {esc(c.get("who", ""))} | {esc(c.get("what", ""))} |')
            w('')
        w('**Test:**')
        w('')
        for i, t in enumerate(f['test'], 1):
            w(f'{i}. {t}')
        w('')
        w(f'_Questions for this feature: {len(f["questions"])} in the sheet (Feature = "{f["feature"]}")._')
        w('')
open(sys.argv[1], 'w').write('\n'.join(out) + '\n')
print(len(feats), sum(len(f['commands']) for f in feats), 'commands', len(out), 'lines')
