package net.skybornmc.skyblock.essentials;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.OfflineNames;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff tools: gamemode, fly, speed, god, heal, feed, clear, time, weather, skull, more, /i and /give. Owner answers
 * 331-333: creative only for builders/admins in the creative worlds, god/fly/heal only in staff or build mode
 * ({@link StaffPowers}), and everything /give, /i, /more and /skull make is a staff item.
 */
final class StaffCommands extends CommandGroup implements Listener {

    /** Item ids for tab completion, built on first use (needs a running server, so not in a static initializer). */
    private static volatile List<String> materials;

    private static List<String> materials() {
        List<String> list = materials;
        if (list == null) {
            list = Arrays.stream(Material.values())
                    .filter(m -> m.isItem() && !m.isAir() && !m.isLegacy())
                    .map(m -> m.getKey().getKey())
                    .sorted()
                    .toList();
            materials = list;
        }
        return list;
    }

    private final StaffPowers powers;
    /** staff uuid → (target uuid, expires at) for /clear <player> confirmation. */
    private final Map<UUID, Map.Entry<UUID, Long>> clearConfirm = new ConcurrentHashMap<>();

    StaffCommands(Core core, EssentialsConfig config, ModuleMessages msg, StaffPowers powers) {
        super(core, config, msg);
        this.powers = powers;
    }

