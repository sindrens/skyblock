package net.skybornmc.skyblock.moderation;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.ban.BanListType;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.CommandService;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.ban.IpBanList;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Staff moderation commands. Currently: {@code /unban <player|ip>} (reference example for the command framework).
 * Later phases add custom punishments, staff mode, reports and appeals here.
 */
public final class ModerationModule implements SkyblockModule {

    static final String PERM_UNBAN = "skyblock.staff.unban";
    static final String PERM_NOTIFY = "skyblock.staff.notify";

    private final JavaPlugin plugin;
    private final Core core;
    private final CommandService commands;
    private final ModuleMessages msg;

    public ModerationModule(Core core, CommandService commands) {
        this.core = core;
        this.plugin = core.plugin();
        this.commands = commands;
        this.msg = core.messages("moderation");
    }

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public void enable() {
        // Banned names + banned IPs; matching is done by BanTargets (Bedrock names, case-insensitive).
        Arg<String> target = Arg.word("target", (sender, raw) -> raw, (ctx, builder) -> {
            BanTargets.suggestions(bannedNames().keySet(), builder.getRemaining()).forEach(builder::suggest);
            return builder.buildFuture();
        });
        commands.register(CommandSpec.command("unban")
                .description("Unban a player (or an IP address)")
                .category("Staff")
                .permission(PERM_UNBAN)
                .arg(target)
                .example("unban Steve")
                .executes(ctx -> unban(ctx.sender(), ctx.get(target))));
    }

    @Override
    public void reload() {
        core.messages().load("moderation");
    }

    @Override
    public void disable() {
        // Commands are unregistered by the server with the plugin.
    }

    private void unban(CommandSender sender, String target) {
        if (BanTargets.looksLikeIp(target)) {
            IpBanList ipBans = Bukkit.getBanList(BanListType.IP);
            try {
                InetAddress address = InetAddress.getByName(target);
                if (ipBans.isBanned(address)) {
                    ipBans.pardon(address);
                    done(sender, target, "unbanned-ip");
                    return;
                }
            } catch (UnknownHostException ignored) {
                // Falls through to "not banned".
            }
            send(sender, "not-banned", target);
            return;
        }

        Map<String, PlayerProfile> banned = bannedNames();
        BanTargets.match(banned.keySet(), target).ifPresentOrElse(name -> {
            ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
            bans.pardon(banned.get(name));
            done(sender, name, "unbanned");
        }, () -> send(sender, "not-banned", target));
    }

    private void done(CommandSender sender, String target, String messageKey) {
        send(sender, messageKey, target);
        String staff = sender.getName();
        plugin.getLogger().info(staff + " unbanned " + target);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(PERM_NOTIFY) && !p.equals(sender))
                .forEach(p -> msg.send(p, "staff-notify", Msg.p("staff", staff), Msg.p("player", target)));
    }

    private void send(CommandSender sender, String key, String target) {
        msg.send(sender, key, Msg.p("player", target), Msg.p("staff", sender.getName()));
    }

    /** Banned player names mapped to their profile. */
    private Map<String, PlayerProfile> bannedNames() {
        ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
        return bans.<BanEntry<PlayerProfile>>getEntries().stream()
                .map(BanEntry::getBanTarget)
                .filter(profile -> profile != null && profile.getName() != null)
                .collect(Collectors.toMap(PlayerProfile::getName, Function.identity(), (a, b) -> a));
    }
}
