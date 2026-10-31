package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.satchel.SatchelService;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The Forge (Blacksmith, {@code /forge}) and the Quartermaster (starter kit): forging the next tier from your current
 * tool (progress kept), crafting blueprint tools and claiming the starter kit.
 *
 * <p>Safety: one action per player at a time; the money is taken first in one atomic economy transaction (with an
 * idempotency key), then on the main thread the tool, blueprint and materials are checked again and taken in the same
 * tick. If anything is gone by then, the money is refunded (ledger reason {@code forge_refund}); nothing is ever lost or
 * created twice. Blueprint tools are inserted with the blueprint's unique id, which the database accepts only once.</p>
 */
final class Forge {

    static final String REASON_FORGE = "forge";
    static final String REASON_BLUEPRINT = "forge_blueprint";
    static final String REASON_REFUND = "forge_refund";

    /** What a Forge tier entry shows for a player. */
    enum State {
        OWNED, CRAFTABLE, NEEDS_TOOL, NEEDS_SKILL, NEEDS_MATERIALS, NEEDS_MONEY
    }

    private final ToolsModule module;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    /** Profile key that paid, per idempotency key: a refund always goes back to the profile that paid (P4), even after a switch. */
    private final Map<String, UUID> payers = new ConcurrentHashMap<>();

    Forge(ToolsModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ inventory helpers

    /** Item id of a stack for recipes: the custom item id, or the vanilla material id for plain items. */
    static String materialId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Optional<String> custom = ItemRegistry.idOf(stack);
        if (custom.isPresent()) {
            return custom.get();
        }
        if (stack.hasItemMeta() && (stack.getItemMeta().hasDisplayName() || stack.getItemMeta().hasLore())) {
            return null; // renamed/lored vanilla items don't count (they may be other plugins' items)
        }
        return stack.getType().getKey().getKey();
    }

