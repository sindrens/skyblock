package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
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

/** {@code /punish <player>} (category → reason → confirm) and {@code /history <player>} menus. */
final class PunishMenus {

    static final String MODULE = "moderation";

    /** The target of a punish/history menu with their history (loaded before the menu opens). */
    record Target(UUID uuid, String name, List<Punishment> history) {

        long count(Punishment.Type type) {
            return history.stream().filter(p -> p.type() == type).count();
        }

        long active(Punishment.Type type) {
            long now = System.currentTimeMillis();
            return history.stream().filter(p -> p.type() == type && p.active(now)).count();
        }

        /** Earlier (not pardoned) punishments with this template = offence number - 1. */
        int previous(String template) {
            return (int) history.stream().filter(p -> template.equals(p.template()) && !p.removed()).count();
        }
    }

    private PunishMenus() {
    }

    static ItemStack head(Target target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(target.uuid(), target.name())));
        return head;
    }

    /** Summary item (top middle): name, active ban/mute, counts. */
    static MenuButton summary(Menu menu, net.skybornmc.skyblock.common.menu.MenuService service, org.bukkit.entity.Player viewer,
                              Target target) {
        ItemStack item = service.item(head(target), service.text(viewer, MODULE, "menu.summary.name", Msg.p("player", target.name())),
                service.lines(viewer, MODULE, "menu.summary.lore",
                        Msg.number("bans", target.count(Punishment.Type.BAN)), Msg.number("mutes", target.count(Punishment.Type.MUTE)),
                        Msg.number("warns", target.count(Punishment.Type.WARN)), Msg.number("kicks", target.count(Punishment.Type.KICK)),
                        Msg.mm("banned", target.active(Punishment.Type.BAN) > 0 ? "<c:error>yes</c:error>" : "<c:success>no</c:success>"),
                        Msg.mm("muted", target.active(Punishment.Type.MUTE) > 0 ? "<c:error>yes</c:error>" : "<c:success>no</c:success>")));
        return MenuButton.display(item);
    }

    // ================================================================== /punish: categories

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
            set(4, summary(this, service, viewer, target));
            int i = 0;
            for (Templates.Category category : module.templates().categories()) {
                if (i >= SLOTS.length) {
                    break;
                }
                long reasons = category.templates().size();
                set(SLOTS[i++], service.item(category.icon(), viewer, MODULE, "menu.category",
                                Msg.p("category", category.name()), Msg.number("reasons", reasons)),
                        click -> openChild(new TemplateMenu(module, target, category)));
            }
            set(40, service.item(Material.WRITABLE_BOOK, viewer, MODULE, "menu.punish.history-button",
                    Msg.number("total", target.history().size())), click -> openChild(new HistoryMenu(target)));
        }
    }

    // ================================================================== /punish: reasons of a category

    static final class TemplateMenu extends PagedMenu<Templates.Template> {

        private final ModerationModule module;
        private final Target target;
        private final Templates.Category category;

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
                    Component.text(category.name()));
        }

        @Override
        protected void decorate() {
            set(4, summary(this, service, viewer, target));
        }

        @Override
        protected String emptyKey() {
            return "menu.empty-category";
        }

        @Override
        protected MenuButton entry(Templates.Template template) {
            int previous = target.previous(template.id());
            Templates.Step step = template.stepFor(previous);
            boolean allowed = module.canApply(viewer, step);
            List<Component> ladder = new ArrayList<>();
            for (int i = 0; i < template.ladder().size(); i++) {
                String key = i == Math.min(previous, template.ladder().size() - 1) ? "menu.ladder-current" : "menu.ladder-step";
                ladder.add(service.text(viewer, MODULE, key, Msg.number("n", i + 1), Msg.p("step", template.ladder().get(i).describe())));
            }
            List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, "menu.reason.lore",
                    Msg.number("offence", previous + 1), Msg.p("step", step.describe())));
            lore.addAll(ladder);
            lore.add(Component.empty());
            lore.add(service.text(viewer, MODULE, allowed ? "menu.reason.click" : "menu.reason.too-long",
                    Msg.p("max", module.service().describeMax(viewer))));
            ItemStack item = service.item(template.icon(), service.text(viewer, MODULE, "menu.reason.name",
                    Msg.p("reason", template.name())), lore);
            if (!allowed) {
                return MenuButton.display(item);
            }
            return MenuButton.of(item, click -> openChild(new ConfirmMenu(
                    p -> service.text(p, MODULE, "menu.confirm.title", Msg.p("player", target.name())),
                    item,
                    p -> service.lines(p, MODULE, "menu.confirm.details", Msg.p("player", target.name()),
                            Msg.p("reason", template.name()), Msg.p("step", step.describe()), Msg.number("offence", previous + 1)),
                    p -> module.punishWithTemplate(p, target, template, previous, false),
                    p -> { })));
        }
    }

    // ================================================================== /history

    static final class HistoryMenu extends PagedMenu<Punishment> {

        private final Target target;

        HistoryMenu(Target target) {
            this.target = target;
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
            set(4, summary(this, service, viewer, target));
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
                    Msg.p("date", PunishmentService.DATE.format(Instant.ofEpochMilli(p.created()))),
                    Msg.p("length", length), Msg.p("left", left),
                    Msg.p("status", plain("status." + status.name().toLowerCase(Locale.ROOT))),
                    Msg.p("template", p.template() == null ? "-" : p.template() + " #" + (p.ladderStep() == null ? 1 : p.ladderStep() + 1)),
                    Msg.p("scope", p.ipWide() ? plain("words.ip-wide") : plain("words.account"))));
            if (p.removed()) {
                lore.addAll(service.lines(viewer, MODULE, "menu.history.removed",
                        Msg.p("by", p.removedByName() == null ? "?" : p.removedByName()),
                        Msg.p("removed_reason", p.removedReason() == null ? "-" : p.removedReason()),
                        Msg.p("removed_date", PunishmentService.DATE.format(Instant.ofEpochMilli(p.removedAt())))));
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
