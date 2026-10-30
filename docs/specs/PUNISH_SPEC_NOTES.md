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
9. **Owner edits on GitHub (section 6):** releases go through the main session's release script, which must first fetch GitHub and bring the owner's commits into the working repo before merging and pushing (otherwise the push is rejected). Documented with P1 below ("How owner edits flow"); nothing in tools/ or server/scripts deploys on its own.
10. **Bottom row slots** (spec mentions several buttons in different sections): 45 Sanctions, 46 Kick, 47 Bulk, 48 History, 49 Close, 50 More reasons, 52 Dry run. 51/53 filler. Configurable.

## Critique / risks
- Very large scope (≈ 10 milestones). Built strictly in the spec's order, one moderation agent at a time (same module), each milestone released and tested on its own.
- Approval (13.4), training (14.6), guard (14.1), duplicate protection (12.2) and probation (13.7) all change what one click does. A single `PunishDecision` pipeline (in order: exempt → guard → approval/trainee → duplicate → never-shorten → probation → apply) must be the only path, so menu, `/p <reason>`, `[Apply]`, bulk and appeals cannot drift apart. Unit-tested as one table of cases.
- Raid lockdown (13.9): only "new" accounts, manual by default, detection only alerts. Must be cleared on restart and visible to staff while active.
- Purge (14.10): legal retention is the owner's call (not legal advice).
- Section numbering in the spec is out of order (12 after 9; 10/11 at the end); content is complete.

## Milestones (see docs/QUEUE.md "Phase P")
P0 bugs · P1 sections 1–11 · P2 12.1/12.2/12.3/12.10 · P3 rest of 12 · P4 13.4/13.13/13.7/13.5 · P5 13.8/13.11/13.10 · P6 13.9/13.3/13.12 · P7 13.1/13.2 · P8 14.2/14.9/14.6 · P9 14.1/14.5/14.10 · P10 14.8/14.11

## How owner edits flow (P1, section 6)
The owner edits `server/plugins/PhoenixSkyblock/punishments.yml` (ids, slots, icons, ladders, warn rules) and
`server/plugins/PhoenixSkyblock/messages/moderation.yml` (every text) on GitHub: pencil icon → commit to a branch → pull
request → merge. The CI build runs `PunishConfigTest`, which parses the tracked file and the bundled default with the
same validator the server uses, so a typo (`bann 3d`, a duplicate slot, an unknown reason id, a rule action `warn`)
fails the pull request before anything is deployed.

Release steps for the main session (before each release, in this order):
1. `git fetch origin && git merge origin/main` in `/home/claude/skyblock` (brings the owner's GitHub commits in; without
   it the push is rejected).
