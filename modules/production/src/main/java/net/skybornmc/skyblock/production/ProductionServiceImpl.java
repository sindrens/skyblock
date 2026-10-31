package net.skybornmc.skyblock.production;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.production.FuelState;
import net.skybornmc.skyblock.common.production.FuelView;
import net.skybornmc.skyblock.common.production.ProductionService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** {@link ProductionService} of the production module (published only while Island Fuel is on). */
final class ProductionServiceImpl implements ProductionService {

    private final ProductionModule m;

    ProductionServiceImpl(ProductionModule module) {
        this.m = module;
    }

    @Override
    public FuelView fuel(UUID islandId) {
        return m.engine().view(islandId);
    }

    @Override
    public List<ItemStack> createFuelCells(UUID profileKey, long minutes, String origin) {
        return m.cells().create(profileKey, minutes, origin);
    }

    @Override
    public Optional<ItemStack> fuelGauge(Player viewer, UUID islandId) {
        if (viewer == null || islandId == null || !viewer.hasPermission(ProductionModule.PERM_MENU)) {
            return Optional.empty();
        }
        FuelView v = fuel(islandId);
        Material material = Material.matchMaterial(m.settings.gaugeMaterial.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.BLAZE_POWDER;
        }
        var placeholders = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                Msg.c("state", m.msg.component(viewer, "state." + v.state().key())),
                Msg.p("fuel", FuelMath.time(v.fuelMs())),
                Msg.p("tank", FuelMath.time(v.capacityMs())),
                Msg.p("level", v.tankLevel()),
                Msg.p("burn", FuelMath.rate(v.burnRate())),
                Msg.p("efficiency", FuelMath.rate(v.efficiencyPercent())),
                Msg.p("ago", FuelMath.time(v.asleepSince() <= 0 ? 0 : System.currentTimeMillis() - v.asleepSince()))};
        List<Component> lore = new ArrayList<>(m.core.menus().lines(viewer, ProductionModule.ID, "gauge.lore", placeholders));
        String stateKey = "gauge.state-" + v.state().key();
        if (v.state() == FuelState.ASLEEP && v.asleepSince() <= 0) {
            stateKey = "gauge.state-never";
        }
        lore.addAll(m.core.menus().lines(viewer, ProductionModule.ID, stateKey, placeholders));
        lore.addAll(m.core.menus().lines(viewer, ProductionModule.ID, "gauge.how", placeholders));
        return Optional.of(m.core.menus().item(material, m.core.menus().text(viewer, ProductionModule.ID, "gauge.name", placeholders), lore));
    }
}
