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

TUNE (E4 + W4, 2026-10-30) put the enchants and sets as built into the model (section 5b, doc §9.5-§9.10):
  - enchant costs, gates, book drops, pity, rolls and caps are read from EnchanterConfig + enchants.yml, set values,
    levels and XP from WardrobeConfig + sets.yml (the bundled defaults);
  - the +12%-per-perk-slot stand-in is gone: the pickaxe carries Swiftness, Scholar's Insight, Prosperity and Vein Rend
    (Gilded Touch while a book is missing); area blocks add items and crystals but no skill XP (as built);
  - players wear Delver's Garb -> Deepforge Plate -> Coinmaster Silks and level them with set XP;
  - new checks: books per hour per band, family caps, combat drop value per kill (mobs.yml x sell.yml), /shop buy > sell.
"""

from __future__ import annotations

import argparse
import bisect
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------------------
# 0. Repo readers (typed config defaults from the Java classes, bundled YAML resources)
# ---------------------------------------------------------------------------

JAVA = {
    "economy.yml": "modules/economy/src/main/java/net/skybornmc/skyblock/economy/EconomyConfig.java",
    "auction.yml": "modules/auction/src/main/java/net/skybornmc/skyblock/auction/AuctionConfig.java",
    "skills.yml": "modules/skills/src/main/java/net/skybornmc/skyblock/skills/SkillsConfig.java",
    "tools.yml": "modules/tools/src/main/java/net/skybornmc/skyblock/tools/ToolsConfig.java",
    "depths.yml": "modules/depths/src/main/java/net/skybornmc/skyblock/depths/DepthsConfig.java",
    "stacking.yml": "modules/stacking/src/main/java/net/skybornmc/skyblock/stacking/StackingConfig.java",
    "enchanter.yml": "modules/enchants/src/main/java/net/skybornmc/skyblock/enchants/EnchanterConfig.java",
    "wardrobe.yml": "modules/wardrobe/src/main/java/net/skybornmc/skyblock/wardrobe/WardrobeConfig.java",
}
CAP_JAVA = "common/src/main/java/net/skybornmc/skyblock/common/economy/MultiplierCap.java"
TOOLS_YML = "modules/tools/src/main/resources/tools-defaults.yml"
ENCHANTS_YML = "modules/enchants/src/main/resources/enchants.yml"
SETS_YML = "modules/wardrobe/src/main/resources/sets.yml"
MOBS_YML = "modules/depths/src/main/resources/mobs.yml"
SELL_YML = "modules/economy/src/main/resources/sell.yml"
SHOP_YML = "modules/shop/src/main/resources/shop.yml"
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


def _java_default(file: str, key: str):
    src = _read(JAVA.get(file, ""))
    if not src:
        return None
    m = re.search(r'path\s*=\s*"' + re.escape(key) + r'".*?public\s+[\w<>.]+\s+\w+\s*=\s*(.+?);', src, re.S)
    return m.group(1) if m else None


def config_value(file: str, key: str):
    """Default of a typed config key, read from the module's Java config class (None if not found)."""
    ck = (file, key)
    if ck in _CFG_CACHE:
        return _CFG_CACHE[ck]
    raw = _java_default(file, key)
    value = _parse_value(raw) if raw is not None else None
    _CFG_CACHE[ck] = value
    return value


def config_strings(file: str, key: str):
    """A List<String> default (e.g. odds "rare:50") as raw strings, or None."""
    raw = _java_default(file, key)
    if raw is None or not raw.strip().startswith("List.of("):
        return None
    return re.findall(r'"([^"]*)"', raw)


def cfg(file: str, key: str, default):
    v = config_value(file, key)
    if v is None or isinstance(v, list) and not v:
        return default
    return v


def _flow(s: str):
    """Parse a YAML flow value (the subset the bundled resources use): numbers, plain/quoted strings, [..], {..}."""
    pos = 0

    def ws():
        nonlocal pos
        while pos < len(s) and s[pos] in " \t":
            pos += 1

    def value():
        nonlocal pos
        ws()
        if pos < len(s) and s[pos] == "[":
            pos += 1
            out = []
            ws()
            if s[pos] == "]":
                pos += 1
                return out
            while True:
                out.append(value())
                ws()
                c = s[pos]
                pos += 1
                if c == "]":
                    return out
        if pos < len(s) and s[pos] == "{":
            pos += 1
            out = {}
            ws()
            if s[pos] == "}":
                pos += 1
                return out
            while True:
                ws()
                k0 = pos
                while s[pos] != ":":
                    pos += 1
                k = s[k0:pos].strip().strip('"')
                pos += 1
                out[k] = value()
                ws()
                c = s[pos]
                pos += 1
                if c == "}":
                    return out
        if pos < len(s) and s[pos] in "\"'":
            q = s[pos]
            end = s.index(q, pos + 1)
            v = s[pos + 1:end]
            pos = end + 1
            return v
        k0 = pos
        while pos < len(s) and s[pos] not in ",]}":
            pos += 1
        return _scalar(s[k0:pos].strip())

    return value()


def _scalar(t: str):
    if re.fullmatch(r"-?[0-9]+", t):
        return int(t)
    if re.fullmatch(r"-?[0-9]*\.[0-9]+", t):
        return float(t)
    if t in ("true", "false"):
        return t == "true"
    return t.strip('"').strip("'")


def parse_yaml(text: str):
    """Tiny block-YAML reader for the bundled resources (maps, '- ' items with flow values, comments)."""
    lines = []
    for raw in text.splitlines():
        stripped = raw.split(" #")[0].rstrip() if not raw.lstrip().startswith("#") else ""
        if stripped.strip():
            lines.append((len(stripped) - len(stripped.lstrip()), stripped.strip()))

    def block(i, indent):
        if i < len(lines) and lines[i][1].startswith("- "):
            out = []
            while i < len(lines) and lines[i][0] == indent and lines[i][1].startswith("- "):
                out.append(_flow(lines[i][1][2:]))
                i += 1
            return out, i
        out = {}
        while i < len(lines) and lines[i][0] == indent:
            key, _, rest = lines[i][1].partition(":")
            key = key.strip().strip('"')
            rest = rest.strip()
            i += 1
            if rest:
                out[key] = _flow(rest)
            elif i < len(lines) and lines[i][0] > indent:
                out[key], i = block(i, lines[i][0])
            elif i < len(lines) and lines[i][0] == indent and lines[i][1].startswith("- "):
                out[key], i = block(i, indent)
            else:
                out[key] = None
        return out, i

    return block(0, lines[0][0])[0] if lines else {}


def load_yaml(rel: str) -> dict:
    text = _read(rel)
    if text is None:
        return {}
    try:
        return parse_yaml(text) or {}
    except (ValueError, IndexError):
        return {}


def round_half_up(x: float) -> float:
    return math.floor(x + 0.5)


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
#   bps = min(REACH_CAP, BPS1 * TOOL_SPEED^(t-1) * (1 + LVL_SPEED*level + speed family))
# TUNE: the speed family (Swiftness on the pickaxe, the Delver set) adds to the tool level bonus, like ToolItems#speedBonus.
# BAL2 used a stand-in of x(1 + 12% per perk slot) here; perk slots hold enchants, so TUNE replaced it with the real build.
BPS1 = 1.0
TOOL_SPEED = 1.05
LVL_SPEED = 0.01
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
SELL_PERSONAL_BOOST = 0.50      # personal sell booster (crates, AFK pool, vote rewards, island shop)
SELL_GLOBAL_BOOST = 0.50        # global booster (store purchase, /thanks)
GLOBAL_BOOST_UPTIME = 0.15      # share of the day a global booster runs (proposal)
SELL_EVENT = 0.25               # sell event (weekend, KOTH reward)
EVENT_UPTIME = 0.05

# ---- Wardrobe sets (W4: WARDROBE §2-§3, read from sets.yml + wardrobe.yml as built in W1/W2) ----
SETS = load_yaml(SETS_YML).get("sets") or {}
SET_XP_BASE = cfg("wardrobe.yml", "levels.base-xp", 200)
SET_XP_G = cfg("wardrobe.yml", "levels.growth", 1.10)
SET_MAX = int(cfg("wardrobe.yml", "levels.max", 50))
SET_MULT_MAX = cfg("wardrobe.yml", "levels.max-multiplier", 2.0)
SET_XP_FULL = cfg("wardrobe.yml", "xp.full-share", 1.0)          # 4 pieces worn
SET_XP_ORE = cfg("wardrobe.yml", "xp.actions.depths-ore", 1)    # per own Depths ore block (area blocks give none)
SET_XP_SELL = cfg("wardrobe.yml", "xp.actions.sell-item", 1)
ISLAND_ITEM_SET_XP = cfg("wardrobe.yml", "xp.actions.sell-island-item", 0.5) / max(1e-9, SET_XP_SELL)
SET_XP_CAP_H = next((float(x.split(":")[1]) for x in (config_strings("wardrobe.yml", "xp.hourly-caps") or [])
                     if x.startswith("sell:")), 8_000.0)   # Coinmaster: max set XP per played hour (WD12)
SET_TOME_MAX = int(cfg("wardrobe.yml", "tomes.max-level", 40))
MERGE_FEE_STEP = cfg("wardrobe.yml", "merge.fee-per-step", 5_000)
MERGED_XP_FACTOR = cfg("wardrobe.yml", "levels.merged-xp-factor", 1.5)


