package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Island tools (Complete Developer Spec decisions D1-D12, T1-T13): every profile gets a wooden pickaxe, axe, hoe and sword
 * on first join (D12); tiers wood to netherite and 7 upgrades per tool, bought with crystals in {@code /is tools} (D4, T3,
 * T7, T8); the tools only work on islands (D1), are put away in the Depths and come back on the island in the same slot
 * (T1/D6); the hoe harvests rows into its storage and the crops regrow (T9-T13, see {@link HoeFarming}).
 */
public final class IslandToolsModule implements SkyblockModule {

    public static final String ID = "islandtools";
    static final String PERM = "skyblock.islandtools.use";
    static final String PERM_ADMIN = "skyblock.islandtools.admin";

    private final Core core;
    private final IslandToolsConfig config = new IslandToolsConfig();
    private ModuleMessages msg;
    private ToolStore store;
    private ToolItems items;
    private volatile Upgrades upgrades = Upgrades.parse(List.of(), w -> { });
    private ToolListener listener;
    private HoeFarming farming;
    private HoeStorage storage;
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    /** Online profile → its tools. */
    private final Map<UUID, Map<Kind, ToolStore.Rec>> records = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    public IslandToolsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("islands", "profiles", "economy");
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        msg = core.messages(ID);
        core.messages().load(ID);
        store = new ToolStore(core.database());
        upgrades = Upgrades.parse(config.upgrades, w -> core.plugin().getLogger().warning(w));
        items = new ToolItems(this);
        items.register();
        storage = new HoeStorage(this);
        farming = new HoeFarming(this);
        listener = new ToolListener(this);
        Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        Bukkit.getPluginManager().registerEvents(farming, core.plugin());
        core.services().optional(BonusService.class).ifPresent(b -> b.register("island_tool", (family, ctx) ->
                family == BonusFamily.EXTRA_DROPS ? dropsBonus(ctx.item()) : 0));
        tasks.add(core.scheduler().repeat("islandtools:haste", 40L, 40L, listener::hasteTick));
        tasks.add(core.scheduler().repeat("islandtools:regrow", 20L, 10L, farming::tick));
        tasks.add(core.scheduler().repeat("islandtools:storage", 200L, 200L, storage::flushAll));
        registerCommands();
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        upgrades = Upgrades.parse(config.upgrades, w -> core.plugin().getLogger().warning(w));
    }

    @Override
    public void disable() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        if (storage != null) {
            storage.flushAllBlocking();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (farming != null) {
            HandlerList.unregisterAll(farming);
            farming.ripenAll();
        }
        core.services().optional(BonusService.class).ifPresent(b -> b.unregister("island_tool"));
        if (items != null) {
            items.unregister();
        }
    }

    // ------------------------------------------------------------------ access

    Core core() {
        return core;
    }

    IslandToolsConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    Upgrades upgrades() {
        return upgrades;
    }

    ToolItems items() {
        return items;
    }

    ToolStore store() {
        return store;
    }

    HoeStorage storage() {
        return storage;
    }

    HoeFarming farming() {
        return farming;
    }

    String raw(String key) {
        String s = core.messages().raw(ID, key);
        return s == null ? key : s;
    }

    UUID profile(Player p) {
        return core.services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(p.getUniqueId());
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    /** The record of a player's tool (online, loaded), or null. */
    ToolStore.Rec rec(Player p, Kind kind) {
        Map<Kind, ToolStore.Rec> map = records.get(profile(p));
        return map == null ? null : map.get(kind);
    }

    Map<Kind, ToolStore.Rec> recs(Player p) {
        return records.getOrDefault(profile(p), Map.of());
    }

    boolean loaded(Player p) {
        return records.containsKey(profile(p));
    }

    /** True when {@code stack} is this player's current copy of an island tool. */
    boolean valid(Player p, ItemStack stack) {
        Kind kind = ToolItems.kindOf(stack);
        if (kind == null) {
            return false;
        }
        ToolStore.Rec r = rec(p, kind);
        return r != null && r.profile().equals(ToolItems.ownerOf(stack)) && r.itemId().equals(ToolItems.copyOf(stack));
    }

    private double dropsBonus(ItemStack item) {
        Kind kind = ToolItems.kindOf(item);
        UUID owner = ToolItems.ownerOf(item);
        if (kind == null || owner == null) {
            return 0;
        }
        Map<Kind, ToolStore.Rec> map = records.get(owner);
        ToolStore.Rec r = map == null ? null : map.get(kind);
        if (r == null || !r.itemId().equals(ToolItems.copyOf(item))) {
            return 0;
        }
        for (Upgrades.Def u : upgrades.of(kind)) {
            if (u.effect() == Upgrades.Effect.DROPS) {
                return r.level(u.id()) * config.dropsPerLevel;
            }
        }
        return 0;
    }

    /** Level of the first upgrade with this effect on the tool (0 = none). */
    int effectLevel(ToolStore.Rec r, Upgrades.Effect effect) {
        if (r == null) {
            return 0;
        }
        for (Upgrades.Def u : upgrades.of(r.kind())) {
            if (u.effect() == effect) {
                return r.level(u.id());
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ loading, first join (D12), put away (T1)

    void load(Player p) {
        UUID profile = profile(p);
        store.load(profile).whenComplete((map, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Island tools: could not load " + p.getName(), error);
                return;
            }
            if (!p.isOnline()) {
                return;
            }
            Map<Kind, ToolStore.Rec> recs = new EnumMap<>(map);
            Map<Kind, ToolStore.Rec> fresh = new EnumMap<>(Kind.class);
            if (config.enabled) {
                for (Kind k : Kind.values()) {
                    if (!recs.containsKey(k)) {
                        ToolStore.Rec r = new ToolStore.Rec(profile, k, 0, "", UUID.randomUUID().toString(), -1, false);
                        fresh.put(k, r);
                    }
                }
            }
            if (fresh.isEmpty()) {
                records.put(profile, recs);
                storage.load(profile);
                placeForWorld(p);
                return;
            }
            store.createMissing(fresh).whenComplete((v, e2) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                if (e2 != null) {
                    core.plugin().getLogger().log(Level.WARNING, "Island tools: could not create the starter set of " + p.getName(), e2);
                    return;
                }
                recs.putAll(fresh);
                records.put(profile, recs);
                storage.load(profile);
                if (!p.isOnline()) {
                    return;
                }
                List<ItemStack> give = new ArrayList<>();
                for (ToolStore.Rec r : fresh.values()) {
                    give.add(items.create(r));
                }
                giveAll(p, give);
                msg.send(p, "starter.given");
                core.audit().logPlayer(AuditCategory.ITEM, "island_tools_starter", Actor.SYSTEM, p.getUniqueId(), p.getName(), null,
                        p.getLocation(), Map.of("profile", profile.toString()));
                placeForWorld(p);
            }));
        }));
    }

    void unload(Player p) {
        unloadProfile(profile(p));
    }

    void unloadProfile(UUID profile) {
        if (profile == null) {
            return;
        }
        storage.unload(profile);
        records.remove(profile);
    }

    boolean putAwayWorld(Player p) {
        return config.putAwayWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(p.getWorld().getName()));
    }

    /** T1/D6: in a put-away world the tools leave the inventory (slot remembered); on the island they come back. */
    void placeForWorld(Player p) {
        if (!loaded(p) || !config.enabled) {
            return;
        }
        boolean away = putAwayWorld(p);
        boolean onIsland = islands().map(i -> i.isIslandWorld(p.getWorld())).orElse(false);
        UUID profile = profile(p);
        Map<Kind, ToolStore.Rec> map = records.get(profile);
        PlayerInventory inv = p.getInventory();
        for (Kind kind : Kind.values()) {
            ToolStore.Rec r = map.get(kind);
            if (r == null) {
                continue;
            }
            if (away && !r.stored()) {
                int slot = -1;
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (ToolItems.kindOf(s) == kind && profile.equals(ToolItems.ownerOf(s))) {
                        if (slot < 0) {
                            slot = i;
                        }
                        inv.setItem(i, null);
                    }
                }
                if (slot >= 0) {
                    ToolStore.Rec next = r.withStored(true, slot);
                    map.put(kind, next);
                    store.saveStored(next);
                }
            } else if (onIsland && r.stored()) {
                ItemStack item = items.create(r);
                int slot = r.slot();
                if (slot >= 0 && slot < inv.getSize() && (inv.getItem(slot) == null || inv.getItem(slot).isEmpty())) {
                    inv.setItem(slot, item);
                } else {
                    giveAll(p, List.of(item));
                }
                ToolStore.Rec next = r.withStored(false, -1);
                map.put(kind, next);
                store.saveStored(next);
            }
        }
    }

    void giveAll(Player p, List<ItemStack> stacks) {
        core.services().optional(net.skybornmc.skyblock.common.box.ItemBox.class).ifPresentOrElse(
                box -> box.giveItems(p, stacks, net.skybornmc.skyblock.common.box.BoxSource.TOOLS),
                () -> p.getInventory().addItem(stacks.toArray(new ItemStack[0])).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left)));
    }

    /** Replaces the player's copy of a tool in place (same slot) after a change; gives one when none is carried. */
    void refreshItem(Player p, ToolStore.Rec r) {
        if (r.stored()) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ToolItems.kindOf(s) == r.kind() && r.profile().equals(ToolItems.ownerOf(s))) {
                inv.setItem(i, items.create(r));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ purchases (T7 crystals only, T8 confirm)

    /** What a purchase is: a tier-up (upgrade null) or one upgrade level. */
    record Purchase(Kind kind, String upgrade, long cost, ToolStore.Rec before, ToolStore.Rec after) {
    }

    /** The next tier-up, or null at netherite. */
    Purchase tierUp(Player p, Kind kind) {
        ToolStore.Rec r = rec(p, kind);
        if (r == null || r.tier() >= ToolRules.TIERS.size() - 1 || r.tier() >= config.tierCosts.size()) {
            return null;
        }
        return new Purchase(kind, null, config.tierCosts.get(r.tier()), r, r.with(r.tier() + 1, r.upgrades()));
    }

    /** The next level of an upgrade, or null at max. */
    Purchase upgrade(Player p, Kind kind, String id) {
        ToolStore.Rec r = rec(p, kind);
        Upgrades.Def def = upgrades.get(kind, id).orElse(null);
        if (r == null || def == null) {
            return null;
        }
        int lv = r.level(id);
        if (lv >= def.max()) {
            return null;
        }
        Map<String, Integer> next = new java.util.LinkedHashMap<>(r.levels());
        next.put(id, lv + 1);
        return new Purchase(kind, id, def.cost(lv), r, r.with(r.tier(), Upgrades.encode(next)));
    }

    /** One crystal payment and the row change in one transaction (the row must still be as shown). */
    void buy(Player p, Purchase purchase, Runnable after) {
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        if (economy == null || purchase == null || !busy.add(p.getUniqueId())) {
            return;
        }
        UUID profile = profile(p);
        UUID batch = UUID.randomUUID();
        Transaction tx = Transaction.withdraw(AccountRef.player(profile), Currency.CRYSTALS, purchase.cost(), net.skybornmc.skyblock.common.economy.TxReason.ISLAND_TOOL_UPGRADE)
                .actor(Actor.player(p)).reference("island_tool:" + batch).idempotencyKey("island_tool:" + batch)
                .detail("kind", purchase.kind().id()).detail("upgrade", purchase.upgrade() == null ? "tier" : purchase.upgrade());
        economy.executeBatch(List.of(tx), c -> ToolStore.change(c, purchase.before(), purchase.after()))
                .whenComplete((BatchResult result, Throwable error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                    busy.remove(p.getUniqueId());
                    if (error != null || result == null || !result.ok()) {
                        boolean poor = result != null && result.status() == TxResult.Status.INSUFFICIENT_FUNDS;
                        msg.send(p, poor ? "buy.poor" : "buy.failed", Msg.number("cost", purchase.cost()));
                        core.sounds().play(p, "error");
                    } else {
                        Map<Kind, ToolStore.Rec> map = records.get(profile);
                        if (map != null) {
                            map.put(purchase.kind(), purchase.after());
                        }
                        refreshItem(p, purchase.after());
                        String what = purchase.upgrade() == null ? raw("tier." + ToolRules.TIERS.get(purchase.after().tier()))
                                : raw("upgrade." + purchase.upgrade() + ".name") + " " + purchase.after().level(purchase.upgrade());
                        msg.send(p, "buy.done", Msg.p("what", what), Msg.p("tool", raw("item.name." + purchase.kind().id())),
                                Msg.number("cost", purchase.cost()));
                        core.sounds().play(p, purchase.upgrade() == null ? "level-up" : "unlock");
                        core.audit().logPlayer(AuditCategory.ITEM, "island_tool_upgrade", Actor.player(p), p.getUniqueId(), p.getName(),
                                purchase.cost(), p.getLocation(), Map.of("kind", purchase.kind().id(),
                                        "upgrade", purchase.upgrade() == null ? "tier" : purchase.upgrade(),
                                        "tier", purchase.after().tier(), "levels", purchase.after().upgrades()));
                    }
                    if (after != null) {
                        after.run();
                    }
                }));
    }

    /** A new copy (lost or broken tool): older copies stop working. */
    void newCopy(Player p, Kind kind) {
        ToolStore.Rec r = rec(p, kind);
        if (r == null) {
            return;
        }
        ToolStore.Rec next = r.withItem(UUID.randomUUID().toString()).withStored(false, -1);
        records.get(profile(p)).put(kind, next);
        store.saveItem(next);
        store.saveStored(next);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (ToolItems.kindOf(inv.getItem(i)) == kind) {
                inv.setItem(i, null);
            }
        }
        giveAll(p, List.of(items.create(next)));
        msg.send(p, "copy.done", Msg.p("tool", raw("item.name." + kind.id())));
        core.audit().logPlayer(AuditCategory.ITEM, "island_tool_copy", Actor.player(p), p.getUniqueId(), p.getName(), null,
                p.getLocation(), Map.of("kind", kind.id()));
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        core.commands().extend("is", CommandSpec.sub("tools").description("Your island tools: tiers, upgrades and the hoe storage")
                .permission(PERM).playerOnly().example("is tools")
                .executes(ctx -> open(ctx.player())));
        net.skybornmc.skyblock.common.command.Arg<Player> target = net.skybornmc.skyblock.common.command.Args.onlinePlayer("player");
        core.commands().register(CommandSpec.command("islandtools").description("Island tools (staff: reset a player's set)")
                .category("Island").permission(PERM).playerOnly().aliases("itools").example("islandtools")
                .executes(ctx -> open(ctx.player()))
                .sub(CommandSpec.sub("reset").description("Staff: reset a player's island tools to the wooden set (and empty the hoe storage)")
                        .permission(PERM_ADMIN).arg(target).example("islandtools reset Steve")
                        .executes(ctx -> reset(ctx.sender(), ctx.get(target)))));
    }

    void open(Player p) {
        if (!config.enabled) {
            throw new CommandFailure(ID, "disabled");
        }
        if (!loaded(p)) {
            throw new CommandFailure(ID, "loading");
        }
        core.menus().open(p, new ToolsMenu(this));
    }

    private void reset(org.bukkit.command.CommandSender sender, Player target) {
        UUID profile = profile(target);
        PlayerInventory inv = target.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (ToolItems.isTool(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        storage.unload(profile);
        records.remove(profile);
        store.reset(profile).whenComplete((v, e) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (target.isOnline()) {
                load(target);
            }
            msg.send(sender, "admin.reset", Msg.p("player", target.getName()));
        }));
        core.audit().logPlayer(AuditCategory.STAFF, "island_tools_reset", sender instanceof Player sp ? Actor.player(sp) : Actor.SYSTEM,
                target.getUniqueId(), target.getName(), null, null, Map.of("profile", profile.toString()));
    }
}
