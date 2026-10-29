package net.skybornmc.skyblock.perks;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CmdContext;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Rank perks ({@code /craft}, {@code /ec}, {@code /hat}, {@code /nick}, {@code /trash}) and restricted staff tools
 * ({@code /setnick}, {@code /realname}, {@code /repair}, {@code /sudo}).
 *
 * <p>Rank perks are permission-gated; donor ranks arrive with the store (Phase 11), so for now only staff/ops have them.</p>
 */
public final class PerksModule implements SkyblockModule {

    static final String ID = "perks";

    static final String PERM_CRAFT = "skyblock.perks.craft";
    static final String PERM_EC = "skyblock.perks.enderchest";
    static final String PERM_HAT = "skyblock.perks.hat";
    static final String PERM_NICK = "skyblock.perks.nick";
    static final String PERM_NICK_COLOR = "skyblock.perks.nick.color";
    static final String PERM_NICK_HEX = "skyblock.perks.nick.hex";
    static final String PERM_NICK_BYPASS = "skyblock.perks.nick.bypass";
    static final String PERM_NICK_OTHERS = "skyblock.staff.nick.others";
    static final String PERM_REALNAME = "skyblock.staff.realname";
    static final String PERM_TRASH = "skyblock.perks.trash";
    static final String PERM_REPAIR = "skyblock.staff.repair";
    static final String PERM_REPAIR_OTHERS = "skyblock.staff.repair.others";
    static final String PERM_SUDO = "skyblock.admin.sudo";
    static final String PERM_SUDO_EXEMPT = "skyblock.sudo.exempt";
    static final String PERM_COOLDOWN_BYPASS = "skyblock.cooldown.bypass";

    private final Core core;
    private final ModuleMessages msg;
    private final PerksConfig config = new PerksConfig();
    private NickService nicks;
    private TrashMenu trash;
    private final Map<UUID, Long> lastNickChange = new java.util.concurrent.ConcurrentHashMap<>();

