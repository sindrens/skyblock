package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.enchant.EnchantBook;
import net.skybornmc.skyblock.common.item.DupeDetector;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.PieceEnchants;
import net.skybornmc.skyblock.common.wardrobe.SetEntryView;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Every Enchanter action (ENCHANTS §4): checks again on the main thread, builds an {@link OpPlan}, runs it as one database
 * transaction ({@link OpRunner}) and then changes the items. While the transaction runs, the player's inventory is locked
 * (clicks, drops, swaps cancelled). If an item is gone when the result comes back (the player left, died, sold it), the
 * database part is undone, so nothing is ever lost or duplicated. No fail chance anywhere (EN10).
 */
final class EnchantOps implements Listener {

    /** Operations that add or raise an enchant (counted by quests through GameActionEvent.ENCHANT). */
    private static final java.util.Set<String> QUEST_ACTIONS = java.util.Set.of("apply", "upgrade", "combine_tool", "armor_apply",
            "armor_upgrade", "armor_combine");

    /** A custom tool in the player's inventory. */
    record HeldTool(int slot, ItemStack stack, ToolInfo info) {
    }

    /** A book or scroll in the player's inventory. */
    record HeldBook(int slot, ItemStack stack, EnchantBook book) {
    }

    private final EnchantsModule module;
    private final Map<UUID, Long> busy = new ConcurrentHashMap<>();
    private final Map<UUID, Long> busyNotice = new ConcurrentHashMap<>();

