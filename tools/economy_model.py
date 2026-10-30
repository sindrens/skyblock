#!/usr/bin/env python3
"""Phoenix Skyblock economy model (M3.0 balance sheet, DRAFT).

Reproduces every table in docs/ECONOMY_BALANCE.md from the formulas and
parameters below. Standard library only.

Usage:
  python3 tools/economy_model.py                 # print all tables (markdown)
  python3 tools/economy_model.py --table NAME    # print one table
  python3 tools/economy_model.py --write  docs/ECONOMY_BALANCE.md   # refresh tables in the doc
  python3 tools/economy_model.py --check  docs/ECONOMY_BALANCE.md   # exit 1 if the doc is out of date

The doc holds each table between markers:
  <!-- BEGIN table:NAME -->  ...  <!-- END table:NAME -->

All money values are whole dollars (the plugin stores money as a long).
Every parameter here is a proposal; the live values go into typed configs.
"""

from __future__ import annotations

import argparse
import math
import re
import sys

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
TIER_XP = [1, 1.5, 2.5, 4, 6, 9, 13, 19, 28, 40, 58, 85]          # WORLD.md §4.2
UNLOCK_LEVEL = [0, 5, 10, 15, 20, 25, 30, 35, 42, 50, 58, 66]      # WORLD.md §4.2
COMBAT_UNLOCK = [0, 6, 12, 18, 24, 30, 38, 46, 55, 66]             # proposal (spread to 66)

# Sell price of one Depths drop (mine reference):
#   price(t) = P1 * PMA^(min(t, 8) - 1) * PMB^max(0, t - 8)
# Vanilla tiers 1-8 grow a bit slower than XP (x1.35 vs x1.45); custom tiers 9-12 are the big-money endgame (x2.4).
P1 = 20
PMA = 1.35
PMB = 2.4

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
SELL_BONUS_PER_TIER = 0.03   # proxy for Sell Multiplier upgrade + Merchant set + boosters
SELL_BONUS_CAP = 3.00        # max total sell multiplier = x4.00 (+300%, owner #46, only with the best of everything)

# Forest and combat parity: same money per hour as the mine at the same tier.
LOGS_PER_BLOCK_TIME = 1.0    # a log takes as long as an ore block
KILLS_PER_HOUR_BASE = 700    # shared mobs, ~4-5 s per kill incl. walking

# Skill XP for level L -> L+1:
#   x(L) = SK_A * SK_G1^min(L, SK_K) * SK_G2^clamp(L - SK_K, 0, SK_K2 - SK_K) * SK_G3^max(0, L - SK_K2)
# (steep early, flatter after level 35 because late tiers are 7-8 levels apart; after the last tier unlock (66)
#  each level costs only 2% more, up to the max level 150 (owner #65))
SK_A = 600
SK_G1 = 1.20
SK_K = 35
SK_G2 = 1.13
SK_K2 = 66
SK_G3 = 1.02
SKILL_MAX = 150
SKILL_LATE_MARKS = [75, 100, 125, 150]   # levels after the last tier unlock shown in the skill table

# Tool XP to reach level L (1..50): XT(L) = TL_A * (TL_G^(L-1) - 1) / (TL_G - 1)
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

# Island production: items per production hour Q(PL) = Q0 * QG^PL, each worth ISL_PRICE x P(tier-1).
# PL = levels across Generator, Crop Growth, Spawner Rate, Harvest Yield, Mob Loot (+ spawner stacks).
Q0 = 170
QG = 1.12
ISL_PRICE = 0.5
PL_PER_TIER = 6          # tasks gate production levels: max PL = 6 x Depths tier
# Production level k costs C_p(k) = CP1 * CPG^(k-1); players only buy it while it pays back
# within PAYBACK_DAYS of their production hours (nobody buys a bad deal).
CP1 = 2_500
CPG = 1.30
PAYBACK_DAYS = 3.5

# Island level upgrades (no income, give island level points, REVIEW A1): 12 upgrades x 12 levels,
# level n needs Depths tier >= n (stand-in for its tasks). Price of level n of upgrade u:
#   C_u(u, n) = W_u * LV_H * LV_HG^(n-1) * REF(n)
LV_H = 0.067
LV_HG = 1.49
LV_MAX = 12
LEVEL_UPGRADES = [("Warp Slots", 0.5), ("Island Vault", 0.6), ("Member Slots", 0.8), ("Fuel Tank", 0.8),
                  ("XP Boost", 0.9), ("Lumberjack", 0.9), ("Spawner Stack Limit", 1.0), ("Island Size", 1.0),
                  ("Bank Capacity", 1.1), ("Bank Interest", 1.2), ("Mine Fortune", 1.4), ("Sell Multiplier", 1.8)]

