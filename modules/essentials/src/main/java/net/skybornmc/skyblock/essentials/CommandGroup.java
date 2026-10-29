package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** Shared helpers for the essentials command groups: messages, failures, permission checks, audit. */
abstract class CommandGroup {

    static final String MODULE = "essentials";

    protected final Core core;
    protected final EssentialsConfig config;
    protected final ModuleMessages msg;

    CommandGroup(Core core, EssentialsConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
    }

    abstract void register();

    static CommandFailure fail(String key, TagResolver... placeholders) {
        return new CommandFailure(MODULE, key, placeholders);
    }

    static Arg<Player> player(String name) {
        return Args.onlinePlayer(name);
    }

    /**
     * The target of a {@code [player]} command: the given player (needs {@code othersPermission}) or the sender
     * (must be a player).
     */
    static Player targetOrSelf(CommandSender sender, Player given, String othersPermission) {
        if (given != null && !(sender instanceof Player self && self.equals(given))) {
            if (!sender.hasPermission(othersPermission)) {
                throw fail("no-permission-others");
            }
            return given;
        }
        if (sender instanceof Player self) {
            return self;
        }
        throw fail("needs-player");
    }

    /** Staff action on a player: audit (category staff) with the actor, target and location. */
    void audit(String action, CommandSender actor, Player target, Map<String, Object> details) {
        core.audit().logPlayer(AuditCategory.STAFF, action, Actor.of(actor), target == null ? null : target.getUniqueId(),
                target == null ? null : target.getName(), null,
                target != null ? target.getLocation() : actor instanceof Player p ? p.getLocation() : null, details);
    }

    /** Tells {@code target} that {@code actor} did something to them (skipped when it is the same person). */
    void notifyTarget(CommandSender actor, Player target, String key, TagResolver... placeholders) {
        if (target != null && !(actor instanceof Player p && p.equals(target))) {
            TagResolver[] all = new TagResolver[placeholders.length + 1];
            System.arraycopy(placeholders, 0, all, 0, placeholders.length);
            all[placeholders.length] = Msg.p("staff", actor.getName());
            msg.send(target, key, all);
        }
    }

    static boolean same(CommandSender sender, Player target) {
        return sender instanceof Player p && p.equals(target);
    }
}
