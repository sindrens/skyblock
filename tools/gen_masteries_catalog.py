#!/usr/bin/env python3
"""One-off generator of the DEFAULT island mastery catalog (modules/progression/src/main/resources/masteries.yml,
PHASE-5 M5.5).

Not part of the build. masteries.yml is the source of truth after generation: owner edits go there (or into the server
copy). Re-running overwrites the bundled default, so only do that on purpose (e.g. in the M5.9 balance pass).

Prices come from the balance model (tools/economy_model.py, ECONOMY_BALANCE.md):
- mastery ranks: W x 0.067 x 1.49^(n-1) x REF(n), n = the rank's price tier (section 5.2 / 5.5);
- Market Contacts (MA5): the endless curve of section 5.4, 4 h of REF(12) x 1.12^(k-1) per level.
Pillar gate: 5 x price tier (plan default). Every number is a changeable default.
"""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
import economy_model as em  # noqa: E402  (the repo's own balance model)

OUT = os.path.join(os.path.dirname(__file__), '..', 'modules', 'progression', 'src', 'main', 'resources', 'masteries.yml')


def nice(v):
    if v <= 0:
        return 0
    digits = 3 - int(math.floor(math.log10(v))) - 1
    return int(round(v, digits)) if digits < 0 else int(round(v))


def rank_price(weight, tier):
    return nice(weight * em.LV_H * em.LV_HG ** (tier - 1) * em.ref(tier))


# id, icon, effect, weight, pillar (or None), price tiers, value per rank (absolute), island level per rank (or None)
MASTERIES = [
    ('timber', 'golden_axe', 'timber', 0.6, 'foraging', [2, 4, 7, 10, 12], [4, 8, 12, 16, 20], None),
    ('swift_hands', 'golden_pickaxe', 'swift-hands', 0.6, 'mining', [2, 4, 7, 10, 12], [4, 8, 12, 16, 20], None),
    ('warrior', 'iron_sword', 'warrior', 0.6, 'combat', [2, 4, 7, 10, 12], [4, 8, 12, 16, 20], None),
    ('deep_pockets', 'bundle', 'deep-pockets', 0.5, 'mining', [1, 3, 6, 9, 12], [10, 20, 30, 40, 50], None),
    ('crystal_rush', 'amethyst_shard', 'crystal-rush', 0.7, None, [2, 4, 7, 10, 12], [3, 6, 9, 12, 15], [10, 20, 35, 50, 70]),
    ('lucky_strike', 'rabbit_foot', 'lucky-strike', 0.7, None, [3, 5, 8, 10, 12], [4, 8, 12, 16, 20], [15, 30, 45, 60, 80]),
    ('fuel_efficiency', 'blaze_powder', 'fuel-efficiency', 0.4, 'farming', [5, 9, 12], [10, 20, 30], None),
    ('beast_tamer', 'lead', 'beast-tamer', 0.4, 'farming', [2, 5, 8, 12], [5, 10, 15, 20], None),
    ('angler', 'fishing_rod', 'angler', 0.4, 'farming', [3, 6, 9, 12], [5, 10, 15, 20], None),
    ('homestead', 'red_bed', 'homestead', 0.5, None, [4, 8], [1, 2], [20, 40]),
    ('wings', 'elytra', 'wings', 1.0, None, [4], [1], [20]),
    ('treasury', 'gold_block', 'treasury', 1.0, None, [3, 7, 12], [1, 2, 3], [15, 35, 60]),
]
MARKET_LEVELS = 20
MARKET_BASE_HOURS = 4.0
MARKET_GROWTH = 1.12