# Late projects (Phase 5+): 3 outposts, each = unlock + 3 biomes x 5 tiers + Fusion I/II = 18 steps.
# Outpost o is gated by Depths tier LATE_GATE[o]; step j (0..17) costs LATE_H * LATE_G^j * REF(gate).
LATE_GATE = [7, 9, 11]
LATE_H = 1.0
LATE_G = 1.22

# Mastery (proposal, owner decision): after level 12 an upgrade can be mastered endlessly;
# mastery level m (1..) costs MASTERY_H * MASTERY_G^(m-1) * REF(tier) and gives a tiny bonus + level points.
MASTERY = True
MASTERY_H = 4.0
MASTERY_G = 1.12

# Quests / events / KOTH / envoys pay EVENT_SHARE x the player's Depths income per active hour
EVENT_SHARE = 0.30

# Player-driven sinks
SHOP_SHARE = 0.04        # share of earned money spent in /shop (blocks, decor)
AH_VOLUME = 0.10         # share of earned money that changes hands on the AH
AH_FEE = 0.01            # listing fee (decided)
AH_TAX = 0.05            # sales tax (decided)

# Banks
PERSONAL_RATE = 0.0015
ISLAND_RATE = 0.0025
EARLY_FEE = 0.02              # early-withdrawal fee (a rule; the model assumes nobody withdraws early)
# Personal bank tier k (1..10): capacity, cost to unlock (sink), payout cap
PB_CAP1, PB_CAPG = 100_000, 5.0
PB_COST_SHARE = 0.02          # unlocking tier k costs 2% of its capacity (tier 1 free)
PB_PAYOUT1, PB_PAYOUTG = 150, 3.2
# Island bank level l (0..10, Bank Capacity / Bank Interest upgrades in Phase 5)
IB_CAP0, IB_CAPG = 1_000_000, 4.0
IB_PAYOUT0, IB_PAYOUTG = 2_500, 3.0

# Player archetypes. save = share of income kept as savings (never spent on upgrades; banked);
# bank_share = share of spare cash the player also parks in the bank between purchases.
ARCHETYPES = {
    "casual": dict(hours_day=1.5, first_day=2.0, uptime=0.60, prod_hours=8, rank=0.00, save=0.20, bank_share=0.5),
    "active": dict(hours_day=4.0, first_day=5.0, uptime=0.75, prod_hours=14, rank=0.04, save=0.15, bank_share=0.7),
    "top":    dict(hours_day=9.0, first_day=12.0, uptime=0.85, prod_hours=22, rank=0.08, save=0.10, bank_share=0.9),
}
# Daily unique players behind a 300 peak (avg concurrency ~112, peak ~2.5x)
POPULATION = {"casual": 540, "active": 270, "top": 90}

STAGES = [("First hour", None), ("Day 1", 1), ("Day 3", 3), ("Week 1", 7),
          ("Week 2", 14), ("Month 1", 30), ("Month 2", 60), ("Month 3", 90)]

# Phase-3 targets for top players (cumulative earned), to confirm with the owner
TARGETS_TOP = {"First hour": 50_000, "Day 3": 5_000_000, "Week 2": 500_000_000, "Month 2": 50_000_000_000}

STEP_H = 0.1  # simulation step in active hours

# Island crystals (replaced tokens, O8): average crystals per block/log/kill-equivalent = TK1 * TKG^(t-1) (rolled as a chance)
TK1 = 0.05
TKG = 1.30
# Custom enchant level L (1..EN_MAX) costs EN1 * ENG^(L-1) crystals
EN1 = 100
ENG = 1.80
EN_MAX = 10

# Dynamic prices (M3.4): each item sold lowers the multiplier m by `impact`;
# every 10 min the gap to base closes by 2%: m <- 1 - (1 - m) * 0.98.
RECOVERY_PER_10MIN = 0.02
TARGET_DEPRESSION = 0.15   # at expected volume, m settles at 1 - 0.15 = 0.85 (= DYN_DEPTHS)


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


def slots_for(level: int, prestiged: bool) -> int:
    return sum(1 for s in SLOT_LEVELS if level >= s) + (1 if prestiged else 0)


