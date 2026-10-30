package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.profile.ProfileDeleteEvent;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Round 3 PR4: no profile switch while frozen. Staff mode keeps the real inventory aside, so switching (or deleting a
 * profile) in staff mode would save the staff tools as the profile's inventory: refused too.
 */
final class ProfileSwitchGuard implements Listener {

    private final FreezeService freeze;
    private final StaffMode mode;
    private final ModuleMessages msg;

    ProfileSwitchGuard(FreezeService freeze, StaffMode mode, ModuleMessages msg) {
        this.freeze = freeze;
        this.mode = mode;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwitch(ProfileSwitchEvent event) {
        if (freeze.isFrozen(event.getPlayer())) {
            event.refuse(msg.component(event.getPlayer(), "profiles.frozen"));
        } else if (mode.inStaffMode(event.getPlayer())) {
            event.refuse(msg.component(event.getPlayer(), "profiles.staff-mode"));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDelete(ProfileDeleteEvent event) {
        if (mode.inStaffMode(event.getPlayer())) {
            event.refuse(msg.component(event.getPlayer(), "profiles.staff-mode"));
        }
    }
}