    /** How many of each recipe material the inventory holds. */
    static Map<String, Integer> count(PlayerInventory inv, Set<String> ids) {
        Map<String, Integer> out = new HashMap<>();
        for (ItemStack stack : inv.getStorageContents()) {
            String id = materialId(stack);
            if (id != null && ids.contains(id)) {
                out.merge(id, stack.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * How many of each recipe material the player has: inventory plus the Depths satchel (M4.4; Forge materials are
     * Depths drops, so they mostly sit in the satchel).
     */
    Map<String, Integer> count(Player player, Set<String> ids) {
        Map<String, Integer> out = count(player.getInventory(), ids);
        Optional<SatchelService> satchel = module.core.services().optional(SatchelService.class);
        if (satchel.isPresent() && satchel.get().loaded(player)) {
            for (String id : ids) {
                long held = satchel.get().amount(player, id);
                if (held > 0) {
                    out.merge(id, (int) Math.min(Integer.MAX_VALUE, held), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
                }
            }
        }
        return out;
    }

    /**
     * Takes the recipe materials: from the inventory first, the rest from the satchel (all or nothing). Returns false and
     * takes nothing if they are not all there any more (call it right after {@link #count(Player, Set)} in the same tick).
     */
    boolean takeMaterials(Player player, Map<String, Integer> materials) {
        PlayerInventory inv = player.getInventory();
        Map<String, Integer> inInventory = count(inv, materials.keySet());
        Map<String, Integer> fromInventory = new LinkedHashMap<>();
        Map<String, Integer> fromSatchel = new LinkedHashMap<>();
        materials.forEach((id, need) -> {
            int inv0 = Math.min(need, inInventory.getOrDefault(id, 0));
            if (inv0 > 0) {
                fromInventory.put(id, inv0);
            }
            if (need - inv0 > 0) {
                fromSatchel.put(id, need - inv0);
            }
        });
        if (!fromSatchel.isEmpty()) {
            Optional<SatchelService> satchel = module.core.services().optional(SatchelService.class);
            if (satchel.isEmpty() || !satchel.get().consume(player, fromSatchel, "forge", Actor.player(player))) {
                return false;
            }
        }
        take(inv, fromInventory);
        return true;
    }

    /** Removes the recipe materials (call only after {@link #count} showed they are all there). */
    static void take(PlayerInventory inv, Map<String, Integer> materials) {
        Map<String, Integer> left = new LinkedHashMap<>(materials);
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            String id = materialId(stack);
            Integer need = id == null ? null : left.get(id);
            if (need == null || need <= 0) {
                continue;
            }
            int used = Math.min(need, stack.getAmount());
            left.put(id, need - used);
            if (used >= stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - used);
            }
        }
        inv.setStorageContents(contents);
    }

    /** The tool types the player has in the inventory or ender chest (for the starter kit). */
    static Set<ToolKind> kindsOwned(Player player) {
        Set<ToolKind> out = EnumSet.noneOf(ToolKind.class);
        for (ItemStack s : player.getInventory().getContents()) {
            ToolItems.read(s).ifPresent(d -> out.add(d.kind()));
        }
        for (ItemStack s : player.getEnderChest().getContents()) {
            ToolItems.read(s).ifPresent(d -> out.add(d.kind()));
        }
        return out;
    }

    /** Highest tier of a tool type in the inventory (0 = none). */
    static int bestTier(PlayerInventory inv, ToolKind kind) {
        return ToolTracker.tools(inv).values().stream().filter(d -> d.kind() == kind).mapToInt(ToolData::tier).max().orElse(0);
    }

    /** The tool to upgrade to {@code target}: one of tier target-1 (the held one first, then the highest level). */
    static Optional<Map.Entry<Integer, ToolData>> baseTool(PlayerInventory inv, ToolKind kind, int target) {
        int held = inv.getHeldItemSlot();
        return ToolTracker.tools(inv).entrySet().stream()
                .filter(e -> e.getValue().kind() == kind && e.getValue().tier() == target - 1)
                .max(Comparator.<Map.Entry<Integer, ToolData>>comparingInt(e -> e.getKey() == held ? 1 : 0)
                        .thenComparingDouble(e -> e.getValue().xp()));
    }

    int skillLevel(Player player, ToolKind kind) {
        Optional<SkillService> skills = module.core.services().optional(SkillService.class);
        if (skills.isEmpty() || !skills.get().available(kind.skill())) {
            return Integer.MAX_VALUE; // no skill system (or skill not open yet): no skill requirement
        }
        return skills.get().level(player, kind.skill());
    }

    State state(Player player, ToolCatalog.TierDef def) {
        PlayerInventory inv = player.getInventory();
        if (bestTier(inv, def.kind()) >= def.tier()) {
            return State.OWNED;
        }
        if (def.tier() == 1 || baseTool(inv, def.kind(), def.tier()).isEmpty()) {
            return State.NEEDS_TOOL;
        }
        if (skillLevel(player, def.kind()) < def.skillLevel()) {
            return State.NEEDS_SKILL;
        }
        if (!def.recipe().missing(count(player, def.recipe().materials().keySet())).isEmpty()) {
            return State.NEEDS_MATERIALS;
        }
        if (def.recipe().money() > 0 && money(player) < def.recipe().money()) {
            return State.NEEDS_MONEY;
        }
        return State.CRAFTABLE;
    }

    /** Cached balance for display (the real check is the atomic withdraw). */
    long money(Player player) {
        return module.core.services().optional(EconomyService.class)
                .map(e -> e.cached(player.getUniqueId(), Currency.MONEY).orElse(0L)).orElse(0L);
    }

    String formatMoney(long amount) {
        return module.core.services().optional(EconomyService.class).map(e -> e.format(Currency.MONEY, amount))
                .orElse("$" + net.skybornmc.skyblock.common.util.Format.number(amount));
    }

    // ------------------------------------------------------------------ forge an upgrade

    void forge(Player player, ToolKind kind, int target) {
        Optional<ToolCatalog.TierDef> found = module.catalog().tier(kind, target);
        if (found.isEmpty() || target < 2) {
            fail(player, "forge.unknown-tier");
            return;
        }
        if (!module.forgeOpen(kind)) {
            // G2: hoe tiers wait for farming (crops.yml → enabled), rod tiers for fishing (M7.8).
            fail(player, "forge.kind-closed", Msg.c("kind", module.items().kindPlural(player, kind)));
            return;
        }
        ToolCatalog.TierDef def = found.get();
        Optional<Map.Entry<Integer, ToolData>> base = baseTool(player.getInventory(), kind, target);
        if (base.isEmpty()) {
            fail(player, "forge.needs-tool", Msg.mm("tool", module.items().tierName(kind, target - 1)));
            return;
        }
        int skill = skillLevel(player, kind);
        if (skill < def.skillLevel()) {
            fail(player, "forge.needs-skill", Msg.c("skill", module.core.messages(ToolsModule.ID).component(player, "skill." + kind.skill().id())),
                    Msg.p("level", def.skillLevel()), Msg.p("have", skill));
            return;
        }
        Map<String, Integer> missing = def.recipe().missing(count(player, def.recipe().materials().keySet()));
        if (!missing.isEmpty()) {
            fail(player, "forge.needs-materials", Msg.p("materials", describe(missing)));
            return;
        }
        if (!busy.add(player.getUniqueId())) {
            fail(player, "forge.busy");
            return;
        }
        UUID toolId = base.get().getValue().id();
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        String key = "forge:" + toolId + ":" + target + ":" + nonce();
        pay(player, def.recipe().money(), REASON_FORGE, "tool:" + toolId, key,
                Map.of("tool", kind.id(), "to_tier", target)).whenComplete((result, error) -> module.core.scheduler().main("tools-forge", () -> {
            try {
                Player p = Bukkit.getPlayer(playerId);
                if (error != null || result == null || !result.ok()) {
                    if (p != null) {
                        moneyFailed(p, result, def.recipe().money());
                    }
                    return;
                }
                if (p == null) {
                    refund(playerId, name, def.recipe().money(), key, "offline");
                    return;
                }
                completeForge(p, def, toolId, key);
            } finally {
                busy.remove(playerId);
                payers.remove(key);
            }
        }));
    }

    private void completeForge(Player player, ToolCatalog.TierDef def, UUID toolId, String key) {
        PlayerInventory inv = player.getInventory();
        int slot = ToolTracker.find(inv, toolId);
        ToolData tool = slot < 0 ? null : ToolItems.read(inv.getItem(slot)).orElse(null);
        Map<String, Integer> missing = def.recipe().missing(count(player, def.recipe().materials().keySet()));
        if (tool == null || tool.tier() != def.tier() - 1 || !missing.isEmpty() || !takeMaterials(player, def.recipe().materials())) {
            refund(player.getUniqueId(), player.getName(), def.recipe().money(), key, "items_moved");
            fail(player, "forge.items-moved");
            return;
        }
        ToolData upgraded = ToolProgress.upgrade(tool, def.tier());
        ItemStack stack = module.items().apply(inv.getItem(slot), upgraded);
        inv.setItem(slot, stack);
        module.tracker().markDirty(upgraded, player);
        String name = module.items().displayName(upgraded, stack);
        module.msg().send(player, "forge.done", Msg.mm("tool", name), Msg.p("level", upgraded.level()));
        sound(player, module.config().soundForge);
        // PHASE-10 M10.7: quests (and later the pass) count a forge upgrade.
        Bukkit.getPluginManager().callEvent(new net.skybornmc.skyblock.common.quest.GameActionEvent(player,
                net.skybornmc.skyblock.common.quest.GameActionEvent.FORGE, 1));
        module.core.audit().log(AuditCategory.ITEM, "tool_forge", Actor.player(player), toolId.toString(), player.getName(),
                def.recipe().money(), player.getLocation(), Map.of("kind", def.kind().id(), "from_tier", tool.tier(), "to_tier", def.tier(),
                        "level", upgraded.level(), "materials", def.recipe().materials().toString()));
        int season = module.config().seasonId;
        UUID holder = player.getUniqueId();
        String holderName = player.getName();
        module.core.database().transaction(c -> module.repository().upgrade(c, upgraded, tool.tier(), holder, holderName, season,
                System.currentTimeMillis())).whenComplete((ok, error) -> {
            if (error == null && Boolean.TRUE.equals(ok)) {
                // SECFIX1 F-01c: money (paid first) and the tool row are in the database now; the player file follows
                // right away (materials gone, new tier) instead of at the next autosave, so a crash loses at worst.
                module.core.scheduler().main("tools-forge-save", () -> net.skybornmc.skyblock.common.player.PlayerSaves.save(
                        Bukkit.getPlayer(holder), "a forge upgrade"));
            }
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: forge of " + toolId + " not saved yet (will retry)", Database.cause(error));
            } else if (!ok) {
                // The stored tier was not the one we upgraded from: another copy of this tool was forged first (a dupe).
                module.core.audit().log(AuditCategory.ITEM, "tool_forge_conflict", Actor.system("tools"), toolId.toString(), holderName,
                        null, null, Map.of("to_tier", def.tier()));
                module.core.plugin().getLogger().warning("Tools: tool " + toolId + " was forged twice to tier " + def.tier()
                        + " (duplicated item?). Held by " + holderName + ".");
            }
        });
    }

    // ------------------------------------------------------------------ blueprints

    void craftBlueprint(Player player, UUID blueprintItem) {
        PlayerInventory inv = player.getInventory();
        int slot = blueprintSlot(inv, blueprintItem);
        Optional<ToolCatalog.BlueprintDef> found = slot < 0 ? Optional.empty()
                : ToolItems.readBlueprint(inv.getItem(slot)).flatMap(e -> module.catalog().blueprint(e.getKey()));
        if (found.isEmpty()) {
            fail(player, "blueprint.not-found");
            return;
        }
        ToolCatalog.BlueprintDef def = found.get();
        int needed = module.catalog().tier(def.kind(), def.tier()).map(ToolCatalog.TierDef::skillLevel).orElse(0);
        int skill = skillLevel(player, def.kind());
        if (skill < needed) {
            fail(player, "forge.needs-skill", Msg.c("skill", module.core.messages(ToolsModule.ID).component(player, "skill." + def.kind().skill().id())),
                    Msg.p("level", needed), Msg.p("have", skill));
            return;
        }
        Map<String, Integer> missing = def.recipe().missing(count(player, def.recipe().materials().keySet()));
        if (!missing.isEmpty()) {
            fail(player, "forge.needs-materials", Msg.p("materials", describe(missing)));
            return;
        }
        if (!busy.add(player.getUniqueId())) {
            fail(player, "forge.busy");
            return;
        }
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        ToolData tool = ToolData.fresh(UUID.randomUUID(), def.kind(), def.tier(), false, def.ability());
        int season = module.config().seasonId;
        String key = "blueprint:" + blueprintItem + ":" + nonce();
        pay(player, def.recipe().money(), REASON_BLUEPRINT, "blueprint:" + blueprintItem, key, Map.of("blueprint", def.id()))
                .thenCompose(result -> {
                    if (result == null || !result.ok()) {
                        return CompletableFuture.completedFuture(new Object[]{result, null});
                    }
                    // The blueprint's unique id goes into the row: a duplicated blueprint can never craft a second tool.
                    return module.core.database().transaction(c -> {
                        module.repository().insert(c, tool, playerId, name, blueprintItem, "blueprint:" + def.id(), season, System.currentTimeMillis());
                        return Boolean.TRUE;
                    }).handle((ok, error) -> new Object[]{result, error == null ? Boolean.TRUE : Database.cause(error)});
                })
                .whenComplete((pair, error) -> module.core.scheduler().main("tools-blueprint", () -> {
                    try {
                        TxResult result = pair == null ? null : (TxResult) pair[0];
                        Player p = Bukkit.getPlayer(playerId);
                        if (error != null || result == null || !result.ok()) {
                            if (p != null) {
                                moneyFailed(p, result, def.recipe().money());
                            }
                            return;
                        }
                        Object insert = pair[1];
                        if (insert instanceof ToolRepository.BlueprintUsedException) {
                            refund(playerId, name, def.recipe().money(), key, "blueprint_used");
                            module.core.audit().log(AuditCategory.ITEM, "blueprint_reused", Actor.player(playerId, name), blueprintItem.toString(),
                                    def.id(), null, null, Map.of("blueprint", def.id()));
                            if (p != null) {
                                confiscateBlueprint(p, blueprintItem);
                                fail(p, "blueprint.already-used");
                            }
                            return;
                        }
                        if (insert instanceof Throwable t) {
                            module.core.plugin().getLogger().log(Level.WARNING, "Tools: blueprint craft failed for " + name, t);
                            refund(playerId, name, def.recipe().money(), key, "db_error");
                            if (p != null) {
                                fail(p, "error");
                            }
                            return;
                        }
                        if (p == null || !completeBlueprint(p, def, blueprintItem, tool)) {
                            refund(playerId, name, def.recipe().money(), key, p == null ? "offline" : "items_moved");
                            module.core.database().run(c -> module.repository().delete(c, tool.id()));
                            if (p != null) {
                                fail(p, "forge.items-moved");
                            }
                        }
                    } finally {
                        busy.remove(playerId);
                        payers.remove(key);
                    }
                }));
    }

    private boolean completeBlueprint(Player player, ToolCatalog.BlueprintDef def, UUID blueprintItem, ToolData tool) {
        PlayerInventory inv = player.getInventory();
        int slot = blueprintSlot(inv, blueprintItem);
        if (slot < 0 || !def.recipe().missing(count(player, def.recipe().materials().keySet())).isEmpty()
                || !takeMaterials(player, def.recipe().materials())) {
            return false;
        }
        inv.setItem(slot, null);
        ItemStack stack = module.items().create(tool, "blueprint:" + def.id(), player.getName(), def.id());
        give(player, stack);
        // SECFIX1 F-01c: the blueprint is used up in the database; save the file without it (and with the new tool) now.
        net.skybornmc.skyblock.common.player.PlayerSaves.save(player, "a blueprint craft");
        module.msg().send(player, "blueprint.crafted", Msg.mm("tool", module.items().displayName(tool, stack)),
                Msg.mm("ability", module.items().abilityName(def.ability())));
        sound(player, module.config().soundForge);
        module.core.audit().log(AuditCategory.ITEM, "blueprint_craft", Actor.player(player), tool.id().toString(), player.getName(),
                def.recipe().money(), player.getLocation(), Map.of("blueprint", def.id(), "blueprint_item", blueprintItem.toString(),
                        "kind", def.kind().id(), "tier", def.tier(), "ability", def.ability()));
        return true;
    }

    private void confiscateBlueprint(Player player, UUID blueprintItem) {
        int slot = blueprintSlot(player.getInventory(), blueprintItem);
        if (slot >= 0) {
            player.getInventory().setItem(slot, null);
        }
    }

    static int blueprintSlot(PlayerInventory inv, UUID blueprintItem) {
        for (int i = 0; i < inv.getSize(); i++) {
            Optional<Map.Entry<String, UUID>> bp = ToolItems.readBlueprint(inv.getItem(i));
            if (bp.isPresent() && bp.get().getValue().equals(blueprintItem)) {
                return i;
            }
        }
        return -1;
    }

    /** Blueprint items in the inventory: unique id → blueprint id. */
    static Map<UUID, String> blueprints(PlayerInventory inv) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (ItemStack s : inv.getContents()) {
            ToolItems.readBlueprint(s).ifPresent(e -> out.put(e.getValue(), e.getKey()));
        }
        return out;
    }

    // ------------------------------------------------------------------ starter kit

    void claimStarter(Player player) {
        List<ToolKind> missing = StarterKit.missing(module.config().starterKinds(), kindsOwned(player)).stream()
                .filter(k -> module.catalog().tier(k, 1).isPresent()).toList();
        if (missing.isEmpty()) {
            fail(player, "starter.have-all");
            return;
        }
        if (!busy.add(player.getUniqueId())) {
            fail(player, "forge.busy");
            return;
        }
        UUID playerId = player.getUniqueId();
        // Per profile: a new profile starts with an empty inventory, so it gets its own starter kit timer.
        UUID claimKey = ProfileService.of(module.core.services()).profileKey(player);
        long cooldown = Duration.ofHours(module.config().starterCooldownHours).toMillis();
        long now = System.currentTimeMillis();
        module.core.database().transaction(c -> {
            Long previous = module.repository().lastClaim(c, claimKey).orElse(null);
            long left = module.repository().claim(c, claimKey, now, cooldown);
            return new long[]{left, previous == null ? -1 : previous};
        }).whenComplete((res, error) -> module.core.scheduler().main("tools-starter", () -> {
            try {
                Player p = Bukkit.getPlayer(playerId);
                if (error != null) {
                    module.core.plugin().getLogger().log(Level.WARNING, "Tools: starter kit claim failed", Database.cause(error));
                    if (p != null) {
                        fail(p, "error");
                    }
                    return;
                }
                if (res[0] > 0) {
                    if (p != null) {
                        fail(p, "starter.cooldown", Msg.p("time", net.skybornmc.skyblock.common.util.Format.duration(Duration.ofMillis(res[0]))));
                    }
                    return;
                }
                if (p == null) {
                    Long previous = res[1] < 0 ? null : res[1];
                    module.core.database().run(c -> module.repository().unclaim(c, claimKey, previous));
                    return;
                }
                giveStarter(p, StarterKit.missing(missing, kindsOwned(p)));
            } finally {
                busy.remove(playerId);
            }
        }));
    }

    private void giveStarter(Player player, List<ToolKind> kinds) {
        List<String> given = new ArrayList<>();
        int season = module.config().seasonId;
        for (ToolKind kind : kinds) {
            ToolData tool = ToolData.fresh(UUID.randomUUID(), kind, 1, true, null);
            ItemStack stack = module.items().create(tool, "starter:" + player.getName(), player.getName(), null);
            give(player, stack);
            given.add(kind.id());
            UUID owner = player.getUniqueId();
            String name = player.getName();
            module.core.database().run(c -> module.repository().insert(c, tool, owner, name, null, "starter", season, System.currentTimeMillis()))
                    .exceptionally(e -> {
                        module.core.plugin().getLogger().log(Level.WARNING, "Tools: starter tool row not saved (saved on first use)", Database.cause(e));
                        return null;
                    });
        }
        module.msg().send(player, "starter.claimed", Msg.p("count", given.size()), Msg.p("hours", module.config().starterCooldownHours));
        sound(player, "success");
        module.core.audit().logPlayer(AuditCategory.ITEM, "starter_kit", Actor.player(player), player.getUniqueId(), player.getName(),
                (long) given.size(), player.getLocation(), Map.of("pieces", given));
    }

    // ------------------------------------------------------------------ shared

    /** Short random part of an idempotency key: one key per attempt, so a refunded attempt can be tried again. */
    private static String nonce() {
        return Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0xffffffffL);
    }

