# Efficient review workflow (agreed 31 Oct 2026; replaces the 20-suggestion rounds)

Goal: fewer rounds, fewer tokens, better decisions. The developer decides what the developer knows better; the owner decides what is game feel, money and look.

## Steps per card (one session)
1. **Read** the card, its linked cards (GRAPH.md) and the code facts (cheap code check only on the files that matter). Load `OWNER_PROFILE.md` and the ledger.
2. **Write the proposed card in full** (the whole design with numbers) into `features/<ID>.md` as status `Review`, with two extra sections:
   - **Developer decisions** (security, dupes, performance, data, tests, Bedrock, config, permissions, commands): written as defaults. The owner only vetoes.
   - **Questions for the owner** (max 8, game feel / difficulty / money / look only), each with a recommended answer.
3. **One owner reply** ("ok", "change 3: ...", or free text). Check the answers against the ledger; stop and ask only on a real conflict.
4. **Lock**: fold answers into the card, ledger lines (one per decision, short), ripples, `refresh.py`, status Locked. One critic pass at lock only for Tier A cards.
5. Disagreement tables go into the card's "Reality check" section, not a separate file, unless they exceed a page.

## Tiers (see TIERS.md)
- **Tier A** (about 24 cards): full design, one session each, critic at lock.
- **Tier B** (about 46 cards): half-page proposal; owner says "OK" or "change X"; no critic.
- **Tier C** (about 30 cards): built or low-impact; only a verify pass and known bug fixes; no review.

## Clusters (cards that share decisions are reviewed together, shared questions asked once)
See TIERS.md. Order is by cluster.

## Token rules
- No raw answer dumps in round files; the ledger line is the record.
- No 20-suggestion rounds unless the owner asks for ideas ("more ideas for X").
- Code checks use the cheapest agent and read only the named files.
- Do not re-explain locked cards; link to them.

## Build early
A card that is Locked and does not depend on an unlocked card may be built at once by the `coder` agent from its card plus a short build spec. Wave 1 = C1 (credits split, rename, purchase path) and B17 fixes.
