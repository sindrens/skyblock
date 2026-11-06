# Research prompt (owner 5 Nov 2026): give this to a research assistant, then paste the result back

You are researching game design for a Minecraft Skyblock server, "Phoenix Skyblock" on SkybornMC (Paper 26.3, Java and
Bedrock via Geyser, 300+ players). We build every feature ourselves, so we want **mechanics, numbers and progression
curves**, not plugin recommendations. The owner wants it to feel like popular Roblox simulator games: fast progress at
the start, then prestige, then more prestiges, always a next goal a few minutes away.

## Our current game (for context)
- Every player has an island (Skyblock). Leaving an island wipes the profile.
- **/farm** and **/mine** are shared grind areas.
  - /farm: per-player crop tiers (wheat → carrots at 3,000 harvests → potatoes → beetroot).
  - /mine: per-player ore tiers (all coal → iron at 400 ores → gold → redstone → lapis → diamond → emerald).
  - Crops and ores regrow in 2–3 s.
- **Island tools:** one **Harvester Hoe** and one **Island Pickaxe**, owned by the island and shared by the members.
  - Levels 1–50 with XP per crop or ore; prestige at 50 (★, a permanent +5% bonus).
  - Upgrades ("perks") are bought with **Farming Shards** (from the hoe) and **Mining Shards** (from the pickaxe).
  - Current perks:
    - Shard Finder
    - Efficiency
    - Extra Drops
    - Fortune
    - Haste
    - Wisdom (XP)
    - Growth
    - Storage
    - Smelter
    - Auto-Sell
    - Sell Bonus
    - Lucky (shard pouches)
    - Sweep (hoe: harvests nearby crops) and Vein (pickaxe: nearby ores), both from prestige 1
- **Island upgrades:**
  - Size, generator, member slots, crop growth, spawner cap, hopper cap, Harvest Yield, Mine Fortune.
  - Paid with island-bank money.
  - Planned: requirements done **on the island** (e.g. farm 1,000 wheat).
- **Spawners:** bought in /shop, stacked, with mobs that don't move.
  - Planned: spawners spawn every 10 s, with a delay upgrade.
  - Planned: spawners level up by killing their mobs on the island, and more kills allow more spawners.
- **Currencies:**
  - money (selling)
  - crystals (island tasks, quests; Black Market and enchants)
  - Farming and Mining Shards (tools only)
  - credits (store)

## Please research and report

1. **Harvester hoe / pickaxe / "prison pickaxe" plugins and Roblox simulators.** Cover the most popular ones you can
   find: Hypixel Skyblock, PvPWars, Minecadia, OPBlocks, "Prison" servers, "SuperiorSkyblock" addons, "Harvester Hoes"
   plugins, and Roblox games like Bee Swarm, Pet Simulator, Mining Simulator.
   - Every enchant or upgrade they offer, with name, what it does, how many levels and the effect per level.
   - When each one unlocks.
   - Cost curves: start price, growth per level, and how long players take to reach max.
   - Which ones players say feel the best and which feel pointless (for example a "+0.01%" step).
   - How the first 10 minutes of upgrades feel, so the first upgrade comes almost at once.
   - Prestige systems: what resets, what you keep, the bonus per prestige, how many prestiges there are, and the time
     per prestige.
   - "Proc" enchants (a chance to trigger an effect): chance per level, cooldowns, how they are shown on screen.
2. **Currency pace:** how fast the upgrade currency comes per minute at the start compared to late game, and how a
   shard/token finder upgrade is designed so it feels strong (a multiplier vs. a chance; flat vs. percent).
3. **Island upgrades with requirements**, in popular Skyblock servers (SuperiorSkyblock, BentoBox, Hypixel "Minions",
   Skyblock "missions").
   - The tiers per upgrade (up to 15).
   - Requirement types ("harvest X crops on your island", "kill X mobs", "reach island level Y") with the real numbers
     per tier.
   - The money cost per tier.
   - How they motivate players.
4. **Spawner systems** (WildStacker, RoseStacker, "spawner upgrades", Hypixel Minions).
   - Spawn delay per level.
   - Upgrade costs.
   - Spawner prices per mob type and the value of the drops per mob.
   - Kill-to-unlock systems.
   - How they stop spawners from breaking the economy.
5. **Tutorials and onboarding** that keep new players: quest-book style guides, NPC dialogs, the "next step" always on
   screen.
6. **Anti-boosting:** how servers stop alt accounts from feeding money to a main account (playtime gates, transfer
   caps, trade taxes, and their numbers).

## Format of the answer
For every system, give a **table**: name · effect · levels · effect per level · unlock · cost at level 1 / 10 / max ·
source (link). Then give a short "best practices" list, and **concrete numbers you would recommend for us**, given the
context above. Keep it factual and give the sources.
