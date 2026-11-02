# Island Guide NPC ("Kindle")

Owner, 2 Nov 2026: "an NPC on the islands that helps you get going, introduces the island, gives help when you unlock
more things and gives you quests; when the island upgrades you unlock more quests from him; synergy with /is masteries
and other relevant things and the Depths. We need to improve this idea."

## The idea, improved
Kindle is one face for everything an island does, so a new player never has to learn a list of commands:

| Kindle shows | Comes from (already built) | Synergy |
|---|---|---|
| **Next step** (one sentence, click does it) | tutorial / HUD objective | the tutorial's steps point at Kindle once he exists |
| **Challenges** (the island task book) | progression tasks (M5.3) | island level-ups unlock new task tiers: Kindle announces them |
| **Quests** (daily / weekly / island) | quests module | |
| **Milestones** (ready tiers glow) | milestones | island milestone tiers now feed the island level |
| **Upgrades** and **Masteries** | progression (M5.4/M5.5) | |
| **Island level** | progression (M5.2) | |
| **The Depths** (your tier, next unlock) | depths | Depths tasks in the book |
| **Help** | /help island | |

v1 (built now, `guide.yml enabled: true`):
- One Kindle per island, standing 2 blocks from the island home (or where the leader/admin put him with `/is guide move`).
  Mannequin body (skin configurable), name line plus a status line ("Click to talk", "★ Something new!").
- Click (any button, Bedrock tap) → the Kindle menu above. Visitors see a short "this is <island>'s guide" line.
- Island level-up → online members get "Kindle has something new for you [Talk]" and his status line shows ★ until a
  member opens his menu.
- `/is guide` opens the same menu anywhere (accessibility), `/is guide move` moves him (leader and admins, inside the
  island border).

## Later (needs the owner)
- GD-1 **Kindle's own quest line**: a short chain of guided quests per island stage (start, level 10, 25, 50 ...), each
  explaining one system (generators, spawners, upgrades, Depths) and paying a small reward through Reward Core. Today the
  challenge book plays that role. Build the own line, or keep the book?
- GD-2 **Talking lines**: Kindle says one tip line when a member walks near him (once per session)?
- GD-3 **Skin**: a phoenix-themed skin name for `guide.yml skin`.
- GD-4 Should Kindle's ★ also light for ready milestones / quests (per player, so only in the menu, not above his head)?

## v2 (owner answers 2 Nov 2026, built)
- **Kindle's story** (`guide-quests.yml story`): one delivery quest at a time, shared by the island ("bring me 30
  wheat"), each harder than the last, some waiting for an island level; rewards pay about 2-3x the sell value plus
  crystals and now and then a key.
- **Errands**: 2 per player, fresh every 3 hours (never shown as a timer). **Finds**: 3 item trades per player per
  rotation ("useful things Kindle found", paid with crops, ores and mob drops, never money), filtered by island level.
- Hand-in is dupe-safe: plain items are taken first, one DB transaction records it (primary key) and pays through the
  ledger, items go back on any failure.
- Kindle speaks when a member comes within 5 blocks, once per rotation ("you have everything for my quest", "could you
  bring me...", "I have new quests and finds"). Title line "Island Guide" above his name. Guide book in his menu.
- Skin: owner asked for "something cute"; `guide.yml skin` takes a player name (set it in /admin).
