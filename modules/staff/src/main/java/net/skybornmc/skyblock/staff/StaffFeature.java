package net.skybornmc.skyblock.staff;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/** Shared helpers for the staff features. */
abstract class StaffFeature {

    static final String MODULE = "staff";

    protected final Core core;
    protected final StaffConfig config;
    protected final ModuleMessages msg;
    protected final StaffStore store;

    StaffFeature(Core core, StaffConfig config, ModuleMessages msg, StaffStore store) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.store = store;
    }

    static CommandFailure fail(String key, TagResolver... placeholders) {
        return new CommandFailure(MODULE, key, placeholders);
    }

    void audit(String action, CommandSender actor, UUID target, String targetName, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.STAFF, action, Actor.of(actor), target, targetName, null,
                actor instanceof Player p ? p.getLocation() : null, details);
    }

    /** Sends {@code key} to every online staff member with the notify permission (except {@code skip}). */
    void notifyStaff(String permission, CommandSender skip, String key, TagResolver... placeholders) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(permission) && !p.equals(skip)) {
                msg.send(p, key, placeholders);
            }
        }
    }

    /** Runs a command for the player if it exists (other modules own /punish, /logs, /invsee …). */
    boolean dispatch(Player player, String commandLine) {
        String name = commandLine.split(" ", 2)[0];
        if (Bukkit.getCommandMap().getCommand(name) == null) {
            msg.send(player, "command-missing", net.skybornmc.skyblock.common.text.Msg.p("command", name));
            return false;
        }
        return player.performCommand(commandLine);
    }
}