2. If the owner changed a tracked `punishments.yml` / `messages/moderation.yml`, copy it back over the bundled copy
   (`modules/moderation/src/main/resources/punishments.yml`, `plugin/src/main/resources/messages/moderation.yml`) and commit,
   **before** running `python3 tools/sync_server_configs.py` (that script copies resources over the tracked files and
   would otherwise undo the owner's edit; open question in docs/OPEN_QUESTIONS.md).
3. Merge the queue branches, `./gradlew build` (includes the config test), bundle, push, deploy as usual.
4. On the server the new file is used at the next start or with `/skyblock reload moderation`. A broken file is rejected
   as a whole: the last working config stays active, the console and online staff with `skyblock.admin.reload` get every
   error with its line number (staff again on join until fixed), and an audit row (`config / punishments_config_rejected`)
   is written. On a first start with a broken file the built-in default is used.

## P1 implementation notes (for P2+)
- **One pipeline:** `PunishDecision` (pure) with slots in order EXEMPT → GUARD → PERMISSION → APPROVAL → LENGTH_LIMIT →
  DUPLICATE → NEVER_SHORTEN → PROBATION. P1 fills EXEMPT, PERMISSION, LENGTH_LIMIT, NEVER_SHORTEN; later milestones plug in
  with `PunishDecision.with(slot, stage)` (a stage may stop with a verdict or change the request, e.g. probation +1 step).
  `PunishmentStore.issue` runs the decision and the insert in one transaction under a per-target lock (active mute/ban read
  inside it), then evaluates the warn rules in the same transaction. Menu, `/p <player> <reason>`, the direct commands,
  [Apply] and auto mode all use it; menu lore uses the same pipeline (`PunishmentService.preview`).
- **Warn counter** (`WarnRules`, pure): derived from `punishments` + `punishment_suggestions` (V002). Handled triggers
  (applied, dismissed, auto, skipped) restart the counter at the moment they were handled and open the repeat window from
  their creation; one pending suggestion at a time; expired pending suggestions count as never fired; automatic
  punishments (`source auto:...`) never trigger anything. `source` on punishments: NULL = manual,
  `auto:warn-rule:<at>` / `auto:warn-rule:repeat`.
- **Suggestions table** is generic (`kind`, `source`) so 12.8 (chat filter) and 13.3 (`/psuggest`) can reuse the claim
  logic (`PunishmentStore.apply/dismiss`, first click wins).
- **Texts** moved out of `punishments.yml`: `reasons.<id>`, `categories.<id>`, `warn-buttons.<id>.name/.reason`; a legacy
  `name:` in the YAML is still read as a fallback.


## P2 implementation notes (for P3+)
- **Config:** `punishments.yml → extras` is parsed into `PunishConfig.Extras` (undo-window, duplicate-window,
  duplicate-confirm, offence-expire, stats.timezone/cache/default-period/top). Later milestones add their keys to the same
  record and parser method. Per reason: `offence-expire: perm | <time>` (cheating reasons ship with `perm`).
- **Duplicate stage** (`PunishDecision.withDuplicates`, slot DUPLICATE): input fields `reasonKey` (= stored `template`:
  reason id or warn-button id), `force`, `confirmed`, `recent` (newest not-pardoned punishment for that reason, read in
  the issue transaction under the target lock). A duplicate verdict stays clickable in the menu; the service arms
  `DuplicateGuard` and the next identical request within `duplicate-confirm` is `confirmed`.
- **Undo:** `Undo.check` (pure) + `PunishmentStore.undo` (atomic). The chat button is an Adventure click callback (no
  extra command); Bedrock uses the `/p` head. Undo = pardon with reason `undo.reason`; warn-rule suggestions carry
  `cause_id` (V003) and a pending one is set to status `REMOVED`, which the rules treat as never fired (filtered out of
  `PunishmentStore.suggestions`).
- **Offences:** `Offences.counting/expired` with `Template.cutoff(now)`; `Target.previous(template, cutoff)`.
- **Stats:** `PunishStats` (periods, argument + permission rules, aggregation, ranking, 60 s cache); queries in
  `PunishmentStore.statRows/topRows` use the V003 index `(issuer_uuid, created)`. Rows without an issuer uuid (system,
  console) never count.

## P3 implementation notes (for P4+)
- **Config:** `PunishConfig.Extras` gained `closeReports`, `evidence`, `notes`, `ack`, `rulesLink`, `discord`, `playerView`,
  `chatSuggestions` (nested records, parsed in `PunishConfigParser.extras`). A `webhook`/`url` key under `extras.discord` is a
  config error: the URL is a secret (`plugins/PhoenixSkyblock/secrets/discord.yml`, written by `apply-secrets.sh` from
  `DISCORD_WEBHOOK_PUNISHMENTS`).
- **Side effects** live in `PunishExtras` and are called by `PunishmentService` after apply (`applied`, evidence + Discord),
  after a staff apply (`linkReports`), after pardon/undo (Discord) and from `apply()` for warns (`warned`, acknowledgement).
  They never throw into the punishment path.
- **Weights:** tenths everywhere (`Weights`): `Punishment.weight`, `Templates.Step.weight` (`warn 0.5`),
  `WarnButton.weight`, `WarnRules.Counter.tenths()/text()`, `Trigger.tenths()`, `Suggestion.warnTenths`. Column
  `punishments.weight DECIMAL(5,1)`, NULL = 1. Rule check: `Weights.reaches(tenths, at)`.
- **V004:** `punishments.weight/acknowledged_at/ack_join_sent`, `punishment_suggestions.warn_tenths/reason_id/details`,
  tables `punishment_evidence` and `staff_notes`.
- **Suggestion API:** `common/moderation/SuggestionService` (moderation provides `ApiSuggestions`). Rows have kind `api`,
  `source = auto:api:<reason>`, `reason_id`; one PENDING per target + reason (`PunishmentStore.suggestApi`, under the
  target lock). `/warns <p> apply|dismiss <id>` and the existing claim logic handle them; the warn rules ignore them.
  13.3 (`/psuggest`, Grim) should reuse `suggestApi`.
- **Report link:** `common/moderation/ReportLink`, provided by the staff module (`ReportService`), atomic
  `StaffStore.closeClaimedBy`.
- **Discord:** `common/discord/DiscordSink` (named channels); moderation registers `WebhookDiscordSink` as the service.
  13.10 and 14.9 add their channel names and switches.

## P4 implementation notes (for P5+)
- **Pipeline (slot order changed):** EXEMPT → GUARD → PROBATION → PERMISSION → APPROVAL → LENGTH_LIMIT → DUPLICATE →
  NEVER_SHORTEN. Probation moved up so every later stage (permission, approval, length limit, never shorten) sees the
  step that will really be applied. `PunishDecision.full(duplicateWindow, approval)` is the live pipeline
  (`PunishmentService.decision()`).
- **Approval (13.4):** new `Outcome.REQUEST`. `Approval.Rule` parses `ban|ipban|mute` + `perm | * | <time> | >= <time> |
  > <time>`; `ban` covers IP-wide bans too. A non-approver (`Actor.approver`, `skyblock.staff.punish.approve`) gets a
  request when a rule matches **or** the length is over their rank limit (the "too long" error only remains with
  approval off, and for approvers: their limit stays the hard maximum, also when approving). The system and the console
  never need approval. A request that would change nothing returns the never-shorten SKIPPED verdict instead.
  `PunishmentStore.issueBlocking` stores the request in the same transaction/lock (`punishment_requests`, V005); merge key
  = reason id, else `type:reason text` (`Approval.mergeKey`), only into a PENDING, unexpired request. `approve` decides
  again with the approver's actor and inserts with `issuer` = requester, `approved_by_*` = approver (first click wins;
  BLOCKED stays pending; already longer = SKIPPED). `deny` needs a reason (preset ids `extras.approval.deny-reasons`,
  texts `approval.deny-reasons.<id>`, or typed via the menu chat prompt). Requester told now (`markNotified`) or on join
  (`requesterUpdates`, also "expired without an answer"). [Apply] on a suggestion by a non-approver → request + suggestion
  status `REQUESTED` (note `request:<id>`, counts as handled). [Approve]/[Deny] are Adventure click callbacks (no new
  command); the Bedrock path is the `/p` head → `RequestMenu` → `DenyMenu`. **P5 bulk** must call the same pipeline per
  player: a REQUEST verdict there is simply one request per player (spec: bans perm in bulk need approval per player).
