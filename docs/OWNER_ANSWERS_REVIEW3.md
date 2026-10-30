# Owner answers: REVIEW3 and the follow-ups page (30 Oct 2026)

Sources: REVIEW3 answers page (db answers/review3v2) and the follow-ups page https://claude.ai/artifact/7BwcM5JbABUeDuC6pXJxHt (db answers/round2). The full developer brief from the review session is [review/REVIEW3_DEVELOPER_BRIEF.md](review/REVIEW3_DEVELOPER_BRIEF.md); the fact-checked list is [REVIEW3_SECOND_LOOK.md](REVIEW3_SECOND_LOOK.md). Where an answer below differs from the brief's proposal, **this file wins**.

## Follow-ups (F, D, K)
| Id | Answer | What we build |
|---|---|---|
| F1 | Yes, **except** to members of the island the sender is on | A profile can send money (/pay, notes, trade) only after it earned $10k itself; sending to members of its own island is always allowed. |
| F2 | Likes it ("we are closing in") | Spawners: fixed /shop price + diminishing output per island and tier (100/60/35/20/10%), drop +10% per owned. Plan first in the spawner planning file, then build. **Plus the IMPORTANT request below.** |
| F3 | **Opposite**: earned gems are bound, **bought** gems are tradeable | Gems bought in the store can be traded/paid; gems earned by playing are bound to the account. |
| F4 | No (see F3) | No extra send gate or daily limits on gem sending. |
| F5 | Bought items are tradeable **while unclaimed**; right-click = claimed = bound | Store items arrive as unclaimed items that can be sold anywhere (/ah, /trade); right-clicking claims them and binds them. |
| F6 | Both | Island size above 9x9 can be bought with earned and bought gems (and shards at the Black Market). |
| F7 | **Not playtime.** Think of the whole grind from start to end game as a battle pass | Rank perks earnable by progression milestones, not hours. Needs a better plan (rank perk planning file). |
| F8 | No | Keep the island bank withdraw as built: a role permission the leader gives; no extra 3-day rule. |
| F9 | Recommended | On kick the island keeps 50% of the member's contribution (unattributed), preview before kick, kicked player gets 50% of unspent deposits back. Built with Phase 5. |
| F10 | Yes, **plus**: after a member leaves or is kicked, that slot stays locked **48 h** before the island can invite someone into it. Clear message. "Make sure ALL messages are saved somewhere" | Leaving = no loss for the island. Slot lock 48 h per freed slot. All texts in messages files (already the rule). |
| F11 | Yes, **plus** rewards for the biggest contributors in general (fair play) | No leader kicks in the last 14 days of a season; top contributors get rewards (plan with seasons). |
| F12 | Yes, if optimized and safe: 64 items every 8 ticks | spigot.yml hopper-amount 64, hopper-transfer 8; check Paper hopper settings. |
| F13 | Make sure it won't lag; optimize or find a safe way | Hopper counter per chunk with a staff alert; cheap (cached per chunk, no scans per tick). |
| F14 | Yes, and possible to toggle | Shop "Undo last buy" 15 s + second click over 50% of money, both toggleable in /settings. |
| F15 | Recommended | In /s, owner and admin may use creative on islands; island bypass on automatically in /s; every block logged; creative items staff-tagged. |
| D1 | Recommended | Depths zones v2: write `docs/phases/PHASE-4-DEPTHS-V2.md` first. |
| D2 | Yes, and the different tiers have different rewards and quantities for the shared ones | Lower tier selectable; each tier has its own drops/amounts even where the block is shared. |
| D3 | **Layered mobs from the start.** Plan it, be critical, ask if anything is unclear | Combat zones v2 with per-tier layered mobs (not shared sub-arenas). Plan must cover targeting/sound/tick cost honestly. |
| D4 | Recommended | One shared visibility service (vanish, AFK pool, Depths visibility modes). |
| K1–K6 | Recommended | GUI sketches (review session). |
| K7 | They ARE final art if they are good: "I need the best" | Treat GUI sketches as final-quality art. |
| K8 | Send in chat for now | — |

## IMPORTANT (F2 note)
> "I want to have a short list of EVERY feature we have that has to be saved somewhere ... I will show it to an AI and ask for improvements, then say yes to some and no to some, keep asking until it doesn't give anything else useful, and THEN implement it. For the whole server: wardrobe, /shop, rank names/perks, how to achieve it, everything."

→ Feature catalog: `docs/features/` with one short md per feature (what it does today, the real numbers from code/live config, commands, how players get it, open points). Index in `docs/features/README.md`. Copy kept on the Mac in `Development/skyborn-owner/features/`. Queue item FEATCAT.

## REVIEW3 page answers
All R3-* answers are listed in the brief §1. Owner notes worth keeping:
- FOUND: "I need a full MD file explaining everything so I can send to developer" (done: the brief).
- S1: the owner talks to the main session about what to build.
- S2: releases: "every time it's something new, in a batch" → release in batches, not after every item.

## Applied on the live server right away (30 Oct, 23:52)
- stacking.yml `spawners.mobs-per-spawner: 1.0` (answer 641, was 4.0).
- afk.yml milestone `480:blaze:1` → `480:cinder:1` (R3-15).
- islands.yml `max-joins-per-season: 0` (N2-1 unlimited). `join-cooldown-hours: 24` kept for now (open question; F10's 48 h slot lock covers the island side).
- Backups of the old files on the VPS in `~/cfg-backup-r3/`. Code defaults follow in REV3-FIX.
