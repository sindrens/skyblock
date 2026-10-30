package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Blueprint drops (rare, per block/log/kill in The Depths, chances in {@code tools.yml → blueprints.<id>.drop}) and giving
 * blueprints (drops and {@code /blueprint give}). Every blueprint a player gets is recorded in {@code blueprints_found}.
 * Depths drop tables (M4.9) can take over the rolling later; giving stays here.
 */
final class BlueprintDrops {

    private final ToolsModule module;

    BlueprintDrops(ToolsModule module) {
        this.module = module;
    }

    /** One roll per blueprint that can drop in this wing and tier. */
    void roll(Player player, String wing, int tier, Location where) {
        if (!module.config().blueprintDrops) {
            return;
        }
        for (ToolCatalog.BlueprintDef def : module.catalog().blueprints().values()) {
            if (def.dropChance() <= 0 || !def.dropWing().equals(wing) || tier < def.dropMinTier()) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() < def.dropChance()) {
                give(player, def, "drop:" + wing + "_t" + tier, Actor.system("depths"));
                module.msg().send(player, "blueprint.found", Msg.mm("blueprint", module.items().blueprintName(def.id())),
                        Msg.p("rarity", def.rarity().id()));
                module.forge().sound(player, "unlock");
            }
        }
    }

    /** Creates a blueprint item for the player, records the find and writes an audit row. Returns the item id. */
    UUID give(Player player, ToolCatalog.BlueprintDef def, String source, Actor actor) {
        ItemStack item = module.items().createBlueprint(def, source);
        UUID id = ToolItems.readBlueprint(item).map(Map.Entry::getValue).orElse(null);
        module.forge().give(player, item, source.startsWith("admin") ? net.skybornmc.skyblock.common.box.BoxSource.STAFF
                : net.skybornmc.skyblock.common.box.BoxSource.TOOLS);
        UUID uuid = player.getUniqueId();
        int season = module.config().seasonId;
        module.core.database().run(c -> module.repository().recordBlueprint(c, uuid, def.id(), season, System.currentTimeMillis()))
                .exceptionally(e -> {
                    module.core.plugin().getLogger().log(Level.WARNING, "Tools: blueprint find not recorded", Database.cause(e));
                    return null;
                });
        module.core.audit().logPlayer(AuditCategory.ITEM, "blueprint_get", actor, uuid, player.getName(), null, player.getLocation(),
                Map.of("blueprint", def.id(), "item", String.valueOf(id), "source", source));
        return id;
    }

    Optional<ToolCatalog.BlueprintDef> def(String id) {
        return module.catalog().blueprint(id);
    }
}
