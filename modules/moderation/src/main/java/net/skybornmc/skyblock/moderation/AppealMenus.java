package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Staff appeal menus (spec 13.10): the {@code /appeals} queue (open first, oldest first), one appeal (punishment, evidence,
 * history, the player's text; Accept / Reduce / Deny) and the deny reasons. Bedrock-safe: plain left clicks only, the
 * permission is checked again on every click, long texts are also printed in chat.
 */
final class AppealMenus {

    private static final String MODULE = PunishMenus.MODULE;
    /** Characters per lore line of the appeal text. */
    private static final int WRAP = 40;

    private AppealMenus() {
    }

    /** Everything one appeal screen shows (loaded before opening). {@code punishment == null} = deleted/unknown. */
    record View(Appeal appeal, Punishment punishment, List<String> evidence, List<Punishment> history) {
    }

    /** Splits a text into lore lines of about {@code width} characters (on spaces where possible), at most {@code max}. */
    static List<String> wrap(String text, int width, int max) {
        List<String> out = new ArrayList<>();
        String rest = text == null ? "" : text.strip();
        while (!rest.isEmpty() && out.size() < max) {
            if (rest.length() <= width) {
                out.add(rest);
                rest = "";
                break;
            }
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= width / 2) {
                cut = width;
            }
            out.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty() && !out.isEmpty()) {
            out.set(out.size() - 1, Evidence.cut(out.getLast() + " " + rest, width));
        }
        return out;
    }

    // ================================================================== queue

    static final class QueueMenu extends PagedMenu<Appeal> {

        private final ModerationModule module;
        private final AppealService appeals;
        private final List<Appeal> entries;
        private final Map<Long, Punishment> punishments;

        QueueMenu(ModerationModule module, AppealService appeals, List<Appeal> entries, Map<Long, Punishment> punishments) {
            this.module = module;
            this.appeals = appeals;
            this.entries = List.copyOf(entries);
            this.punishments = Map.copyOf(punishments);
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "appeal.menu.queue-info";
        }

        @Override
        public String helpTopic() {
            return "staff";
        }

        @Override
        protected List<Appeal> entries() {
            return entries;
        }

        @Override
        protected List<Component> titleCrumbs() {
            return List.of(service.text(viewer, MODULE, "appeal.menu.queue-title"));
        }

        @Override
        protected String emptyKey() {
            return "appeal.menu.queue-empty";
        }

        @Override
        protected MenuButton entry(Appeal a) {
            Punishment p = punishments.get(a.punishmentId());
            boolean active = p != null && p.active(System.currentTimeMillis());
            TagResolver parts = appeals.parts(viewer, a);
            TagResolver pp = p == null ? Msg.p("reason", "?") : appeals.punishmentParts(viewer, p);
            List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, "appeal.menu.entry.lore", parts, pp));
            for (String line : wrap(a.text(), WRAP, 3)) {
                lore.addAll(service.lines(viewer, MODULE, "appeal.menu.text-line", Msg.p("line", line)));
            }
            if (!active) {
                lore.addAll(service.lines(viewer, MODULE, "appeal.menu.entry.ended"));
            }
            lore.add(Component.empty());
            lore.addAll(service.lines(viewer, MODULE, "appeal.menu.entry.click"));
            Material icon = !active ? Material.GRAY_DYE : a.punishmentType() == Punishment.Type.BAN ? Material.RED_CONCRETE
                    : a.punishmentType() == Punishment.Type.MUTE ? Material.ORANGE_CONCRETE : Material.YELLOW_CONCRETE;
            return MenuButton.of(service.item(icon, service.text(viewer, MODULE, "appeal.menu.entry.name", parts, pp), lore),
                    click -> appeals.open(viewer, a.id(), this));
        }
    }

    // ================================================================== one appeal

    /**
     * 6 rows (the standard frame: back, info, close, help on the bottom row):
     * <pre>
     *  row 1: [head] (4)
     *  row 3: [punishment] (19) [text] (21) [evidence] (23) [history] (25)
     *  row 5: [Accept] (38) [Reduce] (40) [Deny] (42)
     * </pre>
     */
    static final class AppealMenu extends Menu {

        private final ModerationModule module;
        private final AppealService appeals;
        private final View view;
        private boolean used;

        AppealMenu(ModerationModule module, AppealService appeals, View view) {
            this.module = module;
            this.appeals = appeals;
            this.view = view;
        }

        @Override
        public int rows() {
            return 6;
        }

        @Override
        public String infoKey() {
            return "appeal.menu.info";
        }

        @Override
        public String helpTopic() {
            return "staff";
        }

        @Override
        public Component title() {
            return service.title(viewer, service.text(viewer, MODULE, "appeal.menu.queue-title"),
                    Component.text("#" + view.appeal().id()), Component.text(view.appeal().playerName()));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        private boolean mayAct() {
            if (!viewer.hasPermission(ModerationModule.PERM_APPEALS)) {
                viewer.closeInventory();
                module.messages().send(viewer, "appeal.no-permission");
                return false;
            }
            return !used;
        }

        @Override
        protected void build() {
            frame();
            Appeal a = view.appeal();
            Punishment p = view.punishment();
            boolean active = p != null && p.active(System.currentTimeMillis()) && a.open();
            TagResolver parts = appeals.parts(viewer, a);
            TagResolver pp = p == null ? Msg.p("reason", "?") : appeals.punishmentParts(viewer, p);
            AppealRules.HistorySummary h = AppealRules.summary(view.history());
            TagResolver hist = TagResolver.resolver(Msg.number("bans", h.bans()), Msg.number("mutes", h.mutes()),
                    Msg.number("warns", h.warns()), Msg.number("kicks", h.kicks()));

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(a.player(), a.playerName())));
            set(4, MenuButton.display(service.item(head, service.text(viewer, MODULE, "appeal.menu.head.name", parts),
                    service.lines(viewer, MODULE, "appeal.menu.head.lore", parts, hist))));

            List<Component> info = new ArrayList<>(service.lines(viewer, MODULE, p == null ? "appeal.menu.punishment.gone"
                    : "appeal.menu.punishment.lore", parts, pp));
            if (a.otherStaff(viewer.getUniqueId())) {
                info.addAll(service.lines(viewer, MODULE, "appeal.menu.punishment.other-staff", parts));
            }
            set(19, MenuButton.display(service.item(Material.PAPER, service.text(viewer, MODULE, "appeal.menu.punishment.name",
                    parts, pp), info)));

            List<Component> textLore = new ArrayList<>();
            for (String line : wrap(a.text(), WRAP, 12)) {
                textLore.addAll(service.lines(viewer, MODULE, "appeal.menu.text-line", Msg.p("line", line)));
            }
            textLore.add(Component.empty());
            textLore.addAll(service.lines(viewer, MODULE, "appeal.menu.text.click"));
            set(21, service.item(Material.BOOK, service.text(viewer, MODULE, "appeal.menu.text.name", parts), textLore), click -> {
                module.messages().send(viewer, "appeal.menu.text-chat", parts);
            });

            if (view.evidence().isEmpty()) {
                set(23, MenuButton.display(service.item(Material.GRAY_DYE, viewer, MODULE, "appeal.menu.evidence-none")));
            } else {
                List<Component> lore = new ArrayList<>();
                for (String line : view.evidence().subList(Math.max(0, view.evidence().size() - 8), view.evidence().size())) {
                    lore.addAll(service.lines(viewer, MODULE, "entry.evidence-line", Msg.p("line", line)));
                }
                lore.add(Component.empty());
                lore.addAll(service.lines(viewer, MODULE, "entry.evidence-click"));
                set(23, service.item(Material.WRITABLE_BOOK, service.text(viewer, MODULE, "entry.evidence-name",
                        Msg.number("count", view.evidence().size())), lore), click -> {
                            if (!viewer.hasPermission(ModerationModule.PERM_HISTORY)) {
                                return;
                            }
                            module.messages().send(viewer, "entry.chat-header", Msg.p("id", a.punishmentId()),
                                    Msg.p("player", a.playerName()));
                            for (String line : view.evidence()) {
                                module.messages().send(viewer, "entry.chat-line", Msg.p("line", line));
                            }
                        });
            }

            set(25, service.item(Material.WRITABLE_BOOK, viewer, MODULE, "appeal.menu.history", hist), click -> {
                if (!viewer.hasPermission(ModerationModule.PERM_HISTORY)) {
                    module.messages().send(viewer, "error.no-permission-menu");
                    return;
                }
                module.target(a.player(), a.playerName()).thenAccept(t -> Bukkit.getScheduler().runTask(service.plugin(),
                        () -> openChild(new PunishMenus.HistoryMenu(t, module))));
            });

            if (!a.open()) {
                set(40, MenuButton.display(service.item(Material.BARRIER, viewer, MODULE, "appeal.menu.handled", parts)));
            } else if (!active) {
                // the punishment is over: Accept only closes the appeal (no decision)
                set(40, service.item(Material.GRAY_DYE, viewer, MODULE, "appeal.menu.close", parts), click -> {
                    if (mayAct()) {
                        used = true;
                        appeals.accept(viewer, a).whenComplete((r, e) -> after());
                    }
                });
            } else {
                set(38, service.item(Material.LIME_CONCRETE, viewer, MODULE, a.otherStaff(viewer.getUniqueId())
                        ? "appeal.menu.accept-other" : "appeal.menu.accept", parts, pp), click -> {
                            if (!mayAct()) {
                                return;
                            }
                            openChild(new ConfirmMenu(v -> service.text(v, MODULE, "appeal.menu.accept-title", parts),
                                    service.item(Material.LIME_CONCRETE, viewer, MODULE, "appeal.menu.accept", parts, pp),
                                    v -> service.lines(v, MODULE, "appeal.menu.accept-details", parts, pp),
                                    v -> {
                                        if (v.hasPermission(ModerationModule.PERM_APPEALS) && !used) {
                                            used = true;
                                            appeals.accept(v, a).whenComplete((r, e) -> after());
                                        }
                                    }, v -> { }));
                        });
                AppealRules.ReduceRange range = AppealRules.reduceRange(p, appeals.cfg().reduceMaxDays());
                if (range == null) {
                    set(40, MenuButton.display(service.item(Material.GRAY_DYE, viewer, MODULE, "appeal.menu.reduce-none")));
                } else {
                    set(40, service.item(Material.CLOCK, viewer, MODULE, "appeal.menu.reduce", parts, pp,
                            Msg.p("unit", unit(range.unit()))), click -> {
                                if (!mayAct()) {
                                    return;
                                }
                                openChild(new NumberPickerMenu(v -> service.text(v, MODULE, "appeal.menu.reduce-title",
                                        Msg.p("unit", unit(range.unit())), parts), range.min(), range.max(), range.initial(),
                                        steps(range.unit()), (v, amount) -> {
                                            if (v.hasPermission(ModerationModule.PERM_APPEALS) && !used) {
                                                used = true;
                                                appeals.reduce(v, a, range.unit(), amount).whenComplete((r, e) -> after());
                                            }
                                        }));
                            });
                }
                set(42, service.item(Material.RED_CONCRETE, viewer, MODULE, "appeal.menu.deny", parts, pp), click -> {
                    if (mayAct()) {
                        openChild(new DenyMenu(module, appeals, a));
                    }
                });
            }
        }

        private String unit(AppealRules.Unit unit) {
            return module.service().text(viewer, "appeal.units." + unit.key());
        }

        private static long[] steps(AppealRules.Unit unit) {
            return switch (unit) {
                case MINUTES -> new long[]{1, 10, 60};
                case HOURS -> new long[]{1, 6, 24};
                case DAYS -> new long[]{1, 7, 30};
            };
        }

        /** Back to the queue (fresh) after a decision. */
        private void after() {
            Bukkit.getScheduler().runTask(service.plugin(), () -> {
                if (viewer.isOnline()) {
                    appeals.queue(viewer);
                }
            });
        }
    }

    // ================================================================== deny reasons

    /** Deny an appeal: preset reasons ({@code appeal.deny-reasons}) or "Type a reason" (chat prompt, Bedrock too). */
    static final class DenyMenu extends Menu {

        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15};

        private final ModerationModule module;
        private final AppealService appeals;
        private final Appeal appeal;
        private boolean used;

        DenyMenu(ModerationModule module, AppealService appeals, Appeal appeal) {
            this.module = module;
            this.appeals = appeals;
            this.appeal = appeal;
        }

        @Override
        public int rows() {
            return 3;
        }

        @Override
        public Component title() {
            return service.title(viewer, service.text(viewer, MODULE, "appeal.menu.queue-title"),
                    Component.text("#" + appeal.id()), service.text(viewer, MODULE, "appeal.menu.deny-title"));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        protected void build() {
            frame();
            TagResolver parts = appeals.parts(viewer, appeal);
            List<String> reasons = appeals.cfg().denyReasons();
            for (int i = 0; i < reasons.size() && i < SLOTS.length; i++) {
                String key = "appeal.deny-reasons." + reasons.get(i);
                String text = module.messages().has(key) ? module.service().text(viewer, key) : reasons.get(i);
                set(SLOTS[i], service.item(Material.RED_DYE, viewer, MODULE, "appeal.menu.deny-reason", parts,
                        Msg.p("deny_reason", text)), click -> deny(text));
            }
            set(16, service.item(Material.WRITABLE_BOOK, viewer, MODULE, "appeal.menu.deny-type", parts), click -> {
                if (used || !viewer.hasPermission(ModerationModule.PERM_APPEALS)) {
                    return;
                }
                service.prompt(viewer, MODULE, "appeal.deny-prompt", 200, this::deny, () -> { }, parts);
            });
            set(22, service.navItem("back", Material.ARROW, viewer, "frame.back"), click -> back());
        }

        private void deny(String text) {
            if (used || !viewer.hasPermission(ModerationModule.PERM_APPEALS)) {
                return;
            }
            used = true;
            appeals.deny(viewer, appeal, text).whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(), () -> {
                if (viewer.isOnline()) {
                    appeals.queue(viewer);
                }
            }));
        }
    }
}