lines = []
w = lines.append
w('# Island masteries (Phase 5, M5.5): /is masteries and the Masteries tab of /is upgrade.')
w('# Edit here, then: /skyblock reload progression. Settings (Ascension too): masteries-settings.yml.')
w('# Texts: messages/progression.yml.')
w('#')
w('# A rank needs its pillar level or island level, every task that lists its key mastery.<id>.<rank> (tasks.yml) and')
w('# money from the ISLAND BANK only (owner 169). Rank and payment are saved in one database transaction. Only members')
w('# with the island permission "Upgrades" buy (leader always; admin on by default).')
w('# Every key inside \'masteries\' is one mastery (no other settings belong in this file). New masteries from an update are')
w('# added automatically: turn one off with "enabled: false" instead of deleting it.')
w('# Masteries whose feature does not exist yet are hidden and can\'t be bought (GUI-2 hide-unbuilt rule): timber and')
w('# swift-hands (the Depths roll and break speed read only the tool), fuel-efficiency (Phase 7), beast-tamer (no')
w('# breeding hook), angler (no fishing yet). Their prices and tasks stay here for later.')
w('#')
w('# masteries.<id>:')
w('#   icon:      item id; order: position in the tab (lower first)')
w('#   effect:    what the value does (fixed per mastery): timber, swift-hands, warrior (+% PvE damage, never in PvP),')
w('#              deep-pockets (+% satchel size), crystal-rush (+% crystals), lucky-strike (+% rare finds),')
w('#              fuel-efficiency, beast-tamer, angler, homestead (extra shared island homes), wings (island fly for')
w('#              everyone on the island), treasury (bank fee step: 2% -> 1.5% -> 1.0% -> 0.5%),')
w('#              market-contacts (+% sell prices, inside the +300% sell cap)')
w('#   pillar:    pillar whose level the ranks need (mining, foraging, farming, combat), or leave out')
w('#   requires-maxed: upgrade ids that must be fully upgraded first (Market Contacts: sell_multiplier)')
w('#   enabled:   false = not shown, not bought (bought ranks keep working)')
w('#   ranks:     one entry per rank, in order:')
w('#     tier:         price tier (island level points: upgrades-settings.yml -> level-points)')
w('#     cost:         money from the island bank')
w('#     value:        the effect at this rank (not added up)')
w('#     pillar-level: level of the pillar above needed (optional, 0 = none)')
w('#     island-level: island level needed (optional, 0 = none)')
w('#')
w('# Prices: tools/gen_masteries_catalog.py from the balance model (ECONOMY_BALANCE.md 5.2, 5.4, 5.5); tuned in M5.9.')
w('')
w('masteries:')


def emit(mid, icon, effect, order, pillar, ranks, requires=None):
    w(f'  {mid}:')
    w(f'    icon: "{icon}"')
    w(f'    order: {order}')
    w(f'    effect: {effect}')
    if pillar:
        w(f'    pillar: {pillar}')
    if requires:
        w('    requires-maxed:')
        for r in requires:
            w(f'      - {r}')
    w('    enabled: true')
    w('    ranks:')
    for tier, cost, value, pillar_level, island_level in ranks:
        w(f'      - tier: {tier}')
        w(f'        cost: {cost}')
        w(f'        value: {value}')
        if pillar_level:
            w(f'        pillar-level: {pillar_level}')
        if island_level:
            w(f'        island-level: {island_level}')


for i, (mid, icon, effect, weight, pillar, tiers, values, island) in enumerate(MASTERIES):
    ranks = []
    for k, (t, v) in enumerate(zip(tiers, values)):
        ranks.append((t, rank_price(weight, t), v, 5 * t if pillar else 0, island[k] if island else 0))
    emit(mid, icon, effect, i + 1, pillar, ranks)
market = []
for k in range(1, MARKET_LEVELS + 1):
    market.append((12, nice(MARKET_BASE_HOURS * MARKET_GROWTH ** (k - 1) * em.ref(12)), 2 * k, 0, 0))
emit('market_contacts', 'emerald_block', 'market-contacts', len(MASTERIES) + 1, None, market, ['sell_multiplier'])

with open(OUT, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
print('wrote', os.path.normpath(OUT))
print('ascension base cost (4 h REF(12)):', nice(MARKET_BASE_HOURS * em.ref(12)))
