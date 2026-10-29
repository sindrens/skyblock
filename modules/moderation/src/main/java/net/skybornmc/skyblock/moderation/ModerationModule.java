package net.skybornmc.skyblock.moderation;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.ban.IpBanList;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile YamlConfiguration config = new YamlConfiguration();

    public ModerationModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "moderation";
    }

    @Override
    public void enable() {
        loadConfig();
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
        loadConfig();
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
                .forEach(p -> p.sendMessage(mm.deserialize(message("staff-notify"),
                        Placeholder.unparsed("staff", staff), Placeholder.unparsed("player", target))));
    }

    private void send(CommandSender sender, String key, String target) {
        sender.sendMessage(mm.deserialize(message("prefix") + message(key),
                Placeholder.unparsed("player", target), Placeholder.unparsed("staff", sender.getName())));
    }

    /** Banned player names mapped to their profile. */
    private Map<String, PlayerProfile> bannedNames() {
        ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
        return bans.<BanEntry<PlayerProfile>>getEntries().stream()
                .map(BanEntry::getBanTarget)
                .filter(profile -> profile != null && profile.getName() != null)
                .collect(Collectors.toMap(PlayerProfile::getName, Function.identity(), (a, b) -> a));
    }

    private String message(String key) {
        return Objects.requireNonNullElse(config.getString("messages." + key), "");
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "moderation.yml");
        if (!file.exists()) {
            plugin.saveResource("moderation.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }
}