def bps(tool: int, level: int, slots: int) -> float:
    raw = BPS1 * TOOL_SPEED ** (tool - 1) * (1 + LVL_SPEED * level) * (1 + AREA_PER_SLOT * slots)
    return min(REACH_CAP, raw)


def fortune(tool: int) -> float:
    return 1 + FORTUNE_PER_TIER * (tool - 1)


def sell_mult(tier: int, rank: float) -> float:
    return 1 + min(SELL_BONUS_CAP, rank + SELL_BONUS_PER_TIER * (tier - 1))


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
        self.active_h = 0.0
        self.earned = {"depths": 0.0, "island": 0.0, "events": 0.0, "interest": 0.0}
        self.spent = {"forge": 0.0, "production": 0.0, "levels": 0.0, "late": 0.0, "mastery": 0.0,
                      "bank_tiers": 0.0, "shop": 0.0, "ah_tax": 0.0}
        self.events_log: list[tuple[str, float, int, int]] = []  # (event, active h, day, tool level)
        self.day = 1

    # rates -----------------------------------------------------------------
    def slots(self) -> int:
        return slots_for(self.tool_level, self.prestiged)

    def blocks_per_hour(self) -> float:
        return bps(self.tool, self.tool_level, self.slots()) * 3600 * self.a["uptime"]

    def depths_rate(self) -> float:
        """Money per active hour from The Depths."""
        return (self.blocks_per_hour() * DEPTHS_TIME * fortune(self.tool) * price(self.tier)
                * DYN_DEPTHS * sell_mult(self.tier, self.a["rank"]))

    def island_tier(self) -> int:
        return max(1, self.tier - 1)

    def island_rate(self) -> float:
        """Money per production hour from the island."""
        return (q_items(self.pl) * ISL_PRICE * price(self.island_tier()) * DYN_ISLAND
                * sell_mult(self.tier, self.a["rank"]))

    # actions ---------------------------------------------------------------
    def earn(self, src: str, amount: float):
        self.earned[src] += amount
        net = amount * (1 - SHOP_SHARE - AH_VOLUME * (AH_FEE + AH_TAX))
        self.savings += net * self.a["save"]
        self.cash += net * (1 - self.a["save"])
        self.spent["shop"] += amount * SHOP_SHARE
        self.spent["ah_tax"] += amount * AH_VOLUME * (AH_FEE + AH_TAX)

    def mark(self, name: str):
        self.events_log.append((name, self.active_h, self.day, self.tool_level))

    @property
    def ul(self) -> int:
        return sum(self.ul_levels)

    def prod_gain_per_day(self) -> float:
        """Extra money per day from one more production level."""
        nxt = (q_items(self.pl + 1) - q_items(self.pl)) * ISL_PRICE * price(self.island_tier()) * DYN_ISLAND \
            * sell_mult(self.tier, self.a["rank"])
        return nxt * self.a["prod_hours"]

    def pay(self, what: str, cost: float) -> bool:
        if self.cash < cost:
            return False
        self.cash -= cost
        self.spent[what] += cost
        return True

    def buy(self):
        while True:
            # 1. Forge the next tool as soon as its tier is unlocked
            if self.tool < self.tier and self.pay("forge", forge_cost(self.tool + 1)):
                self.tool += 1
                self.mark(f"tool{self.tool}")
                continue
            # 2. Production while it pays back within PAYBACK_DAYS
            if (self.pl < PL_PER_TIER * self.tier
                    and prod_cost(self.pl + 1) <= self.prod_gain_per_day() * PAYBACK_DAYS
                    and self.pay("production", prod_cost(self.pl + 1))):
                self.pl += 1
                continue
            # 3. Cheapest available island level upgrade
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
                else:
                    self.late[best[2]] += 1
                continue
            # 4. Personal bank tier (keeps 2x the price as a buffer)
            if self.pbt < 10 and self.cash >= 2 * pb_cost(self.pbt + 1):
                self.pay("bank_tiers", pb_cost(self.pbt + 1))
                self.pbt += 1
                continue
            # 5. Mastery once every level upgrade is maxed (proposal)
            if (MASTERY and best is None and all(n == LV_MAX for n in self.ul_levels)
                    and self.pay("mastery", mastery_cost(self.mastery + 1, self.tier))):
                self.mastery += 1
                continue
            break

    def step_active(self, h: float):
        bph = self.blocks_per_hour()
        blocks = bph * DEPTHS_TIME * h
        xp = blocks * TIER_XP[self.tier - 1]
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
        d = self.depths_rate() * h
        self.earn("depths", d)
        self.earn("events", d * EVENT_SHARE)
        self.earn("island", self.island_rate() * h)
        self.active_h += h
        self.buy()

    def end_of_day(self, active_today: float):
        offline_prod = max(0.0, self.a["prod_hours"] - active_today)
        self.earn("island", self.island_rate() * offline_prod)
        self.buy()
        # Interest: the player banks a share of spare cash; min-balance rule => whole day balance.
        banked = self.savings + self.cash * self.a["bank_share"]
        pb = min(banked, pb_cap(self.pbt))
        ib_level = min(10, self.ul_levels[9])      # Bank Interest level (Capacity follows it closely)
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

    def snapshot(self) -> dict:
        return dict(active_h=self.active_h, tier=self.tier, tool=self.tool, tool_level=self.tool_level,
                    slots=self.slots(), pl=self.pl, ul=self.ul, late=sum(self.late), mastery=self.mastery,
                    pbt=self.pbt, ibl=self.ibl,
                    cash=self.cash + self.savings, earned=dict(self.earned), spent=dict(self.spent),
                    total_earned=self.total_earned(), total_spent=self.total_spent(),
                    depths_rate=self.depths_rate(), island_rate=self.island_rate(),
                    bph=self.blocks_per_hour(),
                    hourly=self.depths_rate() * (1 + EVENT_SHARE) + self.island_rate()
                    * self.a["prod_hours"] / self.a["hours_day"])