    EnchantOps(EnchantsModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ inventory lock

    boolean busy(Player player) {
        Long since = busy.get(player.getUniqueId());
        if (since == null) {
            return false;
        }
        if (System.currentTimeMillis() - since > module.config().operationTimeoutSeconds * 1000L) {
            busy.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void lockNotice(Player player) {
        long now = System.currentTimeMillis();
        Long last = busyNotice.get(player.getUniqueId());
        if (last == null || now - last > 1000) {
            busyNotice.put(player.getUniqueId(), now);
            module.msg().actionBar(player, "busy");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && busy(p)) {
            event.setCancelled(true);
            lockNotice(p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player p && busy(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (busy(event.getPlayer())) {
            event.setCancelled(true);
            lockNotice(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (busy(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** No profile switch while an enchant is saving (the inventory would swap under it). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProfileSwitch(net.skybornmc.skyblock.common.profile.ProfileSwitchEvent event) {
        if (busy(event.getPlayer())) {
            event.refuse(module.msg().component(event.getPlayer(), "busy"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (busy(event.getPlayer())) {
            event.setCancelled(true); // item frames, armor stands, allays
        }
    }

    // ------------------------------------------------------------------ inventory helpers

    List<HeldTool> tools(Player player) {
        List<HeldTool> out = new ArrayList<>();
        ToolService tools = module.tools();
        if (tools == null) {
            return out;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            int slot = i;
            tools.read(stack).ifPresent(info -> out.add(new HeldTool(slot, stack, info)));
        }
        return out;
    }

    Optional<HeldTool> tool(Player player, UUID toolId) {
        return tools(player).stream().filter(t -> t.info().id().equals(toolId)).findFirst();
    }

    List<HeldBook> books(Player player) {
        List<HeldBook> out = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            int slot = i;
            BookItems.read(stack).ifPresent(b -> out.add(new HeldBook(slot, stack, b)));
        }
        return out;
    }

    Optional<HeldBook> book(Player player, UUID bookId) {
        return books(player).stream().filter(b -> b.book().id().equals(bookId)).findFirst();
    }

    // ------------------------------------------------------------------ actions

    /** §4.2: put a book into an empty slot. */
    void apply(Player player, UUID toolId, int slot, UUID bookId) {
        Context ctx = context(player, toolId);
        if (ctx == null) {
            return;
        }
        Optional<HeldBook> held = book(player, bookId);
        if (held.isEmpty() || held.get().book().scroll()) {
            fail(player, "book-gone");
            return;
        }
        EnchantBook book = held.get().book();
        Optional<EnchantDef> def = module.registry().get(book.enchant());
        if (def.isEmpty()) {
            fail(player, "unknown-enchant");
            return;
        }
        EnchantRules.Verdict verdict = module.rules().canApply(ctx.tool.info(), slot, def.get(), book.level(), module.registry()::get);
        if (!verdict.ok()) {
            fail(player, verdict, def.get(), ctx.tool.info());
            return;
        }
        long fee = module.costs().applyFee(def.get(), book.level());
        List<ToolPerk> after = EnchantRules.withApplied(ctx.tool.info().perks(), slot, def.get(), book.level(), fee);
        Map<String, Object> details = details(ctx.tool.info(), def.get().id(), book.level(), fee);
        details.put("slot", slot);
        details.put("book", book.id().toString());
        OpPlan plan = plan("apply", player, ctx.tool.info(), after, List.of(OpPlan.Book.book(book.id(), book.enchant(), book.level(), null)),
                List.of(), fee, 0, null, "enchant_apply", details);
        String name = module.text().displayName(def.get().id(), book.level());
        start(player, plan, List.of(), p -> {
            module.msg().send(p, "applied", Msg.p("enchant", name), Msg.p("tool", toolName(ctx.tool.stack())), cost(fee));
            sound(p, module.config().soundApply);
        });
    }

    /** §4.3: one level up with crystals. */
    void upgrade(Player player, UUID toolId, String enchant) {
        Context ctx = context(player, toolId);
        if (ctx == null) {
            return;
        }
        Optional<ToolPerk> perk = ctx.tool.info().perk(enchant);
        Optional<EnchantDef> def = module.registry().get(enchant);
        if (perk.isEmpty() || def.isEmpty()) {
            fail(player, "enchant-gone");
            return;
        }
        EnchantRules.Verdict verdict = module.rules().canUpgrade(ctx.tool.info(), perk.get(), def.get());
        if (!verdict.ok()) {
            fail(player, verdict, def.get(), ctx.tool.info());
            return;
        }
        int next = perk.get().level() + 1;
        long cost = module.costs().upgradeCost(def.get(), perk.get().level());
        List<ToolPerk> after = EnchantRules.replaced(ctx.tool.info().perks(), enchant, perk.get().upgraded(next, cost));
        OpPlan plan = plan("upgrade", player, ctx.tool.info(), after, List.of(), List.of(), cost, 0, null, "enchant_upgrade",
                details(ctx.tool.info(), enchant, next, cost));
        String name = module.text().displayName(enchant, next);
        start(player, plan, List.of(), p -> {
            module.msg().send(p, "upgraded", Msg.p("enchant", name), cost(cost));
            sound(p, module.config().soundApply);
        });
    }

    /** §4.4: a book of the same enchant and level onto the applied enchant (+1 level for the combine fee). */
    void combineOnTool(Player player, UUID toolId, String enchant, UUID bookId) {
        Context ctx = context(player, toolId);
        if (ctx == null) {
            return;
        }
        Optional<ToolPerk> perk = ctx.tool.info().perk(enchant);
        Optional<EnchantDef> def = module.registry().get(enchant);
        Optional<HeldBook> held = book(player, bookId);
        if (perk.isEmpty() || def.isEmpty()) {
            fail(player, "enchant-gone");
            return;
        }
        if (held.isEmpty() || held.get().book().scroll()) {
            fail(player, "book-gone");
            return;
        }
        EnchantBook book = held.get().book();
        EnchantRules.Verdict verdict = module.rules().canCombineOnTool(ctx.tool.info(), perk.get(), def.get(), book.enchant(), book.level());
        if (!verdict.ok()) {
            fail(player, verdict, def.get(), ctx.tool.info());
            return;
        }
        int next = perk.get().level() + 1;
        long fee = module.costs().combineFee(def.get(), next);
        List<ToolPerk> after = EnchantRules.replaced(ctx.tool.info().perks(), enchant, perk.get().upgraded(next, fee));
        Map<String, Object> details = details(ctx.tool.info(), enchant, next, fee);
        details.put("book", book.id().toString());
        OpPlan plan = plan("combine_tool", player, ctx.tool.info(), after, List.of(OpPlan.Book.book(book.id(), book.enchant(), book.level(), null)),
                List.of(), fee, 0, null, "enchant_combine", details);
        String name = module.text().displayName(enchant, next);
        start(player, plan, List.of(), p -> {
            module.msg().send(p, "upgraded", Msg.p("enchant", name), cost(fee));
            sound(p, module.config().soundApply);
        });
    }

    /** §4.4: two equal books → one book a level higher. */
    void combineBooks(Player player, UUID first, UUID second) {
        if (!ready(player)) {
            return;
        }
        Optional<HeldBook> a = book(player, first);
        Optional<HeldBook> b = book(player, second);
        if (first.equals(second) || a.isEmpty() || b.isEmpty() || a.get().book().scroll() || b.get().book().scroll()) {
            fail(player, "book-gone");
            return;
        }
        EnchantBook ba = a.get().book();
        EnchantBook bb = b.get().book();
        Optional<EnchantDef> def = module.registry().get(ba.enchant());
        if (def.isEmpty()) {
            fail(player, "unknown-enchant");
            return;
        }
        EnchantRules.Verdict verdict = EnchantRules.canCombineBooks(def.get(), ba.enchant(), ba.level(), bb.enchant(), bb.level());
        if (!verdict.ok()) {
            fail(player, verdict, def.get(), null);
            return;
        }
        int next = ba.level() + 1;
        long fee = module.costs().combineFee(def.get(), next);
        ItemStack result = module.books().createBook(def.get().id(), next, "combine:" + player.getName());
        UUID newId = BookItems.id(result);
        Map<String, Object> details = details(null, def.get().id(), next, fee);
        details.put("books", List.of(ba.id().toString(), bb.id().toString()));
        details.put("new_book", newId.toString());
        OpPlan plan = plan("combine", player, null, null,
                List.of(OpPlan.Book.book(ba.id(), ba.enchant(), ba.level(), null), OpPlan.Book.book(bb.id(), bb.enchant(), bb.level(), null)),
                List.of(OpPlan.Book.book(newId, def.get().id(), next, "combine")), fee, 0, null, "enchant_combine", details);
        String name = module.text().displayName(def.get().id(), next);
        start(player, plan, List.of(result), p -> {
            module.msg().send(p, "combined", Msg.p("enchant", name), cost(fee));
            sound(p, module.config().soundApply);
        });
    }

    /** §4.5: with a Disenchant Scroll the enchant comes off as a book at its current level. */
    void remove(Player player, UUID toolId, String enchant, UUID scrollId) {
        Context ctx = context(player, toolId);
        if (ctx == null) {
            return;
        }
        Optional<ToolPerk> perk = ctx.tool.info().perk(enchant);
        if (perk.isEmpty()) {
            fail(player, "enchant-gone");
            return;
        }
        if (module.registry().get(enchant).isEmpty()) {
            fail(player, "unknown-enchant");
            return;
        }
        Optional<HeldBook> scroll = book(player, scrollId);
        if (scroll.isEmpty() || !scroll.get().book().scroll()) {
            fail(player, "no-scroll");
            return;
        }
        ItemStack result = module.books().createBook(enchant, perk.get().level(), "disenchant:" + player.getName());
        UUID newId = BookItems.id(result);
        List<ToolPerk> after = EnchantRules.replaced(ctx.tool.info().perks(), enchant, null);
        Map<String, Object> details = details(ctx.tool.info(), enchant, perk.get().level(), 0);
        details.put("scroll", scrollId.toString());
        details.put("new_book", newId.toString());
        details.put("invested", perk.get().invested());
        OpPlan plan = plan("remove", player, ctx.tool.info(), after, List.of(OpPlan.Book.scroll(scrollId, null)),
                List.of(OpPlan.Book.book(newId, enchant, perk.get().level(), "disenchant")), 0, 0, null, "enchant_remove", details);
        String name = module.text().displayName(enchant, perk.get().level());
        start(player, plan, List.of(result), p -> {
            module.msg().send(p, "removed", Msg.p("enchant", name));
            sound(p, module.config().soundApply);
        });
    }

    /** §4.5: without a scroll the enchant is destroyed for a share of the crystals put into it. */
    void shatter(Player player, UUID toolId, String enchant) {
        Context ctx = context(player, toolId);
        if (ctx == null) {
            return;
        }
        Optional<ToolPerk> perk = ctx.tool.info().perk(enchant);
        if (perk.isEmpty()) {
            fail(player, "enchant-gone");
            return;
        }
        long refund = module.costs().shatterRefund(perk.get().invested());
        List<ToolPerk> after = EnchantRules.replaced(ctx.tool.info().perks(), enchant, null);
        Map<String, Object> details = details(ctx.tool.info(), enchant, perk.get().level(), 0);
        details.put("invested", perk.get().invested());
        details.put("refund", refund);
        OpPlan plan = plan("shatter", player, ctx.tool.info(), after, List.of(), List.of(), 0, refund, null, "enchant_shatter", details);
        String name = module.text().displayName(enchant, perk.get().level());
        start(player, plan, List.of(), p -> {
            module.msg().send(p, "shattered", Msg.p("enchant", name), Msg.p("refund", Format.number(refund)));
            sound(p, module.config().soundApply);
        });
    }

    /** §3.5: a random book of a pool for crystals. */
    void roll(Player player, RollType type, Pool pool) {
        if (!ready(player)) {
            return;
        }
        RollType.Settings settings = module.config().roll(type);
        if (settings.odds().isEmpty() || settings.daily() <= 0) {
            fail(player, "roll.closed");
            return;
        }
        int tier = tier(player, pool);
        if (tier < settings.minTier()) {
            fail(player, "roll.tier", Msg.p("tier", settings.minTier()), Msg.p("pool", module.text().plain("pool." + pool.id())));
            return;
        }
        Optional<EnchantDef> def = Rolls.roll(module.registry(), pool, settings.odds(), tier, ThreadLocalRandom.current());
        if (def.isEmpty()) {
            fail(player, "roll.empty");
            return;
        }
        int level = Math.min(module.config().rollBookLevel, def.get().maxLevel());
        ItemStack result = module.books().createBook(def.get().id(), level, "roll:" + type.id());
        UUID newId = BookItems.id(result);
        Map<String, Object> details = details(null, def.get().id(), level, settings.price());
        details.put("roll", type.id());
        details.put("pool", pool.id());
        details.put("tier", tier);
        details.put("new_book", newId.toString());
        OpPlan plan = plan("roll", player, null, null, List.of(),
                List.of(OpPlan.Book.book(newId, def.get().id(), level, "roll:" + type.id() + ":" + pool.id())), settings.price(), 0,
                new OpPlan.Daily(today(), type.id(), settings.daily()), "enchant_roll", details);
        String name = module.text().displayName(def.get().id(), level);
        String rarity = def.get().rarity().id();
        start(player, plan, List.of(result), p -> {
            module.msg().send(p, "rolled", Msg.mm("book", "<rarity:" + rarity + ">" + net.skybornmc.skyblock.common.item.LoreBuilder.escape(name)),
                    cost(settings.price()));
            sound(p, module.config().soundRoll);
        });
    }

    /** §4.5: buy a Disenchant Scroll. */
    void buyScroll(Player player) {
        if (!ready(player)) {
            return;
        }
        long price = module.config().scrollPrice;
        ItemStack result = module.books().createScroll("shop:" + player.getName());
        UUID newId = BookItems.id(result);
        Map<String, Object> details = details(null, null, 0, price);
        details.put("new_scroll", newId.toString());
        OpPlan plan = plan("scroll_buy", player, null, null, List.of(), List.of(OpPlan.Book.scroll(newId, "shop")), price, 0, null,
                "enchant_scroll", details);
        start(player, plan, List.of(result), p -> {
            module.msg().send(p, "scroll-bought", cost(price));
            sound(p, module.config().soundApply);
        });
    }

    // ------------------------------------------------------------------ armor enchants on wardrobe set pieces (E5)

    /** An owned wardrobe entry's piece as the action found it (the database row is re-checked in the transaction). */
    private record ArmorCtx(SetEntryView entry, ArmorPiece piece, List<ToolPerk> enchants) {
    }

    private ArmorCtx armorContext(Player player, UUID entryId, ArmorPiece piece) {
        if (!ready(player)) {
            return null;
        }
        WardrobeService wardrobe = module.wardrobe();
        if (wardrobe == null) {
            fail(player, "armor.unavailable");
            return null;
        }
        if (wardrobe.busy(player)) {
            module.msg().send(player, "busy");
            return null;
        }
        Optional<SetEntryView> entry = wardrobe.entry(player, entryId);
        if (entry.isEmpty()) {
            fail(player, "armor.entry-gone");
            return null;
        }
        return new ArmorCtx(entry.get(), piece, entry.get().enchants(piece));
    }

    private OpPlan armorPlan(String action, Player player, ArmorCtx ctx, List<ToolPerk> after, List<OpPlan.Book> consume,
                             List<OpPlan.Book> mint, long cost, long refund, String reason, Map<String, Object> details) {
        return new OpPlan(UUID.randomUUID(), action, player.getUniqueId(), profiles().profileKey(player), player.getName(),
                module.config().seasonId, null, ctx.enchants(), after, consume, mint, cost, refund, null, reason, details,
                new OpPlan.Piece(ctx.entry().entryId(), ctx.piece()));
    }

    private static Map<String, Object> armorDetails(ArmorCtx ctx, String enchant, int level, long crystals) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("set_entry", ctx.entry().entryId().toString());
        d.put("set", ctx.entry().setId());
        if (ctx.entry().mergedSetId() != null) {
            d.put("merged_set", ctx.entry().mergedSetId());
        }
        d.put("piece", ctx.piece().key());
        d.put("set_level", ctx.entry().level());
        d.put("enchant", enchant);
        d.put("level", level);
        d.put("crystals", crystals);
        return d;
    }

    /** {@code Deepforge Plate Chestplate} for chat. */
    String pieceLabel(SetEntryView entry, ArmorPiece piece) {
        return entry.name() + " " + module.text().plain("target." + ArmorRules.target(piece).id());
    }

    /** E5 §4.2 on a set piece: a book into an empty slot of a piece. */
    void applyArmor(Player player, UUID entryId, ArmorPiece piece, int slot, UUID bookId) {
        ArmorCtx ctx = armorContext(player, entryId, piece);
        if (ctx == null) {
            return;
        }
        Optional<HeldBook> held = book(player, bookId);
        if (held.isEmpty() || held.get().book().scroll()) {
            fail(player, "book-gone");
            return;
        }
        EnchantBook book = held.get().book();
        Optional<EnchantDef> def = module.registry().get(book.enchant());
        if (def.isEmpty()) {
            fail(player, "unknown-enchant");
            return;
        }
        EnchantRules.Verdict verdict = module.armorRules().canApply(piece, ctx.enchants(), ctx.entry().level(), slot, def.get(), book.level(),
                module.registry()::get);
        if (!verdict.ok()) {
            failArmor(player, verdict, def.get());
            return;
        }
        long fee = module.costs().applyFee(def.get(), book.level());
        List<ToolPerk> after = ArmorRules.withApplied(ctx.enchants(), slot, def.get(), book.level(), fee);
        Map<String, Object> details = armorDetails(ctx, def.get().id(), book.level(), fee);
        details.put("slot", slot);
        details.put("book", book.id().toString());
        OpPlan plan = armorPlan("armor_apply", player, ctx, after, List.of(OpPlan.Book.book(book.id(), book.enchant(), book.level(), null)),
                List.of(), fee, 0, "enchant_apply", details);
        String name = module.text().displayName(def.get().id(), book.level());
        String label = pieceLabel(ctx.entry(), piece);
        start(player, plan, List.of(), p -> {
            module.msg().send(p, "applied", Msg.p("enchant", name), Msg.p("tool", label), cost(fee));
            sound(p, module.config().soundApply);
        });
    }

    /** E5 §4.3 on a set piece: one level up with crystals. */
    void upgradeArmor(Player player, UUID entryId, ArmorPiece piece, String enchant) {
        ArmorCtx ctx = armorContext(player, entryId, piece);
        if (ctx == null) {
            return;
        }
        Optional<ToolPerk> perk = PieceEnchants.find(ctx.enchants(), enchant);
        Optional<EnchantDef> def = module.registry().get(enchant);
        if (perk.isEmpty() || def.isEmpty()) {
            fail(player, "armor.enchant-gone");
            return;
        }
        EnchantRules.Verdict verdict = module.armorRules().canUpgrade(piece, perk.get(), ctx.entry().level(), def.get());
        if (!verdict.ok()) {
            failArmor(player, verdict, def.get());
            return;
        }
        int next = perk.get().level() + 1;
        long cost = module.costs().upgradeCost(def.get(), perk.get().level());
        List<ToolPerk> after = EnchantRules.replaced(ctx.enchants(), enchant, perk.get().upgraded(next, cost));
        OpPlan plan = armorPlan("armor_upgrade", player, ctx, after, List.of(), List.of(), cost, 0, "enchant_upgrade",
                armorDetails(ctx, enchant, next, cost));
        String name = module.text().displayName(enchant, next);
        start(player, plan, List.of(), p -> {
            module.msg().send(p, "upgraded", Msg.p("enchant", name), cost(cost));
            sound(p, module.config().soundApply);
        });
    }

    /** E5 §4.4 on a set piece: a book of the same enchant and level onto the applied enchant. */
    void combineOnArmor(Player player, UUID entryId, ArmorPiece piece, String enchant, UUID bookId) {
        ArmorCtx ctx = armorContext(player, entryId, piece);
        if (ctx == null) {
            return;
        }
        Optional<ToolPerk> perk = PieceEnchants.find(ctx.enchants(), enchant);
        Optional<EnchantDef> def = module.registry().get(enchant);
        Optional<HeldBook> held = book(player, bookId);
        if (perk.isEmpty() || def.isEmpty()) {
            fail(player, "armor.enchant-gone");
            return;
        }
        if (held.isEmpty() || held.get().book().scroll()) {
            fail(player, "book-gone");
            return;
        }
        EnchantBook book = held.get().book();
        EnchantRules.Verdict verdict = module.armorRules().canCombineOn(piece, perk.get(), ctx.entry().level(), def.get(), book.enchant(), book.level());
        if (!verdict.ok()) {
            failArmor(player, verdict, def.get());
            return;
        }
        int next = perk.get().level() + 1;
        long fee = module.costs().combineFee(def.get(), next);
        List<ToolPerk> after = EnchantRules.replaced(ctx.enchants(), enchant, perk.get().upgraded(next, fee));
        Map<String, Object> details = armorDetails(ctx, enchant, next, fee);
        details.put("book", book.id().toString());
        OpPlan plan = armorPlan("armor_combine", player, ctx, after, List.of(OpPlan.Book.book(book.id(), book.enchant(), book.level(), null)),
                List.of(), fee, 0, "enchant_combine", details);
        String name = module.text().displayName(enchant, next);
        start(player, plan, List.of(), p -> {
            module.msg().send(p, "upgraded", Msg.p("enchant", name), cost(fee));
            sound(p, module.config().soundApply);
        });
    }

    /** E5 §4.5 on a set piece: with a Disenchant Scroll the enchant comes off as a book at its level. */
    void removeArmor(Player player, UUID entryId, ArmorPiece piece, String enchant, UUID scrollId) {
        ArmorCtx ctx = armorContext(player, entryId, piece);
        if (ctx == null) {
            return;
        }
        Optional<ToolPerk> perk = PieceEnchants.find(ctx.enchants(), enchant);
        if (perk.isEmpty()) {
            fail(player, "armor.enchant-gone");
            return;
        }
        Optional<EnchantDef> def = module.registry().get(enchant);
        if (def.isEmpty()) {
            fail(player, "unknown-enchant");
            return;
        }
        Optional<HeldBook> scroll = book(player, scrollId);
        if (scroll.isEmpty() || !scroll.get().book().scroll()) {
            fail(player, "no-scroll");
            return;
        }
        int level = Math.min(perk.get().level(), def.get().maxLevel());
        ItemStack result = module.books().createBook(enchant, level, "disenchant:" + player.getName());
        UUID newId = BookItems.id(result);
        List<ToolPerk> after = EnchantRules.replaced(ctx.enchants(), enchant, null);
        Map<String, Object> details = armorDetails(ctx, enchant, level, 0);
        details.put("scroll", scrollId.toString());
        details.put("new_book", newId.toString());
        details.put("invested", perk.get().invested());
        OpPlan plan = armorPlan("armor_remove", player, ctx, after, List.of(OpPlan.Book.scroll(scrollId, null)),
                List.of(OpPlan.Book.book(newId, enchant, level, "disenchant")), 0, 0, "enchant_remove", details);
        String name = module.text().displayName(enchant, level);
        start(player, plan, List.of(result), p -> {
            module.msg().send(p, "removed", Msg.p("enchant", name));
            sound(p, module.config().soundApply);
        });
    }

    /** E5 §4.5 on a set piece: without a scroll the enchant is destroyed for a share of the crystals put into it. */
    void shatterArmor(Player player, UUID entryId, ArmorPiece piece, String enchant) {
        ArmorCtx ctx = armorContext(player, entryId, piece);
        if (ctx == null) {
            return;
        }
        Optional<ToolPerk> perk = PieceEnchants.find(ctx.enchants(), enchant);
        if (perk.isEmpty()) {
            fail(player, "armor.enchant-gone");
            return;
        }
        long refund = module.costs().shatterRefund(perk.get().invested());
        List<ToolPerk> after = EnchantRules.replaced(ctx.enchants(), enchant, null);
        Map<String, Object> details = armorDetails(ctx, enchant, perk.get().level(), 0);
        details.put("invested", perk.get().invested());
        details.put("refund", refund);
        OpPlan plan = armorPlan("armor_shatter", player, ctx, after, List.of(), List.of(), 0, refund, "enchant_shatter", details);
        String name = module.text().displayName(enchant, perk.get().level());
        start(player, plan, List.of(), p -> {
            module.msg().send(p, "shattered", Msg.p("enchant", name), Msg.p("refund", Format.number(refund)));
            sound(p, module.config().soundApply);
        });
    }

    private void failArmor(Player player, EnchantRules.Verdict verdict, EnchantDef def) {
        fail(player, "rule." + EnchantMenus.armorProblemKey(verdict.problem()), module.menus().problemPlaceholders(verdict, def, null));
    }

    // ------------------------------------------------------------------ running

    private record Context(HeldTool tool) {
    }

    private boolean ready(Player player) {
        if (busy(player) || profiles().isSwitching(player.getUniqueId())) {
            module.msg().send(player, "busy");
            return false;
        }
        if (module.tools() == null || module.economy() == null) {
            fail(player, "unavailable");
            return false;
        }
        return true;
    }

    private Context context(Player player, UUID toolId) {
        if (!ready(player)) {
            return null;
        }
        Optional<HeldTool> tool = tool(player, toolId);
        if (tool.isEmpty()) {
            fail(player, "tool-gone");
            return null;
        }
        return new Context(tool.get());
    }

    private OpPlan plan(String action, Player player, ToolInfo tool, List<ToolPerk> after, List<OpPlan.Book> consume,
                        List<OpPlan.Book> mint, long cost, long refund, OpPlan.Daily daily, String reason, Map<String, Object> details) {
        return new OpPlan(UUID.randomUUID(), action, player.getUniqueId(), profiles().profileKey(player), player.getName(), module.config().seasonId,
                tool == null ? null : tool.id(), tool == null ? null : tool.perks(), after, consume, mint, cost, refund, daily, reason, details);
    }

    private static Map<String, Object> details(ToolInfo tool, String enchant, int level, long crystals) {
        Map<String, Object> d = new LinkedHashMap<>();
        if (tool != null) {
            d.put("tool", tool.id().toString());
            d.put("tool_kind", tool.kind().id());
            d.put("tool_level", tool.level());
        }
        if (enchant != null) {
            d.put("enchant", enchant);
            d.put("level", level);
        }
        d.put("crystals", crystals);
        return d;
    }

    private void start(Player player, OpPlan plan, List<ItemStack> minted, Consumer<Player> success) {
        UUID uuid = player.getUniqueId();
        busy.put(uuid, System.currentTimeMillis());
        module.runner().run(plan).whenComplete((result, error) -> module.core.scheduler().main("enchants-" + plan.action(), () -> {
            OpRunner.Result r = error != null ? OpRunner.Result.of(OpRunner.Status.ERROR) : result;
            try {
                finish(plan, r, minted, success);
            } catch (RuntimeException e) {
                module.core.plugin().getLogger().log(Level.SEVERE, "Enchants: finishing " + plan.action() + " " + plan.id() + " failed", e);
                if (r.ok()) {
                    undo(plan, "finish_error");
                }
            } finally {
                busy.remove(uuid);
            }
        }));
    }

    private void finish(OpPlan plan, OpRunner.Result result, List<ItemStack> minted, Consumer<Player> success) {
        Player player = Bukkit.getPlayer(plan.player());
        if (!result.ok()) {
            if (player != null) {
                failed(player, plan, result);
            }
            return;
        }
        if (player == null || !player.isOnline()) {
            undo(plan, "offline");
            return;
        }
        PlayerInventory inv = player.getInventory();
        if (plan.toolId() != null) {
            Optional<HeldTool> tool = tool(player, plan.toolId());
            if (tool.isEmpty() || !tool.get().info().perks().equals(plan.perksBefore())) {
                undo(plan, "tool_moved");
                return;
            }
        }
        WardrobeService wardrobe = plan.piece() == null ? null : module.wardrobe();
        if (plan.piece() != null && wardrobe == null) {
            undo(plan, "wardrobe_off");
            return;
        }
        List<Integer> bookSlots = new ArrayList<>();
        for (OpPlan.Book b : plan.consume()) {
            Optional<HeldBook> held = book(player, b.id());
            if (held.isEmpty()) {
                undo(plan, "book_moved");
                return;
            }
            bookSlots.add(held.get().slot());
        }
        if (plan.toolId() != null && !module.tools().setPerks(player, plan.toolId(), plan.perksAfter(), Actor.player(player), "enchant_" + plan.action())) {
            undo(plan, "tool_refused");
            return;
        }
        for (int slot : bookSlots) {
            inv.setItem(slot, null);
        }
        if (wardrobe != null) {
            // The row is the truth (committed); the cache and the worn piece follow.
            wardrobe.pieceEnchantsChanged(player, plan.piece().entryId(), plan.piece().piece(), plan.perksAfter());
        }
        // Owner answer 7: never dropped. What does not fit goes to /box (the box tells the player).
        net.skybornmc.skyblock.common.box.ItemBox.give(module.core.services(), player, net.skybornmc.skyblock.common.box.BoxSource.ENCHANTER, minted);
        success.accept(player);
        Map<String, Object> details = new LinkedHashMap<>(plan.details());
        details.put("op", plan.id().toString());
        if (result.balance() != null) {
            details.put("crystals_after", result.balance());
        }
        module.core.audit().logPlayer(AuditCategory.ITEM, "enchant_" + plan.action(), Actor.player(player), player.getUniqueId(),
                player.getName(), plan.cost() > 0 ? plan.cost() : plan.refund(), player.getLocation(), details);
        if (QUEST_ACTIONS.contains(plan.action())) {
            // PHASE-10 M10.7: "Add or raise an enchant" quests (and later the pass) count it.
            org.bukkit.Bukkit.getPluginManager().callEvent(new net.skybornmc.skyblock.common.quest.GameActionEvent(player,
                    net.skybornmc.skyblock.common.quest.GameActionEvent.ENCHANT, 1));
        }
        for (OpPlan.Book b : plan.mint()) {
            module.core.audit().logPlayer(AuditCategory.ITEM, "book_created", Actor.player(player), player.getUniqueId(), player.getName(),
                    null, null, Map.of("book", b.id().toString(), "kind", b.scroll() ? "scroll" : "book",
                            "enchant", b.scroll() ? "" : b.enchant(), "level", b.level(), "origin", String.valueOf(b.origin()), "op", plan.id().toString()));
        }
    }

    private void failed(Player player, OpPlan plan, OpRunner.Result result) {
        switch (result.status()) {
            case NOT_ENOUGH -> {
                long have = module.economy() == null ? 0 : module.economy().cached(player.getUniqueId(), Currency.CRYSTALS).orElse(0);
                fail(player, "not-enough", cost(plan.cost()), Msg.p("have", Format.number(have)));
            }
            case LIMIT -> fail(player, "roll.limit");
            case PIECE_CHANGED -> fail(player, "armor.piece-changed");
            case BOOK_INVALID -> confiscate(player, result.badBook(), plan);
            default -> fail(player, "error");
        }
    }

    /** §9: a copied (used-up, void or unknown) book is taken away, staff are told and it is audited. */
    private void confiscate(Player player, UUID bookId, OpPlan plan) {
        Optional<HeldBook> held = bookId == null ? Optional.empty() : book(player, bookId);
        held.ifPresent(h -> player.getInventory().setItem(h.slot(), null));
        fail(player, "confiscated");
        String what = held.map(h -> h.book().scroll() ? module.text().plain("scroll.name")
                : module.text().displayName(h.book().enchant(), h.book().level())).orElse(String.valueOf(bookId));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(DupeDetector.PERM_NOTIFY)) {
                module.msg().send(staff, "staff-confiscated", Msg.p("player", player.getName()), Msg.p("book", what),
                        Msg.p("id", String.valueOf(bookId)));
            }
        }
        module.core.plugin().getLogger().warning("Enchants: took a copied book " + bookId + " (" + what + ") from " + player.getName());
        module.core.audit().logPlayer(AuditCategory.ITEM, "book_confiscated", Actor.system("enchants"), player.getUniqueId(), player.getName(),
                null, player.getLocation(), Map.of("book", String.valueOf(bookId), "what", what, "action", plan.action(), "op", plan.id().toString()));
    }

    /** Undoes the database part when the items can't be changed; the player gets their crystals back. */
    private void undo(OpPlan plan, String why) {
        module.runner().undo(plan).whenComplete((ok, error) -> module.core.scheduler().main("enchants-undo", () -> {
            boolean done = error == null && Boolean.TRUE.equals(ok);
            Player player = Bukkit.getPlayer(plan.player());
            if (player != null) {
                module.msg().send(player, done ? "undone" : "error");
            }
            if (!done) {
                module.core.plugin().getLogger().severe("Enchants: undo of " + plan.action() + " " + plan.id() + " for " + plan.playerName()
                        + " did not fully work (" + why + "); check the audit log");
            }
            Map<String, Object> details = new LinkedHashMap<>(plan.details());
            details.put("op", plan.id().toString());
            details.put("why", why);
            details.put("undone", done);
            module.core.audit().logPlayer(AuditCategory.ITEM, "enchant_undo", Actor.system("enchants"), plan.player(), plan.playerName(),
                    plan.cost() > 0 ? plan.cost() : plan.refund(), null, details);
        }));
    }

    // ------------------------------------------------------------------ feedback

    private void fail(Player player, String key, TagResolver... placeholders) {
        module.msg().send(player, key, placeholders);
        sound(player, module.config().soundError);
    }

    private void fail(Player player, EnchantRules.Verdict verdict, EnchantDef def, ToolInfo tool) {
        fail(player, "rule." + EnchantMenus.problemKey(verdict.problem()), module.menus().problemPlaceholders(verdict, def, tool));
    }

    private void sound(Player player, String key) {
        if (key != null && !key.isBlank()) {
            module.core.sounds().play(player, key);
        }
    }

    private static TagResolver cost(long crystals) {
        return Msg.p("cost", Format.number(crystals));
    }

    private String toolName(ItemStack stack) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(stack.effectiveName());
    }

    /** The player's tier in a pool's wing (from the skill level and {@code enchanter.yml → tiers}). */
    int tier(Player player, Pool pool) {
        int level = module.core.services().optional(SkillService.class).map(s -> s.level(player, pool.skill())).orElse(0);
        return Rolls.tier(module.config().tierLevels(pool), level);
    }

    net.skybornmc.skyblock.common.profile.ProfileService profiles() {
        return net.skybornmc.skyblock.common.profile.ProfileService.of(module.core.services());
    }

    static String today() {
        return LocalDate.now(Format.zone()).toString();
    }

    /** Clears the lock of a player who left (their operation still finishes or is undone). */
    void forget(UUID player) {
        busyNotice.remove(player);
    }

    EconomyService economy() {
        return module.economy();
    }
}
