# Card -> code map (starting point; the reader subagent verifies and corrects)

Module column is what refresh.py shows in INDEX.md. Paths: `modules/<m>/`, `common/src/main/java/net/skybornmc/skyblock/common/<pkg>/`, configs/messages in `modules/<m>/src/main/resources/`, permissions in `.../permissions/<m>.yml`, tests in `src/test`, grants in `server/permissions/<m>.txt`, command docs in `docs/commands/<m>.md`.

| # | Feature | Module / common package |
|---|---|---|
| A1 | Join flow and spawn | essentials, worlds |
| A2 | Spawn portal to island | islands, worlds |
| A3 | Tutorial | tutorial (+ common/tutorial) |
| A4 | Starter chest | islands |
| A5 | NPCs and holograms | npcs (+ common/npc) |
| A6 | Portals | worlds |
| A7 | MOTD | motd |
| B1 | Island creation and grid | islands |
| B2 | Protection and roles | islands (+ common/island, region) |
| B3 | Members, invites, coop | islands |
| B4 | Contribution | islands |
| B5 | Visiting | islands |
| B6 | Warps, ratings, likes | islands |
| B7 | Island settings and fly | islands (+ common/flight) |
| B8 | Island menu and chat | islands, menus |
| B9 | Island notes | islands |
| B10 | Disband, reset, staff tools | islands |
| B11 | Island bank | islands, economy |
| B12 | Island storage / vault | islands (plan: docs/planning/ISLAND_STORAGE.md) |
| B13 | Island level and pillars | islands, progression |
| B14 | Upgrades and masteries | islands |
| B15 | Island size | islands |
| B16 | /is top and leaderboards | islands (+ common/leaderboard) |
| B17 | Profiles | profiles (+ common/profile) |
| C1 | Currencies | economy (+ common/economy) |
| C2 | Pay, balance, baltop | economy |
| C3 | Money notes | economy |
| C4 | Selling | economy, shop (+ common/sell) |
| C5 | Shop | shop (+ common/shop) |
| C6 | Auction house | auction |
| C7 | Secure trade | trade |
| C8 | Personal bank and interest | economy |
| C9 | /box | box (+ common/box) |
| C10 | Economy watch | economy, logs |
| C11 | Player shops and buy orders | shop (plan: docs/planning/PLAYER_SHOPS_BUY_ORDERS.md) |
| C12 | Sell wands | shop, tools (plan: docs/planning/SELL_WANDS.md) |
| C13 | Black Market | blackmarket |
| C14 | Season economy curve | economy (docs/ECONOMY_BALANCE.md) |
| D1 | Mob and item stacking | stacking (+ common/stacking) |
| D2 | Spawners | stacking, production |
| D3 | Hoppers | production |
| D4 | Custom mob drops | production, depths (docs/planning/CUSTOM_MOB_DROPS.md) |
| D5 | Island fuel | production |
| D6 | Generators and crops | production, stacking |
| D7 | Collectors and auto-sell | production |
| D8 | Fishing | production (verify) |
| D9 | Outposts and biomes | worlds, production (verify) |
| D10 | AFK pool | afk (+ common/afk) |
| E1 | Depths v2 zones | depths (docs/WORLD.md) |
| E2 | Per-player blocks | depths |
| E3 | Tiers and gates | depths |
| E4 | Satchel | depths (+ common/satchel) |
| E5 | Depths drops | depths |
| E6 | Combat zones and layered mobs | depths (+ common/mob, combat) |
| E7 | Mini-bosses | depths (verify) |
| E8 | Visibility | depths (+ common/visibility) |
| E9 | Skills | skills (+ common/skills) |
| E10 | Tools and the Forge | tools (+ common/tools) |
| E11 | Blueprints and skins | tools |
| E12 | Enchants | enchants (+ common/enchant; docs/plans/ENCHANTS.md) |
| E13 | Wardrobe and sets | wardrobe (+ common/wardrobe; docs/plans/WARDROBE.md) |
| E14 | Loadouts | wardrobe (verify) |
| F1 | Progression track | progression (+ common/progression) |
| F2 | Ranks and perks | perks, chat (docs/plans/RANKS_TITLES_ACHIEVEMENTS.md) |
| F3 | Achievements | achievements (+ common/achievements) |
| F4 | Tags and titles | cosmetics |
| F5 | Crates and keys | crates (+ common/crate) |
| F6 | Voting | rewards (verify) |
| F7 | Daily rewards and calendar | rewards |
| F8 | Quests | quests (+ common/quest) |
| F9 | Battle pass | pass (+ common/pass) |
| F10 | Collection log | collection (+ common/collection) |
| F11 | Boosters and thanks | boosters (+ common/booster) |
| G1 | Chat | chat |
| G2 | Private messages and ignore | chat, social |
| G3 | Social commands | social |
| G4 | Messages and texts | common/text, common/config |
| G5 | Chat broadcasts | chat (docs/planning/CHAT_BROADCASTS.md) |
| G6 | Friends, parties and guest book | social (docs/planning/FRIENDS_PARTIES.md) |
| G7 | Scoreboard | hud |
| G8 | Tab list and nametags | chat (docs/planning/TAB_LIST.md) |
| G9 | Main menu (/menu) | menus (+ common/menu) |
| G10 | Help and settings | menus (+ common/settings) |
| G11 | Teleports | essentials (+ common/teleport) |
| G12 | Essentials and perk commands | essentials, perks |
| H1 | Staff mode | staff (+ common/staff) |
| H2 | Punishments | moderation (+ common/moderation; docs/specs/PUNISH_SPEC.md) |
| H3 | Reports | staff |
| H4 | Freeze and inspect | staff |
| H5 | Logs | logs (+ common/audit) |
| H6 | Admin panel | menus |
| H7 | Anti-dupe and security | common/item, audit (docs/planning/SECURITY_ANTI_EXPLOIT.md) |
| H8 | Anticheat | server config (GrimAC), staff |
| H9 | Performance | all (docs/planning/PERFORMANCE.md) |
| I1 | Warzone and combat tag | worlds, common/combat (docs/planning/COMBAT_LOGGING.md) |
| I2 | Duels | not built (docs/planning/DUELS.md) |
| I3 | Events | not built (docs/planning/PHASE-9-EVENTS.md) |
| I4 | PvE, bosses and pets | common/pet (verify) |
| I5 | Seasons | not built (docs/planning/PHASE-12-SEASONS.md) |
| I6 | Store and Discord | perks, common/discord (docs/planning/PHASE-11-STORE-DISCORD.md) |
| I7 | Resource pack | pack (+ common/pack; docs/plans/RESOURCE_PACK*.md) |
| I8 | Launch prep | not built (docs/planning/PHASE-13-LAUNCH.md) |