    @Override
    void register() {
        // Gamemode
        Arg<GameMode> mode = Arg.word("mode", (sender, raw) -> parseMode(raw),
                Suggest.of("creative", "survival", "spectator", "adventure", "c", "s", "sp", "a"));
        Arg<Player> gmTarget = player("player");
        core.commands().register(CommandSpec.command("gm")
                .description("Change game mode").category("Staff")
                .permission(Perms.GAMEMODE).arg(mode).optional(gmTarget).aliases("gamemode").example("gm c")
                .executes(ctx -> gamemode(ctx.sender(), ctx.get(mode), ctx.getOr(gmTarget, null))));
        for (GameMode gm : GameMode.values()) {
            String name = switch (gm) {
                case CREATIVE -> "gmc";
                case SURVIVAL -> "gms";
                case SPECTATOR -> "gmsp";
                case ADVENTURE -> "gma";
            };
            Arg<Player> target = player("player");
            core.commands().register(CommandSpec.command(name)
                    .description("Switch to " + gm.name().toLowerCase(Locale.ROOT) + " mode").category("Staff")
                    .permission(Perms.gamemode(gm)).optional(target)
                    .executes(ctx -> gamemode(ctx.sender(), gm, ctx.getOr(target, null))));
        }

        // Fly, speed, god
        Arg<Player> flyTarget = player("player");
        core.commands().register(CommandSpec.command("fly")
                .description("Toggle flying").category("Staff")
                .permission(Perms.FLY).optional(flyTarget)
                .executes(ctx -> fly(ctx.sender(), ctx.getOr(flyTarget, null))));
        Arg<Integer> speed = Args.integer("speed", 1, 10, "1", "2", "3", "5", "10");
        Arg<String> speedType = Args.oneOf("type", () -> List.of("fly", "walk"));
        core.commands().register(CommandSpec.command("speed")
                .description("Set your fly or walk speed (1 = normal)").category("Staff")
                .permission(Perms.SPEED).playerOnly().arg(speed).optional(speedType).example("speed 3")
                .executes(ctx -> speed(ctx.player(), ctx.get(speed), ctx.getOr(speedType, null))));
        Arg<Player> godTarget = player("player");
        core.commands().register(CommandSpec.command("god")
                .description("Toggle god mode (no damage, no hunger)").category("Staff")
                .permission(Perms.GOD).optional(godTarget)
                .executes(ctx -> god(ctx.sender(), ctx.getOr(godTarget, null))));

        // Heal, feed, clear
        Arg<Player> healTarget = player("player");
        core.commands().register(CommandSpec.command("heal")
                .description("Restore health, hunger and remove bad effects").category("Staff")
                .permission(Perms.HEAL).optional(healTarget)
                .executes(ctx -> heal(ctx.sender(), ctx.getOr(healTarget, null))));
        Arg<Player> feedTarget = player("player");
        core.commands().register(CommandSpec.command("feed")
                .description("Fill hunger").category("Staff")
                .permission(Perms.FEED).optional(feedTarget)
                .executes(ctx -> feed(ctx.sender(), ctx.getOr(feedTarget, null))));
        Arg<Player> clearTarget = player("player");
        core.commands().register(CommandSpec.command("clear")
                .description("Clear an inventory (asks to confirm for other players)").category("Staff")
                .permission(Perms.CLEAR).optional(clearTarget).aliases("ci", "clearinventory")
                .executes(ctx -> clear(ctx.sender(), ctx.getOr(clearTarget, null))));

        // World
        Arg<Long> time = Arg.word("time", (sender, raw) -> parseTime(raw),
                Suggest.of("day", "noon", "night", "midnight", "0", "6000", "13000", "18000"));
        Arg<World> timeWorld = Args.world("world");
        core.commands().register(CommandSpec.command("time")
                .description("Set the time of a world").category("Staff")
                .permission(Perms.TIME).arg(time).optional(timeWorld).example("time day")
                .executes(ctx -> time(ctx.sender(), ctx.get(time), ctx.getOr(timeWorld, null))));
        Arg<String> weather = Args.oneOf("weather", () -> List.of("clear", "rain", "thunder"));
        Arg<World> weatherWorld = Args.world("world");
        core.commands().register(CommandSpec.command("weather")
                .description("Set the weather of a world").category("Staff")
                .permission(Perms.WEATHER).arg(weather).optional(weatherWorld).example("weather clear")
                .executes(ctx -> weather(ctx.sender(), ctx.get(weather), ctx.getOr(weatherWorld, null))));

        // Items
        Arg<String> skullName = Args.word("player", Suggest.bySender(sender -> {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            for (String n : OfflineNames.names()) {
                if (!names.contains(n)) {
                    names.add(n);
                }
            }
            return names;
        }));
        core.commands().register(CommandSpec.command("skull")
                .description("Get a player's head").category("Staff")
                .permission(Perms.SKULL).playerOnly().arg(skullName).aliases("head").example("skull Notch")
                .executes(ctx -> skull(ctx.player(), ctx.get(skullName))));
        core.commands().register(CommandSpec.command("more")
                .description("Fill the stack in your hand").category("Staff")
                .permission(Perms.MORE).playerOnly()
                .executes(ctx -> more(ctx.player())));
        Arg<Material> item = material("item");
        Arg<Integer> amount = Args.integer("amount", 1, 6400, "1", "16", "32", "64");
        core.commands().register(CommandSpec.command("i")
                .description("Give yourself a vanilla item").category("Staff")
                .permission(Perms.GIVE).playerOnly().arg(item).optional(amount).aliases("item").example("i diamond_block 64")
                .executes(ctx -> give(ctx.sender(), ctx.player(), ctx.get(item), ctx.getOr(amount, 1))));
        Arg<Player> giveTarget = player("player");
        Arg<Material> giveItem = material("item");
        Arg<Integer> giveAmount = Args.integer("amount", 1, 6400, "1", "16", "32", "64");
        core.commands().register(CommandSpec.command("give")
                .description("Give a player a vanilla item").category("Staff")
                .permission(Perms.GIVE).arg(giveTarget).arg(giveItem).optional(giveAmount).example("give Steve oak_log 64")
                .executes(ctx -> give(ctx.sender(), ctx.get(giveTarget), ctx.get(giveItem), ctx.getOr(giveAmount, 1))));
    }

    // ------------------------------------------------------------------ gamemode / fly / speed / god

