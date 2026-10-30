#!/usr/bin/env python3
"""One-off generator of the DEFAULT challenge book catalog (modules/progression/src/main/resources/tasks.yml, PHASE-5 M5.3).

Not part of the build. tasks.yml is the source of truth after generation: owner edits go there (or into the server copy).
Re-running overwrites the bundled default, so only do that on purpose (e.g. in the M5.9 balance pass).

Rules (PHASE-5 plan, every number is a changeable default):
- 12 task tiers; tier n uses Depths tier n content (WORLD.md tables) and amounts grow ~1.35x per tier.
- Every tier has the same 15 task kinds; upgrade levels and mastery ranks list the 1-3 kinds that fit their theme
  (unlock keys upgrade.<id>.<level> / mastery.<id>.<rank>, used by M5.4 / M5.5).
- Money tasks: earn = 0.5 h and deposit = 0.4 h of REF(t) (reference income at that tier).
- Rewards: crystals per member 20 x 1.35^(n-1); mine/chop/kill/farm tasks also give pillar XP 50 x 1.35^(n-1).
"""
import math, os

GROWTH = 1.35
OUT = os.path.join(os.path.dirname(__file__), '..', 'modules', 'progression', 'src', 'main', 'resources', 'tasks.yml')

ORES = ['coal_ore', 'copper_ore', 'iron_ore', 'gold_ore', 'redstone_ore', 'lapis_ore', 'deepslate_diamond_ore',
        'deepslate_emerald_ore', 'amethyst_block', 'nether_quartz_ore', 'ancient_debris', 'crying_obsidian']
LOGS = ['oak_log', 'birch_log', 'spruce_log', 'jungle_log', 'acacia_log', 'dark_oak_log', 'mangrove_log', 'cherry_log',
        'pale_oak_log', 'crimson_stem', 'warped_stem', 'warped_stem']
MOBS = ['zombie', 'skeleton', 'spider', 'slime', 'drowned', 'witch', 'pillager', 'blaze', 'wither_skeleton', 'ravager',
        'ravager', 'any']
CROPS = ['wheat', 'carrots', 'beetroots', 'sugar_cane', 'melon', 'cocoa', 'cactus', 'sweet_berry_bush', 'nether_wart',
         'torchflower', 'pitcher_crop', 'any']
DELIVER = ['custom:depths_coal', 'custom:depths_copper', 'custom:depths_iron', 'custom:depths_gold', 'custom:depths_redstone',
           'custom:depths_lapis', 'custom:depths_diamond', 'custom:depths_emerald', 'custom:depths_amethyst',
           'custom:depths_quartz', 'custom:depths_netherite', 'custom:depths_voidstone']
CRAFT = ['bread', 'baked_potato', 'iron_ingot', 'gold_ingot', 'redstone_block', 'lapis_block', 'diamond_block',
         'emerald_block', 'amethyst_block', 'quartz_block', 'netherite_scrap', 'netherite_ingot']
SKILL = ['mining', 'foraging', 'combat', 'mining', 'foraging', 'combat', 'mining', 'foraging', 'combat', 'mining', 'foraging', 'combat']
SKILL_LEVEL = [3, 6, 10, 15, 20, 25, 30, 35, 42, 50, 58, 66]       # Depths unlock levels (WORLD.md 4.2)
PILLAR_LEVEL = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60]
ISLAND_LEVEL = [2, 5, 8, 12, 16, 20, 25, 30, 40, 50, 60, 70]


def ref(t):
    """Reference income per played hour at tier t (ECONOMY_BALANCE.md: REF(t) = 2,500 x 1.3^(t-1) x P(t))."""
    p = 20 * 1.35 ** (min(t, 8) - 1) * 2.2 ** max(0, t - 8)
    return 2500 * 1.3 ** (t - 1) * p


def money(t, hours):
    v = ref(t) * hours
    step = 10 ** max(3, int(math.log10(v)) - 1)
    return int(round(v / step) * step)


def amount(base, tier, growth=GROWTH, step=10):
    v = base * growth ** (tier - 1)
    return int(max(step, round(v / step) * step))


