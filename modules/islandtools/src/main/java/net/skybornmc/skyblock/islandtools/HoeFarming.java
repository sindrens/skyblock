package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * The island hoe on crops (T9-T13): a ripe crop broken with the hoe harvests the whole row in front of the player (Row
 * upgrade), the crops replant themselves and are ripe again after the regrow time (60 s, faster with the island's
 * crop-growth upgrade, 15 s at the top; perks can push it to the floor), now and then a Growth Burst makes the row ripe
 * at once (T12), and a ripe crop shows a cue: subtle from afar, stronger close by (T13). Regrowing crops are kept in
 * memory only: after a restart they simply grow the vanilla way.
 */
final class HoeFarming implements Listener {

    static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

    /** One replanted crop waiting to be ripe. */
    private record Due(UUID world, int x, int y, int z, Material crop, long at) {
        String key() {
            return world + ":" + x + ":" + y + ":" + z;
        }
    }

    private final IslandToolsModule m;
    private final PriorityQueue<Due> queue = new PriorityQueue<>((a, b) -> Long.compare(a.at(), b.at()));
    private final Map<String, Due> byKey = new HashMap<>();

    HoeFarming(IslandToolsModule m) {
        this.m = m;
    }

    static boolean ripeCrop(BlockData data) {
        return CROPS.contains(data.getMaterial()) && data instanceof Ageable a && a.getAge() >= a.getMaximumAge();
    }

    /** Regrow seconds for this player on this island. */
    double regrowSeconds(Player p, UUID island) {
        IslandToolsConfig c = m.config();
        double upgrade = m.core().services().optional(ProgressionService.class).map(s -> s.upgradeEffectValue(island, c.regrowEffect)).orElse(0.0);
        double family = m.core().services().optional(BonusService.class)
                .map(b -> b.total(BonusFamily.CROP_GROWTH, BonusContext.of(p).withActivity("crop_growth"))).orElse(0.0);
        return ToolRules.regrowSeconds(upgrade, family, c.regrowBase, c.regrowTop, c.regrowFullAt, c.regrowFloor);
    }

    double burstPercent(UUID island) {
        IslandToolsConfig c = m.config();
        double upgrade = m.core().services().optional(ProgressionService.class).map(s -> s.upgradeEffectValue(island, c.regrowEffect)).orElse(0.0);
        return ToolRules.burstPercent(upgrade, c.burstPerGrowth, c.burstMax);
    }

    /** Replants a harvested crop next tick; ripe at once on a burst, else after the regrow time. */
    void replant(Location loc, Material crop, Player p, UUID island, boolean burst) {
        if (!m.config().regrowEnabled) {
            return;
        }
        double seconds = regrowSeconds(p, island);
        Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
            Block b = loc.getBlock();
            Material below = b.getRelative(0, -1, 0).getType();
            boolean soil = crop == Material.NETHER_WART ? below == Material.SOUL_SAND : below == Material.FARMLAND;
            if (!b.getType().isAir() || !soil) {
                return;
            }
            b.setType(crop, false);
            if (burst) {
                ripen(b, crop);
                return;
            }
            Due d = new Due(loc.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), crop, System.currentTimeMillis() + (long) (seconds * 1000));
            Due old = byKey.put(d.key(), d);
            if (old != null) {
                queue.remove(old);
            }
            queue.add(d);
            while (byKey.size() > m.config().regrowMax && !queue.isEmpty()) {
                Due drop = queue.poll();
                byKey.remove(drop.key());
            }
        });
    }

    /** Every half second: crops whose time came are ripe (loaded chunks only). */
    void tick() {
        long now = System.currentTimeMillis();
        int budget = 400;
        while (!queue.isEmpty() && queue.peek().at() <= now && budget-- > 0) {
            Due d = queue.poll();
            byKey.remove(d.key());
            World w = Bukkit.getWorld(d.world());
            if (w == null || !w.isChunkLoaded(d.x() >> 4, d.z() >> 4)) {
                continue;
            }
            Block b = w.getBlockAt(d.x(), d.y(), d.z());
            if (b.getType() == d.crop()) {
                ripen(b, d.crop());
            }
        }
    }

    /** Disable: what is waiting and loaded becomes ripe (nothing stays half grown because the plugin stopped). */
    void ripenAll() {
        for (Due d : queue) {
            World w = Bukkit.getWorld(d.world());
            if (w != null && w.isChunkLoaded(d.x() >> 4, d.z() >> 4)) {
                Block b = w.getBlockAt(d.x(), d.y(), d.z());
                if (b.getType() == d.crop() && b.getBlockData() instanceof Ageable a) {
                    a.setAge(a.getMaximumAge());
                    b.setBlockData(a, false);
                }
            }
        }
        queue.clear();
        byKey.clear();
    }

    private void ripen(Block b, Material crop) {
        if (!(b.getBlockData() instanceof Ageable a) || b.getType() != crop) {
            return;
        }
        a.setAge(a.getMaximumAge());
        b.setBlockData(a, false);
        cue(b.getLocation().add(0.5, 0.6, 0.5));
    }

    /** T13: a strong cue close by, one particle from afar, nothing further away. */
    void cue(Location at) {
        double near = m.config().cueNear;
        double far = m.config().cueFar;
        for (Player p : at.getWorld().getPlayers()) {
            double d2 = p.getLocation().distanceSquared(at);
            if (d2 <= near * near) {
                p.spawnParticle(Particle.HAPPY_VILLAGER, at, 5, 0.25, 0.2, 0.25, 0);
            } else if (d2 <= far * far) {
                p.spawnParticle(Particle.HAPPY_VILLAGER, at, 1, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    /** The burst show on the harvested row. */
    void burstShow(Player p, Location at) {
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0.5, 0.8, 0.5), 30, 1.2, 0.4, 1.2, 0.05);
        m.core().sounds().play(p, "unlock");
        m.msg().actionBar(p, "hoe.burst");
    }
}
