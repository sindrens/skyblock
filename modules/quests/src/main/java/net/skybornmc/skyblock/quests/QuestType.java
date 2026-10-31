package net.skybornmc.skyblock.quests;

import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.Locale;
import java.util.Optional;

/**
 * What a quest asks for (PHASE-10 M10.7 quest pool). Only live content: every type names the system it needs, and a type
 * is offered only while that system runs and the player has it unlocked ({@link QuestsModule#eligible}).
 */
enum QuestType {
    /** Break blocks in the Depths mine wing, in a tier or deeper. */
    MINE("mine", SkillType.MINING, false),
    /** Chop logs in the Depths forest wing, in a tier or deeper. */
    CHOP("chop", SkillType.FORAGING, false),
    /** Kill Depths combat mobs (only rewarded kills: own hits, mini-boss damage share), in a tier or deeper. */
    KILL("kill", SkillType.COMBAT, false),
    /** Sell items for an amount of money (not the AFK pool). */
    SELL("sell", null, false),
    /** Gain levels on living tools. */
    TOOL_LEVELS("tool_levels", null, false),
    /** Forge a tool to its next tier. */
    FORGE("forge", null, false),
    /** Add or raise an enchant. */
    ENCHANT("enchant", null, false),
    /** Visit other players' islands (first visit of the day per island). */
    VISIT("visit", null, true),
    /** Like islands. */
    LIKE("like", null, true),
    /** Harvest ripe crops on your own island (PHASE-7 M7.3; offered while island farming is on). */
    HARVEST("harvest", null, false);

    private final String id;
    private final SkillType wing;
    private final boolean social;

    QuestType(String id, SkillType wing, boolean social) {
        this.id = id;
        this.wing = wing;
        this.social = social;
    }

    /** Lowercase id used in quests.yml, the database and messages. */
    String id() {
        return id;
    }

    /** The Depths wing skill of mine/chop/kill, or null. */
    SkillType wing() {
        return wing;
    }

    boolean depths() {
        return wing != null;
    }

    /** Social quests (visit, like): at most one per set, so a set never feels like busywork. */
    boolean social() {
        return social;
    }

    /** Types whose target can be worked out from the play rate (no fixed number needed in the pool). */
    boolean auto() {
        return depths() || this == SELL;
    }

    static Optional<QuestType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String s = id.trim().toLowerCase(Locale.ROOT);
        for (QuestType t : values()) {
            if (t.id.equals(s)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
