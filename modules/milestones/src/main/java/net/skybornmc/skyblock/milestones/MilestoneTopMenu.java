package net.skybornmc.skyblock.milestones;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.leaderboard.TopLine;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Milestone leaderboard (owner F13a/G6): the 10 islands or players with the most completed milestone tiers, opened from
 * the {@code /milestones} overview. Two tabs on the top row (Island, Personal); rows read once per open off the main
 * thread through {@link MilestoneClaims#top}. Your own island / you glow. 4 rows, Bedrock: plain taps only.
 */
final class MilestoneTopMenu extends Menu {

    static final int SLOT_TAB_ISLAND = 3;
    static final int SLOT_TAB_PERSONAL = 5;
    static final int[] PLACES = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private final MilestonesModule m;
    private boolean island;
    private List<TopLine> islandRows;
    private List<TopLine> personalRows;
    private boolean loading;

    MilestoneTopMenu(MilestonesModule module, boolean island) {
        this.m = module;
        this.island = island;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, MilestonesModule.ID, "menu.title"),
                service.text(viewer, MilestonesModule.ID, "top.crumb"));
    }

    @Override
    public String messageModule() {
        return MilestonesModule.ID;
    }

    @Override
    public String infoKey() {
        return "top.info";
    }

    @Override
    protected void build() {
        navBar();
        set(SLOT_TAB_ISLAND, service.tab(viewer, new ItemStack(Material.GRASS_BLOCK), service.text(viewer, MilestonesModule.ID, "top.tab.island.name"),
                service.lines(viewer, MilestonesModule.ID, "top.tab.island.lore"), island, () -> {
                    island = true;
                    refresh();
                }));
        set(SLOT_TAB_PERSONAL, service.tab(viewer, new ItemStack(Material.PLAYER_HEAD), service.text(viewer, MilestonesModule.ID, "top.tab.personal.name"),
                service.lines(viewer, MilestonesModule.ID, "top.tab.personal.lore"), !island, () -> {
                    island = false;
                    refresh();
                }));
        List<TopLine> rows = island ? islandRows : personalRows;
        if (rows == null) {
            setLoading(PLACES[2]);
            load();
            return;
        }
        if (rows.isEmpty()) {
            setEmpty(MilestonesModule.ID, "top.empty");
            return;
        }
        UUID own = island ? m.claims().islandOf(viewer) : viewer.getUniqueId();
        for (int i = 0; i < PLACES.length && i < rows.size(); i++) {
            set(PLACES[i], entry(i + 1, rows.get(i), own));
        }
    }

    /** Reads the shown tab once (the other tab is read when it is opened). */
    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        boolean reading = island;
        m.claims().top(reading, PLACES.length).whenCompleteAsync((list, error) -> {
            loading = false;
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Milestones: could not read the leaderboard", Database.cause(error));
                list = List.of();
            }
            if (reading) {
                islandRows = list;
            } else {
                personalRows = list;
            }
            if (isOpen()) {
                refresh();
            }
        }, m.main());
    }

    private ItemStack entry(int place, TopLine line, UUID own) {
        String name = island ? islandName(line.player()) : line.name();
        if (name == null || name.isBlank()) {
            name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(service.text(viewer, MilestonesModule.ID, island ? "top.unknown-island" : "top.unknown-player"));
        }
        String key = "top.entry." + switch (place) {
            case 1 -> "first";
            case 2 -> "second";
            case 3 -> "third";
            default -> "other";
        };
        ItemStack item = service.item(island ? islandIcon(place) : Material.PLAYER_HEAD, viewer, MilestonesModule.ID, key,
                Msg.number("place", place), Msg.p("name", name), Msg.number("points", line.value()));
        if (line.player().equals(own)) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    /** #1 gold, #2 iron, #3 copper, then grass. */
    static Material islandIcon(int place) {
        return switch (place) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.GRASS_BLOCK;
        };
    }

    /** Main thread: island name, else the owner's name, else null. */
    private String islandName(UUID islandId) {
        var info = m.core().services().optional(IslandService.class).flatMap(i -> i.island(islandId)).orElse(null);
        if (info == null) {
            return null;
        }
        String name = info.name();
        if ((name == null || name.isBlank()) && info.owner() != null) {
            name = Bukkit.getOfflinePlayer(info.owner()).getName();
        }
        return name;
    }
}
