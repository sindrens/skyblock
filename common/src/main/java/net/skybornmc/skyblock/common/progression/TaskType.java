package net.skybornmc.skyblock.common.progression;

import java.util.Locale;
import java.util.Optional;

/**
 * Task types of the challenge book (PHASE-5 M5.3, O6). Counted types add to a shared island counter (owner P5-9: counted
 * from the island's creation, never reset); reach types read a live value (best member's skill, a pillar, the island
 * level) and stay done once reached.
 */
public enum TaskType {
    /** Break blocks: Depths ore and island blocks (blocks placed by players never count). Target: block id or {@code any}. */
    MINE("mine", true),
    /** Chop logs: Depths logs and natural island logs. Target: log block id or {@code any}. */
    CHOP("chop", true),
    /** Harvest ripe crops on the island. Target: crop block id or {@code any}. */
    FARM("farm", true),
    /** Kill mobs with your own hit or arrow. Target: entity id or {@code any}. */
    KILL("kill", true),
    /** Craft (or smelt) items. Target: item id or {@code any}. */
    CRAFT("craft", true),
    /** Sell items to the server (count). Target: {@code any}. */
    SELL("sell", true),
    /** Earn money by selling. Target: {@code any}. */
    EARN("earn", true),
    /** Deposit money into the island bank. Target: {@code any}. */
    DEPOSIT("deposit", true),
    /** Hand in items (one click, from the inventory or the island storage). Target: item id or {@code custom:<id>}. */
    DELIVER("deliver", true),
    /** Any counting member reaches this skill level. Target: skill id. */
    SKILL_LEVEL("skill-level", false),
    /** The island's pillar reaches this level. Target: pillar id. */
    PILLAR_LEVEL("pillar-level", false),
    /** The island reaches this island level. Target: {@code any}. */
    ISLAND_LEVEL("island-level", false);

    /** Target that matches every object of a counted type. */
    public static final String ANY = "any";

    private final String id;
    private final boolean counted;

    TaskType(String id, boolean counted) {
        this.id = id;
        this.counted = counted;
    }

    /** Config id, e.g. {@code mine}, {@code skill-level}. */
    public String id() {
        return id;
    }

    /** True for counter types (progress adds up); false for reach types (a live value is compared). */
    public boolean counted() {
        return counted;
    }

    /** True when the target is always {@link #ANY} (sell, earn, deposit, island level). */
    public boolean anyOnly() {
        return this == SELL || this == EARN || this == DEPOSIT || this == ISLAND_LEVEL;
    }

    /** The type with this id ({@code _} accepted for {@code -}, case-insensitive). */
    public static Optional<TaskType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String k = id.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        for (TaskType type : values()) {
            if (type.id.equals(k)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