def _set(sid: str, key: str, default):
    s = SETS.get(sid) or {}
    if key == "price":
        return (s.get("source") or {}).get("price", default)
    if key == "gate":
        return (s.get("gate") or {}).get("tier", default)
    return s.get(key, default)


# The sets the model's players wear (mining is the reference wing): Delver's Garb (money, tier I) from the first hour,
# Deepforge Plate (crystals, mine tier 6) once bought, Coinmaster Silks (money, tier 8) once bought.
DELVER_PRICE = _set("delver", "price", 20_000)
COINMASTER_PRICE = _set("coinmaster", "price", 40_000_000)
COINMASTER_GATE = _set("coinmaster", "gate", 8)
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
GEAR_MAX = 6.6          # ~33 enchants across 5 tools + armor = 6.6 x the pickaxe's 5 slots
GEAR_PER_TIER = 0.6     # enchantable gear grows with the Depths tier (new tools, sets)
CRYSTAL_SET_PRICE = _set("deepforge", "price", 25_000)   # Deepforge Plate (WARDROBE §2.4)
CRYSTAL_SET_GATE = _set("deepforge", "gate", 6)
ROLL_SHARE = 0.5        # share of the crystals left after enchants/sets a player spends on rolls
CRY_SHOP_SHARE = 0.4    # share of what is left at the end of the day spent in the island shop (fuel, boosters, decor)

# ---- Enchants (E4: ENCHANTS §3-§8, read from enchants.yml + enchanter.yml as built in E1-E3) ----
ENCH = load_yaml(ENCHANTS_YML).get("enchants") or {}
EN1 = cfg("enchanter.yml", "costs.step-base", 100)       # step s costs EN1 * ENG^(s-1) crystals x rarity factor (§5)
ENG = cfg("enchanter.yml", "costs.step-growth", 1.8)
EN_MAX = int(cfg("enchanter.yml", "costs.steps", 10))
EN_SINGLE_STEP = int(cfg("enchanter.yml", "costs.single-level-step", 6))
EN_MYTHIC_STEP = int(cfg("enchanter.yml", "costs.mythic-step", 10))
RARITIES = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]
RARITY_FACTOR = {r: cfg("enchanter.yml", f"costs.rarity-factor.{r}", d)
                 for r, d in zip(RARITIES, (0.5, 0.75, 1.0, 1.5, 2.5, 4.0))}
SCROLL_PRICE = cfg("enchanter.yml", "scroll.price", 2_500)
FAMILY_CAP = {"sell": None,   # the sell family uses economy.yml (SELL_CAP)
              "speed": cfg("enchanter.yml", "caps.speed", 200) / 100,
              "extra_drops": cfg("enchanter.yml", "caps.extra-drops", 200) / 100,
              "crystals": cfg("enchanter.yml", "caps.crystals", 200) / 100,
              "area": cfg("enchanter.yml", "caps.area", 100) / 100,
              "skill_xp": cfg("enchanter.yml", "caps.skill-xp", 300) / 100,
              "tool_xp": cfg("enchanter.yml", "caps.tool-xp", 100) / 100,
              "rare_finds": cfg("enchanter.yml", "caps.rare-finds", 150) / 100,
              "mob_damage": cfg("enchanter.yml", "caps.mob-damage", 200) / 100,
              "damage_reduction": cfg("enchanter.yml", "caps.damage-reduction", 60) / 100,
              "crop_growth": cfg("enchanter.yml", "caps.crop-growth", 100) / 100}

# The pickaxe build a money miner aims for, in slot order (slots open at tool Lv 5/15/30/45; the prestige slot takes a
# Mythic only, which the model never assumes). A slot holds the first enchant of this list whose book the player has
# (median player: expected books found >= BOOK_HAVE) and whose minimum tier is reached; later ones move up meanwhile.
BUILD = ["swiftness", "scholars_insight", "prosperity", "vein_rend", "gilded_touch"]
USE_ENCHANTS = True     # scenario switches (§7.3): the model's players use enchants / wear sets
USE_SETS = True
BOOK_HAVE = math.log(2)
# Crystals: the pickaxe gets 1 / gear_factor of the enchant crystals (the rest goes to the axe, sword and armor), split so
# every enchant on it reaches the same share of its target level (the tool-level gate).


def _odds(raw, default):
    out = {}
    for x in raw or default:
        r, _, w = x.partition(":")
        try:
            out[r] = float(w)
        except ValueError:
            pass
    tot = sum(out.values()) or 1
    return {r: w / tot for r, w in out.items()}


def _ints(raw, default):
    return [int(x) for x in raw] if isinstance(raw, list) and raw else default


# Book drops (§3.2-§3.4): 1 in N actions by band, pity after K actions, rarity odds by band, first finds.
BAND_TIERS = _ints(config_value("enchanter.yml", "drops.band-tiers"), [1, 4, 7, 10])
BOOK_ONE_IN = {"mining": _ints(config_value("enchanter.yml", "drops.mining.one-in"), [2000, 3000, 4000, 5000]),
               "combat": _ints(config_value("enchanter.yml", "drops.combat.one-in"), [150, 200, 250, 300])}
BOOK_PITY = {"mining": _ints(config_value("enchanter.yml", "drops.mining.pity"), [5000, 7500, 10000, 12500]),
             "combat": _ints(config_value("enchanter.yml", "drops.combat.pity"), [375, 500, 625, 750])}
BAND_ODDS = [_odds(config_strings("enchanter.yml", f"drops.rarity-odds.{b}"), d) for b, d in zip("abcd", (
    ["common:70", "uncommon:26", "rare:4"], ["common:45", "uncommon:35", "rare:16", "epic:4"],
    ["common:25", "uncommon:33", "rare:27", "epic:12", "legendary:3"],
    ["common:12", "uncommon:26", "rare:32", "epic:22", "legendary:8"]))]
FIRST_FINDS = [(int(a), r) for a, r in (x.split(":") for x in (config_strings("enchanter.yml", "drops.first-finds")
                                                               or ["100:common", "1000:uncommon"]))]
BOOK_TARGET = (1.0, 1.4)   # books per played hour in every activity (EN7)
# Rolls (EN9) the model buys from left-over crystals: (gate tier, crystals, max per day, rarity odds)
ROLLS = [(int(cfg("enchanter.yml", "rolls.adept.min-tier", 4)), cfg("enchanter.yml", "rolls.adept.price", 4_000),
          int(cfg("enchanter.yml", "rolls.adept.daily", 5)),
          _odds(config_strings("enchanter.yml", "rolls.adept.odds"), ["uncommon:40", "rare:50", "epic:10"])),
         (int(cfg("enchanter.yml", "rolls.master.min-tier", 7)), cfg("enchanter.yml", "rolls.master.price", 25_000),
          int(cfg("enchanter.yml", "rolls.master.daily", 3)),
          _odds(config_strings("enchanter.yml", "rolls.master.odds"), ["rare:40", "epic:50", "legendary:10"]))]

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


_SET_CUM: dict = {}


def set_level_for_xp(xp: float) -> int:
    key = (SET_XP_BASE, SET_XP_G, SET_MAX)
    cum = _SET_CUM.get(key)
    if cum is None:
        cum = _SET_CUM[key] = [set_xp_to(lvl) for lvl in range(1, SET_MAX + 1)]
    return bisect.bisect_right(cum, xp)


def slots_for(level: int, prestiged: bool) -> int:
    return sum(1 for s in SLOT_LEVELS if level >= s) + (1 if prestiged else 0)


def bps_raw(tool: int, level: int, speed: float) -> float:
    return BPS1 * TOOL_SPEED ** (tool - 1) * (1 + LVL_SPEED * level + speed)


def bps(tool: int, level: int, speed: float) -> float:
    """Blocks per second; `speed` = the capped speed family (enchants + set) as a fraction."""
    return min(REACH_CAP, bps_raw(tool, level, speed))


# ---- enchants (E4) ----

def ench(eid: str) -> dict:
    return ENCH.get(eid) or {}


def ench_max(eid: str) -> int:
    return int(ench(eid).get("max-level", 1))


def ench_rarity(eid: str) -> str:
    return ench(eid).get("rarity", "rare")


def cost_step(max_level: int, rarity: str, n: int) -> int:
    if max_level <= 1:
        return EN_MYTHIC_STEP if rarity == "mythic" else EN_SINGLE_STEP
    return int(round_half_up(1 + (n - 1) * (EN_MAX - 1) / (max_level - 1)))


def level_cost_cry(max_level: int, rarity: str, n: int) -> float:
    """Crystals for level n (EnchantCosts#levelCost: step cost x rarity factor, rounded)."""
    return round_half_up(EN1 * ENG ** (cost_step(max_level, rarity, n) - 1) * RARITY_FACTOR.get(rarity, 1.0))


_CUM_COST: dict = {}


def cum_cost_cry(max_level: int, rarity: str, n: int) -> float:
    key = (max_level, rarity, n, EN1, ENG, EN_MAX, EN_SINGLE_STEP, EN_MYTHIC_STEP, RARITY_FACTOR.get(rarity))
    v = _CUM_COST.get(key)
    if v is None:
        v = _CUM_COST[key] = sum(level_cost_cry(max_level, rarity, k) for k in range(1, n + 1))
    return v


def ench_cum(eid: str, n: int) -> float:
    return cum_cost_cry(ench_max(eid), ench_rarity(eid), n)


RARE_V_CUM = [cum_cost_cry(5, "rare", n) for n in range(6)]   # Rare max-V enchant, crystals to level 0..5 (§5)