- **Probation (13.7):** derived, never stored: `Probation.until(history, templates::probation, settings, now)` = newest
  natural end (MUTE/BAN, not removed, `expires <= now`) + duration. `probation:` per category (default true) with a
  per-reason override (`Templates.Template.probation`); a reason with it off neither starts probation nor gets the bonus.
  Requests from a ladder carry `PunishDecision.Escalation(ladder, index, ipFlag, probationUntil, bonus)`; the stage raises
  the index (`Probation.raise`, capped) and the store writes the raised `ladder_step`/weight. Menu lore uses the same
  `preview(..., escalation)` and `PunishMenus.raisedIndex`.
- **Mute ended (13.13):** `EndTimers` (one Bukkit task per online player with a timed mute, rescheduled from
  `cacheMute` on the main thread, cancelled on quit/pardon; early fire under lag reschedules). The depths `TimingWheel`
  is a depths-module class (main-thread tick slots), so it was not moved into common for this. `end_notice_sent` (V005)
  is claimed atomically by `claimEndNotices` (MUTE when no mute is active; BAN on join); V005 marks everything that
  ended before the update as told. The message is combined with the probation notice (`ended.mute-probation`,
  `ended.ban-probation`). Audit: `mute_end_notice` / `ban_end_notice`.
- **Watchlist (13.5):** table `watchlist` (V005), one active row per player (renewing updates it), removed rows kept.
  Join line to `skyblock.staff.notify` holders except the watched player (`Watchlist.recipients`). `/watch` keeps the
  player optional because of the `remove` sub-command (framework trap).
