#!/usr/bin/env python3
"""One-off generator of the DEFAULT island upgrade catalog (modules/progression/src/main/resources/upgrades.yml,
PHASE-5 M5.4).

Not part of the build. upgrades.yml is the source of truth after generation: owner edits go there (or into the server
copy). Re-running overwrites the bundled default, so only do that on purpose (e.g. in the M5.9 balance pass).

Prices come from the balance model (tools/economy_model.py, ECONOMY_BALANCE.md):
- level upgrades: W x 0.067 x 1.49^(n-1) x REF(n), n = the level's task tier (section 5.2);
- production upgrades: C_p(5 x (level - 1) + j + 1), j = position in the production list (section 4.2); task tier
  ceil(k / 6) (the PL rule).
Prices are rounded to 3 significant digits. Every number is a changeable default.
"""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
import economy_model as em  # noqa: E402  (the repo's own balance model)

OUT = os.path.join(os.path.dirname(__file__), '..', 'modules', 'progression', 'src', 'main', 'resources', 'upgrades.yml')


def nice(v):
    if v <= 0:
        return 0
    digits = 3 - int(math.floor(math.log10(v))) - 1
    return int(round(v, digits)) if digits < 0 else int(round(v))


def level_price(weight, tier):
    return nice(weight * em.LV_H * em.LV_HG ** (tier - 1) * em.ref(tier))


# id, tab, icon, effect, weight, task tiers per level, effect value per level (cumulative / absolute), coming soon
LEVEL = [
    ('island_size', 'island', 'grass_block', 'island-size', 1.0, [1, 3, 5], [5, 7, 9]),
    ('member_slots', 'island', 'player_head', 'member-slots', 0.8, [2, 4, 6, 8], [6, 7, 8, 9]),
    ('warp_slots', 'island', 'oak_sign', 'warp-slots', 0.5, [2, 4, 6, 8, 10, 12], [4, 5, 6, 7, 8, 9]),
    ('island_vault', 'island', 'ender_chest', 'island-vault', 0.6, [1, 3, 5, 7, 9], [2, 3, 4, 5, 6]),
    ('entity_limits', 'island', 'armor_stand', 'entity-limits', 1.0, [5], [50]),
    ('bank_capacity', 'economy', 'chest', 'bank-capacity', 1.1, list(range(1, 11)), list(range(1, 11))),
    ('bank_interest', 'economy', 'gold_ingot', 'bank-interest', 1.2, list(range(1, 11)), list(range(1, 11))),
    ('sell_multiplier', 'economy', 'emerald', 'sell-bonus', 1.8, list(range(1, 13)), [5 * n for n in range(1, 13)]),
    ('mine_fortune', 'depths', 'iron_pickaxe', 'mine-fortune', 1.4, list(range(1, 13)), [2 * n for n in range(1, 13)]),
    ('lumberjack', 'depths', 'iron_axe', 'lumberjack', 0.9, list(range(1, 13)), [2 * n for n in range(1, 13)]),
    ('xp_boost', 'depths', 'experience_bottle', 'xp-boost', 0.9, list(range(1, 13)), [2 * n for n in range(1, 13)]),
    # 8 -> 32 (Lv2) -> 64 (Lv4) -> 128 (Lv6) -> 256 (Lv9) -> 512 (Lv12); the levels between the plan's steps add a part
    ('spawner_stack_limit', 'production', 'spawner', 'spawner-stack', 1.0, list(range(1, 13)),
     [16, 32, 48, 64, 96, 128, 160, 208, 256, 352, 432, 512]),
    ('fuel_tank', 'production', 'blast_furnace', 'fuel-tank', 0.8, list(range(1, 13)), list(range(1, 13))),
]
PRODUCTION = [
    ('spawner_rate', 'clock', 'spawner-rate'),
    ('mob_loot', 'rotten_flesh', 'mob-loot'),
    ('crop_growth', 'wheat', 'crop-growth'),
    ('harvest_yield', 'hay_block', 'harvest-yield'),
    ('generator', 'cobblestone', 'generator'),
]

