package net.skybornmc.skyblock.islandtools;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Owner 7 Nov 2026 (OWNER_PICKS_7NOV.md section 6): tool skins. A skin is only a look (a vanilla item model until the pack v2
 * art exists): Iron, Golden, Copper and Netherite for the hoe, pickaxe and sword, bought once with credits, switched any
 * time; Diamond is the free default. Owned skins and the chosen one are rows of {@code island_tool_enchants}
 * ({@code skin.<id>} = owned, {@code skin._on} = the chosen skin's place in the list, 0 = Diamond).
 */
final class ToolSkins {

    static final String OWNED = "skin.";
    static final String ON = "skin._on";
    static final List<Kind> KINDS = List.of(Kind.HOE, Kind.PICKAXE, Kind.SWORD);

    record Skin(String id, long price) {

        /** The vanilla item of this skin for a tool kind ({@code golden} + hoe = golden_hoe). */
        Material material(Kind kind) {
            return Material.matchMaterial(id + "_" + kind.id());
        }
    }

    private final IslandToolsModule m;

    ToolSkins(IslandToolsModule m) {
        this.m = m;
    }

    /** Parses "id:credits" lines; skins with no item for any tool kind are skipped. Pure. */
    static List<Skin> parse(List<String> lines, Consumer<String> warn) {
        List<Skin> out = new ArrayList<>();
        for (String line : lines) {
            String[] p = line.trim().toLowerCase(java.util.Locale.ROOT).split(":");
            try {
                Skin s = new Skin(p[0].trim(), Long.parseLong(p[1].trim()));
                if (s.price() < 0 || KINDS.stream().anyMatch(k -> s.material(k) == null)) {
                    throw new IllegalArgumentException();
                }
                out.add(s);
            } catch (RuntimeException e) {
                warn.accept("islandtools.yml skins.list: '" + line + "' needs id:credits with a vanilla <id>_hoe/_pickaxe/_sword (skipped)");
            }
        }
        return List.copyOf(out);
    }

    List<Skin> skins() {
        return parse(m.config().skins, w -> { });
    }

    private Map<String, Integer> rows(UUID profile) {
        Map<String, Integer> r = m.enchants().rows(profile);
        return r == null ? Map.of() : r;
    }

    boolean owns(UUID profile, Kind kind, Skin skin) {
        return rows(profile).getOrDefault(kind.id() + ":" + OWNED + skin.id(), 0) > 0;
    }

    /** The chosen skin of a tool (empty = Diamond). */
    Optional<Skin> chosen(UUID profile, Kind kind) {
        int on = rows(profile).getOrDefault(kind.id() + ":" + ON, 0);
        List<Skin> all = skins();
        if (on <= 0 || on > all.size() || !owns(profile, kind, all.get(on - 1))) {
            return Optional.empty();
        }
        return Optional.of(all.get(on - 1));
    }

    /** Puts the chosen look on a handle (Diamond = the item's own model). */
    void apply(ItemStack stack, Kind kind, UUID profile) {
        if (stack == null || !KINDS.contains(kind)) {
            return;
        }
        Optional<Skin> skin = profile == null ? Optional.empty() : chosen(profile, kind);
        if (skin.isPresent() && skin.get().material(kind) != null) {
            stack.setData(DataComponentTypes.ITEM_MODEL, skin.get().material(kind).getKey());
        } else {
            stack.resetData(DataComponentTypes.ITEM_MODEL);
        }
    }

    /** Chooses an owned skin ({@code index} 1.. in the list, 0 = Diamond). */
    void choose(Player p, Kind kind, int index, Runnable done) {
        UUID profile = m.profile(p);
        Map<String, Integer> r = m.enchants().rows(profile);
        if (r == null) {
            return;
        }
        int before = r.getOrDefault(kind.id() + ":" + ON, 0);
        if (before == index) {
            done.run();
            return;
        }
        r.put(kind.id() + ":" + ON, index); // shown at once; saved after
        m.refreshOwnHandles(p);
        m.core().database().transaction(c -> {
            if (!EnchantStore.set(c, profile, kind, ON, before, index)) {
                net.skybornmc.skyblock.common.db.Sql.update(c, "UPDATE island_tool_enchants SET level = ?, updated_at = ? WHERE profile_id = ? "
                        + "AND kind = ? AND enchant = ?", index, System.currentTimeMillis(), profile, kind.id(), ON);
            }
            return null;
        });
        m.core().sounds().play(p, "click");
        done.run();
    }

    /** Buys a skin with credits (one payment and the owned row in one transaction), then wears it. */
    void buy(Player p, Kind kind, Skin skin, int index, Runnable done) {
        EconomyService economy = m.core().services().optional(EconomyService.class).orElse(null);
        UUID profile = m.profile(p);
        if (economy == null || m.enchants().rows(profile) == null || owns(profile, kind, skin)) {
            return;
        }
        long balance = economy.cached(p.getUniqueId(), Currency.CREDITS).orElse(Long.MAX_VALUE);
        if (balance < skin.price()) {
            m.msg().send(p, "skins.poor", Msg.number("price", skin.price()), Msg.number("have", Math.max(0, balance)));
            m.core().sounds().play(p, "error");
            return;
        }
        UUID batch = UUID.randomUUID();
        Transaction tx = Transaction.withdraw(AccountRef.player(profile), Currency.CREDITS, skin.price(), "tool_skin")
                .actor(Actor.player(p)).reference("tool_skin:" + batch).idempotencyKey("tool_skin:" + batch)
                .detail("kind", kind.id()).detail("skin", skin.id());
        economy.executeBatch(List.of(tx), c -> EnchantStore.set(c, profile, kind, OWNED + skin.id(), 0, 1))
                .whenComplete((BatchResult result, Throwable error) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                    if (error != null || result == null || !result.ok()) {
                        if (p.isOnline()) {
                            m.msg().send(p, "skins.failed");
                            m.core().sounds().play(p, "error");
                        }
                        return;
                    }
                    Map<String, Integer> r = m.enchants().rows(profile);
                    if (r != null) {
                        r.put(kind.id() + ":" + OWNED + skin.id(), 1);
                    }
                    m.core().audit().logPlayer(AuditCategory.ITEM, "tool_skin_buy", Actor.player(p), p.getUniqueId(), p.getName(),
                            skin.price(), p.getLocation(), Map.of("kind", kind.id(), "skin", skin.id(), "currency", "credits"));
                    if (p.isOnline()) {
                        m.msg().send(p, "skins.bought", Msg.p("skin", m.raw("skins.name." + skin.id())),
                                Msg.p("tool", m.raw("item.name." + kind.id())));
                        m.core().sounds().play(p, "unlock");
                        choose(p, kind, index, done);
                    }
                }));
    }
}
