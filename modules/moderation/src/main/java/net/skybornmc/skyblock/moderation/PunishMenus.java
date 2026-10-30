package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.MenuClick;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /p <player>}: the single-screen punishment menu (spec §4), its pending-suggestion sub-menu, the "More reasons"
 * category menus and {@code /history}. Bedrock-safe: every action is a plain left click; nothing needs shift or number
 * keys, and everything is also reachable with {@code /p <player> <reason>} and {@code /warns}.
 */
final class PunishMenus {

    static final String MODULE = "moderation";

    /** The target of a punish/history menu with their history and warn-rule suggestions (loaded before opening). */
    record Target(UUID uuid, String name, List<Punishment> history, List<Suggestion> suggestions) {

        Target(UUID uuid, String name, List<Punishment> history) {
            this(uuid, name, history, List.of());
        }

        long count(Punishment.Type type) {
            return history.stream().filter(p -> p.type() == type).count();
        }

        long active(Punishment.Type type) {
            long now = System.currentTimeMillis();
            return history.stream().filter(p -> p.type() == type && p.active(now)).count();
        }

        /** The longest active punishment of this type (perm wins), or {@code null}. */
        Punishment longestActive(Punishment.Type type) {
            long now = System.currentTimeMillis();
            return PunishmentStore.longest(history.stream().filter(p -> p.type() == type && p.active(now)).toList());
        }

        /**
         * Earlier (not pardoned, not undone) punishments with this template created after {@code cutoff} = offence
         * number - 1 (spec 12.3: older offences no longer count; {@code Long.MIN_VALUE} = all count).
         */
        int previous(String template, long cutoff) {
            return Offences.counting(history, template, cutoff);
        }

        /** Not pardoned punishments with this template that are too old to count ("1 older offence no longer counts"). */
        int expiredOffences(String template, long cutoff) {
            return Offences.expired(history, template, cutoff);
        }

        /** The newest not-pardoned punishment with this template (duplicate check preview, spec 12.2), or {@code null}. */
        Punishment recent(String template) {
            Punishment best = null;
            for (Punishment p : history) {
                if (template.equalsIgnoreCase(String.valueOf(p.template())) && !p.removed()
                        && (best == null || p.created() > best.created() || p.created() == best.created() && p.id() > best.id())) {
                    best = p;
                }
            }
            return best;
        }
    }

    private PunishMenus() {
    }

