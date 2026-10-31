package net.skybornmc.skyblock.logs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PlayerPickerMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /logs}: pick who/what (subject), which category and which time range, then search. Layout:
 * <pre>
 *  row 1  frame · [summary of the current search] · frame
 *  row 2  Online player · Any player · Staff member · Near me · Item · Island · Everything
 *  row 3  Category · Time range · Radius (near only)
 *  row 4  Reset · SEARCH · Export (admin)
 * </pre>
 * Everything works with plain clicks (Bedrock-safe); names and ids are typed with {@code MenuService#input} (QOL3: a text
 * popup with the on-screen keyboard on Bedrock, the chat prompt on Java).
 */
final class LogsHubMenu extends Menu {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_ONLINE = 10;
    static final int SLOT_ANY = 11;
    static final int SLOT_STAFF = 12;
    static final int SLOT_NEAR = 13;
    static final int SLOT_ITEM = 14;
    static final int SLOT_ISLAND = 15;
    static final int SLOT_ALL = 16;
    static final int SLOT_CATEGORY = 20;
    static final int SLOT_TIME = 22;
    static final int SLOT_RADIUS = 24;
    static final int SLOT_RESET = 29;
    static final int SLOT_SEARCH = 31;
    static final int SLOT_EXPORT = 33;
    /** Longest typed values: player names (Bedrock names have a prefix), item ids (UUID), island owner name or id. */
    static final int NAME_MAX = 32;
    static final int ITEM_ID_MAX = 36;
    static final int ISLAND_MAX = 64;

    private final LogsModule module;
    private LogFilter filter;