- **Audit actions:** approval_request, approval_merge, approval_approve, approval_deny, approval_skip, suggestion_request,
  watch_add, watch_update, watch_remove, watchlist_view, mute_end_notice, ban_end_notice. Approved punishments carry
  `issuer` + `approved_by` in their own audit details.

## P5 implementation notes (for P6+)
- **Config:** `PunishConfig.Extras` gained `bulk` (`Bulk(max)`) and `appeal` (`Appeals`: enabled, types, cooldown,
  min/max length, reminder, reduce-max-days, deny-reasons, discord enabled/channel). Per reason `appealable:` (default
  true; `alt-abuse` and `rmt` ship with false) → `Templates.Template.appealable`, `Templates.appealable(id)`. Discord kinds
  `bulk`, `appeal` got colours.
- **V006:** `punishment_requests.evidence_entry`, table `appeals` (status OPEN/ACCEPTED/REDUCED/DENIED/CLOSED, issuer and
  handler, response, old/new expires, `player_notified`). Bulk needs no column: `source = bulk:<group>`
  (`BulkSelection.group`), which `/pstats` does not count as "from suggestions" (that is `auto:%`).
- **One pipeline, two new request flags:** `PunishmentService.Request` gained `evidenceEntry` (13.11) and `bulk` (13.8);
  build them with `request.with(evidence, group)`. A bulk request gets no per-player chat line, [Undo] or Discord embed
  (`handle()`), a REQUEST verdict creates the request silently for the requester (approvers still get each one with its
  own buttons). `BulkPunish` previews with `PunishmentService.preview` (same as the /p lore), applies every not-skipped
  player with `punishWithTemplate/WarnButton(..., group)` (the store decides again atomically) and then sends one summary,
  one `bulk_punish` audit entry, one Discord embed (`PunishExtras.bulkApplied`) and [Undo all] (`undoQuiet` per
  punishment, once per group). Planned skips stay skipped (staff confirmed exactly that list).
- **Punish from logs:** `common/audit/LogLookup` (new interface, provided by the logs module: entry by id, only when the
  viewer may see its category). Logs → moderation goes through the command `/punish <name> --evidence <id>` (the same
  entry point the staff tools dispatch), so logs never touches moderation classes. `/p` accepts a flag in the reason slot.
  `LogEvidence.check` (chat/chat_blocked, written by the target), `Anchor` on `PunishMenus.Target.evidence` (kept on
  menu reloads), `PunishExtras.captureFromLog` saves the lines up to that entry, for every type. Requests keep it in
  `evidence_entry`; approve applies with it.
