package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolLevelUpEvent;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Living tools at runtime and the published {@link ToolService}: XP gains from blocks, logs and kills (written on the item
 * at once, lore refreshed at most every {@code render.interval-ms} and at every level-up), level-up feedback, and the
 * write-behind of changed tools to the database (batched every {@code storage.save-interval-seconds}, on quit and on
 * shutdown). Main thread only, except the database work.
 */
final class ToolTracker implements ToolService {

    /** A changed tool waiting to be saved, with who had it. */
    record Pending(ToolData tool, UUID holder, String holderName) {
    }

    private final ToolsModule module;
    private final Map<UUID, Pending> dirty = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRender = new HashMap<>();

    ToolTracker(ToolsModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ ToolService

    /** TU4 (TUTORIAL §6, TQ15): the starter kit for the starter chest / Starter pack; see {@link ToolService#starterKit}. */
    @Override
    public java.util.concurrent.CompletableFuture<List<ItemStack>> starterKit(UUID profileKey, UUID holder, String holderName,
                                                                              boolean markClaimed) {
        List<net.skybornmc.skyblock.common.tools.ToolKind> kinds = module.config().starterKinds().stream()
                .filter(k -> module.catalog().tier(k, 1).isPresent()).toList();
        if (kinds.isEmpty() || profileKey == null || holder == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
        long now = System.currentTimeMillis();
        long cooldown = java.time.Duration.ofHours(module.config().starterCooldownHours).toMillis();
        java.util.concurrent.CompletableFuture<Boolean> claim = markClaimed
                ? module.core.database().transaction(c -> module.repository().claim(c, profileKey, now, cooldown) == 0)
                : java.util.concurrent.CompletableFuture.completedFuture(true);
        java.util.concurrent.CompletableFuture<List<ItemStack>> out = new java.util.concurrent.CompletableFuture<>();
        claim.whenComplete((claimed, error) -> module.core.scheduler().main("tools-starter-kit", () -> {
            try {
                if (error != null) {
                    module.core.plugin().getLogger().log(Level.WARNING, "Tools: starter kit claim for the tutorial failed", Database.cause(error));
                    out.complete(List.of());
                    return;
                }
                if (!Boolean.TRUE.equals(claimed)) {
                    out.complete(List.of()); // this profile claimed a kit within the cooldown: no second kit
                    return;
                }
                String name = holderName == null ? holder.toString().substring(0, 8) : holderName;
                int season = module.config().seasonId;
                List<ItemStack> stacks = new ArrayList<>();
                List<String> given = new ArrayList<>();
                for (net.skybornmc.skyblock.common.tools.ToolKind kind : kinds) {
                    ToolData tool = ToolData.fresh(UUID.randomUUID(), kind, 1, true, null);
                    stacks.add(module.items().create(tool, "starter:" + name, name, null));
                    given.add(kind.id());
                    module.core.database().run(c -> module.repository().insert(c, tool, holder, name, null, "starter", season,
                                    System.currentTimeMillis()))
                            .exceptionally(e -> {
                                module.core.plugin().getLogger().log(Level.WARNING, "Tools: starter tool row not saved (saved on first use)",
                                        Database.cause(e));
                                return null;
                            });
                }
                module.core.audit().logPlayer(AuditCategory.ITEM, "starter_kit", Actor.system("tutorial"), holder, name,
                        (long) given.size(), null, Map.of("pieces", given, "source", "tutorial", "profile", profileKey.toString(),
                                "claimed", markClaimed));
                out.complete(stacks);
            } catch (RuntimeException e) {
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: making the starter kit failed", e);
                out.complete(List.of());
            }
        }));
        return out;
    }

    @Override
    public Optional<ToolInfo> read(ItemStack stack) {
        return ToolItems.read(stack).map(d -> d.info(module.config().slotLevels()));
    }

    @Override
    public int maxLevel() {
        return module.curve().maxLevel();
    }

    @Override
    public double dropMultiplier(ItemStack stack) {
        return ToolItems.read(stack).map(d -> module.items().fortune(d)).orElse(1.0);
    }

    @Override
    public List<Integer> slotLevels() {
        return module.config().slotLevels();
    }

    @Override
    public boolean setPerks(Player holder, UUID toolId, List<ToolPerk> perks, Actor actor, String reason) {
        int slot = find(holder.getInventory(), toolId);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = holder.getInventory().getItem(slot);
        ToolData data = ToolItems.read(stack).orElse(null);
        if (data == null || ToolProgress.perkProblem(data, perks, slotLevels()) != null) {
            return false;
        }
        ToolData next = data.withPerks(perks);
        holder.getInventory().setItem(slot, module.items().apply(stack, next));
        markDirty(next, holder);
        module.core.audit().log(AuditCategory.ITEM, "tool_perks", actor, toolId.toString(), holder.getName(), null, holder.getLocation(),
                Map.of("before", ToolCodec.perks(data.perks()), "after", ToolCodec.perks(perks), "reason", reason == null ? "" : reason));
        return true;
    }

    @Override
    public void savePerks(Connection connection, UUID toolId, List<ToolPerk> perks) throws SQLException {
        module.repository().savePerks(connection, toolId, perks, System.currentTimeMillis());
    }

    @Override
    public void perkNames(Function<ToolPerk, String> namer) {
        module.items().perkNamer(namer);
    }

    // ------------------------------------------------------------------ XP

    /**
     * XP for an action done with the tool in the player's main hand. The base XP gets the tier bonus and the Scholar
     * ability; {@code stat} is the counter to add 1 to (blocks, logs, kills) or null.
     */
    void gain(Player player, double baseXp, String stat) {
        gain(player, baseXp, stat, 1);
    }

    /** {@link #gain(Player, double, String)} for a batch: the stat grows by {@code statAmount} (stacked kills, E3). */
    void gain(Player player, double baseXp, String stat, long statAmount) {
        PlayerInventory inv = player.getInventory();
        int slot = inv.getHeldItemSlot();
        ItemStack stack = inv.getItem(slot);
        Optional<ToolData> read = ToolItems.read(stack);
        if (read.isEmpty() || stack == null) {
            return;
        }
        ToolData data = read.get();
        double xp = baseXp * (1 + Math.max(0, data.tier() - 1) * module.config().tierBonusPercent / 100.0);
        if (ToolItems.ABILITY_SCHOLAR.equals(data.ability())) {
            xp *= 1 + module.catalog().ability(ToolItems.ABILITY_SCHOLAR).map(ToolCatalog.AbilityDef::value).orElse(0.0);
        }
        // Tool XP family (Tempering and later boosters), capped once in the BonusService (ENCHANTS §8.1).
        ToolData forBonus = data;
        xp *= module.core.services().optional(net.skybornmc.skyblock.common.bonus.BonusService.class)
                .map(b -> b.multiplier(net.skybornmc.skyblock.common.bonus.BonusFamily.TOOL_XP,
                        new net.skybornmc.skyblock.common.bonus.BonusContext(player, stack, forBonus.info(slotLevels()), forBonus.kind().skill().id())))
                .orElse(1.0);
        ToolProgress.Gain gain = ToolProgress.addXp(data, xp, module.curve());
        ToolData next = stat == null ? gain.tool() : gain.tool().addStat(stat, statAmount);
        ToolItems.writeProgress(stack, next);
        long now = System.currentTimeMillis();
        if (gain.levelledUp()) {
            module.items().refresh(stack, next);
            lastRender.put(next.id(), now);
            levelUp(player, stack, next, gain.fromLevel());
        } else if (now - lastRender.getOrDefault(next.id(), 0L) >= module.config().renderIntervalMs) {
            module.core.items().render(stack);
            lastRender.put(next.id(), now);
        }
        inv.setItem(slot, stack);
        markDirty(next, player);
    }

    private void levelUp(Player player, ItemStack stack, ToolData tool, int from) {
        String name = module.items().displayName(tool, stack);
        module.msg().actionBar(player, "level-up.action-bar", Msg.mm("tool", name), Msg.p("level", tool.level()));
        int slots = tool.slots(slotLevels());
        int before = net.skybornmc.skyblock.common.tools.ToolSlots.unlocked(from, tool.prestige(), slotLevels());
        if (slots > before) {
            module.msg().send(player, "level-up.slot", Msg.mm("tool", name), Msg.p("slot", slots), Msg.p("level", tool.level()));
        }
        if (tool.level() >= maxLevel() && module.config().prestigeEnabled) {
            module.msg().send(player, "level-up.max", Msg.mm("tool", name));
        }
        if (!module.config().soundLevelUp.isBlank()) {
            module.core.sounds().play(player, module.config().soundLevelUp);
        }
        module.core.audit().log(AuditCategory.ITEM, "tool_level_up", Actor.player(player), tool.id().toString(), player.getName(),
                (long) tool.level(), null, Map.of("from", from, "to", tool.level(), "kind", tool.kind().id(), "tier", tool.tier()));
        Bukkit.getPluginManager().callEvent(new ToolLevelUpEvent(player, tool.info(slotLevels()), from));
    }

    // ------------------------------------------------------------------ saving

    void markDirty(ToolData tool, Player holder) {
        dirty.put(tool.id(), new Pending(tool, holder == null ? null : holder.getUniqueId(), holder == null ? null : holder.getName()));
    }

    /** Saves every changed tool in one background transaction (failed batches are queued again). */
    void flushAsync() {
        if (dirty.isEmpty() || !module.core.database().isRunning()) {
            return;
        }
        List<Pending> batch = drain();
        int season = module.config().seasonId;
        module.core.database().transaction(c -> {
            long now = System.currentTimeMillis();
            for (Pending p : batch) {
                module.repository().save(c, p.tool(), p.holder(), p.holderName(), season, now);
            }
            return null;
        }).whenComplete((ok, error) -> {
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: could not save " + batch.size() + " tool(s); retrying later",
                        Database.cause(error));
                batch.forEach(p -> dirty.putIfAbsent(p.tool().id(), p));
            }
        });
    }

