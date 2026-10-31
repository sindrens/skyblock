package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.item.DupeDetector;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Getting sets (WARDROBE §2.4): buying with money/crystals/gems, free gem-set claims (Sigils), redeeming Set Tokens,
 * minting tokens, and the staff give/remove actions. The database decides (one transaction each); the player's
 * session and items follow on the main thread. Every change is audited.
 */
final class Shop {

    private final WardrobeModule module;

    Shop(WardrobeModule module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ offers

    Map<SkillType, Integer> skills(Player player) {
        Map<SkillType, Integer> out = new EnumMap<>(SkillType.class);
        Optional<SkillService> skills = module.core.services().optional(SkillService.class);
        for (SkillType t : SkillType.values()) {
            out.put(t, skills.map(s -> s.level(player, t)).orElse(0));
        }
        return out;
    }

    Offers.GateStatus gate(Player player, SetDef def) {
        return Offers.gate(def.gate(), skills(player), this::tiers);
    }

    /**
     * Skill levels per tier of a wing ({@code wardrobe.yml → tiers}). Farming reads the crop tiers of the production module
     * while island farming is on (PHASE-7 M7.3), so Cropwarden opens at crop tier 6 without a second list to keep in sync.
     */
    private java.util.List<Integer> tiers(String wing) {
        if ("farming".equals(wing)) {
            java.util.List<Integer> crops = net.skybornmc.skyblock.common.production.FarmingService.of(module.core.services())
                    .map(net.skybornmc.skyblock.common.production.FarmingService::tierLevels).orElse(java.util.List.of());
            if (!crops.isEmpty()) {
                return crops;
            }
        }
        return module.config().tiers(wing);
    }

    /** What this player can do with a set right now. */
    Offers.Offer offer(Player player, Session s, SetDef def) {
        boolean owned = s != null && s.owned(def.id()).isPresent();
        Offers.State state = new Offers.State(owned, s != null && s.sigils.contains(def.id()), s != null && s.claims.contains(def.id()));
        return Offers.offer(def, state, gate(player, def), module.config().featureAvailable(def.enabledFrom()));
    }

    EconomyService economy() {
        return module.core.services().optional(EconomyService.class).orElse(null);
    }

    String price(Currency currency, long amount) {
        EconomyService eco = economy();
        return eco == null ? String.valueOf(amount) : eco.format(currency, amount);
    }

    // ------------------------------------------------------------------ buy / claim

    /** Buys a set (after the confirm dialog). */
    void buy(Player player, SetDef def) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (!ready(player, s)) {
            return;
        }
        Offers.Offer offer = offer(player, s, def);
        if (offer.kind() != Offers.Kind.BUY) {
            module.fail(player, "cant-buy");
            return;
        }
        if (economy() == null) {
            module.fail(player, "unavailable");
            return;
        }
        s.busy = true;
        UUID profile = s.profile;
        WardrobeOps.Who who = new WardrobeOps.Who(player.getUniqueId(), player.getName(), profile);
        module.ops().buy(who, def, module.config().seasonId).thenAccept(r -> module.core.scheduler().main("wardrobe-buy", () -> {
            s.busy = false;
            Player p = Bukkit.getPlayer(who.player());
            if (r.ok()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("set", def.id());
                details.put("entry", r.entry().entryId().toString());
                details.put("currency", offer.currency().key());
                details.put("price", offer.price());
                details.put("profile", profile.toString());
                details.put("sigil", offer.currency() == Currency.GEMS);
                module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_buy", who.actor(), who.player(), who.name(), offer.price(), null, details);
            }
            if (p == null) {
                return;
            }
            switch (r.status()) {
                case OK -> {
                    if (module.equipment().sessionOrNull(p.getUniqueId()) == s && s.profile.equals(profile)) {
                        s.add(r.entry());
                        if (offer.currency() == Currency.GEMS) {
                            s.sigils = plus(s.sigils, def.id());
                            s.claims = plus(s.claims, def.id());
                        }
                    }
                    module.msg().send(p, "bought", Msg.p("set", def.name()), Msg.p("price", price(offer.currency(), offer.price())));
                    module.core.sounds().play(p, "unlock");
                }
                case NOT_ENOUGH -> module.fail(p, "not-enough", Msg.p("price", price(offer.currency(), offer.price())),
                        Msg.p("have", price(offer.currency(), economy() == null ? 0 : economy().cached(p.getUniqueId(), offer.currency()).orElse(0))));
                case OWNED -> module.fail(p, "owned");
                case HAS_SIGIL, CLAIMED -> {
                    module.fail(p, "has-sigil");
                    module.equipment().load(p);
                }
                default -> module.fail(p, "error");
            }
            module.menus().refreshOpen(p);
        }));
    }

