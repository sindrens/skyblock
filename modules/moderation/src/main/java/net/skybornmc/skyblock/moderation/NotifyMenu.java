package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.Map;

/**
 * {@code /pnotify} (spec 14.9), Bedrock-safe: 4 rows, plain clicks only. One toggle per alert type the viewer has the
 * permission for (others are not shown). Always-on types (extras.notify.always, default guard) show as locked "on".
 * A click flips the switch at once (stored per staff member, audited).
 */
final class NotifyMenu extends net.skybornmc.skyblock.common.menu.Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final StaffNotify notify;
    private final ModerationModule module;

    NotifyMenu(ModerationModule module, StaffNotify notify) {
        this.module = module;
        this.notify = notify;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, PunishMenus.MODULE, "notify-settings.menu.title"));
    }

    @Override
    public String messageModule() {
        return PunishMenus.MODULE;
    }

    @Override
    public String infoKey() {
        return "notify-settings.menu.info";
    }

    @Override
    public String helpTopic() {
        return "staff";
    }

    @Override
    protected void build() {
        frame();
        Map<NotifyRules.Type, Boolean> view = notify.view(viewer);
        if (view.isEmpty()) {
            set(13, service.item(Material.GRAY_DYE, viewer, PunishMenus.MODULE, "notify-settings.menu.none"));
            return;
        }
        int i = 0;
        for (Map.Entry<NotifyRules.Type, Boolean> e : view.entrySet()) {
            if (i >= SLOTS.length) {
                break;
            }
            NotifyRules.Type type = e.getKey();
            String key = "notify-settings.types." + type.id();
            if (notify.settings().always().contains(type)) {
                set(SLOTS[i++], service.item(Material.LIME_DYE, viewer, PunishMenus.MODULE, "notify-settings.menu.always",
                        Msg.p("type", module.service().text(viewer, key + ".name"))));
                continue;
            }
            set(SLOTS[i++], service.toggle(viewer, PunishMenus.MODULE, key, e.getValue(),
                    on -> module.setNotify(viewer, type, on)));
        }
    }
}
