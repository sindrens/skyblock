# Feature overview: how the feature review works

Phase: **feature review. No coding.** Every feature card in `features/` is reviewed with the owner, one at a time, until it is **Locked**. Only Locked cards are built (by the `coder` agent, from `build-specs/<ID>.md`).

## Setup
- Agents (`.opencode/agents`): `review` (primary, default), `planner`, `reader`, `critic`, `coder`. Models: sonnet for all but `coder` (opus).
- Commands: `/feature <ID>`, `/more`, `/lock <ID>`, `/next`, `/verify <ID>`, `/review-diff`.
- Skills: `feature-review`, `card-writing`, `conflict-check`.
- `opencode.json`: bash allowed; asks before git commit/push/merge/reset/rebase/clean, gh, deploy/release/vps scripts, ssh/scp/rsync, tmux, rm; `./gradlew runServer` denied.

## The loop (one feature)
1. `/feature <ID>`: the card, its linked cards (GRAPH.md) and the real code are read. **Disagreements card vs docs vs code** come first (`disagreements/<ID>.md`).
2. **20 suggestions per round**, numbered, tagged `[Bug] [Security/Dupe] [Logic/UX] [Synergy] [QoL] [Performance] [Economy]`, each with change (numbers), why, risk, size S/M/L.
3. Owner answers: `yes 1 4 7, no 2 (reason)`. Unmentioned items stay undecided. `more` = 20 new ideas, no repeats of accepted or rejected ones.
4. **Conflict rule:** if an answer conflicts with an earlier answer on any feature, the review stops and asks what was meant. Nothing is written until resolved.
5. Each answer is written to: `rounds/<ID>/round-N.md`, `LEDGER.md`, `RIPPLES.md` (if other cards are touched), and the card itself.
6. When no useful ideas are left: `/lock <ID>` runs the completeness and conflict checks. Then `/next`.

## Files
| File | Purpose |
|---|---|
| `INDEX.md` | Dashboard of all 102 cards (generated) |
| `ORDER.md` | Review order, biggest and most connected first (generated; the fixed head of the list is in `refresh.py`) |
| `GRAPH.md` | Card links from each card's Connections section (generated) |
| `CODE-MAP.md` | Card -> module/package; a starting point the reader verifies |
| `GLOSSARY.md` | Stale terms in the cards (gems -> credits ...) |
| `LEDGER.md` | One line per decision, never deleted |
| `RIPPLES.md` | Knock-on edits other cards need; `/lock` refuses while open |
| `disagreements/<ID>.md` | Card vs docs vs code, per feature |
| `rounds/<ID>/round-N.md` | The 20 suggestions and the owner's answers |
| `build-specs/<ID>.md` | Exact build instructions for the coder, written after Locked |
| `refresh.py` | `python3 feature-overview/refresh.py` regenerates INDEX, GRAPH, ORDER |

## Card states
Card status line: `Draft` -> `Review N` -> `Locked` -> `Built`. Review state in INDEX: Todo, Reviewing, Locked, Built (verify), Built (reviewed).

## Where truth lives
The cards were written 31 Oct 01:03 and are partly stale (gems are now credits; REVIEW3 and pack-v1 came later). On disagreement: code and the live docs (`docs/FEATURES.md`, `docs/OWNER_ANSWERS_*.md`, `docs/OWNER_PRINCIPLES.md`) beat the card, unless the owner decides otherwise in a round.

Principle 0: **fun first, less is more.** If a suggestion is too advanced, say so and offer the simpler version.