def kinds(t):
    i = t - 1
    return {
        'mine': dict(type='mine', target=ORES[i], amount=amount(300, t), pillar='mining'),
        'chop': dict(type='chop', target=LOGS[i], amount=amount(200, t), pillar='foraging'),
        'kill': dict(type='kill', target=MOBS[i], amount=amount(60, t), pillar='combat'),
        'farm': dict(type='farm', target=CROPS[i], amount=amount(100, t), pillar='farming'),
        'craft': dict(type='craft', target=CRAFT[i], amount=amount(16, t, 1.25, 1)),
        'sell': dict(type='sell', amount=amount(500, t)),
        'earn': dict(type='earn', amount=money(t, 0.5)),
        'deposit': dict(type='deposit', amount=money(t, 0.4)),
        'deliver': dict(type='deliver', target=DELIVER[i], amount=amount(64, t, 1.3, 8)),
        'skill': dict(type='skill-level', target=SKILL[i], amount=SKILL_LEVEL[i]),
        'pillar_mining': dict(type='pillar-level', target='mining', amount=PILLAR_LEVEL[i]),
        'pillar_foraging': dict(type='pillar-level', target='foraging', amount=PILLAR_LEVEL[i]),
        'pillar_combat': dict(type='pillar-level', target='combat', amount=PILLAR_LEVEL[i]),
        'pillar_farming': dict(type='pillar-level', target='farming', amount=PILLAR_LEVEL[i]),
        'island_level': dict(type='island-level', amount=ISLAND_LEVEL[i]),
    }


# (unlock prefix, [task tier per level], [kinds])
UPGRADES = [
    ('upgrade.island_size', [1, 3, 5], ['mine', 'chop', 'farm']),
    ('upgrade.member_slots', [2, 4, 6, 8], ['earn', 'kill', 'island_level']),
    ('upgrade.warp_slots', [2, 4, 6, 8, 10, 12], ['deliver', 'sell']),
    ('upgrade.island_vault', [1, 3, 5, 7, 9], ['deliver', 'mine']),
    ('upgrade.entity_limits', [5], ['kill', 'farm']),
    ('upgrade.bank_capacity', list(range(1, 11)), ['earn', 'deposit']),
    ('upgrade.bank_interest', list(range(1, 11)), ['deposit', 'sell']),
    ('upgrade.sell_multiplier', list(range(1, 13)), ['sell', 'earn']),
    ('upgrade.mine_fortune', list(range(1, 13)), ['mine', 'pillar_mining']),
    ('upgrade.lumberjack', list(range(1, 13)), ['chop', 'pillar_foraging']),
    ('upgrade.xp_boost', list(range(1, 13)), ['skill', 'kill']),
    ('upgrade.spawner_stack_limit', list(range(1, 13)), ['kill', 'deliver']),
    ('upgrade.fuel_tank', list(range(1, 13)), ['farm', 'deliver']),
    ('mastery.timber', [2, 4, 7, 10, 12], ['chop', 'pillar_foraging']),
    ('mastery.swift_hands', [2, 4, 7, 10, 12], ['mine', 'pillar_mining']),
    ('mastery.warrior', [2, 4, 7, 10, 12], ['kill', 'pillar_combat']),
    ('mastery.deep_pockets', [1, 3, 6, 9, 12], ['mine', 'sell']),
    ('mastery.crystal_rush', [2, 4, 7, 10, 12], ['island_level', 'earn']),
    ('mastery.lucky_strike', [3, 5, 8, 10, 12], ['mine', 'kill']),
    ('mastery.fuel_efficiency', [5, 9, 12], ['farm', 'pillar_farming']),
    ('mastery.beast_tamer', [2, 5, 8, 12], ['farm', 'kill']),
    ('mastery.angler', [3, 6, 9, 12], ['farm', 'pillar_farming']),
    ('mastery.homestead', [4, 8], ['island_level', 'deliver']),
    ('mastery.wings', [4], ['island_level', 'deliver']),
    ('mastery.treasury', [3, 7, 12], ['deposit', 'earn']),
]
# Production upgrades (PL rule): level n of upgrade j needs task tier ceil((5 x (n-1) + j + 1) / 6).
PRODUCTION = [('upgrade.spawner_rate', ['kill', 'craft']), ('upgrade.mob_loot', ['kill', 'sell']),
              ('upgrade.crop_growth', ['farm', 'craft']), ('upgrade.harvest_yield', ['farm', 'deliver']),
              ('upgrade.generator', ['mine', 'craft'])]
