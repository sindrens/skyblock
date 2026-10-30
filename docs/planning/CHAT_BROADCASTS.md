# Chat broadcasts (automatic messages)

**Status:** needs owner input

## What you already decided
- **612:** a new message in chat every X minutes or hours: donate to the store, remember to vote, and messages like "sugarcane prices have gone up 22% the past 24 hours".
- **31 / 305:** English only.
- **UI_STYLE §3.3:** announcements: blank line, centered header with the brand gradient, 1-2 lines, a clickable action, blank line. Max one per 30 s from the auto-announcer.
- **500:** ban/mute announcements are public unless `-s` (separate system, stays as is).

## What exists in the game now
- Staff `/broadcast <message>` (`/bc`, `/announce`): chat + title + sound.
- `/settings` has a **Tips** toggle ("short tips the first time you open a feature") and **AFK announcements**.
- No automatic rotation yet.

## The problem / what to decide
How often, which kinds, who can turn them off, and how they look without spamming 300 players.

## Our proposal
**One rotation** every **5 minutes** (config), never more than one auto message per 5 minutes.

**Kinds (each with a weight and its own lines in `broadcasts.yml`):**
| Kind | Example | Can players turn it off? |
|---|---|---|
| Vote | `Vote for us and get an Ember key + 3 gems. /vote` | No |
| Store | `Support the server and get a rank. /store` | No |
| Tips | `Tip: sneak + right-click an NPC to see what it does.` | Yes (Tips toggle) |
| Market | `Sugar cane is up 22% in 24 hours. /sell` | Yes (new Market toggle) |
| Events | `KOTH starts in 10 minutes at the warzone.` | No (events have their own timing) |
| Discord | `Join our Discord for news and giveaways. /discord` | No |

**Look (short, Bedrock-safe):**
```

 » VOTE  Vote for us and get an Ember key + 3 gems.
   [Vote now]  (or type /vote)

```
- Kind word in its color (`» VOTE`, `» TIP`, `» MARKET`, `» EVENT`), one line, then the action.
- Java: clickable `[Vote now]`. Bedrock: the command is always written out.

**Rules:**
- No repeat of the same line within an hour; kinds rotate.
- Not shown to players in the tutorial (they get Pip's help instead).
- Vote line skips players who already voted on all sites today.
- Store line at most once every 30 minutes.
- Lines are in `broadcasts.yml`, reload without restart. Staff can preview with `/broadcast preview <id>`.

## Questions for you
1. One auto message every 5 minutes? **Suggested: Yes.** Write another number below if you like.
2. These six kinds: Vote, Store, Tips, Market, Events, Discord? **Suggested: Yes.**
3. Players can turn off Tips and Market lines, not Vote, Store, Events or Discord? **Suggested: Yes.**
4. Skip the vote line for players who voted on every site today? **Suggested: Yes.**
5. Store line at most once every 30 minutes? **Suggested: Yes.**
6. No auto messages during the tutorial? **Suggested: Yes.**
7. Short look: one line with `» KIND`, then the action, blank lines around it? **Suggested: Yes.**
8. Market lines for movers of ±15% in 24 hours (see SELL_PRICES.md)? **Suggested: Yes.**
9. Add `/broadcast preview <id>` for staff (new sub-command)? **Suggested: Yes.**
10. Write the first 30 tip lines for you to approve? **Suggested: Yes.**

## Your notes (write here)

