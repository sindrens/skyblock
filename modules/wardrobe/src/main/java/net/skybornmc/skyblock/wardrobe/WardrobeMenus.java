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

    /** Opens the set screen of an entry (merged entries get their own screen). */
    void openSet(Player player, WardrobeStore.Entry entry) {
        Optional<SetDef> def = module.registry().get(entry.setId());
        if (def.isEmpty()) {
            openMain(player);
            return;
        }
        WardrobeMenu main = new WardrobeMenu(module);
        module.core.menus().open(player, main);
        main.openChild(new SetMenu(module, def.get(), entry.isMerged() ? entry.entryId() : null));
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
        } else if (open instanceof WardrobeMenu || open instanceof SetMenu || open instanceof StorePickMenu || open instanceof MergePickMenu) {
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
                int level = e.map(WardrobeStore.Entry::level).orElse(0);
                out.addAll(lines(viewer, module.config().levelsEnabled ? "status.owned" : "status.owned-fixed", Msg.p("worn", worn),
                        Msg.p("level", level), Msg.p("multiplier", WardrobeText.number(module.bonusMultiplier(level)))));
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

    /** Status lines of a merged entry (list entry and its set screen). */
    List<Component> mergedStatus(Player viewer, WardrobeStore.Entry e, Session s) {
        int worn = s == null ? 0 : s.worn(e.entryId());
        String other = module.registry().get(e.mergedSetId()).map(SetDef::name).orElse(String.valueOf(e.mergedSetId()));
        String first = module.registry().get(e.setId()).map(SetDef::name).orElse(e.setId());
        return lines(viewer, "status.merged", Msg.p("a", first), Msg.p("b", other), Msg.p("worn", worn), Msg.p("level", e.level()),
                Msg.p("multiplier", WardrobeText.number(module.bonusMultiplier(e.level()))));
    }

    /**
     * The level item (set screen, WARDROBE §6.2): {@code Lv 23 ●●●●○○○○○○ 46% · 0.92× · next: 1,612 XP · earns from: Mining}.
     *
     * @param e the owned entry, or null (not owned: shows the curve's start)
     */
    org.bukkit.inventory.ItemStack levelItem(Player viewer, WardrobeStore.Entry e, SetDef def) {
        var service = module.core.menus();
        if (!module.config().levelsEnabled) {
            return service.item(org.bukkit.Material.EXPERIENCE_BOTTLE, viewer, WardrobeModule.ID, "menu.set.level-off",
                    Msg.p("multiplier", WardrobeText.number(module.bonusMultiplier(0))));
        }
        SetLevels levels = module.config().levels();
        boolean merged = e != null && e.isMerged();
        double xp = e == null ? 0 : e.xp();
        int level = e == null ? 0 : e.level();
        double progress = levels.progress(xp, merged);
        List<Component> lore = new ArrayList<>(lines(viewer, "menu.set.level.lore",
                Msg.p("bar", WardrobeText.bar(progress, 10)), Msg.p("percent", (int) Math.floor(progress * 100)),
                Msg.p("multiplier", WardrobeText.number(module.bonusMultiplier(level))),
                Msg.p("max", module.config().maxLevel),
                Msg.p("max_multiplier", WardrobeText.number(module.bonusMultiplier(module.config().maxLevel)))));
        if (level >= module.config().maxLevel) {
            lore.addAll(lines(viewer, "menu.set.level.maxed"));
        } else {
            lore.addAll(lines(viewer, "menu.set.level.next", Msg.p("xp", WardrobeText.number(Math.ceil(levels.toNext(xp, merged)))),
                    Msg.p("next", level + 1)));
        }
        lore.addAll(lines(viewer, "menu.set.level.earns", Msg.p("from", earnsFrom(e, def))));
        if (merged) {
            lore.addAll(lines(viewer, "menu.set.level.merged", Msg.p("factor", WardrobeText.number(module.config().mergedXpFactor))));
        }
        if (e == null) {
            lore.addAll(lines(viewer, "menu.set.level.not-owned"));
        }
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        if (s != null && s.warzone) {
            lore.addAll(lines(viewer, "menu.set.level.warzone", Msg.p("multiplier", WardrobeText.number(module.config().warzoneMultiplier))));
        }
        return service.item(org.bukkit.Material.EXPERIENCE_BOTTLE, text(viewer, "menu.set.level.name", Msg.p("level", level)), lore);
    }

    /** {@code Mining, Selling} (plain): the XP activities an entry (or set) earns from. */
    String earnsFrom(WardrobeStore.Entry e, SetDef def) {
        java.util.LinkedHashSet<String> activities = new java.util.LinkedHashSet<>(module.config().sources(def.activity()).keySet());
        if (e != null && e.isMerged()) {
            module.registry().get(e.setId().equals(def.id()) ? e.mergedSetId() : e.setId())
                    .ifPresent(o -> activities.addAll(module.config().sources(o.activity()).keySet()));
        }
        List<String> names = new ArrayList<>();
        for (String a : activities) {
            names.add(module.text().plain("xp-activity." + a));
        }
        return names.isEmpty() ? module.text().plain("xp-activity.none") : String.join(", ", names);
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