    /** Puts a Forge item in the inventory; the rest goes to /box (owner answer 7: never dropped; the box tells the player). */
    void give(Player player, ItemStack item) {
        give(player, item, net.skybornmc.skyblock.common.box.BoxSource.FORGE);
    }

    /** {@link #give(Player, ItemStack)} with the /box source shown in the lore ({@code BoxSource}). */
    void give(Player player, ItemStack item, String boxSource) {
        net.skybornmc.skyblock.common.box.ItemBox.give(module.core.services(), player, boxSource, item);
    }

    private CompletableFuture<TxResult> pay(Player player, long amount, String reason, String reference, String key, Map<String, Object> details) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(new TxResult(TxResult.Status.SUCCESS, 0, null, null));
        }
        Optional<EconomyService> economy = module.core.services().optional(EconomyService.class);
        if (economy.isEmpty()) {
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.ERROR));
        }
        UUID account = ProfileService.of(module.core.services()).profileKey(player);
        payers.put(key, account);
        Transaction tx = Transaction.withdraw(AccountRef.player(account), Currency.MONEY, amount, reason)
                .actor(Actor.player(player)).reference(reference).idempotencyKey(key);
        for (Map.Entry<String, Object> e : details.entrySet()) {
            tx = tx.detail(e.getKey(), e.getValue());
        }
        return economy.get().execute(tx);
    }

    private void refund(UUID player, String name, long amount, String key, String why) {
        if (amount <= 0) {
            return;
        }
        Optional<EconomyService> economy = module.core.services().optional(EconomyService.class);
        if (economy.isEmpty()) {
            module.core.plugin().getLogger().severe("Tools: could not refund " + amount + " to " + name + " (" + key + "): economy is off");
            return;
        }
        UUID account = payers.getOrDefault(key, player);
        economy.get().execute(Transaction.deposit(AccountRef.player(account), Currency.MONEY, amount, REASON_REFUND)
                .actor(Actor.system("tools")).reference(key.length() > 64 ? key.substring(0, 64) : key)
                .idempotencyKey("refund:" + key).detail("why", why)).whenComplete((r, e) -> {
            if (e != null || r == null || !(r.ok() || r.status() == TxResult.Status.DUPLICATE)) {
                module.core.plugin().getLogger().severe("Tools: refund of " + amount + " to " + name + " (" + key + ") failed: "
                        + (r == null ? e : r.status()));
            }
        });
    }

    private void moneyFailed(Player player, TxResult result, long amount) {
        if (result != null && result.status() == TxResult.Status.INSUFFICIENT_FUNDS) {
            fail(player, "forge.needs-money", Msg.p("money", formatMoney(amount)));
        } else if (result != null && result.status() == TxResult.Status.DUPLICATE) {
            fail(player, "forge.duplicate");
        } else {
            fail(player, "error");
        }
    }

    void fail(Player player, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        module.msg().send(player, key, placeholders);
        sound(player, "error");
    }

    void sound(Player player, String key) {
        if (key != null && !key.isBlank()) {
            module.core.sounds().play(player, key);
        }
    }

    /** "256x Coal, 12x Raw Iron" (plain names from the item ids). */
    String describe(Map<String, Integer> materials) {
        List<String> parts = new ArrayList<>();
        materials.forEach((id, amount) -> parts.add(net.skybornmc.skyblock.common.util.Format.number(amount) + "x " + materialName(id)));
        return String.join(", ", parts);
    }

    static String materialName(String id) {
        Material m = Material.matchMaterial(id);
        String base = m == null ? id : m.getKey().getKey();
        StringBuilder out = new StringBuilder();
        for (String word : base.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }
}
