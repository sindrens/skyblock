package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.text.Msg;
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
import java.util.UUID;

/**
 * Clicks on the crate blocks of the crate yard (M10.3, UX3/G5): a plain click (either button, a Bedrock tap) opens 1 with
 * the in-world show, a sneak-click shows the rewards. Without a key the click shows the rewards and how to get keys.
 * The opens are the same safe opens as {@code /crates} ({@link CrateOpener}).
 */
final class PhysicalCrates implements Listener {

    private final CratesModule m;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    PhysicalCrates(CratesModule module) {
        this.m = module;
    }

    /** {@code key:value} entries as a map, keys and values lowercase (bad entries skipped). Pure. */
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

    /** True when a click comes at least {@code cooldownMs} after the last one (holding a button sends many). Pure. */
    static boolean clickAllowed(Long last, long now, long cooldownMs) {
        return last == null || now - last >= cooldownMs || now < last;
    }

    /** The pedestal of a clicked block, if it is a crate block. */
    Optional<Pedestals.Pedestal> pedestalAt(Block block) {
        CrateYard yard = m.yard();
        return yard == null ? Optional.empty() : yard.at(block);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (!m.settings.physicalEnabled || event.getClickedBlock() == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Optional<Pedestals.Pedestal> pedestal = pedestalAt(event.getClickedBlock());
        if (pedestal.isEmpty()) {
            return;
        }
        event.setCancelled(true); // never open the vanilla chest / vault or break the block
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (!clickAllowed(lastClick.get(player.getUniqueId()), now, m.settings.clickCooldownMs)) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);
        if (!player.hasPermission(CratesModule.PERM_USE)) {
            return;
        }
        Optional<String> problem = m.problem(player);
        if (problem.isPresent()) {
            m.fail(player, problem.get());
            return;
        }
        Optional<CrateCatalog.Crate> crate = m.catalog().crate(pedestal.get().crateId());
        if (crate.isEmpty()) {
            return;
        }
        if (crate.get().hidden()) {
            m.fail(player, "coming-soon");
            return;
        }
        if (player.isSneaking()) {
            preview(player, crate.get());
            return;
        }
        UUID profile = m.profiles().profileKey(player);
        CratesModule.ProfileView view = m.view(profile);
        if (view != null) {
            click(player, crate.get(), pedestal.get(), view);
        } else {
            m.loadView(profile).whenComplete((v, e) -> {
                if (e == null && v != null && player.isOnline()) {
                    click(player, crate.get(), pedestal.get(), v);
                }
            });
        }
    }

    /** A plain click with the keys known: open 1, or the rewards + how to get keys when there is none. */
    private void click(Player player, CrateCatalog.Crate crate, Pedestals.Pedestal pedestal, CratesModule.ProfileView view) {
        if (view.keys(crate.id()).total() <= 0) {
            preview(player, crate);
            m.msg.send(player, "physical.no-keys", Msg.mm("key", m.keyMini(crate.id())));
            m.core.sounds().play(player, "error");
            return;
        }
        if (m.opener().busy(player.getUniqueId()) || m.show().animating(player.getUniqueId())) {
            m.fail(player, "busy");
            return;
        }
        m.opener().open(player, crate, 1, null, CrateShow.Source.pedestal(pedestal));
    }

    private void preview(Player player, CrateCatalog.Crate crate) {
        PreviewMenu preview = new PreviewMenu(m, crate);
        preview.parent(new CratesMenu(m));
        m.core.menus().open(player, preview);
    }

    /** Forgets a player's last click (quit). */
    void forget(UUID player) {
        lastClick.remove(player);
    }
}
