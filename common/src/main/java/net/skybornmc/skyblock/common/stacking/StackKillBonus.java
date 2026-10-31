package net.skybornmc.skyblock.common.stacking;

/**
 * Per-mob bonuses of a weapon on stacked kills (ENCHANTS §7.2), returned by {@link StackKillProvider#bonus}. The stacking
 * module applies them in one batch for every mob a hit (or an area effect) kills: no per-mob events or entities.
 *
 * @param extraLootPercent chance in percent (0-100) per killed mob for one extra loot roll (Plunder, already capped by the
 *                         extra drops family); never more than one extra roll per mob
 * @param xpPercent        extra vanilla XP in percent (Soul Reaper), 0 for none
 * @param toolXpPercent    extra sword XP in percent for stacked kills (Soul Reaper, R3-24); the tools module adds it to the
 *                         tool XP family before that family's cap, 0 for none
 */
public record StackKillBonus(double extraLootPercent, double xpPercent, double toolXpPercent) {

    /** No bonus. */
    public static final StackKillBonus NONE = new StackKillBonus(0, 0, 0);

    public StackKillBonus {
        extraLootPercent = Double.isFinite(extraLootPercent) ? Math.max(0, Math.min(100, extraLootPercent)) : 0;
        xpPercent = Double.isFinite(xpPercent) ? Math.max(0, xpPercent) : 0;
        toolXpPercent = Double.isFinite(toolXpPercent) ? Math.max(0, toolXpPercent) : 0;
    }

    public StackKillBonus(double extraLootPercent, double xpPercent) {
        this(extraLootPercent, xpPercent, 0);
    }

    public boolean none() {
        return extraLootPercent <= 0 && xpPercent <= 0 && toolXpPercent <= 0;
    }
}
