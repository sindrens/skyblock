package net.skybornmc.skyblock.common.skills;

import java.util.Locale;
import java.util.Optional;

/**
 * The five player skills (DESIGN §6.1). Mining, Foraging and Combat get XP from Phase 4 on; Farming and Fishing come
 * with Phase 7 (the skills module shows them locked until {@code skills.yml → available} lists them).
 */
public enum SkillType {
    MINING,
    FORAGING,
    COMBAT,
    FARMING,
    FISHING;

    /** Lowercase id used in configs, the database, placeholders and messages, e.g. {@code "mining"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SkillType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (SkillType type : values()) {
            if (type.id().equalsIgnoreCase(id.trim())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
