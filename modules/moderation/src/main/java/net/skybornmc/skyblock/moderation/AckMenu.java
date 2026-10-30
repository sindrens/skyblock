package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * Warning acknowledgement for Bedrock players (spec 12.6, {@code ack.bedrock-menu}): 3 rows, one "I understand" button
 * (a plain click; clickable chat is unreliable on Bedrock). Closing it changes nothing: {@code /ack} still works and it
 * never blocks gameplay by default.
 */
final class AckMenu extends net.skybornmc.skyblock.common.menu.Menu {

    private final PunishExtras extras;
    private boolean used;

    AckMenu(PunishExtras extras) {
        this.extras = extras;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, PunishMenus.MODULE, "ack.menu.title"));
    }

    @Override
    public String messageModule() {
        return PunishMenus.MODULE;
    }

    @Override
    protected void build() {
        frame();
        set(13, service.item(Material.LIME_CONCRETE, viewer, PunishMenus.MODULE, "ack.menu.button"), click -> {
            if (used) {
                return;
            }
            used = true;
            extras.acknowledge(viewer).whenComplete((r, e) -> org.bukkit.Bukkit.getScheduler().runTask(service.plugin(), () -> {
                if (isOpen()) {
                    viewer.closeInventory();
                }
            }));
        });
    }
}
