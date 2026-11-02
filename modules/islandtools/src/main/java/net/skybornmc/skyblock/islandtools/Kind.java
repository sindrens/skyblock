package net.skybornmc.skyblock.islandtools;

import java.util.Locale;
import java.util.Optional;

/** The four island tools (T4: the fishing rod comes with island fishing). */
enum Kind {
    PICKAXE, AXE, HOE, SWORD;

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<Kind> of(String id) {
        for (Kind k : values()) {
            if (k.id().equalsIgnoreCase(id)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
