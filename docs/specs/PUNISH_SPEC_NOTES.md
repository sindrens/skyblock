# Phase P: notes and corrections to PUNISH_SPEC.md (main session review, 2026-10-29)

These override the spec where they conflict. Anything still open is in docs/OPEN_QUESTIONS.md → "Waiting for owner".

## Corrections (project facts the spec got wrong or out of date)
1. **Staff mode is `/staffmode`** (alias `/sm`); `/staff` is the staff hub menu. The staff-mode Punish item must open `/p <target>`.
2. **Texts never live in `punishments.yml`.** Layout keeps ids, slots, icons, weights and rules only. Names/lore/reason texts are message keys in `messages/moderation.yml` (e.g. `punish.warn-buttons.<id>.name`). Placeholders use our MiniMessage style `<player>`, never `{player}`.
3. **YAML 1.1 booleans:** keys like `yes`, `no`, `on`, `off` must be quoted (`"yes":`). `YamlKeysTest` enforces this for every tracked YAML.
4. **Evidence source (12.4):** chat is already in the audit log (category `chat`, 30 days). Copy the lines from the audit log into `punishment_evidence` at punish time instead of keeping a second in-memory ring buffer.
5. **Economy and islands now exist** (M3.1, M2.1/M2.2). `SanctionProvider` (13.1) can be implemented by the economy module right away; island steps (13.2) need M2.3 (members/coop) for `coop-remove`/`coop-block`.
6. **Discord (12.11 + 14.9 + 13.10 step 1):** one `DiscordSink` in common with named webhooks (secrets via `apply-secrets.sh`), used by all three. All channels disabled by default.
7. **Guard tiers (14.1)** map to our LuckPerms ranks: `staff` = helper, `moderator` = mod, `admin` = admin (owner unlimited).
8. **Rules link:** `https://skybornmc.net/rules` (the website), not `play.skybornmc.net`.
9. **Owner edits on GitHub (section 6):** releases go through the main session's release script, which must first fetch GitHub and bring the owner's commits into the working repo before merging and pushing (otherwise the push is rejected). Implemented with P1.
10. **Bottom row slots** (spec mentions several buttons in different sections): 45 Sanctions, 46 Kick, 47 Bulk, 48 History, 49 Close, 50 More reasons, 52 Dry run. 51/53 filler. Configurable.

## Critique / risks
- Very large scope (≈ 10 milestones). Built strictly in the spec's order, one moderation agent at a time (same module), each milestone released and tested on its own.
- Approval (13.4), training (14.6), guard (14.1), duplicate protection (12.2) and probation (13.7) all change what one click does. A single `PunishDecision` pipeline (in order: exempt → guard → approval/trainee → duplicate → never-shorten → probation → apply) must be the only path, so menu, `/p <reason>`, `[Apply]`, bulk and appeals cannot drift apart. Unit-tested as one table of cases.
- Raid lockdown (13.9): only "new" accounts, manual by default, detection only alerts. Must be cleared on restart and visible to staff while active.
- Purge (14.10): legal retention is the owner's call (not legal advice).
- Section numbering in the spec is out of order (12 after 9; 10/11 at the end); content is complete.

## Milestones (see docs/QUEUE.md "Phase P")
P0 bugs · P1 sections 1–11 · P2 12.1/12.2/12.3/12.10 · P3 rest of 12 · P4 13.4/13.13/13.7/13.5 · P5 13.8/13.11/13.10 · P6 13.9/13.3/13.12 · P7 13.1/13.2 · P8 14.2/14.9/14.6 · P9 14.1/14.5/14.10 · P10 14.8/14.11
