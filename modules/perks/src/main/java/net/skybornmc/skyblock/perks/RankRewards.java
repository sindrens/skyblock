package net.skybornmc.skyblock.perks;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Ranks V2 rank rewards ({@code /rankrewards}, RANKS-V2 E2): a weekly kit per profile (Monday 00:00 Oslo, size from the
 * highest {@code skyblock.rank.kit.size.<n>}, bound to the profile, inventory then {@code /box}) and a monthly tag per
 * account (first id of {@code rank-rewards.monthly.pool} the player does not own). The claim row goes in first (a second
 * claim in the period fails on the primary key); a kit row left in {@code claiming} by a crash is delivered once to the
 * profile's box at the next join; a monthly row whose grant fails is deleted again.
 */
final class RankRewards implements Listener {

    static final String PERM_KIT = "skyblock.rank.kit";
    static final String PERM_MONTHLY = "skyblock.rank.monthly";
    static final String CLAIMING = "claiming";
    static final String DELIVERED = "delivered";

    private final Core core;
    private final PerksConfig config;
    private final ModuleMessages msg;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    RankRewards(Core core, PerksConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
    }

    void register() {
        core.commands().register(CommandSpec.command("rankrewards")
                .description("Your rank's weekly kit and monthly tag").category("Perks")
                .permission(PERM_KIT).playerOnly().example("rankrewards")
                .executes(ctx -> open(ctx.player())));
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
    }

    void open(Player player) {
        if (!config.rankRewardsEnabled) {
            msg.send(player, "rank-rewards.disabled");
            return;
        }
        status(player).thenAccept(s -> core.menus().open(player, new RankRewardsMenu(this, s)));
    }

    // ------------------------------------------------------------------ state

    /** What the menu shows. */
    record Status(String size, boolean kitClaimed, boolean monthlyClaimed, String nextCosmetic, String month) {
    }

    UUID profile(Player p) {
        return ProfileService.of(core.services()).profileKey(p);
    }

    String week() {
        return RankRewardsRules.weekKey(Instant.now(), Format.zone());
    }

    String month() {
        return RankRewardsRules.monthKey(Instant.now(), Format.zone());
    }

    String kitSize(Player p) {
        return RankRewardsRules.size(n -> p.hasPermission("skyblock.rank.kit.size." + n));
    }

    String nextCosmetic(Player p) {
        Optional<Cosmetics> c = core.services().optional(Cosmetics.class);
        if (c.isEmpty()) {
            return null;
        }
        List<String> owned = new ArrayList<>();
        for (String id : config.rankMonthlyPool) {
            String k = id == null ? "" : id.strip().toLowerCase(java.util.Locale.ROOT);
            if (c.get().owns(p.getUniqueId(), Cosmetics.Kind.TAG, k)) {
                owned.add(k);
            }
        }
        return RankRewardsRules.nextCosmetic(config.rankMonthlyPool, owned);
    }

    /** Completes on the main thread. */
    CompletableFuture<Status> status(Player p) {
        UUID profile = profile(p);
        UUID account = p.getUniqueId();
        String week = week();
        String month = month();
        return core.database().async(c -> new boolean[]{
                Sql.queryOne(c, "SELECT 1 FROM perks_rank_kit WHERE profile_key = ? AND week_key = ?", rs -> true, profile, week).isPresent(),
                Sql.queryOne(c, "SELECT 1 FROM perks_rank_monthly WHERE player_uuid = ? AND month_key = ?", rs -> true, account, month).isPresent()})
                .thenApplyAsync(r -> new Status(kitSize(p), r[0], r[1], nextCosmetic(p), month), new MainThread(core.plugin()));
    }

    // ------------------------------------------------------------------ weekly kit

    List<ItemStack> kitItems(String size) {
        List<String> raw = switch (size) {
            case "S" -> config.rankKitS;
            case "M" -> config.rankKitM;
            case "L" -> config.rankKitL;
            default -> config.rankKitXL;
        };
        List<ItemStack> out = new ArrayList<>();
        for (RankRewardsRules.Line line : RankRewardsRules.parse(raw, id -> {
            Material m = Material.matchMaterial(id);
            return m != null && m.isItem() && !m.isAir();
        }, problem -> core.plugin().getLogger().warning("perks.yml rank-rewards.kits." + size + ": " + problem))) {
            Material m = Material.matchMaterial(line.item());
            int left = line.amount();
            while (left > 0) {
                int n = Math.min(left, m.getMaxStackSize());
                out.add(new ItemStack(m, n));
                left -= n;
            }
        }
        return out;
    }

