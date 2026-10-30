package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.teleport.TeleportOptions;
import net.skybornmc.skyblock.common.teleport.TeleportReason;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Cooldowns;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code /spawn}, {@code /tpa} family, staff {@code /tp /tphere /tppos /top /back /home /sethome /delhome}. */
final class TeleportCommands extends CommandGroup implements Listener {

    private static final String HOMES_SETTING = "staff-homes";

    private final TeleportService teleports;
    private final TpaRequests requests;
    private final EssentialsSettings settings;
    private final Cooldowns cooldowns = new Cooldowns();
    private BukkitTask expiryTask;

    TeleportCommands(Core core, EssentialsConfig config, ModuleMessages msg, TeleportService teleports, EssentialsSettings settings) {
        super(core, config, msg);
        this.teleports = teleports;
        this.settings = settings;
        this.requests = new TpaRequests(System::currentTimeMillis, config.tpaTimeoutSeconds * 1000L, config.tpaMaxPending);
    }

    void reload() {
        requests.configure(config.tpaTimeoutSeconds * 1000L, config.tpaMaxPending);
    }

    void start() {
        expiryTask = Bukkit.getScheduler().runTaskTimer(core.plugin(), () -> requests.expire().forEach(r -> {
            Player requester = Bukkit.getPlayer(r.requester());
            Player target = Bukkit.getPlayer(r.target());
            if (requester != null) {
                msg.send(requester, "tpa.expired-sender", Msg.p("player", target == null ? "?" : target.getName()));
            }
            if (target != null) {
                msg.send(target, "tpa.expired-target", Msg.p("player", requester == null ? "?" : requester.getName()));
            }
        }), 100L, 100L);
    }

