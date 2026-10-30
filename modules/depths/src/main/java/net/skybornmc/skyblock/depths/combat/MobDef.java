package net.skybornmc.skyblock.depths.combat;

import java.util.List;
import java.util.Objects;

/**
 * One custom Depths mob from {@code mobs.yml → mobs.<id>} (M4.8). Names are in {@code messages/depths.yml → mobs.<id>}.
 *
 * @param id            unique id ({@code a-z 0-9 _})
 * @param type          vanilla entity type id ({@code zombie}, {@code slime} ...)
 * @param health        max health (0 = vanilla)
 * @param damage        damage per hit to players, melee and projectiles alike (0 = vanilla); armor still counts
 * @param speed         movement speed attribute (0 = vanilla; zombies are 0.23)
 * @param scale         size multiplier (1 = normal; mini-bosses are bigger)
 * @param size          slime / magma cube size (0 = vanilla random)
 * @param xp            Combat skill XP per kill
 * @param vanillaXp     vanilla experience per kill, given straight to the killer
 * @param crystalChance chance per kill of a crystal hit (above 1 = several hits)
 * @param crystalAmount crystals per hit
 * @param drops         satchel drops
 * @param glowing       glowing outline (mini-bosses)
 * @param nameVisible   name tag always shown (otherwise only when looked at)
 */
public record MobDef(String id, String type, double health, double damage, double speed, double scale, int size, double xp,
                     int vanillaXp, double crystalChance, int crystalAmount, List<Drop> drops, boolean glowing,
                     boolean nameVisible) {

    /**
     * One satchel drop of a mob.
     *
     * @param item   drop item id (depths.yml / mobs.yml drop-items)
     * @param min    least per kill when it drops
     * @param max    most per kill when it drops
     * @param chance chance per kill (0..1)
     */
    public record Drop(String item, int min, int max, double chance) {

        /** Average items per kill (before looting bonuses). */
        public double average() {
            return chance * (min + max) / 2.0;
        }
    }

    public MobDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        drops = List.copyOf(drops);
    }

    /** Average crystals per kill before bonuses. */
    public double crystalsPerKill() {
        return crystalChance * crystalAmount;
    }
}