for j, (prefix, ks) in enumerate(PRODUCTION):
    UPGRADES.append((prefix, [math.ceil((5 * (n - 1) + j + 1) / 6) for n in range(1, 11)], ks))

ORDER = ['mine', 'chop', 'kill', 'farm', 'craft', 'deliver', 'sell', 'earn', 'deposit', 'skill', 'pillar_mining',
         'pillar_foraging', 'pillar_combat', 'pillar_farming', 'island_level']

unlocks = {}
for prefix, tiers, ks in UPGRADES:
    for level, tier in enumerate(tiers, start=1):
        for k in ks:
            unlocks.setdefault((tier, k), []).append(f'{prefix}.{level}')

lines = [
    '# Challenge book (Phase 5, M5.3, O6): the island task list. Edit here, then: /skyblock reload progression.',
    '# Settings: tasks-settings.yml. Texts around the tasks (names, menus, notices): messages/progression.yml.',
    '#',
    '# Progress is shared by every member of an island (never coop players or visitors) and counts from the island\'s',
    '# creation, so earlier play is never wasted. A task is done once per season; its rewards are paid once.',
    '# New tasks from an update are added to this file automatically: turn one off with "enabled: false" instead of',
    '# deleting it. Every key inside \'tasks\' is one task (no other settings belong in this file).',
    '#',
    '# tasks.<id>:',
    '#   tier:      chapter of the book (1-28); tier n uses Depths tier n content',
    '#   type:      mine | chop | farm | kill | craft | sell | earn | deposit | deliver | skill-level | pillar-level | island-level',
    '#   target:    mine/chop/farm: block id; kill: entity id; craft: item id (or any for all of them);',
    '#              deliver: item id (plain items only) or custom:<id> (e.g. custom:depths_coal);',
    '#              skill-level: skill id (best member); pillar-level: pillar id; sell/earn/deposit/island-level: leave out',
    '#   amount:    count (counted types) or level (skill/pillar/island level)',
    '#   rewards:   crystals (to every member, once) and pillar + pillar-xp (island boosts apply on top)',
    '#   unlocks:   keys this task is required for: upgrade.<id>.<level> (M5.4), mastery.<id>.<rank> (M5.5).',
    '#              A key is open when every task listing it is done.',
    '#   icon:      item id shown in the book (optional); name: own name (optional, MiniMessage, max 32 characters)',
    '#   enabled:   false = not shown, not counted',
    '#',
    '# Anti-abuse: blocks placed by players count nothing, kills only by your own hit or arrow, the AFK pool does not',
    '# count for sell/earn. Generated defaults (tools/gen_tasks_catalog.py), tuned in the M5.9 balance pass.',
    '',
    'tasks:',
]
for t in range(1, 13):
    lines.append(f'  # ---------------------------------------------------------------- tier {t}')
    defs = kinds(t)
    for k in ORDER:
        d = defs[k]
        lines.append(f'  t{t}_{k}:')
        lines.append(f'    tier: {t}')
        lines.append(f'    type: {d["type"]}')
        if 'target' in d:
            lines.append(f'    target: "{d["target"]}"')
        lines.append(f'    amount: {d["amount"]}')
        lines.append('    rewards:')
        lines.append(f'      crystals: {int(round(20 * GROWTH ** (t - 1)))}')
        if 'pillar' in d:
            lines.append(f'      pillar: {d["pillar"]}')
            lines.append(f'      pillar-xp: {int(round(50 * GROWTH ** (t - 1)))}')
        u = unlocks.get((t, k), [])
        if u:
            lines.append('    unlocks:')
            for key in u:
                lines.append(f'      - {key}')
        else:
            lines.append('    unlocks: []')

with open(OUT, 'w') as f:
    f.write('\n'.join(lines) + '\n')
print('wrote', os.path.normpath(OUT), '-', 12 * len(ORDER), 'tasks')
