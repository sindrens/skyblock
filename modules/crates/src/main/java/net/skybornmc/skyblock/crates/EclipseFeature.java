package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.CreditSpend;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Eclipse Crate around its tiered pool (Complete Developer Spec, owner M1-M5, F6, V5): month tables
 * ({@code crates.yml crates.eclipse.months.<yyyy-MM>}), the "Next month: ???" teaser from the 25th, a staff warning before
 * a month without a table, and buying a key with credits (tradeable, no limit).
 */
final class EclipseFeature implements Listener {

    private final CratesModule m;
    private final Set<String> teased = ConcurrentHashMap.newKeySet();
    private final Set<UUID> buying = ConcurrentHashMap.newKeySet();

    EclipseFeature(CratesModule m) {
        this.m = m;
    }

    // ------------------------------------------------------------------ pure rules

    /** True while the teaser shows: from {@code teaserDay} to the end of the month (0 = never). */
    static boolean teaser(LocalDate today, int teaserDay) {
        return teaserDay > 0 && today.getDayOfMonth() >= Math.min(teaserDay, today.lengthOfMonth());
    }

    /** True when staff must hear that next month has no table (inside the warning window, crate uses month tables). */
    static boolean warnStaff(LocalDate today, int warnDays, boolean monthlyCrate, boolean nextHasTable) {
        if (!monthlyCrate || nextHasTable || warnDays <= 0) {
            return false;
        }
        int left = today.lengthOfMonth() - today.getDayOfMonth() + 1;
        return left <= warnDays;
    }

    // ------------------------------------------------------------------ join lines

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(m.core.plugin(), () -> {
            if (!p.isOnline()) {
                return;
            }
            LocalDate today = LocalDate.now(CrateCatalog.clock);
            String crate = m.settings.eclipseCrate;
            if (m.catalog().crate(crate).isEmpty()) {
                return;
            }
            String key = p.getUniqueId() + "|" + YearMonth.now(CrateCatalog.clock);
            if (teaser(today, m.settings.eclipseTeaserDay) && teased.add(key)) {
                m.msg.send(p, "eclipse.teaser", Msg.p("month", YearMonth.now(CrateCatalog.clock).plusMonths(1).getMonth()
                        .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)));
            }
            if (p.hasPermission(CratesAdmin.PERM_ADMIN) && warnStaff(today, m.settings.eclipseWarnDays,
                    m.catalog().monthlyCrate(crate), m.catalog().hasMonth(crate, CrateCatalog.nextMonth()))) {
                m.msg.send(p, "eclipse.staff-missing", Msg.p("month", CrateCatalog.nextMonth()));
            }
        }, 80L);
    }

    // ------------------------------------------------------------------ buy with credits

    boolean buyable(String crateId) {
        return m.settings.eclipseBuyEnabled && m.settings.eclipseCrate.equals(crateId)
                && m.core.services().optional(EconomyService.class).isPresent();
    }

    /** One tradeable key for credits, in one transaction (credits taken, key added) or not at all. */
    void buy(Player p, Runnable after) {
        EconomyService economy = m.core.services().optional(EconomyService.class).orElse(null);
        String crate = m.settings.eclipseCrate;
        if (economy == null || !buyable(crate) || !buying.add(p.getUniqueId())) {
            return;
        }
        UUID profile = m.profiles().profileKey(p);
        long price = m.settings.eclipseBuyPrice;
        UUID batch = UUID.randomUUID();
        Transaction tx = Transaction.withdraw(AccountRef.player(p.getUniqueId()), Currency.CREDITS, price, TxReason.CRATE_KEY_PURCHASE)
                .actor(Actor.player(p)).reference("crate_buy:" + batch).idempotencyKey("crate_buy:" + batch)
                .detail("crate", crate).detail("profile", profile.toString());
        long now = System.currentTimeMillis();
        CreditSpend.execute(economy, tx, c -> {
            CrateStore.addKeys(c, profile, crate, 0, 1, now);
            return true;
        }).whenComplete((result, error) -> Bukkit.getScheduler().runTask(m.core.plugin(), () -> {
            buying.remove(p.getUniqueId());
            if (error != null || result == null || !result.ok()) {
                boolean poor = result != null && result.status() == net.skybornmc.skyblock.common.economy.TxResult.Status.INSUFFICIENT_FUNDS;
                m.msg.send(p, poor ? "eclipse.buy-poor" : "eclipse.buy-failed", Msg.number("price", price));
                m.core.sounds().play(p, "error");
            } else {
                m.audit("eclipse_buy", Actor.player(p), p.getUniqueId(), p.getName(), price,
                        Map.of("crate", crate, "profile", profile.toString(), "batch", batch.toString()));
                m.msg.send(p, "eclipse.bought", Msg.number("price", price));
                m.core.sounds().play(p, "money");
                m.changed(profile);
            }
            if (after != null) {
                after.run();
            }
        }));
    }
}
