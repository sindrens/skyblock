package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.rewards.RewardsService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /menu}: the hub (G9 v3, I9 v2). Buttons from {@code menu.yml → hub}: your head on 4 (opens the You menu), 14
 * features in 4 spaced rows (Play, Money, Power, More; names in the row's area colour), the Staff tile on 8 (only with
 * {@code skyblock.staff.hub}), Close on 49 and Help on 50. No frame, no panes: empty slots stay empty. Icons come from
 * the icon catalogue (our pictures for viewers with the pack loaded, plain base items otherwise). Features that are not
 * built are hidden (owner G2). Every screen opened from here gets Back to the hub (built-ins open as children, commands
 * run through {@code MenuService#navigate}).
 *
 * <p>Rewards (owner G7): glows gold with the number of ready rewards; one click claims them all through
 * {@link RewardsService} and shows one summary line; with nothing ready it opens {@code /daily}.</p>
 */
final class HubMenu extends net.skybornmc.skyblock.common.menu.Menu {

    static final int SLOT_PROFILE = 4;
    /** The Staff tile (G9): top right corner, staff only. */
    static final int SLOT_STAFF = 8;
    /** Help (I9 section 4: the hub's one extra bottom-row button). */
    static final int SLOT_HELP = 50;
    static final String PERM_STAFF_TILE = "skyblock.staff.hub";
    /** Ticks between checks of the Rewards count (a daily state that loads after opening, midnight reset). */
    private static final int REWARDS_CHECK_TICKS = 40;

    private final Nav nav;
    private final Buttons buttons;
    private int shownReady = -1;
    private boolean claiming;

    HubMenu(Nav nav) {
        this.nav = nav;
        this.buttons = new Buttons(nav);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.titleKeys(viewer, Nav.TEXTS, "hub.title");
    }

    @Override
    public String messageModule() {
        return Nav.TEXTS;
    }

    /** The bottom of every navigation stack: no Back here. */
    @Override
    public boolean isMainMenu() {
        return true;
    }

    @Override
    public int tickInterval() {
        return REWARDS_CHECK_TICKS;
    }

    @Override
    protected void tick() {
        if (!claiming && shownReady >= 0 && readyCount() != shownReady) {
            refresh();
        }
    }

    @Override
    protected void build() {
        navBar();
        Set<String> services = buttons.services();
        for (ButtonLayout.Button button : nav.hub.buttons()) {
            ButtonLayout.State state = buttons.state(button, services);
            if (state == ButtonLayout.State.HIDDEN || button.hiddenFor(viewer)) {
                continue;
            }
            render(button, state);
        }
        set(SLOT_HELP, service.icon(viewer, "nav/help", service.text(viewer, "frame.help.name"), service.lines(viewer, "frame.help.lore")),
                click -> service.navigate(click.player(), this, () -> click.player().performCommand("help")));
    }

    /** The area colour of a button's group (G9: hub names take their row colour), or null. */
    private String area(ButtonLayout.Button button) {
        return button.group() == null ? null : nav.hub.groups().get(button.group());
    }

    private Component name(ButtonLayout.Button button) {
        return nav.textOr(viewer, "hub.buttons." + button.id() + ".name", "hub.fallback-name", button.id(), area(button));
    }

    private void render(ButtonLayout.Button button, ButtonLayout.State state) {
        if (ButtonLayout.YOU.equals(button.action()) && state == ButtonLayout.State.ACTIVE) {
            set(button.slot(), nav.head(viewer, "hub.head"), click -> openChild(new YouMenu(nav)));
            return;
        }
        Component name = name(button);
        List<Component> lore = new ArrayList<>(nav.lines(viewer, "hub.buttons." + button.id() + ".lore"));
        if (state == ButtonLayout.State.COMING_SOON) {
            set(button.slot(), nav.comingSoon(viewer, button.icon(), name, lore));
            return;
        }
        if (!button.allowed(viewer)) {
            lore.addAll(nav.lines(viewer, "hub.locked"));
            set(button.slot(), nav.icon(viewer, button.icon(), name, lore));
            return;
        }
        if (ButtonLayout.REWARDS.equals(button.action())) {
            set(button.slot(), rewardsItem(button, lore), click -> rewards(click.player(), button));
            return;
        }
        String own = "hub.buttons." + button.id() + ".action";
        lore.addAll(nav.lines(viewer, nav.core.messages().raw(Nav.TEXTS, own) != null ? own : "hub.action"));
        set(button.slot(), nav.icon(viewer, button.icon(), name, lore), click -> open(click.player(), button));
    }

    private void open(Player player, ButtonLayout.Button button) {
        switch (button.action()) {
            case ButtonLayout.YOU -> openChild(new YouMenu(nav));
            case ButtonLayout.TRAVEL -> openChild(new TravelMenu(nav));
            case ButtonLayout.SETTINGS -> openChild(new SettingsMenu(nav));
            case ButtonLayout.HELP -> openChild(new HelpMenu(nav));
            case ButtonLayout.TOP -> openChild(new TopMenu(nav));
            case ButtonLayout.STAFF -> {
                // Checked again on click: a player who lost the node can't use a stale hub.
                if (!button.allowed(player)) {
                    nav.core.sounds().play(player, "error");
                    refresh();
                    return;
                }
                String command = button.command().isBlank() ? "staff" : button.command();
                service.navigate(player, this, () -> player.performCommand(command));
            }
            default -> service.navigate(player, this, () -> player.performCommand(button.command()));
        }
    }

    // ------------------------------------------------------------------ rewards (owner G7)

    private Optional<RewardsService> rewardsService() {
        return nav.core.services().optional(RewardsService.class);
    }

    private int readyCount() {
        return rewardsService().map(r -> r.readyCount(viewer)).orElse(0);
    }

    /**
     * G9: name in the reward colour, the ready count as the stack count plus a glint; lore = the sentence, then
     * {@code ● <count> ready} and the sources, then {@code » CLICK to claim}. Nothing ready: the sentence and
     * {@code » CLICK to open}. Reads the cached ready state only (never the database on open).
     */
    private ItemStack rewardsItem(ButtonLayout.Button button, List<Component> description) {
        List<String> ready = rewardsService().map(r -> r.ready(viewer)).orElse(List.of());
        shownReady = ready.size();
        List<Component> lore = new ArrayList<>(description);
        Component name = name(button);
        if (ready.isEmpty()) {
            lore.addAll(nav.lines(viewer, "hub.action"));
            return nav.icon(viewer, button.icon(), name, lore);
        }
        List<String> names = new ArrayList<>();
        for (String id : ready) {
            String raw = nav.core.messages().raw(Nav.TEXTS, "hub.rewards.sources." + id);
            names.add(raw == null ? id : raw);
        }
        lore.addAll(nav.lines(viewer, "hub.rewards.ready", Msg.number("count", ready.size()), Msg.p("list", String.join(", ", names))));
        ItemStack item = nav.icon(viewer, button.icon(), name, lore);
        item.setAmount(Math.max(1, Math.min(64, ready.size())));
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true)); // the glow is only for real rewards
        return item;
    }

    private void rewards(Player player, ButtonLayout.Button button) {
        Optional<RewardsService> rewards = rewardsService();
        if (claiming) {
            return;
        }
        if (rewards.isEmpty() || rewards.get().readyCount(player) == 0) {
            openRewards(player, button);
            return;
        }
        claiming = true;
        rewards.get().claimAll(player).whenComplete((summary, error) -> nav.core.scheduler().ensureMain("menus:rewards-claimed", () -> {
            claiming = false;
            if (error != null || summary == null) {
                nav.core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Rewards claim from /menu failed", error);
                nav.core.sounds().play(player, "error");
                refreshIfOpen();
                return;
            }
            switch (summary.outcome()) {
                case CLAIMED -> {
                    player.sendMessage(summary.line());
                    nav.core.sounds().play(player, "success");
                    refreshIfOpen();
                }
                case NOTHING_READY -> {
                    if (isOpen()) {
                        openRewards(player, button);
                    }
                }
                case FAILED -> {
                    nav.core.sounds().play(player, "error");
                    refreshIfOpen();
                }
            }
        }));
    }

    /** Nothing ready: open the rewards screen ({@code daily}), with Back to the hub. */
    private void openRewards(Player player, ButtonLayout.Button button) {
        if (button.label().isEmpty() || !HubLayout.commandExists(button.label())) {
            nav.msg.send(player, "hub.rewards.nothing");
            return;
        }
        service.navigate(player, this, () -> player.performCommand(button.command()));
    }

    private void refreshIfOpen() {
        if (isOpen()) {
            refresh();
        }
    }

    /** Resolves the state of hub and You buttons for one build (services read once). */
    static final class Buttons {
        private final Nav nav;

        Buttons(Nav nav) {
            this.nav = nav;
        }

        /** Simple names of the registered services (the "feature is built" check). */
        Set<String> services() {
            Set<String> names = new HashSet<>();
            for (String name : nav.core.services().describe().keySet()) {
                names.add(name.toLowerCase(java.util.Locale.ROOT));
            }
            return names;
        }

        ButtonLayout.State state(ButtonLayout.Button button, Set<String> services) {
            return button.state(s -> services.contains(s.toLowerCase(java.util.Locale.ROOT)), HubLayout::commandExists, nav.showUnbuilt);
        }
    }
}
