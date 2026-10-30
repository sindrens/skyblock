# Open Questions

## Waiting for owner (answer in one sitting)
- M3.4 SELL prices: `sell.yml` is a DRAFT from ECONOMY_BALANCE (BAL2): island generator drops and crops at the §4.1 prices, spawner mob loot at §4.3, Depths drops as custom items `depths_coal` … `depths_voidstone` and `depths_oak_log` … `depths_warped_stem` (ids are proposals for M4.9). Approve before launch? (default: these prices, live on the test server)
- M3.4 SELL emerald: off (`sellable: false`) because fletcher villagers turn free sticks into emeralds. Turn it on once villager trading is limited, or never sell emeralds? (default: off)
- M3.4 SELL left out to avoid craft/smelt profits: arrows (1 feather = 4 arrows), ingots other than iron, storage blocks, sugar, paper, sticks, bone meal, dyes, seeds, wool (sheep can be sheared forever). Iron ingots sell at the raw iron price ($18), so iron golems earn far less than the §4.3 tier-5 spawner value. Give golems a custom drop later? (default: as described)
- M3.4 SELL natural mob farms and island tree farms: vanilla mob drops sell no matter where they come from (dark-room farms too); vanilla logs are not sellable at all (only Depths logs). OK? (default: as described)
- M3.4 SELL trend arrows: the phase doc shows ▲▼, but they are not on the Bedrock-safe symbol list, so the menu shows "Rising / Falling / Steady" in green/red/gray. Test ▲▼ on Bedrock and switch? (default: words)
- M3.4 SELL Hot Item: +25% on one item per day, picked from the date (Europe/Oslo), from all categories. Announce it in chat at midnight / show it on the scoreboard? (default: shown in the /sell menu only)
- M3.4 SELL staff tools: no staff command to inspect or reset dynamic prices (only commands from the plan were added); prices reset at season start is not built yet (season service, Phase 10). Add `/sell admin` (price list, reset one item/all)? (default: no; edit sell.yml, ask a developer)
- M3.4 SELL items stay in the inventory while they are in the sell box (the box remembers slots; inventory clicks are blocked while it is open), instead of moving into the chest. This is what makes close/logout/crash 100% safe. OK? (default: as described)
- NOTES rights: members tick any note done (shared to-do list) but change/pin/delete only their own; Leader/Admin/Mod change, pin and delete anyone's and clear done notes; coop players read only; visitors nothing. All three are rows in the permission matrix (Read / Write / Manage notes). OK? (default: as described)
- NOTES staff view: staff open any island's notes (read only, logged) with `/is notes <island>` (an optional staff-only argument instead of a new `/isadmin notes` command) and see that island's deleted notes of the last 7 days. Notes of a disbanded island are kept 7 days but the island can't be looked up anymore; staff read them in `/logs` (every note change is logged with its full text). Add an `/isadmin notes` view for removed islands? (default: no, /logs)
- NOTES chat filter: notes use the chat filter through a new `TextFilter` service (small addition in common + the chat module). Muted players can still write island notes. Block notes while muted? (default: allowed; notes are only seen by the island team)
- NOTES temporary book: emptying a page does NOT delete its note (deleting only in the menu, so a slip can't wipe notes); writers' books show open notes only, readers' books show all notes. If the book is closed without changes the client sends nothing, so it is taken back at the time limit (300 s). OK? (default: as described)
- NOTES scoreboard (N5 optional): the placeholder `island_next_note` (newest open note, 24 characters) and a `/settings` toggle "Next note on scoreboard" (off by default) exist; the scoreboard line itself belongs to the hud module (per-place scoreboards, SB). Add a "Next: <note>" line to the island scoreboard there? (default: yes, in SB)
- NOTES holograms: max 5 pinned notes per island (`notes.max-holograms`), showing the text and the author's name; they count toward the stacking module's limit of 30 display entities per island. OK? (default: 5, counted)
- PROF profiles: a profile can only be deleted while you play on another one (switch first), so a player with 1 slot can't delete their only profile. Allow "delete and start fresh" for the active/only profile (it would empty everything and make a new profile 1)? (default: no, switch first)
- PROF profiles: deleting a profile that leads a team island with other members is refused until leadership is transferred; a solo island is removed with the profile; a member just leaves. OK? (default: as described)
- PROF profiles: a profile with active auction listings can't be deleted (cancel them first); items still in its auction collection bin are deleted with it. OK? (default: as described)
- PROF profiles: two own profiles can never share an island, **also not one after the other** (a profile can never join an island another profile of the same player was ever on, this season or before). Too strict? (default: strict, safest against moving items)
- PROF profiles: when a rank that gave slot 2/3 is lost, the profile in that slot stays but can't be switched to (it can still be deleted). Or should it stay playable? (default: locked)
- PROF profiles: a new profile gets **no** start balance (one start balance per player and season goes to the profile active at the first join of the season), so creating/deleting profiles can't print money. OK? (default: one per player)
- PROF profiles: switching has only a 10 s anti-spam cooldown (P5 gave no cooldown). Combat and the boss arena don't exist yet: their modules will refuse the switch through `ProfileSwitchEvent`; until then `profiles.yml → switch.blocked-worlds` can list worlds. Which world(s) is the boss arena? (default: none listed)
- PROF profiles: `/settings` values, the `/menu` star setting and the scoreboard on/off are per profile; vanish, staff homes and equipped titles/tags are account-wide (`profiles.yml → settings.profile-keys`). OK? (default: as described)
- PROF profiles: the scoreboard does not show the profile name yet; `messages/hud.yml` has a commented line with `<ph:profile_name>` to turn it on. Show it by default? (default: off)
- PROF profiles: `/isadmin addmember` on an offline player uses their profile 1 (staff can't pick another profile from the command). OK? (default: online players use their active profile)
- PROF profiles: profile slots bought with gems are kept forever (also after a season reset). Should a season reset also delete the profiles themselves, or keep the profiles and only reset their islands/data? (default: decided with the season system, Phase 10)
- STACK spawner speed: each spawner in a stack adds `spawners.mobs-per-spawner: 4` mobs every 10–40 s (vanilla's best case, avg ~9.6 mobs/min per spawner; a full 512 stack ≈ 4,900 mobs/min, merged into stacks of 1,024). Balance against sell prices in BAL2? (default: as given, all in `stacking.yml`)
- BAL2 spawner output: `stacking.yml → spawners.mobs-per-spawner: 4` makes one spawner earn 4× what the balance sheet assumes; spawners would be ~69% of a regular player's income and money supply doubles (`ECONOMY_BALANCE.md` §4.3, §7.3). Change it to 1? (default: suggest 1; the config stays 4 until you decide, no sell prices exist yet)
- BAL2 spawner shop price: the next spawner of a tier costs base × 1.10 per spawner of that tier the island already owns (base = 60 production hours of its own output: $117k tier 1, $213k tier 2). A fixed price lets spawners pay for each other and money compounds (×6 money supply). OK? (default: yes, for the M3.5 shop)
- BAL2 sell bonus values (all summed, then the +300% cap): best rank +15%, Sell Multiplier upgrade +5% per level (+60%), Market Contacts +2% per level (max 20 = +40%), Coinmaster +50% at set level 50, Gilded Touch +15%, personal and global boosters +50% each, sell events +25%. Nobody reaches the cap without boosters (grinder season end ≈ +143%). OK? (default: as in §2.7)
- BAL2 custom Depths tiers 9–12: price growth ×2.2 per tier instead of ×2.4, because the real sell bonuses now sit on top late in the season (grinder month 2: $39B instead of $53B; target $50B). OK? (default: ×2.2, set when M3.4 prices are written)
- BAL2 pacing (#61): a new tier's ore breaks at 70% speed with the previous tier's tool, until the next tool is forged (a short "down" before the Forge "up"). Build this into the Depths break speed (M4.9 / E4)? (default: yes as a design rule; no code change now)
- BAL2 archetypes are now 1 h / 3 h / 8 h per day (casual / regular / grinder). ENCHANTS §12 and WARDROBE §3.2 still use a 4 h/day "active" player; crystal income of the 3 h regular is ~20% lower than those plans assumed. Re-check them in E4/W4 with the new archetypes? (default: yes, no change now)
- BAL2 sink dashboard (M3.8): use the 14-day average sink ratio (target ≥ 80% per season, act below 60% for 3 days); the 7-day average dips whenever many players save for the same big purchase. OK? (default: yes)
- BAL2 crates: money rewards from crates stay inside the 30% event income (a crate is never a money printer); crates mainly give boosters, books, tier 3–5 spawners, Tomes and sets. OK? (default: yes, for the crates plan)
- STACK spawner speed: each spawner in a stack adds `spawners.mobs-per-spawner: 4` mobs every 10–40 s (vanilla's best case, avg ~9.6 mobs/min per spawner; a full 512 stack ≈ 4,900 mobs/min, merged into stacks of 1,024). Balance against sell prices in BAL2? (default: as given, all in `stacking.yml`) → BAL2 checked it: see the BAL2 spawner question below.
- STACK rare drops (SK10): spawner evokers drop a totem 2% of the time and spawner shulkers a shell 5% (vanilla: 100% / 50%), per mob killed. OK? (default: 2% / 5%, `stacking.yml → spawners.rare-drops`)
- STACK shearing: shearing a stack of sheep shears them all (wool × stack, merged into one item); other stacks (mooshrooms, snow golems) can't be sheared. OK? (default: yes, `mobs.shear-whole-stack`)
- STACK spawner mobs: zombies/skeletons from spawners don't burn in daylight and piglins/hoglins never turn into zombified ones (S25: conditions ignored). Slimes/magma cubes from spawners are always the smallest size (they drop loot and never split). OK? (default: yes)
- STACK entity limits count the island's loaded chunks (no full scan of unloaded chunks, for performance). A player could exceed a limit by building in chunks far apart that are never loaded together; islands are small (3×3 chunks at start), so this is rare. OK, or store counts in the database? (default: loaded chunks)
- STACK plugin holograms: display entities count toward the island limit of 30 (owner SK2), also those spawned by our own modules later (warps, notes). Exempt plugin holograms? (default: everything counts; modules can raise the limit later)
- STACK crops: crops in the dark grow with a 15% chance every 30 s (about vanilla speed on watered farmland); lit crops grow the vanilla way. Ripe melon/pumpkin stems in the dark also grow fruit. Saplings, sugar cane, cactus and bamboo are not included. Add any? (default: wheat, carrots, potatoes, beetroots, melon/pumpkin stems, sweet berries, torchflowers)
- STACK spawners in a crash: the database is the truth; a spawner block without a database row (only possible after a crash in a tiny window) is removed rather than trusted, so a crash can lose a spawner but never duplicate one. OK? (default: yes)
- STACK `/spawner give` amount: 1–2,304 (36 stacks) per command, admin only. OK? (default: yes)
- M4.2 Forge materials: taken from the inventory until the satchel (M4.4) exists. From tier 10 the amounts (2,824+ items) don't fit an inventory. (default: inventory now, satchel counted once it exists)
- M4.2 recipe items are vanilla placeholders until Depths drops (M4.9) get their own ids: mine coal → netherite_scrap, forest logs, combat rotten_flesh, bone, string, slime_ball, prismarine_shard, glowstone_dust, arrow, blaze_rod, wither_rose. Sword material amounts are 1/4 of the mine amounts (kills drop more value). OK? (default: yes, all in `tools.yml → tiers`)
- M4.2 break speed: tier 11/12 pickaxes get mining power +60/+120 because their ores are very hard (ancient_debris 30, crying_obsidian 50); that makes lower ores near-instant with them. Tune after an in-game test, or use softer blocks in those zones? (default: as configured)
- E1 prestige slot: normal (non-Mythic) enchants may also go in the prestige slot (slot 5); a 2-slot enchant (Dragonfire Blast, Wildfire) can't use slot 5 as its second slot. OK? (default: yes, `enchanter.yml → rules.two-slot-in-prestige-slot: false`)
- E1 names: "Scholar's Insight" is 17 characters (the plan's Bedrock rule says 16); "Scholar's Insight V" still fits a lore line. Keep, or rename (e.g. "Scholar's Mind")? (default: keep; the test allows 17)
- E1 speed family cap: +200% from bonus sources (enchants now, masteries/sets later); the 6 blocks/s hard cap stays in the block engine. OK? (default: 200, `enchanter.yml → caps.speed`)
- E1 Prosperity (+1 drop chance) counts as fortune (its expected value, extra-drops family) on the tool until E2 adds the per-drop roll. OK? (default: yes)
- E1 Book shop rolls give level I books and offer the Mining, Foraging and Combat pools (Farming/Fishing come with Phase 7). OK? (default: yes, `enchanter.yml → rolls.book-level`, `rolls.pools`)
- E1 roll tiers come from the skill level (same unlock levels as the Forge/Depths tiers, `enchanter.yml → tiers.*`) until a shared tier service exists. OK? (default: yes)
- E1 collection ("found this season", Mythics show ??? until seen): a book counts as seen when you get or use it at the Enchanter, or when it is in your inventory as you open /enchanter. OK? (default: yes)
- E1 enchant collection (guide "found this season") is per player (account), not per profile; crystals, daily rolls and books are per profile. OK? (default: yes)
- E1 crystal rounding: apply/combine fees round half up, the shatter refund rounds down (20% of 32,738 = 6,547). OK? (default: yes)
- M4.2 starter kit: no new command (none approved), so it is the "Starter kit" button in `/forge` (Quartermaster placeholder). Give it automatically on the first join? (default: off, `tools.yml → starter-kit.give-on-first-join`)
- M4.2 skins: `/skin give` adds the skin straight to the collection (no item). DESIGN says skins are tradeable: add a redeemable skin item (crates/trading) later? A skin stays on a tool when the tool is traded. (default: collection only for now)
- M4.2 prestige: the level speed bonus (+1% per level) starts again at Lv 1, so a tool is slower right after a prestige until it levels again. OK? (default: yes, "it costs the time to level again")
- M4.2 levels give speed only (+1%/level); fortune comes from the tier (+5%/tier, balance sheet). Phase 4 says "levels raise speed/fortune": add fortune per level? (default: 0, `fortune.per-level-percent`)
- M4.2 tool XP depends on the block's Depths tier only (balance sheet). Phase 4 also said a higher tool tier gives more XP per block. (default: 0% bonus, `xp.tier-bonus-percent`)
- M4.2 blueprints: 4 first proposals (Sage Pickaxe/Scholar, Magma Pickaxe/Auto-smelt, Windcutter Axe/Swift, Vampire Blade/Lifesteal), drops 1 in 15,000-20,000 blocks or 1 in 1,500 kills from a minimum tier, crafting makes a new Lv 1 tool. OK? (default: as written in `tools.yml`)
- M4.2 hoes and rods have only the starter tier until Phase 7. (default: yes)
- R2A skills: max level 150 with tiers still at 0–66. After level 66 each level costs 2% more (`skills.yml → curve.end-growth: 1.02`). With today's XP sources a top player ends a 90-day season around level 81; level 150 needs ~3.5B XP (~8.6× a top season). Should 150 be reachable in one season (then levels after 66 must get cheaper, or XP boosts bigger)? (default: 2% growth, 150 is a far goal)
- R2A `/skills set|add <player> <skill> <amount>`: the amount is in **levels** (set = start of that level, add = keeps progress), online players only. Want raw XP or offline players too? (default: levels, online only)
- R2A `/freerank`: the free tags are ember, spark and ash. `ember` was also a Rare-crate tag and `ashes` an Uncommon-crate tag, so the free pick makes the crate `ember` tag less special. Rename the crate tag, or pick other free tags? (default: as given, configurable in `perks.yml → freerank.tags`)
- R2A `/freerank`: the Ember rank gives the name color and prefix only. Which "few small perks" should it get (e.g. `/hat`, 1 extra AH slot)? (default: none until decided)
- R2A staff mode: the Inspect tool is now a blaze rod (owner #5), so the Punish tool (also a blaze rod before) is now a breeze rod. OK? (default: breeze rod)
- R2A time zone: all player/staff-facing times use `core.yml → display-timezone: Europe/Oslo` and `server/start.sh` runs the JVM with `-Duser.timezone=Europe/Oslo`. Staff logs now show CET/CEST instead of UTC (CSV export too). OK? (default: yes)
- ~~R2B sell cap: `economy.yml → sell.multiplier-cap-percent: 300` (#46), but the balance sheet (`docs/ECONOMY_BALANCE.md` §2 and `tools/economy_model.py`) still models a +100% total cap. R2A set the model's cap to +300% (`SELL_BONUS_CAP = 3.00`); the archetypes stay at ≤ +41%, so no table changed. Model players who stack boosters/sets near the cap before M3.4 prices go live? (default: yes; config stays 300 meanwhile, no sell system uses it yet)~~ Done in BAL2: every sell source is modelled and capped once (`ECONOMY_BALANCE.md` §2.7).
- R2B: the 30-minute new-account limit only stops the payer (and `/withdraw`); new accounts can still receive `/pay` and cash notes. OK? (default: yes)
- CRY colors: crystals are aqua `#4FD1FF` with `◆` (the old gems look); gems are now emerald `#3DDC97` with `✦` (Bedrock falls back to dark green, money stays light green `$`). Menu icons: crystals = prismarine crystals, gems = emerald (were amethyst shard / diamond). OK? (default: yes, all in `style.yml` and `economy.yml → icons`)
- CRY: the island shop and island missions that earn/spend crystals (CR1/CR2) don't exist yet; crystals currently only exist as a balance (`/bal`, `/eco`, HUD). Build them with the island upgrades milestone? (default: yes)
- UX1 tab: the lines beside SKYBORN are now left frost white (first letter) and right blue (the "MC"), instead of one long gradient over the whole line. OK, or try the long gradient? (default: left white, right blue)
- UX1 scoreboard: the objective line ("Next: ...") and the online count were removed to keep it at 10 lines. Bring one of them back? (default: no; the objective returns with the tutorial/quests)
- UX1 join/quit lines are now just `[+] Name` / `[-] Name` (no "joined the server"). OK? (default: yes)
- P0 freeze: frozen players get **no commands at all** by default (`staff.yml → freeze.allowed-commands: []`, the old default allowed /msg /r /tell /w /discord /rules /help). Chat still goes to staff only. Allow `/discord` and `/rules`? (default: none)
- P0 freeze: staff and plugin teleports (`/tp`, `/tphere`, a `/spawn` warmup that was already running) still move a frozen player and they stay frozen at the new spot (`freeze.allowed-teleport-causes: [command, plugin]`). OK, or staff-only? (default: command + plugin)
- P0 freeze: a frozen player hovers (flight on, no gravity) so they can't fall away; on unfreeze their own flight comes back and fall damage is ignored for `freeze.fall-grace-seconds` (10). OK? (default: yes)
- P0 freeze: with `freeze.logout-action: ban` the freeze is still stored, so after an unban they come back frozen. Clear the freeze on a ban instead? (default: keep it)
- ~~P0 staff mode: the Inspect tool is now an ender eye~~ Answered (owner #5): blaze rod (R2A).
- Islands (M2.3): the member cap (4, upgradable to 8) counts the leader, so a starting team is 4 players in total. OK? (default: yes, leader included)
- ~~Islands (M2.3): join cooldown from the later of join and leave/kick?~~ Decided (#20, ISL2): from the last join only.
- Islands (ISL2): island fly (`skyblock.island.fly`): which ranks get it? Nobody has it yet except staff with `*`; the Wings mastery will give it per island. (default: no rank until the rank perks are planned)
- Islands (ISL2): the islands world now has natural spawning (`spawn_mobs`, `spawn_monsters`) and fire spread (`fire_spread_radius_around_player: 128`) turned on in `worlds.yml`, and the islands module switches them off per island (`/is settings`: mob spawning off, animal spawning on, fire spread off by default). Without that, "on" could never work. OK? (default: yes)
- Islands (ISL2): "storm" weather is rain plus thunder sounds only the players on that island hear (`flags.weather.thunder-seconds` 25); Minecraft can't show a thunderstorm to one player only (no lightning flashes, no darker sky). OK? (default: yes)
- Islands (ISL2): "real" time follows the server clock in `visiting.timezone` (Europe/Oslo), not each player's own time zone. OK? (default: yes)
- Islands (ISL2): personal homes: `/is setmyhome [name]` (default name `my`, `homes.default-name`) and `/is home <name>`; `/is home` alone stays the island home. 1 per member (`homes.per-member`), more from masteries later. There is no delete command (setting the same name moves it); a member's homes are removed when they leave or are kicked. OK? (default: yes)
- Islands (ISL2): "Visitors pick up items" in `/is settings` is the same switch as the Visitor "Pick up items" cell in the permission matrix (one value, two places). OK? (default: yes)
- Islands (ISL2): the island "Visitor alerts" setting (notifications) only covers visitors arriving, leaving and knocking on a locked island; member changes (joins, kicks, promotions) are always announced. OK? (default: yes)
- Islands (ISL2): crop trampling off protects farmland from everyone, members too (not only visitors). OK? (default: yes)
- Islands (ISL2): admins kick mods and members (one rank lower, like the leader kicks admins); only the leader makes or removes admins. OK? (default: yes)
- Islands (ISL2): the "settings" right can only be given to Admin/Mod/Member (never coop or visitors), and only by someone who has it (the leader always). The leader can give it to mods or members in the matrix. OK? (default: yes)
- Islands (ISL2): the permission matrix now shows roles as columns (Admin, Mod, Member, Coop, Visitor) and actions as rows, 4 per page (4 pages), because 5 role rows no longer fit next to the headers. OK? (default: yes)
- Islands (M2.3): a player who already owns an island (even a fresh solo one) can't accept invites until they leave it; a solo leader can only leave by disbanding (M2.7). Allow accepting an invite to auto-disband an untouched starter island? (default: no, they must disband first)
- Islands (M2.3): invite, kick and coop are fixed to Leader + Mod (not toggles in the permission matrix). Make them matrix toggles too? (default: fixed)
- ~~Islands (M2.3): after `/is transfer` the old leader becomes a Mod?~~ Decided (#23, ISL2): Admin.
- Islands (M2.3): coop limit per island `members.max-coops` = 4, and a coop player can be coop on several islands at once. OK? (default: yes)
- Islands (M2.3): `/is go` stays "go to your island home" (same as `/is home`); visiting other islands is `/is visit` in M2.4. OK? (default: yes)
- P1 warn rules: a suggestion nobody handles within `pending-expire` (24h) disappears without restarting the counter, so the next warn suggests again (staff missed it, the player kept going). Or should expiry count like a dismiss (counter restarts)? (default: suggest again)
- P1 exempt: the exempt permission can only be read for online players (no offline LuckPerms lookup), so offline exempt staff are treated as not exempt by `/p`, the direct commands and the warn rules (same as M1.8). Add an offline LuckPerms check? (default: online only)
- P1 never shorten now applies to every path, including `/tempmute` and `/tempban`: a shorter mute/ban than an active one is refused with "already muted for longer, /unmute first" (it also fixes a player being able to chat after a short mute ended while a longer one was still active). OK? (default: yes)
- P1: `/punish <player>` opens the new single-screen menu (same as `/p`); the old category menu is "More reasons" in it, and the staff-mode Punish item opens it too. OK? (default: yes)
- P1: the Kick button in `/p` kicks with a fixed reason (`messages/moderation.yml → punish.kick-reason`); `/kick <player> <reason>` for a custom one. OK? (default: yes)
- P1: automatic mutes in `warn-rules.mode: auto` are told to staff and the player only, never announced publicly. OK? (default: staff only)
- P1: a player muted while offline (e.g. by an applied suggestion) is told on every join while the mute lasts (`muted.join`). Only once? (default: every join)
- P1 GitHub edits: `tools/sync_server_configs.py` copies the bundled resources over the tracked `server/plugins/PhoenixSkyblock/*.yml`, which would undo an owner edit of `punishments.yml` or `messages/moderation.yml` made on GitHub. The release steps in `docs/specs/PUNISH_SPEC_NOTES.md` now say to copy those edits back into the resources first. Make the sync script refuse to overwrite a tracked file that is newer in git instead? (default: documented manual step)
- Islands (M2.7): a disband, staff delete or reset never starts the 24 h **join** cooldown for anyone on the island (members and the old leader can accept an invite right away; the 3 joins per season still count). OK? (default: yes)
- Islands (M2.7): only `/is disband` starts the 24 h **creation** cooldown; a staff `/isadmin delete` or `reset` doesn't (the owner can `/is` again at once). OK? (default: yes)
- Islands (M2.7): reset stays staff-only (`/isadmin reset`, the plan lists it there only) and gives only the leader a new island; the other members are removed like in a disband. Add a player `/is reset`, or keep the team on the new island? (default: neither)
- Islands (M2.7): `/is disband` = confirm screen, then type the word `disband` in chat (`messages/islands.yml → disband.confirm-word`). Type the island name instead? (default: the word)
- Islands (M2.7): `/isadmin addmember` keeps member slots and one island per player but skips the season join limit and join cooldown (the join doesn't count). OK? (default: yes)
- Islands (M2.7): island money/bank on disband is decided with the island bank (M3.3); nothing to pay out yet. Pay the bank to the leader then? (default: decide in M3.3)
- Islands (M2.7): after a removal the clearer empties the max island size + `lifecycle.clear.extra-ring-chunks` (1) chunk rings over the full world height, with FAWE when installed (built-in throttled clearer otherwise); the slot is reused after that. OK? (default: yes)
- Islands (M2.7): `/isadmin` grants: helpers (and mods, by inheritance) get `info` and `tp`; everything else (delete, reset, setsize, addmember, kick, setowner, slots, teleport-slot) admins; `bypass` stays admins (`skyblock.island.bypass`). OK? (default: yes)
- Punishment ladders in `punishments.yml` (default proposal: Hacking 30d → perm; Toxicity warn → 1h → 1d → 7d → perm mute).
- Public ban/mute announcements for players? (default: on)
- Helpers may freeze / use staff mode and see join logs (auth)? (default: yes, IPs hidden)
- Logout while frozen: alert (default) or auto-ban?
- Achievements as their own module with progress tracking? (default: yes, later, F3)
- Deleted titles/tags: keep in DB hidden (default) or wipe?
- Discord webhook for punishments now or with the Discord integration? (default: later)
- F2: `server/permissions/luckperms.txt` was split into `base.txt` + one file per module. Updates copy files without deleting, so the old file stays on the VPS; delete `/opt/skyblock/server/permissions/luckperms.txt` once, or make `update-vps.sh`/`deploy.sh` delete removed files? (default: the apply script ignores the stale file and says so)
- Depths (M4.3): impossibly fast breaks are silently ignored (no reward, no message to the player); staff get an alert + audit row at 3 flags in 10 s (`depths.yml → anti-fast-break`). OK, or should the player see a message / get kicked? (default: silent)
- Depths (M4.3): fast-break audit rows use category `world` (there is no anti-cheat category). Add an `anticheat` audit category? (default: `world`)
- Depths (M4.3): the Depths visibility choice (everyone / island / none) is kept in memory only and resets to "everyone" on rejoin until the `/settings` toggle is built. Persist it? (default: persist once the toggle exists)
- Depths (M4.3): inside regions players can't break non-resource blocks or place blocks (`engine.protect-regions: true`). Keep? (default: yes)
- Depths (M4.1): the Mine and Forest wings come back on a row **south** of their outward row (the Combat U fills the north band). The strip directly south of The Gate (x -80..79) stays free for a possible fourth wing, which would need its own U. OK? (default: yes)
- Depths (M4.1): Combat tier unlock levels are not in WORLD.md; `zones.yml` uses the M3.0 proposal 0/6/12/18/24/30/38/46/55/66. OK? (default: yes)
- Depths (M4.1): the `depths` world spawn (worlds.yml, 0.5 100 0.5) is the center of The Gate, kept free under the brazier canopy; the arrival pad and `/depths tp gate` use the south pad (0.5 100 38.5). Move the world spawn to the arrival pad? (default: keep)
- Depths (M4.1): tier gates are open frames until M4.6 adds the per-player doors; the boss portal in Combat tier 10 already checks the tier's Combat level. OK? (default: yes)
- Depths (M4.1): `zones.yml` is not copied into the tracked `server/plugins/PhoenixSkyblock/` configs, so a deploy never overwrites coordinates builders changed on the server (the plugin writes the default on first start). OK? (default: yes)
- Depths (M4.1): `/depths generate` is admin only; builders get `/depths tp`. Should builders also be allowed to generate placeholders? (default: admin only)
- Depths (M4.3): right after a break the real ore may flash for one tick (50 ms) before the depleted block shows (the server re-sends the real block when a break is cancelled). Acceptable? (default: yes; a packet-level fix would need ProtocolLib/PacketEvents)
- M3.0 balance sheet (`docs/ECONOMY_BALANCE.md`, DRAFT): approve or change it before M3.4 prices go live. Checklist in its §12.
- M3.0: day-3 target $5M (top) vs model ~$15M: accept the smooth curve (default) or reshape tiers 1-8 / 9-12 prices?
- M3.0: endless late money sink "Mastery" after upgrade level 12 (default: yes; without it the sink ratio falls to ~25% after day 60)?
- M3.0: tool XP curve x1.21 per level instead of x1.12 so perk slots land at ~1 h / day 2 / week 1 / week 4 (default: x1.21)?
- M3.0: combat skill thresholds per tier 0/6/12/18/24/30/38/46/55/66 (default: as proposed)?
- M3.0: interest on the lowest balance of each 3 h cycle + interest only for accounts with >= 2 h play in the last 7 days (default: both on)?
- ~~M3.0: tokens tradeable via /pay or AH?~~ Decided: tokens replaced by island crystals, not tradeable (O8, CR3).
- M3.0: new accounts (< 2 h played) can't /pay or list on the AH (default: yes)?
- M3.0: island production only counts as "online" while a member was active in the last 15 min, otherwise it burns fuel (default: yes)? Also: does time in The Depths earn island fuel? (default: no, as DESIGN §5.4 says "on the island")
- M3.0: dynamic price recovery as 2% of the gap per 10 min (default) instead of a fixed 2 points; Hot Item +25% for 24 h?
- M3.0: personal bank tier unlock cost 2% of the tier's capacity (default: yes)?
- M2.1 `/is go <member island?>`: what should the argument mean once teams exist (M2.3)? (default: `/is go` = `/is home` without an argument until then)
- ~~M2.2 island protection: hostile mobs can be hurt by everyone?~~ Decided (#25, ISL2): "Hurt monsters" role permission, default yes for every role and visitors.
- M2.2: `/is bypass` is granted to admins only (`server/permissions/islands.txt`). Also moderators/helpers? (default: admins only)
- M2.2: the staff bypass toggle is per session (off after relog, every toggle and the quit-off logged). Persist it? (default: per session)
- M2.2: default role permissions (`islands.yml → permissions.defaults`): Mod everything; Member everything except spawners; Coop everything except fire and spawners; visitors nothing. OK? (default: as listed)
- M2.2: a player inside an island border can't walk/fly/be pushed out of it (also with /fly; creative/spectator and bypass excepted); they leave by /spawn, /is home etc. OK? (default: yes, `protection.keep-players-inside`)
- M2.2: falling blocks (sand, gravel, anvils) that would land outside their own island's border disappear instead of dropping an item (prevents gravity dupes at the border). OK? (default: yes)
- M2.1 Who may move the island home with `/is sethome`? (default: leader only until M2.3 makes it a role permission)
- M2.1 Players still join at spawn (`essentials.yml` `join.teleport-to-spawn: true`). Join at your island instead once islands exist? (default: keep spawn)
- M2.1 Staff build bypass on islands is permission-only (`skyblock.island.bypass`, admins) until M2.2 adds the logged `/is bypass` toggle. OK? (default: yes)
- M2.1 Scoreboard shows "Lv -" for island level until Phase 5 and no island position (`island_rank` empty). OK? (default: yes)
- ~~Economy (M3.1): `/eco give|take|set|reset` is owner-only; should admins get it too?~~ Decided (#47): owner + admin via `skyblock.admin.economy` (R2B).
- ~~Economy (M3.1): should tokens be tradeable?~~ Decided: tokens replaced by island crystals (`economy.yml → tradeable.crystals: false`, CR3).
- Economy (M3.1): `/pay` minimums $10 / 1 crystal / 1 gem / 1 shard and confirm thresholds $1M (decided) / 10,000 crystals / 100 gems / 1,000 shards. OK? (all in `economy.yml`)
- Economy (M3.1): balances are global and the ledger records the season (0 until the season service exists). At season start, which currencies are wiped (money, crystals, shards) and which kept (gems: see Economy question 2)? The starting $1,000 is given once per season per player. (default: decide with the season system)
- Economy (M3.1): `/bal <player>` lets every player see others' balances (`skyblock.economy.balance.others`, default true). OK, or staff only? (default: everyone)
- Economy (M3.1): the staff reason of `/eco` is shown to the player in `/transactions` (as the note, max 64 characters). OK? (default: yes)
- Skills (M4.5): max skill level 66 (= the highest tier unlock, Voidstone); XP keeps counting after it for leaderboards (`skills.yml → curve.max-level`). Higher cap for Phase 6 perks? (default: 66)
- Skills (M4.5): combat XP per kill is priced for the same XP per hour as the mine at the same unlock level (zombie 5, skeleton 10, spider 22, slime 40, drowned 72, witch 135, pillager/vindicator 250, blaze 420, wither skeleton 700, ravager/evoker 1,200). OK? (default: yes; M4.8 custom mobs will set their own XP)
- Skills (M4.5): Mining/Foraging XP only from The Depths, not from island blocks (generator ores, island trees); Combat XP from any mob a player kills (also on islands), but never from spawner mobs. OK? (default: yes)
- ~~Skills (M4.5): staff `/skills set|add <player> <skill> <amount>`~~ Answered (owner #68): built for admin + owner (R2A).
- Skills (M4.5): own `/settings` toggle "Skill XP in the action bar" (Display, default on) instead of reusing `tips`/`ui-sounds`. OK? (default: yes)
- Skills (M4.5): level-up audit rows use category `world` (no progression category). Add a `progression` audit category? (default: `world`)
- Skills (M4.5): anyone may view another player's skills with `/skills <player>` (`skyblock.skills.others`, default true). Keep public? (default: yes)
- Auction (M3.6): the 1% listing fee is not refunded when the seller cancels (`auction.yml → fee.refund-on-cancel: false`); expired listings never get it back. Refund on cancel? (default: no)
- Auction (M3.6): new accounts (< 30 min played, owner #40) can neither list nor buy (`anti-abuse.new-account.block-listing/block-buying`). The balance sheet only says "can't sell"; allow buying? (default: both blocked)
- Auction (M3.6): extra listing slots per rank (`skyblock.auction.slots.<n>`, default 5, cap 54). Which store/bank ranks get how many? (default: nobody has extra slots until the store ranks exist)
- Auction (M3.6): money notes are not listable (`items.allow-money-notes: false`) until the notes module (M3.2) decides. OK? (default: not listable)
- Auction (M3.6): filled shulker boxes and bundles can't be listed (their contents could hide starter kit tools or dupes; `items.allow-filled-containers: false`). OK? (default: must be empty)
- Auction (M3.6): anti-abuse defaults: price $10 to $1T, 10 s between listings, the same item at most 3 times per 30 min. OK? (all in `auction.yml`)
- Auction (M3.6): `/ah sell <price> [duration]` was added as a sub-command of the planned `/ah` (needed to list from chat; the menu can do the same). OK? (default: keep)
- Auction (M3.6): staff remove listings from the `/ah` menu (`skyblock.staff.auction.remove`, mods+, reasons in `auction.yml → staff.remove-reasons`); no staff command. Add `/ah admin` (e.g. view a player's listings/collection bin)? (default: no command until approved)
- Auction (M3.6): staff alert for sales far above an item's 7-day median or between linked accounts (balance sheet R4) is left to the M3.8 anomaly alerts. OK? (default: M3.8)
- Auction (M3.6): money only for now; listings in shards ("filters (currency later)") later? (default: money only)
- Money notes (M3.2): money notes stay NOT listable on the auction house (`auction.yml → items.allow-money-notes: false`). Allow them? (default: not listable)
- ~~Money notes (M3.2): `/eco note` owner-only?~~ Decided (#47): owner + admin, same node as `/eco give` (`skyblock.admin.economy`, R2B).
- Money notes (M3.2): limits $100 minimum / $1T maximum per note, 10 s between `/withdraw`, no fee (balance sheet §9.4). OK? (all in `economy.yml → notes`)
- ~~Money notes (M3.2): new-account limit for `/withdraw`~~ Decided (#40): 30 minutes of playtime for `/withdraw` and `/pay` (`notes.new-account-minutes`, `pay.new-account-minutes`; an untouched old 120 is changed to 30 on load). Cashing a note stays allowed for everyone (R2B).
- Money notes (M3.2): notes are money only (`notes.currencies: [money]`). Also shard notes? (default: money only)
- Money notes (M3.2): a note whose id is not in the database (e.g. spawned with a generic item give) is confiscated like a copy, with the same staff alert and a `note_dupe` audit row (`why: unknown`). OK? (default: yes)
- Money notes (M3.2): if the server crashes in the instant between taking the money and handing out the note, the note row stays active without an item; staff can see it in `/logs` (`note_withdraw`) and re-issue it with `/eco note`. Automatic recovery (e.g. a "pending" state re-delivered on join)? (default: manual, very rare)
- Money notes (M3.2): notes can't be crafted with, sold to villagers or used in a cartography table (they are paper). OK? (default: yes)

Decisions the owner still needs to make. **Developers: never guess. Add new questions here and ask.** Move answered items into `DESIGN.md`.
- Depths (M4.6): a locked tier's gate shows **iron bars** to that player only (`depths.yml → tiers.gate-block`); barriers would be invisible and confusing. Another block? (default: iron bars)
- Depths (M4.6): a player standing in a zone that becomes locked for them (staff lowered their XP, `required-level` raised) is sent to The Gate with a message. Or only block re-entry? (default: send to The Gate)
- Depths (M4.6): `/dungeon` uses its own 3 s warmup (`dungeon.warmup-seconds`, cancelled by moving or damage) because the essentials `TeleportService` is not in `common`. Move a teleport interface to `common` so every teleport shares one warmup and guards? (default: yes, as a follow-up)
- Depths (M4.6): first unlocks are audited for every tier including tier 1 (3 rows per new player when they first log in). Skip tier 1 rows? (default: audit all)
- Depths (M4.6): the season of remembered unlocks is `depths.yml → tiers.season-id` (must match `skills.yml → season-id` until a central season service exists). OK? (default: yes)
- Depths (M4.6): staff with `/depths tp` into a locked tier get a pass for that zone only (no XP or drops there); builders and admins have `skyblock.depths.bypass` (all gates open, mining counts, no warmup). OK? (default: yes)
- Punishments (P2): double-punish protection also blocks **your own** second click/command for the same player + reason within 15 s (a double click is the same mistake); the message then says "You just punished ...". Spec only names "another staff member". Only block other staff? (default: block everyone, second click within 5 s or -force applies)
- Punishments (P2): duplicate protection runs for the menu, More reasons and `/p <player> <reason>`. Direct commands (`/tempmute`, `/warn` ...) have no reason id so they are not checked; [Apply] on a suggestion is already first-click-wins. Also check direct commands by their reason text? (default: no)
- Punishments (P2): /pstats periods: `24h` = the last 24 hours; `7d` / `30d` = whole days in Europe/Oslo (today plus the 6 / 29 days before, from midnight). Or rolling 7×24 h? (default: whole days)
- Punishments (P2): [Undo] uses a server-side click callback (no new command). The chat button stops answering 10 minutes after the window (Paper then says the click expired); inside that time it says "Too late to undo". OK? (default: yes)
- Punishments (P2): undoing a warn removes only a **pending** suggestion it caused. If the suggestion was already applied (or auto-applied) the mute stays; staff remove it themselves. OK? (default: yes)
- Punishments (P2): `skyblock.staff.punish.undo.others` (undo other staff's punishments, and automatic ones) is granted to admins only. Also mods? (default: admins)
- Punishments (P2): the undo window is not shown live in the `/p` head (it refreshes after any click or when reopened); the click is always re-checked on the server. OK? (default: yes)
- Islands (M2.4): lock, unlock, expel, ban, unban and warps are for the **Leader and Mods** (DESIGN said "the Leader can lock"; mods already invite and kick). Leader only? (default: leader + mods)
- Islands (M2.4): warps are a fixed Leader/Mod right, not a cell in the permission matrix yet. Add a "warps" column to `/is members → Permissions` with M2.5 settings? (default: yes, later)
- Islands (M2.4): `/is rate` and `/is like` need a visit to that island first (`visiting.require-visit`), so nobody rates islands they never saw. OK? (default: yes)
- Islands (M2.4): `/is visit <player>` lands on the island home (safe-spot checked). A separate visitor spawn point set by the leader (M2.5 settings)? (default: home for now)
- Islands (M2.4): `/is lock` sends visitors who are on the island right now to spawn (members, coop players and staff stay). OK? (default: yes)
- Islands (M2.4): banning a member or coop player is refused ("kick first" / "uncoop first"); banning a player who is on the island sends them to spawn at once. OK? (default: yes)
- Islands (M2.4): browser numbers: "Most visited" = unique visitors per day over the last 7 days (`browser.visits-days`), "Top rated" = average stars this season with at least 3 ratings (`browser.min-ratings`), "Most liked" = likes this season, "Newest" = creation date. Locked islands are hidden. OK? (default: as listed)
- Islands (M2.4): arrival, departure and locked-visit alerts are chat lines for online members, each a `/settings` toggle (all on by default); the same visitor is announced at most every 30 s (`visiting.notify-cooldown-seconds`). Departures off by default instead? (default: all on)
- Islands (M2.4): staff (helper and up, `skyblock.island.visit.bypass`) enter locked islands and islands that banned them, can't be expelled, and look "not on your island" to `/is expel`. Vanished staff are never announced or counted as visitors. OK? (default: yes)
- Islands (M2.4): `/is visit` and `/is warp` use a 3 s warmup (`visiting.warmup-seconds`, cancelled by moving or damage; `/is home` stays instant). OK? (default: 3 s)
- Islands (M2.4): a `[warp]` sign saves the spot where the player stands when writing it; editing a warp sign is blocked (break it to delete the warp). A sign that disappears another way (explosion, piston) removes its warp the next time someone uses it. OK? (default: yes)
- Islands (M2.4): the `/menu` Warps button now opens the island warp browser (`/is warp`); there are no server warps (`/warps`) planned. OK? (default: yes)
- Islands (M2.4): `/is setwarp <name>` with a name that already exists moves that warp (keeps its icon, visits and public/private; category only changes when given). OK? (default: yes)
- Enchants (PLAN-ENCH, `docs/plans/ENCHANTS.md`): books unlock an enchant, crystals raise its level (×1.8 per step); combining two equal books is the cheaper alternative. Or should levels also need books? (default: books unlock, crystals level, EN6/EN11)
- Enchants: enchant books and Disenchant Scrolls are unique items, tradeable and listable on the auction house; never sold for gems or credits. OK? (default: yes, EN18)
- Enchants: Mythic enchants only fit the tool's prestige slot (armor: chestplate mythic slot at set level 50); Dragonfire Blast and Wildfire cost 2 slots. OK? (default: yes, EN2/EN3)
- Enchants: enchant level gated by tool level, `ceil(max level × tool level / 50)` (a prestiged tool counts as 50). OK? (default: yes, EN4)
- Enchants: no fail chance; removing with a Disenchant Scroll (◆ 2,500) returns the book at its level, removing without one shatters it for a 20% crystal refund. OK? (default: yes, EN10/EN13)
- Enchants: Enchanter book rolls for crystals (◆ 500 / 4,000 / 25,000, 5 / 5 / 3 per day, up to 10% Legendary, never Mythic). OK? (default: yes, EN9)
- Enchants: stacked-mob kills drop Slaughter/Soul Reaper/Bloodlust/Plunder books at 1 in 25,000 per mob, max 5 per day per profile. OK? (default: yes, EN15)
- Enchants: bonus family caps besides the +300% sell cap: crystals +200%, extra drops +200% (max 3 per action), skill XP +300%, tool XP +100%, rare finds +150%, mob damage +200%, damage reduction 60%, +10 hearts. OK? (default: yes, EN16)
- Enchants: PvP values: damage enchants 40%, healing 50%, AoE/stacked/Phoenix Rebirth/movement and vision enchants off in the warzone and duels. OK? (default: yes, EN17)
- Enchants: armor enchant slots on wardrobe pieces: 1 per piece, a 2nd at set level 25, chestplate mythic slot at 50 (confirmed in `docs/plans/WARDROBE.md` §3.3). OK? (default: yes, EN12)
- Enchants: staff test tools as `/enchanter admin give|apply|clear` subcommands (staff only). New subcommands need your approval. Add them? (default: not built until approved, EN20)
- Enchants: the AH should refuse buying a listing made by the same account (any profile), so items can't hop between own profiles (P4). Build that into the AH with profiles? (default: yes)
- Enchants: the 48 names in `docs/plans/ENCHANTS.md` §6 (Swiftness, Vein Rend, Dragonfire Blast, Treefeller, Slaughter, Phoenix Wrath …). OK? (default: yes)
- Wardrobe (PLAN-SETS, `docs/plans/WARDROBE.md`): sets are virtual wardrobe entries; equipping puts real armor on that is locked to the armor slots (a copy is worthless and deleted). Sets are bound to the profile, only unredeemed crate Set Tokens trade/go on the AH. OK? (default: yes, WD7/WD19)
- Wardrobe: the 16 sets (5 tier I money sets, Banneret Tabard, Lorekeeper Robes, Deepforge Plate, Thornward Mantle, Cropwarden Robes, Dreadguard Mail, Stormkeel Scale, Coinmaster Silks, Voidforged Plate, Conqueror Aegis, Phoenix Regalia), their bonuses and the new bonus families crop growth (+100%) and conquest (+60%). OK? (default: yes, WD2/WD3/WD9)
- Wardrobe prices: tier I $20k, Banneret $2.5M, Coinmaster $40M, tier III crystal sets ◆ 25,000, gem sets ✦ 400 / ✦ 900 (each also in a crate, so no power is gem-only), tier IV crate-only. OK? (default: yes, WD4)
- Wardrobe: a gem set is an account-wide "Sigil": every profile can claim the set once per season for free, and Sigils are kept across seasons. OK? (default: yes, WD5)
- Wardrobe set levels: XP from the activity the set boosts (50% with 2–3 pieces, 100% with 4; stacked island mobs give none), 200 × 1.10^level XP per level, multiplier 2 × level / 50 (Lv 25 = 1×, Lv 50 = 2×); level 50 in ~65 h of that activity for an active player. OK? (default: yes, WD10–WD14)
- Wardrobe merging: both sets level 50, no shared bonus family, tier I–III only, one merge per set, fee ◆ 5,000–25,000, merged set needs 1.5× XP, no unmerge. OK? (default: yes, WD15/WD22/WD23)
- Wardrobe Tomes (+2,500 / +10,000 set XP from crates/quests, never gems, not past level 40). OK? (default: yes, WD13)
- Wardrobe in the warzone and duels: every set counts as 1×, armor normalized to diamond-like, damage reduction at 50%, only conquest bonuses work. OK? (default: yes, WD18)
- Wardrobe Chest (storage for loadout swaps) holds unique items only (custom tools, enchant books/scrolls, set tokens, tomes), 27 slots + rank grants; plain items are never stored. OK? (default: yes, WD21/LD1)
- Loadout slots: 2 free, Knight 3, Warlord 4, Monarch 5, +1 per store-credit purchase (max +3, account-wide), max 8. Credit price with the store plan. OK? (default: yes, LD2)
- Loadout: swaps (and wardrobe equips) refused for 10 s after combat damage with a player or hostile mob, in the warzone and while frozen; 2 s cooldown. OK? (default: yes, LD3/LD6)
- Loadout: `/loadout <name>` applies a preset by name (argument of the approved command) and `/sets` stays as an alias of `/wardrobe`. OK? (default: yes, LD5/WD24)
- Wardrobe staff tools: a Wardrobe button in `/inspect <player>` (view; admins give/remove sets, set levels, give tokens, all audited) instead of a new staff command. OK? (default: yes, WD25)

## Economy
1. **Shards:** how are they earned? (Proposal: bosses, envoys, KOTH, high-tier dungeon zones.)
2. **Gems at season reset:** are store-bought gems kept or wiped? (Recommendation: keep unspent gems/credits to avoid support issues.)
3. **Personal bank:** which perk at each of the 10 tiers, and the cap per tier?
4. **Max total sell multiplier:** proposal +100% max.

## The Depths
5. **Mine drops:** ore items to sell, crystals, key chance, rare items? Exact mix per tier.
6. Number of custom ore/tree tiers after vanilla, and their names.
7. Tier unlock thresholds (blocks broken / logs / kills) per tier.

## Gear
8. ~~**Sets:** how are pieces obtained? Final set list.~~ Planned (SE1 + follow-ups): `docs/plans/WARDROBE.md`.
9. Tool prestige at level 50: confirm, and what it gives (default: permanent title + skin unlock + small bonus on that tool).

## Leaderboards
10. Team island rewards for 2nd and 3rd place.
11. Weekly leaderboard rewards.

## Other
13. Hosting specs (decides plugin budget). Owner will handle; single server at start.
14. Spawner types and the max stack size.
15. Rank perk table per rank (vault count, cosmetics, convenience).

## From design review #1 (see REVIEW.md for details and defaults)
19. Team size cap: 4 → 8 with upgrades OK? (B1)
20. Death rules in PvP zones (B7)
21. Vanilla rules table (WORLD §6): rest of the table approve or change
22. Nether/End: what is the future use? (owner has a plan, TBD)
23. Elytra: how are they obtained without the End? (proposal: crates/Black Market/events)

## Network (SkybornMC)
24. Network brand: logo drafted (sky-blue S, see branding/). Confirm or request changes. Phoenix Skyblock stays red/phoenix.
25. Other gamemodes planned, and when? (decides when the proxy + hub are needed)
26. Hub/lobby: what should it contain?

## Phase plans (defaults apply unless the owner changes them; details in docs/phases/)

Answered 2026-10-29 (moved into the plans): retention 30 days / forever, `/transactions` yes, AH 1%+5% and 5 listings, `/pay` confirm >$1M, gems not tradeable, start $1,000, island build Y 0–256, Depths logs 10 s + personal blocks, Omnitool replaced by Forge/living tools/blueprints/skins (tradeable, no durability), titles + tags in one menu.

## Decided 2026-10-29 (batch 3)
- Our `/tp`, `/give`, `/gamemode`, `/msg`, `/list`, `/help` replace the vanilla commands.
- Spawn point fixed at 0, 100, 0 (`worlds.yml`: 0.5 / 100 / 0.5); any `/setspawn` change is copied into the tracked file.
- Join IP history (`/whois`, alts) and `auth` audit rows are kept **forever** (security).
- Nicknames get a visible marker (`~Phoenix`) and reset when the player loses the rank that allowed them.
- Depths wings bend into a U inside the 2000 border; boss arena on its own north island via a portal from tier 10.
- Spawn gets the Forge Quarter (Quartermaster, Blacksmith, Enchanter).
- Builds are done by a separate builder agent/team from `docs/BUILDER_BRIEF.md`.