- **Appeals:** `AppealRules` (pure: canAppeal, cooldownUntil (after DENIED or REDUCED), text limits, `/appeal [id] <text>`
  parsing, pick = active mute first, reduce range/end), `Appeal.Status.canMoveTo` (OPEN → final, once). Store methods run
  under the player's lock: `submitAppeal` (one OPEN per punishment), `acceptAppeal` (pardon + ACCEPTED in one
  transaction), `reduceAppeal` (UPDATE expires, REDUCED), `denyAppeal`; a punishment that is no longer active CLOSES the
  appeal. Effects after a decision in `AppealService` (mute cache/timer, vanilla ban list via `resyncVanillaBan`,
  "mute ended" when a reduce ends it now). Player told now (`markAppealNotified`) or on join (`appealUpdates`). Staff
  reminder on join for OPEN appeals older than `appeal.reminder`. `/pstats` line from `appealCounts`.
- **Discord (13.10 step 1):** `PunishExtras.appealPosted` posts to `appeal.discord.channel` only when
  `appeal.discord.enabled` and the webhook secret exist; the module's sink accepts that channel with its own switch.
  Embeds: `PunishEmbeds.appealNew` (ids, type, reason, staff, text, history counts; never IPs), `appealDecision`.
  Step 2 (Discord `/appeal <ban-id>`, verified/unverified, DM) waits for the Discord bot.
- **Audit actions:** bulk_punish, bulk_undo, undo (with `bulk` group), appeal_create, appeal_accepted, appeal_reduced,
  appeal_denied, appeal_closed, appeal_view, appeals_view, evidence_saved (with `log_entry`), logs: logs_punish_open.

## P6 implementation notes (for P7+)
- **Config:** `PunishConfig.Extras` gained `neverAuto` (list of reason ids; only a list written in the file is checked
  against the reasons), `lockdown` (`Lockdown.Settings`: new-player-playtime/age, chat/join default + max (caps 6h / 1h),
  staff-reminder, blocked-commands), `raid` (`Lockdown.Raid`: detect, accounts, window, auto-lock, auto-duration (max 5m),
  auto-cooldown (min 30m)) and `network` (`NetworkScope.Settings`: enabled, server-id, default-scope). The P5 constructor
  is kept (P6 keys get their defaults). New config error keys `config.duration-too-long/-short`. Discord kind `lockdown`.
- **Raid lockdown (13.9):** pure rules in `Lockdown` (isNew, chatBlocked, joinBlocked, blockedCommand, length, auto,
  `State` (volatile, in memory only), `RaidDetector` (sliding window, each account once, one alert per wave)), server side in
  `LockdownService` + `LockdownMenu` (3 rows, plain clicks; `/lockdown` without a sub-command). Gates in
  `ModerationModule`: chat (after the mute check), commands (`blocked-commands`), signs, books; login at **LOWEST**
  (`onPreLoginLockdown`), i.e. before `PlayerService` creates the player row, so "never joined" = no row yet
  (`core.players().offline(uuid)`, only while a join lockdown is live, fails open). Exempt = `skyblock.lockdown.bypass`
  or `skyblock.staff.notify`; a player without loaded data counts as not new. Raid detection counts
  `PlayerData.isNew()` joins (MONITOR). Every start/stop: staff line, audit (`lockdown_start`, `lockdown_end` with
  `how` = manual/expired/restart and `refused` for join lockdowns), Discord embed (`PunishExtras.lockdownPosted`,
  `PunishEmbeds.lockdown`, no player data). `shutdown()` in `disable()` ends everything before the Discord sink closes.
  Placeholder `lockdown` (`<ph:lockdown>`, staff only) for a later HUD line.
- **Anticheat suggestions (13.3):** `/psuggest` calls `ApiSuggestions.suggest(..., append=true, actor)`; the store's
  `suggestApi(..., append)` appends the new details line to the waiting row (`ApiSuggestions.appendDetails`, " | ",
  newest 5 lines, 255 chars). The `SuggestionService` interface (chat filter, 12.8) keeps replacing. Source = staff name,
  empty from the console, so Grim's line reads "Steve (Grim: Reach vl 12)". Nothing applies a suggestion automatically;
  `never-auto` is validated and documented for any future automatic path. Grim: plain Grim config, no API dependency
  (`server/CONFIG_NOTES.md` → GrimAC); thresholds are the owner's call.
