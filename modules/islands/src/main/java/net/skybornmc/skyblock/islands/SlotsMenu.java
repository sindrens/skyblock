package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /isadmin slots} (M2.7): every grid slot ever handed out, in spiral order (slot 0 = grid 0,0 in the middle of
 * the world, then ring by ring). Grass = used (owner, members, size), magma = being cleared (progress), glass = free
 * (reused by the next new island). One plain click teleports to the slot (Bedrock-safe; needs the teleport-slot
 * permission). Top: totals per state. Bottom: 47 filter (all / used / clearing / free), 51 reload.
 */
final class SlotsMenu extends PagedMenu<IslandStore.SlotRow> {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_FILTER = 47;
    static final int SLOT_RELOAD = 51;

    private final IslandsModule module;
    private final IslandManager islands;
    private final IslandAdmin admin;
    private final List<IslandStore.SlotRow> rows;
    /** null = all states. */
    private SlotState filter;

    SlotsMenu(IslandsModule module, IslandManager islands, IslandAdmin admin, List<IslandStore.SlotRow> rows, SlotState filter) {
        this.module = module;
        this.islands = islands;
        this.admin = admin;
        this.rows = List.copyOf(rows);
        this.filter = filter;
    }

    /** The rows with state {@code filter} (all rows for null), in their given order. Pure. */
    static List<IslandStore.SlotRow> filter(List<IslandStore.SlotRow> rows, SlotState filter) {
        if (filter == null) {
            return rows;
        }
        List<IslandStore.SlotRow> out = new ArrayList<>();
        for (IslandStore.SlotRow row : rows) {
            if (SlotState.byKey(row.state()).orElse(null) == filter) {
                out.add(row);
            }
        }
        return out;
    }

    /** The filter choices in pick-list order: all (null), used, clearing, free. Pure. */
    static List<SlotState> filterChoices() {
        List<SlotState> choices = new ArrayList<>();
        choices.add(null);
        choices.add(SlotState.USED);
        choices.add(SlotState.CLEARING);
        choices.add(SlotState.FREE);
        return choices;
    }

    @Override
    protected List<IslandStore.SlotRow> entries() {
        return filter(rows, filter);
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("admin.crumb"), text("admin.slots.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "admin.slots.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    protected String emptyKey() {
        return "admin.slots.empty";
    }

    @Override
    protected void decorate() {
        Map<SlotState, Integer> counts = counts(rows);
        set(SLOT_SUMMARY, service.item(new ItemStack(Material.FILLED_MAP), text("admin.slots.summary.name"),
                lines("admin.slots.summary.lore", Msg.p("total", rows.size()), Msg.p("used", counts.get(SlotState.USED)),
                        Msg.p("clearing", counts.get(SlotState.CLEARING)), Msg.p("free", counts.get(SlotState.FREE)), Msg.p("queue", islands.module().lifecycle().clearer().pending()))));
        String shown = filter == null ? module.raw("admin.slots.filter.all") : module.raw("admin.state." + filter.key());
        set(SLOT_FILTER, service.item(new ItemStack(Material.HOPPER), text("admin.slots.filter.name", Msg.p("filter", shown)),
                lines("admin.slots.filter.lore")), click -> pickFilter());
        set(SLOT_RELOAD, service.item(new ItemStack(Material.CLOCK), text("admin.slots.reload.name"), lines("admin.slots.reload.lore")),
                click -> admin.openSlots(viewer, filter));
    }

    /** A pick list of the states (GUI-4b/G9: no forward-only cycling). */
    private void pickFilter() {
        Map<SlotState, Integer> counts = counts(rows);
        List<PickListMenu.Option<SlotState>> options = new ArrayList<>();
        for (SlotState state : filterChoices()) {
            String name = state == null ? module.raw("admin.slots.filter.all") : module.raw("admin.state." + state.key());
            int count = state == null ? rows.size() : counts.getOrDefault(state, 0);
            options.add(PickListMenu.Option.of(state, Material.HOPPER, text("admin.slots.filter.pick", Msg.p("filter", name),
                    Msg.p("count", count)), state == filter));
        }
        openChild(new PickListMenu<>(List.of(text("admin.slots.crumb"), text("admin.slots.filter.crumb")), options, state -> {
            filter = state;
            page(0);
        }));
    }

    @Override
    protected MenuButton entry(IslandStore.SlotRow row) {
        SlotState state = SlotState.byKey(row.state()).orElse(SlotState.CLEARING);
        TagResolver[] p = {Msg.p("slot", row.id()), Msg.p("state", module.raw("admin.state." + state.key())),
                Msg.p("grid-x", row.gridX()), Msg.p("grid-z", row.gridZ()),
                Msg.p("x", IslandGrid.centerBlock(row.gridX())), Msg.p("z", IslandGrid.centerBlock(row.gridZ())),
                Msg.p("updated", module.config().dateFormatter().format(Instant.ofEpochMilli(row.updatedAt())))};
        List<Component> lore = new ArrayList<>(lines("admin.slots.entry.where", p));
        Material icon;
        switch (state) {
            case USED -> {
                icon = Material.GRASS_BLOCK;
                Island island = islands.islandInCell(row.gridX(), row.gridZ());
                if (island != null) {
                    lore.addAll(lines("admin.slots.entry.used", Msg.p("island", islands.displayName(island)),
                            Msg.p("owner", islands.ownerName(island)), Msg.p("members", island.memberCount()),
                            Msg.p("size", island.sizeChunks())));
                }
            }
            case CLEARING -> {
                icon = Material.MAGMA_BLOCK;
                IslandClearer.Progress progress = islands.module().lifecycle().clearer().progress(row.id()).orElse(null);
                if (progress == null) {
                    lore.addAll(lines("admin.slots.entry.clearing-waiting"));
                } else if (progress.total() == 0) {
                    lore.addAll(lines("admin.slots.entry.clearing-queued"));
                } else {
                    lore.addAll(lines("admin.slots.entry.clearing", Msg.p("done", progress.done()), Msg.p("total", progress.total()),
                            Msg.p("percent", progress.done() * 100 / Math.max(1, progress.total()))));
                }
            }
            default -> {
                icon = Material.GLASS;
                lore.addAll(lines("admin.slots.entry.free"));
            }
        }
        lore.addAll(lines("admin.slots.entry.updated", p));
        boolean canTeleport = viewer.hasPermission(IslandAdmin.PERM_TELEPORT_SLOT);
        if (canTeleport) {
            lore.add(Component.empty());
            lore.addAll(lines("admin.slots.entry.click"));
        }
        ItemStack item = service.item(new ItemStack(icon), text("admin.slots.entry.name", p), lore);
        if (!canTeleport) {
            return MenuButton.display(item);
        }
        int id = row.id();
        return MenuButton.of(item, click -> {
            viewer.closeInventory();
            admin.teleportSlot(viewer, id);
        });
    }

    /** Counts per state of {@code rows} (for tests and the summary). Pure. */
    static Map<SlotState, Integer> counts(List<IslandStore.SlotRow> rows) {
        Map<SlotState, Integer> counts = new java.util.EnumMap<>(SlotState.class);
        for (SlotState state : SlotState.values()) {
            counts.put(state, 0);
        }
        for (IslandStore.SlotRow row : rows) {
            counts.merge(SlotState.byKey(row.state()).orElse(SlotState.CLEARING), 1, Integer::sum);
        }
        return counts;
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