    void stop() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
    }

    @Override
    void register() {
        // /spawn
        core.commands().register(CommandSpec.command("spawn")
                .description("Teleport to spawn").category("Teleport")
                .permission(Perms.SPAWN).playerOnly()
                .executes(ctx -> spawn(ctx.player())));

        // /tpa, /tpahere
        Arg<Player> tpaTarget = player("player");
        core.commands().register(CommandSpec.command("tpa")
                .description("Ask to teleport to a player").category("Teleport")
                .permission(Perms.TPA).playerOnly().arg(tpaTarget).example("tpa Steve")
                .executes(ctx -> request(ctx.player(), ctx.get(tpaTarget), TpaRequests.Type.TO_TARGET)));
        Arg<Player> hereTarget = player("player");
        core.commands().register(CommandSpec.command("tpahere")
                .description("Ask a player to teleport to you").category("Teleport")
                .permission(Perms.TPA).playerOnly().arg(hereTarget).example("tpahere Steve")
                .executes(ctx -> request(ctx.player(), ctx.get(hereTarget), TpaRequests.Type.HERE)));

        // /tpaccept, /tpdeny [player]: suggests the people who sent you a request
        Arg<Player> acceptFrom = requester("player");
        core.commands().register(CommandSpec.command("tpaccept")
                .description("Accept a teleport request").category("Teleport")
                .permission(Perms.TPA).playerOnly().optional(acceptFrom).aliases("tpyes")
                .executes(ctx -> accept(ctx.player(), ctx.getOr(acceptFrom, null))));
        Arg<Player> denyFrom = requester("player");
        core.commands().register(CommandSpec.command("tpdeny")
                .description("Deny a teleport request").category("Teleport")
                .permission(Perms.TPA).playerOnly().optional(denyFrom).aliases("tpno")
                .executes(ctx -> deny(ctx.player(), ctx.getOr(denyFrom, null))));
        core.commands().register(CommandSpec.command("tpcancel")
                .description("Cancel the teleport requests you sent").category("Teleport")
                .permission(Perms.TPA).playerOnly()
                .executes(ctx -> cancel(ctx.player())));

        // Staff teleports
        Arg<Player> tpFirst = player("player");
        Arg<Player> tpSecond = player("target");
        core.commands().register(CommandSpec.command("tp")
                .description("Teleport to a player, or a player to another player").category("Staff")
                .permission(Perms.TP).arg(tpFirst).optional(tpSecond).aliases("teleport").example("tp Steve")
                .executes(ctx -> tp(ctx.sender(), ctx.get(tpFirst), ctx.getOr(tpSecond, null))));
        Arg<Player> hereArg = player("player");
        core.commands().register(CommandSpec.command("tphere")
                .description("Teleport a player to you").category("Staff")
                .permission(Perms.TPHERE).playerOnly().arg(hereArg).example("tphere Steve")
                .executes(ctx -> tphere(ctx.player(), ctx.get(hereArg))));
        Arg<Double> x = coordinate("x", 0);
        Arg<Double> y = coordinate("y", 1);
        Arg<Double> z = coordinate("z", 2);
        Arg<World> world = Args.world("world");
        core.commands().register(CommandSpec.command("tppos")
                .description("Teleport to coordinates").category("Staff")
                .permission(Perms.TPPOS).playerOnly().arg(x).arg(y).arg(z).optional(world).example("tppos 0 100 0 spawn")
                .executes(ctx -> tppos(ctx.player(), ctx.get(x), ctx.get(y), ctx.get(z), ctx.getOr(world, null))));
        core.commands().register(CommandSpec.command("top")
                .description("Teleport to the highest block above you").category("Staff")
                .permission(Perms.TOP).playerOnly()
                .executes(ctx -> top(ctx.player())));
        core.commands().register(CommandSpec.command("back")
                .description("Return to your location before your last teleport or death").category("Staff")
                .permission(Perms.BACK).playerOnly()
                .executes(ctx -> back(ctx.player())));

        // Staff homes (owner answer 347: /home and /sethome are the island's now, staff use /shome)
        Arg<String> homeName = homeArg("name");
        core.commands().register(CommandSpec.command("shome")
                .description("Teleport to one of your staff homes").category("Staff")
                .permission(Perms.HOME).playerOnly().optional(homeName).aliases("shomes")
                .executes(ctx -> home(ctx.player(), ctx.getOr(homeName, null))));
        Arg<String> newHome = Args.word("name", Suggest.of("home", "spawn-build", "depths", "warzone"));
        core.commands().register(CommandSpec.command("setshome")
                .description("Save your location as a staff home").category("Staff")
                .permission(Perms.HOME).playerOnly().optional(newHome)
                .executes(ctx -> setHome(ctx.player(), ctx.getOr(newHome, "home"))));
        Arg<String> delName = homeArg("name");
        core.commands().register(CommandSpec.command("delshome")
                .description("Delete a staff home").category("Staff")
                .permission(Perms.HOME).playerOnly().arg(delName)
                .executes(ctx -> delHome(ctx.player(), ctx.get(delName))));
    }

    // ------------------------------------------------------------------ spawn

    private void spawn(Player player) {
        World world = Bukkit.getWorld(config.spawnWorld);
        if (world == null) {
            throw fail("spawn.missing", Msg.p("world", config.spawnWorld));
        }
        // Warmup and cooldown: teleport.yml → spawn.* (the core TeleportService explains refusals itself).
        teleports.request(player, world.getSpawnLocation().toCenterLocation(), TeleportReason.SPAWN,
                TeleportOptions.to("spawn", () -> msg.send(player, "spawn.arrived")));
    }

    // ------------------------------------------------------------------ tpa

    private Arg<Player> requester(String name) {
        return Arg.word(name, (sender, raw) -> {
            Player p = Bukkit.getPlayerExact(raw);
            if (p == null) {
                throw fail("tpa.none-from", Msg.p("player", raw));
            }
            return p;
        }, Suggest.bySender(sender -> {
            List<String> names = new ArrayList<>();
            if (sender instanceof Player p) {
                for (UUID id : requests.requesters(p.getUniqueId())) {
                    Player r = Bukkit.getPlayer(id);
                    if (r != null) {
                        names.add(r.getName());
                    }
                }
            }
            return names;
        }));
    }

    private void request(Player sender, Player target, TpaRequests.Type type) {
        // F1: the target turned teleport requests off in /settings (self-requests keep their own message).
        // Owner answer 316: a player you /ignore can't send you /tpa or /tpahere (same answer as "requests off", so the
        // ignore list stays private; staff can't be ignored).
        if (!sender.getUniqueId().equals(target.getUniqueId()) && (!settings.acceptsTpa(target.getUniqueId())
                || net.skybornmc.skyblock.common.social.IgnoreService.ignoring(core.services(), target, sender))) {
            throw fail("tpa.disabled", Msg.p("player", target.getName()));
        }
        if (!sender.hasPermission(Perms.TELEPORT_BYPASS)) {
            Duration left = cooldowns.remaining(sender.getUniqueId(), "tpa");
            if (!left.isZero()) {
                throw fail("cooldown", Msg.p("time", Format.duration(left)));
            }
        }
        Optional<TpaRequests.Refusal> refusal = requests.create(sender.getUniqueId(), target.getUniqueId(), type);
        if (refusal.isPresent()) {
            throw fail(switch (refusal.get()) {
                case SELF -> "tpa.self";
                case ALREADY_SENT -> "tpa.already-sent";
                case TARGET_FULL -> "tpa.target-full";
            }, Msg.p("player", target.getName()));
        }
        cooldowns.set(sender.getUniqueId(), "tpa", Duration.ofSeconds(config.tpaCooldownSeconds));
        TagResolver[] p = {Msg.p("player", sender.getName()), Msg.p("seconds", config.tpaTimeoutSeconds)};
        msg.send(sender, type == TpaRequests.Type.HERE ? "tpa.sent-here" : "tpa.sent",
                Msg.p("player", target.getName()), Msg.p("seconds", config.tpaTimeoutSeconds));
        msg.send(target, type == TpaRequests.Type.HERE ? "tpa.received-here" : "tpa.received", p);
        core.sounds().play(target, "notify");
    }

    private void accept(Player target, Player from) {
        TpaRequests.Request request = requests.find(target.getUniqueId(), from == null ? null : from.getUniqueId())
                .orElseThrow(() -> fail(from == null ? "tpa.none" : "tpa.none-from",
                        Msg.p("player", from == null ? "" : from.getName())));
        requests.remove(request);
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            throw fail("tpa.requester-offline");
        }
        Player traveller = request.type() == TpaRequests.Type.HERE ? target : requester;
        Player destination = request.type() == TpaRequests.Type.HERE ? requester : target;
        msg.send(target, "tpa.accepted-target", Msg.p("player", requester.getName()));
        msg.send(requester, "tpa.accepted-sender", Msg.p("player", target.getName()));
        teleports.request(traveller, destination.getLocation(), TeleportReason.TPA, TeleportOptions.to(destination.getName(),
                () -> msg.send(traveller, "tpa.arrived", Msg.p("player", destination.getName()))));
    }

    private void deny(Player target, Player from) {
        TpaRequests.Request request = requests.find(target.getUniqueId(), from == null ? null : from.getUniqueId())
                .orElseThrow(() -> fail(from == null ? "tpa.none" : "tpa.none-from",
                        Msg.p("player", from == null ? "" : from.getName())));
        requests.remove(request);
        Player requester = Bukkit.getPlayer(request.requester());
        msg.send(target, "tpa.denied-target", Msg.p("player", requester == null ? "?" : requester.getName()));
        if (requester != null) {
            msg.send(requester, "tpa.denied-sender", Msg.p("player", target.getName()));
        }
    }

    private void cancel(Player sender) {
        List<TpaRequests.Request> sent = requests.sentBy(sender.getUniqueId());
        if (sent.isEmpty()) {
            throw fail("tpa.nothing-to-cancel");
        }
        for (TpaRequests.Request r : sent) {
            requests.remove(r);
            Player target = Bukkit.getPlayer(r.target());
            if (target != null) {
                msg.send(target, "tpa.cancelled-target", Msg.p("player", sender.getName()));
            }
        }
        msg.send(sender, "tpa.cancelled-sender", Msg.p("count", sent.size()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (TpaRequests.Request r : requests.forget(id)) {
            UUID other = r.requester().equals(id) ? r.target() : r.requester();
            Player p = Bukkit.getPlayer(other);
            if (p != null) {
                msg.send(p, "tpa.left", Msg.p("player", event.getPlayer().getName()));
            }
        }
    }

    // ------------------------------------------------------------------ staff teleports

    private void tp(CommandSender sender, Player first, Player second) {
        if (second == null) {
            if (!(sender instanceof Player self)) {
                throw fail("needs-player");
            }
            if (self.equals(first)) {
                throw fail("tp.self");
            }
            teleports.now(self, first.getLocation(), TeleportReason.STAFF,
                    TeleportOptions.to(first.getName(), () -> msg.send(self, "tp.done", Msg.p("player", first.getName()))));
            audit("tp", sender, first, Map.of());
            return;
        }
        if (!sender.hasPermission(Perms.TP_OTHERS)) {
            throw fail("no-permission-others");
        }
        if (first.equals(second)) {
            throw fail("tp.same");
        }
        teleports.now(first, second.getLocation(), TeleportReason.STAFF, TeleportOptions.to(second.getName(), () -> {
            msg.send(sender, "tp.other", Msg.p("player", first.getName()), Msg.p("target", second.getName()));
            notifyTarget(sender, first, "tp.notify", Msg.p("target", second.getName()));
        }));
        audit("tp_other", sender, first, Map.of("to", second.getName()));
    }

    private void tphere(Player staff, Player target) {
        if (staff.equals(target)) {
            throw fail("tp.self");
        }
        teleports.now(target, staff.getLocation(), TeleportReason.STAFF, TeleportOptions.to(staff.getName(), () -> {
            msg.send(staff, "tphere.done", Msg.p("player", target.getName()));
            notifyTarget(staff, target, "tphere.notify");
        }));
        audit("tphere", staff, target, Map.of());
    }

    private Arg<Double> coordinate(String name, int axis) {
        return Arg.word(name, (sender, raw) -> {
            try {
                if (raw.startsWith("~")) {
                    double base = sender instanceof Player p ? axis(p.getLocation(), axis) : 0;
                    return base + (raw.length() > 1 ? Double.parseDouble(raw.substring(1)) : 0);
                }
                double value = Double.parseDouble(raw);
                if (Math.abs(value) > 30_000_000) {
                    throw new NumberFormatException();
                }
                return value;
            } catch (NumberFormatException e) {
                throw fail("tppos.invalid", Msg.p("input", raw));
            }
        }, Suggest.bySender(sender -> sender instanceof Player p
                ? List.of("~", String.valueOf((int) Math.floor(axis(p.getLocation(), axis))))
                : List.of("0")));
    }

    private static double axis(Location loc, int axis) {
        return switch (axis) {
            case 0 -> loc.getX();
            case 1 -> loc.getY();
            default -> loc.getZ();
        };
    }

    private void tppos(Player player, double x, double y, double z, World world) {
        World w = world == null ? player.getWorld() : world;
        Location loc = new Location(w, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
        String label = String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
        teleports.now(player, loc, TeleportReason.STAFF, TeleportOptions.to(label,
                () -> msg.send(player, "tppos.done", Msg.p("position", label), Msg.p("world", w.getName()))));
        audit("tppos", player, null, Map.of("world", w.getName(), "x", x, "y", y, "z", z));
    }

    private void top(Player player) {
        Location loc = player.getLocation();
        Block highest = loc.getWorld().getHighestBlockAt(loc);
        if (highest.getType().isAir() || highest.getY() + 1 <= loc.getBlockY()) {
            throw fail("top.none");
        }
        Location dest = highest.getLocation().add(0.5, 1, 0.5);
        dest.setYaw(loc.getYaw());
        dest.setPitch(loc.getPitch());
        teleports.now(player, dest, TeleportReason.STAFF, TeleportOptions.to("top", () -> msg.send(player, "top.done")));
    }

    private void back(Player player) {
        Location loc = teleports.back(player.getUniqueId()).orElseThrow(() -> fail("back.none"));
        teleports.now(player, loc, TeleportReason.STAFF, TeleportOptions.to("back", () -> msg.send(player, "back.done")));
    }

    // ------------------------------------------------------------------ staff homes

    private Arg<String> homeArg(String name) {
        return Arg.word(name, (sender, raw) -> raw.toLowerCase(Locale.ROOT),
                Suggest.bySender(sender -> sender instanceof Player p ? homes(p).keySet() : List.of()));
    }

    /** Homes are stored in the player's settings: {@code name=world,x,y,z,yaw,pitch;name2=...}. */
    private Map<String, Location> homes(Player player) {
        Map<String, Location> out = new LinkedHashMap<>();
        Optional<PlayerData> data = core.players().get(player.getUniqueId());
        if (data.isEmpty()) {
            return out;
        }
        String raw = data.get().setting(HOMES_SETTING, "");
        for (String entry : raw.split(";")) {
            String[] kv = entry.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String[] p = kv[1].split(",");
            World w = p.length == 6 ? Bukkit.getWorld(p[0]) : null;
            if (w == null) {
                continue;
            }
            try {
                out.put(kv[0], new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        Float.parseFloat(p[4]), Float.parseFloat(p[5])));
            } catch (NumberFormatException ignored) {
                // skip a broken entry
            }
        }
        return out;
    }

    private void saveHomes(Player player, Map<String, Location> homes) {
        StringBuilder sb = new StringBuilder();
        homes.forEach((name, l) -> {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(name).append('=').append(l.getWorld().getName()).append(',')
                    .append(String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f", l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        });
        core.players().require(player).setSetting(HOMES_SETTING, sb.isEmpty() ? null : sb.toString());
    }

    private void home(Player player, String name) {
        Map<String, Location> homes = homes(player);
        if (homes.isEmpty()) {
            throw fail("home.none");
        }
        if (name == null) {
            if (homes.size() == 1) {
                name = homes.keySet().iterator().next();
            } else {
                msg.send(player, "home.list", Msg.p("homes", String.join(", ", homes.keySet())));
                return;
            }
        }
        Location loc = homes.get(name);
        if (loc == null) {
            throw fail("home.unknown", Msg.p("name", name), Msg.p("homes", String.join(", ", homes.keySet())));
        }
        String home = name;
        teleports.now(player, loc, TeleportReason.STAFF, TeleportOptions.to(home, () -> msg.send(player, "home.done", Msg.p("name", home))));
    }

    private void setHome(Player player, String name) {
        String clean = name.toLowerCase(Locale.ROOT);
        if (!clean.matches("[a-z0-9_-]{1,24}")) {
            throw fail("home.invalid-name");
        }
        Map<String, Location> homes = homes(player);
        if (!homes.containsKey(clean) && homes.size() >= config.maxHomes) {
            throw fail("home.limit", Msg.p("max", config.maxHomes));
        }
        homes.put(clean, player.getLocation().clone());
        saveHomes(player, homes);
        msg.send(player, "home.set", Msg.p("name", clean));
    }

    private void delHome(Player player, String name) {
        Map<String, Location> homes = homes(player);
        if (homes.remove(name) == null) {
            throw fail("home.unknown", Msg.p("name", name), Msg.p("homes", String.join(", ", homes.keySet())));
        }
        saveHomes(player, homes);
        msg.send(player, "home.deleted", Msg.p("name", name));
    }
}
