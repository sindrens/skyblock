#!/usr/bin/env python3
"""Phoenix Skyblock economy model (M3.0 balance sheet, BAL2 re-run, DRAFT).

Reproduces every table in docs/ECONOMY_BALANCE.md from the formulas and
parameters below. Standard library only, deterministic (no randomness).

Usage:
  python3 tools/economy_model.py                 # print all tables (markdown) + run the self-check
  python3 tools/economy_model.py --table NAME    # print one table
  python3 tools/economy_model.py --write  docs/ECONOMY_BALANCE.md   # refresh tables in the doc
  python3 tools/economy_model.py --check  docs/ECONOMY_BALANCE.md   # exit 1 if the doc is out of date

The doc holds each table between markers:
  <!-- BEGIN table:NAME -->  ...  <!-- END table:NAME -->

All money values are whole dollars (the plugin stores money as a long).
Every parameter here is a proposal; the live values go into typed configs.

BAL2 (2026-10-30) added, from the owner decisions of round 2/3:
  - the +300% sell cap: every sell bonus (rank, Sell Multiplier upgrade, Market Contacts mastery,
    Coinmaster set by set level, Gilded Touch enchant, personal + global boosters, sell events)
    is summed first, then capped once (MultiplierCap in common);
  - spawner stacking: stack limit 8 -> 512 in 5 steps (Spawner Stack Limit upgrade), 5 spawner tiers,
    shop price that rises with every spawner the island owns;
  - crystals as a currency (earned per block + island missions; spent on enchants, crystal sets,
    rolls, island shop) with a balance table;
  - the pacing curve the owner asked for (#61): fast start, harder each tier, ups (tier unlock,
    Forge, stack steps, Coinmaster) and downs (new ore is slow until the next tool is forged);
  - archetypes of 1 h (casual), 3 h (regular) and 8 h (grinder) per day.
"""

from __future__ import annotations

import argparse
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------------------
# 1. Parameters (every number in the doc comes from here)
# ---------------------------------------------------------------------------

START_MONEY = 1_000
SEASON_DAYS = 90
PAYOUTS_PER_DAY = 8  # interest every 3 h

# The Depths: 12 mine tiers (reference wing), forest 11, combat 10.
MINE_TIERS = ["Coal", "Copper", "Iron", "Gold", "Redstone", "Lapis", "Diamond",
              "Emerald", "Amethyst", "Quartz", "Netherite", "Voidstone"]
FOREST_TIERS = ["Oak", "Birch", "Spruce", "Jungle", "Acacia", "Dark Oak",
                "Mangrove", "Cherry", "Pale Oak", "Crimson", "Warped"]
COMBAT_TIERS = ["Graveyard", "Bone Crypt", "Spider Den", "Slime Bog",
                "Sunken Ruins", "Witch Hollow", "Pillager Outpost",
                "Blaze Forge", "Wither Keep", "Warlord's Citadel"]
TIER_XP = [1, 1.5, 2.5, 4, 6, 9, 13, 19, 28, 40, 58, 85]          # WORLD.md §4.2, tools.yml xp.tier-xp
UNLOCK_LEVEL = [0, 5, 10, 15, 20, 25, 30, 35, 42, 50, 58, 66]      # WORLD.md §4.2
COMBAT_UNLOCK = [0, 6, 12, 18, 24, 30, 38, 46, 55, 66]             # owner #61 (round 2): yes

# Sell price of one Depths drop (mine reference):
#   price(t) = P1 * PMA^(min(t, 8) - 1) * PMB^max(0, t - 8)
# Vanilla tiers 1-8 grow a bit slower than XP (x1.35 vs x1.45); custom tiers 9-12 are the big-money endgame.
# BAL2: x2.4 -> x2.2 because the real sell bonuses (sets, upgrade, mastery, boosters) now sit on top late in the season.
P1 = 20
PMA = 1.35
PMB = 2.2

# Blocks per second on the player's own tier with tool tier t, before uptime:
#   bps = min(REACH_CAP, BPS1 * TOOL_SPEED^(t-1) * (1 + LVL_SPEED*level) * (1 + AREA_PER_SLOT*slots))
BPS1 = 1.0
TOOL_SPEED = 1.05
LVL_SPEED = 0.01
AREA_PER_SLOT = 0.12
REACH_CAP = 6.0          # ~60 ore blocks in reach / 10 s respawn
RESPAWN_S = 10
FIELD = 40 * 40          # ore field size
FORTUNE_PER_TIER = 0.05  # drops per block = 1 + 0.05*(tool tier - 1)
DEPTHS_TIME = 0.70       # share of active time spent breaking in The Depths
DYN_DEPTHS = 0.85        # average realised dynamic price factor (Depths items)
DYN_ISLAND = 0.90        # island products are spread over more item types

# Pacing (#61): a new tier's ore is harder, so with the old tool it breaks at this speed until the next
# tool is forged (money + materials of the previous tier). Players mine whatever pays more per hour.
NEW_ORE_SPEED = 0.70

# ---- Sell bonus family (owner #46: everything adds up, then +300% cap; O11: boosters count) ----
SELL_CAP = 3.00                 # economy.yml -> sell.multiplier-cap-percent: 300
SELL_RANK_MAX = 0.15            # best store rank (proposal; ranks are not planned yet)
SELL_UPGRADE_PER_LEVEL = 0.05   # Sell Multiplier island upgrade, 12 levels -> +60%
SELL_MASTERY_PER_LEVEL = 0.02   # Market Contacts mastery (MA5)
SELL_MASTERY_MAX = 20           # -> +40%
MASTERY_SELL_SHARE = 0.25       # share of a player's mastery levels that go into Market Contacts
SELL_SET_MAX = 0.50             # Coinmaster Silks at set level 50 (WARDROBE §2.3: +20% 2-piece, +30% 4-piece)
SELL_GILDED_PER_LEVEL = 0.03    # Gilded Touch I-V: 3-15% chance a drop sells for 2x (ENCHANTS §6.1)
SELL_PERSONAL_BOOST = 0.50      # personal sell booster (crates, AFK pool, vote rewards, island shop)
SELL_GLOBAL_BOOST = 0.50        # global booster (store purchase, /thanks)
GLOBAL_BOOST_UPTIME = 0.15      # share of the day a global booster runs (proposal)
SELL_EVENT = 0.25               # sell event (weekend, KOTH reward)
EVENT_UPTIME = 0.05

# Coinmaster Silks (WARDROBE §2.4 + §3): money set, gate Depths tier 8, levels from items sold.
COINMASTER_PRICE = 40_000_000
COINMASTER_GATE = 8
SET_XP_BASE = 200               # wardrobe.yml -> levels.base-xp
SET_XP_G = 1.10                 # wardrobe.yml -> levels.growth
SET_MAX = 50
SET_XP_CAP_H = 8_000            # Coinmaster: max set XP per played hour
ISLAND_ITEM_SET_XP = 0.5        # island items sold give half XP
SAVE_DAYS = 3                   # players save for the set only if it costs at most this many days of income

# Forest and combat parity: same money per hour as the mine at the same tier.
LOGS_PER_BLOCK_TIME = 1.0    # a log takes as long as an ore block
KILLS_PER_HOUR_BASE = 700    # shared mobs, ~4-5 s per kill incl. walking

# Skill XP for level L -> L+1 (skills.yml curve.*):
#   x(L) = SK_A * SK_G1^min(L, SK_K) * SK_G2^clamp(L - SK_K, 0, SK_K2 - SK_K) * SK_G3^max(0, L - SK_K2)
SK_A = 600
SK_G1 = 1.20
SK_K = 35
SK_G2 = 1.13
SK_K2 = 66
SK_G3 = 1.02
SKILL_MAX = 150
SKILL_LATE_MARKS = [75, 100, 125, 150]   # levels after the last tier unlock shown in the skill table

# Tool XP to reach level L (1..50): XT(L) = TL_A * (TL_G^(L-1) - 1) / (TL_G - 1)   (tools.yml curve.*)
TL_A = 400
TL_G = 1.21
TOOL_MAX_LEVEL = 50
SLOT_LEVELS = [5, 15, 30, 45]   # + 1 slot from prestige at level 50

# Forge: money cost of tool tier t: F(t) = F1 * FG^(t-2) for t >= 2 (tier 1 = starter kit, free)
F1 = 15_000
FG = 2.35
MAT1 = 256      # materials: MAT1 * MATG^(t-2) drops of the previous tier
MATG = 1.35

# Reference income per played hour (all sources) of a typical player at Depths tier t.
# Upgrade prices are written as "hours of REF", so they follow income automatically:
#   REF(t) = REF1 * REFG^(t-1) * P(t)
REF1 = 2_500
REFG = 1.30

# Island production (generators, crops, harvest yield, mob loot): items per production hour Q(PL) = Q0 * QG^PL,
# each worth ISL_PRICE x P(tier-1). BAL2: spawners are modelled on their own (below), so Q0 went 170 -> 120.
Q0 = 120
QG = 1.12
ISL_PRICE = 0.5
PL_PER_TIER = 6          # tasks gate production levels: max PL = 6 x Depths tier
# Production level k costs C_p(k) = CP1 * CPG^(k-1); players only buy it while it pays back
# within PAYBACK_DAYS of their production hours (nobody buys a bad deal).
CP1 = 2_500
CPG = 1.30
PAYBACK_DAYS = 3.5

# ---- Spawners (STACK + owner S20/SK3-SK7) ----
# Stack limit steps of the Spawner Stack Limit upgrade: (upgrade level, spawners per block). 8 -> 512 in 5 steps.
STACK_STEPS = [(0, 8), (2, 32), (4, 64), (6, 128), (9, 256), (12, 512)]
SP_BLOCKS = 4             # spawner blocks a typical island fills (no block limit; this is the model's habit)
SPAWNER_GATE = [1, 3, 5, 7, 9]   # Depths tier at which spawner tier s is in reach (shop, Depths drops, crates, Black Market)
SP_LOOT = 1.5             # sellable items per mob (vanilla loot, averaged)
SP_MOBS_PER_SPAWN = 1.0   # stacking.yml -> spawners.mobs-per-spawner (BAL2 recommends 1; config today: 4)
SP_INTERVAL_MIN, SP_INTERVAL_MAX = 10, 40   # stacking.yml -> spawners.min/max-interval-seconds
# Shop price of the next spawner of tier s = SP_PRICE[s] * SP_G^(spawners of that tier the island already owns).
# SP_PRICE[s] = SP_PRICE_H production hours of that spawner's own output (at the recommended rate, no multipliers).
SP_PRICE_H = 60
SP_G = 1.10
SP_DROP_PER_H = 0.10      # spawners found per played hour (Depths drops, crates), best tier in reach

# Island level upgrades (no income, give island level points, REVIEW A1): 12 upgrades x 12 levels,
# level n needs Depths tier >= n (stand-in for its tasks). Price of level n of upgrade u:
#   C_u(u, n) = W_u * LV_H * LV_HG^(n-1) * REF(n)
LV_H = 0.067
LV_HG = 1.49
LV_MAX = 12
LEVEL_UPGRADES = [("Warp Slots", 0.5), ("Island Vault", 0.6), ("Member Slots", 0.8), ("Fuel Tank", 0.8),
                  ("XP Boost", 0.9), ("Lumberjack", 0.9), ("Spawner Stack Limit", 1.0), ("Island Size", 1.0),
                  ("Bank Capacity", 1.1), ("Bank Interest", 1.2), ("Mine Fortune", 1.4), ("Sell Multiplier", 1.8)]
UP_STACK = 6
UP_BANK_INTEREST = 9
UP_SELL = 11

# Late projects (Phase 5+): 3 outposts, each = unlock + 3 biomes x 5 tiers + Fusion I/II = 18 steps.
LATE_GATE = [7, 9, 11]
LATE_H = 1.0
LATE_G = 1.22

# Mastery: after level 12 an upgrade can be mastered endlessly (owner O4-O6, MA1-MA12);
# mastery level m (1..) costs MASTERY_H * MASTERY_G^(m-1) * REF(tier).
MASTERY = True
MASTERY_H = 4.0
MASTERY_G = 1.12