def gate_level(max_level: int, tool_level: int, prestiged: bool = False) -> int:
    """EN4 / ToolSlots#maxEnchantLevel: ceil(max x tool level / 50), a prestiged tool counts as 50."""
    lvl = TOOL_MAX_LEVEL if prestiged else min(tool_level, TOOL_MAX_LEVEL)
    return 0 if lvl <= 0 else int(min(max_level, math.ceil(max_level * lvl / TOOL_MAX_LEVEL - 1e-9)))


def ench_value(eid: str, n: int) -> float:
    """What level n adds to the enchant's family, as a fraction (expected value for chance effects)."""
    e = ench(eid)
    if n <= 0 or not e:
        return 0.0
    v, p = e.get("values") or {}, e.get("params") or {}

    def at(k):
        lst = v.get(k) or [0]
        return float(lst[min(n, len(lst)) - 1])

    if eid == "vein_rend":
        return at("chance") / 100 * at("count") * p.get("yield", 0) / 100
    if eid == "dragonfire_blast":
        size = p.get("size", 3)
        return at("chance") / 100 * size * size * at("depth") * p.get("yield", 0) / 100
    if "worth" in p and e.get("family") == "crystals":
        return at("chance") / 100 * p["worth"]
    if e.get("family") == "sell" and "multiplier" in p:
        return at("chance") / 100 * (p["multiplier"] - 1)
    if "max-stacks" in p:
        return at("percent") / 100 * p["max-stacks"]
    if "percent" in v:
        return at("percent") / 100
    if "chance" in v:
        return at("chance") / 100
    return 0.0


def band_of(tier: int) -> int:
    b = 0
    for i, t in enumerate(BAND_TIERS):
        if tier >= t:
            b = i
    return b


def books_per_action(pool: str, tier: int, pity: bool = True, one_in=None, pity_n=None) -> float:
    """Expected books per action: 1 in N, and a guaranteed book after K actions without one (E = N x (1 - q^K))."""
    b = band_of(tier)
    n = (one_in or BOOK_ONE_IN[pool])[b]
    k = (pity_n or BOOK_PITY[pool])[b]
    if n <= 0:
        return 0.0
    if not pity or k <= 0:
        return 1 / n
    return 1 / (n * (1 - (1 - 1 / n) ** k))


_POOL_CACHE: dict = {}


def pool_split(pool: str, tier: int, rarity_probs: dict) -> dict:
    """Expected copies of each enchant per book (rarity first, then equal weights; empty rarity falls back one step)."""
    key = (pool, tier, tuple(sorted(rarity_probs.items())))
    if key in _POOL_CACHE:
        return _POOL_CACHE[key]
    out: dict = {}
    for r, pr in rarity_probs.items():
        i = RARITIES.index(r) if r in RARITIES else 0
        while i >= 0:
            members = [(eid, float(e.get("weight", 1))) for eid, e in ENCH.items()
                       if pool in (e.get("pools") or []) and e.get("rarity") == RARITIES[i]
                       and int(e.get("min-tier", 1)) <= tier and e.get("enabled", True) is not False]
            if members:
                tot = sum(w for _, w in members)
                for eid, w in members:
                    out[eid] = out.get(eid, 0.0) + pr * w / tot
                break
            i -= 1
    _POOL_CACHE[key] = out
    return out


# ---- sets (W4) ----

def set_bonus(sid: str, family: str, level: int, pieces: int = 4, activity: str = "mining") -> float:
    """A set's bonus of one family as a fraction at a set level (value x level x max multiplier / max level)."""
    s = SETS.get(sid) or {}
    b = s.get("bonuses") or {}
    parts = list(b.get("two") or []) + (list(b.get("four") or []) if pieces >= 4 else [])
    total = sum(float(x.get("value", 0)) for x in parts if x.get("family") == family
                and (activity is None or not x.get("activities") or activity in x.get("activities")))
    return total / 100 * level * SET_MULT_MAX / SET_MAX


SELL_SET_MAX = set_bonus("coinmaster", "sell", SET_MAX, activity="sell")   # Coinmaster Silks at Lv 50 (+50%)
SELL_GILDED_PER_LEVEL = ench_value("gilded_touch", 1)                      # Gilded Touch I (3% chance of 2x)
SELL_GILDED_MAX = ench_value("gilded_touch", ench_max("gilded_touch") if ENCH else 5)


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
            "Coinmaster set Lv 50 (merged or pure)": SELL_SET_MAX, "Gilded Touch V": SELL_GILDED_MAX,
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


def gear_factor(tier: int) -> float:
    return min(GEAR_MAX, 1 + GEAR_PER_TIER * (tier - 1))


# ---------------------------------------------------------------------------
# 3. Simulation
# ---------------------------------------------------------------------------

