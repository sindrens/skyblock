package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Island entity limits (owner SK2): armor stands, item frames, minecarts, boats, villagers and display entities per
 * island, +{@link IslandService#entityLimitBonusPercent} with the upgrade. Counts are kept by entity add/remove events
 * (so they cover the island's loaded chunks; no scans) and enforced when one is placed or spawned.
 */
final class EntityLimits implements Listener {

    enum Kind {
        ARMOR_STANDS("armor-stands"), ITEM_FRAMES("item-frames"), MINECARTS("minecarts"), BOATS("boats"), VILLAGERS("villagers"),
        DISPLAYS("displays");

        final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    private record Counted(UUID island, Kind kind) {
    }

    private final StackingModule module;
    private final Map<UUID, int[]> counts = new HashMap<>();
    private final Map<UUID, Counted> counted = new HashMap<>();

    EntityLimits(StackingModule module) {
        this.module = module;
    }

    static Kind kind(Entity entity) {
        return switch (entity) {
            case ArmorStand ignored -> Kind.ARMOR_STANDS;
            case ItemFrame ignored -> Kind.ITEM_FRAMES;
            case Minecart ignored -> Kind.MINECARTS;
            case Boat ignored -> Kind.BOATS;
            case Villager ignored -> Kind.VILLAGERS;
            case Display ignored -> Kind.DISPLAYS;
            default -> null;
        };
    }

    int base(Kind kind) {
        StackingConfig c = module.config();
        return switch (kind) {
            case ARMOR_STANDS -> c.limitArmorStands;
            case ITEM_FRAMES -> c.limitItemFrames;
            case MINECARTS -> c.limitMinecarts;
            case BOATS -> c.limitBoats;
            case VILLAGERS -> c.limitVillagers;
            case DISPLAYS -> c.limitDisplays;
        };
    }

    private Optional<UUID> islandAt(Location location) {
        return module.islands().flatMap(s -> s.islandAt(location)).map(IslandService.IslandInfo::id);
    }

    // ------------------------------------------------------------------ counting (called by the module's add/remove listener)

    void added(Entity entity) {
        Kind kind = kind(entity);
        if (kind == null || counted.containsKey(entity.getUniqueId()) || !module.eligible(entity.getWorld())) {
            return;
        }
        islandAt(entity.getLocation()).ifPresent(island -> {
            counted.put(entity.getUniqueId(), new Counted(island, kind));
            counts.computeIfAbsent(island, i -> new int[Kind.values().length])[kind.ordinal()]++;
        });
    }

    void removed(Entity entity) {
        Counted c = counted.remove(entity.getUniqueId());
        if (c == null) {
            return;
        }
        int[] row = counts.get(c.island());
        if (row != null) {
            row[c.kind().ordinal()] = Math.max(0, row[c.kind().ordinal()] - 1);
        }
    }

    int count(UUID island, Kind kind) {
        int[] row = counts.get(island);
        return row == null ? 0 : row[kind.ordinal()];
    }

    int limit(UUID island, Kind kind) {
        int bonus = module.islands().map(s -> s.entityLimitBonusPercent(island)).orElse(0);
        return StackMath.entityLimit(base(kind), bonus);
    }

    /** The limit if one more would go over it, else -1. */
    private int over(Location location, Kind kind) {
        Optional<UUID> island = islandAt(location);
        if (island.isEmpty()) {
            return -1;
        }
        int limit = limit(island.get(), kind);
        return count(island.get(), kind) >= limit ? limit : -1;
    }

    // ------------------------------------------------------------------ enforcing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(EntityPlaceEvent event) {
        check(event.getEntity(), event.getEntity().getLocation(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHang(HangingPlaceEvent event) {
        check(event.getEntity(), event.getEntity().getLocation(), event.getPlayer(), event);
    }

    /** Everything else that adds one (dispensers, breeding, curing, plugins) is cancelled quietly. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        check(event.getEntity(), event.getLocation(), null, event);
    }

    private void check(Entity entity, Location at, Player player, org.bukkit.event.Cancellable event) {
        Kind kind = kind(entity);
        if (kind == null || at.getWorld() == null || !module.eligible(at.getWorld())) {
            return;
        }
        int limit = over(at, kind);
        if (limit < 0) {
            return;
        }
        event.setCancelled(true);
        if (player != null) {
            module.msg().send(player, "limit.reached", Msg.p("limit", limit),
                    Msg.c("what", module.msg().component(player, "limit.kind." + kind.key)));
            module.core().sounds().play(player, "error");
        }
    }

    void clear() {
        counts.clear();
        counted.clear();
    }
}