# Quests / events / KOTH / envoys / crates pay EVENT_SHARE x the player's Depths income per active hour
EVENT_SHARE = 0.30

# Player-driven sinks
SHOP_SHARE = 0.04        # share of earned money spent in /shop (blocks, decor)
AH_VOLUME = 0.10         # share of earned money that changes hands on the AH
AH_FEE = 0.01            # auction.yml fee.percent
AH_TAX = 0.05            # auction.yml tax.percent

# Banks
PERSONAL_RATE = 0.0015
ISLAND_RATE = 0.0025
EARLY_FEE = 0.02
PB_CAP1, PB_CAPG = 100_000, 5.0
PB_COST_SHARE = 0.02
PB_PAYOUT1, PB_PAYOUTG = 150, 3.2
IB_CAP0, IB_CAPG = 1_000_000, 4.0
IB_PAYOUT0, IB_PAYOUTG = 2_500, 3.0

# Player archetypes (BAL2: 1 h / 3 h / 8 h per day). save = share of income banked and never spent;
# bank_share = share of spare cash also parked in the bank; boost = share of time a personal sell booster runs.
ARCHETYPES = {
    "casual":  dict(hours_day=1.0, first_day=1.5, uptime=0.60, prod_hours=8, rank=0.00, save=0.20, bank_share=0.5,
                    boost=0.05),
    "regular": dict(hours_day=3.0, first_day=4.0, uptime=0.75, prod_hours=12, rank=0.04, save=0.15, bank_share=0.7,
                    boost=0.15),
    "grinder": dict(hours_day=8.0, first_day=10.0, uptime=0.85, prod_hours=20, rank=0.08, save=0.10, bank_share=0.9,
                    boost=0.30),
}
KINDS = ["casual", "regular", "grinder"]
LABEL = {"casual": "Casual (1 h/day)", "regular": "Regular (3 h/day)", "grinder": "Grinder (8 h/day)"}
# Daily unique players behind a 300 peak (avg concurrency ~112, peak ~2.5x)
POPULATION = {"casual": 540, "regular": 270, "grinder": 90}

STAGES = [("First hour", None), ("Day 1", 1), ("Day 3", 3), ("Week 1", 7),
          ("Week 2", 14), ("Month 1", 30), ("Month 2", 60), ("Month 3", 90)]

# Phase-3 targets for the most active players (cumulative earned), to confirm with the owner
TARGETS_TOP = {"First hour": 50_000, "Day 3": 5_000_000, "Week 2": 500_000_000, "Month 2": 50_000_000_000}

STEP_H = 0.1  # simulation step in active hours

# ---- Crystals (replaced tokens, O8 + CR1-CR4) ----
TK1 = 0.05              # crystals per block/log/kill-equivalent on tier 1 (rolled as a chance)
TKG = 1.30
CRY_MISSIONS = 0.20     # island missions (CR1) pay this share on top of the Depths crystals
EN1 = 100               # enchant step s costs EN1 * ENG^(s-1) crystals x rarity factor (ENCHANTS §5)
ENG = 1.80
EN_MAX = 10
RARE_V_CUM = [0, 100, 424, 2_314, 8_436, 28_272]   # Rare max-V enchant, crystals to level 0..5 (ENCHANTS §5)
GEAR_MAX = 6.6          # ~33 enchants across 5 tools + armor = 6.6 x the pickaxe's 5 slots
GEAR_PER_TIER = 0.6     # enchantable gear grows with the Depths tier (new tools, sets)
GILDED_SHARE = 0.25     # money players put a quarter of their enchant crystals into Gilded Touch
CRYSTAL_SET_PRICE = 25_000   # Deepforge Plate (WARDROBE §2.4), gate tier 6
CRYSTAL_SET_GATE = 6
ROLLS = [(4, 4_000, 5), (7, 25_000, 3)]   # (gate tier, crystals, max per day): Adept, Master roll
ROLL_SHARE = 0.5        # share of the crystals left after enchants/sets a player spends on rolls
CRY_SHOP_SHARE = 0.4    # share of what is left at the end of the day spent in the island shop (fuel, boosters, decor)

# Dynamic prices (M3.4)
RECOVERY_PER_10MIN = 0.02
TARGET_DEPRESSION = 0.15

# Money milestones shown in the time-to-milestone table
MONEY_MARKS = [1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11]


# ---------------------------------------------------------------------------
# 2. Formulas
# ---------------------------------------------------------------------------

def price(t: int) -> float:
    return P1 * PMA ** (min(t, 8) - 1) * PMB ** max(0, t - 8)


def skill_xp_step(level: int) -> float:
    return (SK_A * SK_G1 ** min(level, SK_K) * SK_G2 ** min(max(0, level - SK_K), SK_K2 - SK_K)
            * SK_G3 ** max(0, level - SK_K2))


_SKILL_CUM: list = []


def skill_xp_to(level: int) -> float:
    """Total skill XP needed to reach `level` (sum of steps 0..level-1)."""
    key = (SK_A, SK_G1, SK_K, SK_G2, SK_K2, SK_G3)
    if not _SKILL_CUM or _SKILL_CUM[0] != key:
        _SKILL_CUM.clear()
        cum, acc = [0.0], 0.0
        for lv in range(200):
            acc += skill_xp_step(lv)
            cum.append(acc)
        _SKILL_CUM.extend([key, cum])
    return _SKILL_CUM[1][level]


def tool_xp_to(level: int) -> float:
    if level <= 1:
        return 0.0
    return TL_A * (TL_G ** (level - 1) - 1) / (TL_G - 1)


def set_xp_to(level: int) -> float:
    return SET_XP_BASE * (SET_XP_G ** level - 1) / (SET_XP_G - 1)


def set_level_for_xp(xp: float) -> int:
    lvl = 0
    while lvl < SET_MAX and set_xp_to(lvl + 1) <= xp:
        lvl += 1
    return lvl


def slots_for(level: int, prestiged: bool) -> int:
    return sum(1 for s in SLOT_LEVELS if level >= s) + (1 if prestiged else 0)


def bps(tool: int, level: int, slots: int) -> float:
    raw = BPS1 * TOOL_SPEED ** (tool - 1) * (1 + LVL_SPEED * level) * (1 + AREA_PER_SLOT * slots)
    return min(REACH_CAP, raw)


def fortune(tool: int) -> float:
    return 1 + FORTUNE_PER_TIER * (tool - 1)


def capped(bonus: float) -> float:
    """MultiplierCap: the summed bonus, capped once (never below -100%)."""
    return 1 + max(-1.0, min(SELL_CAP, bonus))


def sell_mult_expected(base: float, boost_uptime: float) -> float:
    """Average sell multiplier over the day: the cap is applied in every booster/event state, then averaged."""
    m = 0.0
    for p_on, pb in ((boost_uptime, SELL_PERSONAL_BOOST), (1 - boost_uptime, 0.0)):
        for g_on, gb in ((GLOBAL_BOOST_UPTIME, SELL_GLOBAL_BOOST), (1 - GLOBAL_BOOST_UPTIME, 0.0)):
            for e_on, eb in ((EVENT_UPTIME, SELL_EVENT), (1 - EVENT_UPTIME, 0.0)):
                m += p_on * g_on * e_on * capped(base + pb + gb + eb)
    return m


def best_of_everything() -> dict:
    return {"Best rank": SELL_RANK_MAX, "Sell Multiplier upgrade Lv 12": SELL_UPGRADE_PER_LEVEL * LV_MAX,
            f"Market Contacts mastery Lv {SELL_MASTERY_MAX}": SELL_MASTERY_PER_LEVEL * SELL_MASTERY_MAX,
            "Coinmaster set Lv 50 (merged or pure)": SELL_SET_MAX, "Gilded Touch V": SELL_GILDED_PER_LEVEL * 5,
            "Personal booster": SELL_PERSONAL_BOOST, "Global booster": SELL_GLOBAL_BOOST, "Sell event": SELL_EVENT}


def forge_cost(t: int) -> float:
    return 0 if t <= 1 else F1 * FG ** (t - 2)


def forge_mats(t: int) -> float:
    return 0 if t <= 1 else MAT1 * MATG ** (t - 2)


def q_items(pl: int) -> float:
    return Q0 * QG ** pl


def prod_cost(k: int) -> float:
    return CP1 * CPG ** (k - 1)


def ref(t: int) -> float:
    return REF1 * REFG ** (t - 1) * price(t)


def level_cost(u: int, n: int) -> float:
    """Level n (1..LV_MAX) of level upgrade u (index into LEVEL_UPGRADES)."""
    return LEVEL_UPGRADES[u][1] * LV_H * LV_HG ** (n - 1) * ref(n)


def late_cost(o: int, j: int) -> float:
    return LATE_H * LATE_G ** j * ref(LATE_GATE[o])


def mastery_cost(m: int, tier: int) -> float:
    return MASTERY_H * MASTERY_G ** (m - 1) * ref(tier)


def stack_limit(upgrade_level: int) -> int:
    lim = STACK_STEPS[0][1]
    for need, value in STACK_STEPS:
        if upgrade_level >= need:
            lim = value
    return lim


def sp_mobs_per_hour(per_spawn: float = None) -> float:
    per = SP_MOBS_PER_SPAWN if per_spawn is None else per_spawn
    return per * 3600 / ((SP_INTERVAL_MIN + SP_INTERVAL_MAX) / 2)


def sp_value(s: int) -> float:
    """Sell value of one spawner mob's loot, spawner tier s (1..5)."""
    return ISL_PRICE * price(2 * s - 1) * SP_LOOT


SP_PRICE = [round(SP_PRICE_H * sp_mobs_per_hour() * sp_value(s) * DYN_ISLAND, -3) for s in range(1, 6)]


def sp_price(s: int, owned: int) -> float:
    return SP_PRICE[s - 1] * SP_G ** owned


def pb_cap(k: int) -> float:
    return PB_CAP1 * PB_CAPG ** (k - 1)


def pb_cost(k: int) -> float:
    return 0 if k <= 1 else PB_COST_SHARE * pb_cap(k)


def pb_payout_cap(k: int) -> float:
    return PB_PAYOUT1 * PB_PAYOUTG ** (k - 1)


def ib_cap(level: int) -> float:
    return IB_CAP0 * IB_CAPG ** level


def ib_payout_cap(level: int) -> float:
    return IB_PAYOUT0 * IB_PAYOUTG ** level


def tier_for_level(level: float, unlocks=UNLOCK_LEVEL) -> int:
    t = 1
    for i, need in enumerate(unlocks):
        if level >= need:
            t = i + 1
    return t


def level_for_xp(xp: float) -> int:
    lvl = 0
    while lvl < SKILL_MAX and skill_xp_to(lvl + 1) <= xp:
        lvl += 1
    return lvl


def tool_level_for_xp(xp: float) -> int:
    lvl = 1
    while lvl < TOOL_MAX_LEVEL and tool_xp_to(lvl + 1) <= xp:
        lvl += 1
    return lvl


def crystal_rate(t: int) -> float:
    return TK1 * TKG ** (t - 1)