def simulate(kind: str):
    p = Player(kind)
    snaps = {}
    daily = []  # per-day dict of earned/spent deltas
    for day in range(1, SEASON_DAYS + 1):
        p.day = day
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
            cash=p.cash + p.savings))
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

KINDS = ["casual", "active", "top"]


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
        ("Fortune", f"drops/block = 1 + {FORTUNE_PER_TIER} x (tool-1)", ""),
        ("Depths time", pct(DEPTHS_TIME), "share of a played hour spent breaking"),
        ("Uptime casual / active / top", " / ".join(pct(ARCHETYPES[k]['uptime']) for k in KINDS), "breaking vs walking/menus"),
        ("Dynamic price (Depths / island)", f"{DYN_DEPTHS} / {DYN_ISLAND}", "average realised multiplier"),
        ("Sell multiplier", f"1 + min({SELL_BONUS_CAP}, rank + {SELL_BONUS_PER_TIER} x (tier-1))", "rank 0/4/8% by archetype"),
        ("Skill XP per level", f"x(L) = {SK_A} x {SK_G1}^min(L,{SK_K}) x {SK_G2}^clamp(L-{SK_K},0,{SK_K2 - SK_K}) x {SK_G3}^max(0,L-{SK_K2})",
         f"total = sum of steps; max level {SKILL_MAX}"),
        ("Tool XP to level L", f"XT(L) = {TL_A} x ({TL_G}^(L-1) - 1) / ({TL_G} - 1)", "XP per block = tier XP"),
        ("Forge money", f"F(t) = {money(F1)} x {FG}^(t-2), t >= 2", "tier 1 = free starter kit"),
        ("Forge materials", f"M(t) = {MAT1} x {MATG}^(t-2) drops of tier t-1", ""),
        ("Island output", f"Q(PL) = {Q0} x {QG}^PL items per production hour", f"each worth {ISL_PRICE} x P(tier-1)"),
        ("Production level", f"C_p(k) = {money(CP1)} x {CPG}^(k-1)", f"max {PL_PER_TIER} x tier; bought if payback <= {PAYBACK_DAYS:g} days"),
        ("Level upgrade", f"C_u(u,n) = W_u x {LV_H} x {LV_HG}^(n-1) x REF(n)", f"12 upgrades x {LV_MAX} levels, level n needs tier n"),
        ("Late projects", f"C_late(o,j) = {LATE_H} x {LATE_G}^j x REF(gate_o)", f"3 outposts x 18 steps, gates tier {'/'.join(map(str, LATE_GATE))}"),
        ("Mastery (proposal)", f"C_m(m) = {MASTERY_H} x {MASTERY_G}^(m-1) x REF(tier)", "endless, after all levels are maxed"),
        ("Events/quests", f"{EVENT_SHARE} x Depths income per played hour", "rewards defined per tier"),
        ("Shop spend", pct(SHOP_SHARE) + " of earned", "blocks, decor, seeds"),
        ("AH", f"{pct(AH_VOLUME)} of earned traded, fee {pct(AH_FEE)} + tax {pct(AH_TAX)}", "only fee+tax leave the economy"),
        ("Interest", f"personal {PERSONAL_RATE * 100:.2f}% / island {ISLAND_RATE * 100:.2f}% per 3 h", "capped per payout"),
        ("Savings casual / active / top", " / ".join(pct(ARCHETYPES[k]['save']) for k in KINDS), "of income, banked, never spent"),
        ("Production hours/day casual / active / top", " / ".join(str(ARCHETYPES[k]['prod_hours']) for k in KINDS), "online + AFK + fuel"),
        ("Played hours/day casual / active / top", " / ".join(f"{ARCHETYPES[k]['hours_day']:g} (day 1: {ARCHETYPES[k]['first_day']:g})" for k in KINDS), ""),
        ("Daily uniques casual / active / top", " / ".join(str(POPULATION[k]) for k in KINDS), "avg ~112 online, peak ~300"),
    ]
    return table(["Parameter", "Formula / value", "Note"], rows)


