package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Fuel Cells (PHASE-7 M7.1, PD4): one unique custom item ({@code fuel_cell}) holding its minutes. Always bound to the
 * profile that got it (fuel can't be traded). USE it while standing on your own island: the item is claimed first, then
 * the fuel is added in one transaction; a full tank or an error gives it back. A cell is never partly wasted: it is only
 * used when it fits completely.
 */
final class FuelCells {

    static final String ITEM_ID = "fuel_cell";
    static final NamespacedKey MINUTES = new NamespacedKey(ItemKeys.NAMESPACE, "fuel_cell_minutes");

    /** What a cell holds. */
    record Spec(long minutes, UUID itemUuid) {
        long millis() {
            return minutes * FuelMath.MINUTE;
        }
    }

    private final ProductionModule m;
    private boolean registered;

    FuelCells(ProductionModule module) {
        this.m = module;
    }

    void register(Material material) {
        if (registered || m.core.items().get(ITEM_ID).isPresent()) {
            return;
        }
        m.core.items().register(ItemDefinition.builder(ITEM_ID, material, ItemType.CONSUMABLE, Rarity.UNCOMMON)
                .name(m.raw("cell.item.name").replace("{time}", "?"))
                .description(m.raw("cell.item.description"))
                .action(m.raw("cell.item.action-input"), m.raw("cell.item.action-text"))
                .unique()
                .glint()
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public String name(ItemStack stack, ItemDefinition definition) {
                        return read(stack).map(s -> "<rarity:" + FuelCells.rarity(s.minutes(), m.settings.cellSizes()).id() + ">"
                                + LoreBuilder.fill(m.raw("cell.item.name"), Map.of("time", FuelMath.time(s.millis()))))
                                .orElse(definition.name());
                    }

                    @Override
                    public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
                        read(stack).ifPresent(s -> {
                            Map<String, String> tokens = Map.of("time", FuelMath.time(s.millis()));
                            for (String line : m.rawList("cell.item.lore")) {
                                lore.line(LoreBuilder.fill(line, tokens));
                            }
                            if (BoundItems.isBound(stack)) {
                                lore.line(m.raw("cell.item.bound"));
                            }
                        });
                    }

                    @Override
                    public Rarity rarity(ItemStack stack, ItemDefinition definition) {
                        return read(stack).map(s -> FuelCells.rarity(s.minutes(), m.settings.cellSizes())).orElse(definition.rarity());
                    }
                })
                .build());
        registered = true;
    }

    void unregister() {
        if (registered) {
            m.core.items().unregister(ITEM_ID);
            registered = false;
        }
    }

    /** Rarity by size: the largest configured cell epic, the middle one rare, anything smaller uncommon. */
    static Rarity rarity(long minutes, long[] sizesLargestFirst) {
        if (sizesLargestFirst.length > 0 && minutes >= sizesLargestFirst[0]) {
            return Rarity.EPIC;
        }
        if (sizesLargestFirst.length > 1 && minutes >= sizesLargestFirst[1]) {
            return Rarity.RARE;
        }
        return Rarity.UNCOMMON;
    }

    static Optional<Spec> read(ItemStack stack) {
        if (!ItemRegistry.is(stack, ITEM_ID)) {
            return Optional.empty();
        }
        Optional<UUID> uuid = ItemRegistry.uuidOf(stack);
        Long minutes = stack.getPersistentDataContainer().get(MINUTES, PersistentDataType.LONG);
        if (uuid.isEmpty() || minutes == null || minutes < 1) {
            return Optional.empty();
        }
        return Optional.of(new Spec(minutes, uuid.get()));
    }

    static boolean isCell(ItemStack stack) {
        return ItemRegistry.is(stack, ITEM_ID);
    }

    /** Cells worth {@code minutes} in total, bound to {@code profile}. */
    List<ItemStack> create(UUID profile, long minutes, String origin) {
        List<ItemStack> out = new ArrayList<>();
        for (long size : FuelMath.splitIntoCells(minutes, m.settings.cellSizes(), m.settings.maxCellsPerHandOut)) {
            ItemStack stack = m.core.items().create(ITEM_ID, 1, origin, pdc -> pdc.set(MINUTES, PersistentDataType.LONG, size));
            if (profile != null) {
                BoundItems.mark(stack, profile);
                m.core.items().render(stack);
            }
            out.add(stack);
        }
        return out;
    }

    // ================================================================== using a cell

    /** USE (right-click) with a cell in the main hand. Main thread. */
    void use(Player p, ItemStack stack) {
        Optional<Spec> spec = read(stack);
        if (spec.isEmpty()) {
            m.msg.send(p, "cell.invalid");
            return;
        }
        if (!p.hasPermission(ProductionModule.PERM_CELLS)) {
            m.msg.send(p, "cell.no-permission");
            return;
        }
        Optional<String> problem = m.problem(p);
        if (problem.isPresent()) {
            m.msg.send(p, problem.get());
            return;
        }
        ProfileService profiles = ProfileService.of(m.core.services());
        UUID profile = profiles.profileKey(p);
        if (BoundItems.isBound(stack) && !BoundItems.mayReceive(stack, profile)) {
            m.msg.send(p, "cell.not-yours");
            return;
        }
        Optional<UUID> island = m.engine().ownIslandHere(p);
        if (island.isEmpty()) {
            m.msg.send(p, "cell.not-on-island");
            return;
        }
        Spec s = spec.get();
        Optional<String> refuse = m.engine().cellProblem(island.get(), s.millis());
        if (refuse.isPresent()) {
            m.msg.send(p, refuse.get(), Msg.p("room", FuelMath.time(m.engine().room(island.get()))), Msg.p("time", FuelMath.time(s.millis())));
            return;
        }
        Actor actor = Actor.player(p.getUniqueId(), p.getName());
        CellUse.run(() -> take(p, s.itemUuid()),
                () -> m.engine().addCell(island.get(), s.itemUuid(), profile, s.millis(), "fuel_cell", actor),
                item -> m.core.scheduler().ensureMain("production:cell-back", () -> giveBack(p, profile, item)))
                .whenCompleteAsync((outcome, error) -> done(p, island.get(), s, outcome == null ? CellUse.Outcome.ERROR : outcome), m.main());
    }

    private void done(Player p, UUID island, Spec s, CellUse.Outcome outcome) {
        if (!p.isOnline()) {
            return;
        }
        switch (outcome) {
            case ADDED -> {
                var view = m.engine().view(island);
                m.msg.send(p, "cell.added", Msg.p("time", FuelMath.time(s.millis())), Msg.p("fuel", FuelMath.time(view.fuelMs())),
                        Msg.p("tank", FuelMath.time(view.capacityMs())));
                m.sound(p, m.settings.cellSound);
                if (m.settings.savePlayerData) {
                    PlayerSaves.save(p, "a Fuel Cell");
                }
            }
            case NO_ROOM -> m.msg.send(p, "cell.no-room", Msg.p("room", FuelMath.time(m.engine().room(island))),
                    Msg.p("time", FuelMath.time(s.millis())));
            case REUSED -> m.msg.send(p, "cell.already-used");
            case MOVED -> m.msg.send(p, "cell.moved");
            case ERROR -> m.msg.send(p, "cell.error");
        }
    }

    /** Takes one cell with this unique id out of the inventory; a copy of it. */
    private Optional<ItemStack> take(Player p, UUID itemUuid) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack s = contents[slot];
            if (s != null && ItemRegistry.uuidOf(s).filter(itemUuid::equals).isPresent()) {
                ItemStack copy = s.clone();
                copy.setAmount(1);
                if (s.getAmount() > 1) {
                    s.setAmount(s.getAmount() - 1);
                    inv.setItem(slot, s);
                } else {
                    inv.setItem(slot, null);
                }
                return Optional.of(copy);
            }
        }
        return Optional.empty();
    }

    /** A refused or failed cell goes back: the inventory, else /box (never dropped). */
    private void giveBack(Player online, UUID profile, ItemStack item) {
        if (online.isOnline() && ProfileService.of(m.core.services()).profileKey(online).equals(profile)) {
            ItemBox.give(m.core.services(), online, BoxSource.PRODUCTION, item);
            return;
        }
        ItemBox.of(m.core.services()).ifPresentOrElse(
                box -> box.deliver(profile, List.of(item), BoxSource.PRODUCTION, Actor.system(ProductionModule.ID)).exceptionally(e -> {
                    m.core.plugin().getLogger().log(Level.SEVERE, "Production: could not give a Fuel Cell back to profile " + profile, e);
                    return null;
                }),
                () -> m.core.plugin().getLogger().severe("Production: no item box to give a Fuel Cell back to profile " + profile));
    }
}
