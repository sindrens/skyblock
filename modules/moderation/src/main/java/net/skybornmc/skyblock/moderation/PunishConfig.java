package net.skybornmc.skyblock.moderation;

import org.bukkit.Material;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * One validated, immutable {@code punishments.yml}. Texts are never here (they live in {@code messages/moderation.yml});
 * this only holds ids, slots, icons, ladders and rules.
 */
record PunishConfig(List<Templates.Category> categories, MenuLayout menu, WarnRules.Settings warnRules) {

    static final PunishConfig EMPTY = new PunishConfig(List.of(), MenuLayout.EMPTY, WarnRules.Settings.DISABLED);

    /** A fixed bottom-row button ({@code kick}, {@code history}, ...). */
    record Button(String id, int slot, Material icon) {
    }

    /** A plain warn button (bottom-left quarter): one warn with a preset reason text ({@code warn-buttons.<id>.reason}). */
    record WarnButton(String id, int slot, Material icon) {
    }

    /** Fixed button ids, in config order. Sanctions, bulk and dry run show "Not available yet" until P7/P5/P8. */
    static final List<String> BUTTONS = List.of("sanctions", "kick", "bulk", "history", "close", "more-reasons", "dry-run");

    /** Quarters of the menu, in config order. */
    static final List<String> QUARTERS = List.of("mutes", "bans", "small");

    /**
     * The single-screen {@code /p} layout.
     *
     * @param reasonSlots reason id → slot for every placed reason (all quarters)
     * @param quarterOf   reason id → quarter name ({@code mutes}, {@code bans}, {@code small})
     */
    record MenuLayout(boolean confirm, boolean keepOpen, Material filler, Material divider, List<Integer> dividerSlots,
                      int summarySlot, int warnCounterSlot, Material warnCounterIcon, Material unavailableIcon,
                      Map<String, Button> buttons, Map<String, Integer> reasonSlots, Map<String, String> quarterOf,
                      List<WarnButton> warns) {

        static final MenuLayout EMPTY = new MenuLayout(false, true, Material.GRAY_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE, List.of(), 4, 31, Material.YELLOW_CONCRETE, Material.GRAY_DYE, Map.of(),
                Map.of(), Map.of(), List.of());
    }

    /** Durations kept as text for display. */
    static String text(Duration d) {
        return d == null ? "perm" : Templates.Step.compact(d);
    }
}
