package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Sanctions button of {@code /p} (P7, spec 13.1), Bedrock-safe: plain clicks only, no shift/drag. Freeze balance and
 * block trading (length, then reason), remove money (amount with the number picker, then reason), confiscate item (the
 * online player's inventory or ender chest, then reason), and the player's recent sanctions with Lift / Restore.
 */
final class SanctionMenus {

    private static final String MODULE = PunishMenus.MODULE;
    private static final int[] CHOICE_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private SanctionMenus() {
    }

    /** Reopens the Sanctions menu of {@code name} with fresh data (after an action). Main thread. */
    static void reopen(ModerationModule module, Player viewer, PunishMenus.Target target) {
        module.target(target.uuid(), target.name()).thenAcceptAsync(fresh -> {
            if (viewer.isOnline()) {
                Menu menu = new SanctionsMenu(module, fresh);
                menu.parent(new PunishMenus.PunishMenu(module, fresh.withEvidence(target.evidence())));
                module.core().menus().open(viewer, menu);
            }
        }, runnable -> Bukkit.getScheduler().runTask(module.core().plugin(), runnable));
    }

    // ================================================================== main sanctions menu

    /** 6 rows: head, the four actions, the player's last sanctions (click to lift/restore). */
    static final class SanctionsMenu extends Menu {

        private static final int SLOT_HEAD = 4;
        private static final int SLOT_FREEZE = 19;
        private static final int SLOT_TRADE = 21;
        private static final int SLOT_MONEY = 23;
        private static final int SLOT_CONFISCATE = 25;
        private static final int[] RECENT = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

        private final ModerationModule module;
        private final PunishMenus.Target target;
        private boolean used;

        SanctionsMenu(ModerationModule module, PunishMenus.Target target) {
            this.module = module;
            this.target = target;
        }

        @Override
        public int rows() {
            return 6;
        }

        @Override
        public Component title() {
            return service.title(viewer, service.text(viewer, MODULE, "punish.title", Msg.p("player", target.name())),
                    service.text(viewer, MODULE, "sanctions.menu.title"));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "sanctions.menu.info";
        }

        @Override
        public String helpTopic() {
            return "staff";
        }

        @Override
        protected void build() {
            frame();
            Sanctions s = module.sanctions();
            set(SLOT_HEAD, service.item(PunishMenus.head(target), service.text(viewer, MODULE, "sanctions.menu.head.name",
                    Msg.p("player", target.name())), service.lines(viewer, MODULE, "sanctions.menu.head.lore",
                    Msg.number("count", target.sanctions().size()))));
            long now = System.currentTimeMillis();
            blockButton(SLOT_FREEZE, Sanction.Kind.FREEZE, Material.PACKED_ICE, now);
            blockButton(SLOT_TRADE, Sanction.Kind.TRADE, Material.IRON_BARS, now);
            set(SLOT_MONEY, actionItem(Sanction.Kind.MONEY, Material.GOLD_INGOT), click -> {
                if (!allowed(Sanction.Kind.MONEY)) {
                    return;
                }
                long max = s.settings().moneyMax();
                openChild(new NumberPickerMenu(p -> service.text(p, MODULE, "sanctions.money.picker", Msg.p("player", target.name())),
                        1, max, Math.min(1000, max), new long[]{1000, 100_000, 10_000_000}, (p, amount) -> pickReason(
                        Sanction.Kind.MONEY, id -> s.removeMoney(p, target.uuid(), target.name(), amount, id))));
            });
            Player online = Bukkit.getPlayer(target.uuid());
            set(SLOT_CONFISCATE, actionItem(Sanction.Kind.ITEM, online == null ? Material.GRAY_DYE : Material.SHEARS), click -> {
                if (!allowed(Sanction.Kind.ITEM)) {
                    return;
                }
                Player now2 = Bukkit.getPlayer(target.uuid());
                if (now2 == null) {
                    module.messages().send(viewer, "sanctions.confiscate-offline", Msg.p("player", target.name()));
                    module.core().sounds().play(viewer, "error");
                    return;
                }
                openChild(new ConfiscateMenu(module, target, ConfiscateLayout.View.HOTBAR));
            });
            List<Sanction> list = target.sanctions();
            for (int i = 0; i < list.size() && i < RECENT.length; i++) {
                set(RECENT[i], recent(list.get(i), now));
            }
        }

        private boolean allowed(Sanction.Kind kind) {
            String problem = module.sanctions().rightsProblem(viewer, kind);
            if (problem != null) {
                module.messages().send(viewer, problem, Msg.p("kind", module.sanctions().kindText(viewer, kind)));
                module.core().sounds().play(viewer, "error");
                return false;
            }
            return !used;
        }

        private ItemStack actionItem(Sanction.Kind kind, Material icon) {
            Sanctions s = module.sanctions();
            String problem = s.rightsProblem(viewer, kind);
            String key = "sanctions.menu." + kind.id();
            if ("sanctions.unavailable".equals(problem)) {
                return service.item(module.templates().config().menu().unavailableIcon(), viewer, MODULE, key + "-unavailable");
            }
            return service.item(problem != null ? Material.GRAY_DYE : icon, viewer, MODULE, problem != null ? key + "-locked" : key,
                    Msg.p("player", target.name()), Msg.p("max", s.amountText("money", s.settings().moneyMax())));
        }

        private void blockButton(int slot, Sanction.Kind kind, Material icon, long now) {
            Sanctions s = module.sanctions();
            Optional<Sanction> live = SanctionRules.active(s.activeOf(target.uuid()), kind, now);
            if (live.isPresent()) {
                Sanction x = live.get();
                set(slot, service.item(Material.RED_CONCRETE, viewer, MODULE, "sanctions.menu." + kind.id() + "-on",
                        s.parts(viewer, x)), click -> {
                    if (used || !viewer.hasPermission(Sanctions.permission(kind))) {
                        return;
                    }
                    used = true;
                    s.lift(viewer, x).whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(),
                            () -> reopen(module, viewer, target)));
                });
                return;
            }
            set(slot, actionItem(kind, icon), click -> {
                if (!allowed(kind)) {
                    return;
                }
                openChild(new ChoiceMenu(module, target, kind, lengthChoices(module, viewer, length -> pickReason(kind,
                        id -> module.sanctions().startBlock(viewer, target.uuid(), target.name(), kind, length, id)))));
            });
        }

        /** Opens the reason choice for {@code kind}; the picked reason runs {@code action}, then the menu reopens. */
        private void pickReason(Sanction.Kind kind, java.util.function.Function<String, java.util.concurrent.CompletableFuture<?>> action) {
            Player p = viewer;
            Bukkit.getScheduler().runTask(service.plugin(), () -> module.core().menus().open(p,
                    new ChoiceMenu(module, target, kind, reasonChoices(module, p, id -> action.apply(id)
                            .whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(), () -> reopen(module, p, target)))))));
        }

        private MenuButton recent(Sanction x, long now) {
            Sanctions s = module.sanctions();
            Material icon = switch (x.kind()) {
                case FREEZE -> Material.PACKED_ICE;
                case TRADE -> Material.IRON_BARS;
                case MONEY -> Material.GOLD_NUGGET;
                case ITEM -> Material.SHEARS;
            };
            boolean blocking = x.blocksAt(now);
            String key = x.restorable() ? "sanctions.menu.entry-restore" : blocking ? "sanctions.menu.entry-lift"
                    : "sanctions.menu.entry";
            ItemStack item = service.item(icon, viewer, MODULE, key, s.parts(viewer, x));
            if (x.restorable() && viewer.hasPermission(Sanctions.PERM_RESTORE)) {
                return MenuButton.of(item, click -> {
                    if (used) {
                        return;
                    }
                    used = true;
                    s.restore(viewer, x.id());
                    Bukkit.getScheduler().runTaskLater(service.plugin(), () -> reopen(module, viewer, target), 10L);
                });
            }
            if (blocking && viewer.hasPermission(Sanctions.permission(x.kind()))) {
                return MenuButton.of(item, click -> {
                    if (used) {
                        return;
                    }
                    used = true;
                    s.lift(viewer, x).whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(),
                            () -> reopen(module, viewer, target)));
                });
            }
            return MenuButton.display(item);
        }
    }

    // ================================================================== choices (length, reason)

    /** One option of a {@link ChoiceMenu}. */
    record Choice(Material icon, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver parts, Runnable pick) {
    }

    /** The lengths of {@code extras.sanctions.durations}. */
    static List<Choice> lengthChoices(ModerationModule module, Player viewer, Consumer<Duration> pick) {
        List<Choice> out = new ArrayList<>();
        for (Duration d : module.sanctions().settings().durations()) {
            out.add(new Choice(d == null ? Material.BEDROCK : Material.CLOCK, "sanctions.menu.length",
                    Msg.p("length", module.sanctions().lengthText(viewer, d)), () -> pick.accept(d)));
        }
        return out;
    }

    /** The preset reasons of {@code extras.sanctions.reasons}. */
    static List<Choice> reasonChoices(ModerationModule module, Player viewer, Consumer<String> pick) {
        List<Choice> out = new ArrayList<>();
        for (String id : module.sanctions().settings().reasons()) {
            out.add(new Choice(Material.PAPER, "sanctions.menu.reason", Msg.p("reason", module.sanctions().reasonText(viewer, id)),
                    () -> pick.accept(id)));
        }
        return out;
    }

    /** 3 rows: up to 7 options (one click each) and Back. */
    static final class ChoiceMenu extends Menu {

        private final ModerationModule module;
        private final PunishMenus.Target target;
        private final Sanction.Kind kind;
        private final List<Choice> choices;
        private boolean used;

        ChoiceMenu(ModerationModule module, PunishMenus.Target target, Sanction.Kind kind, List<Choice> choices) {
            this.module = module;
            this.target = target;
            this.kind = kind;
            this.choices = choices;
        }

        @Override
        public int rows() {
            return 3;
        }

        @Override
        public Component title() {
            return service.title(viewer, Component.text(target.name()), service.text(viewer, MODULE, "sanctions.menu.title"),
                    Component.text(module.sanctions().kindText(viewer, kind)));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        protected void build() {
            frame();
            for (int i = 0; i < choices.size() && i < CHOICE_SLOTS.length; i++) {
                Choice c = choices.get(i);
                set(CHOICE_SLOTS[i], service.item(c.icon(), viewer, MODULE, c.key(), c.parts(), Msg.p("player", target.name()),
                        Msg.p("kind", module.sanctions().kindText(viewer, kind))), click -> {
                    if (used || !viewer.hasPermission(Sanctions.permission(kind))) {
                        return;
                    }
                    used = true;
                    viewer.closeInventory();
                    c.pick().run();
                });
            }
            if (parent() == null) {
                // Opened without a parent (by command): Back (the frame's slot 18) returns to the /p screen.
                set(SLOT_SMALL_BACK, service.navItem("back", Material.ARROW, viewer, "frame.back"),
                        click -> reopen(module, viewer, target));
            }
        }
    }

    // ================================================================== confiscate

    /**
     * The online target's stacks, refreshed every second. QOL3: the standard frame (Back 45, ? 48, Close 49, Main menu
     * 50) with three tabs in row 0 (Hotbar &amp; armor, Backpack, Ender chest; {@link ConfiscateLayout}), so every
     * place keeps a fixed slot. Click a stack, then a reason, then confirm.
     */
    static final class ConfiscateMenu extends Menu {

        private static final int TAB_HOTBAR = 3;
        private static final int TAB_BACKPACK = 4;
        private static final int TAB_ENDER = 5;

        private final ModerationModule module;
        private final PunishMenus.Target target;
        private ConfiscateLayout.View view;

        ConfiscateMenu(ModerationModule module, PunishMenus.Target target, ConfiscateLayout.View view) {
            this.module = module;
            this.target = target;
            this.view = view;
        }

        @Override
        public int rows() {
            return 6;
        }

        @Override
        public Component title() {
            return service.title(viewer, Component.text(target.name()), service.text(viewer, MODULE,
                    "sanctions.confiscate-menu.title-" + view.source().id()));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "sanctions.confiscate-menu.info";
        }

        @Override
        public String helpTopic() {
            return "staff";
        }

        @Override
        public int tickInterval() {
            return 20;
        }

        @Override
        protected void tick() {
            refresh();
        }

        @Override
        protected void build() {
            Player online = Bukkit.getPlayer(target.uuid());
            if (online == null) {
                viewer.closeInventory();
                module.messages().send(viewer, "sanctions.confiscate-offline", Msg.p("player", target.name()));
                return;
            }
            frame();
            tab(TAB_HOTBAR, ConfiscateLayout.View.HOTBAR, Material.IRON_CHESTPLATE);
            tab(TAB_BACKPACK, ConfiscateLayout.View.BACKPACK, Material.CHEST);
            tab(TAB_ENDER, ConfiscateLayout.View.ENDER, Material.ENDER_CHEST);
            Sanctions.Source source = view.source();
            Inventory inv = Sanctions.inventory(online, source);
            int[] map = ConfiscateLayout.slots(view, online.getEnderChest().getSize());
            boolean any = false;
            for (int menuSlot = 0; menuSlot < map.length; menuSlot++) {
                int at = map[menuSlot];
                if (at < 0 || at >= inv.getSize()) {
                    continue;
                }
                ItemStack stack = inv.getItem(at);
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                any = true;
                ItemStack expected = stack.clone();
                ConfiscateLayout.Place place = ConfiscateLayout.place(view, at);
                List<Component> lore = new ArrayList<>();
                if (stack.hasItemMeta() && stack.getItemMeta().lore() != null) {
                    lore.addAll(stack.getItemMeta().lore());
                }
                lore.add(Component.empty());
                lore.add(service.text(viewer, MODULE, "sanctions.confiscate-menu.place." + place.key(), Msg.p("n", place.number())));
                lore.addAll(service.lines(viewer, MODULE, "sanctions.confiscate-menu.click", Msg.p("item", Sanctions.describe(stack))));
                ItemStack shown = stack.clone();
                shown.editMeta(meta -> meta.lore(lore));
                set(menuSlot, shown, click -> openChild(new ChoiceMenu(module, target, Sanction.Kind.ITEM,
                        reasonChoices(module, viewer, id -> confirm(source, at, expected, id)))));
            }
            if (!any) {
                setEmpty(MODULE, "sanctions.confiscate-menu.empty");
            }
        }

        /** A tab in row 0; switching redraws this window in place (same Back, cursor stays). */
        private void tab(int slot, ConfiscateLayout.View tab, Material icon) {
            String key = "sanctions.confiscate-menu.tab." + tab.id();
            set(slot, service.tab(viewer, new ItemStack(icon), service.text(viewer, MODULE, key + ".name"),
                    service.lines(viewer, MODULE, key + ".lore"), tab == view, () -> view = tab));
        }

        /**
         * GUI-4c (GUI_DESIGN §4): "Confiscate <item>?" with the player, the place and the reason on the middle item. The
         * stack is checked again on confirm ({@code expected}), so a stack that changed meanwhile is not taken.
         */
        private void confirm(Sanctions.Source source, int at, ItemStack expected, String reason) {
            ConfirmMenu confirm = new ConfirmMenu(v -> service.text(v, MODULE, "sanctions.confiscate-menu.confirm.crumb"), expected.clone(),
                    v -> service.lines(v, MODULE, "sanctions.confiscate-menu.confirm.details", Msg.p("item", Sanctions.describe(expected)),
                            Msg.p("player", target.name()), Msg.p("reason", module.sanctions().reasonText(v, reason)),
                            Msg.c("where", service.text(v, MODULE, "sanctions.confiscate-menu.title-" + source.id()))),
                    p -> {
                        Player now = Bukkit.getPlayer(target.uuid());
                        if (now == null) {
                            module.messages().send(p, "sanctions.confiscate-offline", Msg.p("player", target.name()));
                            return;
                        }
                        module.sanctions().confiscate(p, now, source, at, expected, reason)
                                .whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(), () -> reopen(module, p, target)));
                    }, p -> { });
            confirm.parent(this);
            module.core().menus().open(viewer, confirm);
        }
    }
}
