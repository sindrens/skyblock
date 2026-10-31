package net.skybornmc.skyblock.quests;

import java.util.Locale;
import java.util.Optional;

/** The three quest boards (PHASE-10 M10.7). Daily and weekly are per profile, island missions per island. */
enum QuestKind {
    DAILY("daily"),
    WEEKLY("weekly"),
    ISLAND("island");

    private final String id;

    QuestKind(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    /** Daily quests reset every day, weekly quests and island missions every Monday. */
    boolean weekly() {
        return this != DAILY;
    }

    static Optional<QuestKind> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String s = id.trim().toLowerCase(Locale.ROOT);
        for (QuestKind k : values()) {
            if (k.id.equals(s)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
