package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The crate blocks on the crate yard pedestals (build points {@code crates}, {@code crates-settings.yml →
 * physical.points}): right-click opens 1, left-click shows the rewards (UI_STYLE §6.4b). Just a shortcut to the same
 * opens as {@code /crates}; the blocks, holograms and animations themselves come in M10.3.
 */
final class PhysicalCrates implements Listener {

    private final CratesModule m;

    PhysicalCrates(CratesModule module) {
        this.m = module;
    }

    /** {@code point:crate} entries as a map (bad entries skipped). Pure. */
    static Map<String, String> mapping(List<String> raw) {
        Map<String, String> out = new HashMap<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            int colon = s == null ? -1 : s.indexOf(':');
            if (colon > 0 && colon < s.length() - 1) {
                out.put(s.substring(0, colon).trim().toLowerCase(Locale.ROOT), s.substring(colon + 1).trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** Whole numbers of {@code physical.y-offsets} (bad ones skipped). Pure. */
    static List<Integer> offsets(List<String> raw) {
        List<Integer> out = new ArrayList<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException | NullPointerException ignored) {
                // skipped
            }
        }
        return out;
    }

    /** The crate whose build point (or a block {@code offsets} above it) is this block. */
    Optional<CrateCatalog.Crate> crateAt(Block block) {
        Optional<BuildPoints> points = m.core.services().optional(BuildPoints.class);
        if (points.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> map = mapping(m.settings.physicalPoints);
        List<Integer> ys = offsets(m.settings.physicalOffsets);
        String world = block.getWorld().getName();
        for (BuildPoint p : points.get().points("crates")) {
            if (!p.world().equals(world) || p.blockX() != block.getX() || p.blockZ() != block.getZ()) {
                continue;
            }
            if (!ys.contains(block.getY() - p.blockY())) {
                continue;
            }
            String crate = map.get(p.id().toLowerCase(Locale.ROOT));
            if (crate != null) {
                return m.catalog().crate(crate).filter(c -> !c.hidden());
            }
        }
        return Optional.empty();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!m.settings.physicalEnabled || !m.ready() || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Optional<CrateCatalog.Crate> crate = crateAt(event.getClickedBlock());
        if (crate.isEmpty()) {
            return;
        }
        event.setCancelled(true); // never open the vanilla chest / break the block
        Player player = event.getPlayer();
        if (!player.hasPermission(CratesModule.PERM_USE)) {
            return;
        }
        Optional<String> problem = m.problem(player);
        if (problem.isPresent()) {
            m.fail(player, problem.get());
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK || player.isSneaking()) {
            PreviewMenu preview = new PreviewMenu(m, crate.get());
            preview.parent(new CratesMenu(m));
            m.core.menus().open(player, preview);
        } else if (!m.opener().busy(player.getUniqueId())) {
            m.opener().open(player, crate.get(), 1, null);
        }
    }
}
