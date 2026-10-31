package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tutorial stats for staff (TUTORIAL.md §10, TU7, TQ24): {@code /tutorial admin stats}, the {@code /staff} hub and
 * {@code /inspect → Tutorial}. One item per core step ({@code reached 412 · done 398 · 96%}, stopped here, median and
 * 90th percentile time, stuck presses), the summary, cameras (played / skipped), optional steps and idle reminders.
 * Toggles: 7 / 30 days, All / Java / Bedrock. Results are cached ({@link TutorialStats}).
 * <pre>
 *  . . . . H . . . .      4 summary
 *  . s s s s s s s .     10-16, 19-25 steps
 *  . s s s s s s s .
 *  . . C . . . O . .     29 cameras, 33 optional
 *  . . . P K R . . .     39 period, 40 client, 41 refresh
 * </pre>
 */
final class StatsMenu extends Menu {

    static final String PERM_STATS = "skyblock.tutorial.stats";
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int[] PERIODS = {7, 30};

    private final TutorialModule module;
    private int days;
    private FunnelStats.Client client;

    StatsMenu(TutorialModule module) {
        this(module, 7, FunnelStats.Client.ALL);
    }

    private StatsMenu(TutorialModule module, int days, FunnelStats.Client client) {
        this.module = module;
        this.days = days;
        this.client = client;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, TutorialModule.ID, "stats.title"));
    }

    @Override
    public String messageModule() {
        return TutorialModule.ID;
    }

    @Override
    public String infoKey() {
        return "stats.info";
    }

    @Override
    public String helpTopic() {
        return TutorialModule.ID;
    }

    @Override
    protected void build() {
        frame();
        if (!viewer.hasPermission(PERM_STATS)) {
            return;
        }
        CompletableFuture<FunnelStats.Result> result = module.stats().funnel(days, client);
        List<String> steps = module.engine().flow().steps();
        setAsync(4, result.thenApply(r -> MenuButton.display(summary(r))));
        for (int i = 0; i < steps.size() && i < SLOTS.length; i++) {
            int index = i;
            setAsync(SLOTS[i], result.thenApply(r -> MenuButton.display(step(r, index))));
        }
        setAsync(29, result.thenApply(r -> MenuButton.display(cameras(r))));
        setAsync(33, result.thenApply(r -> MenuButton.display(optional(r))));
        set(39, service.item(Material.CLOCK, viewer, TutorialModule.ID, "stats.period", Msg.number("days", days)), click -> pickPeriod());
        set(40, service.item(Material.SPYGLASS, viewer, TutorialModule.ID, "stats.client",
                Msg.c("client", clientName(client))), click -> pickClient());
        set(41, service.item(Material.SUNFLOWER, viewer, TutorialModule.ID, "stats.refresh"), click -> {
            module.stats().clear();
            refresh();
        });
    }

    /** Pick lists instead of cyclers (GUI-4b/G9). */
    private void pickPeriod() {
        List<PickListMenu.Option<Integer>> options = new ArrayList<>();
        for (int period : PERIODS) {
            options.add(PickListMenu.Option.of(period, Material.CLOCK,
                    service.text(viewer, TutorialModule.ID, "stats.period-pick", Msg.number("days", period)), period == days));
        }
        openChild(new PickListMenu<>(List.of(service.text(viewer, TutorialModule.ID, "stats.title"),
                service.text(viewer, TutorialModule.ID, "stats.period-crumb")), options, period -> days = period));
    }

    private void pickClient() {
        List<PickListMenu.Option<FunnelStats.Client>> options = new ArrayList<>();
        for (FunnelStats.Client c : FunnelStats.Client.values()) {
            options.add(PickListMenu.Option.of(c, Material.SPYGLASS, clientName(c), c == client));
        }
        openChild(new PickListMenu<>(List.of(service.text(viewer, TutorialModule.ID, "stats.title"),
                service.text(viewer, TutorialModule.ID, "stats.client-crumb")), options, c -> client = c));
    }

    private Component clientName(FunnelStats.Client c) {
        return service.text(viewer, TutorialModule.ID, "stats.clients." + c.name().toLowerCase(java.util.Locale.ROOT));
    }

    private ItemStack summary(FunnelStats.Result r) {
        FunnelStats.StepStats worst = r.worst();
        return service.item(Material.WRITTEN_BOOK, viewer, TutorialModule.ID, "stats.summary",
                Msg.number("days", days), Msg.c("client", service.text(viewer, TutorialModule.ID, "stats.clients." + client.name().toLowerCase(java.util.Locale.ROOT))),
                Msg.number("started", r.started()), Msg.number("finished", r.finished()), Msg.number("percent", r.finishedPercent()),
                Msg.number("players", r.players()), Msg.number("idle", r.idleReminders()),
                Msg.c("worst", worst == null ? service.text(viewer, TutorialModule.ID, "stats.none")
                        : module.engine().stepName(viewer, worst.step())));
    }

    private ItemStack step(FunnelStats.Result r, int index) {
        FunnelStats.StepStats s = r.steps().get(index);
        // Red when more than 10% of the players who reached it stopped there (§10: fix that step first).
        boolean bad = s.reached() > 0 && s.dropped() * 10 > s.reached();
        Material icon = bad ? Material.RED_DYE : s.reached() == 0 ? Material.GRAY_DYE : Material.LIME_DYE;
        return service.item(icon, viewer, TutorialModule.ID, "stats.step", Msg.number("number", index + 1),
                Msg.c("step", module.engine().stepName(viewer, s.step())), Msg.number("reached", s.reached()), Msg.number("done", s.done()),
                Msg.number("percent", s.percent()), Msg.number("dropped", s.dropped()), Msg.p("median", time(s.medianMs())),
                Msg.p("p90", time(s.p90Ms())), Msg.number("stuck", s.stuck()));
    }

    private ItemStack cameras(FunnelStats.Result r) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, TutorialModule.ID, "stats.cameras.lore"));
        if (r.cameras().isEmpty()) {
            lore.add(service.text(viewer, TutorialModule.ID, "stats.none"));
        }
        for (FunnelStats.CameraStats c : r.cameras()) {
            lore.add(service.text(viewer, TutorialModule.ID, "stats.cameras.line", Msg.p("camera", c.camera()),
                    Msg.number("played", c.played()), Msg.number("skipped", c.skipped())));
        }
        return service.item(Material.ENDER_EYE, service.text(viewer, TutorialModule.ID, "stats.cameras.name"), lore);
    }

    private ItemStack optional(FunnelStats.Result r) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, TutorialModule.ID, "stats.optional.lore"));
        if (r.optional().isEmpty()) {
            lore.add(service.text(viewer, TutorialModule.ID, "stats.none"));
        }
        for (Map.Entry<String, Integer> e : r.optional().entrySet()) {
            lore.add(service.text(viewer, TutorialModule.ID, "stats.optional.line", Msg.p("step", e.getKey()), Msg.number("done", e.getValue())));
        }
        return service.item(Material.KNOWLEDGE_BOOK, service.text(viewer, TutorialModule.ID, "stats.optional.name"), lore);
    }

    private static String time(long ms) {
        return ms <= 0 ? "-" : Format.duration(Duration.ofMillis(Math.max(1000, ms)));
    }
}