    LogsHubMenu(LogsModule module, LogFilter filter) {
        this.module = module;
        this.filter = filter;
        if (filter.range == null) {
            filter.range = module.defaultRange();
        }
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.render.text(viewer, "title.staff"), module.render.text(viewer, "title.logs"));
    }

    @Override
    public String messageModule() {
        return LogsModule.ID;
    }

    @Override
    public String infoKey() {
        return "hub.info";
    }

    @Override
    public String helpTopic() {
        return "logs";
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_SUMMARY, service.item(Material.KNOWLEDGE_BOOK, module.render.text(viewer, "hub.summary.name"),
                service.lines(viewer, LogsModule.ID, "hub.summary.lore",
                        Msg.c("subject", module.render.subject(viewer, filter)),
                        Msg.c("categories", module.render.categories(viewer, filter)),
                        Msg.c("range", module.render.range(viewer, filter.range)))));

        subject(SLOT_ONLINE, Material.PLAYER_HEAD, "hub.online", filter.subject == LogFilter.Subject.PLAYER, () ->
                openChild(new PlayerPickerMenu(v -> service.title(v, module.render.text(v, "title.logs"),
                        module.render.text(v, "title.pick-player")), p -> true, (v, picked) -> {
                    filter.player(picked.getUniqueId(), picked.getName());
                    service.open(v, this);
                })));
        subject(SLOT_ANY, Material.NAME_TAG, "hub.any", false, () -> askName("prompt.player", false));
        if (viewer.hasPermission(LogsModule.PERM_STAFF)) {
            subject(SLOT_STAFF, Material.NETHER_STAR, "hub.staff", filter.subject == LogFilter.Subject.ACTOR, () ->
                    askName("prompt.staff", true));
        } else {
            // QOL3: a locked item instead of a gap, so the subject row keeps its shape for every rank.
            set(SLOT_STAFF, service.locked(viewer, module.render.text(viewer, "hub.staff.name"),
                    module.render.text(viewer, "hub.staff.locked")));
        }
        subject(SLOT_NEAR, Material.COMPASS, "hub.near", filter.subject == LogFilter.Subject.NEAR, () -> {
            int radius = filter.subject == LogFilter.Subject.NEAR ? filter.radius : module.config.nearDefaultRadius;
            LogFilter near = module.near(viewer, Math.min(radius, module.config.nearMaxRadius), filter.range);
            near.categories.addAll(filter.categories);
            filter = near;
            refresh();
        });
        subject(SLOT_ITEM, Material.CHEST, "hub.item", filter.subject == LogFilter.Subject.ITEM, () -> {
            Optional<UUID> held = module.heldItemUuid(viewer);
            if (held.isPresent()) {
                useItem(held.get());
                refresh();
                return;
            }
            service.input(viewer, LogsModule.ID, "prompt.item", ITEM_ID_MAX + 4, null, input -> {
                Optional<UUID> uuid = itemId(input);
                if (uuid.isPresent()) {
                    useItem(uuid.get());
                } else {
                    module.msg.send(viewer, "errors.not-uuid", Msg.p("input", input));
                    module.sound(viewer, "error");
                }
                service.open(viewer, this);
            }, () -> service.open(viewer, this), Msg.p("max", ITEM_ID_MAX));
        });
        subject(SLOT_ISLAND, Material.GRASS_BLOCK, "hub.island", filter.subject == LogFilter.Subject.ISLAND, () ->
                service.input(viewer, LogsModule.ID, "prompt.island", ISLAND_MAX, null, input -> {
                    String known = net.skybornmc.skyblock.common.command.OfflineNames.names().stream()
                            .filter(n -> n.equalsIgnoreCase(input)).findFirst().orElse(null);
                    if (known == null) {
                        setIsland(null, null, input);
                        service.open(viewer, this);
                        return;
                    }
                    module.withPlayer(viewer, known, (uuid, name) -> {
                        setIsland(uuid, name, null);
                        service.open(viewer, this);
                    });
                }, () -> service.open(viewer, this), Msg.p("max", ISLAND_MAX)));
        subject(SLOT_ALL, Material.BOOKSHELF, "hub.all", filter.subject == LogFilter.Subject.ALL, () -> {
            LogFilter all = new LogFilter();
            all.categories.addAll(filter.categories);
            all.range = filter.range;
            filter = all;
            refresh();
        });

        // Category
        Material categoryIcon = filter.categories.size() == 1
                ? module.render.icon(filter.categories.iterator().next()) : Material.HOPPER;
        set(SLOT_CATEGORY, service.item(categoryIcon, module.render.text(viewer, "hub.category.name"),
                service.lines(viewer, LogsModule.ID, "hub.category.lore",
                        Msg.c("categories", module.render.categories(viewer, filter)))), click ->
                openChild(new LogCategoryMenu(module, filter)));

        // Time range (GUI-4/G9: a pick list, one click picks)
        set(SLOT_TIME, service.item(Material.CLOCK, module.render.text(viewer, "hub.time.name"),
                service.lines(viewer, LogsModule.ID, "hub.time.lore",
                        Msg.c("range", module.render.range(viewer, filter.range)),
                        Msg.p("options", String.join(", ", module.config.ranges)))), click -> pickRange());

        // Radius (near only)
        if (filter.subject == LogFilter.Subject.NEAR) {
            set(SLOT_RADIUS, service.item(Material.SPYGLASS, module.render.text(viewer, "hub.radius.name"),
                    service.lines(viewer, LogsModule.ID, "hub.radius.lore", Msg.p("radius", filter.radius),
                            Msg.p("options", String.join(", ", module.config.nearRadii)))), click -> pickRadius());
        }

        set(SLOT_RESET, service.item(Material.STRUCTURE_VOID, viewer, LogsModule.ID, "hub.reset"), click -> {
            filter = new LogFilter();
            filter.range = module.defaultRange();
            refresh();
        });
        set(SLOT_SEARCH, service.item(Material.LIME_CONCRETE, module.render.text(viewer, "hub.search.name"),
                service.lines(viewer, LogsModule.ID, "hub.search.lore",
                        Msg.c("subject", module.render.subject(viewer, filter)))), click -> {
            if (module.prepare(viewer, filter)) {
                service.sound(viewer, "success");
                openChild(new LogResultsMenu(module, filter.copy()));
            }
        });
        if (module.canExport(viewer)) {
            set(SLOT_EXPORT, service.item(Material.MAP, viewer, LogsModule.ID, "hub.export"), click -> {
                viewer.closeInventory();
                module.export(viewer, filter.copy());
            });
        }
    }

    /** A subject button; the selected one says so in its lore. */
    private void subject(int slot, Material icon, String key, boolean selected, Runnable action) {
        List<Component> lore = new ArrayList<>(service.lines(viewer, LogsModule.ID, key + ".lore"));
        if (selected) {
            lore.addAll(service.lines(viewer, LogsModule.ID, "hub.selected", Msg.c("subject", module.render.subject(viewer, filter))));
        }
        ItemStack item = service.item(icon, module.render.text(viewer, key + ".name"), lore);
        if (selected) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        set(slot, item, click -> action.run());
    }

    private void askName(String promptKey, boolean asActor) {
        service.input(viewer, LogsModule.ID, promptKey, NAME_MAX, null, input -> module.withPlayer(viewer, input, (uuid, name) -> {
            Duration range = filter.range;
            LogFilter next = asActor ? new LogFilter().actor(uuid, name) : new LogFilter().player(uuid, name);
            next.categories.addAll(filter.categories);
            next.range = range;
            filter = next;
            service.open(viewer, this);
        }), () -> service.open(viewer, this), Msg.p("max", NAME_MAX));
    }

    /**
     * A typed item id: a UUID, with or without spaces (phone keyboards add them) and in any case. Package-private for
     * tests.
     */
    static Optional<UUID> itemId(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String clean = input.replace(" ", "").strip();
        if (clean.length() != ITEM_ID_MAX) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(clean));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void useItem(UUID uuid) {
        LogFilter item = new LogFilter();
        item.subject = LogFilter.Subject.ITEM;
        item.target = uuid.toString();
        item.range = Duration.ZERO;
        filter = item;
    }

    private void setIsland(UUID uuid, String name, String id) {
        LogFilter island = new LogFilter().categories(AuditCategory.ISLAND);
        island.subject = LogFilter.Subject.ISLAND;
        island.player = uuid;
        island.playerName = name;
        island.target = id;
        island.range = filter.range;
        filter = island;
    }

    private void pickRange() {
        List<net.skybornmc.skyblock.common.menu.PickListMenu.Option<Duration>> options = new ArrayList<>();
        Duration now = filter.range == null ? Duration.ZERO : filter.range;
        for (String text : module.config.ranges) {
            Duration range = module.parseRange(text);
            Duration value = range == null ? Duration.ZERO : range;
            if (options.stream().anyMatch(o -> o.value().equals(value))) {
                continue;
            }
            options.add(new net.skybornmc.skyblock.common.menu.PickListMenu.Option<>(value, new org.bukkit.inventory.ItemStack(Material.CLOCK),
                    module.render.range(viewer, value), List.of(), value.equals(now)));
        }
        openChild(new net.skybornmc.skyblock.common.menu.PickListMenu<>(List.of(module.render.text(viewer, "title.logs"),
                module.render.text(viewer, "hub.time.name")), options, value -> filter.range = value));
    }

    private void pickRadius() {
        List<net.skybornmc.skyblock.common.menu.PickListMenu.Option<Integer>> options = new ArrayList<>();
        for (String r : module.config.nearRadii) {
            int radius;
            try {
                radius = Math.min(module.config.nearMaxRadius, Integer.parseInt(r.trim()));
            } catch (NumberFormatException ignored) {
                continue; // skip bad entries
            }
            if (options.stream().anyMatch(o -> o.value() == radius)) {
                continue;
            }
            options.add(new net.skybornmc.skyblock.common.menu.PickListMenu.Option<>(radius, new org.bukkit.inventory.ItemStack(Material.SPYGLASS),
                    service.text(viewer, LogsModule.ID, "hub.radius.value", Msg.p("radius", radius)), List.of(), radius == filter.radius));
        }
        openChild(new net.skybornmc.skyblock.common.menu.PickListMenu<>(List.of(module.render.text(viewer, "title.logs"),
                module.render.text(viewer, "hub.radius.name")), options, value -> filter.radius = value));
    }

    /** The value after {@code current} in {@code options} (first when not found). */
    static Duration next(List<Duration> options, Duration current) {
        if (options.isEmpty()) {
            return current;
        }
        Duration now = current == null ? Duration.ZERO : current;
        int index = options.indexOf(now);
        return options.get((index + 1) % options.size());
    }
}