- **Network-ready (13.12):** V007 adds `punishments.server_id` + `scope` (no backfill; NULL = network). `PunishmentStore`
  takes a `Supplier<NetworkScope.Settings>` (the module passes the live punishments.yml value); `insertRow` writes server id
  + scope (bans/mutes `default-scope`, warns/kicks `server`), `activeOf` (every "active ban/mute" query: login, chat cache,
  issue pipeline, pardon, alts) adds `NetworkScope.sql` when enabled. Not filtered: history, ladders, warn counter,
  appeals (open question). The per-target lock in `PunishmentStore.locked` is JVM-local: with several servers writing
  to one database a row lock (`SELECT ... FOR UPDATE` on a per-player row) would be needed there; not built (spec: the
  database is the sync, no cross-server code). IDs stay database AUTO_INCREMENT, unique across servers sharing it.
- **Audit actions:** lockdown_start, lockdown_end, raid_detected, raid_ignored; /psuggest uses suggestion_api /
  suggestion_api_refresh with the sender as actor.

## P7 implementation notes (for P8+)
- **Island steps (13.2):** `Punishment.Type` gained `ISLAND_KICK`, `ISLAND_BAN`, `COOP_REMOVE`, `COOP_BLOCK` (`island()`,
  `instant()` = kick/island kick/coop remove: never active, never undoable; `timed()` = ban/mute/island ban/coop block).
  V008 widens `punishments.type` and `punishment_requests.type` to VARCHAR(16). Ladder syntax `island-kick`,
  `island-ban <time|perm>`, `coop-remove`, `coop-block <time|perm>` (`Templates.Step.parse`, `kindId()`); warn-rule actions
  still accept only mute/ban. Permission `skyblock.staff.punish.island` (`permissionFor`). Approval and the rank length
  limit stay ban/mute only, so island steps never become requests.
- **Which step is used:** `IslandSteps` (pure) replaces steps the target can't get with the next possible step of the same
  ladder (same length, so the offence count picks the same index): no islands module → next possible step; owner → island
  kick/ban/coop remove skip to the next coop-block (or next normal step); no team island → coop-block or the next normal
  step; nothing later → nearest earlier step, else a warning. The context (`IslandSteps.Context`: island of the active
  profile, owner flag) is loaded with the target (`PunishMenus.Target.island`, `IslandPunish.context`, main thread).
  `Target.adapt(template)` is used by the menu lore, More reasons, bulk and `punishWithTemplate` (which also tells staff
  "X isn't possible ... Y instead"). Probation raises on the adapted ladder. API suggestions (12.8, /psuggest, dupes) adapt
  with `Context.UNAVAILABLE`, so a suggestion never carries an island step.
- **Enforcement:** `IslandPunish.apply` after the punishment is stored (`PunishmentService.apply`; the island is resolved
  again at that moment): island kick → `IslandService.expelFromIsland`, island ban → `banFromIsland(..., reference
  "punishment:<id>")`, coop remove → `removeMember`, coop block → `blockJoining` with max(existing, new). Undo lifts island
  bans (`liftIslandBan(reference)`) and coop blocks (the longest other active coop block stays). Failures tell staff
  (`island-steps.apply-failed`) and audit `island_punish_failed`; the history row stays.
- **Islands side:** `island_staff_bans` (islands V011, never deleted, `lifted_at`), cached in `VisitManager.staffBans`;
  `VisitRules.access(..., staffBanned)` returns BANNED before the member check (only the staff bypass ignores it), so every
  existing guard (teleport, move, join, visit) enforces it. `IslandService.supportsModeration()` gates the hooks. A coop
  block also refuses `/is create` (`create.blocked`).
