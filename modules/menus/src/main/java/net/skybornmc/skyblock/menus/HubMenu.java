package net.skybornmc.skyblock.menus;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.rewards.RewardsService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /menu}: the clean hub (GUI_DESIGN §7.1, GUI-2, owner G1). 15 buttons from {@code menu.yml → hub}: your head on 4
 * (opens the You menu) and 14 features in 4 groups with space between them (Play, Money, Power, More), each group marked
 * by a coloured pane on the left edge of its row. 48 ?, 49 Close, 50 Help & guides come from the framework (the only
 * Help button). Features that are not built are hidden (owner G2). Every screen opened from here gets Back to the hub
 * (built-ins open as children, commands run through {@code MenuService#navigate}).
 *
 * <p>Rewards (owner G7): glows gold with the number of ready rewards; one click claims them all through
 * {@link RewardsService} and shows one summary line; with nothing ready it opens {@code /daily}.</p>
 */
final class HubMenu extends net.skybornmc.skyblock.common.menu.Menu {

    static final int SLOT_PROFILE = 4;
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

    @Override
    public String infoKey() {
        return "hub.info";
    }

    @Override
    public String helpTopic() {
        return "menu";
    }

    /** The bottom of every navigation stack: no Back here, and slot 50 is Help & guides. */
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
        frame();
        ItemStack filler = nav.core.menus().filler(border());
        for (int slot = 9; slot < 45; slot++) {
            if (slot % 9 != 0 && slot % 9 != 8) {
                set(slot, filler); // the space between the groups (GUI_DESIGN sketch: dark well)
            }
        }
        Set<String> services = buttons.services();
        Map<String, List<ButtonLayout.Button>> visibleByGroup = new java.util.LinkedHashMap<>();
        for (ButtonLayout.Button button : nav.hub.buttons()) {
            ButtonLayout.State state = buttons.state(button, services);
            if (state == ButtonLayout.State.HIDDEN) {
                continue;
            }
            if (button.group() != null) {
                visibleByGroup.computeIfAbsent(button.group(), g -> new ArrayList<>()).add(button);
            }
            render(button, state);
        }
        for (Map.Entry<String, String> group : nav.hub.groups().entrySet()) {
            int mark = ButtonLayout.markSlot(visibleByGroup.getOrDefault(group.getKey(), List.of()));
            if (mark >= 0) {
                set(mark, nav.item(Nav.material(group.getValue(), Material.GRAY_STAINED_GLASS_PANE), nav.textOr(viewer, "hub.groups." + group.getKey() + ".name", "hub.fallback-name",
                        group.getKey()), nav.lines(viewer, "hub.groups." + group.getKey() + ".lore")));
            }
        }
    }

    private void render(ButtonLayout.Button button, ButtonLayout.State state) {
        if (ButtonLayout.YOU.equals(button.action()) && state == ButtonLayout.State.ACTIVE) {
            set(button.slot(), nav.head(viewer, "hub.head"), click -> openChild(new YouMenu(nav)));
            return;
        }
        Component name = nav.textOr(viewer, "hub.buttons." + button.id() + ".name", "hub.fallback-name", button.id());
        List<Component> lore = new ArrayList<>(nav.lines(viewer, "hub.buttons." + button.id() + ".lore"));
        if (state == ButtonLayout.State.COMING_SOON) {
            set(button.slot(), nav.comingSoon(viewer, Nav.material(button.icon(), Material.BOOK), name, lore));
            return;
        }
        if (!button.allowed(viewer)) {
            lore.addAll(nav.lines(viewer, "hub.locked"));
            set(button.slot(), nav.item(Nav.material(button.icon(), Material.BOOK), name, lore));
            return;
        }
        if (ButtonLayout.REWARDS.equals(button.action())) {
            set(button.slot(), rewardsItem(button, lore), click -> rewards(click.player(), button));
            return;
        }
        lore.addAll(nav.lines(viewer, "hub.action"));
        set(button.slot(), nav.item(Nav.material(button.icon(), Material.BOOK), name, lore), click -> open(click.player(), button));
    }

    private void open(Player player, ButtonLayout.Button button) {
        switch (button.action()) {
            case ButtonLayout.YOU -> openChild(new YouMenu(nav));
            case ButtonLayout.TRAVEL -> openChild(new TravelMenu(nav));
            case ButtonLayout.SETTINGS -> openChild(new SettingsMenu(nav));
            case ButtonLayout.HELP -> openChild(new HelpMenu(nav));
            case ButtonLayout.TOP -> openChild(new TopMenu(nav));
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

    private ItemStack rewardsItem(ButtonLayout.Button button, List<Component> description) {
        List<String> ready = rewardsService().map(r -> r.ready(viewer)).orElse(List.of());
        shownReady = ready.size();
        List<Component> lore = new ArrayList<>(description);
        ItemStack item;
        if (ready.isEmpty()) {
            lore.addAll(nav.lines(viewer, "hub.rewards.none"));
            item = nav.item(Nav.material(button.icon(), Material.BOOK), nav.textOr(viewer, "hub.buttons." + button.id() + ".name", "hub.fallback-name", button.id()), lore);
        } else {
            List<String> names = new ArrayList<>();
            for (String id : ready) {
                String raw = nav.core.messages().raw(Nav.TEXTS, "hub.rewards.sources." + id);
                names.add(raw == null ? id : raw);
            }
            lore.addAll(nav.lines(viewer, "hub.rewards.ready", Msg.number("count", ready.size()), Msg.p("list", String.join(", ", names))));
            item = nav.item(Nav.material(button.icon(), Material.BOOK), nav.text(viewer, "hub.rewards.ready-name", Msg.number("count", ready.size())), lore);
            item.setAmount(Math.max(1, Math.min(64, ready.size())));
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true)); // gold glow: only for real rewards
        }
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

    private Material border() {
        Material m = Material.matchMaterial(String.valueOf(nav.core.menus().config().frameBorder).strip());
        return m == null || !m.isItem() ? Material.BLACK_STAINED_GLASS_PANE : m;
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