    public PerksModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        nicks = new NickService(core.plugin(), core.database());
        trash = new TrashMenu(core, config, msg);
        Bukkit.getPluginManager().registerEvents(nicks, core.plugin());
        Bukkit.getPluginManager().registerEvents(trash, core.plugin());
        nicks.loadOnline();
        core.placeholders().registerText(ID, "realname", p -> p == null ? "" : p.getName());
        core.placeholders().register(ID, "nick", p -> p == null ? Component.empty()
                : nicks.nick(p.getUniqueId()).map(NickService.StoredNick::component).orElse(Component.text(p.getName())));
        core.placeholders().registerText(ID, "has_nick", p -> p != null && nicks.nick(p.getUniqueId()).isPresent() ? "true" : "false");
        registerCommands();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
    }

    @Override
    public void disable() {
        if (trash != null) {
            trash.closeAll();
            HandlerList.unregisterAll(trash);
        }
        if (nicks != null) {
            HandlerList.unregisterAll(nicks);
            nicks.resetAll();
        }
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        var commands = core.commands();

        commands.register(CommandSpec.command("craft")
                .description("Open a crafting table anywhere").category("Perks")
                .permission(PERM_CRAFT).playerOnly().aliases("workbench", "wb")
                .executes(ctx -> craft(ctx.player())));

        commands.register(CommandSpec.command("ec")
                .description("Open your ender chest anywhere").category("Perks")
                .permission(PERM_EC).playerOnly().aliases("enderchest")
                .executes(ctx -> enderChest(ctx.player())));

        commands.register(CommandSpec.command("hat")
                .description("Wear the item in your hand as a hat").category("Perks")
                .permission(PERM_HAT).playerOnly()
                .executes(ctx -> hat(ctx.player())));

        Arg<String> nickArg = Arg.greedy("nickname", (sender, raw) -> raw.strip(), (ctx, builder) -> {
            CommandSender sender = ctx.getSource().getSender();
            List<String> hints = new ArrayList<>(List.of("off"));
            hints.add(sender.getName());
            if (sender.hasPermission(PERM_NICK_COLOR)) {
                hints.add("&c" + sender.getName());
            }
            if (sender.hasPermission(PERM_NICK_HEX)) {
                hints.add("<gradient:#FF3D2E:#FFD23F>" + sender.getName() + "</gradient>");
            }
            Suggest.filter(hints, builder.getRemaining()).forEach(builder::suggest);
            return builder.buildFuture();
        });
        commands.register(CommandSpec.command("nick")
                .description("Set or remove your nickname").category("Perks")
                .permission(PERM_NICK).playerOnly().aliases("nickname")
                .arg(nickArg).example("nick Phoenix")
                .executes(ctx -> nick(ctx.sender(), ctx.player(), ctx.get(nickArg))));

        Arg<Player> nickTarget = Args.onlinePlayer("player");
        Arg<String> otherNick = Arg.greedy("nickname", (sender, raw) -> raw.strip(), Suggest.of("off", "&cPhoenix",
                "<gradient:#FF3D2E:#FFD23F>Phoenix</gradient>"));
        commands.register(CommandSpec.command("setnick")
                .description("Set or remove another player's nickname").category("Staff")
                .permission(PERM_NICK_OTHERS)
                .arg(nickTarget).arg(otherNick).example("setnick Steve off")
                .executes(ctx -> nick(ctx.sender(), ctx.get(nickTarget), ctx.get(otherNick))));

        Arg<String> realArg = Args.word("nickname", Suggest.of(() -> nicks.onlineNicks()));
        commands.register(CommandSpec.command("realname")
                .description("Find the real name behind a nickname").category("Staff")
                .permission(PERM_REALNAME).aliases("whonick")
                .arg(realArg).example("realname Phoenix")
                .executes(ctx -> realName(ctx.sender(), ctx.get(realArg))));

        commands.register(CommandSpec.command("trash")
                .description("Open a trash can to destroy items").category("Perks")
                .permission(PERM_TRASH).playerOnly().aliases("disposal")
                .executes(ctx -> trash.open(ctx.player())));

        Arg<String> repairMode = Args.oneOf("mode", () -> List.of("hand", "all"));
        Arg<Player> repairTarget = Args.onlinePlayer("player");
        commands.register(CommandSpec.command("repair")
                .description("Repair vanilla items (hand or whole inventory)").category("Staff")
                .permission(PERM_REPAIR).aliases("fix")
                .optional(repairMode).optional(repairTarget).example("repair all")
                .executes(ctx -> repair(ctx, ctx.getOr(repairMode, "hand"), ctx.has(repairTarget) ? ctx.get(repairTarget) : null)));

        Arg<Player> sudoTarget = Args.onlinePlayer("player");
        Arg<String> sudoLine = Args.text("command", "c:Hello!", "spawn", "is home");
        commands.register(CommandSpec.command("sudo")
                .description("Run a command or chat as another player (owner tool, fully logged)").category("Staff")
                .permission(PERM_SUDO)
                .arg(sudoTarget).arg(sudoLine).example("sudo Steve spawn")
                .executes(ctx -> sudo(ctx.sender(), ctx.get(sudoTarget), ctx.get(sudoLine))));
    }

    // ------------------------------------------------------------------ /craft, /ec

    private void craft(Player player) {
        MenuType.CRAFTING.builder()
                .title(msg.component(player, "craft.title"))
                .checkReachable(false)
                .build(player)
                .open();
    }

    private void enderChest(Player player) {
        player.openInventory(player.getEnderChest());
        core.sounds().play(player, "menu-open");
    }

    // ------------------------------------------------------------------ /hat

    private void hat(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        if (hand.getType().isAir()) {
            throw new CommandFailure(ID, "hat.empty-hand");
        }
        String type = hand.getType().getKey().getKey();
        if (config.hatBlocked.stream().anyMatch(b -> b.equalsIgnoreCase(type))) {
            throw new CommandFailure(ID, "hat.blocked", Msg.p("item", type));
        }
        ItemStack helmet = inv.getHelmet();
        boolean hasHelmet = helmet != null && !helmet.getType().isAir();
        if (hasHelmet && helmet.containsEnchantment(Enchantment.BINDING_CURSE) && player.getGameMode() != GameMode.CREATIVE) {
            throw new CommandFailure(ID, "hat.binding");
        }
        ItemStack wear = hand.asOne();
        if (hand.getAmount() == 1) {
            // Plain swap: hand <-> helmet, nothing created or lost.
            inv.setItemInMainHand(hasHelmet ? helmet : null);
            inv.setHelmet(wear);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            inv.setItemInMainHand(hand);
            inv.setHelmet(wear);
            if (hasHelmet) {
                inv.addItem(helmet).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
        }
        msg.send(player, "hat.done", Msg.p("item", type));
        core.sounds().play(player, "success");
    }

    // ------------------------------------------------------------------ /nick, /setnick, /realname

    private NickRules.Tier tierOf(CommandSender sender) {
        if (sender.hasPermission(PERM_NICK_HEX)) {
            return NickRules.Tier.HEX;
        }
        return sender.hasPermission(PERM_NICK_COLOR) ? NickRules.Tier.COLOR : NickRules.Tier.PLAIN;
    }

    private void nick(CommandSender sender, Player target, String input) {
        boolean self = sender == target;
        if (self && config.nickCooldownSeconds > 0 && !sender.hasPermission(PERM_COOLDOWN_BYPASS)) {
            Long last = lastNickChange.get(target.getUniqueId());
            long wait = last == null ? 0 : last + config.nickCooldownSeconds * 1000L - System.currentTimeMillis();
            if (wait > 0) {
                throw new CommandFailure(ID, "nick.cooldown",
                        Msg.p("time", net.skybornmc.skyblock.common.util.Format.duration(Duration.ofMillis(wait))));
            }
        }
        if (input.equalsIgnoreCase("off") || input.equalsIgnoreCase("reset")) {
            nicks.remove(target.getUniqueId()).thenAccept(removed -> {
                core.audit().logPlayer(self ? AuditCategory.AUTH : AuditCategory.STAFF, "nick_remove", Actor.of(sender),
                        target.getUniqueId(), target.getName(), null, null, Map.of());
                lastNickChange.put(target.getUniqueId(), System.currentTimeMillis());
                if (self) {
                    msg.send(sender, removed ? "nick.removed" : "nick.none");
                } else {
                    msg.send(sender, removed ? "nick.removed-other" : "nick.none-other", Msg.p("player", target.getName()));
                    if (removed) {
                        msg.send(target, "nick.removed-by-staff");
                    }
                }
            });
            return;
        }

        NickRules.Nick nick;
        try {
            nick = NickRules.validate(input, tierOf(sender), config.nickMinLength, Math.max(config.nickMinLength, config.nickMaxLength));
        } catch (NickRules.Invalid e) {
            throw new CommandFailure(ID, e.key(), Msg.number("min", config.nickMinLength), Msg.number("max", config.nickMaxLength));
        }
        if (!sender.hasPermission(PERM_NICK_BYPASS)) {
            String reserved = NickRules.reservedWord(nick.plain(), config.nickReserved);
            if (reserved != null) {
                throw new CommandFailure(ID, "nick.reserved", Msg.p("word", reserved));
            }
        }
        if (NickRules.impersonates(nick.plain(), target.getName(), core.players().knownNames())) {
            throw new CommandFailure(ID, "nick.is-player");
        }
        String old = nicks.nick(target.getUniqueId()).map(NickService.StoredNick::formatted).orElse(null);
        String setBy = sender instanceof Player p ? p.getUniqueId().toString() : sender.getName();
        nicks.set(target.getUniqueId(), nick, setBy).whenCompleteAsync((result, error) -> {
            if (error != null) {
                core.plugin().getLogger().warning("Could not save nickname for " + target.getName() + ": " + error.getMessage());
                msg.send(sender, "nick.error");
                return;
            }
            if (result == NickService.SetResult.TAKEN) {
                msg.send(sender, "nick.taken", Msg.p("nick", nick.plain()));
                return;
            }
            lastNickChange.put(target.getUniqueId(), System.currentTimeMillis());
            core.audit().logPlayer(self ? AuditCategory.AUTH : AuditCategory.STAFF, "nick_set", Actor.of(sender),
                    target.getUniqueId(), target.getName(), null, null,
                    old == null ? Map.of("nick", nick.formatted()) : Map.of("nick", nick.formatted(), "old", old));
            Component shown = NickRules.SAFE.deserialize(nick.formatted());
            if (self) {
                msg.send(sender, "nick.set", Msg.c("nick", shown));
                if (sender instanceof Player p) {
                    core.sounds().play(p, "success");
                }
            } else {
                msg.send(sender, "nick.set-other", Msg.p("player", target.getName()), Msg.c("nick", shown));
                msg.send(target, "nick.set-by-staff", Msg.c("nick", shown));
            }
        }, new MainThread(core.plugin()));
    }

    private void realName(CommandSender sender, String nickPlain) {
        nicks.realName(nickPlain).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "realname.none", Msg.p("nick", nickPlain));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            UUID uuid = found.get();
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                msg.send(sender, "realname.found", Msg.p("nick", nickPlain), Msg.p("player", online.getName()),
                        Msg.c("status", msg.component(sender, "realname.online")));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            return core.players().offline(uuid).thenAcceptAsync(snapshot -> msg.send(sender, "realname.found",
                    Msg.p("nick", nickPlain), Msg.p("player", snapshot.map(s -> s.name()).orElse(uuid.toString())),
                    Msg.c("status", msg.component(sender, "realname.offline"))), new MainThread(core.plugin()));
        }, new MainThread(core.plugin()));
    }

    // ------------------------------------------------------------------ /repair

    private void repair(CmdContext ctx, String mode, Player explicitTarget) {
        CommandSender sender = ctx.sender();
        Player target = explicitTarget;
        if (target == null) {
            if (!(sender instanceof Player self)) {
                throw new CommandFailure(ID, "repair.console-needs-player");
            }
            target = self;
        }
        if (target != sender && !sender.hasPermission(PERM_REPAIR_OTHERS)) {
            throw new CommandFailure(ID, "repair.no-permission-others");
        }
        boolean all = mode.toLowerCase(Locale.ROOT).equals("all");
        int repaired = 0;
        if (all) {
            for (ItemStack item : target.getInventory().getContents()) {
                repaired += repairItem(item) ? 1 : 0;
            }
        } else {
            repaired = repairItem(target.getInventory().getItemInMainHand()) ? 1 : 0;
        }
        if (repaired == 0) {
            throw new CommandFailure(ID, all ? "repair.nothing-all" : "repair.nothing-hand");
        }
        core.audit().logPlayer(AuditCategory.STAFF, "repair", Actor.of(sender), target.getUniqueId(), target.getName(),
                (long) repaired, target.getLocation(), Map.of("mode", all ? "all" : "hand"));
        if (target == sender) {
            msg.send(sender, "repair.done", Msg.number("amount", repaired));
        } else {
            msg.send(sender, "repair.done-other", Msg.number("amount", repaired), Msg.p("player", target.getName()));
            msg.send(target, "repair.done-by-staff", Msg.number("amount", repaired));
        }
        if (sender instanceof Player p) {
            core.sounds().play(p, "success");
        }
    }

    /** Resets vanilla durability. Custom items are skipped (they have no durability by design). */
    private static boolean repairItem(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getType().getMaxDurability() <= 0 || ItemRegistry.isCustom(item)) {
            return false;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable) || !damageable.hasDamage()) {
            return false;
        }
        item.editMeta(Damageable.class, meta -> meta.setDamage(0));
        return true;
    }

    // ------------------------------------------------------------------ /sudo

    private void sudo(CommandSender sender, Player target, String line) {
        if (sender == target) {
            throw new CommandFailure(ID, "sudo.self");
        }
        if (target.hasPermission(PERM_SUDO_EXEMPT)) {
            throw new CommandFailure(ID, "sudo.exempt", Msg.p("player", target.getName()));
        }
        if (sender instanceof Player staff && core.ranks().weight(target) >= core.ranks().weight(staff)) {
            throw new CommandFailure(ID, "sudo.higher-rank", Msg.p("player", target.getName()));
        }
        boolean chat = line.regionMatches(true, 0, "c:", 0, 2);
        String body = chat ? line.substring(2).strip() : line.strip();
        if (!chat) {
            body = body.startsWith("/") ? body.substring(1) : body;
            String first = body.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
            String label = first.contains(":") ? first.substring(first.indexOf(':') + 1) : first;
            if (label.isEmpty() || config.sudoBlocked.stream().anyMatch(b -> b.equalsIgnoreCase(label))) {
                throw new CommandFailure(ID, "sudo.blocked", Msg.p("command", label));
            }
        }
        if (body.isEmpty()) {
            throw new CommandFailure(ID, "sudo.empty");
        }
        core.audit().logPlayer(AuditCategory.STAFF, chat ? "sudo_chat" : "sudo_command", Actor.of(sender),
                target.getUniqueId(), target.getName(), null, target.getLocation(), Map.of("line", body));
        core.plugin().getLogger().info(sender.getName() + " sudo " + target.getName() + ": " + (chat ? "c:" : "/") + body);
        if (chat) {
            target.chat(body);
            msg.send(sender, "sudo.chat-done", Msg.p("player", target.getName()), Msg.p("line", body));
        } else {
            boolean ok = target.performCommand(body);
            msg.send(sender, ok ? "sudo.command-done" : "sudo.command-failed", Msg.p("player", target.getName()), Msg.p("line", body));
        }
    }
}