- **Sanctions (13.1):** table `sanctions` (kind FREEZE/TRADE/MONEY/ITEM, state ACTIVE/PENDING/LIFTED/EXPIRED/RESTORED/FAILED,
  `profile_key` for money/items) + `confiscated_items` (serialized stack, source, slot). `Sanctions` (service + actions),
  `SanctionStore` (conditional UPDATEs: first click wins), `SanctionRules` (pure), `SanctionMenus` (Sanctions button slot 45,
  length/reason choice menus, confiscate view). `common/moderation/SanctionService` (published by moderation):
  `block(player|profileKey, Use)`, `blocks(Transaction)`, `denied(...)` / `deniedOther(...)` one-line hooks.
  `common/moderation/ListingSanctions` (published by auction) cancels every listing of every profile on a trade block.
- **Hooks (value paths):** economy `/pay` (both sides), `/withdraw`, note redeem, bank deposit/withdraw (personal and
  island), shop buy, auction list (command, sell menu, confirm) and buy, trade request/accept (both sides). Safety net:
  `EconomyServiceImpl.execute/executeGuarded/executeBatch` refuse (INVALID, audit `sanction_blocked`, guards never run)
  money of `freeze-currencies` leaving a frozen player's player/bank account (except `freeze-exempt-reasons`), a frozen
  player receiving a `freeze-receive-reasons` transfer (island bank withdraw), and `trade-reasons` touching a trade-blocked
  player on either side or as actor. Island accounts belong to nobody here.
- **Atomicity:** remove money = `executeGuarded(withdraw, guard inserts the MONEY row)` (one transaction; INSUFFICIENT_FUNDS
  retries once with the new balance; never more than the balance). Restore money = `executeGuarded(deposit with idempotency
  key sanction_restore:<id>, guard claims ACTIVE → RESTORED)`. Confiscate = PENDING row + item written first, then the slot
  is checked (`ItemStack.equals`) and emptied on the main thread, then ACTIVE (or FAILED when it moved); PENDING is never
  restorable, so a crash can't duplicate (an orphaned PENDING row shows as "pending (check by hand)"). Restore item = claim
  ACTIVE → RESTORED, hand over on the main thread, or back to ACTIVE if the player left/switched profile.
- **Expiry:** blocks live in memory (`Sanctions.active`, loaded at start); a 1 s main-thread timer marks run-out blocks
  EXPIRED once (`sanction_expired`) without polling the database; the checks ignore run-out blocks anyway.
- **Approval:** `Approval.Rule` accepts `sanction freeze|trade|money|confiscate|*`; a matching kind needs
  `skyblock.staff.punish.approve` and is refused for others (no queue; open question).
- **Stats/history:** `/pstats` line `stats.sanctions` (island punishments from `PunishStats.Counts.island`, also in
  "given"; sanctions from `SanctionStore.givenBy`). `/history` (chat) lists sanctions under the punishments.
- **Dupe link:** `core.dupes()` handler (hooked once, gated by `enabled`) → `ApiSuggestions.suggest(..., dupe-suggestion.reason,
  "Dupe detector", "<item> found N times (also freeze balance ...)")`, never applied.
- **Audit actions:** sanction_freeze, sanction_trade, sanction_money, sanction_confiscate, sanction_confiscate_failed,
  sanction_lift, sanction_expired, sanction_restore, sanction_listings_cancelled; economy: sanction_blocked; island:
  island_kick, island_ban, coop_remove, coop_block (punishments), island_kick_applied, island_ban_applied,
  coop_remove_applied, coop_block_applied, island_punish_failed; islands module: island_staff_ban, island_staff_unban,
  island_staff_kick.
- **Not built / later:** sanction requests queue, Discord embeds for sanctions, sanctions in the `/history` menu (chat
  only; the Sanctions menu lists the last 14), a pardon command for island punishments.