@register("targets")
def t_targets():
    rows = []
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            r.append(f"{money(s['hourly'])}/h · {money(s['total_earned'])}")
        rows.append(r)
    return table(["Stage", "Casual (income per played hour · earned so far)",
                  "Active", "Top"], rows)


@register("targets_check")
def t_targets_check():
    rows = []
    for name, target in TARGETS_TOP.items():
        top = sim("top")[1][name]["total_earned"]
        act = sim("active")[1][name]["total_earned"]
        rows.append([name, money(target), money(top), f"{top / target:.1f}x", money(act), f"{act / target:.1f}x"])
    return table(["Stage", "Owner target (top, earned so far)", "Model top", "Top / target",
                  "Model active", "Active / target"], rows)


@register("progress")
def t_progress():
    rows = []
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            r.append(f"{s['active_h']:.0f} h · T{s['tier']} · Lv{s['tool_level']} ({s['slots']} slots) · "
                     f"PL {s['pl']} · UL {s['ul']} · late {s['late']} · M {s['mastery']}")
        rows.append(r)
    return table(["Stage", "Casual (played h · Depths tier · tool level · production levels · level upgrades "
                  "· late projects · mastery)", "Active", "Top"], rows)


@register("shares")
def t_shares():
    rows = []
    for k in KINDS:
        for name in ("Week 1", "Month 1", "Month 3"):
            s = sim(k)[1][name]
            e, te = s["earned"], s["total_earned"]
            rows.append([k, name, pct(e["depths"] / te), pct(e["island"] / te), pct(e["events"] / te),
                         pct(e["interest"] / te, 1)])
    rows.append(["**target**", "season", "~50%", "~35%", "~15%", "<= 5%"])
    return table(["Archetype", "Earned up to", "Depths", "Island", "Events/quests", "Interest"], rows)


def ref_loadout(t: int):
    """Tool level the active archetype has when it unlocks tier t (and the tool for it)."""
    if t == 1:
        return 1
    ev = event_at("active", f"tier{t}") or event_at("top", f"tier{t}")
    return ev[3] if ev else TOOL_MAX_LEVEL


def ref_bph(t: int, uptime: float) -> float:
    lvl = ref_loadout(t)
    return bps(t, lvl, slots_for(lvl, False)) * 3600 * uptime


