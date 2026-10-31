---
description: Start the review of one feature card (disagreements first, then round 1 of 20 suggestions)
agent: review
---
Review feature $1 using feature-overview/WORKFLOW.md (one session: write the full proposed card with 'Developer decisions' and at most 8 owner questions with recommended answers; Tier B cards get a half-page proposal; Tier C cards only a verify pass). The numbered steps below describe the old 20-suggestion loop; use them only if the owner asks for ideas.

1. Load the `feature-review` skill. Read features/$1.md in full and every card it links to (feature-overview/GRAPH.md, feature-overview/RIPPLES.md for pending ripples). Check feature-overview/LEDGER.md for earlier decisions on $1 and its neighbours.
2. Delegate to the `reader` subagent: compare card $1 with the real code, config, messages, tests and docs, plus stale terms from feature-overview/GLOSSARY.md.
3. Write feature-overview/disagreements/$1.md and show the owner the disagreement table first.
4. Give round 1: exactly 20 numbered suggestions, tagged [Bug] [Security/Dupe] [Logic/UX] [Synergy] [QoL] [Performance] [Economy], each with what to change (concrete numbers), why, risk, size S/M/L. Be critical and honest. Run the `critic` subagent on your draft before showing it and drop or fix anything it blocks.
5. Save the round to feature-overview/rounds/$1/round-1.md (suggestions only; answers get added when the owner replies). Set $1 to Reviewing in INDEX.md via refresh.py. Then wait for the owner's answer.

Current ledger for $1:
!`grep -n "^$1-" feature-overview/LEDGER.md || echo "(no decisions yet)"`
