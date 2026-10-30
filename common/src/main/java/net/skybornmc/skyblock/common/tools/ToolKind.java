package net.skybornmc.skyblock.common.tools;

import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.Locale;
import java.util.Optional;

/**
 * The five custom tool types (DESIGN §5.1). Each one belongs to the skill it is used for: the Forge tiers of a pickaxe
 * follow the Mine tiers, axes the Forest tiers, swords the Combat tiers; hoes and rods get their tiers in Phase 7.
 */
public enum ToolKind {
    PICKAXE(SkillType.MINING),
    AXE(SkillType.FORAGING),
    SWORD(SkillType.COMBAT),
    HOE(SkillType.FARMING),
    ROD(SkillType.FISHING);

    private final SkillType skill;

    ToolKind(SkillType skill) {
        this.skill = skill;
    }

    /** The skill this tool levels with and whose Depths wing sets its tiers. */
    public SkillType skill() {
        return skill;
    }

    /** Lowercase id used in configs, item ids ({@code tool_pickaxe}), the database and commands. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ToolKind> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (ToolKind kind : values()) {
            if (kind.id().equals(key)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
