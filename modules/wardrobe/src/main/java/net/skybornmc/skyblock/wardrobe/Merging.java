package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.enchant.PreparedBook;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Merging two max-level sets (WARDROBE §5, W2): one set with both sets' bonuses, back to level 0, for a crystal fee.
 * The menus show only valid partners as clickable (the others say why); the merge itself is one database transaction
 * that re-checks every rule on the locked rows ({@link WardrobeOps#merge}). The session follows on the main thread after
 * the commit; the old pieces vanish through the reconcile (their rows are gone). Audited.
 */
final class Merging {

    private final WardrobeModule module;

    Merging(WardrobeModule module) {
        this.module = module;
    }

    /** The rule-side view of an entry. */
    MergeRules.Side side(WardrobeStore.Entry e) {
        SetDef def = module.registry().get(e.setId()).orElse(null);
        if (def == null) {
            return null;
        }
        return new MergeRules.Side(e.entryId(), def, e.level(), e.isMerged(), e.state() == WardrobeStore.EntryState.ACTIVE);
    }

    /** Why this entry can't start a merge (message key under merge.why), or empty. */
    Optional<MergeRules.Refusal> canStart(WardrobeStore.Entry e) {
        MergeRules.Side side = side(e);
        return side == null ? Optional.of(MergeRules.Refusal.GONE) : MergeRules.canStart(side, module.config().maxLevel);
    }

    /** Why these two can't be merged, or empty. */
    Optional<MergeRules.Refusal> check(WardrobeStore.Entry a, WardrobeStore.Entry b) {
        MergeRules.Side sa = side(a);
        MergeRules.Side sb = side(b);
        if (sa == null || sb == null) {
            return Optional.of(MergeRules.Refusal.GONE);
        }
        return MergeRules.check(sa, sb, module.config().maxLevel);
    }

    long fee(WardrobeStore.Entry a, WardrobeStore.Entry b) {
        int ta = module.registry().get(a.setId()).map(SetDef::tier).orElse(SetDef.MAX_TIER);
        int tb = module.registry().get(b.setId()).map(SetDef::tier).orElse(SetDef.MAX_TIER);
        return MergeRules.fee(ta, tb, module.config().mergeFeePerStep);
    }

    String feeText(long fee) {
        return fee <= 0 ? module.text().plain("merge.free") : module.shop().price(Currency.CRYSTALS, fee);
    }

    /** Whether merging is on at all (config + set levels). */
    boolean enabled() {
        return module.config().mergeEnabled && module.config().levelsEnabled;
    }

    /**
     * Prepares one book per armor enchant of the entry that does not give the look (E5). Null (and the player told why)
     * when that is not possible: the enchants module is off or an enchant does not exist any more. Nothing is lost then:
     * the merge does not start.
     */
    private WardrobeOps.MergeEnchants returnedBooks(Player player, Map<ArmorPiece, List<ToolPerk>> other) {
        boolean any = other.values().stream().anyMatch(l -> !l.isEmpty());
        if (!any) {
            return WardrobeOps.MergeEnchants.NONE;
        }
        EnchantService service = module.core.services().optional(EnchantService.class).orElse(null);
        if (service == null) {
            module.fail(player, "merge.enchants-unavailable");
            return null;
        }
        List<WardrobeOps.ReturnedBook> books = new ArrayList<>();
        Map<java.util.UUID, PreparedBook> prepared = new java.util.HashMap<>();
        for (MergeRules.Returned ret : MergeRules.returnedBooks(other, service::maxLevel)) {
            PreparedBook book;
            try {
                book = service.prepareBook(ret.enchant(), ret.level(), "merge:" + player.getName());
            } catch (IllegalArgumentException e) {
                module.fail(player, "merge.enchant-unknown", Msg.p("enchant", ret.enchant()));
                return null;
            }
            prepared.put(book.id(), book);
            ItemStack item = book.item();
            Optional<ItemDefinition> def = module.core.items().definitionOf(item);
            if (def.isEmpty()) {
                module.fail(player, "error");
                return null;
            }
            books.add(new WardrobeOps.ReturnedBook(book.id(), ret.enchant(), ret.level(), ret.piece(), def.get().type().id(), def.get().id(),
                    PlainTextComponentSerializer.plainText().serialize(item.effectiveName()), item.serializeAsBytes()));
        }
        EnchantService sink = service;
        Map<java.util.UUID, PreparedBook> byId = Map.copyOf(prepared);
        return new WardrobeOps.MergeEnchants(other, books, (c, b, playerId, profile, reason) -> {
            PreparedBook book = byId.get(b.id());
            if (book == null) {
                throw new java.sql.SQLException("Unknown returned book " + b.id());
            }
            sink.insertBook(c, book, "merge", playerId, profile, reason);
        });
    }

    /** Merges {@code a} (its set comes first) with {@code b}, pieces in the look of {@code lookSetId} (after the confirm). */
    void merge(Player player, UUID entryA, UUID entryB, String lookSetId) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        if (s.busy || s.stripped || module.equipment().profiles().isSwitching(player.getUniqueId())) {
            module.fail(player, "busy");
            return;
        }
        if (!enabled()) {
            module.fail(player, "merge.turned-off");
            return;
        }
        if (module.config().blockedWorld(player.getWorld().getName())) {
            module.fail(player, "blocked-world");
            return;
        }
        Optional<WardrobeStore.Entry> a = s.entry(entryA);
        Optional<WardrobeStore.Entry> b = s.entry(entryB);
        if (a.isEmpty() || b.isEmpty()) {
            module.fail(player, "not-owned");
            return;
        }
        Optional<MergeRules.Refusal> refusal = check(a.get(), b.get());
        if (refusal.isPresent()) {
            module.fail(player, "merge.refused", Msg.mm("why", module.text().raw("merge.why." + refusal.get().key())));
            return;
        }
        if (!lookSetId.equals(a.get().setId()) && !lookSetId.equals(b.get().setId())) {
            lookSetId = a.get().setId();
        }
        long fee = fee(a.get(), b.get());
        if (fee > 0 && module.shop().economy() == null) {
            module.fail(player, "unavailable");
            return;
        }
        SetDef defA = module.registry().get(a.get().setId()).orElseThrow();
        SetDef defB = module.registry().get(b.get().setId()).orElseThrow();
        // E5: the other set's armor enchants come back as books (built now, written in the merge transaction).
        WardrobeStore.Entry other = lookSetId.equals(a.get().setId()) ? b.get() : a.get();
        Map<ArmorPiece, List<ToolPerk>> otherEnchants = s.enchants(other.entryId());
        WardrobeOps.MergeEnchants enchants = returnedBooks(player, otherEnchants);
        if (enchants == null) {
            return; // told why
        }
        s.busy = true;
        UUID profile = s.profile;
        double pendingA = Optional.ofNullable(s.pendingXp.remove(entryA)).orElse(0.0);
        double pendingB = Optional.ofNullable(s.pendingXp.remove(entryB)).orElse(0.0);
        WardrobeOps.Who who = new WardrobeOps.Who(player.getUniqueId(), player.getName(), profile);
        String look = lookSetId;
        WardrobeOps.MergeRequest request = new WardrobeOps.MergeRequest(who, entryA, defA, entryB, defB, look, fee, pendingA, pendingB,
                module.config().levels(), module.config().seasonId, enchants);
        module.ops().merge(request).thenAccept(r -> module.core.scheduler().main("wardrobe-merge", () -> {
            s.busy = false;
            if (!r.ok()) {
                // Rolled back: the unsaved XP is still unsaved.
                if (pendingA > 0) {
                    s.pendingXp.merge(entryA, pendingA, Double::sum);
                }
                if (pendingB > 0) {
                    s.pendingXp.merge(entryB, pendingB, Double::sum);
                }
            }
            if (r.ok()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("entry", r.entry().entryId().toString());
                details.put("entry_a", entryA.toString());
                details.put("entry_b", entryB.toString());
                details.put("set_a", defA.id());
                details.put("set_b", defB.id());
                details.put("look", look);
                details.put("fee", fee);
                details.put("profile", profile.toString());
                details.put("kept_enchants", String.valueOf(r.enchants()));
                details.put("returned_books", enchants.books().stream().map(x -> x.id() + "=" + x.enchant() + ":" + x.level() + "@" + x.piece().key()).toList());
                module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_merge", who.actor(), who.player(), who.name(), fee, null, details);
                for (WardrobeOps.ReturnedBook book : enchants.books()) {
                    module.core.audit().logPlayer(AuditCategory.ITEM, "book_created", who.actor(), who.player(), who.name(), null, null,
                            Map.of("book", book.id().toString(), "kind", "book", "enchant", book.enchant(), "level", book.level(),
                                    "origin", "merge", "piece", book.piece().key(), "entry", r.entry().entryId().toString(), "to", "wardrobe_chest"));
                }
            }
            Player p = Bukkit.getPlayer(who.player());
            if (p == null) {
                return;
            }
            boolean same = module.equipment().sessionOrNull(p.getUniqueId()) == s && s.profile.equals(profile);
            switch (r.status()) {
                case OK -> {
                    if (same) {
                        s.pendingXp.remove(entryA);
                        s.pendingXp.remove(entryB);
                        s.remove(entryA);
                        s.remove(entryB);
                        s.add(r.entry());
                        for (Map.Entry<ArmorPiece, List<ToolPerk>> e : r.enchants().entrySet()) {
                            s.putEnchants(r.entry().entryId(), e.getKey(), e.getValue());
                        }
                        s.loadouts = Loadouts.repointed(s.loadouts, entryA, entryB, r.entry().entryId());
                        module.equipment().refreshBonuses(s);
                        module.equipment().reconcile(p);
                    }
                    module.msg().send(p, "merge.done", Msg.p("set", module.text().entryName(module.registry(), r.entry())),
                            Msg.p("fee", feeText(fee)));
                    if (!enchants.books().isEmpty()) {
                        module.msg().send(p, "merge.books-returned", Msg.p("count", enchants.books().size()));
                    }
                    module.core.sounds().play(p, "unlock");
                    if (same) {
                        module.menus().openSet(p, r.entry());
                        return;
                    }
                }
                case NOT_ENOUGH -> module.fail(p, "not-enough", Msg.p("price", feeText(fee)),
                        Msg.p("have", feeText(module.shop().economy() == null ? 0
                                : module.shop().economy().cached(p.getUniqueId(), Currency.CRYSTALS).orElse(0))));
                case REFUSED -> module.fail(p, "merge.refused", Msg.mm("why", module.text().raw("merge.why."
                        + (r.refusal() == null ? MergeRules.Refusal.GONE : r.refusal()).key())));
                case GONE -> {
                    module.fail(p, "merge.gone");
                    module.equipment().load(p);
                }
                default -> module.fail(p, "error");
            }
            module.menus().refreshOpen(p);
        }));
    }
}
