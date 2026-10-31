# Depths v2 spike: test script for the owner

Built in DV2-SPIKE (behind `depths.yml → palette.enabled`, off by default). Run with 2 Java players (A, B) and 1 Bedrock player (C).

**Setup (console):** set `depths.yml → palette.enabled: true`, then `/skyblock reload depths`. The console should print "Depths palette (spike) on: mine_t01 shows [Coal→¦, Copper→¦, Iron→¦]".

1. **Everyone starts on coal.**
   - Do: `/skills set A mining 0`, the same for B and C. All three go to `/depths tp mine_t01` and stand in the ore field.
   - Look for: all see coal ore. `/depths palette` shows "Coal (Tier 1 of 3)".
2. **The unlock moment (A).**
   - Do: in the field, `/skills set A mining 5`.
   - Look for: within about one tick the whole field turns to copper ore for A only, nearest blocks first; the title "Copper unlocked!", the unlock sound and the particle wave. B and C still see coal. The console shows a swap line with the block count and ms.
   - Then: `/skills set A mining 10`. The field turns to iron with "Iron unlocked!".
3. **Rewards are A's tier.**
   - Do: A mines a few blocks.
   - Look for: raw iron in the satchel (iron's table), iron Mining XP on the action bar, depleted blocks show diorite. B mines a block A can see: B gets coal and cobblestone, A still sees iron there. Each player's depleted blocks belong only to them.
4. **Break timing.**
   - Do: A mines iron with a wooden pickaxe.
   - Look for: slow, as vanilla, with no "too fast" staff alert. A stone pickaxe should feel normal. A hacked fast-break on shown iron should be flagged.
5. **Lower tier.**
   - Do: `/depths palette 1 A`, then `/depths palette auto A`.
   - Look for: a quiet swap back to coal (no title) and coal rewards, then a quiet swap back to iron. `/depths palette 3 B` while B is at level 0 stays on coal.
6. **Re-send cases (A at iron).**
   - Do: walk away until the field unloads and come back; relog; `/depths tp gate` and return; switch profile.
   - Look for: iron again each time (after a relog it may show coal for up to about 1 s first). Right-clicking an iron block must not flash coal. A profile switch shows that profile's tier.
7. **Bedrock (C), the main reason for the spike.**
   - Do: `/skills set C mining 10` while C stands in the field.
   - Look for:
     - The swap rolls out over a few ticks with no stutter or disconnect. The console says ", Bedrock" and logs a done line with the extra ticks.
     - Breaking shown iron takes the right time with no ghost blocks.
     - Depleted diorite appears and comes back as iron, not coal.
     - Title and particles are visible.
   - To try the throttle on Java: set `palette.java-blocks-per-tick: 300` and reload.
8. **Turn it off.**
   - Do: set `palette.enabled: false` and reload.
   - Look for: everyone sees real coal again.


## What I could not verify
- Anything on a real server, since no test server was allowed. In particular:
  - that Paper and Geyser hold the blocks we send through digging, block updates and chunk resends;
  - how Bedrock times breaking shown copper or iron, and whether 300 blocks per tick is smooth on a phone;
  - real swap ms and block counts (the field has between 441 and 1,267 slots);
  - that the `end_rod` particle shows on Bedrock;
  - the order of level-up listeners, in case another plugin's handler runs first.
- WorldEdit/FAWE edits inside the field are not picked up by the slot index until `/skyblock reload depths`.
- Ambient mobs already spawned keep their old tier until they despawn.

## 