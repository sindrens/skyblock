package net.skybornmc.skyblock.shardblock;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Shard blocks (ECON-SHARDS): the ONLY source of shards (owner 1 Nov 2026). A placeable island block that pays 1 shard
 * (sometimes 2) when a player breaks it, regrows, and has two upgrades (respawn speed for crystals, double chance for
 * shards). Bounded: 4 blocks per island, 100 shards per profile and day, one break per 400 ms per player, nothing while
 * away. Settings {@code shardblock.yml}, texts {@code messages/shardblock.yml}, data {@code db/shardblock}.
 */
public final class ShardBlockModule implements SkyblockModule, ShardBlockService {

    static final String ID = "shardblock";
    static final String PERM_USE = "skyblock.shardblock.use";
    static final String PERM_VIEW = "skyblock.staff.shardblock.view";
    static final String PERM_GIVE = "skyblock.staff.shardblock.give";
    static final String PERM_REMOVE = "skyblock.staff.shardblock.remove";

    private final Core core;
    private final ShardBlockConfig config = new ShardBlockConfig();
    private ModuleMessages msg;
    private ShardBlockItems items;
    private volatile ShardRules rules;
    private ShardBlockManager manager;

    public ShardBlockModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("economy", "islands", "profiles");
    }

    @Override
    public void enable() throws Exception {
        if (core.services().optional(EconomyService.class).isEmpty()) {
            throw new IllegalStateException("EconomyService is not available (economy module disabled?)");
        }
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        msg = core.messages(ID);
        loadRules();
        items = new ShardBlockItems(core.plugin(), core.items(), key -> core.messages().raw(ID, key));
        if (!items.register(activeMaterial())) {
            throw new IllegalStateException("The item id shard_block is already used by another module");
        }
        manager = new ShardBlockManager(this, new ShardBlockStore(core.database()));
        core.plugin().getServer().getPluginManager().registerEvents(manager, core.plugin());
        manager.start();
        core.services().register(ID, ShardBlockService.class, this);
        registerCommand();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        loadRules();
        items.register(activeMaterial());
        manager.restartTasks();
    }

    @Override
    public void disable() {
        core.services().unregister(ID, ShardBlockService.class);
        if (manager != null) {
            HandlerList.unregisterAll(manager);
            manager.stop();
        }
        if (items != null) {
            items.unregister();
        }
    }

    private void loadRules() {
        rules = ShardRules.of(config, problem -> core.plugin().getLogger().warning("shardblock.yml → " + problem));
    }

    private Material activeMaterial() {
        Material m = Material.matchMaterial(config.activeMaterial);
        return m == null || !m.isItem() || !m.isBlock() ? Material.INFESTED_STONE : m;
    }

    // ------------------------------------------------------------------ ShardBlockService

    @Override
    public ItemStack create(int amount, String source) {
        return items.create(Math.max(1, amount), source, 0, 0);
    }

    // ------------------------------------------------------------------ staff command

    private void registerCommand() {
        Arg<Player> target = Args.onlinePlayer("player");
        Arg<Integer> amount = Args.integer("amount", 1, 64, "1", "4", "16");
        core.commands().register(CommandSpec.command("shardblock")
                .description("Staff tools for shard blocks").category("Staff").permission(PERM_VIEW)
                .sub(CommandSpec.sub("give")
                        .description("Give shard blocks (inventory first, the rest to /box)")
                        .permission(PERM_GIVE).arg(target).optional(amount).example("shardblock give Steve 4")
                        .executes(ctx -> give(ctx.sender(), ctx.get(target), ctx.getOr(amount, 1))))
                .sub(CommandSpec.sub("info")
                        .description("The shard block you look at: owner, island, levels, state, breaks, shards paid")
                        .permission(PERM_VIEW).playerOnly().example("shardblock info")
                        .executes(ctx -> info(ctx.player())))
                .sub(CommandSpec.sub("remove")
                        .description("Remove the shard block you look at (it goes to the placer's /box with its levels)")
                        .permission(PERM_REMOVE).playerOnly().confirm(10).example("shardblock remove")
                        .executes(ctx -> remove(ctx.player())))
                .sub(CommandSpec.sub("stats")
                        .description("Blocks placed, shards paid today and in total, islands with the most blocks")
                        .permission(PERM_VIEW).example("shardblock stats")
                        .executes(ctx -> stats(ctx.sender()))));
    }

    private void give(CommandSender sender, Player to, int amount) {
        ItemStack stack = create(amount, "staff:" + sender.getName());
        ItemBox.give(core.services(), to, BoxSource.STAFF, stack);
        Actor actor = sender instanceof Player p ? Actor.player(p) : Actor.system("console");
        core.audit().logPlayer(AuditCategory.ITEM, "shard_block_give", actor, to.getUniqueId(), to.getName(), (long) amount,
                to.getLocation(), Map.of("item", ShardBlockItems.ID, "amount", amount));
        msg.send(sender, "admin.given", Msg.number("amount", amount), Msg.p("player", to.getName()));
    }

    private void info(Player staff) {
        ShardBlockManager.Placed p = manager.looked(staff);
        if (p == null) {
            msg.send(staff, "admin.none");
            return;
        }
        long now = System.currentTimeMillis();
        String owner = core.services().optional(ProfileService.class).map(ps -> ps.ownerOf(p.placedBy))
                .map(uuid -> Optional.ofNullable(org.bukkit.Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString().substring(0, 8)))
                .orElse(String.valueOf(p.placedBy));
        String island = islands().flatMap(i -> i.island(p.island)).map(IslandService.IslandInfo::name).orElse(String.valueOf(p.island));
        msg.send(staff, "admin.info", Msg.number("id", p.id), Msg.p("owner", owner), Msg.p("island", island),
                Msg.number("respawn", p.respawnLevel), Msg.number("double", p.doubleLevel),
                Msg.c("state", p.ready(now) ? msg.component(staff, "menu.state-ready")
                        : msg.component(staff, "menu.state-regrowing", Msg.p("time", Format.duration(Duration.ofMillis(p.readyAt - now))))),
                Msg.number("breaks", p.breaks), Msg.number("shards", p.shardsPaid));
    }

    private void remove(Player staff) {
        ShardBlockManager.Placed p = manager.looked(staff);
        if (p == null) {
            msg.send(staff, "admin.none");
            return;
        }
        manager.staffRemove(staff, p);
    }

    private void stats(CommandSender sender) {
        java.util.UUID senderId = sender instanceof Player p ? p.getUniqueId() : null;
        manager.store().stats(manager.today(), config.statsTop).whenComplete((s, error) -> core.scheduler().main("shardblock-stats", () -> {
            CommandSender to = senderId == null ? core.plugin().getServer().getConsoleSender() : org.bukkit.Bukkit.getPlayer(senderId);
            if (to == null) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Shard blocks: stats failed", Database.cause(error));
                msg.send(to, "admin.failed");
                return;
            }
            msg.send(to, "admin.stats", Msg.number("blocks", s.blocks()), Msg.number("today", s.today()), Msg.number("total", s.total()));
            int rank = 1;
            for (Map.Entry<java.util.UUID, Long> e : s.top()) {
                String name = islands().flatMap(i -> i.island(e.getKey())).map(IslandService.IslandInfo::name).orElse(e.getKey().toString().substring(0, 8));
                msg.send(to, "admin.stats-island", Msg.number("rank", rank++), Msg.p("island", name), Msg.number("blocks", e.getValue()));
            }
        }));
    }

    // ------------------------------------------------------------------ helpers for the manager and the menu

    Core core() {
        return core;
    }

    ShardBlockConfig config() {
        return config;
    }

    ShardRules rules() {
        return rules;
    }

    ShardBlockItems items() {
        return items;
    }

    ShardBlockManager manager() {
        return manager;
    }

    ModuleMessages messages() {
        return msg;
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    /** A price in its currency's format ({@code 900 ◆}). */
    String price(ShardRules.Cost cost) {
        return core.services().optional(EconomyService.class).map(e -> e.format(cost.currency(), cost.amount()))
                .orElse(Format.number(cost.amount()));
    }
}