@register("mine")
def t_mine():
    rows = []
    for t in range(1, 13):
        bph = ref_bph(t, ARCHETYPES["active"]["uptime"]) * DEPTHS_TIME
        per_h = bph * fortune(t) * price(t) * DYN_DEPTHS
        rows.append([t, MINE_TIERS[t - 1], UNLOCK_LEVEL[t - 1], TIER_XP[t - 1], money(price(t)),
                     f"x{price(t) / price(max(1, t - 1)):.2f}" if t > 1 else "-",
                     num(bph), money(per_h)])
    return table(["Tier", "Ore", "Mining lv", "XP", "Sell price / drop", "vs prev", "Blocks per played h (active)",
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
        # parity: per-kill value = mine $/h at tier / kills per hour
        bph = ref_bph(t, ARCHETYPES["active"]["uptime"]) * DEPTHS_TIME
        kills = KILLS_PER_HOUR_BASE * ARCHETYPES["active"]["uptime"] * DEPTHS_TIME * (1 + 0.04 * (t - 1))
        per_kill = bph * fortune(t) * price(t) / kills
        rows.append([t, COMBAT_TIERS[t - 1], COMBAT_UNLOCK[t - 1], num(kills), money(per_kill),
                     f"{per_kill / price(t):.1f}x"])
    return table(["Tier", "Zone", "Combat lv (proposal)", "Kills per played h (active)",
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
        sp = COMBAT_TIERS[t - 1] + " mob drop" if t <= 10 else "-"
        rows.append([t, crops[t - 1], money(v), g, gv, sp, money(v) if t <= 10 else "-"])
    return table(["Tier", "Crop (Farming unlock)", "Crop item", "Generator block", "Generator drop",
                  "Spawner type (by combat tier)", "Spawner drop"], rows)


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
                  "Active", "Top", "Field depleted at once"], rows)


@register("skill")
def t_skill():
    rows = []
    for t in range(2, 13):
        L = UNLOCK_LEVEL[t - 1]
        rows.append([t, MINE_TIERS[t - 1], L, num(skill_xp_to(L)), *(when(k, f"tier{t}") for k in KINDS)])
    for L in SKILL_LATE_MARKS:
        rows.append(["-", "(after tier 12)", L, num(skill_xp_to(L)), *(when(k, f"skill{L}") for k in KINDS)])
    return table(["Tier", "Ore", "Mining level", "Total skill XP", "Casual reaches it", "Active", "Top"], rows)


@register("tool_levels")
def t_tool_levels():
    rows = []
    targets = {5: "~1 h", 15: "~day 2", 30: "~week 1", 45: "~week 3-4", 50: "~week 6+"}
    for lv, slot in ((5, "1"), (15, "2"), (30, "3"), (45, "4"), (50, "5 (prestige)")):
        rows.append([slot, lv, num(tool_xp_to(lv)), targets[lv], *(when(k, f"toollvl{lv}") for k in KINDS)])
    return table(["Slot", "Tool level", "Total tool XP", "Phase-4 target (active)", "Casual", "Active", "Top"], rows)


@register("forge")
def t_forge():
    rows = []
    for t in range(2, 13):
        rows.append([t, MINE_TIERS[t - 1], money(forge_cost(t)), f"{num(forge_mats(t))} {MINE_TIERS[t - 2]}",
                     money(forge_mats(t) * price(t - 1)),
                     f"{forge_cost(t) / ref(t - 1):.1f} h", when("active", f"tool{t}"), when("top", f"tool{t}")])
    return table(["Tool tier", "Pickaxe", "Money", "Materials", "Material sell value",
                  "Money = hours of REF(t-1)", "Active forges it", "Top forges it"], rows)


@register("upgrades_prod")
def t_upgrades_prod():
    """Highest production level that still pays back within PAYBACK_DAYS, per Depths tier (active player)."""
    rows = []
    a = ARCHETYPES["active"]
    for t in range(1, 13):
        v = ISL_PRICE * price(max(1, t - 1)) * DYN_ISLAND * sell_mult(t, a["rank"])
        k = 0
        while (k < PL_PER_TIER * t
               and prod_cost(k + 1) <= (q_items(k + 1) - q_items(k)) * v * a["prod_hours"] * PAYBACK_DAYS):
            k += 1
        cum = sum(prod_cost(i) for i in range(1, k + 1))
        rows.append([t, k, money(prod_cost(k)) if k else "-", money(cum), num(q_items(k)),
                     money(q_items(k) * v) + "/h", money(q_items(k) * v * a["prod_hours"]) + "/day"])
    return table(["Depths tier", "Worth-it production level (PL)", "Cost of that level", "All levels so far",
                  "Items per production h", "Island income per production h", "Per day (active, 14 h)"], rows)


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
                     f"{total / ref(gate):.0f} h", when("active", f"tier{gate}"), when("top", f"tier{gate}")])
    return table(["Outpost", "Gate (Depths tier, proxy for pillars)", "Unlock", "Fusion II (step 18)",
                  "All 18 steps", "= hours of REF(gate)", "Active reaches gate", "Top reaches gate"], rows)


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
    # Worst case: an idle alt that parks money at the top of each personal tier
    return table(["Day", "Casual interest (share of that day's income)", "Active", "Top"], rows)


