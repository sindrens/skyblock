package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk punish menus (spec 13.8), all Bedrock-safe (plain left clicks only): pick players (multi-select), pick the reason,
 * then the common confirm with the count ({@link BulkPunish#confirm}); Bedrock players get a result menu with
 * [Undo all] afterwards.
 */
final class BulkMenus {

    private static final String MODULE = PunishMenus.MODULE;
    static final int SLOT_CLEAR = 47;
    static final int SLOT_NEXT_STEP = 51;

    private BulkMenus() {
    }

    /** A selectable player: online players, plus the player whose /p the menu was opened from (may be offline). */
    record Pick(UUID uuid, String name) {
    }

    /** Step 1: select up to {@code bulk.max} players. Click a head to select / unselect it. */
    static final class PickMenu extends PagedMenu<Pick> {

        private final ModerationModule module;
        private final BulkPunish bulk;
        private final Pick preselected;
        private final Map<UUID, String> names = new LinkedHashMap<>();
        private final LinkedHashSet<UUID> selected = new LinkedHashSet<>();

        PickMenu(ModerationModule module, BulkPunish bulk, Pick preselected) {
            this.module = module;
            this.bulk = bulk;
            this.preselected = preselected;
            if (preselected != null) {
                selected.add(preselected.uuid());
                names.put(preselected.uuid(), preselected.name());
            }
        }

        private int max() {
            return module.templates().config().extras().bulk().max();
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "bulk.pick.info";
        }

        @Override
        public String helpTopic() {
            return "staff";
        }

        @Override
        protected List<Component> titleCrumbs() {
            return List.of(service.text(viewer, MODULE, "bulk.title"), service.text(viewer, MODULE, "bulk.pick.title"));
        }

        @Override
        protected String emptyKey() {
            return "bulk.pick.empty";
        }

        @Override
        protected List<Pick> entries() {
            List<Pick> out = new ArrayList<>();
            if (preselected != null) {
                out.add(preselected);
            }
            Bukkit.getOnlinePlayers().stream().map(p -> (Player) p)
                    .filter(p -> !p.equals(viewer) && viewer.canSee(p) && (preselected == null || !p.getUniqueId().equals(preselected.uuid())))
                    .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(p -> out.add(new Pick(p.getUniqueId(), p.getName())));
            return out;
        }

        @Override
        protected void decorate() {
            List<Component> lore = new ArrayList<>(service.lines(viewer, MODULE, "bulk.pick.summary.lore",
                    Msg.number("count", selected.size()), Msg.number("max", max())));
            for (UUID id : selected) {
                lore.addAll(service.lines(viewer, MODULE, "bulk.pick.summary.line", Msg.p("player", names.getOrDefault(id, "?"))));
            }
            set(4, MenuButton.display(service.item(Material.CHEST_MINECART, service.text(viewer, MODULE, "bulk.pick.summary.name",
                    Msg.number("count", selected.size()), Msg.number("max", max())), lore)));
            set(SLOT_CLEAR, service.item(Material.BUCKET, viewer, MODULE, "bulk.pick.clear"), click -> {
                selected.clear();
                refresh();
            });
            if (selected.isEmpty()) {
                set(SLOT_NEXT_STEP, MenuButton.display(service.item(Material.GRAY_DYE, viewer, MODULE, "bulk.pick.next-none")));
            } else {
                set(SLOT_NEXT_STEP, service.item(Material.LIME_CONCRETE, viewer, MODULE, "bulk.pick.next",
                        Msg.number("count", selected.size())), click -> {
                            if (!viewer.hasPermission(ModerationModule.PERM_BULK)) {
                                viewer.closeInventory();
                                module.messages().send(viewer, "error.no-permission-menu");
                                return;
                            }
                            List<String> chosen = selected.stream().map(id -> names.getOrDefault(id, id.toString())).toList();
                            openChild(new ReasonMenu(module, bulk, chosen));
                        });
            }
        }

        @Override
        protected MenuButton entry(Pick pick) {
            names.putIfAbsent(pick.uuid(), pick.name());
            boolean on = selected.contains(pick.uuid());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(pick.uuid(), pick.name())));
            ItemStack item = service.item(head, service.text(viewer, MODULE, on ? "bulk.pick.entry.selected" : "bulk.pick.entry.name",
                    Msg.p("player", pick.name())), service.lines(viewer, MODULE, on ? "bulk.pick.entry.lore-selected"
                    : "bulk.pick.entry.lore", Msg.p("player", pick.name())));
            return MenuButton.of(item, click -> {
                if (!BulkSelection.toggle(selected, pick.uuid(), max())) {
                    module.messages().send(viewer, "bulk.full", Msg.number("max", max()));
                    service.sound(viewer, "error");
                    return;
                }
                refresh();
            });
        }
    }

    /** Step 2: the reason (every reason of punishments.yml, then the warn buttons). A click shows the confirm. */
    static final class ReasonMenu extends PagedMenu<String> {

        private final ModerationModule module;
        private final BulkPunish bulk;
        private final List<String> names;
        private boolean busy;

        ReasonMenu(ModerationModule module, BulkPunish bulk, List<String> names) {
            this.module = module;
            this.bulk = bulk;
            this.names = List.copyOf(names);
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        public String infoKey() {
            return "bulk.reason.info";
        }

        @Override
        protected List<Component> titleCrumbs() {
            return List.of(service.text(viewer, MODULE, "bulk.title"), service.text(viewer, MODULE, "bulk.reason.title"));
        }

        @Override
        protected List<String> entries() {
            return module.templates().reasonIds();
        }

        @Override
        protected MenuButton entry(String id) {
            var template = module.templates().template(id);
            var button = template.isPresent() ? java.util.Optional.<PunishConfig.WarnButton>empty() : module.templates().warnButton(id);
            if (template.isEmpty() && button.isEmpty()) {
                return MenuButton.display(new ItemStack(Material.BARRIER));
            }
            Material icon = template.map(Templates.Template::icon).orElseGet(() -> button.get().icon());
            String name = template.map(t -> module.reasonName(viewer, t)).orElseGet(() -> module.warnButtonText(viewer, button.get(), "name"));
            String ladder = template.map(t -> String.join(", ", t.ladder().stream().map(s -> module.service().stepText(viewer, s)).toList()))
                    .orElseGet(() -> module.service().stepText(viewer, new Templates.Step(Punishment.Type.WARN, null, false,
                            button.get().weight())));
            ItemStack item = service.item(icon, service.text(viewer, MODULE, "bulk.reason.name", Msg.p("reason", name)),
                    service.lines(viewer, MODULE, "bulk.reason.lore", Msg.p("ladder", ladder), Msg.number("count", names.size())));
            return MenuButton.of(item, click -> {
                if (busy || !viewer.hasPermission(ModerationModule.PERM_BULK)) {
                    return;
                }
                busy = true;
                bulk.plan(viewer, id, names).whenComplete((plan, e) -> Bukkit.getScheduler().runTask(service.plugin(), () -> {
                    busy = false;
                    if (plan == null || !viewer.isOnline()) {
                        module.messages().send(viewer, "error.failed");
                        return;
                    }
                    bulk.confirm(viewer, plan, this);
                }));
            });
        }
    }

    /** Bedrock: what the bulk action did, with [Undo all] while the undo window is open. 3 rows, plain clicks. */
    static final class ResultMenu extends Menu {

        private final ModerationModule module;
        private final BulkPunish bulk;
        private final BulkPunish.Plan plan;
        private final List<Punishment> applied;
        private final BulkSelection.Summary summary;
        private final long created = System.currentTimeMillis();

        ResultMenu(ModerationModule module, BulkPunish bulk, BulkPunish.Plan plan, List<Punishment> applied,
                   BulkSelection.Summary summary) {
            this.module = module;
            this.bulk = bulk;
            this.plan = plan;
            this.applied = List.copyOf(applied);
            this.summary = summary;
        }

        @Override
        public int rows() {
            return 3;
        }

        @Override
        public Component title() {
            return service.title(viewer, service.text(viewer, MODULE, "bulk.title"), service.text(viewer, MODULE, "bulk.result.title"));
        }

        @Override
        public String messageModule() {
            return MODULE;
        }

        @Override
        protected void build() {
            frame();
            set(13, MenuButton.display(service.item(Material.PAPER, viewer, MODULE, "bulk.result.info",
                    Msg.p("reason", plan.reasonName()), Msg.number("apply", summary.apply()),
                    Msg.number("request", summary.request()), Msg.number("skip", summary.skip()),
                    Msg.p("players", PunishEmbeds.players(applied, true)))));
            long window = module.service().extras().undoWindow().toMillis();
            if (System.currentTimeMillis() - created < window) {
                set(11, service.item(Material.RED_CONCRETE, viewer, MODULE, "bulk.result.undo", Msg.number("count", applied.size()),
                        Msg.p("window", PunishConfig.text(module.service().extras().undoWindow()))), click -> {
                            viewer.closeInventory();
                            bulk.undoAll(viewer, plan, applied);
                        });
            }
            set(15, service.item(Material.LIME_CONCRETE, viewer, MODULE, "bulk.result.close"), click -> viewer.closeInventory());
        }
    }
}