    /** Shutdown: saves everything on this thread. */
    void flushBlocking() {
        if (dirty.isEmpty() || !module.core.database().isRunning()) {
            return;
        }
        List<Pending> batch = drain();
        int season = module.config().seasonId;
        try {
            module.core.database().blockingTransaction(c -> {
                long now = System.currentTimeMillis();
                for (Pending p : batch) {
                    module.repository().save(c, p.tool(), p.holder(), p.holderName(), season, now);
                }
                return null;
            });
        } catch (RuntimeException e) {
            module.core.plugin().getLogger().log(Level.SEVERE, "Tools: could not save " + batch.size() + " tool(s) at shutdown", Database.cause(e));
        }
    }

    private List<Pending> drain() {
        List<Pending> batch = new ArrayList<>();
        for (UUID id : new ArrayList<>(dirty.keySet())) {
            Pending p = dirty.remove(id);
            if (p != null) {
                batch.add(p);
            }
        }
        return batch;
    }

    /** Renders the tools of a player that still show old lore (on quit, so the item is current wherever it goes). */
    void renderAll(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            ToolItems.read(stack).ifPresent(d -> {
                if (lastRender.remove(d.id()) != null) {
                    module.core.items().render(stack);
                }
            });
        }
    }

    // ------------------------------------------------------------------ lookup helpers

    /** Inventory slot of the tool with this id, or -1. */
    static int find(PlayerInventory inv, UUID toolId) {
        for (int i = 0; i < inv.getSize(); i++) {
            Optional<ToolData> d = ToolItems.read(inv.getItem(i));
            if (d.isPresent() && d.get().id().equals(toolId)) {
                return i;
            }
        }
        return -1;
    }

    /** Every custom tool in the inventory by slot. */
    static Map<Integer, ToolData> tools(PlayerInventory inv) {
        Map<Integer, ToolData> out = new LinkedHashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            int slot = i;
            ToolItems.read(inv.getItem(i)).ifPresent(d -> out.put(slot, d));
        }
        return out;
    }
}