    /** Claims a gem set the account has a Sigil for (free, once per profile per season). */
    void claim(Player player, SetDef def) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (!ready(player, s)) {
            return;
        }
        if (offer(player, s, def).kind() != Offers.Kind.CLAIM) {
            module.fail(player, "cant-claim");
            return;
        }
        s.busy = true;
        UUID profile = s.profile;
        WardrobeOps.Who who = new WardrobeOps.Who(player.getUniqueId(), player.getName(), profile);
        module.ops().claim(who, def.id(), module.config().seasonId).thenAccept(r -> module.core.scheduler().main("wardrobe-claim", () -> {
            s.busy = false;
            if (r.ok()) {
                module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_claim", who.actor(), who.player(), who.name(), null, null,
                        Map.of("set", def.id(), "entry", r.entry().entryId().toString(), "profile", profile.toString(),
                                "season", module.config().seasonId));
            }
            Player p = Bukkit.getPlayer(who.player());
            if (p == null) {
                return;
            }
            switch (r.status()) {
                case OK -> {
                    if (module.equipment().sessionOrNull(p.getUniqueId()) == s && s.profile.equals(profile)) {
                        s.add(r.entry());
                        s.claims = plus(s.claims, def.id());
                    }
                    module.msg().send(p, "claimed", Msg.p("set", def.name()));
                    module.core.sounds().play(p, "unlock");
                }
                case OWNED -> module.fail(p, "owned");
                case CLAIMED -> module.fail(p, "claimed-already");
                case NO_SIGIL -> module.fail(p, "cant-claim");
                default -> module.fail(p, "error");
            }
            module.menus().refreshOpen(p);
        }));
    }

    // ------------------------------------------------------------------ tokens

    /** Redeems the token in an inventory slot (right-click or the Redeem button). */
    void redeem(Player player, int slot) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (!ready(player, s)) {
            return;
        }
        ItemStack item = player.getInventory().getItem(slot);
        Optional<String> setId = WardrobeItems.tokenSet(item);
        Optional<UUID> tokenId = ItemRegistry.uuidOf(item);
        if (setId.isEmpty() || tokenId.isEmpty()) {
            module.fail(player, "token-gone");
            return;
        }
        Optional<SetDef> def = module.registry().get(setId.get());
        if (def.isEmpty()) {
            module.fail(player, "unknown-set");
            return;
        }
        boolean owned = s.owned(def.get().id()).isPresent();
        Offers.GateStatus gate = gate(player, def.get());
        boolean feature = module.config().featureAvailable(def.get().enabledFrom());
        if (!Offers.canRedeem(def.get(), owned, gate, feature)) {
            module.fail(player, owned ? "owned" : !def.get().enabled() ? "set-disabled" : !feature ? "arrives" : "locked",
                    Msg.p("set", def.get().name()), Msg.mm("requirement", module.menus().requirement(player, gate)));
            return;
        }
        // The database decides first; the token stays in the (locked) inventory and is taken after the commit. If the
        // player leaves meanwhile the item stays behind as a copy of a redeemed token, which is cleaned on join.
        s.busy = true;
        UUID profile = s.profile;
        UUID id = tokenId.get();
        WardrobeOps.Who who = new WardrobeOps.Who(player.getUniqueId(), player.getName(), profile);
        module.ops().redeem(who, id, def.get().id(), module.config().seasonId).thenAccept(r -> module.core.scheduler().main("wardrobe-redeem", () -> {
            s.busy = false;
            Player p = Bukkit.getPlayer(who.player());
            if (r.ok()) {
                module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_token_redeem", who.actor(), who.player(), who.name(), null, null,
                        Map.of("set", def.get().id(), "token", id.toString(), "entry", r.entry().entryId().toString(), "profile", profile.toString()));
            }
            if (p == null) {
                return;
            }
            switch (r.status()) {
                case OK -> {
                    removeItem(p.getInventory(), id);
                    if (module.equipment().sessionOrNull(p.getUniqueId()) == s && s.profile.equals(profile)) {
                        s.add(r.entry());
                    }
                    module.msg().send(p, "redeemed", Msg.p("set", def.get().name()));
                    module.core.sounds().play(p, "unlock");
                }
                case TOKEN_INVALID -> confiscate(p, id, def.get().id(), "redeem");
                case OWNED -> module.fail(p, "owned");
                default -> module.fail(p, "error");
            }
            module.menus().refreshOpen(p);
        }));
    }

    /** First inventory slot holding a token for this set, or -1. */
    static int tokenSlot(PlayerInventory inv, String setId) {
        for (int i = 0; i < inv.getStorageContents().length; i++) {
            if (WardrobeItems.tokenSet(inv.getItem(i)).filter(setId::equals).isPresent()) {
                return i;
            }
        }
        return -1;
    }

    /** A copied token was used: it is taken, staff are told, audited. */
    private void confiscate(Player player, UUID tokenId, String setId, String where) {
        removeItem(player.getInventory(), tokenId);
        module.fail(player, "token-copy");
        module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_token_confiscated", Actor.system("wardrobe"), player.getUniqueId(),
                player.getName(), null, player.getLocation(), Map.of("token", tokenId.toString(), "set", setId, "where", where));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(DupeDetector.PERM_NOTIFY)) {
                module.msg().send(staff, "staff-token-copy", Msg.p("player", player.getName()), Msg.p("id", tokenId.toString()));
            }
        }
    }

    /** On join: tokens in the inventory whose row is not active any more (redeemed, void) are copies and are taken. */
    void cleanTokens(Player player) {
        Map<UUID, String> tokens = new LinkedHashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            Optional<String> set = WardrobeItems.tokenSet(item);
            Optional<UUID> id = ItemRegistry.uuidOf(item);
            if (set.isPresent() && id.isPresent()) {
                tokens.put(id.get(), set.get());
            }
        }
        if (tokens.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        module.core.database().async(c -> {
            Set<UUID> dead = new LinkedHashSet<>();
            for (UUID id : tokens.keySet()) {
                Optional<WardrobeStore.Token> row = module.store().token(c, id, false);
                if (row.isEmpty() || row.get().state() != WardrobeStore.TokenState.ACTIVE) {
                    dead.add(id);
                }
            }
            return dead;
        }).whenComplete((dead, error) -> module.core.scheduler().main("wardrobe-tokens", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || error != null || dead.isEmpty()) {
                return;
            }
            for (UUID id : dead) {
                confiscate(p, id, tokens.get(id), "join");
            }
        }));
    }

    /**
     * Makes a token: the item is created (unique id), its row written, then the future completes on the main thread.
     * A failed write completes exceptionally and the item is never handed out.
     */
    CompletableFuture<ItemStack> createToken(String setId, String origin, UUID owner, Actor actor) {
        if (module.registry().get(setId).isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown set '" + setId + "'"));
        }
        ItemStack item = module.items().token(setId, origin);
        UUID id = ItemRegistry.uuidOf(item).orElseThrow();
        CompletableFuture<ItemStack> out = new CompletableFuture<>();
        module.ops().mintToken(id, setId, origin, owner).thenAccept(ok -> module.core.scheduler().main("wardrobe-token", () -> {
            if (!ok) {
                out.completeExceptionally(new IllegalStateException("Could not save the token"));
                return;
            }
            module.core.audit().log(AuditCategory.ITEM, "wardrobe_token_created", actor, id.toString(), setId, null, null,
                    Map.of("set", setId, "origin", origin, "owner", owner == null ? "" : owner.toString()));
            out.complete(item);
        }));
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private boolean ready(Player player, Session s) {
        String problem = s == null || !s.loaded ? "loading" : s.busy || module.equipment().profiles().isSwitching(player.getUniqueId()) ? "busy" : null;
        if (problem != null) {
            module.fail(player, problem);
            return false;
        }
        return true;
    }

    static boolean removeItem(PlayerInventory inv, UUID id) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (ItemRegistry.uuidOf(inv.getItem(i)).filter(id::equals).isPresent()) {
                inv.setItem(i, null);
                return true;
            }
        }
        return false;
    }

    private static Set<String> plus(Set<String> set, String value) {
        List<String> list = new ArrayList<>(set);
        list.add(value);
        return Set.copyOf(list);
    }
}