lines = []
w = lines.append
w('# Island upgrades (Phase 5, M5.4): /is upgrade, the Upgrades button in the island menu and in /is level.')
w('# Edit here, then: /skyblock reload progression. Settings: upgrades-settings.yml. Texts: messages/progression.yml.')
w('#')
w('# A level needs every task that lists its key upgrade.<id>.<level> (tasks.yml) and money from the ISLAND BANK only')
w('# (owner 169; no 2% fee, the period\'s interest is kept). Level and payment are saved in one database transaction.')
w('# Only members with the island permission "Upgrades" buy (leader always; admin on by default).')
w('# Every key inside \'upgrades\' is one upgrade (no other settings belong in this file). New upgrades from an update are')
w('# added automatically: turn one off with "enabled: false" instead of deleting it.')
w('#')
w('# upgrades.<id>:')
w('#   tab:      island | economy | depths | production')
w('#   icon:     item id; order: position in its tab (lower first)')
w('#   effect:   what the value does (fixed per upgrade, see below); effects whose feature does not exist yet show')
w('#             "Coming soon" and can\'t be bought: island-vault, fuel-tank, mob-loot, crop-growth, harvest-yield,')
w('#             generator, and mine-fortune / lumberjack / xp-boost until the Depths roll and skill XP read them')
w('#   enabled:  false = not shown, not bought (bought levels keep working)')
w('#   levels:   one entry per level, in order:')
w('#     tier:            task tier of the level (island level points: upgrades-settings.yml -> level-points)')
w('#     cost:            money from the island bank')
w('#     value:           the effect at this level (not added up): island-size border width in chunks (odd),')
w('#                      member-slots slots WITH the leader, warp-slots warps, island-vault storage rows,')
w('#                      entity-limits +%, bank-capacity / bank-interest bank level, sell-bonus / mine-fortune /')
w('#                      lumberjack / xp-boost / spawner-rate / mob-loot / crop-growth / harvest-yield +%,')
w('#                      spawner-stack spawners per block, generator / fuel-tank tier')
w('#     min-island-level: island level needed as well (optional, 0 = none)')
w('#')
w('# Prices: tools/gen_upgrades_catalog.py from the balance model (ECONOMY_BALANCE.md 4.2 and 5.2); tuned in M5.9.')
w('')
w('upgrades:')
order = {}


def emit(uid, tab, icon, effect, levels):
    order[tab] = order.get(tab, 0) + 1
    w(f'  {uid}:')
    w(f'    tab: {tab}')
    w(f'    icon: "{icon}"')
    w(f'    order: {order[tab]}')
    w(f'    effect: {effect}')
    w('    enabled: true')
    w('    levels:')
    for tier, cost, value in levels:
        w(f'      - tier: {tier}')
        w(f'        cost: {cost}')
        w(f'        value: {value}')


for uid, tab, icon, effect, weight, tiers, values in LEVEL:
    if uid == 'fuel_tank':
        continue
    emit(uid, tab, icon, effect, [(t, level_price(weight, t), v) for t, v in zip(tiers, values)])
for j, (uid, icon, effect) in enumerate(PRODUCTION):
    levels = []
    for n in range(1, 11):
        k = 5 * (n - 1) + j + 1
        value = n if effect == 'generator' else 12 * n
        levels.append((math.ceil(k / 6), nice(em.prod_cost(k)), value))
    emit(uid, 'production', icon, effect, levels)
for uid, tab, icon, effect, weight, tiers, values in LEVEL:
    if uid == 'fuel_tank':
        emit(uid, tab, icon, effect, [(t, level_price(weight, t), v) for t, v in zip(tiers, values)])

with open(OUT, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
print('wrote', os.path.normpath(OUT))
