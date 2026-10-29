package net.skybornmc.skyblock.moderation;

import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Durations;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Issues and pardons punishments: stores them, applies them (kick/ban screen, mute cache), syncs the vanilla ban
 * list, notifies staff (clickable), announces publicly and writes the audit log. All database work is async; Bukkit
 * work hops back to the main thread.
 */
final class PunishmentService {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    /** What a staff member asked for. {@code duration == null} = permanent (ban/mute). */
    record Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                   boolean ipWide, String ip, String template, Integer ladderStep) {
    }

    private final Core core;
    private final ModuleMessages msg;
    private final PunishmentStore store;
    private final java.util.function.Supplier<ModerationConfig> config;
    private final Executor main;
    /** Active mutes of online players (loaded at login, updated on mute/unmute). */
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();

    PunishmentService(Core core, ModuleMessages msg, PunishmentStore store, java.util.function.Supplier<ModerationConfig> config) {
        this.core = core;
        this.msg = msg;
        this.store = store;
        this.config = config;
        this.main = new MainThread(core.plugin());
    }

    PunishmentStore store() {
        return store;
    }

    // ------------------------------------------------------------------ limits

    /** Longest duration {@code sender} may give; {@code null} = permanent allowed. Console = unlimited. */
    Duration maxDuration(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return null;
        }
        Duration best = parse(config.get().defaultMaxDuration);
        boolean any = false;
        for (String entry : config.get().maxDurations) {
            String[] kv = entry.split("=", 2);
            if (kv.length != 2 || !sender.hasPermission("skyblock.staff.punish.max." + kv[0].trim())) {
                continue;
            }
            Duration d = parse(kv[1].trim());
            if (!any) {
                best = d;
                any = true;
            } else if (best != null && (d == null || d.compareTo(best) > 0)) {
                best = d;
            }
        }
        return best;
    }

    private static Duration parse(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("perm") || text.equalsIgnoreCase("permanent")) {
            return null;
        }
        try {
            return Durations.parse(text);
        } catch (IllegalArgumentException e) {
            return Duration.ofDays(1);
        }
    }

    /** True if the requested duration fits the sender's limit. */
    boolean allowed(CommandSender sender, Duration requested) {
        Duration max = maxDuration(sender);
        if (max == null) {
            return true;
        }
        return requested != null && requested.compareTo(max) <= 0;
    }

    String describeMax(CommandSender sender) {
        Duration max = maxDuration(sender);
        return max == null ? text(null, "words.permanent") : Format.duration(max);
    }

    // ------------------------------------------------------------------ issue

    /** Stores and applies a punishment; completes with the stored row (on the main thread). */
    CompletableFuture<Punishment> issue(CommandSender sender, Request r) {
        long now = System.currentTimeMillis();
        UUID issuer = sender instanceof Player p ? p.getUniqueId() : null;
        Long expires = r.duration() == null || r.type() == Punishment.Type.WARN || r.type() == Punishment.Type.KICK
                ? null : now + r.duration().toMillis();
        CompletableFuture<String> ipFuture = r.ipWide() && r.ip() == null && r.target() != null
                ? store.lastIp(r.target()).thenApply(o -> o.orElse(null))
                : CompletableFuture.completedFuture(r.ip());
        return ipFuture.thenCompose(ip -> {
            if (r.ipWide() && ip == null) {
                throw new IllegalStateException("no-ip");
            }
            Punishment row = new Punishment(0, r.type(), r.target(), r.targetName(), ip, r.ipWide(), r.reason(), r.template(),
                    r.ladderStep(), issuer, sender.getName(), now, expires, r.silent(), null, null, null, null);
            return store.insert(row);
        }).thenApplyAsync(p -> {
            apply(p);
            announce(sender, p);
            audit(sender, p);
            return p;
        }, main);
    }

    /** Kicks/bans online players hit by the punishment, updates mute caches and the vanilla ban list. */
    private void apply(Punishment p) {
        for (Player online : affected(p)) {
            switch (p.type()) {
                case BAN -> online.kick(banScreen(online, p));
                case KICK -> online.kick(msg.component(online, "screen.kick", parts(online, p)));
                case MUTE -> {
                    mutes.put(online.getUniqueId(), p);
                    msg.send(online, "muted.you", parts(online, p));
                    core.sounds().play(online, "error");
                }
                case WARN -> {
                    msg.send(online, "warned.you", parts(online, p));
                    online.showTitle(net.kyori.adventure.title.Title.title(msg.component(online, "warned.title"),
                            msg.component(online, "warned.subtitle", parts(online, p))));
                    core.sounds().play(online, "warning");
                }
            }
        }
        if (p.type() == Punishment.Type.BAN && config.get().vanillaSync) {
            Date until = p.expires() == null ? null : new Date(p.expires());
            String reason = "Phoenix #" + p.id() + ": " + p.reason();
            if (p.target() != null) {
                Bukkit.getBanList(BanListType.PROFILE).addBan(Bukkit.createProfile(p.target(), p.targetName()), reason, until, p.issuerName());
            }
            if (p.ipWide() && p.ip() != null) {
                try {
                    Bukkit.getBanList(BanListType.IP).addBan(InetAddress.getByName(p.ip()), reason, until, p.issuerName());
                } catch (Exception ignored) {
                    // invalid address: our own table still has the ban
                }
            }
        }
    }

    /** Online players affected: the target, plus everyone on the same address for IP-wide punishments. */
    private List<Player> affected(Punishment p) {
        return Bukkit.getOnlinePlayers().stream().map(Player.class::cast).filter(o -> (p.target() != null && o.getUniqueId().equals(p.target()))
                || (p.ipWide() && p.ip() != null && o.getAddress() != null
                && o.getAddress().getAddress().getHostAddress().equals(p.ip()))).toList();
    }

    // ------------------------------------------------------------------ pardon

    /** Pardons active punishments; completes (main thread) with what was pardoned (empty = nothing active). */
    CompletableFuture<List<Punishment>> pardon(CommandSender sender, Punishment.Type type, UUID target, String targetName,
                                               String ip, String reason) {
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        return store.pardon(type, target, ip, by, sender.getName(), reason, type == Punishment.Type.WARN)
                .thenApplyAsync(list -> {
                    if (list.isEmpty()) {
                        return list;
                    }
                    if (type == Punishment.Type.MUTE && target != null) {
                        mutes.remove(target);
                        Player online = Bukkit.getPlayer(target);
                        if (online != null) {
                            msg.send(online, "muted.unmuted");
                        }
                    }
                    if (type == Punishment.Type.BAN && config.get().vanillaSync) {
                        if (target != null) {
                            Bukkit.getBanList(BanListType.PROFILE).pardon(Bukkit.createProfile(target, targetName));
                        }
                        for (Punishment p : list) {
                            if (p.ipWide() && p.ip() != null) {
                                try {
                                    Bukkit.getBanList(BanListType.IP).pardon(InetAddress.getByName(p.ip()));
                                } catch (Exception ignored) {
                                    // not a valid address
                                }
                            }
                        }
                        if (ip != null) {
                            try {
                                Bukkit.getBanList(BanListType.IP).pardon(InetAddress.getByName(ip));
                            } catch (Exception ignored) {
                                // not a valid address
                            }
                        }
                    }
                    String action = switch (type) {
                        case BAN -> "unban";
                        case MUTE -> "unmute";
                        case WARN -> "unwarn";
                        case KICK -> "unkick";
                    };
                    notifyStaff(sender, "notify." + action, Msg.p("player", targetName), Msg.p("reason", reason),
                            Msg.p("staff", sender.getName()), pre("target", targetName));
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("reason", reason);
                    details.put("ids", list.stream().map(Punishment::id).toList());
                    core.audit().log(AuditCategory.PUNISHMENT, action, Actor.of(sender), target == null ? ip : target.toString(),
                            targetName, null, sender instanceof Player p ? p.getLocation() : null, details);
                    return list;
                }, main);
    }

    // ------------------------------------------------------------------ mutes

    Optional<Punishment> mute(UUID player) {
        Punishment p = mutes.get(player);
        if (p != null && !p.active(System.currentTimeMillis())) {
            mutes.remove(player);
            return Optional.empty();
        }
        return Optional.ofNullable(p);
    }

    void cacheMute(UUID player, Punishment p) {
        if (p == null) {
            mutes.remove(player);
        } else {
            mutes.put(player, p);
        }
    }

    void forget(UUID player) {
        mutes.remove(player);
    }

    // ------------------------------------------------------------------ texts

    /** Placeholders describing a punishment for {@code viewer}. */
    TagResolver parts(org.bukkit.entity.Player viewer, Punishment p) {
        long now = System.currentTimeMillis();
        String expires = p.expires() == null ? text(viewer, "words.permanent")
                : Format.duration(Duration.ofMillis(Math.max(1000, p.expires() - now)));
        String length = p.expires() == null ? text(viewer, "words.permanent")
                : Format.duration(Duration.ofMillis(p.expires() - p.created()));
        return TagResolver.resolver(
                Msg.p("id", p.id()), Msg.p("reason", p.reason()), Msg.p("player", p.targetName()),
                Msg.p("staff", config.get().showStaff ? p.issuerName() : text(viewer, "words.staff")),
                Msg.p("staff_real", p.issuerName()),
                Msg.p("expires", expires), Msg.p("length", length),
                Msg.p("date", DATE.format(Instant.ofEpochMilli(p.created()))),
                Msg.p("type", text(viewer, "types." + p.type().name().toLowerCase(Locale.ROOT))),
                pre("target", p.targetName()), pre("appeal", appealUrl()));
    }

    Component banScreen(Player viewer, Punishment p) {
        return msg.component(viewer, p.expires() == null ? "screen.ban" : "screen.tempban", parts(viewer, p));
    }

    /** Ban screen for the pre-login check (no Player object yet). */
    Component banScreen(Punishment p) {
        return msg.component(Bukkit.getConsoleSender(), p.expires() == null ? "screen.ban" : "screen.tempban", parts(null, p));
    }

    String text(Player viewer, String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(msg.component(viewer == null ? Bukkit.getConsoleSender() : viewer, key));
    }

    /** Appeal link: moderation.yml appeal-url, else social.yml links.discord. */
    String appealUrl() {
        String own = config.get().appealUrl;
        if (own != null && !own.isBlank()) {
            return own;
        }
        ModuleConfig social = core.configs().loaded().get("social.yml");
        if (social != null) {
            for (SettingInfo info : core.configs().describe(social)) {
                if (info.path().equals("links.discord")) {
                    return ConfigManager.display(info.value());
                }
            }
        }
        return "discord.skybornmc.net";
    }

    /** Placeholder replaced before parsing (works inside click/hover arguments). Safe values only (names, ids, links). */
    static TagResolver pre(String name, Object value) {
        String safe = String.valueOf(value).replace("'", "").replace("<", "").replace(">", "").replace("\\", "");
        return TagResolver.resolver(name, net.kyori.adventure.text.minimessage.tag.Tag.preProcessParsed(safe));
    }

    // ------------------------------------------------------------------ notifications

    private void announce(CommandSender sender, Punishment p) {
        String type = p.type().name().toLowerCase(Locale.ROOT);
        String key = "notify." + (p.expires() != null && (p.type() == Punishment.Type.BAN || p.type() == Punishment.Type.MUTE)
                ? "temp" + type : (p.ipWide() ? "ip" + type : type));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ModerationModule.PERM_NOTIFY) || staff.equals(sender)) {
                msg.send(staff, key, parts(staff, p), Msg.p("staff", p.issuerName()), Msg.p("silent", p.silent() ? text(staff, "words.silent") : ""));
            }
        }
        if (!(sender instanceof Player)) {
            msg.send(sender, key, parts(null, p), Msg.p("staff", p.issuerName()), Msg.p("silent", p.silent() ? "(silent)" : ""));
        }
        boolean publicType = p.type() == Punishment.Type.BAN || p.type() == Punishment.Type.MUTE;
        if (config.get().announcePublic && !p.silent() && publicType) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(ModerationModule.PERM_NOTIFY)) {
                    msg.send(player, "public." + (p.type() == Punishment.Type.BAN ? "ban" : "mute"), parts(player, p));
                }
            }
        }
    }

    void notifyStaff(CommandSender sender, String key, TagResolver... placeholders) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ModerationModule.PERM_NOTIFY) || staff.equals(sender)) {
                msg.send(staff, key, placeholders);
            }
        }
        if (!(sender instanceof Player)) {
            msg.send(sender, key, placeholders);
        }
    }

    private void audit(CommandSender sender, Punishment p) {
        String action = switch (p.type()) {
            case BAN -> p.ipWide() ? "ipban" : p.expires() == null ? "ban" : "tempban";
            case MUTE -> p.expires() == null ? "mute" : "tempmute";
            case WARN -> "warn";
            case KICK -> "kick";
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", p.id());
        details.put("reason", p.reason());
        if (p.expires() != null) {
            details.put("expires", p.expires());
        }
        if (p.template() != null) {
            details.put("template", p.template());
            details.put("step", p.ladderStep());
        }
        if (p.silent()) {
            details.put("silent", true);
        }
        if (p.ip() != null) {
            details.put("ip", p.ip());
        }
        core.audit().log(AuditCategory.PUNISHMENT, action, Actor.of(sender),
                p.target() == null ? p.ip() : p.target().toString(), p.targetName(),
                p.expires() == null ? null : p.expires() - p.created(), sender instanceof Player pl ? pl.getLocation() : null, details);
    }
}
