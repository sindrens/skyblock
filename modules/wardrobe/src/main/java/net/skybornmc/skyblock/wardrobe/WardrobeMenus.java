package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Opening the wardrobe menus and the texts they share (requirement lines, offer status, worn bonuses). Every menu is
 * click-only (no dragging, no shift-click needed), so it works the same on Bedrock (WARDROBE §10).
 */
final class WardrobeMenus {

    private final WardrobeModule module;

    WardrobeMenus(WardrobeModule module) {
        this.module = module;
    }

    void openMain(Player player) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        module.equipment().reconcile(player);
        module.core.menus().open(player, new WardrobeMenu(module));
    }

    /** The Wardrobe Chest: loads the stored items first. */
    void openChest(Player player, Menu parent) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        UUID uuid = player.getUniqueId();
        UUID profile = s.profile;
        module.core.database().async(c -> module.store().stored(c, profile)).whenComplete((rows, error) ->
                module.core.scheduler().main("wardrobe-chest", () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) {
                        return;
                    }
                    if (error != null) {
                        module.core.plugin().getLogger().log(Level.WARNING, "Wardrobe: could not load the chest", Database.cause(error));
                        module.fail(p, "error");
                        return;
                    }
                    ChestMenu menu = new ChestMenu(module, rows);
                    if (parent != null) {
                        menu.parent(parent);
                    }
                    module.core.menus().open(p, menu);
                }));
    }

    /** Rebuilds an open wardrobe menu after a change (reloads the chest list when the chest is open). */
    void refreshOpen(Player player) {
        Menu open = module.core.menus().openMenu(player);
        if (open instanceof ChestMenu chest) {
            openChest(player, chest.parent());
        } else if (open instanceof WardrobeMenu || open instanceof SetMenu || open instanceof StorePickMenu) {
            open.refresh();
        }
    }

    // ------------------------------------------------------------------ texts

    Component text(Player viewer, String key, TagResolver... placeholders) {
        return module.core.menus().text(viewer, WardrobeModule.ID, key, placeholders);
    }

    List<Component> lines(Player viewer, String key, TagResolver... placeholders) {
        return module.core.menus().lines(viewer, WardrobeModule.ID, key, placeholders);
    }

    /** MiniMessage of a gate requirement: {@code Mining tier 6 ▸ 4/6}. */
    String requirement(Player viewer, Offers.GateStatus gate) {
        WardrobeText t = module.text();
        String key = switch (gate.kind()) {
            case "tier" -> gate.wing().equals("any") ? "gate.tier-any" : "gate.tier";
            case "any-skill" -> "gate.any-skill";
            case "skill" -> "gate.skill";
            default -> "gate.none";
        };
        return LoreBuilder.fill(t.raw(key), Map.of(
                "wing", LoreBuilder.escape(t.plain("wing-name." + gate.wing())),
                "need", String.valueOf(gate.need()),
                "have", String.valueOf(Math.min(gate.have(), gate.need()))));
    }

    /** "Also from" crate names, comma separated. */
    String crates(SetDef def) {
        List<String> names = new ArrayList<>();
        for (String c : def.source().crates()) {
            String raw = module.core.messages().raw(WardrobeModule.ID, "crate-name." + c);
            names.add(raw == null ? c : raw.replaceAll("<[^>]+>", ""));
        }
        return String.join(", ", names);
    }

    /** Status lines of a set for its list entry and detail screen. */
    List<Component> status(Player viewer, SetDef def, Offers.Offer offer, Session s) {
        List<Component> out = new ArrayList<>();
        switch (offer.kind()) {
            case OWNED -> {
                Optional<WardrobeStore.Entry> e = s == null ? Optional.empty() : s.owned(def.id());
                int worn = e.map(x -> s.worn(x.entryId())).orElse(0);
                out.addAll(lines(viewer, "status.owned", Msg.p("worn", worn),
                        Msg.p("multiplier", WardrobeText.number(module.bonusMultiplier(e.map(WardrobeStore.Entry::level).orElse(0))))));
                if (!def.enabled()) {
                    out.addAll(lines(viewer, "status.disabled"));
                }
            }
            case DISABLED -> out.addAll(lines(viewer, "status.disabled"));
            case ARRIVES -> out.addAll(lines(viewer, "status.arrives", Msg.p("feature", module.text().plain("feature-name." + def.enabledFrom()))));
            case LOCKED -> out.addAll(lines(viewer, "status.locked", Msg.mm("requirement", requirement(viewer, offer.gate()))));
            case CLAIM -> out.addAll(lines(viewer, "status.claim"));
            case CLAIMED -> out.addAll(lines(viewer, "status.claimed"));
            case BUY -> out.addAll(lines(viewer, "status.buy", Msg.p("price", module.shop().price(offer.currency(), offer.price()))));
            case CRATE_ONLY -> out.addAll(lines(viewer, "status.crate", Msg.p("crates", crates(def))));
        }
        if (offer.kind() != Offers.Kind.CRATE_ONLY && !def.source().crates().isEmpty() && offer.kind() != Offers.Kind.OWNED) {
            out.addAll(lines(viewer, "status.also-from", Msg.p("crates", crates(def))));
        }
        return out;
    }

    /** "Worn now": every active bonus line with its value; families the cap cuts show "capped". */
    List<Component> wornLines(Player viewer) {
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        List<Component> out = new ArrayList<>();
        if (s == null || s.bonuses.lines().isEmpty()) {
            out.addAll(lines(viewer, "menu.main.worn.none"));
            return out;
        }
        Optional<BonusService> bonus = module.core.services().optional(BonusService.class);
        for (SetBonuses.Line line : s.bonuses.all()) {
            String activity = line.activities().isEmpty() ? null : line.activities().getFirst();
            boolean capped = bonus.map(b -> b.isCapped(line.family(), new BonusContext(viewer, null, null, activity))).orElse(false);
            out.add(text(viewer, capped ? "menu.main.worn.line-capped" : "menu.main.worn.line",
                    Msg.mm("text", module.text().bonus(line.text(), line.value()))));
        }
        return out;
    }
}
