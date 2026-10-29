package net.skybornmc.skyblock.moderation;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.skybornmc.skyblock.common.Core;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Staff moderation commands. Currently: {@code /unban <player|ip>}.
 * Later phases add custom punishments, staff mode, reports and appeals here.
 */
public final class ModerationModule implements SkyblockModule {

    static final String PERM_UNBAN = "skyblock.staff.unban";
    static final String PERM_NOTIFY = "skyblock.staff.notify";

    private final JavaPlugin plugin;
    private final Core core;
    private final ModuleMessages msg;

    public ModerationModule(Core core) {
        this.core = core;
        this.plugin = core.plugin();
        this.msg = core.messages("moderation");
    }

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public void enable() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("unban")
                        .requires(src -> src.getSender().hasPermission(PERM_UNBAN))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BanTargets.suggestions(bannedNames().keySet(), builder.getRemaining())
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    unban(ctx.getSource(), StringArgumentType.getString(ctx, "target"));
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(),
                "Unban a player (or an IP address)",
                List.of()));
    }

    @Override
    public void reload() {
        core.messages().load("moderation");
    }

    @Override
    public void disable() {
        // Commands are unregistered by the server with the plugin.
    }

    private void unban(CommandSourceStack source, String target) {
        CommandSender sender = source.getSender();

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
