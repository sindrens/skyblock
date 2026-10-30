package net.skybornmc.skyblock.economy.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The biggest single transactions of the last 24 hours (M3.8), optionally of some reasons only (the reasons drill-down).
 * Read from the ledger with the (currency, time, amount) index, one window, biggest first. A click opens the full history
 * of the player in it.
 */
final class TransactionListMenu extends WatchMenu {

    static final long WINDOW_MS = 24 * 3_600_000L;

    private final Currency currency;
    private final List<String> reasons;
    private final Component label;
    private List<LedgerRow> rows;
    private Map<UUID, String> names = Map.of();

    TransactionListMenu(EconomyWatch watch, Currency currency, List<String> reasons, Component label) {
        super(watch);
        this.currency = currency;
        this.reasons = List.copyOf(reasons);
        this.label = label;
    }

    @Override
    public Component title() {
        Component base = service.text(viewer, EconomyModule.ID, "watch.menu.title");
        Component crumb = label != null ? label : service.text(viewer, EconomyModule.ID, "watch.tx.title");
        return service.title(viewer, List.of(base, crumb), null);
    }

    @Override
    public String infoKey() {
        return "watch.tx.info";
    }

    @Override
    protected void build() {
        frame();
        if (rows == null) {
            showLoading();
            if (!failed) {
                long now = System.currentTimeMillis();
                int limit = Math.min(PagedMenu.CONTENT_SLOTS.length, watch.config.bigSize);
                load(watch.store().async(c -> WatchStore.biggest(c, currency, now - WINDOW_MS, now + 1, reasons, limit))
                        .thenCompose(list -> watch.names(EconomyWatch.accountKeys(list)).thenApply(n -> {
                            names = n;
                            return list;
                        })), list -> rows = list);
            }
            return;
        }
        if (rows.isEmpty()) {
            set(PagedMenu.SLOT_EMPTY, service.item(Material.COBWEB, viewer, EconomyModule.ID, "watch.tx.empty"));
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.size() && i < PagedMenu.CONTENT_SLOTS.length; i++) {
            LedgerRow row = rows.get(i);
            UUID player = mainPlayer(row);
            set(PagedMenu.CONTENT_SLOTS[i], entry(watch, viewer, row, names, now, player != null ? names.get(player) : null),
                    player == null ? null : click -> watch.history(this, player));
        }
    }

    /** The player account of a row to open the history of: the receiver if a player, else the payer. */
    static UUID mainPlayer(LedgerRow row) {
        if (row.to() != null && row.to().isPlayer()) {
            return row.to().owner();
        }
        if (row.from() != null && row.from().isPlayer()) {
            return row.from().owner();
        }
        return null;
    }

    /** One ledger row as an item (shared with the alert evidence). */
    static ItemStack entry(EconomyWatch watch, Player viewer, LedgerRow row, Map<UUID, String> names, long now, String clickName) {
        var service = watch.core.menus();
        TagResolver[] tags = {watch.amount("amount", viewer, row.currency(), row.amount()),
                Msg.c("reason", watch.reasonLabel(viewer, row.reason(), false)),
                Msg.p("from", watch.accountLabel(row.from(), names)),
                Msg.p("to", watch.accountLabel(row.to(), names)),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(0, now - row.time())))),
                Msg.p("date", EconomyWatch.date(row.time())),
                Msg.p("id", row.id()),
                Msg.p("player", clickName == null ? "" : clickName)};
        List<Component> lore = new ArrayList<>(service.lines(viewer, EconomyModule.ID, "watch.tx.entry.lore", tags));
        if (clickName != null) {
            lore.addAll(service.lines(viewer, EconomyModule.ID, "watch.tx.click", tags));
        }
        Material icon = switch (row.flow()) {
            case CREATED -> Material.LIME_DYE;
            case REMOVED -> Material.RED_DYE;
            case MOVED -> Material.PAPER;
        };
        return service.item(icon, service.text(viewer, EconomyModule.ID, "watch.tx.entry.name", tags), lore);
    }
}