def enchant_allowed_level(tool_level: int) -> int:
    """Highest level of a max-V enchant the tool-level gate allows (ENCHANTS §12.2: I at Lv 5, V at Lv 41)."""
    return 0 if tool_level < 5 else min(5, 1 + (tool_level - 5) // 9)


def gear_factor(tier: int) -> float:
    return min(GEAR_MAX, 1 + GEAR_PER_TIER * (tier - 1))


# ---------------------------------------------------------------------------
# 3. Simulation
# ---------------------------------------------------------------------------

class Player:
    def __init__(self, kind: str):
        self.kind = kind
        self.a = ARCHETYPES[kind]
        self.cash = float(START_MONEY)
        self.savings = 0.0
        self.skill_xp = 0.0
        self.tier = 1
        self.tool = 1
        self.tool_xp = 0.0
        self.tool_level = 1
        self.prestiged = False
        self.pl = 0
        self.ul_levels = [0] * len(LEVEL_UPGRADES)
        self.late = [0] * len(LATE_GATE)
        self.mastery = 0
        self.pbt = 1
        self.ibl = 0
        self.spawners = [0] * 5
        self.sp_found = 0.0
        self.cm_owned = False
        self.cm_xp = 0.0
        self.cm_level = 0
        self.mined = [0.0] * 13      # drops mined per tier (Forge materials)
        self.crystals = 0.0
        self.cry_earned = 0.0
        self.ench_invested = 0.0
        self.crystal_set = False
        self.cry_spent = {"enchants": 0.0, "sets": 0.0, "rolls": 0.0, "shop": 0.0}
        self.active_h = 0.0
        self.earned = {"depths": 0.0, "island": 0.0, "spawners": 0.0, "events": 0.0, "interest": 0.0}
        self.spent = {"forge": 0.0, "production": 0.0, "spawners": 0.0, "sets": 0.0, "levels": 0.0, "late": 0.0,
                      "mastery": 0.0, "bank_tiers": 0.0, "shop": 0.0, "ah_tax": 0.0}
        self.events_log: list = []  # (event, active h, day, tool level)
        self.seen: set = set()
        self.day = 1
        self.today_h = 0.0
        self.max_mult = 1.0

    # rates -----------------------------------------------------------------
    def slots(self) -> int:
        return slots_for(self.tool_level, self.prestiged)

    def blocks_per_hour(self) -> float:
        return bps(self.tool, self.tool_level, self.slots()) * 3600 * self.a["uptime"]

    def mine_choice(self):
        """(tier mined, speed factor): new ore is slow until its tool is forged; mine what pays more."""
        t = self.tier
        if self.tool >= t:
            return t, 1.0
        if price(t) * NEW_ORE_SPEED >= price(t - 1):
            return t, NEW_ORE_SPEED
        return t - 1, 1.0

    def gilded_level(self) -> int:
        if self.tier < 3:
            return 0
        budget = self.ench_invested * GILDED_SHARE
        n = 0
        while n < 5 and RARE_V_CUM[n + 1] <= budget:
            n += 1
        return min(n, enchant_allowed_level(self.tool_level))

    def stack_limit(self) -> int:
        return stack_limit(self.ul_levels[UP_STACK])

    def sell_sources(self) -> dict:
        mc = min(SELL_MASTERY_MAX, int(self.mastery * MASTERY_SELL_SHARE))
        return {"rank": self.a["rank"],
                "upgrade": SELL_UPGRADE_PER_LEVEL * self.ul_levels[UP_SELL],
                "mastery": SELL_MASTERY_PER_LEVEL * mc,
                "set": SELL_SET_MAX * self.cm_level / SET_MAX if self.cm_owned else 0.0,
                "enchant": SELL_GILDED_PER_LEVEL * self.gilded_level()}

    def sell_base(self) -> float:
        return sum(self.sell_sources().values())

    def sell_mult(self) -> float:
        return sell_mult_expected(self.sell_base(), self.a["boost"])

    def depths_rate(self) -> float:
        """Money per active hour from The Depths."""
        t, speed = self.mine_choice()
        return (self.blocks_per_hour() * speed * DEPTHS_TIME * fortune(self.tool) * price(t)
                * DYN_DEPTHS * self.sell_mult())

    def island_tier(self) -> int:
        return max(1, self.tier - 1)

    def island_rate(self) -> float:
        """Money per production hour from generators, crops and mob loot (production levels)."""
        return q_items(self.pl) * ISL_PRICE * price(self.island_tier()) * DYN_ISLAND * self.sell_mult()

    def spawner_rate(self) -> float:
        """Money per production hour from stacked spawners."""
        mobs = sp_mobs_per_hour()
        return sum(n * mobs * sp_value(s + 1) for s, n in enumerate(self.spawners)) * DYN_ISLAND * self.sell_mult()

    def spawner_count(self) -> int:
        return sum(self.spawners)

    def best_spawner_tier(self) -> int:
        s = 1
        for i, gate in enumerate(SPAWNER_GATE):
            if self.tier >= gate:
                s = i + 1
        return s

    def items_per_played_hour(self) -> float:
        t, speed = self.mine_choice()
        depths = self.blocks_per_hour() * speed * DEPTHS_TIME * fortune(self.tool)
        island = (q_items(self.pl) + self.spawner_count() * sp_mobs_per_hour() * SP_LOOT) \
            * self.a["prod_hours"] / max(1e-9, self.a["hours_day"])
        return depths + island * ISLAND_ITEM_SET_XP

    # actions ---------------------------------------------------------------
    def earn(self, src: str, amount: float):
        before = self.total_earned()
        self.earned[src] += amount
        net = amount * (1 - SHOP_SHARE - AH_VOLUME * (AH_FEE + AH_TAX))
        self.savings += net * self.a["save"]
        self.cash += net * (1 - self.a["save"])
        self.spent["shop"] += amount * SHOP_SHARE
        self.spent["ah_tax"] += amount * AH_VOLUME * (AH_FEE + AH_TAX)
        after = self.total_earned()
        for m in MONEY_MARKS:
            if before < m <= after:
                self.mark(f"earn{m:.0e}")

    def mark(self, name: str):
        if name not in self.seen:
            self.seen.add(name)
            self.events_log.append((name, self.active_h, self.day, self.tool_level))

    @property
    def ul(self) -> int:
        return sum(self.ul_levels)

    def prod_gain_per_day(self) -> float:
        nxt = (q_items(self.pl + 1) - q_items(self.pl)) * ISL_PRICE * price(self.island_tier()) * DYN_ISLAND \
            * self.sell_mult()
        return nxt * self.a["prod_hours"]

    def pay(self, what: str, cost: float) -> bool:
        if self.cash < cost:
            return False
        self.cash -= cost
        self.spent[what] += cost
        return True

    def buy(self):
        while True:
            # 1. Forge the next tool as soon as its tier is unlocked (money + materials of the previous tier)
            if (self.tool < self.tier and self.mined[self.tool] >= forge_mats(self.tool + 1)
                    and self.pay("forge", forge_cost(self.tool + 1))):
                self.mined[self.tool] -= forge_mats(self.tool + 1)
                self.tool += 1
                self.mark(f"tool{self.tool}")
                continue
            # 2. Coinmaster Silks once the gate is reached (the sell set)
            if not self.cm_owned and self.tier >= COINMASTER_GATE and self.pay("sets", COINMASTER_PRICE):
                self.cm_owned = True
                self.mark("coinmaster")
                continue
            # 3. Production while it pays back within PAYBACK_DAYS
            if (self.pl < PL_PER_TIER * self.tier
                    and prod_cost(self.pl + 1) <= self.prod_gain_per_day() * PAYBACK_DAYS
                    and self.pay("production", prod_cost(self.pl + 1))):
                self.pl += 1
                continue
            # 4. Spawners (best tier in reach) while they pay back and fit in the stacks
            s = self.best_spawner_tier()
            owned = self.spawners[s - 1]
            if self.spawner_count() < self.stack_limit() * SP_BLOCKS:
                gain = sp_mobs_per_hour() * sp_value(s) * DYN_ISLAND * self.sell_mult() * self.a["prod_hours"]
                if sp_price(s, owned) <= gain * PAYBACK_DAYS and self.pay("spawners", sp_price(s, owned)):
                    self.spawners[s - 1] += 1
                    continue
            # Saving for Coinmaster (if it is within SAVE_DAYS of income): nothing below this line until it is bought
            if (not self.cm_owned and self.tier >= COINMASTER_GATE
                    and COINMASTER_PRICE <= SAVE_DAYS * self.hourly() * self.a["hours_day"]):
                break
            # 5. Cheapest available island level upgrade or late project
            best = None
            for u, n in enumerate(self.ul_levels):
                if n < LV_MAX and n + 1 <= self.tier:
                    c = level_cost(u, n + 1)
                    if best is None or c < best[0]:
                        best = (c, "levels", u)
            for o, j in enumerate(self.late):
                if j < 18 and self.tier >= LATE_GATE[o]:
                    c = late_cost(o, j)
                    if best is None or c < best[0]:
                        best = (c, "late", o)
            if best is not None and self.pay(best[1], best[0]):
                if best[1] == "levels":
                    self.ul_levels[best[2]] += 1
                    if best[2] == UP_STACK:
                        self.mark(f"stack{self.stack_limit()}")
                else:
                    self.late[best[2]] += 1
                continue
            # 6. Personal bank tier (keeps 2x the price as a buffer)
            if self.pbt < 10 and self.cash >= 2 * pb_cost(self.pbt + 1):
                self.pay("bank_tiers", pb_cost(self.pbt + 1))
                self.pbt += 1
                continue
            # 7. Mastery once every level upgrade is maxed
            if (MASTERY and best is None and all(n == LV_MAX for n in self.ul_levels)
                    and self.pay("mastery", mastery_cost(self.mastery + 1, self.tier))):
                self.mastery += 1
                continue
            break

    def spend_crystals(self, end_of_day: bool):
        if not self.crystal_set and self.tier >= CRYSTAL_SET_GATE and self.crystals >= CRYSTAL_SET_PRICE:
            self.crystals -= CRYSTAL_SET_PRICE
            self.cry_spent["sets"] += CRYSTAL_SET_PRICE
            self.crystal_set = True
            self.mark("crystal_set")
        # Saving for the crystal set once its gate is reached, then enchants up to what gear and tool level allow
        reserve = CRYSTAL_SET_PRICE if (not self.crystal_set and self.tier >= CRYSTAL_SET_GATE) else 0.0
        demand = self.slots() * gear_factor(self.tier) * RARE_V_CUM[enchant_allowed_level(self.tool_level)]
        e = min(max(0.0, self.crystals - reserve), max(0.0, demand - self.ench_invested))
        self.crystals -= e
        self.ench_invested += e
        self.cry_spent["enchants"] += e
        if self.ench_invested >= RARE_V_CUM[5] and self.tier >= 3:
            self.mark("enchant_max")
        if end_of_day and not reserve:
            # Rolls (daily limits) from part of what is left, then the island shop
            budget = self.crystals * ROLL_SHARE
            for gate, cost, per_day in ROLLS:
                if self.tier >= gate:
                    n = min(per_day, int(budget // cost))
                    self.crystals -= n * cost
                    budget -= n * cost
                    self.cry_spent["rolls"] += n * cost
            shop = self.crystals * CRY_SHOP_SHARE
            self.crystals -= shop
            self.cry_spent["shop"] += shop

    def step_active(self, h: float):
        t, speed = self.mine_choice()
        bph = self.blocks_per_hour() * speed
        blocks = bph * DEPTHS_TIME * h
        xp = blocks * TIER_XP[t - 1]
        self.mined[t] += blocks * fortune(self.tool)
        self.skill_xp += xp
        self.tool_xp += xp
        old_level = level_for_xp(self.skill_xp - xp)
        new_level = level_for_xp(self.skill_xp)
        for s in SKILL_LATE_MARKS:
            if old_level < s <= new_level:
                self.mark(f"skill{s}")
        new_tier = tier_for_level(new_level)
        if new_tier > self.tier:
            self.tier = new_tier
            self.mark(f"tier{self.tier}")
        lvl = tool_level_for_xp(self.tool_xp)
        if lvl > self.tool_level:
            for s in SLOT_LEVELS + [TOOL_MAX_LEVEL]:
                if self.tool_level < s <= lvl:
                    self.mark(f"toollvl{s}")
            self.tool_level = lvl
        # Coinmaster set XP from items sold
        if self.cm_owned:
            self.cm_xp += min(SET_XP_CAP_H, self.items_per_played_hour()) * h
            new_cm = set_level_for_xp(self.cm_xp)
            for mark in (25, 50):
                if self.cm_level < mark <= new_cm:
                    self.mark(f"cm{mark}")
            self.cm_level = new_cm
        # Crystals: Depths blocks + island missions
        c = blocks * crystal_rate(t) * (1 + CRY_MISSIONS)
        self.crystals += c
        self.cry_earned += c
        self.spend_crystals(False)
        self.sp_found += SP_DROP_PER_H * h
        while self.sp_found >= 1 and self.spawner_count() < self.stack_limit() * SP_BLOCKS:
            self.sp_found -= 1
            self.spawners[self.best_spawner_tier() - 1] += 1
        d = self.depths_rate() * h
        self.earn("depths", d)
        self.earn("events", d * EVENT_SHARE)
        self.earn("island", self.island_rate() * h)
        self.earn("spawners", self.spawner_rate() * h)
        self.active_h += h
        self.today_h += h
        base = self.sell_base()
        for mark in (1.0, 2.0):
            if base >= mark:
                self.mark(f"sell{int(mark * 100)}")
        self.max_mult = max(self.max_mult, capped(base + SELL_PERSONAL_BOOST + SELL_GLOBAL_BOOST + SELL_EVENT))
        self.buy()

    def end_of_day(self, active_today: float):
        offline_prod = max(0.0, self.a["prod_hours"] - active_today)
        self.earn("island", self.island_rate() * offline_prod)
        self.earn("spawners", self.spawner_rate() * offline_prod)
        self.buy()
        self.spend_crystals(True)
        banked = self.savings + self.cash * self.a["bank_share"]
        pb = min(banked, pb_cap(self.pbt))
        ib_level = min(10, self.ul_levels[UP_BANK_INTEREST])
        ib = min(banked - pb, ib_cap(ib_level))
        per_payout = (min(pb * PERSONAL_RATE, pb_payout_cap(self.pbt))
                      + min(ib * ISLAND_RATE, ib_payout_cap(ib_level)))
        self.earned["interest"] += per_payout * PAYOUTS_PER_DAY
        self.savings += per_payout * PAYOUTS_PER_DAY
        self.ibl = ib_level
        self.buy()

    def total_earned(self) -> float:
        return sum(self.earned.values())

    def total_spent(self) -> float:
        return sum(self.spent.values())

    def hourly(self) -> float:
        """Income per played hour: Depths + events, plus the day's island and spawner production spread over it."""
        return (self.depths_rate() * (1 + EVENT_SHARE)
                + (self.island_rate() + self.spawner_rate()) * self.a["prod_hours"] / self.a["hours_day"])

    def snapshot(self) -> dict:
        return dict(active_h=self.active_h, tier=self.tier, tool=self.tool, tool_level=self.tool_level,
                    slots=self.slots(), pl=self.pl, ul=self.ul, late=sum(self.late), mastery=self.mastery,
                    pbt=self.pbt, ibl=self.ibl, spawners=self.spawner_count(), stack=self.stack_limit(),
                    best_sp=self.best_spawner_tier(), cm=self.cm_level if self.cm_owned else None,
                    gilded=self.gilded_level(), sources=self.sell_sources(), sell_base=self.sell_base(),
                    sell_mult=self.sell_mult(),
                    cash=self.cash + self.savings, earned=dict(self.earned), spent=dict(self.spent),
                    total_earned=self.total_earned(), total_spent=self.total_spent(),
                    depths_rate=self.depths_rate(), island_rate=self.island_rate(),
                    spawner_rate=self.spawner_rate(), bph=self.blocks_per_hour(),
                    mined_tier=self.mine_choice()[0],
                    crystals=self.crystals, cry_earned=self.cry_earned, cry_spent=dict(self.cry_spent),
                    cry_rate=self.blocks_per_hour() * self.mine_choice()[1] * DEPTHS_TIME
                    * crystal_rate(self.mine_choice()[0]) * (1 + CRY_MISSIONS),
                    hourly=self.hourly())


def simulate(kind: str):
    p = Player(kind)
    snaps = {}
    daily = []
    for day in range(1, SEASON_DAYS + 1):
        p.day = day
        p.today_h = 0.0
        before_e = dict(p.earned)
        before_s = dict(p.spent)
        hours = p.a["first_day"] if day == 1 else p.a["hours_day"]
        done = 0.0
        while done < hours - 1e-9:
            h = min(STEP_H, hours - done)
            p.step_active(h)
            done += h
            if "First hour" not in snaps and p.active_h >= 1 - 1e-9:
                snaps["First hour"] = p.snapshot()
        p.end_of_day(hours)
        daily.append(dict(
            earned={k: p.earned[k] - before_e[k] for k in p.earned},
            spent={k: p.spent[k] - before_s[k] for k in p.spent},
            cash=p.cash + p.savings, hourly=p.hourly(), tier=p.tier, tool=p.tool))
        for name, d in STAGES:
            if d == day:
                snaps[name] = p.snapshot()
    return p, snaps, daily


_CACHE: dict = {}


def sim(kind: str):
    if kind not in _CACHE:
        _CACHE[kind] = simulate(kind)
    return _CACHE[kind]


# ---------------------------------------------------------------------------
# 4. Formatting
# ---------------------------------------------------------------------------

def money(v: float) -> str:
    v = float(v)
    neg = v < 0
    v = abs(v)
    for div, suf in ((1e12, "T"), (1e9, "B"), (1e6, "M"), (1e3, "k")):
        if v >= div:
            s = f"{v / div:.1f}".rstrip("0").rstrip(".")
            out = f"${s}{suf}"
            break
    else:
        out = f"${v:,.0f}"
    return ("-" if neg else "") + out


def num(v: float, nd: int = 0) -> str:
    if abs(v) >= 1e6:
        return money(v).replace("$", "")
    return f"{v:,.{nd}f}"


def pct(v: float, nd: int = 0) -> str:
    return f"{v * 100:.{nd}f}%"


def hours_label(h: float) -> str:
    if h < 1:
        return f"{h * 60:.0f} min"
    return f"{h:.1f} h"


def table(headers, rows) -> str:
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    for r in rows:
        out.append("| " + " | ".join(str(c) for c in r) + " |")
    return "\n".join(out)


TABLES: dict = {}


def register(name):
    def deco(fn):
        TABLES[name] = fn
        return fn
    return deco


# ---------------------------------------------------------------------------
# 5. Tables
# ---------------------------------------------------------------------------

def event_at(kind: str, name: str):
    p, _, _ = sim(kind)
    for ev in p.events_log:
        if ev[0] == name:
            return ev
    return None


def when(kind: str, name: str) -> str:
    ev = event_at(kind, name)
    if ev is None:
        return "not this season"
    return f"{hours_label(ev[1])} (day {ev[2]})"


@register("params")
def t_params():
    rows = [
        ("Start money", money(START_MONEY), "decided"),
        ("Mine drop price", f"P(t) = {P1} x {PMA}^(min(t,8)-1) x {PMB}^max(0,t-8)", "tiers 1-8 / 9-12"),
        ("Reference income", f"REF(t) = {REF1:,} x {REFG}^(t-1) x P(t)", "per played hour, all sources; prices are 'hours of REF'"),
        ("Break rate", f"bps = min({REACH_CAP}, {BPS1} x {TOOL_SPEED}^(tool-1) x (1+{LVL_SPEED} x level) x (1+{AREA_PER_SLOT} x slots))", "blocks/s"),
        ("New ore before its tool", f"break speed x {NEW_ORE_SPEED}", "until the next tool is forged (#61 'down')"),
        ("Fortune", f"drops/block = 1 + {FORTUNE_PER_TIER} x (tool-1)", ""),
        ("Depths time", pct(DEPTHS_TIME), "share of a played hour spent breaking"),
        ("Dynamic price (Depths / island)", f"{DYN_DEPTHS} / {DYN_ISLAND}", "average realised multiplier"),
        ("Sell multiplier", f"1 + min({SELL_CAP:g}, sum of all sell bonuses)", "§2.7; cap applied in every booster state"),
        ("Skill XP per level", f"x(L) = {SK_A} x {SK_G1}^min(L,{SK_K}) x {SK_G2}^clamp(L-{SK_K},0,{SK_K2 - SK_K}) x {SK_G3}^max(0,L-{SK_K2})",
         f"total = sum of steps; max level {SKILL_MAX}"),
        ("Tool XP to level L", f"XT(L) = {TL_A} x ({TL_G}^(L-1) - 1) / ({TL_G} - 1)", "XP per block = tier XP"),
        ("Forge money", f"F(t) = {money(F1)} x {FG}^(t-2), t >= 2", "tier 1 = free starter kit"),
        ("Forge materials", f"M(t) = {MAT1} x {MATG}^(t-2) drops of tier t-1", "checked in the model"),
        ("Island output", f"Q(PL) = {Q0} x {QG}^PL items per production hour", f"each worth {ISL_PRICE} x P(tier-1); spawners separate"),
        ("Production level", f"C_p(k) = {money(CP1)} x {CPG}^(k-1)", f"max {PL_PER_TIER} x tier; bought if payback <= {PAYBACK_DAYS:g} days"),
        ("Spawner output", f"{SP_MOBS_PER_SPAWN:g} mob per spawner every {SP_INTERVAL_MIN}-{SP_INTERVAL_MAX} s = {sp_mobs_per_hour():.0f} mobs/h",
         f"each mob {SP_LOOT} items worth {ISL_PRICE} x P(2s-1)"),
        ("Spawner price", f"base(s) x {SP_G}^(spawners of that tier on the island)",
         f"base = {SP_PRICE_H} production h of its own output: {' / '.join(money(x) for x in SP_PRICE)}"),
        ("Spawners found", f"{SP_DROP_PER_H:g} per played hour", "Depths drops + crates, best tier in reach"),
        ("Spawner stack limit", " -> ".join(str(v) for _, v in STACK_STEPS), "per block, by Spawner Stack Limit level"),
        ("Level upgrade", f"C_u(u,n) = W_u x {LV_H} x {LV_HG}^(n-1) x REF(n)", f"12 upgrades x {LV_MAX} levels, level n needs tier n"),
        ("Late projects", f"C_late(o,j) = {LATE_H} x {LATE_G}^j x REF(gate_o)", f"3 outposts x 18 steps, gates tier {'/'.join(map(str, LATE_GATE))}"),
        ("Mastery", f"C_m(m) = {MASTERY_H} x {MASTERY_G}^(m-1) x REF(tier)", "endless, after all levels are maxed"),
        ("Events/quests/crates", f"{EVENT_SHARE} x Depths income per played hour", "rewards defined per tier"),
        ("Shop spend", pct(SHOP_SHARE) + " of earned", "blocks, decor, seeds"),
        ("AH", f"{pct(AH_VOLUME)} of earned traded, fee {pct(AH_FEE)} + tax {pct(AH_TAX)}", "only fee+tax leave the economy"),
        ("Interest", f"personal {PERSONAL_RATE * 100:.2f}% / island {ISLAND_RATE * 100:.2f}% per 3 h", "capped per payout"),
        ("Crystals", f"{TK1} x {TKG}^(t-1) per block, +{pct(CRY_MISSIONS)} island missions", "not tradeable (CR3)"),
        ("Daily uniques casual / regular / grinder", " / ".join(str(POPULATION[k]) for k in KINDS), "avg ~112 online, peak ~300"),
    ]
    return table(["Parameter", "Formula / value", "Note"], rows)


@register("archetypes")
def t_archetypes():
    rows = []
    for k in KINDS:
        a = ARCHETYPES[k]
        rows.append([LABEL[k], f"{a['hours_day']:g} (day 1: {a['first_day']:g})", pct(a["uptime"]), a["prod_hours"],
                     pct(a["rank"]), pct(a["boost"]), pct(a["save"]), POPULATION[k]])
    return table(["Archetype", "Played h/day", "Breaking uptime", "Production h/day (online + AFK + fuel)",
                  "Rank sell bonus", "Personal booster uptime", "Saves (never spent)", "Daily players"], rows)


@register("targets")
def t_targets():
    rows = []
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            r.append(f"{money(s['hourly'])}/h · {money(s['total_earned'])}")
        rows.append(r)
    return table(["Stage", "Casual (income per played hour · earned so far)", "Regular", "Grinder"], rows)


@register("targets_check")
def t_targets_check():
    rows = []
    for name, target in TARGETS_TOP.items():
        top = sim("grinder")[1][name]["total_earned"]
        act = sim("regular")[1][name]["total_earned"]
        rows.append([name, money(target), money(top), f"{top / target:.1f}x", money(act), f"{act / target:.1f}x"])
    return table(["Stage", "Owner target (most active, earned so far)", "Model grinder", "Grinder / target",
                  "Model regular", "Regular / target"], rows)


MILESTONES = [
    ("Earned $100k", "earn1e+05"), ("Earned $1M", "earn1e+06"), ("Earned $10M", "earn1e+07"),
    ("Earned $100M", "earn1e+08"), ("Earned $1B", "earn1e+09"), ("Earned $10B", "earn1e+10"),
    ("Earned $100B", "earn1e+11"),
    ("Depths tier 4 (Gold)", "tier4"), ("Depths tier 8 (Emerald)", "tier8"), ("Depths tier 10 (Quartz)", "tier10"),
    ("Depths tier 12 (Voidstone)", "tier12"), ("Tool Lv 30 (3 perk slots)", "toollvl30"),
    ("Spawner stack 64", "stack64"), ("Spawner stack 512", "stack512"),
    ("Coinmaster set bought", "coinmaster"), ("Coinmaster Lv 50 (2x)", "cm50"),
    ("Sell bonus +100% (no boosters)", "sell100"), ("Sell bonus +200% (no boosters)", "sell200"),
    ("First enchant at V (crystals)", "enchant_max"), ("Crystal set (Deepforge)", "crystal_set"),
]


@register("milestones")
def t_milestones():
    rows = [[name, *(when(k, key) for k in KINDS)] for name, key in MILESTONES]
    return table(["Milestone", "Casual (1 h/day): played h (day)", "Regular (3 h/day)", "Grinder (8 h/day)"], rows)


@register("pacing")
def t_pacing():
    """Tier unlock gaps (progressively harder) and the income jump at each unlock + forge (ups)."""
    rows = []
    k = "regular"
    p, _, daily = sim(k)
    prev_h = 0.0
    prev_gap = None
    for t in range(2, 13):
        ev = event_at(k, f"tier{t}")
        if ev is None:
            rows.append([t, MINE_TIERS[t - 1], "not this season", "-", "-", "-", when(k, f"tool{t}")])
            continue
        gap = ev[1] - prev_h
        ratio = f"x{gap / prev_gap:.1f}" if prev_gap else "-"
        fe = event_at(k, f"tool{t}")
        wait = f"{fe[1] - ev[1]:.1f} h" if fe else "not this season"
        jump = price(t) / price(t - 1)
        rows.append([t, MINE_TIERS[t - 1], f"{hours_label(gap)} (day {ev[2]})", ratio,
                     f"+{(jump * NEW_ORE_SPEED - 1) * 100:.0f}%" if jump * NEW_ORE_SPEED >= 1 else "0% (keeps old ore)",
                     wait, f"+{(jump - 1) * 100:.0f}%"])
        prev_h, prev_gap = ev[1], gap
    return table(["Tier", "Ore", "Played h since last tier (regular)", "vs last gap", "Up at unlock (new ore, old tool)",
                  "Plateau until the tool is forged", "Up after the forge (vs old ore)"], rows)


CURVE_DAYS = [1, 2, 3, 5, 7, 10, 14, 21, 30, 45, 60, 75, 90]


@register("curve")
def t_curve():
    rows = []
    prev = {k: None for k in KINDS}
    for d in CURVE_DAYS:
        r = [d]
        for k in KINDS:
            daily = sim(k)[2]
            h = daily[d - 1]["hourly"]
            if prev[k] is None:
                tag = "start"
            else:
                pd = prev[k][0]
                g = (h / prev[k][1]) ** (1 / (d - pd)) - 1  # growth per day
                tag = "UP" if g >= 0.10 else ("climb" if g >= 0.02 else "plateau")
            r.append(f"{money(h)}/h · T{daily[d - 1]['tier']} · {tag}")
            prev[k] = (d, h)
        rows.append(r)
    return table(["Day", "Casual (income per played h · tier · trend per day)", "Regular", "Grinder"], rows)


@register("progress")
def t_progress():
    rows = []
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            cm = f"CM {s['cm']}" if s["cm"] is not None else "CM -"
            r.append(f"{s['active_h']:.0f} h · T{s['tier']} · Lv{s['tool_level']} ({s['slots']}) · PL {s['pl']} · "
                     f"SP {s['spawners']} ({s['stack']}/block) · UL {s['ul']} · late {s['late']} · M {s['mastery']} · {cm}")
        rows.append(r)
    return table(["Stage", "Casual (played h · tier · tool level (slots) · production levels · spawners/stack limit "
                  "· level upgrades · late projects · mastery · Coinmaster level)", "Regular", "Grinder"], rows)


@register("shares")
def t_shares():
    rows = []
    for k in KINDS:
        for name in ("Week 1", "Month 1", "Month 3"):
            s = sim(k)[1][name]
            e, te = s["earned"], s["total_earned"]
            rows.append([k, name, pct(e["depths"] / te), pct(e["island"] / te), pct(e["spawners"] / te),
                         pct((e["island"] + e["spawners"]) / te), pct(e["events"] / te), pct(e["interest"] / te, 1)])
    rows.append(["**target**", "season", "~50%", "", "", "~35%", "~15%", "<= 5%"])
    return table(["Archetype", "Earned up to", "Depths", "Island production", "Spawners", "Island total",
                  "Events/quests", "Interest"], rows)


@register("sell_sources")
def t_sell_sources():
    rows = [
        ("Rank", pct(SELL_RANK_MAX), "store ranks (Ember/freerank: 0%)", "model: 0 / 4 / 8%"),
        ("Sell Multiplier island upgrade", f"{pct(SELL_UPGRADE_PER_LEVEL)} per level, {pct(SELL_UPGRADE_PER_LEVEL * LV_MAX)} at Lv 12",
         "level n needs tier n", "bought with money"),
        ("Market Contacts mastery (MA5)", f"{pct(SELL_MASTERY_PER_LEVEL)} per level, max {pct(SELL_MASTERY_PER_LEVEL * SELL_MASTERY_MAX)}",
         "after the upgrades are maxed", f"model: {pct(MASTERY_SELL_SHARE)} of mastery levels"),
        ("Coinmaster Silks (set)", f"0 at Lv 0 → {pct(SELL_SET_MAX / 2)} at Lv 25 → {pct(SELL_SET_MAX)} at Lv 50",
         f"{money(COINMASTER_PRICE)}, Depths tier {COINMASTER_GATE}", "levels from items sold (max 8,000 XP/h)"),
        ("Gilded Touch (enchant)", f"{pct(SELL_GILDED_PER_LEVEL)} per level, {pct(SELL_GILDED_PER_LEVEL * 5)} at V",
         "Mining T3 book, crystals", f"model: {pct(GILDED_SHARE)} of enchant crystals"),
        ("Personal sell booster", pct(SELL_PERSONAL_BOOST), "crates, AFK pool, votes, island shop", "model uptime 5 / 15 / 30%"),
        ("Global booster", pct(SELL_GLOBAL_BOOST), "store purchase (O11 counts toward the cap)", f"model uptime {pct(GLOBAL_BOOST_UPTIME)}"),
        ("Sell event", pct(SELL_EVENT), "weekend events, KOTH reward", f"model uptime {pct(EVENT_UPTIME)}"),
    ]
    total = sum(best_of_everything().values())
    rows.append(("**Best of everything**", f"**{pct(total)} → capped at {pct(SELL_CAP)} (x{1 + SELL_CAP:.2f})**",
                 "all sources maxed at once", "only then does the cap bite"))
    return table(["Source (all add up, then one cap)", "Bonus", "Where it comes from", "Model assumption"], rows)


@register("sell_stack")
def t_sell_stack():
    rows = []
    for name, _ in STAGES[1:]:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            src = s["sources"]
            parts = [f"{key[0].upper()}{src[key] * 100:.0f}" for key in ("rank", "upgrade", "mastery", "set", "enchant")
                     if src[key] > 0]
            r.append(f"+{s['sell_base'] * 100:.0f}% ({' '.join(parts) or '-'}) · avg x{s['sell_mult']:.2f}")
        rows.append(r)
    return table(["Stage", "Casual: sell bonus without boosters (R rank, U upgrade, M mastery, S set, E enchant) · "
                  "average multiplier with boosters", "Regular", "Grinder"], rows)


def ref_loadout(t: int):
    """Tool level the regular archetype has when it unlocks tier t."""
    if t == 1:
        return 1
    ev = event_at("regular", f"tier{t}") or event_at("grinder", f"tier{t}")
    return ev[3] if ev else TOOL_MAX_LEVEL


def ref_bph(t: int, uptime: float) -> float:
    lvl = ref_loadout(t)
    return bps(t, lvl, slots_for(lvl, False)) * 3600 * uptime


def ref_mult(t: int) -> float:
    """Typical (regular) average sell multiplier at tier t (taken when it unlocks t)."""
    ev = event_at("regular", f"tier{t}")
    if ev is None or t == 1:
        return sell_mult_expected(ARCHETYPES["regular"]["rank"], ARCHETYPES["regular"]["boost"])
    _, _, daily = sim("regular")
    snaps = sim("regular")[1]
    for name, d in STAGES[1:]:
        if d is not None and d >= ev[2]:
            return snaps[name]["sell_mult"]
    return snaps["Month 3"]["sell_mult"]


@register("mine")
def t_mine():
    rows = []
    for t in range(1, 13):
        bph = ref_bph(t, ARCHETYPES["regular"]["uptime"]) * DEPTHS_TIME
        per_h = bph * fortune(t) * price(t) * DYN_DEPTHS
        rows.append([t, MINE_TIERS[t - 1], UNLOCK_LEVEL[t - 1], TIER_XP[t - 1], money(price(t)),
                     f"x{price(t) / price(max(1, t - 1)):.2f}" if t > 1 else "-",
                     num(bph), money(per_h)])
    return table(["Tier", "Ore", "Mining lv", "XP", "Sell price / drop", "vs prev", "Blocks per played h (regular)",
                  "Base Depths $/played h (no multipliers)"], rows)


@register("forest")
def t_forest():
    rows = []
    for t in range(1, 12):
        rows.append([t, FOREST_TIERS[t - 1], UNLOCK_LEVEL[t - 1], TIER_XP[t - 1],
                     money(price(t) / LOGS_PER_BLOCK_TIME), "same as mine tier " + str(t)])
    return table(["Tier", "Tree", "Foraging lv", "XP/log", "Sell price / log", "Money per hour"], rows)


@register("combat")
def t_combat():
    rows = []
    for t in range(1, 11):
        bph = ref_bph(t, ARCHETYPES["regular"]["uptime"]) * DEPTHS_TIME
        kills = KILLS_PER_HOUR_BASE * ARCHETYPES["regular"]["uptime"] * DEPTHS_TIME * (1 + 0.04 * (t - 1))
        per_kill = bph * fortune(t) * price(t) / kills
        rows.append([t, COMBAT_TIERS[t - 1], COMBAT_UNLOCK[t - 1], num(kills), money(per_kill),
                     f"{per_kill / price(t):.1f}x"])
    return table(["Tier", "Zone", "Combat lv", "Kills per played h (regular)",
                  "Drop value / kill", "= mine drops"], rows)


@register("island_products")
def t_island_products():
    crops = ["Wheat", "Carrot, Potato", "Beetroot", "Sugar cane", "Melon, Pumpkin", "Cocoa", "Cactus",
             "Sweet berries", "Nether wart", "Torchflower", "Pitcher plant"]
    gen = ["Cobblestone", "Coal", "Copper", "Iron", "Gold", "Redstone", "Lapis", "Diamond", "Emerald"]
    rows = []
    for t in range(1, 12):
        v = ISL_PRICE * price(t)
        g = gen[t - 1] if t <= len(gen) else "-"
        gv = money(ISL_PRICE * price(t - 1)) if 2 <= t <= len(gen) else ("$1" if t == 1 else "-")
        rows.append([t, crops[t - 1], money(v), g, gv])
    return table(["Tier", "Crop (Farming unlock)", "Crop item", "Generator block", "Generator drop"], rows)


SPAWNER_EXAMPLES = ["pig, cow, chicken, sheep, rabbit", "zombie, skeleton, spider, slime …",
                    "creeper, witch, drowned, husk …", "blaze, enderman, guardian, piglin …",
                    "iron golem, wither skeleton, shulker …"]
SPAWNER_SOURCE = ["shop", "shop + The Depths", "The Depths + crates", "crates", "crates + Black Market"]


@register("spawners")
def t_spawners():
    rows = []
    cfg = config_value("stacking.yml", "spawners.mobs-per-spawner")
    for s in range(1, 6):
        per_h = sp_mobs_per_hour() * sp_value(s) * DYN_ISLAND
        per_h_cfg = sp_mobs_per_hour(cfg) * sp_value(s) * DYN_ISLAND if isinstance(cfg, float) else None
        rows.append([s, SPAWNER_EXAMPLES[s - 1], SPAWNER_SOURCE[s - 1], SPAWNER_GATE[s - 1], money(sp_value(s)),
                     money(per_h) + "/h", money(per_h_cfg) + "/h" if per_h_cfg else "?",
                     money(sp_price(s, 0)), money(sp_price(s, 9)), money(sp_price(s, 24))])
    return table(["Spawner tier", "Types (stacking.yml)", "Source", "Model: in reach at Depths tier", "Loot per mob",
                  f"One spawner, {SP_MOBS_PER_SPAWN:g} mob/spawn (recommended)",
                  f"One spawner, config today ({cfg:g} mobs/spawn)" if isinstance(cfg, float) else "Config today",
                  "Price: first of this tier", "10th", "25th"], rows)


@register("stack_steps")
def t_stack_steps():
    rows = []
    for i, (lvl, lim) in enumerate(STACK_STEPS):
        name = "start" if i == 0 else f"step {i}"
        cost = sum(level_cost(UP_STACK, n) for n in range(1, lvl + 1))
        rows.append([name, f"Lv {lvl}" if lvl else "-", lim, lim * SP_BLOCKS, money(cost), when("regular", f"stack{lim}")
                     if i else "start", when("grinder", f"stack{lim}") if i else "start"])
    return table(["Stack step", "Spawner Stack Limit level", "Spawners per block", f"Model island cap ({SP_BLOCKS} blocks)",
                  "Upgrade money to get there", "Regular reaches it", "Grinder reaches it"], rows)


@register("blocks")
def t_blocks():
    rows = []
    for t in range(1, 13):
        lvl = ref_loadout(t)
        sl = slots_for(lvl, False)
        raw = BPS1 * TOOL_SPEED ** (t - 1) * (1 + LVL_SPEED * lvl) * (1 + AREA_PER_SLOT * sl)
        b = bps(t, lvl, sl)
        rows.append([t, MINE_TIERS[t - 1] + " pickaxe", lvl, sl, f"{raw:.2f}",
                     *(num(b * 3600 * ARCHETYPES[k]["uptime"] * DEPTHS_TIME) for k in KINDS),
                     f"{b * RESPAWN_S / FIELD * 100:.1f}%"])
    rows.append(["max", "Voidstone, Lv50, 5 slots", 50, 5,
                 f"{BPS1 * TOOL_SPEED ** 11 * 1.5 * (1 + AREA_PER_SLOT * 5):.2f}",
                 *(num(bps(12, 50, 5) * 3600 * ARCHETYPES[k]["uptime"] * DEPTHS_TIME) for k in KINDS),
                 f"{bps(12, 50, 5) * RESPAWN_S / FIELD * 100:.1f}%"])
    return table(["Tool tier", "Tool", "Typical level", "Slots", "Blocks/s", "Casual blocks/played h",
                  "Regular", "Grinder", "Field depleted at once"], rows)


@register("skill")
def t_skill():
    rows = []
    for t in range(2, 13):
        L = UNLOCK_LEVEL[t - 1]
        rows.append([t, MINE_TIERS[t - 1], L, num(skill_xp_to(L)), *(when(k, f"tier{t}") for k in KINDS)])
    for L in SKILL_LATE_MARKS:
        rows.append(["-", "(after tier 12)", L, num(skill_xp_to(L)), *(when(k, f"skill{L}") for k in KINDS)])
    return table(["Tier", "Ore", "Mining level", "Total skill XP", "Casual reaches it", "Regular", "Grinder"], rows)


@register("tool_levels")
def t_tool_levels():
    rows = []
    targets = {5: "~1 h", 15: "~day 2", 30: "~week 1", 45: "~week 3-4", 50: "~week 6+"}
    for lv, slot in ((5, "1"), (15, "2"), (30, "3"), (45, "4"), (50, "5 (prestige)")):
        rows.append([slot, lv, num(tool_xp_to(lv)), targets[lv], *(when(k, f"toollvl{lv}") for k in KINDS)])
    return table(["Slot", "Tool level", "Total tool XP", "Phase-4 target", "Casual", "Regular", "Grinder"], rows)


@register("forge")
def t_forge():
    rows = []
    for t in range(2, 13):
        rows.append([t, MINE_TIERS[t - 1], money(forge_cost(t)), f"{num(forge_mats(t))} {MINE_TIERS[t - 2]}",
                     money(forge_mats(t) * price(t - 1)),
                     f"{forge_cost(t) / ref(t - 1):.1f} h", when("regular", f"tool{t}"), when("grinder", f"tool{t}")])
    return table(["Tool tier", "Pickaxe", "Money", "Materials", "Material sell value",
                  "Money = hours of REF(t-1)", "Regular forges it", "Grinder forges it"], rows)


@register("upgrades_prod")
def t_upgrades_prod():
    rows = []
    a = ARCHETYPES["regular"]
    for t in range(1, 13):
        v = ISL_PRICE * price(max(1, t - 1)) * DYN_ISLAND * ref_mult(t)
        k = 0
        while (k < PL_PER_TIER * t
               and prod_cost(k + 1) <= (q_items(k + 1) - q_items(k)) * v * a["prod_hours"] * PAYBACK_DAYS):
            k += 1
        cum = sum(prod_cost(i) for i in range(1, k + 1))
        rows.append([t, k, money(prod_cost(k)) if k else "-", money(cum), num(q_items(k)),
                     money(q_items(k) * v) + "/h", money(q_items(k) * v * a["prod_hours"]) + "/day"])
    return table(["Depths tier", "Worth-it production level (PL)", "Cost of that level", "All levels so far",
                  "Items per production h", "Island income per production h", f"Per day (regular, {a['prod_hours']} h)"],
                 rows)


@register("upgrades_level")
def t_upgrades_level():
    rows = []
    cum = 0.0
    for n in range(1, LV_MAX + 1):
        costs = [level_cost(u, n) for u in range(len(LEVEL_UPGRADES))]
        cum += sum(costs)
        rows.append([n, n, money(min(costs)) + " – " + money(max(costs)), money(sum(costs)),
                     f"{sum(costs) / ref(n):.1f} h", money(cum)])
    return table(["Level", "Needs Depths tier", "One upgrade (Warp Slots → Sell Multiplier)",
                  "All 12 at this level", "= hours of REF(n)", "All 12 up to this level"], rows)


@register("upgrade_weights")
def t_upgrade_weights():
    rows = [[name, w, money(level_cost(u, 1)), money(level_cost(u, 6)), money(level_cost(u, 12))]
            for u, (name, w) in enumerate(LEVEL_UPGRADES)]
    return table(["Upgrade", "Weight W", "Level 1", "Level 6", "Level 12"], rows)


@register("late")
def t_late():
    rows = []
    names = ["Greenhold (farming)", "Wildwood (combat/spawners)", "Emberdeep (late game)"]
    for o, gate in enumerate(LATE_GATE):
        total = sum(late_cost(o, j) for j in range(18))
        rows.append([names[o], gate, money(late_cost(o, 0)), money(late_cost(o, 17)), money(total),
                     f"{total / ref(gate):.0f} h", when("regular", f"tier{gate}"), when("grinder", f"tier{gate}")])
    return table(["Outpost", "Gate (Depths tier, proxy for pillars)", "Unlock", "Fusion II (step 18)",
                  "All 18 steps", "= hours of REF(gate)", "Regular reaches gate", "Grinder reaches gate"], rows)


@register("mastery")
def t_mastery():
    rows = []
    for m in (1, 5, 10, 15, 20, 25):
        rows.append([m, money(mastery_cost(m, 11)), money(mastery_cost(m, 12)),
                     f"{MASTERY_H * MASTERY_G ** (m - 1):.1f} h"])
    return table(["Mastery level", "Cost at tier 11", "Cost at tier 12", "= hours of REF(tier)"], rows)


@register("interest_personal")
def t_interest_personal():
    rows = []
    for k in range(1, 11):
        cap = pb_cap(k)
        unc = cap * PERSONAL_RATE
        pay = min(unc, pb_payout_cap(k))
        rows.append([k, money(cap), money(pb_cost(k)), money(pb_payout_cap(k)), money(unc),
                     f"{pay / cap * 100:.3f}%", money(pay * PAYOUTS_PER_DAY)])
    return table(["Tier", "Capacity", "Unlock cost", "Payout cap (per 3 h)", "Uncapped payout at full",
                  "Effective rate at full", "Max interest / day"], rows)


@register("interest_island")
def t_interest_island():
    rows = []
    for lv in range(0, 11):
        cap = ib_cap(lv)
        unc = cap * ISLAND_RATE
        pay = min(unc, ib_payout_cap(lv))
        rows.append([lv, money(cap), money(ib_payout_cap(lv)), money(unc), f"{pay / cap * 100:.3f}%",
                     money(pay * PAYOUTS_PER_DAY)])
    return table(["Bank Capacity / Interest level", "Capacity", "Payout cap (per 3 h)", "Uncapped payout at full",
                  "Effective rate at full", "Max interest / day"], rows)


@register("interest_vs_income")
def t_interest_vs_income():
    rows = []
    for name, d in STAGES:
        if d is None:
            continue
        r = [name]
        for k in KINDS:
            daily = sim(k)[2][d - 1]
            e = sum(daily["earned"].values())
            i = daily["earned"]["interest"]
            r.append(f"{money(i)}/day ({pct(i / e, 1)})")
        rows.append(r)
    return table(["Day", "Casual interest (share of that day's income)", "Regular", "Grinder"], rows)


def daily_population(day: int, window: int = 7):
    """Average money created/removed per day over the `window` days ending at `day`, all daily uniques."""
    created = removed = supply = 0.0
    by_reason = {}
    days = range(max(1, day - window + 1), day + 1)
    for k in KINDS:
        n = POPULATION[k]
        supply += n * sim(k)[2][day - 1]["cash"]
        for dd in days:
            d = sim(k)[2][dd - 1]
            created += n * sum(d["earned"].values()) / len(days)
            removed += n * sum(d["spent"].values()) / len(days)
            for r in d["earned"]:
                by_reason["+" + r] = by_reason.get("+" + r, 0.0) + n * d["earned"][r] / len(days)
            for r in d["spent"]:
                by_reason["-" + r] = by_reason.get("-" + r, 0.0) + n * d["spent"][r] / len(days)
    return created, removed, supply, by_reason


INFL_DAYS = [1, 3, 7, 14, 30, 45, 60, 75, 90]


@register("sources_sinks")
def t_sources_sinks():
    rows = []
    reasons = ["+depths", "+island", "+spawners", "+events", "+interest", "-forge", "-production", "-spawners", "-sets",
               "-levels", "-late", "-mastery", "-bank_tiers", "-shop", "-ah_tax"]
    for d in INFL_DAYS:
        _, _, _, br = daily_population(d)
        rows.append([d] + [money(br.get(x, 0)) for x in reasons])
    return table(["Day (7-day avg)", "Depths", "Island", "Spawners", "Events", "Interest", "Forge", "Production",
                  "Spawners bought", "Sets", "Level upg.", "Late proj.", "Mastery", "Bank tiers", "Shop", "AH fee+tax"],
                 rows)


def sink_status(ratio: float) -> str:
    return "OK" if ratio >= 0.75 else ("watch" if ratio >= 0.6 else "act")


@register("inflation")
def t_inflation():
    rows = []
    players = sum(POPULATION.values())
    for d in INFL_DAYS:
        c, r, s, _ = daily_population(d)
        c14, r14, _, _ = daily_population(d, 14)
        reg_daily = sum(sim("regular")[2][d - 1]["earned"].values())
        rows.append([d, money(c), money(r), pct(r / c), pct(r14 / c14), money(c - r), money(s), money(s / players),
                     f"{s / players / reg_daily:.1f}", sink_status(r14 / c14)])
    return table(["Day (7-day avg)", "Created / day", "Removed / day", "Sink ratio (7 days)", "Sink ratio (14 days)",
                  "Net / day", "Money supply (held)", "Held per player", "Held per player in days of regular income",
                  "Check (14 days)"], rows)


def season_sink_ratio() -> float:
    c, r, _, _ = daily_population(SEASON_DAYS, SEASON_DAYS)
    return r / c


def min_sink_ratio(window: int) -> tuple:
    worst = (2.0, 0)
    for d in range(window, SEASON_DAYS + 1):
        c, r, _, _ = daily_population(d, window)
        worst = min(worst, (r / c, d))
    return worst


def scenario(changes: dict):
    """Run the model with some parameters changed; returns summary numbers, then restores them."""
    g = globals()
    old = {k: g[k] for k in changes}
    g.update(changes)
    _CACHE.clear()
    try:
        out = {}
        late = [daily_population(d) for d in (60, 75, 90)]
        out["sink_late"] = min(r / c for c, r, _, _ in late)
        out["sink_season"] = season_sink_ratio()
        out["supply90"] = late[-1][2]
        tops = sim("grinder")[1]["Month 3"]
        out["interest_top"] = tops["earned"]["interest"] / tops["total_earned"]
        out["top_m2"] = sim("grinder")[1]["Month 2"]["total_earned"]
        act = sim("regular")[1]["Month 3"]
        out["island_reg"] = (act["earned"]["island"] + act["earned"]["spawners"]) / act["total_earned"]
        out["sp_reg"] = act["earned"]["spawners"] / act["total_earned"]
        return out
    finally:
        g.update(old)
        _CACHE.clear()


def sensitivity_cases():
    cfg = config_value("stacking.yml", "spawners.mobs-per-spawner")
    cfg = cfg if isinstance(cfg, float) else 4.0
    return [
        ("Baseline (this sheet)", {}),
        (f"Spawners at today's config ({cfg:g} mobs per spawn)", {"SP_MOBS_PER_SPAWN": cfg}),
        ("Shop spawner price does not rise per owned spawner", {"SP_G": 1.0}),
        ("No mastery (no endless sink)", {"MASTERY": False}),
        ("Boosters always on (personal + global)", {"GLOBAL_BOOST_UPTIME": 1.0,
                                                    "ARCHETYPES": {k: dict(v, boost=1.0) for k, v in ARCHETYPES.items()}}),
        ("Old cap +100%", {"SELL_CAP": 1.0}),
        ("Custom tiers x2.4 (old curve)", {"PMB": 2.4}),
        ("Players save twice as much", {"ARCHETYPES": {k: dict(v, save=min(0.6, v["save"] * 2)) for k, v in ARCHETYPES.items()}}),
        ("Interest without payout caps", {"PB_PAYOUTG": 1e9, "IB_PAYOUTG": 1e9, "PB_PAYOUT1": 1e18, "IB_PAYOUT0": 1e18}),
        ("Production payback 8 days (players over-invest)", {"PAYBACK_DAYS": 8.0}),
    ]


@register("sensitivity")
def t_sensitivity():
    rows = []
    for name, ch in sensitivity_cases():
        r = scenario(ch)
        rows.append([name, pct(r["sink_season"]), pct(r["sink_late"]), money(r["supply90"]), pct(r["interest_top"], 1),
                     pct(r["island_reg"]), pct(r["sp_reg"]), money(r["top_m2"])])
    return table(["Scenario", "Sink ratio, season", "Lowest 7-day sink ratio, days 60-90", "Money supply day 90", "Interest share (grinder)",
                  "Island total share (regular)", "Spawner share (regular)", "Grinder earned by month 2"], rows)


@register("crystals")
def t_crystals():
    rows = []
    full = RARE_V_CUM[5]
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            tph = s["cry_rate"]
            r.append(f"{num(tph)}/h · {full / tph:.0f} h per Rare V enchant")
        rows.append(r)
    return table(["Stage", "Casual (crystals per played h incl. missions · hours for one Rare enchant I→V)",
                  "Regular", "Grinder"], rows)


@register("crystal_balance")
def t_crystal_balance():
    rows = []
    for k in KINDS:
        for name in ("Week 1", "Month 1", "Month 3"):
            s = sim(k)[1][name]
            e = s["cry_earned"]
            sp = s["cry_spent"]
            rows.append([k, name, num(e), pct(sp["enchants"] / e), pct(sp["sets"] / e), pct(sp["rolls"] / e),
                         pct(sp["shop"] / e), num(s["crystals"]), pct(s["crystals"] / e)])
    return table(["Archetype", "Up to", "Crystals earned", "Enchants", "Crystal sets", "Rolls", "Island shop",
                  "Still held", "Held share"], rows)


@register("enchant_costs")
def t_enchant_costs():
    rows = []
    cum = 0.0
    for L in range(1, EN_MAX + 1):
        c = EN1 * ENG ** (L - 1)
        cum += c
        rows.append([L, num(c), num(cum)])
    return table(["Enchant step", "Crystal cost (x rarity factor)", "Total to this step"], rows)


@register("crystal_rates")
def t_crystal_rates():
    rows = []
    for t in range(1, 13):
        rate = crystal_rate(t)
        rows.append([t, MINE_TIERS[t - 1], f"{rate:.3f}", f"1 per {1 / rate:.0f} blocks"])
    return table(["Tier", "Ore", "Crystals per block (avg)", "Feels like"], rows)


@register("dynamic")
def t_dynamic():
    k_per_h = -math.log(1 - RECOVERY_PER_10MIN) * 6
    rows = []
    for t in range(1, 13):
        per_player = ref_bph(t, ARCHETYPES["regular"]["uptime"]) * DEPTHS_TIME * fortune(t)
        cells = []
        for n in (10, 25, 50):
            impact = TARGET_DEPRESSION * k_per_h / (per_player * n)
            cells.append(f"{impact * 1e6:.2f} ppm")
        rows.append([t, MINE_TIERS[t - 1], num(per_player), *cells])
    return table(["Tier", "Ore", "Drops sold per player per played h", "Impact per item, 10 players on tier",
                  "25 players", "50 players"], rows)


@register("gems")
def t_gems():
    rows = [
        ("Vote (per site, ~5 sites)", "3", "15 / day", "free"),
        ("Say gg after a store purchase", "1", "max 5 / day", "free, 60 s window"),
        ("Daily login streak day 7", "10", "~10 / week", "free"),
        ("Store credit", "set in Tebex", "-", "paid"),
    ]
    return table(["Gem source", "Gems", "Typical", "Type"], rows)


# ---------------------------------------------------------------------------
# 6. Current configs (read from the repo) vs this model
# ---------------------------------------------------------------------------

JAVA = {
    "economy.yml": "modules/economy/src/main/java/net/skybornmc/skyblock/economy/EconomyConfig.java",
    "auction.yml": "modules/auction/src/main/java/net/skybornmc/skyblock/auction/AuctionConfig.java",
    "skills.yml": "modules/skills/src/main/java/net/skybornmc/skyblock/skills/SkillsConfig.java",
    "tools.yml": "modules/tools/src/main/java/net/skybornmc/skyblock/tools/ToolsConfig.java",
    "depths.yml": "modules/depths/src/main/java/net/skybornmc/skyblock/depths/DepthsConfig.java",
    "stacking.yml": "modules/stacking/src/main/java/net/skybornmc/skyblock/stacking/StackingConfig.java",
}
CAP_JAVA = "common/src/main/java/net/skybornmc/skyblock/common/economy/MultiplierCap.java"
TOOLS_YML = "modules/tools/src/main/resources/tools-defaults.yml"
_CFG_CACHE: dict = {}


def _read(rel: str):
    try:
        with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
            return f.read()
    except OSError:
        return None


def _parse_value(raw: str):
    raw = raw.strip()
    if raw.startswith("List.of("):
        return [float(x) for x in re.findall(r'"([0-9.]+)"', raw)]
    if raw.endswith("DEFAULT_CAP_PERCENT"):
        src = _read(CAP_JAVA) or ""
        m = re.search(r"DEFAULT_CAP_PERCENT\s*=\s*([0-9_.]+)", src)
        return float(m.group(1).replace("_", "")) if m else None
    m = re.fullmatch(r"(-?[0-9_]+(?:\.[0-9_]+)?)[LdD]?", raw)
    return float(m.group(1).replace("_", "")) if m else None


def config_value(file: str, key: str):
    """Default of a typed config key, read from the module's Java config class (None if not found)."""
    ck = (file, key)
    if ck in _CFG_CACHE:
        return _CFG_CACHE[ck]
    value = None
    src = _read(JAVA.get(file, ""))
    if src:
        m = re.search(r'path\s*=\s*"' + re.escape(key) + r'".*?public\s+[\w<>.]+\s+\w+\s*=\s*(.+?);', src, re.S)
        if m:
            value = _parse_value(m.group(1))
    _CFG_CACHE[ck] = value
    return value


def forge_money_from_yml():
    src = _read(TOOLS_YML) or ""
    out = {}
    m = re.search(r"pickaxe:\s*\n(.*?)\n\s*axe:", src, re.S)
    if m:
        for t, line in enumerate(m.group(1).splitlines(), start=1):
            mm = re.search(r"money:\s*([0-9]+)", line)
            if mm:
                out[t] = float(mm.group(1))
    return out


def config_rows():
    """(file, key, current, model value, note) for every config number the model depends on."""
    rows = [
        ("economy.yml", "start.money", START_MONEY, "start money"),
        ("economy.yml", "start.crystals", 0, "first enchant step comes from the first ~20 min of mining"),
        ("economy.yml", "sell.multiplier-cap-percent", SELL_CAP * 100, "#46 +300%, summed then capped once"),
        ("auction.yml", "fee.percent", AH_FEE * 100, "listing fee"),
        ("auction.yml", "tax.percent", AH_TAX * 100, "sales tax"),
        ("skills.yml", "curve.base", SK_A, ""), ("skills.yml", "curve.early-growth", SK_G1, ""),
        ("skills.yml", "curve.break-level", SK_K, ""), ("skills.yml", "curve.late-growth", SK_G2, ""),
        ("skills.yml", "curve.end-break-level", SK_K2, ""), ("skills.yml", "curve.end-growth", SK_G3, ""),
        ("skills.yml", "curve.max-level", SKILL_MAX, "#65"),
        ("tools.yml", "curve.base", TL_A, ""), ("tools.yml", "curve.growth", TL_G, ""),
        ("tools.yml", "curve.max-level", TOOL_MAX_LEVEL, ""),
        ("tools.yml", "perk-slots.levels", [float(x) for x in SLOT_LEVELS], ""),
        ("tools.yml", "xp.tier-xp", [float(x) for x in TIER_XP], ""),
        ("tools.yml", "speed.per-level-percent", LVL_SPEED * 100, ""),
        ("tools.yml", "fortune.per-tier-percent", FORTUNE_PER_TIER * 100, ""),
        ("depths.yml", "engine.default-respawn-seconds", RESPAWN_S, "reach cap 6 blocks/s"),
        ("stacking.yml", "mobs.max-stack", 1024, "S2"),
        ("stacking.yml", "spawners.stack-start", STACK_STEPS[0][1], "S20"),
        ("stacking.yml", "spawners.stack-max", STACK_STEPS[-1][1], "S20"),
        ("stacking.yml", "spawners.min-interval-seconds", SP_INTERVAL_MIN, ""),
        ("stacking.yml", "spawners.max-interval-seconds", SP_INTERVAL_MAX, ""),
        ("stacking.yml", "spawners.mobs-per-spawner", SP_MOBS_PER_SPAWN,
         f"{config_value('stacking.yml', 'spawners.mobs-per-spawner') or 4:g} makes one spawner earn "
         f"{(config_value('stacking.yml', 'spawners.mobs-per-spawner') or 4) / SP_MOBS_PER_SPAWN:g}x the model"),
    ]
    out = []
    for file, key, model, note in rows:
        out.append((file, key, config_value(file, key), model, note))
    yml = forge_money_from_yml()
    for t in range(2, 13):
        out.append(("tools.yml", f"tiers.pickaxe[{t}].money", yml.get(t), round(forge_cost(t), -2), "Forge §5.1"))
    return out


def _same(cur, model) -> bool:
    if cur is None:
        return False
    if isinstance(model, list):
        return isinstance(cur, list) and len(cur) == len(model) and all(abs(a - b) < 1e-9 for a, b in zip(cur, model))
    if isinstance(cur, list):
        return False
    return abs(float(cur) - float(model)) <= max(1e-9, abs(float(model)) * 0.005)


def _fmt(v) -> str:
    if v is None:
        return "not found"
    if isinstance(v, list):
        return ", ".join(f"{x:g}" for x in v)
    return f"{v:,.0f}" if float(v).is_integer() else f"{v:g}"


@register("config_check")
def t_config_check():
    rows = []
    for file, key, cur, model, note in config_rows():
        rows.append([f"`{file}`", f"`{key}`", _fmt(cur), _fmt(model), "ok" if _same(cur, model) else "**CHANGE**", note])
    return table(["File", "Key", "Config today", "Model", "Status", "Note"], rows)


@register("config_changes")
def t_config_changes():
    rows = []
    for file, key, cur, model, note in config_rows():
        if not _same(cur, model):
            rows.append([f"`{file}`", f"`{key}`", _fmt(cur), _fmt(model), note])
    planned = [
        ("`shop.yml` (M3.5, new)", "`spawners.price-growth-per-owned`", "not built", f"{SP_G}",
         f"price of a shop spawner rises {(SP_G - 1) * 100:.0f}% per spawner of that tier the island owns; without it spawners compound (§4.3)"),
        ("`shop.yml` (M3.5, new)", "`spawners.price.tier-1..2`", "not built", f"{money(SP_PRICE[0])} / {money(SP_PRICE[1])}",
         "first spawner of that tier"),
        ("`boosters.yml` (new)", "`sell.personal-percent` / `sell.global-percent`", "not built",
         f"{SELL_PERSONAL_BOOST * 100:g} / {SELL_GLOBAL_BOOST * 100:g}", "both count toward the +300% cap (O11)"),
        ("`islands` upgrades (Phase 5)", "`upgrades.sell-multiplier.percent-per-level`", "not built",
         f"{SELL_UPGRADE_PER_LEVEL * 100:g}", "12 levels = +60%"),
        ("`islands` upgrades (Phase 5)", "`upgrades.spawner-stack-limit.steps`", "not built",
         ", ".join(f"Lv{a}:{b}" for a, b in STACK_STEPS[1:]), "feeds `IslandService#spawnerStackLimit`"),
        ("`masteries` (Phase 5)", "`market-contacts.percent-per-level` / `max-level`", "not built",
         f"{SELL_MASTERY_PER_LEVEL * 100:g} / {SELL_MASTERY_MAX}", "MA5"),
        ("`sets.yml` (W1)", "`coinmaster.source.price`", "not built", money(COINMASTER_PRICE), "WARDROBE §2.4 (no change)"),
    ]
    rows.extend([list(p) for p in planned])
    return table(["File", "Key", "Today", "Suggested", "Why"], rows)


# ---------------------------------------------------------------------------
# 7. Checks
# ---------------------------------------------------------------------------

def tier_gaps(kind: str):
    out, prev = [], 0.0
    for t in range(2, 13):
        ev = event_at(kind, f"tier{t}")
        if ev is None:
            break
        out.append(ev[1] - prev)
        prev = ev[1]
    return out


@register("checks")
def t_checks():
    """Automatic sanity checks; each row must say PASS."""
    rows = []

    def chk(name, ok, detail):
        rows.append([name, "PASS" if ok else "FAIL", detail])

    s = sim("regular")[1]
    e, te = s["Month 3"]["earned"], s["Month 3"]["total_earned"]
    isl = e["island"] + e["spawners"]
    chk("Depths share 40-60% (regular, season)", 0.40 <= e["depths"] / te <= 0.60, pct(e["depths"] / te))
    chk("Island + spawner share 25-45% (regular, season)", 0.25 <= isl / te <= 0.45, pct(isl / te))
    chk("Spawners 8-25% of income (regular, season)", 0.08 <= e["spawners"] / te <= 0.25, pct(e["spawners"] / te))
    chk("Events share 10-20% (regular, season)", 0.10 <= e["events"] / te <= 0.20, pct(e["events"] / te))
    chk("Interest <= 5% of income (all, season)",
        all(sim(k)[1]["Month 3"]["earned"]["interest"] / sim(k)[1]["Month 3"]["total_earned"] <= 0.05 for k in KINDS),
        ", ".join(pct(sim(k)[1]["Month 3"]["earned"]["interest"] / sim(k)[1]["Month 3"]["total_earned"], 1)
                  for k in KINDS))
    top1 = sim("grinder")[1]["First hour"]["total_earned"]
    chk("First hour 30k-100k (grinder)", 30_000 <= top1 <= 100_000, money(top1))
    w2 = sim("grinder")[1]["Week 2"]["total_earned"]
    chk("Week 2 within 0.5x-2x of $500M (grinder)", 250e6 <= w2 <= 1e9, money(w2))
    m2 = sim("grinder")[1]["Month 2"]["total_earned"]
    chk("Month 2 within 0.5x-2x of $50B (grinder)", 25e9 <= m2 <= 100e9, money(m2))
    ss = season_sink_ratio()
    chk("Sink ratio over the whole season >= 80%", ss >= 0.80, pct(ss))
    w14 = min_sink_ratio(14)
    chk("Sink ratio (14-day average) never below 60% ('act')", w14[0] >= 0.60, f"min {pct(w14[0])} (day {w14[1]})")
    lv5 = event_at("regular", "toollvl5")
    chk("Slot 1 within ~1-2 h (regular)", lv5 is not None and lv5[1] <= 2, when("regular", "toollvl5"))
    lv45 = event_at("regular", "toollvl45")
    chk("Slot 4 in week 3-8 (regular)", lv45 is not None and 14 <= lv45[2] <= 56, when("regular", "toollvl45"))
    chk("Max blocks/s under reach cap", bps(12, 50, 5) <= REACH_CAP, f"{bps(12, 50, 5):.2f} <= {REACH_CAP}")
    chk("Money fits in a long", sim("grinder")[0].total_earned() * POPULATION["grinder"] < 9.2e18, "season total, all grinders")
    t12 = event_at("grinder", "tier12")
    chk("Tier 12 not before week 4 (grinder)", t12 is None or t12[2] >= 28, when("grinder", "tier12"))
    gaps = tier_gaps("regular")
    chk("Fast start: tier 2 within 3 h (all)", all((event_at(k, "tier2") or (0, 99))[1] <= 3 for k in KINDS),
        ", ".join(when(k, "tier2") for k in KINDS))
    chk("Progressively harder: each tier gap >= previous (regular)", all(b >= a for a, b in zip(gaps, gaps[1:])),
        " → ".join(f"{g:.0f}" for g in gaps) + " h")
    peak = max(sim(k)[0].max_mult for k in KINDS)
    chk("Sell multiplier never above the cap", peak <= 1 + SELL_CAP + 1e-9, f"peak x{peak:.2f} <= x{1 + SELL_CAP:.2f}")
    best = sum(best_of_everything().values())
    chk("Cap only reached with the best of everything", best >= SELL_CAP and all(
        best - v < SELL_CAP for v in best_of_everything().values()), f"all sources {pct(best)}; drop any one → under cap")
    cb = sim("regular")[1]["Month 3"]
    chk("Crystals: sinks take >= 70% (regular, season)", 1 - cb["crystals"] / cb["cry_earned"] >= 0.70,
        pct(1 - cb["crystals"] / cb["cry_earned"]))
    return table(["Check", "Result", "Value"], rows)


def self_check():
    """Invariants (asserts). Runs after every invocation; a failure means the model itself is broken."""
    # cap math
    assert capped(10.0) == 1 + SELL_CAP and capped(-5) == 0.0 and capped(0.5) == 1.5
    assert sell_mult_expected(SELL_CAP, 1.0) <= 1 + SELL_CAP + 1e-9
    assert abs(sell_mult_expected(0.0, 0.0) - (1 + GLOBAL_BOOST_UPTIME * SELL_GLOBAL_BOOST + EVENT_UPTIME * SELL_EVENT
                                               + 0 * 1)) < 0.05
    best = sum(best_of_everything().values())
    assert best >= SELL_CAP, "the best of everything must reach the cap"
    # spawner stacking: start 8, max 512, 5 steps, strictly rising
    lims = [v for _, v in STACK_STEPS]
    assert lims[0] == 8 and lims[-1] == 512 and len(lims) == 6
    assert all(b > a for a, b in zip(lims, lims[1:]))
    assert stack_limit(0) == 8 and stack_limit(LV_MAX) == 512
    assert len(SPAWNER_GATE) == 5 and len(SP_PRICE) == 5
    # simulation invariants
    for k in KINDS:
        p, snaps, daily = sim(k)
        assert p.max_mult <= 1 + SELL_CAP + 1e-9
        assert p.cash >= -1e-6 and p.crystals >= -1e-6
        assert p.spawner_count() <= p.stack_limit() * SP_BLOCKS
        assert all(daily[i]["tier"] <= daily[i + 1]["tier"] for i in range(len(daily) - 1))
        assert snaps["Month 3"]["total_earned"] > snaps["Week 1"]["total_earned"] > snaps["First hour"]["total_earned"]
    # archetype order: more play = more progress
    m3 = [sim(k)[1]["Month 3"]["total_earned"] for k in KINDS]
    assert m3[0] < m3[1] < m3[2]
    # determinism: a fresh run gives the same numbers
    before = sim("regular")[0].total_earned()
    _CACHE.pop("regular")
    assert sim("regular")[0].total_earned() == before
    # skill gaps grow (progressively harder) for every archetype
    for k in KINDS:
        g = tier_gaps(k)
        assert all(b >= a * 0.95 for a, b in zip(g, g[1:])), (k, g)


# ---------------------------------------------------------------------------
# 8. CLI
# ---------------------------------------------------------------------------

def render(name: str) -> str:
    return TABLES[name]().strip()


BLOCK_RE = re.compile(r"(<!-- BEGIN table:([a-z_]+) -->\n)(.*?)(<!-- END table:\2 -->)", re.S)


def refresh(text: str):
    found = []

    def rep(m):
        found.append(m.group(2))
        return m.group(1) + render(m.group(2)) + "\n" + m.group(4)

    return BLOCK_RE.sub(rep, text), found


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--table")
    ap.add_argument("--write")
    ap.add_argument("--check")
    args = ap.parse_args(argv)
    code = 0
    if args.table:
        print(render(args.table))
    elif args.write or args.check:
        path = args.write or args.check
        with open(path, encoding="utf-8") as f:
            text = f.read()
        new, found = refresh(text)
        missing = sorted(set(TABLES) - set(found))
        if args.write:
            with open(path, "w", encoding="utf-8") as f:
                f.write(new)
            print(f"refreshed {len(found)} tables in {path}" + (f"; not in doc: {missing}" if missing else ""))
        elif new != text or missing:
            print(f"{path} is out of date: run --write" + (f" (tables missing from the doc: {missing})" if missing else ""))
            code = 1
        else:
            print(f"{path} is up to date ({len(found)} tables)")
    else:
        for name in TABLES:
            print(f"### {name}\n\n{render(name)}\n")
    self_check()
    print("self-check: OK", file=sys.stderr)
    return code


if __name__ == "__main__":
    sys.exit(main())
