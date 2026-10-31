---
description: Check a card against the code and for completeness (also used for Built cards)
agent: review
---
Verify feature $1.

- Delegate to the `reader` subagent: card features/$1.md versus code, config, messages, permissions, migrations and tests, plus stale terms.
- Check the card itself: all 9 sections present, Decided and Rejected filled in, Open questions empty or intentional, version and status consistent with INDEX.md and LEDGER.md.
- Report mismatches in a table. For a Built card, propose card fixes (the code is the truth unless the owner says otherwise) and ask before writing. Never edit code.
