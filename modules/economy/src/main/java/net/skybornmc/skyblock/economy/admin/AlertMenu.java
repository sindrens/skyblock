package net.skybornmc.skyblock.economy.admin;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One alert's evidence (M3.8, {@code /eco admin <id>} and the [Open] button of the chat alert). Row 2: full history, Open
 * /p, Suggest punishment, the summary, Mark reviewed, False alarm, Open again. Rows 3-5: the ledger rows behind it (the
 * profile's biggest incoming rows of that hour for earn-rate, the biggest sales of that hour for a sell spike, else the
 * rows stored with the alert). Nothing here changes a balance or punishes.
 */
final class AlertMenu extends WatchMenu {

    static final int SLOT_HISTORY = 10;
    static final int SLOT_PUNISH = 11;
    static final int SLOT_SUGGEST = 12;
    static final int SLOT_SUMMARY = 13;
    static final int SLOT_REVIEW = 14;
    static final int SLOT_DISMISS = 15;
    static final int SLOT_REOPEN = 16;
    static final int[] EVIDENCE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private WatchAlert alert;
    private List<LedgerRow> rows;
    private Map<UUID, String> names = Map.of();

    AlertMenu(EconomyWatch watch, WatchAlert alert) {
        super(watch);
        this.alert = alert;
    }

    @Override
    public Component title() {
        return titleWith("watch.alert-menu.title", Msg.p("id", alert.id()));
    }

    @Override
    public String infoKey() {
        return "watch.alert-menu.info";
    }

    @Override
    protected void build() {
        frame();
        long now = System.currentTimeMillis();
        set(SLOT_SUMMARY, service.item(AlertsMenu.icon(alert), viewer, EconomyModule.ID, "watch.alert-menu.summary",
                Msg.c("severity", watch.severity(viewer, alert)), Msg.c("rule", watch.ruleName(viewer, alert)),
                Msg.p("id", alert.id()), Msg.c("details", watch.details(viewer, alert, names)),
                Msg.c("state", service.text(viewer, EconomyModule.ID, "watch.state." + alert.state().key())),
                Msg.p("by", alert.reviewedBy() == null ? "-" : alert.reviewedBy()),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(0, now - alert.createdAt())))),
                Msg.p("date", EconomyWatch.date(alert.createdAt()))));
        String player = watch.name(alert.subject(), names);
        if (alert.subject() != null) {
            set(SLOT_HISTORY, service.item(Material.WRITABLE_BOOK, viewer, EconomyModule.ID, "watch.alert-menu.history",
                    Msg.p("player", player)), click -> watch.history(this, alert.subject()));
            set(SLOT_PUNISH, service.item(Material.BREEZE_ROD, viewer, EconomyModule.ID, "watch.alert-menu.punish",
                    Msg.p("player", player)), click -> watch.punish(viewer, alert, names));
            set(SLOT_SUGGEST, service.item(Material.PAPER, viewer, EconomyModule.ID, "watch.alert-menu.suggest",
                    Msg.p("reason", watch.config.suggestReason)), click -> {
                click();
                watch.suggest(viewer, alert, names);
            });
        }
        if (alert.state() != WatchAlert.State.REVIEWED) {
            set(SLOT_REVIEW, service.item(Material.LIME_DYE, viewer, EconomyModule.ID, "watch.alert-menu.review"),
                    click -> change(WatchAlert.State.REVIEWED));
        }
        if (alert.state() != WatchAlert.State.DISMISSED) {
            set(SLOT_DISMISS, service.item(Material.GRAY_DYE, viewer, EconomyModule.ID, "watch.alert-menu.dismiss"),
                    click -> change(WatchAlert.State.DISMISSED));
        }
        if (alert.state() != WatchAlert.State.OPEN) {
            set(SLOT_REOPEN, service.item(Material.ORANGE_DYE, viewer, EconomyModule.ID, "watch.alert-menu.reopen"),
                    click -> change(WatchAlert.State.OPEN));
        }
        if (rows == null) {
            set(EVIDENCE_SLOTS[3], failed ? service.item(Material.RED_STAINED_GLASS, viewer, "error-item")
                    : service.item(Material.CLOCK, viewer, "loading"));
            if (!failed) {
                load(evidence(), list -> rows = list);
            }
            return;
        }
        if (rows.isEmpty()) {
            set(EVIDENCE_SLOTS[3], service.item(Material.COBWEB, viewer, EconomyModule.ID, "watch.alert-menu.no-evidence"));
        }
        for (int i = 0; i < rows.size() && i < EVIDENCE_SLOTS.length; i++) {
            LedgerRow row = rows.get(i);
            UUID target = TransactionListMenu.mainPlayer(row);
            set(EVIDENCE_SLOTS[i], TransactionListMenu.entry(watch, viewer, row, names, now, target == null ? null : names.get(target)),
                    target == null ? null : click -> watch.history(this, target));
        }
    }

    private void change(WatchAlert.State state) {
        click();
        watch.setState(viewer, alert, state, () -> {
            alert = new WatchAlert(alert.id(), alert.createdAt(), alert.hour(), alert.rule(), alert.severity(), alert.currency(),
                    alert.subject(), alert.other(), alert.amount(), alert.expected(), alert.hits(), alert.ledgerIds(), state,
                    state == WatchAlert.State.OPEN ? null : viewer.getName(), state == WatchAlert.State.OPEN ? null : System.currentTimeMillis());
            if (isOpen()) {
                refresh();
            }
        });
    }

    /** The ledger rows that show what the alert saw, plus every name needed. */
    private CompletableFuture<List<LedgerRow>> evidence() {
        WatchConfig c = watch.config;
        long from = Buckets.start(alert.hour());
        long to = from + Buckets.HOUR_MS;
        int limit = Math.min(EVIDENCE_SLOTS.length, c.evidenceRows);
        CompletableFuture<List<LedgerRow>> loaded = switch (alert.rule()) {
            case EARN_RATE -> watch.store().async(conn -> WatchStore.incoming(conn, alert.subject(), alert.currency(), from, to, limit));
            case SELL_SPIKE -> watch.store().async(conn -> WatchStore.biggest(conn, alert.currency(), from, to, List.of(c.sellReason), limit));
            case SINK_RATIO -> CompletableFuture.completedFuture(List.of());
            default -> watch.store().async(conn -> WatchStore.byIds(conn, alert.ledgerIds()));
        };
        return loaded.thenCompose(list -> {
            List<UUID> keys = new ArrayList<>(EconomyWatch.accountKeys(list));
            keys.add(alert.subject());
            keys.add(alert.other());
            return watch.names(keys).thenApply(n -> {
                names = n;
                return list;
            });
        });
    }
}