    void claimKit(Player p, Runnable refresh) {
        String size = kitSize(p);
        if (!config.rankWeeklyEnabled || size == null) {
            msg.send(p, "rank-rewards.kit-none");
            return;
        }
        if (core.services().optional(ItemBox.class).map(b -> b.refuses(p)).orElse(false)) {
            msg.send(p, "rank-rewards.kit-box-full");
            return;
        }
        UUID id = p.getUniqueId();
        if (!busy.add(id)) {
            return;
        }
        UUID profile = profile(p);
        String week = week();
        long now = System.currentTimeMillis();
        core.database().async(c -> insertKit(c, profile, week, id, size, now)).whenCompleteAsync((inserted, error) -> {
            busy.remove(id);
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Rank kit: claim failed for " + p.getName(), Database.cause(error));
                msg.send(p, "rank-rewards.failed");
                return;
            }
            if (!Boolean.TRUE.equals(inserted)) {
                msg.send(p, "rank-rewards.kit-already");
                return;
            }
            List<ItemStack> items = kitItems(size);
            BoundItems.markAll(items, profile);
            ItemBox.give(core.services(), p, BoxSource.PERKS, items);
            core.database().async(c -> Sql.update(c, "UPDATE perks_rank_kit SET state = ?, updated_at = ? WHERE profile_key = ? AND week_key = ?",
                    DELIVERED, System.currentTimeMillis(), profile, week));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("profile", profile.toString());
            d.put("week", week);
            d.put("size", size);
            core.audit().log(AuditCategory.ITEM, "rank_kit", Actor.player(p), d);
            msg.send(p, "rank-rewards.kit-claimed", Msg.p("size", size));
            core.sounds().play(p, "success");
            refresh.run();
        }, new MainThread(core.plugin()));
    }

    static boolean insertKit(Connection c, UUID profile, String week, UUID player, String size, long now) throws SQLException {
        try {
            Sql.update(c, "INSERT INTO perks_rank_kit (profile_key, week_key, player_uuid, kit_size, state, claimed_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", profile, week, player, size, CLAIMING, now, now);
            return true;
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                return false;
            }
            throw e;
        }
    }

    /** A crash between the claim row and the hand-out: the kit goes to the profile's box once, at the next join. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        core.scheduler().later("rank-kit-recover", 60L, () -> {
            if (!p.isOnline()) {
                return;
            }
            UUID profile = profile(p);
            core.database().async(c -> Sql.query(c, "SELECT week_key, kit_size FROM perks_rank_kit WHERE profile_key = ? AND state = ?",
                    rs -> new String[]{rs.getString(1), rs.getString(2)}, profile, CLAIMING)).thenAccept(rows -> {
                for (String[] row : rows) {
                    core.database().async(c -> Sql.update(c, "UPDATE perks_rank_kit SET state = ?, updated_at = ? WHERE profile_key = ? "
                            + "AND week_key = ? AND state = ?", DELIVERED, System.currentTimeMillis(), profile, row[0], CLAIMING))
                            .thenAccept(n -> {
                                if (n == 1) {
                                    core.scheduler().main("rank-kit-redeliver", () -> {
                                        List<ItemStack> items = kitItems(row[1]);
                                        BoundItems.markAll(items, profile);
                                        ItemBox.of(core.services()).ifPresent(b -> b.deliver(profile, items, BoxSource.PERKS,
                                                Actor.system("rank_kit")));
                                        core.audit().log(AuditCategory.ITEM, "rank_kit_redeliver", Actor.system("rank_kit"),
                                                Map.<String, Object>of("profile", profile.toString(), "week", row[0], "size", row[1]));
                                    });
                                }
                            });
                }
            });
        });
    }

    // ------------------------------------------------------------------ monthly tag

    void claimMonthly(Player p, Runnable refresh) {
        Optional<Cosmetics> cosmetics = core.services().optional(Cosmetics.class);
        String tag = nextCosmetic(p);
        if (!config.rankMonthlyEnabled || !p.hasPermission(PERM_MONTHLY) || cosmetics.isEmpty() || tag == null) {
            msg.send(p, "rank-rewards.monthly-nothing");
            return;
        }
        UUID id = p.getUniqueId();
        if (!busy.add(id)) {
            return;
        }
        String month = month();
        long now = System.currentTimeMillis();
        core.database().async(c -> {
            try {
                Sql.update(c, "INSERT INTO perks_rank_monthly (player_uuid, month_key, cosmetic_id, claimed_at) VALUES (?, ?, ?, ?)",
                        id, month, tag, now);
                return true;
            } catch (SQLException e) {
                if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                    return false;
                }
                throw e;
            }
        }).thenCompose(inserted -> !Boolean.TRUE.equals(inserted) ? CompletableFuture.completedFuture((Boolean) null)
                : cosmetics.get().grantTag(id, tag, "rank_monthly").exceptionally(e -> false)
                .thenCompose(ok -> Boolean.TRUE.equals(ok) ? CompletableFuture.completedFuture(true)
                        : core.database().async(c -> Sql.update(c, "DELETE FROM perks_rank_monthly WHERE player_uuid = ? AND month_key = ?",
                        id, month)).thenApply(n -> false)))
                .whenCompleteAsync((result, error) -> {
                    busy.remove(id);
                    if (error != null || Boolean.FALSE.equals(result)) {
                        msg.send(p, "rank-rewards.failed");
                        return;
                    }
                    if (result == null) {
                        msg.send(p, "rank-rewards.monthly-already");
                        return;
                    }
                    msg.send(p, "rank-rewards.monthly-claimed", Msg.p("month", month),
                            Msg.c("tag", cosmetics.get().display(p, Cosmetics.Kind.TAG, tag)));
                    core.sounds().play(p, "success");
                    refresh.run();
                }, new MainThread(core.plugin()));
    }
}
