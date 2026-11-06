---
name: conflict-check
description: Mechanical check of an owner answer against earlier answers, the ledger, card Decided/Rejected lists, owner principles and linked cards. Load before writing any answer down.
---
Before writing ANY answer into a card or the ledger:
1. Search feature-overview/LEDGER.md for the same topic, number, currency, role or system (grep keywords, not just the card id).
2. Read the card's Decided and Rejected sections and the Decided sections of the cards linked in GRAPH.md.
3. Check docs/OWNER_PRINCIPLES.md, docs/OWNER_ANSWERS_*.md and docs/DECISIONS_2026-10-30.md.
4. Check for numeric clashes (two different prices, caps, cooldowns for the same thing) and rule clashes (e.g. a yes that creates a way to move value between profiles, a waiting time against principle 1, a paid advantage against principle 8).

If ANY conflict exists: STOP. Quote both statements with their ids/sources, say how they clash, and ask the owner what they meant. Write nothing until answered. Record the resolution in the ledger, naming both ids.
If an accepted item changes another card, add it to RIPPLES.md.