class Player:
    def __init__(self, kind: str, enchants: bool = True, sets: bool = True):
        self.kind = kind
        self.a = ARCHETYPES[kind]
        self.use_enchants = enchants and USE_ENCHANTS
        self.use_sets = sets and USE_SETS
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
        self.set_xp: dict = {}           # owned set id -> set XP
        self.mined = [0.0] * 13      # drops mined per tier (Forge materials)
        self.crystals = 0.0
        self.cry_earned = 0.0
        self.ench_invested = 0.0
        self.crystal_set = False
        self.cry_spent = {"enchants": 0.0, "sets": 0.0, "rolls": 0.0, "shop": 0.0}
        self.actions = 0.0               # mining book actions (blocks + area blocks at yield)
        self.books = {r: 0.0 for r in RARITIES}       # expected books found, by rarity
        self.found: dict = {}            # expected copies found per enchant id (mining pool)
        self.books_rolled = 0.0
        self.ench_level: dict = {}       # enchant id -> level on the pickaxe (kept when it moves slot)
        self.build: dict = {}            # enchant id -> level, in slot order
        self.active_h = 0.0
        self.earned = {"depths": 0.0, "island": 0.0, "spawners": 0.0, "events": 0.0, "interest": 0.0}
        self.spent = {"forge": 0.0, "production": 0.0, "spawners": 0.0, "sets": 0.0, "levels": 0.0, "late": 0.0,
                      "mastery": 0.0, "bank_tiers": 0.0, "shop": 0.0, "ah_tax": 0.0}
        self.events_log: list = []  # (event, active h, day, tool level, speed family, area, extra drops family)
        self.seen: set = set()
        self.day = 1
        self.today_h = 0.0
        self.max_mult = 1.0
        self.peak_family: dict = {}      # family -> (highest value, day first at it)
        self._fam: dict = {}             # cached family values for the current build + worn set

    # sets -----------------------------------------------------------------
    def set_level(self, sid: str) -> int:
        if sid not in self.set_xp:
            return 0
        key = ("lvl", sid)
        v = self._fam.get(key)
        if v is None:
            v = self._fam[key] = set_level_for_xp(self.set_xp[sid])
        return v

    @property
    def cm_owned(self) -> bool:
        return "coinmaster" in self.set_xp

    @property
    def cm_level(self) -> int:
        return self.set_level("coinmaster")

    def worn(self):
        """The 4-piece set the model wears: the newest of Delver -> Deepforge -> Coinmaster (each beats the last at Lv 50)."""
        if not self.use_sets:
            return None
        for sid in ("coinmaster", "deepforge", "delver"):
            if sid in self.set_xp:
                return sid
        return None

    def set_frac(self, family: str, activity: str = "mining") -> float:
        key = ("set", family, activity)
        v = self._fam.get(key)
        if v is None:
            w = self.worn()
            v = self._fam[key] = set_bonus(w, family, self.set_level(w), activity=activity) if w else 0.0
        return v

    def invalidate(self):
        """Forget cached family values (after a build, set level or worn set change)."""
        self._fam = {}

    # enchants ---------------------------------------------------------------
    def refresh_build(self):
        """Which enchants sit on the pickaxe and at what level (books found, min tier, tool-level gate, crystals)."""
        if not self.use_enchants:
            if self.build:
                self.build = {}
                self.invalidate()
            return
        normal = min(self.slots(), len(SLOT_LEVELS))   # the prestige slot takes a Mythic only (never modelled)
        chosen = [e for e in BUILD if ENCH.get(e) and self.tier >= int(ench(e).get("min-tier", 1))
                  and self.found.get(e, 0.0) >= BOOK_HAVE][:normal]
        targets = {e: ench_cum(e, gate_level(ench_max(e), self.tool_level, self.prestiged)) for e in chosen}
        total = sum(targets.values())
        f = min(1.0, self.ench_invested / gear_factor(self.tier) / total) if total else 0.0
        out = {}
        for e in chosen:
            g = gate_level(ench_max(e), self.tool_level, self.prestiged)
            budget = f * targets[e]
            lvl = 0
            while lvl < g and ench_cum(e, lvl + 1) <= budget + 1e-6:
                lvl += 1
            lvl = max(lvl, min(g, self.ench_level.get(e, 0)))
            self.ench_level[e] = lvl
            if lvl > 0:
                out[e] = lvl
        if out != self.build:
            self.build = out
            self.invalidate()
        if any(lvl >= ench_max(e) for e, lvl in out.items()):
            self.mark("enchant_max")
        for e in out:
            self.mark(f"slotted_{e}")

    def enchant_frac(self, family: str) -> float:
        key = ("ench", family)
        v = self._fam.get(key)
        if v is None:
            v = self._fam[key] = sum(ench_value(e, lvl) for e, lvl in self.build.items() if ench(e).get("family") == family)
        return v

    def enchant_demand(self) -> float:
        """Crystals all enchants of the player's gear would cost at the tool-level gate (pickaxe x gear factor)."""
        normal = min(self.slots(), len(SLOT_LEVELS))
        chosen = [e for e in BUILD if ENCH.get(e) and self.tier >= int(ench(e).get("min-tier", 1))
                  and self.found.get(e, 0.0) >= BOOK_HAVE][:normal]
        return gear_factor(self.tier) * sum(ench_cum(e, gate_level(ench_max(e), self.tool_level, self.prestiged))
                                            for e in chosen)

    def family(self, name: str) -> float:
        """Enchants + worn set of one family, capped (enchanter.yml -> caps)."""
        v = self.enchant_frac(name) + self.set_frac(name)
        cap = FAMILY_CAP.get(name)
        return min(cap, v) if cap is not None else v

    def speed(self) -> float:
        return self.family("speed")

    def drops_per_block(self) -> float:
        """Items per main block: tool tier fortune (outside the cap) + the extra drops family, then area blocks at yield."""
        return fortune(self.tool) + self.family("extra_drops")

    def area(self) -> float:
        return self.family("area")

    def items_per_block(self) -> float:
        return self.drops_per_block() * (1 + self.area())

    # rates -----------------------------------------------------------------
    def slots(self) -> int:
        return slots_for(self.tool_level, self.prestiged)

    def blocks_per_hour(self) -> float:
        return bps(self.tool, self.tool_level, self.speed()) * 3600 * self.a["uptime"]

    def mine_choice(self):
        """(tier mined, speed factor): new ore is slow until its tool is forged; mine what pays more."""
        t = self.tier
        if self.tool >= t:
            return t, 1.0
        if price(t) * NEW_ORE_SPEED >= price(t - 1):
            return t, NEW_ORE_SPEED
        return t - 1, 1.0

    def gilded_level(self) -> int:
        return self.build.get("gilded_touch", 0)

    def stack_limit(self) -> int:
        return stack_limit(self.ul_levels[UP_STACK])

    def sell_sources(self) -> dict:
        mc = min(SELL_MASTERY_MAX, int(self.mastery * MASTERY_SELL_SHARE))
        return {"rank": self.a["rank"],
                "upgrade": SELL_UPGRADE_PER_LEVEL * self.ul_levels[UP_SELL],
                "mastery": SELL_MASTERY_PER_LEVEL * mc,
                "set": self.set_frac("sell", "sell"),
                "enchant": self.enchant_frac("sell")}

    def sell_base(self, enchant: bool = True) -> float:
        s = self.sell_sources()
        return sum(v for k, v in s.items() if enchant or k != "enchant")

    def sell_mult(self, enchant: bool = True) -> float:
        """Gilded Touch (the enchant source) only works on Depths drops; island items use the rest of the family."""
        return sell_mult_expected(self.sell_base(enchant), self.a["boost"])

    def depths_rate(self) -> float:
        """Money per active hour from The Depths."""
        t, speed = self.mine_choice()
        return (self.blocks_per_hour() * speed * DEPTHS_TIME * self.items_per_block() * price(t)
                * DYN_DEPTHS * self.sell_mult())

    def island_tier(self) -> int:
        return max(1, self.tier - 1)

    def island_rate(self) -> float:
        """Money per production hour from generators, crops and mob loot (production levels)."""
        return q_items(self.pl) * ISL_PRICE * price(self.island_tier()) * DYN_ISLAND * self.sell_mult(False)

    def spawner_rate(self) -> float:
        """Money per production hour from stacked spawners."""
        mobs = sp_mobs_per_hour()
        return (sum(n * mobs * sp_value(s + 1) for s, n in enumerate(self.spawners)) * DYN_ISLAND
                * self.sell_mult(False))

    def spawner_count(self) -> int:
        return sum(self.spawners)

    def best_spawner_tier(self) -> int:
        s = 1
        for i, gate in enumerate(SPAWNER_GATE):
            if self.tier >= gate:
                s = i + 1
        return s

    def main_blocks_per_hour(self) -> float:
        t, speed = self.mine_choice()
        return self.blocks_per_hour() * speed * DEPTHS_TIME

    def items_per_played_hour(self) -> float:
        depths = self.main_blocks_per_hour() * self.items_per_block()
        island = (q_items(self.pl) + self.spawner_count() * sp_mobs_per_hour() * SP_LOOT) \
            * self.a["prod_hours"] / max(1e-9, self.a["hours_day"])
        return depths + island * ISLAND_ITEM_SET_XP

    def crystals_per_block(self) -> float:
        """Crystals per main block: the tier's rate x (1 + crystals family) x area blocks at yield, + island missions."""
        t, _ = self.mine_choice()
        r = crystal_rate(t)
        return r * (1 + self.family("crystals")) * (1 + self.area()) + r * CRY_MISSIONS

    def books_per_hour(self) -> float:
        t, _ = self.mine_choice()
        return self.main_blocks_per_hour() * (1 + self.area()) * books_per_action("mining", t)

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
            self.events_log.append((name, self.active_h, self.day, self.tool_level, self.speed(), self.area(),
                                    self.family("extra_drops")))

    @property
    def ul(self) -> int:
        return sum(self.ul_levels)

    def prod_gain_per_day(self) -> float:
        nxt = (q_items(self.pl + 1) - q_items(self.pl)) * ISL_PRICE * price(self.island_tier()) * DYN_ISLAND \
            * self.sell_mult(False)
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
            # 2. Delver's Garb (tier I mining set) as soon as it is affordable, then Coinmaster Silks at its gate
            if self.use_sets and "delver" not in self.set_xp and self.pay("sets", DELVER_PRICE):
                self.set_xp["delver"] = 0.0
                self.invalidate()
                self.mark("delver")
                continue
            if (self.use_sets and not self.cm_owned and self.tier >= COINMASTER_GATE
                    and self.pay("sets", COINMASTER_PRICE)):
                self.set_xp["coinmaster"] = 0.0
                self.invalidate()
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
                gain = sp_mobs_per_hour() * sp_value(s) * DYN_ISLAND * self.sell_mult(False) * self.a["prod_hours"]
                if sp_price(s, owned) <= gain * PAYBACK_DAYS and self.pay("spawners", sp_price(s, owned)):
                    self.spawners[s - 1] += 1
                    continue
            # Saving for Coinmaster (if it is within SAVE_DAYS of income): nothing below this line until it is bought
            if (self.use_sets and not self.cm_owned and self.tier >= COINMASTER_GATE
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

    def add_books(self, count: float, tier: int, rarity_probs: dict):
        for r, pr in rarity_probs.items():
            self.books[r] = self.books.get(r, 0.0) + count * pr
        for eid, n in pool_split("mining", tier, rarity_probs).items():
            self.found[eid] = self.found.get(eid, 0.0) + count * n
        for r in ("rare", "epic", "legendary"):
            if self.books.get(r, 0.0) >= BOOK_HAVE:
                self.mark(f"book_{r}")

    def spend_crystals(self, end_of_day: bool):
        if (self.use_sets and not self.crystal_set and self.tier >= CRYSTAL_SET_GATE
                and self.crystals >= CRYSTAL_SET_PRICE):
            self.crystals -= CRYSTAL_SET_PRICE
            self.cry_spent["sets"] += CRYSTAL_SET_PRICE
            self.crystal_set = True
            self.set_xp["deepforge"] = 0.0
            self.invalidate()
            self.mark("crystal_set")
        # Saving for the crystal set once its gate is reached, then enchants up to what books, gear and tool level allow
        reserve = CRYSTAL_SET_PRICE if (self.use_sets and not self.crystal_set and self.tier >= CRYSTAL_SET_GATE) else 0.0
        demand = self.enchant_demand() if self.use_enchants else 0.0
        e = min(max(0.0, self.crystals - reserve), max(0.0, demand - self.ench_invested))
        self.crystals -= e
        self.ench_invested += e
        self.cry_spent["enchants"] += e
        self.refresh_build()
        if end_of_day and not reserve:
            # Rolls (daily limits) from part of what is left, then the island shop
            budget = self.crystals * ROLL_SHARE
            for gate, cost, per_day, odds in ROLLS:
                if self.tier >= gate:
                    n = min(per_day, int(budget // cost))
                    self.crystals -= n * cost
                    budget -= n * cost
                    self.cry_spent["rolls"] += n * cost
                    if n:
                        self.books_rolled += n
                        self.add_books(n, self.tier, odds)
            shop = self.crystals * CRY_SHOP_SHARE
            self.crystals -= shop
            self.cry_spent["shop"] += shop
            self.refresh_build()

    def track_families(self):
        vals = {"speed": self.speed(), "extra_drops": self.family("extra_drops"), "crystals": self.family("crystals"),
                "area": self.area(), "sell": self.sell_base()}
        for k, v in vals.items():
            old = self.peak_family.get(k)
            if old is None or v > old[0] + 1e-9:
                self.peak_family[k] = (v, self.day)

    def step_active(self, h: float):
        t, speed = self.mine_choice()
        bph = self.blocks_per_hour() * speed
        blocks = bph * DEPTHS_TIME * h
        xp = blocks * TIER_XP[t - 1] * (1 + self.family("skill_xp"))
        self.mined[t] += blocks * self.items_per_block()
        self.skill_xp += xp
        self.tool_xp += blocks * TIER_XP[t - 1] * (1 + self.family("tool_xp"))
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
        # Set XP for the worn set (4 pieces): mining sets from own ore blocks, Coinmaster from items sold
        w = self.worn()
        if w:
            before = self.set_level(w)
            if w == "coinmaster":
                self.set_xp[w] += min(SET_XP_CAP_H, self.items_per_played_hour() * SET_XP_SELL) * SET_XP_FULL * h
            else:
                self.set_xp[w] += blocks * SET_XP_ORE * SET_XP_FULL
            self._fam.pop(("lvl", w), None)
            after = self.set_level(w)
            if after != before:
                self.invalidate()
            for mark in (25, 50):
                if before < mark <= after:
                    self.mark(f"{'cm' if w == 'coinmaster' else w}{mark}")
        # Enchant books: blocks + area blocks at yield, 1 in N by band with pity; first finds once per account
        acts = blocks * (1 + self.area())
        for at, rarity in FIRST_FINDS:
            if self.actions < at <= self.actions + acts:
                self.add_books(1, t, {rarity: 1.0})
        self.actions += acts
        self.add_books(acts * books_per_action("mining", t), t, BAND_ODDS[band_of(t)])
        # Crystals: Depths blocks (set + area) + island missions
        c = blocks * self.crystals_per_block()
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
        self.track_families()
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

    def hourly_without(self, enchants: bool = False, sets: bool = False) -> float:
        """Income per played hour at this state with the enchant build and/or the worn set taken off."""
        keep_b, keep_s = self.build, self.use_sets
        try:
            if enchants:
                self.build = {}
            if sets:
                self.use_sets = False
            self.invalidate()
            return self.hourly()
        finally:
            self.build, self.use_sets = keep_b, keep_s
            self.invalidate()

    def crystals_without(self, enchants: bool = False, sets: bool = False) -> float:
        keep_b, keep_s = self.build, self.use_sets
        try:
            if enchants:
                self.build = {}
            if sets:
                self.use_sets = False
            self.invalidate()
            return self.main_blocks_per_hour() * self.crystals_per_block()
        finally:
            self.build, self.use_sets = keep_b, keep_s
            self.invalidate()

    def snapshot(self) -> dict:
        w = self.worn()
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
                    cry_rate=self.main_blocks_per_hour() * self.crystals_per_block(),
                    cry_no_ench=self.crystals_without(enchants=True), cry_no_sets=self.crystals_without(sets=True),
                    hourly=self.hourly(), hourly_no_ench=self.hourly_without(enchants=True),
                    hourly_no_sets=self.hourly_without(sets=True),
                    hourly_bare=self.hourly_without(enchants=True, sets=True),
                    build=dict(self.build), speed=self.speed(), drops=self.family("extra_drops"), area=self.area(),
                    cry_family=self.family("crystals"),
                    worn=w, worn_level=self.set_level(w) if w else 0,
                    set_levels={k: self.set_level(k) for k in self.set_xp},
                    books=dict(self.books), books_h=self.books_per_hour(), books_rolled=self.books_rolled,
                    found=dict(self.found), ench_invested=self.ench_invested)


def simulate(kind: str, enchants: bool = True, sets: bool = True):
    p = Player(kind, enchants, sets)
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


def sim(kind: str, enchants: bool = True, sets: bool = True):
    key = kind if enchants and sets else (kind, enchants, sets)
    if key not in _CACHE:
        _CACHE[key] = simulate(kind, enchants, sets)
    return _CACHE[key]


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
        ("Break rate", f"bps = min({REACH_CAP}, {BPS1} x {TOOL_SPEED}^(tool-1) x (1 + {LVL_SPEED} x level + speed family))",
         "blocks/s; speed family = Swiftness + set, capped (§9.5)"),
        ("Drops per ore block", f"(1 + {FORTUNE_PER_TIER} x (tool-1) + extra drops family) x (1 + area family)",
         "Prosperity + Deepforge; Vein Rend blocks at 50% yield (§9.5)"),
        ("New ore before its tool", f"break speed x {NEW_ORE_SPEED}", "until the next tool is forged (#61 'down')"),
        ("Depths time", pct(DEPTHS_TIME), "share of a played hour spent breaking"),
        ("Dynamic price (Depths / island)", f"{DYN_DEPTHS} / {DYN_ISLAND}", "average realised multiplier"),
        ("Sell multiplier", f"1 + min({SELL_CAP:g}, sum of all sell bonuses)", "§2.7; cap applied in every booster state"),
        ("Enchant crystals", f"step s = {EN1:g} x {ENG}^(s-1) x rarity factor; level n of max M at step round(1+(n-1)x9/(M-1))",
         "enchanter.yml costs.*; tool-level gate ceil(M x level / 50)"),
        ("Book drops", "1 in " + "/".join(map(str, BOOK_ONE_IN["mining"])) + " ore blocks by band, pity "
         + "/".join(map(str, BOOK_PITY["mining"])), "enchanter.yml drops.*; first finds at 100 / 1,000 actions"),
        ("Set level bonus", f"value x level x {SET_MULT_MAX:g} / {SET_MAX}; XP to next level {SET_XP_BASE:g} x {SET_XP_G}^L",
         "wardrobe.yml levels.*; sets.yml values at 1x"),
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
    ("Delver's Garb bought", "delver"), ("Delver's Garb Lv 25 (1x)", "delver25"),
    ("Crystal set (Deepforge) bought", "crystal_set"), ("Deepforge Lv 25", "deepforge25"), ("Deepforge Lv 50", "deepforge50"),
    ("Coinmaster set bought", "coinmaster"), ("Coinmaster Lv 25 (1x)", "cm25"), ("Coinmaster Lv 50 (2x)", "cm50"),
    ("Sell bonus +100% (no boosters)", "sell100"), ("Sell bonus +200% (no boosters)", "sell200"),
    ("Median player owns a Rare book", "book_rare"), ("... an Epic book", "book_epic"),
    ("... a Legendary book", "book_legendary"), ("Vein Rend on the pickaxe", "slotted_vein_rend"),
    ("Gilded Touch on the pickaxe", "slotted_gilded_touch"), ("First pickaxe enchant at its max level", "enchant_max"),
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
            cm = f"{SET_NAME.get(s['worn'], '-')} {s['worn_level']}" if s["worn"] else "set -"
            r.append(f"{s['active_h']:.0f} h · T{s['tier']} · Lv{s['tool_level']} ({s['slots']}) · PL {s['pl']} · "
                     f"SP {s['spawners']} ({s['stack']}/block) · UL {s['ul']} · late {s['late']} · M {s['mastery']} · {cm}")
        rows.append(r)
    return table(["Stage", "Casual (played h · tier · tool level (slots) · production levels · spawners/stack limit "
                  "· level upgrades · late projects · mastery · set worn + level)", "Regular", "Grinder"], rows)


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
         "Mining T3 book, crystals", "not in the model's 4-slot build (§9.5); a money build is in §7.3"),
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


def max_mining_speed() -> float:
    """Most speed family a pickaxe can have: Swiftness V + Ember Momentum III (3 stacks) + the best mining set at Lv 50."""
    sets_best = max([set_bonus(sid, "speed", SET_MAX) for sid in SETS] or [0.0])
    return min(FAMILY_CAP["speed"], ench_value("swiftness", ench_max("swiftness"))
               + ench_value("ember_momentum", ench_max("ember_momentum")) + sets_best)


def ref_event(t: int):
    """(tool level, speed family) the regular archetype has when it unlocks tier t."""
    if t == 1:
        return 1, 0.0
    ev = event_at("regular", f"tier{t}") or event_at("grinder", f"tier{t}")
    return (ev[3], ev[4]) if ev else (TOOL_MAX_LEVEL, max_mining_speed())


def ref_loadout(t: int):
    """Tool level the regular archetype has when it unlocks tier t."""
    return ref_event(t)[0]


def ref_bph(t: int, uptime: float) -> float:
    lvl, sp = ref_event(t)
    return bps(t, lvl, sp) * 3600 * uptime


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
        lvl, sp = ref_event(t)
        raw = bps_raw(t, lvl, sp)
        b = bps(t, lvl, sp)
        rows.append([t, MINE_TIERS[t - 1] + " pickaxe", lvl, slots_for(lvl, False), f"+{sp * 100:.0f}%", f"{raw:.2f}",
                     *(num(b * 3600 * ARCHETYPES[k]["uptime"] * DEPTHS_TIME) for k in KINDS),
                     f"{b * RESPAWN_S / FIELD * 100:.1f}%"])
    ms = max_mining_speed()
    rows.append(["max", "Voidstone, Lv50, best speed build", 50, 5, f"+{ms * 100:.0f}%", f"{bps_raw(12, 50, ms):.2f}",
                 *(num(bps(12, 50, ms) * 3600 * ARCHETYPES[k]["uptime"] * DEPTHS_TIME) for k in KINDS),
                 f"{bps(12, 50, ms) * RESPAWN_S / FIELD * 100:.1f}%"])
    return table(["Tool tier", "Tool", "Typical level", "Slots", "Speed family (Swiftness + set)", "Blocks/s",
                  "Casual blocks/played h", "Regular", "Grinder", "Field depleted at once"], rows)


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


def longest_run_below(limit: float, window: int) -> int:
    """Longest run of days whose `window`-day sink ratio is below `limit` (the §7.2 'act' rule: 3 days in a row)."""
    best = cur = 0
    for d in range(window, SEASON_DAYS + 1):
        c, r, _, _ = daily_population(d, window)
        cur = cur + 1 if r / c < limit else 0
        best = max(best, cur)
    return best


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
        ("No enchants (TUNE: pickaxe build off)", {"USE_ENCHANTS": False}),
        ("No sets (TUNE: Delver, Deepforge, Coinmaster off)", {"USE_SETS": False}),
        ("Money build: Gilded Touch instead of Scholar's Insight", {"BUILD": ["swiftness", "prosperity", "vein_rend",
                                                                            "gilded_touch"]}),
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
# 5b. Enchants and sets (TUNE = E4 + W4)
# ---------------------------------------------------------------------------

ENCH_NAME = {"scholars_insight": "Scholar's Insight", "ember_momentum": "Ember Momentum", "crystal_seeker": "Crystal Seeker",
             "geode_sense": "Geode Sense", "dragonfire_blast": "Dragonfire Blast", "gilded_touch": "Gilded Touch",
             "treasure_hunter": "Treasure Hunter", "phoenix_edge": "Phoenix Edge", "giant_slayer": "Giant Slayer"}
ABBR = {"swiftness": "Swi", "scholars_insight": "Sch", "prosperity": "Pro", "vein_rend": "Vein", "gilded_touch": "Gild"}
ROMAN = ["-", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
SET_NAME = {"delver": "Delver", "deepforge": "Deepforge", "coinmaster": "Coinmaster"}

# What TUNE changed in the bundled defaults (file, key, before TUNE, after TUNE, reason). "Today" is read live.
TUNE_COMBAT_BEFORE = ([150, 200, 250, 300], [375, 500, 625, 750])
MINE_D_SUGGESTED = 6_500   # suggested only (watch): mining band D 1 in 6,500, pity 16,250
TUNE_APPLIED = [
    ("enchanter.yml", "drops.combat.one-in", "150, 200, 250, 300", "300, 350, 400, 450",
     "a regular kills ~370-500 mobs per played hour, so 1 in 150-300 gave 1.8-2.9 books/h, twice the 1-1.4 target (EN7)"),
    ("enchanter.yml", "drops.combat.pity", "375, 500, 625, 750", "750, 875, 1000, 1125",
     "pity stays 2.5x the new 1-in-N gap (EN5), so it fires in ~8% of gaps like the mine"),
]


def _build_str(b: dict) -> str:
    return " · ".join(f"{ABBR.get(e, e)} {ROMAN[lvl]}" for e, lvl in b.items()) or "-"


@register("enchant_build")
def t_enchant_build():
    rows = []
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            r.append(f"{_build_str(s['build'])} ({s['slots']} slots) · speed +{s['speed'] * 100:.0f}% · drops "
                     f"+{s['drops'] * 100:.0f}% · area +{s['area'] * 100:.0f}%")
        rows.append(r)
    return table(["Stage", "Casual: pickaxe enchants (slots) · speed / extra drops / area family", "Regular", "Grinder"],
                 rows)


@register("enchant_set_impact")
def t_enchant_set_impact():
    rows = []
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            e = s["hourly"] / s["hourly_no_ench"] - 1
            st = s["hourly"] / s["hourly_no_sets"] - 1
            ce = s["cry_rate"] / s["cry_no_ench"] - 1 if s["cry_no_ench"] else 0
            cs = s["cry_rate"] / s["cry_no_sets"] - 1 if s["cry_no_sets"] else 0
            r.append(f"{money(s['hourly'])}/h · enchants +{e * 100:.0f}% · set +{st * 100:.0f}% · crystals "
                     f"+{ce * 100:.0f}% / +{cs * 100:.0f}%")
        rows.append(r)
    return table(["Stage", "Casual: income per played h · what the enchants and the worn set add to it · "
                  "crystals per h added by enchants / set", "Regular", "Grinder"], rows)


def _set_line(s: dict) -> str:
    w = s["worn"]
    if not w:
        return "-"
    lvl = s["worn_level"]
    parts = []
    for fam, act, label in (("speed", "mining", "speed"), ("extra_drops", "mining", "drops"),
                            ("crystals", "mining", "crystals"), ("sell", "sell", "sell")):
        v = set_bonus(w, fam, lvl, activity=act)
        if v > 0:
            parts.append(f"{label} +{v * 100:.0f}%")
    return f"{SET_NAME.get(w, w)} Lv {lvl} ({', '.join(parts) or 'no bonus yet'})"


@register("set_levels")
def t_set_levels():
    rows = []
    for name, _ in STAGES:
        rows.append([name, *(_set_line(sim(k)[1][name]) for k in KINDS)])
    return table(["Stage", "Casual: set worn (4 pieces) · level · bonus now", "Regular", "Grinder"], rows)


def _dur(h: float) -> str:
    return f"{h:.1f} h" if h >= 1 else f"{h * 60:.0f} min"


@register("set_xp")
def t_set_xp():
    """Hours of play from level 0 to a set level at each archetype's real XP rate."""
    rates = []
    for k in KINDS:
        s = sim(k)[1]["Week 1"]
        rates.append(s["bph"] * DEPTHS_TIME * SET_XP_ORE)
    rows = []
    for lvl in (1, 5, 10, 25, 40, 50):
        xp = set_xp_to(lvl)
        cells = [_dur(xp / r) for r in rates]
        rows.append([lvl, f"{lvl * SET_MULT_MAX / SET_MAX:.2f}x", num(xp), *cells,
                     _dur(xp / SET_XP_CAP_H), _dur(xp * MERGED_XP_FACTOR / (SET_XP_CAP_H + rates[1]))])
    return table(["Set level", "Multiplier", "Total set XP",
                  f"Mining set, casual ({num(rates[0])} XP/h in week 1)", f"Regular ({num(rates[1])} XP/h)",
                  f"Grinder ({num(rates[2])} XP/h)", f"Coinmaster at the cap ({num(SET_XP_CAP_H)} XP/h)",
                  "Merged Deepforge · Coinmaster (regular mining + sell cap)"], rows)


@register("book_rates")
def t_book_rates():
    rows = []
    reg = ARCHETYPES["regular"]
    for b, lo in enumerate(BAND_TIERS):
        hi = (BAND_TIERS[b + 1] - 1) if b + 1 < len(BAND_TIERS) else len(MINE_TIERS)
        acts = []
        for t in (lo, hi):
            ev = event_at("regular", f"tier{t}") or event_at("grinder", f"tier{t}")
            ar = ev[5] if ev and t > 1 else 0.0
            acts.append(ref_bph(t, reg["uptime"]) * DEPTHS_TIME * (1 + ar))
        n, k = BOOK_ONE_IN["mining"][b], BOOK_PITY["mining"][b]
        bh = [a * books_per_action("mining", t) for a, t in zip(acts, (lo, hi))]
        rows.append([f"Mine {'ABCD'[b]} ({_tiers(lo, hi)})", f"1 in {n:,}", f"{k:,}", pct((1 - 1 / n) ** k),
                     f"+{(books_per_action('mining', lo) * n - 1) * 100:.0f}%", _span(acts, num),
                     _span(bh, lambda x: f"{x:.2f}"), _book_status(bh)])
    for label, one_in, pity in (("Combat (before TUNE)", *TUNE_COMBAT_BEFORE), ("Combat (bundled now)", None, None)):
        for b, lo in enumerate(BAND_TIERS):
            hi = (BAND_TIERS[b + 1] - 1) if b + 1 < len(BAND_TIERS) else len(COMBAT_TIERS)
            ks = [KILLS_PER_HOUR_BASE * reg["uptime"] * DEPTHS_TIME * (1 + 0.04 * (t - 1)) for t in (lo, hi)]
            n = (one_in or BOOK_ONE_IN["combat"])[b]
            k = (pity or BOOK_PITY["combat"])[b]
            bh = [x * books_per_action("combat", t, True, one_in, pity) for x, t in zip(ks, (lo, hi))]
            rows.append([f"{label} {'ABCD'[b]} ({_tiers(lo, hi)})", f"1 in {n:,}", f"{k:,}", pct((1 - 1 / n) ** k),
                         f"+{(books_per_action('combat', lo, True, one_in, pity) * n - 1) * 100:.0f}%",
                         _span(ks, num), _span(bh, lambda x: f"{x:.2f}"), _book_status(bh)])
    return table(["Activity · band", "Chance per action", "Pity after", "Gaps that end in pity", "Pity adds",
                  "Actions per played h (regular, blocks incl. area at yield / kills)", "Books per played h",
                  f"vs target {BOOK_TARGET[0]:g}-{BOOK_TARGET[1]:g}"], rows)


def _span(vals, fmt) -> str:
    a, b = fmt(vals[0]), fmt(vals[1])
    return a if a == b else f"{a}-{b}"


def _tiers(lo: int, hi: int) -> str:
    return f"T{lo}" if lo == hi else f"T{lo}-{hi}"


def book_rate_rows():
    """Status of every book_rates row (mine and the bundled combat values)."""
    return [r for r in t_book_rates().splitlines()[2:] if "before TUNE" not in r]


def _book_status(bh) -> str:
    lo, hi = min(bh), max(bh)
    if lo >= BOOK_TARGET[0] * 0.9 and hi <= BOOK_TARGET[1] * 1.1:
        return "ok"
    if hi <= BOOK_TARGET[1] * 1.4 and lo >= BOOK_TARGET[0] * 0.7:
        return "watch"
    return "**off**"


@register("book_finds")
def t_book_finds():
    rows = []
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            bk = s["books"]
            r.append(f"{s['books_h']:.2f}/h · {sum(bk.values()):.0f} books (C {bk['common']:.0f} U {bk['uncommon']:.0f} "
                     f"R {bk['rare']:.1f} E {bk['epic']:.1f} L {bk['legendary']:.1f}) · rolls {s['books_rolled']:.0f}")
        rows.append(r)
    return table(["Stage", "Casual: mining books per played h (drops + pity) · expected books found so far by rarity "
                  "(incl. first finds and rolls) · of them from rolls", "Regular", "Grinder"], rows)


def _cry_rate(kind: str, stage: str) -> float:
    return sim(kind)[1][stage]["cry_rate"]


@register("crystal_prices")
def t_crystal_prices():
    build_max = sum(ench_cum(e, ench_max(e)) for e in BUILD[:len(SLOT_LEVELS)])
    items = [
        ("Deepforge Plate (crystal set, mine tier 6)", CRYSTAL_SET_PRICE),
        ("Merge fee I+III (e.g. Delver · Coinmaster)", MERGE_FEE_STEP * (1 + 3 - 1)),
        ("Merge fee III+III (Deepforge · Coinmaster)", MERGE_FEE_STEP * (3 + 3 - 1)),
        ("Pickaxe build to max (4 slots: " + ", ".join(ABBR.get(e, e) for e in BUILD[:len(SLOT_LEVELS)]) + ")",
         build_max),
        (f"All gear to max (x{GEAR_MAX} the pickaxe)", build_max * GEAR_MAX),
        ("Mythic I (prestige slot)", cum_cost_cry(1, "mythic", 1)),
        ("Disenchant Scroll", SCROLL_PRICE),
        ("Adept Roll (tier 4)", ROLLS[0][1]),
        ("Master Roll (tier 7)", ROLLS[1][1]),
    ]
    rows = []
    for label, cost in items:
        c = cost
        rows.append([label, num(cost),
                     *(f"{c / _cry_rate(k, st):.0f} h" for k, st in (("casual", "Month 1"), ("regular", "Week 2"),
                                                                     ("regular", "Month 1"), ("grinder", "Week 2")))])
    return table(["Crystal price", "Crystals", "Casual, month-1 crystals/h", "Regular, week 2", "Regular, month 1",
                  "Grinder, week 2"], rows)


def _family_max_enchants(fam: str) -> tuple:
    best = {"speed": ("swiftness", "ember_momentum"), "extra_drops": ("prosperity",),
            "crystals": ("crystal_seeker", "geode_sense"), "area": ("dragonfire_blast",), "sell": ("gilded_touch",),
            "skill_xp": ("scholars_insight",), "tool_xp": ("tempering",), "rare_finds": ("treasure_hunter",),
            "mob_damage": ("phoenix_edge", "giant_slayer")}.get(fam, ())
    v = sum(ench_value(e, ench_max(e)) for e in best if ENCH.get(e))
    return v, " + ".join(f"{ENCH_NAME.get(e, e.replace('_', ' ').title())} {ROMAN[ench_max(e)]}" for e in best if ENCH.get(e))


def _family_max_sets(fam: str) -> tuple:
    best, who = 0.0, "-"
    for sid in SETS:
        v = set_bonus(sid, fam, SET_MAX, activity=None)
        if v > best:
            best, who = v, (SETS[sid].get("name") or sid)
    return best, who


@register("family_caps")
def t_family_caps():
    rows = []
    fams = [("sell", SELL_CAP), ("speed", FAMILY_CAP["speed"]), ("extra_drops", FAMILY_CAP["extra_drops"]),
            ("crystals", FAMILY_CAP["crystals"]), ("area", FAMILY_CAP["area"]), ("skill_xp", FAMILY_CAP["skill_xp"]),
            ("tool_xp", FAMILY_CAP["tool_xp"]), ("rare_finds", FAMILY_CAP["rare_finds"]),
            ("mob_damage", FAMILY_CAP["mob_damage"])]
    for fam, cap in fams:
        ev, ew = _family_max_enchants(fam)
        sv, sw = _family_max_sets(fam)
        peaks = []
        for k in ("regular", "grinder"):
            pk = sim(k)[0].peak_family.get(fam)
            peaks.append(f"+{pk[0] * 100:.0f}% (day {pk[1]})" if pk else "not modelled")
        total = ev + sv
        reached = "never by enchants + sets" if total < cap else f"**yes**: {pct(total)} >= {pct(cap)}"
        rows.append([fam.replace("_", " "), f"+{cap * 100:.0f}%", f"+{ev * 100:.0f}% ({ew or '-'})",
                     f"+{sv * 100:.0f}% ({sw})", f"{pct(total / cap)} of the cap", *peaks, reached])
    return table(["Family", "Cap", "Max from enchants on one tool", "Max from one set at Lv 50 (a merge can't stack a "
                  "family)", "Enchants + set at max", "Regular peak (first day)", "Grinder peak (first day)",
                  "Cap reached?"], rows)


def _combat_config():
    """Per combat tier: (zone, average drops per kill, item, sell price, crystals per kill) from mobs.yml + sell.yml."""
    text = _read(MOBS_YML) or ""
    tiers = {}
    tier = None
    for line in text.splitlines():
        m = re.match(r"\s*#\s*Tier\s+(\d+)\s+(.+)$", line)
        if m:
            tier = int(m.group(1))
            continue
        m = re.match(r"\s{2}([a-z_]+):\s*(\{.*\})\s*$", line)
        if m and tier is not None and "glowing" not in m.group(2) and "drops" in m.group(2):
            d = _flow(m.group(2))
            drops = d.get("drops") or []
            avg = sum((x.get("min", 1) + x.get("max", 1)) / 2 * x.get("chance", 1) for x in drops)
            item = drops[0].get("item") if drops else None
            tiers.setdefault(tier, []).append((avg, item, float(d.get("crystal-chance", 0)) * d.get("crystal-amount", 1)))
    prices = ((load_yaml(SELL_YML).get("custom-items") or {}).get("depths-combat") or {})
    out = {}
    for t, mobs in tiers.items():
        avg = sum(m[0] for m in mobs) / len(mobs)
        item = mobs[0][1]
        pr = prices.get(item)
        pr = pr.get("price") if isinstance(pr, dict) else pr
        out[t] = (avg, item, pr, sum(m[2] for m in mobs) / len(mobs))
    return out


def combat_parity(t: int) -> tuple:
    """(model drop value per kill, model crystals per kill) at combat tier t (same money and crystals per hour as the mine)."""
    reg = ARCHETYPES["regular"]
    bph = ref_bph(t, reg["uptime"]) * DEPTHS_TIME
    kills = KILLS_PER_HOUR_BASE * reg["uptime"] * DEPTHS_TIME * (1 + 0.04 * (t - 1))
    per_kill = bph * fortune(t) * price(t) / kills
    return per_kill, crystal_rate(t) * per_kill / price(t)


def enchanted_mine_factor(t: int) -> float:
    """Items per ore block with the regular's enchants and set when it unlocks tier t, over the bare tool's fortune."""
    ev = event_at("regular", f"tier{t}") or event_at("grinder", f"tier{t}")
    if not ev or t == 1:
        return 1.0
    return (fortune(t) + ev[6]) * (1 + ev[5]) / fortune(t)


def combat_rows():
    """(tier, zone, avg drops, item, price, config value/kill, base parity, enchanted mine parity, config crystals,
    model crystals) per combat tier."""
    cfg_rows = _combat_config()
    out = []
    for t in range(1, len(COMBAT_TIERS) + 1):
        model_v, model_c = combat_parity(t)
        c = cfg_rows.get(t)
        if not c or c[2] is None:
            out.append((t, COMBAT_TIERS[t - 1], None, None, None, None, model_v, model_v * enchanted_mine_factor(t), None,
                        model_c))
            continue
        avg, item, pr, cry = c
        out.append((t, COMBAT_TIERS[t - 1], avg, item, pr, avg * pr, model_v, model_v * enchanted_mine_factor(t), cry,
                    model_c))
    return out


def combat_ok(r) -> bool:
    _, _, avg, _, _, v, base, ench_v, cry, model_c = r
    if v is None:
        return False
    return 0.85 <= v / base <= 1.35 and v / ench_v <= 1.15 and (not model_c or 0.75 <= cry / model_c <= 1.35)


@register("combat_drops")
def t_combat_drops():
    rows = []
    for r in combat_rows():
        t, zone, avg, item, pr, v, base, ench_v, cry, model_c = r
        if v is None:
            rows.append([t, zone, "not found", "-", "-", money(base), money(ench_v), "-", "-", f"{model_c:.2f}", "check"])
            continue
        rows.append([t, zone, f"{avg:g}", f"`{item}` {money(pr)}", money(v), money(base), money(ench_v),
                     f"{v / base:.2f} / {v / ench_v:.2f}", f"{cry:.2f}", f"{model_c:.2f}", "ok" if combat_ok(r) else "check"])
    return table(["Tier", "Zone", "Drops per kill (mobs.yml)", "Item, sell.yml price", "Value per kill (configs)",
                  "Parity with the bare mine", "Parity with the regular's enchanted mine", "Configs / bare / enchanted",
                  "Crystals per kill (mobs.yml)", "Model crystals (bare mine)", "Status"], rows)


def shop_sells_combat_items() -> list:
    """Combat drop ids that /shop sells (must be none: they would be a buy-low / sell-high loop)."""
    combat = set(((load_yaml(SELL_YML).get("custom-items") or {}).get("depths-combat") or {}).keys())
    shop = load_yaml(SHOP_YML).get("categories") or {}
    sold = set()
    for cat in shop.values():
        for item in ((cat or {}).get("items") or {}):
            sold.add(item)
    return sorted(combat & sold)


@register("tune_changes")
def t_tune_changes():
    rows = []
    for file, key, before, after, why in TUNE_APPLIED:
        today = config_value(file, key)
        rows.append([f"`{file}`", f"`{key}`", _fmt(today), before, after,
                     "applied" if _fmt(today).replace(",", "") == after.replace(",", "") else "**not applied**", why])
    return table(["File", "Key", "Bundled default today", "Before TUNE", "Suggested", "Status", "Why (one line)"], rows)


# ---------------------------------------------------------------------------
# 6. Current configs (read from the repo) vs this model
# ---------------------------------------------------------------------------

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
        ("enchanter.yml", "costs.step-base", 100, "EN14"), ("enchanter.yml", "costs.step-growth", 1.8, "EN14"),
        ("enchanter.yml", "drops.mining.one-in", [2000.0, 3000.0, 4000.0, 5000.0], "EN7; band D is 'watch' (§9.7)"),
        ("enchanter.yml", "drops.mining.pity", [5000.0, 7500.0, 10000.0, 12500.0], "EN5: 2.5x the gap"),
        ("enchanter.yml", "drops.combat.one-in", [300.0, 350.0, 400.0, 450.0], "TUNE (§9.7)"),
        ("enchanter.yml", "drops.combat.pity", [750.0, 875.0, 1000.0, 1125.0], "TUNE: 2.5x the gap"),
        ("enchanter.yml", "rolls.adept.price", 4_000, "EN9"), ("enchanter.yml", "rolls.master.price", 25_000, "EN9"),
        ("enchanter.yml", "caps.speed", 200, "EN16 (6 blocks/s is the real limit)"),
        ("enchanter.yml", "caps.extra-drops", 200, "EN16"), ("enchanter.yml", "caps.crystals", 200, "EN16"),
        ("enchanter.yml", "caps.area", 100, "EN16 (max +54%)"),
        ("wardrobe.yml", "levels.base-xp", 200, "WD14"), ("wardrobe.yml", "levels.growth", 1.10, "WD14"),
        ("wardrobe.yml", "levels.max-multiplier", 2.0, "WD14"), ("wardrobe.yml", "merge.fee-per-step", 5_000, "WD23"),
        ("wardrobe.yml", "xp.actions.depths-ore", 1, "WD10"), ("wardrobe.yml", "xp.actions.sell-item", 1, "WD10"),
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
    mine_d = BOOK_ONE_IN["mining"][3]
    rows.append(["`enchanter.yml`", "`drops.mining.one-in` (band D) / `drops.mining.pity` (band D)",
                 f"{mine_d:,} / {BOOK_PITY['mining'][3]:,}", f"{MINE_D_SUGGESTED:,} / {int(MINE_D_SUGGESTED * 2.5):,}",
                 "watch, not applied: with Vein Rend II-III a regular gets 1.35-1.9 books/h at tiers 10-12 (target 1-1.4); "
                 "the plan counts area blocks at yield on purpose (open question)"])
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

    def chk(name, ok, detail, near=False):
        """near = missed by less than 10% of the limit: WATCH (explained in the text), not FAIL."""
        rows.append([name, "PASS" if ok else ("WATCH" if near else "FAIL"), detail])

    s = sim("regular")[1]
    e, te = s["Month 3"]["earned"], s["Month 3"]["total_earned"]
    isl = e["island"] + e["spawners"]
    chk("Depths share 40-60% (regular, season)", 0.40 <= e["depths"] / te <= 0.60, pct(e["depths"] / te))
    chk("Island + spawner share 25-45% (regular, season)", 0.25 <= isl / te <= 0.45, pct(isl / te))
    chk("Spawners 8-25% of income (regular, season)", 0.08 <= e["spawners"] / te <= 0.25, pct(e["spawners"] / te))
    chk("Events share 10-20% (regular, season)", 0.10 <= e["events"] / te <= 0.20, pct(e["events"] / te))
    shares = [sim(k)[1]["Month 3"]["earned"]["interest"] / sim(k)[1]["Month 3"]["total_earned"] for k in KINDS]
    chk("Interest <= 5% of income (all, season)", all(x <= 0.05 for x in shares),
        ", ".join(pct(x, 1) for x in shares), near=all(x <= 0.055 for x in shares))
    top1 = sim("grinder")[1]["First hour"]["total_earned"]
    chk("First hour 30k-100k (grinder)", 30_000 <= top1 <= 100_000, money(top1))
    w2 = sim("grinder")[1]["Week 2"]["total_earned"]
    chk("Week 2 within 0.5x-2x of $500M (grinder)", 250e6 <= w2 <= 1e9, money(w2))
    m2 = sim("grinder")[1]["Month 2"]["total_earned"]
    chk("Month 2 within 0.5x-2x of $50B (grinder)", 25e9 <= m2 <= 100e9, money(m2))
    ss = season_sink_ratio()
    chk("Sink ratio over the whole season >= 80%", ss >= 0.80, pct(ss))
    w14 = min_sink_ratio(14)
    run = longest_run_below(0.60, 14)
    chk("Sink ratio (14-day average) not below 60% for 3 days in a row ('act', §7.2)", run < 3,
        f"min {pct(w14[0])} (day {w14[1]}), longest run below 60%: {run} day{'s' if run != 1 else ''}")
    lv5 = event_at("regular", "toollvl5")
    chk("Slot 1 within ~1-2 h (regular)", lv5 is not None and lv5[1] <= 2, when("regular", "toollvl5"))
    lv45 = event_at("regular", "toollvl45")
    chk("Slot 4 in week 3-8 (regular)", lv45 is not None and 14 <= lv45[2] <= 56, when("regular", "toollvl45"))
    chk("Max blocks/s under reach cap", bps_raw(12, 50, max_mining_speed()) <= REACH_CAP,
        f"{bps_raw(12, 50, max_mining_speed()):.2f} <= {REACH_CAP}")
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
    # TUNE: enchants and sets
    br = book_rate_rows()
    chk("Books per played h (regular): no band off the 1-1.4 target (mine + combat, §9.7)",
        not any("**off**" in r for r in br), f"{sum('| ok |' in r for r in br)} ok, {sum('| watch |' in r for r in br)} watch")
    caps = [r for r in t_family_caps().splitlines()[2:] if "**yes**" in r]
    chk("No family cap reached by enchants + sets alone (§9.8)", not caps, f"{len(caps)} families at the cap")
    sell_top = max(sim(k)[0].peak_family.get("sell", (0, 0))[0] for k in KINDS)
    chk("+300% sell cap: highest sell bonus without boosters stays under 2/3 of the cap", sell_top <= SELL_CAP * 2 / 3,
        f"+{sell_top * 100:.0f}% (grinder, month 3)")
    dv, d25 = event_at("regular", "delver"), event_at("regular", "delver25")
    chk("Mining set Lv 25 (1x) within 4-10 played h of buying it (regular, WARDROBE §3.2: ~6 h)",
        dv is not None and d25 is not None and 4 <= d25[1] - dv[1] <= 10,
        f"{d25[1] - dv[1]:.1f} h (bought at {dv[1]:.1f} h)" if dv and d25 else "not this season")
    cmb, cm50 = event_at("regular", "coinmaster"), event_at("regular", "cm50")
    chk("Coinmaster Lv 50 within 40 played h of buying it (regular)",
        cmb is not None and cm50 is not None and cm50[1] - cmb[1] <= 40,
        f"{cm50[1] - cmb[1]:.0f} h" if cmb and cm50 else "not this season")
    df = event_at("regular", "crystal_set")
    chk("Deepforge (◆25,000) bought in week 2-3 (regular, WARDROBE §2.4)", df is not None and 8 <= df[2] <= 21,
        when("regular", "crystal_set"))
    cr = combat_rows()
    chk("Combat drop value per kill (sell.yml x mobs.yml) matches the mine (§9.9)", all(combat_ok(r) for r in cr),
        f"{sum(combat_ok(r) for r in cr)}/{len(cr)} tiers ok")
    loops = shop_sells_combat_items()
    chk("/shop sells no Depths combat drop (buy > sell)", not loops, ", ".join(loops) or "none")
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
    # enchant costs match ENCHANTS §5 (EN14) and the set curve WARDROBE §3.2 (WD14)
    assert RARE_V_CUM[5] == 28_272 and cum_cost_cry(5, "common", 5) == 14_136 and cum_cost_cry(10, "rare", 10) == 44_506
    assert cum_cost_cry(3, "legendary", 3) == 54_564 and cum_cost_cry(1, "mythic", 1) == 79_344
    assert round(set_xp_to(SET_MAX)) == 232_782 and abs(SELL_SET_MAX - 0.50) < 1e-9
    assert gate_level(5, 5) == 1 and gate_level(5, 30) == 3 and gate_level(5, 45) == 5 and gate_level(10, 15) == 3
    assert abs(ench_value("vein_rend", 3) - 0.48) < 1e-9 and abs(SELL_GILDED_MAX - 0.15) < 1e-9
    # books: pity can only add books; first finds are paid once
    assert books_per_action("mining", 1) > books_per_action("mining", 1, pity=False)
    for k in KINDS:
        p = sim(k)[0]
        assert all(0 <= p.build.get(e, 0) <= ench_max(e) for e in BUILD)
        assert p.family("speed") <= FAMILY_CAP["speed"] + 1e-9 and p.family("crystals") <= FAMILY_CAP["crystals"] + 1e-9
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
