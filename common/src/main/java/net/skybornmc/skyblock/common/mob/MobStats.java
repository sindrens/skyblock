package net.skybornmc.skyblock.common.mob;

import java.util.List;

/**
 * The optional M8.1 stats of a mob ({@code mobs.yml → mobs.<id>}). Every field has a "not set" value that keeps the
 * mob exactly as before, so an old {@code mobs.yml} without these keys behaves unchanged.
 *
 * @param armor               damage reduction against player hits in percent, 0..{@value #MAX_ARMOR} (0 = none)
 * @param knockbackResistance 0..1, or {@code -1} = vanilla
 * @param attackCooldownTicks least ticks between two melee hits on players (0 = vanilla)
 * @param followRange         blocks the mob notices players from (0 = vanilla)
 * @param abilities           abilities in config order (empty = none); unset cooldown/chance/params come from the
 *                            catalog's {@code ability-defaults} and then the code defaults ({@link AbilityId})
 * @param dropTable           id of the shared drop table it uses ({@code null} = only inline drops)
 * @param eliteChance         chance (0..1) to spawn as an elite (0 = never)
 */
public record MobStats(double armor, double knockbackResistance, int attackCooldownTicks, double followRange,
                       List<AbilitySpec> abilities, String dropTable, double eliteChance) {

    /** Highest armor a mob can have (plan M8.1: max 60%). */
    public static final double MAX_ARMOR = 60.0;
    /** Most abilities on one mob (bosses use up to six). */
    public static final int MAX_ABILITIES = 6;

    /** No M8.1 stats at all. */
    public static final MobStats NONE = new MobStats(0, -1, 0, 0, List.of(), null, 0);

    public MobStats {
        armor = Double.isNaN(armor) ? 0 : Math.max(0, Math.min(MAX_ARMOR, armor));
        knockbackResistance = Double.isNaN(knockbackResistance) || knockbackResistance < 0 ? -1 : Math.min(1, knockbackResistance);
        attackCooldownTicks = Math.max(0, attackCooldownTicks);
        followRange = Double.isNaN(followRange) ? 0 : Math.max(0, followRange);
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        dropTable = dropTable == null || dropTable.isBlank() ? null : dropTable;
        eliteChance = Double.isNaN(eliteChance) ? 0 : Math.max(0, Math.min(1, eliteChance));
    }

    /** True if the mob has at least one ability. */
    public boolean hasAbilities() {
        return !abilities.isEmpty();
    }

    /** True if knockback resistance is left to vanilla. */
    public boolean vanillaKnockback() {
        return knockbackResistance < 0;
    }

    /** The ability with this id, or {@code null}. */
    public AbilitySpec ability(AbilityId id) {
        for (AbilitySpec a : abilities) {
            if (a.id() == id) {
                return a;
            }
        }
        return null;
    }
}
