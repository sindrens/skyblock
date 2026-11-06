---
description: Lock a feature card so the coder can build it (runs the completeness and conflict checks)
agent: review
---
Lock feature $1.

1. Load `card-writing` and `conflict-check`. Check ALL of: no open conflicts; every accepted ledger entry for $1 is written into the card with numbers; every rejected one is in the card's Rejected list with its reason; Open questions is empty (or the owner explicitly accepts each as a default); RIPPLES.md has no unresolved ripple pointing TO $1 (ripples from $1 to other cards are expected; they are resolved when those cards are reviewed); no stale GLOSSARY terms; the card says exactly what the coder must build, with no "maybe" or "TBD".
2. Run the `critic` subagent on the diff of features/$1.md and the ledger entries.
3. If any check fails, list what is missing and stop. Do not lock.
4. If all pass: set the status to Locked, bump the version, add a Change log line, update LEDGER.md and INDEX.md (refresh.py), and show the owner the final card. Do not commit; ask first.
