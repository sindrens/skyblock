---
name: feature-review
description: Round format, tags, answer syntax and file bookkeeping for reviewing one Phoenix Skyblock feature card with the owner. Load before the first round of any feature.
---
## Round format
Exactly 20 numbered suggestions, each on its own block:

`N. [Tag] Title`
- **Change:** concrete numbers and names (what the coder would build).
- **Why:** fun, fairness, economy, performance or abuse reason.
- **Risk:** the honest downside.
- **Size:** S / M / L.

Tags: `[Bug]` `[Security/Dupe]` `[Logic/UX]` `[Synergy]` `[QoL]` `[Performance]` `[Economy]`.
Be critical, not flattering. Mix tags; do not pad with weak ideas. Principle 0 of docs/OWNER_PRINCIPLES.md: fun first, less is more. If an idea is advanced, say so and offer the simpler version as the alternative.

## Before round 1
1. Full card plus linked cards (feature-overview/GRAPH.md).
2. Disagreement table card vs docs vs code (reader subagent) in feature-overview/disagreements/<ID>.md. Show it first.
3. Stale terms from feature-overview/GLOSSARY.md.

## Owner answers
Syntax: `yes 1 4 7, no 2 (reason)`. Items not mentioned stay undecided and are re-offered, never silently accepted. "more" = 20 new ideas.
For each answer: load `conflict-check` first. On a conflict, STOP and ask.

## Bookkeeping after each answer
1. Append the answers to rounds/<ID>/round-N.md.
2. One LEDGER.md line per decision: `<ID>-R<round>-<nn> | accepted/rejected | <exact text with numbers> | <date> | affects: <ids>`.
3. For every other card an accepted item touches, add an open line to RIPPLES.md.
4. Update the card (see `card-writing`): accepted into the text, rejected into Rejected with reason.
5. `python3 feature-overview/refresh.py`.
