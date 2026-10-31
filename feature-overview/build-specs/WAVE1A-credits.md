# Build spec: wave 1a, credits rename and ledger split (for the `coder` agent, Opus 5.5)

Run: restart opencode in `~/Development/skyblock/skyblock`, then send `@coder` followed by "Do feature-overview/build-specs/WAVE1A-credits.md".
Work ONLY in the worktree `/Users/sindreskogholt/Development/skyblock-wt-wave1b` (branch `q/wave1b`, fresh from main, already created). Never touch the main checkout or `../skyblock-wt-wave1a`.
Spec = LOCKED card `features/C1.md`; decision ids `C1-R*` in `feature-overview/LEDGER.md`. Also read CLAUDE.md, docs/STANDARDS.md, docs/UI_STYLE.md.

## Hard rules (owner)
1. **NO TESTS.** Do not run any test, do not write any new test. Never `./gradlew test` or `./gradlew build` with tests. Verify only with `./gradlew compileJava compileTestJava` (touch EXISTING tests only where the rename breaks compilation).
2. Never `runServer` or any server. No git push, no deploy, no ssh/tmux, no rm outside the worktree. Small conventional commits on the branch.
3. A weaker-model draft exists on `q/wave1a` (reference only, do not trust). In the final report list every place where it was wrong or risky.

## Task (tight scope)
1. Rename GEMS to CREDITS everywhere (code, config, messages, help, hud, docs). Add `CREDITS_EARNED` (key `credits_earned`, `transferable=false` hard in code like CRYSTALS). Old `gems` rows migrate to `credits_earned`. `Currency.byKey("gems")` resolves to `CREDITS_EARNED` until config date `legacy-gems-alias-until`; refused for store grants.
2. Idempotent per-module SQL migrations (style of `modules/economy/src/main/resources/db/economy/V003__tokens_to_crystals.sql`) for EVERY table/column with the currency or gem values; grep and list them first (economy eco_accounts, eco_ledger, money_notes, eco_watch_*; auction, moderation, shop, blackmarket, wardrobe incl. `gems` column and origins `gems`/`gems_claim`, profiles slot source, boosters, rewards, trade, hud). Next free V-number per module. Both H2 and MariaDB 11.8 valid SQL; re-read it, it cannot be tested. Never edit already-applied migrations (checksums).
3. `CreditSpend` in common/economy: earned first, then bought, ONE transaction, two ledger rows, reusing `executeGuarded`/`executeAll`; think through races and refunds. `CreditView.total()` used by display, baltop, hud placeholders.
4. `/credit bal [player]` (own: split; other: total only), tab completion, messages in messages/economy.yml, permission nodes in the module permissions file + `server/permissions/` grants per the pattern, `docs/commands/economy.md`, then `./gradlew commandsIndex`. `/pay` refuses credits and points to `/credit pay` (not built now). Baltop credits = total only; crystals not on public lists. `/eco give|set credits` (bought) needs new owner-only node `skyblock.economy.credits.grant` (explicit false for admin); `credits_earned` stays staff-creatable.
5. Fix stale "store credits" wording and the RewardsSettings save-streak text.
6. Move C1-R3-03 hard-coded values to config/messages (display.exact-below, baltop.check-ticks, note purge timings, db retry count/delays, English tab hints).
7. CHANGELOG.md entry and docs/MIGRATION_CREDITS.md (deploy jar and DB together; Tebex later uses /storegrant; alias dates).
NOT in scope: /credit pay, /storegrant, CreditShop, entitlements, perks, trail.

## Final report (under 500 words)
Commits, modules touched, compile result, and an honest review per risky area (MariaDB migrations, ledger-split races, anything that could lose or duplicate balances on deploy, config key moves): what was checked, what is unverified.
