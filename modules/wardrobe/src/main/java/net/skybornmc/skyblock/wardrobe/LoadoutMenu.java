package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /loadout} (WARDROBE §7.5, LD5): up to 8 presets. Click a preset to apply it (the fast path); the Edit button
 * under it opens Save current · Rename · Icon · Delete. Empty places save the current gear; places above the player's
 * slots show how to get more. Click-only, Bedrock-safe.
 * <pre>
 *  presets 1-4: 10 12 14 16 (edit 19 21 23 25)   presets 5-8: 28 30 32 34 (edit 37 39 41 43)
 *  46 slots · 47 pet · 51 wardrobe
 * </pre>
 */
final class LoadoutMenu extends Menu {

    static final int[] PRESET_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};

    private final WardrobeModule module;
    /** Item ids in the Wardrobe Chest when the menu opened (missing tools show in red). */
    private final Set<UUID> chest;

    LoadoutMenu(WardrobeModule module, Set<UUID> chest) {
        this.module = module;
        this.chest = Set.copyOf(chest);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.menus().text(viewer, "menu.title"), module.menus().text(viewer, "menu.loadout.title"));
    }

    @Override
    public String messageModule() {
        return WardrobeModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.loadout.info";
    }

    @Override
    public String helpTopic() {
        return "loadout";
    }

    Set<UUID> chest() {
        return chest;
    }

    @Override
    protected void build() {
        frame();
        Session s = module.equipment().sessionOrNull(viewer.getUniqueId());
        if (s == null || !s.loaded) {
            return;
        }
        int slots = module.loadouts().slots(viewer, s);
        int max = Math.min(PRESET_SLOTS.length, module.config().loadoutMaxSlots);
        for (int i = 0; i < max; i++) {
            int slot = PRESET_SLOTS[i];
            Optional<Loadout> preset = s.loadoutAt(i);
            int index = i;
            if (preset.isPresent()) {
                Loadout l = preset.get();
                boolean usable = i < slots;
                set(slot, presetItem(viewer, s, l, usable), click -> {
                    if (usable) {
                        module.loadouts().apply(viewer, l);
                    } else {
                        module.fail(viewer, "loadout.locked");
                    }
                });
                set(slot + 9, service.item(Material.WRITABLE_BOOK, viewer, WardrobeModule.ID, "menu.loadout.edit", Msg.p("name", l.name())),
                        click -> openChild(new LoadoutEditMenu(module, l.id(), chest)));
            } else if (i < slots) {
                set(slot, service.item(Material.LIME_STAINED_GLASS_PANE, viewer, WardrobeModule.ID, "menu.loadout.empty",
                        Msg.p("n", i + 1)), click -> module.loadouts().saveNew(viewer, index));
            } else {
                set(slot, service.item(Material.GRAY_DYE, viewer, WardrobeModule.ID, "menu.loadout.locked-slot", Msg.p("n", i + 1)));
            }
        }
        set(46, service.item(Material.BOOK, viewer, WardrobeModule.ID, "menu.loadout.slots", Msg.p("used", Math.min(slots, s.loadouts.size())),
                Msg.p("slots", slots), Msg.p("max", module.config().loadoutMaxSlots)));
        boolean pets = module.loadouts().pets().available();
        set(47, service.item(Material.BONE, viewer, WardrobeModule.ID, pets ? "menu.loadout.pet" : "menu.loadout.pet-soon"));
        if (!(parent() instanceof WardrobeMenu)) {
            set(51, service.item(Material.LEATHER_CHESTPLATE, viewer, WardrobeModule.ID, "menu.loadout.wardrobe"),
                    click -> module.menus().openMain(viewer));
        }
    }

    /** A preset's button: its icon, contents and (in red) what is missing. */
    ItemStack presetItem(Player viewer, Session s, Loadout l, boolean usable) {
        List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.loadout.preset.lore", Msg.p("pieces", l.entries().size()),
                Msg.p("tools", l.hotbar().size())));
        int missing = missing(viewer, s, l, chest);
        if (missing > 0) {
            lore.addAll(module.menus().lines(viewer, "menu.loadout.preset.missing", Msg.p("count", missing)));
        }
        lore.add(Component.empty());
        lore.addAll(module.menus().lines(viewer, usable ? "menu.loadout.preset.click" : "menu.loadout.preset.locked"));
        return service.item(icon(l.icon(), module.config().loadoutDefaultIcon), module.menus().text(viewer, "menu.loadout.preset.name",
                Msg.p("name", l.name())), lore);
    }

    /** Sets that are gone plus tools that are neither in the inventory nor in the chest. */
    static int missing(Player viewer, Session s, Loadout l, Set<UUID> chest) {
        Set<UUID> inventory = inventoryIds(viewer);
        int n = 0;
        for (UUID entry : l.entries()) {
            if (s.entry(entry).isEmpty()) {
                n++;
            }
        }
        for (Loadout.Tool t : l.hotbar().values()) {
            if (!inventory.contains(t.itemId()) && !chest.contains(t.itemId())) {
                n++;
            }
        }
        return n;
    }

    static Set<UUID> inventoryIds(Player viewer) {
        Set<UUID> out = new HashSet<>();
        for (ItemStack item : viewer.getInventory().getContents()) {
            ItemRegistry.uuidOf(item).ifPresent(out::add);
        }
        return out;
    }

    /** A valid item material for an icon id, or the fallback (then armor stand). */
    static Material icon(String id, String fallback) {
        for (String candidate : new String[]{id, fallback}) {
            Material m = candidate == null ? null : Material.matchMaterial(candidate);
            if (m != null && m.isItem() && !m.isAir()) {
                return m;
            }
        }
        return Material.ARMOR_STAND;
    }

    /** Preview lines: every armor slot, the pet and every hotbar tool (missing ones in red). */
    static List<Component> contents(WardrobeModule module, Player viewer, Session s, Loadout l, Set<UUID> chest) {
        WardrobeMenus m = module.menus();
        List<Component> out = new ArrayList<>();
        for (ArmorPiece piece : ArmorPiece.values()) {
            String part = module.text().plain("piece-name." + piece.key());
            Loadout.Armor a = l.armor().get(piece);
            if (a == null) {
                out.add(m.text(viewer, "menu.loadout.line.keep", Msg.p("part", part)));
            } else if (a.empty()) {
                out.add(m.text(viewer, "menu.loadout.line.empty", Msg.p("part", part)));
            } else {
                Optional<WardrobeStore.Entry> e = s.entry(a.entryId());
                out.add(e.isPresent()
                        ? m.text(viewer, "menu.loadout.line.set", Msg.p("part", part), Msg.p("set", clip(module.text().entryName(module.registry(), e.get()))))
                        : m.text(viewer, "menu.loadout.line.gone", Msg.p("part", part)));
            }
        }
        out.add(m.text(viewer, module.loadouts().pets().available() && l.petId() != null ? "menu.loadout.line.pet" : "menu.loadout.line.pet-keep",
                Msg.p("pet", l.petId() == null ? "" : clip(l.petId()))));
        if (l.hotbar().isEmpty()) {
            out.add(m.text(viewer, "menu.loadout.line.no-tools"));
        }
        Set<UUID> inventory = inventoryIds(viewer);
        for (Map.Entry<Integer, Loadout.Tool> e : l.hotbar().entrySet()) {
            boolean there = inventory.contains(e.getValue().itemId()) || chest.contains(e.getValue().itemId());
            out.add(m.text(viewer, there ? "menu.loadout.line.tool" : "menu.loadout.line.tool-missing", Msg.p("slot", e.getKey() + 1),
                    Msg.p("item", clip(e.getValue().name()))));
        }
        return out;
    }

    private static String clip(String text) {
        String t = text == null ? "" : text;
        return t.length() > 20 ? t.substring(0, 19) + "." : t;
    }
}
