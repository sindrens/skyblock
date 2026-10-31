# Backlog: everything not done yet (start here)

One place that points to every list of unbuilt work, ideas and questions. Nothing is deleted from these files; finished items are marked ✅ where they live.

## Where things live
| File | What it holds |
|---|---|
| [QUEUE.md](QUEUE.md) | The work queue the agents pick from (status per item) |
| [ROADMAP.md](ROADMAP.md) | Phases 0–13. Phases 0–4 have detailed plans in [phases/](phases/); 5–13 still need their plan docs |
| [LATER.md](LATER.md) | Decided but postponed (Discord, hub, store, web map, network punishments, CoreProtect, Nether/End) |
| [FEATURE_IDEAS.md](FEATURE_IDEAS.md) | 80+ ideas the owner said yes to (2026-10-30); each lands in a phase plan |
| [QUESTIONS_ROUND2.md](QUESTIONS_ROUND2.md), [QUESTIONS_ROUND3.md](QUESTIONS_ROUND3.md) | Earlier question rounds and the owner's answers |
| [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) | Every decision made with a default while waiting for the owner |
| [OWNER_ANSWERS_ROUND1.md](OWNER_ANSWERS_ROUND1.md) | The owner's one-by-one answers (30 Oct) |
| Decisions page | https://claude.ai/artifact/KvSdKBox2cMSS5h6WDUXfs — the same questions as one-click cards (Suggested / No / Other). Answers live in its database (collection `answers`); read them with ArtifactData and copy them into OWNER_ANSWERS_ROUND*.md |
| [DECISIONS_2026-10-30.md](DECISIONS_2026-10-30.md) | Owner decisions from the design rounds |
| [REVIEW.md](REVIEW.md) | Known problems with their default decisions |
| [plans/](plans/) | Feature plans (enchants, wardrobe, resource pack, art brief) |
| [TEST_GUIDE.md](TEST_GUIDE.md) | What every built feature does and how to test it |
| [PLANNING.md](PLANNING.md) | Every plan and phase in one table: status, open questions, next build step |
| [SECURITY_LOG.md](SECURITY_LOG.md) | Anything suspicious and reliability incidents |

## Not built yet (by area)
- **Join flow and tutorial** (owner 30 Oct): portal at spawn that creates your island and always teleports you there; guided tutorial on the island (camera/animation, arrow guide, step by step); permissions unlock per step; help NPCs and holograms everywhere with clean lores. → TUTORIAL (plan first), NPCHOLO, PORTALS.
- **Builds stored but not used yet:** NPC pads, holograms, leaderboards, crates, particles, tutorial nodes, viewpoints, treasure spots, secrets, portals, protected regions in the Depths, safe zones and dev flags (no PvE in arrival/exit paths, no pearls in combat zones, no anvils/containers in the Forge Quarter). → NPCHOLO, PORTALS, DEPTHSPROT.
- **Builder fixes:** the crypt's gate is not on the zone edge; spruce forest and spider den are paused at v1.
- **Phase 5 Progression core:** pillars, dynamic island level from many sources (owner: not only block value), `/is upgrade` masteries with tasks (bank fee discount, size), leaderboards. → PLAN5.
- **Phase 7 Island production:** custom generators and crops, collectors/auto-sell, Island Fuel (AFK pool fuel is saved until then), outposts and biomes; unblocks E6 (hoe and rod enchants, farming/fishing). → PLAN7.
- **Phase 8 PvE:** custom mobs, world and summon bosses, pets, fishing. → PLAN8.
- **Phase 9 Conquest and events:** KOTH, envoys, PvP zones, duels, event engine. → PLAN9.
- **Phase 10 Rewards and retention:** crates and keys (AFK pool keys are saved until then), voting, daily rewards (per profile, owner), quests, battle pass, collection log, Black Market (also sells island size above 9x9), tutorial. → PLAN10.
- **Phase 11 Store, ranks, Discord:** gems/credits, 5 ranks and perks (owner: some ranks get island fly; plan ranks with the owner), Discord. → RANKS, PLAN11.
- **Phase 12 Seasons** and **Phase 13 Balance and launch prep.** → PLAN12, PLAN13.
- **Resource pack:** forced pack with a prompt before joining; the owner draws the art from the art brief. → RP1 waits for art.
- **Starter chest:** custom contents for this server (proposal to the owner). → STARTER.
- **Island storage** (needed for STORE2).
- **/settings:** tips stays "Coming soon" until that feature exists ("players in The Depths" became the Show players choice in VIS ✅).
- **VIS follow-ups (visibility service, other lanes):** (1) `modules/islands` `VisitManager` still uses `Player#canSee` for "does this player exist" (`/is visit <player>`, the visitor notice, two tab-completion lists): switch to `Visibility.knows(...)` so a player hidden by the AFK pool or Show players can still be visited by name (islands lane was busy). (2) `modules/depths` `AmbientMobs` / `MobFactory` per-player entities (`setVisibleByDefault(false)` + `showEntity`) can move onto the visibility service later (D4; depths lane). (3) The Depths v2 tier palette (D1) and lower-tier choice (D2) don't exist yet; Show players already applies in the whole `depths` world.

## Rules for agents
- Pick from QUEUE.md; max 3 agents at once; one lane each; merge and release as each finishes.
- A phase without a plan doc gets a PLAN item first (write `docs/phases/PHASE-N-*.md` with milestones, mockups and a question list). New questions go to OPEN_QUESTIONS.md **and** to the Decisions page (add them to tools/owner_sheet/final_q.json with new ids and republish the page).
- Never delete an idea, question or "later" item: mark it ✅ or move it, and link where it went.
