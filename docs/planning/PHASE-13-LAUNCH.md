# Phase 13: Balance and launch prep (outline)

**Status:** needs owner input (outline only)

## What you already decided
- **ROADMAP:** full balance pass, bot load test to 300+ players, closed beta, bug fixing. Launch in about 3 years; everything in season 1.
- **94:** whitelist until closed beta, then open with a launch countdown in the server list.
- **95:** server-list text "PHOENIX SKYBLOCK - Season 1 starts DATE - Java and Bedrock".
- **96:** one-command maintenance mode, staff only.
- **422:** raid alerts at 60 new accounts per 30 s in launch week, then back to 15.
- **78:** you or a trusted tester checks each pack release on a Bedrock phone.
- **650 / 692 / 711:** balance sheet, sell and shop prices go live on the test server first, tuned after testing. **R3-37:** day-3 money target $5M vs $15M.
- **RTA-1:** check Mojang's usage guidelines before the store opens.
- **319 / 529 / 532:** privacy policy, logging notice, IP data deleted after 12 months.
- **PERFORMANCE.md §5** (load test plan) and **SECURITY_ANTI_EXPLOIT.md §5** (test plan) are written.

## What exists in the game now
Test server with deploy scripts, MOTD module, whitelist, balance model (`tools/economy_model.py`), economy dashboard, audit log, backups.

## The problem / what to decide
Who tests, how long the beta is, what must be true before launch day.

## Our proposal (milestones)
- **M13.1 Balance pass:** run the model with every system built, fix outliers, owner approves the final numbers.
- **M13.2 Load test:** bots to 350 players on a staging copy, acceptance targets from PERFORMANCE.md §5.5.
- **M13.3 Security pass:** every test in SECURITY_ANTI_EXPLOIT.md §5, dupe hunt with testers.
- **M13.4 Closed beta:** 2-4 weeks, invited players, then a full wipe.
- **M13.5 Launch checklist:** Mojang check, privacy policy page, rules page, store live, Discord ready, pack checked on Bedrock, backups tested by a real restore, maintenance mode tested, staff trained.
- **M13.6 Launch week:** countdown MOTD, raid alert 60, extra staff schedule, daily hotfix window.

## Questions for you
1. Closed beta of 2-4 weeks with invited players, then a full wipe? **Suggested: Yes.**
2. Bot load test to 350 players before the beta? **Suggested: Yes.**
3. A real restore test from backup before launch? **Suggested: Yes.**
4. Beta testers get a cosmetic "Beta Tester" title? **Suggested: Yes.**
5. Launch countdown in the server list for the last 7 days? **Suggested: Yes.**
6. Daily hotfix window during launch week (a short restart at a fixed time)? **Suggested: Yes.**
7. Who are your trusted testers (Java and Bedrock)? Write below.

## Your notes (write here)

