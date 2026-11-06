---
name: card-writing
description: How to edit a feature card (template, Decided, Rejected, Change log, versions) so the coder builds with zero misunderstanding. Load before editing any card in features/.
---
## Template (every card keeps these 9 sections, in this order)
`# <Name> · id: <ID> · status: Draft | Review N | Locked | Built · version: N`
In one sentence / How it works / What the player sees / Rules and limits / Connections / Decided / Open questions / Rejected ideas / Change log.

## Rules
- Accepted idea: write it into "How it works" / "What the player sees" / "Rules and limits" as a plain fact with exact numbers, names, config key names and message intent. Add a line to "Decided" with the ledger id (e.g. `C1-R1-07`).
- Rejected idea: one line in "Rejected ideas" with the reason, so it never returns.
- Every edit gets a "Change log" line: `Round N, date: what changed (ids)`.
- Status: Draft -> Review N while rounds run. Locked only through `/lock`. Built only when the code matches.
- Version bumps on every Locked change after locking.
- No "maybe", "TBD", "around", "something like". If unknown, it is an open question, not text.
- English only. Use current terms (GLOSSARY.md): credits, not gems.
- Keep cards short and standalone. Move long reasoning to the round file, not the card.
- Never change a Locked card's meaning without the owner saying so; bump the version.