def daily_population(day: int, window: int = 7):
    """Average money created/removed per day over the `window` days ending at `day`
    (purchases are lumpy), all daily uniques; supply = money held at the end of `day`."""
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
            for r, v in list(d["earned"].items()) + list(d["spent"].items()):
                by_reason[r] = by_reason.get(r, 0.0) + n * v / len(days)
    return created, removed, supply, by_reason


INFL_DAYS = [1, 3, 7, 14, 30, 45, 60, 75, 90]


@register("sources_sinks")
def t_sources_sinks():
    rows = []
    reasons = ["depths", "island", "events", "interest", "forge", "production", "levels", "late", "mastery",
               "bank_tiers", "shop", "ah_tax"]
    for d in INFL_DAYS:
        c, r, s, br = daily_population(d)
        rows.append([d] + [money(br.get(x, 0)) for x in reasons])
    return table(["Day (7-day avg)", "Depths", "Island", "Events", "Interest", "Forge", "Production",
                  "Level upg.", "Late proj.", "Mastery", "Bank tiers", "Shop", "AH fee+tax"], rows)


def sink_status(ratio: float) -> str:
    return "OK" if ratio >= 0.75 else ("watch" if ratio >= 0.6 else "act")


@register("inflation")
def t_inflation():
    rows = []
    players = sum(POPULATION.values())
    for d in INFL_DAYS:
        c, r, s, _ = daily_population(d)
        active_daily = sum(sim("active")[2][d - 1]["earned"].values())
        rows.append([d, money(c), money(r), pct(r / c), money(c - r), money(s), money(s / players),
                     f"{s / players / active_daily:.1f}", sink_status(r / c)])
    return table(["Day (7-day avg)", "Created / day", "Removed / day", "Sink ratio", "Net / day",
                  "Money supply (held)", "Held per player", "Held per player in days of active income",
                  "Check"], rows)


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
        out["supply90"] = late[-1][2]
        tops = sim("top")[1]["Month 3"]
        out["interest_top"] = tops["earned"]["interest"] / tops["total_earned"]
        out["top_m2"] = sim("top")[1]["Month 2"]["total_earned"]
        act = sim("active")[1]["Month 3"]
        out["island_active"] = act["earned"]["island"] / act["total_earned"]
        return out
    finally:
        g.update(old)
        _CACHE.clear()


@register("sensitivity")
def t_sensitivity():
    cases = [
        ("Baseline (this sheet)", {}),
        ("No mastery (owner says no endless sink)", {"MASTERY": False}),
        ("Players save twice as much", {"ARCHETYPES": {k: dict(v, save=min(0.6, v["save"] * 2)) for k, v in ARCHETYPES.items()}}),
        ("Interest without payout caps", {"PB_PAYOUTG": 1e9, "IB_PAYOUTG": 1e9, "PB_PAYOUT1": 1e18, "IB_PAYOUT0": 1e18}),
        ("Production payback 8 days (players over-invest)", {"PAYBACK_DAYS": 8.0}),
        ("Sell prices +25%", {"P1": P1 * 1.25}),
    ]
    rows = []
    for name, ch in cases:
        r = scenario(ch)
        rows.append([name, pct(r["sink_late"]), money(r["supply90"]), pct(r["interest_top"], 1),
                     pct(r["island_active"]), money(r["top_m2"])])
    return table(["Scenario", "Lowest sink ratio, days 60-90", "Money supply day 90", "Interest share (top)",
                  "Island share (active)", "Top earned by month 2"], rows)


def crystals_per_hour(kind: str, snap: dict) -> float:
    return snap["bph"] * DEPTHS_TIME * TK1 * TKG ** (snap["tier"] - 1)


@register("crystals")
def t_crystals():
    rows = []
    full = sum(EN1 * ENG ** (L - 1) for L in range(1, EN_MAX + 1))
    for name, _ in STAGES:
        r = [name]
        for k in KINDS:
            s = sim(k)[1][name]
            tph = crystals_per_hour(k, s)
            r.append(f"{num(tph)}/h · {full / tph:.0f} h per maxed enchant")
        rows.append(r)
    return table(["Stage", "Casual (crystals per played h · hours to max one enchant)", "Active", "Top"], rows)


@register("enchant_costs")
def t_enchant_costs():
    rows = []
    cum = 0.0
    for L in range(1, EN_MAX + 1):
        c = EN1 * ENG ** (L - 1)
        cum += c
        rows.append([L, num(c), num(cum)])
    return table(["Enchant level", "Crystal cost", "Total to this level"], rows)