    static ItemStack head(Target target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(target.uuid(), target.name())));
        return head;
    }

    private static String left(Punishment p) {
        long now = System.currentTimeMillis();
        return p.expires() == null ? null : Format.duration(Duration.ofMillis(Math.max(1000, p.expires() - now)));
    }

    /** Summary item (head): name, counts, active ban/mute with time left, and a waiting suggestion. */
    static ItemStack summaryItem(ModerationModule module, net.skybornmc.skyblock.common.menu.MenuService service,
                                 org.bukkit.entity.Player viewer, Target target) {
        return summaryItem(module, service, viewer, target, null);
    }

    /**
     * Same, plus "Last action: ... (click to undo)" for {@code undoable} (spec 12.1, the Bedrock path: clickable chat is
     * unreliable there). Only the {@code /p} screen passes it, because only there the head is clickable.
     */
    static ItemStack summaryItem(ModerationModule module, net.skybornmc.skyblock.common.menu.MenuService service,
                                 org.bukkit.entity.Player viewer, Target target, Punishment undoable) {
        Punishment ban = target.longestActive(Punishment.Type.BAN);
        Punishment mute = target.longestActive(Punishment.Type.MUTE);
        String permanent = module.service().text(viewer, "words.permanent");
        List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, "menu.summary.lore",
                Msg.number("bans", target.count(Punishment.Type.BAN)), Msg.number("mutes", target.count(Punishment.Type.MUTE)),
                Msg.number("warns", target.count(Punishment.Type.WARN)), Msg.number("kicks", target.count(Punishment.Type.KICK)),
                Msg.mm("banned", ban != null ? "<c:error>" + module.service().text(viewer, "words.yes") + "</c:error>"
                        : "<c:success>" + module.service().text(viewer, "words.no") + "</c:success>"),
                Msg.mm("muted", mute != null ? "<c:error>" + module.service().text(viewer, "words.yes") + "</c:error>"
                        : "<c:success>" + module.service().text(viewer, "words.no") + "</c:success>")));
        if (ban != null) {
            lore.addAll(service.lines(viewer, MODULE, "punish.summary.ban-left", Msg.p("left", left(ban) == null ? permanent : left(ban)),
                    Msg.p("reason", ban.reason())));
        }
        if (mute != null) {
            lore.addAll(service.lines(viewer, MODULE, "punish.summary.mute-left", Msg.p("left", left(mute) == null ? permanent : left(mute)),
                    Msg.p("reason", mute.reason())));
        }
        WarnRules.Counter counter = WarnRules.counter(target.history(), target.suggestions(),
                module.templates().config().warnRules(), System.currentTimeMillis());
        if (undoable != null) {
            long left = Math.max(1, (undoable.created() + module.service().extras().undoWindow().toMillis()
                    - System.currentTimeMillis()) / 1000);
            lore.addAll(service.lines(viewer, MODULE, "punish.summary.undo",
                    Msg.p("type", module.service().text(viewer, "types." + undoable.type().name().toLowerCase(Locale.ROOT))),
                    Msg.p("reason", undoable.reason()), Msg.p("id", undoable.id()),
                    Msg.p("left", PunishConfig.text(Duration.ofSeconds(left)))));
        } else if (counter.pending() != null) {
            lore.addAll(service.lines(viewer, MODULE, "punish.summary.pending",
                    Msg.p("action", module.service().stepText(viewer, counter.pending().step()))));
        }
        return service.item(head(target), service.text(viewer, MODULE, "menu.summary.name", Msg.p("player", target.name())), lore);
    }

    /** Summary item (top middle) for the history and category menus. */
    static MenuButton summary(Menu menu, net.skybornmc.skyblock.common.menu.MenuService service, org.bukkit.entity.Player viewer,
                              Target target, ModerationModule module) {
        return MenuButton.display(summaryItem(module, service, viewer, target));
    }

    // ================================================================== /p: single screen

    static final class PunishMenu extends Menu {

        private final ModerationModule module;
        private Target target;
        /** An apply is running: further clicks wait (the debounce already stops double clicks). */
        private boolean busy;

        PunishMenu(ModerationModule module, Target target) {
            this.module = module;
            this.target = target;
        }

        @Override
        public int rows() {
            return 6;
        }

        @Override
        public Component title() {
            return service.title(viewer, service.text(viewer, MODULE, "punish.title"), Component.text(target.name()));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "punish.info";
        }

        @Override
        public String helpTopic() {
            return "staff";
        }

        private PunishConfig.MenuLayout layout() {
            return module.templates().config().menu();
        }

        @Override
        protected void build() {
            PunishConfig.MenuLayout l = layout();
            ItemStack filler = service.filler(l.filler());
            for (int slot = 0; slot < rows() * 9; slot++) {
                set(slot, filler);
            }
            ItemStack divider = service.filler(l.divider());
            for (int slot : l.dividerSlots()) {
                set(slot, divider);
            }
            WarnRules.Settings rules = module.templates().config().warnRules();
            WarnRules.Counter counter = WarnRules.counter(target.history(), target.suggestions(), rules, System.currentTimeMillis());
            Punishment undoable = module.service().undoable(viewer, target.history());
            ItemStack summary = summaryItem(module, service, viewer, target, undoable);
            if (undoable != null) {
                // spec 12.1: the head is the Bedrock-safe [Undo] while the window is open (pending stays on the counter)
                set(l.summarySlot(), MenuButton.of(summary, guarded(() -> module.service().undo(viewer, undoable.id()))));
            } else {
                set(l.summarySlot(), counter.pending() != null
                        ? MenuButton.of(summary, click -> openSuggestion(counter.pending()))
                        : MenuButton.display(summary));
            }
            set(l.warnCounterSlot(), counterButton(l, rules, counter));
            l.reasonSlots().forEach((id, slot) -> module.templates().template(id)
                    .ifPresent(t -> set(slot, reasonButton(t, l.quarterOf().get(id)))));
            for (PunishConfig.WarnButton w : l.warns()) {
                set(w.slot(), warnButton(w));
            }
            for (PunishConfig.Button b : l.buttons().values()) {
                set(b.slot(), button(b, l));
            }
        }

        // ---------------------------------------------------------- clicks

        /** Re-checks the permission on every click, runs {@code action}, then reloads the target and refreshes. */
        private java.util.function.Consumer<MenuClick> guarded(java.util.function.Supplier<CompletableFuture<?>> action) {
            return click -> {
                if (!viewer.hasPermission(ModerationModule.PERM_GUI)) {
                    viewer.closeInventory();
                    module.messages().send(viewer, "error.no-permission-menu");
                    return;
                }
                if (busy) {
                    return;
                }
                busy = true;
                CompletableFuture<?> f;
                try {
                    f = action.get();
                } catch (RuntimeException e) {
                    busy = false;
                    throw e;
                }
                f.whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(), this::reload));
            };
        }

        /** Reloads history + suggestions and refreshes in place (spec 4.3: the menu stays open after applying). */
        private void reload() {
            if (!layout().keepOpen()) {
                busy = false;
                if (isOpen()) {
                    viewer.closeInventory();
                }
                return;
            }
            module.target(target.uuid(), target.name()).whenComplete((t, e) -> Bukkit.getScheduler().runTask(service.plugin(), () -> {
                busy = false;
                if (t != null) {
                    target = t;
                }
                refresh();
            }));
        }

        private void openSuggestion(Suggestion s) {
            if (!viewer.hasPermission(ModerationModule.PERM_GUI)) {
                viewer.closeInventory();
                return;
            }
            openChild(new SuggestionMenu(module, target, s));
        }

        // ---------------------------------------------------------- items

        private MenuButton counterButton(PunishConfig.MenuLayout l, WarnRules.Settings rules, WarnRules.Counter counter) {
            List<Component> lore = new ArrayList<>();
            if (!rules.enabled()) {
                lore.addAll(service.lines(viewer, MODULE, "punish.counter.off"));
            } else {
                if (counter.next() != null) {
                    lore.addAll(service.lines(viewer, MODULE, "punish.counter.next", Msg.p("at", counter.next().at()),
                            Msg.p("action", module.service().stepText(viewer, counter.next().action()))));
                } else {
                    lore.addAll(service.lines(viewer, MODULE, "punish.counter.no-next"));
                }
                if (counter.repeatUntil() != null) {
                    lore.addAll(service.lines(viewer, MODULE, "punish.counter.repeat", Msg.p("until",
                            ModerationModule.when(counter.repeatUntil())),
                            Msg.p("action", module.service().stepText(viewer, rules.repeatAction()))));
                }
                if (!counter.expiries().isEmpty()) {
                    lore.addAll(service.lines(viewer, MODULE, "punish.counter.expiries-header"));
                    for (long at : counter.expiries()) {
                        lore.addAll(service.lines(viewer, MODULE, "punish.counter.expiry", Msg.p("date", ModerationModule.when(at))));
                    }
                }
                lore.addAll(service.lines(viewer, MODULE, "punish.counter.mode." + rules.mode().name().toLowerCase(Locale.ROOT)));
                if (counter.pending() != null) {
                    lore.add(Component.empty());
                    lore.addAll(service.lines(viewer, MODULE, "punish.counter.pending",
                            Msg.p("action", module.service().stepText(viewer, counter.pending().step()))));
                }
            }
            ItemStack item = service.item(l.warnCounterIcon(), service.text(viewer, MODULE, "punish.counter.name",
                    Msg.p("count", counter.count()), Msg.p("next", ModerationModule.counterLimit(counter, rules))), lore);
            Suggestion pending = counter.pending();
            return pending == null ? MenuButton.display(item) : MenuButton.of(item, click -> openSuggestion(pending));
        }

        private MenuButton reasonButton(Templates.Template t, String quarter) {
            long cutoff = t.cutoff(System.currentTimeMillis());
            int previous = target.previous(t.id(), cutoff);
            int older = target.expiredOffences(t.id(), cutoff);
            int index = t.stepIndex(previous);
            Templates.Step step = t.ladder().get(index);
            Templates.Step after = t.ladder().get(Math.min(index + 1, t.ladder().size() - 1));
            boolean repeats = index + 1 >= t.ladder().size();
            PunishDecision.Verdict preview = module.service().preview(viewer, target.uuid(), target.name(), step, false,
                    target.longestActive(Punishment.Type.MUTE), target.longestActive(Punishment.Type.BAN), t.id(),
                    target.recent(t.id()));
            List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, "punish.reason.lore",
                    Msg.number("offence", previous + 1), Msg.p("step", module.service().stepText(viewer, step))));
            if (older > 0) {
                lore.addAll(service.lines(viewer, MODULE, older == 1 ? "punish.reason.older-one" : "punish.reason.older",
                        Msg.number("older", older)));
            }
            lore.addAll(service.lines(viewer, MODULE, repeats ? "punish.reason.after-repeat" : "punish.reason.after",
                    Msg.p("step", module.service().stepText(viewer, after))));
            lore.add(Component.empty());
            lore.addAll(verdictLines(preview));
            ItemStack item = service.item(t.icon(), service.text(viewer, MODULE, "punish.reason.name",
                    Msg.p("reason", module.reasonName(viewer, t)), Msg.p("quarter", quarter == null ? "" : quarter)), lore);
            if (blocked(preview)) {
                return MenuButton.display(item);
            }
            return MenuButton.of(item, guardedConfirm(item, module.reasonName(viewer, t), step, previous,
                    () -> module.punishWithTemplate(viewer, target, t, false, false)));
        }

        private MenuButton warnButton(PunishConfig.WarnButton w) {
            Templates.Step warn = new Templates.Step(Punishment.Type.WARN, null, false);
            PunishDecision.Verdict preview = module.service().preview(viewer, target.uuid(), target.name(), warn, false, null, null,
                    w.id(), target.recent(w.id()));
            List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, "punish.warn-button.lore",
                    Msg.p("reason", module.warnButtonText(viewer, w, "reason"))));
            lore.add(Component.empty());
            lore.addAll(verdictLines(preview));
            ItemStack item = service.item(w.icon(), service.text(viewer, MODULE, "punish.warn-button.name",
                    Msg.p("name", module.warnButtonText(viewer, w, "name"))), lore);
            if (blocked(preview)) {
                return MenuButton.display(item);
            }
            return MenuButton.of(item, guardedConfirm(item, module.warnButtonText(viewer, w, "name"), warn,
                    (int) target.count(Punishment.Type.WARN),
                    () -> module.punishWithWarnButton(viewer, target, w, false)));
        }

        /** Click hint, or why it can't be applied (from the same pipeline the click uses). */
        private List<Component> verdictLines(PunishDecision.Verdict v) {
            return PunishMenus.verdictLines(service, module, viewer, v);
        }

        /** Applies at once (owner decision), or through the confirm menu when {@code menu.confirm: true}. */
        private java.util.function.Consumer<MenuClick> guardedConfirm(ItemStack shown, String reason, Templates.Step step,
                                                                     int previous,
                                                                     java.util.function.Supplier<CompletableFuture<?>> action) {
            if (!layout().confirm()) {
                return guarded(action);
            }
            return click -> {
                if (!viewer.hasPermission(ModerationModule.PERM_GUI)) {
                    viewer.closeInventory();
                    return;
                }
                openChild(new ConfirmMenu(
                        p -> service.text(p, MODULE, "menu.confirm.title", Msg.p("player", target.name())),
                        shown,
                        p -> service.lines(p, MODULE, "menu.confirm.details", Msg.p("player", target.name()),
                                Msg.p("reason", reason), Msg.p("step", module.service().stepText(p, step)),
                                Msg.number("offence", previous + 1)),
                        p -> {
                            if (p.hasPermission(ModerationModule.PERM_GUI)) {
                                action.get().whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(),
                                        () -> module.openPunishMenu(p, target.name())));
                            }
                        },
                        p -> { }));
            };
        }

        private MenuButton button(PunishConfig.Button b, PunishConfig.MenuLayout l) {
            String key = "punish.buttons." + b.id();
            return switch (b.id()) {
                case "kick" -> {
                    Templates.Step kick = new Templates.Step(Punishment.Type.KICK, null, false);
                    PunishDecision.Verdict v = module.service().preview(viewer, target.uuid(), target.name(), kick, false, null, null);
                    List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, key + ".lore"));
                    lore.addAll(verdictLines(v));
                    ItemStack item = service.item(icon(b, Material.LEATHER_BOOTS), service.text(viewer, MODULE, key + ".name"), lore);
                    yield v.outcome() == PunishDecision.Outcome.BLOCKED ? MenuButton.display(item)
                            : MenuButton.of(item, guarded(() -> module.kickFromMenu(viewer, target)));
                }
                case "history" -> MenuButton.of(service.item(icon(b, Material.WRITABLE_BOOK), viewer, MODULE, key,
                        Msg.number("total", target.history().size())), click -> {
                            if (viewer.hasPermission(ModerationModule.PERM_HISTORY)) {
                                openChild(new HistoryMenu(target, module));
                            } else {
                                module.messages().send(viewer, "error.no-permission-menu");
                            }
                        });
                case "close" -> MenuButton.of(service.item(icon(b, Material.BARRIER), viewer, MODULE, key),
                        click -> viewer.closeInventory());
                case "more-reasons" -> MenuButton.of(service.item(icon(b, Material.BOOKSHELF), viewer, MODULE, key,
                        Msg.number("reasons", module.templates().templateIds().size()),
                        Msg.number("unplaced", module.templates().templateIds().stream()
                                .filter(id -> !l.reasonSlots().containsKey(id)).count())),
                        click -> openChild(new CategoryMenu(module, target)));
                // Sanctions (P7), bulk (P5) and dry run (P8): placeholders until their milestone.
                default -> MenuButton.display(service.item(icon(b, l.unavailableIcon()), viewer, MODULE, key));
            };
        }

        private static Material icon(PunishConfig.Button b, Material fallback) {
            return b.icon() == null ? fallback : b.icon();
        }
    }

    /**
     * BLOCKED for good (display only). A duplicate block stays clickable: "click again within 5s to apply anyway"
     * (spec 12.2); the second click is confirmed by the service.
     */
    static boolean blocked(PunishDecision.Verdict v) {
        return v.outcome() == PunishDecision.Outcome.BLOCKED && !"duplicate".equals(v.stage());
    }

    /** Lore for a verdict: click hint, or why it can't be applied (every placeholder a stage may give). */
    static List<Component> verdictLines(net.skybornmc.skyblock.common.menu.MenuService service, ModerationModule module,
                                        org.bukkit.entity.Player viewer, PunishDecision.Verdict v) {
        if (v.outcome() == PunishDecision.Outcome.APPLY) {
            return service.lines(viewer, MODULE, "punish.click");
        }
        return service.lines(viewer, MODULE, "punish.blocked." + v.stage(),
                Msg.p("max", v.args().getOrDefault("max", "?")), Msg.p("left", v.args().getOrDefault("left", "?")),
                Msg.p("staff", v.args().getOrDefault("staff", "?")), Msg.p("ago", v.args().getOrDefault("ago", "?")),
                Msg.p("confirm", PunishConfig.text(module.service().extras().duplicateConfirm())));
    }

    // ================================================================== pending suggestion: Apply / Dismiss

    /** 3 rows: Apply (left), what is suggested (middle), Dismiss (right), Back. Plain clicks only (Bedrock). */
    static final class SuggestionMenu extends Menu {

        private final ModerationModule module;
        private final Target target;
        private final Suggestion suggestion;
        private boolean used;

        SuggestionMenu(ModerationModule module, Target target, Suggestion suggestion) {
            this.module = module;
            this.target = target;
            this.suggestion = suggestion;
        }

        @Override
        public int rows() {
            return 3;
        }

        @Override
        public Component title() {
            return service.title(viewer, service.text(viewer, MODULE, "punish.title"), Component.text(target.name()),
                    service.text(viewer, MODULE, "suggest.menu.title"));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        protected void build() {
            frame();
            net.kyori.adventure.text.minimessage.tag.resolver.TagResolver parts = module.service().suggestionParts(viewer, suggestion);
            set(13, service.item(Material.PAPER, viewer, MODULE, "suggest.menu.info", parts));
            set(11, service.item(Material.LIME_CONCRETE, viewer, MODULE, "suggest.menu.apply", parts), click -> handle(true));
            set(15, service.item(Material.RED_CONCRETE, viewer, MODULE, "suggest.menu.dismiss", parts), click -> handle(false));
            set(22, service.navItem("back", Material.ARROW, viewer, "frame.back"), click -> back());
        }

        private void handle(boolean apply) {
            if (used || !viewer.hasPermission(ModerationModule.PERM_GUI)) {
                return;
            }
            used = true;
            CompletableFuture<PunishmentStore.HandleResult> f = apply
                    ? module.service().applySuggestion(viewer, suggestion.id())
                    : module.service().dismissSuggestion(viewer, suggestion.id());
            f.whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(),
                    () -> module.openPunishMenu(viewer, target.name())));
        }
    }

    // ================================================================== More reasons: categories

    static final class CategoryMenu extends Menu {

        private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

        private final ModerationModule module;
        private final Target target;

        CategoryMenu(ModerationModule module, Target target) {
            this.module = module;
            this.target = target;
        }

        @Override
        public int rows() {
            return 6;
        }

        @Override
        public Component title() {
            return service.title(viewer, service.text(viewer, MODULE, "menu.punish.title"), Component.text(target.name()));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "menu.punish.info";
        }

        @Override
        public String helpTopic() {
            return "staff";
        }

        @Override
        protected void build() {
            frame();
            set(4, summary(this, service, viewer, target, module));
            int i = 0;
            for (Templates.Category category : module.templates().categories()) {
                if (i >= SLOTS.length) {
                    break;
                }
                long reasons = category.templates().size();
                set(SLOTS[i++], service.item(category.icon(), viewer, MODULE, "menu.category",
                                Msg.p("category", module.categoryName(viewer, category)), Msg.number("reasons", reasons)),
                        click -> openChild(new TemplateMenu(module, target, category)));
            }
            set(40, service.item(Material.WRITABLE_BOOK, viewer, MODULE, "menu.punish.history-button",
                    Msg.number("total", target.history().size())), click -> openChild(new HistoryMenu(target, module)));
        }
    }

    // ================================================================== More reasons: reasons of a category

    static final class TemplateMenu extends PagedMenu<Templates.Template> {

        private final ModerationModule module;
        private final Target target;
        private final Templates.Category category;
        private boolean used;

        TemplateMenu(ModerationModule module, Target target, Templates.Category category) {
            this.module = module;
            this.target = target;
            this.category = category;
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "menu.punish.info";
        }

        @Override
        protected List<Templates.Template> entries() {
            return category.templates();
        }

        @Override
        protected List<Component> titleCrumbs() {
            return List.of(service.text(viewer, MODULE, "menu.punish.title"), Component.text(target.name()),
                    Component.text(module.categoryName(viewer, category)));
        }

        @Override
        protected void decorate() {
            set(4, summary(this, service, viewer, target, module));
        }

        @Override
        protected String emptyKey() {
            return "menu.empty-category";
        }

        @Override
        protected MenuButton entry(Templates.Template template) {
            long cutoff = template.cutoff(System.currentTimeMillis());
            int previous = target.previous(template.id(), cutoff);
            int older = target.expiredOffences(template.id(), cutoff);
            Templates.Step step = template.stepFor(previous);
            PunishDecision.Verdict preview = module.service().preview(viewer, target.uuid(), target.name(), step, false,
                    target.longestActive(Punishment.Type.MUTE), target.longestActive(Punishment.Type.BAN), template.id(),
                    target.recent(template.id()));
            List<Component> ladder = new ArrayList<>();
            for (int i = 0; i < template.ladder().size(); i++) {
                String key = i == template.stepIndex(previous) ? "menu.ladder-current" : "menu.ladder-step";
                ladder.add(service.text(viewer, MODULE, key, Msg.number("n", i + 1),
                        Msg.p("step", module.service().stepText(viewer, template.ladder().get(i)))));
            }
            List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, "menu.reason.lore",
                    Msg.number("offence", previous + 1), Msg.p("step", module.service().stepText(viewer, step))));
            lore.addAll(ladder);
            if (older > 0) {
                lore.addAll(service.lines(viewer, MODULE, older == 1 ? "punish.reason.older-one" : "punish.reason.older",
                        Msg.number("older", older)));
            }
            lore.add(Component.empty());
            boolean blocked = blocked(preview);
            lore.addAll(verdictLines(service, module, viewer, preview));
            ItemStack item = service.item(template.icon(), service.text(viewer, MODULE, "menu.reason.name",
                    Msg.p("reason", module.reasonName(viewer, template))), lore);
            if (blocked) {
                return MenuButton.display(item);
            }
            Runnable apply = () -> module.punishWithTemplate(viewer, target, template, false, false)
                    .whenComplete((r, e) -> Bukkit.getScheduler().runTask(service.plugin(),
                            () -> module.openPunishMenu(viewer, target.name())));
            if (!module.templates().config().menu().confirm()) {
                return MenuButton.of(item, click -> {
                    if (!used && viewer.hasPermission(ModerationModule.PERM_GUI)) {
                        used = true;
                        apply.run();
                    }
                });
            }
            return MenuButton.of(item, click -> openChild(new ConfirmMenu(
                    p -> service.text(p, MODULE, "menu.confirm.title", Msg.p("player", target.name())),
                    item,
                    p -> service.lines(p, MODULE, "menu.confirm.details", Msg.p("player", target.name()),
                            Msg.p("reason", module.reasonName(p, template)), Msg.p("step", module.service().stepText(p, step)),
                            Msg.number("offence", previous + 1)),
                    p -> {
                        if (p.hasPermission(ModerationModule.PERM_GUI)) {
                            apply.run();
                        }
                    },
                    p -> { })));
        }
    }

    // ================================================================== /history

    static final class HistoryMenu extends PagedMenu<Punishment> {

        private final Target target;
        private final ModerationModule module;

        HistoryMenu(Target target, ModerationModule module) {
            this.target = target;
            this.module = module;
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "menu.history.info";
        }

        @Override
        protected List<Punishment> entries() {
            return target.history();
        }

        @Override
        protected List<Component> titleCrumbs() {
            return List.of(service.text(viewer, MODULE, "menu.history.title"), Component.text(target.name()));
        }

        @Override
        protected void decorate() {
            set(4, summary(this, service, viewer, target, module));
        }

        @Override
        protected String emptyKey() {
            return "menu.history.empty";
        }

        @Override
        protected MenuButton entry(Punishment p) {
            long now = System.currentTimeMillis();
            Punishment.Status status = p.status(now);
            Material icon = switch (p.type()) {
                case BAN -> status == Punishment.Status.ACTIVE ? Material.RED_CONCRETE : Material.RED_TERRACOTTA;
                case MUTE -> status == Punishment.Status.ACTIVE ? Material.ORANGE_CONCRETE : Material.ORANGE_TERRACOTTA;
                case WARN -> status == Punishment.Status.ACTIVE ? Material.YELLOW_CONCRETE : Material.YELLOW_TERRACOTTA;
                case KICK -> Material.LEATHER_BOOTS;
            };
            String type = service.messages().raw(MODULE, "types." + p.type().name().toLowerCase(Locale.ROOT));
            String length = p.type() == Punishment.Type.WARN || p.type() == Punishment.Type.KICK ? "-"
                    : p.expires() == null ? plain("words.permanent") : Format.duration(Duration.ofMillis(p.expires() - p.created()));
            String left = p.expires() == null || status != Punishment.Status.ACTIVE ? "-"
                    : Format.duration(Duration.ofMillis(Math.max(1000, p.expires() - now)));
            List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, "menu.history.entry",
                    Msg.p("id", p.id()), Msg.p("reason", p.reason()), Msg.p("staff", p.issuerName()),
                    Msg.p("date", PunishmentService.DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(p.created()))),
                    Msg.p("length", length), Msg.p("left", left),
                    Msg.p("status", plain("status." + status.name().toLowerCase(Locale.ROOT))),
                    Msg.p("template", p.template() == null ? "-" : p.template() + " #" + (p.ladderStep() == null ? 1 : p.ladderStep() + 1)),
                    Msg.p("scope", p.ipWide() ? plain("words.ip-wide") : plain("words.account"))));
            if (p.removed()) {
                lore.addAll(service.lines(viewer, MODULE, "menu.history.removed",
                        Msg.p("by", p.removedByName() == null ? "?" : p.removedByName()),
                        Msg.p("removed_reason", p.removedReason() == null ? "-" : p.removedReason()),
                        Msg.p("removed_date", PunishmentService.DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(p.removedAt())))));
            }
            return MenuButton.display(service.item(icon, service.text(viewer, MODULE, "menu.history.name",
                    Msg.mm("type", type == null ? p.type().name() : type), Msg.p("id", p.id()),
                    Msg.p("status", plain("status." + status.name().toLowerCase(Locale.ROOT)))), lore));
        }

        private String plain(String key) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(service.text(viewer, MODULE, key));
        }
    }
}