    static GameMode parseMode(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "c", "creative", "1" -> GameMode.CREATIVE;
            case "s", "survival", "0" -> GameMode.SURVIVAL;
            case "sp", "spectator", "3" -> GameMode.SPECTATOR;
            case "a", "adventure", "2" -> GameMode.ADVENTURE;
            default -> throw fail("gamemode.invalid", Msg.p("input", raw));
        };
    }

    private void gamemode(CommandSender sender, GameMode mode, Player given) {
        if (!sender.hasPermission(Perms.gamemode(mode))) {
            throw fail("gamemode.no-permission", Msg.p("mode", modeName(mode)));
        }
        Player target = targetOrSelf(sender, given, Perms.GAMEMODE_OTHERS);
        if (mode == GameMode.CREATIVE) {
            // Owner answer 331: builders and admins only, and only in the creative worlds (mods use spectator).
            StaffPowerRules.Creative check = powers.creative(target);
            if (check != StaffPowerRules.Creative.OK) {
                throw fail((same(sender, target) ? "creative." : "creative.other-") + check.key(), powers.worlds(),
                        Msg.p("player", target.getName()));
            }
        }
        GameMode before = target.getGameMode();
        target.setGameMode(mode);
        String name = modeName(mode);
        if (same(sender, target)) {
            msg.send(target, "gamemode.self", Msg.p("mode", name));
        } else {
            msg.send(sender, "gamemode.other", Msg.p("player", target.getName()), Msg.p("mode", name));
            notifyTarget(sender, target, "gamemode.notify", Msg.p("mode", name));
        }
        audit("gamemode", sender, target, Map.of("from", before.name(), "to", mode.name()));
    }

    private static String modeName(GameMode mode) {
        String n = mode.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /**
     * {@code /fly} (FIXES1): toggles only the flight {@code /fly} itself gave (never island fly, staff mode or freeze),
     * checks the staff/build mode rule (owner answer 333) before turning it on, and prints the real result once.
     */
    private void fly(CommandSender sender, Player given) {
        Player target = targetOrSelf(sender, given, Perms.FLY_OTHERS);
        GameMode mode = target.getGameMode();
        StaffPowerRules.FlyAction action = StaffPowerRules.fly(powers.flyOn(target.getUniqueId()),
                mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        boolean self = same(sender, target);
        if (action == StaffPowerRules.FlyAction.GAME_MODE) {
            if (self) {
                msg.send(target, "fly.game-mode", Msg.p("mode", modeName(mode)));
            } else {
                msg.send(sender, "fly.game-mode-other", Msg.p("player", target.getName()), Msg.p("mode", modeName(mode)));
            }
            return;
        }
        if (action == StaffPowerRules.FlyAction.TURN_ON) {
            powers.require(StaffPowerRules.Power.FLY, sender, target); // owner answer 333: staff or build mode only
            powers.grantFly(target);
        } else {
            powers.revokeFly(target);
        }
        StaffPowerRules.FlyText text = StaffPowerRules.flyText(powers.flyOn(target.getUniqueId()), target.getAllowFlight());
        String key = "fly." + text.key();
        String source = powers.flightSourceName(target);
        if (self) {
            msg.send(target, key, Msg.p("source", source));
        } else {
            msg.send(sender, key + "-other", Msg.p("player", target.getName()), Msg.p("source", source));
            notifyTarget(sender, target, (text == StaffPowerRules.FlyText.ENABLED ? "fly.enabled" : "fly.disabled") + "-notify");
        }
        audit(action == StaffPowerRules.FlyAction.TURN_ON ? "fly_on" : "fly_off", sender, target,
                Map.of("still_flying", text == StaffPowerRules.FlyText.DISABLED_STILL_FLYING));
    }

    private void speed(Player player, int value, String type) {
        boolean fly = type == null ? player.isFlying() : type.equals("fly");
        // 1 = vanilla (fly 0.1, walk 0.2), 10 = maximum (1.0).
        float speed = fly ? value / 10f : Math.min(1f, 0.2f + (value - 1) * (0.8f / 9f));
        if (fly) {
            player.setFlySpeed(speed);
        } else {
            player.setWalkSpeed(speed);
        }
        msg.send(player, fly ? "speed.fly" : "speed.walk", Msg.p("speed", value));
    }

    private void god(CommandSender sender, Player given) {
        Player target = targetOrSelf(sender, given, Perms.GOD_OTHERS);
        if (!powers.isGod(target.getUniqueId())) {
            powers.require(StaffPowerRules.Power.GOD, sender, target); // owner answer 333
        }
        boolean enable = powers.toggleGod(target.getUniqueId());
        String key = enable ? "god.enabled" : "god.disabled";
        if (same(sender, target)) {
            msg.send(target, key);
        } else {
            msg.send(sender, key + "-other", Msg.p("player", target.getName()));
            notifyTarget(sender, target, key + "-notify");
        }
        audit(enable ? "god_on" : "god_off", sender, target, Map.of());
    }

    boolean isGod(UUID player) {
        return powers.isGod(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && powers.isGod(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && powers.isGod(p.getUniqueId()) && event.getFoodLevel() < p.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearConfirm.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ heal / feed / clear

    private void heal(CommandSender sender, Player given) {
        Player target = targetOrSelf(sender, given, Perms.HEAL_OTHERS);
        powers.require(StaffPowerRules.Power.HEAL, sender, target); // owner answer 333
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        target.setHealth(max == null ? 20 : max.getValue());
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setFireTicks(0);
        for (PotionEffect effect : target.getActivePotionEffects()) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                target.removePotionEffect(effect.getType());
            }
        }
        if (same(sender, target)) {
            msg.send(target, "heal.self");
        } else {
            msg.send(sender, "heal.other", Msg.p("player", target.getName()));
            notifyTarget(sender, target, "heal.notify");
        }
        audit("heal", sender, target, Map.of());
    }

    private void feed(CommandSender sender, Player given) {
        Player target = targetOrSelf(sender, given, Perms.FEED_OTHERS);
        target.setFoodLevel(20);
        target.setSaturation(20f);
        if (same(sender, target)) {
            msg.send(target, "feed.self");
        } else {
            msg.send(sender, "feed.other", Msg.p("player", target.getName()));
            notifyTarget(sender, target, "feed.notify");
        }
        audit("feed", sender, target, Map.of());
    }

    private void clear(CommandSender sender, Player given) {
        Player target = targetOrSelf(sender, given, Perms.CLEAR_OTHERS);
        if (!same(sender, target)) {
            UUID staffId = sender instanceof Player p ? p.getUniqueId() : new UUID(0, 0);
            Map.Entry<UUID, Long> pending = clearConfirm.get(staffId);
            long now = System.currentTimeMillis();
            if (pending == null || !pending.getKey().equals(target.getUniqueId()) || pending.getValue() < now) {
                clearConfirm.put(staffId, Map.entry(target.getUniqueId(), now + config.clearConfirmSeconds * 1000L));
                msg.send(sender, "clear.confirm", Msg.p("player", target.getName()), Msg.p("seconds", config.clearConfirmSeconds));
                return;
            }
            clearConfirm.remove(staffId);
        }
        int items = 0;
        List<String> summary = new ArrayList<>();
        for (ItemStack stack : target.getInventory().getContents()) {
            if (stack != null && !stack.isEmpty()) {
                items += stack.getAmount();
                summary.add(stack.getType().getKey().getKey() + " x" + stack.getAmount());
            }
        }
        target.getInventory().clear();
        target.setItemOnCursor(null);
        if (same(sender, target)) {
            msg.send(target, "clear.self", Msg.p("count", items));
        } else {
            msg.send(sender, "clear.other", Msg.p("player", target.getName()), Msg.p("count", items));
            notifyTarget(sender, target, "clear.notify");
        }
        audit("clear", sender, target, Map.of("items", items, "contents", summary));
    }

    // ------------------------------------------------------------------ time / weather

    static long parseTime(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> {
                try {
                    long ticks = Long.parseLong(raw);
                    if (ticks >= 0 && ticks < 24000) {
                        yield ticks;
                    }
                } catch (NumberFormatException ignored) {
                    // falls through to the error
                }
                throw fail("time.invalid", Msg.p("input", raw));
            }
        };
    }

    private World worldOf(CommandSender sender, World given) {
        if (given != null) {
            return given;
        }
        if (sender instanceof Player p) {
            return p.getWorld();
        }
        World spawn = Bukkit.getWorld(config.spawnWorld);
        return spawn != null ? spawn : Bukkit.getWorlds().getFirst();
    }

    private void time(CommandSender sender, long ticks, World given) {
        World world = worldOf(sender, given);
        world.setTime(ticks);
        msg.send(sender, "time.set", Msg.p("ticks", ticks), Msg.p("world", world.getName()));
        audit("time", sender, null, Map.of("world", world.getName(), "ticks", ticks));
    }

    private void weather(CommandSender sender, String weather, World given) {
        World world = worldOf(sender, given);
        switch (weather) {
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "thunder" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
            default -> {
                world.setStorm(false);
                world.setThundering(false);
                world.setClearWeatherDuration(20 * 60 * 30);
            }
        }
        msg.send(sender, "weather.set", Msg.p("weather", weather), Msg.p("world", world.getName()));
        audit("weather", sender, null, Map.of("world", world.getName(), "weather", weather));
    }

    // ------------------------------------------------------------------ items

    private static Arg<Material> material(String name) {
        return Arg.word(name, (sender, raw) -> {
            String key = raw.toLowerCase(Locale.ROOT);
            if (key.startsWith("minecraft:")) {
                key = key.substring("minecraft:".length());
            }
            Material m = Material.matchMaterial(key);
            if (m == null || !m.isItem() || m.isAir()) {
                throw fail("give.unknown-item", Msg.p("input", raw));
            }
            return m;
        }, Suggest.of(StaffCommands::materials));
    }

    private void give(CommandSender sender, Player target, Material material, int amount) {
        if (amount > config.giveMaxAmount) {
            throw fail("give.too-many", Msg.p("max", config.giveMaxAmount));
        }
        int left = amount;
        int max = Math.max(1, material.getMaxStackSize());
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        while (left > 0) {
            int n = Math.min(left, max);
            left -= n;
            stacks.add(new ItemStack(material, n));
        }
        // Owner answer 332: made out of nothing by staff → hidden staff-item tag (can't be sold, traded or auctioned).
        if (config.tagStaffItems) {
            net.skybornmc.skyblock.common.item.StaffItems.markAll(stacks, "give:" + sender.getName());
        }
        // Owner answer 7: never dropped; what does not fit goes to the target's /box.
        int boxed = net.skybornmc.skyblock.common.box.ItemBox.give(core.services(), target, net.skybornmc.skyblock.common.box.BoxSource.STAFF, stacks).stream().mapToInt(ItemStack::getAmount).sum();
        String item = material.getKey().getKey();
        if (same(sender, target)) {
            msg.send(target, "give.self", Msg.p("amount", amount), Msg.p("item", item));
        } else {
            msg.send(sender, "give.other", Msg.p("amount", amount), Msg.p("item", item), Msg.p("player", target.getName()));
            notifyTarget(sender, target, "give.notify", Msg.p("amount", amount), Msg.p("item", item));
        }
        if (boxed > 0 && !same(sender, target)) {
            msg.send(sender, "give.boxed", Msg.p("amount", boxed), Msg.p("player", target.getName()));
        }
        audit("give", sender, target, Map.of("item", item, "amount", amount));
    }

    private void skull(Player player, String name) {
        if (!name.matches("[A-Za-z0-9_.]{1,17}")) {
            throw fail("skull.invalid", Msg.p("input", name));
        }
        PlayerProfile profile = Bukkit.createProfile(name);
        profile.update().whenComplete((completed, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setPlayerProfile(error == null && completed != null ? completed : profile);
                head.setItemMeta(meta);
            }
            if (config.tagStaffItems) {
                net.skybornmc.skyblock.common.item.StaffItems.mark(head, "skull:" + player.getName());
            }
            net.skybornmc.skyblock.common.box.ItemBox.give(core.services(), player, net.skybornmc.skyblock.common.box.BoxSource.STAFF, head);
            msg.send(player, "skull.given", Msg.p("player", name));
        }));
    }

    private void more(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            throw fail("more.empty");
        }
        if (net.skybornmc.skyblock.common.item.ItemRegistry.isCustom(hand)) {
            // Custom items are given by their own commands (and unique ones must never be copied).
            throw fail("more.custom");
        }
        hand.setAmount(hand.getMaxStackSize());
        if (config.tagStaffItems) {
            net.skybornmc.skyblock.common.item.StaffItems.mark(hand, "more:" + player.getName());
            player.getInventory().setItemInMainHand(hand);
        }
        msg.send(player, "more.done", Msg.p("amount", hand.getAmount()));
        audit("more", player, null, Map.of("item", hand.getType().getKey().getKey(), "amount", hand.getAmount()));
    }
}