@register("crystal_rates")
def t_crystal_rates():
    rows = []
    for t in range(1, 13):
        rate = TK1 * TKG ** (t - 1)
        rows.append([t, MINE_TIERS[t - 1], f"{rate:.3f}", f"1 per {1 / rate:.0f} blocks"])
    return table(["Tier", "Ore", "Crystals per block (avg)", "Feels like"], rows)


@register("dynamic")
def t_dynamic():
    k_per_h = -math.log(1 - RECOVERY_PER_10MIN) * 6
    rows = []
    for t in range(1, 13):
        per_player = ref_bph(t, ARCHETYPES["active"]["uptime"]) * DEPTHS_TIME * fortune(t)
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


@register("checks")
def t_checks():
    """Automatic sanity checks; each row must say PASS."""
    rows = []

    def chk(name, ok, detail):
        rows.append([name, "PASS" if ok else "FAIL", detail])

    s = sim("active")[1]
    e, te = s["Month 3"]["earned"], s["Month 3"]["total_earned"]
    chk("Depths share 40-60% (active, season)", 0.40 <= e["depths"] / te <= 0.60, pct(e["depths"] / te))
    chk("Island share 25-45% (active, season)", 0.25 <= e["island"] / te <= 0.45, pct(e["island"] / te))
    chk("Events share 10-20% (active, season)", 0.10 <= e["events"] / te <= 0.20, pct(e["events"] / te))
    chk("Interest <= 5% of income (all, season)",
        all(sim(k)[1]["Month 3"]["earned"]["interest"] / sim(k)[1]["Month 3"]["total_earned"] <= 0.05 for k in KINDS),
        ", ".join(pct(sim(k)[1]["Month 3"]["earned"]["interest"] / sim(k)[1]["Month 3"]["total_earned"], 1)
                  for k in KINDS))
    top1 = sim("top")[1]["First hour"]["total_earned"]
    chk("First hour 30k-100k (top)", 30_000 <= top1 <= 100_000, money(top1))
    m2 = sim("top")[1]["Month 2"]["total_earned"]
    chk("Month 2 within 0.5x-2x of $50B (top)", 25e9 <= m2 <= 100e9, money(m2))
    w2 = sim("top")[1]["Week 2"]["total_earned"]
    chk("Week 2 within 0.5x-2x of $500M (top)", 250e6 <= w2 <= 1e9, money(w2))
    ratios = [daily_population(d)[1] / daily_population(d)[0] for d in INFL_DAYS]
    chk("Sink ratio >= 75% every checked day", min(ratios) >= 0.75, "min " + pct(min(ratios)))
    lv5 = event_at("active", "toollvl5")
    chk("Slot 1 within ~1-2 h (active)", lv5 is not None and lv5[1] <= 2, when("active", "toollvl5"))
    lv45 = event_at("active", "toollvl45")
    chk("Slot 4 in week 3-6 (active)", lv45 is not None and 14 <= lv45[2] <= 42, when("active", "toollvl45"))
    chk("Max blocks/s under reach cap", bps(12, 50, 5) <= REACH_CAP, f"{bps(12, 50, 5):.2f} <= {REACH_CAP}")
    chk("Money fits in a long", sim("top")[0].total_earned() * POPULATION["top"] < 9.2e18, "season total, all tops")
    t12 = event_at("top", "tier12")
    chk("Tier 12 not before week 4 (top)", t12 is None or t12[2] >= 28, when("top", "tier12"))
    return table(["Check", "Result", "Value"], rows)


# ---------------------------------------------------------------------------
# 6. CLI
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
    if args.table:
        print(render(args.table))
        return 0
    if args.write or args.check:
        path = args.write or args.check
        with open(path, encoding="utf-8") as f:
            text = f.read()
        new, found = refresh(text)
        missing = sorted(set(TABLES) - set(found))
        if args.write:
            with open(path, "w", encoding="utf-8") as f:
                f.write(new)
            print(f"refreshed {len(found)} tables in {path}" + (f"; not in doc: {missing}" if missing else ""))
            return 0
        if new != text or missing:
            print(f"{path} is out of date: run --write" + (f" (tables missing from the doc: {missing})" if missing else ""))
            return 1
        print(f"{path} is up to date ({len(found)} tables)")
        return 0
    for name in TABLES:
        print(f"### {name}\n\n{render(name)}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
