package net.skybornmc.skyblock.blackmarket;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Staff sub-commands {@code /blackmarket admin reroll|give|view} (owner approved P10-41: admins edit, mods view). Work
 * from the console, full tab completion, every change audited. A give hands the offer's reward to the player's active
 * profile for free (a buy row with price 0, delivered like a buy) and never counts toward limits.
 */
final class BlackMarketAdmin {

    static final String PERM_ADMIN = "skyblock.blackmarket.admin";
    static final String PERM_VIEW = "skyblock.blackmarket.view";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ROOT);

    private final BlackMarketModule m;

    BlackMarketAdmin(BlackMarketModule module) {
        this.m = module;
    }

    CommandSpec spec() {
        Arg<String> player = Args.offlinePlayer("player");
        Arg<OfferCatalog.Offer> offer = Arg.word("offer", (sender, raw) -> m.catalog().get(raw)
                .filter(o -> o.kind() != OfferCatalog.Kind.COMING_SOON)
                .orElseThrow(() -> new CommandFailure(BlackMarketModule.ID, "admin.unknown-offer", Msg.p("input", raw),
                        Msg.p("offers", String.join(", ", m.catalog().liveIds())))), Suggest.of(() -> m.catalog().liveIds()));
        Arg<String> player2 = Args.offlinePlayer("player");
        return CommandSpec.sub("admin")
                .description("Staff tools for the Black Market: reroll the stock, give an offer, view a player's buys")
                .category("Staff")
                .permission(PERM_VIEW)
                .example("blackmarket admin view Steve")
                .sub(CommandSpec.sub("reroll")
                        .description("Pick new offers for the current rotation now (announced)")
                        .permission(PERM_ADMIN)
                        .confirm(15)
                        .example("blackmarket admin reroll")
                        .executes(ctx -> reroll(ctx.sender())))
                .sub(CommandSpec.sub("give")
                        .description("Give a Black Market offer to a player's active profile for free (no limits counted)")
                        .permission(PERM_ADMIN)
                        .arg(player).arg(offer)
                        .example("blackmarket admin give Steve elytra")
                        .executes(ctx -> give(ctx.sender(), ctx.get(player), ctx.get(offer))))
                .sub(CommandSpec.sub("view")
                        .description("A player's last Black Market buys and their hand-out state")
                        .permission(PERM_VIEW)
                        .arg(player2)
                        .example("blackmarket admin view Steve")
                        .executes(ctx -> view(ctx.sender(), ctx.get(player2))));
    }

    private void main(Runnable r) {
        m.main().execute(r);
    }

    private void reroll(CommandSender sender) {
        m.market().reroll(Actor.of(sender)).whenComplete((rows, error) -> main(() -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Black Market: reroll failed", Database.cause(error));
                m.fail(sender, "admin.failed");
                return;
            }
            m.msg.send(sender, "admin.rerolled", Msg.number("count", rows.size()),
                    Msg.p("offers", String.join(", ", rows.stream().map(BlackMarketStore.RotationRow::offerId).toList())));
        }));
    }

    /** Looks up a player (online first) and their active profile key. */
    private CompletableFuture<Optional<Map.Entry<UUID, UUID>>> target(String name) {
        Player online = Bukkit.getPlayerExact(name);
        CompletableFuture<Optional<UUID>> who = online != null ? CompletableFuture.completedFuture(Optional.of(online.getUniqueId()))
                : m.core.players().offline(name).thenApply(found -> found.map(s -> s.uuid()));
        return who.thenCompose(found -> found.isEmpty() ? CompletableFuture.completedFuture(Optional.<Map.Entry<UUID, UUID>>empty())
                : m.profiles().profileKeyAsync(found.get()).thenApply(profile -> Optional.of(Map.entry(found.get(), profile))));
    }

    private void give(CommandSender sender, String name, OfferCatalog.Offer offer) {
        if (!m.live(offer)) {
            m.fail(sender, "buy.unavailable");
            return;
        }
        String ref = Purchases.chooseRef(m, offer);
        if (ref == null) {
            m.fail(sender, "buy.unavailable");
            return;
        }
        Actor actor = Actor.of(sender);
        target(name).thenCompose(t -> {
            if (t.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<BlackMarketStore.BuyRow>empty());
            }
            long now = System.currentTimeMillis();
            BlackMarketStore.BuyRow row = new BlackMarketStore.BuyRow(UUID.randomUUID(), t.get().getKey(), t.get().getValue(),
                    m.rotation().id(now), offer.id(), offer.kind().key(), ref, offer.amount(), BlackMarketStore.STAFF, 0, null,
                    BlackMarketStore.PENDING, now);
            return m.core.database().transaction(c -> {
                BlackMarketStore.insertBuy(c, row);
                return Optional.of(row);
            });
        }).whenComplete((row, error) -> main(() -> {
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Black Market: staff give to " + name + " failed", Database.cause(error));
                m.fail(sender, "admin.failed");
                return;
            }
            if (row.isEmpty()) {
                m.fail(sender, "admin.unknown-player", Msg.p("player", name));
                return;
            }
            Map<String, Object> d = new LinkedHashMap<>(Deliveries.details(row.get()));
            m.audit("staff_give", actor, row.get().account(), name, 0L, d);
            m.msg.send(sender, "admin.given", Msg.c("offer", m.offerName(sender, offer)),
                    Msg.p("player", name));
            m.deliveries().handOut(row.get());
        }));
    }

    private void view(CommandSender sender, String name) {
        int lines = m.settings.historyLines;
        target(name).thenCompose(t -> t.isEmpty() ? CompletableFuture.completedFuture(Optional.<java.util.List<BlackMarketStore.BuyRow>>empty())
                : m.core.database().async(c -> Optional.of(BlackMarketStore.history(c, t.get().getKey(), lines))))
                .whenComplete((rows, error) -> main(() -> {
                    if (error != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Black Market: looking up " + name + " failed", Database.cause(error));
                        m.fail(sender, "admin.failed");
                        return;
                    }
                    if (rows.isEmpty()) {
                        m.fail(sender, "admin.unknown-player", Msg.p("player", name));
                        return;
                    }
                    m.audit("view", Actor.of(sender), null, name, null, Map.of("player", name));
                    if (rows.get().isEmpty()) {
                        m.msg.send(sender, "admin.view-none", Msg.p("player", name));
                        return;
                    }
                    m.msg.send(sender, "admin.view-header", Msg.p("player", name), Msg.number("count", rows.get().size()));
                    for (BlackMarketStore.BuyRow r : rows.get()) {
                        String when = TIME.format(Instant.ofEpochMilli(r.boughtAt()).atZone(m.rotation().zone()));
                        String price = BlackMarketStore.STAFF.equals(r.currency()) ? "staff"
                                : net.skybornmc.skyblock.common.economy.Currency.byKey(r.currency()).map(c -> m.price(c, r.price()))
                                .orElse(String.valueOf(r.price()));
                        m.msg.send(sender, "admin.view-line", Msg.p("time", when), Msg.p("offer", r.offerId()),
                                Msg.p("reward", r.kind() + ":" + r.ref()), Msg.number("amount", r.amount()), Msg.p("price", price),
                                Msg.p("state", r.state()));
                    }
                }));
    }
}
