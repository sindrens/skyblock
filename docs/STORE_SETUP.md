# Tebex store setup (owner steps)

Build spec CREDIT-SHOP section 13. The server side (`/credit grant`, `/credit chargeback`) is built; everything below is done once by the owner in the Tebex panel and on the server. Command reference: [commands/economy.md](commands/economy.md).

1. **Webstore.** In the Tebex control panel create the store (domain `store.skybornmc.net`, currency **USD**, game server type *Minecraft: Java Edition*). Read Mojang's current commercial usage guidelines before publishing (C1 blocker). The store page says it is not affiliated with Mojang.
2. **Four packages** (category "Credits"), named like the in-game packs: `500 Credits` $5, `1,000 Credits` $10, `2,700 Credits` $25 (2,500 + 200 bonus), `14,000 Credits` $130. The amounts live in `economy.yml credit-store.packages` (`c500=500|5` ...); the command only names the package id.
3. **Commands per package** (Packages → edit → Commands). Initial command, run even if the player is offline, delay 0, no leading slash:
   - `credit grant {id} c500 {transaction}`
   - `credit grant {id} c1000 {transaction}`
   - `credit grant {id} c2700 {transaction}`
   - `credit grant {id} c14000 {transaction}`

   **Chargeback** and **Refund** command on every package: `credit chargeback {id} {transaction}`.
   `{id}` is the UUID for Java players and the XUID for Bedrock players ("game-specific primary ID"); a player name also works.
4. **Tebex plugin.** Put the Tebex plugin jar (Paper) in `plugins/`, restart, then run once in the console `tebex secret <SECRET>` (Tebex panel → Game Servers → your server → Secret key) and check with `tebex info`. The key stays in `plugins/Tebex/` on the VPS, never in git. **Unverified:** whether the Tebex plugin loads on Paper 26.3. If it does not, tell the lead (fallback: Tebex's headless/command API, a separate job).
5. **Bedrock.** Enable the Bedrock username prefix in Tebex so `{id}` arrives as the XUID.
6. **Test with "Manual payment"** (never real money), on a test account:
   - create a manual payment for package `c500`, wait 1 to 3 minutes;
   - the console prints `[Store] <player> +500 credits (c500, tx ...)` and `/credit bal` shows 500 bought;
   - run `credit grant <player> c500 <same tx>` in the console again: "Already granted ... Nothing changed.";
   - optional: `credit chargeback <player> <tx>` takes the 500 back (ledger rows stay as the audit trail).
7. **Alerts.** A failed grant (unknown package or player, a package above `credit-store.max-grant`, a database error) alerts online staff with `skyblock.staff.economy.anomalies`, the console and the Discord `alerts` channel (when its webhook is set). A successful grant is silent. Store grants never raise economy-watch earning alerts (`economy-watch.yml earnings.ignore-reasons` has `store_grant` and `store_chargeback`).
