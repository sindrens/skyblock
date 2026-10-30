# Planning: every plan and phase in one table

What is planned, what still needs a plan, and what each one waits for. Built features are in [TEST_GUIDE.md](TEST_GUIDE.md); every unbuilt item by area is in [BACKLOG.md](BACKLOG.md); the work queue is [QUEUE.md](QUEUE.md).

Status words: **built** · **being built** · **plan written** (defaults apply until you answer) · **waiting for owner answers** (the next step needs your answers first) · **needs plan** · **waiting for art** · **later** (decided, postponed on purpose).

"Open questions" = questions on the Decisions page (link in [BACKLOG.md](BACKLOG.md)) for that plan that are not answered yet in [OWNER_ANSWERS_ROUND1.md](OWNER_ANSWERS_ROUND1.md) / [OWNER_ANSWERS_ROUND2.md](OWNER_ANSWERS_ROUND2.md) (counted 30 Oct 2026, after GUIDE2). Answers you gave on the page since then lower these numbers.

| Plan / phase | Status | Link | Open questions on the Decisions page | Next build step |
|---|---|---|---|---|
| Tutorial + starter chest (TUTORIAL) | plan written; TU1-TU4 being built with the suggested answers | [plans/TUTORIAL.md](plans/TUTORIAL.md) | 32 of 32 (ids TUT-1 to TUT-32) | Merge TU1-TU4 (gates, step engine, cameras, starter chest), then TU5 (companion, NPC lines, Guide NPC opens /tutorial) |
| Ranks, titles, achievements (RANKS_TITLES_ACHIEVEMENTS) | waiting for owner answers (RK1 = your answers to §E) | [plans/RANKS_TITLES_ACHIEVEMENTS.md](plans/RANKS_TITLES_ACHIEVEMENTS.md) | 46 of 46 (ids RTA-1 to RTA-46) | AC1 `/achievements` command + menu tabs (already approved, answer 356); RK2 rename the free rank to Ashborn after your answers |
| Phase 5 Progression (pillars, island level, upgrades, masteries, leaderboards) | plan written | [phases/PHASE-5-PROGRESSION.md](phases/PHASE-5-PROGRESSION.md) | 37 of 37 (ids P5-1 to P5-37), plus 1 older island level question | M5.1 Pillars (modules/progression) |
| Phase 7 Island production (fuel, generators, crops, collectors, fishing, outposts) | plan written (merged 30 Oct, this was "being written" when GUIDE2 started) | [phases/PHASE-7-PRODUCTION.md](phases/PHASE-7-PRODUCTION.md) | 0 on the page yet: its 37 questions (PD1-PD37) go on the page next | M7.1 Island Fuel core + Fuel Cells + AFK fuel delivery |
| Phase 8 PvE (custom mobs, world/summon bosses, pets, sea creatures) | needs plan (PLAN8 is next in the queue) | [ROADMAP.md](ROADMAP.md) | 0 | Write docs/phases/PHASE-8-PVE.md with questions |
| Phase 9 Conquest and events (KOTH, envoys, PvP zones, duels, event engine) | needs plan | [ROADMAP.md](ROADMAP.md) | 0 | Write docs/phases/PHASE-9-*.md (PLAN9) |
| Phase 10 Rewards (crates, keys, boosters, voting, daily calendar, quests, battle pass, collection log, Black Market) | plan written | [phases/PHASE-10-REWARDS.md](phases/PHASE-10-REWARDS.md) | 43 of 43 (ids P10-1 to P10-43) | M10.1 crates + keys core (pending AFK pool keys are delivered) |
| Phase 11 Store, ranks, Discord | needs plan (ranks part already in RANKS_TITLES_ACHIEVEMENTS) | [ROADMAP.md](ROADMAP.md), [LATER.md](LATER.md) | 0 (rank questions are in RTA) | Write the Phase 11 plan (PLAN11): Tebex, gems/credits, Discord linking |
| Phase 12 Seasons (reset tool, pre-season, carry-over, Final Week) | needs plan | [ROADMAP.md](ROADMAP.md) | 0 | Write the Phase 12 plan (PLAN12) |
| Phase 13 Balance and launch prep (balance pass, bot load test, closed beta) | needs plan | [ROADMAP.md](ROADMAP.md) | 0 | Write the Phase 13 plan (PLAN13) |
| Resource pack + art brief (RESOURCE_PACK, ART_BRIEF) | waiting for art (forced pack and "you draw the art" are decided) | [plans/RESOURCE_PACK.md](plans/RESOURCE_PACK.md), [plans/RESOURCE_PACK_ART_BRIEF.md](plans/RESOURCE_PACK_ART_BRIEF.md) | 19 of 31 (feature "Resource pack plan" / "Resource pack (plan)"; RP-D2 to RP-D19 and the art brief questions are Q2-39 to Q2-52) | You draw the 5 pilot pieces; then RP1 (PackService, forced sending in the configuration phase) |
| Island storage / STORE2 | needs plan (the Island Vault is defined in Phase 5 M5.4, answer O4: `/is storage` = Island Vault) | [phases/PHASE-5-PROGRESSION.md](phases/PHASE-5-PROGRESSION.md), [QUEUE.md](QUEUE.md) | 0 | Build the Island Vault upgrade (M5.4), then STORE2 (mastery deliveries from storage, daily take-out limit per role, staff recall from kicked members) |
| E6 hoe + rod enchants | plan written (ENCHANTS §6.4, §6.7); waits for Phase 7 | [plans/ENCHANTS.md](plans/ENCHANTS.md), [phases/PHASE-7-PRODUCTION.md](phases/PHASE-7-PRODUCTION.md) | 0 | After M7.3 (crops, `IslandCropHarvestEvent`) and M7.8 (fishing, `FishCatchEvent`) |
| Enchants E1-E5, wardrobe + loadout W1-W4 (Phase 6 part) | built | [plans/ENCHANTS.md](plans/ENCHANTS.md), [plans/WARDROBE.md](plans/WARDROBE.md) | see TEST_GUIDE features | E6 above |
| Custom starter chest (STARTER) | plan written (TUTORIAL §6) | [plans/TUTORIAL.md](plans/TUTORIAL.md) | in TUT | Built in TU4 |
| Dynamic island level (ISLAND-LEVEL) | plan written (Phase 5 M5.2, 8 sources) | [phases/PHASE-5-PROGRESSION.md](phases/PHASE-5-PROGRESSION.md) | in P5 | M5.2 after M5.1 |
| New decisions from AFK, SELL2, STYLE2, BANK2, BOX, ANS1-3, NPCHOLO, PORTALS, DEPTHSPROT | built with defaults; waiting for owner answers | [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) | 52 of 52 (ids Q2-1 to Q2-52) | Change only what you answer differently |
| LATER: Discord integration (linking, webhooks, appeals step 2, announcements) | later | [LATER.md](LATER.md) | 0 | With Phase 11 |
| LATER: SkybornMC hub / lobby for other gamemodes | later | [LATER.md](LATER.md) | 0 | After launch |
| LATER: resource pack extras (border, models, crate and donor visuals, glyphs, sounds) | later (inside RP1-RP10) | [LATER.md](LATER.md), [plans/RESOURCE_PACK.md](plans/RESOURCE_PACK.md) | in RP | RP milestones |
| LATER: store (Tebex), rank perks, "gg" after purchases, streak-save | later | [LATER.md](LATER.md) | in RTA | Phase 11 |
| LATER: website stats / web map | later | [LATER.md](LATER.md) | 0 | After launch |
| LATER: Discord bot appeals for banned players | later | [LATER.md](LATER.md) | 0 | Phase 11 |
| LATER: network mode for punishments | later | [LATER.md](LATER.md) | 0 | When a second server exists |
| LATER: CoreProtect block logs in `/logs near` | later (waits for a 26.3 build) | [LATER.md](LATER.md) | 0 | When CoreProtect supports 26.3 |
| LATER: Nether and End as custom worlds | later | [LATER.md](LATER.md) | 0 | Needs a plan when that content is scheduled |
